package dev.squadx.controlpanel.dto.harness;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectModelRequest {

    @NotBlank(message = "model is required")
    private String model;
}
