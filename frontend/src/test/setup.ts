import '@testing-library/jest-dom/vitest'

// jsdom lacks ResizeObserver, which some Radix UI primitives (e.g. Switch) require.
if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}
