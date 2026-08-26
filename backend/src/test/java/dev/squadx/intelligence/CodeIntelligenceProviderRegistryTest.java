package dev.squadx.intelligence;

import dev.squadx.model.CodeIntelligenceProviderPolicy;
import dev.squadx.repository.CodeIntelligenceProviderPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.squadx.intelligence.CodeIntelligenceModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CodeIntelligenceProviderRegistryTest {

    @Test
    void selectsTenantPrimaryFallbackAndShadowByCapability() {
        var policies = mock(CodeIntelligenceProviderPolicyRepository.class);
        CodeIntelligenceProvider nativeProvider = provider("native", Set.of(Capability.SEARCH));
        CodeIntelligenceProvider repoWise = provider("repowise", Set.of(Capability.SEARCH));
        CodeIntelligenceProvider fallback = provider("ripgrep", Set.of(Capability.SEARCH));
        var policy = CodeIntelligenceProviderPolicy.builder()
                .primaryProvider("RepoWise").fallbackProvider("ripgrep")
                .shadowProvider("native").shadowEnabled(true).build();
        when(policies.findByOrganizationId(7L)).thenReturn(Optional.of(policy));
        var registry = new CodeIntelligenceProviderRegistry(
                List.of(nativeProvider, repoWise, fallback), policies, "native", "ripgrep");

        var selection = registry.select(7L, Capability.SEARCH);

        assertThat(selection.primary()).isSameAs(repoWise);
        assertThat(selection.fallback()).isSameAs(fallback);
        assertThat(selection.shadow()).isSameAs(nativeProvider);
    }

    @Test
    void usesDefaultsWithoutTenantPolicyAndDropsUnsupportedFallback() {
        var policies = mock(CodeIntelligenceProviderPolicyRepository.class);
        CodeIntelligenceProvider nativeProvider = provider("native", Set.of(Capability.CHANGE_IMPACT));
        CodeIntelligenceProvider searchOnly = provider("ripgrep", Set.of(Capability.SEARCH));
        when(policies.findByOrganizationId(9L)).thenReturn(Optional.empty());
        var registry = new CodeIntelligenceProviderRegistry(
                List.of(nativeProvider, searchOnly), policies, "native", "ripgrep");

        var selection = registry.select(9L, Capability.CHANGE_IMPACT);

        assertThat(selection.primary()).isSameAs(nativeProvider);
        assertThat(selection.fallback()).isNull();
        assertThat(selection.shadow()).isNull();
    }

    private CodeIntelligenceProvider provider(String id, Set<Capability> capabilities) {
        CodeIntelligenceProvider provider = mock(CodeIntelligenceProvider.class);
        when(provider.descriptor()).thenReturn(new ProviderDescriptor(id, "test", capabilities));
        return provider;
    }
}

