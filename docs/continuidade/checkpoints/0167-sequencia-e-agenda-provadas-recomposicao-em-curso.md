# 0167 — Sequência e agenda comprovadas; recomposição em implementação

## Identificação e objetivo
EM_EXECUCAO. Macrobloco integral A–N adotado em15/09/2026, conforme prompt preparado e anexado. Entrega final continua pendente; não encerrar nem pedir outra ordem.
Anterior:0166-sequencias-integrais-admissao-e-executor-em-prova.md; SHA256 0eacfa391198ccc445424c2a37cc72803321cb967e38d6b14598320a7431942e.
Base0165/schema102,39/45 e67/115 preservadas; inventário e snapshots em target/macrobloco-campanhas-integrais-20260915-01/baseline.json e before/.

## Autorização e limites
Somente fontes sintéticas e SQL localhost/ETL_SISTEMA_V2_SHADOW, Windows existente, duas travas Maven, commit bloqueado/rollback. Sem subagentes, perguntas, produção, fontes externas, DDL nesta frente ou índiceGit. Até1800s/seq,240s/etapa,3600s/campanha,512MiB,query60s. Reservas e PID/start por tentativa no diretório próprio. Nenhuma aprovação pendente.

## Execução e evidência
sequence-contract-01:13unitPASS. sequence-sql-01:falhaCheckstyle preservada. sequence-sql-02:2ITPASS/rollback. sequence-agenda-sql-01:4ITPASS,0falhas/erros/skips,rollback confirmado; relatório e inputs congelados na tentativa. Agenda foi consumida,33previews por etapa em savepoint preservaram o estado principal. Sem efeito desconhecido nessa tentativa após result.json OBSERVED/exit0.
Provas ainda por Java de teste; pacote novo não executado. Sem aceites produtivos novos.

## Alterações e decisões
Envelope/executor/supervisor aditivos, inputv3 separa captureDate/supplementRevision. Planner5famílias e coordinator4 fontes;LOC usa plano6fontes,USERsnapshot. Replay limitado à referênciaBOOTSTRAP admitida pelo V049. ImplementaçãoF posterior ao snapshot agenda:recomposição terminal de suplementos,5materializações,sem ciclo de captura inventado. Mudança de tarifa sem captura deve ser recusada pelo vínculo imutávelV085/V086; demais frentes continuam. Refatoração ainda não qualificada.

## Próximas ações
1. Qualificar recomposição e ampliar campanhas A/B para incremental/backfill e revisões; consultar recibos antes de qualquer retry.
2. Contraprovas, métricas4escalas, supervisor e JAR extraído; regressão completa da revisãofinal.
3. Matriz45/A–N, sucessão0165, overlay aplicado, scanner, selo/readback e sincronizaçãofinal.

Logs completos:target/macrobloco-campanhas-integrais-20260915-01/WORKLOG.md e diretórios de tentativas. Dependências externas G01–G08 permanecem por parcela; não bloqueiam implementação local. Conclusão exige A–N elegíveis, pacote executado e evidência íntegra.
