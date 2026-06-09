"use client";

import { useState } from "react";
import { SpecTaskResponse } from "@/lib/api";
import { SPEC_TASK_STATUS_ORDER, SPEC_TASK_STATUS_LABEL } from "@/lib/control-panel";
import { Button } from "@/components/ui/button";
import { SpecTaskCard } from "./spec-task-card";

type AssigneeFilter = "ALL" | "HUMAN" | "AGENT";

export function SpecTaskBoard({ tasks }: { tasks: SpecTaskResponse[] }) {
  const [filter, setFilter] = useState<AssigneeFilter>("ALL");

  const filtered = tasks.filter((t) => filter === "ALL" || t.assignee_type === filter);

  return (
    <div className="space-y-3">
      <div className="flex gap-1">
        {(["ALL", "HUMAN", "AGENT"] as AssigneeFilter[]).map((f) => (
          <Button
            key={f}
            size="sm"
            variant={filter === f ? "default" : "outline"}
            onClick={() => setFilter(f)}
          >
            {f === "ALL" ? "Todos" : f === "HUMAN" ? "Humanos" : "IA"}
          </Button>
        ))}
      </div>

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {SPEC_TASK_STATUS_ORDER.map((status) => {
          const columnTasks = filtered.filter((t) => t.status === status);
          return (
            <div key={status} className="rounded-lg border bg-muted/30 p-2">
              <div className="mb-2 flex items-center justify-between px-1">
                <span className="text-sm font-semibold">{SPEC_TASK_STATUS_LABEL[status]}</span>
                <span className="text-xs text-muted-foreground">{columnTasks.length}</span>
              </div>
              <div className="space-y-2">
                {columnTasks.map((task) => (
                  <SpecTaskCard key={task.id} task={task} />
                ))}
                {columnTasks.length === 0 && (
                  <p className="px-1 py-2 text-xs text-muted-foreground">—</p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
