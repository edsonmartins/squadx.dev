"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient, useQuery } from "@tanstack/react-query";
import { projectsApi, squadsApi, ProjectResponse } from "@/lib/api";
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

const projectSchema = z.object({
  name: z.string().min(1, "Name is required").max(100),
  description: z.string().max(500).optional(),
  repository_url: z.string().url("Must be a valid URL").optional().or(z.literal("")),
  default_branch: z.string().max(50).optional(),
  squad_id: z.number().optional().nullable(),
});

type ProjectFormData = z.infer<typeof projectSchema>;

interface ProjectModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
  project?: ProjectResponse | null;
  organizationId?: number;
}

export function ProjectModal({ open, onOpenChange, onSuccess, project, organizationId }: ProjectModalProps) {
  const onClose = () => onOpenChange(false);
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const isEditing = !!project;

  // Fetch squads for the organization
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
  } = useForm<ProjectFormData>({
    resolver: zodResolver(projectSchema),
    defaultValues: {
      name: "",
      description: "",
      repository_url: "",
      default_branch: "main",
      squad_id: null,
    },
  });

  const squadId = watch("squad_id");

  // Reset form when modal opens/closes or project changes
  useEffect(() => {
    if (open) {
      if (project) {
        reset({
          name: project.name,
          description: project.description || "",
          repository_url: project.repository_url || "",
          default_branch: project.default_branch || "main",
          squad_id: project.squad_id || null,
        });
      } else {
        reset({
          name: "",
          description: "",
          repository_url: "",
          default_branch: "main",
          squad_id: null,
        });
      }
    }
  }, [open, project, reset]);

  const createMutation = useMutation({
    mutationFn: (data: ProjectFormData) =>
      projectsApi.create({
        ...data,
        organization_id: organizationId!,
        repository_url: data.repository_url || undefined,
        squad_id: data.squad_id || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      toast({
        title: "Project created",
        description: "Your project has been created successfully.",
      });
      onClose();
      onSuccess?.();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to create project. Please try again.",
        variant: "destructive",
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: ProjectFormData) =>
      projectsApi.update(project!.id, {
        ...data,
        repository_url: data.repository_url || undefined,
        squad_id: data.squad_id || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      toast({
        title: "Project updated",
        description: "Your project has been updated successfully.",
      });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to update project. Please try again.",
        variant: "destructive",
      });
    },
  });

  const onSubmit = (data: ProjectFormData) => {
    if (isEditing) {
      updateMutation.mutate(data);
    } else {
      createMutation.mutate(data);
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>{isEditing ? "Edit Project" : "Create Project"}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update your project details below."
              : "Create a new project to organize your tasks."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="name">Name *</Label>
              <Input
                id="name"
                placeholder="My Awesome Project"
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
                placeholder="A brief description of your project..."
                rows={3}
                {...register("description")}
              />
              {errors.description && (
                <p className="text-sm text-destructive">{errors.description.message}</p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="repository_url">Repository URL</Label>
              <Input
                id="repository_url"
                placeholder="https://github.com/org/repo"
                {...register("repository_url")}
              />
              {errors.repository_url && (
                <p className="text-sm text-destructive">{errors.repository_url.message}</p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="default_branch">Default Branch</Label>
              <Input
                id="default_branch"
                placeholder="main"
                {...register("default_branch")}
              />
              {errors.default_branch && (
                <p className="text-sm text-destructive">{errors.default_branch.message}</p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="squad">Assigned Squad</Label>
              <Select
                value={squadId?.toString() || "none"}
                onValueChange={(value) =>
                  setValue("squad_id", value === "none" ? null : parseInt(value))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select a squad (optional)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">No squad assigned</SelectItem>
                  {squads?.content?.map((squad) => (
                    <SelectItem key={squad.id} value={squad.id.toString()}>
                      {squad.name} ({squad.active_agents_count} agents)
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? "Saving..." : isEditing ? "Save Changes" : "Create Project"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
