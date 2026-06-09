"use client";

import { RequirementResponse } from "@/lib/api";
import { Check, X } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function RequirementList({ requirements }: { requirements: RequirementResponse[] }) {
  if (requirements.length === 0) {
    return <p className="text-sm text-muted-foreground">Nenhum requisito ainda.</p>;
  }
  return (
    <div className="space-y-3">
      {requirements.map((req) => (
        <Card key={req.id}>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs">{req.requirement_id}</span>
              <span className="rounded bg-muted px-1.5 py-0.5 text-xs">{req.type}</span>
              {req.title}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {req.description && <p className="text-sm text-muted-foreground">{req.description}</p>}
            <ul className="space-y-1">
              {req.scenarios.map((sc) => (
                <li key={sc.id} className="rounded border p-2 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="font-medium">{sc.name}</span>
                    <span
                      className={
                        sc.covered ? "inline-flex items-center text-emerald-600" : "inline-flex items-center text-red-600"
                      }
                    >
                      {sc.covered ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
                      <span className="ml-1">{sc.covered ? "coberto" : "sem teste"}</span>
                    </span>
                  </div>
                  <p className="mt-1 text-muted-foreground">
                    <strong>QUANDO</strong> {sc.when}
                  </p>
                  <p className="text-muted-foreground">
                    <strong>ENTÃO</strong> {sc.then}
                  </p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
