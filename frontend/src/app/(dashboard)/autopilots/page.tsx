"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Plus,
  Zap,
  MoreVertical,
  Pencil,
  Trash2,
  Power,
  Play,
  Clock,
} from "lucide-react";
import { autopilotsApi, organizationsApi, AutopilotResponse } from "@/lib/api";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/hooks/use-toast";
import { AutopilotModal } from "@/components/autopilots/autopilot-modal";
import { AutopilotDetailSheet } from "@/components/autopilots/autopilot-detail-sheet";
import { DeleteConfirmDialog } from "@/components/shared/delete-confirm-dialog";
import { describeCron } from "@/components/autopilots/schedule-editor";
import { cn } from "@/lib/utils";

export default function AutopilotsPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAutopilot, setSelectedAutopilot] = useState<AutopilotResponse | null>(null);
  const [toDelete, setToDelete] = useState<AutopilotResponse | null>(null);
  const [detail, setDetail] = useState<AutopilotResponse | null>(null);
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });
  const organizationId = organizations?.content?.[0]?.id;

  const { data: autopilots, isLoading } = useQuery({
    queryKey: ["autopilots", organizationId],
    queryFn: () => autopilotsApi.list(organizationId!),
    enabled: !!organizationId,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => autopilotsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["autopilots"] });
      toast({ title: "Autopilot deleted" });
      setToDelete(null);
    },
    onError: () =>
      toast({
        title: "Error",
        description: "Failed to delete autopilot.",
        variant: "destructive",
      }),
  });

  const toggleMutation = useMutation({
    mutationFn: (id: number) => autopilotsApi.toggle(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["autopilots"] });
      toast({ title: "Autopilot updated" });
    },
    onError: () =>
      toast({
        title: "Error",
        description: "Failed to update autopilot.",
        variant: "destructive",
      }),
  });

  const runMutation = useMutation({
    mutationFn: (id: number) => autopilotsApi.run(id),
    onSuccess: (run) => {
      queryClient.invalidateQueries({ queryKey: ["autopilots"] });
      queryClient.invalidateQueries({ queryKey: ["autopilot-runs"] });
      toast({
        title: "Autopilot triggered",
        description: run ? `Result: ${run.status}` : undefined,
      });
    },
    onError: () =>
      toast({
        title: "Error",
        description: "Failed to trigger autopilot.",
        variant: "destructive",
      }),
  });

  const handleCreate = () => {
    setSelectedAutopilot(null);
    setIsModalOpen(true);
  };

  const handleEdit = (autopilot: AutopilotResponse) => {
    setSelectedAutopilot(autopilot);
    setIsModalOpen(true);
  };

  const handleClose = () => {
    setIsModalOpen(false);
    setSelectedAutopilot(null);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Autopilots</h1>
          <p className="text-muted-foreground">
            Schedule recurring work — standups, audits, reports — that runs itself.
          </p>
        </div>
        <Button onClick={handleCreate} disabled={!organizationId}>
          <Plus className="mr-2 h-4 w-4" />
          New Autopilot
        </Button>
      </div>

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[...Array(3)].map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader className="space-y-2">
                <div className="h-5 bg-muted rounded w-3/4" />
                <div className="h-4 bg-muted rounded w-1/2" />
              </CardHeader>
              <CardContent>
                <div className="h-4 bg-muted rounded w-full" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : autopilots?.content?.length === 0 ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Zap className="h-12 w-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-medium mb-2">No autopilots yet</h3>
            <p className="text-muted-foreground text-center mb-4 max-w-sm">
              Create an autopilot to run recurring tasks on a schedule without lifting a finger.
            </p>
            <Button onClick={handleCreate} disabled={!organizationId}>
              <Plus className="mr-2 h-4 w-4" />
              Create Autopilot
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {autopilots?.content?.map((autopilot) => (
            <AutopilotCard
              key={autopilot.id}
              autopilot={autopilot}
              onEdit={() => handleEdit(autopilot)}
              onDelete={() => setToDelete(autopilot)}
              onToggle={() => toggleMutation.mutate(autopilot.id)}
              onRun={() => runMutation.mutate(autopilot.id)}
              onViewDetails={() => setDetail(autopilot)}
            />
          ))}
        </div>
      )}

      <AutopilotModal
        open={isModalOpen}
        onClose={handleClose}
        autopilot={selectedAutopilot}
        organizationId={organizationId}
      />

      <AutopilotDetailSheet autopilot={detail} onClose={() => setDetail(null)} />

      <DeleteConfirmDialog
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        title="Delete Autopilot"
        description={`Are you sure you want to delete "${toDelete?.name}"? This stops its schedule and removes its run history.`}
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}

interface AutopilotCardProps {
  autopilot: AutopilotResponse;
  onEdit: () => void;
  onDelete: () => void;
  onToggle: () => void;
  onRun: () => void;
  onViewDetails: () => void;
}

function AutopilotCard({
  autopilot,
  onEdit,
  onDelete,
  onToggle,
  onRun,
  onViewDetails,
}: AutopilotCardProps) {
  return (
    <Card
      className={cn(
        "group hover:shadow-md transition-all cursor-pointer",
        !autopilot.enabled && "opacity-60"
      )}
      onClick={onViewDetails}
    >
      <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <CardTitle className="text-lg">{autopilot.name}</CardTitle>
            <Badge variant={autopilot.enabled ? "default" : "secondary"}>
              {autopilot.enabled ? "Enabled" : "Disabled"}
            </Badge>
          </div>
          <CardDescription className="line-clamp-2">
            {autopilot.description || autopilot.task_title}
          </CardDescription>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 opacity-0 group-hover:opacity-100"
            >
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={(e) => { e.stopPropagation(); onRun(); }}>
              <Play className="mr-2 h-4 w-4" />
              Run now
            </DropdownMenuItem>
            <DropdownMenuItem onClick={(e) => { e.stopPropagation(); onEdit(); }}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </DropdownMenuItem>
            <DropdownMenuItem onClick={(e) => { e.stopPropagation(); onToggle(); }}>
              <Power className="mr-2 h-4 w-4" />
              {autopilot.enabled ? "Disable" : "Enable"}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={(e) => { e.stopPropagation(); onDelete(); }}
              className="text-destructive focus:text-destructive"
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </CardHeader>
      <CardContent>
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <div className="flex items-center gap-1.5 min-w-0">
            <Clock className="h-4 w-4 shrink-0" />
            <span className="truncate">{describeCron(autopilot.cron_expression)}</span>
          </div>
          <Badge variant="outline" className="shrink-0">
            {autopilot.execution_mode === "RUN_TASK" ? "Run" : "Create"}
          </Badge>
        </div>
      </CardContent>
    </Card>
  );
}
