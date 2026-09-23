# 0225 — Segunda falha integral e recurso local

2026-09-22T01:05:22.343Z
Anterior:docs/continuidade/checkpoints/0224-p11-p07-timeout-de-lock-preservado.md;SHA-256 d8f39b59f82b9603138de2b87a8cd2641d6ed66b7b5c3669099c61ac5f89ac8b.
# P11 — segunda falha SQL preservada; pressao de memoria observada

P11_SECOND_FULL_FAILED. Full02:2162 unitarios/4skips historicos;79 ITs
registrados em22classes,2 timeouts SQL em AnalyticLaboratoryManifestGatesIT.
Execucao interrompida apos falhas persistidas,apenas arvore Maven propria
conferida por PID/inicio/parent/attempt. Wrapper exit1,rollback confirmado;
readback dedicado igual ao inicial0/0/453,246tabelas/1816objetos.
Apos a falha,SQL declarou process_physical_memory_low=1;zero requisicoes
ativas/bloqueadas e transacoes read/write no alvo. Isso demonstra pressao
de recursos naquele instante,nao causalidade retroativa dos timeouts.
Diagnostico atual da classe original de quatro gates,sem mudar codigo/asserts/timeouts.
P07 nao qualificado;P08 nao iniciou. A local qualificada,C corrigido;16
requisitos externos sem novo aceite. Teto36000s/vigencia originais mantidos.
Evidencias:target/p11-fisico-20260921-01/;fotografias anteriores preservadas.


Provas:full02-stop-request.json,p07-verify-02/result.json,
master/audit/tables-after-p07-failure02.log,diagnostic-after-failure02.log
e host-resource-failure02.json. A primeira trava de encerramento recusou
corretamente porque havia conhost irmao;nenhum efeito ocorreu nessa recusa.
Segunda trava confirmou PID/inicio/parent e somente Java Maven+conhost.
Falha full01 e seu diagnostico escala PASS continuam intactos.
A classe isolada atual nao substitui VerifyPhysical nem a prova do pacote.

Proximas acoes:
1. Conferir diagnostico de Manifestos e reconciliar agregados/recursos.
2. Registrar resultado real sem converter suites falhas em qualificacao.
3. Fechar busca/matriz/sucessao com falhas e limites preservados.
Recuperacao: somente processos proprios e rollback;nenhum KILL SQL,
DDL,commit de dominio ou alteracao de processo/servico de terceiros.
