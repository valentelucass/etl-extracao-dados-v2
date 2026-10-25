# Checkpoint 0549 — timeout de Fretes comprovado; chave aceita nos controles

23/10/2026. O usuário reafirmou que a evolução V2 deve usar **Data Export** e pediu continuar os testes, depois aprofundar a investigação de chave e demais causas. Continuação de [0548](0548-comparacao-v1-fretes-transporte-e-prova-real.md). GraphQL não foi utilizado como desvio da falha: chamadas GraphQL e SQL/TDS zero.

## Evidência atual da origem

Foram executadas quatro chamadas novas, em duas unidades finitas e distintas, com a mesma credencial existente. A primeira comparação tinha teto de três chamadas, mas encerrou na primeira por HTTP 500. Depois do novo pedido humano para aprofundar chave/contrato, uma segunda unidade própria de até três controles foi reservada antes dos efeitos. As duas chamadas não usadas da primeira unidade não foram reutilizadas.

| Horário Brasília | Consulta | Resultado observado |
| --- | --- | --- |
| 19:25:58 | Fretes 6389, DATA, GET com corpo JSON, página 1/per 100, data 15/10 | HTTP 500, JSON com `status=500` e `error=Internal Server Error` |
| 19:37:17 | Fretes 6389, INFO | HTTP 200; 114 campos e 16 filtros |
| 19:37:22 | Coletas 6908, DATA, GET com corpo JSON, página 1/per 1, data 15/10 | HTTP 200; uma linha/uma entidade, limite por identidade conferido |
| 19:37:28 | Fretes 6389, DATA, mesmo corpo/per 100 da primeira chamada, alterando somente a data para 22/10 | HTTP 422; resposta abaixo |

```json
{
  "errors": ["timeout"],
  "formatted_errors": ["Tempo limite excedido"]
}
```

Essa resposta comprova a **causa imediata do 422 desta consulta atual: a API informou esgotamento do tempo para executar o relatório**. Houve uma resposta HTTP completa antes do limite local de 30 segundos; não foi o cliente encerrando a conexão por seu timeout. O motivo interno da demora — consulta do relatório, volume, capacidade ou outra condição no fornecedor — não aparece na resposta. Não o inventar, nem reclassificar retrospectivamente o 422 de 21/10 cujo corpo não foi retido. O HTTP 500 da primeira data é outra observação; sua causa interna continua desconhecida.

Primeiro erro encerra cada unidade. Nenhuma chamada foi repetida automaticamente, nenhum redirect/fallback/retry ou troca de chave/origem ocorreu. Intervalo próprio mínimo de três segundos, timeout 30 segundos/conexão 10 segundos, resposta até 10 MiB, limite até 100 IDs e 1000 linhas físicas conforme o caso. Nenhum 401, 403 ou 429 observado. Isso não declara saldo ilimitado ou disponibilidade global. Os outros sete relatórios não foram retestados nesta unidade; seus HTTP 200 de 21/10 continuam históricos.

## Hipóteses verificadas

- **Chave de acesso:** igualdade em memória entre os arquivos V1/V2 e a credencial salva da app específica `ETL-EXTRACAO-DADOS-LOOP` no dump PM2, sem imprimir valor/digest de segredo. Mesmo endereço efetivo dos arquivos; alias prioritário de URL não estava presente. Não foi observado override de token por argumento Java nessa configuração salva. O ambiente vivo do processo não foi lido, pois V1 permanece pausado. A mesma chave foi efetivamente aceita no INFO de Fretes e nos dados de Coletas agora; não há evidência de rejeição geral da chave.
- **Configuração do artefato V1:** o JAR referenciado pela app salva existe e contém `config.properties` com método GET; `ConfigSource.class` contém suporte a `.env` e caminho explícito. A fonte dá precedência a system property, depois `.env`, depois ambiente. Não executar a aplicação para investigar essa precedência, nem declarar que o dump é observação viva.
- **Filtro:** o INFO atual declara `table=freights`, `field=service_at`, `type=date`, exatamente o caminho lógico enviado. A tabela técnica dos 16 filtros foi preservada, sem valores de domínio. Isso não comprova que todo filtro/ordenação será aceito em qualquer consulta.
- **Envio:** o JSON da referência V1 foi usado como forma da caracterização, com `search.freights.service_at`, datas sem horários, `page`, `per` e `order_by`. A consulta de controle de Coletas confirma funcionamento desse modo de envio com a mesma chave/cliente. Não é réplica byte a byte de uma requisição operacional capturada do V1: o per foi reduzido de 1000 para 100 e as janelas são declaradas neste teste.
- **Identidade dos registros de Fretes:** ainda não qualificada na origem nesta unidade, porque nenhuma página de dados de Fretes retornou com sucesso. Chave de acesso aceita e chave de identidade dos dados são provas diferentes.

## Por que o V1 pode continuar funcionando

A carga principal de Fretes do V1 é GraphQL; o 6389 é complemento, conforme 0548. O código `DataExportHttpExecutor.java:136` reconhece explicitamente 422 contendo `timeout`/`tempo limite`. `DataExportPaginator.java:355` aplica tentativas delimitadas por página com backoff e jitter; a configuração consultada tem limites próprios para página 1 e demais páginas. A partir de página posterior, o suporte de sonda pode experimentar per 10/50/100 e preservar resultado parcial com lacuna; isso não é prova de completude.

`FreteExtractor.java:480` captura falha do complemento e permite manter os dados principais já obtidos por GraphQL. Logs existentes têm tanto sucessos do 6389 quanto falhas desse complemento. Logo, um ciclo geral bem-sucedido não prova que todos os campos Data Export foram obtidos em todas as execuções. Não transferir a dependência GraphQL nem as tentativas adicionais do legado para o V2 automaticamente; esta evolução permanece Data Export e respeita seu próprio orçamento/contrato.

## Testes locais e qualificação

Acrescentadas três invocações ao `HttpDataExportGatewayTest`: Fretes/GET com corpo/per 100, respostas 422 com o JSON real de timeout, 429 sintético e 500 genérico. Verificam o request serializado, preservação do status/template, uma tentativa no perfil testado, ausência de troca de transporte mesmo com fallback compatível habilitado e ausência de sucesso/página vazia em erro. São testes locais com servidor simulado, sem ESL/GraphQL/SQL. Não alteram a política genérica de retry do produto.

Compilação estrita dos 673 testes passou; produção reutiliza as classes já qualificadas e não foi recompilada. Recorte atual de nove suítes: 56 casos distintos qualificados por composição, incluindo as três invocações novas; não somar rodadas nem declarar uma suíte integral do projeto.

Falhas auxiliares preservadas: primeiro lote teve 53 aprovações e três erros por catálogo documental omitido do snapshot; copiado o catálogo existente e repetidos somente os cinco casos dessa suíte, todos aprovados. Ao incorporar o corpo real do 422, os 21 casos do gateway passaram novamente. Filtro do formatter foi corrigido para expressão regular que visita efetivamente o arquivo; uma tentativa com glob foi recusada antes de compilação. Formatter dirigido atual e Checkstyle passaram. O relatório Checkstyle incremental contém o arquivo alterado, enquanto o check anterior varreu os 1422 Java; um verificador auxiliar que exigia 1422 entradas também no relatório incremental recusou o lote posterior. Os recibos falhos permanecem falhos; a reconciliação usa resultados nativos e hashes, sem repetir testes aprovados.

**Reconciliação e atualização AST concluídas.** Executor original: exit 0, sem timeout, 203354 ms, pico 1297739776 B, encerramento às 19:50:29 BRT. AST sem agrupamento/LLM: 44316 nós e 115957 arestas. Hashes das entradas e do JAR reconferidos após a atualização, sem drift. O arquivo de teste formatado preserva os tokens Java do fonte testado; SHA-256 publicado `936671D1A6C919ED18171F41E40AC8CDB77A271412D7053884112ABF33547A0F`. A atualização não reconstrói a análise semântica documental nem o HTML histórico.

Nenhuma alteração de Java de produção, POM, transporte operacional, credenciais ou schema foi adotada. O gateway genérico suporta GET com corpo, mas ADR 0001/0004 e a configuração operacional continuam fixando GET com query; as falhas observadas não justificam inverter esse contrato global. O JAR de 0547 permanece a referência de produção, sem implantação: `92714FBFAA4B5836D4B55F59DF7ED062934C72681A5FA701CCBCF6B8597D5C42`.

## Evidências e próximo ponto real

Base privada: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/teste-dataexport-fretes-20261023`.

- `reservation.json`, `http-ledger.jsonl`, `results.json`, `comparison-memory-job-receipt.json`: primeira chamada/HTTP 500 e interrupção.
- `key-precedence-proof.json`: comparação da chave e da configuração salva, somente flags públicas.
- `key-control/reservation.json`, `http-ledger.jsonl`, `results.json`, `control-memory-job-receipt.json`: três controles atuais, filtros técnicos e HTTP 422 timeout.
- Originais de erros sob DPAPI do usuário atual; versões públicas `error-response-sanitized.json` contêm somente mensagens genéricas conferidas. Sem token, ID ou payload de negócio. Não há recuperação retroativa de headers/request-id não capturados.
- `offline`, `offline-recovery`, `offline-final`, `offline-final02`, `qualification-reconciled`: compilações, relatórios de testes, estilo, falhas, recuperação e índice.
- `qualification-reconciled/result.json`: qualificação composta concluída, SHA-256 `F4EDBF667403822D779C5DAEDA729E096114A06A01C446C74B8D7F6FCA2005DD`. Receipt final `reconciled-memory-job-receipt.json`, SHA-256 `B5DE179E5F6DBF71A0009E9357EC768073A2E3921147DE555EBC367674CB30CA`.
- `support-evidence-sanitized.json`: endpoint, request técnico com token substituído por placeholder, horários dos quatro controles e resposta de timeout, sem nomes V1/V2 no pedido ao fornecedor. SHA-256 `07D94E44F1A33817A892FE99309D391995DB0B46335917EB2B739EEEBBE325D6`.

O próximo problema específico é fazer o relatório 6389 responder dentro do tempo da origem com uma geometria de extração válida e completa. Nenhuma nova chamada automática foi deixada ativa. Se for necessário acionar a ESL, encaminhar o request e os horários atuais com a mensagem **timeout**, pedindo diagnóstico da execução interna do relatório; não pedir um exemplo genérico nem atribuir o erro à chave ou à franquia sem prova. Nomes V1/V2 não são necessários para a comunicação ao fornecedor.

Extração completa, paginação terminal de Fretes, paridade, retenção e gates de cobertura/aceite continuam abertos. C78 rollback-only e MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED preservados. V1/WMS não reiniciados; SQL Server 16.0.1000.6, instalação, contas, permissões, identidades, credenciais, serviços/listeners/TLS global, schema/DDL/Flyway e produção não alterados.
