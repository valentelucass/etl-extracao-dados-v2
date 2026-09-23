# ADR 0045 — referência temporal local de Coletas com proveniência

10/09/2026. Decisão técnica adotada pelo pedido “pode aplicar nesse chat mesmo
seguindo states.md”. Complementa ADR0023/0044 e checkpoint0063. Responsável
técnico: manutenção local de Coletas. Aceitante nominal de negócio: pendente.

## Problema e decisão

A prova de fonte encerrada5/5 confirmou ausência de status_updated_at no6908.
A seleção GraphQL histórica de Coletas tampouco pede statusUpdatedAt.
Preservar os documentos históricos e acrescentar a operação estática
PICKS_TEMPORAL_REFERENCE, versão2026-09-10.coletas-temporal.1, first máximo20:
id, status, statusUpdatedAt, requestDate e os dois campos pageInfo.
O ledger próprio graphql-coletas-temporal.csv fixa finalidade, owner-papel,
gate de saída e remoçãoV2-040. Continua OBSERVATION_ONLY/SYNTHETIC_ONLY;
a implementação nova foi qualificada localmente, não na fonte operacional.

**COL-TIME-05 — complemento é uma observação distinta.** Preservar source key
com tag INTEGER/STRING, source_instance, tenant_scope, execução, janela,
versão/seleção, página/ordinal, captura, presença e bruto dos campos selecionados.
O mapper aceita como instante GraphQL somente ISO com offset explícito ou Z;
mantém nanos. Ausência, null, tipo errado e texto inválido não viram instante.
Esta seleção nova adota contrato conservador de offset, separado dos formatos
locais históricos aceitos pelo mapper Data Export em COL-TIME-02.
Exemplo: 17:00-03:00 corresponde a20:00Z;17:00 sem offset fica inválido.

**COL-TIME-06 — validar um par explícito, sem cruzamento de massa na JVM.**
ExtrairReferenciasTemporaisColetas usa gate, streamer, caps e cancelamento
existentes. Entrega uma observação por callback síncrono; não acumula páginas.
LigarReferenciaTemporalColeta recebe linha de um batch6908 e referência já
pareadas, com ColetaTemporalIdentityBinding fornecido pelo futuro cruzamento
SQL. O binding exige entidade/escopo iguais, IDs tipados distintos e referência
à evidência de correspondência. Não converte INTEGER:17 em STRING:17,
não usa sequenceCode e não infere ligação por semelhança dos IDs.

A execução6908 do batch e a execução GraphQL da observação devem corresponder
ao binding; o chamador SQL continua responsável por recuperar o batch6908
no source_instance/tenant_scope comprovados, pois ColetaStageBatch não contém
esses namespaces. Um fingerprint sintético identifica uma hipótese de teste;
não qualifica um crosswalk real. Janela consultada, request_date6908 e
requestDateGraphQL devem concordar. Status conhecidos normalizados pelo catálogo
vigente devem ser iguais; done e finished continuam códigos distintos.
Divergência de status, identidade, execução, janela ou contrato bloqueia o
candidato. Status igual em duas leituras não exclui transição intermediária
(ABA) nem prova atomicidade de fontes; isso permanece na qualificação real.

**COL-TIME-07 — resultado tipado não altera o significado do6908.**
O resultado conserva a linha6908 original e a referência; não injeta
status_updated_at no payload/presença6908, não troca seu fallback e não é
ColetaStageRecord consumível pelo JDBC antigo. Quando o instante nativo6908
for válido, preservá-lo; divergência com referência válida é conflito.
Sem instante nativo nem referência utilizável, não emitir candidato temporal.
Datas civis, updated_at e observedAt não substituem o evento.

Não deduplicar pares ou escolher vencedor por chegada/ordinal/hash.
Nanos distintos continuam distintos, inclusive quando ambos arredondariam ao
mesmo DATETIME2(3). V004/V010, erro51428 e antirregressão ficam preservados.
O contrato GraphQL bloqueia promoção mesmo após travessia terminal.

## Consequências e integração futura

Novo membro do catálogo altera o fingerprint agregado de configuração GraphQL;
documentos/fingerprints individuais históricos continuam iguais.
Bindings operacionais antigos não devem ser silenciosamente regenerados.
Não há composição no Main/RuntimeCompositionRoot, gateway SQL do complemento,
migration, nova policy ou ativação. A entrega implementa captura e validação
unitária local, exercitadas juntas com parser/gate/streamer sintéticos.

Para persistir e promover este tipo: contratar staging próprio com ambas
proveniências, recuperar correspondências escopadas e tratar cardinalidade,
duplicatas e conflitos set-based no SQL; só então ligar o resultado à promoção
sob bindings qualificados. O resultado Java isolado não autoriza esse efeito.
A captura parcial é evidência parcial: falha de página/consumidor/cancelamento
invalida o guard; o consumidor futuro não pode promover antes de sua conclusão.
Página vazia mantém a recusa conservadora do streamer existente.

Testes: ColetaTemporalLinkTest, ExtrairReferenciasTemporaisColetasTest,
GraphQlTransitionCatalogTest e regressões existentes de Coletas/GraphQL.
Relatório e recibos próprios em docs/catalogos/coletas-temporal-link/ e
target/coletas-temporal-link-20260910/. Sem novo bloco, orçamento ou aceite.

Rollback: reverter apenas os deltas desta manutenção conforme snapshots e diff;
retirar os arquivos novos do caminho de execução, preservando as evidências.
Não tocar em migrations aplicadas, fontes ou dados.
