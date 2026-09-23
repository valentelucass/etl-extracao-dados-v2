# MAT-02 — Coletores por contribuição canônica e recorte

Origem: regra MAT-02 e procedure legada de carga de Coletores; ADR0050.
V067 materializa contribuições de MAN e INV, depois agrega por data de negócio,
filial e classificação Geral. Os IDs canônicos são definidos por bindings;
repetições físicas/filhos não aumentam entidades. V094 protege a procedure
com contenção e recuperação transacional testadas em sessões reais.

| Regra | Mecanismo e disposição | Prova SQL/JDBC |
| --- | --- | --- |
| Emitidos/descarregados | Uma contribuição por raiz MAN e tipo; filial por papel vigente | CollectorsIT/physicalExpansion, valores manuais1/1 |
| Exclusões | Prefixos governados Carga Fechada, Acerto de Motorista, Frete Retorno, Viagem Vazia | CollectorsGatesIT quatro parâmetros: OPERATION_EXCLUDED nas duas contribuições |
| Inventário | Cohort atual da raiz, duplicata não conta nova raiz | CollectorsIT e GatesIT seis tipos, componentes físicos distintos |
| Tipos | Picking, Return, Receipt, Loading, Unloading; prefixo CheckIn::Order:: normalizado | Seis positivos independentes e tipo não permitido TYPE_EXCLUDED |
| Incompleto | finished_at nulo com tipo permitido e started_at válido | scanned1/incomplete1; completado no cenário complementar |
| started_at ausente | STARTED_DATE_MISSING; não fabrica data | GatesIT condição DATE |
| Conflito de raiz | Atributos comuns divergentes no mesmo cohort bloqueiam | GatesIT condição CONFLICT, INVENTORY_ROOT_CONFLICT |
| Filial direta | Binding vigente de INV, sem resolver por nome lexicográfico | CollectorsIT, DimensionsIT e GatesIT condição BRANCH |
| Fallback | Texto de filial ausente e um Frete canônico resolvido; binding e dependency_id retidos | GatesIT/freightFallback; segundo alvo torna FREIGHT_FALLBACK_AMBIGUOUS |
| Matemática | total=issued+unloaded; scanned*100/total em DECIMAL(28,8), zero no denominador zero | Valores manuais100 e0, sem tolerância financeira genérica |
| Correção | União das partições antigas/novas; antiga vazia fica inativa | CollectorsIT/changedDateAndUnloadingBinding; cenário BACKFILL/REPLAY |
| Idempotência | Mesmo receipt exige conjunto idêntico; novo receipt registra NOOP/UPDATE | CollectorsIT e GatesIT/reusedReceipt: erro53625 sem nova observação |
| Completude/escopo | MAN preparado e INV completo; vazio completo aceito, captura ausente53622, FULL estreito53621 | GatesIT/emptyCompleteInputs; ScenarioPlan/Isolation |
| Full e ausência | Janela inteira explícita, inclui registro antigo; carga não aplica Sweep | ScenarioRuntimeIT, CollectionSweepIT |
| Concorrência/escala | Trava MAT02 efetiva e consumo após rollback;4/16/32/16 | ConcurrencyIT e ScaleIT/planos reais |

CollectorsIT2 e CollectorsGatesIT17 passaram; os17 novos casos foram executados
na campanha physical-analytic-closing-gates02 após o verify integral aprovado.
Fontes passaram pelos pipelines e preparação; nenhum agregado final foi inserido
para montar o oráculo. Moeda não participa da contagem; percentual é adimensional
e quantidades são contagens. A atribuição de filial continua política sintética
explícita, sem aceite empresarial nominal. JAR/entrega final têm estado separado
no verification-summary.json.
