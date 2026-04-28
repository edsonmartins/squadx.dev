import type { Page, Request, Route } from "@playwright/test";

export interface ApiState {
  user: {
    id: number;
    email: string;
    full_name: string;
    avatar_url?: string;
    role: string;
    is_active: boolean;
    email_verified: boolean;
    created_at: string;
    last_login_at?: string;
  };
  organization: {
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
  };
  projects: Array<Record<string, unknown>>;
  tasks: Array<Record<string, unknown>>;
  agents: Array<Record<string, unknown>>;
  executions: Array<Record<string, unknown>>;
  liveSessions: Array<Record<string, unknown>>;
  recordings: Array<Record<string, unknown>>;
  nextProjectId: number;
  nextTaskId: number;
  nextExecutionId: number;
  nextLiveSessionId: number;
  nextRecordingId: number;
}

const NOW = "2026-04-27T12:00:00.000Z";
const ACCESS_TOKEN = "e2e-access-token";
const REFRESH_TOKEN = "e2e-refresh-token";

function iso(minutesOffset = 0) {
  const date = new Date(NOW);
  date.setMinutes(date.getMinutes() + minutesOffset);
  return date.toISOString();
}

function slugify(value: string) {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function envelope(data: unknown, message?: string) {
  return {
    success: true,
    message,
    data,
    timestamp: new Date().toISOString(),
  };
}

function errorEnvelope(message: string, errors?: Record<string, string>) {
  return {
    success: false,
    message,
    errors,
    timestamp: new Date().toISOString(),
  };
}

function pageResponse<T>(items: T[]) {
  return {
    content: items,
    page_number: 0,
    page_size: Math.max(items.length, 1),
    total_elements: items.length,
    total_pages: items.length > 0 ? 1 : 0,
    is_first: true,
    is_last: true,
  };
}

function parseJsonBody(request: Request): Record<string, unknown> {
  const raw = request.postData();
  if (!raw) {
    return {};
  }

  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function syncProjectCounts(state: ApiState) {
  state.projects = state.projects.map((project) => {
    const tasksCount = state.tasks.filter(
      (task) => task.project_id === project.id
    ).length;
    return {
      ...project,
      tasks_count: tasksCount,
    };
  });
  state.organization.projects_count = state.projects.length;
}

async function fulfillJson(route: Route, status: number, body: unknown) {
  await route.fulfill({
    status,
    contentType: "application/json",
    headers: {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
      "access-control-allow-headers": "Content-Type, Authorization",
    },
    body: JSON.stringify(body),
  });
}

export function createApiState(): ApiState {
  return {
    user: {
      id: 1,
      email: "admin@squadx.dev",
      full_name: "Admin SquadX",
      role: "ADMIN",
      is_active: true,
      email_verified: true,
      created_at: iso(-10_000),
      last_login_at: iso(-15),
    },
    organization: {
      id: 1,
      name: "SquadX Labs",
      slug: "squadx-labs",
      description: "Internal product organization",
      is_active: true,
      members_count: 8,
      projects_count: 2,
      squads_count: 3,
      created_at: iso(-20_000),
      updated_at: iso(-100),
    },
    projects: [
      {
        id: 101,
        name: "Apollo Platform",
        slug: "apollo-platform",
        description: "Core web platform",
        repository_url: "https://github.com/squadx/apollo",
        default_branch: "main",
        is_active: true,
        organization_id: 1,
        organization_name: "SquadX Labs",
        squad_id: null,
        squad_name: undefined,
        tasks_count: 2,
        created_at: iso(-5000),
        updated_at: iso(-180),
      },
      {
        id: 102,
        name: "Beacon Mobile",
        slug: "beacon-mobile",
        description: "Mobile companion app",
        repository_url: "https://github.com/squadx/beacon-mobile",
        default_branch: "develop",
        is_active: true,
        organization_id: 1,
        organization_name: "SquadX Labs",
        squad_id: null,
        squad_name: undefined,
        tasks_count: 1,
        created_at: iso(-4000),
        updated_at: iso(-200),
      },
    ],
    tasks: [
      {
        id: 1001,
        title: "Build login flow",
        description: "Finish the secure login experience.",
        status: "TODO",
        priority: "HIGH",
        story_points: 5,
        estimated_hours: 8,
        actual_hours: undefined,
        due_date: undefined,
        started_at: undefined,
        completed_at: undefined,
        order_index: 0,
        project_id: 101,
        project_name: "Apollo Platform",
        assigned_agent_id: 501,
        assigned_agent_name: "Frontend Agent",
        parent_task_id: undefined,
        subtasks_count: 0,
        created_by_id: 1,
        created_by_name: "Admin SquadX",
        tags: ["auth", "web"],
        blocked: false,
        blocked_by_ids: [],
        dependent_ids: [],
        created_at: iso(-1500),
        updated_at: iso(-90),
      },
      {
        id: 1002,
        title: "Stabilize websocket stream",
        description: "Reduce reconnect churn during live sessions.",
        status: "IN_PROGRESS",
        priority: "URGENT",
        story_points: 8,
        estimated_hours: 12,
        actual_hours: 3,
        due_date: undefined,
        started_at: iso(-50),
        completed_at: undefined,
        order_index: 0,
        project_id: 101,
        project_name: "Apollo Platform",
        assigned_agent_id: 502,
        assigned_agent_name: "Realtime Agent",
        parent_task_id: undefined,
        subtasks_count: 2,
        created_by_id: 1,
        created_by_name: "Admin SquadX",
        tags: ["realtime", "streaming"],
        blocked: false,
        blocked_by_ids: [],
        dependent_ids: [],
        created_at: iso(-1400),
        updated_at: iso(-10),
      },
      {
        id: 1003,
        title: "Ship onboarding copy",
        description: "Finalize copy for mobile onboarding.",
        status: "DONE",
        priority: "MEDIUM",
        story_points: 3,
        estimated_hours: 4,
        actual_hours: 4,
        due_date: undefined,
        started_at: iso(-800),
        completed_at: iso(-700),
        order_index: 0,
        project_id: 102,
        project_name: "Beacon Mobile",
        assigned_agent_id: undefined,
        assigned_agent_name: undefined,
        parent_task_id: undefined,
        subtasks_count: 0,
        created_by_id: 1,
        created_by_name: "Admin SquadX",
        tags: ["copy"],
        blocked: false,
        blocked_by_ids: [],
        dependent_ids: [],
        created_at: iso(-1000),
        updated_at: iso(-700),
      },
    ],
    agents: [
      {
        id: 501,
        name: "Frontend Agent",
        type: "FRONTEND",
        description: "Builds UI",
        model: "gpt-5.4",
        temperature: 0.2,
        max_tokens: 16000,
        is_active: true,
        squad_id: 201,
        squad_name: "Web Squad",
        created_at: iso(-6000),
      },
      {
        id: 502,
        name: "Realtime Agent",
        type: "FULLSTACK",
        description: "Handles streaming",
        model: "gpt-5.4",
        temperature: 0.2,
        max_tokens: 16000,
        is_active: true,
        squad_id: 201,
        squad_name: "Web Squad",
        created_at: iso(-5900),
      },
    ],
    executions: [
      {
        id: 7001,
        task_id: 1002,
        task_title: "Stabilize websocket stream",
        agent_id: 502,
        agent_name: "Realtime Agent",
        status: "RUNNING",
        output: undefined,
        error_message: undefined,
        tokens_used: 12500,
        cost: 1.84,
        started_at: iso(-45),
        completed_at: undefined,
        duration_seconds: 900,
        created_at: iso(-45),
      },
      {
        id: 7000,
        task_id: 1003,
        task_title: "Ship onboarding copy",
        agent_id: 501,
        agent_name: "Frontend Agent",
        status: "COMPLETED",
        output: "Copy delivered.",
        error_message: undefined,
        tokens_used: 4300,
        cost: 0.58,
        started_at: iso(-800),
        completed_at: iso(-760),
        duration_seconds: 2400,
        created_at: iso(-800),
      },
    ],
    liveSessions: [
      {
        id: 9001,
        code: "live1234",
        task_id: 1002,
        task_title: "Stabilize websocket stream",
        host_user_id: 1,
        host_user_name: "Admin SquadX",
        container_id: "ctr-9001",
        status: "ACTIVE",
        max_viewers: 25,
        current_viewers: 3,
        resolution: "1920x1080",
        viewer_url: "http://127.0.0.1:3001/live/live1234",
        host_url: "http://127.0.0.1:3001/live/live1234?host=1",
        participants: [
          {
            id: 1,
            user_id: 1,
            user_name: "Admin SquadX",
            user_email: "admin@squadx.dev",
            can_control: true,
            is_host: true,
            joined_at: iso(-45),
            left_at: undefined,
          },
        ],
        created_at: iso(-45),
        ended_at: undefined,
      },
    ],
    recordings: [
      {
        id: 9501,
        session_id: 9001,
        s3_key: "recordings/live1234.mp4",
        s3_bucket: "squadx-recordings",
        upload_url: undefined,
        playback_url: "https://example.com/playback/live1234",
        duration_seconds: 780,
        file_size_bytes: 104857600,
        status: "COMPLETED",
        started_at: iso(-45),
        completed_at: iso(-20),
        created_at: iso(-45),
      },
    ],
    nextProjectId: 200,
    nextTaskId: 2000,
    nextExecutionId: 8000,
    nextLiveSessionId: 9900,
    nextRecordingId: 9990,
  };
}

export async function seedAuthenticatedSession(page: Page, state?: ApiState) {
  await page.addInitScript(
    ({ accessToken, refreshToken, user }) => {
      window.localStorage.setItem(
        "squadx-auth",
        JSON.stringify({
          state: {
            user,
            accessToken,
            refreshToken,
            isAuthenticated: true,
          },
          version: 0,
        })
      );
    },
    {
      accessToken: ACCESS_TOKEN,
      refreshToken: REFRESH_TOKEN,
      user: state?.user ?? null,
    }
  );
}

export async function mockApi(page: Page, state: ApiState) {
  await page.context().route("**/api/v1/**", async (route) => {
    syncProjectCounts(state);

    const request = route.request();
    const method = request.method();
    const url = new URL(request.url());
    const path = url.pathname;

    if (method === "OPTIONS") {
      await route.fulfill({
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
          "access-control-allow-headers": "Content-Type, Authorization",
        },
      });
      return;
    }

    if (path === "/api/v1/auth/login" && method === "POST") {
      const body = parseJsonBody(request) as { email?: string; password?: string };
      if (
        body.email === state.user.email &&
        typeof body.password === "string" &&
        body.password.length >= 8
      ) {
        await fulfillJson(
          route,
          200,
          envelope({
            access_token: ACCESS_TOKEN,
            refresh_token: REFRESH_TOKEN,
            token_type: "Bearer",
            expires_in: 3600,
            user: state.user,
          })
        );
        return;
      }

      await fulfillJson(route, 401, errorEnvelope("Invalid email or password"));
      return;
    }

    if (path === "/api/v1/auth/me" && method === "GET") {
      await fulfillJson(route, 200, envelope(state.user));
      return;
    }

    if (path === "/api/v1/organizations/my" && method === "GET") {
      await fulfillJson(route, 200, envelope(pageResponse([state.organization])));
      return;
    }

    if (path === `/api/v1/projects/organization/${state.organization.id}` && method === "GET") {
      await fulfillJson(route, 200, envelope(pageResponse(state.projects)));
      return;
    }

    if (path === "/api/v1/projects/my" && method === "GET") {
      await fulfillJson(route, 200, envelope(pageResponse(state.projects)));
      return;
    }

    if (path === "/api/v1/projects" && method === "POST") {
      const body = parseJsonBody(request);
      const project = {
        id: state.nextProjectId++,
        name: String(body.name),
        slug: slugify(String(body.name)),
        description: body.description ? String(body.description) : undefined,
        repository_url: body.repository_url ? String(body.repository_url) : undefined,
        default_branch: body.default_branch ? String(body.default_branch) : "main",
        is_active: true,
        organization_id: state.organization.id,
        organization_name: state.organization.name,
        squad_id: body.squad_id ?? null,
        squad_name: undefined,
        tasks_count: 0,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      };
      state.projects.push(project);
      syncProjectCounts(state);
      await fulfillJson(route, 200, envelope(project));
      return;
    }

    if (path.startsWith("/api/v1/projects/") && method === "PUT") {
      const projectId = Number(path.split("/").pop());
      const body = parseJsonBody(request);
      const project = state.projects.find((item) => item.id === projectId);
      if (!project) {
        await fulfillJson(route, 404, errorEnvelope("Project not found"));
        return;
      }
      Object.assign(project, {
        name: body.name ?? project.name,
        description: body.description ?? project.description,
        repository_url: body.repository_url ?? project.repository_url,
        default_branch: body.default_branch ?? project.default_branch,
        squad_id: body.squad_id ?? project.squad_id,
        updated_at: new Date().toISOString(),
      });
      project.slug = slugify(String(project.name));
      await fulfillJson(route, 200, envelope(project));
      return;
    }

    if (path.startsWith("/api/v1/projects/") && method === "DELETE") {
      const projectId = Number(path.split("/").pop());
      state.projects = state.projects.filter((item) => item.id !== projectId);
      state.tasks = state.tasks.filter((item) => item.project_id !== projectId);
      syncProjectCounts(state);
      await fulfillJson(route, 200, envelope({}));
      return;
    }

    if (path === `/api/v1/squads/organization/${state.organization.id}` && method === "GET") {
      await fulfillJson(route, 200, envelope(pageResponse([])));
      return;
    }

    if (path === `/api/v1/agents/organization/${state.organization.id}` && method === "GET") {
      await fulfillJson(route, 200, envelope(pageResponse(state.agents)));
      return;
    }

    const kanbanMatch = path.match(/^\/api\/v1\/tasks\/project\/(\d+)\/kanban$/);
    if (kanbanMatch && method === "GET") {
      const projectId = Number(kanbanMatch[1]);
      const tasks = state.tasks
        .filter((task) => task.project_id === projectId)
        .sort((a, b) => Number(a.order_index) - Number(b.order_index));
      await fulfillJson(route, 200, envelope(tasks));
      return;
    }

    if (path === "/api/v1/tasks" && method === "POST") {
      const body = parseJsonBody(request);
      const project = state.projects.find((item) => item.id === body.project_id);
      const assignedAgent =
        state.agents.find((item) => item.id === body.assigned_agent_id) || null;
      const task = {
        id: state.nextTaskId++,
        title: String(body.title),
        description: body.description ? String(body.description) : undefined,
        status: body.status ?? "TODO",
        priority: body.priority ?? "MEDIUM",
        story_points: body.story_points ?? undefined,
        estimated_hours: body.estimated_hours ?? undefined,
        actual_hours: undefined,
        due_date: body.due_date ?? undefined,
        started_at: undefined,
        completed_at: undefined,
        order_index: state.tasks.filter((item) => item.project_id === body.project_id).length,
        project_id: body.project_id,
        project_name: project?.name ?? "Unknown project",
        assigned_agent_id: assignedAgent?.id,
        assigned_agent_name: assignedAgent?.name,
        parent_task_id: undefined,
        subtasks_count: 0,
        created_by_id: state.user.id,
        created_by_name: state.user.full_name,
        tags: Array.isArray(body.tags) ? body.tags : [],
        blocked: false,
        blocked_by_ids: [],
        dependent_ids: [],
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      };
      state.tasks.push(task);
      syncProjectCounts(state);
      await fulfillJson(route, 200, envelope(task));
      return;
    }

    if (path.startsWith("/api/v1/tasks/") && method === "PUT") {
      const taskId = Number(path.split("/").pop());
      const body = parseJsonBody(request);
      const task = state.tasks.find((item) => item.id === taskId);
      if (!task) {
        await fulfillJson(route, 404, errorEnvelope("Task not found"));
        return;
      }
      const assignedAgent =
        state.agents.find((item) => item.id === body.assigned_agent_id) || null;
      Object.assign(task, {
        title: body.title ?? task.title,
        description: body.description ?? task.description,
        status: body.status ?? task.status,
        priority: body.priority ?? task.priority,
        story_points: body.story_points ?? task.story_points,
        estimated_hours: body.estimated_hours ?? task.estimated_hours,
        due_date: body.due_date ?? task.due_date,
        assigned_agent_id: body.assigned_agent_id ?? task.assigned_agent_id,
        assigned_agent_name: assignedAgent?.name ?? task.assigned_agent_name,
        tags: Array.isArray(body.tags) ? body.tags : task.tags,
        updated_at: new Date().toISOString(),
      });
      await fulfillJson(route, 200, envelope(task));
      return;
    }

    const statusMatch = path.match(/^\/api\/v1\/tasks\/(\d+)\/status$/);
    if (statusMatch && method === "PATCH") {
      const taskId = Number(statusMatch[1]);
      const status = url.searchParams.get("status");
      const task = state.tasks.find((item) => item.id === taskId);
      if (!task || !status) {
        await fulfillJson(route, 404, errorEnvelope("Task not found"));
        return;
      }
      task.status = status;
      task.updated_at = new Date().toISOString();
      await fulfillJson(route, 200, envelope(task));
      return;
    }

    if (path.startsWith("/api/v1/tasks/") && method === "DELETE") {
      const taskId = Number(path.split("/").pop());
      state.tasks = state.tasks.filter((item) => item.id !== taskId);
      state.executions = state.executions.filter((item) => item.task_id !== taskId);
      state.liveSessions = state.liveSessions.filter((item) => item.task_id !== taskId);
      syncProjectCounts(state);
      await fulfillJson(route, 200, envelope({}));
      return;
    }

    if (path === "/api/v1/executions" && method === "POST") {
      const body = parseJsonBody(request) as { task_id: number };
      const task = state.tasks.find((item) => item.id === body.task_id);
      if (!task) {
        await fulfillJson(route, 404, errorEnvelope("Task not found"));
        return;
      }

      task.status = "IN_PROGRESS";
      task.started_at = new Date().toISOString();
      task.updated_at = new Date().toISOString();

      const execution = {
        id: state.nextExecutionId++,
        task_id: task.id,
        task_title: task.title,
        agent_id: task.assigned_agent_id,
        agent_name: task.assigned_agent_name ?? "Auto Agent",
        status: "RUNNING",
        output: undefined,
        error_message: undefined,
        tokens_used: 0,
        cost: 0,
        started_at: new Date().toISOString(),
        completed_at: undefined,
        duration_seconds: 0,
        created_at: new Date().toISOString(),
      };

      state.executions.unshift(execution);

      const existingSession = state.liveSessions.find((item) => item.task_id === task.id);
      if (!existingSession) {
        const liveSession = {
          id: state.nextLiveSessionId++,
          code: `task${task.id}`,
          task_id: task.id,
          task_title: task.title,
          host_user_id: state.user.id,
          host_user_name: state.user.full_name,
          container_id: `ctr-${task.id}`,
          status: "ACTIVE",
          max_viewers: 25,
          current_viewers: 1,
          resolution: "1920x1080",
          viewer_url: `http://127.0.0.1:3001/live/task${task.id}`,
          host_url: `http://127.0.0.1:3001/live/task${task.id}?host=1`,
          participants: [
            {
              id: 1,
              user_id: state.user.id,
              user_name: state.user.full_name,
              user_email: state.user.email,
              can_control: true,
              is_host: true,
              joined_at: new Date().toISOString(),
              left_at: undefined,
            },
          ],
          created_at: new Date().toISOString(),
          ended_at: undefined,
        };
        state.liveSessions.unshift(liveSession);
      }

      await fulfillJson(route, 200, envelope(execution));
      return;
    }

    const taskExecutionsMatch = path.match(/^\/api\/v1\/executions\/task\/(\d+)$/);
    if (taskExecutionsMatch && method === "GET") {
      const taskId = Number(taskExecutionsMatch[1]);
      const executions = state.executions.filter((item) => item.task_id === taskId);
      await fulfillJson(route, 200, envelope(pageResponse(executions)));
      return;
    }

    const orgExecutionsMatch = path.match(/^\/api\/v1\/executions\/organization\/(\d+)$/);
    if (orgExecutionsMatch && method === "GET") {
      await fulfillJson(route, 200, envelope(pageResponse(state.executions)));
      return;
    }

    const metricsMatch = path.match(/^\/api\/v1\/executions\/organization\/(\d+)\/metrics$/);
    if (metricsMatch && method === "GET") {
      const totalCost = state.executions.reduce(
        (sum, item) => sum + Number(item.cost ?? 0),
        0
      );
      const totalTokens = state.executions.reduce(
        (sum, item) => sum + Number(item.tokens_used ?? 0),
        0
      );
      await fulfillJson(
        route,
        200,
        envelope({
          total_input_tokens: Math.round(totalTokens * 0.6),
          total_output_tokens: Math.round(totalTokens * 0.4),
          total_cost: totalCost,
        })
      );
      return;
    }

    const taskSessionMatch = path.match(/^\/api\/v1\/live-view\/sessions\/task\/(\d+)$/);
    if (taskSessionMatch && method === "GET") {
      const taskId = Number(taskSessionMatch[1]);
      const session = state.liveSessions.find((item) => item.task_id === taskId);
      if (!session) {
        await fulfillJson(route, 404, errorEnvelope("Session not found"));
      } else {
        await fulfillJson(route, 200, envelope(session));
      }
      return;
    }

    const orgSessionMatch = path.match(/^\/api\/v1\/live-view\/sessions\/organization\/(\d+)$/);
    if (orgSessionMatch && method === "GET") {
      const activeSessions = state.liveSessions.filter((item) => item.status !== "ENDED");
      await fulfillJson(route, 200, envelope(activeSessions));
      return;
    }

    if (path === "/api/v1/live-view/supabase/sessions/active" && method === "GET") {
      const activeSessions = state.liveSessions.filter((item) => item.status !== "ENDED");
      await fulfillJson(route, 200, envelope(activeSessions));
      return;
    }

    const sessionByCodeMatch = path.match(/^\/api\/v1\/live-view\/sessions\/code\/([^/]+)$/);
    if (sessionByCodeMatch && method === "GET") {
      const session = state.liveSessions.find((item) => item.code === sessionByCodeMatch[1]);
      if (!session) {
        await fulfillJson(route, 404, errorEnvelope("Session not found"));
      } else {
        await fulfillJson(route, 200, envelope(session));
      }
      return;
    }

    const supabaseSessionByCodeMatch = path.match(
      /^\/api\/v1\/live-view\/supabase\/sessions\/code\/([^/]+)$/
    );
    if (supabaseSessionByCodeMatch && method === "GET") {
      const session = state.liveSessions.find(
        (item) => item.code === supabaseSessionByCodeMatch[1]
      );
      if (!session) {
        await fulfillJson(route, 404, errorEnvelope("Session not found"));
      } else {
        await fulfillJson(route, 200, envelope(session));
      }
      return;
    }

    const sessionRecordingsMatch = path.match(/^\/api\/v1\/recordings\/session\/(\d+)$/);
    if (sessionRecordingsMatch && method === "GET") {
      const sessionId = Number(sessionRecordingsMatch[1]);
      const recordings = state.recordings.filter((item) => item.session_id === sessionId);
      await fulfillJson(route, 200, envelope(recordings));
      return;
    }

    const recordingUrlMatch = path.match(/^\/api\/v1\/recordings\/(\d+)\/url$/);
    if (recordingUrlMatch && method === "GET") {
      const recordingId = Number(recordingUrlMatch[1]);
      const recording = state.recordings.find((item) => item.id === recordingId);
      if (!recording) {
        await fulfillJson(route, 404, errorEnvelope("Recording not found"));
      } else {
        await fulfillJson(route, 200, envelope(recording));
      }
      return;
    }

    await fulfillJson(route, 404, errorEnvelope(`Unhandled mocked endpoint: ${method} ${path}`));
  });
}
