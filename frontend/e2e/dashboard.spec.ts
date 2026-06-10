import { expect, test } from "./fixtures";
import { seedAuthenticatedSession } from "./helpers/mock-api";

test.beforeEach(async ({ page, apiState }) => {
  await seedAuthenticatedSession(page, apiState);
});

test("renders dashboard metrics for an authenticated user", async ({ page }) => {
  await page.goto("/dashboard");

  await expect(page.getByRole("heading", { name: /Welcome back/ })).toBeVisible();
  await expect(page.getByText("Total Projects")).toBeVisible();
  await expect(page.getByRole("heading", { name: "AI Squads" })).toBeVisible();
  await expect(page.getByText("Admin SquadX")).toBeVisible();
});
