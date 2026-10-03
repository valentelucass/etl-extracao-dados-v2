# ADR 0056 — Amostra Coletas 6908 com staging, readback e rollback

Data: 02/10/2026. Estado: decisão técnica local, implementação qualificada offline pelo gate integral02; prova real integrada ainda não executada. Complementa a ADR 0055 sem alterar a travessia completa ou seus permits.

## Problema e decisão

O usuário solicitou uma extração ponta a ponta com poucos dados. O piloto anterior só expunha `--plan`; seu runner exigia página terminal vazia, auditoria de conclusão e promoção para comparar o resultado. Um limite de amostra não fornece essas evidências.

A entrada separada `Coletas6908PilotMain --sample` usa uma página real de Coletas 6908, guard contratual, mapper e staging JDBC existentes. Exige os dois opt-ins shadow e `coletas6908.sample.enabled`, configuração/plano/request explícitos e preflight fresco do Banco vinculado aos bytes e fingerprints dessa invocação. O preflight não é gerado pelo Runtime; hashes não comprovam autorização nem verificações físicas.

Após a página populada auditada e os batches de staging, uma fronteira intencional interrompe a travessia. O estado da ocorrência permanece `FAILED`, sem auditoria terminal, permit, promoção, watermark ou sweep. Uma exceção suprimida por falha de auditoria/transição invalida a fronteira; falha de origem ou SQL não produz recibo positivo. Não se fabrica uma página vazia.

O Banco compara somente o staging da mesma ocorrência, sessão e transação: origem/tenant, partição civil, contrato, página auditada, conjuntos de chaves, multiplicidade, batch→página e integridade do vínculo com `stg.execution_record`. O fechamento mantém rollback explícito. `SAMPLE_STAGED_READBACK_ROLLED_BACK` só é emitido depois de `close` bem-sucedido; um fechamento incerto exige reconciliação, sem retry.

## Regra técnica COL-SAMPLE-01

Na correção causal de03/10/2026, `reservationReference` do preflight deve ser uma string JSON não vazia: número/booleano não podem virar referência por coerção `asText()`. O teste CLI demonstrou número aceito antes da correção e recusa após `isTextual()`, antes de chamar o executor; arrays/objetos/nulo e os gates físicos permanecem recusados. Evidência: `target/ajustes-extracao-20261003/runtime/handoff.json` e red XML/green27 testes; ainda não é prova de emissão física do preflight. Esse ajuste preserva COL-SAMPLE-01, o vínculo de arquivos/fingerprints e a responsabilidade do Banco; não transforma uma string em autorização.

- Origem: pedido do usuário de 02/10/2026, ADR 0055 e contratos de auditoria/staging já versionados. Responsável técnico: Banco e Persistência; integração: Runtime e Qualificação. Responsável de negócio não identificado; não foi criada regra de negócio.
- Exemplo sintético: duas raízes expandidas em três linhas da página 1 devem corresponder exatamente às três linhas de staging, inclusive multiplicidade e batch de origem. Ausência de uma linha, raiz extra, contrato/tenant divergente, quarantine ou rollback incerto recusa o recibo.
- Testes causais: `Coletas6908SampleRunnerTest` percorre a composição operacional com HTTP/SQL falsos; `ColetaShadowRollbackTrialTest` verifica a API staging-only e recusas. Seus resultados executados e bytes estão em STATES e nos recibos da unidade, não nesta decisão.
- Contratos afetados: nova CLI SAMPLE e API `verifyObservedSample`; auditoria/staging SQL existentes são consumidos sem DDL. `verifyObservedTraversal`, `ContractPromotionPermit`, travessia completa, `Main` operacional e autenticação Windows existente permanecem com seus gates.

## Limites e recuperação

Uma página, até cinco entidades distintas, até mil linhas físicas e 10 MiB por resposta; uma tentativa, sem redirect, timeout por chamada até 30 s e deadline cooperativo do piloto até 60 s. O teto físico do controlador e o preflight/readback externo continuam sob Banco. A amostra normalmente requer `/info` e página 1. Cada rodada conserva sua reserva e para no primeiro erro ou teto; HTTP 429 interrompe a frente externa.

O recibo demonstra somente observações comparadas: `promoted=false`, `windowCompleteness=false`, `childCompleteness=false` e `presenceComparedCells=0`. Não prova paridade de valores de todos os campos, snapshot, completude da janela, estabilidade global de identidade ou prontidão produtiva. Rollback de dados não prova contador IDENTITY inalterado; Banco deve observar esse impacto no gate físico.

Não habilita SQL auth, login, grants, certificado, listeners, DDL/Flyway, agendamento ou cutover. O acesso diagnóstico `sa` existente não satisfaz automaticamente a sessão JDBC Windows/TLS do piloto. Fonte real e invariantes do shadow precisam de evidências atuais antes de qualquer trial.

Reversão de código: retirar somente a entrada SAMPLE e seus componentes, mantendo o caminho da ADR 0055. Um trial físico deve reverter sua transação e ser reconferido pelo Banco. Nesta unidade não houve escrita SQL nem trial real integrado; o checkpoint 0421 registra a parada externa HTTP 429. Gate offline02: Surefire2456/0/0/5 e Failsafe7/0/0/0, com limites de cobertura originais e zero HTTP/SQL real; recibos e limites atuais constam em STATES. O aceite offline não resolve STOP429 nem gates físicos do shadow.
