# B60 — 42 casos restantes e contraprova física de cancelamento

Continuação da instrução efetiva para corrigir e concluir dentro do envelope
iniciado em 10/09/2026 03:40:10.0501172 UTC. Deadline final permanece 04:40:10.0501172 UTC.
Os dois ledgers anteriores permanecem imutáveis; 51 sqlcmd e 33 JVMs foram debitados,
com 34 HTTP sintéticos observados. Limites totais:80 JVMs/240 sqlcmd/400 HTTP;
não há reembolso nem renovação. Esta rodada usa até 43 JVMs (42 casos+1 contraprova).

32 casos físicos anteriores passaram, incluindo seis verticais e cancelamento.
MUTATE_TENANT teve os oráculos SQL/exit/HTTP corretos, mas o controlador marcou
falha porque a desconexão atrasada do cancelamento leu a configuração do caso
seguinte. A falha é preservada e esse caso será repetido com novos IDs.
Compensação confirmou SERVICE21, quatro scopes v4 revogados, políticas v2
revogadas, 32 grants e preservação do multiconjunto histórico.

Correção do servidor sintético: cada conexão captura cenário, grupo, data, delay
e permissão de desconexão esperada antes de processar a requisição. Testes reais
loopback reproduziram o erro anterior, comprovaram a correção e mantiveram a recusa
de desconexão inesperada. Tetos HTTP/nós/bytes desta rodada descontam o consumo
anterior:366 requests,788 nós,16.766.078 bytes de resposta,8.369.356 de requests.

As duas requests de replay agora conservam start/endExclusive da origem SQL,
como exige o contrato de ctl.usp_control_plane_start_execution. A origem pode
estar publicada ou parcialmente falha, sempre no mesmo namespace/janela.
Essa correção de entrada não relaxa o SQL nem seus oráculos.

Mesmo localhost/ETL_SISTEMA_V2_SHADOW, principals, permissões e validade original.
Sem DDL/Java novo. Ativação: SERVICE21→22, quatro Users scopes4→5 ativos, duas
políticas DQ v3 novas; v1/v2 ficam revogadas. Compensação: SERVICE22→23,
scopes5→6 revogados, duas políticas v3 revogadas e dois grants removidos.
Bundles existentes são reutilizados somente após conferir seus bytes e ACLs;
apenas novo diretório de requests sob o hash da revisão é acrescentado.

O preflight exige ausência dos novos UUIDs e das chaves sintéticas exatas;
as ocorrências dos 32 casos anteriores são preservadas. Readback final inclui
origens anteriores e casos novos. Dados, histórias, recibos, policies e V024 não
são apagados. Um mapping SERVICE e quatro scopes exatos têm oráculos de perfil;
os demais registros anteriores devem permanecer no multiconjunto de hashes.

Aceite depende da união verificável dos 74 casos originais, da contraprova adicional,
dos negativos SQL finais e da recuperação; 32+42 não é autorização para ignorar falhas.
