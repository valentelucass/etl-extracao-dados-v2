# Preparação e qualificação dos contratos do dashboard — 0755

Referência: 08/11/2026. Ler junto do [índice central](README.md). Esta revisão complementa o levantamento 0754 e preserva suas evidências.

**Resultado:** os 134 campos inicialmente sem adaptação explícita receberam expressão candidata, origem, regra de composição, dependências de publicação e critérios de aceite. As expressões foram descritas pelo SQL Server nos objetos reais do Shadow, sem escrita. Foram executadas verificações lógicas e leituras agregadas dos dados atuais. **Isso não conclui implementação, equivalência dos dados publicados ou consumo real pelo dashboard.** O novo banco não existe e as publicações analíticas consultadas no Shadow estão vazias.

## Entregas e evidências

Diretório: `C:\Users\suporte\Documents\Codex\2026-11-08\etl-v2-sql-server-dashboard\outputs`.

| Arquivo | Conteúdo / limite |
| --- | --- |
| `solucoes-134-campos-0755.json` | Uma solução explícita por campo: expressão, FROM candidato, parâmetros exigidos, granularidade, evidência V1 e critérios posteriores. Nenhuma linha declara aceite semântico. |
| `matriz-958-contratos-0755.json` | Disposição dos 958 pares originais; os nomes/aliases restantes conservam revisão semântica pendente. Não é uma seleção automática dos candidatos. |
| `sql-qualification-0755-attempt1.json` | Descrição de tipos dos 134 campos e de 23 objetos candidatos, casos sintéticos executados no engine, regras sobre dados atuais e restrições reais do Shadow. |
| `sql-field-profiles0755-attempt1.json` | Tentativa inicial do perfil; três conversões proibidas revelaram candidatos inadequados. Preservar esta falha. |
| `sql-profile-diagnosis0755.json` | Diagnóstico pelo engine: INT→DATE, DATE→TIME e DATETIMEOFFSET→BIGINT não são conversões diretas válidas nesses candidatos. |
| `sql-field-profiles0755-attempt2.json` | Perfil agregado dos campos; 887 campos lidos sobre o conjunto completo de cada objeto. Duas consultas completas atingiram aproximadamente o prazo de 10 segundos e não foram aprovadas. |
| `sql-qualification-followup0755.json` | Os outros 71 campos avaliados em amostras TOP(200), teste dos limites de arrays e confirmação dos textos NF acima de 1024. A amostra não substitui varredura integral. |
| `escopo-perfil-958-campos0755.json` | Tipo candidato usado em cada perfil. A primeira sugestão de metadados foi usada para diagnosticar riscos, não escolhida como contrato aprovado. |
| `avaliacao-candidatos-0755.json` | 24 pares com alertas de tamanho/conversão, com resolução indicada. Candidato rejeitado não significa dado inválido na origem. |
| `restricoes-producao-0755.json` | 278 ocorrências estáticas de travas/sintético/segurança nas migrations, com arquivo/linha; não são 278 erros. |
| `resultado-qualificacao0755.json` | 76 verificações aprovadas no escopo declarado e respectivos resultados; sem aceite produtivo. |
| `Correcao_Conciliacao_Dashboard_0755.patch` | Correção revisável para SQL do indicador e seu texto no dicionário de KPIs. Preparada, não aplicada. |
| `correcao-conciliacao-preparada0755.json` | Hashes dos dois arquivos atuais, escopo dos testes e testes de aplicação ainda necessários. |
| `verificacao-preservacao0755.json`, `manifesto-qualificacao0755.json` | Preservação e integridade da entrega 0755; separados dos manifestos históricos. |

As projeções completas usadas na descrição ficam em `work/projection-*-0755.sql` no mesmo chat. São `SELECT TOP(0)` com parâmetros obrigatórios, destinados à descrição de tipos. **Não são instaladores nem consultas de publicação prontas.** Os scripts de preparação, planos SELECT e recibos de memória também permanecem em `work`. Não aplicar os scripts de investigação como migrations.

## Solução dos 134 campos, por grupo

| Grupo | Campos | Solução definida | Dependência que permanece para implementação |
| --- | ---: | --- | --- |
| Calendário | 19 | Usar `ref.calendario`: data, chave, dia ISO, feriados e referência de faturamento; derivar ano/mês/trimestre/semana e nomes sem depender de idioma/DATEFIRST. | Selecionar e carregar uma release real ratificada, cobrindo o período e a referência anterior. O registro da release informa a versão, não a data de extração. |
| Fretes financeiro | 9 | Adaptar responsável de destino, documento normalizado e status; recuperar estado fiscal da relação Frete/Fatura; separar snapshot e reconciliação. | Provar a relação fiscal e cardinalidade, catálogo de status e publicação coerente. O JOIN fiscal `1=0` na descrição valida apenas tipos; não é solução de dados. |
| Coletores | 21 | Mapear issued/unloaded/scanned/incomplete/percentage e flags; ligar filial e competência; gerar metadados de publicação e hash canônico. | Referências por vigência, prova de unicidade, filial operacional e tombstones. A view atual active=1 não oferece todo o histórico de exclusão. |
| Faturas | 10 | Limpar documento, derivar Faturado/Aguardando Faturamento por existência do documento; preservar fallback de CNPJ/nome e remoção de TAB/NBSP; separar exclusão, reconciliação e publicação. | Identidade raiz/parte, deduplicação e publicação. Texto raw de status não substitui a regra calculada do dashboard. |
| Indicadores de Fretes | 51 | Adaptar os dados de `analytic_lab_freight_operational` e a projeção de Fretes; preservar indicador PE/CB, datas completas, flags, pesos e valores. | Garantir vigência/cardinalidade das dimensões, competência por indicador, exclusão e contexto da publicação. Não somar PE e CB como fretes distintos em uma métrica de total de fretes. |
| Auditoria | 8 | Usar execução/template, início/fim, registros entregues e páginas; traduzir estados terminais e expor código sanitizado. | `noop_count` de extração não é o noops de materialização; a instrumentação/contrato precisa defini-lo. Não fabricar zero. A CASE atual cobre nove templates, mas o catálogo produtivo deve cobrir também apoios. |
| Raster — viagens | 9 | Mapear campos tipados de duração/prazo/rota, manter horário extraído, atividade e metadados de origem. | Confirmar unidades do fornecedor, reconstrução/preservação do envelope de metadata e evento real de exclusão. |
| Raster — paradas | 7 | Relacionar trip_key/stop_key no mesmo run, preservando presença, valor raw, sentinelas e diferenças de tempo. | Confirmar unidades e publicação; não relacionar apenas por ordem nem atribuir data de exclusão com base apenas em active=0. |

Campos de contexto como `@published_at_utc`, `@deleted_at_utc`, `@reconciled_at_utc`, `@source_metadata`, `@canonical_row_payload` e `@extraction_noops` são entradas obrigatórias de uma implementação futura. Não têm valor padrão nem foram abastecidos na qualificação de tipos. Sua presença torna explícita a informação que falta publicar; não demonstra que essa informação já exista numa tabela operacional.

## Testes e avaliações realmente executados

- 94 consultas bem sucedidas no conjunto final e no diagnóstico, exclusivamente SELECT/CTE e funções de metadados. Nenhum DDL/DML, migration, fonte externa ou endpoint executado.
- Descrição de tipos das oito projeções, cobrindo os 134 campos, sem erro de compilação e sem nomes duplicados; descrição de 23 objetos candidatos reais. Não se trata de comparação de valores V1/V2.
- 46 casos sintéticos executados pelo SQL Server: conciliação, calendário ISO, documentos/NBSP, documento/status de Faturas, performance, utilização de coletores, fuso, escolha da última publicação comprometida e arrays específicos de Faturas. Não são execuções do runtime Java.
- Faixas adicionais verificadas: 14 casas decimais, overflow de INT, largura de comentário e limite de armazenamento. O engine demonstrou arredondamento em DECIMAL(28,8), limite de INT e truncamento numa conversão explícita a NVARCHAR(1024).
- Faturas: regra de status conferida nas 15.767 linhas do fato atual, zero divergências.
- Calendário: chave civil, dia ISO e ausência de referência futura conferidos em 4.780 linhas, zero divergências.
- Indicadores de Fretes: flags de prazo e código calculado conferidos em 39.783 linhas, zero divergências.
- Coletores: total e flags de conferência/bipagem conferidos em 342 linhas, zero divergências.
- As quatro verificações anteriores representam 60.672 linhas em quatro conjuntos diferentes, não 60.672 entidades distintas nem paridade com o V2.
- Perfil por campo: 887 campos sobre os objetos completos; 71 em amostras de até 200 linhas, após limite de leitura em Inventário e Raster Transit Time. Preservar as falhas e o escopo menor, sem promover amostra a integral.
- O patch da conciliação foi conferido por `git apply --check`; o primeiro formato não aplicava por finais de linha, foi preservado em `work`, e o arquivo final manteve os finais de linha corretos. A conferência não aplicou o patch.

As 76 verificações automáticas registradas aprovam somente essas condições. Vazio não recebeu aceite de dados e teste sintético não recebeu aceite de fornecedor. Não foram executados Maven/JUnit, TypeScript/lint/Vitest, aplicação HTTP ou navegador nesta unidade; a correção permanece como proposta de código para a implementação posterior.

## Diferenças comprovadas e tratamento

**Conciliação de Contas a Pagar.** Na view completa: 17.131 linhas, expressão atual conta 15.273; a comparação exata conta zero; 1.858 valores são NULL. A proposta usa igualdade com “conciliado”, removendo espaços nas extremidades e ignorando maiúsculas/minúsculas. Os nove casos passaram, incluindo “Não conciliado”, valores vazios e textos parciais. Manter denominador, filtros de período e ACL; atualizar o dicionário de KPIs na mesma entrega. A proposta não foi aplicada ao repositório nem ao runtime.

**Normalização de documentos.** A função atual do V2 não remove NBSP; a regra de Faturas V1 remove. O caso com NBSP preservado pela função atual e removido pela adaptação foi reproduzido no engine. Aplicar a regra de Faturas em sua publicação, preservando o valor original; não mudar globalmente todas as normalizações sem rever seus contratos.

**Status e calendário financeiro.** `status_processo` do fato é derivado de documento, e o status de pagamento segue precedência: sem documento → sem_fatura; com baixa → baixado; sem vencimento → sem_vencimento; vencido na data-base → vencido; demais → a_vencer. Competência mensal usa fallback da emissão específica, emissão da fatura e data civil do CT-e. Os candidatos de tipo DATE/TIME obtidos por aliases parciais foram recusados. YYYYMM deve ser derivado como inteiro da data correta.

**Notas fiscais.** Na projeção atual de Fretes, três valores de `NF` excedem 1024 unidades UTF-16; o máximo observado foi 2254. Não aceitar a projeção candidata com largura menor como equivalente. Preservar a coleção completa e dimensionar armazenamento/publicação; provar a captura integral no mapper antes de afirmar que ampliar a view resolve.

**Mensagens e nomes de entidades.** Há valores atuais maiores que as larguras candidatas. Mensagem operacional sanitizada e detalhe técnico restrito têm contratos diferentes. Definir largura e conteúdo permitido; não truncar silenciosamente texto de erro privado para afirmar equivalência.

**Restrições sintéticas.** Foram lidos 30 CHECKs reais que restringem identidades/escopo a valores sintéticos: 11 em `ctl`, 11 em `stg`, quatro em `ref`, três em `core` e um em `recon`. Exemplos: `ctl.analytic_lab_run` exige `SYNTHETIC_ANALYTIC_LAB`, e o registro analítico exige chaves `synthetic-*`. Portanto, habilitar produção exige mais que um perfil JDBC e aliases. Criar contratos produtivos explícitos para origem, tenant, chave, referência e proveniência, com validações próprias; preservar os contratos e testes de laboratório. Não retirar todas as travas por substituição textual.

## O que falta para concluir de fato a compatibilidade

1. Implementar no novo alvo os contratos produtivos e as adaptações especificadas. Antes disso, fechar o pacote de instalação com baseline/V106/V107, precisão, precondições de collation e tratamento dos efeitos de segurança conforme proibição vigente de alterar contas/permissões. A primeira implementação combinada continua sendo criar `ETL_SISTEMA_V2`; esse ato ainda não aconteceu.
2. Carregar referências reais ratificadas e uma publicação não vazia, com identidade e reconciliação de origem provadas. As cinco fontes consultadas para presença de dados — Fretes operacional, Coletores, Faturas, calendário e Raster — estavam vazias no Shadow. Não há como confrontar suas linhas produtivas com a expectativa nesta fotografia.
3. Aplicar/qualificar o patch da conciliação na fase de implementação; executar regressões Java e frontend, conferir KPI e filtros/ACL reais. Em seguida, testar SQL→API→tabela/exportação, recorrência, falha/retomada e publicação coerente. Teste do algoritmo de seleção em CTE não substitui a implementação transacional.

Também permanecem: ampliar a leitura integral dos 71 campos sem exceder o orçamento de consulta — por partições indexadas e plano de execução revisado — e conferir semanticamente as correspondências de nome/alias. Os 24 alertas têm tratamento indicado; não são 24 correções já implantadas.

Não marcar esta frente como compatibilidade integral concluída. Esta unidade entrega especificações explícitas, testes reais nos limites declarados, candidatos recusados e uma correção revisável; os gates acima dependem da implementação e de dados que ainda não estão publicados.
