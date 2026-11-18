# 0666 — Coletas: 31 colunas individuais e contrato do consumidor

Atualização de 03/11/2026. As conclusões de 26/31 dos checkpoints anteriores foram superadas por provas adicionais, preservadas em seus artefatos originais.

## Comparação com o Excel

Par exato: Source03 `033b024b-d604-4210-9930-67106a1aba2c`, encerrado às 16:04:37 BRT, e Excel ESL1760193, gerado às 16:05 sem cache, solicitação de 19–25/10/2026, SHA-256 `EDD65AF4F47B87EF0BEBAE14A10E32B4E89DC90E70DB0FE9651DE2076EA36512`.

1.561 ocorrências, 1.155 coletas, nenhuma raiz faltante/extra ou diferença de multiplicidade. RAW e Mapped passaram nas 113 guardas originais. As três medidas decimais também passaram nas 16 guardas adicionais e na prova exata de representação IEEE754binary64: todas as células, conjuntos por raiz, associação conjunta das três medidas e somas exatas dos valores individualmente codificados. Os decimais originais e as diferenças racionais de representação permanecem preservados; não houve tolerância ou arredondamento arbitrário.

As duas datas/horas passaram numa conferência independente dos textos exibidos no próprio XLSX, importado somente para leitura pelo runtime de planilhas. Foram conferidas todas as 3.122 células temporais e sua associação conjunta por raiz. Frações e offsets da API continuam preservados. Esse aceite é específico para o workbook citado e não autoriza presumir o comportamento de outro workbook/período.

**31 colunas passaram individualmente. A associação conjunta de todas as 31 ainda está em qualificação na frente de Correções, com 131 guardas propostas. Ainda são 0/3 períodos completos. Outro período e o dia atual não foram testados nesta rodada.** Não confundir colunas individualmente aprovadas com linhas completas aprovadas.

Aceites privados no supervisor:

- `outputs/root-acceptance-binary64-mapped0665-01.json`: SHA A4FCEF763807000FDCAC07C89354F3F33FFC16CAF68B99099697B4B8A04616F3.
- `outputs/root-acceptance-binary64-raw0665-01.json`: SHA 3398947C22C10F1EEE1BE8E59609637DEA03DD112F5FE6F56C958DD4530CB5E5.
- `outputs/root-acceptance-timestamp-display0665-01.json`: SHA 9F7B9D1A71386A5A0D883BB7C2CF1A06F4872AD380E1612A98BE1283E3256DC3.

## Encaixe no dashboard

Consumidor definido pelo usuário: `C:/Users/suporte/Documents/projetos/dashboards-etl`.

As 21 colunas do contrato estão protegidas por validador e 30 testes já aprovados; colunas adicionais são aceitas. Uma nova auditoria offline dos 1.561 registros verificou os 18 campos de origem direta no Data Export, com zero valores fora dos tipos/limites do consumidor: inteiros32/64bits, datas civis, texto1024UTF16 e decimais exatamente representáveis em28,8. Os nulos foram preservados e não houve variação observada desses campos dentro da mesma raiz.

Aceite `outputs/root-acceptance-dashboard-direct-value-fit0666-02.json`, SHA `392429190E7EB200BDC67BB3FB16160A19BEAF33128E489E6102017FD8318FF5`. Original0/3126ms/pico64249856B, processos encerrados, estado restaurado, zeroSource/HTTP/SQL. A tentativa01parou no limite de tamanho do resumo e está preservada.

**Consumo real ainda não aprovado.** Falta provar exclusão na origem, região logística e data de extração. Também há pendências de publicação e semântica: V091 publica `pub.analytic_lab_sql_03`, enquanto o consumidor lê `dbo.vw_coletas_powerbi`; Cliente utiliza complemento lateral, Filial utiliza referência, manifesto utiliza vínculo relacional e Região da Coleta é sintetizada de cidade/UF. Essas regras precisam ser confrontadas com o contrato do consumidor e o Data Export antes de promover dados. Não representam automaticamente campos que a extração deixou de trazer.

O catálogo canônico ainda é `coletas-status-v1`, `finished`→`Finalizada`, inclusive constraintsV010. A correção privada qualificada anteriormente ainda não foi incorporada à publicação canônica. A frente de Correções recebeu os achados para proposta posterior à prova conjunta131. Não aplicar SQL nem habilitar GraphQL de Coletas: nesta tarefa, entidades usam Data Export; GraphQL somente Usuários.

Mapa21, contagens de nulos e critérios restantes: `dashboards-etl/docs/coletas-aceite-consumo-v2-2026-11-03.md`. Exigir posterior roundtripSQL e consumo real pelos endpoints, sem declarar dados da V1 como prova da V2 e sem mudar KPIs para esconder divergências.

## Estado operacional

Sem execução pesada ativa ao fechar esta atualização. Estado canônico703000B/SHA856FD82028B5393B7FC6B28E5D65EF7A4739BF10C96D0BD89C9537389163B95A, ACLB4E214EB, Root0658, slot0. Source03consumiu a autorização de uma tentativa; nenhum novo envio foi executado. V1 permanece pausado pelo humano. SQL2022,PM2,serviços,produção preservados. Nenhum commit/push.

Próximos passos: fechar associação31, incorporar correções qualificadas, resolver publicação do consumidor e preparar novo período com escopo e requisições controlados. Preservar dados extras da API; não exigir igualdade artificial entre linhas físicas da extração e uma linha por coleta do painel.
