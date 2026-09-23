# Entrega — qualificação E2E e pacote executável local

**CONSTRUÇÃO_LOCAL_CONCLUÍDA — A–N no laboratório sintético.** O operador dispõe
de campanha tipada consumida pelo runtime, comparação independente, gates por
escopo, agenda física, journal/retomada e pacote fechado executável fora da árvore
de fontes. O resultado se aplica à revisão e aos recibos vinculados abaixo;
o selo final externo confirma os validadores executados após a última edição.

Construção passou de37/45 para39/45 (86,7%). SomenteV2-038 eV2-039 acrescentam
mecanismo local implementado, integrado e verificado. Os67/115aceites históricos
permanecem separados; os requisitos operacionais dos pais continuam abertos.

## Resultado funcional

O contrato fechado de campanha fixa pacote/JAR/schema/fixture/oráculo, casos,
ondas, dependências, relógio lógico, janelas e limites. Seus dados selecionam
ações compiladas. O plano afeta capturas e procedures existentes; não há
comando, classe, SQL ou destino executável recebido de um manifesto.

As onze entradas alimentam seis dimensões, cinco fatos e dezenove contratos por
seus adapters. Os673campos de negócio têm comparador/caso/regra/origem/exemplo
explícitos;971metadados físicos são conferidos. Dinheiro é exato, nulo difere de
zero, Unicode/instantes/datas civis/offsets e linhagem têm provas específicas.
SQL04 tem candidata, confirmação independente e reaparecimento; a saída vazia
do cenário básico não é sua prova positiva. O harness V2-012 é consumidor real.

São35escopos do DAG. Raster incompleto, frota sem binding, referência financeira
ausente e snapshot Coletas inválido bloqueiam dependentes com motivo. Na campanha
WAVES, Raster fica bloqueado, CAP passa e o dependente é adiado sem filho/recibo.
Uma contraprova aprovada não converte seu dado recusado em PASS_LOCAL.

As cinco políticas temporais usam planner/coordinator/dispatcher existentes.
BOOTSTRAP/INCREMENTAL/BACKFILL/REPLAY, antigo/late data, correção, lookback,
blackout/deadline/catch-up, fonte/execução, gap/frontier e DST23h têm integração
física. COT conserva sua publicação efetiva; capturas cujo contrato é DEGRADED
continuam DEGRADED. Fato, replay ou intervalo incompleto/não contíguo não inventa
avanço incremental.

O supervisor reserva antes do filho e vincula nonce/PID/criação/Java/JAR a
campanha, configuração, logs, recibo e reconciliação. Os quatro pontos de
interrupção passaram. Perda de recibo com exit0 termina OUTCOME_UNKNOWN;
resume consulta o journal e não repete automaticamente. Cada novo processo
reconstrói a fixture revertida. A disputa usa SPIDs distintos/claim real,
cancelamento JDBC e consumo depois do rollback do dono.

## Artefato entregue e execução

- [ZIP final](../../../target/macrobloco-qualificacao-pacote-20260913-01/qualification-final-01/qualification.zip),172membros e9dependências (8JARs+DLL nativa).
- Revisão: <code>3b969cef84a2367e304748b930bbc66b95b3273ad93b5270a55f9d75b31dc540</code>.
- SHA256 do ZIP: <code>54889e1d2153881856a0e68be17b8e770904351d77be10e7b960c56b98aa92d9</code>.
- SHA256 do manifesto: <code>b008ff7bcc5b4e4468db6de01a8bb2e7e9a0395363a4c6c8803e2e9d560e3b2f</code>.
- [Comandos completos](COMANDOS.md): extrair/verificar; inspect, plan, run, status, resume e compare.
- [README empacotado](PACKAGE-README.md) e exemplo config/campaign.synthetic.json executado sem edição no primeiro smoke.

Dois builds offline independentes do mesmo snapshot produziram bytes ZIP e JAR
idênticos, sem normalização posterior. 1996inputs e1015classes/recursos
foram vinculados às duas árvores compiladas e ao JAR. Alteração real de uma cópia
de fonte depois do build foi recusada. Não houve delta em main/test após verify03.

O result.json imutável de cada builder registra a montagem anterior ao smoke
(PACKAGED_NOT_SMOKE_QUALIFIED). A qualificação posterior é demonstrada pelos
recibos das campanhas, verification-summary.json e final-seal.json; não se
reescreve o recibo de montagem para alterar sua fotografia histórica.

O manifesto valida conteúdo, papéis, tamanhos, hashes e compatibilidade antes de
JDBC. SBOM CycloneDX1.6 é validada contra schema oficial congelado e confrontada
com os nove artefatos reais, POMs/licenças/lock/proveniência. Timestamp de build é
fixo; recibos com relógio real ficam fora do payload. A licença nativa Microsoft
Proprietary é distinta da MIT do JAR JDBC. Feed: NOT_EXECUTED; sem assinatura/CI.

O pacote passou17smokes e4escalas,126comandos em21diretórios novos com espaço e
Unicode. JAR, dependências, fixtures, oráculos e configuração vieram do conteúdo
declarado; cwd/classpath dos processos não apontam a src/target/classes/Maven.
Há mais21recusas de configuração/pacote e8adulterações do controle pelo comando
fixo extraído, além de25contraprovas do envelope ZIP. Todas foram preservadas.

## Verify, regressão e medições

Verify físico integral03: **1976unitários,4skips históricos;
417IT em80classes,0skip,0falha e0erro.** As378IT anteriores
permanecem por identidade exata; são39casos novos em19classes. A suíte analítica
anterior de378 era uma união de revisões; não se alega verify antigo de378.
Formato/estilo/arquitetura/cobertura passaram. Os thresholds .80line/.60branch
foram mantidos. Rollback e UTF-8 bruto foram conferidos.

As novas classes físicas incluídas no verify são:

- QualificationCancellationIT: 3 casos.
- QualificationCharacterizationIT: 1 casos.
- QualificationConcurrencyIT: 1 casos.
- QualificationControlIT: 4 casos.
- QualificationDegradationIT: 4 casos.
- QualificationLineageIT: 1 casos.
- QualificationMetricsIT: 2 casos.
- QualificationMonitoringIT: 1 casos.
- QualificationOutputIT: 1 casos.
- QualificationPackageCompositionIT: 10 casos.
- QualificationPhysicalMetadataIT: 2 casos.
- QualificationReplayIT: 1 casos.
- QualificationScenarioVerifierIT: 1 casos.
- QualificationTemporalMatrixIT: 1 casos.
- QualificationTemporalPlansIT: 1 casos.
- QualificationUtcIT: 1 casos.
- QualificationValueOracleIT: 1 casos.
- QualificationVariantsIT: 1 casos.
- QualificationWindowIT: 2 casos.

Os quatro skips históricos preservados são:

- `br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationDeterminismTest/refusesAReparsePointInsideTargetWhenThePlatformSupportsLinks`
- `br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02DeterminismTest/refusesSymbolicLinkInsideReceiptRoot`
- `br.com.esl.etl.v2.contratos.mapping.CotacoesSourceCommandTest/command`
- `br.com.esl.etl.v2.contratos.medicao.MeasurementReceiptWriterTest/refusesASymbolicLinkInsideTheMeasurementRootWhenSupported`

Dois factories unitários históricos publicam o mesmo nome de método em48/53
ocorrências XML. O inventário confere essas multiplicidades contra os XML
anteriores e identifica cada ocorrência; nenhuma IT foi deduplicada/removida.

Escalas do novo caminho extraído, na ordem4/16/32/16, página configurada2:

| Raízes | Registros stageados | Páginas | Bytes | Lotes | JDBC | Duração caso(s) | Heap antes/depois(MiB) | Lag lógico(s) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 4 | 135 | 67 | 191606 | 57 | 489 | 23.121 | 100.65 / 24.10 | 32400 |
| 16 | 543 | 229 | 767391 | 223 | 1080 | 35.332 | 99.76 / 77.88 | 32400 |
| 32 | 1087 | 446 | 1535577 | 446 | 1878 | 51.463 | 100.16 / 33.20 | 32400 |
| 16 | 543 | 229 | 767391 | 223 | 1080 | 36.064 | 101.77 / 75.39 | 32400 |

Registros físicos seguem34×raízes−1, incluindo duplicatas/hidratação/USER/RASTER.
Uma página/lote em voo, retenção final zero por entrada; lotes até20registros.
Teto512MiB/300s por caso,100.000chamadas JDBC,4.096linhas/64MiB/10.000páginas
por entrada; query60s/login5s/socket90s. O controlador da campanha estabelece
420s e o wrapper da prova480s. O conjunto de handles limita64 e recusa o65º
antes de perder o controle de cancelamento. Lag é calculado do plano efetivo.

Foram capturados3planos novos das consultas temporais e284planos de regressão
do cenário no verify. Os novos planos não apresentaram spill nem conversão que
afete o plano. Nenhum grant SHOWPLAN foi solicitado. Esses pontos de heap e
escala não demonstram platô, SLO ou desempenho de fonte real.

## Defeitos encontrados e revisão

As falhas foram preservadas e corrigidas: parâmetros Instant sem Calendar UTC
deslocavam DATETIME2 em3horas; onze adapters passaram a informar UTC e a leitura
do lease foi alinhada. A IT Raster passou a ler UTC explicitamente. Probes de
escala antigos foram ligados aos observadores reais USER/COT/RASTER, mantendo
seus nove casos e atualizando a equação física para as novas entradas medidas.

Verify01 expôs contratos de coleção/registro JDBC não limitados na arquitetura;
28APIs receberam contratos exatos e64handles passaram pela recusa física do65º.
Verify02 expôs os probes antigos e lacuna de cobertura de composição. A suíte
de10IT exercitou os consumidores reais do JAR, cinco ações worker, três filhos
supervisor, retomada/admissão e mutações de pacote; os gates originais passaram
no verify03, sem exclusões ou redução de thresholds.

A composição também demonstrou que a mesma DLL carregava em caminho173 e era
recusada em268caracteres nesta máquina. O pacote limita conservadoramente o
caminho absoluto nativo a240 antes de JDBC; a contraprova250 foi recusada.
Nenhuma mudança de PATH/registro/configuração global foi feita.

Scanner passou com POM/TXT tratados como texto. Quatro descrições públicas de
schema têm exceção por bytes e caminho exatos, com mutação de valor/localização
recusada. As16contraprovas preservam os11casos anteriores. Logs auxiliares com
encoding inválido foram conservados como falhas e repetidos com UTF-8 explícito.

A revisão conferiu queries parametrizadas e operações set-based existentes,
precisão/null/UTC, orçamento compartilhado entre sessões, fechamento de recursos,
cadeia de erros/recibos, dependências e inventário. Não houve migration/DDL novo;
V001–V098 e baseline histórico permaneceram. V099 continua apenas candidata
livre. Hash/diff e revisão automática não equivalem a revisão humana.

## Sucessão, evidências e limites

- [Resumo verificável](verification-summary.json), [matriz A–N](MATRIZ-A-N.md), [673colunas](matriz-colunas.json), [35responsabilidades](matriz-responsabilidades.json) e [quadro45](quadro-construcao.json).
- [Manifesto sucessor](manifesto.json), ligado ao analítico0389d441f3390f61206bc6ea3403cdb5001b2c32a1502bc98d884806e7a04d3f.
- [Inventário/delta final](../../../target/macrobloco-qualificacao-pacote-20260913-01/succession-final-01/delta.json), [diff completo](../../../target/macrobloco-qualificacao-pacote-20260913-01/succession-final-01/diff.patch) e [diff para revisão](../../../target/macrobloco-qualificacao-pacote-20260913-01/succession-final-01/diff-review.patch).
- [Selo final](../../../target/macrobloco-qualificacao-pacote-20260913-01/final-seal.json): pins dos validadores finais, fontes de evidência, manifesto, resumo, checkpoint e diffs; fica fora do ciclo do manifesto.
- [Checkpoint0141](../../continuidade/checkpoints/0141-entrega-qualificacao-e-pacote-local.md), STATES funcional e RETOMADA sincronizados.

O sucessor compara os2.988snapshots iniciais byte a byte; cada delta existente,
novo arquivo e fotografia histórica é enumerado. Manifestos anteriores,
CheckGuidance, ledgers, checkpoints, migrations e falhas não são reescritos para
ocultar drift. Os validadores correntes analítico/expansão/relacional/temporal,
continuidade e contraprovas são executados sobre essa revisão e selados depois.

Somente localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado, duas travas e dados
sintéticos rollback-only. Não houve fonte real, DDL, COMMIT de domínio, produção,
V1/dashboard, serviço/agenda recorrente, grants, feed/NVD, publicação, commit/push
ou limpeza. Ações remotas documentais públicas anteriores não são chamadas de
fonte de negócio. O contador remoteCalls se refere a chamadas de origem de dados.

V2-038/039 operacionais, V2-012b/c, V2-015c/d, V2-016b, V2-041, V2-045b,
V2-048b e V2-014/040 mantêm seus requisitos. Não estão comprovados: paridade
real/owner, assinatura/CI/audit externo, TLS/service account produtivos, novo
banco versus upgrade físico, recuperação COMMIT/crash, backup/restore/RTO/RPO,
platô/SLO, release candidate de onda ou smoke fora de Windows/x64/Java17.
