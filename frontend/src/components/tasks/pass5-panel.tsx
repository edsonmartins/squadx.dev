"use client";

import * as React from "react";
import { pass5Api, type Pass5Status } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

export function Pass5Panel({ taskId }: { taskId: number }) {
  const [status, setStatus] = React.useState<Pass5Status | null>(null);
  const [running, setRunning] = React.useState(false);
  const load = React.useCallback(() => pass5Api.getStatus(taskId).then(setStatus), [taskId]);
  React.useEffect(() => { void load(); }, [load]);
  if (!status) return <p className="text-sm text-muted-foreground">Carregando Pass 5…</p>;
  const run = async () => { setRunning(true); try { setStatus(await pass5Api.run(taskId)); } finally { setRunning(false); } };
  return <div className="space-y-3">
    <div className="flex items-center justify-between">
      <div><p className="text-sm font-medium">Pass 5</p><p className="text-xs text-muted-foreground">{status.coverageCovered}/{status.coverageTotal} cenários cobertos</p></div>
      {status.outcome && <Badge variant={status.outcome === "PASS" ? "default" : "destructive"}>{status.outcome}</Badge>}
    </div>
    {status.critique && <p className="text-sm text-destructive">{status.critique}</p>}
    <Button variant="outline" size="sm" onClick={run} disabled={running}>{running ? "Executando…" : "Executar Pass 5"}</Button>
  </div>;
}
