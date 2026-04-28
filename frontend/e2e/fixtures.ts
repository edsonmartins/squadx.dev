import { test as base, expect } from "@playwright/test";
import { createApiState, mockApi, type ApiState } from "./helpers/mock-api";

type Fixtures = {
  apiState: ApiState;
};

export const test = base.extend<Fixtures>({
  apiState: async ({ page }, use) => {
    const state = createApiState();
    await mockApi(page, state);
    await use(state);
  },
});

export { expect };
