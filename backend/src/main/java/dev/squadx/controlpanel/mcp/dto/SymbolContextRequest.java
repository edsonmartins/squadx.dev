package dev.squadx.controlpanel.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SymbolContextRequest {
    @NotBlank
    @Size(max = 300)
    private String symbol;
}
