package dev.squadx.controlpanel.validation;

import org.springframework.stereotype.Service;

/**
 * Revisor padrão: não reprova por conformidade comportamental (aprova a etapa semântica). É o
 * ponto de extensão — uma implementação Pullwise real substitui este bean (ex.: via {@code @Primary}
 * ou profile) quando a integração existir.
 */
@Service
public class NoopConformanceReviewer implements ConformanceReviewer {

    @Override
    public ConformanceVerdict review(ConformanceRequest request) {
        return ConformanceVerdict.ok();
    }
}
