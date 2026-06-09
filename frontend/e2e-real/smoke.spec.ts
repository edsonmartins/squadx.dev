import { expect, test } from "@playwright/test";
import { createTaskForAdmin, ensureAgentForAdmin, ensureProjectForAdmin, issueServiceToken, loginAsAdmin, seedRealSession } from "./helpers/auth";

test("real backend accepts admin login", async ({ page }) => {
  await page.goto("/login");

  await page.getByTestId("login-email").fill(process.env.E2E_ADMIN_EMAIL || "admin@squadx.dev");
  await page.getByTestId("login-password").fill(process.env.E2E_ADMIN_PASSWORD || "admin123");
  await page.getByTestId("login-submit").click();

  await expect(page).toHaveURL(/\/dashboard$/, { timeout: 15000 });
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
});

test("real backend exposes authenticated dashboard surfaces", async ({ page, request }) => {
  await seedRealSession(page, request);
  await page.goto("/dashboard");

  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByText("Total Projects")).toBeVisible();
  await expect(page.getByText("Admin User")).toBeVisible();

  await page.goto("/projects");
  await expect(page.getByRole("heading", { name: "Projects" })).toBeVisible();
  await expect(page.getByTestId("new-project-button")).toBeVisible();

  await page.goto("/tasks");
  await expect(page.getByRole("heading", { name: "Tasks" })).toBeVisible();
  await expect(page.getByTestId("new-task-button")).toBeVisible();

  await page.goto("/live");
  await expect(page.getByRole("heading", { name: "Live View" })).toBeVisible();

  await page.goto("/recordings");
  await expect(page.getByRole("heading", { name: "Recordings" })).toBeVisible();
});

test("real backend creates, edits and deletes a project from the UI", async ({ page, request }) => {
  await seedRealSession(page, request);
  await page.goto("/projects");

  const projectName = `E2E UI Project ${Date.now()}`;
  const updatedDescription = "Updated from the real E2E suite.";

  await page.getByTestId("new-project-button").click();
  await expect(page.getByTestId("project-modal")).toBeVisible();

  await page.getByTestId("project-name-input").fill(projectName);
  await page.getByTestId("project-description-input").fill("Created from the real E2E suite.");
  await page.getByTestId("project-repository-input").fill("https://github.com/squadx/e2e-ui-project");
  await page.getByTestId("project-default-branch-input").fill("develop");
  await page.getByTestId("project-submit-button").click();

  await expect(page.getByText("Your project has been created successfully.").first()).toBeVisible();
  await expect(page.getByText(projectName)).toBeVisible();

  const projectCard = page.locator('[data-testid^="project-card-"]').filter({ hasText: projectName }).first();
  const projectId = await projectCard.getAttribute("data-testid");

  expect(projectId).toMatch(/^project-card-\d+$/);

  const numericProjectId = projectId!.replace("project-card-", "");

  await page.getByTestId(`project-menu-trigger-${numericProjectId}`).click();
  await page.getByTestId(`project-edit-${numericProjectId}`).click();
  await expect(page.getByTestId("project-modal")).toBeVisible();

  await page.getByTestId("project-description-input").fill(updatedDescription);
  await page.getByTestId("project-submit-button").click();

  await expect(page.getByText("Your project has been updated successfully.").first()).toBeVisible();
  await expect(page.getByText(updatedDescription)).toBeVisible();

  await page.getByTestId(`project-menu-trigger-${numericProjectId}`).click();
  await page.getByTestId(`project-delete-${numericProjectId}`).click();
  await page.getByRole("button", { name: "Delete" }).click();

  await expect(page.getByText("The project has been deleted successfully.").first()).toBeVisible();
  await expect(page.getByText(projectName)).toHaveCount(0);
});

test("real backend renders an API-created task and opens it in the UI", async ({ page, request }) => {
  const { auth, project } = await ensureProjectForAdmin(request);
  const taskTitle = `E2E Task ${Date.now()}`;
  const taskDescription = "Task created by the real E2E suite.";

  const createTaskResponse = await request.post(`${process.env.E2E_API_URL || "http://127.0.0.1:8080"}/api/v1/tasks`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
    data: {
      title: taskTitle,
      description: taskDescription,
      status: "TODO",
      priority: "MEDIUM",
      project_id: project.id,
    },
  });

  expect(createTaskResponse.ok()).toBeTruthy();

  await seedRealSession(page, request);
  await page.goto("/tasks");

  await page.getByTestId(`project-filter-${project.id}`).click();
  await expect(page.getByText(taskTitle)).toBeVisible();

  await page.getByText(taskTitle).click();
  await expect(page.getByTestId("task-detail-sheet")).toBeVisible();
  await expect(page.getByTestId("task-detail-sheet").getByText(taskDescription)).toBeVisible();
});

test("real backend API login returns reusable auth payload", async ({ request }) => {
  const auth = await loginAsAdmin(request);

  expect(auth.user.email).toBe(process.env.E2E_ADMIN_EMAIL || "admin@squadx.dev");
  expect(auth.access_token.length).toBeGreaterThan(20);
  expect(auth.refresh_token.length).toBeGreaterThan(20);
});

test("real backend reconciles live session and recording webhook events", async ({ page, request }) => {
  const { auth, task } = await createTaskForAdmin(request, {
    title: `E2E Live Recording ${Date.now()}`,
    description: "Exercise live and recording webhook reconciliation.",
  });

  const apiUrl = process.env.E2E_API_URL || "http://127.0.0.1:8080";
  const serviceToken = issueServiceToken("squadx-live");

  const createSessionResponse = await request.post(`${apiUrl}/api/v1/live-view/sessions`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
    data: {
      taskId: task.id,
      maxViewers: 5,
      resolution: "1280x720",
    },
  });
  expect(createSessionResponse.ok()).toBeTruthy();
  const createSessionBody = await createSessionResponse.json();
  const sessionId = createSessionBody.data.id as number;

  const startSessionResponse = await request.post(`${apiUrl}/api/v1/live-view/sessions/${sessionId}/start`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
  });
  expect(startSessionResponse.ok()).toBeTruthy();
  const startedSession = (await startSessionResponse.json()).data as {
    id: number;
    externalSessionId?: string;
    status: string;
  };
  expect(startedSession.status).toBe("ACTIVE");
  const webhookSessionRef =
    startedSession.externalSessionId
      ? { sessionId: startedSession.externalSessionId }
      : { localSessionId: sessionId };

  const startRecordingResponse = await request.post(`${apiUrl}/api/v1/recordings/start`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
    data: {
      session_id: sessionId,
    },
  });
  expect(startRecordingResponse.ok()).toBeTruthy();

  const recordingWebhookResponse = await request.post(`${apiUrl}/api/v1/webhooks/live`, {
    headers: {
      Authorization: `Bearer ${serviceToken}`,
    },
    data: {
      event: "recording.ready",
      ...webhookSessionRef,
      durationSeconds: 120,
      fileSizeBytes: 5000,
      recordingUrl: "https://example.com/playback/reconciled.webm",
    },
  });
  expect(recordingWebhookResponse.ok()).toBeTruthy();

  const sessionEndedWebhookResponse = await request.post(`${apiUrl}/api/v1/webhooks/live`, {
    headers: {
      Authorization: `Bearer ${serviceToken}`,
    },
    data: {
      event: "session.ended",
      ...webhookSessionRef,
    },
  });
  expect(sessionEndedWebhookResponse.ok()).toBeTruthy();

  const recordingsResponse = await request.get(`${apiUrl}/api/v1/recordings/session/${sessionId}`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
  });
  expect(recordingsResponse.ok()).toBeTruthy();
  const recordings = (await recordingsResponse.json()).data as Array<{
    id: number;
    status: string;
    duration_seconds?: number;
    file_size_bytes?: number;
  }>;
  expect(recordings.length).toBeGreaterThan(0);
  expect(recordings[0].status).toBe("COMPLETED");
  expect(recordings[0].duration_seconds).toBe(120);
  expect(recordings[0].file_size_bytes).toBe(5000);
  const recordingId = recordings[0].id;

  const sessionByTaskResponse = await request.get(`${apiUrl}/api/v1/live-view/sessions/task/${task.id}`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
  });
  expect(sessionByTaskResponse.status()).toBe(404);

  await seedRealSession(page, request);
  await page.goto("/recordings");
  await expect(page.getByRole("heading", { name: "Recordings" })).toBeVisible();

  await page.getByTestId("recording-session-id-input").fill(String(sessionId));
  await page.getByTestId("recording-search-button").click();
  const recordingCard = page.getByTestId(`recording-card-${recordingId}`);
  await expect(recordingCard.getByText("COMPLETED", { exact: true })).toBeVisible();
  await expect(recordingCard.getByText("2m 0s")).toBeVisible();
});

test("real backend completes an execution through the daemon loop", async ({ request }) => {
  test.skip(process.env.E2E_DAEMON_SMOKE !== "1", "Daemon smoke is not enabled for this run.");

  const { auth, project, agent } = await ensureAgentForAdmin(request);
  const apiUrl = process.env.E2E_API_URL || "http://127.0.0.1:8080";
  const taskTitle = `E2E Daemon Execution ${Date.now()}`;

  const createTaskResponse = await request.post(`${apiUrl}/api/v1/tasks`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
    data: {
      title: taskTitle,
      description: "Exercise execution -> daemon -> completion flow.",
      status: "TODO",
      priority: "MEDIUM",
      project_id: project.id,
      assigned_agent_id: agent.id,
    },
  });
  expect(createTaskResponse.ok()).toBeTruthy();
  const task = (await createTaskResponse.json()).data as { id: number };

  const startExecutionResponse = await request.post(`${apiUrl}/api/v1/executions`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
    data: {
      task_id: task.id,
      agent_id: agent.id,
    },
  });
  expect(startExecutionResponse.ok()).toBeTruthy();
  const execution = (await startExecutionResponse.json()).data as { id: number };

  let finalStatus = "PENDING";
  let sessionId: string | undefined;
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const executionResponse = await request.get(`${apiUrl}/api/v1/executions/${execution.id}`, {
      headers: {
        Authorization: `Bearer ${auth.access_token}`,
      },
    });
    expect(executionResponse.ok()).toBeTruthy();
    const payload = (await executionResponse.json()).data as {
      status: string;
      result?: string;
      brain_sentry_session_id?: string;
    };
    finalStatus = payload.status;
    sessionId = payload.brain_sentry_session_id;
    if (finalStatus === "COMPLETED") {
      expect(payload.result).toContain("Smoke execution completed successfully.");
      break;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }

  expect(finalStatus).toBe("COMPLETED");
  if (process.env.E2E_BRAINSENTRY_ASSERT === "1") {
    expect(sessionId).toBeTruthy();
  }

  const taskResponse = await request.get(`${apiUrl}/api/v1/tasks/${task.id}`, {
    headers: {
      Authorization: `Bearer ${auth.access_token}`,
    },
  });
  expect(taskResponse.ok()).toBeTruthy();
  expect((await taskResponse.json()).data.status).toBe("IN_REVIEW");
});
