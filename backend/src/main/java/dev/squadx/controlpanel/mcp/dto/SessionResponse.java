package dev.squadx.controlpanel.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private String token;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("contract_version")
    private String contractVersion;
}
