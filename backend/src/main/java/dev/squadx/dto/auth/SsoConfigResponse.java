package dev.squadx.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoConfigResponse {

    private Long id;
    private Long organizationId;
    private String provider;
    private String clientId;
    private String issuerUri;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
