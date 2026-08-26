package dev.squadx.service;

import dev.squadx.dto.harness.HarnessRequest;
import dev.squadx.dto.harness.HarnessResponse;
import dev.squadx.dto.harness.HarnessCatalogItem;
import dev.squadx.config.HarnessCatalogProperties;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Harness;
import dev.squadx.model.User;
import dev.squadx.repository.HarnessRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HarnessService {
    private final HarnessRepository harnessRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final HarnessCatalogProperties catalogProperties;

    public List<HarnessCatalogItem> catalog() {
        String configured = catalogProperties.getCatalog();
        if (configured == null || configured.isBlank()) return List.of();
        return java.util.Arrays.stream(configured.split(";"))
                .map(String::trim).filter(item -> !item.isBlank())
                .map(item -> item.split("\\|", -1))
                .filter(parts -> parts.length >= 3 && !parts[0].isBlank() && !parts[1].isBlank())
                .map(parts -> HarnessCatalogItem.builder().key(parts[0]).name(parts[1]).vendor(parts[2])
                        .models(parts.length > 3 ? java.util.Arrays.stream(parts[3].split(","))
                                .map(String::trim).filter(model -> !model.isBlank()).toList() : List.of()).build())
                .toList();
    }

    @Transactional
    public HarnessResponse create(Long organizationId, HarnessRequest request, User user) {
        validateAccess(organizationId, user);
        if (harnessRepository.existsByOrganizationIdAndKey(organizationId, request.getKey())) {
            throw new IllegalArgumentException("Harness key already exists in organization");
        }
        Harness harness = Harness.builder()
                .organization(organizationRepository.findById(organizationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Organization not found")))
                .key(request.getKey()).name(request.getName()).vendor(request.getVendor())
                .status(request.getStatus() == null ? "AVAILABLE" : request.getStatus())
                .model(request.getModel()).models(request.getModels() == null ? List.of() : request.getModels())
                .build();
        return map(harnessRepository.save(harness));
    }

    @Transactional(readOnly = true)
    public List<HarnessResponse> list(Long organizationId, User user) {
        validateAccess(organizationId, user);
        return harnessRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream().map(this::map).toList();
    }

    @Transactional
    public HarnessResponse update(Long id, HarnessRequest request, User user) {
        Harness harness = load(id);
        validateAccess(harness.getOrganization().getId(), user);
        if (request.getName() != null) harness.setName(request.getName());
        if (request.getVendor() != null) harness.setVendor(request.getVendor());
        if (request.getStatus() != null) harness.setStatus(request.getStatus());
        if (request.getModel() != null) harness.setModel(request.getModel());
        if (request.getModels() != null) harness.setModels(request.getModels());
        return map(harnessRepository.save(harness));
    }

    @Transactional
    public void delete(Long id, User user) {
        Harness harness = load(id);
        validateAccess(harness.getOrganization().getId(), user);
        harnessRepository.delete(harness);
    }

    private Harness load(Long id) {
        return harnessRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Harness not found"));
    }

    private void validateAccess(Long organizationId, User user) {
        if (user == null || !memberRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private HarnessResponse map(Harness h) {
        return HarnessResponse.builder().id(h.getId()).organizationId(h.getOrganization().getId())
                .key(h.getKey()).name(h.getName()).vendor(h.getVendor()).status(h.getStatus())
                .model(h.getModel()).models(h.getModels()).createdAt(h.getCreatedAt()).updatedAt(h.getUpdatedAt()).build();
    }
}
