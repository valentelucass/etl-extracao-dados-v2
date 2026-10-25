# Checkpoint 0548 — comparação com V1: Fretes, transporte e prova real

23/10/2026. O usuário determinou consultar a documentação e as regras do sistema que já extrai normalmente, identificando o V1 em `C:/Users/suporte/Documents/projetos/script-etl-automacao`. Essa investigação é interna; a ESL não precisa conhecer os nomes V1/V2. Foram lidos documentação, código e logs existentes, sem iniciar processos do V1, chamadas ESL ou SQL/TDS.

## O que foi confirmado

- `states.md` e `docs/moderno/fluxos/fretes.md` do V1: os dados principais de Fretes vêm de GraphQL; o relatório Data Export 6389 complementa os campos de finalização/performance. O sucesso da carga principal, isoladamente, não prova o sucesso do complemento.
- `FreteExtractor.java:123` conecta o cliente GraphQL; a tentativa do complemento começa em `:457`. Em `:480`, a falha do complemento é registrada e retorna mapa vazio. Portanto, o V1 pode continuar gravando os dados principais mesmo quando o complemento falha.
- Há prova positiva do próprio complemento, além do sucesso da carga principal: `logs/aplicacao/runtime/extrator-esl.log:17071`, em 23/10 às 01:59:04, registra enriquecimento via 6389 concluído com 15 correspondências e 15 campos de performance oficial. A linha 146539, às 13:08:53, registra duas correspondências. São contagens do log real, não readback SQL independente.
- O mesmo log registra falha do complemento às 14:25:58, linha 163251. Os sucessos não permitem afirmar disponibilidade permanente. Nenhum texto de payload ou dado individual foi copiado para esta documentação.

## Diferenças entre a referência e o diagnóstico de 21/10

| Item | Código/configuração V1 consultados | Diagnóstico V2 de 21/10 |
| --- | --- | --- |
| Relatório | 6389 | 6389 |
| Filtro | `search.freights.service_at` | mesmo caminho lógico |
| Datas | somente datas, com fim inclusivo | uma tentativa com horários e outra somente datas |
| Envio GET inicial | JSON no corpo | parâmetros na URL, sem corpo |
| Quantidade solicitada por página | 1000 | 1 |
| Ordenação | `corporation_sequence_number asc` | mesma ordenação |

Fontes V1: `ClienteApiDataExport.java:85–86` seleciona o método configurado e inicia modo `corpo`; `:394` configura template 6389/per 1000/ordenação; `DataExportRequestBodyFactory.java` constrói `search`; `DataExportTimeWindowSupport.java` formata datas; `DataExportRequestFactory.java:58–64` envia o corpo JSON no GET. O arquivo público `src/main/resources/config.properties:91` configura GET. Eventual override de ambiente, os bytes do JAR em execução e uma captura da requisição real não foram inspecionados nesta unidade; a evidência de modo inicial vem do código/configuração consultados.

No V2, `HttpDataExportGateway.java:300` já implementa GET com corpo. Porém, `RuntimeConfigurationFactory.java:196` recusa qualquer transporte operacional diferente de `GET_WITH_QUERY`; não basta mudar uma propriedade para reproduzir a referência. Essa divergência local foi identificada, não corrigida ou qualificada nesta unidade.

**Não há causa comprovada do 422.** A diferença no envio é uma hipótese concreta a comparar, não uma conclusão de defeito da ESL. Há também contraprova histórica importante: [checkpoint 0284](0284-fretes-6389-readonly-real.md) registra, em 22/09, INFO e quatro páginas de 6389 com `GET_WITH_QUERY`/per 100, todos HTTP 200, 410 linhas/400 entidades. Assim, tampouco é correto declarar que 6389 sempre exige corpo ou que o formato em URL nunca funciona. A travessia antiga parou no teto, sem terminalidade; não é prova de extração completa nem disponibilidade atual.

## Correção da orientação ao usuário

Já existe uma referência de consulta no V1 e prova real de retorno do 6389. Por isso, não encaminhar como necessidade já demonstrada um pedido genérico para a ESL ensinar a consultar Fretes, nem apresentar a falta do número comercial de chamadas como causa comprovada ou impedimento de todo o trabalho local. A pergunta anterior que mencionava V1/V2 à ESL não é necessária para esta comparação interna.

O limite contratado continua desconhecido; não se presume ilimitado ou atingido. Antes de nova consulta, continuam aplicáveis o escopo autorizado, intervalo mínimo, orçamento delimitado, STOP 429 e ausência de sondas para descobrir saldo. Sucesso de outra janela/rota não renova automaticamente uma unidade física.

Próxima parcela interna: usar o contrato que funciona como referência para preparar e qualificar uma comparação delimitada, incluindo transporte, datas, paginação e request efetivo. Alteração operacional e novo teste remoto exigem seus controles próprios; não transformar a leitura em validação causal da API ou extração completa. Perguntar ao fornecedor somente sobre uma divergência específica que permanecer sem explicação depois dessa comparação.

## Evidência e efeitos

`C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/comparacao-v1-dataexport-20261023/comparison-proof-v2.json`, SHA-256 `41453779405D02C1B57EF656638152FC2681934DBFB5BE1CE482B82ED13CEFE5`: hashes das fontes consultadas e do log, posições/horários/contagens de três eventos selecionados. A primeira versão da prova foi preservada; sua revisão remove somente marcadores de palavra-chave do contexto completo da linha, que não classificam a causa de erro.

Código de produção, testes e POM não alterados; nenhum novo teste, build ou índice por esta unidade documental. O JAR qualificado de 0547 permanece 92714FBF...97D5C42. Escritas no V1, restart V1/WMS, ESL HTTP e SQL/TDS: zero. C78 rollback-only, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, gates de extração/paridade/retenção e proibições permanentes SQL Server/identidades/credenciais/produção preservados.
