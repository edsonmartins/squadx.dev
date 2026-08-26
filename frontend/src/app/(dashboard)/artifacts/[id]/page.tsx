"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { executionArtifactsApi } from "@/lib/api";

export default function ArtifactPage() {
  const params = useParams<{ id: string }>();
  const artifactId = Number(params.id);
  const { data: artifact, isLoading, error } = useQuery({
    queryKey: ["execution-artifact", artifactId],
    queryFn: () => executionArtifactsApi.get(artifactId),
    enabled: Number.isFinite(artifactId),
  });

  if (isLoading) return <p className="p-6 text-muted-foreground">Loading architecture artifact…</p>;
  if (error || !artifact) return <p className="p-6 text-destructive">Architecture artifact not found.</p>;

  return (
    <main className="flex h-[calc(100vh-5rem)] flex-col gap-3 p-6">
      <div>
        <h1 className="text-xl font-semibold">{artifact.name}</h1>
        <p className="text-sm text-muted-foreground">
          {artifact.base_revision?.slice(0, 12) || "initial"} → {artifact.git_revision?.slice(0, 12) || "working tree"}
        </p>
      </div>
      {artifact.format === "HTML" && artifact.content ? (
        <iframe title={artifact.name} sandbox="" srcDoc={artifact.content}
          className="min-h-0 flex-1 rounded-md border bg-white" />
      ) : (
        <pre className="min-h-0 flex-1 overflow-auto rounded-md bg-muted p-4 text-xs">
          {artifact.content}
        </pre>
      )}
    </main>
  );
}
