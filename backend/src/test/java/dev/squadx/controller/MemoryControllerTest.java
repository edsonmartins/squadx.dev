package dev.squadx.controller;

import dev.squadx.dto.memory.MemorySkillRequest;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.MemoryGovernanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = MemoryController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemoryGovernanceService memoryGovernanceService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/v1/memory/skills should return managed skills")
    void shouldReturnSkills() throws Exception {
        when(memoryGovernanceService.listSkills(eq(7L), isNull(), isNull(), isNull(), eq(20), any()))
                .thenReturn(List.of(Map.of(
                        "id", "skill-1",
                        "summary", "Review migration failures",
                        "steps", List.of("Inspect logs")
                )));

        mockMvc.perform(get("/api/v1/memory/skills").param("organizationId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("skill-1"))
                .andExpect(jsonPath("$.data[0].steps[0]").value("Inspect logs"));
    }

    @Test
    @DisplayName("POST /api/v1/memory/skills should create a skill")
    void shouldCreateSkill() throws Exception {
        MemorySkillRequest request = new MemorySkillRequest();
        request.setOrganizationId(7L);
        request.setTitle("Review migration failures");
        request.setSummary("Check Flyway drift");

        when(memoryGovernanceService.createSkill(any(), any()))
                .thenReturn(Map.of("id", "skill-1", "summary", "Review migration failures: Check Flyway drift"));

        mockMvc.perform(post("/api/v1/memory/skills")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("skill-1"));
    }

    @Test
    @DisplayName("GET /api/v1/memory/history/search should return explicit history payload")
    void shouldReturnHistoryPayload() throws Exception {
        when(memoryGovernanceService.searchHistory(eq(7L), isNull(), isNull(), isNull(), eq("retry"), eq(10), any()))
                .thenReturn(Map.of(
                        "query", "retry",
                        "memories", List.of(Map.of("id", "mem-1")),
                        "skills", List.of(),
                        "executions", List.of(Map.of("execution_id", 31)),
                        "active_sessions", List.of()
                ));

        mockMvc.perform(get("/api/v1/memory/history/search")
                        .param("organizationId", "7")
                        .param("query", "retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("retry"))
                .andExpect(jsonPath("$.data.memories[0].id").value("mem-1"))
                .andExpect(jsonPath("$.data.executions[0].execution_id").value(31));
    }

    @Test
    @DisplayName("DELETE /api/v1/memory/skills/{id} should acknowledge deletion")
    void shouldDeleteSkill() throws Exception {
        mockMvc.perform(delete("/api/v1/memory/skills/skill-1").param("organizationId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
