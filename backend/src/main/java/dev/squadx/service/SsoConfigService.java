package dev.squadx.service;

import dev.squadx.dto.auth.SsoConfigRequest;
import dev.squadx.dto.auth.SsoConfigResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.SsoConfig;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.SsoConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SsoConfigService {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "microsoft", "okta", "custom");

    private final SsoConfigRepository ssoConfigRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public SsoConfigResponse createOrUpdate(Long orgId, SsoConfigRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        String provider = request.getProvider().toLowerCase();
        if (!SUPPORTED_PROVIDERS.contains(provider)) {
            throw new BadRequestException("Unsupported SSO provider: " + provider
                    + ". Supported: " + SUPPORTED_PROVIDERS);
        }

        SsoConfig config = ssoConfigRepository
                .findByOrganizationIdAndProvider(orgId, provider)
                .orElse(SsoConfig.builder()
                        .organization(org)
                        .provider(provider)
                        .build());

        config.setClientId(request.getClientId());
        config.setClientSecret(request.getClientSecret());
        config.setIssuerUri(request.getIssuerUri());
        config.setEnabled(request.getEnabled());

        config = ssoConfigRepository.save(config);
        log.info("SSO config saved for org={}, provider={}, enabled={}", orgId, provider, config.isEnabled());

        return mapToResponse(config);
    }

    @Transactional(readOnly = true)
    public List<SsoConfigResponse> getByOrganization(Long orgId) {
        return ssoConfigRepository.findByOrganizationId(orgId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SsoConfigResponse getByOrganizationAndProvider(Long orgId, String provider) {
        SsoConfig config = ssoConfigRepository.findByOrganizationIdAndProvider(orgId, provider.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SSO config not found for provider: " + provider));
        return mapToResponse(config);
    }

    @Transactional
    public void delete(Long orgId, String provider) {
        SsoConfig config = ssoConfigRepository.findByOrganizationIdAndProvider(orgId, provider.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SSO config not found for provider: " + provider));
        ssoConfigRepository.delete(config);
        log.info("SSO config deleted for org={}, provider={}", orgId, provider);
    }

    @Transactional
    public SsoConfigResponse toggleEnabled(Long orgId, String provider, boolean enabled) {
        SsoConfig config = ssoConfigRepository.findByOrganizationIdAndProvider(orgId, provider.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SSO config not found for provider: " + provider));
        config.setEnabled(enabled);
        config = ssoConfigRepository.save(config);
        log.info("SSO config toggled for org={}, provider={}, enabled={}", orgId, provider, enabled);
        return mapToResponse(config);
    }

    private SsoConfigResponse mapToResponse(SsoConfig config) {
        return SsoConfigResponse.builder()
                .id(config.getId())
                .organizationId(config.getOrganization().getId())
                .provider(config.getProvider())
                .clientId(config.getClientId())
                // Never expose client secret in responses
                .issuerUri(config.getIssuerUri())
                .enabled(config.isEnabled())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
