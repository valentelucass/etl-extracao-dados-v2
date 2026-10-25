# Checkpoint 0554 — Fretes por ID qualificado; terminal37 e timeout remanescente

## Identificação e resultado

24/10/2026, após00:30BRT. Anterior: [0553](0553-fretes-revisao-id-96-testes-pacote-em-curso.md), SHA-256 `E6CA842CB7B267406008D96FB03DAC4BE6D1C6D1410F3BF473D214369B4E7F59`.

O usuário retomou com “pronto, agora so ta vc pode continuar”. A melhoria de ordenação de Fretes foi implementada, versionada e qualificada localmente, com pacote conferido. Também foi observada uma travessia real até página vazia do dia17/10, com37fretes. **A segunda travessia falhou por timeout mesmo com ID asc; extração completa, comparação entre passagens, persistência e paridade não foram concluídas.**

## Mudança entregue

- `DataExportTemplate.FRETES` agora usa `id asc`, na revisão `2026-10-24.fretes-id.v1`. O gate recusa a ordem antiga antes da chamada. Filtros, default/teto per100, transporte GET_WITH_QUERY, classificação TRANSITIONAL e bloqueios de completude continuam iguais. A mudança não cria garantia de snapshot, estabilidade ou disponibilidade da ESL.
- O catálogo ativo usa revisão `2026-10-24.fretes-id.1` e fingerprints calculados a partir das classes compiladas. O contrato estrutural de metadata/resposta continua sintético; os114campos observados não foram promovidos automaticamente a release real.
- O manifesto anterior foi preservado byte a byte em `docs/catalogos/contratos-primeira-onda/historico/manifesto-2026-09-01.v2-025a.3.json`, SHA-256 `D7910FF0E3D0F78BEE57AAAD5178561DB8BCB0D0D253448531B606BCEF45950C`. Contratos6908 e Usuários foram comparados integralmente e permanecem iguais. Os outros templates do enum não foram alterados.
- Dois testes existentes foram atualizados e uma invocação adicional exige recusa da ordem antiga antes da fonte. O validador do catálogo exige versão/ordem transitória novas e o hash histórico exato; nenhum limiar, classificação ou gate foi relaxado.

## Validação concluída

| Prova | Resultado |
| --- | --- |
| RED contra produto anterior | 8casos,3falhas esperadas,0erros/0skips |
| Compilação estrita | 749fontes de produção e673de teste, sem warnings aceitos |
| Testes selecionados | 96casos de13suítes,0falhas/0erros/0skips |
| Formatter dirigido | três arquivos visitados e limpos |
| Checkstyle | 1422Java,0violações |
| Pacote offline | 7IT,0falhas/0erros/0skips |
| Bytes do pacote | 1319classes e45recursos conferidos contra a compilação; oito bibliotecas iguais às já qualificadas |
| Validador do catálogo | 3contratos,42aspectos e14fixtures, sem classificação desconhecida |
| Índice AST | atualizado semLLM/agrupamento:44366nós/116027arestas |
| Integridade final | 1542inputs de código/configuração/manifesto sem drift antes/depois do índice |

É qualificação composta no escopo, **não um verify integral, cobertura nova ou uma única execução limpa**. A primeira execução dos7IT teve6erros por arquivos `database/migrations` omitidos do snapshot; após completar os arquivos canônicos de esquema e launcher, somente os7IT foram repetidos e passaram. O recibo falho e seus relatórios permanecem falhos/preservados. Nenhum arquivo SQL foi executado; osIT conferem cópias de arquivos e comportamento offline. Os96aprovados não foram repetidos.

Pacote qualificado, sem implantação:

`C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/fretes-ordem-id-20261024-01/green/snapshot/target/etl-dataexport-v2.jar`

SHA-256 **`8044887E07B5FB8D05258CF8A2E75901C578744E4A71A9CDFA99208010F62D95`**. A referência0547 continua histórica, não representa a revisão nova. Nenhum install/dispatch/Main produtivo foi iniciado com esses bytes.

## Prova real da API e limite ainda aberto

Conforme [0552](0552-fretes-terminal37-e-timeout-tambem-com-id.md), a unidade nova consumiu6HTTP:5x200/1x422,zero429; serial,intervalo3s,timeout30s,semretry/fallback/redirect. Encerrou no primeiro erro e não foi reaberta.

- Dia17/10, `id asc`, per25:duas páginas com37linhas/37IDs e114campos; página3 retornou200vazia. ID integral,data,ordem e ausência de sobreposição foram conferidos em memória.
- Mesmo dia/ordem, per50:página1 retornou200 com37linhas/37IDs; página2 retornou422 em2932ms. O corpo exato foi `errors=[timeout]`, `formatted_errors=[Tempo limite excedido]`.
- A comparação entre conjuntos/valores de duas travessias completas **não aconteceu**, porque a segunda não terminou. Totais iguais não substituem essa prova. IDs, valores e hashes de negócio não foram persistidos.

O resultado demonstra que a ordenação por minuta não explica sozinha todos os timeouts. A nova ordem tem caminho funcional e uma terminalidade observada, mas não elimina a instabilidade. Não afirmar limite interno de3s apenas pelo tempo medido no cliente, índice ausente ou causa específica do banco ESL. Também não pressupor página terminal vazia quando ela não respondeu, nem completar pela regra “menos registros que per”.

A requisição específica para investigação ESL é GET`/api/analytics/reports/6389/data`, **sem body**, com:

```json
{
  "search[freights][service_at]": "2026-10-17 - 2026-10-17",
  "page": "2",
  "per": "50",
  "order_by": "id asc"
}
```

Horário Brasília: início24/10/2026 00:02:00, resposta00:02:03. A página anterior trouxe37fretes, e a travessia per25 chegou ao terminal. Pergunta concreta: **qual é a causa/limite interno desse timeout e como corrigir a execução para permitir paginação até uma resposta terminal válida?** Não pedir exemplo genérico, atribuir à chave/franquia ou afirmar necessidade de dividir o relatório sem diagnóstico. Nenhuma mensagem foi enviada ao gestor/ESL.

## Evidências e fechamento

Base externa de qualificação: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/fretes-ordem-id-20261024-01`.

- `recover/result.json`, SHA-256 `5A68484304D3BAAC3D801FD443F5B96FB91F6FB2C92FF24459CE6E6232107A7E`: composição96+7, JAR/bytes/libs, referência ao recibo anterior falho.
- `index/result.json`, SHA-256 `5D16F5D8608B2A530BB83BB7D94F5A0F59106F0AB31C3A8BE69D76A2DBD3C80F`: validador e AST concluídos, hashes reconferidos.
- `index-memory-job-receipt.json`, SHA-256 `13790C4B72D68FCBCB1D2D5A6A7F46C7CC0447496A0AA2A47CE9CC0C8A5FDE29`: exit0,324394ms,pico1300574208B,sem timeout,encerrado00:30:42BRT.
- Validador `scripts/validation/Test-FirstWaveContractCatalog.ps1`, SHA-256 `F57E2643CB421298119B28EF2C8C45D49A99256381B26D37119CB8A737ADA635`.
- Base da fonte: `../fretes-terminal-20261023-01/results.json`, SHA-256 `A93BE253C367364342113A9AEA94FBC0C91B82311CB33D12753688DF02FEB654`. O nome da pasta é histórico de preparo; timestamps reais são24/10.
- `../fretes-terminal-20261023-01/support-evidence.json`, SHA-256 `7CA68FC4129196BD2DED68E5703F59BE61D26AA941729CB312D8171B048A68AE`: endpoint/parâmetros,tokenplaceholder,horários e resposta genérica,semdados de negócio. A primeira exportação de horário sofreu conversão automática de datas; foi preservada como inválida e refeita com DateKindString/cultura explícita, sem reconstruir headers/request-id ausentes.

Todos os processos próprios encerrados. SourceHTTPnovo após o422:zero; qualificação usa somente fixtures/HTTPloopback. SQL/TDS,GraphQL,deploy,produção,DDL/Flyway,instalação/versãoSQLServer16.0.1000.6,contas/credenciais/permissões/identidades/serviços,V1/WMS:não alterados. C78rollback-only,MainHTTPhistóricoUNKNOWN/MISSING_NOT_RECONSTRUCTED,43RAW/53exclusões e gates de cobertura/aceite preservados. AST atualizou código; análise semântica documental/HTML históricos não foram reconstruídos.

## Próximas ações

1. ESL: investigar o timeout específico da página2/per50/ID, com request/horário preservados. Não renovar automaticamente a unidade encerrada.
2. Para usar a revisão no fluxo real, qualificar o release atual de metadata/resposta114campos e os bindings/coorte da execução; a baseline sintética não autoriza captura por si só.
3. Após admissões e unidade própria, concluir comparação terminal e fluxo ETL/persistência/paridade. Não marcar extração completa, retenção ou corte por essa amostra e qualificação local.
