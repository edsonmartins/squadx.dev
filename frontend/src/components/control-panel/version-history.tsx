"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { GitCommit, UploadCloud } from "lucide-react";
import { specVersionsApi } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";

export function VersionHistory({ changeId }: { changeId: number }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const { data: versions } = useQuery({
    queryKey: ["cp-versions", changeId],
    queryFn: () => specVersionsApi.history(changeId),
  });

  const createVersion = useMutation({
    mutationFn: () => specVersionsApi.create(changeId, undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-versions", changeId] });
      toast({ title: "Versão criada" });
    },
  });

  const materialize = useMutation({
    mutationFn: () => specVersionsApi.materialize(changeId),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ["cp-versions", changeId] });
      const parts = [
        res.message,
        res.commit ? `commit ${res.commit.slice(0, 8)}` : undefined,
        res.pr_url ? `PR: ${res.pr_url}` : undefined,
      ].filter(Boolean);
      toast({
        title: res.ok ? "Materializado" : "Materialização pendente",
        description: parts.length > 0 ? parts.join(" · ") : undefined,
      });
    },
    onError: () => toast({ title: "Falha ao materializar", variant: "destructive" }),
  });

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center justify-between text-base">
          <span>Versões</span>
          <div className="flex gap-2">
            <Button size="sm" variant="outline" disabled={createVersion.isPending} onClick={() => createVersion.mutate()}>
              Nova versão
            </Button>
            <Button size="sm" disabled={materialize.isPending} onClick={() => materialize.mutate()}>
              <UploadCloud className="mr-1 h-3 w-3" /> Materializar
            </Button>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {!versions || versions.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhuma versão ainda.</p>
        ) : (
          <ul className="space-y-1">
            {versions.map((v) => (
              <li key={v.id} className="flex items-center justify-between rounded border p-2 text-sm">
                <span className="flex items-center gap-2">
                  <strong>v{v.version}</strong>
                  {v.current && <span className="rounded bg-ok-soft px-1.5 text-xs text-ok">atual</span>}
                  {v.summary && <span className="text-muted-foreground">{v.summary}</span>}
                </span>
                <span className="flex items-center gap-1 font-mono text-xs text-muted-foreground">
                  {v.commit ? (
                    <>
                      <GitCommit className="h-3 w-3" />
                      {v.commit.slice(0, 8)}
                    </>
                  ) : (
                    "não materializada"
                  )}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
