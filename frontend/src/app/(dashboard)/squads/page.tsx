"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Users, Bot, MoreVertical, Pencil, Trash2, Power, ChevronRight } from "lucide-react";
import { squadsApi, organizationsApi, SquadResponse } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/hooks/use-toast";
import { SquadModal } from "@/components/squads/squad-modal";
import { SquadDetailSheet } from "@/components/squads/squad-detail-sheet";
import { DeleteConfirmDialog } from "@/components/shared/delete-confirm-dialog";
import { cn } from "@/lib/utils";

export default function SquadsPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedSquad, setSelectedSquad] = useState<SquadResponse | null>(null);
  const [squadToDelete, setSquadToDelete] = useState<SquadResponse | null>(null);
  const [detailSquad, setDetailSquad] = useState<SquadResponse | null>(null);
  const queryClient = useQueryClient();
  const { toast } = useToast();

  // Fetch organizations from API (same pattern as projects page)
  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  const organizationId = organizations?.content?.[0]?.id;

  // Fetch squads
  const { data: squads, isLoading } = useQuery({
    queryKey: ["squads", organizationId],
    queryFn: () => squadsApi.list(organizationId!),
    enabled: !!organizationId,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => squadsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["squads"] });
      queryClient.invalidateQueries({ queryKey: ["organizations"] });
      toast({
        title: "Squad deleted",
        description: "The squad has been deleted successfully.",
      });
      setSquadToDelete(null);
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to delete squad. Please try again.",
        variant: "destructive",
      });
    },
  });

  // Toggle active mutation
  const toggleActiveMutation = useMutation({
    mutationFn: (id: number) => squadsApi.toggleActive(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["squads"] });
      queryClient.invalidateQueries({ queryKey: ["organizations"] });
      toast({
        title: "Squad updated",
        description: "The squad status has been updated.",
      });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to update squad status.",
        variant: "destructive",
      });
    },
  });

  const handleEdit = (squad: SquadResponse) => {
    setSelectedSquad(squad);
    setIsModalOpen(true);
  };

  const handleCreate = () => {
    setSelectedSquad(null);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedSquad(null);
  };

  const handleDelete = (squad: SquadResponse) => {
    setSquadToDelete(squad);
  };

  const confirmDelete = () => {
    if (squadToDelete) {
      deleteMutation.mutate(squadToDelete.id);
    }
  };

  const handleViewDetails = (squad: SquadResponse) => {
    setDetailSquad(squad);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Squads</h1>
          <p className="text-muted-foreground">
            Manage your AI development squads and their agents.
          </p>
        </div>
        <Button onClick={handleCreate}>
          <Plus className="mr-2 h-4 w-4" />
          New Squad
        </Button>
      </div>

      {/* Squads Grid */}
      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[...Array(6)].map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader className="space-y-2">
                <div className="h-5 bg-muted rounded w-3/4" />
                <div className="h-4 bg-muted rounded w-1/2" />
              </CardHeader>
              <CardContent>
                <div className="h-4 bg-muted rounded w-full mb-2" />
                <div className="h-4 bg-muted rounded w-2/3" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : squads?.content?.length === 0 ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Users className="h-12 w-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-medium mb-2">No squads yet</h3>
            <p className="text-muted-foreground text-center mb-4 max-w-sm">
              Create your first AI squad to start automating your development tasks.
            </p>
            <Button onClick={handleCreate}>
              <Plus className="mr-2 h-4 w-4" />
              Create Squad
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {squads?.content?.map((squad) => (
            <SquadCard
              key={squad.id}
              squad={squad}
              onEdit={() => handleEdit(squad)}
              onDelete={() => handleDelete(squad)}
              onToggleActive={() => toggleActiveMutation.mutate(squad.id)}
              onViewDetails={() => handleViewDetails(squad)}
            />
          ))}
        </div>
      )}

      {/* Create/Edit Modal */}
      <SquadModal
        open={isModalOpen}
        onClose={handleCloseModal}
        squad={selectedSquad}
        organizationId={organizationId}
      />

      {/* Squad Detail Sheet */}
      <SquadDetailSheet
        squad={detailSquad}
        onClose={() => setDetailSquad(null)}
        organizationId={organizationId}
      />

      {/* Delete Confirmation */}
      <DeleteConfirmDialog
        open={!!squadToDelete}
        onClose={() => setSquadToDelete(null)}
        onConfirm={confirmDelete}
        title="Delete Squad"
        description={`Are you sure you want to delete "${squadToDelete?.name}"? This action cannot be undone and will delete all associated agents.`}
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}

interface SquadCardProps {
  squad: SquadResponse;
  onEdit: () => void;
  onDelete: () => void;
  onToggleActive: () => void;
  onViewDetails: () => void;
}

function SquadCard({ squad, onEdit, onDelete, onToggleActive, onViewDetails }: SquadCardProps) {
  return (
    <Card
      className={cn(
        "group hover:shadow-md transition-all cursor-pointer",
        !squad.is_active && "opacity-60"
      )}
      onClick={onViewDetails}
    >
      <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <CardTitle className="text-lg">{squad.name}</CardTitle>
            <Badge variant={squad.is_active ? "default" : "secondary"}>
              {squad.is_active ? "Active" : "Inactive"}
            </Badge>
          </div>
          <CardDescription className="line-clamp-2">
            {squad.description || "No description"}
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
            <DropdownMenuItem onClick={(e) => { e.stopPropagation(); onEdit(); }}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </DropdownMenuItem>
            <DropdownMenuItem onClick={(e) => { e.stopPropagation(); onToggleActive(); }}>
              <Power className="mr-2 h-4 w-4" />
              {squad.is_active ? "Deactivate" : "Activate"}
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
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4 text-sm text-muted-foreground">
            <div className="flex items-center gap-1">
              <Bot className="h-4 w-4" />
              <span>{squad.agents_count} agents</span>
            </div>
            <div className="flex items-center gap-1 text-ok">
              <span className="h-2 w-2 rounded-full bg-ok" />
              <span>{squad.active_agents_count} active</span>
            </div>
          </div>
          <ChevronRight className="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
        </div>
      </CardContent>
    </Card>
  );
}
