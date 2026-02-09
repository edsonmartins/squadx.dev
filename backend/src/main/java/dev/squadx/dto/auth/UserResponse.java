package dev.squadx.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private UserRole role;

    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("email_verified")
    private boolean emailVerified;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("last_login_at")
    private Instant lastLoginAt;
}
