package dev.squadx.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank
    @JsonProperty("current_password")
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 100)
    @JsonProperty("new_password")
    private String newPassword;
}
