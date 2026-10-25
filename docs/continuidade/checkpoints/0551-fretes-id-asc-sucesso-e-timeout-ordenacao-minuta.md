# Checkpoint 0551 — Fretes: ID asc funciona; ordenação por minuta retorna timeout

## Identificação e objetivo

23/10/2026, fechamento após20:56 BRT. Anterior: [0550](0550-peso-fretes-intermitencia-real-e-comparacao-reduzida.md), SHA-256 `E1875F27F7342A1800B13601018B4CCD2A1DC16C5933DA4744CBC40352168AC9`.

Pedido efetivo: **“pois teste, vamos ver de todos os angulos e formas para chegar num resultado”**. O usuário liberou memória e, depois, escolheu **“Deixar o WMS terminar primeiro”**. A última rodada já estava em curso quando essa preferência chegou; foi fechada, sem iniciar outra execução física depois. Nenhum WMS/V1 foi interrompido. Próximos testes/builds físicos ficam para depois do trabalho WMS.

Estado: **caracterização real bem-sucedida de ordenação por ID, em amostras; hipótese da ordenação por minuta reforçada, sem causa interna definitiva ou extração integral**. A necessidade imediata de dividir o relatório sugerida em0550 não foi demonstrada: o relatório existente retornou todos114 campos com per100.

## Autorização, unidades e limites

Três unidades distintas, cada uma com reserva e ledger antes dos efeitos, dentro da investigação direta do usuário:

1. Comparação inicial: teto6; consumiu INFO, corpo/per1/sem ordem e corpo/per1/com ordem por minuta. Encerrou no primeiro422. Três chamadas não usadas foram descartadas.
2. Nova hipótese concreta após o contraste: testar **outra ordenação, `id asc`**, sem repetir a consulta que falhou. Teto3, consumiu3 e concluiu: corpo/página1, corpo/página2, URL/página1. Não é reposição do saldo descartado.
3. Após os três sucessos da unidade2, amostra de escala/data: teto4, consumiu4 e concluiu; somente GET com query/id asc, per10/páginas1–2, per100/página1 e per10/data17.

Cada unidade serial, intervalo mínimo3s, conexão10s, request30s, resposta10MiB, até1000 linhas físicas por página; cap de IDs distintos conforme per. Sem retry, redirect, troca de credencial, fallback, GraphQL ou SQL. STOP no primeiro não2xx/429/erro de contrato, identidade, ordem, data, limite ou consistência. As duas últimas unidades não tentaram novamente `corporation_sequence_number asc`.

Todas passaram pelo executor original, com limite512MiB por processo/árvore e deadline finito. Admissões reais HTTP:5921MiB/77%,6059MiB/77%,5841MiB/77%. Nenhum contorno do mínimo4096MiB/máximo85%. Refusas anteriores por concorrência foram preservadas. Wrapper externo ganhou espera única de até30s pela mesma trava global; sua posse reentrante mantém a vaga durante o executor canônico, que conserva admissão, Job Object e limites. O executor compartilhado não foi alterado.

## Resultados reais

Dez HTTP novos nesta investigação: **nove200 e um422; nenhum429/401/403**. Oito páginas DATA200 e uma DATA422, além de INFO200. Não confundir esse total com sondas de outros checkpoints.

| Conclusão BRT | Dia consultado | Envio GET | Ordenação | Página/per | HTTP | Tempo após intervalo | Linhas / IDs distintos |
| --- | --- | --- | --- | --- | ---: | ---: | --- |
| 20:45:30 | INFO | sem corpo | — | — | 200 | 1032ms | 114 campos/16 filtros |
| 20:45:36 | 22/10 | corpo JSON | ausente | 1/1 | 200 | 2495ms | 1/1 |
| 20:45:42 | 22/10 | corpo JSON | corporation_sequence_number asc | 1/1 | 422 | 2997ms | sem página válida |
| 20:50:09 | 22/10 | corpo JSON | id asc | 1/1 | 200 | 1415ms | 1/1 |
| 20:50:15 | 22/10 | corpo JSON | id asc | 2/1 | 200 | 2269ms | 1/1 |
| 20:50:19 | 22/10 | query na URL | id asc | 1/1 | 200 | 1344ms | 1/1 |
| 20:54:38 | 22/10 | query na URL | id asc | 1/10 | 200 | 2128ms | 10/10 |
| 20:54:43 | 22/10 | query na URL | id asc | 2/10 | 200 | 1538ms | 10/10 |
| 20:54:49 | 22/10 | query na URL | id asc | 1/100 | 200 | 2613ms | 113/100 |
| 20:54:54 | 17/10 | query na URL | id asc | 1/10 | 200 | 1409ms | 10/10 |

Todos os DATA200 expuseram114 nomes técnicos. Na página sem ordem, o conjunto de nomes coincide exatamente com o INFO atual; portanto não foi um relatório de colunas reduzidas. Nenhum valor de negócio ou ID/hash de ID foi retido. As113 linhas/per100 são expansão válida de100 IDs distintos, conforme contrato histórico; não são excesso de entidades nem113 fretes.

O corpo exato do422 foi preservado cifrado em DPAPI do usuário existente; após comparação estrita, somente esta resposta genérica foi publicada:

```json
{"errors":["timeout"],"formatted_errors":["Tempo limite excedido"]}
```

A consulta sem ordem e a que falhou tinham o mesmo endpoint6389/data, credencial, GET-corpo, filtro `search.freights.service_at = 2026-10-22 - 2026-10-22`, página1 e per1. A única propriedade de requisição acrescentada foi:

```json
{"order_by":"corporation_sequence_number asc"}
```

O ID ordenado foi testado com:

```json
{
  "search":{"freights":{"service_at":"2026-10-22 - 2026-10-22"}},
  "page":"1",
  "per":"1",
  "order_by":"id asc"
}
```

As chamadas com query codificam os mesmos parâmetros lógicos na URL; per100 e data17 constam separadamente da tabela e das reservas. Nenhum token aparece nesses exemplos ou em argumentos de processo; o token existente foi passado pelo stdin do curl.

## Consistência realmente verificada e interpretação

- ID integral sem perda de precisão, fonte `service_at` compatível com o dia pedido e limite por identidade conferidos.
- As duas páginas de per1 tiveram IDs diferentes e progressão crescente. Corpo e query retornaram o mesmo primeiro ID. Essa comparação não conserva o ID nem seu hash.
- Nas páginas com per10, IDs crescentes e ausência de sobreposição entre páginas1/2. A página per100 contém todos os IDs das duas páginas menores. Não somar esses conjuntos sobrepostos como novos fretes extraídos.
- Os114 campos, duas datas, dois meios de envio e per1/10/100 funcionaram com ID asc nesta amostra. Isso reduz a justificativa para dividir imediatamente o relatório e mostra uma alternativa útil ao timeout observado.
- **Ordenação por minuta é a principal hipótese concreta**, pois sua inclusão foi o único delta no contraste imediato que falhou. É um contraste único e a fonte já era intermitente: não prova sozinho causalidade definitiva, tempo limite interno exato, índice ausente, plano de consulta, banco utilizado pela ESL ou sucesso permanente da alternativa.
- A comparação sem ordem não autoriza paginação operacional sem ordem. Os sucessos de ID asc também não certificam snapshot/ordem global/terminalidade, estabilidade em todos os dias, outros relatórios, persistência ou paridade.

## Consequência para o ETL e próxima investigação

O transporte operacional **GET_WITH_QUERY** já obteve sucesso nesta investigação, inclusive per100; não há motivo demonstrado para inverter o transporte global. A alternativa a qualificar é a **ordem de Fretes**, preservando a arquitetura Data Export.

`DataExportTemplate.FRETES` ainda fixa `corporation_sequence_number asc` e a versão contratual histórica. `DataExportPageRequest`, pedidos de observação e gates de semântica vinculam-se ao contrato. Nenhum desses defaults, fingerprints, releases, fixtures ou gates foi alterado aqui; trocar uma string sem reconciliar contrato/consumidores não constitui correção qualificada. O JAR atual permanece anterior a essa descoberta.

Após o WMS, o próximo trabalho concreto é qualificar um contrato/caminho controlado para ID asc e a paginação terminal de uma janela delimitada, com dedupe, retomada e consistência reais. Só então adotar a ordem no fluxo adequado e ampliar. Para diagnóstico ESL, encaminhar a diferença específica entre as duas ordenações e os horários atuais; pedir revisão do custo/execução da ordenação por minuta, em vez de presumir que o relatório precisa ser dividido. Divisão continua alternativa se os testes posteriores demonstrarem necessidade, não requisito atual comprovado.

## Evidências, falhas preservadas e fechamento

Base externa: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2`.

- `testes-fretes-variantes-20261023-01`: procedimento, selfcheck protegido, reserva/ledger, resultados, erro DPAPI e resumo sanitizado. Results SHA-256 `6AAF905F1170675CE4175B5F3C9231802103D6B281726CED4D18E21E224D3ECD`.
- `testes-fretes-id-20261023-01`: fonte qualificada, reserva, identidade comparada somente em memória, ledger, resultados e receipt. Results SHA-256 `F21ED50ACC095C1A7E4B1A271C90F48EC6936E2D4D8A357B66469836DBC7470D`.
- `testes-fretes-escala-20261023-01`: selfcheck e escala/datas concluídos. Results SHA-256 `C2903752FD9E36CBB7BD062095B8AEBA51C73BF3EE50381FC58AD9927451812F`.
- `testes-fretes-escala-20261023-01/final-evidence.json`: dez eventos, hashes das três reservas/results/receipts e limites; SHA-256 `4DFE2AD5BECE9D86E5AE605C43C66D60DDAADE708F7F4875249F09F5DC75E5B0`. Fontes reconferidas contra hashes reservados, sem drift.
- Receipts finais: primeira unidade exit1 por422, sem timeout do Job, pico66473984B; ID exit0/pico95678464B; escala exit0/pico103833600B. Todos encerrados; nenhum processo próprio deixado ativo ou retomada automática.
- Falhas locais anteriores preservadas: sintaxe de catch do wrapper antes de worker; remoção ampla do header de parâmetro ao compor helper, corrigida para remoção ancorada; fixture de data desserializada automaticamente pelo PowerShell, corrigida para DateKind String como no transporte. Os selfchecks falhos abriram zero HTTP; fontes/receipts anteriores foram guardados e não convertidos em PASS. As fontes corrigidas passaram selfcheck antes do próprio HTTP e foram reconferidas sem alteração entre fases.
- Nenhum Maven/JUnit/JVM/índice novo nesta unidade. Não somar selfchecks procedurais aos56 testes Java históricos. Produção Java, POM, configuração, schema/DDL/Flyway e relatório original não alterados. JAR de0547 reconferido: `92714FBFAA4B5836D4B55F59DF7ED062934C72681A5FA701CCBCF6B8597D5C42`.
- SQL/GraphQL, persistência, deploy, restart V1/WMS, credenciais/contas/permissões/identidades e efeitos produtivos:zero. SQL16.0.1000.6 e demais proibições permanentes preservados. C78 rollback-only, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, cobertura e demais aceites permanecem abertos.

## Retomada imediata

1. Respeitar a prioridade posterior do usuário: próximas execuções físicas somente depois do WMS; sem monitor, loop ou automação criado para isso.
2. Qualificar contrato e uso controlado de ID asc no fluxo V2, incluindo os vínculos de semântica e testes causais pertinentes, preservando os demais templates/defaults históricos até adoção correta.
3. Reservar e provar paginação terminal/consistência de uma janela pequena antes de ampliar ou declarar extração completa; levar à ESL o contraste específico se a causa interna ainda precisar de confirmação.
