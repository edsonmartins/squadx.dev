"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  autopilotsApi,
  projectsApi,
  agentsApi,
  squadsApi,
  AutopilotResponse,
  AutopilotExecutionMode,
  TaskPriority,
} from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
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
import { ScheduleEditor } from "@/components/autopilots/schedule-editor";

const autopilotSchema = z.object({
  name: z.string().min(1, "Name is required").max(120),
  description: z.string().max(500).optional(),
  cron_expression: z.string().min(1, "Schedule is required"),
  project_id: z.number({ required_error: "Project is required" }),
  execution_mode: z.enum(["CREATE_TASK", "RUN_TASK"]),
  target_squad_id: z.number().nullable().optional(),
  target_agent_id: z.number().nullable().optional(),
  task_title: z.string().min(1, "Task title is required").max(200),
  task_description: z.string().max(2000).optional(),
  task_priority: z.enum(["LOW", "MEDIUM", "HIGH", "URGENT"]),
  enabled: z.boolean(),
});

type AutopilotFormData = z.infer<typeof autopilotSchema>;

const priorityOptions: { value: TaskPriority; label: string }[] = [
  { value: "LOW", label: "Low" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
  { value: "URGENT", label: "Urgent" },
];

interface AutopilotModalProps {
  open: boolean;
  onClose: () => void;
  autopilot?: AutopilotResponse | null;
  organizationId?: number;
}

export function AutopilotModal({
  open,
  onClose,
  autopilot,
  organizationId,
}: AutopilotModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const isEditing = !!autopilot;

  const { data: projects } = useQuery({
    queryKey: ["projects", organizationId],
    queryFn: () => projectsApi.list(organizationId!),
    enabled: !!organizationId,
  });

  const { data: agents } = useQuery({
    queryKey: ["agents-org", organizationId],
    queryFn: () => agentsApi.listByOrganization(organizationId!),
    enabled: !!organizationId,
  });

  const { data: squads } = useQuery({
    queryKey: ["squads", organizationId],
    queryFn: () => squadsApi.list(organizationId!),
    enabled: !!organizationId,
  });

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<AutopilotFormData>({
    resolver: zodResolver(autopilotSchema),
    defaultValues: {
      name: "",
      description: "",
      cron_expression: "0 9 * * 1-5",
      project_id: undefined as unknown as number,
      execution_mode: "CREATE_TASK",
      target_squad_id: null,
      target_agent_id: null,
      task_title: "",
      task_description: "",
      task_priority: "MEDIUM",
      enabled: true,
    },
  });

  const cron = watch("cron_expression");
  const projectId = watch("project_id");
  const executionMode = watch("execution_mode");
  const squadId = watch("target_squad_id");
  const agentId = watch("target_agent_id");
  const priority = watch("task_priority");
  const enabled = watch("enabled");

  useEffect(() => {
    if (!open) return;
    if (autopilot) {
      reset({
        name: autopilot.name,
        description: autopilot.description || "",
        cron_expression: autopilot.cron_expression,
        project_id: autopilot.project_id,
        execution_mode: autopilot.execution_mode,
        target_squad_id: autopilot.target_squad_id ?? null,
        target_agent_id: autopilot.target_agent_id ?? null,
        task_title: autopilot.task_title,
        task_description: autopilot.task_description || "",
        task_priority: autopilot.task_priority,
        enabled: autopilot.enabled,
      });
    } else {
      reset({
        name: "",
        description: "",
        cron_expression: "0 9 * * 1-5",
        project_id: projects?.content?.[0]?.id as unknown as number,
        execution_mode: "CREATE_TASK",
        target_squad_id: null,
        target_agent_id: null,
        task_title: "",
        task_description: "",
        task_priority: "MEDIUM",
        enabled: true,
      });
    }
  }, [open, autopilot, projects, reset]);

  const createMutation = useMutation({
    mutationFn: (data: AutopilotFormData) =>
      autopilotsApi.create({
        name: data.name,
        description: data.description || undefined,
        cron_expression: data.cron_expression,
        execution_mode: data.execution_mode,
        project_id: data.project_id,
        target_squad_id: data.target_squad_id ?? undefined,
        target_agent_id: data.target_agent_id ?? undefined,
        task_title: data.task_title,
        task_description: data.task_description || undefined,
        task_priority: data.task_priority,
        enabled: data.enabled,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["autopilots"] });
      toast({ title: "Autopilot created", description: "Your autopilot is scheduled." });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to create autopilot. Please try again.",
        variant: "destructive",
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: AutopilotFormData) =>
      autopilotsApi.update(autopilot!.id, {
        name: data.name,
        description: data.description || undefined,
        cron_expression: data.cron_expression,
        execution_mode: data.execution_mode,
        target_squad_id: data.target_squad_id ?? null,
        target_agent_id: data.target_agent_id ?? null,
        task_title: data.task_title,
        task_description: data.task_description || undefined,
        task_priority: data.task_priority,
        enabled: data.enabled,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["autopilots"] });
      toast({ title: "Autopilot updated", description: "Your changes have been saved." });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to update autopilot. Please try again.",
        variant: "destructive",
      });
    },
  });

  const onSubmit = (data: AutopilotFormData) => {
    if (isEditing) {
      updateMutation.mutate(data);
    } else {
      createMutation.mutate(data);
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent
        className="sm:max-w-[600px] max-h-[90vh] overflow-y-auto"
        data-testid="autopilot-modal"
      >
        <DialogHeader>
          <DialogTitle>{isEditing ? "Edit Autopilot" : "Create Autopilot"}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update your scheduled automation."
              : "Schedule recurring work for your squad."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="name">Name *</Label>
              <Input
                id="name"
                placeholder="Daily standup report"
                {...register("name")}
              />
              {errors.name && (
                <p className="text-sm text-destructive">{errors.name.message}</p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                rows={2}
                placeholder="What does this autopilot do?"
                {...register("description")}
              />
            </div>

            <ScheduleEditor
              value={cron || ""}
              onChange={(v) =>
                setValue("cron_expression", v, { shouldValidate: true })
              }
            />
            {errors.cron_expression && (
              <p className="text-sm text-destructive">
                {errors.cron_expression.message}
              </p>
            )}

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="project">Project *</Label>
                <Select
                  value={projectId ? projectId.toString() : ""}
                  onValueChange={(v) =>
                    setValue("project_id", parseInt(v), { shouldValidate: true })
                  }
                >
                  <SelectTrigger id="project">
                    <SelectValue placeholder="Select a project" />
                  </SelectTrigger>
                  <SelectContent>
                    {projects?.content?.map((p) => (
                      <SelectItem key={p.id} value={p.id.toString()}>
                        {p.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.project_id && (
                  <p className="text-sm text-destructive">Project is required</p>
                )}
              </div>

              <div className="grid gap-2">
                <Label htmlFor="mode">Mode</Label>
                <Select
                  value={executionMode}
                  onValueChange={(v) =>
                    setValue("execution_mode", v as AutopilotExecutionMode)
                  }
                >
                  <SelectTrigger id="mode">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CREATE_TASK">Create task only</SelectItem>
                    <SelectItem value="RUN_TASK">Create &amp; run task</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="agent">Target agent</Label>
                <Select
                  value={agentId ? agentId.toString() : "none"}
                  onValueChange={(v) =>
                    setValue("target_agent_id", v === "none" ? null : parseInt(v))
                  }
                >
                  <SelectTrigger id="agent">
                    <SelectValue placeholder="Optional" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">No specific agent</SelectItem>
                    {agents?.content?.map((a) => (
                      <SelectItem key={a.id} value={a.id.toString()}>
                        {a.name} ({a.type})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="squad">Target squad</Label>
                <Select
                  value={squadId ? squadId.toString() : "none"}
                  onValueChange={(v) =>
                    setValue("target_squad_id", v === "none" ? null : parseInt(v))
                  }
                >
                  <SelectTrigger id="squad">
                    <SelectValue placeholder="Optional" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">No specific squad</SelectItem>
                    {squads?.content?.map((s) => (
                      <SelectItem key={s.id} value={s.id.toString()}>
                        {s.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {executionMode === "RUN_TASK" && (
              <p className="text-xs text-muted-foreground -mt-2">
                Run mode dispatches to the agent immediately. If no agent is online
                when the autopilot fires, the run is skipped.
              </p>
            )}

            <div className="border-t pt-4 grid gap-4">
              <div className="grid gap-2">
                <Label htmlFor="task_title">Task title *</Label>
                <Input
                  id="task_title"
                  placeholder="Generate the daily standup summary"
                  {...register("task_title")}
                />
                {errors.task_title && (
                  <p className="text-sm text-destructive">
                    {errors.task_title.message}
                  </p>
                )}
              </div>

              <div className="grid gap-2">
                <Label htmlFor="task_description">Task description</Label>
                <Textarea
                  id="task_description"
                  rows={3}
                  placeholder="Instructions for the agent..."
                  {...register("task_description")}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="task_priority">Task priority</Label>
                <Select
                  value={priority}
                  onValueChange={(v) => setValue("task_priority", v as TaskPriority)}
                >
                  <SelectTrigger id="task_priority">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {priorityOptions.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="flex items-center justify-between border-t pt-4">
              <div>
                <Label htmlFor="enabled">Enabled</Label>
                <p className="text-xs text-muted-foreground">
                  Disabled autopilots keep their config but never fire.
                </p>
              </div>
              <Switch
                id="enabled"
                checked={enabled}
                onCheckedChange={(v) => setValue("enabled", v)}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading} data-testid="autopilot-submit-button">
              {isLoading ? "Saving..." : isEditing ? "Save Changes" : "Create Autopilot"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
