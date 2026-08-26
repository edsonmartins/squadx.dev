"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, GitCommit, History } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { RequirementCard } from "@/components/specs/requirement-card";
import { SpecTaskBoard } from "@/components/specs/spec-task-board";
import { changesApi, requirementsApi, specTasksApi } from "@/lib/api";

export default function ChangeDetailPage() {
  const params = useParams<{ id: string }>();
  const changeId = Number(params?.id);

  const { data: change, isLoading: changeLoading } = useQuery({
    queryKey: ["change", changeId],
    queryFn: () => changesApi.get(changeId),
    enabled: Number.isFinite(changeId) && changeId > 0,
  });
  const { data: versions = [] } = useQuery({
    queryKey: ["change-versions", changeId],
    queryFn: () => changesApi.versions(changeId),
    enabled: Number.isFinite(changeId) && changeId > 0,
  });
  const { data: requirements = [] } = useQuery({
    queryKey: ["requirements", changeId],
    queryFn: () => requirementsApi.listByChange(changeId),
    enabled: Number.isFinite(changeId) && changeId > 0,
  });
  const { data: tasks = [] } = useQuery({
    queryKey: ["spec-tasks", changeId],
    queryFn: () => specTasksApi.listByChange(changeId),
    enabled: Number.isFinite(changeId) && changeId > 0,
  });

  const totalScenarios = requirements.reduce((sum, r) => sum + r.scenarios.length, 0);
  const coveredScenarios = requirements.reduce(
    (sum, r) => sum + r.scenarios.filter((s) => s.covered).length,
    0
  );
  const doneTasks = tasks.filter((t) => t.status === "CONCLUIDA").length;
  const progress = tasks.length ? Math.round((doneTasks / tasks.length) * 100) : 0;

  if (!Number.isFinite(changeId) || changeId <= 0) {
    return <p className="p-6 text-sm text-destructive">Change inválida.</p>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <Button asChild variant="ghost" size="icon" aria-label="Voltar">
          <Link href="/changes">
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div className="min-w-0">
          <h1 className="flex items-center gap-2 truncate text-2xl font-bold tracking-tight">
            <History className="h-5 w-5 text-primary" />
            {change?.module || `Change #${changeId}`}
          </h1>
          <p className="text-sm text-muted-foreground">
            {change?.project_name && `${change.project_name} · `}
            fase {change?.phase?.toLowerCase() || "—"} · progresso {progress}% ({doneTasks}/
            {tasks.length} tarefas) · cobertura {coveredScenarios}/{totalScenarios} cenários
          </p>
        </div>
      </div>

      <Tabs defaultValue="requisitos">
        <TabsList>
          <TabsTrigger value="requisitos">Requisitos</TabsTrigger>
          <TabsTrigger value="board">Board de tarefas</TabsTrigger>
          <TabsTrigger value="versoes">Versões da spec</TabsTrigger>
        </TabsList>

        <TabsContent value="requisitos" className="mt-4 space-y-4">
          {!requirements.length && (
            <Card>
              <CardContent className="py-10 text-center text-sm text-muted-foreground">
                Nenhum requisito registrado nesta change.
              </CardContent>
            </Card>
          )}
          <div className="grid gap-4 lg:grid-cols-2">
            {requirements.map((requirement) => (
              <RequirementCard
                key={requirement.id}
                requirement={requirement}
                tasks={tasks}
              />
            ))}
          </div>
        </TabsContent>

        <TabsContent value="board" className="mt-4">
          <SpecTaskBoard changeId={changeId} tasks={tasks} />
        </TabsContent>

        <TabsContent value="versoes" className="mt-4 space-y-3">
          {!versions.length && (
            <Card>
              <CardContent className="py-10 text-center text-sm text-muted-foreground">
                Nenhuma versão materializada.
              </CardContent>
            </Card>
          )}
          {versions.map((version) => (
            <Card key={version.id}>
              <CardContent className="flex items-start gap-3 py-4">
                <GitCommit className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">v{version.version}</span>
                    {version.current && <Badge>atual</Badge>}
                    <span className="text-xs text-muted-foreground">
                      {new Date(version.created_at).toLocaleDateString()}
                    </span>
                    {version.commit_sha && (
                      <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs">
                        {version.commit_sha.slice(0, 12)}
                      </code>
                    )}
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {version.summary || "Sem resumo"}
                  </p>
                </div>
              </CardContent>
            </Card>
          ))}
        </TabsContent>
      </Tabs>
    </div>
  );
}
