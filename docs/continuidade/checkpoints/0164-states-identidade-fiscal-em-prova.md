# 0164 — STATES: identidade fiscal em prova

## Estado e autoridade

EM_EXECUCAO da ordem target/preparacao-execucao-states-20260915-01/PROMPT-EXECUCAO-STATES.md. Base0162/schema102,39/45 e67/115 preservados. Não é entrega final. A antiga parada por admissão nominal foi revogada. Sem perguntas/subagentes ou outro prompt.

## Provas observadas

Quatro divergências anteriores corrigidas em cinco arquivos de produção: decimais Raster/expansões, escala de admissão e memória/cancelamento dos suplementos. support-green-03 passou42testes; preflight128páginas/8192linhas, duas validações em2575ms contra156570ms do intermediário. Filtro128KiB+64chaves mantém comparação exata, inclusive colisão de hash; não se alega platô produtivo. decimal-sql-01 passou19unit/2IT,11famílias/5fatos/19SQL/33previews e rollback.

full-verify-02 está em execução com a revisão anterior ao quinto defeito: PID54984, início2026-09-15T19:08:41.9674395Z, sessão4666, budget3600s,heap64–512MiB,logs16MiB.238XMLunitários completos:2111testes/0falhas/0erros/4skips; IT em andamento. Consultar processo/result/XML antes de qualquer novo SQL. Alterações posteriores não podem ser qualificadas por essa revisão.

Quinto defeito reproduzido: fiscal-identity-red-01 exit1 observado,14testes/2erros. Duas tuplas fiscais STRING distintas, contendo separadores permitidos, eram recusadas como INTEGRAL_SUPPORT_DUPLICATE. Duplicatas exatas continuaram recusadas. PID54968 encerrado;semSQL. Representação interna foi corrigida para array JSON de três chaves, sem mudar chave de negócio/SQL. fiscal-identity-green-01 está em execução, sessão27421; consultar process.json/result.json antes de repetir. Novos autores A2/B24 incluem raízes/parcelas FAT cujas concatenações antigas colidem, série explícita por raiz e contraprovas SQL01/SQL13; ainda faltam provas SQL/JAR dessa revisão.

Diff-rehearsal-01 aplicado/revertido/reaplicado e overlay aplicado:3475arquivos após3465;10novos/13alterados/zero removidos, bytes iguais. É ensaio anterior à correção fiscal. Scanner completo desse ensaio PASS3475/zeroachados. V09916regressões PASS/rollback. Scanner16autotestes,7guards, catálogo11/2437 e gate progressivo passaram. Preservação0162:27pins/2829membrosZIP e V099 íntegros; índice real ba3a0ac43a72350689bf297f2b13a6fdc502e00eed40b90f83682688b9bcdac4 intacto.

## Falhas e recuperação

support-green-02: JVM falhou antes dos testes por memória nativa; processos encerrados, diagnóstico sanitizado preservado, corrigido-Xms64m com teto512MiB. Dump de ambiente não lido/distribuído. full-verify-01 encerrado por revisão superada com PID/start/rollback reconciliados, sem PASS. Gerador privado de entrega teve sintaxe corrigida antes de executar; snapshot falho e parse corrigido preservados. Nenhum efeito desconhecido desses passos permanece.

## Continuidade — até três ações

1. Observar fiscal-identity-green-01 e full-verify-02, corrigir falhas e reconciliar processos/rollback; preservar recibos da revisão anterior.
2. Executar prova física A2/B24 fiscal+decimal e gate final da revisão, sem SQL concorrente, orçamento compatível com medições observadas.
3. Qualificar pacote/JAR extraído/recusas/ARTIFACT; fechar matrizes45/A–N/75, revisão, diff/overlay finais, sucessão, scanner, selo/readback e checkpoint0165. Não encerrar antes.

Rodada/recibos:target/execucao-states-20260915-01/WORKLOG.md. SQL só localhost/ETL_SISTEMA_V2_SHADOW, Windows, duas travas Maven+receipt, commit bloqueado/rollback; nenhum DDL/migration reaplicado. V2-041:sem segredos/.env/API autenticada/V1 executada/produção/serviços/agendamentos/deploy/cutover/commit/push/índice real. V1 lida em seis escopos registrados, sem auditoria completa alegada. G01–G08 retêm apenas parcelas externas.
