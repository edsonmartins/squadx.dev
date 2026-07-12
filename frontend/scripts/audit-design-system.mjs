#!/usr/bin/env node
/**
 * Design-system audit (ratchet).
 * -----------------------------------------------------------------------------
 * Flags raw styling that should go through the semantic token system
 * (src/styles/tokens.css + Tailwind semantic utilities). Inspired by Lemma's
 * scripts/audit-design-system.mjs.
 *
 * It is a RATCHET, not a wall: existing violations are recorded in
 * design-audit-baseline.json (count per rule). CI fails only if a rule's count
 * grows above its baseline — so debt can shrink but never grow. New code must
 * use tokens; legacy code is migrated opportunistically.
 *
 * Usage:
 *   node scripts/audit-design-system.mjs           # report (non-baseline)
 *   node scripts/audit-design-system.mjs --ci      # fail if any rule > baseline
 *   node scripts/audit-design-system.mjs --update  # rewrite baseline to current
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { readdirSync, statSync } from "node:fs";
import { join, relative, extname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(fileURLToPath(new URL(".", import.meta.url)), "..");
const SRC = join(ROOT, "src");
const BASELINE_PATH = join(ROOT, "design-audit-baseline.json");

// Files/dirs exempt from the audit.
const IGNORE = [
  "src/components/ui", // shadcn primitives — owned upstream, tokenized via CSS vars
  "src/styles/tokens.css",
  "src/app/globals.css",
  "__tests__",
  ".test.",
];

// Rules: id → { test(line), message }. Kept deliberately small and high-signal.
const TAILWIND_PALETTE =
  "slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose";
const RULES = [
  {
    id: "rawHex",
    // hex colors written inline in className/style (allow in .css token files, which are ignored)
    re: /#[0-9a-fA-F]{3,8}\b/,
    message: "raw hex color — use a semantic token (bg-intelligence, text-attention, …)",
  },
  {
    id: "rawPaletteColor",
    // Tailwind palette utility with a numeric shade, e.g. bg-blue-500, text-red-600, border-l-slate-400
    re: new RegExp(`\\b(?:bg|text|border|ring|from|to|via|fill|stroke)(?:-[a-z]+)?-(?:${TAILWIND_PALETTE})-\\d{2,3}\\b`),
    message: "raw palette color — map the meaning to a semantic token/status-token instead",
  },
  {
    id: "arbitraryColor",
    // arbitrary color value: text-[#fff], bg-[rgb(...)], border-[hsl(...)]
    re: /\b(?:bg|text|border|ring|fill|stroke)-\[(?:#|rgb|hsl)/,
    message: "arbitrary color value — promote it to a token",
  },
];

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const rel = relative(ROOT, full);
    if (IGNORE.some((frag) => rel.includes(frag))) continue;
    const s = statSync(full);
    if (s.isDirectory()) walk(full, out);
    else if ([".tsx", ".ts", ".jsx", ".js"].includes(extname(full))) out.push(full);
  }
  return out;
}

function audit() {
  const counts = {};
  const findings = [];
  for (const rule of RULES) counts[rule.id] = 0;

  for (const file of walk(SRC)) {
    const lines = readFileSync(file, "utf8").split("\n");
    lines.forEach((line, i) => {
      for (const rule of RULES) {
        if (rule.re.test(line)) {
          counts[rule.id]++;
          findings.push({ rule: rule.id, file: relative(ROOT, file), line: i + 1, message: rule.message });
        }
      }
    });
  }
  return { counts, findings };
}

const mode = process.argv.includes("--ci") ? "ci" : process.argv.includes("--update") ? "update" : "report";
const { counts, findings } = audit();

if (mode === "update") {
  writeFileSync(BASELINE_PATH, JSON.stringify(counts, null, 2) + "\n");
  console.log("✔ baseline updated:", JSON.stringify(counts));
  process.exit(0);
}

const baseline = existsSync(BASELINE_PATH) ? JSON.parse(readFileSync(BASELINE_PATH, "utf8")) : {};
let regressed = false;
for (const rule of RULES) {
  const now = counts[rule.id];
  const base = baseline[rule.id] ?? 0;
  const status = now > base ? "✗ REGRESSED" : now < base ? "↓ improved" : "· ok";
  if (now > base) regressed = true;
  console.log(`${status.padEnd(12)} ${rule.id.padEnd(18)} ${now} (baseline ${base})`);
}

if (mode === "ci" && regressed) {
  console.error("\nDesign-system audit failed: a rule exceeded its baseline.");
  console.error("Fix the new violations, or run `--update` intentionally if the baseline should move.\n");
  const worst = findings.filter((f) => counts[f.rule] > (baseline[f.rule] ?? 0)).slice(0, 25);
  for (const f of worst) console.error(`  ${f.file}:${f.line}  [${f.rule}] ${f.message}`);
  process.exit(1);
}
console.log(`\n${findings.length} total occurrence(s) across ${RULES.length} rules.`);
