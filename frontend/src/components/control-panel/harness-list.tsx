"use client";

import { HarnessResponse } from "@/lib/api";
import { Card, CardContent } from "@/components/ui/card";
import { HarnessModelSelect } from "./harness-model-select";

export function HarnessList({ harnesses }: { harnesses: HarnessResponse[] }) {
  if (harnesses.length === 0) {
    return (
      <Card>
        <CardContent className="py-10 text-center text-sm text-muted-foreground">
          Nenhum harness cadastrado. Registre o primeiro (Claude Code, Codex, Gemini CLI, Cursor…).
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-2">
      {harnesses.map((h) => (
        <Card key={h.id}>
          <CardContent className="flex flex-wrap items-center justify-between gap-3 py-3">
            <div>
              <p className="font-medium">{h.name}</p>
              <p className="text-xs text-muted-foreground">
                {h.vendor ? `${h.vendor} · ` : ""}
                {h.key}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <span
                className={
                  h.status === "CONNECTED"
                    ? "rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs text-emerald-600"
                    : "rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground"
                }
              >
                {h.status === "CONNECTED" ? "conectado" : "disponível"}
              </span>
              <HarnessModelSelect harness={h} />
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
