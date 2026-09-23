# Checkpoint 0046 — B60, recuperação e concorrência pendente

Anterior: 0045-bloco60-retomada-dos-casos-restantes.md; SHA-256
a29498041edc48cb338d74a5e473575a09c8062595db8df85494c08703013b4d.

B60 físico: 72/74 casos comprovados; concorrência ainda sem aceite após encerramento da janela.
72/74 casos originais comprovados e rechecagem de cancelamento aprovada: 32 + 7 + 9 + 10 + 8 + 7 resultados físicos preservados, incluindo revisões explícitas dos oráculos AUDIT_NULL, HALT_BEFORE_APPLY e LEASE_EXPIRED_REFUSED.

Cumulativo: 160/240 sqlcmd, 77/80 JVMs e 71/400 HTTP. Janela retomada por instrução do usuário: 12:15:21–13:15:21 UTC de 10/09/2026; parada física em 2026-09-10T13:14:19.2681942+00:00; recuperação em 2026-09-10T13:16:06.3891974+00:00, com dois readbacks do escrow. Sem reembolso, ampliação de saldo ou renovação automática.
Recuperação SQL: SERVICE31, replay/force desligados, quatro Users scopes v14 revogadas, políticas temporárias revogadas e dois grants retirados. V024 preservada, zero efeitos desconhecidos, sessões restritas, processos próprios ou listener 62160. Todos os demais registros históricos preservados por multiconjunto; uma partição própria de cancelamento reconciliada por reconstrução exata do hash anterior, com as duas tentativas retidas.

Alvo exclusivo localhost/ETL_SISTEMA_V2_SHADOW, identidades Windows suporte,
etl_v2_exec e etl_v2_view, fonte apenas sintética em loopback. O retorno do usuário
autorizou uma única nova janela; todas as correções compartilharam seu prazo
e os débitos antigos. UAC normal, sem bypass ou alteração de credenciais.

Correções desta retomada: effective de DQ v3 exclusivo .125 após colisão SQL2601;
entrada de teste converte DurableAuthorizationException para CONFIG_AUTH=20,
sem repetir consumo e preservando a entrada oficial/JAR; DQ v4/v5/v6/v7 exclusivos
.126/.127/.128/.129. AUDIT_NULL teve expectativa HTTP corrigida de 1 para 2 com fundamento
em V018 e V024, acrescentando estado FAILED, uma falha de DQ e ausência de selo,
publicação, aplicação e histórico. HALT_BEFORE_APPLY exige o selo já criado, estado PROMOTED, DQ PASSED e zero aplicação/publicação/histórico. As execuções falhas originais permanecem intactas.

Build Java17 completo desta retomada: 1.397 testes, zero falhas/erros, quatro skips
(três symlinks indisponíveis, uma propriedade opt-in de cotações ausente).
Somente dois arquivos Java novos de teste; os 1.956 arquivos iniciais estavam
byte a byte preservados antes da sucessão documental. JAR operacional idêntico.

Pacote físico final: database/proposals/bloco60-lease-restante/package.json;
SHA-256 f08f6243c3904f8e6daa6a2ddf8f4fde24375789c18868ecd73d7ff450708942.
Provas: target/execucao-b60-retomada-20260910-0910/physical-verification.json, onze ledgers encadeados, logs SQL
e JVM por caso, partition-diagnostic/PARTITION_RECONCILIATION.sql.log e
database/proposals/bloco60-audit-restante/audit-oracle-review.json e database/proposals/bloco60-seal-restante/seal-oracle-review.json.
Relatório A–F, gates, diff, recibo e releitura independente: target/execucao-b60-retomada-20260910-0910/final/.

Nenhum novo checkbox: 67/115 = 58,26%, 48 pendentes, 191 rotas, zero AGORA.
Não fecha V2-022 pai, Q-USR-01, V2-012a/b/c, V2-047, V2-013, V2-050 por entidade,
V2-038, release ou cutover. Não reabre V2-033 nem atribui novo aceite às cinco
verticais. Users GraphQL permanece transitório SHADOW_UPSERT_ONLY;
hasNextPage=false não prova snapshot, completude ou ausência. Legado segue escritor.

Próximas ações: definir nova janela autorizada para seed e duas JVMs concorrentes;
executar os readbacks e validação adversarial pendentes; revisar recibo/estado.
Saldo JVM: somente 3 de 80, sem reembolso. A correção de coordenação está testada
offline e não foi executada fisicamente. A hipótese de defeito exclusivo no
StreamReader não reproduziu; foi descartada e preservada. A nova coordenação
consulta a prontidão no SQL e conserva ambos os resultados mesmo se houver erro.