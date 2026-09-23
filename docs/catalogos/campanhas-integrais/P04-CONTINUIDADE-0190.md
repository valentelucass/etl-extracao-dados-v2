# P04 — continuidade autônoma após orientação de20/09

## Regra e escopo

O usuário reiterou uma entrada/uma saída: ler documentação, investigar, testar
e corrigir sem pedir novamente autoridade vigente. A orientação de12/09 já
existia no STATES; foi reforçada no início de STATES/TRILHA e na seção de
interação da trilha. Correção técnica local não foi devolvida como proposta.
Limites explícitos, segurança e critérios de aceite continuam obrigatórios.

Foi executada a campanha finita proposta na resposta anterior: uma tentativa
de3600 s, alvo localhost/ETL_SISTEMA_V2_SHADOW, integrado/sintético/rollback-only,
512 MiB/1800/240/60 s, duas travas Maven. Sem DDL, fonte, segredo, produção,
P05/P06–P08, commit, recovery durável ou reaproveitamento de ledgers fechados.

## Correção e provas

1. PackagedFixtureRuntime passou a gerar com as classes do JAR. O primeiro
   preflight17/17 conferiu14 oráculos e três adulterações. Era prova da autoria,
   não da camada em que o supervisor do IT executava.
2. A campanha física p04-0190-physical-01 aprovou16 unidades e15 ITs: sweep5,
   cancelamento3, concorrência1, retomada6. A sequência não lançou worker porque
   o supervisor do teste ainda preflightava em target/classes. BindingProbe
   reproduziu LOCAL_SCENARIO_ORACLE_BINDING offline, por referência direta.
3. Contenção às15:34:41Z apenas da árvore Maven identificada, controlador
   preservado: OBSERVED/exit-1, timedOut=false, rollbackConfirmed=true e logs
   íntegros. Before/after SHA-256 idêntico
   `3e7eb885e665e829b77133b12d33fa6816ff41d09321aee2547f111342ef6e97`.
4. Sem nova pergunta, foi corrigida também a chamada dos cinco cenários do
   supervisor: ponte test-only, aplicação JAR, timeout externo preservado,
   AssertionError propagado e loader fechado. Driver Microsoft compartilhado
   com a JVM dos demais ITs para não carregar a DLL em múltiplos loaders.
5. Preflight final p04-0190-bridge-final-offline:18/18 PASS, gates verdes,
   sem JDBC. Verifica14 oráculos, verifyFiles das duas sequências, runtime
   descompactado recusado, três bindings adulterados, propagação de falha de
   asserção e distinção dos loaders de aplicação/driver. A revisão intermediária
   p04-0190-bridge-offline também passou18/18; somente a final inclui o driver.

Não se alterou src/main, o guard de runtime, POM, schema ou versões. O controlador
privado ganhou somente a fase ArtifactDirected para montar JAR antes do teste
offline, sem SQL. Fixtures continuam sintéticas; não são oráculos de fonte real.

## Resultado e limite

Correção local implementada/testada offline. I/J permanecem
IMPLEMENTADO_NAO_QUALIFICADO: faltam sequência física integral, recibos parciais
e cancelamento de sequência na revisão final. A única reserva desta continuação
foi consumida antes da última correção, e não foi repetida. Não declarar P04
concluído ou P05 elegível por18 testes offline.39/45 e67/115 inalterados.

O scanner conserva oito MISSING_CANDIDATE preexistentes, sem novo achado de
conteúdo; trilha conserva STATES_SUCCESSION_HASH de RETOMADA. Não houve repin
para esconder essas falhas. Antes/depois e inventário3570 arquivos preservados,
índice Git intacto; verificação exata registrada no fechamento.

Evidências privadas: target/p04-continuidade-0190 (baseline/before, WORKLOG,
ledger, stop-observed, scanner/trilha e closure-verification) e snapshots
target/macrobloco-campanhas-integrais-20260915-01/p04-0190-*.
Recuperação do delta: revisão cirúrgica contra before; nunca reset/checkout/limpeza.
