# 0192 — supervisor no JAR; qualificação offline em andamento

## Identificação, autorização e limites

- UTC2026-09-20T15:43:26Z; continuar diagnóstico/correção sem reconfirmação.
- Anterior0191 SHA-256 `28c0a29ae84560a2feae442263dd675f56e1d7d6909dc9d9d35408e0a0ffcf90`.
- I/J IMPLEMENTADO_NAO_QUALIFICADO. Campanha física proposta de uma tentativa
  consumida/fechada; não renovada automaticamente. Correção offline continua.
- Mesmas exclusões de0191: sem P05,DDL,migration,fonte,segredo,produção/commit.
- Inventário/before: target/p04-continuidade-0190; históricos preservados.

## Alterações e decisões

O preflight17/17 provava autoria no JAR, mas o IT ainda instanciava supervisor
em classes soltas. A sondagem BindingProbe reproduziu offline
LOCAL_SCENARIO_ORACLE_BINDING na primeira sequência física. A recusa antecede
o worker e é correta; não alterar o guard para aceitar bindings incompatíveis.

QualificationSequenceSupervisorIT agora usa ponte test-only para executar os
cinco métodos no mesmo loader do JAR. O timeout JUnit externo continua cobrindo
a chamada inteira e AssertionError é propagado. O loader compartilha somente
o driver Microsoft da JVM de teste para não disputar a DLL integrada; demais
classes da aplicação vêm do JAR e CodeSource é conferido. Loader sempre fechado.
PackagedFixtureBindingIT também verifica verifyFiles das duas sequências,
recusa em classes soltas, asserção negativa e distinção do loader do driver.
Sem alteração de src/main, POM, schema ou driver.

## Execução e evidência

| Passo | Camada | Resultado observado | Evidência |
| --- | --- | --- | --- |
| Campanha | JAR/SQL sintético rollback-only |16 unidades+15 ITs PASS; sequência não qualificada | p04-0190-physical-01 |
| Sonda causal | Offline/sem JDBC |LOCAL_SCENARIO_ORACLE_BINDING no preflight descompactado | BindingProbe.java e WORKLOG |
| Contenção | Árvore própria verificada |15:34:41Z; controlador OBSERVED/exit-1, sem timeout, rollback e logs íntegros | stop-observed.json, result.json |
| Readback | SQL agregado | before/after mesmo SHA3e7eb885e665e829b77133b12d33fa6816ff41d09321aee2547f111342ef6e97 | before.log/after.log |
| Correção da ponte | Offline |p04-0190-bridge-offline em execução; revisão anterior sem compartilhamento driver | process/result |
| Revisão final | Offline |p04-0190-bridge-final-offline em execução; esta é a revisão a aceitar | process/result |

Os dois preflights são isolados e sem SQL; snapshots distintos. Não reiniciar
por ausência de resposta. Raiz target/macrobloco-campanhas-integrais-20260915-01.
Nenhum processo físico remanescente; não há domínio de resultado desconhecido.
Não confundir15 ITs aprovados com família completa de sequência, sem XML final.
Aceites novos: nenhum;39/45 e67/115 preservados.

## Próximas ações

1. Acompanhar ambos preflights e corrigir falha offline se houver; não repetir SQL.
2. Conferir revisão testada, diff/UTF-8/JSON, histórico, índice e processos.
3. Consolidar resultado real no STATES/TRILHA e checkpoint final, RETOMADA por último.

Regra uma entrada/saída reforçada: esgotar correções locais elegíveis, sem
oferta de continuar ou pergunta de rotina. Não fabricar aceite físico nem orçamento.
