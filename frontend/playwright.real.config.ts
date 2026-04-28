import { defineConfig, devices } from "@playwright/test";

const port = Number(process.env.E2E_FRONTEND_PORT || 3002);
const apiUrl = process.env.E2E_API_URL || "http://127.0.0.1:8080";

export default defineConfig({
  testDir: "./e2e-real",
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [["html"], ["github"]]
    : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: "on-first-retry",
    video: "retain-on-failure",
  },
  webServer: {
    command: `NEXT_PUBLIC_API_URL=${apiUrl} NEXT_PUBLIC_WS_URL=${apiUrl}/ws NEXT_PUBLIC_DISABLE_REALTIME=true pnpm dev --hostname 127.0.0.1 --port ${port}`,
    port,
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
