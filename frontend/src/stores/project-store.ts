import { create } from "zustand";
import {
  projectsApi,
  type ProjectResponse,
  type CreateProjectRequest,
  type UpdateProjectRequest,
} from "@/lib/api";

interface ProjectState {
  projects: ProjectResponse[];
  selectedProject: ProjectResponse | null;
  isLoading: boolean;
  error: string | null;

  fetchProjects: (organizationId?: number) => Promise<void>;
  fetchProject: (id: number) => Promise<void>;
  createProject: (data: CreateProjectRequest) => Promise<ProjectResponse>;
  updateProject: (id: number, data: UpdateProjectRequest) => Promise<ProjectResponse>;
  deleteProject: (id: number) => Promise<void>;
  selectProject: (project: ProjectResponse | null) => void;
  clearError: () => void;
}

export const useProjectStore = create<ProjectState>((set, get) => ({
  projects: [],
  selectedProject: null,
  isLoading: false,
  error: null,

  fetchProjects: async (organizationId?: number) => {
    set({ isLoading: true, error: null });
    try {
      const response = await projectsApi.list(organizationId);
      set({ projects: response.content, isLoading: false });
    } catch (error) {
      set({ error: (error as Error).message, isLoading: false });
    }
  },

  fetchProject: async (id: number) => {
    set({ isLoading: true, error: null });
    try {
      const project = await projectsApi.get(id);
      set({ selectedProject: project, isLoading: false });
    } catch (error) {
      set({ error: (error as Error).message, isLoading: false });
    }
  },

  createProject: async (data: CreateProjectRequest) => {
    set({ isLoading: true, error: null });
    try {
      const newProject = await projectsApi.create(data);
      set((state) => ({
        projects: [...state.projects, newProject],
        isLoading: false,
      }));
      return newProject;
    } catch (error) {
      set({ error: (error as Error).message, isLoading: false });
      throw error;
    }
  },

  updateProject: async (id: number, data: UpdateProjectRequest) => {
    set({ isLoading: true, error: null });
    try {
      const updatedProject = await projectsApi.update(id, data);
      set((state) => ({
        projects: state.projects.map((p) => (p.id === id ? updatedProject : p)),
        selectedProject:
          state.selectedProject?.id === id ? updatedProject : state.selectedProject,
        isLoading: false,
      }));
      return updatedProject;
    } catch (error) {
      set({ error: (error as Error).message, isLoading: false });
      throw error;
    }
  },

  deleteProject: async (id: number) => {
    set({ isLoading: true, error: null });
    try {
      await projectsApi.delete(id);
      set((state) => ({
        projects: state.projects.filter((p) => p.id !== id),
        selectedProject: state.selectedProject?.id === id ? null : state.selectedProject,
        isLoading: false,
      }));
    } catch (error) {
      set({ error: (error as Error).message, isLoading: false });
      throw error;
    }
  },

  selectProject: (project: ProjectResponse | null) => {
    set({ selectedProject: project });
  },

  clearError: () => {
    set({ error: null });
  },
}));
