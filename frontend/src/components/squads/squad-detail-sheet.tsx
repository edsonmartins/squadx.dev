"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Bot, Power, Pencil, Trash2, MoreVertical, Cpu, Brain, MessageSquare } from "lucide-react";
import { agentsApi, SquadResponse, AgentResponse, AgentType, liveViewApi } from "@/lib/api";
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
import { useToast } from "@/hooks/use-toast";
import { AgentModal } from "./agent-modal";
import { DeleteConfirmDialog } from "@/components/shared/delete-confirm-dialog";
import { cn } from "@/lib/utils";

const agentTypeIcons: Record<AgentType, React.ReactNode> = {
  FRONTEND: <span className="text-info">FE</span>,
  BACKEND: <span className="text-ok">BE</span>,
  FULLSTACK: <span className="text-info">FS</span>,
  DEVOPS: <span className="text-warn">DO</span>,
  QA: <span className="text-warn">QA</span>,
  COORDINATOR: <span className="text-danger">CO</span>,
  DATABASE: <span className="text-info">DB</span>,
};

const agentTypeLabels: Record<AgentType, string> = {
  FRONTEND: "Frontend Specialist",
  BACKEND: "Backend Specialist",
  FULLSTACK: "Fullstack Developer",
  DEVOPS: "DevOps Engineer",
  QA: "QA Engineer",
  COORDINATOR: "Coordinator",
  DATABASE: "Database Specialist",
};

interface SquadDetailSheetProps {
  squad: SquadResponse | null;
  onClose: () => void;
  organizationId?: number;
}

export function SquadDetailSheet({ squad, onClose, organizationId }: SquadDetailSheetProps) {
  const [isAgentModalOpen, setIsAgentModalOpen] = useState(false);
  const [selectedAgent, setSelectedAgent] = useState<AgentResponse | null>(null);
  const [agentToDelete, setAgentToDelete] = useState<AgentResponse | null>(null);
  const queryClient = useQueryClient();
  const { toast } = useToast();

  // Fetch agents for the squad
  const { data: agents, isLoading } = useQuery({
    queryKey: ["agents", squad?.id],
    queryFn: () => agentsApi.list(squad!.id),
    enabled: !!squad?.id,
  });

  // Delete agent mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => agentsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["agents", squad?.id] });
      queryClient.invalidateQueries({ queryKey: ["squads"] });
      toast({
        title: "Agent deleted",
        description: "The agent has been deleted successfully.",
      });
      setAgentToDelete(null);
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to delete agent. Please try again.",
        variant: "destructive",
      });
    },
  });

  // Toggle active mutation
  const toggleActiveMutation = useMutation({
    mutationFn: (id: number) => agentsApi.toggleActive(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["agents", squad?.id] });
      queryClient.invalidateQueries({ queryKey: ["squads"] });
      toast({
        title: "Agent updated",
        description: "The agent status has been updated.",
      });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to update agent status.",
        variant: "destructive",
      });
    },
  });

  const handleAddAgent = () => {
    setSelectedAgent(null);
    setIsAgentModalOpen(true);
  };

  const handleEditAgent = (agent: AgentResponse) => {
    setSelectedAgent(agent);
    setIsAgentModalOpen(true);
  };

  const handleCloseAgentModal = () => {
    setIsAgentModalOpen(false);
    setSelectedAgent(null);
  };

  const directChatMutation = useMutation({
    mutationFn: (agentId: number) => liveViewApi.ensureDirectAgentSession(agentId),
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to open direct agent session.",
        variant: "destructive",
      });
    },
  });

  const handleOpenDirectChat = async (agent: AgentResponse) => {
    const session = await directChatMutation.mutateAsync(agent.id);
    window.open(session.viewer_url || `/live/${session.code}`, "_blank", "noopener,noreferrer");
  };

  return (
    <>
      <Sheet open={!!squad} onOpenChange={onClose}>
        <SheetContent className="sm:max-w-lg overflow-y-auto">
          <SheetHeader>
            <div className="flex items-center gap-2">
              <SheetTitle>{squad?.name}</SheetTitle>
              <Badge variant={squad?.is_active ? "default" : "secondary"}>
                {squad?.is_active ? "Active" : "Inactive"}
              </Badge>
            </div>
            <SheetDescription>
              {squad?.description || "No description"}
            </SheetDescription>
          </SheetHeader>

          <div className="mt-6 space-y-6">
            {/* Stats */}
            <div className="grid grid-cols-2 gap-4">
              <div className="rounded-lg border p-3">
                <div className="flex items-center gap-2 text-muted-foreground text-sm">
                  <Bot className="h-4 w-4" />
                  <span>Total Agents</span>
                </div>
                <div className="mt-1 text-2xl font-bold">{squad?.agents_count || 0}</div>
              </div>
              <div className="rounded-lg border p-3">
                <div className="flex items-center gap-2 text-muted-foreground text-sm">
                  <Power className="h-4 w-4 text-ok" />
                  <span>Active</span>
                </div>
                <div className="mt-1 text-2xl font-bold text-ok">
                  {squad?.active_agents_count || 0}
                </div>
              </div>
            </div>

            {/* Agents List */}
            <div>
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-medium">Agents</h3>
                <Button size="sm" onClick={handleAddAgent}>
                  <Plus className="h-4 w-4 mr-1" />
                  Add Agent
                </Button>
              </div>

              {isLoading ? (
                <div className="space-y-3">
                  {[...Array(3)].map((_, i) => (
                    <div key={i} className="rounded-lg border p-4 animate-pulse">
                      <div className="h-5 bg-muted rounded w-1/2 mb-2" />
                      <div className="h-4 bg-muted rounded w-1/3" />
                    </div>
                  ))}
                </div>
              ) : agents?.content?.length === 0 ? (
                <div className="rounded-lg border-2 border-dashed p-6 text-center">
                  <Bot className="h-8 w-8 mx-auto text-muted-foreground mb-2" />
                  <p className="text-sm text-muted-foreground">
                    No agents yet. Add your first agent to this squad.
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  {agents?.content?.map((agent) => (
                    <AgentCard
                      key={agent.id}
                      agent={agent}
                      onEdit={() => handleEditAgent(agent)}
                      onDelete={() => setAgentToDelete(agent)}
                      onToggleActive={() => toggleActiveMutation.mutate(agent.id)}
                      onOpenDirectChat={() => handleOpenDirectChat(agent)}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        </SheetContent>
      </Sheet>

      {/* Agent Modal */}
      <AgentModal
        open={isAgentModalOpen}
        onClose={handleCloseAgentModal}
        agent={selectedAgent}
        squadId={squad?.id}
      />

      {/* Delete Confirmation */}
      <DeleteConfirmDialog
        open={!!agentToDelete}
        onClose={() => setAgentToDelete(null)}
        onConfirm={() => agentToDelete && deleteMutation.mutate(agentToDelete.id)}
        title="Delete Agent"
        description={`Are you sure you want to delete "${agentToDelete?.name}"? This action cannot be undone.`}
        isLoading={deleteMutation.isPending}
      />
    </>
  );
}

interface AgentCardProps {
  agent: AgentResponse;
  onEdit: () => void;
  onDelete: () => void;
  onToggleActive: () => void;
  onOpenDirectChat: () => void;
}

function AgentCard({ agent, onEdit, onDelete, onToggleActive, onOpenDirectChat }: AgentCardProps) {
  return (
    <div
      className={cn(
        "rounded-lg border p-4 group hover:shadow-sm transition-shadow",
        !agent.is_active && "opacity-60"
      )}
    >
      <div className="flex items-start justify-between">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-muted font-bold text-sm">
            {agentTypeIcons[agent.type]}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h4 className="font-medium">{agent.name}</h4>
              {agent.is_active && (
                <span className="flex h-2 w-2 rounded-full bg-ok" />
              )}
            </div>
            <p className="text-sm text-muted-foreground">
              {agentTypeLabels[agent.type]}
            </p>
            <Button
              variant="link"
              className="h-auto p-0 mt-2 text-sm"
              onClick={onOpenDirectChat}
              data-testid={`agent-direct-chat-${agent.id}`}
            >
              <MessageSquare className="h-4 w-4 mr-1" />
              Chat anytime
            </Button>
          </div>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 opacity-0 group-hover:opacity-100"
              aria-label="Agent options"
            >
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={onEdit}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </DropdownMenuItem>
            <DropdownMenuItem onClick={onToggleActive}>
              <Power className="mr-2 h-4 w-4" />
              {agent.is_active ? "Deactivate" : "Activate"}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={onDelete}
              className="text-destructive focus:text-destructive"
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <div className="mt-3 flex items-center gap-4 text-xs text-muted-foreground">
        <div className="flex items-center gap-1">
          <Brain className="h-3 w-3" />
          <span>{agent.model}</span>
        </div>
        <div className="flex items-center gap-1">
          <Cpu className="h-3 w-3" />
          <span>Temp: {agent.temperature}</span>
        </div>
      </div>

      {agent.description && (
        <p className="mt-2 text-sm text-muted-foreground line-clamp-2">
          {agent.description}
        </p>
      )}
    </div>
  );
}
