# ADR-0004 — Máquina de estados com Pass 5 como único caminho para "concluída"

## Status

Aceito — 2026-06-09.

## Contexto

Se o agente ou o desenvolvedor puderem marcar uma tarefa como "concluída", o sistema volta a
permitir drift: declara-se pronto sem que o código tenha sido conferido contra a spec. É preciso
um portão objetivo entre "afirmei que terminei" e "está pronto".

## Decisão

Adotar uma máquina de **6 estados de board** (rótulos de UI em PT; identificadores em EN):

| status | rótulo | significado |
|---|---|---|
| `a_fazer` | A fazer | não iniciada |
| `em_curso` | Em execução | dev ou agente trabalhando |
| `em_validacao` | Em validação | PR aberto; Pass 5 conferindo |
| `concluida` | Concluída | aprovada no Pass 5 |
| `bloqueada` | Bloqueada | impedida; sempre com motivo |
| `ajustes` | Ajustes necessários | reprovada no Pass 5; reabre com a crítica |

Transições válidas:

```
a_fazer ─▶ em_curso ─▶ (implementado: evento) ─▶ em_validacao ─▶ concluida
                                                      │
                                                      └─▶ ajustes ─▶ em_curso (reabre)
qualquer estado ativo ◀─▶ bloqueada (com motivo)
```

Regras de autoria do estado:
- O agente/dev reporta `em_curso` e `implementado`; pode reportar `bloqueada` (com motivo).
- A **abertura do PR** leva a `em_validacao`.
- **`concluida` e `ajustes` são definidos exclusivamente pelo Pass 5.** O agente/dev **nunca**
  marca `concluida`.

`implementado` é **vocabulário de evento**, não estado de board: é a afirmação de que terminou
de codar; aparece no histórico. Quem move para `em_validacao` é a abertura do PR.

## Alternativas consideradas

1. **Agente/dev marca "concluída".** Reintroduz drift. Rejeitada (viola a Constituição §4).
2. **Sem estados de desvio** (`bloqueada`/`ajustes`). Perde a semântica de impedimento e de
   reprovação com crítica. Rejeitada.
3. **Máquina de 6 estados com Pass 5 como gate (escolhida).**

## Consequências

- **Positivas:** "pronto" é objetivo e conferido; impossível pular a validação; `bloqueada`
  sempre carrega motivo; `ajustes` reabre com a crítica anexada.
- **Custos:** depende do Pass 5 estar disponível (ADR-0005, RFC-0004); transições derivam de
  eventos e precisam ser idempotentes (RFC-0003).
- **Relacionado:** ADR-0002, ADR-0005, RFC-0003, RFC-0004.
