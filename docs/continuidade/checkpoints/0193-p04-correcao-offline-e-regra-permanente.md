# 0193 — correção offline comprovada; regra permanente reforçada

## Identificação e objetivo

- Data20/09/2026 UTC; objetivo: executar autonomamente, corrigir e testar P04,
  com uma entrada/uma saída, sem devolver correção local como nova pergunta.
- Anterior0192 SHA-256 `811e5e10f4294499ece5fadb9ed1a6bb756920624a0e3720b5e196d572eccdd1`.
- Correção local TESTADO_NA_CAMADA offline; I/J IMPLEMENTADO_NAO_QUALIFICADO.
- Critérios: SEQ-03/CP-01/FIX-01, pedido P04 e reforço efetivo do usuário20/09.

## Autorização e limites

A orientação permanente de12/09 já existia no STATES. Foi reforçada no início
de STATES/TRILHA e na seção de interação da trilha: ler, investigar, implementar,
integrar, testar e corrigir até a entrega autorizada; sem confirmação de rotina.
Uma falha técnica local não é automaticamente falta de autorização.

A campanha proposta de uma tentativa3600 s foi executada e consumida; ledger
target/p04-continuidade-0190/ledger.json fechado. Não houve repetição ou novo
saldo por inferência. Os ledgers anteriores permanecem fechados e intactos.
Alvo único localhost/ETL_SISTEMA_V2_SHADOW, integrado/sintético/rollback-only,
duas travas,512 MiB/1800/240/60 s. Sem DDL/migration/fonte/segredo/produção,
P05/P06–P08, commit/índice, limpeza material ou recovery durável.

## Alterações e decisões

- Baseline3570 arquivos, before e logs em target/p04-continuidade-0190.
- QualificationPackageFixture passa a autoria ao JAR real.
- PackagedFixtureRuntime é helper test-only; CodeSource conferido, aplicação
  JAR isolada, driver compartilhado com a JVM de teste e loader fechado.
- QualificationSequenceSupervisorIT executa seus cinco métodos nessa camada,
  preservando timeout externo, asserções e propagação de falhas.
- PackagedFixtureBindingIT prova bindings, verifyFiles, ponte e negativos.
- STATES/TRILHA/CONTRATO/matriz A–N e P04-CONTINUIDADE-0190.md sincronizados.
- Nenhuma alteração de src/main, POM, schema ou versão de dependência.
- Rejeitado: aceitar runtime descompactado no guard do JAR, repinar históricos,
  declarar aceite físico a partir de teste offline ou fingir revisão humana.

## Execução e evidência

| Passo | Camada | Resultado | Evidência |
| --- | --- | --- | --- |
| Autoria inicial | Offline/JAR |17/17 PASS;14 oráculos,3 negativos | p04-0190-binding-offline |
| Campanha | SQL/JAR |16 unidades+15 ITs PASS; sequência antes do worker não qualificada | p04-0190-physical-01 |
| Causa delimitada | Offline |BindingProbe reproduziu LOCAL_SCENARIO_ORACLE_BINDING em classes soltas | WORKLOG/BindingProbe.java |
| Contenção | SO/SQL |árvore própria; OBSERVED/exit-1; sem timeout; rollback e agregados iguais | stop-observed/result/before/after |
| Ponte intermediária | Offline |18/18 PASS | p04-0190-bridge-offline |
| Ponte final | Offline/JAR |18/18 PASS,zero skip,gates verdes;14 oráculos,2 sequências/verifyFiles,3 adulterações,recusa descompactada,asserção propagada/driver compartilhado | p04-0190-bridge-final-offline |
| Fechamento | Offline |revisão Java exata,3570 arquivos preservados,índice intacto,JSON/UTF-8/diff PASS,zero processos | closure-verification.json |
| Mapa | Offline |PASS33/48/9 | Test-TrilhaPreparation |
| Scanner/trilha | Offline |FAIL históricos preservados:8 ausências e hash de RETOMADA; nenhum finding de conteúdo novo | scanner-final.log/trilha.log |

Builds sob target/macrobloco-campanhas-integrais-20260915-01.
Result final offline SHA-256:
`747831ece429d89e32dec6a55c149d048b7e39620cd97b74011d9888568bae0c`.
Agregados before/after SHA-256:
`3e7eb885e665e829b77133b12d33fa6816ff41d09321aee2547f111342ef6e97`.
Scanner3584 candidatos/3575textos/1binário,sem oversized/non-text inesperado.

Efeito desconhecido: nenhum. Processos próprios remanescentes: zero.
I/J sem aceite integral: revisão final não tem sequência física completa,
recibos parciais ou cancelamento de sequência comprovados. P05/K não elegível.
39/45 e67/115 preservados. A correção não ficou apenas proposta: foi implementada
e provada offline; essa prova não substitui o critério físico pendente.

## Retomada — até três ações

1. Usar a revisão final testada e este checkpoint; não repetir diagnóstico já
   resolvido nem pedir confirmação para correção local coberta.
2. Para uma prova física futura, conferir autoridade quantitativa vigente e
   reservar campanha própria antes do efeito; não reutilizar ledgers consumidos.
3. Só aceitar I/J com todos os critérios físicos; só então considerar P05/K.

Parada factual: correção offline comprovada e teto da campanha física consumido,
não falta genérica de permissão para programar. Recuperação: delta cirúrgico
contra before, sem apagar worktree/históricos ou tocar produção.
