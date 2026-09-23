# MAT05 — implementação local em verificação

Estado: IMPLEMENTADO_EM_QUALIFICACAO. Cenário sintético e sessão rollback-only;
identidade real, cardinalidade e aceite Frota/consumidores conservam os gates.
V072–078 instaladas; migrações anteriores permanecem imutáveis. V076 restringe
relações aos componentes atuais e passou na campanha mat05-time-alias02.
V078 papéis de veículos está em prova física própria.

| Regra | Comportamento executável | Proveniência |
| --- | --- | --- |
| Grão | Um run + Manifesto canônico; current aponta observação imutável | snapshot tipado92, receipt e lifecycle explícito |
| Competência MAN04 | Saída válida; criação somente por ausência/nulo. Fuso do run e nanos brutos preservados | competence_provenance, snapshot e field_audit |
| Estado MAN02 | closed > in_transit > pending na mesma coorte. Ativo vem de binding lateral versionado | state_id; origem sem flag não fabrica exclusão |
| Frete direto | Conjunto declarado, captura e cardinalidade seladas; selo ultrapassado bloqueia | composition_id e direct_binding_id |
| Frete por Coleta | Relações MC/CF resolvidas e crosswalk explícito para raiz Frete da expansão | mc_link_id, cf_link_id e crosswalk_binding_id |
| Receita MAN05 | Deduplica alvo canônico antes de somar; conjunto direto confere exatamente com agregado capturado | analytic_manifest_freight e analytic_manifest_freight_path |
| Financeiro local | BRL/MAJOR declarados nos termos; ausente/conflito/moeda/unidade divergente bloqueia. Zero/negativo são valores, inativo contribui zero | captura Fretes + termo; DECIMAL28,8, overflow recusado |
| Sobreposição | Fretes/Total e Coletas/Total preservam valores dos caminhos; Receita Total usa união. shared_revenue expõe a interseção | total = direto + coleta − compartilhado, sem tolerância financeira |
| Capacidade MAN06 | Trator obrigatório; cada reboque declarado requer binding/registro resolvido. Soma KG; zero válido, nulo/negativo/unidade divergente bloqueiam | três binding_ids, valores de registry e bruto6399 separado |
| Frota | OWNED_FLEET V008/V071, documento do registry do trator vinculado; matriz e exceções versionadas | release, ownership_provenance e TRACTOR_BINDING_REGISTRY_DOCUMENT |
| Exclusão | Binding inativo e placa excluída por referência retiram somente publicação atual | histórico preservado; nenhum sweep implícito |
| Replay | Mesmo receipt confere valores e conjuntos de proveniência. Nova execução com negócio igual é NOOP e avança ponteiro de proveniência | comparações de conjuntos, sem hash parcial ou concat ambíguo |
| Correção | Data/filial mutable no current; observação registra partição antiga e nova | old_reference_date/old_branch_key/prior_observation_id |
| SQL08/09 | 105/106 colunas ordenadas mais linhagem técnica; preparação leve de apresentação antes da leitura | manifesto-colunas-consumo.json, metadata SQL e IT |

Diferenças explícitas do legado: identificador composto de pick/MDF-e/hash não
define raiz; Identificador Único usa run/source_key. Não há MAX entre versões
nem soma do produto de filhos. Coleta/Número é apresentação do conjunto canônico
deduplicado, nunca identidade. Capacidade/placas e motorista pertencem aos bindings;
atributos brutos do fornecedor permanecem no snapshot/auditoria e colunas técnicas.
Metadata é JSON do snapshot tipado com sua proveniência, preparado durante a carga;
não é armazenamento substituto dos92campos ou payload arbitrário para o domínio.
SQL09 difere de SQL08 pela coluna Tipo de contrato key; ambas têm Local de
Descarregamento, conforme inventário completo105/106. A menção diferente no
checkpoint0099 era índice impreciso; os inventários de colunas foram preservados.

Provas em curso no target da rodada: physical-mat05-01 e02 preservam falhas
descobertas. A primeira revelou termos financeiros ausentes no cenário; a
segunda isolou alias de Coleta lido com wrapper incorreto e expectativa errada
de três observações após duas capturas completas. As correções recebem nova
campanha e não convertem as tentativas históricas em PASS.
physical-mat05-time-alias-02 passou13IT: MAT05/SQL08/09cinco, preparaçãoMANsete
e aliasColetaum. Zero falhas/erros/skips, DML revertido e contagens preservadas.

Revisão posterior: verify-physical-analytic02 passou com ManifestGates4,
FleetReferences3, Dimensions8 e demais classes de composição/receita/preparação.
Capacidade zero/nula, dois reboques, papéis sobrepostos/expirados, troca de papel,
cancelamento e dependência hidratada têm prova na camada física. ConcurrencyIT
executa a procedure MAT05 em SPIDs distintos e consome após rollback. ScaleIT
passou4/16/32/16 com planos reais. O JAR composto e a selagem A–N são declarados
separadamente no verification-summary.json, sem promover Frota nominal.
