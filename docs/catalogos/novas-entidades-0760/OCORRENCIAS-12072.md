# Relação de Ocorrências

Template **12072**, grupo **Relatórios**, 20 campos e 13 filtros. Em 2026-11-08, cURL e Excel oficial 1777562 contêm 279 linhas, 5580 células e as mesmas tuplas completas, preservadas as multiplicidades. As regras de apresentação abaixo se limitam a esse corte.

## Requisição e seleção

Origem: `https://rodogarcia.eslcloud.com.br`. Metadata: `GET /api/analytics/reports/12072/info`. Dados: `GET /api/analytics/reports/12072/data`. Bearer do próprio V2 enviado por stdin, sem segredo em URL/argumento/log.

Seleção principal: `invoice_occurrences.occurrence_at`. `required_date_filters` retornou `["invoice_occurrences.occurrence_at", "invoice_occurrences.updated_at"]`. `default_order` retornou `null`; nenhum `order_by` foi enviado nesta prova.

Exemplo sem credenciais: `page=1&per=100&search[invoice_occurrences][occurrence_at]=2026-11-08 - 2026-11-08`. A URI real deve aplicar percent encoding. A consulta recebeu somente a data de negócio; os filtros auxiliares ficaram vazios/omitidos.

A consulta real com somente `invoice_occurrences[occurrence_at]` funcionou. Portanto, a lista de duas datas do INFO não deve ser interpretada como obrigação de enviar ambas simultaneamente. O uso isolado de `updated_at`, a interseção dos filtros e seus limites temporais ainda precisam de testes próprios.

## Filtros completos

| Ordem | Parâmetro da query | Rótulo ESL | Tipo INFO | Uso observado |
| --- | --- | --- | --- | --- |
| 1 | search[invoice_occurrences][occurrence_at] | Data ocorrência | date | intervalo ISO inclusivo |
| 2 | search[scopes][by_cte_or_draft_number] | Doc/Minuta | numeric | vazio/omitido; comportamento não vazio ainda não qualificado |
| 3 | search[scopes][by_freight_reference_number] | Nº Referência | string | vazio/omitido; comportamento não vazio ainda não qualificado |
| 4 | search[scopes][by_invoice_number] | Nota Fiscal | numeric | vazio/omitido; comportamento não vazio ainda não qualificado |
| 5 | search[scopes][by_invoice_key] | Chave NF-e | numeric | vazio/omitido; comportamento não vazio ainda não qualificado |
| 6 | search[scopes][by_cte_key] | Chave CT-e | numeric | vazio/omitido; comportamento não vazio ainda não qualificado |
| 7 | search[scopes][from_manifest_sequence_code] | Manifesto | numeric | vazio/omitido; comportamento não vazio ainda não qualificado |
| 8 | search[scopes][from_consolidation_document_number] | Nº Consolidação | numeric | vazio/omitido; comportamento não vazio ainda não qualificado |
| 9 | search[scopes][from_freight_payer] | Pagador | select | vazio/omitido; comportamento não vazio ainda não qualificado |
| 10 | search[scopes][by_mobile] | Baixa por mobile | select | vazio/omitido; comportamento não vazio ainda não qualificado |
| 11 | search[ice_occurrence][description] | Ocorrência/Ocorrência | string | vazio/omitido; comportamento não vazio ainda não qualificado |
| 12 | search[ice_occurrence][code] | Ocorrência/Código | string | vazio/omitido; comportamento não vazio ainda não qualificado |
| 13 | search[invoice_occurrences][updated_at] | Data atualização | datetime | vazio/omitido; comportamento não vazio ainda não qualificado |

Filtros de chave NF-e/CT-e têm maxlength 44 no INFO. Mesmo quando o controle é numeric, chaves/documentos devem ser tratados como texto para não perder dígitos.

Nenhum filtro auxiliar foi ensaiado com valor não vazio.

## Dicionário completo dos campos

Os nomes e a ordem abaixo são os do INFO e dos cabeçalhos Excel. Os tipos Java/SQL são propostas para a implementação futura. O máximo observado mede o recorte e não é um limite garantido pelo fornecedor.

### 01 Minuta

- Campo JSON: `ice_f_e_fit_corporation_sequence_number`. Propriedade Java proposta: `iceFEFitCorporationSequenceNumber`.
- Significado: Minuta relacionada. Há 246 minutas em 279 linhas; uma minuta não identifica uma ocorrência física.
- Observado: INTEGER:279; presente em 279/279 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> pelo parser integer; BigInteger em regra aritmética explícita`. SQL proposto: `Identificador textual ou DECIMAL(38,0), conforme papel; sem INT por amostra`.
- Parser e preservação: Inteiro exato; não usar double. O parser atual limita a 38 dígitos e precisa do teste do domínio.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NUMBER": 279}.
- Inteiro observado: até 6 dígitos. Isso não aprova armazenamento INT.

### 02 Nota Fiscal

- Campo JSON: `ice_f_e_ioe_number`. Propriedade Java proposta: `iceFEIoeNumber`.
- Significado: Número de NF como texto escalar. A expansão de notas produz linhas separadas; não converter esta coluna em lista por analogia com 11067.
- Observado: STRING:279; presente em 279/279 linhas; 0 NULL; máximo textual 7 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 279}.
- Texto numérico observado: precisão máxima 7, escala máxima 0. Identificadores continuam texto mesmo quando só contêm dígitos.

### 03 CTe

- Campo JSON: `ice_fit_fhe_cte_number`. Propriedade Java proposta: `iceFitFheCteNumber`.
- Significado: CT-e relacionado, inteiro ou NULL. Não usar isoladamente como chave de evento ou vínculo sem filial/emissor.
- Observado: INTEGER:275, NULL:4; presente em 279/279 linhas; 4 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> pelo parser integer; BigInteger em regra aritmética explícita`. SQL proposto: `Identificador textual ou DECIMAL(38,0), conforme papel; sem INT por amostra`.
- Parser e preservação: Inteiro exato; não usar double. O parser atual limita a 38 dígitos e precisa do teste do domínio.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NUMBER": 275, "NULL": 4}.
- Inteiro observado: até 6 dígitos. Isso não aprova armazenamento INT.

### 04 Ocorrência/Código

- Campo JSON: `ice_ore_code`. Propriedade Java proposta: `iceOreCode`.
- Significado: Código da ocorrência como inteiro exato. Código classifica o evento; repetições do mesmo código são legítimas.
- Observado: INTEGER:279; presente em 279/279 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> pelo parser integer; BigInteger em regra aritmética explícita`. SQL proposto: `Identificador textual ou DECIMAL(38,0), conforme papel; sem INT por amostra`.
- Parser e preservação: Inteiro exato; não usar double. O parser atual limita a 38 dígitos e precisa do teste do domínio.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NUMBER": 279}.
- Inteiro observado: até 3 dígitos. Isso não aprova armazenamento INT.

### 05 Ocorrência/Ocorrência

- Campo JSON: `ice_ore_description`. Propriedade Java proposta: `iceOreDescription`.
- Significado: Descrição da ocorrência original. Campo distinto da ocorrência de solução da tratativa.
- Observado: STRING:279; presente em 279/279 linhas; 0 NULL; máximo textual 30 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 279}.

### 06 Data ocorrência

- Campo JSON: `occurrence_at`. Propriedade Java proposta: `occurrenceAt`.
- Significado: Instante do evento e data de seleção principal. Preservar frações na origem; o Excel do corte mostra segundos inteiros.
- Observado: STRING:279; presente em 279/279 linhas; 0 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Somente apresentação deste campo no Excel: descartar fração de segundo, mantendo data/hora civil. RAW e futuro tipo Java conservam a fração. Tipos de célula observados: {"DATETIME": 279}.
- Offset observado: -03:00; 21 timestamps com fração. Guardar o ISO original e a precisão integral.

### 07 Baixa por mobile

- Campo JSON: `on_mobile`. Propriedade Java proposta: `onMobile`.
- Significado: Booleano de baixa por mobile. Excel mostra Sim/Não; armazenar o booleano original.
- Observado: BOOLEAN:279; presente em 279/279 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<Boolean>`. SQL proposto: `BIT NULL`.
- Parser e preservação: Reutilizar parser booleano estrito; sem converter NULL em false.
- Excel: Apresentação Excel: true→Sim e false→Não. Booleano original preservado. Tipos de célula observados: {"STRING": 279}.

### 08 Usuário/Nome

- Campo JSON: `ice_uer_name`. Propriedade Java proposta: `iceUerName`.
- Significado: Nome do usuário relacionado ao evento. Dado pessoal de negócio; não publicar amostras ou usar como identificador.
- Observado: STRING:279; presente em 279/279 linhas; 0 NULL; máximo textual 31 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 279}.

### 09 Frete/Data do frete

- Campo JSON: `ice_fit_service_at`. Propriedade Java proposta: `iceFitServiceAt`.
- Significado: Data e hora do frete relacionado. Não substituir o filtro da data da ocorrência.
- Observado: STRING:279; presente em 279/279 linhas; 0 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Excel conserva data/hora civil do offset de origem. Não converter para UTC para comparar a célula. Tipos de célula observados: {"DATETIME": 279}.
- Offset observado: -03:00; 0 timestamps com fração. Guardar o ISO original e a precisão integral.

### 10 Previsão Entrega/Previsão de entrega

- Campo JSON: `ice_fit_dpn_delivery_prediction_at`. Propriedade Java proposta: `iceFitDpnDeliveryPredictionAt`.
- Significado: Previsão de entrega relacionada ao frete. Excel omite frações; manter precisão original no recebimento.
- Observado: STRING:279; presente em 279/279 linhas; 0 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Somente apresentação deste campo no Excel: descartar fração de segundo, mantendo data/hora civil. RAW e futuro tipo Java conservam a fração. Tipos de célula observados: {"DATETIME": 279}.
- Offset observado: -03:00; 217 timestamps com fração. Guardar o ISO original e a precisão integral.

### 11 Data agendamento

- Campo JSON: `occurrence_schedule_at`. Propriedade Java proposta: `occurrenceScheduleAt`.
- Significado: Data de agendamento. Somente NULL no corte; máscara Excel sugere data/hora, sem provar o tipo não nulo da API.
- Observado: NULL:279; presente em 279/279 linhas; 279 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant> candidato; JsonNode até qualificar não nulo`. SQL proposto: `DATETIMEOFFSET(7) candidato; envelope original preservado`.
- Parser e preservação: Somente NULL observado. O formato Excel não homologa o wire não nulo.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NULL": 279}.
- Pendência de contrato: nenhum valor não nulo foi observado; não homologar o parser nem preencher valor padrão.

### 12 Previsão Entrega/Finalização performance

- Campo JSON: `ice_fit_dpn_performance_finished_at`. Propriedade Java proposta: `iceFitDpnPerformanceFinishedAt`.
- Significado: Data de finalização da performance. 51 valores e 228 NULL observados; não equivale automaticamente à entrega física.
- Observado: NULL:228, STRING:51; presente em 279/279 linhas; 228 NULL; máximo textual 29 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant>, com ISO/offset original em raw`. SQL proposto: `DATETIMEOFFSET(7) candidato e raw ISO; qualificar precisão física`.
- Parser e preservação: O offset-03:00 foi observado; a janela usa America/Sao_Paulo. Não truncar para reproduzir o Excel.
- Excel: Excel conserva data/hora civil do offset de origem. Não converter para UTC para comparar a célula. Tipos de célula observados: {"NULL": 228, "DATETIME": 51}.
- Offset observado: -03:00; 0 timestamps com fração. Guardar o ISO original e a precisão integral.

### 13 Status Entrega

- Campo JSON: `delivery_status`. Propriedade Java proposta: `deliveryStatus`.
- Significado: Estado técnico de entrega. Mapa observado: failure→Insucesso, pending→Pendente, delivered→Entregue. Outros estados exigem qualificação.
- Observado: STRING:279; presente em 279/279 linhas; 0 NULL; máximo textual 9 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Tradução de status observada por associação das demais colunas, sem conflito, seguida de comparação das tuplas completas. Tipos de célula observados: {"STRING": 279}.
- Tradução comprovada no corte: `{"failure": "Insucesso", "pending": "Pendente", "delivered": "Entregue"}`. Estado desconhecido deve conservar o código e gerar tratamento explícito.

### 14 Localização Atual

- Campo JSON: `ice_fit_fln_cln_nickname`. Propriedade Java proposta: `iceFitFlnClnNickname`.
- Significado: Localização atual relacionada ao frete. Texto de snapshot; não representa por si um histórico de movimentações.
- Observado: STRING:279; presente em 279/279 linhas; 0 NULL; máximo textual 45 unidades UTF-16.
- Java proposto: `ExpansionValue<String>`. SQL proposto: `NVARCHAR com largura a dimensionar; original integral`.
- Parser e preservação: Não aplicar trim, normalização, rótulo ou remoção de zeros por convenção. Parser text atual limita 1024 UTF-16.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"STRING": 279}.

### 15 Agendado

- Campo JSON: `ice_fit_dpn_manual_schedule`. Propriedade Java proposta: `iceFitDpnManualSchedule`.
- Significado: Booleano de agendamento manual fornecido pelo ESL. Separar da data de agendamento.
- Observado: BOOLEAN:279; presente em 279/279 linhas; 0 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<Boolean>`. SQL proposto: `BIT NULL`.
- Parser e preservação: Reutilizar parser booleano estrito; sem converter NULL em false.
- Excel: Apresentação Excel: true→Sim e false→Não. Booleano original preservado. Tipos de célula observados: {"STRING": 279}.

### 16 Tratativa/Prazo solução

- Campo JSON: `ice_ttt_solution_deadline`. Propriedade Java proposta: `iceTttSolutionDeadline`.
- Significado: Prazo da solução da tratativa. Somente NULL; candidato a instante pelo formato Excel, ainda sem valor não nulo da API.
- Observado: NULL:279; presente em 279/279 linhas; 279 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant> candidato; JsonNode até qualificar não nulo`. SQL proposto: `DATETIMEOFFSET(7) candidato; envelope original preservado`.
- Parser e preservação: Somente NULL observado. O formato Excel não homologa o wire não nulo.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NULL": 279}.
- Pendência de contrato: nenhum valor não nulo foi observado; não homologar o parser nem preencher valor padrão.

### 17 Tratativa/Data Solução

- Campo JSON: `ice_ttt_solution_at`. Propriedade Java proposta: `iceTttSolutionAt`.
- Significado: Data da solução da tratativa. Somente NULL no corte; não preencher com prazo ou data do evento.
- Observado: NULL:279; presente em 279/279 linhas; 279 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<Instant> candidato; JsonNode até qualificar não nulo`. SQL proposto: `DATETIMEOFFSET(7) candidato; envelope original preservado`.
- Parser e preservação: Somente NULL observado. O formato Excel não homologa o wire não nulo.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NULL": 279}.
- Pendência de contrato: nenhum valor não nulo foi observado; não homologar o parser nem preencher valor padrão.

### 18 Tratativa/Solução

- Campo JSON: `ice_ttt_solution_type`. Propriedade Java proposta: `iceTttSolutionType`.
- Significado: Tipo da solução. Somente NULL; domínio e tradução não devem ser inventados ou copiados de Sinistros.
- Observado: NULL:279; presente em 279/279 linhas; 279 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> candidato; JsonNode até qualificar não nulo`. SQL proposto: `NVARCHAR com largura a qualificar; envelope original preservado`.
- Parser e preservação: Somente NULL observado. Não fixar parser de texto/número ou limite por suposição.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NULL": 279}.
- Pendência de contrato: nenhum valor não nulo foi observado; não homologar o parser nem preencher valor padrão.

### 19 Tratativa/Ocorrência

- Campo JSON: `ice_ttt_soe_description`. Propriedade Java proposta: `iceTttSoeDescription`.
- Significado: Descrição da ocorrência da tratativa. Somente NULL; campo separado da descrição do evento original.
- Observado: NULL:279; presente em 279/279 linhas; 279 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> candidato; JsonNode até qualificar não nulo`. SQL proposto: `NVARCHAR com largura a qualificar; envelope original preservado`.
- Parser e preservação: Somente NULL observado. Não fixar parser de texto/número ou limite por suposição.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NULL": 279}.
- Pendência de contrato: nenhum valor não nulo foi observado; não homologar o parser nem preencher valor padrão.

### 20 Tratativa/Observações

- Campo JSON: `ice_ttt_solution_comments`. Propriedade Java proposta: `iceTttSolutionComments`.
- Significado: Observações da tratativa. Somente NULL; largura, quebras de linha e limite não nulo precisam de qualificação específica.
- Observado: NULL:279; presente em 279/279 linhas; 279 NULL; máximo textual 0 unidades UTF-16.
- Java proposto: `ExpansionValue<String> candidato; JsonNode até qualificar não nulo`. SQL proposto: `NVARCHAR com largura a qualificar; envelope original preservado`.
- Parser e preservação: Somente NULL observado. Não fixar parser de texto/número ou limite por suposição.
- Excel: Valor literal; NULL continua NULL. Tipos de célula observados: {"NULL": 279}.
- Pendência de contrato: nenhum valor não nulo foi observado; não homologar o parser nem preencher valor padrão.

## Granularidade e identidade

| Candidato | Linhas | Distintos | Maior multiplicidade | Conclusão no corte |
| --- | --- | --- | --- | --- |
| `ice_f_e_fit_corporation_sequence_number` | 279 | 246 | 5 | Não único |
| `ice_f_e_fit_corporation_sequence_number+ice_ore_code+occurrence_at` | 279 | 263 | 5 | Não único |
| `ice_f_e_fit_corporation_sequence_number+ice_ore_code+occurrence_at+ice_f_e_ioe_number` | 279 | 279 | 1 | Único apenas neste corte |

A identidade física proposta é execução/captura + ordinal da ocorrência. Nenhum desses candidatos recebeu prova de estabilidade entre execuções, tenants e mudanças de template. Não criar MERGE, UNIQUE, deduplicação ou tombstone por semelhança com outra entidade.

## Limites da qualificação

GET sem retry ou redirect; timeout30 s; intervalo mínimo3 s depois da resposta; até 10 MiB por resposta; captura com INFO + até 16 páginas de 100, teto 12000 linhas/210 s e executor compartilhado2 GiB. O teste de amostra usou per 3 e não recebeu aceite de completude. O corte completo só terminou após página vazia literal. O tamanho máximo observado não define o contrato de per, arrays ou objetos para todo o histórico.

A semântica de filtros auxiliares não vazios, intervalos maiores, dias vazios, estabilidade da paginação, alterações concorrentes, reexecução, valores não nulos ausentes e falhas ainda integra o plano de testes. A igualdade com Excel é de apresentação: frações descartadas pelo Excel permanecem no RAW. Não há Java novo, recebimento SQL destas entidades ou publicação de negócio.

Evidências públicas específicas em `evidencias/`; RAW/Excel integrais somente no diretório privado proprietário.
