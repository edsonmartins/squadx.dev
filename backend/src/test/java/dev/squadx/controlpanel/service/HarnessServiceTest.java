package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.harness.HarnessRequest;
import dev.squadx.controlpanel.dto.harness.HarnessResponse;
import dev.squadx.controlpanel.model.Harness;
import dev.squadx.controlpanel.model.enums.HarnessStatus;
import dev.squadx.controlpanel.repository.HarnessRepository;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.model.Organization;
import dev.squadx.model.User;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HarnessServiceTest {

    @Mock private HarnessRepository harnessRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private OrganizationMemberRepository memberRepository;

    @InjectMocks private HarnessService service;

    private User user;
    private Organization org;

    @BeforeEach
    void setUp() {
        user = User.builder().email("u@example.com").fullName("U").build();
        user.setId(1L);
        org = Organization.builder().name("Org").slug("org").build();
        org.setId(100L);
    }

    @Test
    void registersHarnessAsAvailable() {  // R1/R3
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(harnessRepository.existsByOrganizationIdAndKey(100L, "claude-code")).thenReturn(false);
        when(organizationRepository.findById(100L)).thenReturn(Optional.of(org));
        when(harnessRepository.save(any(Harness.class))).thenAnswer(i -> i.getArgument(0));

        HarnessResponse r = service.register(HarnessRequest.builder()
                .organizationId(100L).key("claude-code").name("Claude Code")
                .models(List.of("claude-opus-4-8", "claude-sonnet-4-6")).build(), user);

        assertThat(r.getStatus()).isEqualTo(HarnessStatus.AVAILABLE);
        assertThat(r.getModels()).contains("claude-opus-4-8");
    }

    @Test
    void rejectsDuplicateKey() {
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(harnessRepository.existsByOrganizationIdAndKey(100L, "claude-code")).thenReturn(true);

        assertThatThrownBy(() -> service.register(HarnessRequest.builder()
                .organizationId(100L).key("claude-code").name("Claude Code").build(), user))
                .isInstanceOf(BadRequestException.class);
        verify(harnessRepository, never()).save(any());
    }

    @Test
    void selectsAvailableModel() {  // R2
        Harness harness = Harness.builder().organization(org).key("k").name("n")
                .models(new java.util.ArrayList<>(List.of("m1", "m2"))).build();
        harness.setId(5L);
        when(harnessRepository.findById(5L)).thenReturn(Optional.of(harness));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(harnessRepository.save(any(Harness.class))).thenAnswer(i -> i.getArgument(0));

        HarnessResponse r = service.selectModel(5L, "m2", user);
        assertThat(r.getModel()).isEqualTo("m2");
    }

    @Test
    void rejectsModelNotAvailable() {  // R2
        Harness harness = Harness.builder().organization(org).key("k").name("n")
                .models(new java.util.ArrayList<>(List.of("m1"))).build();
        harness.setId(5L);
        when(harnessRepository.findById(5L)).thenReturn(Optional.of(harness));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.selectModel(5L, "m9", user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available");
        verify(harnessRepository, never()).save(any());
    }

    @Test
    void resolvesModelForAgent() {  // R2
        Harness harness = Harness.builder().organization(org).key("k").name("n").model("m2").build();
        when(harnessRepository.findByAgentId(3L)).thenReturn(Optional.of(harness));

        assertThat(service.resolveModelForAgent(3L)).contains("m2");
    }

    @Test
    void deniesWithoutAccess() {
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.list(100L, user))
                .isInstanceOf(ForbiddenException.class);
    }
}
