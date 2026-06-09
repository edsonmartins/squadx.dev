"use client";

import Link from "next/link";
import { ChangeResponse } from "@/lib/api";
import { CHANGE_PHASE_LABEL } from "@/lib/control-panel";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function ChangesList({ changes }: { changes: ChangeResponse[] }) {
  if (changes.length === 0) {
    return (
      <Card>
        <CardContent className="py-10 text-center text-sm text-muted-foreground">
          Nenhuma mudança ainda. Crie a primeira para começar a especificar.
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {changes.map((change) => (
        <Link key={change.id} href={`/control-panel/changes/${change.id}`}>
          <Card className="h-full transition-colors hover:border-primary">
            <CardHeader className="pb-2">
              <CardTitle className="text-base">{change.module || `Mudança #${change.id}`}</CardTitle>
            </CardHeader>
            <CardContent className="text-xs text-muted-foreground">
              <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5">
                {CHANGE_PHASE_LABEL[change.phase]}
              </span>
            </CardContent>
          </Card>
        </Link>
      ))}
    </div>
  );
}
