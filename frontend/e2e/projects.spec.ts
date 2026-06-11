import { expect, test } from "./fixtures";
import { seedAuthenticatedSession } from "./helpers/mock-api";

test.beforeEach(async ({ page, apiState }) => {
  await seedAuthenticatedSession(page, apiState);
});

test("creates, edits and deletes a project", async ({ page }) => {
  await page.goto("/projects");

  await page.getByTestId("new-project-button").click();
  await expect(page.getByTestId("project-modal")).toBeVisible();

  await page.getByTestId("project-name-input").fill("Gamma QA Portal");
  await page.getByTestId("project-description-input").fill("Portal for QA coverage.");
  await page.getByTestId("project-repository-input").fill("https://github.com/squadx/gamma-qa");
  await page.getByTestId("project-default-branch-input").fill("release");
  await page.getByTestId("project-submit-button").click();

  // exact:true evita colisão com o announcement aria-live do toast ("Notification Project created…")
  await expect(page.getByText("Project created", { exact: true })).toBeVisible();
  await expect(page.getByText("Gamma QA Portal")).toBeVisible();

  const projectCard = page.getByText("Gamma QA Portal").locator("..").locator("..").locator("..");
  await projectCard.hover();
  await projectCard.getByRole("button").click();
  await page.getByRole("menuitem", { name: "Edit" }).click();

  await page.getByTestId("project-description-input").fill("Updated QA portal description.");
  await page.getByTestId("project-submit-button").click();

  await expect(page.getByText("Updated QA portal description.")).toBeVisible();

  await projectCard.hover();
  await projectCard.getByRole("button").click();
  await page.getByRole("menuitem", { name: "Delete" }).click();
  await page.getByRole("button", { name: "Delete" }).click();

  await expect(page.getByText("Project deleted").first()).toBeVisible();
  await expect(page.getByText("Gamma QA Portal")).toHaveCount(0);
});
