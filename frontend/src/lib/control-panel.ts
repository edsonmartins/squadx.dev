import { ChangePhase, Pass5Result, SpecTaskStatus } from "./api";

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

/** Classes de badge por estado. */
export const SPEC_TASK_STATUS_BADGE: Record<SpecTaskStatus, string> = {
  A_FAZER: "bg-muted text-muted-foreground",
  EM_CURSO: "bg-blue-500/15 text-blue-600 dark:text-blue-400",
  EM_VALIDACAO: "bg-amber-500/15 text-amber-600 dark:text-amber-400",
  CONCLUIDA: "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400",
  BLOQUEADA: "bg-red-500/15 text-red-600 dark:text-red-400",
  AJUSTES: "bg-orange-500/15 text-orange-600 dark:text-orange-400",
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
  PASS: "text-emerald-600",
  FAIL: "text-red-600",
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
