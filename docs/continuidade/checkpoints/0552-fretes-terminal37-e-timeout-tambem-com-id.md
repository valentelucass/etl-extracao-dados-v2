# Checkpoint 0552 — Fretes: 37 no terminal; timeout também com ID

24/10/2026,00:02 BRT. Anterior:0551. Pedido novo do usuário: “pronto, agora so ta vc pode continuar”, revogando a espera de recursos WMS. Recursos observados7132MiB/74%; nenhum WMS/V1 interrompido.

## Unidade real encerrada

Nova unidade finita, reservada antes dos efeitos:INFO6389 + duas travessias do dia17/10 com `id asc`, GET_WITH_QUERY, per25 e per50. Teto12HTTP/450s, intervalo3s, request30s/conexão10s,10MiB,1000linhas por resposta,200IDs/3000linhas por passagem; STOP no primeiro erro/429/limite inválido, sem retry/redirect/fallback/GraphQL/SQL. Autoridade direta da nova continuação; nenhuma unidade consumida foi reaberta.

Seis HTTP efetivos, cinco200/um422, nenhum429:

| Passagem | Página | Resultado |
| --- | --- | --- |
| INFO | — | 200,114campos/16filtros |
| per25 | 1 | 200 |
| per25 | 2 | 200 |
| per25 | 3 | 200,vazia terminal |
| per50 | 1 | 200,37linhas/37IDs |
| per50 | 2 | 422,timeout |

A primeira passagem concluiu **37linhas/37IDs**,114campos, duas páginas não vazias e terminal na terceira. ID integral, data de serviço, ordem crescente e ausência de sobreposição conferidos em memória. Na segunda passagem os mesmos totais foram observados na primeira página, mas a consulta seguinte falhou; **comparação entre conjuntos e valores das duas passagens não foi concluída**. Igualdade de contagens não prova igualdade de IDs/dados. Não repetir automaticamente o erro nem concluir que o terminal possa ser omitido porque a página foi menor que per.

Resposta genérica exata retida originalmente sob DPAPI:

```json
{"errors":["timeout"],"formatted_errors":["Tempo limite excedido"]}
```

Isso corrige qualquer interpretação absoluta do0551: **ID asc também pode sofrer timeout**. A ordenação por minuta continua hipótese de custo no contraste anterior, mas não é causa única demonstrada. Ainda não conhecemos o limite interno, plano/índice ou causa da demora na ESL. Uma travessia terminal empírica desta janela não prova snapshot, cobertura independente, histórico completo, persistência ou paridade.

## Evidência e preservação

Base externa:`C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/fretes-terminal-20261023-01`. O nome da pasta é rótulo de preparo; timestamps reais são24/10. `reservation.json`, `http-ledger.jsonl`, `results.json`, `error-response-sanitized.json`, originalDPAPI e receipt protegido conservam a execução. Final Job exit1 porHTTP422,35807ms,pico97509376B,sem timeout do Job,encerrado00:02:03BRT. Seis reservas/seis respostas; saldo não reutilizado. Nenhum valor,ID ou hash de negócio persistido. Primeiro selfcheck falhou antes da rede por recorte de helper em string literal; substituído por posição do IF real na árvore sintática, falha/fonte/receipt preservados. Selfcheck corrigido passou antes do HTTP.

Produto ainda sem nova alteração de produção neste checkpoint. Dois testes existentes foram ajustados para exigir versão/ordem de Fretes ID asc e rejeitar a ordem antiga antes da fonte; **ainda não executados/qualificados**, preparar RED antes de mudar produção. O contrato novo conservará classificação transitória, gates de completude, transporte e todos os demais templates. Não promover a baseline sintética a release real114campos por esse delta de ordem. Nenhum Maven/JAR/AST novo ainda; JAR0547 continua referência anterior.

HTTPnão2xx não será convertido em página vazia, sucesso ou avanço de checkpoint. SQLServer16.0.1000.6/instalação/contas/permissões/identidades/credenciais/serviços/DDL/Flyway/produção preservados. C78rollback-only, MainHTTPhistóricoUNKNOWN e demais gates abertos.

## Próximas ações

1. Provar RED dos testes de nova ordem contra o produto anterior, sem ESL/SQL.
2. Versionar a semântica Fretes ID asc, conservar a baseline anterior e qualificar testes/serialização/gates/pacote; não relaxar contrato ou cobertura.
3. Consolidar o timeout remanescente com a ESL e planejar nova prova somente com ganho causal e unidade própria. Extração completa e comparação de duas travessias permanecem abertas.
