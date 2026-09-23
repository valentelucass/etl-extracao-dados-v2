# B60 — SQL versionado, autoridade e controladores locais

Pedido adotado: `target/bloco60-local/PROMPT-ADOTADO.txt`, seções A–F.
SQL físico, inclusive preflight, continua proibido até aprovação única da seção4.
Não houve consulta SQL, instalação administrada, leitura de credencial Windows,
fonte real, commit/push ou consumo de orçamento físico nesta unidade.

V024 e ADR0041 acrescentam vínculos de protocolo por origem/execução sem alterar
source_kind histórico, identidade, fingerprints ou algoritmo current/history.
Stage/apply de Usuários recebem fences; recovery incorpora GraphQL e a ordem
temporal real de V007 (aplicação tipada posterior à publicação genérica).
Validador estático:14 contraprovas e preservação do ramo DE/algoritmo V007.
SQL ainda não compilado ou executado em servidor. Validações056/057 e pacote
`database/proposals/bloco60-local` preparados, revisão operacional em andamento.

Java:50 testes de telemetria (`graphql-directed-01`),55 de autoridade/controlador
(`authority-directed-02`) e51 de budget/controlador (`budget-directed-01`), todos
sem falhas/erros/skips. Não somar essas contagens como suíte distinta: há overlap.
RED de telemetria e falha de checkstyle (`authority-directed-01`) preservados.
Instrumentação SQL adicional ativa somente no recurso administrado bloco60:
256 aberturas/4 conexões simultâneas/512 submissões por JVM; tentativas falhas
consomem saldo, sem payload no diagnóstico. Probes permanecem no test-classpath.

Servidor temporário B60:22 chamadas offline nas seis verticais,9 checks do
controller/ledger passaram, sem SQL/identidade Windows física. Ledger próprio,
janela proposta09–16/09 UTC e escrow de parada; nenhuma renovação de B53–B59.
Matriz física inicial tem71 invocações congeladas; ainda revisar negativos,
montar artefatos finais, executor, reconciliação e prova de concorrência.

Integridade inicial B59 e1572 cópias continuam em `target/bloco60-local/initial`.
67/115,48 pendentes,191 rotas,zero AGORA; nenhum aceite agregado/checkbox novo.

Próximas ações:
1. Completar/revisar executor físico, artefatos/manifest/matriz e limites, sem SQL.
2. Verify offline Java17/512MiB, gates afetados, scanner/autotestes e contraprovas.
3. Sucessão exata, diff/recuperação/recibo revisável; então aprovação física única.
