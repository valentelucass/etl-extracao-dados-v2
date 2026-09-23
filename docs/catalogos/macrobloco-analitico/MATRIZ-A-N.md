# Matriz A–N — construção local

CONSTRUÇÃO_LOCAL_CONCLUÍDA no recorte sintético adotado. Não equivale a aceite
real dos pais nem a prontidão produtiva. O pedido congelado tem SHA256
990e481060eefd1c1ee01c168d46ba85c7d384edd5d61ce746ef9974d8d01a33.
Resultados e revisões estão em [verification-summary.json](verification-summary.json);
as tentativas falhas permanecem no diretório privado da rodada.

| Frente | Componente e comportamento | Teste/camada e evidência | Limite preservado |
| --- | --- | --- | --- |
| A | Inventário inicial2704, before byte a byte e cadeia orientação→expansão→analítico | inventory-before; sucessão exata17arquivos; validadores com evidência privada | HEAD sujo não é a base do diff |
| B | [Pesquisa](PESQUISA.md), corpus legado e ADR0050; fonte/hash de cada inventário | Contratos tipados, matriz por campo e snapshot SQL observado | Campos Raster/identidades reais não ratificados por fixture |
| C | V055/056/071/076/078: seis dimensões, releases, vigências, rekey, papéis e políticas | Dimensions8IT, References3, FleetReferences3, ManifestGates4 e consumo no cenário | Nome, placa e documento não viram chave; owner nominal pendente |
| D | V052–054/093;51declarações Raster, envelopes, pai/paradas, ledger de folhas, histórico, cap e SQL13 | RasterParser/Transport/FieldGate; RasterIT/Identity/Proofs/Transit/CivilTime; cap256 e concorrência real | Identity/source contract externos; nenhuma posição é identidade |
| E | V057–063/094;90atributos, MAT01 por Frete canônico/PE/CB, previsões/volumes/performance/lineage | FreightAttributes/Operational/Fallback/LocationQueries, XML NFS-e, cenários de0,01, concorrência e escala | BRL/MAJOR local e unidades explícitas; decisão real de vínculo/referência pendente |
| F | V067/094; contribuições MAN/INV, agregação por dia/filial/Geral e recomposição de recortes antigos/novos | CollectorsIT2 + CollectorsGatesIT17, cenário quatro modos, disputa de procedure e escala | Política de filial sintética; ambiguidade não recebe TOP1/MIN |
| G | V064–078/094/095;92campos MAN, coortes exatas, composição selada e MAT05, cinco papéis de frota | ManifestPreparation/Capture/Manifests/ManifestGates/FreightPaths/ManifestCompositions; receita sem fan-out | Capacidade KG por binding vigente; fleet nominal continua pendente |
| H | [19contratos/673colunas](matriz-colunas-final.json),971colunas físicas; leitor JDBC tipado e limitado | QueryReaderIT2, provas diretas por contrato,19seleções reais do JAR e metadata SQL | Nomes pub.analytic_lab_sql_01..19; nenhum alias produtivo |
| I | PUB01–08: documentos, status, ausência, tarifa, região, traduções, consumo ativo e Transit Time | Matriz por coluna, FinancialQueries, CollectionQueries, Quotes, FreightLocationQueries, InventoryIncidentQueries, Monitoring e RasterTransit | SQL10 interno e sem payload/segredo; nulidade não é valor inventado |
| J | AnalyticScenarioRuntime: nove Data Export+Usuários+Raster, hidratação, referências, relações e cinco fatos; quatro modos | RuntimeIT3, PlanIT3; JAR scenario/recompose/replay/status/query; fronteira contígua por publicação terminal | Entradas diárias e materialização FULL3dias são janelas distintas; rollback não é durabilidade entre processos |
| K | Kernel Sweep existente ligado somente à aplicação sintética de Coletas; duas observações independentes e reaparecimento | CollectionSweepIT5/IsolationIT2; ScenarioIsolationIT4,19queries e processo degradado | Nenhum sweep real ou de Raster habilitado; CAP conclui nos cenários de falha dependente |
| L | Oráculos manuais, tri-state, precisão, coortes, bindings, scope/receipt, concorrência física e recuperação após rollback | Matriz de regras/colunas,378IT únicas, claim/PLAN/Raster/MAT01/02/05 em SPIDs distintos | Sem aceite de dados reais, COMMIT/crash durável ou paridade externa |
| M | Java17, heap512MiB; verify integral aprovado; provas afetadas após mudança apenas em testes; JAR40 | 1946unitários,4skips históricos;233IT anteriores exatas;4/16/32/16 e284planos; scanner/contraprovas/style/coverage | Falha64 preservada; sem platô/SLO, grants SHOWPLAN ou audit externo presumido |
| N | [Relatório](RELATORIO.md), quadro45, capacidades no STATES, sucessão, snapshots, diff completo/revisão e checkpoint | Manifesto/hashes e validadores finais; revisão do delta contra inventário inicial | Construção32→37/45; aceite67/115 separado, gates produtivos inalterados |

Cada arquivo em contratos-colunas-inicial.json é a fotografia inicial, inclusive
seus marcadores PENDING históricos. A matriz-colunas-final.json registra a revisão
implementada; não reescreve a evidência de descoberta. As definições SQL observadas
registram expressões, CTEs, joins, filtros, labels e fallbacks; não são instalador.

As cinco unidades novas são somente MAT-01, MAT-02, MAT-05, V2-034 e V2-037.
Dimensões aprofundam V2-035; K aprofunda V2-013; não há contagem por classe/view.
