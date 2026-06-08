import { describe, it, expect, vi } from "vitest";
import { z } from "zod";
import { parseWithFallback, pageSchema, emptyPage } from "../schema";

const item = z.object({
  id: z.number(),
  name: z.string(),
  status: z.enum(["A", "B"]).catch("A"),
});

describe("parseWithFallback", () => {
  it("returns parsed data on a valid payload", () => {
    const data = { id: 1, name: "x", status: "B" };
    expect(parseWithFallback(item, data, { id: 0, name: "", status: "A" })).toEqual(
      data
    );
  });

  it("returns the fallback (and warns) on a malformed payload", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const fallback = { id: 0, name: "", status: "A" as const };

    expect(parseWithFallback(item, null, fallback)).toBe(fallback);
    expect(parseWithFallback(item, { id: "nope" }, fallback)).toBe(fallback);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it("downgrades an unknown enum value via .catch instead of failing", () => {
    const data = { id: 1, name: "x", status: "Z" };
    const result = parseWithFallback(item, data, { id: 0, name: "", status: "A" });
    expect(result.status).toBe("A");
    expect(result.id).toBe(1);
  });
});

describe("pageSchema", () => {
  const schema = pageSchema(item);

  it("parses a well-formed page", () => {
    const page = {
      content: [{ id: 1, name: "a", status: "A" }],
      page_number: 0,
      page_size: 20,
      total_elements: 1,
      total_pages: 1,
      is_first: true,
      is_last: true,
    };
    expect(parseWithFallback(schema, page, emptyPage()).content).toHaveLength(1);
  });

  it("falls back content to [] when content is not a valid array", () => {
    const page = { content: "not-an-array" };
    const result = parseWithFallback(schema, page, emptyPage());
    expect(result.content).toEqual([]);
    expect(result.is_last).toBe(true);
  });
});
