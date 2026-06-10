"use client";

import { useMutation, useQueryClient, useQuery } from "@tanstack/react-query";
import {
  Clock,
  Calendar,
  User,
  Play,
  Pause,
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
  tasksApi,
  executionsApi,
  liveViewApi,
  TaskResponse,
  TaskStatus,
  TaskPriority,
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
import { useToast } from "@/hooks/use-toast";
import { useTaskStore } from "@/stores/task-store";
import { cn } from "@/lib/utils";
import { format } from "date-fns";
import {
  TASK_STATUS_TONE,
  TASK_PRIORITY_TONE,
  TONE_BADGE,
  TONE_TEXT,
} from "@/lib/design/semantics";

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

  // Start execution mutation
  const startExecutionMutation = useMutation({
    mutationFn: () => executionsApi.start({ task_id: task!.id }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["executions", task?.id] });
      toast({
        title: "Execution started",
        description: "The AI agent is now working on this task.",
      });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to start execution. Please try again.",
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
  const progress = isRunning ? 75 : latestExecution?.status === "COMPLETED" ? 100 : 0;

  return (
    <Sheet open={!!task} onOpenChange={handleClose}>
      <SheetContent className="sm:max-w-lg overflow-y-auto" data-testid="task-detail-sheet">
        <SheetHeader>
          <div className="flex items-start justify-between">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Badge className={cn(TONE_BADGE[TASK_STATUS_TONE[task?.status || "TODO"]], "border-transparent")}>
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
                <span className="text-sm text-muted-foreground">{progress}% Complete</span>
              </div>
              <Progress value={progress} className="h-2" />
              {isRunning && (
                <p className="text-sm text-muted-foreground">
                  Current Step: Writing unit tests for validation
                </p>
              )}
            </div>
          )}

          {/* Live View Section */}
          {(isRunning || liveSession) && (
            <div className="rounded-lg border border-live/50 bg-live/5 p-4 space-y-3">
              <div className="flex items-center gap-2">
                <span className="live-dot flex h-2 w-2" aria-hidden="true" />
                <span className="text-sm font-medium text-live">LIVE VIEW AVAILABLE</span>
              </div>

              {liveSession && (
                <>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Eye className="h-4 w-4" />
                    <span>{liveSession.current_viewers} watching</span>
                    <span className="mx-2">•</span>
                    <span>Session started: {format(new Date(liveSession.created_at), "p")}</span>
                  </div>

                  <Button className="w-full" variant="destructive">
                    <Video className="mr-2 h-4 w-4" />
                    Watch Live
                  </Button>
                </>
              )}

              {!liveSession && isRunning && (
                <Button className="w-full" variant="outline">
                  <Video className="mr-2 h-4 w-4" />
                  Start Live Session
                </Button>
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

          {/* Action Buttons */}
          <div className="space-y-2">
            {task?.status === "TODO" && (
              <Button
                className="w-full"
                onClick={() => startExecutionMutation.mutate()}
                disabled={startExecutionMutation.isPending}
                data-testid="start-execution-button"
              >
                <Play className="mr-2 h-4 w-4" />
                {startExecutionMutation.isPending ? "Starting..." : "Start Execution"}
              </Button>
            )}

            {isRunning && (
              <div className="flex gap-2">
                <Button variant="outline" className="flex-1">
                  <Pause className="mr-2 h-4 w-4" />
                  Pause
                </Button>
                <Button variant="destructive" className="flex-1">
                  <Square className="mr-2 h-4 w-4" />
                  Stop
                </Button>
              </div>
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
