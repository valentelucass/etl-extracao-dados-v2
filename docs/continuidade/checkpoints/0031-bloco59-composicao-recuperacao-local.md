# Checkpoint 0031 — B59 composição e recuperação local

Data: 2026-09-09. Anterior: 0030-bloco59-request-auditoria-cancelamento.md,
SHA-256 47f1f51f57144c1f00e26a1f76d288dac9b15ccafa6369d001a9cc83e2b8f8a6.
Objetivo: concluir A–F; EM_EXECUCAO. Mandato do usuário e limites do prompt
inalterados: offline sintético, zero API/SQL físico/segredo/orçamento/instalação/
agenda/deploy/commit/push. Inventário/cópias iniciais preservados em target/bloco59-local.

A–D implementados e parcialmente testados. Root mantém autoridade Windows/SQL
real como tipo; harness injeta somente transporte/persistência sintéticos,
passa por AUTHORIZE e CONSUME antes do callback operacional real. Requests DE e
suas cinco verticais conservam o caminho anterior. Não há Map de current/history.

Comandos: runner Invoke-Java.ps1, Java17 offline heap512MiB sem clean.
- red-recovery-01 exit1: 14 testes/1 falha; positivo alcançou restrição DE da
  recuperação. Adapter agora exige GraphQL/users-snapshot, entidade usuarios,
  modo BACKFILL/REPLAY e janela técnica FULL; DE conserva sua condição.
- red-dq-seal-01 exit1: 3 testes/1 falha. Positivo root passou, DQ negativa
  ainda gravava selo (1, esperado0). Ordem de Usuários foi corrigida: travessia
  auditada em memória -> staging/candidate -> DQ -> selo durável -> apply.
- users-directed-01 exit0: 70 testes, zero falhas/erros/skips. 23 integração
  Usuários, 26 request, 4 operação DE, 6 autoridade, 11 auditoria/extrator.
  Inclui ack perdido de selo/apply, recibo válido, ausência de selo sem repetição,
  paginação malformada/caps/parcial, bindings JDBC tipados e consumo único.
Logs/exit/surefire em cada diretório. Esses números não são a suíte final.

Nova investigação: coluna agregada NULL do recibo pode virar zero por getLong.
Teste red-null-receipt-01 iniciado, processo próprio controlado pelo runner/log;
resultado deve ser conferido antes de editar correção ou repetir rodada.
Negativos adicionais de cancelamento operacional, scope e gateway sem gate
entram na mesma rodada. Nenhum resultado físico desconhecido.

Decisão SQL: V022 recovery reconhece somente cinco entidades DE e sela EXTRACTING;
a extensão para Usuários precisa distinguir GRAPHQL_PAGE_INFO e selar PROMOTED
somente após DQ íntegra. Preparar arquivo separado, não alterar/aplicar migrations
históricas. Recibo recuperado deve vir de recon.usuario_reconciliation_result,
validando aplicações/history/autorização APPLIED; nunca usar contagens genéricas
como substituto das tipadas. Estado físico/permissões requerem qualificação própria.

Próximas ações:
1. Conferir red-null-receipt-01 e corrigir causas confirmadas; completar replay,
   heartbeat, negativos de configuração/scope e sessão.
2. Preparar extensão SQL e ADR/runbook; validar artefato somente como arquivo.
3. Verify offline completo, validadores/cadeia, snapshots/diff/reverse e recibo
   final após o último delta. Nenhum aceite canônico pai ou Q-USR-01 fechado.
