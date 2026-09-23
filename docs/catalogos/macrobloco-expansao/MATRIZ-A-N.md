# Matriz da entrega A–N

O escopo é a construção sintética local adotada em12/09/2026. A coluna de prova
identifica código executado e camada; o resultado consolidado da revisão está
em [verification-summary.json](verification-summary.json). Falhas intermediárias
continuam nos logs da rodada e no relatório. A matriz não concede aceite real.

| Frente | Entrega integrada | Prova e camada | Evidência | Limite |
| --- | --- | --- | --- | --- |
| A | Contratos/ADRs de grão,151 campos tipados, presença/wire/raw/typed, bindings laterais e política fiscal explícita | ContractTest, FieldCoverageIT e FieldRejectionTest; unitário + JDBC/SQL | ADR0049 EXP01–24, CONTRATO.md, MATRIZ-CAMPOS.csv | Observação de shape não ratifica identidade; horário de comunicação de Sinistros conserva texto sem formato inventado |
| B | CAP8636:28 campos, filtros issue_date+created_at, raiz/parcela/rateio, frescor, pagamento/competência/reducers e consulta | LocalIntegrationIT, AdversarialIT, CaptureFencesIT, FieldCoverageIT | src/main/java/.../modulos/contasapagar; V038/V039/V050; logs físicos | Binding sintético não vira ID ESL da raiz; financeiro exige moeda/unidade/rateio explícitos |
| C | FAT4924:53 campos, título/documento, arrays ordenados, placeholders, CNPJ atributo, fiscal dual e consulta | LocalIntegrationIT, EdgesIT, AdversarialIT, FieldCoverageIT | modulos/faturasporcliente; V038/V039/V043–45 | Série NFS-e permanece sem fonte; política dual não resolvida bloqueia somente dependentes |
| D | INV10633:26 campos, prioridade temporal, filhos físicos, prova cumulativa e relação explícita | EdgesIT e AdversarialIT, inclusive prova antiga positiva e arrays reordenados | modulos/inventario; V038–44 | Minuta/alias/posição não identifica raiz nem cria vínculo automático |
| E | SIN6392:44 campos, ocorrência/minuta/seq distintas, tratamento→abertura, horas exatas, valores e seis arrays compartilhados | FieldCoverageIT, FieldRejectionTest, LocalIntegrationIT, AdversarialIT | modulos/sinistros; V038/V039/V051 | Três arrays de Sinistros corrigidos por V051; formato não ratificado não recebe conversão inventada |
| F | Quatro pipelines reais no streamer/guard/audit/control plane, três batches JDBC por flush, staging e aplicação SQL | Captura completa/vazia/parcial/cancelada, replay/exato/divergente, source/tenant/contrato e recursos | LocalExpansionRuntime, JdbcExpansionStaging/Laboratory; CaptureFencesIT16 casos | Sem DDL nas IT; incompleta não sela nem alimenta materialização válida |
| G | Bindings de FAT/INV/SIN/LOC→Frete, cardinalidades, conflitos, fila com claim/lease/tentativas/disposições e hidratação mínima | DependenciesIT6, RelationsIT3, AdversarialIT, ConcurrencyIT e RecompositionIT | V040/V041/V044; JdbcExpansionRelations/Hydrator | Nenhuma relação artificial CAP→FAT/Frete; lookup por alias/TOP1 ausente |
| H | Labels, calendário e atribuição pagador→filial em releases seladas V2-035a selecionadas por run/revisão/vigência | ReferencesIT4, RevenueIT e InvoicesIT; SQL de lacuna/limite/sobreposição/divergência/replay | V042 e resources/expansion-laboratory/references.synthetic.json | Pagador é referência sintética declarada; sem owner/policy produtiva inventados |
| I | MAT04 por título: UNIQUE/upsert/recibo/history/lineage, aging/business_date, data nula/full e valor sem multiplicação | InvoicesIT10 + EdgesIT estados/fiscal + RecompositionIT; oráculos manuais | V045; JdbcExpansionMaterializations.invoices; invoiceFactsPage | Grão e política fiscal reais pendentes; ausência incremental não apaga título |
| J | MAT03 por Frete: fontes capturadas, termos laterais explícitos, calendário, atribuição, volumes, cancelamento/bloqueio/cortesia, sem dupla receita | FreightTermsIT3, RevenueIT12, ScaleIT e RecompositionIT; expected manual | V046–48; revenue/revenueFactsPage | Receita não é fixture final inserida; paridade nominal e unidades reais não ratificadas |
| K | Planos/slots/recibos SQL, BOOTSTRAP/INCREMENTAL/BACKFILL/REPLAY, lacunas/ordem inversa, seis consultas tipadas e reconciliação SQL | RecompositionIT12 incluindo9 fronteiras; ProjectionsIT8, MainIT10 e testes financeiros | V049; ExpansionLaboratoryExecutor; contratos-consultas.json | Reidratação SQL na mesma transação, sem prova COMMIT/crash; sem aceitar19 views |
| L | Entrada companheira no mesmo JAR: scenario/hydrate/replay/status/query, opt-in, fixtures e validações antes de I/O | MainTest19, MainIT10 e21 processos JAR positivos/degradado/recusas | ExpansionLaboratoryMain, COMANDOS.md e jar-01/result.json | Cada invocação prepara/reverte o próprio cenário; Main padrão dormente |
| M | Provas físicas,233 IT incluindo95 regressões, concorrência de quatro procedures efetivas, três escalas+repetição, planos reais, scanners e gates | Java17/Maven offline; heap512,900s build,240s por escala,60s processo JAR | logs/verify-*, medições, SQL062, resultados do scanner e validações | Falhas preservadas; sem platô/SLO, feed externo ou equivalência física de banco novo |
| N | Quadro funcional45 antes/depois, evidência nas capacidades do STATES, snapshots exatos, manifesto, diff completo/revisão, relatório e continuidade | Validadores de sucessão/contraprovas, inventários/hash/UTF-8 e conferência fonte-build-JAR | RELATORIO.md, quadro-construcao.json, manifesto.json; target/.../diff*.patch |26→32 unidades com mecanismo local no subescopo; sem inflar por classe, pai+filho ou aceite externo |

Todos os arquivos privados de evidência estão em
`target/macrobloco-expansao-20260912-01/`. Os nomes de classes das IT são
`br.com.esl.etl.v2.bootstrap.ExpansionLaboratory<Nome>IT`, salvo indicação
explícita. Os diretórios de relatórios copiados podem conter XML de tentativas
anteriores; a contagem consolidada usa a campanha completa identificada, seu
log e os relatórios conferidos, sem somar tentativas como novos testes.
