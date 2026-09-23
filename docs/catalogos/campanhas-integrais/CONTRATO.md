# P06 — SEQ-CLOSE-01: preservação de falhas no encerramento

Origem: revisão P06 POS0202, reprodução com JDBC proxy de duas falhas simultâneas.
ColetaTemporalLaboratorySession.close deve tentar rollback e fechamento físico,
preservar rollback como causa primária e close como suprimida quando ambas
falham, e sempre limpar o tracking. Fechamento repetido não repete efeitos.
Teste: ColetaTemporalLaboratorySessionCloseTest (quatro combinações); regressão
offline68 e binding JAR18 PASS. Sem nova fórmula/regra de negócio; responsável
de negócio não informado. Nova revisão não herda qualificação física do JAR
POS0198: P07 revalida consumidores e P08 executa o pacote extraído.
Relatório: [P06](P06-REVISAO-POS0202.md). A/L/M/N continuam abertos.

# Evidência histórica — POS0198 (20/09/2026)

P04/I–J e P05/K foram aceitos no escopo sintético local, conforme matriz A–N
e relatório P04-P05-POS0198-20260920.md. Cancelamento, retomada, concorrência,
falha tardia e quatro escalas têm recibos próprios. As menções de pendência
nos parágrafos históricos abaixo conservam sua fotografia original; revisão,
regressão completa e pacote P06–P08 não foram executados nesta ordem.
Os critérios das regras seguintes permanecem exigíveis; nenhum aceite produtivo.

# Sequências integrais locais — contrato em implementação

Pedido A–N adotado em 15/09/2026. Base0165/schema102. Estado atual:
EM_EXECUCAO; as provas de três etapas e agenda passaram na revisão indicada no
checkpoint0167. A revisão ampliada ainda está em teste; este documento não é
recibo de entrega.

## SEQ-01 — entrada fechada e limitada

`local-artifact-sequence-v1` declara alvo, fonte/tenant sintéticos, janela do run,
raízes, tamanho de página, teto total e duas a oito etapas. Cada etapa contém
ID técnico, predecessor explícito, modo, revisão da execução, da fonte e da
referência, teto próprio e pins de entrada/oráculo. A cadeia é linear e ordenada:
predecessor único imediato evita ramos concorrentes ambíguos sobre a mesma transação.
Ciclos, predecessores ausentes, BOOTSTRAP intermediário e membros excedentes
são recusados. Modos não criam permissão de acesso a fontes externas.

Teto da sequência:1800s; etapa:240s; arquivo de sequência:64KiB; raízes:2–32;
página:1–16. Os limites transitivos dos envelopes integrais permanecem.
Preflight percorre entradas e oráculos antes de SQL. Cada consumo confere
novamente os arquivos. Mantêm-se apenas descritores e até oito recibos técnicos;
as linhas de domínio ficam nos parsers/staging/SQL existentes.

## SEQ-02 — execução e comparação

Consumidor: `LocalArtifactSequence`, entrada `local-sequence run --sequence`.
Cada etapa usa `AnalyticScenarioRuntime`, sobre o mesmo run e a mesma sessão
`ColetaTemporalLaboratorySession`. O oráculo externo compara cinco fatos e
19saídas acumuladas após cada etapa. Divergência interrompe a cadeia.
Replay exige fonte, referência e entrada originais. `USERS_SNAPSHOT` e sua
tradução explícita permanecem; nenhuma modalidade incremental de USER é criada.
O replay integral aponta para o BOOTSTRAP imediatamente anterior: V049 vincula
seu plano de expansão a esse modo. Replay de BACKFILL/INCREMENTAL é recusado na
admissão; a revisão de execução é transmitida separadamente da revisão de fonte.

## SEQ-04 — agenda e dependência

O envelope v2 acrescenta `schedule` por etapa, com cinco políticas explícitas:
COL/FRE/MAN/COT/LOC. Tick, lookback, deadline, blackouts e catch-up chegam ao
planner existente. As janelas resultantes chegam aos adaptadores. COL/FRE/MAN
exigem um único dia civil declarado; falta de páginas de outro dia não é suprida
por fixture. Mais de uma janela planejada exige etapas declaradas distintas.
Datas fora do run, blackout, janela não devida e deadline vencido são recusados.

Na sequência, Coletas deve terminar a captura/preparação antes dos dois
consumidores de Fretes. O caminho anterior conserva sua ordem qualificada.
O coordinator consulta os terminais SQL efetivos. A captura analítica local
DEGRADED não é publicação operacional: somente COT pode avançar como PUBLISHED.
LOC mantém o plano de seis fontes da expansão; CAP/FAT/INV/SIN, RAS e USER não
recebem por conveniência a agenda das cinco famílias.

| Família | Modos de captura declarados | Garantia local |
| --- | --- | --- |
| COL/FRE/MAN | BOOTSTRAP, REPLAY, BACKFILL, INCREMENTAL | Captura/preparação; controle termina DEGRADED |
| COT | Os quatro modos | Promoção efetiva pelo runtime existente; fronteira incremental separada |
| LOC/CAP/FAT/INV/SIN | Os quatro modos | Plano/capturas de expansão e recibos próprios |
| RAS | Os quatro modos | Snapshot declarado completo da janela Raster |
| USER | Snapshot; REPLAY quando declarado | Tradução explícita de observação para BACKFILL/USERS_SNAPSHOT |

## SEQ-05 — recomposição sem captura

O envelope v3 acrescenta `operation`: CAPTURE ou RECOMPOSE. Esta última é uma
etapa final BACKFILL, sem schedule; aplica a nova revisão de suplementos aos
consumidores existentes e executa as cinco materializações. Os recibos são de
materialização. Não são inventados ciclos nem recibos de captura. O input v3
separa `captureDate` e `supplementRevision` da revisão da fonte.

Fontes, relações de expansão e referência permanecem idênticas ao predecessor.
Termos financeiros devem preservar seus valores; apenas a revisão técnica do
suplemento pode mudar. Alterar termos exige a captura FRE existente. Trocar
tarifa sem captura COT é recusado, pois V085/V086 vinculam o snapshot à release
usada. Mudanças de release na operação CAPTURE importam os cinco arquivos e
transmitem a tarifa correspondente. Nenhuma série, crosswalk ou identidade é
inferida para tornar a operação admissível.

## SEQ-06 — sweep, evidência e medição

### Correções P03 — SEQ-MC-01 e SEQ-REF-01

O suplemento integral `relational` declara conjuntos MC completos apenas para
as origens incluídas, depois de importar todos os lotes. V103 registra essa
declaração separadamente do bind comum. Revisões anteriores dessas origens ficam
SUPERSEDED, com histórico preservado. Omissão de outra origem ou de componente
em bind/captura parcial não constitui exclusão. Conflitos contemporâneos e as
cardinalidades originais continuam sendo avaliados.

V104 permite que uma captura COT com fonte e frescor idênticos aplique uma release
tarifária diferente, já validada. O kernel registra NO_OP da fonte e a revisão de
referência fica em auditoria própria; o snapshot analítico encadeia as releases.
Repetir a mesma combinação não cria snapshot. Fonte stale não revisa tarifa,
e conteúdo divergente com frescor igual continua recusado. RECOMPOSE continua
sem reextrair páginas e não passa a autorizar troca isolada de tarifa.

Decisão e exemplos: [ADR0052](../../adr/0052-sucessao-mc-e-referencia-com-fonte-preservada.md).
Provas: `103_exercise_explicit_mc_succession.sql`, `RelationalLaboratoryMatrixIT`,
`AnalyticLaboratoryQuotesIT` e `SequenceReferenceIT`; consultar os recibos P03
para distinguir implementação de resultado executado.

Cada etapa aprovada compara 33 previews. As observações próprias do sweep ficam
em savepoint e são revertidas antes da próxima etapa; não substituem a coorte
principal nem concedem capacidade de apply. SQL04 vazia é admissível.

O recibo do supervisor identifica cada etapa, revisões e hashes de input/oráculo,
os cinco fatos, 19 saídas, agenda observada e previews. Falha posterior conserva
a evidência das etapas concluídas e continua sendo falha de execução.

Medição nova mantém no máximo 128 amostras de heap ao longo de páginas, lotes e
etapas, além de mínimos/máximos de todos os eventos observados. O campo
`maximumBatchFetchedBytes` mede bytes buscados desde o lote anterior, não o
tamanho de objetos Java. Limites antigos de massa, arquivo, heap e SQL permanecem.
Essas medidas sintéticas não demonstram platô de heap nem SLO produtivo.

## SEQ-03 — supervisão e recuperação

Ação aditiva `SEQUENCE` no supervisor existente; ações anteriores mantêm seus
tetos. Manifesto, pacote, campanha, configuração, nonce, processo e recibos
continuam vinculados pelo protocolo existente. Journal não mantém dados de
domínio após perda da JVM. Uma tentativa perdida exige reconciliação e outra
tentativa integral; não existe retomada durável no meio da transação perdida.

## Qualificação e evolução pendentes

### SEQ-FIX-01 — autoria da fixture na mesma camada do worker

Origem: LOCAL_SCENARIO_ORACLE_BINDING observado no worker P04 após0188.
Fixtures destinadas ao JAR devem ser geradas com as classes desse JAR; o
fingerprint de classes descompactadas é propositalmente diferente. O helper
exclusivamente de teste PackagedFixtureRuntime usa classloader isolado, com
pai de plataforma, aplicação JAR primeiro, classes autoras de teste e bibliotecas
do pacote. Confere CodeSource antes de gerar; fecha o loader ao terminar.
Não altera fingerprints nem guards de src/main, não consulta JDBC/fonte e não
transforma fixture em prova produtiva. Exemplo: oráculo gerado para classes
descompactadas continua recusado quando o consumidor é JAR, mesmo com input
e schema corretos. Teste: PackagedFixtureBindingIT (offline),14 oráculos de
duas sequências e três adulterações de runtime/input/schema. Contrato afetado:
SEQ-03 e binding de LocalArtifactScenario. Responsável de negócio: não informado.

O supervisor de teste também deve executar as classes do JAR; não basta gerar
o oráculo nessa camada e validar depois com target/classes. Os cinco cenários
QualificationSequenceSupervisorIT passam por ponte test-only isolada. Asserções
JUnit são executadas e suas falhas propagadas; timeout externo mantém a abrangência
da chamada inteira. A contraprova offline verifica a ponte, AssertionError,
verifyFiles das duas sequências e a recusa da mesma sequência em classes soltas.
Nenhum loader de teste é incluído no JAR da aplicação ou usado em produção.

### SEQ-FIX-02 — contraprova independente do classpath Maven e validação de caminhos

Origem: reprodução offline P04-P05-POS0198, em20/09/2026. Failsafe após
jar:jar usa o artefato como aplicação; isso não constitui cenário explodido.
PackagedFixtureBindingIT passa a carregar target/classes em loader explícito,
conferindo CodeSource distinto do JAR e a recusa real da sequência nessa camada.
Mantém14 oráculos, duas sequências e AssertionError; acrescenta JAR alterado
às adulterações de runtime/input/schema. O controlador ArtifactDirected e
seu teto240s permanecem intactos.

QualificationJson.regular resolve o caminho integral com/sem links uma vez
por leitura, conservando a inspeção de symlinks ancestrais, o requisito de
arquivo regular, a recusa de ADS e o rehash. Nenhum resultado é armazenado em
cache. A implementação anterior refazia a travessia integral para cada
prefixo; a pilha observada e o código JDK17 local mostram o custo quadrático
em profundidade no Windows. QualificationJsonPathTest verifica caminho
profundo, arquivo alterado, não-arquivo e ancestral substituído por junction
(symlink fora do Windows), inclusive acesso por descendentes. Contratos
afetados: binding de arquivos, SEQ-FIX-01 e SEQ-03. Regra técnica; responsável
de negócio não informado. Aceite físico depende de recibo próprio.

### SEQ-SCALE-01 — parada e evidência por escala

Origem: ordem P04/P05 POS0198 de20/09/2026. SequenceScaleIT conserva as
quatro invocações2/4/8/16,1800s por escala e240s por etapa. Admissão serial
ocorre antes de qualquer fixture/I/O; falha no corpo ou informada posteriormente
pelo JUnit bloqueia efeitos das invocações seguintes. Não depende de uma opção
fail-fast não consumida pelo provider Maven local. Não há skip para obter PASS.

Cada execução usa identidade técnica distinta, sete etapas,19saídas e33previews
por etapa; os agregados SQL anteriores e posteriores ao rollback devem ser
iguais. Os relatórios registram contagens, terminal e medidas reais, sem payload
ou ID de negócio. Exemplo: falha na escala4 impede I/O em8/16; êxito em2 não
qualifica as demais. SequenceScaleAdmissionTest cobre ordem, repetição, corpo
interrompido e callback tardio; SequenceScaleIT fornece a prova física.
Contrato afetado: SEQ-06 e frente K/P05. Responsável de negócio não informado;
nenhuma fórmula ou regra de domínio alterada. Não demonstra SLO ou platô produtivo.

### SEQ-CP-01 — classpath do worker selado

Origem: incompatibilidade causal observada em P04 physical-01, retomada após
checkpoint0187. O launcher pode usar somente o JAR da aplicação, com o
Class-Path do manifesto previamente conferido, ou o processo filho pode usar
a aplicação seguida do conjunto exato de dependências JAR seladas, expandido
pelo JVM a partir de `lib/*`. A ordem entre bibliotecas é indiferente; aplicação
deve ser a primeira. Caminhos devem resolver para os membros regulares do pacote;
extras, omissões, duplicatas, diretórios e cópias externas são recusados.
Exemplo: `app.jar;lib/a.jar;lib/b.jar` é admissível apenas quando corresponde
ao conjunto selado; substituir `b.jar` por arquivo externo não é admissível.
Não remove a conferência de hashes, manifesto, JDK, plataforma ou CodeSource.
Contratos afetados: SEQ-03 e verificação de pacote no bootstrap/worker.
Teste automatizado: `QualifiedPackageClasspathTest` (quatro testes); prova
física: seleção dirigida P04, somente quando registrada em recibo.
Responsável de negócio: não informado; regra técnica, sem regra nova de domínio.

Campanhas ampliadas, revisão/recomposição de apoios, contraprovas físicas,
cancelamento, quatro escalas, regressão completa e pacote extraído ainda precisam
ser concluídos e vinculados no relatório desta mesma execução. Não há aceite
nominal novo. Responsável de negócio: não informado. Origem das regras SEQ:
pedido adotado; provas: `LocalArtifactSequenceTest` e `LocalArtifactSequenceIT`
(consultar recibos para resultado executado).
