# Checkpoint 0557 — Documentação, cURL e cliente Java concluem a paginação de Fretes

24/10/2026, após10:47BRT. Anterior: [0556](0556-fechamento-metadados-service-at-pacote-e-indice.md),
SHA256 `29DCE39701D35FD714755362D3D51F63560461965592E51C4400854E55584313`.
O usuário questionou a necessidade de pedir esclarecimentos à ESL e solicitou testar com
Postman/cURL e reler a documentação já fornecida. **Houve avanço real sem novo retorno da ESL.**
O objetivo de extração completa continua; a prova abaixo fecha componentes/paginação de uma janela,
não persistência, universo histórico, paridade independente ou cutover.

## Referência relida e caminho funcional

A coleção pública da documentação indicada pelo usuário foi recuperada do link oficial Postman;
o leitor web não renderizou o corpo, e o cliente local leu a coleção pública. Seção Data Export:
GET com JSON no corpo contendo `search`, `page`, `per`; intervalo mínimo2s no mesmoIP. A regra
de horas trata períodos históricos conforme a documentação; dia17/10 nesta rodada não recebeu
limite temporal inventado. Isso não é afirmação de franquia comercial ilimitada.

Foi testado esse GET com corpo, sem ordenação extra, dia17/10/2026. A unidade cURL reservou até8
calls/450s, consumiu7, intervalo3s, timeout30s/resposta10MiB, uma tentativa/semredirect/fallback:

- INFO6389 HTTP200,114campos/16filtros.
- per25: páginas1/2populadas,37linhas/37IDs; página3HTTP200vazia.
- per20: páginas1/2populadas,37linhas/37IDs; página3HTTP200vazia.
- Todos os HTTPs200. Mapas completos de valores e multiplicidades iguais entre as duas travessias;
  comparação somente em memória. Não foi apenas igualdade de contagem ou prefixo.
- Respostas cURL foram entregues ao parser/profiler/mapper Java atual emRAM, preservando precisão
  decimal. Os37registros foram aceitos pelo mapper em cada travessia, sem quarentena.

## Comparação controlada de formato e ordem

Três diagnósticos independentes, predeclarados, uma chamada por unidade, mesma janela/página1/per25:

| Transporte/ordem | Resultado |
|---|---|
| GETquery,semordem |200,25linhas/25IDs/114campos |
| GETcorpo,`id asc` |200,25linhas/25IDs/114campos |
| GETquery,`id asc` |422, corpo exato `errors=[timeout]`, `formatted_errors=[Tempo limite excedido]` |

O422 atual foi capturado com headers técnicos e original cifrado DPAPI, sem token/dados em logs.
Request-id técnico `635dde78-2454-8ba4-6ac0-99fe1a3f264b`; início10:32:37,287BRT,
término10:32:40,133BRT. `x-runtime=2.261859`, tempo cURL2.760274s; isso não determina sozinho
um limite interno ESL. Cada unidade encerrou após sua chamada; não houve retry, troca de credencial,
fallback automático, sonda para descobrir quota ou429. A matriz externa retornou0 porque executou
o diagnóstico planejado, mas o caso QUERY_ID continua **STOPPED/422**, não aprovado.

Esta rodada sustenta um caminho funcional pelo corpo e uma falha com query/ID. O histórico contém
sucessos com query/ID: **não declarar causa interna exclusiva ou garantia universal do corpo**.

## Prova independente no cliente HTTP Java do V2

Depois do cURL, foi congelado o release observado de114campos/16filtros/115paths, fingerprint
`ad072100143c765705cd7b01e581f19ce3e5bb9a38d181f9cfeae4bcbd141b35`.
Foi então aberta outra unidade própria de até7calls/300s, com o factory/gateway/parser/profiler,
validator e mapper reais Java, usando GET_WITH_BODY/`id asc` e esse contrato independente:

- INFO validado contra o release congelado antes dos dados.
- per25 e per20:37IDs/37linhas, terminal vazio página3, ordem porID/progressão/limites conferidos.
- Igualdade dos mapas completos de valores/multiplicidade entre passagens.
-37mapperVALID em cada passagem, zero quarentena. Sete chamadas consumidas; semSQL/GraphQL/Main.
- Job exit0,33012ms,pico245415936B/limite1GiB,sem timeout, término10:39:16BRT.

Não foi usado o catálogo sintético como se fosse real. O JAR usado na prova dos componentes é o
qualificado0556, SHA256 `42251A42CEC7938EAA529C61E3045E31215334131904C5C64C1232814130D398`.
A factory HTTP de baixo nível já suportava BODY; a configuração geral ainda o recusava.

## Contrato versionado e mudança de configuração em qualificação

O release e manifesto observados foram publicados, separados do catálogo ativo, em
`docs/catalogos/contratos-primeira-onda/observados/fretes-6389-20261024/`.
`release.json` SHA256 `19B4D5D9E093A1BA1C88978EDD46EF5755F9D6F35772B3EFA8213D3F89AD6ADB`.
Há15paths sempreNULL nesta janela; seu tipo não nulo continua UNOBSERVED. Nenhum tipo foi inferido
pelo nome, nem a metadata UNDECLARED convertida artificialmente em declaração do fornecedor.

`RuntimeConfigurationFactory` foi ajustada para aceitar escolha explícita GET_WITH_BODY, além
de QUERY. POST_JSON segue recusado, nenhuma troca automática/fallback/default/perfil privado
foi introduzida. Dois testes novos de arquivo/ambiente falharam causalmente contra o código
anterior. Na correção,88testes selecionados/strictcompile749+673 passaram; pacote/style final
ainda em execução em `adocao-get-body-20261024-01`. Observar recibo antes de afirmar pacote
qualificado. ADR0069 registra capacidade de configuração versus adoção operacional por contrato.

## Evidências e limites

Base externa: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/investigacao-curl-esl-20261024-01`.

| Artefato | SHA256 |
|---|---|
| `results.json` — duas passagens cURL | `9C8ECC96F242847444F15D3282C53605542207F6CC16B39E95FD970DC9FDC6C8` |
| `page-25-3-analysis.json` | `3F19B5932B95CA6DDC29DFCDAEFE48065934F8B29D7752D6A810B64AD6CFE0F4` |
| `page-20-3-analysis.json` | `CDCF47021C046805EFB704B83C218866E7850D73F2DA3B122F1F61C2F77820AF` |
| `controlled-matrix/results.json` | `460D89AD357AD2B9F871C4C516E86490F388EB36CFD27796D7EFD578B9F5251B` |
| `java-body-live/results.json` | `C2BBDC8A7272BFBD2D9280277B569098D4C3ADA6C67F72A44D61D7369E7E997A` |
| `java-body-live-v2-memory-job-receipt.json` | `23C5EBE8ED5086CC3FC52B1F005F74245BE4AFEA41E12EA510D40ACAAD061CFC` |

Total17tentativas de fonte novas:7cURLdocumentado+3comparações+7Java. O422 foi interrompido e
preservado em sua unidade; o teste Java posterior era outra prova, com contrato congelado e caminho
BODY já aprovado, não retry da query falha. Nenhum registro, ID ou digest de negócio foi persistido.
O helper inicial falhou offline por fixture financeira com escala acima do domínio; mantido o
teste de precisão em outro campo e total com escala válida. O wrapper Java inicial falhou offline
por PSScriptRoot vazio em função extraída; correção explícita qualificada antes de fonte. Recibos
falhos, scripts iniciais e logs preservados. Essas falhas não são falhas da API nem PASS apagado.

Este resultado elimina a suposição de que era necessário aguardar a ESL para continuar essa
leitura. Ainda faltam adoção/bindings da rota real, captura/persistência, oráculo/paridade e demais
entidades/janelas. O campo sempreNULL não comprova seu tipo futuro. Página terminal não prova
snapshot global nem todo o histórico. Não mudar o aceite global ou expurgar ausência.

SQL/TDS/GraphQL/deploy/DDL/Flyway, versãoSQL16.0.1000.6, contas/credenciais/permissões/identidades,
serviços/listeners/trustglobal/V1/WMS/produção:zeroalterações. C78rollback-only e MainHTTP histórico
UNKNOWN/MISSING_NOT_RECONSTRUCTED permanecem. Nenhuma mensagem à ESL/gestor foi enviada.

1. Concluir pacote/style/AST já planejados da aceitação explícita BODY, com hashes atuais.
2. Selecionar/admitir o release real e o transporte no caminho de execução por ocorrência, sem
   transferir a autoridade diagnóstica ao Main/SQL ou aos perfis6908 existentes.
3. Provar persistência/paridade e ampliar entidades/janelas por unidades próprias, sem exigir
   resposta externa genérica para o caminho de leitura que agora tem prova funcional.
