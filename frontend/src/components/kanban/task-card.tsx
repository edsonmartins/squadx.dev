"use client";

import { ListTree, Clock, User, Video } from "lucide-react";
import { cn } from "@/lib/utils";
import type { TaskResponse, TaskPriority } from "@/lib/api";
import {
  TASK_PRIORITY_TONE,
  TONE_BADGE,
  TONE_BORDER_L,
} from "@/lib/design/semantics";

interface TaskCardContentProps {
  task: TaskResponse;
  onClick?: () => void;
  liveSessionCode?: string;
  onWatchLive?: (code: string) => void;
}

const priorityLabels: Record<TaskPriority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
};

export function TaskCardContent({
  task,
  onClick,
  liveSessionCode,
  onWatchLive,
}: TaskCardContentProps) {
  const handleWatchLive = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (liveSessionCode && onWatchLive) {
      onWatchLive(liveSessionCode);
    }
  };

  return (
    <div
      className={cn(
        "task-card task-card-bordered p-2.5",
        TONE_BORDER_L[TASK_PRIORITY_TONE[task.priority]],
        liveSessionCode && "ring-1 ring-live/50"
      )}
      onClick={onClick}
      data-testid={`task-card-${task.id}`}
    >
      {/* Priority Badge + Live Indicator */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-1.5">
          <span
            className={cn(
              "badge-pill text-[10px]",
              TONE_BADGE[TASK_PRIORITY_TONE[task.priority]]
            )}
          >
            {priorityLabels[task.priority]}
          </span>
          {liveSessionCode && (
            <button
              type="button"
              onClick={handleWatchLive}
              aria-label="Assistir sessão ao vivo"
              className="inline-flex h-4 items-center rounded-full bg-live px-1.5 text-[9px] font-semibold text-white hover:bg-live/85 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-1"
            >
              <span className="live-dot mr-1 inline-block h-1.5 w-1.5" />
              <Video className="h-2.5 w-2.5 mr-0.5" />
              LIVE
            </button>
          )}
        </div>
        {task.story_points && (
          <span className="flex items-center gap-1 text-[10px] text-muted-foreground">
            <Clock className="h-3 w-3" />
            {task.story_points}pt
          </span>
        )}
      </div>

      {/* Title */}
      <h4 className="font-medium text-sm mb-1.5 line-clamp-2 font-heading leading-tight">
        {task.title}
      </h4>

      {/* Description Preview */}
      {task.description && (
        <p className="text-xs text-muted-foreground mb-2 line-clamp-2 leading-relaxed">
          {task.description}
        </p>
      )}

      {/* Tags */}
      {task.tags && task.tags.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-2">
          {task.tags.slice(0, 2).map((tag) => (
            <span
              key={tag}
              className="badge-pill bg-muted text-muted-foreground text-[10px]"
            >
              {tag}
            </span>
          ))}
          {task.tags.length > 2 && (
            <span className="badge-pill bg-muted text-muted-foreground text-[10px]">
              +{task.tags.length - 2}
            </span>
          )}
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-between text-[11px] text-muted-foreground pt-1.5 border-t border-border/50">
        <div className="flex items-center gap-2">
          {task.subtasks_count > 0 && (
            <span className="flex items-center gap-1" title={`${task.subtasks_count} subtask${task.subtasks_count > 1 ? "s" : ""}`}>
              <ListTree className="h-3 w-3" />
              {task.subtasks_count}
            </span>
          )}
        </div>

        {/* Assigned Agent */}
        {task.assigned_agent_name && (
          <div className="flex items-center gap-1.5">
            <div className="h-4 w-4 rounded-full bg-primary flex items-center justify-center">
              <User className="h-2.5 w-2.5 text-primary-foreground" />
            </div>
            <span className="truncate max-w-[70px]">
              {task.assigned_agent_name}
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
