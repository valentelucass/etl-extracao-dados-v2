# B63 — delta acionável de COL-TIME-01 / Q-COL-01

PREPARADO_LOCALMENTE_ORACULOS_PENDENTES. Complemento exclusivo de Coletas à
[matriz B61](../../runbooks/bloco61-matriz-paridade.md). Não autoriza fonte, SQL,
novo orçamento ou sidecar. Não há chamada reservada nem campanha vigente.
Bindings descobertos constam de [inputs-prova.json](inputs-prova.json).

## Captura e campos mínimos conhecidos

6908: GET `/api/analytics/reports/6908/info` e `/data`, contrato
`2026-09-10.b62-coletas-scalar-capture.1`, ROOT_ARRAY, fingerprint
`b143864a5bbde9ccc3f36f488cbcf0d67de1034743987104b793736f00beefe9`.
Preservar cada linha física original, incluindo os 31 campos permitidos; os
campos comparados são id, sequence_code, status, request_date, service_date,
finish_date e cancellation_reason. Observar separadamente updated_at e created_at
como valores brutos, sem usá-los como instante de status. Registrar a ausência
de status_updated_at; não o acrescentar ao release para viabilizar comparação.
`pck_mik_mft_sequence_code` é candidato relacional preservado, não relação provada.

Filtros conhecidos: `search[picks][request_date]` = intervalo de datas inclusivo;
`page`, `per`, `order_by=sequence_code asc`. `search[scopes][by_updated_at]` é
complementar e precisa de limites temporais próprios e equivalência de filtro
ratificada. Metadados usam `by_updated_at`, sem prefixo scopes. Não converter
a última página ou updated_at em watermark. Critério de corte entre dias deve
ser registrado explicitamente, não inferido de um timestamp convertido em UTC.

GraphQL: `/graphql`, query estática PickInput já usada pelo V1/B62. Candidata
abaixo é **texto para revisão, não comando nem nova seleção operacional**:

```graphql
query ColetasTemporalReference($params: PickInput!, $after: String, $first: Int!) {
  pick(params: $params, after: $after, first: $first) {
    edges { node {
      id sequenceCode status requestDate serviceDate finishDate
      cancellationReason statusUpdatedAt
    } }
    pageInfo { hasNextPage endCursor }
  }
}
```

Forma conhecida do filtro: `params.requestDate` como dia. Para intervalo,
especificar dias/coortes autorizados; não inventar sintaxe de intervalo GraphQL.
Não herdar first=100 como garantia: B62 observou 20 nós apesar de first100.
Nunca decidir terminalidade pelo tamanho da página; usar pageInfo, sem persistir
cursor na evidência pública. GraphQL não fornece updatedAt nesta seleção.

## Seleção e expectativas a ratificar

| Caso | Seleção/observação requerida | Prova discriminante / resultado esperado |
| --- | --- | --- |
| T01 evolução | Coletas observadas pelo menos em dois instantes, com transição conhecida pending/treatment/manifested/in_transit→done/finished/canceled | Mesmo ID e scope, status e timestamp de evento independentes. Data civil igual não ordena eventos. Ausência 6908 permanece divergência temporal |
| T02 terminais | Exemplares done, finished, canceled e cancelled; motivo vazio/nulo/não vazio | Coletada ≠ Finalizada; ambas terminais e ação Coleta Realizada. Motivo tem precedência sobre ação genérica de cancelamento, sem apagar status bruto |
| T03 presença | Ausente, NULL, vazio, inválido e válido em cada campo temporal onde observável | Separar ausência de tipo/parse; registrar campos não observados como lacuna. Não fabricar casos reais a partir das sete linhas B62 |
| T04 fronteiras | Solicitações/serviços/conclusões dos dois lados da meia-noite local, offset explícito e histórico de DST se disponível | Dia de negócio e instante são comparados separadamente. Fallback V1 UTC versus V2 São Paulo é diferença conhecida, sem tolerância automática |
| T05 gap/overlap | Evidência histórica com offset conhecido, ou registro nominal de inexistência na amostra | Sem offset, gap/overlap é não interpretável; offset explícito determina instante. Teste sintético cobre algoritmo, não prevalência na fonte |
| T06 empate | Mesmo ID/dia com status divergentes e eventos coincidentes ou sem instante | Conflito explícito. Não aceitar último recebido/hash. A data civil sozinha não decide done versus canceled |
| T07 ordem inversa | Mesmas observações em ordem direta/inversa e repetições entre páginas | Mesmo resultado de parsing/presença; staging conserva linhas. Dedupe/promoção física é outra camada, com gates V004/V010 |
| T08 precisão | Eventos distintos abaixo de segundo/milissegundo, se a fonte os fornecer | Medir precisão declarada e real; separar igualdade de Instant de igualdade após persistência V1/V2. Perda de precisão não vira paridade |
| T09 expansão | IDs com várias linhas físicas, repetidos ou com candidatos de Manifesto diferentes | per limita IDs por página, não linhas; contar raízes/linhas separadamente. JSON preservado; não inferir cardinalidade relacional |
| T10 travessia | Captura limitada e, quando autorizada/possível, terminal observada em cada canal | Cap/falha não gera sucesso total; terminal não prova snapshot nem representatividade. Igualdade global só com oráculo de população/escopo |

Escolher a menor janela/coorte que reúna casos conhecidos pelo owner. O dia
09/09/2026 do B62 é só referência histórica, não seleção representativa ratificada.
Registrar para cada T01–T10: observado/não observado/não aplicável com justificativa
nominal, fonte, instante da leitura, resultado esperado independente e responsável.
Se a janela não contiver os casos, encerrar no limite e registrar a lacuna; não
aumentar janela/teto nem chamar novamente por iniciativa do harness.

## Fonte em movimento versus defeito do mapper

Congelar os bytes efetivamente capturados e o hash da revisão executada em meio
privado autorizado (ou em memória, se retenção não for autorizada). Reexecutar
somente o parser/mapper local sobre os **mesmos** bytes é determinístico; se o
resultado viola o esperado manual independente, é defeito local. Hash público
é apenas de artefato técnico sanitizado, nunca de ID/payload de negócio.

Comparação entre canais requer instantes/faixas de captura e coorte comum. Se
status/evento mudam entre leituras, registrar SOURCE_CHANGED_BETWEEN_READS apenas
com evidência de versão/evento ou observações que delimitem a mudança. Uma coleta
com o mesmo status antes/depois ainda pode ter alterado outro atributo. Sem
snapshot/versão/event log/oráculo independente, classificar UNRESOLVED_SOURCE_RACE;
não culpar mapper nem declarar equivalência. Uma futura observação antes/depois
exige autorização e orçamento próprios; este pacote não fixa número de chamadas.

Comparar id 6908 INTEGER com id GraphQL de tag explícita via correspondência
nominal no mesmo source_instance/tenant_scope. sequence_code é auxiliar para
pareamento, nunca chave técnica; rejeitar ambiguidade/colisão. B62 comparou
representação de IDs de fonte, não surrogate canônico do registry SQL.

## Harness e impedimentos concretos

Reusar ColetasCharacterization.pipeline/LocalCharacterization.Pages com release
corrente e `DataExportContractObservationConfiguration.forRelease`. O gate recebe
o array original; envelope B58 continua fixture histórica. Q-FND Coletas tem
fingerprint antigo, recordRoot `/data/*` e BOUNDARIES_UNPROVEN: executar esse
perfil não valida o release atual. Preservá-lo; ligação futura ao input autorizado
precisa registrar explicitamente versão de contrato/identidade/configuração e
reusar evaluator/writer aplicáveis, sem forjar perfil aprovado.

Tetos: 65.536 bytes/entrada, 1.000 linhas, 100 páginas, profundidade16, 256 paths,
4.096 nós; per≤100 IDs, microbatch≤100. Aplicar o menor limite entre autorização,
contrato e harness. São tetos técnicos, **não orçamento de chamadas**. Captura B62
é encerrada, test-only, tem control plane de laboratório e não validou binding
operacional. Não reexecutar a sonda/replay real. Os corpos reais não foram retidos.

Faltam somente inputs externos reais: autorização nominal vigente por canal;
source_instance/tenant_scope efetivos e correspondência de identidade; janela e
tetos aprovados; oráculo/artefato e expectativas por caso com owner/aceitante;
atestado sanitizado e aceite de Segurança V2-041 aplicáveis; garantia temporal
da fonte ou aceitação nominal das diferenças. Paths, seleções e versões já
preenchidos não precisam ser pedidos novamente ao usuário.

## Propostas revisáveis, sem execução

**SQL/precisão e convergência:** alvo candidato é somente o schema V2 shadow,
em futura autorização física explícita. Preservar V004/V010 e baseline atuais.
Exercitar rollback-only os pares T06–T08 no `stg.usp_stage_coleta_record`,
`core.usp_prepare_staged_execution`, trigger Coletas e aplicação Coletas:
replay idêntico; timestamps mais novo/velho; .123100/.123400; empate
pending→done; done retroativo; done→pending posterior; dois terminais divergentes.
Conferir candidato genérico, estado tipado e frescor efetivo em conjunto.
Resultado esperado corrente inclui BLOCKED/51428 nos conflitos; nenhuma promoção
física foi comprovada aqui. Se o owner exigir convergência de transição no mesmo
dia, uma migration nova deve especializar a política de Coletas na preparação
e aplicação comum/tipada sem impacto nas outras entidades, mantendo conflito de
mesmo instante/terminais incompatíveis. Não simplesmente remover o guard 51428
ou escolher último/hash. Dependências: oracle temporal e decisão nominal,
aprovação de schema, testes transacionais/concorrência/baseline. Recuperação:
rollback da prova; revisão nova e plano de reversão antes de implantação, sem
reprocessar ou apagar histórico neste bloco.

**Complemento GraphQL, caso necessário:** manter os oito campos acima em canal
independente com scope/tag/correspondência de ID, presença, origem, data de captura
e versão de seleção. Proibir COALESCE silencioso entre os canais. Join somente
em staging/SQL set-based futuro, com conflito/quarentena e limites próprios;
nenhum sidecar operacional criado. Remover a ponte quando o fornecedor oferecer
instante de status equivalente em release versionado do6908, com prova repetida
e aceite nominal. Sem esse marco, não prometer prazo de remoção inventado.

Preparação C fecha apenas o pacote local. COL-TIME-01, Q-COL-01, V2-012a/b/c e
V2-041 permanecem abertos; não habilita bootstrap, snapshot, sweep ou produção.
