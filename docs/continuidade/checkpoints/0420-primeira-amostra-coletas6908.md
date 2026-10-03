# 0420 — Primeira amostra real Coletas 6908

- Data: 02/10/2026. Anterior: 0419-vm-powershell-portatil-trilha-e-continuidade.md, SHA 13E77B5667CA895A8F561C6B0CED14403CA5754A0AFBD8F6AB2DCDA7A73C2750.
- Objetivo: pequena extração recente para teste. Instrução: “tanto faz, pode extrair pouco dos ultimos dias para testarmos”. Estado: TESTADO_NA_CAMADA, amostra parcial real.
- Decisão: instrução atual delega origem/janela e autoriza rodada isolada com configuração provisionada. Não comprova G01 nem elimina holds para outras operações. CONTEXTO_GLOBAL continua ausente; nenhum efeito produtivo.
- Pré-condições: script read-only allowlisted inspecionado, PowerShell portátil verificado disponível, .env V2 presente sem exposição de valores; trava serial interna. Reserva anterior ao HTTP em target/probe-coletas-20261002-01/reservation.json, com hash do script e STATES inicial e status Git. Única mudança preexistente: STATES.md; preservada.
- Limites: template 6908, 29/09–01/10, duas chamadas, página 1/per=1, timeout 30 s, resposta 10 MiB, sem redirects/retries/fallback manual. Credenciais locais, última definição não vazia. Sem SQL/Java/DDL/escrita produtiva.

| Camada | Esperado | Observado | Evidência |
| --- | --- | --- | --- |
| HTTP read-only | /info e página 1, depois teto | HTTP 200/200; JSON e shapes válidos; duas chamadas | target/probe-coletas-20261002-01/summary.json |
| Limite de entidade | até uma entidade, identificador escalar não nulo | uma linha física/uma entidade distinta; zero identificadores nulos; limite verificável | mesmo resumo sanitizado |
| Perfil | tipos/contagens sem valores | 31 campos, três nulos, updated_at string com offset | mesmo resumo sanitizado |
| Parada | CALL_BUDGET_REACHED/exit 1 | conforme esperado; página 2 request_executed=false; stderr vazio | execution.json/error.txt |
| Preservação | script inalterado, sem payload persistido | hash do script igual à reserva; somente resumo sanitizado | reservation.json |

Recuperação: consultas sem escrita não exigem rollback; não repetir rodada nem ampliar teto. Nenhum efeito desconhecido ou processo próprio residual após conclusão. Exit do wrapper 0 não substitui exit 1 da sonda, preservado no recibo. Aceites globais/checkboxes: nenhum. Paginação completa, estabilidade, paridade e ingestão não comprovadas. Resultado independente do gate P08/SQL e do recibo histórico ausente.

Alterações atuais: STATES, trilha, este checkpoint e ponteiro RETOMADA; nenhum código/contrato/migration. Evidências e decisões anteriores preservadas. Próximas ações: (1) selecionar unidade própria limitada de paginação se autorizada; (2) Banco reconciliar schema/Flyway/acesso/listeners antes de piloto SQL; (3) recuperar recibo histórico original se disponível, sem fabricar evidência. A rodada atual está encerrada no teto, sem novas chamadas.
Validação documental executada: Test-TrilhaPreparation.ps1 PASS/exit 0 (target/probe-coletas-20261002-01/trail-validation.txt), UTF-8 estrito dos quatro documentos PASS e git diff --check PASS. Gate histórico HANDOFF_PATH não repetido, lacuna preservada. Nenhum teste Java necessário/executado para esta unidade sem código alterado.
