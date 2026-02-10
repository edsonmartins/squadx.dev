const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  errors?: Record<string, string>;
  timestamp: string;
}

interface RequestOptions extends RequestInit {
  token?: string;
}

class ApiClient {
  private baseUrl: string;
  private token: string | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  setToken(token: string | null) {
    this.token = token;
  }

  getToken(): string | null {
    return this.token;
  }

  private async request<T>(
    endpoint: string,
    options: RequestOptions = {}
  ): Promise<ApiResponse<T>> {
    const { token, ...fetchOptions } = options;

    const headers: HeadersInit = {
      "Content-Type": "application/json",
      ...options.headers,
    };

    const authToken = token || this.token;
    if (authToken) {
      (headers as Record<string, string>)["Authorization"] =
        `Bearer ${authToken}`;
    }

    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...fetchOptions,
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

  async get<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    const response = await this.request<T>(endpoint, {
      ...options,
      method: "GET",
    });
    return response.data as T;
  }

  async post<T>(
    endpoint: string,
    body?: unknown,
    options?: RequestOptions
  ): Promise<T> {
    const response = await this.request<T>(endpoint, {
      ...options,
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    });
    return response.data as T;
  }

  async put<T>(
    endpoint: string,
    body?: unknown,
    options?: RequestOptions
  ): Promise<T> {
    const response = await this.request<T>(endpoint, {
      ...options,
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
    });
    return response.data as T;
  }

  async patch<T>(
    endpoint: string,
    body?: unknown,
    options?: RequestOptions
  ): Promise<T> {
    const response = await this.request<T>(endpoint, {
      ...options,
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
    });
    return response.data as T;
  }

  async delete<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    const response = await this.request<T>(endpoint, {
      ...options,
      method: "DELETE",
    });
    return response.data as T;
  }
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

export const api = new ApiClient(API_URL);

// Auth API
export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>("/api/v1/auth/login", { email, password }),

  register: (email: string, password: string, fullName: string) =>
    api.post<AuthResponse>("/api/v1/auth/register", {
      email,
      password,
      fullName,
    }),

  refresh: (refreshToken: string) =>
    api.post<AuthResponse>("/api/v1/auth/refresh", {
      refresh_token: refreshToken,
    }),

  me: () => api.get<UserResponse>("/api/v1/auth/me"),
};

// Organizations API
export const organizationsApi = {
  list: () => api.get<PageResponse<OrganizationResponse>>("/api/v1/organizations/my"),
  get: (id: number) => api.get<OrganizationResponse>(`/api/v1/organizations/${id}`),
  create: (data: CreateOrganizationRequest) =>
    api.post<OrganizationResponse>("/api/v1/organizations", data),
  update: (id: number, data: UpdateOrganizationRequest) =>
    api.put<OrganizationResponse>(`/api/v1/organizations/${id}`, data),
  delete: (id: number) => api.delete(`/api/v1/organizations/${id}`),
};

// Projects API
export const projectsApi = {
  list: (organizationId?: number) =>
    organizationId
      ? api.get<PageResponse<ProjectResponse>>(
          `/api/v1/projects/organization/${organizationId}`
        )
      : api.get<PageResponse<ProjectResponse>>("/api/v1/projects/my"),
  get: (id: number) => api.get<ProjectResponse>(`/api/v1/projects/${id}`),
  create: (data: CreateProjectRequest) =>
    api.post<ProjectResponse>("/api/v1/projects", data),
  update: (id: number, data: UpdateProjectRequest) =>
    api.put<ProjectResponse>(`/api/v1/projects/${id}`, data),
  delete: (id: number) => api.delete(`/api/v1/projects/${id}`),
};

// Tasks API
export const tasksApi = {
  list: (projectId: number) =>
    api.get<PageResponse<TaskResponse>>(`/api/v1/tasks/project/${projectId}`),
  kanban: (projectId: number) =>
    api.get<TaskResponse[]>(`/api/v1/tasks/project/${projectId}/kanban`),
  get: (id: number) => api.get<TaskResponse>(`/api/v1/tasks/${id}`),
  create: (data: CreateTaskRequest) =>
    api.post<TaskResponse>("/api/v1/tasks", data),
  update: (id: number, data: UpdateTaskRequest) =>
    api.put<TaskResponse>(`/api/v1/tasks/${id}`, data),
  updateStatus: (id: number, status: TaskStatus) =>
    api.patch<TaskResponse>(`/api/v1/tasks/${id}/status?status=${status}`),
  updateOrder: (id: number, orderIndex: number) =>
    api.patch(`/api/v1/tasks/${id}/order?orderIndex=${orderIndex}`),
  delete: (id: number) => api.delete(`/api/v1/tasks/${id}`),
};

// Types
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
  is_first: boolean;
  is_last: boolean;
}

export interface OrganizationResponse {
  id: number;
  name: string;
  slug: string;
  description?: string;
  logo_url?: string;
  is_active: boolean;
  members_count: number;
  projects_count: number;
  squads_count: number;
  created_at: string;
  updated_at?: string;
}

export interface CreateOrganizationRequest {
  name: string;
  description?: string;
  logo_url?: string;
}

export interface UpdateOrganizationRequest {
  name?: string;
  description?: string;
  logo_url?: string;
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
  squad_id?: number;
  squad_name?: string;
  tasks_count: number;
  created_at: string;
  updated_at?: string;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  repository_url?: string;
  default_branch?: string;
  organization_id: number;
  squad_id?: number;
}

export interface UpdateProjectRequest {
  name?: string;
  description?: string;
  repository_url?: string;
  default_branch?: string;
  squad_id?: number;
}

export type TaskStatus =
  | "TODO"
  | "IN_PROGRESS"
  | "IN_REVIEW"
  | "BLOCKED"
  | "DONE"
  | "CANCELLED";

export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export interface TaskResponse {
  id: number;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: TaskPriority;
  story_points?: number;
  estimated_hours?: number;
  actual_hours?: number;
  due_date?: string;
  started_at?: string;
  completed_at?: string;
  order_index: number;
  project_id: number;
  project_name: string;
  assigned_agent_id?: number;
  assigned_agent_name?: string;
  parent_task_id?: number;
  subtasks_count: number;
  created_by_id?: number;
  created_by_name?: string;
  tags?: string[];
  created_at: string;
  updated_at?: string;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  story_points?: number;
  estimated_hours?: number;
  due_date?: string;
  project_id: number;
  parent_task_id?: number;
  assigned_agent_id?: number;
  tags?: string[];
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  story_points?: number;
  estimated_hours?: number;
  due_date?: string;
  assigned_agent_id?: number;
  tags?: string[];
}

// Squads API
export const squadsApi = {
  list: (organizationId: number) =>
    api.get<PageResponse<SquadResponse>>(`/api/v1/squads/organization/${organizationId}`),
  get: (id: number) => api.get<SquadResponse>(`/api/v1/squads/${id}`),
  create: (data: CreateSquadRequest) =>
    api.post<SquadResponse>("/api/v1/squads", data),
  update: (id: number, data: UpdateSquadRequest) =>
    api.put<SquadResponse>(`/api/v1/squads/${id}`, data),
  delete: (id: number) => api.delete(`/api/v1/squads/${id}`),
  toggleActive: (id: number) =>
    api.patch<SquadResponse>(`/api/v1/squads/${id}/toggle-active`),
};

// Agents API
export const agentsApi = {
  list: (squadId: number) =>
    api.get<PageResponse<AgentResponse>>(`/api/v1/agents/squad/${squadId}`),
  listByOrganization: (organizationId: number) =>
    api.get<PageResponse<AgentResponse>>(`/api/v1/agents/organization/${organizationId}`),
  listActive: (squadId: number) =>
    api.get<AgentResponse[]>(`/api/v1/agents/squad/${squadId}/active`),
  get: (id: number) => api.get<AgentResponse>(`/api/v1/agents/${id}`),
  create: (data: CreateAgentRequest) =>
    api.post<AgentResponse>("/api/v1/agents", data),
  update: (id: number, data: UpdateAgentRequest) =>
    api.put<AgentResponse>(`/api/v1/agents/${id}`, data),
  delete: (id: number) => api.delete(`/api/v1/agents/${id}`),
  toggleActive: (id: number) =>
    api.patch<AgentResponse>(`/api/v1/agents/${id}/toggle-active`),
};

// Executions API
export const executionsApi = {
  start: (data: StartExecutionRequest) =>
    api.post<ExecutionResponse>("/api/v1/executions", data),
  get: (id: number) => api.get<ExecutionResponse>(`/api/v1/executions/${id}`),
  listByTask: (taskId: number) =>
    api.get<PageResponse<ExecutionResponse>>(`/api/v1/executions/task/${taskId}`),
  listByProject: (projectId: number) =>
    api.get<PageResponse<ExecutionResponse>>(`/api/v1/executions/project/${projectId}`),
  listByOrganization: (organizationId: number) =>
    api.get<PageResponse<ExecutionResponse>>(`/api/v1/executions/organization/${organizationId}`),
  updateStatus: (id: number, status: ExecutionStatus) =>
    api.patch<ExecutionResponse>(`/api/v1/executions/${id}/status?status=${status}`),
  cancel: (id: number) =>
    api.post<ExecutionResponse>(`/api/v1/executions/${id}/cancel`),
  getMetrics: (organizationId: number) =>
    api.get<Record<string, unknown>>(`/api/v1/executions/organization/${organizationId}/metrics`),
};

// Live View API
export const liveViewApi = {
  createSession: (data: CreateLiveSessionRequest) =>
    api.post<LiveSessionResponse>("/api/v1/live-view/sessions", data),
  joinSession: (code: string) =>
    api.post<LiveSessionResponse>("/api/v1/live-view/sessions/join", { code }),
  startSession: (sessionId: number) =>
    api.post<LiveSessionResponse>(`/api/v1/live-view/sessions/${sessionId}/start`),
  endSession: (sessionId: number) =>
    api.post<LiveSessionResponse>(`/api/v1/live-view/sessions/${sessionId}/end`),
  leaveSession: (code: string) =>
    api.post<void>(`/api/v1/live-view/sessions/${code}/leave`),
  getByCode: (code: string) =>
    api.get<LiveSessionResponse>(`/api/v1/live-view/sessions/code/${code}`),
  getByTask: (taskId: number) =>
    api.get<LiveSessionResponse>(`/api/v1/live-view/sessions/task/${taskId}`),
  getActiveByOrganization: (organizationId: number) =>
    api.get<LiveSessionResponse[]>(`/api/v1/live-view/sessions/organization/${organizationId}`),
  grantControl: (sessionId: number, userId: number) =>
    api.post<LiveSessionResponse>(`/api/v1/live-view/sessions/${sessionId}/participants/${userId}/grant-control`),
  revokeControl: (sessionId: number, userId: number) =>
    api.post<LiveSessionResponse>(`/api/v1/live-view/sessions/${sessionId}/participants/${userId}/revoke-control`),
  // Supabase endpoints (for Python client sessions)
  supabase: {
    getByCode: (code: string) =>
      api.get<LiveSessionResponse>(`/api/v1/live-view/supabase/sessions/code/${code}`),
    getByTask: (taskId: number) =>
      api.get<LiveSessionResponse>(`/api/v1/live-view/supabase/sessions/task/${taskId}`),
    getActive: () =>
      api.get<LiveSessionResponse[]>(`/api/v1/live-view/supabase/sessions/active`),
  },
};

// Squad Types
export type AgentType = "FRONTEND" | "BACKEND" | "FULLSTACK" | "DEVOPS" | "QA" | "COORDINATOR";

export interface SquadResponse {
  id: number;
  name: string;
  description?: string;
  is_active: boolean;
  organization_id: number;
  organization_name: string;
  agents_count: number;
  active_agents_count: number;
  created_at: string;
  updated_at?: string;
}

export interface CreateSquadRequest {
  name: string;
  description?: string;
  organization_id: number;
}

export interface UpdateSquadRequest {
  name?: string;
  description?: string;
}

// Agent Types
export interface AgentResponse {
  id: number;
  name: string;
  type: AgentType;
  description?: string;
  model: string;
  system_prompt?: string;
  temperature: number;
  max_tokens: number;
  is_active: boolean;
  squad_id: number;
  squad_name: string;
  created_at: string;
  updated_at?: string;
}

export interface CreateAgentRequest {
  name: string;
  type: AgentType;
  description?: string;
  model?: string;
  system_prompt?: string;
  temperature?: number;
  max_tokens?: number;
  squad_id: number;
}

export interface UpdateAgentRequest {
  name?: string;
  description?: string;
  model?: string;
  system_prompt?: string;
  temperature?: number;
  max_tokens?: number;
}

// Execution Types
export type ExecutionStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";

export interface ExecutionResponse {
  id: number;
  task_id: number;
  task_title: string;
  agent_id?: number;
  agent_name?: string;
  status: ExecutionStatus;
  output?: string;
  error_message?: string;
  tokens_used: number;
  cost: number;
  started_at?: string;
  completed_at?: string;
  duration_seconds?: number;
  created_at: string;
}

export interface StartExecutionRequest {
  task_id: number;
  agent_id?: number;
}

// Live Session Types
export type LiveSessionStatus = "PENDING" | "ACTIVE" | "PAUSED" | "ENDED";

export interface LiveSessionResponse {
  id: number;
  code: string;
  task_id: number;
  task_title: string;
  host_user_id: number;
  host_user_name: string;
  container_id?: string;
  status: LiveSessionStatus;
  max_viewers: number;
  current_viewers: number;
  resolution: string;
  viewer_url: string;
  host_url: string;
  participants: ParticipantResponse[];
  created_at: string;
  ended_at?: string;
}

export interface ParticipantResponse {
  id: number;
  user_id: number;
  user_name: string;
  user_email: string;
  can_control: boolean;
  is_host: boolean;
  joined_at: string;
  left_at?: string;
}

export interface CreateLiveSessionRequest {
  task_id: number;
  container_id?: string;
  max_viewers?: number;
  resolution?: string;
}
