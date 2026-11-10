# Atualização de campos relacionados — 0768

Este complemento do [acionador0766](atualizacao-historico-onze-entidades-0766.md) trata os campos que um relatório busca em outros registros do ESL. A [comparação0767](../catalogos/novas-entidades-0761/comparacao-excel-0767.md) demonstrou que a atualização da própria ocorrência não informa todas as mudanças de previsão e localização do frete.

## Estratégia por relatório

| Relatório | Conferência complementar |
| --- | --- |
| Consolidação Detalhada11067, 39campos | Reconferir diretamente todas as datas do histórico carregado, com cursor durável e substituição de cada fotografia completa. Não depender exclusivamente da atualização da consolidação para os valores de fretes e últimas ocorrências. |
| Ocorrências12072, 20campos | Localizar as datas relacionadas aos fretes alterados e comparar a localização atual com a observada nas fotografias anteriores. Reextrair integralmente os dias afetados. |

A Consolidação tem volume pequeno na amostra observada:221linhas em oito datas. Sua leitura direta evita buscar milhares de linhas de outros relatórios somente para conferir esses campos. O cursor `consolidationSweep` registra início, fim, próxima data e limite temporal da rodada. Uma falha conserva a próxima data e o marcador anterior; somente o término de toda a rodada permite confirmar o novo marcador.

Em Ocorrências, `FreightDependencyIndex` registra referências observadas em cada fotografia já confirmada no SQL. São índices auxiliares de trabalho: números de minuta/CT-e, data do frete e localização. Nunca são chaves primárias de negócio. Colisões ampliam os dias a reconferir; nenhuma linha do relatório é fundida ou eliminada por essas referências. As multiplicidades físicas permanecem na captura e no banco.

O índice privado guarda o arquivo, SHA256, escopo da origem, entidade, dia, execução e hash da captura. Ele só é publicado depois da gravação e releitura integral do SQL. Índice ausente é reconstruído pela captura completa do dia, em lotes; índice alterado ou pertencente a outro escopo interrompe a operação.

## Buscas e retomada

As datas dos fretes vêm das referências efetivamente retornadas por Ocorrências. Podem ser anteriores ao início do período das ocorrências: não presumir que uma ocorrência recente pertence a um frete recente.

Fretes alterados são consultados por **um dia de serviço por requisição lógica**, mantendo o filtro de atualização e paginação de25raízes. A localização é consultada por conjuntos de até oito datas, com paginação de100. A localização precisa dessa leitura completa: os controles reais mostraram mudanças que não apareceram no filtro de atualização do frete.

As descobertas de atualização do acionador também dividem a data de negócio: um dia por vez em Fretes; até oito dias nos demais relatórios. A data mínima do histórico permanece fixa. A divisão distribui a leitura e não descarta datas antigas.

`relatedPending` conserva a janela de atualização, datas afetadas, intervalos de localização e cursores `freightNext`/`locationNext`. A migração do cursor anterior, que usava intervalos maiores para Fretes, preserva os intervalos já confirmados e a fila de datas pendentes. Um erro no intervalo atual não confirma esse intervalo nem avança o marcador.

Uma descoberta parcial nunca substitui uma fotografia completa. A confirmação exige resposta terminal vazia, todos os registros válidos no modelo Java, gravação atômica e comparação de cada ocorrência/valor com a releitura SQL. Uma diferença de localização descoberta depois de uma captura anterior do mesmo ciclo exige nova captura; essa fotografia anterior não pode ser reutilizada para encerrar o trabalho.

Referências nulas, incompatíveis ou sem correspondência suficiente na localização provocam a reconferência do dia completo. Elas não autorizam criar uma chave ou preencher campos com valores de outro frete.

A consulta auxiliar de Localização preserva todos os 26 campos literais e a disposição do modelo legado. Para selecionar dias de Ocorrências, valida separadamente a data de serviço e a localização; uma referência de minuta ausente ou incompatível seleciona conservadoramente todos os dias que contêm fretes naquele intervalo. Isso permite tratar um registro antigo observado sem número de frete sem descartar a linha, inventar identidade ou aprovar a publicação da entidade legada em quarentena. O recibo distingue quantidade projetada, rejeições legadas e referências não atribuíveis. Paginação incompleta ou projeção inválida impedem confirmar o cursor.

Quando todas as datas do período já estão na fila de recaptura integral, consultas auxiliares adicionais não acrescentariam trabalho. O processamento registra explicitamente os intervalos dispensados e prossegue para essas capturas completas. O marcador ainda depende de todas as gravações e releituras SQL; dispensar uma busca auxiliar nunca aprova uma captura parcial.

## Banco e operação

O histórico permanece em `stg.production_export_occurrence`. A visão corrente continua sendo `stg.production_export_latest`, vinculada a `ctl.production_ingestion_coverage.latest_run_id`. Repetir100linhas gera uma nova fotografia histórica; a visão corrente mostra as100linhas da última fotografia confirmada.

Não há nova migração, credencial, concessão ou versão do SQL. Mantêm-se os modelos de39 e20campos, o catálogo de11entidades/508campos e a preservação de nulos e valores literais. As diferenças de apresentação do Excel são aplicadas apenas na comparação, conforme as políticas já qualificadas.

Os limites de memória, HTTP, duração, parada compartilhada e exclusão mútua do acionador0766 continuam vigentes. A espera é de60minutos **depois do término da execução inteira**, inclusive após reinício. Trabalho limitado pelo orçamento permanece pendente; um lote confirmado não significa que todo o histórico ou todos os marcadores já estejam em dia.

O runtime corrigido e seus manifestos ficam no armazenamento privado, fora do Git. O manifesto qualificado deve ser escolhido no terminal pelo operador. Esta implementação não registra tarefa, inicia supervisão contínua, executa a carga inicial de90dias ou liga o dashboard automaticamente.

## Verificação

Os testes incluem ausência de atualização própria com mudança de localização, fretes anteriores ao período de ocorrências, referências ambíguas/nulas, duplicidades físicas, falha de SQL, alteração de índice, orçamento parcial, retomada e migração de cursor, buscas diárias e reutilização indevida de fotografia anterior. Os recibos reais, comparações com Excel e resultado de entrega são registrados separadamente no checkpoint0768; resultados históricos divergentes permanecem preservados.
