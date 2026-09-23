# 0160 — Apoios em lotes e regressão V099 preservada

## Objetivo e autorização

**EM_EXECUCAO**, sem entrega parcial. Pedido A–N integral adotado em
`target/preparacao-macrobloco-cadeia-integral-20260914-01/`; P01–P24, 45 unidades,
uma entrada/uma entrega final, sem perguntas ou subagentes. Predecessor entregue
0154; progresso anterior0159 SHA256
`1568918afa03796f2a383b150d1c91245f56e3b7c4834224919e67448cfb908e`.

Preservar 39/45, 67/115, V099 e macrobloco anterior. Sem segredos, API, V1
executada, produção, deploy, cutover, serviços ou índice Git real. SQL somente
localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado, duas travas e rollback.
Nenhuma aprovação pendente. Baseline privada3.349 arquivos; V100–V102 instaladas,
não reaplicar nem reescrever. Próxima migration livreV103, somente se necessária.

## Evidência e correções desta unidade

Rodada `target/macrobloco-cadeia-integral-20260914-01`.

- physical16:2.067 unitários, zero falha/erro, quatro skips históricos. Nove IT,
  dois erros, rollback confirmado. Scope estrangeiro recusado antes de criar
  ciclo; dois scopes com mesmas chaves/datas passaram. Três casos de referências
  passaram, inclusive retry/overlap/TVP duplicada e binding estrangeiro.
- Replay16 recusou alteração da revisão com o mesmo evidenceId imutável. A autoria
  passou a declarar evidenceId por revisão; nenhuma regra SQL foi flexibilizada.
- A falha COLLECTION_SNAPSHOT_INVALID legada tentava usar fixtures e política
  fixa. O modo integral agora recusa essas flags antes de SQL; falhas de apoio
  são provocadas nos próprios arquivos, com hashes atualizados. NONE e wrapper
  RASTER_INCOMPLETE sobre a fonte declarada permanecem suportados.
- DeclaredAnalyticSupport deixou de consultar/gravar por linha. Lotes16 usam
  OPENJSON parametrizado para resolver identidades e os consumidores JDBC/TVP
  existentes; mapas locais limitados ao lote. Fiscal cruza root/part/component
  explícitos; ordinal apenas correlaciona a resposta à linha solicitante.
- batch-compile01 PASS. physical17:2.067 unitários sem falha/erro, quatro skips;
  oito IT, uma falha de esperado, zero erro, rollback confirmado. A2/B24 completos
  passaram cinco fatos,19 SQL,33 previews. Chaves MAN/fiscal desconhecidas recusadas
  antes de materialização; capturas independentes preservadas. Falhas de dependência
  e recusa das três flags de fixture passaram.
- Escalas17:A2=55 páginas/92 registros/130.571 bytes/456 statements/26.954ms;
  B24=251 páginas/1.104 registros/1.565.871 bytes/1.182 statements/87.543ms.
  Heap antes/depois A114.049.928/65.585.728; B172.652.320/134.973.632 bytes.
  Maior batch observado12/20, zero inflight final. Incluem capturas adicionais
  de Coletas para sweep e os dois caminhos de Fretes; não são raízes distintas.
- Replay17 chegou aos fatos e comparações finais; SQL02 coluna11 tinha duas
  divergências no esperado de CT-e Criado em. Autoria corrigida para14Z→11-03,
  precisão7 truncada conforme contrato. Prova completa ainda em revalidação.
- Referências agora recusam versão/campos internos desconhecidos nas cinco
  famílias, preservando ausência/NULL opcional e tipos nos importadores existentes.
  Cinco contraprovas novas em admissão; testes de entradas faltantes instanciam
  diretamente DeclaredIntegralInputs para não depender da divergência do oráculo.
- v099-regression03:16/16PASS contra procedures instaladas, sem DDL, contagens
  antes/depois iguais. Tentativa01 falhou ao decodificar diagnóstico SQL OEM850;
  tentativa02 executou done com PASS, mas wrapper vazava VoidTaskResult. Bytes,
  recibos e reconciliação preservados;03 descarta o resultado técnico e normaliza
  diagnóstico para UTF8, mantendo raw.bin. Não reaplicouV099.
- Sucessor `IntegralChainSuccession.psm1` criado; EntityAlignmentSuccession o
  consome e compõe mapas/snapshots. Ainda não há manifesto final nem validação
  integral da sucessão. O checkpoint0162 foi reservado no contrato para entrega.
- Leitura V1 somente fonte: Localização usa sequence_number/hash; Manifestos
  sequence_code/pick/mdfe/metadata; Usuários mapper enabled e revisão operacional
  de08/09 usa filtro updatedAt. V2 preserva suas identidades e USERS_SNAPSHOT.
  Nenhum comando ou teste V1 executado.

## Tentativa ativa e recuperação

**physical18**, PackagePhysical,1.800s/heap512, testes Replay/References/Support,
está em execução. Consultar `result.json`, `process.json`, logs e contagens antes
de repetir. Inclui fechamento de referências e esperado temporal corrigido.
O script de JAR extraído teve regex corrigida para os IDs reais SQL-01..SQL-19;
continua **não executado**. Pacote final/supervisor/JAR precisam de prova efetiva.

## Próximas ações — até três

1. Reconciliar18, corrigir falhas locais, gerar pacote/exemplos e executar o JAR
   extraído A2/B24 e recusas; concluir testes de propagação/bordas pendentes.
2. Completar revisão11 famílias/classes/45 unidades, suíte final completa,
   cobertura/gates/scanner e revisão de diffs. Preservar identidades e quatro skips.
3. Concluir sucessão M, matrizes A–N/45/P01–P24/G01–G08, diff/overlay aplicados
   em cópia, selo/readback e continuidade. Não encerrar com trabalho local pendente.
