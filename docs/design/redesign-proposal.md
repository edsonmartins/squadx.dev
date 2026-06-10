# SquadX.dev — Auditoria de Frontend & Proposta de Redesign

> **Status:** proposta · **Data:** 2026-06-10
> **Mockup navegável:** [`redesign-mockup.html`](./redesign-mockup.html) (abrir direto no browser)
> **Direção visual:** "Mission Control" — corporativo, sóbrio, denso em dados, alta legibilidade.

---

## 1. Auditoria — estado atual

### 1.1 O que está bom (manter)

| Item | Evidência |
|---|---|
| Stack moderna: Next.js 16 App Router, React 19, Tailwind, Radix UI | `frontend/package.json` |
| Tokens HSL via CSS variables com dark mode por classe | `src/app/globals.css:5-93` |
| State management limpo: Zustand + TanStack Query | `src/stores/*`, mutations com `invalidateQueries` |
| Forms robustos: React Hook Form + Zod em todos os modais | `task-modal.tsx`, `project-modal.tsx`, etc. |
| Feedback centralizado via toast | `src/hooks/use-toast.ts` |
| Delete confirm centralizado | `src/components/shared/delete-confirm-dialog.tsx` |

### 1.2 Problemas críticos

**P1 — Tipografia monoespaçada no corpo inteiro do app**
`globals.css:102` aplica `Fragment Mono` como fonte de **todo o body** a 14px. Monoespaçada para texto corrido reduz legibilidade (~30% menos caracteres por linha), passa estética de "terminal hobbysta" e não de produto corporativo. Mono deve ser reservada para **dados**: IDs, custos, tokens, códigos.

**P2 — Cores semânticas duplicadas e hardcoded (9+ arquivos, 4 convenções diferentes)**
Não existe fonte única de verdade para cores de status/prioridade:
- `kanban/task-card.tsx:15-27` — `border-l-blue-500`, `bg-orange-50`…
- `tasks/task-detail-sheet.tsx:15-26` — `bg-blue-500`, `bg-red-500` (sólidos, padrão diferente)
- `(dashboard)/page.tsx:132-139` — `text-green-500`, `text-yellow-500`
- `approvals/page.tsx:27-44` — `bg-yellow-100 text-yellow-800` (terceiro padrão)
- `kanban/kanban-board.tsx:14-21` — cores de coluna hardcoded
- `analytics/page.tsx` (`PIE_COLORS`) e `live/annotation-toolbar.tsx` (`PRESET_COLORS`) — hex literais

Consequência: impossível mudar a paleta globalmente; o mesmo status aparece com cores diferentes em telas diferentes; nada disso respeita dark mode (`bg-yellow-100` fica ilegível no tema escuro).

**P3 — Navegação plana com 11 itens sem hierarquia**
`layout/sidebar.tsx:32-47` — lista única mistura conceitos de natureza diferente (Dashboard, Tasks, Approvals, Calendar, Analytics). Sem agrupamento, sem badges de contexto (aprovações pendentes, sessões live), logo genérico "SX".

**P4 — Acessibilidade quase ausente**
- Zero `aria-label` custom no app; busca do header sem label (`header.tsx:17`)
- Badge de notificação sem `role="status"` (`header.tsx:33`)
- Indicadores live com `animate-pulse` sem `aria-live`
- Live badge clicável não acessível por teclado (`task-card.tsx:70-82`)

**P5 — Header subaproveitado**
`header.tsx` — busca com largura fixa `w-72` (quebra em mobile), sem atalho ⌘K, sem breadcrumb, botão "New Task" global sem contexto, contador "3" de notificações hardcoded.

### 1.3 Problemas médios

- **6+ modais de formulário sem abstração comum** (`project-modal`, `task-modal`, `squad-modal`, `change-modal`…) — padrão copiado/colado.
- **Dois sistemas de badge:** `ui/badge.tsx` + `control-panel/status-badge.tsx`.
- **Espaçamentos inconsistentes:** cards `p-6` vs task-card `p-2.5`; gaps `gap-3/4/6` sem escala.
- **Estados vazios pobres:** ícone + frase, sem call-to-action (`page.tsx:271-275`).
- **i18n inexistente:** textos hardcoded em inglês, `<html lang="en">`.
- **Sem dark-mode toggle visível** apesar do tema escuro estar implementado.

---

## 2. Proposta — Design System v2 "Mission Control"

Conceito: o SquadX orquestra squads de IA executando trabalho real (código, deploys, aprovações). A UI deve parecer uma **sala de controle corporativa**: sidebar escura ink-navy que ancora a navegação, canvas claro neutro-frio para os dados, mono apenas em números/IDs, semântica de status única em todo o app.

### 2.1 Tipografia

| Papel | Atual | Proposto | Racional |
|---|---|---|---|
| Display/headings | DM Sans | **Schibsted Grotesk** (600–800) | Grotesca contemporânea, sóbria, ótima em pesos altos |
| Corpo/UI | Fragment Mono (!) | **Instrument Sans** (400–600) | Humanista limpa, excelente em 13–14px |
| Dados | Fragment Mono | **JetBrains Mono** (400–600) | Só para números, IDs, custos, código |

### 2.2 Cor

- **Manter o azul SquadX** como brand (`hsl(224 76% 48%)` levemente aprofundado para uso em botões).
- **Neutros frios** (slate) no canvas em vez dos atuais warm-gray — leitura mais "enterprise".
- **Sidebar ink-navy** (`hsl(228 32% 7%)`) — identidade imediata, contraste com o canvas, esconde o "app branco genérico".
- **Sistema semântico único** (tokens novos em `globals.css` + `tailwind.config.ts`):

```css
--ok / --ok-bg          /* success, DONE, approved      */
--warn / --warn-bg      /* warning, pending, IN_REVIEW  */
--danger / --danger-bg  /* error, BLOCKED, URGENT       */
--info / --info-bg      /* running, IN_PROGRESS         */
--neutral / --neutral-bg/* TODO, cancelled, low         */
--live                  /* sessões ao vivo (pulse)      */
```

E um mapa TypeScript único consumido por kanban, sheets, modais, dashboard e approvals:

```ts
// src/lib/design/semantics.ts
export const TASK_STATUS_STYLE: Record<TaskStatus, SemanticToken> = {
  TODO: "neutral", IN_PROGRESS: "info", IN_REVIEW: "warn",
  BLOCKED: "danger", DONE: "ok", CANCELLED: "neutral",
};
export const PRIORITY_STYLE: Record<Priority, SemanticToken> = { ... };
export const CHART_PALETTE = [/* derivada dos tokens */];
```

### 2.3 Shell (sidebar + header)

- **Sidebar agrupada:** `Workspace` (Dashboard, Projects, Tasks, Control Panel, Squads) · `Operação` (Approvals, Live View, Recordings, Calendar) · `Insights` (Analytics). Badges dinâmicos: contagem de approvals pendentes, dot pulsante quando há sessão live.
- **Indicador de ambiente** (PROD/STAGING) ao lado do logo — relevante para um produto que faz deploy.
- **User card no rodapé da sidebar** (avatar + nome + menu) — libera o header.
- **Header:** breadcrumb à esquerda, busca central estilo command palette com `⌘K`, ações à direita. Busca `w-full max-w-xs` responsiva.

### 2.4 Dashboard

- Saudação contextual + resumo operacional ("3 squads ativos · 2 live · 3 aprovações").
- KPI cards com **números em mono**, delta chips (▲/▼) e sparklines.
- **Live strip** destacada quando há sessões ativas (proposta de valor nº 1 do produto).
- **Fila de aprovações acionável** direto do dashboard (aprovar/rejeitar inline).
- Painel de custo com grid mono (tokens in/out, custo total, custo por task).

### 2.5 Micro-interações (sutis, corporativas)

- Entrada da página com stagger (60ms entre KPI cards, `cubic-bezier(.2,.7,.3,1)`).
- Hover de card: `translateY(-2px)` + sombra `--shadow-pop`.
- Pulse ring nos indicadores live (`box-shadow` animado, não `opacity`).
- Header com `backdrop-filter: blur` ao scrollar.

> Tudo demonstrado no mockup: `docs/design/redesign-mockup.html`.

---

## 3. Roadmap de implementação

### Fase 1 — Fundações (1–2 dias, sem mudança visual disruptiva)
1. Criar `src/lib/design/semantics.ts` com mapas de status/prioridade/paleta de charts.
2. Adicionar tokens semânticos (`ok/warn/danger/info/neutral/live` + `*-bg`) em `globals.css` e `tailwind.config.ts`.
3. Migrar os 9 pontos de cor hardcoded (P2) para os mapas — kanban, sheets, dashboard, approvals, recordings, analytics, annotation-toolbar.
4. Unificar `status-badge.tsx` no `ui/badge.tsx` (variants semânticas via CVA).

### Fase 2 — Tipografia & shell (2–3 dias, maior impacto visual)
5. Trocar fontes em `layout.tsx` (next/font): Schibsted Grotesk + Instrument Sans + JetBrains Mono; corpo deixa de ser mono (`globals.css:102`).
6. Redesenhar `sidebar.tsx`: tema ink-navy, grupos com labels, badges dinâmicos (approvals count, live dot), user card no rodapé.
7. Redesenhar `header.tsx`: breadcrumb, busca ⌘K responsiva, remover dados hardcoded.

### Fase 3 — Dashboard & padrões (2–3 dias)
8. Dashboard: KPI cards com delta/sparkline, live strip, fila de approvals inline, painel de custo.
9. Extrair `FormModal` genérico e refatorar os 6 modais.
10. Empty states com CTA (`EmptyState` component compartilhado).

### Fase 4 — Qualidade (contínuo)
11. Acessibilidade: `aria-label` em inputs/botões de ícone, `aria-live` nos indicadores live, focus visible consistente, navegação por teclado no kanban.
12. Responsividade: auditar `md:`/`lg:` em todas as páginas; sidebar como drawer em mobile.
13. Dark mode: ajustar os novos tokens para `.dark` e expor toggle no user card.
14. (Opcional) Base de i18n com `next-intl`.

---

## 4. Resumo executivo

| Dimensão | Hoje | Proposta |
|---|---|---|
| Identidade | App branco genérico, corpo mono | Mission Control: sidebar ink-navy + canvas frio, mono só em dados |
| Status/prioridade | 4 convenções de cor em 9 arquivos | 1 sistema semântico (tokens + mapa TS) |
| Navegação | 11 itens planos | 3 grupos + badges operacionais |
| Header | Busca fixa, dados fake | Breadcrumb + ⌘K + ações contextuais |
| Dashboard | Stats estáticos | KPIs com tendência, live strip, approvals acionáveis |
| A11y | Inexistente | aria/focus/keyboard como critério de PR |
