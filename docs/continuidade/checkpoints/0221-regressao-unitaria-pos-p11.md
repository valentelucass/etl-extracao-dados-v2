# Checkpoint0221 — regressão unitária após P11; sucessão em validação

P11_LOCAL_REGRESSION_RECORDED. 2026-09-21T23:01:17.716Z.
Anterior: docs/continuidade/checkpoints/0220-p11-dependencias-corrigidas.md; SHA-256 a0226da0fd48b7286056d4741bf80ad576d507e75fee6737d71cc8b1d35ad3da.
Objetivo: concluir pendências P10/P11/P12/P14/P15/P21 aproveitando0220,
sem repetir preparação nem converter provas históricas em aceite dos novos pins.
Estado: TESTADO_NA_CAMADA_UNITARIA; A/B físicos BLOQUEADO_POR_INPUT.

## Autoridade, limites e recuperação

Pedido anexado A/B/C: correções locais e regressão elegíveis; falta autorização
específica JDBC/nativo12.8.2. Sem SQL/DDL/COMMIT, fonte de negócio, credencial,
publicação, produção, deploy ou cutover. Rodada isolada
target/p11-regressao-20260921-01; intent.json e execution-ledger.jsonl registram
900s por tentativa,512MiB, sem rede externa; fixtures loopback/sintéticas.
Nenhuma reserva física ou saldo herdado. Sem processos próprios ativos de Maven.
Inventário inicial3739 arquivos em initial.json. Alterações públicas desta unidade:
STATES/trilha, sucessão no módulo P11 e novo catálogo/validador. POM/Java/SQL
públicos preservados. Recuperação somente por snapshots exatos do delta próprio.

## Execução e evidência

| Camada | Observado | Evidência |
| --- | --- | --- |
| Surefire inteiro |2162 casos/247 classes,0falhas/erros,4skips históricos; identidade/multiplicidade exatas|unit-04.json,unit-summary.json/XMLs|
| Build |Enforcer,Spotless,Checkstyle,compilação PASS|unit-04.stdout.log|
| JAR construção |JAR/oito libs atuais; classpath estático PASS; não executado|package-01.json,jar-static.json|
| Guard |10recusas/2checks permitidos sintéticos|guard-probe-final.stdout.log|
| Matriz C |10contraprovas PASS; sete GETs0220 com recibo, total público desconhecido|matrix-only.stdout.log,matriz-atual.json|
| Sucessão integrada |Ainda em validação; conferir próximo checkpoint|manifesto novo e validation-*|

Resultado: docs/catalogos/p11-regressao-local/resultado.json; SHA-256 5adecc8693481759e94f6ac9d90d8cafc36573e2967ee656982e418672bbcd10.
A matriz antiga/selos/ledgers permanecem intactos. Esta revisão separa preparação,
rede pública0220 e teste local0221. Há17 requisitos totais/16 externos abertos,
FEED-ACHADOS já atendido. P09 sem G01 novo e não reavaliado.39/45 e67/115.

Falhas preservadas: plugin recursos recusado; erro de cleanup da fixture
.env.local; unit03 interrompida por correção de sufixo; unit04 integral PASS.
Primeiro GuardProbe sem classpath e primeira comparação de libs com precedência
PowerShell incorreta foram corrigidos; não houve alteração de produto.
Sem ITs/cobertura, autenticação nativa/SQL, execução de aplicação ou selo P08 novo.

## Próximas ações

1. Validar sucessão/trilha/matriz e scan delimitado, corrigindo qualquer falha local.
2. Salvar checkpoint final com recibos, conferir hash e atualizar RETOMADA.
3. A/B físicos só sob ordens finitas próprias do usuário;16 inputs nominais
   por owners G02/Segurança/G05/fornecedor/Negócio, conforme matriz e origem.
