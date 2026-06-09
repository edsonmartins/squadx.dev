# RFC-0002 — Versionamento + materialização (versão da spec → commit)

> Realiza ADR-0001. Define como uma versão aprovada da spec vira um commit no repositório, sem
> drift e sem lock-in.

## 1. Modelo de versão

Cada `Change` tem um histórico de `SpecVersion`:
```jsonc
SpecVersion = {
  version: "v3",            // monotônico por change
  current: true,
  summary: "string",        // o delta semântico daquela versão (o que mudou e por quê)
  author: "string",
  when: "timestamp",
  commit: "sha?"            // preenchido na materialização; null antes
}
```
O versionamento é **semântico por requisito**: o painel guarda quem mudou qual requisito/cenário,
quando. Uma versão é um "corte" aprovado desse histórico.

## 2. Layout materializado (OpenSpec)

A materialização escreve markdown em `openspec/changes/<change-id>/`:
```
proposal.md
design.md
tasks.md
specs/<capability>/spec.md      # requisitos ADDED/MODIFIED/REMOVED + cenários WHEN/THEN
```
Quando a mudança é **aplicada/arquivada**, o conteúdo dos deltas é promovido para
`openspec/specs/<capability>/spec.md` (verdade atual). O Control Panel é a **única ponta** que
gera esses arquivos a partir do seu modelo versionado.

## 3. Algoritmo de materialização

`materialize_change(change_id)` (idempotente por `(change_id, version)`):

```
1. carregar Change + SpecVersion.current (V) + requisitos/cenários da V
2. render(V) → conjunto de arquivos markdown (layout §2) de forma DETERMINÍSTICA
   (ordenação estável de requisitos/cenários; sem timestamps voláteis no corpo)
3. abrir/garantir a branch da mudança (ex.: change/<change-id>)
4. computar diff(arquivos_render, arquivos_no_repo_na_branch)
5. SE diff vazio E V.commit != null → retornar V.commit (no-op idempotente)
6. SENÃO:
   a. escrever arquivos; criar commit com mensagem convencional
      "spec(<change-id>): materialize <version> — <summary curto>"
   b. registrar commit em V.commit; marcar evento spec_materialized
   c. retornar { version: V.version, commit }
```

Determinismo (passo 2) é essencial: re-render da mesma versão produz bytes idênticos → diff vazio
→ no-op. Isso dá idempotência real e evita commits espúrios.

## 4. PR carrega spec + código

O fluxo de execução de uma tarefa abre/atualiza um PR na branch da mudança. Como a spec é
materializada na **mesma branch**, o PR exibe **spec + código no mesmo diff**. O merge é o ponto
de reconciliação (e dispara o Pass 5 — RFC-0004).

## 5. Concorrência e conflitos

- **Lock por change** durante a materialização (evita dois commits concorrentes para o mesmo
  change). Concorrência → `E_CONFLICT`, com retry.
- Se o repo divergiu (alguém editou a spec à mão na branch): a materialização **não sobrescreve
  cegamente** — detecta diff inesperado em arquivos que ela controla e sinaliza para resolução
  (evento `spec_materialize_conflict`); a política padrão é "painel é dono dos arquivos sob
  `openspec/`", então edições manuais ali são desencorajadas (documentado no AGENTS.md).

## 6. Auditoria (LGPD)

Toda materialização gera um evento auditável (autor, versão, commit, timestamp). O histórico de
`SpecVersion` é a trilha semântica; o Git é a trilha física.

## 7. Itens em aberto

- Estratégia de credencial do painel para commitar (app token vs deploy key) — decisão de infra.
- Squash vs commits incrementais por materialização (default: um commit por versão materializada).
