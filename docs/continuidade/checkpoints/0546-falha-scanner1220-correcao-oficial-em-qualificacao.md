# Checkpoint 0546 — falha real do scanner12.2.0; correção oficial em qualificação

23/10/2026. Continuação de [0545](0545-sucessor-nvd-arquivos-oficiais-em-execucao.md) na autorização atual de continuidade V2. O problema identificado pertence ao cache interno da ferramenta de auditoria, sem execução SQL Server/TDS ou alteração de versão/instalação do SQL Server.

## Falha real e encerramento do sucessor02

A carga por arquivos oficiais avançou após recuperação da trava própria. O Dependency-Check12.2.0 registrou erros ao processar `CVE-2026-6785` e `CVE-2026-6786`:referências com1585/1115caracteres ultrapassavam `reference.url VARCHAR(1000)` no H2 interno. Não era uma vulnerabilidade encontrada no código ETL, mas uma falha de ingestão da base usada para auditá-lo.

Root marcou cancelamento por integridade, preservando log/plano/cache parcial e a causa. O controlador02 encerrou a árvore do subprocesso próprio23860; receipt final02 exit1, sem timeout,726429ms, pico1171210240B. Esse recibo e o registro de término próprio ficam preservados. Não houve relatório ou aceite G05; ausência de outras mensagens não transforma a base parcial em completa.

A trava temporária restante da unidade02 foi preservada e removida somente após confirmação de JVM59092 ausente e correspondência do último write18:12:27 com a trava observada durante a própria unidade. Caminho absoluto explícito e SHA reconferidos; nenhum arquivo do DB foi removido. O cache parcial12.2.0 não deve ser promovido por mtime ou usado com update desabilitado.

## Causa e correção oficial conferidas

O erro observado coincide com a [issue oficial8466](https://github.com/dependency-check/DependencyCheck/issues/8466). A [PR8467](https://github.com/dependency-check/DependencyCheck/pull/8467) ampliou o campo de referência; o [changelog da12.2.2](https://raw.githubusercontent.com/dependency-check/DependencyCheck/v12.2.2/CHANGELOG.md) registra essa correção. Foi escolhido esse patch da mesma série para qualificação causal.

A versão12.2.2, cadeia, JAR/POM publicados, descriptor e ajuda foram obtidos/conferidos antes do novo check. Preparo protegido:exit0,32464ms,pico679227392B,sem timeout. SHA256:

```text
plugin JAR: 3af13387bb96b46c4a4323f42167fc71ed5fbd12e47c62df5d5b88f59856b3d9
plugin POM: 6d91a82b3fffee828b941f0b96e6af21381e68e36f74b5682abcd27be47e575c
descriptor: 8fb58b1d9cf5c8073ed2a18dacbb049f7a5333ad3ef499fadfe93ba2c78b7fa9
```

Checksums SHA1 publicados correspondentes e SHA256 locais preservados no resultado de preparo; não declarar assinatura PGP verificada. A primeira admissão do candidato foi recusada por concorrência protegida; observada vaga livre às18:29, a unidade foi então admitida, sem encerrar o lote de outro projeto.

## Candidato03 em execução

Uma configuração de auditoria derivada do POM foi gerada no projeto isolado03. Alterações limitadas e reversíveis:versão do scanner12.2.0→12.2.2 e cache novo em diretório próprio. O POM de referência foi preservado byte a byte e a reversão dessas duas substituições corresponde exatamente ao original. Recursos main foram copiados; as dependências do ETL não mudaram. Nenhuma alteração do POM canônico ou do JAR qualificado foi adotada por essa preparação.

Mesmo threshold0.0, `failOnError`, analisadores e exceções. `PolicyOnly`/`VerifyImplementation` passaram como preflight; são os mesmos33casos, sem soma de rodadas. O check real começou às18:29BRT, usando o mecanismo oficial NVD2.0 em cache limpo separado do parcial12.2.0. Uma vaga protegida,2048MiB, até2400s no scanner/43min no lote. Planejamento e hashes foram escritos antes da execução do scanner.

**Execução ativa, somente candidato.** É necessário verificar o término real, erros de ingestão, JSON/HTML quando existirem e a política. Um exit nativo isolado não autoriza ignorar erro de carga observado. Nenhuma adoção, relatório concluído ou aceite G05 decorre do início. O escopo padrão continua excluindo test scope/plugins e não equivale à auditoria de todos os componentes.

## Evidências e próximos critérios

- Unidade02: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/auditoria-dependencias-20261023-02-bulk`, com erros originais, `cancel-requested`, término próprio, receipt1 e recuperação da trava própria.
- Unidade03: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/auditoria-dependencias-20261023-03-dc1222`, com preparo12.2.2, POM de referência/candidato, cache próprio, plano e resultado corrente.

Qualificar a correção real antes de decidir a adoção da configuração no projeto. Achados, dados faltantes, erro de scanner, relatório parcial/inválido, score/severidade desconhecidos ou exceção inválida continuam bloqueando. Preservar falhas e não relabelar os resultados12.2.0. O novo JAR de0543 continua sendo preparo, sem bundle/dispatch/reserva física.

ESL HTTP, token ESL, SQL Server e TDS:zero. V1/WMS não reiniciados. Franquia/escopo/saldo ESL, Fretes6389/422, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, C78 rollback-only, cobertura/oráculos/paridade/retenção e todas as proibições SQL16.0.1000.6 permanecem.
