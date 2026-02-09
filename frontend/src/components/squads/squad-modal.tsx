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
import { useToast } from "@/hooks/use-toast";

const squadSchema = z.object({
  name: z.string().min(1, "Name is required").max(100),
  description: z.string().max(500).optional(),
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
    formState: { errors },
  } = useForm<SquadFormData>({
    resolver: zodResolver(squadSchema),
    defaultValues: {
      name: "",
      description: "",
    },
  });

  // Reset form when modal opens/closes or squad changes
  useEffect(() => {
    if (open) {
      if (squad) {
        reset({
          name: squad.name,
          description: squad.description || "",
        });
      } else {
        reset({
          name: "",
          description: "",
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
