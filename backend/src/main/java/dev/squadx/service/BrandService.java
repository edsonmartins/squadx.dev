package dev.squadx.service;

import dev.squadx.dto.brand.BrandConfigRequest;
import dev.squadx.dto.brand.BrandConfigResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.BrandConfig;
import dev.squadx.model.Organization;
import dev.squadx.repository.BrandConfigRepository;
import dev.squadx.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandService {

    private final BrandConfigRepository brandConfigRepository;
    private final OrganizationRepository organizationRepository;

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.[A-Za-z0-9-]{1,63})*\\.[A-Za-z]{2,}$"
    );

    @Transactional(readOnly = true)
    public BrandConfigResponse getByOrganization(Long orgId) {
        BrandConfig config = brandConfigRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand config not found for organization: " + orgId));
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public BrandConfigResponse getByDomain(String domain) {
        BrandConfig config = brandConfigRepository.findByCustomDomain(domain)
                .orElseThrow(() -> new ResourceNotFoundException("Brand config not found for domain: " + domain));
        return toResponse(config);
    }

    @Transactional
    public BrandConfigResponse upsert(Long orgId, BrandConfigRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + orgId));

        validateRequest(request, orgId);

        BrandConfig config = brandConfigRepository.findByOrganizationId(orgId)
                .orElse(BrandConfig.builder()
                        .organization(org)
                        .build());

        applyRequest(config, request);
        config = brandConfigRepository.save(config);

        log.info("Brand config upserted for organization={}", orgId);
        return toResponse(config);
    }

    @Transactional
    public void delete(Long orgId) {
        if (!brandConfigRepository.existsByOrganizationId(orgId)) {
            throw new ResourceNotFoundException("Brand config not found for organization: " + orgId);
        }
        brandConfigRepository.deleteByOrganizationId(orgId);
        log.info("Brand config deleted for organization={}", orgId);
    }

    private void validateRequest(BrandConfigRequest request, Long orgId) {
        if (request.getPrimaryColor() != null) {
            validateHexColor(request.getPrimaryColor(), "primaryColor");
        }
        if (request.getSecondaryColor() != null) {
            validateHexColor(request.getSecondaryColor(), "secondaryColor");
        }
        if (request.getAccentColor() != null) {
            validateHexColor(request.getAccentColor(), "accentColor");
        }
        if (request.getCustomDomain() != null && !request.getCustomDomain().isBlank()) {
            validateDomain(request.getCustomDomain(), orgId);
        }
    }

    private void validateHexColor(String color, String fieldName) {
        if (!HEX_COLOR_PATTERN.matcher(color).matches()) {
            throw new BadRequestException(
                    "Invalid hex color format for " + fieldName + ": " + color + ". Expected format: #RRGGBB");
        }
    }

    private void validateDomain(String domain, Long orgId) {
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new BadRequestException("Invalid domain format: " + domain);
        }
        // Check uniqueness - another org should not own this domain
        brandConfigRepository.findByCustomDomain(domain).ifPresent(existing -> {
            if (!existing.getOrganization().getId().equals(orgId)) {
                throw new BadRequestException("Domain already in use by another organization: " + domain);
            }
        });
    }

    private void applyRequest(BrandConfig config, BrandConfigRequest request) {
        if (request.getAppName() != null) config.setAppName(request.getAppName());
        if (request.getLogoUrl() != null) config.setLogoUrl(request.getLogoUrl());
        if (request.getFaviconUrl() != null) config.setFaviconUrl(request.getFaviconUrl());
        if (request.getPrimaryColor() != null) config.setPrimaryColor(request.getPrimaryColor());
        if (request.getSecondaryColor() != null) config.setSecondaryColor(request.getSecondaryColor());
        if (request.getAccentColor() != null) config.setAccentColor(request.getAccentColor());
        if (request.getCustomDomain() != null) config.setCustomDomain(request.getCustomDomain());
        if (request.getCustomCss() != null) config.setCustomCss(request.getCustomCss());
        if (request.getFooterText() != null) config.setFooterText(request.getFooterText());
        if (request.getSupportEmail() != null) config.setSupportEmail(request.getSupportEmail());
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
    }

    private BrandConfigResponse toResponse(BrandConfig config) {
        return BrandConfigResponse.builder()
                .id(config.getId())
                .organizationId(config.getOrganization().getId())
                .appName(config.getAppName())
                .logoUrl(config.getLogoUrl())
                .faviconUrl(config.getFaviconUrl())
                .primaryColor(config.getPrimaryColor())
                .secondaryColor(config.getSecondaryColor())
                .accentColor(config.getAccentColor())
                .customDomain(config.getCustomDomain())
                .customCss(config.getCustomCss())
                .footerText(config.getFooterText())
                .supportEmail(config.getSupportEmail())
                .enabled(config.isEnabled())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
