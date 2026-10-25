# Checkpoint 0544 — ferramenta de auditoria preparada; scanner real em execução

23/10/2026. Continuação da autorização atual para avançar V2 enquanto o usuário está ausente, após pausa humana do WMS. Anterior:[0543](0543-indice-recuperado-executavel-conferido.md). Nova unidade técnica de auditoria de dependências, separada de captura ESL, SQL Server, aceite nominal G05 e release.

## Falta material da ferramenta resolvida

Dependency-Check12.2.0 e sua cadeia foram obtidos do Maven Central por uma configuração de cliente própria, sem proxy autenticado, alteração de settings global ou nova identidade. Os dois checksums SHA1 publicados correspondem aos arquivos recebidos; SHA256 locais também registrados. Essa verificação não é uma declaração de assinatura PGP verificada.

| Artefato | SHA256 local |
|---|---|
| dependency-check-maven-12.2.0.jar |a42e62a91802c4e6db9b24c5562f80c2946d84dc4f8a8382e9a9ff0a29094e13|
| dependency-check-maven-12.2.0.pom |a5e8be4196ab2be97ebef6eda2875b00189efed138794d810fcc9de6084ae250|
| descriptor META-INF/maven/plugin.xml |91172b63c64201e9d434d058063e6fc297402adfce764c0ff51a2bb265f1da22|

Descriptor conferido, parâmetros obrigatórios presentes e ajuda do goal `check` executada com exit0. Preparo protegido concluído em58787ms, pico680562688B, sem timeout. Nenhum scanner/feed foi chamado durante **o preparo**; isso mudou somente na unidade técnica seguinte descrita abaixo. POM e política não foram alterados.

Fonte de distribuição:[artefato oficial no Maven Central](https://repo.maven.apache.org/maven2/org/owasp/dependency-check-maven/12.2.0/dependency-check-maven-12.2.0.pom). A [documentação oficial](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/configuration.html) foi consultada e confrontada com o descriptor da versão efetivamente obtida, sem presumir que defaults da documentação atual sejam os da versão fixada.

## Unidade técnica nova, delimitada antes dos efeitos

Root estabeleceu `scan-plan.json` antes de iniciar o scanner, com base na autorização atual de continuidade do usuário. Não há reutilização de reserva ESL, prova HTTP histórica ou autoridade de outro perfil. A política local por si só não concedia rede; esta é uma unidade pública técnica nova, que não concede aceite nominal G05 nem cutover.

- JAR vinculado: `10D275EE4F0AB342A04FC5FD924E4735F076D35E3230118FE063CBEA1A2A9BA3`, qualificado pela composição0542/0543.
- POM: `5cb67c506b11ff1f85ebb965745ade87c9a36f860249141f1df03aa7d0e1e385`.
- Policy: `83c8698663f33b0e79ac987d72c7853fb59b12af42f31b07d1809377a454e056`.
- Uma invocação do goal real12.2.0 `check`, perfil `security-audit` original, threshold0.0, `failOnError=true`, sem exceções ou suppression locais novas e sem desabilitar analisadores.
- Escopo efetivo do perfil:dependências padrão do Maven; test scope é ignorado e plugins não são escaneados pelos defaults conferidos. Não declarar cobertura de todos os componentes build/test/native ou de todo o projeto por esse resultado.
- Execução no snapshot próprio, `.env` não lido; chave NVD somente de `NVD_API_KEY` já existente no ambiente, se presente. Nenhuma chave aparece em argumentos, plano ou saída publicada.
- Um lote protegido por vez,2048MiB por árvore/processo, scanner até2100s e lote até37min. NVD com intervalo8000ms, retry máximo1 e timeouts conexão/leitura30000ms; reserva mínima de8GiB livres em disco. Não é alegado um contador físico de HTTP global, nem saldo de franquia ESL.
- Cache próprio do scanner no caminho configurado pelo POM, `C:/Users/suporte/.m2/dependency-check-data`; relatórios no `target/dependency-check` do snapshot. O cache H2 da ferramenta não é o SQL Server do ETL.

## Resultado observado até este registro

`PolicyOnly` e `VerifyImplementation` passaram; são duas verificações do mesmo conjunto33casos, sem soma para66 e sem alegar auditoria real por fixtures. O scanner começou em23/10 às17:54BRT e informou413810registros na atualização inicial da NVD. Essa mensagem não comprova que todos foram recebidos, que o DB está completo/fresco ou que existe relatório.

**Execução em andamento.** Não iniciar outro scanner ou batch pesado enquanto a unidade estiver ativa. Acompanhar `scan-result.json`/log/recibo corrente; não promover um início ou cache parcial a sucesso. Após término, preservar exit code real, hashes/relatórios quando existentes e executar o validator do JSON. Erro, ausência/parcialidade/formato inesperado, severidade/score desconhecido ou vulnerabilidade sem exceção continuam bloqueando. Nenhum aceite G05 foi emitido.

## Evidências e limites

Diretório: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/auditoria-dependencias-20261023-01`.

`preparation-result.json`, logs de resolução/ajuda, descriptor e receipt do preparo preservam a primeira etapa. `scan-plan.json`, `run-scan.py`, `scan-result.json`, logs de policy/implementation/scanner e o recibo ao término preservam a execução real. O supervisor próprio aceita um marcador `cancel-requested` para interrupção explícita e fecha somente a árvore criada; não tocar processos de outros projetos.

ESL HTTP, token ESL, SQL Server e TDS:zero. A rede pública de dependências/feeds é um escopo diferente; não declarar HTTP total0. V1/WMS não foram reiniciados. Limite/escopo/saldo da franquia ESL, Fretes6389/422, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, C78 rollback-only, cobertura/oráculos/paridade/retenção e proibições SQL16.0.1000.6 permanecem. Resultados finais desta unidade devem ser registrados por sucessão verdadeira.
