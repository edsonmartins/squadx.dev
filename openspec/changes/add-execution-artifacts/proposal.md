# Proposta: artefatos versionados de execução

## Problema

O runtime executa tasks e persiste logs/resultados, mas não possui um recurso canônico para
entregáveis ricos produzidos durante uma execução. O primeiro consumidor é o SquadX Maps, que
precisa anexar JSON IR e HTML verificável à execução sem transformar logs ou memória em storage.

## Mudança

- adicionar o recurso `ExecutionArtifact`, sempre pertencente a uma execução e, por ela, a uma organização;
- aceitar publicação idempotente de artefatos pelo client;
- registrar tipo, formato, revisão Git, checksum, evidências e conteúdo;
- listar e ler artefatos com isolamento multi-tenant;
- renderizar HTML somente em iframe com sandbox no frontend;
- gerar um mapa de arquitetura ao final de uma execução quando solicitado.

## Capabilities afetadas

- `execution-artifacts` (nova);
- `agent-runtime` (publicação pós-execução);
- `task-ui` (visualização segura).

