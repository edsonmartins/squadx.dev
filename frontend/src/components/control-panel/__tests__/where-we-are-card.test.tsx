import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { WhereWeAreCard } from "../where-we-are-card";
import type { WhereWeAreResponse } from "@/lib/api";

const data: WhereWeAreResponse = {
  project_id: 1,
  counts: { A_FAZER: 1, EM_CURSO: 0, EM_VALIDACAO: 0, CONCLUIDA: 2, BLOQUEADA: 0, AJUSTES: 0 },
  total: 3,
  concluidas: 2,
  progress: 2 / 3,
};

describe("WhereWeAreCard", () => {
  it("renders progress and per-status counts", () => {
    render(<WhereWeAreCard data={data} />);
    expect(screen.getByText("2/3 concluídas")).toBeInTheDocument();
    expect(screen.getByText("67% concluído")).toBeInTheDocument();
    expect(screen.getByText("Concluída")).toBeInTheDocument();
  });
});
