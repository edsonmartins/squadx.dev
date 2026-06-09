"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Copy, RefreshCw } from "lucide-react";
import { autopilotsApi, AutopilotResponse, AutopilotRunStatus } from "@/lib/api";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { describeCron } from "@/components/autopilots/schedule-editor";

const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

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
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [token, setToken] = useState<string | undefined>(autopilot?.webhook_token);

  useEffect(() => {
    setToken(autopilot?.webhook_token);
  }, [autopilot?.id, autopilot?.webhook_token]);

  const { data: runs, isLoading } = useQuery({
    queryKey: ["autopilot-runs", autopilot?.id],
    queryFn: () => autopilotsApi.runs(autopilot!.id),
    enabled: !!autopilot,
  });

  const rotateMutation = useMutation({
    mutationFn: () => autopilotsApi.rotateWebhook(autopilot!.id),
    onSuccess: (updated) => {
      setToken(updated.webhook_token);
      queryClient.invalidateQueries({ queryKey: ["autopilots"] });
      toast({ title: "Webhook token rotated" });
    },
    onError: () =>
      toast({
        title: "Error",
        description: "Failed to rotate the webhook token.",
        variant: "destructive",
      }),
  });

  const webhookUrl = token
    ? `${API_URL}/api/v1/webhooks/autopilots/${token}`
    : null;

  const copyWebhook = () => {
    if (webhookUrl && typeof navigator !== "undefined" && navigator.clipboard) {
      navigator.clipboard.writeText(webhookUrl);
      toast({ title: "Webhook URL copied" });
    }
  };

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
                <h4 className="text-sm font-medium mb-2">Webhook trigger</h4>
                <p className="text-xs text-muted-foreground mb-2">
                  POST to this URL to fire the autopilot (the token is the secret).
                </p>
                <div className="flex items-center gap-2">
                  <code className="flex-1 min-w-0 truncate rounded-md border bg-muted px-2 py-1.5 text-xs">
                    {webhookUrl ?? "No token — rotate to generate one"}
                  </code>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-8 w-8 shrink-0"
                    onClick={copyWebhook}
                    disabled={!webhookUrl}
                    title="Copy URL"
                  >
                    <Copy className="h-4 w-4" />
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-8 w-8 shrink-0"
                    onClick={() => rotateMutation.mutate()}
                    disabled={rotateMutation.isPending}
                    title="Rotate token"
                  >
                    <RefreshCw className="h-4 w-4" />
                  </Button>
                </div>
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
