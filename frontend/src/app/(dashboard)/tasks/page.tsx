"use client";

import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { Plus, Filter, Search, FolderKanban } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { KanbanBoard } from "@/components/kanban/kanban-board";
import { TaskModal } from "@/components/tasks/task-modal";
import { TaskDetailSheet } from "@/components/tasks/task-detail-sheet";
import { DeleteConfirmDialog } from "@/components/shared/delete-confirm-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { useTaskStore } from "@/stores/task-store";
import { projectsApi, organizationsApi, TaskResponse, TaskStatus } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";
import { useProjectSocket } from "@/hooks/use-socket";

export default function TasksPage() {
  const router = useRouter();
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<TaskResponse | null>(null);
  const [taskToDelete, setTaskToDelete] = useState<TaskResponse | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const { fetchTasks, isLoading: isLoadingTasks, selectedTask, setSelectedTask, deleteTask } = useTaskStore();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  // Real-time updates via WebSocket
  useProjectSocket(selectedProjectId);

  // Fetch organizations
  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  const defaultOrgId = organizations?.content?.[0]?.id;

  const { data: projectsData, isLoading: isLoadingProjects } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectsApi.list(),
  });

  const projects = projectsData?.content || [];

  useEffect(() => {
    if (projects.length > 0 && !selectedProjectId) {
      setSelectedProjectId(projects[0].id);
    }
  }, [projects, selectedProjectId]);

  useEffect(() => {
    if (selectedProjectId) {
      fetchTasks(selectedProjectId);
    }
  }, [selectedProjectId, fetchTasks]);

  const selectedProject = projects.find((p) => p.id === selectedProjectId);

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteTask(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks", selectedProjectId] });
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      toast({
        title: "Task deleted",
        description: "The task has been deleted successfully.",
      });
      setTaskToDelete(null);
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to delete task. Please try again.",
        variant: "destructive",
      });
    },
  });

  const handleCreateTask = () => {
    setEditingTask(null);
    setIsModalOpen(true);
  };

  const handleEditTask = (task: TaskResponse) => {
    setEditingTask(task);
    setSelectedTask(null);
    setIsModalOpen(true);
  };

  const handleDeleteTask = (task: TaskResponse) => {
    setTaskToDelete(task);
    setSelectedTask(null);
  };

  const confirmDelete = () => {
    if (taskToDelete) {
      deleteMutation.mutate(taskToDelete.id);
    }
  };

  if (isLoadingProjects) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (projects.length === 0) {
    return (
      <EmptyState
        icon={FolderKanban}
        title="No projects found"
        description="Tasks live inside projects. Create your first project to start planning work."
        actionLabel="Create your first project"
        onAction={() => router.push("/projects")}
        className="h-64"
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Tasks</h1>
          <p className="text-muted-foreground">
            Manage and track your project tasks
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search tasks..."
              aria-label="Search tasks"
              className="pl-8 w-full sm:w-[200px]"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <Button variant="outline">
            <Filter className="mr-2 h-4 w-4" />
            Filter
          </Button>
          <Button onClick={handleCreateTask} data-testid="new-task-button">
            <Plus className="mr-2 h-4 w-4" />
            New Task
          </Button>
        </div>
      </div>

      {/* Project Selector */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2">
        {projects.map((project) => (
          <Button
            key={project.id}
            variant={selectedProjectId === project.id ? "default" : "outline"}
            size="sm"
            onClick={() => setSelectedProjectId(project.id)}
            data-testid={`project-filter-${project.id}`}
          >
            {project.name}
            <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-xs">
              {project.tasks_count}
            </span>
          </Button>
        ))}
      </div>

      {/* Kanban Board */}
      {selectedProjectId && (
        <div className="min-h-[calc(100vh-300px)]">
          {isLoadingTasks ? (
            <div className="flex items-center justify-center h-64">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
            </div>
          ) : (
            <KanbanBoard projectId={selectedProjectId} />
          )}
        </div>
      )}

      {/* Task Modal */}
      <TaskModal
        open={isModalOpen}
        onClose={() => {
          setIsModalOpen(false);
          setEditingTask(null);
        }}
        task={editingTask}
        projectId={selectedProjectId!}
        organizationId={defaultOrgId}
      />

      {/* Task Detail Sheet */}
      <TaskDetailSheet
        task={selectedTask}
        onClose={() => setSelectedTask(null)}
        onEdit={handleEditTask}
        onDelete={handleDeleteTask}
      />

      {/* Delete Confirmation */}
      <DeleteConfirmDialog
        open={!!taskToDelete}
        onClose={() => setTaskToDelete(null)}
        onConfirm={confirmDelete}
        title="Delete Task"
        description={`Are you sure you want to delete "${taskToDelete?.title}"? This action cannot be undone.`}
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}
