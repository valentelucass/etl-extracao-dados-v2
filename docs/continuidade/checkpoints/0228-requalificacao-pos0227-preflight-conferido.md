# 0228 — Requalificação local pós-0227: pré-flight conferido

Estado: PRONTO_PARA_EXECUTAR. 2026-09-22T01:35:05.814Z.
Anterior: docs/continuidade/checkpoints/0227-p11-resultados-e-succesao-conferidos.md,
SHA-256 7c87e0d4e7c455e6b2878a10cb6544b2c54cad1bbb6f324f89c812e63396dd14.
Manifesto anterior conferido: 20b9262adcdb8192e5231e57ccae943ffbf44a2a2472df1ea54b3081ac45cbe4.

Pedido efetivo: concluir pendências executáveis P10/P11/P12/P14/P15/P21 e
requalificar P07/P08 em macroblocos completos. Critérios canônicos em STATES
e no pedido; nenhuma redução de massa, assertions, identidades ou skips.
Autorização A/B local conferida em STATES e authority.json da rodada anterior;
continuação atual cobre somente localhost/ETL_SISTEMA_V2_SHADOW, Windows,
sintéticos e rollback. Sem DDL, commit de domínio, fontes, produção ou terceiros.

Nova ordem independente: target/requalificacao-pos0227-20260922-01/authority.json.
Teto de reservas 43200s, até duas suítes integrais (7200s cada), até duas
correções físicas após diagnóstico, execução serial; P08 depende de P07 PASS.
Vigência: 2026-09-23T01:33:02.959Z. Ledger anterior CLOSED, não reutilizado.
Pré-flight consumiu reserva300s: target exato, auditorias0/0/453,
246tabelas/1816objetos e contagens por tabela iguais ao readback0227.
Zero Java próprio, requisições ativas/bloqueadas, transações read/write e
sessões JDBC; flags de memória SQL0/0, sistema0. Host:5542588KiB livres.

Inventário3779 arquivos preservado em initial.json e before/. Snapshot isolado
pos0227-source-01;2079 arquivos src/database/POM sem drift contra a prova anterior.
Relatórios originais confirmam lock no readback pós-rollback em escala[4] e
query timeouts em ManifestGates. Diagnósticos4+4 aprovados não serão repetidos.
Pressão de memória transitória é observação, sem causa retroativa demonstrada.
Nenhum defeito de código demonstrado até aqui; nenhum runtime alterado.

Próximas ações:
1. Executar pos0227-p07-verify-01 e conferir resultados, cobertura e rollback.
2. Somente após PASS integral, pacote novo reproduzível/JAR/A/B/recusas completos.
3. Reutilizar investigação dos16 inputs externos e sincronizar sucessão/diff.

Recuperação: falha interrompe dependentes; preservar XML/log/attempt, reconciliar
processos próprios e SQL antes de corrigir/reservar. Sem aceite novo;39/45 e67/115.
Evidências: preflight-01/result.json, diagnostic-before.log, host-before.json,
runtime-drift.json na rodada privada. Resultado físico de P07 ainda não observado.
