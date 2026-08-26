import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SpecTaskBoard } from "@/components/specs/spec-task-board";
import type { SpecTaskResponse } from "@/lib/api";

vi.mock("@/lib/api", () => ({
  SPEC_TASK_STATUS_LABELS: {
    A_FAZER: "A fazer",
    EM_CURSO: "Em execução",
    EM_VALIDACAO: "Em validação",
    CONCLUIDA: "Concluída",
    BLOQUEADA: "Bloqueada",
    AJUSTES: "Ajustes necessários",
  },
  SPEC_TASK_UI_TRANSITIONS: {
    A_FAZER: ["EM_CURSO", "BLOQUEADA"],
    EM_CURSO: ["EM_VALIDACAO", "BLOQUEADA"],
    EM_VALIDACAO: [],
    CONCLUIDA: [],
    BLOQUEADA: ["EM_CURSO"],
    AJUSTES: ["EM_CURSO"],
  },
  specTasksApi: { transition: vi.fn().mockResolvedValue({}) },
}));

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));

import { specTasksApi } from "@/lib/api";

function makeTask(overrides: Partial<SpecTaskResponse>): SpecTaskResponse {
  return {
    id: 1,
    title: "Tarefa",
    status: "A_FAZER",
    change_id: 7,
    requirement_ref: "R1",
    assignee_type: "HUMAN",
    pass5: null,
    blocker_reason: null,
    revise_reason: null,
    ...overrides,
  };
}

function renderBoard(tasks: SpecTaskResponse[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <SpecTaskBoard changeId={7} tasks={tasks} />
    </QueryClientProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("SpecTaskBoard", () => {
  const tasks = [
    makeTask({ id: 1, title: "Criar endpoint", status: "A_FAZER", assignee_type: "HUMAN" }),
    makeTask({ id: 2, title: "Rodar migração", status: "EM_CURSO", assignee_type: "AGENT", assigned_agent_name: "Claude Code" }),
    makeTask({ id: 3, title: "Revisar schema", status: "EM_VALIDACAO", assignee_type: "HUMAN" }),
    makeTask({ id: 4, title: "Publicar pacote", status: "CONCLUIDA", assignee_type: "AGENT" }),
  ];

  it("renderiza as seis colunas do board", () => {
    renderBoard(tasks);
    for (const label of ["A fazer", "Em execução", "Em validação", "Concluída", "Bloqueada", "Ajustes necessários"]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
  });

  it("filtro IA esconde tarefas humanas e mostra a do agente", async () => {
    renderBoard(tasks);
    await userEvent.click(screen.getByRole("button", { name: "IA" }));
    expect(screen.queryByText("Criar endpoint")).not.toBeInTheDocument();
    expect(screen.getByText("Rodar migração")).toBeInTheDocument();
  });

  it("não oferece transição manual em EM_VALIDACAO — apenas Pass 5 decide", () => {
    renderBoard([makeTask({ id: 3, title: "Revisar schema", status: "EM_VALIDACAO" })]);
    expect(screen.getByText(/aguardando Pass 5/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /executar$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bloquear" })).not.toBeInTheDocument();
  });

  it("tarefa em A_FAZER oferece exatamente as transições permitidas e chama a API", async () => {
    renderBoard([makeTask({ id: 1, title: "Criar endpoint", status: "A_FAZER" })]);
    await userEvent.click(screen.getByRole("button", { name: "Executar" }));
    // Ação abre diálogo de confirmação; nota é opcional fora de bloqueio
    await userEvent.click(screen.getByRole("button", { name: "Confirmar" }));
    await waitFor(() =>
      expect(specTasksApi.transition).toHaveBeenCalledWith(1, "EM_CURSO", undefined)
    );
  });

  it("bloqueio exige motivo antes de habilitar confirmação", async () => {
    renderBoard([makeTask({ id: 1, title: "Criar endpoint", status: "A_FAZER" })]);
    await userEvent.click(screen.getByRole("button", { name: "Bloquear" }));
    const confirm = screen.getByRole("button", { name: "Confirmar" });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/motivo do bloqueio/i), "sem credenciais");
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);
    await waitFor(() =>
      expect(specTasksApi.transition).toHaveBeenCalledWith(1, "BLOQUEADA", "sem credenciais")
    );
  });

  it("exibe crítica do Pass 5 em tarefa reprovada (AJUSTES)", () => {
    renderBoard([
      makeTask({
        id: 5,
        title: "Corrigir validação",
        status: "AJUSTES",
        revise_reason: "Cenário R1·S2 sem teste",
      }),
    ]);
    expect(screen.getByText(/Crítica do Pass 5: Cenário R1·S2 sem teste/)).toBeInTheDocument();
    // Única ação possível é reabrir para execução
    expect(screen.getByRole("button", { name: "Executar" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bloquear" })).not.toBeInTheDocument();
  });
});
