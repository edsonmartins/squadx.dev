package dev.squadx.dto.region;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegionInfo {

    private String name;

    @JsonProperty("display_name")
    private String displayName;

    private String status;

    @JsonProperty("latency_ms")
    private Long latency;
}
