## Resultado final 0762 — onze entidades recebidas; Excel divergente; equivalência de 100% pendente

[Checkpoint0762](../continuidade/checkpoints/0762-onze-entidades-requalificacao-e-confronto-excel.md). Requalificação final v5 completa: 80.981 registros/3.319.248 valores/88 partições, zero rejeições, HTTP→SQL integralmente conferido; view latest e 88 runs exatos, zero RUNNING. Código final v6: 59 atributos qualificados, 22 filtros, 120 testes; replay de 36.283 linhas/729.631 valores idênticos ao banco, incluindo dois ctd_ant_document STRING iguais ao Excel. Não houve nova execução de rede v6. O segundo disparo real após 3607.645s falhou Fretes; ajuste25 passou na terceira carga manual. Task Disabled/LastResult1/STOP preservado; nenhum loop permanente.

Excel: divergente; equivalência de 100% pendente. Template 11067: 39 cabeçalhos exatos; origem 209 / Excel 212; ocorrências completas iguais 208; somente origem 1 / somente Excel 4; equivalência integral=False; arquivo ESL 1778129. Template 12072: 20 cabeçalhos exatos; origem 36.074 / Excel 36.166; ocorrências completas iguais 35.953; somente origem 121 / somente Excel 213; equivalência integral=False; arquivo ESL 1778123. Diferenças conservadas; snapshot comum não comprovado. Dados anteriores/acessos/SQL16.0.1000.6/001/002/16pins0759 preservados. Sem publicação/corte dashboard/V1. Notas abaixo são históricas; não repetir fontes encerradas.

# ADR0081 — Recebimento produtivo das onze entidades

Data:09/11/2026. Decisão aplicada ao schema e implementada no runtime; qualificação integral em andamento nos recibos0762.

## Contexto

O usuário esclareceu que a extração completa deve acionar a rotina operacional, buscar todas as entidades, organizar os dados no banco e aguardar60min após terminar. Exigiu confronto integral com Excel oficial para Consolidação11067 e Ocorrências12072. O recebimento0759 qualificava apenas Faturas4924; a captura finita0761, embora usasse os onze mappers, não gravava SQL e não satisfazia o pedido ampliado.

As restrições vigentes preservam ETL_SISTEMA/V1, DASHBOARDS, contas, grants, identidades/ACLs, serviço, TLS e SQLServer16.0.1000.6. O alvo próprio ETL_SISTEMA_V2 e o acesso dedicado já estavam autorizados. Scripts001/002, pacotes0759 e seu histórico não devem ser reescritos. Nenhuma chave de negócio está qualificada para as duas fontes novas; Ocorrências pode repetir uma minuta legitimamente.

## Decisão

1. Evoluir o recebimento mediante003, catálogo fechado11entidades/508nomes de campos, campos exatos e FK confiáveis. Adicionar parâmetros opcionais às procedures existentes, preservando defaults de Faturas e as cinco permissões já concedidas. Não criar grants ou identidades. Registrar a aplicação real no diário existente, sem inventar histórico Flyway.
2. Reutilizar as nove aplicações anteriores e os dois novos extratores através da composição de bootstrap. Não importar módulos de negócio na plataforma. Localização recebe os recortes literais do JSON HTTP exigidos pelo seu mapeador; JSON reconstruído não constitui evidência lexical.
3. Receber por entidade/data/execução/ordinal, conservar duplicatas e o registro completo, exigir terminal vazio real e aceitação dos mappers antes do store. Gravar dados, STORED e cobertura na mesma transação; comparar todas as ocorrências e hashes após commit. A cobertura é por escopo/entidade/data, nunca inferida por MAX(data).
4. Executar D−7..D0 inclusive e até quatro lacunas antigas por entidade, respeitando a âncora explícita e os limites da campanha. Não filtrar Ocorrências por updated_at nem Contas a Pagar por created_at. Manifestos não expõe sua data de serviço entre os91campos; registrar a limitação, sem inventar substituto.
5. Separar o supervisor externo do ciclo Java. A qualificação possui dois ciclos, sem sobreposição, com60min após completedAt do primeiro(+2s de margem). Falha para a campanha; êxito do segundo desabilita a própria task. Não ativar supervisor permanente ou trocar dashboard por inferência.
6. Manter limites explícitos, STOP429/mutex e quota2GiB. Runtime0762-v5 usa25raízes por página em Fretes após timeout observado nos perfis100/50 e prova causal finita das páginas27/28per25 do mesmo trecho. Demais fontes usam100. Não há retry/redirect. Resultado perdido/incerto exige reconciliação autoritativa antes de repetir.
7. Comparar os Excel oficiais com o mesmo escopo e todas as colunas, tuplas completas e multiplicidades, usando somente regras de apresentação qualificadas. Preservar frações, arrays, números e nulos no RAW/SQL; não alterar dados para obter igualdade. Captura completa não prova snapshot transacional de origem mutável.

## Evidência e consequências

003 foi aplicada em transação. Os10010registros/23runs/12coberturas existentes e os hashes de dados/grants/usuários/roles permaneceram iguais. Treze casos de proteção passaram; nove tentativas de controleFAILED não gravaram ocorrências/cobertura. O runtime dispõe de116testes aprovados e45revalidados, além de replay literal e de metadados reais.

A campanha a1 demonstrou um defeito no gerador do catálogo: leitura CP1252 de rótulos UTF8. Foi recusada declaradamente; somente Sinistros recebeu8datas/9linhas. A prova causal mostrou os11INFO originais/novos iguais emUTF8. O recurso v2 corrige a leitura e conserva comparação estrita, inclusive rejeição de rótulos adulterados. SQL003 não mudou porque seus508nomes sãoASCII. A campanha a2 usa runtimev3 congelado; o aceite final depende dos recibos de execução, conferências independentes e Excel.

O recebimento fica organizado em stg/ctl e na view stg.production_export_latest. STORED não significa publicação de negócio: core/mart/pub e consumidores conservam seus gates. Campos ainda sem exemplo não nulo ficam opacos, com wire/valor original, sem tipo ou PK inventados. O histórico de todas as tentativas é conservado. Depois de receber novas entidades, retorno ao CHECK antigo de Faturas exige preservação planejada; não apagar dados para fazer rollback. Ver o runbook0762 e STATES para o estado efetivamente comprovado.

## Qualificação adicional de tipos e isolamento — runtime0762-v4

A janela completa de 02–09/11 forneceu valores não nulos para os seis atributos de Ocorrências antes opacos: occurrence_schedule_at (376), ice_ttt_solution_deadline (448), ice_ttt_solution_at (163), ice_ttt_solution_type (163), ice_ttt_soe_description (163) e ice_ttt_solution_comments (39). O contrato versionado ocorrencias-12072-0762-v2.json qualifica os três primeiros como Instant com offset de origem preservado, e os três últimos como textos de até4.000 unidades UTF16, sem enum fechado e sem truncamento. Nulo continua distinto de ausência; forma errada é recusada. Novos testes cobrem offsets/fração, null, objetos em texto, código futuro e fronteira4.000/4.001. As 35.129 linhas originais passaram no novo mapper. As contagens são desta amostra, não restrições de domínio.

Somente ctd_ant_document em Consolidação continua opaco por falta de valor não nulo observado; não inferir seu tipo por nome. As duas entidades conservam39/20 atributos e os filtros já qualificados. Os mappers das nove entidades anteriores e os limites globais legados não foram relaxados.

O componente de banco evoluído está em AllEntitiesIngestionStore.java. ProductionIngestionStore.java e os16 pins do runtime0759rev2 são idênticos aos originais. Runtime0762-v4 usa o mesmo SQL003 já aplicado e identifica seu hash e contrato no recibo; nenhuma migração adicional, grant, conta ou corte de dashboard foi introduzido.

Aceite de Excel é comparação integral de todos os atributos e multiplicidades no mesmo período, com regras de apresentação provadas. Divergência entre duas consultas em momentos diferentes deve aparecer no resultado; o recebimento exato no SQL não constitui por si equivalência100% ao Excel posterior.
