"use client";

import { CheckCircle2, FileText, XCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  SPEC_TASK_STATUS_LABELS,
  RequirementResponse,
  SpecTaskResponse,
} from "@/lib/api";

const TYPE_LABEL: Record<RequirementResponse["type"], string> = {
  ADDED: "Adicionado",
  MODIFIED: "Modificado",
  REMOVED: "Removido",
};

const TYPE_CLASS: Record<RequirementResponse["type"], string> = {
  ADDED: "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400",
  MODIFIED: "bg-blue-500/15 text-blue-600 dark:text-blue-400",
  REMOVED: "bg-red-500/15 text-red-600 dark:text-red-400",
};

export function RequirementCard({
  requirement,
  tasks = [],
}: {
  requirement: RequirementResponse;
  tasks?: SpecTaskResponse[];
}) {
  const covered = requirement.scenarios.filter((s) => s.covered).length;
  const linkedTasks = requirement.task_refs
    .map((ref) => tasks.find((t) => t.id === ref))
    .filter((t): t is SpecTaskResponse => Boolean(t));

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge className={TYPE_CLASS[requirement.type]} variant="secondary">
            {TYPE_LABEL[requirement.type]}
          </Badge>
          <CardTitle className="text-base">
            {requirement.requirement_id} · {requirement.title}
          </CardTitle>
          <span className="ml-auto text-xs text-muted-foreground">
            Cobertura {covered}/{requirement.scenarios.length}
          </span>
        </div>
        {requirement.description && (
          <p className="text-sm text-muted-foreground">{requirement.description}</p>
        )}
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          {requirement.scenarios.map((scenario) => (
            <div key={scenario.id} className="rounded-lg border p-3 text-sm">
              <div className="flex items-center gap-2 font-medium">
                <FileText className="h-3.5 w-3.5 text-muted-foreground" />
                {scenario.name}
                {scenario.covered ? (
                  <CheckCircle2 className="ml-auto h-4 w-4 text-emerald-500" aria-label="Coberto" />
                ) : (
                  <XCircle className="ml-auto h-4 w-4 text-destructive" aria-label="Sem teste" />
                )}
              </div>
              <p className="mt-1 text-muted-foreground">
                <span className="font-medium text-foreground">When</span> {scenario.when}
              </p>
              <p className="text-muted-foreground">
                <span className="font-medium text-foreground">Then</span> {scenario.then}
              </p>
            </div>
          ))}
          {!requirement.scenarios.length && (
            <p className="text-sm text-muted-foreground">Nenhum cenário definido.</p>
          )}
        </div>

        {linkedTasks.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {linkedTasks.map((task) => (
              <Badge key={task.id} variant="outline" className="gap-1 font-normal">
                #{task.id} {task.title}
                <span className="text-muted-foreground">
                  · {SPEC_TASK_STATUS_LABELS[task.status]}
                </span>
              </Badge>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
