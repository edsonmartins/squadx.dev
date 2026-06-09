package dev.squadx.controlpanel.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVersionRequest {

    /** Resumo semântico da versão (o que mudou e por quê). */
    private String summary;
}
