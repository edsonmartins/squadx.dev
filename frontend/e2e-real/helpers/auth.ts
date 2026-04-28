import type { APIRequestContext, Page } from "@playwright/test";
import { createHmac } from "node:crypto";

const apiUrl = process.env.E2E_API_URL || "http://127.0.0.1:8080";
const adminEmail = process.env.E2E_ADMIN_EMAIL || "admin@squadx.dev";
const adminPassword = process.env.E2E_ADMIN_PASSWORD || "admin123";

interface AuthPayload {
  access_token: string;
  refresh_token: string;
  user: {
    id: number;
    email: string;
    full_name: string;
    role: string;
    is_active: boolean;
    email_verified: boolean;
    created_at: string;
    last_login_at?: string;
  };
}

interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data?: T;
  errors?: Record<string, string>;
}

interface PageResponse<T> {
  content: T[];
}

interface OrganizationResponse {
  id: number;
  name: string;
}

interface ProjectResponse {
  id: number;
  name: string;
}

interface TaskResponse {
  id: number;
  title: string;
  project_id: number;
}

let cachedAuth: AuthPayload | null = null;
let cachedProject: ProjectResponse | null = null;

export async function loginAsAdmin(request: APIRequestContext) {
  if (cachedAuth) {
    return cachedAuth;
  }

  const response = await request.post(`${apiUrl}/api/v1/auth/login`, {
    data: {
      email: adminEmail,
      password: adminPassword,
    },
  });

  if (!response.ok()) {
    throw new Error(`Real login failed with status ${response.status()}`);
  }

  const body = (await response.json()) as ApiEnvelope<AuthPayload>;

  if (!body.data?.access_token || !body.data?.refresh_token || !body.data?.user) {
    throw new Error("Real login response did not include tokens and user");
  }

  cachedAuth = body.data;

  return body.data;
}

export async function ensureProjectForAdmin(request: APIRequestContext) {
  if (cachedAuth && cachedProject) {
    return { auth: cachedAuth, project: cachedProject };
  }

  const auth = await loginAsAdmin(request);

  const organizationsResponse = await request.get(`${apiUrl}/api/v1/organizations/my`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
  });

  if (!organizationsResponse.ok()) {
    throw new Error(`Failed to load organizations with status ${organizationsResponse.status()}`);
  }

  const organizationsBody = (await organizationsResponse.json()) as ApiEnvelope<PageResponse<OrganizationResponse>>;
  const organizationId = organizationsBody.data?.content?.[0]?.id;

  if (!organizationId) {
    throw new Error("No organization available for real E2E project seeding");
  }

  const projectsResponse = await request.get(`${apiUrl}/api/v1/projects/my`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
  });

  if (!projectsResponse.ok()) {
    throw new Error(`Failed to load projects with status ${projectsResponse.status()}`);
  }

  const projectsBody = (await projectsResponse.json()) as ApiEnvelope<PageResponse<ProjectResponse>>;
  const existingProject = projectsBody.data?.content?.[0];

  if (existingProject) {
    cachedProject = existingProject;
    return { auth, project: existingProject };
  }

  const createResponse = await request.post(`${apiUrl}/api/v1/projects`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
    data: {
      name: `E2E Real Project ${Date.now()}`,
      description: "Project seeded by the real E2E smoke suite",
      organization_id: organizationId,
    },
  });

  if (!createResponse.ok()) {
    throw new Error(`Failed to create project with status ${createResponse.status()}`);
  }

  const createBody = (await createResponse.json()) as ApiEnvelope<ProjectResponse>;

  if (!createBody.data?.id) {
    throw new Error("Project creation response did not include a project id");
  }

  cachedProject = createBody.data;

  return { auth, project: createBody.data };
}

export async function seedRealSession(page: Page, request: APIRequestContext) {
  const { auth } = await ensureProjectForAdmin(request);

  await page.addInitScript((session) => {
    window.localStorage.setItem(
      "squadx-auth",
      JSON.stringify({
        state: {
          user: session.user,
          accessToken: session.accessToken,
          refreshToken: session.refreshToken,
          isAuthenticated: true,
        },
        version: 0,
      })
    );
  }, {
    user: auth.user,
    accessToken: auth.access_token,
    refreshToken: auth.refresh_token,
  });

  return auth;
}

export async function createTaskForAdmin(
  request: APIRequestContext,
  overrides: Partial<{
    title: string;
    description: string;
    status: string;
    priority: string;
  }> = {}
) {
  const { auth, project } = await ensureProjectForAdmin(request);
  const response = await request.post(`${apiUrl}/api/v1/tasks`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
    data: {
      title: overrides.title || `E2E Task ${Date.now()}`,
      description: overrides.description || "Task created by the real E2E suite.",
      status: overrides.status || "TODO",
      priority: overrides.priority || "MEDIUM",
      project_id: project.id,
    },
  });

  if (!response.ok()) {
    throw new Error(`Failed to create task with status ${response.status()}`);
  }

  const body = (await response.json()) as ApiEnvelope<TaskResponse>;
  if (!body.data?.id) {
    throw new Error("Task creation response did not include a task id");
  }

  return { auth, project, task: body.data };
}

export function issueServiceToken(issuer: string) {
  const secret = process.env.E2E_SERVICE_SECRET;
  if (!secret) {
    throw new Error("E2E_SERVICE_SECRET is not configured");
  }

  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64Url(JSON.stringify({
    iss: issuer,
    sub: "service",
    iat: now,
    exp: now + 300,
  }));
  const data = `${header}.${payload}`;

  const signature = createHmac("sha256", secret)
    .update(data)
    .digest("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");

  return `${data}.${signature}`;
}

function base64Url(value: string) {
  return Buffer.from(value)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}
