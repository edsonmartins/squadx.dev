"use client";

import { SpecTaskResponse } from "@/lib/api";
import { PASS5_LABEL, PASS5_OUTCOME_CLASS } from "@/lib/control-panel";
import { Bot, User } from "lucide-react";
import { TransitionControls } from "./transition-controls";

export function SpecTaskCard({ task }: { task: SpecTaskResponse }) {
  const assigneeName =
    task.assignee_type === "AGENT" ? task.assigned_agent_name : task.assigned_user_name;
  return (
    <div className="rounded-lg border bg-card p-3 shadow-sm">
      <p className="text-sm font-medium">{task.title}</p>
      <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        {task.requirement_ref && (
          <span className="rounded bg-muted px-1.5 py-0.5 font-mono">{task.requirement_ref}</span>
        )}
        {task.assignee_type && (
          <span className="inline-flex items-center gap-1">
            {task.assignee_type === "AGENT" ? <Bot className="h-3 w-3" /> : <User className="h-3 w-3" />}
            {assigneeName ?? task.assignee_type}
          </span>
        )}
        {task.pass5 !== "PENDING" && (
          <span className={PASS5_OUTCOME_CLASS[task.pass5]}>Pass 5: {PASS5_LABEL[task.pass5]}</span>
        )}
      </div>
      {task.blocker_reason && (
        <p className="mt-1 text-xs text-red-600">Bloqueio: {task.blocker_reason}</p>
      )}
      {task.revise_reason && (
        <p className="mt-1 text-xs text-orange-600">Ajustes: {task.revise_reason}</p>
      )}
      <div className="mt-2">
        <TransitionControls task={task} />
      </div>
    </div>
  );
}
