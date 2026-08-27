"use client";

import { ListTree, Clock, User, Video } from "lucide-react";
import { cn } from "@/lib/utils";
import type { TaskResponse, TaskPriority } from "@/lib/api";
import { Badge } from "@/components/ui/badge";

interface TaskCardContentProps {
  task: TaskResponse;
  onClick?: () => void;
  liveSessionCode?: string;
  onWatchLive?: (code: string) => void;
}

const priorityColors: Record<TaskPriority, string> = {
  LOW: "border-l-slate-400",
  MEDIUM: "border-l-blue-500",
  HIGH: "border-l-orange-500",
  URGENT: "border-l-red-500",
};

const priorityBadgeColors: Record<TaskPriority, string> = {
  LOW: "bg-slate-100 text-slate-600",
  MEDIUM: "bg-blue-50 text-blue-600",
  HIGH: "bg-orange-50 text-orange-600",
  URGENT: "bg-red-50 text-red-600",
};

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
        priorityColors[task.priority],
        liveSessionCode && "ring-1 ring-red-500/50"
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
              priorityBadgeColors[task.priority]
            )}
          >
            {priorityLabels[task.priority]}
          </span>
          {liveSessionCode && (
            <Badge
              variant="destructive"
              className="text-[9px] px-1.5 py-0 h-4 cursor-pointer hover:bg-red-600"
              onClick={handleWatchLive}
            >
              <span className="animate-pulse mr-1">
                <span className="inline-block h-1 w-1 bg-white rounded-full" />
              </span>
              <Video className="h-2.5 w-2.5 mr-0.5" />
              LIVE
            </Badge>
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
          {task.source_kind && task.source_kind !== "NONE" && (
            <span
              className="badge-pill bg-violet-50 text-violet-600 text-[9px]"
              title={`Origem da decisão: ${task.source_ref ?? task.source_kind}`}
            >
              {task.source_kind.toLowerCase()} · {shortSource(task.source_ref)}
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

/** Abrevia a âncora (ex.: "RFC-0007.md#T-0010-5" → "RFC-0007 #T-0010-5"). */
function shortSource(ref?: string | null): string {
  if (!ref) return "";
  const hash = ref.includes("#") ? ref.slice(ref.indexOf("#")) : "";
  const file = ref.slice(0, ref.indexOf("#") >= 0 ? ref.indexOf("#") : ref.length);
  const base = file.split("/").pop() ?? file;
  return `${base}${hash}`;
}
