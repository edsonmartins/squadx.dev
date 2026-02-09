"use client";

import { MessageSquare, Clock, User } from "lucide-react";
import { cn } from "@/lib/utils";
import { Card, CardContent } from "@/components/ui/card";
import type { TaskResponse, TaskPriority } from "@/lib/api";

interface TaskCardContentProps {
  task: TaskResponse;
  onClick?: () => void;
}

const priorityColors: Record<TaskPriority, string> = {
  LOW: "bg-slate-500",
  MEDIUM: "bg-blue-500",
  HIGH: "bg-orange-500",
  URGENT: "bg-red-500",
};

const priorityLabels: Record<TaskPriority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
};

export function TaskCardContent({ task, onClick }: TaskCardContentProps) {
  return (
    <Card
      className="task-card cursor-pointer transition-shadow hover:shadow-md"
      onClick={onClick}
    >
      <CardContent className="p-3">
        {/* Priority Badge */}
        <div className="flex items-center justify-end mb-2">
          <div className="flex items-center gap-1">
            <div
              className={cn(
                "h-2 w-2 rounded-full",
                priorityColors[task.priority]
              )}
            />
            <span className="text-xs text-muted-foreground">
              {priorityLabels[task.priority]}
            </span>
          </div>
        </div>

        {/* Title */}
        <h4 className="font-medium text-sm mb-2 line-clamp-2">{task.title}</h4>

        {/* Description Preview */}
        {task.description && (
          <p className="text-xs text-muted-foreground mb-3 line-clamp-2">
            {task.description}
          </p>
        )}

        {/* Tags */}
        {task.tags && task.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mb-3">
            {task.tags.slice(0, 3).map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs"
              >
                {tag}
              </span>
            ))}
            {task.tags.length > 3 && (
              <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs">
                +{task.tags.length - 3}
              </span>
            )}
          </div>
        )}

        {/* Footer */}
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <div className="flex items-center gap-3">
            {task.story_points && (
              <span className="flex items-center gap-1">
                <Clock className="h-3 w-3" />
                {task.story_points} pts
              </span>
            )}
            {task.subtasks_count > 0 && (
              <span className="flex items-center gap-1">
                <MessageSquare className="h-3 w-3" />
                {task.subtasks_count}
              </span>
            )}
          </div>

          {/* Assigned Agent */}
          {task.assigned_agent_name && (
            <div className="flex items-center gap-1">
              <div className="h-5 w-5 rounded-full bg-primary flex items-center justify-center">
                <User className="h-3 w-3 text-primary-foreground" />
              </div>
              <span className="truncate max-w-[80px]">
                {task.assigned_agent_name}
              </span>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
