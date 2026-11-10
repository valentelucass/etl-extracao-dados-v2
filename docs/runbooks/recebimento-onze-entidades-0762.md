## Resultado final 0762 — onze entidades recebidas; Excel divergente; equivalência de 100% pendente

[Checkpoint0762](../continuidade/checkpoints/0762-onze-entidades-requalificacao-e-confronto-excel.md). Requalificação final v5 completa: 80.981 registros/3.319.248 valores/88 partições, zero rejeições, HTTP→SQL integralmente conferido; view latest e 88 runs exatos, zero RUNNING. Código final v6: 59 atributos qualificados, 22 filtros, 120 testes; replay de 36.283 linhas/729.631 valores idênticos ao banco, incluindo dois ctd_ant_document STRING iguais ao Excel. Não houve nova execução de rede v6. O segundo disparo real após 3607.645s falhou Fretes; ajuste25 passou na terceira carga manual. Task Disabled/LastResult1/STOP preservado; nenhum loop permanente.

Excel: divergente; equivalência de 100% pendente. Template 11067: 39 cabeçalhos exatos; origem 209 / Excel 212; ocorrências completas iguais 208; somente origem 1 / somente Excel 4; equivalência integral=False; arquivo ESL 1778129. Template 12072: 20 cabeçalhos exatos; origem 36.074 / Excel 36.166; ocorrências completas iguais 35.953; somente origem 121 / somente Excel 213; equivalência integral=False; arquivo ESL 1778123. Diferenças conservadas; snapshot comum não comprovado. Dados anteriores/acessos/SQL16.0.1000.6/001/002/16pins0759 preservados. Sem publicação/corte dashboard/V1. Notas abaixo são históricas; não repetir fontes encerradas.

# Recebimento das onze entidades — 0762

## Escopo e contrato

O acionamento usa o mesmo conjunto de aplicações da captura finita: nove mappers existentes e os extratores próprios de Consolidação11067 e Ocorrências12072. `ProductionAllReceivingMain` compõe `ProductionAllReceivingCycle`; a plataforma não importa módulos de negócio. Cada execução usa datas civis de São Paulo, D−7 até D0 inclusive, e recebe todas as onze fontes. O modo RANGE permite até oito datas explícitas. A captura finita0761 isoladamente não satisfaz a qualificação operacional.

As tabelas de recebimento conservam cada ocorrência física, inclusive duplicatas, por execução/data/ordinal. `ctl.production_ingestion_coverage` aponta a última execução completa por escopo de origem, entidade e data. O escopo deriva da origem e do hash do token, sem publicar o segredo. Não se usa `MAX(data)` como prova de continuidade. O planejamento busca lacunas efetivas desde a âncora, recebe até quatro datas antigas por entidade e sempre visita as oito datas recentes. A âncora02/11/2026 é a da campanha de qualificação; não define uma política definitiva de histórico inicial.

O catálogo fechado possui onze entidades e508campos: Consolidação39, Ocorrências20, Coletas31, Fretes114, Manifestos91, Cotações37, Localização26, Contas a Pagar28, Faturas52, Inventário26, Sinistros44. Fretes114 é o relatório amplo observado; não confundir com o subconjunto37 de outro contrato. Manifestos não devolve `service_date` entre os91campos: registra-se a ausência; o filtro de origem continua nessa data e não se substitui por `created_at`.

## Gravação e conferência

`003_all_entities.sql` evolui o recebimento produtivo em uma transação, preservando001/002, o diário real de instalação e as assinaturas antigas de Faturas. Não concede permissões nem modifica contas. O runtime existente usa somente as cinco procedures já concedidas. O catálogo é finito, os nomes de campos são exatos, nulos explícitos contam como campos, nomes duplicados/extras/faltantes são recusados. A data é conferida também no SQL; frações são removidas apenas da expressão de validação civil para impedir arredondamento à meia-noite, preservando integralmente o envelope original.

Cada data inicia uma tentativa RUNNING antes dos DATA. Apenas captura terminal, completa e inteiramente aceita pelos mappers pode chegar ao store. O commit grava ocorrências, estado STORED e cobertura juntos. O Java relê todas as ocorrências pela procedure restrita à execução, compara conteúdo, ordinal, contagem e SHA256UTF16LE de cada envelope. O recibo só marca `stored`/`sqlEveryOccurrenceExact` depois dessa releitura. Falha depois de entrar no store pode representar resultado não confirmado; consultar a execução autoritativa antes de repetir. `fail` não rebaixa STORED.

`stg.production_export_latest` organiza o recebimento corrente por fonte, entidade, relatório, data e ordinal. A camada é recebimento: core/mart/pub de negócio e o dashboard não foram promovidos por esta unidade. Os 59 atributos novos têm tipo qualificado: Ocorrências v2 inclui três Instant e três textos opcionais; Consolidação v2 inclui `ctd_ant_document` como String após dois valores reais confirmados no Excel. Não se inventam tipos numéricos ou chaves únicas de negócio. Código final v6 reproduz integralmente as observações da captura completa v5, sem nova consulta ou gravação.

## Limites e operação

O lançador é `scripts/operations/Invoke-AllEntitiesReceiving0762.ps1`. Credenciais existentes DPAPI e perfil TLS validado alimentam stdin do Java17; não há segredo em argumento/arquivo novo de configuração. O runtime de noveJARs fica congelado fora do Git, com JDBC13.4 já qualificado, sem mudar dependências do Main/Shadow antigos. O manifesto verifica todos os JARs e scripts; JAR extra é recusado.

Fonte:105min/2000HTTP,200páginas e100000linhas por entidade/data,30s por chamada,10MiB por resposta, intervalo3s após conclusão, zero retry/redirect. Runtime0762-v5 usa25raízes por página em Fretes após timeout do ESL nos perfis100/50 e prova finita das páginas27/28per25 do mesmo trecho; as demais fontes usam100. Página curta não encerra; exige array vazio literal. Respeitar mutex e STOP429 compartilhados; nunca apagar STOP para sondar. Payload por partição é limitado antes do store. A árvore inteira usa o executor compartilhado2GiB, uma tarefa pesada por vez.

O supervisor `Invoke-AllReceivingQualification0762.ps1` executa campanha finita de dois ciclos, sem sobreposição. Após êxito integral do primeiro, agenda a própria task para60min após `completedAt`, com2s de margem. O segundo inicia processo novo e lê a cobertura durável; a espera é medida nos recibos. Ao terminar dois ciclos, desabilita a própria task. Falha cria STOP da campanha e não reexecuta automaticamente. Essa campanha não ativa um serviço permanente. O agendamento conserva o usuário Windows atual e não altera sua identidade/permissões.

## Evidência e recuperação

Recibos técnicos ficam no work do chat; respostas literais, JSONL e Excel oficiais ficam em Armazenamento-Projetos, nunca nos entregáveis. Não apagar tentativas falhas, RAW, manifests ou o histórico de recebimentos para obter tabela verde.

Preparação:116testes passaram;45revalidados após as últimas guardas. Replay literal aceitou73058registros das81partições completas disponíveis, incluindo3899Localizações corrigidas, HTTP0. Prova limitada Fretes:118linhas/100raízes nas páginas9/10per50. Instalação003 preservou10010ocorrências/23runs/12coberturas e hashes de dados/grants/usuários/roles. Treze testes de proteção passaram; nove tentativas de controle terminaramFAILED sem ocorrências/cobertura.

A primeira campanha operacional0762a1 recusou dez entidades por erro local de decodificação CP1252 na geração do catálogo de rótulos. A prova causal confirmou que todos os metadados originais e novos do ESL eram iguais quando lidos em UTF8. Apenas Sinistros concluiu8datas/9registros/396células nessa tentativa; ela não é êxito das onze. O recurso v1 é histórico inválido para rótulos. O recurso v2 corrige a leitura explícitaUTF8, preserva os508nomes de campos e mantém a comparação estrita de filtros, tipos, rótulos, ordem e datas requeridas. A SQL003 contém nomes técnicosASCII e não precisou ser alterada nem reaplicada.

A aceitação final depende da nova campanha completa, commit/readback, cadência real e comparação integral dos dois Excel oficiais no mesmo período. A extração do dia corrente é mutável: divergências temporais devem ser demonstradas, não ocultadas por deduplicação ou consultas repetidas. A alteração não permite reverter003 para o CHECK de Faturas depois de receber outras entidades sem planejamento de preservação; preferir correção adiante, sem apagar dados.

## Qualificação adicional de tipos e isolamento — runtime0762-v4

A janela completa de 02–09/11 forneceu valores não nulos para os seis atributos de Ocorrências antes opacos: occurrence_schedule_at (376), ice_ttt_solution_deadline (448), ice_ttt_solution_at (163), ice_ttt_solution_type (163), ice_ttt_soe_description (163) e ice_ttt_solution_comments (39). O contrato versionado ocorrencias-12072-0762-v2.json qualifica os três primeiros como Instant com offset de origem preservado, e os três últimos como textos de até4.000 unidades UTF16, sem enum fechado e sem truncamento. Nulo continua distinto de ausência; forma errada é recusada. Novos testes cobrem offsets/fração, null, objetos em texto, código futuro e fronteira4.000/4.001. As 35.129 linhas originais passaram no novo mapper. As contagens são desta amostra, não restrições de domínio.

Somente ctd_ant_document em Consolidação continua opaco por falta de valor não nulo observado; não inferir seu tipo por nome. As duas entidades conservam39/20 atributos e os filtros já qualificados. Os mappers das nove entidades anteriores e os limites globais legados não foram relaxados.

O componente de banco evoluído está em AllEntitiesIngestionStore.java. ProductionIngestionStore.java e os16 pins do runtime0759rev2 são idênticos aos originais. Runtime0762-v4 usa o mesmo SQL003 já aplicado e identifica seu hash e contrato no recibo; nenhuma migração adicional, grant, conta ou corte de dashboard foi introduzido.

Aceite de Excel é comparação integral de todos os atributos e multiplicidades no mesmo período, com regras de apresentação provadas. Divergência entre duas consultas em momentos diferentes deve aparecer no resultado; o recebimento exato no SQL não constitui por si equivalência100% ao Excel posterior.
