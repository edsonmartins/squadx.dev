"use client";

import { WhereWeAreResponse } from "@/lib/api";
import { SPEC_TASK_STATUS_ORDER, SPEC_TASK_STATUS_LABEL } from "@/lib/control-panel";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";

export function WhereWeAreCard({ data }: { data: WhereWeAreResponse }) {
  const pct = Math.round((data.progress ?? 0) * 100);
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span>Onde estamos</span>
          <span className="text-sm font-normal text-muted-foreground">
            {data.concluidas}/{data.total} concluídas
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <Progress value={pct} />
          <p className="mt-1 text-xs text-muted-foreground">{pct}% concluído</p>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
          {SPEC_TASK_STATUS_ORDER.map((status) => (
            <div key={status} className="rounded-lg border p-2">
              <p className="text-2xl font-semibold">{data.counts?.[status] ?? 0}</p>
              <p className="text-xs text-muted-foreground">{SPEC_TASK_STATUS_LABEL[status]}</p>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
