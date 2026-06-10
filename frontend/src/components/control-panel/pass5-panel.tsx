"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, X, RefreshCw } from "lucide-react";
import { pass5Api, Pass5StatusResponse } from "@/lib/api";
import { PASS5_LABEL, PASS5_OUTCOME_CLASS } from "@/lib/control-panel";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";

interface Pass5PanelProps {
  taskId: number;
  title: string;
  changeId: number;
  status?: Pass5StatusResponse;
}

export function Pass5Panel({ taskId, title, changeId, status }: Pass5PanelProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["cp-pass5-change", changeId] });
  };

  const coverage = useMutation({
    mutationFn: (vars: { id: number; covered: boolean }) => pass5Api.setCoverage(vars.id, vars.covered),
    onSuccess: () => {
      invalidate();
      queryClient.invalidateQueries({ queryKey: ["cp-requirements", changeId] });
    },
    onError: () => toast({ title: "Falha ao atualizar cobertura", variant: "destructive" }),
  });

  const run = useMutation({
    mutationFn: () => pass5Api.run(taskId),
    onSuccess: () => {
      invalidate();
      queryClient.invalidateQueries({ queryKey: ["cp-tasks", changeId] });
      queryClient.invalidateQueries({ queryKey: ["cp-where-we-are"] });
      toast({ title: "Pass 5 executado" });
    },
    onError: () => toast({ title: "Falha ao revalidar", variant: "destructive" }),
  });

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center justify-between text-base">
          <span>{title}</span>
          <Button size="sm" variant="outline" disabled={run.isPending} onClick={() => run.mutate()}>
            <RefreshCw className="mr-1 h-3 w-3" /> Revalidar
          </Button>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <div className="flex items-center gap-3 text-sm">
          <span>
            Desfecho:{" "}
            <strong className={PASS5_OUTCOME_CLASS[status?.outcome ?? "PENDING"]}>
              {status?.outcome ? PASS5_LABEL[status.outcome] : "—"}
            </strong>
          </span>
          <span className="text-muted-foreground">
            Cobertura: {status?.coverage_covered ?? 0}/{status?.coverage_total ?? 0}
          </span>
        </div>
        {status?.critique && <p className="text-xs text-warn">{status.critique}</p>}
        <ul className="space-y-1">
          {status?.scenarios?.map((sc) => (
            <li key={sc.id} className="flex items-center justify-between rounded border p-2 text-xs">
              <span className="flex items-center gap-1">
                {sc.covered ? <Check className="h-3 w-3 text-ok" /> : <X className="h-3 w-3 text-danger" />}
                {sc.name}
              </span>
              <Button
                size="sm"
                variant="ghost"
                disabled={coverage.isPending}
                onClick={() => coverage.mutate({ id: sc.id, covered: !sc.covered })}
              >
                {sc.covered ? "Marcar sem teste" : "Marcar coberto"}
              </Button>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
