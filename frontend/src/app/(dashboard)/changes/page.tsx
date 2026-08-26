"use client";

import { useQuery } from "@tanstack/react-query";
import { GitCommit, History } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { organizationsApi, projectsApi, changesApi } from "@/lib/api";

export default function ChangesPage() {
  const { data: organizations } = useQuery({ queryKey: ["organizations"], queryFn: () => organizationsApi.list() });
  const organizationId = organizations?.content?.[0]?.id;
  const { data: projects } = useQuery({
    queryKey: ["projects", organizationId],
    queryFn: () => projectsApi.list(organizationId),
    enabled: !!organizationId,
  });
  const projectList = projects?.content || [];
  const { data: changeGroups } = useQuery({
    queryKey: ["changes", projectList.map((project) => project.id)],
    queryFn: async () => Promise.all(projectList.map(async (project) => ({
      project,
      changes: (await changesApi.list(project.id)).content,
    }))),
    enabled: projectList.length > 0,
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Changes e versões</h1>
        <p className="text-muted-foreground">Histórico semântico materializado no repositório, com commit auditável.</p>
      </div>
      {!changeGroups?.length && <Card><CardContent className="py-10 text-sm text-muted-foreground">Nenhuma change encontrada.</CardContent></Card>}
      <div className="grid gap-4 lg:grid-cols-2">
        {changeGroups?.flatMap(({ project, changes }) => changes.map((change) => (
          <ChangeCard key={change.id} projectName={project.name} changeId={change.id} module={change.module} />
        )))}
      </div>
    </div>
  );
}

function ChangeCard({ projectName, changeId, module }: { projectName: string; changeId: number; module?: string | null }) {
  const { data: versions, isLoading } = useQuery({
    queryKey: ["change-versions", changeId],
    queryFn: () => changesApi.versions(changeId),
  });
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base"><History className="h-4 w-4" />{module || `Change #${changeId}`}</CardTitle>
        <p className="text-xs text-muted-foreground">{projectName} · change #{changeId}</p>
      </CardHeader>
      <CardContent>
        {isLoading && <p className="text-sm text-muted-foreground">Carregando histórico…</p>}
        <div className="space-y-3">
          {versions?.map((version) => (
            <div key={version.id} className="flex items-start gap-3 text-sm">
              <GitCommit className="mt-0.5 h-4 w-4 text-primary" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2"><span className="font-medium">v{version.version}{version.current ? " · atual" : ""}</span><span className="text-xs text-muted-foreground">{new Date(version.created_at).toLocaleDateString()}</span></div>
                <p className="text-muted-foreground">{version.summary || "Sem resumo"}</p>
                {version.commit_sha && <p className="font-mono text-xs text-muted-foreground">{version.commit_sha.slice(0, 12)}</p>}
              </div>
            </div>
          ))}
          {!isLoading && !versions?.length && <p className="text-sm text-muted-foreground">Nenhuma versão materializada.</p>}
        </div>
      </CardContent>
    </Card>
  );
}
