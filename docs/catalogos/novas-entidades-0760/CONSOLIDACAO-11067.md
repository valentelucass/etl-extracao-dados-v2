# Relação de Consolidação Detalhada

Template **11067**, grupo **Parceiro**, 39 campos e 9 filtros. Em 2026-11-06, cURL e Excel oficial 1777587 contêm 41 linhas, 1599 células e as mesmas tuplas completas, preservadas as multiplicidades. As regras de apresentação abaixo se limitam a esse corte.

## Requisição e seleção

Origem: `https://rodogarcia.eslcloud.com.br`. Metadata: `GET /api/analytics/reports/11067/info`. Dados: `GET /api/analytics/reports/11067/data`. Bearer do próprio V2 enviado por stdin, sem segredo em URL/argumento/log.

Seleção principal: `consolidations.issue_date`. `required_date_filters` retornou `["consolidations.issue_date"]`. `default_order` retornou `"issue_date"`; nenhum `order_by` foi enviado nesta prova.

Exemplo sem credenciais: `page=1&per=100&search[consolidations][issue_date]=2026-11-06 - 2026-11-06`. A URI real deve aplicar percent encoding. A consulta recebeu somente a data de negócio; os filtros auxiliares ficaram vazios/omitidos.

## Filtros completos

| Ordem | Parâmetro da query | Rótulo ESL | Tipo INFO | Uso observado |
| --- | --- | --- | --- | --- |
| 1 | search[consolidations][issue_date] | Emissão | date | intervalo ISO inclusivo |
| 2 | search[consolidations][sequence_code] | N° Consolidação | numeric | vazio/omitido; comportamento não vazio ainda não qualificado |
| 3 | search[consolidations][document_number] | Nº CT-e 3º | string | vazio/omitido; comportamento não vazio ainda não qualificado |
| 4 | search[consolidations][operational_number] | N° Minuta | string | vazio/omitido; comportamento não vazio ainda não qualificado |
| 5 | search[consolidations][redispatch_carrier_id] | Transportadora | select | vazio/omitido; comportamento não vazio ainda não qualificado |
| 6 | search[consolidations][price_table_id] | Tabela de Preço | select | vazio/omitido; comportamento não vazio ainda não qualificado |
| 7 | search[consolidations][origin_city_id] | Cidade origem | select | vazio/omitido; comportamento não vazio ainda não qualificado |
| 8 | search[consolidations][destination_city_id] | Cidade destino | select | vazio/omitido; comportamento não vazio ainda não qualificado |
| 9 | search[consolidations][payer_id] | Tomador | select | vazio/omitido; comportamento não vazio ainda não qualificado |

Nenhum filtro auxiliar foi ensaiado com valor não vazio.

## Dicionário completo dos campos

Os nomes e a ordem abaixo são os do INFO e dos cabeçalhos Excel. Os tipos Java/SQL são propostas para a implementação futura. O máximo observado mede o recorte e não é um limite garantido pelo fornecedor.

### 01 Filial Expedidora

- Campo JSON: `ctd_fgs_crn_psn_nickname`. Propriedade Java proposta: `ctdFgsCrnPsnNickname`.
- Significado: Filial expedidora do frete Rodogarcia. Referência contextual; o apelido não substitui ID de filial.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 45 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.

### 02 N° Consolidação

- Campo JSON: `sequence_code`. Propriedade Java proposta: `sequenceCode`.
- Significado: Número da consolidação. Único somente nas 41 linhas observadas; não homologado como chave estável global.
- Observado: INTEGER:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> pelo parser integer; BigInteger em regra aritmética explícita`. SQL proposto: `Identificador textual ou DECIMAL(38,0), conforme papel; sem INT por amostra`.
- Parser e preservação: Inteiro exato; não usar double. O parser atual limita a 38 dígitos e precisa do teste do domínio.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NUMBER": 41}.
- Inteiro observado: até 5 dígitos. Isso não aprova armazenamento INT.

### 03 Emissão Consolidação

- Campo JSON: `issue_date`. Propriedade Java proposta: `issueDate`.
- Significado: Data civil de emissão da consolidação e seleção principal do relatório. Não substituir pela data do frete relacionado.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 10 unidades UTF-16.
- Java proposto: `ExpansionValue<LocalDate>`. SQL proposto: `DATE NULL`.
- Parser e preservação: Data civil, sem conversão UTC; reutilizar parser estrito de data.
- Excel: Data ISO civil apresentada como data Excel à meia-noite, sem mudança de dia. Tipos de célula observados: {"DATETIME": 41}.

### 04 Transportadora

- Campo JSON: `ctd_rhr_cpy_nickname`. Propriedade Java proposta: `ctdRhrCpyNickname`.
- Significado: Nome da transportadora credenciada. Preservar literalmente; dimensão depende de identidade própria.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 51 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.

### 05 CNPJ Transportadora

- Campo JSON: `ctd_rhr_cpy_document`. Propriedade Java proposta: `ctdRhrCpyDocument`.
- Significado: CNPJ da transportadora como texto. Preservar zeros e formato da origem.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 14 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.
- Texto numérico observado: precisão máxima 13, escala máxima 0. Identificadores continuam texto mesmo quando só contêm dígitos.

### 06 CTe 3º

- Campo JSON: `document_number`. Propriedade Java proposta: `documentNumber`.
- Significado: Número do CT-e de terceiro como texto; não confundir com CT-e Rodogarcia nem transformar em chave única.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 6 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.
- Texto numérico observado: precisão máxima 6, escala máxima 0. Identificadores continuam texto mesmo quando só contêm dígitos.

### 07 CTe Rodogarcia

- Campo JSON: `ctd_fgs_fhe_cte_number`. Propriedade Java proposta: `ctdFgsFheCteNumber`.
- Significado: Número do CT-e Rodogarcia. Relação candidata com Fretes exige filial/emissor e prova de cardinalidade.
- Observado: INTEGER:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> pelo parser integer; BigInteger em regra aritmética explícita`. SQL proposto: `Identificador textual ou DECIMAL(38,0), conforme papel; sem INT por amostra`.
- Parser e preservação: Inteiro exato; não usar double. O parser atual limita a 38 dígitos e precisa do teste do domínio.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NUMBER": 41}.
- Inteiro observado: até 6 dígitos. Isso não aprova armazenamento INT.

### 08 Valor Frete a Pagar à Credenciada

- Campo JSON: `charged_value`. Propriedade Java proposta: `chargedValue`.
- Significado: Frete a pagar à credenciada. Valor da consolidação; não somar uma vez por filho se houver expansão futura.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 6 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 41}.
- Texto numérico observado: precisão máxima 5, escala máxima 2. Identificadores continuam texto mesmo quando só contêm dígitos.

### 09 Valor Calculado ESL

- Campo JSON: `subtotal`. Propriedade Java proposta: `subtotal`.
- Significado: Valor calculado pelo ESL para a consolidação. Campo distinto do valor cobrado e do subtotal do frete Rodogarcia.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 6 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 41}.
- Texto numérico observado: precisão máxima 5, escala máxima 2. Identificadores continuam texto mesmo quando só contêm dígitos.

### 10 Frete Rodogarcia Sem Impostos

- Campo JSON: `ctd_fgs_subtotal`. Propriedade Java proposta: `ctdFgsSubtotal`.
- Significado: Frete Rodogarcia sem impostos. Não substituir pelo subtotal da consolidação.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 7 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 41}.
- Texto numérico observado: precisão máxima 6, escala máxima 2. Identificadores continuam texto mesmo quando só contêm dígitos.

### 11 Frete Rodogarcia Final

- Campo JSON: `ctd_fgs_total`. Propriedade Java proposta: `ctdFgsTotal`.
- Significado: Frete Rodogarcia final. Repetição por relação futura pode duplicar totais; medir a granularidade antes de somar.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 7 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 41}.
- Texto numérico observado: precisão máxima 6, escala máxima 2. Identificadores continuam texto mesmo quando só contêm dígitos.

### 12 Peso Taxado Rodogarcia

- Campo JSON: `ctd_fgs_taxed_weight`. Propriedade Java proposta: `ctdFgsTaxedWeight`.
- Significado: Peso taxado do frete Rodogarcia. Três casas decimais observadas; a unidade não está declarada no INFO.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 7 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 41}.
- Texto numérico observado: precisão máxima 6, escala máxima 3. Identificadores continuam texto mesmo quando só contêm dígitos.

### 13 Comprovante Anexado

- Campo JSON: `ctd_fgs_fte_has_delivery_receipt`. Propriedade Java proposta: `ctdFgsFteHasDeliveryReceipt`.
- Significado: Indicador booleano de comprovante anexado. Separar de data da entrega e do evento finalizador.
- Observado: BOOLEAN:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<Boolean>`. SQL proposto: `BIT NULL`.
- Parser e preservação: Reutilizar parser booleano estrito; sem converter NULL em false.
- Excel: Apresentação Excel: true→Sim e false→Não. Booleano original preservado. Tipos de célula observados: {"STRING": 41}.

### 14 Status Fatura

- Campo JSON: `bill_status`. Propriedade Java proposta: `billStatus`.
- Significado: Status da fatura apresentado como texto pela própria origem. No corte, o Excel conserva esse texto sem tradução adicional.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 9 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.

### 15 Data Frete Rodogarcia

- Campo JSON: `ctd_fgs_service_at`. Propriedade Java proposta: `ctdFgsServiceAt`.
- Significado: Data e hora do frete Rodogarcia relacionado. Não é a data de seleção da consolidação.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Excel conserva data/hora civil do offset de origem. Não converter para UTC para comparar a célula. Tipos de célula observados: {"DATETIME": 41}.
- Offset observado: -03:00; 0 timestamps com fração. Guardar o ISO original e a precisão integral.

### 16 Minuta Rodogarcia

- Campo JSON: `ctd_fgs_corporation_sequence_number`. Propriedade Java proposta: `ctdFgsCorporationSequenceNumber`.
- Significado: Minuta Rodogarcia relacionada. Candidata a associação com Fretes, contextualizada por filial/escopo.
- Observado: INTEGER:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> pelo parser integer; BigInteger em regra aritmética explícita`. SQL proposto: `Identificador textual ou DECIMAL(38,0), conforme papel; sem INT por amostra`.
- Parser e preservação: Inteiro exato; não usar double. O parser atual limita a 38 dígitos e precisa do teste do domínio.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NUMBER": 41}.
- Inteiro observado: até 6 dígitos. Isso não aprova armazenamento INT.

### 17 Volumes Rodogarcia

- Campo JSON: `ctd_fgs_invoices_volumes`. Propriedade Java proposta: `ctdFgsInvoicesVolumes`.
- Significado: Volumes do frete Rodogarcia. Campo distinto dos volumes da consolidação.
- Observado: INTEGER:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> pelo parser integer; BigInteger em regra aritmética explícita`. SQL proposto: `Identificador textual ou DECIMAL(38,0), conforme papel; sem INT por amostra`.
- Parser e preservação: Inteiro exato; não usar double. O parser atual limita a 38 dígitos e precisa do teste do domínio.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NUMBER": 41}.
- Inteiro observado: até 3 dígitos. Isso não aprova armazenamento INT.

### 18 Nº NF

- Campo JSON: `ctd_fgs_invoices_mapping`. Propriedade Java proposta: `ctdFgsInvoicesMapping`.
- Significado: Lista ordenada de números de NF. Preservar cada item, posição e repetição; a célula Excel usa vírgula seguida de espaço.
- Observado: ARRAY:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<ExpansionStrings> candidato ou coleção própria qualificada`. SQL proposto: `Tabela filha por ocorrência e posição; envelope JSON original`.
- Parser e preservação: Preservar ordem/repetição. O parser compartilhado limita 32 itens/256 UTF-16 por item; máximo observado não vira limite do fornecedor.
- Excel: Somente apresentação Excel: juntar itens em ordem com vírgula e espaço. Lista original preservada. Tipos de célula observados: {"STRING": 41}.
- Lista: máximo 2 itens; tipos {"STRING": 42}; item textual até 7 UTF-16. Não deduplicar nem ordenar.

### 19 Cidade Origem Rodogarcia

- Campo JSON: `ctd_fgs_dos_cty_name`. Propriedade Java proposta: `ctdFgsDosCtyName`.
- Significado: Cidade de origem Rodogarcia conforme o rótulo oficial. Não inverter por interpretação da sigla do alias.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 20 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.

### 20 CNPJ Remetente

- Campo JSON: `ctd_sdr_document`. Propriedade Java proposta: `ctdSdrDocument`.
- Significado: CNPJ do remetente da consolidação como texto. Nome e documento não são chaves físicas de recebimento.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 14 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.
- Texto numérico observado: precisão máxima 14, escala máxima 0. Identificadores continuam texto mesmo quando só contêm dígitos.

### 21 Remetente

- Campo JSON: `ctd_sdr_nickname`. Propriedade Java proposta: `ctdSdrNickname`.
- Significado: Nome do remetente da consolidação, preservado literalmente.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 62 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.

### 22 Cidade Destino Rodogarcia

- Campo JSON: `ctd_fgs_oas_cty_name`. Propriedade Java proposta: `ctdFgsOasCtyName`.
- Significado: Cidade de destino Rodogarcia conforme o rótulo oficial. Preservar o alias observado.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 19 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.

### 23 CNPJ Destinatário

- Campo JSON: `ctd_rpt_document`. Propriedade Java proposta: `ctdRptDocument`.
- Significado: CNPJ do destinatário da consolidação como texto, com zeros e caracteres preservados.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 14 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.
- Texto numérico observado: precisão máxima 14, escala máxima 0. Identificadores continuam texto mesmo quando só contêm dígitos.

### 24 Destinatário

- Campo JSON: `ctd_rpt_nickname`. Propriedade Java proposta: `ctdRptNickname`.
- Significado: Nome do destinatário da consolidação, preservado literalmente.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 60 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.

### 25 Cobra TDE

- Campo JSON: `ctd_rpt_charge_tde`. Propriedade Java proposta: `ctdRptChargeTde`.
- Significado: Indicador booleano Cobra TDE. Não inferir que seja o valor monetário TDA de outro campo.
- Observado: BOOLEAN:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<Boolean>`. SQL proposto: `BIT NULL`.
- Parser e preservação: Reutilizar parser booleano estrito; sem converter NULL em false.
- Excel: Apresentação Excel: true→Sim e false→Não. Booleano original preservado. Tipos de célula observados: {"STRING": 41}.

### 26 Previsão de Entrega

- Campo JSON: `ctd_fgs_dpn_delivery_prediction_at`. Propriedade Java proposta: `ctdFgsDpnDeliveryPredictionAt`.
- Significado: Previsão de entrega do frete. Frações de segundo existem na origem e são omitidas no Excel do corte.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Somente apresentação deste campo no Excel: descartar fração de segundo, mantendo data/hora civil. RAW e futuro tipo Java conservam a fração. Tipos de célula observados: {"DATETIME": 41}.
- Offset observado: -03:00; 26 timestamps com fração. Guardar o ISO original e a precisão integral.

### 27 Data de Entrega

- Campo JSON: `ctd_fgs_finished_at`. Propriedade Java proposta: `ctdFgsFinishedAt`.
- Significado: Data e hora de entrega do frete. NULL em 16/41 linhas; não preencher com previsão ou data de captura.
- Observado: STRING:25, NULL:16; presente em 41/41 linhas; 16 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Excel conserva data/hora civil do offset de origem. Não converter para UTC para comparar a célula. Tipos de célula observados: {"DATETIME": 25, "NULL": 16}.
- Offset observado: -03:00; 0 timestamps com fração. Guardar o ISO original e a precisão integral.

### 28 Entregue no Prazo

- Campo JSON: `ctd_fgs_dpn_finished_within_deadline`. Propriedade Java proposta: `ctdFgsDpnFinishedWithinDeadline`.
- Significado: Indicador booleano de entrega no prazo fornecido pelo ESL; não recalcular silenciosamente.
- Observado: BOOLEAN:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<Boolean>`. SQL proposto: `BIT NULL`.
- Parser e preservação: Reutilizar parser booleano estrito; sem converter NULL em false.
- Excel: Apresentação Excel: true→Sim e false→Não. Booleano original preservado. Tipos de célula observados: {"STRING": 41}.

### 29 Última Ocorrência

- Campo JSON: `ctd_fgs_fte_lce_ore_description`. Propriedade Java proposta: `ctdFgsFteLceOreDescription`.
- Significado: Descrição da última ocorrência do frete. É um estado relacionado, não a coleção inteira de Ocorrências 12072.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 30 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 41}.

### 30 Ocorrência Finalizadora

- Campo JSON: `ctd_fgs_fte_foe_ore_description`. Propriedade Java proposta: `ctdFgsFteFoeOreDescription`.
- Significado: Descrição da ocorrência finalizadora do frete; pode ser NULL.
- Observado: STRING:25, NULL:16; presente em 41/41 linhas; 16 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 25, "NULL": 16}.

### 31 Data Ocorrência Finalizadora

- Campo JSON: `ctd_fgs_fte_foe_occurrence_at`. Propriedade Java proposta: `ctdFgsFteFoeOccurrenceAt`.
- Significado: Data e hora da ocorrência finalizadora; pode ser NULL e não substitui a data de entrega.
- Observado: STRING:25, NULL:16; presente em 41/41 linhas; 16 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Excel conserva data/hora civil do offset de origem. Não converter para UTC para comparar a célula. Tipos de célula observados: {"DATETIME": 25, "NULL": 16}.
- Offset observado: -03:00; 0 timestamps com fração. Guardar o ISO original e a precisão integral.

### 32 Comprovantes de entrega/Data criação

- Campo JSON: `ctd_fgs_d_t_created_at`. Propriedade Java proposta: `ctdFgsDTCreatedAt`.
- Significado: Data de criação do comprovante. Preservar offset/frações; o Excel omite a parte fracionária no corte.
- Observado: STRING:25, NULL:16; presente em 41/41 linhas; 16 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Somente apresentação deste campo no Excel: descartar fração de segundo, mantendo data/hora civil. RAW e futuro tipo Java conservam a fração. Tipos de célula observados: {"DATETIME": 25, "NULL": 16}.
- Offset observado: -03:00; 24 timestamps com fração. Guardar o ISO original e a precisão integral.

### 33 Volumes Consolidação

- Campo JSON: `invoices_volumes`. Propriedade Java proposta: `invoicesVolumes`.
- Significado: Volumes da consolidação. Não confundir nem somar junto com os volumes Rodogarcia para contar o mesmo transporte.
- Observado: INTEGER:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> pelo parser integer; BigInteger em regra aritmética explícita`. SQL proposto: `Identificador textual ou DECIMAL(38,0), conforme papel; sem INT por amostra`.
- Parser e preservação: Inteiro exato; não usar double. O parser atual limita a 38 dígitos e precisa do teste do domínio.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NUMBER": 41}.
- Inteiro observado: até 3 dígitos. Isso não aprova armazenamento INT.

### 34 Peso Real Consolidação

- Campo JSON: `real_weight`. Propriedade Java proposta: `realWeight`.
- Significado: Peso real da consolidação. Decimal textual; unidade depende de confirmação do contrato de negócio.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 6 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 41}.
- Texto numérico observado: precisão máxima 5, escala máxima 2. Identificadores continuam texto mesmo quando só contêm dígitos.

### 35 Peso Taxado Consolidação

- Campo JSON: `taxed_weight`. Propriedade Java proposta: `taxedWeight`.
- Significado: Peso taxado da consolidação, distinto do peso taxado Rodogarcia. Não usar ponto flutuante.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 6 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 41}.
- Texto numérico observado: precisão máxima 5, escala máxima 2. Identificadores continuam texto mesmo quando só contêm dígitos.

### 36 Valor NF Consolidação

- Campo JSON: `invoices_value`. Propriedade Java proposta: `invoicesValue`.
- Significado: Valor das notas fiscais da consolidação. Decimal exato, sem derivar de valores de frete.
- Observado: STRING:41; presente em 41/41 linhas; 0 NULL; máximo textual 9 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 41}.
- Texto numérico observado: precisão máxima 8, escala máxima 2. Identificadores continuam texto mesmo quando só contêm dígitos.

### 37 Valor TDA

- Campo JSON: `ctd_diy_cin_tda_value`. Propriedade Java proposta: `ctdDiyCinTdaValue`.
- Significado: Valor TDA. Texto numérico ou NULL; não transformar NULL em zero nem confundir com Cobra TDE.
- Observado: STRING:28, NULL:13; presente em 41/41 linhas; 13 NULL; máximo textual 3 unidades UTF-16.
- Java proposto: `ExpansionValue<BigDecimal>`. SQL proposto: `DECIMAL(p,s) a dimensionar; manter literal original`.
- Parser e preservação: Sem float/double ou arredondamento. Parser atual admite20 inteiros/8 casas; qualificar em recortes maiores antes de reutilizar.
- Excel: Texto numérico interpretado por decimal exato para confrontar a célula numérica. RAW permanece textual. Tipos de célula observados: {"NUMBER": 28, "NULL": 13}.
- Texto numérico observado: precisão máxima 1, escala máxima 1. Identificadores continuam texto mesmo quando só contêm dígitos.

### 38 Nº Fatura Financeiro

- Campo JSON: `ctd_ant_document`. Propriedade Java proposta: `ctdAntDocument`.
- Significado: Número da fatura do financeiro. Somente NULL neste corte; tipo não nulo e relação com Contas a Pagar ainda precisam de prova.
- Observado: NULL:41; presente em 41/41 linhas; 41 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> candidato; JsonNode até qualificar não nulo`. SQL proposto: `NVARCHAR com largura a qualificar; envelope original preservado`.
- Parser e preservação: Somente NULL observado. Não fixar parser de texto/número ou limite por suposição.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NULL": 41}.
- Pendência de contrato: nenhum valor não nulo foi observado; não homologar o parser nem preencher valor padrão.

### 39 Composição XML

- Campo JSON: `charged_value_details`. Propriedade Java proposta: `chargedValueDetails`.
- Significado: Objeto JSON com composição de cobrança XML. Preservar chaves dinâmicas, valores decimais exatos, ordem e objeto vazio. Não expandir em colunas fixas sem contrato.
- Observado: OBJECT:41; presente em 41/41 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `JsonNode/objeto próprio com números BigDecimal e raw original`. SQL proposto: `NVARCHAR(MAX) com ISJSON; filhos opcionais por chave e posição`.
- Parser e preservação: Parser de objeto precisa de contrato próprio. Preservar{}e chave dinâmica; não reduzir à string da planilha.
- Excel: Excel contém JSON compacto; chaves e números decimais conferidos sem ponto flutuante. Tipos de célula observados: {"STRING": 41}.
- Chaves internas observadas: `DESPACHO`, `FRETE PESO`, `FRETE VALOR`, `GRIS`, `PEDAGIO`, `TAXA ADICIONAL`, `TAXA DIF.ACESSO`. Tipos por chave: `{"FRETE VALOR": {"Decimal": 16}, "FRETE PESO": {"Decimal": 1}, "DESPACHO": {"Decimal": 1}, "PEDAGIO": {"Decimal": 1}, "GRIS": {"Decimal": 1}, "TAXA DIF.ACESSO": {"Decimal": 1}, "TAXA ADICIONAL": {"Decimal": 1}}`. As chaves são dinâmicas; novas composições não podem ser truncadas.

## Granularidade e identidade

| Candidato | Linhas | Distintos | Maior multiplicidade | Conclusão no corte |
| --- | --- | --- | --- | --- |
| `sequence_code` | 41 | 41 | 1 | Único apenas neste corte |
| `sequence_code+ctd_fgs_corporation_sequence_number` | 41 | 41 | 1 | Único apenas neste corte |
| `sequence_code+ctd_fgs_corporation_sequence_number+document_number` | 41 | 41 | 1 | Único apenas neste corte |

A identidade física proposta é execução/captura + ordinal da ocorrência. Nenhum desses candidatos recebeu prova de estabilidade entre execuções, tenants e mudanças de template. Não criar MERGE, UNIQUE, deduplicação ou tombstone por semelhança com outra entidade.

## Limites da qualificação

GET sem retry ou redirect; timeout30 s; intervalo mínimo3 s depois da resposta; até 10 MiB por resposta; captura com INFO + até 16 páginas de 100, teto 12000 linhas/210 s e executor compartilhado2 GiB. O teste de amostra usou per 3 e não recebeu aceite de completude. O corte completo só terminou após página vazia literal. O tamanho máximo observado não define o contrato de per, arrays ou objetos para todo o histórico.

A semântica de filtros auxiliares não vazios, intervalos maiores, dias vazios, estabilidade da paginação, alterações concorrentes, reexecução, valores não nulos ausentes e falhas ainda integra o plano de testes. A igualdade com Excel é de apresentação: frações descartadas pelo Excel permanecem no RAW. Não há Java novo, recebimento SQL destas entidades ou publicação de negócio.

Evidências públicas específicas em `evidencias/`; RAW/Excel integrais somente no diretório privado proprietário.
