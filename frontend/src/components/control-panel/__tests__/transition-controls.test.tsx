import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TransitionControls } from "../transition-controls";
import type { SpecTaskResponse } from "@/lib/api";

vi.mock("@/lib/api", () => ({
  specTasksApi: { transition: vi.fn().mockResolvedValue({}) },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const task = (status: SpecTaskResponse["status"]): SpecTaskResponse => ({
  id: 1,
  title: "T",
  status,
  change_id: 5,
  pass5: "PENDING",
  created_at: "2026-06-09T00:00:00Z",
});

describe("TransitionControls", () => {
  beforeEach(() => vi.clearAllMocks());

  it("offers only valid manual transitions (no Concluir/Ajustes)", () => {
    renderWithProviders(<TransitionControls task={task("EM_VALIDACAO")} />);
    expect(screen.getByRole("button", { name: "Bloquear" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Conclu/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Ajustes/ })).not.toBeInTheDocument();
  });

  it("calls transition when starting a task", async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransitionControls task={task("A_FAZER")} />);
    await user.click(screen.getByRole("button", { name: "Iniciar" }));
    const { specTasksApi } = await import("@/lib/api");
    expect(specTasksApi.transition).toHaveBeenCalledWith(1, { status: "EM_CURSO" });
  });
});
