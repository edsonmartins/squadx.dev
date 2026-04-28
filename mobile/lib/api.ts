import AsyncStorage from "@react-native-async-storage/async-storage";

const API_URL = process.env.EXPO_PUBLIC_API_URL || "http://localhost:8080";

const TOKEN_KEY = "squadx_token";
const REFRESH_TOKEN_KEY = "squadx_refresh_token";

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  errors?: Record<string, string>;
  timestamp: string;
}

export class ApiError extends Error {
  status: number;
  errors?: Record<string, string>;

  constructor(
    message: string,
    status: number,
    errors?: Record<string, string>
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errors = errors;
  }
}

class ApiClient {
  private baseUrl: string;
  private token: string | null = null;
  private refreshToken: string | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  async loadTokens(): Promise<void> {
    try {
      this.token = await AsyncStorage.getItem(TOKEN_KEY);
      this.refreshToken = await AsyncStorage.getItem(REFRESH_TOKEN_KEY);
    } catch {
      // Ignore storage errors
    }
  }

  async setTokens(token: string, refreshToken: string): Promise<void> {
    this.token = token;
    this.refreshToken = refreshToken;
    await AsyncStorage.setItem(TOKEN_KEY, token);
    await AsyncStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }

  async clearTokens(): Promise<void> {
    this.token = null;
    this.refreshToken = null;
    await AsyncStorage.removeItem(TOKEN_KEY);
    await AsyncStorage.removeItem(REFRESH_TOKEN_KEY);
  }

  getToken(): string | null {
    return this.token;
  }

  isAuthenticated(): boolean {
    return !!this.token;
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      ...(options.headers as Record<string, string>),
    };

    if (this.token) {
      headers["Authorization"] = `Bearer ${this.token}`;
    }

    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...options,
      headers,
    });

    const data: ApiResponse<T> = await response.json();

    if (!response.ok) {
      throw new ApiError(
        data.message || "An error occurred",
        response.status,
        data.errors
      );
    }

    return data;
  }

  async get<T>(endpoint: string): Promise<T> {
    const response = await this.request<T>(endpoint, { method: "GET" });
    return response.data as T;
  }

  async post<T>(endpoint: string, body?: unknown): Promise<T> {
    const response = await this.request<T>(endpoint, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    });
    return response.data as T;
  }

  async put<T>(endpoint: string, body?: unknown): Promise<T> {
    const response = await this.request<T>(endpoint, {
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
    });
    return response.data as T;
  }

  async patch<T>(endpoint: string, body?: unknown): Promise<T> {
    const response = await this.request<T>(endpoint, {
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
    });
    return response.data as T;
  }

  async delete<T>(endpoint: string): Promise<T> {
    const response = await this.request<T>(endpoint, { method: "DELETE" });
    return response.data as T;
  }
}

export const api = new ApiClient(API_URL);

// ---- API Endpoints ----

export interface AuthResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  user: UserResponse;
}

export interface UserResponse {
  id: number;
  email: string;
  full_name: string;
  avatar_url?: string;
  role: string;
  is_active: boolean;
  email_verified: boolean;
  created_at: string;
  last_login_at?: string;
}

export interface PageResponse<T> {
  content: T[];
  page_number: number;
  page_size: number;
  total_elements: number;
  total_pages: number;
}

export interface ProjectResponse {
  id: number;
  name: string;
  slug: string;
  description?: string;
  repository_url?: string;
  default_branch: string;
  is_active: boolean;
  organization_id: number;
  organization_name: string;
  squad_id?: number | null;
  squad_name?: string;
  tasks_count: number;
  created_at: string;
  updated_at?: string;
}

export type TaskStatus =
  | "TODO"
  | "IN_PROGRESS"
  | "IN_REVIEW"
  | "BLOCKED"
  | "DONE"
  | "CANCELLED";

export interface TaskResponse {
  id: number;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: string;
  project_id: number;
  project_name: string;
  assigned_agent_name?: string;
  created_at: string;
}

export interface LiveSessionResponse {
  id: number;
  code: string;
  task_id: number;
  task_title: string;
  host_user_name: string;
  status: string;
  current_viewers: number;
  max_viewers: number;
  created_at: string;
}

export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>("/api/v1/auth/login", { email, password }),
  me: () => api.get<UserResponse>("/api/v1/auth/me"),
  refresh: (refreshToken: string) =>
    api.post<AuthResponse>("/api/v1/auth/refresh", {
      refresh_token: refreshToken,
    }),
};

export const tasksApi = {
  listByProject: (projectId: number) =>
    api.get<PageResponse<TaskResponse>>(`/api/v1/tasks/project/${projectId}`),
};

export const projectsApi = {
  list: () => api.get<PageResponse<ProjectResponse>>("/api/v1/projects/my"),
};

export const liveViewApi = {
  getActive: () =>
    api.get<LiveSessionResponse[]>(
      "/api/v1/live-view/supabase/sessions/active"
    ),
  getByCode: (code: string) =>
    api.get<LiveSessionResponse>(
      `/api/v1/live-view/sessions/code/${code}`
    ),
  joinSession: (code: string) =>
    api.post<LiveSessionResponse>("/api/v1/live-view/sessions/join", { code }),
};
