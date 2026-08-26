# ADR-0010 — Providers de code intelligence e modo shadow

## Status

Aceita para implementação incremental.

## Contexto

RepoWise oferece um motor amplo de análise, mas sobrepõe o grafo do Pullwise e usa AGPL. Sourcebot
tem restrições FSL/comerciais. Acoplar agentes a qualquer contrato específico criaria lock-in e
dificultaria validar qualidade, custo e licença.

## Decisão

Criar um `SquadX Intelligence Gateway` com contrato canônico e providers plugáveis. RepoWise será
integrado como serviço separado e operará em modo shadow durante o piloto. Resultados shadow não
alteram gates, verdict ou estado terminal. A promoção de um provider exige métricas, decisão de
fonte canônica e validação jurídica/comercial.

## Consequências

- agentes e Maps dependem apenas do contrato SquadX;
- existe custo inicial de normalização e instrumentação;
- duplicidade de cálculo é aceita temporariamente para comparação;
- nenhuma fonte experimental pode bloquear PR sem nova decisão registrada;
- código AGPL não é copiado para o core proprietário.

