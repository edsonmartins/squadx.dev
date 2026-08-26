package dev.squadx.service;

import dev.squadx.config.HarnessCatalogProperties;
import dev.squadx.dto.harness.HarnessRequest;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.model.Harness;
import dev.squadx.model.Organization;
import dev.squadx.model.User;
import dev.squadx.repository.HarnessRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HarnessServiceTest {
    @Mock private HarnessRepository harnessRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private HarnessCatalogProperties properties;
    @InjectMocks private HarnessService service;

    @Test
    void rejectsHarnessCreationOutsideOrganizationMembership() {
        User user = User.builder().email("user@example.com").build();
        user.setId(10L);
        when(memberRepository.existsByOrganizationIdAndUserId(20L, 10L)).thenReturn(false);

        HarnessRequest request = new HarnessRequest();
        request.setKey("openai");
        request.setName("OpenAI");
        request.setVendor("OpenAI");
        assertThatThrownBy(() -> service.create(20L, request, user))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(organizationRepository, harnessRepository);
    }

    @Test
    void parsesConfiguredCatalogWithoutExposingSecrets() {
        when(properties.getCatalog()).thenReturn("openai|OpenAI|OpenAI|gpt-4o,gpt-4o-mini");

        var catalog = service.catalog();

        assertThat(catalog).singleElement().satisfies(item -> {
            assertThat(item.getKey()).isEqualTo("openai");
            assertThat(item.getModels()).containsExactly("gpt-4o", "gpt-4o-mini");
        });
    }
}
