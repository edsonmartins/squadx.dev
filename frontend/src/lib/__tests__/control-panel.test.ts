import { describe, it, expect } from "vitest";
import {
  manualTransitions,
  SPEC_TASK_STATUS_LABEL,
  transitionActionLabel,
} from "../control-panel";

describe("control-panel state machine helpers", () => {
  it("excludes Pass5-only targets from manual transitions", () => {
    const targets = manualTransitions("EM_VALIDACAO");
    expect(targets).not.toContain("CONCLUIDA");
    expect(targets).not.toContain("AJUSTES");
    expect(targets).toEqual(["BLOQUEADA"]);
  });

  it("allows start/block from a_fazer", () => {
    expect(manualTransitions("A_FAZER")).toEqual(["EM_CURSO", "BLOQUEADA"]);
  });

  it("treats concluida as terminal", () => {
    expect(manualTransitions("CONCLUIDA")).toEqual([]);
  });

  it("maps statuses to PT labels", () => {
    expect(SPEC_TASK_STATUS_LABEL.CONCLUIDA).toBe("Concluída");
    expect(SPEC_TASK_STATUS_LABEL.EM_CURSO).toBe("Em execução");
  });

  it("labels transition actions", () => {
    expect(transitionActionLabel("EM_CURSO")).toBe("Iniciar");
    expect(transitionActionLabel("EM_VALIDACAO")).toBe("Enviar p/ validação");
  });
});
