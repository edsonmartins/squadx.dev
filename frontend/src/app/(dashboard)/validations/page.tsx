"use client";

import { useCallback, useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, FlaskConical, ShieldCheck, XCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  changesApi,
  organizationsApi,
  projectsApi,
  pass5Api,
  specTasksApi,
  Pass5Status,
  Pass5Result,
  SpecTaskResponse,
} from "@/lib/api";

interface QueueItem {
  task: SpecTaskResponse;
  changeModule: string;
}

export default function ValidationsPage() {
  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });
  const organizationId = organizations?.content?.[0]?.id;
  const { data: projects } = useQuery({
    queryKey: ["projects", organizationId],
    queryFn: () => projectsApi.list(organizationId),
    enabled: !!organizationId,
  });
  const projectList = projects?.content || [];

  const { data: queue = [], isLoading } = useQuery({
    queryKey: ["validation-queue", projectList.map((p) => p.id)],
    queryFn: async (): Promise<QueueItem[]> => {
      const groups = await Promise.all(
        projectList.map(async (project) => {
          const { content: changes } = await changesApi.list(project.id);
          const items = await Promise.all(
            changes.map(async (change) => {
              const tasks = await specTasksApi.listByChange(change.id);
              return tasks
                .filter((t) => t.status === "EM_VALIDACAO")
                .map((task) => ({ task, changeModule: change.module || `Change #${change.id}` }));
            })
          );
          return items.flat();
        })
      );
      return groups.flat();
    },
    enabled: projectList.length > 0,
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-3xl font-bold tracking-tight">
          <ShieldCheck className="h-6 w-6 text-primary" />
          Validação · Pass 5
        </h1>
        <p className="text-muted-foreground">
          Fila de tarefas aguardando o portão de conformidade. Cenário sem teste reprova;
          concluída/ajustes são definidos apenas aqui.
        </p>
      </div>

      {isLoading && (
        <Card>
          <CardContent className="py-10 text-sm text-muted-foreground">Carregando fila…</CardContent>
        </Card>
      )}
      {!isLoading && !queue.length && (
        <Card>
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            Nenhuma tarefa aguardando validação.
          </CardContent>
        </Card>
      )}

      <div className="space-y-4">
        {queue.map(({ task, changeModule }) => (
          <ValidationRow key={task.id} task={task} changeModule={changeModule} />
        ))}
      </div>
    </div>
  );
}

function ValidationRow({ task, changeModule }: { task: SpecTaskResponse; changeModule: string }) {
  const [status, setStatus] = useState<Pass5Status | null>(null);
  const [running, setRunning] = useState(false);

  const load = useCallback(() => {
    pass5Api
      .getStatus(task.id)
      .then(setStatus)
      .catch(() => setStatus(null));
  }, [task.id]);
  useEffect(() => {
    void load();
  }, [load]);

  const run = async () => {
    setRunning(true);
    try {
      setStatus(await pass5Api.run(task.id));
    } finally {
      setRunning(false);
    }
  };

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex flex-wrap items-center gap-2 text-base">
          <FlaskConical className="h-4 w-4 text-primary" />
          #{task.id} {task.title}
          <Badge variant="outline">{changeModule}</Badge>
          {status?.outcome && <OutcomeBadge outcome={status.outcome} />}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {status ? (
          <>
            <p className="text-sm text-muted-foreground">
              Cobertura {status.coverageCovered}/{status.coverageTotal} cenários
            </p>
            {!!status.scenarios.length && (
              <div className="grid gap-1.5 md:grid-cols-2">
                {status.scenarios.map((scenario) => (
                  <div key={scenario.id} className="flex items-center gap-2 rounded border px-2 py-1.5 text-sm">
                    {scenario.covered ? (
                      <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500" />
                    ) : (
                      <XCircle className="h-4 w-4 shrink-0 text-destructive" />
                    )}
                    <span className="truncate">{scenario.name}</span>
                    <span className="ml-auto shrink-0 text-xs text-muted-foreground">
                      {scenario.covered ? "coberto" : "sem teste"}
                    </span>
                  </div>
                ))}
              </div>
            )}
            {status.critique && (
              <p className="rounded bg-destructive/10 p-2 text-sm text-destructive">
                Crítica: {status.critique}
              </p>
            )}
          </>
        ) : (
          <p className="text-sm text-muted-foreground">Carregando status do Pass 5…</p>
        )}
        <Button size="sm" variant="outline" onClick={run} disabled={running}>
          {running ? "Executando…" : "Executar Pass 5"}
        </Button>
      </CardContent>
    </Card>
  );
}

function OutcomeBadge({ outcome }: { outcome: NonNullable<Pass5Status["outcome"]> | Pass5Result }) {
  if (outcome === "PASS") return <Badge className="bg-emerald-500/15 text-emerald-600 dark:text-emerald-400">aprovado</Badge>;
  if (outcome === "FAIL") return <Badge variant="destructive">ajustes necessários</Badge>;
  return <Badge variant="secondary">pendente</Badge>;
}
