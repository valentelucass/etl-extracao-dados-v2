# 0422 — SAMPLE testado na camada focada; CI integrado pendente

Data: 02/10/2026. Anterior: [0421](0421-etl-amostra-stop-http429-e-qualificacao-local.md), SHA-256 `9ABA82C4C1AB7D90FB84ADCAD92493573939F48A4B607D4D4C82C8511CFF175B`.

## Objetivo, autoridade e estado

Pedido efetivo: tentar uma extração completa com poucos dados usando os agentes Maestri, com curl para diagnóstico se necessário. A unidade verifica uma página real no fluxo Java/mapper/staging/readback/rollback, não completude do dia. Estado: **TESTADO_NA_CAMADA_OFFLINE**, build integrado pendente; **BLOQUEADO_POR_INPUT** apenas para a prova física/fonte real.

Escopo e owners em `target/pilot-etl-20261002/supervisor-scope.json`: Coletas6908 provisionado e `localhost/ETL_SISTEMA_V2_SHADOW`; Banco único executor SQL, Runtime bootstrap/testes, Fontes adaptadores, Regras domínio, Codex documentos compartilhados. Nenhuma produção, DDL/Flyway, login/grant/serviço/TLS ou credencial alterada. Mudanças anteriores preservadas.

Teto original10 HTTP; Fontes2 reservadas e curl1 reservada, duas tentativas efetivas. Saldo teórico7 congelado após curl HTTP429. Nenhuma nova chamada externa/retry/fallback nem escrita SQL. Código SAMPLE agora limita sua invocação a duas tentativas, INFO+pagina1; não concede nova vigência/reserva.

## Implementação e revisão

CLI `--sample` com nove argumentos, três opt-ins e flags JVM sem retry interno; preflight Banco fresco≤5min vincula arquivos e binding. Handler Runtime separado preserva enforcement contratual; extração captura identidades/multiplicidade antes do mapper e usa microbatches≤100. Uma página populada≤5raízes/1000linhas, sem página vazia fictícia, permit/promoção/watermark/sweep. Readback Banco observa binding real, página auditada, conjuntos bidirecionais e multiplicidade no staging da mesma sessão/transação. Recibo somente após `close`/rollback; não prova paridade completa de valores, filhos ou janela. ADR0056/COL-SAMPLE-01 registra origem, exemplo, contratos, testes, responsáveis e recuperação.

Codex conferiu sete hashes Runtime `runtime/owned-freeze.json` e três hashes Banco `banco/freeze.json`; os três Banco também são idênticos ao snapshot focado final. Aplicou seis entradas UNIT_INCLUDED no catálogo CI, preservando demais linhas/ordem/classificações, sem mudar pacotes/limiares. Evento Maestri enviado ao Runtime para snapshot completo e `clean verify` único offline. Seu build01 parcial não equivale a gate integral.

## Evidência executada

Todos os caminhos privados abaixo partem de `target/pilot-etl-20261002/`.

| Camada | Resultado | Evidência e limite |
| --- | --- | --- |
| Runtime focado | 44/0/0/0 PASS, incluindo 12 SAMPLE | `runtime/test-07.log`; Enforcer/Spotless/Checkstyle PASS; HTTP/SQL falsos. Snapshot precede redução cap8→2, bytes finais aguardam gate completo. |
| Banco focado | 59/0/0/0 PASS, incluindo 21 trial | `banco/offline-test-06.log`/exit0; três hashes idênticos ao freeze/root/snapshot. Zero SQL real nesses testes. |
| Fontes diagnóstico | 17 checks PASS, 195 classes reais compiladas | `fontes/freeze-receipt.json`; zero chamadas adicionais, original preservado. Causa Java original irrecuperável; 429 posterior não é sua atribuição retroativa. |
| Regras | 71/0/0/0 PASS | `regras/handoff.md`; snapshot anterior ao SAMPLE, sem defeito causal de domínio nem patch Java. |
| SQL diagnóstico | READ_COMPLETE_PREFLIGHT_INELIGIBLE | `banco/read-05-explicit-loopback/preflight-receipt.json`; alvo/sa/peers loopback comprovados, zero SQLwrite, transação final0. Não prova gates JDBC Windows/listeners/TLS/objetos. |
| Revisão Codex | PASS focado, integrado pendente | `supervisor-integration-review.json`, freezes e `supervisor-ci-scope-before.txt`. |
| Scanner auxiliar | PASS, zero achados | `supervisor-secret-scan.txt`, 4209 candidatos/4208 textos/1binário verificado; não substitui Gitleaks/histórico. |
| Trilha/UTF-8/diff | PASS | `supervisor-trilha.txt`; cinco documentos compartilhados UTF-8 estrito; diff sem erro. Continuidade HANDOFF_PATH histórica permanece aberta. |
| Graphify AST | PASS/exit0 | `supervisor-graph-update.txt`; 38947 nós/92581 arestas/2163 comunidades, sem API/LLM. |

FAILs anteriores de compilação, formatação e fixtures preservados. SQL_FAILURE→SOURCE_DQ pertence à matriz existente; correção do review está em `supervisor-review-correction.json`, sem alterar classificador. Fixture de data inválida era fallback permitido pelo mapper; teste negativo passou a usar tipo de status incompatível com contrato, sem patch domínio. CLI filtra somente os dois opt-ins shadow previamente validados antes de carregar configuração; demais políticas de segredo preservadas.

Nenhuma carga real foi feita pelo ETL/JDBC, nenhum aceite P08/G01/cutover/checkbox ou contador histórico alterado. Nenhum processo próprio Codex permanece ativo após Graphify/scanner encerrados; Runtime recebeu unidade offline de build completo por evento, sem polling pelo Supervisor.

## Próximas ações — até três

1. Runtime executa `clean verify` offline sobre snapshot completo de bytes congelados e catálogo CI aplicado; entrega XML Surefire/Failsafe, quality gates e fingerprints. Codex confere root/readback e registra o resultado, preservando qualquer FAIL; não repetir gate já equivalente.
2. Owner da origem demonstra mudança concreta no bloqueio/quota429 antes de qualquer nova chamada. Saldo/teto não renovados; não sondar para descobrir se liberou.
3. Banco qualifica sessão Windows local, listeners, TLS JDBC e contrato físico dos objetos usados com autoridade/gates/reserva próprios. Acesso sa read-only não libera trial. Só então avaliar prova real pequena de staging/readback/rollback com PRE/POST independentes e impacto IDENTITY explícito.

Condição de conclusão do objetivo: página real processada pela composição ETL, staging observado, comparação e rollback comprovados na camada física, dentro dos limites. Código/testes offline ou recibo de fonte isolada não satisfazem esse critério. `CONTEXTO_GLOBAL.md` continua ausente.
