# B63 — mapa temporal de Coletas

10/09/2026. Camada: leitura de código V1/V2 e provas sintéticas locais.
Base: ADR0023, decisão `coletas-v2-010/decisao-v01.json`, ADR0043 e B62/0057.
Decisão complementar: [ADR0044](../../adr/0044-coletas-temporal-local-sem-inferir-instante-de-status.md).
Nenhuma consulta de fonte/banco; V1 lido somente como código.

## Campos e consumidores

| Campo / seleção | Presença e parse | Natureza / zona | Frescor e consumo |
| --- | --- | --- | --- |
| V1 GraphQL `pick.edges.node.statusUpdatedAt` → DTO String → entidade `statusUpdatedAt`/`statusUpdatedAtEm` | DTO não distingue ABSENT de NULL. Bruto conservado; OffsetDateTime ou formatos locais no parser V1; parser local legado pode ajustar data/gap/overlap | Evento de status candidato; offset declarado ou America/Sao_Paulo. Persistência DATETIMEOFFSET(0) | Primeiro COALESCE do MERGE V1; bruto e normalizado expostos pela view ETL `013_criar_view_coletas_powerbi.sql` |
| V2 `/status_updated_at` → `freshnessRaw`, `freshnessAtUtc`/origin | Mapper preserva ABSENT/NULL/VALUE; vazio/inválido continuam VALUE no payload. Z/offset, ISO local e três formatos locais estritos. Gap/overlap sem offset deixam de ser instantes válidos | Evento de status, quando disponível e interpretável. Nunca extração | Primeiro na precedência ADR0023. **Proibido pelo gate do release corrente**, mesmo NULL; cenários somente no mapper/schema sintético B58 |
| V2 `/updated_at` | STRING/NULL/ABSENT no release corrente; conservado estruturalmente no payload. Não é interpretado pelo mapper nem consta do índice tipado `fieldPresenceJson`; sua presença é recuperável do payload | Última alteração da entidade candidata; sem equivalência demonstrada com evento de status | Não participa do frescor, não alimenta checkpoint. `by_updated_at` é filtro complementar, não prova da semântica deste campo |
| V1 `finishDate` / V2 `/finish_date` | V1 DTO String → LocalDate; V2 STRING/NULL/ABSENT; LocalDate ISO estrito com trim. Vazio, inválido ou tipo inadequado não fornecem fallback | Data civil de conclusão. V1 MERGE projeta meia-noite +00:00; V2 início válido do dia em America/Sao_Paulo | Primeiro fallback válido, inclusive quando o status não é terminal; não é instante de transição. V1 persiste DATE/exibe na view; V2 conserva payload e origem FINISH_DATE |
| V1 `serviceDate` / V2 `/service_date` | Mesma política de data civil; preservação V2 por campo/payload | Data de serviço, com a mesma diferença de projeção V1/V2 | Segundo fallback; V1 DATE, índices e filtros de serviço; V2 SERVICE_DATE |
| V1 `requestDate` / V2 `/request_date` | Mesma política de data civil; ausência de fallback válido resulta UNAVAILABLE | Data de solicitação; fuso IANA da partição V2 | Terceiro fallback; `picks.request_date` delimita extração/partição. Estratégia ColetasExecutionWindowStrategy e reconciliação V1 usam janela de negócio, não statusUpdatedAt |
| V2 `observedAt` / V1 `data_extracao` | Instante técnico do lote/captura, independente dos campos anteriores | Tempo de observação, não relógio da entidade | JDBC parâmetro 23 e auditoria/last_seen; nunca substituto do frescor (parâmetro 19) |

V1: `ColetaNodeDTO`, `ColetaEntity`, `ColetaMapper`, `ColetaStatusTimestampParser`,
`ColetaRepository`, `GraphQLQueries`, `GraphQLColetaSupport` e view/tabela ETL.
Localização relativa: `../etl-extracao-dados/src/main/java/br/com/extrator/`;
views/tabelas em `../etl-extracao-dados/database/`. Nenhum dashboard foi aberto.
GraphQLColetaSupport compara candidatos por status/timestamps/datas; esse apoio
legado não é o reducer do V2 nem oráculo nominal. A coluna V1 `updated_at`
não integra esta seleção mínima de Coletas; não inferir correspondência pelo nome.

## Precedência e promoção reais

1. Mapper: primeiro timestamp de status válido; senão finish, service, request.
   Não usa o maior timestamp entre campos. Preserva o bruto de status inválido
   em freshnessRaw mesmo quando a origem vencedora é uma data civil. O bruto
   do fallback está no payload: freshnessRaw não é necessariamente o valor vencedor.
2. JDBC: envia o mesmo instante ao staging, com Calendar UTC. V010 repassa-o ao
   kernel genérico como `source_freshness_at_utc`. V010/generic usam DATETIME2(3).
3. V004 `core.usp_prepare_staged_execution`: máximo frescor por chave; assinaturas
   diferentes no máximo geram EQUAL_FRESHNESS_CONFLICT, ou UNKNOWN_FRESHNESS_CONFLICT
   se todos NULL. Ordinal escolhe representante só após recusa de conflitos;
   não é precedência de negócio. Mesmo payload/replay pode ser deduplicado.
4. V010 `ctl.trg_coleta_prepare_candidate_set`: bloqueia **qualquer** chave com
   mais de um attribute_hash no lote, mesmo em horários diferentes. Duas linhas
   pending/done na mesma data não recebem vencedor inventado. Expansão com
   atributos iguais é compatível; candidato relacional diferente não vira filho.
5. V010 aplicação chama primeiro o kernel comum. V004 recusa empate de frescor
   com conteúdo diferente (51428), permite mais novo e identifica stale.
   Só depois o plano tipado faz aberto→terminal vencer stale e impede
   terminal→aberto. Hash igual é NO_OP. Portanto a prioridade terminal **não
   remove** conflitos anteriores: mesma data/pending→done pode ser bloqueada,
   e terminal retroativo dentro do mesmo lote não é automaticamente promovido.
6. `ABSENT` em status/alias preserva atributo corrente na promoção; campo NULL
   não equivale a ABSENT. `freshness_raw` usa COALESCE, e UNAVAILABLE conserva
   frescor persistido. Payload/última observação e estado materializado são
   representações diferentes. Não alegar merge parcial universal de todo JSON.

Isso é leitura estática, **não prova de execução SQL**. O mock JDBC verifica
parâmetros/calendário/transação simulada; não executa trigger, dedupe ou commit real.
O guard conservador protege contra ordem inventada, mas pode impedir convergência.
Não anunciar a regra terminal como comprovada em todos os caminhos.

## Classificação e contraprovas

| Regra / classe | Caso discriminante | Resultado e ação |
| --- | --- | --- |
| COL-TIME-01 / lacuna de fonte | 6908 ABSENT; GraphQL statusUpdatedAt presente nas sete linhas B62 | Paridade temporal pendente; nunca preencher por updated_at/finish_date |
| COL-TIME-02 / defeito de implementação | 2018-11-04 00:30 (gap); 2019-02-16 23:30 (overlap), sem offset | Antes atZone inventava/escolhia instante; correção exige exatamente um offset, conserva bruto e usa fallback |
| COL-TIME-03 / diferença intencional local | finish_date=2026-09-09: V1 00:00Z, V2 03:00Z | Manter zona V2; diferença externa não aceita. Data civil 2018-11-04 tem início válido 03:00Z |
| COL-TIME-04 / limite de camada | .123100Z e .123400Z são distintos em Java; DATETIME2(3) não representa ambos | Preservar nanos em Java; provar conversão/empate futuramente, sem truncamento para obter igualdade |
| COL-TIME-05 / convergência SQL pendente | Mesma chave/data pending e done; ou done antigo/pending novo no mesmo lote | V010 bloqueia múltiplos hashes; kernel pode recusar empate entre execuções. Não relaxar conflito/reducer neste bloco |
| COL-03/04 / contrato de status | done=Coletada, finished=Finalizada; canceled/cancelled=Cancelada; unknown sem terminal inferido | Preservar catálogo e prioridade de ação; motivo não vazio após done/finished |
| R-B62-REPLAY-01 / cobertura | Páginas expandidas/repetidas, interrupção antes de vazio terminal | Staging parcial não gera sucesso total/snapshot. Página terminal tampouco prova representatividade |

Propostas SQL/externas concretas e casos de seleção constam do
[pacote de prova](PROVA-REPRESENTATIVA.md). A conclusão A é o mapa e a decisão;
equivalência de fonte, convergência física e aceite nominal permanecem pendentes.
