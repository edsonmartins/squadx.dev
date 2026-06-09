package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.version.SpecVersionResponse;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.SpecVersion;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.SpecVersionRepository;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecVersionServiceTest {

    @Mock private SpecVersionRepository specVersionRepository;
    @Mock private ChangeRepository changeRepository;
    @Mock private OrganizationMemberRepository memberRepository;

    @InjectMocks private SpecVersionService service;

    private User user;
    private Change change;

    @BeforeEach
    void setUp() {
        user = User.builder().email("u@example.com").fullName("U").build();
        user.setId(1L);
        Organization org = Organization.builder().name("Org").slug("org").build();
        org.setId(100L);
        Project project = Project.builder().name("Proj").slug("proj").organization(org).build();
        project.setId(7L);
        change = Change.builder().project(project).build();
        change.setId(5L);
    }

    @Test
    void firstVersionIsOneAndCurrent() {
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(specVersionRepository.findTopByChangeIdOrderByVersionDesc(5L)).thenReturn(Optional.empty());
        when(specVersionRepository.findByChangeIdAndCurrentTrue(5L)).thenReturn(Optional.empty());
        when(specVersionRepository.save(any(SpecVersion.class))).thenAnswer(i -> i.getArgument(0));

        SpecVersionResponse r = service.createVersion(5L, "first", user);

        assertThat(r.getVersion()).isEqualTo(1);
        assertThat(r.isCurrent()).isTrue();
    }

    @Test
    void incrementsAndUnmarksPreviousCurrent() {
        SpecVersion prev = SpecVersion.builder().change(change).version(2).current(true).build();
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(specVersionRepository.findTopByChangeIdOrderByVersionDesc(5L)).thenReturn(Optional.of(prev));
        when(specVersionRepository.findByChangeIdAndCurrentTrue(5L)).thenReturn(Optional.of(prev));
        when(specVersionRepository.save(any(SpecVersion.class))).thenAnswer(i -> i.getArgument(0));

        SpecVersionResponse r = service.createVersion(5L, "next", user);

        assertThat(r.getVersion()).isEqualTo(3);
        assertThat(prev.isCurrent()).isFalse();
    }

    @Test
    void deniesWithoutAccess() {
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.createVersion(5L, "x", user))
                .isInstanceOf(ForbiddenException.class);
        verify(specVersionRepository, never()).save(any());
    }
}
