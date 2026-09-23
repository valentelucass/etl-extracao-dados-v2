# B60 — correção da collation do preflight

A revisão 60e96d5d…f5db parou antes da ativação: a variável de tabela das chaves
sintéticas herdou Latin1_General_CI_AS, enquanto source_key usa Latin1_General_100_BIN2.
O erro SQL468 impediu a consulta de colisões; preflight/perfil SERVICE21 passaram.
Não houve DML, ativação ou JVM. Os três sqlcmd foram debitados sem reembolso.

Esta revisão declara explicitamente a mesma collation da coluna comparada.
Não altera objetos SQL, dados, chaves ou critérios de colisão. O parser SQL e
a sintaxe do controlador passaram. Demais arquivos,43 casos e32 provas anteriores
são os bytes do pacote restante, preservados integralmente.

Mesmo orçamento cumulativo:54 sqlcmd e33 JVMs já debitados,34 HTTP observados;
tetos80/240/400 e deadline10/09/2026 04:40:10.0501172 UTC, sem renovação.
A recuperação continua SERVICE23/scopes6 revogados, v3 policies revogadas,
dois grants removidos e preservação de todos os registros históricos.
