## ADDED Requirements

### Requirement: R1 — Publicar um artefato de execução
O sistema SHALL aceitar um artefato identificado por execução e chave estável.

#### Scenario: primeira publicação
- **WHEN** um client autorizado publica JSON IR ou HTML para uma execução acessível
- **THEN** o artefato é persistido com tipo, formato, revisão e conteúdo
- **AND** o backend calcula seu checksum SHA-256.

### Requirement: R2 — Tornar a publicação idempotente
O sistema SHALL manter uma única versão atual por `execution_id + artifact_key`.

#### Scenario: repetição da publicação
- **WHEN** o client publica novamente a mesma chave para a mesma execução
- **THEN** o registro existente é atualizado
- **AND** não é criado um artefato duplicado.

### Requirement: R3 — Isolar artefatos por organização
O sistema SHALL validar membership por meio da execução antes de listar, publicar ou ler conteúdo.

#### Scenario: usuário de outra organização
- **WHEN** um usuário tenta acessar artefato de execução fora de sua organização
- **THEN** o acesso é negado sem retornar o conteúdo.

### Requirement: R4 — Gerar mapa após execução aprovada
O client SHALL poder gerar e publicar um mapa de arquitetura versionado após o commit da execução.

#### Scenario: geração habilitada
- **WHEN** uma execução termina aprovada e a geração de mapas está habilitada
- **THEN** o client gera JSON IR e HTML pelo SquadX Maps
- **AND** publica ambos associados à revisão Git.

### Requirement: R5 — Visualizar HTML sem executar scripts
O frontend SHALL apresentar artefatos HTML em um contexto isolado.

#### Scenario: abertura de mapa
- **WHEN** o usuário seleciona um artefato HTML
- **THEN** a UI o abre em iframe com sandbox sem permissão de scripts
- **AND** oferece download do conteúdo original.

### Requirement: R6 — Comparar arquitetura com o baseline anterior
O sistema SHALL selecionar como baseline o último IR de arquitetura do mesmo projeto anterior à execução atual.

#### Scenario: projeto com baseline
- **WHEN** uma nova execução publica seu mapa de arquitetura
- **THEN** o sistema correlaciona Base, Delta e Head em um mesmo grupo
- **AND** registra as revisões Git base e head.

#### Scenario: primeiro mapa do projeto
- **WHEN** não existe IR anterior no projeto
- **THEN** o mapa atual é publicado como Head
- **AND** a ausência de Delta não falha a execução.

### Requirement: R7 — Associar o delta ao review
O sistema SHALL expor ao Pullwise a identidade do grupo de artefatos e suas revisões.

#### Scenario: review de mudança com delta
- **WHEN** o Pullwise revisa a mesma revisão head de um grupo Architecture Delta
- **THEN** o review pode referenciar o artefato sem duplicar seu conteúdo
- **AND** o verdict continua pertencendo ao Pullwise.
