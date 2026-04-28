import { expect, test } from "./fixtures";

test("shows login validation errors before submission", async ({ page }) => {
  await page.goto("/login");

  await page.getByTestId("login-submit").click();

  await expect(page.getByText("Invalid email address")).toBeVisible();
  await expect(page.getByText("Password must be at least 8 characters")).toBeVisible();
});

test("shows an error on invalid credentials", async ({ page }) => {
  await page.goto("/login");

  await page.getByTestId("login-email").fill("wrong@squadx.dev");
  await page.getByTestId("login-password").fill("admin123");
  await page.getByTestId("login-submit").click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText("Login failed").first()).toBeVisible();
});
