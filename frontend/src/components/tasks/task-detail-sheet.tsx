"use client";

import { useRouter } from "next/navigation";
import { useMutation, useQueryClient, useQuery } from "@tanstack/react-query";
import {
  Clock,
  Calendar,
  User,
  Play,
  Square,
  Video,
  Eye,
  Pencil,
  Trash2,
  MoreVertical,
  FileText,
  DollarSign,
  Zap,
  Bot,
} from "lucide-react";
import {
  executionsApi,
  liveViewApi,
  TaskResponse,
  TaskStatus,
} from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Progress } from "@/components/ui/progress";
import { ExecutionLogs } from "@/components/executions/execution-logs";
import { useToast } from "@/hooks/use-toast";
import { useTaskStore } from "@/stores/task-store";
import { cn } from "@/lib/utils";
import {
  TASK_STATUS_TONE,
  TASK_PRIORITY_TONE,
  TONE_BADGE,
  TONE_TEXT,
} from "@/lib/design/semantics";
import { format } from "date-fns";

const statusLabels: Record<TaskStatus, string> = {
  TODO: "To Do",
  IN_PROGRESS: "In Progress",
  IN_REVIEW: "In Review",
  BLOCKED: "Blocked",
  DONE: "Done",
  CANCELLED: "Cancelled",
};

interface TaskDetailSheetProps {
  task: TaskResponse | null;
  onClose: () => void;
  onEdit: (task: TaskResponse) => void;
  onDelete: (task: TaskResponse) => void;
}

export function TaskDetailSheet({ task, onClose, onEdit, onDelete }: TaskDetailSheetProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { setSelectedTask } = useTaskStore();
  const router = useRouter();

  // Fetch executions for the task
  const { data: executions } = useQuery({
    queryKey: ["executions", task?.id],
    queryFn: () => executionsApi.listByTask(task!.id),
    enabled: !!task?.id,
  });

  // Fetch live session for the task
  const { data: liveSession } = useQuery({
    queryKey: ["live-session", task?.id],
    queryFn: () => liveViewApi.getByTask(task!.id),
    enabled: !!task?.id,
    retry: false,
  });

  // Start execution mutation. An idempotency key lets the backend dedup repeated triggers
  // (RFC-0005 §2). The admission outcome is surfaced so duplicate/queued runs are not silent.
  const startExecutionMutation = useMutation({
    mutationFn: (idempotencyKey: string) =>
      executionsApi.start({ task_id: task!.id, idempotency_key: idempotencyKey }),
    onSuccess: (execution) => {
      queryClient.invalidateQueries({ queryKey: ["executions", task?.id] });
      const action = execution.admission?.action;
      if (action === "DROP_DUPLICATE") {
        toast({
          title: "Duplicate ignored",
          description: "This task already has a run for that trigger.",
        });
      } else if (action === "QUEUE_FOLLOW_UP") {
        toast({
          title: "Queued as follow-up",
          description: "A run is already active; your request was queued behind it.",
        });
      } else {
        toast({
          title: "Execution started",
          description: "The AI agent is now working on this task.",
        });
      }
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to start execution. Please try again.",
        variant: "destructive",
      });
    },
  });

  // Cancel the running execution. There is no "pause" on the backend, so the only
  // real control here is stop/cancel.
  const cancelExecutionMutation = useMutation({
    mutationFn: (executionId: number) => executionsApi.cancel(executionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["executions", task?.id] });
      toast({ title: "Stopping", description: "The run is being cancelled." });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to stop the run. Please try again.",
        variant: "destructive",
      });
    },
  });

  const handleClose = () => {
    setSelectedTask(null);
    onClose();
  };

  const latestExecution = executions?.content?.[0];
  const isRunning = latestExecution?.status === "RUNNING";
  const isDone = latestExecution?.status === "COMPLETED";
  // There is no real progress percentage on the execution (the daemon streams step
  // updates over STOMP but they are not persisted), so don't fabricate one: show a
  // determinate bar only when finished, and surface the latest real log line as the
  // current step instead of a hardcoded string.
  const latestLog =
    latestExecution?.logs && latestExecution.logs.length > 0
      ? latestExecution.logs[latestExecution.logs.length - 1].message
      : undefined;

  return (
    <Sheet open={!!task} onOpenChange={handleClose}>
      <SheetContent className="sm:max-w-lg overflow-y-auto" data-testid="task-detail-sheet">
        <SheetHeader>
          <div className="flex items-start justify-between">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Badge
                  className={cn(
                    TONE_BADGE[TASK_STATUS_TONE[task?.status || "TODO"]],
                    "border-transparent"
                  )}
                >
                  {statusLabels[task?.status || "TODO"]}
                </Badge>
                <Badge
                  variant="outline"
                  className={cn("border-2", TONE_TEXT[TASK_PRIORITY_TONE[task?.priority || "LOW"]])}
                >
                  {task?.priority}
                </Badge>
              </div>
              <SheetTitle className="text-xl">{task?.title}</SheetTitle>
            </div>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon">
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => task && onEdit(task)}>
                  <Pencil className="mr-2 h-4 w-4" />
                  Edit
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => task && onDelete(task)}
                  className="text-destructive focus:text-destructive"
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
          <SheetDescription className="text-left">
            {task?.description || "No description provided"}
          </SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-6">
          {/* Progress Section */}
          {(isRunning || latestExecution) && (
            <div className="rounded-lg border p-4 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Progress</span>
                <span className="text-sm text-muted-foreground">
                  {isRunning ? "In progress…" : isDone ? "100% Complete" : latestExecution?.status}
                </span>
              </div>
              {/* Running has no known percentage — a pulsing full bar signals activity
                  without claiming a false number; finished runs show a real 100%. */}
              <Progress
                value={isDone ? 100 : isRunning ? 100 : 0}
                className={cn("h-2", isRunning && "animate-pulse")}
              />
              {isRunning && latestLog && (
                <p className="text-sm text-muted-foreground line-clamp-2">
                  Current step: {latestLog}
                </p>
              )}
            </div>
          )}

          {/* Live View Section */}
          {(isRunning || liveSession) && (
            <div className="rounded-lg border border-red-500/50 bg-red-500/5 p-4 space-y-3">
              <div className="flex items-center gap-2">
                <span className="flex h-2 w-2 rounded-full bg-red-500 animate-pulse" />
                <span className="text-sm font-medium text-red-600">LIVE VIEW AVAILABLE</span>
              </div>

              {liveSession && (
                <>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Eye className="h-4 w-4" />
                    <span>{liveSession.current_viewers} watching</span>
                    <span className="mx-2">•</span>
                    <span>Session started: {format(new Date(liveSession.created_at), "p")}</span>
                  </div>

                  <Button
                    className="w-full"
                    variant="destructive"
                    onClick={() => router.push(`/live/${liveSession.code}`)}
                  >
                    <Video className="mr-2 h-4 w-4" />
                    Watch Live
                  </Button>
                </>
              )}

              {!liveSession && isRunning && (
                <p className="text-sm text-muted-foreground">
                  A live view opens automatically once the agent starts streaming its
                  screen; the link will appear here.
                </p>
              )}
            </div>
          )}

          {/* Details Grid */}
          <div className="grid grid-cols-2 gap-4">
            {task?.assigned_agent_name && (
              <div className="flex items-center gap-2 text-sm">
                <Bot className="h-4 w-4 text-muted-foreground" />
                <span>{task.assigned_agent_name}</span>
              </div>
            )}

            {task?.story_points && (
              <div className="flex items-center gap-2 text-sm">
                <Zap className="h-4 w-4 text-muted-foreground" />
                <span>{task.story_points} story points</span>
              </div>
            )}

            {task?.estimated_hours && (
              <div className="flex items-center gap-2 text-sm">
                <Clock className="h-4 w-4 text-muted-foreground" />
                <span>{task.estimated_hours}h estimated</span>
              </div>
            )}

            {task?.due_date && (
              <div className="flex items-center gap-2 text-sm">
                <Calendar className="h-4 w-4 text-muted-foreground" />
                <span>Due {format(new Date(task.due_date), "MMM d, yyyy")}</span>
              </div>
            )}

            {task?.created_by_name && (
              <div className="flex items-center gap-2 text-sm">
                <User className="h-4 w-4 text-muted-foreground" />
                <span>Created by {task.created_by_name}</span>
              </div>
            )}
          </div>

          {/* Tags */}
          {task?.tags && task.tags.length > 0 && (
            <div className="space-y-2">
              <h4 className="text-sm font-medium">Tags</h4>
              <div className="flex flex-wrap gap-2">
                {task.tags.map((tag) => (
                  <Badge key={tag} variant="secondary">
                    {tag}
                  </Badge>
                ))}
              </div>
            </div>
          )}

          {/* Execution Metrics */}
          {latestExecution && (
            <div className="rounded-lg border p-4 space-y-3">
              <h4 className="text-sm font-medium">Metrics</h4>
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div className="flex items-center gap-2">
                  <FileText className="h-4 w-4 text-muted-foreground" />
                  <span>{latestExecution.tokens_used.toLocaleString()} tokens</span>
                </div>
                <div className="flex items-center gap-2">
                  <DollarSign className="h-4 w-4 text-muted-foreground" />
                  <span>${latestExecution.cost.toFixed(2)}</span>
                </div>
                {latestExecution.duration_seconds && (
                  <div className="flex items-center gap-2">
                    <Clock className="h-4 w-4 text-muted-foreground" />
                    <span>{Math.round(latestExecution.duration_seconds / 60)} min</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Execution Logs — Attention Budget: quiet by default (RFC-0005 §1) */}
          {latestExecution && (
            <div className="rounded-lg border p-4">
              <ExecutionLogs
                executionId={latestExecution.id}
                initialLogs={latestExecution.logs}
              />
            </div>
          )}

          {/* Action Buttons */}
          <div className="space-y-2">
            {task?.status === "TODO" && (
              <Button
                className="w-full"
                onClick={() => startExecutionMutation.mutate(crypto.randomUUID())}
                disabled={startExecutionMutation.isPending}
                data-testid="start-execution-button"
              >
                <Play className="mr-2 h-4 w-4" />
                {startExecutionMutation.isPending ? "Starting..." : "Start Execution"}
              </Button>
            )}

            {isRunning && latestExecution && (
              <Button
                variant="destructive"
                className="w-full"
                onClick={() => cancelExecutionMutation.mutate(latestExecution.id)}
                disabled={cancelExecutionMutation.isPending}
              >
                <Square className="mr-2 h-4 w-4" />
                {cancelExecutionMutation.isPending ? "Stopping..." : "Stop"}
              </Button>
            )}
          </div>

          {/* Timestamps */}
          <div className="text-xs text-muted-foreground border-t pt-4 space-y-1">
            <p>Created: {task?.created_at && format(new Date(task.created_at), "PPp")}</p>
            {task?.started_at && (
              <p>Started: {format(new Date(task.started_at), "PPp")}</p>
            )}
            {task?.completed_at && (
              <p>Completed: {format(new Date(task.completed_at), "PPp")}</p>
            )}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
