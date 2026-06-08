import { z } from "zod";

/**
 * Validate an API response against a zod schema, returning a safe fallback
 * instead of throwing when the shape drifts.
 *
 * Installed clients (Tauri/Expo) outlive any given backend, so response shapes
 * WILL drift. Parse, don't cast: on validation failure this logs a warning and
 * returns the fallback — it never throws into the UI. See CLAUDE.md
 * "API Response Compatibility".
 */
export function parseWithFallback<T>(
  schema: z.ZodType<T>,
  data: unknown,
  fallback: T
): T {
  const result = schema.safeParse(data);
  if (result.success) {
    return result.data;
  }
  if (typeof console !== "undefined") {
    console.warn(
      "[api] response validation failed; using fallback",
      result.error.issues
    );
  }
  return fallback;
}

/**
 * Schema for the backend's paginated wrapper. Non-content fields use `.catch`
 * so a malformed page still yields a usable PageResponse rather than failing.
 */
export function pageSchema<T>(item: z.ZodType<T>) {
  return z.object({
    content: z.array(item).catch([]),
    page_number: z.number().catch(0),
    page_size: z.number().catch(0),
    total_elements: z.number().catch(0),
    total_pages: z.number().catch(0),
    is_first: z.boolean().catch(true),
    is_last: z.boolean().catch(true),
  });
}

/** An empty, valid page — the fallback for list endpoints. */
export function emptyPage<T>() {
  return {
    content: [] as T[],
    page_number: 0,
    page_size: 0,
    total_elements: 0,
    total_pages: 0,
    is_first: true,
    is_last: true,
  };
}
