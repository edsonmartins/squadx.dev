package dev.squadx.controlpanel.mcp;

import org.springframework.stereotype.Service;

/**
 * Materializador padrão: indica indisponibilidade até a capability
 * {@code spec-versioning-materialization} fornecer a implementação real.
 */
@Service
public class NoopSpecMaterializer implements SpecMaterializer {

    @Override
    public MaterializationResult materialize(Long changeId) {
        return MaterializationResult.unavailable(
                "Materialization not configured yet (pending spec-versioning-materialization)");
    }
}
