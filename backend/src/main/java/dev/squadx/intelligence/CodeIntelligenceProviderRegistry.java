package dev.squadx.intelligence;

import dev.squadx.exception.BadRequestException;
import dev.squadx.model.CodeIntelligenceProviderPolicy;
import dev.squadx.repository.CodeIntelligenceProviderPolicyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.squadx.intelligence.CodeIntelligenceModels.Capability;

@Component
@Slf4j
public class CodeIntelligenceProviderRegistry {

    private final Map<String, CodeIntelligenceProvider> providers;
    private final CodeIntelligenceProviderPolicyRepository policyRepository;
    private final String defaultProvider;
    private final String defaultFallback;

    public CodeIntelligenceProviderRegistry(
            List<CodeIntelligenceProvider> providers,
            CodeIntelligenceProviderPolicyRepository policyRepository,
            @Value("${intelligence.default-provider:native}") String defaultProvider,
            @Value("${intelligence.default-fallback:}") String defaultFallback) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> normalize(provider.descriptor().id()), Function.identity()));
        this.policyRepository = policyRepository;
        this.defaultProvider = normalize(defaultProvider);
        this.defaultFallback = normalizeNullable(defaultFallback);
        log.info("Registered code-intelligence providers: {}", this.providers.keySet());
    }

    public ProviderSelection select(Long organizationId, Capability capability) {
        CodeIntelligenceProviderPolicy policy = policyRepository.findByOrganizationId(organizationId)
                .orElse(null);
        String primaryId = policy == null ? defaultProvider : normalize(policy.getPrimaryProvider());
        String fallbackId = policy == null ? defaultFallback : normalizeNullable(policy.getFallbackProvider());
        String shadowId = policy != null && Boolean.TRUE.equals(policy.getShadowEnabled())
                ? normalizeNullable(policy.getShadowProvider()) : null;

        CodeIntelligenceProvider primary = require(primaryId, capability, "primary");
        CodeIntelligenceProvider fallback = optional(fallbackId, capability);
        CodeIntelligenceProvider shadow = optional(shadowId, capability);
        if (fallback == primary) fallback = null;
        if (shadow == primary) shadow = null;
        return new ProviderSelection(primary, fallback, shadow);
    }

    public Set<String> registeredProviderIds() {
        return providers.keySet();
    }

    public CodeIntelligenceProvider requireProvider(String id, Capability capability) {
        return require(normalize(id), capability, "requested");
    }

    private CodeIntelligenceProvider require(String id, Capability capability, String role) {
        CodeIntelligenceProvider provider = optional(id, capability);
        if (provider == null) {
            throw new BadRequestException("No " + role + " code-intelligence provider '" + id
                    + "' supports " + capability);
        }
        return provider;
    }

    private CodeIntelligenceProvider optional(String id, Capability capability) {
        if (id == null) return null;
        CodeIntelligenceProvider provider = providers.get(normalize(id));
        return provider != null && provider.descriptor().capabilities().contains(capability)
                ? provider : null;
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "provider id").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }

    public record ProviderSelection(CodeIntelligenceProvider primary,
                                    CodeIntelligenceProvider fallback,
                                    CodeIntelligenceProvider shadow) {}
}
