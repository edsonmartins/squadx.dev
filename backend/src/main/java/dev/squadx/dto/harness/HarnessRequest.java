package dev.squadx.dto.harness;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class HarnessRequest {
    @NotBlank private String key;
    @NotBlank private String name;
    @NotBlank private String vendor;
    private String status;
    private String model;
    @JsonProperty("models") private List<String> models;
}
