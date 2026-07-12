import type {
  TaskStatus,
  TaskPriority,
  ApprovalStatus,
  ApprovalType,
  RecordingStatus,
} from "@/lib/api";

/**
 * Sistema semântico de cores (Design System v2 — "Mission Control").
 *
 * Fonte única de verdade para cores de status/prioridade em todo o app.
 * Os tons mapeiam para tokens CSS definidos em globals.css e expostos no
 * tailwind.config.ts (ok / warn / danger / info / neutral / live).
 */
export type SemanticTone = "ok" | "warn" | "danger" | "info" | "neutral";

/** Pill/badge: fundo suave + texto no tom. Respeita dark mode via tokens. */
export const TONE_BADGE: Record<SemanticTone, string> = {
  ok: "bg-ok-soft text-ok",
  warn: "bg-warn-soft text-warn",
  danger: "bg-danger-soft text-danger",
  info: "bg-info-soft text-info",
  neutral: "bg-neutral-soft text-neutral",
};

/** Texto colorido (ícones de status, labels). */
export const TONE_TEXT: Record<SemanticTone, string> = {
  ok: "text-ok",
  warn: "text-warn",
  danger: "text-danger",
  info: "text-info",
  neutral: "text-neutral",
};

/** Dot/indicador sólido (colunas do kanban, status dots). */
export const TONE_DOT: Record<SemanticTone, string> = {
  ok: "bg-ok",
  warn: "bg-warn",
  danger: "bg-danger",
  info: "bg-info",
  neutral: "bg-neutral",
};

/** Borda esquerda (cards de prioridade no kanban). */
export const TONE_BORDER_L: Record<SemanticTone, string> = {
  ok: "border-l-ok",
  warn: "border-l-warn",
  danger: "border-l-danger",
  info: "border-l-info",
  neutral: "border-l-neutral",
};

export const TASK_STATUS_TONE: Record<TaskStatus, SemanticTone> = {
  TODO: "neutral",
  IN_PROGRESS: "info",
  IN_REVIEW: "warn",
  BLOCKED: "danger",
  DONE: "ok",
  CANCELLED: "neutral",
};

export const TASK_PRIORITY_TONE: Record<TaskPriority, SemanticTone> = {
  LOW: "neutral",
  MEDIUM: "info",
  HIGH: "warn",
  URGENT: "danger",
};

export const EXECUTION_STATUS_TONE: Record<string, SemanticTone> = {
  COMPLETED: "ok",
  RUNNING: "info",
  FAILED: "danger",
  PENDING: "warn",
  CANCELLED: "neutral",
};

export const APPROVAL_STATUS_TONE: Record<ApprovalStatus, SemanticTone> = {
  PENDING: "warn",
  APPROVED: "ok",
  REJECTED: "danger",
  EXPIRED: "neutral",
  CANCELLED: "neutral",
};

export const APPROVAL_TYPE_TONE: Record<ApprovalType, SemanticTone> = {
  COMMIT: "info",
  DEPLOY: "warn",
  DELETE: "danger",
  MERGE: "info",
  CONFIGURATION: "neutral",
};

export const RECORDING_STATUS_TONE: Record<RecordingStatus, SemanticTone> = {
  RECORDING: "danger",
  COMPLETED: "ok",
  FAILED: "neutral",
};

/**
 * Paleta para gráficos (Recharts). Derivada do brand + tons semânticos,
 * em literais hsl porque atributos SVG não resolvem var().
 */
export const CHART_PALETTE = [
  "hsl(224 76% 48%)",
  "hsl(199 89% 48%)",
  "hsl(260 60% 56%)",
  "hsl(152 64% 36%)",
  "hsl(36 92% 44%)",
  "hsl(340 65% 47%)",
];

/** Cores de tinta para anotação sobre vídeo (precisam ser literais p/ canvas). */
export const ANNOTATION_PRESET_COLORS = [
  "#ef4444",
  "#3b82f6",
  "#22c55e",
  "#eab308",
  "#ffffff",
];
