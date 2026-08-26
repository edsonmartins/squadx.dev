# Design: SquadX Intelligence Gateway

## Decisões

1. O Gateway é dono do contrato e da escolha de provider; não é dono do índice especializado.
2. Toda resposta inclui provider, revisão, versão do provider, confiança e evidência citável.
3. Snapshots são imutáveis por `organization + repository + revision + provider`.
4. RepoWise roda fora do processo SquadX, sem módulos AGPL incorporados ao backend.
5. O piloto é `shadow`: resultados são medidos, mas não alteram Pass 5 ou verdict.
6. Falha/timeout usa fallback configurado e nunca mistura fatos de revisões diferentes.
7. BrainSentry recebe apenas conhecimento curado; fatos reconstruíveis permanecem no provider.
8. O provider primário comercial seguirá ADR-0011: Tree-sitter + SCIP + busca permissiva; RepoWise permanece shadow e separado.

## Contrato canônico inicial

- `RepositorySnapshot`
- `CodeLocation`
- `CodeSymbol`
- `CodeRelationship`
- `EvidenceRef`
- `SearchResult`
- `SymbolContext`
- `DependencyGraph`
- `ChangeImpact`
- `ArchitectureSnapshot`

## Providers

- `NativePullwiseProvider`: fatos já disponíveis no grafo/review Pullwise;
- `RepoWiseProvider`: adapter HTTP privado, opcional e separado;
- `RipgrepFallbackProvider`: recall lexical mínimo quando aplicável;
- `SquadXNativeProvider`: futuro provider comercial baseado em Tree-sitter/SCIP/Zoekt;
- `SourcebotSearchProvider`: futuro, somente para tenant licenciado.

## Fluxo

1. Webhook cria review com base/head SHA.
2. SquadX solicita `ensureSnapshot(head)` antes do contexto do agente.
3. Gateway reutiliza ou agenda índice no provider selecionado.
4. Agente consulta as tools canônicas pelo `workspace` MCP.
5. Pullwise pede impacto base/head e compara provider nativo × RepoWise em shadow.
6. Maps recebe `ArchitectureSnapshot` e evidências fixadas ao SHA.
7. Métricas registram latência, tokens, exploração, recall e divergências.

## Segurança e operação

- credenciais do provider nunca chegam ao agente ou navegador;
- escopo obrigatório por organização/projeto;
- timeout, circuit breaker, limites de índice e retenção;
- egress do RepoWise restrito a Git/storage necessários;
- logs não contêm conteúdo ou credenciais do repositório;
- exclusão do tenant remove snapshots e índices correspondentes.

## Critério go/no-go RepoWise

- redução de exploração/tokens alvo de 20–30%;
- recall de impacto superior ou complementar ao Pullwise;
- latência incremental e custo operacional aceitáveis;
- contratos estáveis nas linguagens piloto;
- solução jurídica explícita: serviço AGPL conforme licença, fork público ou licença comercial.

## Referências

- ADR-0003 — contrato MCP único;
- ADR-0010 — providers de code intelligence e modo shadow;
- RFC-0004 — Pass 5 plugável;
- `docs/ANALISE-CODE-INTELLIGENCE-ECOSYSTEM.md`.
