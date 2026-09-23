# 0156 — Cadeia integral: contexto explícito e oráculos em construção

- Estado: EM_EXECUCAO, sem entrega ou aceite. Prosseguir no escopo integral A–N adotado pelo usuário, após compactações; sem perguntas, subagentes ou conclusão parcial.
- Predecessor entregue continua 0154; checkpoint intermediário 0155 e histórico preservados. V099 intacta; 39/45 e 67/115 sem alteração.
- Rodada privada: target/macrobloco-cadeia-integral-20260914-01. Baseline before/ 3.349 arquivos, 409 pins, 115 membros e sete guardas verificados.
- Recibos reconciliados: capture-unit-01 PASS, 21 testes/zero falha/erro/skip. context-compile-01 FAIL em quatro linhas acima de 140 caracteres, corrigidas. context-compile-02 compilou main, falhou em dois consumidores de observed no teste de sweep; Observer foi movido para a interface herdada para preservar esses consumidores.
- Candidatos: DeclaredIntegralInputs, DeclaredAnalyticSupport e DeclaredAnalyticReferences obrigatórios; runtime existente seleciona arquivos das 11 famílias, referências, relações, suplementos, período/relógio/revisões e origem/tenant. Arquivos e cobertura precisam de prova física; nenhum fallback é autorizado no modo completo.
- LocalArtifactScenario v2 está conectado ao runtime e ao novo oráculo de tuplas das 19 saídas. O contexto técnico reutiliza os comparadores existentes com chaves e apoios declarados. Ainda falta gerar e executar os dois conjuntos completos, validar replay e completar sweep/33 prévias e contraprovas.
- Migration V100 candidata mantém limites anteriores e valida origem/tenant sintéticos contra o run associado. Sete módulos SQL alterados por contexto, sem mudança deliberada de negócio; revisão física pendente. Nenhum SQL foi executado nesta rodada até este checkpoint.
- integral-compile-03 iniciado com Invoke-Build.ps1; reconciliar process.json/result.json/logs antes de nova tentativa. Scripts privados de edição executados uma vez: Integrate-Context.cjs, Build-V100.cjs, Extend-OracleContext.cjs, Integrate-ArtifactV2.cjs. Não repetir sobre fontes modificadas.
- Limites vigentes: V2-041/G01–G08 externos preservados; somente localhost/ETL_SISTEMA_V2_SHADOW integrado, migrations versionadas e provas sintéticas em rollback. Sem segredos, API, V1 executada, produção, deploy/cutover, commit/push ou índice real.

## Próximas ações

1. Reconciliar integral-compile-03 e corrigir falhas; gerar dois conjuntos completos independentes e suas expectativas, incluindo fonte/relação/suplemento alterados até SQL.
2. Executar preflight/migration autorizados e qualificar cadeia física/JAR extraído, sweep, sucesso/erro/bordas/replay/rollback e P01–P24, corrigindo toda falha local.
3. Concluir revisão de contratos/45 unidades/código sem uso, testes/gates/scan/pacote/diffs em cópia, sucessão/selo/relatório finais. Não encerrar com trabalho local autorizado pendente.
