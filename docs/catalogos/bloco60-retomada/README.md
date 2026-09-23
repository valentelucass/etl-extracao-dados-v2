# B60 — recuperação e pendência de concorrência

B60 físico: 72/74 casos comprovados; concorrência ainda sem aceite após encerramento da janela.
72/74 casos originais comprovados e rechecagem de cancelamento aprovada: 32 + 7 + 9 + 10 + 8 + 7 resultados físicos preservados, incluindo revisões explícitas dos oráculos AUDIT_NULL, HALT_BEFORE_APPLY e LEASE_EXPIRED_REFUSED.

Cumulativo: 160/240 sqlcmd, 77/80 JVMs e 71/400 HTTP. Janela retomada por instrução do usuário: 12:15:21–13:15:21 UTC de 10/09/2026; parada física em 2026-09-10T13:14:19.2681942+00:00; recuperação em 2026-09-10T13:16:06.3891974+00:00, com dois readbacks do escrow. Sem reembolso, ampliação de saldo ou renovação automática.
Recuperação SQL: SERVICE31, replay/force desligados, quatro Users scopes v14 revogadas, políticas temporárias revogadas e dois grants retirados. V024 preservada, zero efeitos desconhecidos, sessões restritas, processos próprios ou listener 62160. Todos os demais registros históricos preservados por multiconjunto; uma partição própria de cancelamento reconciliada por reconstrução exata do hash anterior, com as duas tentativas retidas.

[Checkpoint 0046](../../continuidade/checkpoints/0046-bloco60-recuperacao-e-concorrencia-pendente.md).
[Pacote final](../../../database/proposals/bloco60-lease-restante/package.json),
SHA-256 f08f6243c3904f8e6daa6a2ddf8f4fde24375789c18868ecd73d7ff450708942.
Provas: target/execucao-b60-retomada-20260910-0910/physical-verification.json e final/.

O manifesto sucede a fotografia anterior de 32/74, preservando seus bytes,
os 2.704 artefatos do recibo histórico, os 1.956 arquivos iniciais e quatro
snapshots exatos de continuidade. A revisão de AUDIT_NULL conserva a falha
original e fortalece os oráculos SQL; não se reescrevem logs ou ledgers.
Java17 1.397/0/0/4, com motivos dos skips em java-and-preservation-verification.json.
Nenhum novo checkbox ou aceite de fonte real, release ou cutover.