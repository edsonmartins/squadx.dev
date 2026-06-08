"use client";

import { useQuery } from "@tanstack/react-query";
import { autopilotsApi, AutopilotResponse, AutopilotRunStatus } from "@/lib/api";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { describeCron } from "@/components/autopilots/schedule-editor";

const statusVariant: Record<
  AutopilotRunStatus,
  "default" | "secondary" | "destructive"
> = {
  SUCCESS: "default",
  SKIPPED: "secondary",
  FAILED: "destructive",
};

interface AutopilotDetailSheetProps {
  autopilot: AutopilotResponse | null;
  onClose: () => void;
}

export function AutopilotDetailSheet({
  autopilot,
  onClose,
}: AutopilotDetailSheetProps) {
  const { data: runs, isLoading } = useQuery({
    queryKey: ["autopilot-runs", autopilot?.id],
    queryFn: () => autopilotsApi.runs(autopilot!.id),
    enabled: !!autopilot,
  });

  return (
    <Sheet open={!!autopilot} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="w-full sm:max-w-lg overflow-y-auto">
        {autopilot && (
          <>
            <SheetHeader>
              <SheetTitle>{autopilot.name}</SheetTitle>
              <SheetDescription>{describeCron(autopilot.cron_expression)}</SheetDescription>
            </SheetHeader>

            <div className="mt-6 space-y-4">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <Detail label="Project" value={autopilot.project_name} />
                <Detail
                  label="Mode"
                  value={
                    autopilot.execution_mode === "RUN_TASK"
                      ? "Create & run"
                      : "Create task"
                  }
                />
                <Detail
                  label="Target"
                  value={
                    autopilot.target_agent_name ||
                    autopilot.target_squad_name ||
                    "Unassigned"
                  }
                />
                <Detail label="Runs" value={String(autopilot.run_count ?? 0)} />
                <Detail
                  label="Last run"
                  value={
                    autopilot.last_run_at
                      ? new Date(autopilot.last_run_at).toLocaleString()
                      : "Never"
                  }
                />
                <Detail
                  label="Status"
                  value={autopilot.enabled ? "Enabled" : "Disabled"}
                />
              </div>

              <div>
                <h4 className="text-sm font-medium mb-2">Run history</h4>
                {isLoading ? (
                  <p className="text-sm text-muted-foreground">Loading…</p>
                ) : runs?.content?.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No runs yet.</p>
                ) : (
                  <div className="space-y-2">
                    {runs?.content?.map((run) => (
                      <div
                        key={run.id}
                        className="flex items-start justify-between gap-2 rounded-md border p-3 text-sm"
                      >
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <Badge variant={statusVariant[run.status]}>
                              {run.status}
                            </Badge>
                            <span className="text-xs text-muted-foreground">
                              {run.trigger_type}
                            </span>
                          </div>
                          {run.message && (
                            <p className="mt-1 text-muted-foreground break-words">
                              {run.message}
                            </p>
                          )}
                        </div>
                        <span className="shrink-0 text-xs text-muted-foreground">
                          {new Date(run.triggered_at).toLocaleString()}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium truncate">{value}</p>
    </div>
  );
}
