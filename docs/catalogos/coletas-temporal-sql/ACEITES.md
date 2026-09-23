# B63 — critérios conferidos no fechamento técnico

O bloco adotado é o prompt-bloco-63-semantica-temporal-coletas.md, frentes A–D.
Os pedidos posteriores acrescentaram investigação real controlada e ligação
temporal local. Esta tabela separa seus resultados dos critérios do roadmap.

| Critério | Evidência e resultado | Situação |
| --- | --- | --- |
| A: mapa e decisão temporal | MAPA-TEMPORAL.md do B63 local; ADR0044/0045/0046. Eventos, datas civis, fonte do frescor, fuso, perda de precisão e consumidores identificados. | Concluído no escopo adotado |
| B: defeitos e regressões locais | Correção de gap/overlap; parser/gate6908 corrente; captura, ligação unitária e staging complementar. Verify1556/0/0/4. | Concluído no escopo adotado |
| C: inputs e critérios representativos | PROVA-REPRESENTATIVA.md do B63 local e matriz B61; delta operacional de seleção e evidências abaixo. | Preparação concluída; aceite externo não concedido |
| D: testes e entrega | Relatório, diff próprio, sucessão0067→0068→0069, manifesto, checks e recibo desta fase. | Conferir checks finais PASS e recibo |
| Staging/cruzamento SQL complementar | Captura→porta JDBC, proposta SQL própria,34 casos físicos transacionais revertidos. SQL calcula cardinalidade, conflitos e contagens. | Implementado e testado nas camadas declaradas |
| Exclusão concorrente do complemento | Uma segunda sessão não obtém a trava mantida pelo staging; obtém após rollback. | Comprovado para a trava; não é corrida promocional completa |
| Equivalência temporal dos canais/COL-TIME-01 | Fonte real mostrou timestamp ausente6908 e presente GraphQL. Nenhuma mudança real de status observada nas leituras. | Não comprovada; divergência documentada |
| Q-COL-01/V2-012a | Amostra real limitada, sem oráculo independente ratificado nem aceite nominal de representatividade. | Pendente externo |
| V2-012b/c e V2-041 | Esta rodada não testa outras entidades, rotação/invalidação de chaves ou continuidade de credenciais. | Sem novo aceite |

## Pacote representativo pronto

Fontes e campos: release6908 ROOT_ARRAY com id/status/request_date e os campos
temporais já contratados; referência estática PICKS_TEMPORAL_REFERENCE,
2026-09-10.coletas-temporal.1, id/status/statusUpdatedAt/requestDate/pageInfo,
first máximo20. Fingerprint e scopes devem ser os da execução efetiva, nunca
regenerados silenciosamente a partir de bindings históricos. O SQL aceita
explicitamente fonte e tenant da execução6908, tag de ID e evidência do crosswalk.

Selecionar uma janela que contenha transições verificáveis pendente→done,
pendente→finished, cancelamento e estados abertos. Incluir nulo/ausente/inválido,
bordas de data/fuso, empate, ordem inversa e expansão/repetição da mesma raiz.
Os testes sintéticos cobrem esses discriminantes nas camadas pertinentes;
eles não demonstram a frequência nem a cobertura desses casos na fonte real.

Para cada transição real, o oráculo deve conter estado e instante esperados,
origem independente dessa expectativa, identidade escopada e vínculo às duas
capturas. A simples igualdade do status não exclui ABA ou mudança intermediária.
Ausência do instante6908 é uma diferença esperada, não defeito a ocultar. Candidato
GraphQL deve ser classificado como complemento, sem alegar paridade dos canais.

Evidência ainda necessária: janela com esses casos identificados por fonte
independente, correspondências reais qualificadas e responsável nominal pelo
aceite das expectativas/representatividade. A documentação atual fornece campos,
contratos, limites e regras; não contém esses fatos nem permite fabricá-los.
Teto/autorizações futuros precisam corresponder à rodada efetivamente adotada.
Rodadas anteriores encerradas5/5 não fornecem saldo. Não há nova chamada reservada.

Antes de ativar qualquer consumidor promocional: obter os aceites próprios,
integrar a projeção sob bindings qualificados e preparar migration/baseline
da ativação. A proposta SQL deste fechamento não altera os procedimentos
produtivos ou a autorização de promoção. Isso pertence à ativação futura,
não constitui uma nova frente pendente do B63 local.
