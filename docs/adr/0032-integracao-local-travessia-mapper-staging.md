# ADR 0032 — Integração local da travessia ao staging de Coletas e Fretes

- Status: implementação local, dentro da composição pendente de V2-022
- Data: 2026-09-07
- Escopo: Java, gateways injetados e testes sintéticos; operação continua em V2-022b

## Contexto

O pedido do owner priorizou construir o código complexo já sustentado pelas decisões locais.
ADRs 0004, 0009, 0010, 0011, 0012, 0014, 0018, 0022, 0023 e 0027 estabelecem paginação
defensiva, memória limitada, identidade, invalidação de evidência e separação entre staging e
publicação. Os casos de uso antigos de Coletas/Fretes leem uma página; seus mappers e gateways
de staging já existem, mas não estavam compostos numa travessia da entidade.

`per=100` limita entidades distintas, não linhas físicas. A expansão de uma página pode exceder
o teto físico dos batches de domínio; entregar a página inteira ao construtor desses batches
falharia. Deduplicar na JVM descartaria observações necessárias à promoção set-based.

## Decisão

`DataExportStagingPipeline` compõe o streamer existente, um mapper tipado e um consumidor síncrono.
`ExtrairColetasDataExport` e `ExtrairFretesDataExport` são seus dois consumidores concretos.
Não há novo transporte, configuração operacional, scheduler, endpoint ou banco.

- A execução começa na página 1 e exige template correspondente, limites, metadata validada e
  guard do contrato da ocorrência. A composição deve fornecer o gateway protegido pelo
  `DataExportContractGate` do mesmo guard; testes podem fornecer gateways sintéticos que observam
  suas respostas no próprio guard. O pipeline recusa página sem observação na sequência desse guard.
- Uma página permanece em voo; `forEachRecord` copia defensivamente somente a linha entregue,
  evitando a segunda cópia da página inteira feita por `records()`.
- Cada lote tem até 100 linhas físicas, inclusive quarentenas. Número de batch é monotônico na
  tentativa; ordinal reinicia em 1 por batch. A última fração de cada página é enviada imediatamente,
  e uma página vazia não cria batch. Não há dedupe, relação, agregação de domínio ou retry local.
- Cancelamento é reavaliado antes/depois do mapper e do staging. Exceção de mapper, staging,
  fonte ou auditoria invalida a evidência da ocorrência e impede sucesso retornado. Lotes parciais
  já escritos não são apagados: permanecem isolados pela execução e sem permit de promoção válido.
- Nova tentativa precisa de ocorrência/guard próprios e reinicia página e batch em 1. Isso prova
  somente o reinício da ingestão; recovery durável, lock e publicação idempotente não são alegados.
- O resultado é exclusivamente o resumo de travessia local existente. Página terminal não prova
  completude; este componente não chama `complete()`, candidate set, DQ, promoção ou publicação.

## Limites e aceitação

Os testes exercitam os mappers e batches reais com gateways em memória. Formas e valores de
decisão nas fixtures são exclusivamente sintéticos; não ampliam os sete paths comprovados do
6389 nem estabelecem novos contratos do fornecedor. O `Main` e o composition root continuam
deny-all. Não há ligação de Coletas a Fretes, sidecar, watermark, rede ou JDBC nesta fatia.

Esta entrega compõe uma parte local de V2-022; não conclui V2-022b, V2-042b/c, V2-012,
V2-046, V2-047, V2-050 ou V2-038, nem altera a contagem de macroetapas/rotas concluídas.
O dispatcher com dependências duráveis e os gates de promoção continuam trabalhos separados.

Rollback de código: retirar os dois novos casos de uso, pipeline, porta de batches e o método
aditivo de visita. Nenhuma migration, dado ou configuração precisa de rollback.

Validação focada: `mvnw.cmd --offline --batch-mode --no-transfer-progress -Dtest=DataExportStagingPipelineTest,DataExportPageResponseTest,DataExportPageStreamerTest,DataExportContractGateTest,ArchitectureRulesTest test`.
O fechamento exige ainda `clean verify`, scanner offline e sincronização do estado.

## Evidência de fechamento local

Os 25 testes novos passaram. `clean verify` offline sob JDK 17 passou 974 testes, zero falhas,
zero erros e quatro skips esperados, com Enforcer, Spotless, Checkstyle e todos os thresholds
JaCoCo verdes. A limpeza padrão encontrou interferência em `target/test-classes` antes dos testes.
Foi usada uma cópia temporária do POM com somente `build.directory` diferente, sob
`target/ingestion-validation-20260907`; a equivalência XML foi verificada e a cópia removida.
Nenhum processo foi parado e o POM canônico permaneceu intacto. Os relatórios da validação estão
na saída isolada. Scanner offline, validators de Coletas/Fretes e sincronização da trilha passaram.
