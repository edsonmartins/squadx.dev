import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ExecutionLogs } from "../execution-logs";
import type { ExecutionLogEntry } from "@/lib/api";

// The socket hook is a no-op in tests; we drive the component via initialLogs.
vi.mock("@/hooks/use-socket", () => ({
  useExecutionLogsSocket: vi.fn(),
}));

const logs: ExecutionLogEntry[] = [
  { level: "INFO", visibility: "human", importance: "high", message: "run completed" },
  { level: "DEBUG", visibility: "debug", importance: "low", message: "tool log noise" },
  { level: "INFO", visibility: "audit", importance: "normal", message: "admission decided" },
  { level: "INFO", message: "legacy log without visibility" },
];

describe("ExecutionLogs", () => {
  it("shows only human-facing logs by default (quiet mode)", () => {
    render(<ExecutionLogs executionId={1} initialLogs={logs} />);

    // human + the legacy log (no visibility defaults to human) are shown
    expect(screen.getByText("run completed")).toBeInTheDocument();
    expect(screen.getByText("legacy log without visibility")).toBeInTheDocument();
    // audit/debug are hidden
    expect(screen.queryByText("tool log noise")).not.toBeInTheDocument();
    expect(screen.queryByText("admission decided")).not.toBeInTheDocument();
    // and it reports how many are hidden
    expect(screen.getByText("2 ocultos")).toBeInTheDocument();
  });

  it("reveals audit and debug logs when Auditoria is toggled on", async () => {
    const user = userEvent.setup();
    render(<ExecutionLogs executionId={1} initialLogs={logs} />);

    await user.click(screen.getByRole("switch", { name: /auditoria/i }));

    expect(screen.getByText("tool log noise")).toBeInTheDocument();
    expect(screen.getByText("admission decided")).toBeInTheDocument();
    expect(screen.getByText("run completed")).toBeInTheDocument();
  });

  it("renders an empty-state hint in quiet mode when nothing is human-facing", () => {
    const auditOnly: ExecutionLogEntry[] = [
      { level: "DEBUG", visibility: "debug", message: "internal" },
    ];
    render(<ExecutionLogs executionId={1} initialLogs={auditOnly} />);

    expect(screen.getByText(/Ative Auditoria para ver tudo/i)).toBeInTheDocument();
  });
});
