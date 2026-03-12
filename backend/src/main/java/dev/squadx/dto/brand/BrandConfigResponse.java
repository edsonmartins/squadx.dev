package dev.squadx.dto.brand;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandConfigResponse {

    private Long id;

    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("app_name")
    private String appName;

    @JsonProperty("logo_url")
    private String logoUrl;

    @JsonProperty("favicon_url")
    private String faviconUrl;

    @JsonProperty("primary_color")
    private String primaryColor;

    @JsonProperty("secondary_color")
    private String secondaryColor;

    @JsonProperty("accent_color")
    private String accentColor;

    @JsonProperty("custom_domain")
    private String customDomain;

    @JsonProperty("custom_css")
    private String customCss;

    @JsonProperty("footer_text")
    private String footerText;

    @JsonProperty("support_email")
    private String supportEmail;

    private boolean enabled;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
