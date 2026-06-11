import { defineConfig } from "@playwright/test";
import baseConfig from "./playwright.config";

/**
 * Execução visual da suíte e2e: browser visível, 1 teste por vez e
 * slow motion para acompanhar cada ação. Uso:
 *   npx playwright test -c playwright.headed.config.ts
 */
export default defineConfig({
  ...baseConfig,
  fullyParallel: false,
  workers: 1,
  use: {
    ...baseConfig.use,
    headless: false,
    launchOptions: {
      slowMo: 700,
    },
    viewport: { width: 1440, height: 860 },
  },
});
