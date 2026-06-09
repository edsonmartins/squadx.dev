"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { squadsApi, SquadResponse } from "@/lib/api";
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

const squadSchema = z.object({
  name: z.string().min(1, "Name is required").max(100),
  description: z.string().max(500).optional(),
  leader_agent_id: z.number().nullable().optional(),
});

type SquadFormData = z.infer<typeof squadSchema>;

interface SquadModalProps {
  open: boolean;
  onClose: () => void;
  squad?: SquadResponse | null;
  organizationId?: number;
}

export function SquadModal({ open, onClose, squad, organizationId }: SquadModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const isEditing = !!squad;

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<SquadFormData>({
    resolver: zodResolver(squadSchema),
    defaultValues: {
      name: "",
      description: "",
      leader_agent_id: null,
    },
  });

  const leaderAgentId = watch("leader_agent_id");
  const squadAgents = squad?.agents ?? [];

  // Reset form when modal opens/closes or squad changes
  useEffect(() => {
    if (open) {
      if (squad) {
        reset({
          name: squad.name,
          description: squad.description || "",
          leader_agent_id: squad.leader_agent_id ?? null,
        });
      } else {
        reset({
          name: "",
          description: "",
          leader_agent_id: null,
        });
      }
    }
  }, [open, squad, reset]);

  const createMutation = useMutation({
    mutationFn: (data: SquadFormData) =>
      squadsApi.create({
        ...data,
        organization_id: organizationId!,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["squads"] });
      queryClient.invalidateQueries({ queryKey: ["organizations"] });
      toast({
        title: "Squad created",
        description: "Your AI squad has been created successfully.",
      });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to create squad. Please try again.",
        variant: "destructive",
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: SquadFormData) =>
      squadsApi.update(squad!.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["squads"] });
      queryClient.invalidateQueries({ queryKey: ["organizations"] });
      toast({
        title: "Squad updated",
        description: "Your squad has been updated successfully.",
      });
      onClose();
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to update squad. Please try again.",
        variant: "destructive",
      });
    },
  });

  const onSubmit = (data: SquadFormData) => {
    if (isEditing) {
      updateMutation.mutate(data);
    } else {
      createMutation.mutate(data);
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>{isEditing ? "Edit Squad" : "Create Squad"}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update your AI squad details."
              : "Create a new AI development squad to automate your tasks."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="name">Name *</Label>
              <Input
                id="name"
                placeholder="Backend Squad"
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
                placeholder="A squad specialized in backend development with Node.js and Python..."
                rows={3}
                {...register("description")}
              />
              {errors.description && (
                <p className="text-sm text-destructive">{errors.description.message}</p>
              )}
            </div>

            {isEditing && squadAgents.length > 0 && (
              <div className="grid gap-2">
                <Label htmlFor="leader">Leader agent</Label>
                <Select
                  value={leaderAgentId ? leaderAgentId.toString() : "none"}
                  onValueChange={(value) =>
                    setValue("leader_agent_id", value === "none" ? null : parseInt(value))
                  }
                >
                  <SelectTrigger id="leader">
                    <SelectValue placeholder="No leader" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">No leader</SelectItem>
                    {squadAgents.map((agent) => (
                      <SelectItem key={agent.id} value={agent.id.toString()}>
                        {agent.name} ({agent.agent_type})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  Work routed to this squad prefers the leader (when online).
                </p>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? "Saving..." : isEditing ? "Save Changes" : "Create Squad"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
