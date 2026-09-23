# B60 — retomada dos casos restantes

O usuário retornou após ser informado do encerramento da janela e pediu continuar
até concluir o bloco. Uma nova janela de até 60 minutos começa no primeiro OPEN,
com início permitido entre 12:08:56 e 12:45:00 UTC em 10/09/2026. Esse pedido
não devolve saldo: 54 sqlcmd, 33 JVMs e 34 HTTP continuam debitados dos tetos
cumulativos 240/80/400. Nenhuma nova janela automática é permitida.

São os mesmos 43 casos preparados no pacote 61720936…1dca7, ainda não iniciado:
42 casos originais e uma contraprova de cancelamento, conservando 32 provas válidas.
Requests, SQL, JAR, bundles, collation corrigida, oráculos e limites preservados.
A mudança operacional é o controle da janela explicitamente retomada pelo usuário.

Alvo exclusivo localhost/ETL_SISTEMA_V2_SHADOW, suporte elevado pelo UAC normal,
etl_v2_exec e etl_v2_view. Fonte somente sintética em loopback. Não há DDL ou
credencial nova. Preflight exige V024/SERVICE21/scopes4 e ausência de colisões.
Ativação SERVICE22/scopes5; compensação SERVICE23/scopes6 revogados, duas policies
v3 revogadas e dois grants removidos, preservando dados, histórico e evidências.

Registro: target/execucao-b60-retomada-20260910-0910/USER-INSTRUCTION.txt.
O aceite depende dos 74 casos originais, contraprova de cancelamento, negativos
SQL finais, recuperação e revisão. Não fecha nenhum critério maior do roadmap.
