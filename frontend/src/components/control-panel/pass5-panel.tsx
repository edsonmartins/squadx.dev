"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, X, RefreshCw } from "lucide-react";
import { pass5Api, SpecTaskResponse } from "@/lib/api";
import { PASS5_LABEL } from "@/lib/control-panel";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";

export function Pass5Panel({ task }: { task: SpecTaskResponse }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const { data } = useQuery({
    queryKey: ["cp-pass5", task.id],
    queryFn: () => pass5Api.status(task.id),
  });

  const coverage = useMutation({
    mutationFn: (vars: { id: number; covered: boolean }) => pass5Api.setCoverage(vars.id, vars.covered),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-pass5", task.id] });
      queryClient.invalidateQueries({ queryKey: ["cp-requirements", task.change_id] });
    },
  });

  const run = useMutation({
    mutationFn: () => pass5Api.run(task.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-pass5", task.id] });
      queryClient.invalidateQueries({ queryKey: ["cp-tasks", task.change_id] });
      queryClient.invalidateQueries({ queryKey: ["cp-where-we-are"] });
      toast({ title: "Pass 5 executado" });
    },
    onError: () => toast({ title: "Falha ao revalidar", variant: "destructive" }),
  });

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center justify-between text-base">
          <span>{task.title}</span>
          <Button size="sm" variant="outline" disabled={run.isPending} onClick={() => run.mutate()}>
            <RefreshCw className="mr-1 h-3 w-3" /> Revalidar
          </Button>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <div className="flex items-center gap-3 text-sm">
          <span>
            Desfecho:{" "}
            <strong className={data?.outcome === "PASS" ? "text-emerald-600" : data?.outcome === "FAIL" ? "text-red-600" : ""}>
              {data?.outcome ? PASS5_LABEL[data.outcome] : "—"}
            </strong>
          </span>
          <span className="text-muted-foreground">
            Cobertura: {data?.coverage_covered ?? 0}/{data?.coverage_total ?? 0}
          </span>
        </div>
        {data?.critique && <p className="text-xs text-orange-600">{data.critique}</p>}
        <ul className="space-y-1">
          {data?.scenarios?.map((sc) => (
            <li key={sc.id} className="flex items-center justify-between rounded border p-2 text-xs">
              <span className="flex items-center gap-1">
                {sc.covered ? <Check className="h-3 w-3 text-emerald-600" /> : <X className="h-3 w-3 text-red-600" />}
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
