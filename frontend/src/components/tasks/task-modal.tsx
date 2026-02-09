"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient, useQuery } from "@tanstack/react-query";
import {
  tasksApi,
  agentsApi,
  TaskResponse,
  TaskStatus,
  TaskPriority,
} from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useTaskStore } from "@/stores/task-store";

const taskSchema = z.object({
  title: z.string().min(1, "Title is required").max(200),
  description: z.string().max(2000).optional(),
  status: z.enum(["TODO", "IN_PROGRESS", "IN_REVIEW", "BLOCKED", "DONE", "CANCELLED"]),
  priority: z.enum(["LOW", "MEDIUM", "HIGH", "URGENT"]),
  story_points: z.number().min(0).max(100).optional().nullable(),
  estimated_hours: z.number().min(0).max(1000).optional().nullable(),
  due_date: z.string().optional().nullable(),
  assigned_agent_id: z.number().optional().nullable(),
  tags: z.string().optional(),
});

type TaskFormData = z.infer<typeof taskSchema>;

const statusOptions: { value: TaskStatus; label: string }[] = [
  { value: "TODO", label: "To Do" },
  { value: "IN_PROGRESS", label: "In Progress" },
  { value: "IN_REVIEW", label: "In Review" },
  { value: "BLOCKED", label: "Blocked" },
  { value: "DONE", label: "Done" },
  { value: "CANCELLED", label: "Cancelled" },
];

const priorityOptions: { value: TaskPriority; label: string; color: string }[] = [
  { value: "LOW", label: "Low", color: "text-slate-500" },
  { value: "MEDIUM", label: "Medium", color: "text-blue-500" },
  { value: "HIGH", label: "High", color: "text-orange-500" },
  { value: "URGENT", label: "Urgent", color: "text-red-500" },
];

interface TaskModalProps {
  open: boolean;
  onClose: () => void;
  task?: TaskResponse | null;
  projectId: number;
  organizationId?: number;
  defaultStatus?: TaskStatus;
}

export function TaskModal({
  open,
  onClose,
  task,
  projectId,
  organizationId,
  defaultStatus = "TODO",
}: TaskModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { createTask, updateTask } = useTaskStore();
  const isEditing = !!task;

  // Fetch agents for the organization
  const { data: agents } = useQuery({
    queryKey: ["agents-org", organizationId],
    queryFn: () => agentsApi.listByOrganization(organizationId!),
    enabled: !!organizationId,
  });

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<TaskFormData>({
    resolver: zodResolver(taskSchema),
    defaultValues: {
      title: "",
      description: "",
      status: defaultStatus,
      priority: "MEDIUM",
      story_points: null,
      estimated_hours: null,
      due_date: null,
      assigned_agent_id: null,
      tags: "",
    },
  });

  const status = watch("status");
  const priority = watch("priority");
  const assignedAgentId = watch("assigned_agent_id");

  // Reset form when modal opens/closes or task changes
  useEffect(() => {
    if (open) {
      if (task) {
        reset({
          title: task.title,
          description: task.description || "",
          status: task.status,
          priority: task.priority,
          story_points: task.story_points || null,
          estimated_hours: task.estimated_hours || null,
          due_date: task.due_date?.split("T")[0] || null,
          assigned_agent_id: task.assigned_agent_id || null,
          tags: task.tags?.join(", ") || "",
        });
      } else {
        reset({
          title: "",
          description: "",
          status: defaultStatus,
          priority: "MEDIUM",
          story_points: null,
          estimated_hours: null,
          due_date: null,
          assigned_agent_id: null,
          tags: "",
        });
      }
    }
  }, [open, task, defaultStatus, reset]);

  const createMutation = useMutation({
    mutationFn: async (data: TaskFormData) => {
      const tags = data.tags
        ? data.tags.split(",").map((t) => t.trim()).filter(Boolean)
        : undefined;

      return createTask({
        title: data.title,
        description: data.description || undefined,
        status: data.status,
        priority: data.priority,
        story_points: data.story_points || undefined,
        estimated_hours: data.estimated_hours || undefined,
        due_date: data.due_date || undefined,
        assigned_agent_id: data.assigned_agent_id || undefined,
        project_id: projectId,
        tags,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks", projectId] });
      toast({
        title: "Task created",
        description: "Your task has been created successfully.",
      });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to create task. Please try again.",
        variant: "destructive",
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: async (data: TaskFormData) => {
      const tags = data.tags
        ? data.tags.split(",").map((t) => t.trim()).filter(Boolean)
        : undefined;

      return updateTask(task!.id, {
        title: data.title,
        description: data.description || undefined,
        status: data.status,
        priority: data.priority,
        story_points: data.story_points || undefined,
        estimated_hours: data.estimated_hours || undefined,
        due_date: data.due_date || undefined,
        assigned_agent_id: data.assigned_agent_id || undefined,
        tags,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks", projectId] });
      toast({
        title: "Task updated",
        description: "Your task has been updated successfully.",
      });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to update task. Please try again.",
        variant: "destructive",
      });
    },
  });

  const onSubmit = (data: TaskFormData) => {
    if (isEditing) {
      updateMutation.mutate(data);
    } else {
      createMutation.mutate(data);
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[600px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEditing ? "Edit Task" : "Create Task"}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update your task details."
              : "Create a new task for your project."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="title">Title *</Label>
              <Input
                id="title"
                placeholder="Implement user authentication"
                {...register("title")}
              />
              {errors.title && (
                <p className="text-sm text-destructive">{errors.title.message}</p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                placeholder="Add detailed description of the task..."
                rows={4}
                {...register("description")}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="status">Status</Label>
                <Select
                  value={status}
                  onValueChange={(value) => setValue("status", value as TaskStatus)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select status" />
                  </SelectTrigger>
                  <SelectContent>
                    {statusOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="priority">Priority</Label>
                <Select
                  value={priority}
                  onValueChange={(value) => setValue("priority", value as TaskPriority)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select priority" />
                  </SelectTrigger>
                  <SelectContent>
                    {priorityOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        <span className={option.color}>{option.label}</span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="story_points">Story Points</Label>
                <Input
                  id="story_points"
                  type="number"
                  min="0"
                  max="100"
                  placeholder="5"
                  {...register("story_points", {
                    setValueAs: (v) => (v === "" ? null : parseInt(v, 10)),
                  })}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="estimated_hours">Est. Hours</Label>
                <Input
                  id="estimated_hours"
                  type="number"
                  min="0"
                  step="0.5"
                  placeholder="8"
                  {...register("estimated_hours", {
                    setValueAs: (v) => (v === "" ? null : parseFloat(v)),
                  })}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="due_date">Due Date</Label>
                <Input
                  id="due_date"
                  type="date"
                  {...register("due_date")}
                />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="assigned_agent">Assigned Agent</Label>
              <Select
                value={assignedAgentId?.toString() || "none"}
                onValueChange={(value) =>
                  setValue("assigned_agent_id", value === "none" ? null : parseInt(value))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select an agent (optional)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">No agent assigned</SelectItem>
                  {agents?.content?.map((agent) => (
                    <SelectItem key={agent.id} value={agent.id.toString()}>
                      {agent.name} ({agent.type})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="tags">Tags</Label>
              <Input
                id="tags"
                placeholder="frontend, auth, urgent (comma separated)"
                {...register("tags")}
              />
              <p className="text-xs text-muted-foreground">
                Separate tags with commas
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? "Saving..." : isEditing ? "Save Changes" : "Create Task"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
