# Checkpoint 0543 — índice recuperado e executável atual conferido

23/10/2026. O usuário informou “liberei o WMS ta parado, pode copntinuar enquanto n volto”. Continuação de [0542](0542-qualificacao-piso-esl-apos-liberacao-memoria.md). A vaga protegida ficou disponível; a leitura inicial registrou8291MiB livres/71% comprometidos. V1/WMS não foram reiniciados ou alterados por esta unidade.

## Índice de código concluído

`graphify update . --no-cluster` concluiu às17:40:30BRT, com44259nós e115868arestas. Nenhum LLM ou atualização semântica foi usado. O incremento documental deste checkpoint é posterior ao corte do índice; HTML/relatórios semânticos históricos não são promovidos a atuais. O próprio Graphify preservou backup dos cinco arquivos curados antes da publicação.

Recuperação vinculada ao SHA256 do `result.json` anterior e aos mesmos inputs qualificados; drift0. Recibo protegido:exit0,216403ms, pico1320787968B, limite2147483648B, sem timeout. A ocorrência124 e a recusa por concorrência de0542 permanecem preservadas; não foram reclassificadas como sucesso nem repetidos os70 testes anteriores.

## Pacote e integridade

Maven offline gerou um executável e suas oito bibliotecas de runtime no snapshot isolado. Os sete casos `QualificationPackageIntegrityIT` passaram:zero falhas, erros ou skips. São sete casos distintos dos70 selecionados de0542; não são suíte integral, relatório novo de cobertura ou prova física.

A conferência adicional do pacote terminou inicialmente em erro após esses testes. O comparador Python contou1318classes, enquanto o JAR e uma conferência PowerShell independente continham1319. Causa demonstrada:um arquivo com caminho Windows de267caracteres era ignorado por `Path.is_file()` na forma comum. O arquivo existia, media1073bytes e também estava presente no JAR.

Foi corrigida somente a leitura do verificador externo, usando o prefixo de caminho estendido do Windows. Controle causal nos mesmos arquivos:forma comum `is_file=false`; forma estendida `is_file=true`; comprimento267. Não houve alteração de registro/configuração global do Windows, fonte, classe ou JAR para fazer a conferência passar.

Conferência corrigida:

- 1319 classes compiladas e 1319 classes no JAR, mesmos nomes;
- 1364 membros main comparados: 1319 classes + 45 recursos, bytes iguais;
- zero membro ausente, classe inesperada, divergência de bytes ou drift dos inputs canônicos;
- JAR estável durante a leitura e SHA256 igual ao da conferência anterior que falhou;
- oito bibliotecas reais inventariadas com seus hashes, sem tratá-las como os cinco componentes da consulta OSV histórica.

```text
JAR SHA256: 10D275EE4F0AB342A04FC5FD924E4735F076D35E3230118FE063CBEA1A2A9BA3
```

Arquivo preparado:

`C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/qualificacao-intervalo-esl-20261023-01/snapshot/target/etl-dataexport-v2.jar`

Aceite local **composto**:compilação/70 testes/estilo de0542, recuperação AST0, sete testes de pacote0 e comparação corrigida0. `package-result.json` e `package-verification.json` continuam registrando as falhas originais; `package-verification-longpaths.json` registra o sucessor qualificado e o hash do resultado anterior. Não declarar que o primeiro lote inteiro passou.

## Evidências preservadas

Diretório:`C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/qualificacao-intervalo-esl-20261023-01`.

- `index-recovery.json`, log e `index-memory-job-receipt.json`: índice concluído, sem drift.
- `package.log`, `package-it.log`, XMLs Failsafe e `package-memory-job-receipt.json`: geração/testes efetivos e recusa posterior do comparador.
- `package-verification.json` e seu recibo: contraprova 1318/1319, sem divergência dos demais bytes.
- `package-verification-longpaths.json`, verificador correspondente e recibo: controle de 267 caracteres, 1364 membros iguais e JAR inalterado.

## Próxima frente elegível e limites

Conferida novamente a falta local do Dependency-Check12.2.0 e do DB/feed. Uma HEAD pública ao POM exato no Maven Central retornou200, confirmando disponibilidade do artefato; isso não é execução do scanner nem verificação de vulnerabilidades. Preparada unidade separada para resolver a cadeia fixada, conferir checksums publicados/descriptor e abrir somente a ajuda do plugin. Não alterar POM, threshold0.0, failOnError, exceções ou a policy. Preparo iniciado em `auditoria-dependencias-20261023-01`; resultado final será registrado por sucessão verdadeira. Nenhum feed NVD, scanner ou aceite G05 decorre deste checkpoint.

ESL HTTP, SQL e materialização de credenciais:zero nesta unidade. Consulta Maven pública não representa chamada ESL. O novo JAR não foi implantado em bundle operacional nem herda dispatch/reserva dos deployments históricos. Permanecem franquia/escopo contratual, Fretes6389/422, requisitos atuais de caller/alvo/proteção/reserva, STOP429, C78 rollback-only, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, cobertura/G05 e os oráculos/paridade/retenção. SQL16.0.1000.6 e todas as proibições permanentes preservados. Nenhuma extração completa ou cutover foi aceito.
