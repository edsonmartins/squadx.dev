import { expect, test } from "./fixtures";
import { seedAuthenticatedSession } from "./helpers/mock-api";

test.beforeEach(async ({ page, apiState }) => {
  await seedAuthenticatedSession(page, apiState);
});

test("lists live sessions and opens the live room via join code", async ({ page }) => {
  await page.goto("/live");

  await expect(page.getByRole("heading", { name: "Live View" })).toBeVisible();
  await expect(page.getByText("Stabilize websocket stream")).toBeVisible();

  const popupPromise = page.waitForEvent("popup");
  await page.getByTestId("join-session-input").fill("live1234");
  await page.getByTestId("join-session-button").click();
  const popup = await popupPromise;

  await popup.waitForLoadState();
  await expect(popup).toHaveURL(/\/live\/live1234$/);
  await expect(popup.getByRole("heading", { name: "Stabilize websocket stream" })).toBeVisible();
  await expect(popup.getByText("Hosted by Admin SquadX")).toBeVisible();

  await popup.getByPlaceholder("Type a message...").fill("Watching the stream");
  await popup.getByPlaceholder("Type a message...").press("Enter");
  await expect(popup.getByText("Watching the stream")).toBeVisible();
});

test("shows recordings and opens playback for completed items", async ({ page }) => {
  await page.goto("/recordings");

  await expect(page.getByRole("heading", { name: "Recordings" })).toBeVisible();
  await expect(page.getByTestId("recording-card-9501")).toBeVisible();

  await page.getByTestId("recording-session-id-input").fill("9001");
  await page.getByTestId("recording-search-button").click();
  await expect(page.getByText("Session #9001")).toBeVisible();

  const popupPromise = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Watch" }).click();
  const popup = await popupPromise;
  await popup.waitForLoadState("domcontentloaded");
  await expect(popup).toHaveURL(/example\.com\/playback\/live1234/);
});
