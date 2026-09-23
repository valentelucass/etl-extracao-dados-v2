# MAT-01 — regras locais e contraprovas

Origem: procedures/001_criar_sp_carga_fato_gestao_vista_fretes.sql do V1;
regra MAT-01 em regras-negocio.csv; decisões ANA-02/09/11–13 no ADR0050.
O grão local é(run, Frete canônico, PE/CB); minuta é atributo. A data é partição
mutável. As fontes passam pelo pipeline6389/8656, suplemento tipado versionado,
termos financeiros e referências seladas. Não há fixture de fato final.

| Regra/caso | Resultado esperado independente | Prova física |
| --- | --- | --- |
| CT-e e NFS-e presentes | CT-e prevalece | FreightOperationalIT/canonicalIndicators e SQL02 |
| Somente NFS-e | NFS-E nos fatos; SQL02 retorna XML preservado | FreightFallbackIT/nfseFallback: XML positivo na campanha closing-gates02 |
| Valor120/documento emitido | PE e CB válidos | matriz16gates |
| Valor0, negativo, nulo ou0,01/documento emitido | PE válido; CB VALUE_THRESHOLD | matriz16gates |
| Valor0,01000001/documento emitido | PE e CB válidos | matriz16gates |
| Sem documento e valor0,01 | Ambos INELIGIBLE | matriz16gates |
| Substituto pendente sem documento | Ambos INELIGIBLE | matriz16gates |
| Pagador documento de filial, sem documento emitido | Ambos INELIGIBLE | matriz16gates |
| Mesmo pagador com documento emitido | Ambos válidos | matriz16gates |
| Pagador excluído da cubagem | PE válido; CB PAYER_EXCLUDED | matriz16gates |
| Cortesia, inativo, cancelado ou complementar | Ambos inválidos, motivo específico | matriz16gates |
| Previsão ausente | PE DATE_MISSING; CB pode concluir | matriz16gates |
| Finalização ausente | Fato EM ABERTO/0; SQL02 performance nula | FreightOperationalIT/dateCorrection; FreightLocationQueriesIT |
| Finalização oficial nanosegundos antes de meia-noite civil | Dia anterior preservado, sem arredondar | FreightOperationalIT/canonicalIndicators |
| Diferença negativa/zero/positiva | NO PRAZO/1 ou FORA DO PRAZO/2; faixas SQL02 de-4 a+4 | FreightLocationQueriesIT/nineSignedDayBands |
| M3 negativo | is_cubed=1 porque é diferente de zero | FreightFallbackIT/nfseFallback |
| Previsão do Frete nula e uma Localização vinculada | Fallback8656 e sua proveniência; volumes da Localização | FreightFallbackIT/locationFallback |
| Uma Localização repetida fisicamente | Um vínculo canônico e nenhum fan-out | Mesmo teste, captura com duplicata |
| Duas Localizações canônicas para um Frete | LOCATION_CONFLICT, sem escolha acidental | Mesmo teste, segundo vínculo |
| Correção da previsão | Mesmo grão; partição antiga no histórico, corrente na nova data | FreightOperationalIT/dateCorrection |
| Replay exato | Mesmo receipt retorna a reconciliação; outro receipt produz NOOP | FreightOperationalIT/canonicalIndicators |
| Mesmo receipt com entrada alterada | Recusa53585 | FreightOperationalIT/sameReceiptWithChangedInput |
| Escala fonte acima de8 | Mapper rejeita antes do suplemento/fato; captura registra quarentena | FreightFallbackIT/excessSourceAmountScale, tentativa06 passou |

Valores monetários são DECIMAL(28,8), BRL/MAJOR explícitos. No contrato sintético,
pesos publicados como Kg são quilogramas, total_cubic_volume/M3 é metro cúbico,
km é quilômetro, volumes é contagem e performance_days é diferença de dias civis.
Preservam-se bruto/tipo/presença; não se convertem unidades silenciosamente nem
se ratifica unidade do fornecedor real. FreightAttributesIT confere os90atributos
físicos e valores decimais, incluindo máximo Unicode; FieldGateTest isola cada
campo inválido. Zero, nulo e negativo mantêm disposições próprias.

Revisão local: verify-physical-analytic02 aprovado; closing-gates02 passou o
fallback XML. FreightPaths/LocationQueries, ScenarioRuntime/Plan/Isolation e
Concurrency exercitam os vínculos, previsão/finalização, correção, incompletude,
independência e consumo após rollback. Escalas4/16/32/16 e planos reais estão
registrados em final-scale-proof.json. O estado do JAR e da entrega A–N é o do
verification-summary.json; estas provas SQL não são aceite nominal de referência.
