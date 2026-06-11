import { expect, test } from "./fixtures";
import { seedAuthenticatedSession } from "./helpers/mock-api";

test.beforeEach(async ({ page, apiState }) => {
  await seedAuthenticatedSession(page, apiState);
});

test("dark mode toggle cycles the theme and persists it", async ({ page }) => {
  await page.goto("/dashboard");

  const html = page.locator("html");
  const toggle = page.getByRole("button", { name: /^Tema:/ });

  // system -> light: sem classe dark
  await toggle.click();
  await expect(html).not.toHaveClass(/dark/);

  // light -> dark: classe dark aplicada
  await toggle.click();
  await expect(html).toHaveClass(/dark/);

  // persistido no localStorage: sobrevive a reload
  await page.reload();
  await expect(html).toHaveClass(/dark/);
});

test("mobile drawer opens the navigation and closes after navigating", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard");

  await page.getByRole("button", { name: "Abrir menu de navegação" }).click();

  const drawer = page.getByRole("dialog");
  await expect(drawer).toBeVisible();
  await expect(drawer.getByRole("navigation", { name: "Navegação principal" })).toBeVisible();

  await drawer.getByRole("link", { name: "Projects" }).click();
  await expect(page).toHaveURL(/\/projects/);
  await expect(drawer).not.toBeVisible();
});

test("sidebar shows pending approvals badge and live session indicator", async ({ page }) => {
  // approvals/pending não faz parte do mock global — stub local com 3 pendências
  await page.route("**/api/v1/approvals/pending*", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        success: true,
        data: {
          content: [],
          page_number: 0,
          page_size: 20,
          total_elements: 3,
          total_pages: 1,
          is_first: true,
          is_last: true,
        },
        timestamp: new Date().toISOString(),
      }),
    })
  );

  await page.goto("/dashboard");

  await expect(page.getByRole("status", { name: "3 aprovações pendentes" })).toBeVisible();
  // o mock global semeia uma sessão live ACTIVE — o dot deve aparecer
  await expect(page.getByRole("status", { name: "Sessões ao vivo ativas" })).toBeVisible();
});

test("cmd/ctrl+K focuses the global search", async ({ page }) => {
  await page.goto("/dashboard");

  const search = page.getByRole("searchbox", { name: "Buscar tasks, projetos e squads" });
  await expect(search).toBeVisible();

  await page.keyboard.press("ControlOrMeta+k");
  await expect(search).toBeFocused();
});
