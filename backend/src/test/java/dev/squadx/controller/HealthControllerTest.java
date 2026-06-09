package dev.squadx.controller;

import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.integration.BrainSentryClient;
import dev.squadx.integration.IntegrationConfig;
import dev.squadx.integration.LinktorClient;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.MemoryPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = HealthController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private BrainSentryClient brainSentryClient;

    @MockBean
    private SquadxLiveClient squadxLiveClient;

    @MockBean
    private LinktorClient linktorClient;

    @MockBean
    private IntegrationConfig integrationConfig;

    @MockBean
    private MemoryPolicyService memoryPolicyService;

    @Nested
    @DisplayName("GET /api/v1/health/live")
    class LiveEndpoint {

        @Test
        @DisplayName("should return alive status with 200")
        void shouldReturnAlive() throws Exception {
            mockMvc.perform(get("/api/v1/health/live"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("alive"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/health")
    class HealthEndpoint {

        @Test
        @DisplayName("should return health status with database and redis checks")
        void shouldReturnHealthStatus() throws Exception {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isValid(2)).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(mockConnection);

            RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
            RedisConnection mockRedisConnection = mock(RedisConnection.class);
            when(redisTemplate.getConnectionFactory()).thenReturn(mockFactory);
            when(mockFactory.getConnection()).thenReturn(mockRedisConnection);
            when(mockRedisConnection.ping()).thenReturn("PONG");

            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("UP"))
                    .andExpect(jsonPath("$.data.service").value("squadx-backend"))
                    .andExpect(jsonPath("$.data.database").value("UP"))
                    .andExpect(jsonPath("$.data.redis").value("UP"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/health/doctor")
    class DoctorEndpoint {

        @Test
        @DisplayName("should return ready diagnostics when dependencies are healthy")
        void shouldReturnReadyDiagnostics() throws Exception {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isValid(2)).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(mockConnection);

            RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
            RedisConnection mockRedisConnection = mock(RedisConnection.class);
            when(redisTemplate.getConnectionFactory()).thenReturn(mockFactory);
            when(mockFactory.getConnection()).thenReturn(mockRedisConnection);
            when(mockRedisConnection.ping()).thenReturn("PONG");

            when(brainSentryClient.healthCheck()).thenReturn(java.util.Map.of(
                    "enabled", true,
                    "configured", true,
                    "reachable", true,
                    "status", "UP"
            ));
            when(memoryPolicyService.describePolicy()).thenReturn(java.util.Map.of(
                    "enabled", true,
                    "memoryScope", "adaptive",
                    "proceduralLimit", 5,
                    "proceduralMemoryEnabled", true,
                    "status", "ACTIVE"
            ));
            when(squadxLiveClient.healthCheck()).thenReturn(java.util.Map.of(
                    "enabled", false,
                    "configured", false,
                    "reachable", false,
                    "status", "DISABLED"
            ));
            when(linktorClient.healthCheck()).thenReturn(java.util.Map.of(
                    "enabled", true,
                    "configured", true,
                    "reachable", true,
                    "authenticated", true,
                    "status", "UP"
            ));
            when(integrationConfig.getServiceSecret()).thenReturn("secret");

            mockMvc.perform(get("/api/v1/health/doctor"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("READY"))
                    .andExpect(jsonPath("$.data.brainsentry.status").value("UP"))
                    .andExpect(jsonPath("$.data.memory_policy.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.live.status").value("DISABLED"))
                    .andExpect(jsonPath("$.data.linktor.status").value("UP"))
                    .andExpect(jsonPath("$.data.service_secret_configured").value(true));
        }

        @Test
        @DisplayName("should return degraded diagnostics when required dependency is down")
        void shouldReturnDegradedDiagnostics() throws Exception {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isValid(2)).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(mockConnection);

            RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
            RedisConnection mockRedisConnection = mock(RedisConnection.class);
            when(redisTemplate.getConnectionFactory()).thenReturn(mockFactory);
            when(mockFactory.getConnection()).thenReturn(mockRedisConnection);
            when(mockRedisConnection.ping()).thenReturn("PONG");

            when(brainSentryClient.healthCheck()).thenReturn(java.util.Map.of(
                    "enabled", true,
                    "configured", true,
                    "reachable", false,
                    "status", "DOWN"
            ));
            when(memoryPolicyService.describePolicy()).thenReturn(java.util.Map.of(
                    "enabled", true,
                    "memoryScope", "adaptive",
                    "proceduralLimit", 5,
                    "proceduralMemoryEnabled", true,
                    "status", "ACTIVE"
            ));
            when(squadxLiveClient.healthCheck()).thenReturn(java.util.Map.of(
                    "enabled", false,
                    "configured", false,
                    "reachable", false,
                    "status", "DISABLED"
            ));
            when(linktorClient.healthCheck()).thenReturn(java.util.Map.of(
                    "enabled", true,
                    "configured", true,
                    "reachable", false,
                    "authenticated", false,
                    "status", "DOWN"
            ));
            when(integrationConfig.getServiceSecret()).thenReturn("");

            mockMvc.perform(get("/api/v1/health/doctor"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors.status").value("DEGRADED"))
                    .andExpect(jsonPath("$.errors.brainsentry.status").value("DOWN"))
                    .andExpect(jsonPath("$.errors.linktor.status").value("DOWN"))
                    .andExpect(jsonPath("$.errors.service_secret_configured").value(false));
        }
    }
}
