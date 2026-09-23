# Contrato da construção local integrada

Este contrato implementa mecanismos sintéticos. Não aprova identidade ESL,
cardinalidade real, política fiscal, corte ou os pais V2-036/V2-037.
A [matriz dos 151 campos](MATRIZ-CAMPOS.csv) identifica cada path, proveniência,
tipo, presença, destino e consumidor. Nenhum path do recorte ficou omitido;
tipos não observados continuam classificados como não resolvidos na origem.
O release interno expansion-synthetic-v1 permite exercitar o mecanismo sem
transformar metadados laterais em campos oficiais de /data.

## Captura, grão e frescor

Cada observação tem execução e ocorrência física. Esse endereço não identifica
uma entidade. O binding lateral declara root/part/component, revisão, evidência,
moeda/unidade, aditividade, ativo e reativação. Chaves STRING e INTEGER são
distintas, conservam caixa e rejeitam padding. Raízes/filhos são escopados pelo
run e pela vertical. Sem binding, a observação fica UNBOUND e não promove.
CAP não ganha id de raiz ESL; parcela repetida não serve como chave da raiz.
FAT.id continua candidato da linha; título e documentos vêm de vínculos
sintéticos explícitos. Arrays são sequências físicas de strings, incluindo
ordem e duplicatas, sem identidade documental derivada. INV conserva candidato
de minuta separado de raiz; SIN conserva sequência, minuta e ocorrência.

ABSENT, NULL e VALUE, tipo de wire, raw e valor tipado são persistidos
separadamente. DECIMAL(28,8) recusa overflow e arredondamento. IDs integrais
preservam wire STRING/INTEGER sem inferir equivalência. Instantes armazenam
segundo+nano; horas conhecidas armazenam nanos do dia. Datas civis usam
America/Sao_Paulo, inclusive DST. customer_communication_time permanece texto
com formato de origem não ratificado; occurrence/finished têm hora tipada.

CAP usa máximo de criação e fronteiras civis de transação/liquidação. FAT usa
máximo das quatro datas civis e do instante CT-e. Fronteira civil final é o
início exclusivo do próximo dia, sem 23:59:59. INV usa prioridade performance,
finished, started; comprovante é cumulativo, inclusive evidência antiga válida.
SIN usa tratamento, senão início civil de abertura. Dedupe e aplicação usam
as mesmas expressões. Empate divergente vira quarentena; correção exige frescor
ou revisão que supere a evidência pendente. Reativação precisa ser explícita.

A captura usa a janela inteira do dia e termina com página vazia auditada.
Fonte/tenant/fingerprint/modo/replay/janela e contagens devem coincidir antes da
selagem. Incompleta/cancelada/falha não alimenta corrente válido. CAP envia
issue_date e created_at na captura e no oráculo. Transporte aceita no máximo
64 KiB por página; política limita páginas, linhas e lotes até 100. O runner
usa página até 16. JDBC envia batches reais para observação, campos e arrays.
SQL mantém dedupe, joins, reduções, fila, materializações e totais.

## Relações, referências e retomada

FAT_DOCUMENT_FREIGHT, INV_FREIGHT, SIN_FREIGHT e LOC_FREIGHT exigem evidência
lateral. Alias de minuta, igualdade de documento e posição não criam vínculos.
1:1 limita ambos os lados; 1:N limita origem por destino; N:N conserva arestas.
Revisão antiga fica SUPERSEDED. A fila agrupa por alvo explícito, limita claim
a 100, lease a 60 segundos e tentativas a três. EMPTY, TEMPORARY, INVALID,
CONFLICT e ABANDONED são distintos. Hidratação extrai somente o alvo necessário
pelo pipeline existente, com auditoria e selagem, e resolve novamente em SQL.

Referências reutilizam registro/selagem V2-035a: labels, calendário, filial e
atribuição de pagador. Releases são imutáveis, selecionados por run/revisão e
vigência exclusiva. Falta ou sobreposição bloqueia o consumidor afetado.
O pagador é um literal sintético declarado, sem hash inferido de dado real.
Termos financeiros laterais de Frete declaram data, classificação, cortesia,
elegibilidade, fallback de volumes, pagador e unidade/moeda. Não contêm receita
final; o valor vem do Frete capturado. O financeiro operacional anterior
continua com sua própria restrição aritmética, sem promoção desta política.

Planos BOOTSTRAP/INCREMENTAL/BACKFILL/REPLAY persistem até 31 partições, seis
slots por partição e dois recibos. Reabrir adapters lê o SQL. Captura concluída
antes de attach é reconhecida sem nova extração. Falha de captura reverte seu
savepoint; falha do plano registra categoria/fronteira quando a transação ainda
permite escrita. A primeira lacuna permanece visível com partições inversas.
Replay aponta explicitamente para uma revisão BOOTSTRAP completa. Nenhum
watermark produtivo avança. A prova é reidratação SQL na mesma transação;
COMMIT/crash e dados persistidos entre invocações ficam fora deste laboratório.

## Materializações e reconciliação

MAT04 tem uma linha por título sintético. Datas são atributos; corrigir emissão
atualiza a mesma chave. Reduz atributos de título por igualdade entre componentes,
sem somar expansão. Operacional usa fit_ant_value, senão total, senão zero.
Placeholder não significa faturado; ambas as evidências fiscais são preservadas,
e caso dual sem política retorna UNRESOLVED. As políticas sintéticas CT-e e
NFS-e exercitam alternativas, sem default real. Aging usa business_date injetada.
Full conserva data nula e disposição de bloqueio; incremental visita capturas
afetadas e datas antigas. Observações ausentes não provocam exclusão.

MAT03 tem uma linha por Frete explicitamente vinculado. Múltiplos documentos
não multiplicam receita. Calendário ajusta a data, e pagador exige atribuição
na filial da revisão selecionada. Volumes usam Localização vinculada, senão o
fallback lateral. Cancelamento conserva proveniência; bloqueio exige “bloqueio”
e “anulação” ou “isolamento”. Cortesia, inelegibilidade, inatividade, cancelamento
e data nula geram zero com termos válidos; desconhecido permanece nulo com
motivo. READY é a receita elegível. Outras disposições entram em blocked do
recibo, inclusive exclusões conhecidas de zero, sem significar erro SQL.

Observações = inserts + updates + noops + stale + quarantine + unbound +
duplicates; dependências usam a mesma equação sem unbound. Recibos financeiros:
candidatos = inserts + updates + noops = ready + blocked. O status calcula
raízes, componentes, vínculos, faltas, conflitos, fila, capturas e essas equações
em SQL. Valor financeiro é somado somente em grão distinto com moeda/unidade
explícitas; valores de raiz/parcela repetidos nas consultas de componente são
atributos não aditivos. Componentes inativos não participam da redução corrente.

## Seis consultas JDBC

A ordem de paginação é o cursor técnico abaixo, crescente e exclusivo. Esses
cursos não são identidades de negócio. Os métodos validam limites antes de I/O,
usam TOP parametrizado e recusam overflow. A API devolve records tipados,
nullable quando o SQL declarar nulo, com BigDecimal, LocalDate, LocalTime,
BigInteger/strings integrais e linhagem explícita. O runner imprime somente
contagens e motivos; a inspeção programática retorna uma página sintética.

### CAP: componente corrente CAP

SQL: pub.ufn_expansion_cap; JDBC: detailPage; ordem/cursor: component_id.

| Ordem | Coluna SQL | Tipo | Nullable |
| --- | --- | --- | --- |
| 1 | run_id | uniqueidentifier | não |
| 2 | root_id | bigint | não |
| 3 | vertical | char(3) | não |
| 4 | root_type | varchar(8) | não |
| 5 | root_key | nvarchar(64) | não |
| 6 | currency | char(3) | não |
| 7 | unit | varchar(16) | não |
| 8 | root_amount | decimal(28,8) | sim |
| 9 | amount_state | varchar(16) | não |
| 10 | component_id | bigint | não |
| 11 | part_type | varchar(8) | não |
| 12 | part_key | nvarchar(64) | não |
| 13 | component_type | varchar(8) | não |
| 14 | component_key | nvarchar(64) | não |
| 15 | proof_attached | bit | não |
| 16 | execution_id | uniqueidentifier | não |
| 17 | occurrence | bigint | não |
| 18 | observation_id | bigint | não |
| 19 | revision | int | sim |
| 20 | fresh_second | bigint | sim |
| 21 | fresh_nano | int | sim |
| 22 | fresh_inclusive | tinyint | sim |
| 23 | part_amount | decimal(28,8) | sim |
| 24 | allocation_amount | decimal(28,8) | sim |
| 25 | additive_allocation | bit | sim |
| 26 | partition_start | date | não |
| 27 | unresolved_conflict | bit | sim |
| 28 | installment_candidate | varchar(40) | sim |
| 29 | installment_wire | varchar(8) | não |
| 30 | paid | bit | sim |
| 31 | reconciled | bit | sim |
| 32 | payment_state | varchar(6) | não |
| 33 | paid_label | nvarchar(3) | não |
| 34 | reconciliation_label | nvarchar(14) | não |
| 35 | type_raw | nvarchar(1024) | sim |
| 36 | type_token | nvarchar(1024) | sim |
| 37 | type_label | nvarchar(128) | sim |
| 38 | classification_raw | nvarchar(1024) | sim |
| 39 | classification_label | nvarchar(128) | sim |
| 40 | label_release | bigint | sim |
| 41 | issue_date | date | sim |
| 42 | created_at | bigint | sim |
| 43 | created_at_nano | int | sim |
| 44 | transaction_date | date | sim |
| 45 | liquidation_date | date | sim |
| 46 | value_to_pay | decimal(28,8) | sim |
| 47 | paid_value | decimal(28,8) | sim |
| 48 | interest_value | decimal(28,8) | sim |
| 49 | discount_value | decimal(28,8) | sim |
| 50 | competence_month | varchar(40) | sim |
| 51 | competence_year | varchar(40) | sim |

### FAT: componente corrente FAT

SQL: pub.ufn_expansion_fat; JDBC: detailPage; ordem/cursor: component_id.

| Ordem | Coluna SQL | Tipo | Nullable |
| --- | --- | --- | --- |
| 1 | run_id | uniqueidentifier | não |
| 2 | root_id | bigint | não |
| 3 | vertical | char(3) | não |
| 4 | root_type | varchar(8) | não |
| 5 | root_key | nvarchar(64) | não |
| 6 | currency | char(3) | não |
| 7 | unit | varchar(16) | não |
| 8 | root_amount | decimal(28,8) | sim |
| 9 | amount_state | varchar(16) | não |
| 10 | component_id | bigint | não |
| 11 | part_type | varchar(8) | não |
| 12 | part_key | nvarchar(64) | não |
| 13 | component_type | varchar(8) | não |
| 14 | component_key | nvarchar(64) | não |
| 15 | proof_attached | bit | não |
| 16 | execution_id | uniqueidentifier | não |
| 17 | occurrence | bigint | não |
| 18 | observation_id | bigint | não |
| 19 | revision | int | sim |
| 20 | fresh_second | bigint | sim |
| 21 | fresh_nano | int | sim |
| 22 | fresh_inclusive | tinyint | sim |
| 23 | part_amount | decimal(28,8) | sim |
| 24 | allocation_amount | decimal(28,8) | sim |
| 25 | additive_allocation | bit | sim |
| 26 | partition_start | date | não |
| 27 | unresolved_conflict | bit | sim |
| 28 | line_candidate | varchar(40) | sim |
| 29 | line_wire | varchar(8) | não |
| 30 | document_raw | nvarchar(1024) | sim |
| 31 | has_invoice | bit | sim |
| 32 | cte_number | varchar(40) | sim |
| 33 | nfse_number | varchar(40) | sim |
| 34 | nfse_alias | nvarchar(128) | sim |
| 35 | fiscal_state | varchar(14) | não |
| 36 | official_number | nvarchar(128) | sim |
| 37 | nfse_series_state | varchar(32) | sim |
| 38 | cte_status_raw | nvarchar(1024) | sim |
| 39 | cte_status_result | nvarchar(1024) | sim |
| 40 | cte_status_label | nvarchar(128) | sim |
| 41 | label_release | bigint | sim |
| 42 | issue_date | date | sim |
| 43 | due_date | date | sim |
| 44 | paid_date | date | sim |
| 45 | cte_issue_second | bigint | sim |
| 46 | cte_issue_nano | int | sim |
| 47 | title_value | decimal(28,8) | sim |
| 48 | freight_value | decimal(28,8) | sim |
| 49 | client_key | nvarchar(4000) | sim |
| 50 | client_provenance | varchar(24) | não |
| 51 | payer_document_raw | nvarchar(1024) | sim |
| 52 | payer_name_raw | nvarchar(1024) | sim |
| 53 | freight_type_raw | nvarchar(1024) | sim |
| 54 | freight_type | nvarchar(4000) | sim |
| 55 | courtesy | bit | sim |
| 56 | status_raw | nvarchar(1024) | sim |
| 57 | classification_raw | nvarchar(1024) | sim |
| 58 | service_second | bigint | sim |
| 59 | service_nano | int | sim |

### INV: componente corrente INV

SQL: pub.ufn_expansion_inv; JDBC: detailPage; ordem/cursor: component_id.

| Ordem | Coluna SQL | Tipo | Nullable |
| --- | --- | --- | --- |
| 1 | run_id | uniqueidentifier | não |
| 2 | root_id | bigint | não |
| 3 | vertical | char(3) | não |
| 4 | root_type | varchar(8) | não |
| 5 | root_key | nvarchar(64) | não |
| 6 | currency | char(3) | não |
| 7 | unit | varchar(16) | não |
| 8 | root_amount | decimal(28,8) | sim |
| 9 | amount_state | varchar(16) | não |
| 10 | component_id | bigint | não |
| 11 | part_type | varchar(8) | não |
| 12 | part_key | nvarchar(64) | não |
| 13 | component_type | varchar(8) | não |
| 14 | component_key | nvarchar(64) | não |
| 15 | proof_attached | bit | não |
| 16 | execution_id | uniqueidentifier | não |
| 17 | occurrence | bigint | não |
| 18 | observation_id | bigint | não |
| 19 | revision | int | sim |
| 20 | fresh_second | bigint | sim |
| 21 | fresh_nano | int | sim |
| 22 | fresh_inclusive | tinyint | sim |
| 23 | part_amount | decimal(28,8) | sim |
| 24 | allocation_amount | decimal(28,8) | sim |
| 25 | additive_allocation | bit | sim |
| 26 | partition_start | date | não |
| 27 | unresolved_conflict | bit | sim |
| 28 | sequence_candidate | varchar(40) | sim |
| 29 | sequence_wire | varchar(8) | não |
| 30 | freight_candidate | varchar(40) | sim |
| 31 | invoices_value | decimal(28,8) | sim |
| 32 | volumes | varchar(40) | sim |
| 33 | volumes_presence | varchar(6) | não |
| 34 | real_weight | decimal(28,8) | sim |
| 35 | taxed_weight | decimal(28,8) | sim |
| 36 | cubic_volume | decimal(28,8) | sim |
| 37 | read_volumes | varchar(40) | sim |
| 38 | mapping_count | smallint | sim |
| 39 | mapping_presence | varchar(6) | não |
| 40 | status_raw | nvarchar(1024) | sim |
| 41 | type_raw | nvarchar(1024) | sim |
| 42 | occurrence_description | nvarchar(1024) | sim |

### SIN: componente corrente SIN

SQL: pub.ufn_expansion_sin; JDBC: detailPage; ordem/cursor: component_id.

| Ordem | Coluna SQL | Tipo | Nullable |
| --- | --- | --- | --- |
| 1 | run_id | uniqueidentifier | não |
| 2 | root_id | bigint | não |
| 3 | vertical | char(3) | não |
| 4 | root_type | varchar(8) | não |
| 5 | root_key | nvarchar(64) | não |
| 6 | currency | char(3) | não |
| 7 | unit | varchar(16) | não |
| 8 | root_amount | decimal(28,8) | sim |
| 9 | amount_state | varchar(16) | não |
| 10 | component_id | bigint | não |
| 11 | part_type | varchar(8) | não |
| 12 | part_key | nvarchar(64) | não |
| 13 | component_type | varchar(8) | não |
| 14 | component_key | nvarchar(64) | não |
| 15 | proof_attached | bit | não |
| 16 | execution_id | uniqueidentifier | não |
| 17 | occurrence | bigint | não |
| 18 | observation_id | bigint | não |
| 19 | revision | int | sim |
| 20 | fresh_second | bigint | sim |
| 21 | fresh_nano | int | sim |
| 22 | fresh_inclusive | tinyint | sim |
| 23 | part_amount | decimal(28,8) | sim |
| 24 | allocation_amount | decimal(28,8) | sim |
| 25 | additive_allocation | bit | sim |
| 26 | partition_start | date | não |
| 27 | unresolved_conflict | bit | sim |
| 28 | sequence_candidate | varchar(40) | sim |
| 29 | sequence_wire | varchar(8) | não |
| 30 | freight_candidate | varchar(40) | sim |
| 31 | occurrence_candidate | nvarchar(128) | sim |
| 32 | insurance_claim_total | decimal(28,8) | sim |
| 33 | invoices_value | decimal(28,8) | sim |
| 34 | invoices_volumes | varchar(40) | sim |
| 35 | invoices_weight | decimal(28,8) | sim |
| 36 | invoices_count | varchar(40) | sim |
| 37 | customer_credit_entries_subtotal | decimal(28,8) | sim |
| 38 | customer_debits_subtotal | decimal(28,8) | sim |
| 39 | insurer_credits_subtotal | decimal(28,8) | sim |
| 40 | responsible_credits_subtotal | decimal(28,8) | sim |
| 41 | responsible_debit_entries_subtotal | decimal(28,8) | sim |
| 42 | opening_at_date | date | sim |
| 43 | occurrence_at_date | date | sim |
| 44 | occurrence_time_nano | bigint | sim |
| 45 | occurrence_at_time_raw | nvarchar(4000) | sim |
| 46 | finished_at_date | date | sim |
| 47 | finished_time_nano | bigint | sim |
| 48 | finished_at_time_raw | nvarchar(4000) | sim |
| 49 | treatment_second | bigint | sim |
| 50 | treatment_nano | int | sim |
| 51 | icm_ttt_dealing_type | nvarchar(1024) | sim |
| 52 | icm_ttt_solution_type | nvarchar(1024) | sim |

### INVOICE: título sintético (root_id)

SQL: mart.expansion_lab_invoice; JDBC: invoiceFactsPage; ordem/cursor: root_id.

| Ordem | Coluna SQL | Tipo | Nullable |
| --- | --- | --- | --- |
| 1 | root_id | bigint | não |
| 2 | run_id | uniqueidentifier | não |
| 3 | issue_date | date | sim |
| 4 | due_date | date | sim |
| 5 | paid_date | date | sim |
| 6 | base_date | date | sim |
| 7 | monthly_reference_date | date | sim |
| 8 | client_key | nvarchar(1100) | sim |
| 9 | client_provenance | varchar(32) | sim |
| 10 | has_invoice | bit | sim |
| 11 | process_state | nvarchar(32) | sim |
| 12 | payment_state | varchar(24) | sim |
| 13 | days_past_due | int | sim |
| 14 | operational_value | decimal(28,8) | sim |
| 15 | currency | char(3) | não |
| 16 | unit | varchar(16) | não |
| 17 | disposition | varchar(32) | não |
| 18 | component_count | bigint | não |
| 19 | document_count | bigint | não |
| 20 | freight_count | bigint | não |
| 21 | reference_revision | int | não |
| 22 | label_release | bigint | sim |
| 23 | business_date | date | não |
| 24 | last_receipt | uniqueidentifier | não |

### REVENUE: Frete vinculado (dependency_id)

SQL: mart.expansion_lab_revenue; JDBC: revenueFactsPage; ordem/cursor: dependency_id.

| Ordem | Coluna SQL | Tipo | Nullable |
| --- | --- | --- | --- |
| 1 | dependency_id | bigint | não |
| 2 | run_id | uniqueidentifier | não |
| 3 | source_key | nvarchar(256) | não |
| 4 | freight_stage_id | bigint | não |
| 5 | term_id | bigint | sim |
| 6 | term_revision | int | sim |
| 7 | original_reference_date | date | sim |
| 8 | billing_reference_date | date | sim |
| 9 | branch_code | nvarchar(32) | sim |
| 10 | calendar_release | bigint | sim |
| 11 | branch_release | bigint | sim |
| 12 | payer_release | bigint | sim |
| 13 | source_value | decimal(28,8) | sim |
| 14 | revenue_value | decimal(28,8) | sim |
| 15 | currency | char(3) | sim |
| 16 | unit | varchar(16) | sim |
| 17 | cancelled | bit | não |
| 18 | cancellation_provenance | varchar(32) | não |
| 19 | billing_block | bit | não |
| 20 | courtesy | bit | sim |
| 21 | eligible | bit | sim |
| 22 | active | bit | sim |
| 23 | volumes | int | sim |
| 24 | volume_provenance | varchar(32) | não |
| 25 | location_stage_id | bigint | sim |
| 26 | disposition | varchar(32) | não |
| 27 | invoice_count | bigint | não |
| 28 | document_count | bigint | não |
| 29 | reference_revision | int | não |
| 30 | business_date | date | não |
| 31 | last_receipt | uniqueidentifier | não |

O contrato de colunas foi lido do metadata do SQL Server, sem consultar dados
de negócio; [JSON estruturado](contratos-consultas.json). Provas de valores,
presença, grão, paginação, tipos e linhagem estão nas IT de projeções, faturas,
faturamento, bordas e campos. A matriz A–N registra a revisão efetivamente
executada e diferencia essa evidência dos critérios de aceite real pendentes.
