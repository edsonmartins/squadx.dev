package dev.squadx.controller;

import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
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

@WebMvcTest(HealthController.class)
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
}
