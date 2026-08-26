"use client";

import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, FileCode2, Map } from "lucide-react";
import { executionArtifactsApi, type ExecutionArtifactResponse } from "@/lib/api";
import { Button } from "@/components/ui/button";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";

export function ExecutionArtifacts({ executionId }: { executionId: number }) {
  const [selectedId, setSelectedId] = React.useState<number | null>(null);
  const { data: artifacts = [] } = useQuery({
    queryKey: ["execution-artifacts", executionId],
    queryFn: () => executionArtifactsApi.list(executionId),
  });
  const { data: selected } = useQuery({
    queryKey: ["execution-artifact", selectedId],
    queryFn: () => executionArtifactsApi.get(selectedId!),
    enabled: selectedId !== null,
  });

  if (artifacts.length === 0) return null;

  const download = (artifact: ExecutionArtifactResponse) => {
    if (!artifact.content) return;
    const blob = new Blob([artifact.content], {
      type: artifact.format === "HTML" ? "text/html" : "application/json",
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = artifact.format === "HTML" ? "architecture.html" : "architecture.json";
    anchor.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="rounded-lg border p-4 space-y-3" data-testid="execution-artifacts">
      <h4 className="text-sm font-medium flex items-center gap-2"><Map className="h-4 w-4" />Artifacts</h4>
      {[...artifacts].sort((a, b) => (a.view_role === "DELTA" ? -1 : b.view_role === "DELTA" ? 1 : 0)).map((artifact) => (
        <button key={artifact.id} onClick={() => setSelectedId(artifact.id)}
          className="w-full rounded-md border p-3 text-left hover:bg-muted">
          <span className="flex items-center gap-2 text-sm font-medium">
            <FileCode2 className="h-4 w-4" />{artifact.name}
          </span>
          <span className="mt-1 block text-xs text-muted-foreground">
            {artifact.view_role || artifact.format} · {artifact.base_revision?.slice(0, 8) || "initial"} → {artifact.git_revision?.slice(0, 8) || "working tree"}
            {" · "}sha256 {artifact.checksum_sha256.slice(0, 12)}…
          </span>
        </button>
      ))}
      <Dialog open={selectedId !== null} onOpenChange={(open) => !open && setSelectedId(null)}>
        <DialogContent className="max-w-6xl h-[88vh] flex flex-col">
          <DialogHeader><DialogTitle>{selected?.name || "Loading artifact…"}</DialogTitle></DialogHeader>
          {selected?.format === "HTML" && selected.content ? (
            <iframe title={selected.name} sandbox="" srcDoc={selected.content}
              className="min-h-0 flex-1 w-full rounded-md border bg-white" />
          ) : selected?.content ? (
            <pre className="min-h-0 flex-1 overflow-auto rounded-md bg-muted p-4 text-xs">{selected.content}</pre>
          ) : <div className="flex-1 grid place-items-center text-muted-foreground">Loading…</div>}
          <Button variant="outline" onClick={() => selected && download(selected)} disabled={!selected?.content}>
            <Download className="mr-2 h-4 w-4" />Download original
          </Button>
        </DialogContent>
      </Dialog>
    </div>
  );
}
