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
public class MaterializeResponse {

    private boolean ok;

    @JsonProperty("change_id")
    private Long changeId;

    private String version;
    private String commit;

    @JsonProperty("pr_url")
    private String prUrl;

    private String message;
}
