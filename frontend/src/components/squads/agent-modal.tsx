"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { agentsApi, AgentResponse, AgentType } from "@/lib/api";
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

const agentSchema = z.object({
  name: z.string().min(1, "Name is required").max(100),
  type: z.enum(["FRONTEND", "BACKEND", "FULLSTACK", "DEVOPS", "QA", "COORDINATOR", "DATABASE"]),
  description: z.string().max(500).optional(),
  model: z.string().min(1, "Model is required"),
  system_prompt: z.string().max(4000).optional(),
  temperature: z.number().min(0).max(2),
  max_tokens: z.number().min(100).max(128000),
});

type AgentFormData = z.infer<typeof agentSchema>;

const agentTypes: { value: AgentType; label: string; description: string }[] = [
  { value: "COORDINATOR", label: "Coordinator", description: "Orchestrates tasks and delegates to specialists" },
  { value: "FRONTEND", label: "Frontend Specialist", description: "React, Vue, CSS, responsive design" },
  { value: "BACKEND", label: "Backend Specialist", description: "APIs, databases, server-side logic" },
  { value: "FULLSTACK", label: "Fullstack Developer", description: "End-to-end development" },
  { value: "DEVOPS", label: "DevOps Engineer", description: "CI/CD, infrastructure, deployment" },
  { value: "QA", label: "QA Engineer", description: "Testing, quality assurance" },
  { value: "DATABASE", label: "Database Specialist", description: "Database design, optimization, migrations" },
];

const modelOptions = [
  { value: "gpt-4o", label: "GPT-4o (OpenAI)" },
  { value: "gpt-4o-mini", label: "GPT-4o Mini (OpenAI)" },
  { value: "claude-3-5-sonnet-20241022", label: "Claude 3.5 Sonnet (Anthropic)" },
  { value: "claude-3-5-haiku-20241022", label: "Claude 3.5 Haiku (Anthropic)" },
  { value: "claude-opus-4-5-20251101", label: "Claude Opus 4.5 (Anthropic)" },
];

interface AgentModalProps {
  open: boolean;
  onClose: () => void;
  agent?: AgentResponse | null;
  squadId?: number;
}

export function AgentModal({ open, onClose, agent, squadId }: AgentModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const isEditing = !!agent;

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<AgentFormData>({
    resolver: zodResolver(agentSchema),
    defaultValues: {
      name: "",
      type: "FULLSTACK",
      description: "",
      model: "claude-3-5-sonnet-20241022",
      system_prompt: "",
      temperature: 0.7,
      max_tokens: 4096,
    },
  });

  const agentType = watch("type");
  const model = watch("model");

  // Reset form when modal opens/closes or agent changes
  useEffect(() => {
    if (open) {
      if (agent) {
        reset({
          name: agent.name,
          type: agent.type,
          description: agent.description || "",
          model: agent.model,
          system_prompt: agent.system_prompt || "",
          temperature: agent.temperature,
          max_tokens: agent.max_tokens,
        });
      } else {
        reset({
          name: "",
          type: "FULLSTACK",
          description: "",
          model: "claude-3-5-sonnet-20241022",
          system_prompt: "",
          temperature: 0.7,
          max_tokens: 4096,
        });
      }
    }
  }, [open, agent, reset]);

  const createMutation = useMutation({
    mutationFn: (data: AgentFormData) =>
      agentsApi.create({
        ...data,
        squad_id: squadId!,
        system_prompt: data.system_prompt || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["agents", squadId] });
      queryClient.invalidateQueries({ queryKey: ["squads"] });
      toast({
        title: "Agent created",
        description: "Your AI agent has been created successfully.",
      });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to create agent. Please try again.",
        variant: "destructive",
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: AgentFormData) =>
      agentsApi.update(agent!.id, {
        name: data.name,
        description: data.description,
        model: data.model,
        system_prompt: data.system_prompt,
        temperature: data.temperature,
        max_tokens: data.max_tokens,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["agents", squadId] });
      queryClient.invalidateQueries({ queryKey: ["squads"] });
      toast({
        title: "Agent updated",
        description: "Your agent has been updated successfully.",
      });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to update agent. Please try again.",
        variant: "destructive",
      });
    },
  });

  const onSubmit = (data: AgentFormData) => {
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
          <DialogTitle>{isEditing ? "Edit Agent" : "Add Agent"}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update your AI agent configuration."
              : "Configure a new AI agent for your squad."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="name">Name *</Label>
                <Input
                  id="name"
                  placeholder="Backend Agent"
                  {...register("name")}
                />
                {errors.name && (
                  <p className="text-sm text-destructive">{errors.name.message}</p>
                )}
              </div>

              <div className="grid gap-2">
                <Label htmlFor="type">Type *</Label>
                <Select
                  value={agentType}
                  onValueChange={(value) => setValue("type", value as AgentType)}
                  disabled={isEditing}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select type" />
                  </SelectTrigger>
                  <SelectContent>
                    {agentTypes.map((type) => (
                      <SelectItem key={type.value} value={type.value}>
                        <div className="flex flex-col">
                          <span>{type.label}</span>
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  {agentTypes.find((t) => t.value === agentType)?.description}
                </p>
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                placeholder="Specialized in Node.js and Python backend development..."
                rows={2}
                {...register("description")}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="model">Model *</Label>
                <Select
                  value={model}
                  onValueChange={(value) => setValue("model", value)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select model" />
                  </SelectTrigger>
                  <SelectContent>
                    {modelOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="temperature">Temperature</Label>
                <Input
                  id="temperature"
                  type="number"
                  step="0.1"
                  min="0"
                  max="2"
                  {...register("temperature", { valueAsNumber: true })}
                />
                <p className="text-xs text-muted-foreground">
                  Higher = more creative, Lower = more focused
                </p>
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="max_tokens">Max Tokens</Label>
              <Input
                id="max_tokens"
                type="number"
                min="100"
                max="128000"
                {...register("max_tokens", { valueAsNumber: true })}
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="system_prompt">System Prompt (Optional)</Label>
              <Textarea
                id="system_prompt"
                placeholder="You are an expert backend developer specializing in..."
                rows={4}
                {...register("system_prompt")}
              />
              <p className="text-xs text-muted-foreground">
                Custom instructions for the agent. Leave empty to use default prompts.
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? "Saving..." : isEditing ? "Save Changes" : "Add Agent"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
