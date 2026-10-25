# Checkpoint 0542 — qualificação do piso ESL após liberação de memória

23/10/2026. O usuário informou “liberei mais memopria, pode continuar”. Continuação da alteração de [0541](0541-piso-esl-implementado-qualificacao-pendente-memoria.md), preservando a recusa de memória anterior. Nenhuma nova alteração de lógica Java nesta etapa.

## Resultado de código e testes

Validação em snapshot isolado, sem `.env`, histórico de `target` ou diretório `.git`, com ambiente de teste sanitizado e Maven offline. Não foram ativados perfis físicos, integrações SQL ou testes remotos.

| Verificação | Resultado |
|---|---|
| Compilação Java17 estrita | 749 fontes de produção e 673 de teste; PASS |
| Regressão causal com a fonte anterior | Três casos inválidos: três falhas esperadas por ausência da recusa, zero erros e skips. Log/XML RED preservados |
| Implementação atual | 70 casos aprovados, zero falhas, erros ou skips |
| Formatação | Spotless dos três arquivos alterados: PASS, sem mudança dos bytes preparados em0541 |
| Checkstyle | Zero violações; PASS |
| Drift dos inputs antes de publicar | Zero; fontes publicadas iguais aos bytes compilados/testados |

Os70 incluem37 de `RuntimeConfigurationFactoryTest`,14 de `MainDevWorkspaceProcessTest`,6 de governor,5 de policy,4 de configuração da origem DEV,3 de configuração GraphQL e1 de fingerprint. Os sete casos novos de0541 estão incluídos nos37 da factory; não somar novamente. Cada teste parametrizado continua contando uma invocação, sem multiplicar os loops internos por origem/canal. Esses resultados não são suíte integral, `verify` completo, cobertura nova nem aceite operacional.

A correção efetiva recusa0/1/1999ms, aceita2000/2001/60000ms e respeita o valor final após precedência do ambiente para Data Export e GraphQL. A policy genérica com intervalos sintéticos não foi alterada. Acrescentado um comentário ao exemplo não secreto: budgets locais não representam saldo/franquia comercial.

## Execução protegida e ocorrência preservada

O executor admitiu o lote em23/10 às17:15BRT com7884MiB disponíveis e73% de comprometimento. Limite de2048MiB por árvore/processo, prazo10min e uma vaga pesada mantidos.

Após os testes e o estilo passarem, `graphify update .` iniciou a atualização AST com agrupamento. A etapa não concluiu dentro do prazo global: recibo às17:25:09BRT, `ExitCode=124`, `TimedOut=true`, pico da árvore1219354624B (1162,87MiB). **O lote completo não passou.** O timeout não apaga os resultados já concluídos, nem permite declarar o índice atualizado. O arquivo publicado `graph.json` ainda conservava a data anterior na conferência após a ocorrência.

Preparada recuperação específica `graphify update . --no-cluster`, sem LLM, sem repetir compilação/testes e com conferência dos hashes atuais. A primeira admissão dessa recuperação foi recusada por `BATCH_CONCURRENCY_LIMIT`; um lote protegido do projeto WMS ocupava a vaga. Nenhum processo de outro projeto foi encerrado. Novo empacotamento offline e sete testes de integridade também estão preparados, condicionados à conclusão do índice e à admissão pelo executor.

## Evidências

Diretório desta unidade, compartilhado com o preparo0541:

`C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/qualificacao-intervalo-esl-20261023-01`

- `result.json`, `input-hashes.json`, logs de compilação/estilo e diretórios `red-reports`/`green-reports`: resultados locais efetivos.
- `memory-job-receipt.json`: ocorrência final124/prazo; não é recibo de sucesso integral.
- `graphify-ast.log`: etapa de índice não concluída.
- `index-admission-refusal-01.json`: recusa por concorrência antes de iniciar a recuperação.
- `resume-index.py`, `index-protected.ps1`, `package.py`, `package-protected.ps1`: sucessores preparados. Preparo não é execução; registrar abaixo resultados efetivos quando ocorrerem.

Hashes SHA256 dos três arquivos Java qualificados:

```text
RuntimeConfigurationFactory.java      7D675EE60CBD07DBBFC09AA83979BC0246551AB756178F47D746F9D484A58DC6
RuntimeConfigurationFactoryTest.java  F12676181DAA5392B900C9544DE1C0E177A8F4C6997CDC7795D3EDCB1B5F985E
MainDevWorkspaceProcessTest.java      1468D809AF513F11D99F03BA0ECBF721D5CD8AECE33C9823278E6067CE563952
```

## Continuação e limites

Concluir a recuperação AST e o pacote quando a vaga protegida estiver disponível, preservando todos os recibos anteriores e sem repetir uma admissão recusada enquanto o recurso continuar ocupado. O novo pacote não deve sobrescrever deployments históricos nem receber autoridade física por ter sido gerado.

ESL HTTP, SQL e materialização de credenciais: zero nesta etapa. V1 permanece pausada pelo usuário. Franquia/escopo contratual e Fretes6389/422 continuam pendentes; a resposta do fornecedor sobre Coletas e as regras de429 não informaram saldo comercial. Permanecem os requisitos de caller/coorte/alvo/proteção/reserva antes de efeitos, STOP429, C78 rollback-only, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, limites de cobertura/G05 e todas as proibições SQL16.0.1000.6. Não houve extração, retenção, paridade integral ou cutover.
