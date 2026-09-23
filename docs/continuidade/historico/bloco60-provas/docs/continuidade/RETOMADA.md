# Retomada — B60 com 72 casos e recuperação comprovados

B60 físico: 72/74 casos comprovados; concorrência ainda sem aceite após encerramento da janela.
72/74 casos originais comprovados e rechecagem de cancelamento aprovada: 32 + 7 + 9 + 10 + 8 + 7 resultados físicos preservados, incluindo revisões explícitas dos oráculos AUDIT_NULL, HALT_BEFORE_APPLY e LEASE_EXPIRED_REFUSED.
[Checkpoint 0046](checkpoints/0046-bloco60-recuperacao-e-concorrencia-pendente.md).
SHA-256: 149fc96d26a46194e0302b8c1a62b9e34d7d6001bd3fa5b81172f109ee1177e3

Cumulativo: 160/240 sqlcmd, 77/80 JVMs e 71/400 HTTP. Janela retomada por instrução do usuário: 12:15:21–13:15:21 UTC de 10/09/2026; parada física em 2026-09-10T13:14:19.2681942+00:00; recuperação em 2026-09-10T13:16:06.3891974+00:00, com dois readbacks do escrow. Sem reembolso, ampliação de saldo ou renovação automática.
Recuperação SQL: SERVICE31, replay/force desligados, quatro Users scopes v14 revogadas, políticas temporárias revogadas e dois grants retirados. V024 preservada, zero efeitos desconhecidos, sessões restritas, processos próprios ou listener 62160. Todos os demais registros históricos preservados por multiconjunto; uma partição própria de cancelamento reconciliada por reconstrução exata do hash anterior, com as duas tentativas retidas.

Estado autoritativo: STATES.md. Provas: target/execucao-b60-retomada-20260910-0910/physical-verification.json.
Relatório A–F, testes, diff, recibo e verificação independente: target/execucao-b60-retomada-20260910-0910/final/.
Pacote final SHA-256 f08f6243c3904f8e6daa6a2ddf8f4fde24375789c18868ecd73d7ff450708942.
Não repetir os 72 casos comprovados nem renovar a janela automaticamente. Faltam CONCURRENT_A/B, readback agregado e validação adversarial não alcançados.
Java17 1.397/0/0/4; somente entrada de teste e três testes novos, JAR preservado.
67/115, 48 pendentes, 191 rotas, zero AGORA; nenhum novo checkbox de critério maior.
Qualificação sintética local não equivale a fonte real, completude ou cutover.