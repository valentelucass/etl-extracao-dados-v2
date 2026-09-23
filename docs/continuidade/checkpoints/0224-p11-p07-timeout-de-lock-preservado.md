# 0224 — Falha P07 preservada e reconciliada

P11_P07_LOCK_FAILURE_PRESERVED. 2026-09-22T00:36:03.449Z
Anterior:docs/continuidade/checkpoints/0223-p11-nativo-qualificado-p07-em-execucao.md;SHA-256 e21baf1480ea4d870b670b4cb0e23baf9162ef30c27b10f19cb98422e90aed8d.
Objetivo/autoridade de0223 permanecem;sem ampliacao de alvo,vigencia,teto ou efeitos.
Full01:2162 unitarios/4skips;492 ITs,491PASS/1erro de lock apos rollback
no caso[4],256raizes repetidas. Nao foi falha da escala1024. Cobertura PASS,
mas Maven exit1. Falha,XMLs e logs originais imutaveis em p11-p07-verify-01.
Recibo:target/p11-fisico-20260921-01/p07-verify-01/result.json;SHA-256 cf4a5b666ec7f1e984b58f9afe7e03bd9ab7cd73fec1e83778dd86b6b93773ce.
Pipeline original STOPPED/P07_NOT_PASS;nenhum pacote iniciado.

Readback apos falha:agregados iguais0/0/453,246tabelas/1816objetos.
Consulta agregada de locks/sessoes:zero ativos,bloqueados,transacoes/JDBC;
nao prova a identidade ou origem da contencao no instante passado.
Evidencias:readback-p07-failure.ps1,lock-after-failure.log e respectivas
reservas/resultados em target/p11-fisico-20260921-01/. Sem KILL ou alteracao de SQL/timeouts.
Diagnostico atual:scale-diagnostic-01,original RelationalLaboratoryScaleIT
com quatro casos,teto Maven900s/wrapper1200s;mesmo snapshot e pins.

Proximas acoes:
1. Conferir diagnostico de escala/readback;se falhar,investigar causa local antes de nova execucao.
2. Se passar,reservar VerifyPhysical02 integral;preservar01 e seus erros.
3. Replanejar apenas tetos futuros ainda nao reservados por numero real de etapas
(A/B7,referencia antiga6,mutações1,admissoes0),mantendo teto total36000s;
qualificar pacote somente apos novo P07 completo.
Sem aceite externo novo;39/45 e67/115 intactos;nenhum efeito desconhecido.
