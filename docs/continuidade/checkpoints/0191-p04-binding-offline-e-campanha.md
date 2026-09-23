# 0191 — binding offline aprovado e campanha P04 reservada

## Identificação, autorização e limites

- UTC2026-09-20T15:14:28Z; objetivo continua concluir P04 com uma entrega consolidada.
- Anterior0190 SHA-256 `31fc895f21dfb30566ff0ccfceec5f64224e44062bcbcbec2135bd5453e79aa5`.
- Estado EM_EXECUCAO. Resposta efetiva do usuário manda continuar correção/testes
  sem reconfirmação; limitada à campanha proposta de uma tentativa3600 s.
- Ledger `target/p04-continuidade-0190/ledger.json`, reservado antes do efeito;
  antigos fechados/preservados, nenhum saldo transferido. I/J não aceitos ainda.
- Alvo localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado, sintético/rollback-only,
  duas travas,512 MiB,1800 s sequência/240 s etapa/60 s SQL. P05, P06–P08, fonte,
  segredo, DDL, migration, produção, commit e recovery durável excluídos.

## Alterações e decisões

- Inventário3570 arquivos e cópias before em target/p04-continuidade-0190.
- PackagedFixtureRuntime/QualificationPackageFixture/PackagedFixtureBindingIT:
  autoria contra JAR real, não repin posterior e não alteração do guard produtivo.
- Regra uma entrada/saída destacada em STATES/TRILHA; SEQ-FIX-01 documentada.
- Controller ArtifactDirected monta JAR antes de preflight offline, sem SQL.
- Delta reversível por before, sem remover trabalho anterior.0190 e históricos intactos.

## Execução e evidência

| Passo | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Preflight | Offline/JAR/512 MiB |17/17 PASS,14 oráculos e3 contraprovas; gates verdes | p04-0190-binding-offline/result.json e surefire |
| Alvo | master read-only | nome exato ONLINE | consulta da rodada |
| Processos anteriores | SO read-only | nenhum próprio ativo | consulta da rodada |
| Campanha | Physical/3600 s | reserva e início; resultado pendente | p04-0190-physical-01/process.json e futuro result |

Raiz de builds: target/macrobloco-campanhas-integrais-20260915-01.
Observador delimitado: target/p04-continuidade-0190/Observe-Physical.ps1.
Não repetir execução por retorno perdido; conferir process/result/recibos.
Aceites novos: nenhum. Contadores39/45 e67/115 preservados.

## Até três próximas ações

1. Observar a mesma campanha e seus limites; não iniciar outra simultânea.
2. Conferir todos os critérios I/J, rollback/agregados e ausência de processos.
3. Sincronizar STATES/trilha, verificações, checkpoint final e RETOMADA por último.

Erro inesperado/timeout/limite/rollback incerto exige contenção só da árvore própria,
preservando controlador e recibos. Atualização de andamento não é saída final.
