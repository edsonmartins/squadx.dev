package dev.squadx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional, non-secret catalog of harnesses supplied by deployment configuration. */
@Getter
@Setter
@ConfigurationProperties(prefix = "squadx.harnesses")
public class HarnessCatalogProperties {
    /** key|name|vendor|model1,model2;key2|name2|vendor2|model */
    private String catalog = "";
}
