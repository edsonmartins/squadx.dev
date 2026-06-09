import { describe, it, expect } from "vitest";
import {
  SPEC_TASK_STATUS_LABEL,
  PASS5_OUTCOME_CLASS,
  transitionActionLabel,
} from "../control-panel";

describe("control-panel helpers", () => {
  it("maps statuses to PT labels", () => {
    expect(SPEC_TASK_STATUS_LABEL.CONCLUIDA).toBe("Concluída");
    expect(SPEC_TASK_STATUS_LABEL.EM_CURSO).toBe("Em execução");
  });

  it("labels transition actions", () => {
    expect(transitionActionLabel("EM_CURSO")).toBe("Iniciar");
    expect(transitionActionLabel("EM_VALIDACAO")).toBe("Enviar p/ validação");
    expect(transitionActionLabel("BLOQUEADA")).toBe("Bloquear");
  });

  it("maps pass5 outcomes to colors", () => {
    expect(PASS5_OUTCOME_CLASS.PASS).toContain("emerald");
    expect(PASS5_OUTCOME_CLASS.FAIL).toContain("red");
  });
});
