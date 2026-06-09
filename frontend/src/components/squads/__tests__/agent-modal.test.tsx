import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AgentModal } from "../agent-modal";
import { agentsApi } from "@/lib/api";

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

vi.mock("@/lib/api", () => ({
  agentsApi: {
    create: vi.fn().mockResolvedValue({}),
    update: vi.fn().mockResolvedValue({}),
  },
}));

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  );
}

const baseProps = { open: true, onClose: vi.fn(), squadId: 1 };

describe("AgentModal runtime selector", () => {
  beforeEach(() => vi.clearAllMocks());

  it("defaults to NATIVE runtime and shows the model field", () => {
    renderWithProviders(<AgentModal {...baseProps} />);

    expect(screen.getByText("Runtime")).toBeInTheDocument();
    expect(screen.getByText("Model *")).toBeInTheDocument();
  });

  it("shows the CLI provider field and hides model for an EXTERNAL_CLI agent", () => {
    const agent = {
      id: 1,
      name: "Claude Runner",
      type: "FULLSTACK" as const,
      runtime_kind: "EXTERNAL_CLI" as const,
      cli_provider: "CLAUDE_CODE" as const,
      model: "gpt-4o",
      temperature: 0.7,
      max_tokens: 4096,
      is_active: true,
      squad_id: 1,
      squad_name: "Squad",
      created_at: "2025-01-01T00:00:00Z",
    };

    renderWithProviders(<AgentModal {...baseProps} agent={agent} />);

    expect(screen.getByText("CLI provider *")).toBeInTheDocument();
    expect(screen.queryByText("Model *")).not.toBeInTheDocument();
  });

  it("creates with backend field names (agent_type / model_id)", async () => {
    const user = userEvent.setup();
    renderWithProviders(<AgentModal {...baseProps} />);

    await user.type(screen.getByLabelText("Name *"), "My Agent");
    await user.click(screen.getByRole("button", { name: "Add Agent" }));

    await waitFor(() => expect(agentsApi.create).toHaveBeenCalledTimes(1));
    expect(agentsApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "My Agent",
        agent_type: "FULLSTACK",
        model_id: expect.any(String),
        squad_id: 1,
      })
    );
  });
});
