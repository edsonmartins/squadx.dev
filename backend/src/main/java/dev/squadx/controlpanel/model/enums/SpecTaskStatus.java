package dev.squadx.controlpanel.model.enums;

/**
 * Estados do board de uma tarefa do Control Panel (ADR-0004). Rótulos de UI em PT:
 * A_FAZER=A fazer, EM_CURSO=Em execução, EM_VALIDACAO=Em validação, CONCLUIDA=Concluída,
 * BLOQUEADA=Bloqueada, AJUSTES=Ajustes necessários.
 * {@code CONCLUIDA} e {@code AJUSTES} só são atribuídos pelo Pass 5.
 */
public enum SpecTaskStatus {
    A_FAZER,
    EM_CURSO,
    EM_VALIDACAO,
    CONCLUIDA,
    BLOQUEADA,
    AJUSTES
}
