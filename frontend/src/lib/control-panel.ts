import { ChangePhase, Pass5Result, SpecTaskStatus } from "./api";
import { TONE_BADGE, TONE_TEXT } from "./design/semantics";

/** Rótulos PT dos estados do board (ADR-0004). */
export const SPEC_TASK_STATUS_LABEL: Record<SpecTaskStatus, string> = {
  A_FAZER: "A fazer",
  EM_CURSO: "Em execução",
  EM_VALIDACAO: "Em validação",
  CONCLUIDA: "Concluída",
  BLOQUEADA: "Bloqueada",
  AJUSTES: "Ajustes necessários",
};

/** Ordem das colunas no board. */
export const SPEC_TASK_STATUS_ORDER: SpecTaskStatus[] = [
  "A_FAZER",
  "EM_CURSO",
  "EM_VALIDACAO",
  "CONCLUIDA",
  "BLOQUEADA",
  "AJUSTES",
];

/** Classes de badge por estado (tons semânticos do design system). */
export const SPEC_TASK_STATUS_BADGE: Record<SpecTaskStatus, string> = {
  A_FAZER: TONE_BADGE.neutral,
  EM_CURSO: TONE_BADGE.info,
  EM_VALIDACAO: TONE_BADGE.warn,
  CONCLUIDA: TONE_BADGE.ok,
  BLOQUEADA: TONE_BADGE.danger,
  AJUSTES: TONE_BADGE.warn,
};

export const CHANGE_PHASE_LABEL: Record<ChangePhase, string> = {
  SPEC: "Especificação",
  IMPLEMENTACAO: "Implementação",
  VALIDACAO: "Validação",
  CONCLUIDA: "Concluída",
};

export const PASS5_LABEL: Record<Pass5Result, string> = {
  PENDING: "Pendente",
  PASS: "Aprovado",
  FAIL: "Reprovado",
};

/** Cor do desfecho do Pass 5. */
export const PASS5_OUTCOME_CLASS: Record<Pass5Result, string> = {
  PENDING: "text-muted-foreground",
  PASS: TONE_TEXT.ok,
  FAIL: TONE_TEXT.danger,
};

// As transições válidas são data-driven: o backend envia `available_transitions` em SpecTaskResponse
// (única fonte de verdade — a máquina de estados vive em SpecTaskStateMachine no servidor).

/** Rótulo de ação para uma transição manual. */
export function transitionActionLabel(to: SpecTaskStatus): string {
  switch (to) {
    case "EM_CURSO":
      return "Iniciar";
    case "EM_VALIDACAO":
      return "Enviar p/ validação";
    case "BLOQUEADA":
      return "Bloquear";
    case "A_FAZER":
      return "Reabrir";
    default:
      return SPEC_TASK_STATUS_LABEL[to];
  }
}
