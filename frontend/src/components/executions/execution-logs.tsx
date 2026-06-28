"use client";

import * as React from "react";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { ExecutionLogEntry } from "@/lib/api";
import type { ExecutionLogEvent } from "@/lib/socket";
import { useExecutionLogsSocket } from "@/hooks/use-socket";

interface ExecutionLogsProps {
  executionId: number;
  initialLogs?: ExecutionLogEntry[];
}

/** A log is human-facing unless explicitly classified otherwise (drift-safe default). */
function isHuman(log: ExecutionLogEntry): boolean {
  return (log.visibility ?? "human") === "human";
}

function importanceTone(importance?: string): string {
  switch (importance) {
    case "blocking":
      return "border-destructive text-destructive";
    case "high":
      return "border-amber-500 text-amber-600 dark:text-amber-400";
    default:
      return "border-muted-foreground/30 text-muted-foreground";
  }
}

/**
 * Execution log timeline with an Attention Budget filter (RFC-0005 §1). By default it shows only
 * human-facing events (quiet mode); toggling "Auditoria" reveals audit/debug events too.
 */
export function ExecutionLogs({ executionId, initialLogs = [] }: ExecutionLogsProps) {
  const [auditMode, setAuditMode] = React.useState(false);
  const [streamed, setStreamed] = React.useState<ExecutionLogEntry[]>([]);

  const handleLog = React.useCallback((event: ExecutionLogEvent) => {
    setStreamed((prev) => [
      ...prev,
      {
        level: event.level,
        visibility: event.visibility,
        importance: event.importance,
        message: event.message,
        created_at: new Date(event.timestamp).toISOString(),
      },
    ]);
  }, []);

  useExecutionLogsSocket(executionId, handleLog);

  const all = React.useMemo(() => [...initialLogs, ...streamed], [initialLogs, streamed]);
  const visible = auditMode ? all : all.filter(isHuman);
  const hiddenCount = all.length - visible.length;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-medium">Logs</h4>
        <div className="flex items-center gap-2">
          {!auditMode && hiddenCount > 0 && (
            <span className="text-xs text-muted-foreground">{hiddenCount} ocultos</span>
          )}
          <Label htmlFor="audit-mode" className="text-xs text-muted-foreground">
            Auditoria
          </Label>
          <Switch
            id="audit-mode"
            checked={auditMode}
            onCheckedChange={setAuditMode}
            aria-label="Mostrar logs de auditoria"
          />
        </div>
      </div>

      {visible.length === 0 ? (
        <p className="text-xs text-muted-foreground">
          {auditMode ? "Sem logs." : "Sem eventos para humanos. Ative Auditoria para ver tudo."}
        </p>
      ) : (
        <ul className="space-y-1">
          {visible.map((log, index) => (
            <li
              key={log.id ?? `${log.created_at ?? ""}-${index}`}
              className="flex items-start gap-2 text-xs"
            >
              <Badge
                variant="outline"
                className={cn("shrink-0 font-mono", importanceTone(log.importance))}
              >
                {log.level}
              </Badge>
              <span className="whitespace-pre-wrap break-words">{log.message}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
