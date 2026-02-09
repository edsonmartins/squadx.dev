package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.organization.OrganizationRequest;
import dev.squadx.dto.organization.OrganizationResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.OrganizationMember;
import dev.squadx.model.User;
import dev.squadx.model.enums.OrgRole;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public OrganizationResponse create(OrganizationRequest request, User currentUser) {
        String slug = generateSlug(request.getName());

        if (organizationRepository.existsBySlug(slug)) {
            throw new BadRequestException("Organization with this name already exists");
        }

        Organization org = Organization.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .build();

        org = organizationRepository.save(org);

        // Add creator as owner
        OrganizationMember member = OrganizationMember.builder()
                .organization(org)
                .user(currentUser)
                .role(OrgRole.OWNER)
                .build();

        memberRepository.save(member);

        return mapToResponse(org);
    }

    public OrganizationResponse getById(Long id, User currentUser) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        validateUserAccess(id, currentUser.getId());

        return mapToResponse(org);
    }

    public OrganizationResponse getBySlug(String slug, User currentUser) {
        Organization org = organizationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        validateUserAccess(org.getId(), currentUser.getId());

        return mapToResponse(org);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> getByUserId(Long userId, Pageable pageable) {
        Page<OrganizationResponse> page = organizationRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    @Transactional
    public OrganizationResponse update(Long id, OrganizationRequest request, User currentUser) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        validateAdminAccess(id, currentUser.getId());

        if (request.getName() != null && !request.getName().equals(org.getName())) {
            String newSlug = generateSlug(request.getName());
            if (!newSlug.equals(org.getSlug()) && organizationRepository.existsBySlug(newSlug)) {
                throw new BadRequestException("Organization with this name already exists");
            }
            org.setName(request.getName());
            org.setSlug(newSlug);
        }

        if (request.getDescription() != null) {
            org.setDescription(request.getDescription());
        }

        if (request.getLogoUrl() != null) {
            org.setLogoUrl(request.getLogoUrl());
        }

        org = organizationRepository.save(org);

        return mapToResponse(org);
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        validateOwnerAccess(id, currentUser.getId());

        organizationRepository.delete(org);
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private void validateAdminAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserIdAndRoleIn(
                organizationId, userId, OrgRole.OWNER, OrgRole.ADMIN)) {
            throw new ForbiddenException("User does not have admin access to this organization");
        }
    }

    private void validateOwnerAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserIdAndRoleIn(
                organizationId, userId, OrgRole.OWNER)) {
            throw new ForbiddenException("Only owner can perform this action");
        }
    }

    private String generateSlug(String input) {
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }

    private OrganizationResponse mapToResponse(Organization org) {
        return OrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .description(org.getDescription())
                .logoUrl(org.getLogoUrl())
                .isActive(org.isActive())
                .membersCount((int) organizationRepository.countMembersByOrganizationId(org.getId()))
                .projectsCount((int) organizationRepository.countProjectsByOrganizationId(org.getId()))
                .squadsCount((int) organizationRepository.countSquadsByOrganizationId(org.getId()))
                .createdAt(org.getCreatedAt())
                .updatedAt(org.getUpdatedAt())
                .build();
    }
}
