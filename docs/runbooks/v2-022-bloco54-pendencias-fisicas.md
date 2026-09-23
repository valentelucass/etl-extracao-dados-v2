# B54 — provas físicas restantes

## Estado atual — 08/09/2026

Bloco 54 concluído no laboratório A–G: V021 aplicada, 25 grants, revisão v7 física,
1098 testes aprovados, 302 reservas e 30 campanhas encerradas. O [relatório atual](v2-022-bloco54-conclusao-local.md)
registra os resultados, os limites nominais, o diagnóstico V021 e a recuperação.
As afirmações de pendência/preparação/revisão final abaixo pertencem às fotografias
históricas indicadas; não são o procedimento atual. Preservadas para rastreabilidade.

## Histórico preservado

Estado: **PREPARED_NOT_AUTHORIZED** para uma sétima campanha. A sexta foi autorizada,
executou oito casos do harness, encerrou com 109/128 reservas e teve OPS02 recuperado
sob a reserva existente. Não falta escolher banco, contas, schema ou grants.

O [pacote residual executável](../../database/proposals/bloco54-residual-runtime/README.md)
fixa preflight, 19 unidades, revisão v4, hashes, aplicação, verificação e compensação.
A autorização adicional seria somente uma campanha de até 15 minutos usando esse
saldo, sem unidade nova, oitava campanha ou mudança de alvo. O helper ativo não
reabre a sexta. O módulo proposto fica isolado e não foi aplicado ao ledger real.

A revisão v4 implementa a execução temporal explícita e o alerta de contrato
recusado, com 1065 testes aprovados. Falta a prova física pelo JAR: REPLAY/FORCE_RUN,
alerta, duas janelas fora de ordem e repetição, mais configuração TLS recusada.
A matriz integral ainda exige artefato/ACL/adulterações, queda/lease/cancelamento
no pipeline oficial, falha física do sink e todos os casos de fuso/mês/degradação.
O saldo preparado não basta para alegar o fechamento dessa matriz.

Checkpoint: V001–V020, catálogo
6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a,
5.328 linhas, 25 grants, SERVICE v15/OPERATOR v1, scope 1 v10/demais sete v1.
Oito scopes ativos, sem REPLAY/FORCE_RUN, validade original até 07/10/2026.
B53: 112 reservas, 95 tentativas, 75 publicações e oito EXTRACTING preservados.

A compensação UTC já executada está em `target/bloco54/resume-sixth/recovery-reviewed-utc.sql`:
hash integral, locks, atualização exata v14→v15 e equivalência funcional antes
do commit; validação em outra conexão e ausência de sessões/transações confirmadas.
O script recusa repetição. Nenhum direito ou prazo foi ampliado.

Não repetir os controladores da quinta/sexta, IDs, seeds já confirmados ou o
provisionamento. Preservar ledger, logs e dados. O novo pacote gera IDs e diretório
próprios, reserva a compensação antes dos papéis temporários e retém os dois scopes
REPLAY revogados após o ensaio. Toda falha conserva evidência e exige leitura antes
de recuperar. Comparação real continua bloqueada pelos oito inputs documentados.
