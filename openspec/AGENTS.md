# OpenSpec — Fluxo para agentes

Como propor, revisar e aplicar mudanças de spec neste repositório. (Spec em PT; identificadores em EN.)

## Fluxo de três estágios

1. **Propor** — crie `openspec/changes/<change-id>/` com:
   - `proposal.md` — por quê (problema/motivação) e o que muda (resumo das capabilities afetadas).
   - `design.md` — decisões técnicas, mapeamento de entidades/tabelas, reúso, links para ADR/RFC.
   - `tasks.md` — checklist de implementação; **cada task referencia o requisito de origem e o
     ADR/RFC pertinente**.
   - `specs/<capability>/spec.md` — o **delta** de cada capability.
2. **Implementar** — siga `tasks.md`; o código e a spec viajam no mesmo PR (materialização).
3. **Aplicar/Arquivar** — quando a mudança é aceita e materializada, o conteúdo dos deltas é
   promovido para `openspec/specs/<capability>/spec.md` (a "verdade atual") e o change é arquivado.

## Formato de delta (`changes/<id>/specs/<capability>/spec.md`)

Use cabeçalhos de operação e o formato de requisito/cenário abaixo. Todo requisito tem ≥1 cenário.

```markdown
## ADDED Requirements

### Requirement: <título imperativo>
O sistema SHALL <comportamento observável>.

#### Scenario: <nome do cenário>
- **WHEN** <condição/gatilho>
- **THEN** <resultado esperado>
- **AND** <resultado adicional, opcional>
```

- Operações: `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`.
- Cada `### Requirement:` deve ter ao menos um `#### Scenario:` com WHEN/THEN.
- IDs estáveis de requisito (ex.: `R1`, `R2`) podem aparecer no título para rastreabilidade com `tasks.md`.

## Verdade atual (`specs/<capability>/spec.md`)

Espelha o estado consolidado de cada capability. É atualizada na fase de "aplicar/arquivar",
não durante a proposta.

## Validação

- Se o CLI estiver disponível: `openspec validate <change-id> --strict`.
- Caso contrário, conferência manual: layout correto, todo requisito com ≥1 cenário WHEN/THEN,
  toda task ligada a requisito + ADR/RFC, numeração de ADR/RFC contínua.

## Relação com outros arquivos

- `CONSTITUTION.md` — princípios invioláveis (precede specs).
- `CLAUDE.md` — orientações para agentes de **codificação** (build, convenções de código).
- `docs/adr/`, `docs/rfc/` — decisões e contratos referenciados pelas tasks.
