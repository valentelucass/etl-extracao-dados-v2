# ADR 0033 — Motor local de Coletas/Fretes e recuperação por evidência

- Data: 2026-09-07
- Estado: aceito para implementação local sintética, Bloco 51 / P02M.
- Origem: pedido do owner; ADRs 0009, 0010, 0011, 0012, 0014, 0022 e 0032.
- Responsável técnico: manutenção do V2; não introduz decisão de negócio.

## Decisão

`RuntimeDispatcher` consome o plano determinístico do registry e bindings tipados. Valida
todos os bindings e dependências antes de IO. Abre o ciclo uma vez e inicia cada ocorrência
imediatamente antes do despacho. V003 registra PLANNED e EXTRACTING e adquire lease na mesma
transação de start; antecipar todos os starts consumiria leases de trabalhos ainda na fila.
O comentário anterior de `RuntimePlanPersistence` descrevia somente PLANNED e foi corrigido.

`LocalColetasFretesRuntime` compõe os casos de uso existentes. O adapter comum exige o guard
da ocorrência, `/info`, observação de contrato, travessia, mappers e batches reais. A ponte
de auditoria persiste páginas no control plane e exige o terminal observado antes de aceitar
EXTRACTED/STAGED. O evento legado não informa bytes: o campo recebe zero não medido, sem
afirmação de telemetria de transporte. Dados continuam minimizados em lotes físicos de 100.

O gateway comum `ShadowPromotionGateway` tem dois consumidores concretos, as portas de
Coletas/Fretes. Candidate set, DQ e apply permanecem nos adapters e procedures existentes.
O motor não emite permits: verifica bindings e consome `ContractPromotionPermit` e
`DataQualityPromotionPermit`. SQL mantém dedupe, revalidação, reconciliação, publicação e
fronteira incremental contígua; nenhuma migration ou regra financeira foi alterada.

## Regras verificáveis

| Regra | Exemplo e comportamento | Teste em LocalRuntimeIntegrationTest |
| --- | --- | --- |
| RUN-01 | Callback vazio não publica nem libera dependente; exige recibo da ocorrência e mesma janela/namespace | callbackWithoutPublicationDoesNotReleaseDependency |
| RUN-02 | Falha de uma vertical bloqueia dependentes e conserva publicação independente | blocksDependentButPreservesIndependentConfirmedPublication |
| RUN-03 | Perda de lease interrompe trabalho; rejeição da transição terminal exige recuperação | heartbeatIsBoundedAndLostLeaseStopsWork |
| RUN-04 | Ack de apply perdido permite apenas repetir comando idempotente com permits idênticos; nunca reextrai | testes loseApplyResponse nas duas verticais |
| RUN-05 | REPLAY exige origem diferente da nova ocorrência e não avança frontier incremental | replayCarriesOriginAndNeverAdvancesIncrementalFrontier |
| RUN-06 | Cancelamento após recibo confirmado conserva publicação; antes dela falha fechado | testes cancellationBeforeSourceDoesNotFetch e cancellationAfterCommitPreservesReceipt |

## Falhas e limites de recuperação

A sessão retém no máximo um par de permits e um resumo por ocorrência; o plano limita 64
workloads. Heartbeat é cooperativo, no início e a cada terço da lease nos checkpoints.
Chamadas JDBC/fonte bloqueantes continuam dependentes de timeouts da composição: isto não
prova renovação durante uma chamada mais longa que a lease nem cancelamento físico de socket.

Uma exceção em start/transição/prepare/apply pode ocorrer após commit. A sessão marca
`RECOVERY_REQUIRED` e conserva causa interna, sem transformar incerteza em FAILED/CANCELLED
durável. Com permits vivos, `recoverPromotion()` retoma somente prepare/apply idempotentes.
O apply não exige heartbeat prévio na retomada: o commit anterior pode ter liberado a lease.
O aggregate original é fotografia imutável; o chamador recebe o resultado da recuperação,
sem executar automaticamente dependentes já bloqueados.

Perda do processo ou incerteza de start/transição não têm readback tipado no contrato atual;
`RUNTIME_DURABLE_READBACK_REQUIRED` recusa retomada presumida. Recuperação stale existente é
chamada ao abrir ciclo. Nova tentativa usa nova ocorrência/chave; replay é outro modo com
origem explícita; retry de transporte continua responsabilidade da resiliência da fonte.

## Consequências e prova

O entry point oficial continua deny-all. O harness executa adapters JDBC reais sobre um
simulador de procedures e fontes sintéticas. Não prova SQL físico, locks, dedupe set-based,
concorrência, reconstituição após restart, completude de fornecedor ou desempenho operacional.
Sidecars e candidatos relacionais não recebem joins/crosswalks. Nenhuma fonte foi consultada.

Próximo pacote local: contrato de leitura durável de ocorrência/publicação, reconstituição
de evidências/permits e recuperação após perda da JVM, incluindo limites de timeout. Sua
preparação local não depende de identidade externa; prova física Java/JDBC integral exige
autorização separada, pois o perfil atual permite apenas auditoria em rollback sem commit.

Rollback do bloco: retirar bindings da composição local e reverter somente os arquivos
desta entrega após revisão do diff. Não existe mudança de schema ou ação operacional a desfazer.
