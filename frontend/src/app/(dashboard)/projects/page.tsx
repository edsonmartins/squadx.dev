"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, FolderGit2, GitBranch, MoreVertical, Pencil, Trash2, ExternalLink } from "lucide-react";
import { projectsApi, organizationsApi, ProjectResponse } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/hooks/use-toast";
import { ProjectModal } from "@/components/projects/project-modal";
import { DeleteConfirmDialog } from "@/components/shared/delete-confirm-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import Link from "next/link";

export default function ProjectsPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedProject, setSelectedProject] = useState<ProjectResponse | null>(null);
  const [projectToDelete, setProjectToDelete] = useState<ProjectResponse | null>(null);
  const queryClient = useQueryClient();
  const { toast } = useToast();

  // Fetch organizations for the current user
  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  const defaultOrgId = organizations?.content?.[0]?.id;

  // Fetch projects
  const { data: projects, isLoading } = useQuery({
    queryKey: ["projects", defaultOrgId],
    queryFn: () => projectsApi.list(defaultOrgId),
    enabled: !!defaultOrgId,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => projectsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      toast({
        title: "Project deleted",
        description: "The project has been deleted successfully.",
      });
      setProjectToDelete(null);
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to delete project. Please try again.",
        variant: "destructive",
      });
    },
  });

  const handleEdit = (project: ProjectResponse) => {
    setSelectedProject(project);
    setIsModalOpen(true);
  };

  const handleCreate = () => {
    setSelectedProject(null);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedProject(null);
  };

  const handleDelete = (project: ProjectResponse) => {
    setProjectToDelete(project);
  };

  const confirmDelete = () => {
    if (projectToDelete) {
      deleteMutation.mutate(projectToDelete.id);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Projects</h1>
          <p className="text-muted-foreground">
            Manage your software projects and assign squads.
          </p>
        </div>
        <Button onClick={handleCreate} data-testid="new-project-button">
          <Plus className="mr-2 h-4 w-4" />
          New Project
        </Button>
      </div>

      {/* Projects Grid */}
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
      ) : projects?.content?.length === 0 ? (
        <Card className="border-dashed">
          <EmptyState
            icon={FolderGit2}
            title="No projects yet"
            description="Create your first project to start managing tasks with AI squads."
            actionLabel="Create Project"
            onAction={handleCreate}
            className="py-12"
          />
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {projects?.content?.map((project) => (
            <ProjectCard
              key={project.id}
              project={project}
              onEdit={() => handleEdit(project)}
              onDelete={() => handleDelete(project)}
            />
          ))}
        </div>
      )}

      {/* Create/Edit Modal */}
      <ProjectModal
        open={isModalOpen}
        onOpenChange={(open) => !open && handleCloseModal()}
        project={selectedProject}
        organizationId={defaultOrgId}
      />

      {/* Delete Confirmation */}
      <DeleteConfirmDialog
        open={!!projectToDelete}
        onClose={() => setProjectToDelete(null)}
        onConfirm={confirmDelete}
        title="Delete Project"
        description={`Are you sure you want to delete "${projectToDelete?.name}"? This action cannot be undone and will delete all associated tasks.`}
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}

interface ProjectCardProps {
  project: ProjectResponse;
  onEdit: () => void;
  onDelete: () => void;
}

function ProjectCard({ project, onEdit, onDelete }: ProjectCardProps) {
  return (
    <Card className="group hover:shadow-md transition-shadow" data-testid={`project-card-${project.id}`}>
      <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
        <div className="space-y-1">
          <CardTitle className="text-lg">
            <Link
              href={`/tasks?projectId=${project.id}`}
              className="hover:underline"
            >
              {project.name}
            </Link>
          </CardTitle>
          <CardDescription className="line-clamp-2">
            {project.description || "No description"}
          </CardDescription>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 opacity-0 group-hover:opacity-100"
              data-testid={`project-menu-trigger-${project.id}`}
            >
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={onEdit} data-testid={`project-edit-${project.id}`}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </DropdownMenuItem>
            {project.repository_url && (
              <DropdownMenuItem asChild>
                <a
                  href={project.repository_url}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <ExternalLink className="mr-2 h-4 w-4" />
                  Open Repository
                </a>
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={onDelete}
              className="text-destructive focus:text-destructive"
              data-testid={`project-delete-${project.id}`}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </CardHeader>
      <CardContent>
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          {project.repository_url && (
            <div className="flex items-center gap-1">
              <GitBranch className="h-4 w-4" />
              <span>{project.default_branch}</span>
            </div>
          )}
          <div className="flex items-center gap-1">
            <FolderGit2 className="h-4 w-4" />
            <span>{project.tasks_count} tasks</span>
          </div>
        </div>
        {project.squad_name && (
          <div className="mt-3 inline-flex items-center rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
            Squad: {project.squad_name}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
