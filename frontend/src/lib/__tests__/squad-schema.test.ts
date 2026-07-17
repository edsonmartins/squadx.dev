/**
 * Schema drift tests for the squad response (CLAUDE.md: "parse, don't cast").
 *
 * Desktop (Tauri) and mobile (Expo) builds are installed and outlive any given
 * backend, so these payloads WILL drift. The contract is that drift downgrades —
 * it must never white-screen, and for a security control it must never silently
 * become "no policy".
 */

import { describe, expect, it } from "vitest";
import { z } from "zod";

import { parseWithFallback } from "../schema";

// Mirrors the sandbox_egress_policy field of squadResponseSchema in api.ts. The
// schema itself is not exported, so the shape under test is reproduced here; the
// point is to pin the drift behaviour of this specific field.
const policyField = z
  .enum(["AGENT_DEFAULT", "DENY_ALL", "FULL"])
  .catch("AGENT_DEFAULT")
  .optional();

const squadPolicySchema = z.object({
  id: z.number(),
  name: z.string(),
  sandbox_egress_policy: policyField,
});

const fallback = { id: 0, name: "", sandbox_egress_policy: "AGENT_DEFAULT" as const };

describe("squad sandbox_egress_policy schema drift", () => {
  it("parses a well-formed payload", () => {
    const parsed = parseWithFallback(
      squadPolicySchema,
      { id: 1, name: "Backend", sandbox_egress_policy: "DENY_ALL" },
      fallback
    );
    expect(parsed.sandbox_egress_policy).toBe("DENY_ALL");
  });

  it("accepts a payload from a backend that predates the field", () => {
    const parsed = parseWithFallback(
      squadPolicySchema,
      { id: 1, name: "Backend" },
      fallback
    );
    expect(parsed.id).toBe(1);
    expect(parsed.sandbox_egress_policy).toBeUndefined();
  });

  it("downgrades an unknown policy to the safe default rather than failing the parse", () => {
    // A newer backend adds a policy this build has never heard of. Losing the whole
    // squad object over one unknown enum value would white-screen the page.
    const parsed = parseWithFallback(
      squadPolicySchema,
      { id: 1, name: "Backend", sandbox_egress_policy: "SOME_FUTURE_POLICY" },
      fallback
    );
    expect(parsed.id).toBe(1);
    expect(parsed.sandbox_egress_policy).toBe("AGENT_DEFAULT");
  });

  it("downgrades a wrong-typed policy rather than throwing", () => {
    const parsed = parseWithFallback(
      squadPolicySchema,
      { id: 1, name: "Backend", sandbox_egress_policy: 42 },
      fallback
    );
    expect(parsed.sandbox_egress_policy).toBe("AGENT_DEFAULT");
  });

  it("returns the fallback for a structurally broken payload", () => {
    const parsed = parseWithFallback(squadPolicySchema, null, fallback);
    expect(parsed).toBe(fallback);
  });

  it("never yields an unrestricted policy from malformed input", () => {
    // The property that matters: drift must not be able to widen network access.
    for (const bad of [undefined, null, 42, "FULL_ACCESS", {}, []]) {
      const parsed = parseWithFallback(
        squadPolicySchema,
        { id: 1, name: "S", sandbox_egress_policy: bad },
        fallback
      );
      expect(parsed.sandbox_egress_policy).not.toBe("FULL");
    }
  });
});
