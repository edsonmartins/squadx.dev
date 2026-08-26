package dev.squadx.controlpanel.mcp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchCodeRequest {
    @NotBlank
    @Size(max = 500)
    private String query;

    @Min(1)
    @Max(100)
    private int limit = 20;
}
