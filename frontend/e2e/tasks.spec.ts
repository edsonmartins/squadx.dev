import { expect, test } from "./fixtures";
import { seedAuthenticatedSession } from "./helpers/mock-api";

test.beforeEach(async ({ page, apiState }) => {
  await seedAuthenticatedSession(page, apiState);
});

test("opens an existing task and starts an execution from the detail sheet", async ({ page }) => {
  await page.goto("/tasks");

  await expect(page.getByRole("heading", { name: "Tasks" })).toBeVisible();

  await page.getByText("Build login flow").click();
  await expect(page.getByTestId("task-detail-sheet")).toBeVisible();
  await expect(
    page.getByTestId("task-detail-sheet").getByText("Finish the secure login experience.")
  ).toBeVisible();

  await page.getByTestId("start-execution-button").click();

  await expect(page.getByText("LIVE VIEW AVAILABLE")).toBeVisible();
  await expect(page.getByTestId("task-detail-sheet").getByText("Progress")).toBeVisible();
});
