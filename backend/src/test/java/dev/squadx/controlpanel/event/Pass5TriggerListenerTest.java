package dev.squadx.controlpanel.event;

import dev.squadx.controlpanel.service.Pass5Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class Pass5TriggerListenerTest {

    @Mock private Pass5Service pass5Service;
    @InjectMocks private Pass5TriggerListener listener;

    @Test
    void mergedEventTriggersValidation() {  // R5
        listener.onMerged(new SpecTaskMergedEvent(42L, "pr-3", "sha7"));
        verify(pass5Service).validate(42L, "sha7");
    }
}
