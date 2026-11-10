# Resultado 0762 — duas entidades Java e recebimento completo das onze

## Implementação e escopo

Consolidação Detalhada, template 11067, possui 39 atributos. Ocorrências, template 12072, possui 20. Os dois módulos seguem o padrão de DTO, domínio, mapper e extrator nas pastas próprias, reutilizando a plataforma e preservando os nove módulos anteriores. Os 59 atributos e 22 filtros estão documentados. Nenhum atributo continua sem tipo qualificado no código final v6.

A extração completa desta qualificação cobre 2026-11-02 até 2026-11-09, inclusive: oito datas civis de America/Sao_Paulo, último dia intradiário. São todas as entidades e todas as páginas dessa janela; não é todo o histórico do ESL. O modo operacional também visita até quatro lacunas antigas por entidade a partir da âncora explícita. Não havia lacunas antigas neste escopo.

| Entidade | Template | Colunas | Primeira completa | Tentativa agendada | Final completa |
|---|---:|---:|---:|---:|---:|
| Coletas | 6908 | 31 | 1.299 | 1.310 | 1.311 |
| Fretes | 6389 | 114 | 4.532 | 2.888 (parcial) | 4.825 |
| Manifestos | 6399 | 91 | 13.038 | 13.181 | 13.212 |
| Cotações | 6906 | 37 | 1.385 | 1.387 | 1.388 |
| Localização de Cargas | 8656 | 26 | 3.980 | 4.137 | 4.270 |
| Contas a Pagar | 8636 | 28 | 801 | 808 | 810 |
| Faturas por Cliente | 4924 | 52 | 4.349 | 4.509 | 4.634 |
| Inventário | 10633 | 26 | 13.947 | 14.129 | 14.239 |
| Sinistros | 6392 | 44 | 9 | 9 | 9 |
| Consolidação Detalhada | 11067 | 39 | 191 | 203 | 209 |
| Ocorrências | 12072 | 20 | 35.129 | 35.690 | 36.074 |

## Três acionamentos distintos

1. Primeira completa, a2/ciclo1: 2026-11-09T21:46:53.351088300Z → 2026-11-09T22:42:08.658210500Z; 715 HTTP, 78.660 registros, 3.219.723 valores e 88 partições. Releitura independente aprovada.
2. Segundo disparo automático, a2/ciclo2: 2026-11-09T23:42:16.303543Z → 2026-11-10T00:36:02.793373800Z; 701 HTTP. Iniciou 3607.6453325 segundos após a conclusão anterior, sem sobreposição, e falhou em Fretes no dia 06/11, página 14/per50: HTTP 422, timeout informado pelo ESL. Dez entidades completas; Fretes confirmou quatro datas/2.888 registros. O dia falho gravou zero linhas parciais no SQL, mantendo as quatro coberturas anteriores dos dias não concluídos. Não confundir cobertura antiga com atualização completa. WorkerExit1, própria task Disabled/LastResult1, STOP conservado. O intervalo real de 60 minutos foi comprovado; não houve dois êxitos automáticos consecutivos.
3. Correção causal e requalificação: três requisições CURL, páginas 27/28/per25 do mesmo trecho, 90 linhas/50 raízes/114 campos, zero rejeições e mesmos 50 IDs da resposta saudável anterior. Fretes passou para 25 raízes por página; demais fontes permanecem em 100. A única requalificação completa adicional, a3/ciclo1, usou runtime v5: 2026-11-10T00:51:23.223785200Z → 2026-11-10T01:55:39.956692Z, 820 HTTP, 80.981 registros, 3.319.248 valores, 88 partições, zero rejeições. Este terceiro acionamento foi manual e finito; não é o segundo disparo automático reclassificado. Nenhum loop permanente foi ativado.

## Banco e conferência

O worker gravou e releu cada ocorrência com conteúdo, ordinal, hash, escopo, entidade, data e contagem. Outro processo confirmou todas as 80.981 linhas e 3.319.248 células do SQL contra os arquivos recebidos. Outro verificador confrontou HTTP literal → JSONL, valores e multiplicidades, incluindo os 729.631 valores tipados novos. As duas entidades novas completas da tentativa agendada parcial também foram conferidas: 35.893 linhas/721.717 valores/16 partições; a campanha global daquela tentativa continua reprovada.

A view stg.production_export_latest aponta para os 88 latest_run_id da requalificação final e contém 80.981 linhas na janela. Zero RUNNING. Os 10.010 registros anteriores e os hashes de usuários, grants e roles foram preservados. SQL Server 16.0.1000.6, SQL001/002, SQL003 aplicada uma única vez, 16 pins0759rev2 e ProductionIngestionStore.java original intactos. A evolução usa AllEntitiesIngestionStore.java próprio. Recebimento é staging: não houve publicação de negócio nem corte das 26 rotas do dashboard/V1.

## Último campo e código final v6

A carga final trouxe dois valores de ctd_ant_document, ambos STRING, até cinco unidades UTF16. Os dois valores e suas multiplicidades são iguais na coluna correspondente do Excel oficial. Contrato consolidacoes-11067-0762-v2.json qualifica String: conserva espaços e zeros à esquerda, aceita nulo e recusa números/objetos em vez de coerção. O limite local de 4.000 unidades UTF16 não é uma restrição de negócio inferida da amostra; não há truncamento. O contrato v1 anterior continua preservado.

O código final v6 foi validado depois da captura completa v5: 120 testes específicos, compilação, Spotless, Checkstyle e package aprovados. Releitura dos corpos HTTP originais de todas as 36.283 linhas das duas novas entidades, em todas as datas, confirmou 729.631 valores e todas as observações idênticas às já gravadas. Números são comparados por BigDecimal exato; wire, raw, presença, textos e ordem de arrays são preservados. O primeiro ensaio que relia JSONL normalizado foi recusado, corrigido para usar os corpos HTTP originais; diferenças apenas de classe numérica no comparador foram tratadas por igualdade decimal exata, sem tolerância. Não houve outra requisição de fonte ou gravação SQL para qualificar essa restrição do último campo.

Assim, captura operacional completa = v5; código final = v6 com replay integral e idêntico das duas entidades alteradas. O manifesto v6 registra essa fronteira. Não declarar execução de rede v6 que não ocorreu. JDBC, limites, paginação, SQL e nove mappers anteriores não mudaram nessa última qualificação. A primeira tentativa local de build não executou por caminho alterado; foi detectada pela ausência de evidência Maven e não foi aceita como build. Os recibos falhos foram preservados.

Ocorrências v2 qualifica três datas opcionais como Instant e três textos de tratativa. Códigos permanecem abertos. Nenhuma PK de negócio ou deduplicação foi inferida. Manifestos não expõe service_date entre suas 91 colunas; o filtro da origem continua nessa data, sem substituição por created_at. A regressão ampliada anterior tinha 21 falhas reproduzidas com o JAR antigo; não se declara a suíte histórica inteira verde.

## Excel oficial final

Resultado: divergente; equivalência de 100% pendente.

- Template 11067: 39 cabeçalhos exatos; origem 209 / Excel 212; ocorrências completas iguais 208; somente origem 1 / somente Excel 4; equivalência integral=False; arquivo ESL 1778129.
- Template 12072: 20 cabeçalhos exatos; origem 36.074 / Excel 36.166; ocorrências completas iguais 35.953; somente origem 121 / somente Excel 213; equivalência integral=False; arquivo ESL 1778123.

Os dois arquivos finais foram solicitados depois de fechar todas as onze entidades, no mesmo período e sem filtros adicionais. Conferência de todos os cabeçalhos, valores, tuplas completas e multiplicidades, CRC do ZIP, planilha única, nenhuma fórmula e quantidade igual à processada no painel. A criação exibida pelo ESL é a criação do trabalho assíncrono; o instante de snapshot dos dados não é exposto.

No confronto preliminar havia 448 células delivery_status sem tradução comprovada; elas não demonstravam 448 mudanças reais da origem. A tradução foi qualificada pelos 448 pares não ambíguos com as outras 19 colunas iguais, 34.516 controles conhecidos e zero conflitos. O mapa ficou congelado antes dos confrontos posteriores; não se aprende uma tradução das diferenças finais para aprovar o resultado. O primeiro ciclo também foi recalculado com essas regras, preservando o resultado preliminar.

Não se excluiu o dia atual, não se removeram repetições nem se alteraram valores para obter igualdade. As diferenças de quantidade e conteúdo acima permanecem pendentes para o aceite de 100%. A própria origem mudou entre as capturas, com mudanças demonstradas nos corpos HTTP em source-evolution0762.json; isso não prova a causa de toda diferença com o Excel. SQL exato não equivale automaticamente a Excel posterior idêntico.

## Preservação e continuidade

Runtime v6 e nove JARs congelados; v5 e manifesto arquivados intactos. SQL, scripts operacionais e credenciais não mudaram na última qualificação. Graph AST escopado: 55.400 nós/139.518 arestas; restante preservado, sem reclusterização geral ou HTML. Respeitado executor único de 2 GiB, 105 minutos/2000 HTTP, 30 segundos/chamada, 10 MiB/resposta, três segundos após conclusão, 200 páginas/100.000 linhas por data, sem retry/redirect. Tentativas, STOP e evidências históricas mantidos.

Evidências no work deste chat: campaign0762-a2/cycle1/2.json; campaign0762-a3/cycle1.json e memória; independent-sql/source-final-0762.json; independent-new-sql/source-cycle2-0762.json; cadence-attempt0762.json; receiving-failure-atomicity0762.json; receiving-preservation-final0762.json; final-task-state0762.json; native-spec-final-0762.json; native-comparison-final0762.json; presentation-qualified0762.json; final-model-replay0762-v6.json; ant-document-native0762.json; build0762-a10.memory.json/tests0762-a10.log; graph0762-v6.json; preservation-code0762.json. Respostas, JSONL e Excel ficam privados; outputs contêm relatório, tabela CSV/JSON, dicionário, patch próprio, comprovação técnica e manifesto.

Não há execução desta campanha para aguardar. Não repetir as três cargas, reaplicar SQL003 ou apagar STOP. Próxima pendência é reconciliar as diferenças remanescentes com uma revisão comum da origem antes de declarar Excel100%. Ativação permanente, publicação e consumidores continuam com suas fronteiras próprias. Finalizadores antigos que pressupõem dois êxitos agendados ou campo ainda vazio não devem ser usados.
