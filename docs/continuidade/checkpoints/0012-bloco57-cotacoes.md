# Checkpoint 0012 — B57 A, Cotações ligada ao parser e mapper

Objetivo: executar A–D do prompt target/preparacao-bloco57/PROMPT-BLOCO-57.md,
adotado pelo usuário. A está TESTADO_NA_CAMADA; B/C/D continuam autorizadas.
Base: 1.434 arquivos inventariados/copied em target/bloco57-local/initial.
Limites: somente local, sem API/SQL/runtime, instalações, agenda, credenciais,
commit/push ou renovação de orçamento. Windows/SQL preservado.

39 casos sintéticos independentes com hash da decisão V2-027 atravessam o parser
Data Export real e o mapper. O RED reproduziu perda de precisão/escala numérica
no parser e data impossível aceita pelo formatter SMART de Cotações. Correções:
BigDecimal sem remoção de zeros de escala e formatter estrito uuuu com espaço.
O erro da expectativa de MissingNode foi corrigido apenas na fixture; sua versão
anterior permanece no target. Nenhum alias requester_name foi introduzido.

Maven offline Java 17, POM equivalente e saída target/bloco57-local/build:
test-A-red-01.log registrou 11 testes/uma falha; test-A-green-01.log passou 34
sem falhas/erros/skips, incluindo 39 casos no consumidor e regressão gateway.
Dois erros de invocação do formatador precederam a execução; logs preservados.
Guards adicionais de limites estão escritos, ainda pendentes de execução.
Nenhum resultado sintético comprova ESL. Zero aceite: 67/115, 48 pendentes.

Sucessão B57 registra snapshots/hash antes/depois; manifestos Q-FND/B53–B56/ESL
imutáveis. Recuperar somente deltas próprios após comparar hashes e edições
posteriores. Nenhum efeito externo desconhecido; build focado terminou.

Até três ações: (1) executar guards e integrar Localização com léxico numérico
original; (2) integrar somente Data Export Fretes; (3) pergunta documental nova,
pacote de fonte futura e verify/regressões/fechamento. Todas locais e autorizadas.
Anterior: 0011-documentacao-esl-postman.md; SHA-256 e883aef8c0aa7c81cd41c650071b5ea0225ad794c515463ae39de16bd315747a.
UTC: 2026-09-09T02:58:27.7470903Z.
