# Checkpoint 0550 — peso de Fretes: intermitência real e comparação reduzida

## Identificação e objetivo

23/10/2026, conclusão documental após 20:34 BRT. Anterior: [0549](0549-fretes-timeout-comprovado-chave-controles-validos.md), SHA-256 `139E8F0718376D4EA08207553FF14D20575AF99F2DFBA44E7C49733595AC868D`.

O usuário trouxe a hipótese do gestor de que Fretes é um relatório pesado e poderia ser dividido em dois relatórios complementares; pediu investigar a fundo. Estado: **hipótese plausível, causa interna ainda não demonstrada; intermitência e timeout no legado comprovados por logs existentes**. Esta unidade examinou contratos, código e evidências locais, sem novo HTTP, SQL, edição do relatório ou implementação de junção.

## Evidência nova extraída de registros existentes

Leitura somente do `logs/aplicacao/runtime/extrator-esl.log` do V1. A atribuição exige data 23/10, logger `ClienteApiDataExport`/`DataExportPaginator`, mesma thread e último cabeçalho `INICIANDO EXTRACAO` com template 6389. Contextos de comandos, identificadores e mensagens brutas não foram exportados. Hash do log reconferido depois da análise, sem alteração.

- 92 cabeçalhos de extração 6389, todos com períodos **diários** registrados. A janela agregada 16/10–23/10 dos logs de enriquecimento não significa uma única consulta de oito dias. A investigação detalhada corrige essa interpretação possível.
- 651 eventos `Resposta recebida` atribuídos: **647 HTTP 200 e quatro HTTP 422**. O código emite essa mensagem depois do laço de tentativas da página; portanto **651 não é a quantidade total de chamadas HTTP**.
- **72 avisos explícitos `Timeout 422 ... Retentativa`** no mesmo conjunto de extrações, inclusive em execuções que depois registram 200. Isto é evidência do tratamento observado de timeout, além da mera existência do código de retry.
- As quatro respostas finais 422 aparecem às 00:33:00, 06:20:02, 10:01:23 e 14:25:58. Os períodos correspondentes também têm respostas 200 em outros horários. Não inferir estabilidade permanente, causa única ou completude a partir disso.

| Dia consultado no legado | Cabeçalhos diários | Com respostas observadas somente 200 | Com resposta observada 422 |
| --- | ---: | ---: | ---: |
| 16/10 | 12 | 11 | 1 |
| 17/10 | 11 | 9 | 2 |
| 18/10 | 9 | 8 | 1 |
| 22/10 | 7 | 7 | 0 |

Exemplos verificáveis no log: cabeçalho linha 583/data16 e resposta 200 linha639 às 00:32:47; cabeçalho linha109943/data16 e resposta422 linha109953 às10:01:23. Para data17, resposta422 linha651 às00:33:00 e resposta200 linha16863 às01:57:15. Para data22, cabeçalho linha17014 às01:58:34 e resposta200 linha17055 às01:58:56. As diferenças de horário, transporte/tamanho efetivo e demais condições impedem tratar isto como um experimento causal controlado.

Os cabeçalhos declaram per1000; não assumir que todos os requests efetivos posteriores mantêm esse tamanho, devido ao tratamento de páginas/retries. As 2316 mensagens de aplicação bem-sucedida do complemento a lotes do fluxo principal **não são 2316 chamadas HTTP nem fretes distintos**. `FreteExtractor.extractAndSaveWithMetrics` carrega o mapa complementar e o entrega ao acumulador de lotes; não somar correspondências repetidas como cobertura nova.

## Peso do relatório: o que se pode concluir

1. O timeout atual de 0549 vem em uma resposta HTTP da API, antes do limite local de30s. Não é evidência de processamento local travado ou de chave rejeitada.
2. O INFO atual declara114 campos e16 filtros. O histórico de22/09 registrava110 campos; nomes completos antigos não foram preservados e não se pode identificar os quatro campos novos pela contagem. A quantidade de campos, isoladamente, não mede custo de execução.
3. O contrato histórico já comprovou expansão física: um mesmo `id` pode aparecer em várias linhas. Isso exige cuidado com detalhes/relacionamentos, mas não identifica quais junções ou cálculos internos consomem tempo. Não temos a consulta interna nem plano/tempos do servidor ESL.
4. O diagnóstico de21/10 falhou com per1, e o de23/10 falhou com per100 em janela diária. São observações de momentos/transportes diferentes; não provam que o tamanho da página seja irrelevante. Reduzir resultados retornados pode não reduzir todas as operações internas necessárias ao relatório; isso continua hipótese, sem atribuir banco ou implementação específica à ESL.
5. O V1 também apresenta timeout422 e sucessos para o mesmo dia. Portanto não é correto dizer que todo Data Export6389 funciona sempre no legado e falha somente no V2. A carga principal GraphQL pode prosseguir quando o complemento falha; V2 permanece Data Export.

**Conclusão sustentada:** custo/complexidade do relatório é compatível com as evidências e merece comparação, mas não foi distinguido de carga momentânea, dados específicos, filtro/ordenação ou outra condição interna do fornecedor. Não declarar que dividir em dois já resolve, nem atribuir custo a um campo específico sem prova.

## Comparação concreta necessária e condições da divisão

Antes de alterar o relatório original, o gestor/ESL pode fornecer uma **cópia reduzida do6389**, com identificador próprio, mantendo o mesmo filtro de data, ordenação e identidade de frete. Começar pelo identificador e poucos campos básicos, retirando grupos complementares e os cálculos/relacionamentos que realmente sustentam esses grupos. Cortar aproximadamente metade dos nomes sem retirar o trabalho interno pode não aliviar a execução.

Quando os IDs e definições dessas cópias estiverem disponíveis, uma unidade HTTP própria, previamente delimitada, poderá comparar reduzido/original na mesma janela fechada e com os mesmos parâmetros. Sem alterar produção, token ou transportes operacionais. Comparação próxima no tempo reduz uma diferença, mas sucesso isolado ainda não prova causalidade; pedir também à ESL o diagnóstico/tempo interno das variantes. STOP no primeiro erro/429; nenhuma tentativa automática adicional foi reservada aqui.

Se a redução funcionar e for adotada a divisão, os dois contratos precisam conservar o **mesmo ID técnico de frete e escopo**, filtros temporais compatíveis, tipos e regra de atualização, cobertura e campos necessários ao ETL. Não juntar pela posição da linha ou somente pelo número de minuta. Um frete com vários detalhes exige identidade própria do detalhe ou agregação aprovada; uma junção simples pode multiplicar linhas e valores. A ausência do complemento não equivale a nulo nem autoriza apagar informação. A partição só pode ser declarada completa depois de ambas as fontes concluídas e reconciliadas. Nenhum desses contratos/joins foi implementado nesta unidade.

`service_at` é declarado `date`: não converter a consulta em fatias horárias sem contrato comprovado. `by_updated_at` é outro critério e não substitui automaticamente a cobertura pela data de serviço.

## Execução, limitações e preservação

Base externa: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/investigacao-peso-fretes-20261023`.

- `Analyze-ExistingEvidence.ps1`: leitura/perfil sanitizado; sem rede, SQL, V1 executado ou comandos administrativos.
- `report-cost-analysis-v2.json`: análise atual; SHA-256 `889244CED16C3F49F8B61D2DAD3A0AB3C40B02851AC606601E6C58612AD231C8`.
- `support-facts.json`: exemplos e contraste por dia, hash do log reconferido; SHA-256 `AC65D9544227504B4F1D6CDE3BEF23E923CD841B27DD2A174772AB0FD738E7BD`.
- Falhas auxiliares preservadas: primeira atribuição presumiu logger diferente e não emitiu resultado; a agregação inicial por propriedade de dicionário produziu status0 e foi rejeitada/corrigida por chave explícita, com total reconciliado. `analysis-initial-failure.json`, fonte anterior e `report-cost-analysis.failed-grouping.json` preservam isso. O perfil inicial de mensagens examinava uma palavra timeout em contexto de logging, inclusive em sucessos; esse marcador é inválido e foi removido em `v1-log-events-sanitized-v2.json`. Não utilizar o marcador antigo para classificar erros.
- Última leitura de recursos:2531MiB livres/86% comprometidos; fora do mínimo4096MiB/máximo85% do executor. **Nenhum novo teste remoto foi iniciado**; não contornar a admissão nem encerrar outros processos. Isso é uma restrição local desta rodada, separada do timeout já devolvido pela ESL.
- Java de produção/testes, POM e configurações não alterados nesta unidade; nenhum Maven/JVM/índice novo. JAR de0547 reconferido, SHA-256 `92714FBFAA4B5836D4B55F59DF7ED062934C72681A5FA701CCBCF6B8597D5C42`. Os56 testes de0549 permanecem evidência anterior, não foram repetidos ou acrescidos.
- HTTP/GraphQL/SQL novos, edição de relatório, deploy, restart V1/WMS e efeitos produtivos:zero. C78 rollback-only, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, cobertura/aceites, SQL16.0.1000.6 e proibições permanentes preservados. `../CONTEXTO_GLOBAL.md` continua ausente.

## Retomada imediata

1. Gestor/ESL: fornecer ID e definição de uma cópia reduzida preservando identidade/filtro, ou a análise interna da execução do6389 e grupos responsáveis pelo custo.
2. Com artefatos e recursos admitidos, qualificar e reservar comparação pequena original/reduzido; preservar resposta, tempo e limites reais, sem replay de unidades consumidas.
3. Só após prova de melhora e contratos reconciliados, decidir divisão e implementar a composição Data Export no V2. Extração integral e causa interna permanecem abertas.
