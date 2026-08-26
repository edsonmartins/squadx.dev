# Design: artefatos versionados de execução

## Decisões

1. O backend é dono do metadado e conteúdo do artefato nesta primeira entrega.
2. `execution_id + artifact_key` é a chave idempotente; reenvio substitui a mesma versão lógica.
3. O checksum SHA-256 é calculado no backend, nunca confiado ao publicador.
4. A autorização deriva de `Execution -> Task -> Project -> Organization` e reutiliza membership.
5. HTML é devolvido como dado, não servido como página executável pelo backend. A UI usa
   `iframe sandbox` sem `allow-scripts`.
6. O client chama o serviço local do SquadX Maps depois do commit aprovado. Falha na geração é
   observável, mas não rebaixa uma execução de código já concluída.

## Persistência

Tabela `execution_artifacts`: `execution_id`, `artifact_key`, `type`, `format`, `name`,
`git_revision`, `checksum_sha256`, `evidence_json`, `content`, timestamps. Unique em
`(execution_id, artifact_key)`.

## Reúso

- entidade `Execution` e acesso multi-tenant de `ExecutionService`;
- envelopes `ApiResponse`;
- `squad-maps` API/CLI e IR já validadas;
- painel de detalhes da task e TanStack Query.

## Evolução

O conteúdo poderá migrar para object storage mantendo a mesma API e metadados.

## Baseline e Pullwise

O baseline é o último IR `ARCHITECTURE_MAP` do mesmo projeto, excluindo a execução corrente.
Um `artifact_group` correlaciona Base/Delta/Head e `base_revision` mantém a proveniência.
O primeiro mapa estabelece o baseline sem delta. O Pullwise poderá ser modificado para consumir
esse contrato diretamente e associar o grupo ao review/Pass 5; ele continua dono do verdict,
enquanto Maps produz a visualização e SquadX persiste o artefato.
