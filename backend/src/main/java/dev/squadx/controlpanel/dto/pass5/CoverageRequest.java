package dev.squadx.controlpanel.dto.pass5;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageRequest {

    @NotNull(message = "covered is required")
    private Boolean covered;
}
