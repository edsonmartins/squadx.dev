# ADR-0011 — Provider nativo comercialmente distribuível

## Status

Aceita para implementação.

## Decisão

O core comercial do SquadX não dependerá de código RepoWise/AGPL ou Depwire/BSL. O provider
principal será implementado sobre componentes permissivos e contratos próprios:

- Tree-sitter (MIT) para parsing incremental;
- SCIP (Apache-2.0) como formato/intercâmbio de símbolos e referências;
- Zoekt (Apache-2.0) para busca lexical rápida;
- grafo, impacto, evidências, API e MCP pertencentes ao SquadX.

`codesearch` e `Code-Index-MCP` podem ser avaliados como adapters ou referências, sempre após
auditoria de licença, dependências transitivas e compatibilidade operacional.

RepoWise permanece serviço separado, opcional e shadow. Não será copiado para o core nem promovido
a provider primário antes do gate jurídico/comercial registrado em `docs/REPOWISE-LEGAL-COMMERCIAL-GATE.md`.

## Consequências

- SquadX pode ser empacotado e comercializado com uma cadeia de dependências predominantemente MIT/Apache/BSD;
- será necessário publicar SBOM, NOTICE e auditoria de licenças transitivas por release;
- a primeira versão nativa terá menos funcionalidades que RepoWise, mas controlará contrato, segurança, custo e roadmap;
- o gateway atual continua válido e permite evolução incremental por capability.

## Próximas entregas

1. indexador Tree-sitter/SCIP para Java, Python, TypeScript e Go (próxima etapa; o slice atual já cobre mirror por SHA, busca lexical, símbolos básicos e boundaries de arquitetura);
2. consumer SCIP no backend canônico;
3. provider lexical Zoekt ou integração equivalente;
4. dependências/dependentes e change impact por snapshot SHA;
5. MCP workspace com `search_code`, `get_symbol_context`, `get_dependencies` e `get_change_impact`;
6. SBOM/licence scan bloqueando release quando aparecer licença não aprovada.

## Estado do slice atual (2026-08)

- `native` é o provider padrão e já está validado em Docker com mirror local pinado por SHA.
- `search_code` usa `git grep` no snapshot versionado, com evidência SHA-256 por ocorrência.
- `get_symbol_context` resolve símbolos básicos por nome/ID determinístico, com localização e evidência do snapshot.
- `architecture` entrega boundaries de diretórios com evidências do snapshot.
- RepoWise permanece adapter opcional em shadow; sua indisponibilidade não bloqueia agentes, Pullwise ou verdicts.

## Contrato do índice canônico

O indexador não expõe estruturas específicas do Tree-sitter ou do SCIP ao restante do produto. Ele
materializa, por snapshot SHA, quatro famílias de registros:

- `file`: caminho relativo, linguagem, tamanho e hash do conteúdo;
- `symbol`: id estável, nome qualificado, tipo, linguagem e intervalo de linhas;
- `reference`: origem, destino, tipo da relação e confiança;
- `evidence`: snapshot, revisão, caminho, linhas e hash do trecho retornado.

O backend consome esse contrato através de `CodeIntelligenceProvider`; os agentes e o MCP não
conhecem o parser, o banco ou o formato SCIP. A primeira implementação pode gerar somente `file` e
`symbol` para um subconjunto de linguagens, desde que a capability não seja anunciada como pronta
antes de haver evidência revision-pinned.

Rollout: (1) manifesto e validação da revisão; (2) indexação incremental por linguagem; (3) publicação
atômica do snapshot; (4) consultas somente em snapshots `READY`; (5) comparação shadow e métricas;
(6) habilitação por capability e organização.
