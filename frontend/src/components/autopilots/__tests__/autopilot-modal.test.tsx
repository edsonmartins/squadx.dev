import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AutopilotModal } from "../autopilot-modal";

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

vi.mock("@/lib/api", () => ({
  autopilotsApi: {
    create: vi.fn().mockResolvedValue({}),
    update: vi.fn().mockResolvedValue({}),
  },
  projectsApi: {
    list: vi.fn().mockResolvedValue({ content: [{ id: 1, name: "Proj" }] }),
  },
  agentsApi: { listByOrganization: vi.fn().mockResolvedValue({ content: [] }) },
  squadsApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
}));

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  );
}

const defaultProps = {
  open: true,
  onClose: vi.fn(),
  organizationId: 1,
};

describe("AutopilotModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the create dialog when open", () => {
    renderWithProviders(<AutopilotModal {...defaultProps} />);

    expect(
      screen.getByRole("heading", { name: "Create Autopilot" })
    ).toBeInTheDocument();
    expect(
      screen.getByText("Schedule recurring work for your squad.")
    ).toBeInTheDocument();
  });

  it("renders the key form fields", () => {
    renderWithProviders(<AutopilotModal {...defaultProps} />);

    expect(screen.getByLabelText("Name *")).toBeInTheDocument();
    expect(screen.getByLabelText("Schedule")).toBeInTheDocument();
    expect(screen.getByLabelText("Task title *")).toBeInTheDocument();
    expect(screen.getByLabelText("Enabled")).toBeInTheDocument();
  });

  it("shows edit title when an autopilot is provided", () => {
    const autopilot = {
      id: 1,
      name: "Daily standup",
      cron_expression: "0 9 * * *",
      timezone: "UTC",
      execution_mode: "CREATE_TASK" as const,
      organization_id: 1,
      project_id: 1,
      project_name: "Proj",
      task_title: "Run standup",
      task_priority: "MEDIUM" as const,
      enabled: true,
      run_count: 0,
      created_at: "2025-01-01T00:00:00Z",
    };

    renderWithProviders(<AutopilotModal {...defaultProps} autopilot={autopilot} />);

    expect(screen.getByText("Edit Autopilot")).toBeInTheDocument();
  });

  it("calls onClose when cancel is clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    renderWithProviders(<AutopilotModal {...defaultProps} onClose={onClose} />);
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
