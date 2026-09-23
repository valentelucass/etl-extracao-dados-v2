# Checkpoint0064 — ligação temporal local implementada; validação em curso

10/09/2026. Predecessor0063-coletas-prova-temporal.md, SHA-256
8076baaf0f0e07b11d0483685a1ade0741e8fa3776b223a265a7f55cd6e17d0e.
Estado: IMPLEMENTADO_NAO_QUALIFICADO. Nenhum aceite novo.

Pedido “pode aplicar nesse chat mesmo seguindo states.md”: executar a direção
local do checkpoint0063 e suas regressões. Sem outra chamada de fonte, leitura
de .env/credenciais, SQL físico, DDL/UAC, campanha B60/B62 ou ativação.
O orçamento anterior permanece encerrado5/5; não há efeito desconhecido.

Inventário2400 arquivos e cópias anteriores preservados em
target/coletas-temporal-link-20260910/inventory-before.json e before/.
Pré-condição conferida: validators ColetasTemporalProof (7guardas) e
Bloco63TemporalLocal (24guardas), ambos com evidência privada, passaram.

Código novo: seleção temporal versionada e ledger, contrato estrutural local,
mapper, observação/binding tipados, captura paginada síncrona e validação de par.
ADR0045 registra COL-TIME-05/06/07. Sem cruzamento em massa na JVM; complemento
separado do payload6908 e do JDBC antigo; promoção GraphQL bloqueada.
Bindings reais, transições representativas e consumidor SQL seguem não provados.

Primeira execução dirigida parou no Checkstyle (linha sintética com144 caracteres).
Log directed-01 preservado; corrigidos tamanho e uso das APIs reais do guard.
Segunda execução dirigida em curso, sem PASS presumido:
target/coletas-temporal-link-20260910/logs/directed-02.log; controlador
Invoke-Offline.ps1, Java17/Maven3.9.14, offline, heap512MiB, build isolado.
O próximo checkpoint deve registrar resultados observados, não este plano.

Próximas ações:
1. Concluir regressões dirigidas e corrigir falhas demonstradas.
2. Executar verify completo offline e vincular hashes dos fontes testados.
3. Sincronizar STATES/trilha/RETOMADA, sucessão de manifestos, diff e relatório.
