package dev.squadx.dto.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("user_email")
    private String userEmail;

    private String action;

    @JsonProperty("resource_type")
    private String resourceType;

    @JsonProperty("resource_id")
    private Long resourceId;

    private String details;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("created_at")
    private Instant createdAt;
}
