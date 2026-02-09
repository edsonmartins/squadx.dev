import { create } from "zustand";
import {
  tasksApi,
  type TaskResponse,
  type TaskStatus,
  type CreateTaskRequest,
  type UpdateTaskRequest,
} from "@/lib/api";

interface TaskState {
  tasks: TaskResponse[];
  selectedTask: TaskResponse | null;
  isLoading: boolean;
  error: string | null;

  fetchTasks: (projectId: number) => Promise<void>;
  createTask: (data: CreateTaskRequest) => Promise<TaskResponse>;
  updateTask: (id: number, data: UpdateTaskRequest) => Promise<TaskResponse>;
  updateTaskStatus: (id: number, status: TaskStatus) => Promise<void>;
  moveTask: (taskId: number, newStatus: TaskStatus, newIndex: number) => void;
  deleteTask: (id: number) => Promise<void>;
  setSelectedTask: (task: TaskResponse | null) => void;
  getTasksByStatus: (status: TaskStatus) => TaskResponse[];
}

export const useTaskStore = create<TaskState>((set, get) => ({
  tasks: [],
  selectedTask: null,
  isLoading: false,
  error: null,

  fetchTasks: async (projectId: number) => {
    set({ isLoading: true, error: null });
    try {
      const tasks = await tasksApi.kanban(projectId);
      set({ tasks, isLoading: false });
    } catch (error) {
      set({ error: (error as Error).message, isLoading: false });
    }
  },

  createTask: async (data: CreateTaskRequest) => {
    const task = await tasksApi.create(data);
    set((state) => ({
      tasks: [...state.tasks, task],
    }));
    return task;
  },

  updateTask: async (id: number, data: UpdateTaskRequest) => {
    const task = await tasksApi.update(id, data);
    set((state) => ({
      tasks: state.tasks.map((t) => (t.id === id ? task : t)),
      selectedTask: state.selectedTask?.id === id ? task : state.selectedTask,
    }));
    return task;
  },

  updateTaskStatus: async (id: number, status: TaskStatus) => {
    await tasksApi.updateStatus(id, status);
    set((state) => ({
      tasks: state.tasks.map((t) => (t.id === id ? { ...t, status } : t)),
    }));
  },

  moveTask: (taskId: number, newStatus: TaskStatus, newIndex: number) => {
    set((state) => {
      const tasks = [...state.tasks];
      const taskIndex = tasks.findIndex((t) => t.id === taskId);

      if (taskIndex === -1) return state;

      const [task] = tasks.splice(taskIndex, 1);
      task.status = newStatus;
      task.order_index = newIndex;

      // Find the correct position to insert
      const sameStatusTasks = tasks.filter((t) => t.status === newStatus);
      const insertIndex = tasks.findIndex(
        (t) => t.status === newStatus && t.order_index >= newIndex
      );

      if (insertIndex === -1) {
        tasks.push(task);
      } else {
        tasks.splice(insertIndex, 0, task);
      }

      return { tasks };
    });
  },

  deleteTask: async (id: number) => {
    await tasksApi.delete(id);
    set((state) => ({
      tasks: state.tasks.filter((t) => t.id !== id),
      selectedTask: state.selectedTask?.id === id ? null : state.selectedTask,
    }));
  },

  setSelectedTask: (task: TaskResponse | null) => {
    set({ selectedTask: task });
  },

  getTasksByStatus: (status: TaskStatus) => {
    return get()
      .tasks.filter((t) => t.status === status)
      .sort((a, b) => a.order_index - b.order_index);
  },
}));
