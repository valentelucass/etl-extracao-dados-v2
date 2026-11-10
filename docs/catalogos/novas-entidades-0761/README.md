## Atualização0767 — Comparação de conteúdo concluída e limite de atualização identificado

Ver [comparação atual](comparacao-excel-0767.md) e [apresentação dos booleanos no Excel](apresentacao-excel-0767.md). As59colunas e737959valores recebidos foram conferidos no SQL; todas as ocorrências extraídas têm confirmação nos Excel observados. O último arquivo de Ocorrências contém36mudanças relacionadas e5novaslinhas, todas explicadas. O filtro updated_at recuperou as5novas,mas não as36mudanças de campos ligados ao frete;garantia de atualização integral por ciclo permanece pendente antesdashboard.90dias adiados por pedido do usuário. Entradas abaixo históricas.

## Resultado final 0762 — onze entidades recebidas; Excel divergente; equivalência de 100% pendente

[Checkpoint0762](../../continuidade/checkpoints/0762-onze-entidades-requalificacao-e-confronto-excel.md). Requalificação final v5 completa: 80.981 registros/3.319.248 valores/88 partições, zero rejeições, HTTP→SQL integralmente conferido; view latest e 88 runs exatos, zero RUNNING. Código final v6: 59 atributos qualificados, 22 filtros, 120 testes; replay de 36.283 linhas/729.631 valores idênticos ao banco, incluindo dois ctd_ant_document STRING iguais ao Excel. Não houve nova execução de rede v6. O segundo disparo real após 3607.645s falhou Fretes; ajuste25 passou na terceira carga manual. Task Disabled/LastResult1/STOP preservado; nenhum loop permanente.

Excel: divergente; equivalência de 100% pendente. Template 11067: 39 cabeçalhos exatos; origem 209 / Excel 212; ocorrências completas iguais 208; somente origem 1 / somente Excel 4; equivalência integral=False; arquivo ESL 1778129. Template 12072: 20 cabeçalhos exatos; origem 36.074 / Excel 36.166; ocorrências completas iguais 35.953; somente origem 121 / somente Excel 213; equivalência integral=False; arquivo ESL 1778123. Diferenças conservadas; snapshot comum não comprovado. Dados anteriores/acessos/SQL16.0.1000.6/001/002/16pins0759 preservados. Sem publicação/corte dashboard/V1. Notas abaixo são históricas; não repetir fontes encerradas.

# Consolidação Detalhada e Ocorrências: implementação Java 0761

Pedido do responsável em09/11/2026: aplicar as duas entidades em código e executar uma extração completa das onze entidades, apresentando os resultados em tabela. Esta unidade implementa a fronteira de extração e os modelos Java. A janela comunicada é02/11–09/11/2026, D-7..D0, oito datas inclusivas. O dia09 é uma fotografia intradiária.

## Contrato e organização

O mapeamento individual de39 colunas/9 filtros de11067 e20 colunas/13 filtros de12072 permanece no [catálogo0760](../novas-entidades-0760/README.md). Os contratos executáveis estão em `src/main/resources/contracts/dataexport/consolidacoes-11067-0761-v1.json` e `ocorrencias-12072-0761-v1.json`. Conservam nomes técnicos, títulos, parsers, filtros, lista de datas obrigatórias e ordenação declarada. As requisições novas não enviam `order_by`, filtros auxiliares nem `updated_at`.

Cada pasta `modulos/consolidacoes` e `modulos/ocorrencias` possui DTO defensivo, mapper, observação de domínio, regra estrutural e extrator. O objeto de cobranças de Consolidação tem modelo e parser próprios. `ExpansionValue` conserva presença, wire, representação e disposição; os limites globais anteriores não foram ampliados. A resposta HTTP literal é preservada separadamente, inclusive representação numérica lexical. A representação `raw` do campo resulta da árvore JSON, sem arredondar o valor numérico.

- `CON-0761-01`/`OCO-0761-01`: todas as colunas exigem disposição de parsing; NULL explícito difere de coluna ausente. A ausência ou coluna extra recusa o contrato.
- Os sete campos sem exemplo não nulo ficam opacos, com presença e wire preservados. Um valor futuro não é convertido para um tipo de negócio presumido.
- `charged_value_details` é um mapa imutável de chaves dinâmicas e `BigDecimal`, com NULL explícito permitido. Estrutura inesperada fica recusada, preservando o original.
- A lista de notas preserva posição e repetições. A apresentação Excel não altera o domínio.
- Identidade física: UUID de captura e ordinal. Nenhum candidato de negócio foi promovido a PK, deduplicação ou MERGE.

## Captura finita e integração

`ReportSnapshotExtractor` recebe transporte e sink, percorre uma página por vez e só encerra em array vazio literal. Página curta continua; repetições permanecem. O consumidor decide o armazenamento. `ExtrairConsolidacaoDataExport` e `ExtrairOcorrenciaDataExport` usam esse pipeline com os respectivos mappers. O caminho anterior de `DataExportStagingPipeline` requer contratos/ordenação próprios e não foi liberado para os templates novos por analogia. O catálogo geral passou a onze entradas; o observador do laboratório antigo recusa explicitamente as duas entradas novas.

`DataExportSnapshotMain` compõe as onze fontes com os nove mappers existentes e os dois extratores novos. `CurlSnapshotWire` executa GET no host fechado, lê a última definição não vazia do .env, incluindo BOM, e fornece o Bearer somente por stdin. O lançador `scripts/runtime/Invoke-DataExportSnapshot0761.ps1` confere o runtime congelado e mantém mutex compartilhado e quota de2GiB.

Limites da campanha:60min,2000HTTP,200 páginas por data/entidade, `per=100`,100000 linhas por data,10MiB por resposta,30s por chamada,3s após cada término; zero retry e zero redirect. Há uma rodada por entidade: falha interrompe suas datas restantes;429 persiste STOP compartilhado e impede todas as demais. Falha parcial não é completude. A extração física tem teto próprio; quando não há raiz de paginação qualificada, o tamanho físico da página não prova o significado remoto de `per`. As raízes conhecidas de Coletas/Fretes e dos relatórios de sequência mantêm a verificação quando há expansão.

INFO atual é confrontado com as colunas e semântica dos contratos novos antes de DATA. Cada linha tem as mesmas colunas do INFO; datas expostas são verificadas na janela civil America/Sao_Paulo. Manifestos91 não expõe a data de serviço do filtro: o recibo distingue essa limitação. Captura até terminal não comprova consistência de snapshot entre páginas da origem mutável.

Respostas literais, JSONL físicos e observações tipadas ficam no armazenamento privado0761. Recibos públicos contêm apenas datas de consulta, status, contagens e razões sanitizadas. Os resultados finais e as pendências da execução constarão no checkpoint0761 e no relatório desta unidade.

## Fronteiras e recuperação

Não há conexão SQL, migration aplicada, publicação, reativação de scheduler ou alteração do dashboard. Recebimento0759 continua específico de Faturas4924; seus scripts001/002, JARrev2 e manifests permanecem preservados. STORED/PUBLISHED, persistência tipada produtiva das fontes novas e rotina permanente exigem seus próprios adapters e provas.

O runtime da captura é uma cópia nova e imutável do pacote Java validado. Dados parciais e falhas não são sobrescritos. Para reexecução, primeiro reconciliar o recibo; uma rodada nova exige namespace novo e causa registrada, sem reconsulta automática de falha. Não houve commit automático.

## Validação

Os testes novos cobrem59 campos, tipos, precisão, fração temporal, enum futuro, nulos, campos opacos, listas/objetos, cópia defensiva, drift, terminal vazio, multiplicidades, erro do sink, limites, origem fechada e STOP429. O replay Java sobre os arquivos0760 já comparados aos Excel oficiais confirmou320 linhas/7179 células iguais, sem HTTP adicional.

A regressão ampliada selecionou836 casos:809 passaram;21 falhas se reproduziram com o JAR anterior0759rev2, e6 casos eram parâmetros antigos que incluíam inadvertidamente os templates novos. Estes6 foram corrigidos preservando o escopo das fixtures antigas e adicionando prova de recusa no caminho legado. A suíte específica posterior passou64/64; o teste adicional de captura física expandida será incluído no pacote final. Não declarar a suíte histórica inteira verde.

## Qualificação adicional de tipos e isolamento — runtime0762-v4

A janela completa de 02–09/11 forneceu valores não nulos para os seis atributos de Ocorrências antes opacos: occurrence_schedule_at (376), ice_ttt_solution_deadline (448), ice_ttt_solution_at (163), ice_ttt_solution_type (163), ice_ttt_soe_description (163) e ice_ttt_solution_comments (39). O contrato versionado ocorrencias-12072-0762-v2.json qualifica os três primeiros como Instant com offset de origem preservado, e os três últimos como textos de até4.000 unidades UTF16, sem enum fechado e sem truncamento. Nulo continua distinto de ausência; forma errada é recusada. Novos testes cobrem offsets/fração, null, objetos em texto, código futuro e fronteira4.000/4.001. As 35.129 linhas originais passaram no novo mapper. As contagens são desta amostra, não restrições de domínio.

Somente ctd_ant_document em Consolidação continua opaco por falta de valor não nulo observado; não inferir seu tipo por nome. As duas entidades conservam39/20 atributos e os filtros já qualificados. Os mappers das nove entidades anteriores e os limites globais legados não foram relaxados.

O componente de banco evoluído está em AllEntitiesIngestionStore.java. ProductionIngestionStore.java e os16 pins do runtime0759rev2 são idênticos aos originais. Runtime0762-v4 usa o mesmo SQL003 já aplicado e identifica seu hash e contrato no recibo; nenhuma migração adicional, grant, conta ou corte de dashboard foi introduzido.

Aceite de Excel é comparação integral de todos os atributos e multiplicidades no mesmo período, com regras de apresentação provadas. Divergência entre duas consultas em momentos diferentes deve aparecer no resultado; o recebimento exato no SQL não constitui por si equivalência100% ao Excel posterior.