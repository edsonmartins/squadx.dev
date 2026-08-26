package dev.squadx.dto.harness;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class HarnessCatalogItem {
    String key;
    String name;
    String vendor;
    List<String> models;
}
