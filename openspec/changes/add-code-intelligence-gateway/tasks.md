# Tarefas

- [x] T1 — Definir tipos canônicos e `CodeIntelligenceProvider` sem dependência de vendor (`R1`, `R2`; ADR-0010).
- [x] T2 — Persistir snapshots/jobs imutáveis, estados e isolamento multi-tenant (`R1`, `R3`; ADR-0010).
- [x] T3 — Implementar registry/policy de provider por organização e fallback (`R2`, `R3`; ADR-0010).
- [x] T4 — Implementar `RipgrepFallbackProvider` com limites e evidência por revisão (`R2`, `R3`; ADR-0010).
- [x] T5 — Implementar adapter HTTP `RepoWiseProvider`, health, timeout e circuit breaker (`R2`, `R3`; ADR-0010).
- [x] T5 — Acompanhar jobs externos até `READY`/`FAILED` sem liberar snapshot prematuro (`R1`, `R3`; ADR-0010).
- [x] T6 — Subir RepoWise separado, fixar versão e documentar licença/NOTICE/operação (`R3`; ADR-0010).
- [x] T7 — Expor backend canônico de `search_code` e `get_symbol_context`; binding MCP aguarda materialização do workspace server (`R4`; ADR-0003/ADR-0010).
- [x] T8 — Expor endpoints canônicos de `get_dependencies` e `get_change_impact`; binding MCP aguarda materialização do workspace server (`R4`; ADR-0003/ADR-0010).
- [x] T9 — Injetar contexto progressivo e limitado do snapshot no briefing do agente, com feature flag e fallback silencioso (`R4`; ADR-0003/ADR-0010).
- [x] T10 — Executar comparação shadow RepoWise × provider nativo/Pullwise sem alterar verdict; persistir divergência e latência (`R5`; RFC-0004/ADR-0010).
- [x] T11 — Instrumentar chamadas/tools, latência, divergência shadow e duração/resultado de indexação, reutilizando métricas de tokens/custo existentes (`R5`; ADR-0010).
- [x] T12 — Alimentar Maps com `ArchitectureSnapshot` e evidências canônicas; provider local produz boundaries verificadas por revisão e o Maps preserva esses dados como evidência (`R6`; ADR-0010).
- [x] T13 — Enviar decisões candidatas ao BrainSentry como pendentes, com evidência/proveniência e aprovação explícita (`R7`; ADR-0010).
- [ ] T14 — Rodar piloto em SquadX, Pullwise e repositório Go/monorepo; publicar go/no-go (`R5`; ADR-0010). **Preparação concluída:** matriz, procedimento e critérios em `docs/CODE-INTELLIGENCE-PILOT.md`; execução real aguarda os três mirrors/ambientes.
- [ ] T15 — Obter decisão jurídica/comercial antes de customizar ou oferecer RepoWise por rede (`R3`; ADR-0010). **Preparação concluída:** checklist e registro em `docs/REPOWISE-LEGAL-COMMERCIAL-GATE.md`; aprovação humana ainda pendente.
- [ ] T16 — Implementar `SquadXNativeProvider` com Tree-sitter/SCIP/Zoekt e pipeline de SBOM/licence scan (`R2`, `R3`; ADR-0011). **Fatia 1 concluída:** provider nativo habilitável com snapshots SHA, search e architecture boundaries; SCIP/Zoekt/dependências ainda pendentes.
