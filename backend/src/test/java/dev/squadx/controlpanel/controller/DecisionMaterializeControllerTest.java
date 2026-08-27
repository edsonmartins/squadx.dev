package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.parser.TaskDecisionParser;
import dev.squadx.controlpanel.service.TaskMaterializationService;
import dev.squadx.integration.ServiceJwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionMaterializeControllerTest {

    @Mock private ServiceJwtProvider serviceJwtProvider;
    @Mock private TaskMaterializationService materializer;
    @InjectMocks private DecisionMaterializeController controller;

    @Test
    void rejectsUnauthorized() {
        when(serviceJwtProvider.validateToken("bad", "squadx-decision")).thenReturn(false);
        Map<String, Object> payload = Map.of("projectId", 1L, "path", "x.md", "content", "c");

        var resp = controller.materialize("Bearer bad", payload);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().isSuccess()).isFalse();
    }

    @Test
    void materializesWithValidAuth() {
        when(serviceJwtProvider.validateToken("ok", "squadx-decision")).thenReturn(true);
        when(materializer.materialize("conteudo", "docs/rfc/x.md", "RFC", 5L))
                .thenReturn(List.of(10L, 11L));

        var resp = controller.materialize("Bearer ok", Map.of(
                "projectId", 5L, "path", "docs/rfc/x.md", "sourceKind", "RFC", "content", "conteudo"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().isSuccess()).isTrue();
        assertThat(resp.getBody().getData()).containsExactly(10L, 11L);
    }

    @Test
    void malformedDecisionReturnsBadRequest() {
        when(serviceJwtProvider.validateToken("ok", "squadx-decision")).thenReturn(true);
        when(materializer.materialize(anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new TaskDecisionParser.ParseException("Front-matter malformado"));

        var resp = controller.materialize("Bearer ok", Map.of(
                "projectId", 1L, "path", "x.md", "sourceKind", "RFC", "content", "---"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
