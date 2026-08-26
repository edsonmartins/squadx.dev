# Proposta: gateway de code intelligence plugável

## Problema

Os agentes exploram o repositório repetidamente e o Pullwise mantém fatos de impacto orientados ao
review. RepoWise oferece símbolos, relações, Git e saúde, mas incorporá-lo diretamente criaria
lock-in, duplicaria o grafo do Pullwise e introduziria obrigações AGPL no core proprietário.

## Mudança

- criar um contrato canônico, sempre fixado a repositório e revisão imutável;
- introduzir `CodeIntelligenceProvider` e seleção por organização/projeto;
- integrar RepoWise apenas como serviço separado e inicialmente em modo shadow;
- expor busca, contexto de símbolo, dependências e impacto pelo contrato `workspace` MCP;
- comparar RepoWise e Pullwise antes de permitir que um provider participe do gate;
- enviar ao BrainSentry somente decisões candidatas com evidência, confiança e aprovação.

## Fora de escopo inicial

- fork ou cópia de código AGPL do RepoWise no monorepo;
- substituição imediata do grafo ou verdict do Pullwise;
- Sourcebot como dependência obrigatória;
- armazenamento de AST/símbolos no BrainSentry;
- bloqueio de PR baseado no provider experimental.

## Capabilities afetadas

- `code-intelligence-gateway` (nova);
- `workspace-mcp-server` (novas tools de leitura);
- `pass5-validation` (comparação shadow, sem alterar verdict inicialmente);
- `execution-artifacts` (IR/evidência para Maps).

