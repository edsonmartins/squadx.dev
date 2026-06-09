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

/** Alvos atribuídos exclusivamente pelo Pass 5 (não há botão manual). */
export const PASS5_ONLY: SpecTaskStatus[] = ["CONCLUIDA", "AJUSTES"];

/** Transições válidas da máquina de estados (espelha o backend; fonte de verdade é o servidor). */
const TRANSITIONS: Record<SpecTaskStatus, SpecTaskStatus[]> = {
  A_FAZER: ["EM_CURSO", "BLOQUEADA"],
  EM_CURSO: ["EM_VALIDACAO", "BLOQUEADA"],
  EM_VALIDACAO: ["CONCLUIDA", "AJUSTES", "BLOQUEADA"],
  AJUSTES: ["EM_CURSO", "BLOQUEADA"],
  BLOQUEADA: ["A_FAZER", "EM_CURSO"],
  CONCLUIDA: [],
};

/** Transições que um humano/agente pode acionar (exclui as do Pass 5). */
export function manualTransitions(from: SpecTaskStatus): SpecTaskStatus[] {
  return (TRANSITIONS[from] ?? []).filter((s) => !PASS5_ONLY.includes(s));
}

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
