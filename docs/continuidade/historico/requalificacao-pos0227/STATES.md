Validacao documental da rodada fisica PASS: falhas P07 preservadas, sucessao/cadeia/trilha e scans delimitados conferidos. A qualificada/C corrigido; B nao qualificado e P08 nao iniciado.16 requisitos externos permanecem;39/45 e67/115 intactos. Recibos:docs/catalogos/p11-fisico/validacoes.json.

Falha documental preservada: partial-closure-01 parou na composicao por typo Import-sourceText gerado nesta rodada. Corrigido para Import-Module; nao houve SQL nem promocao de aceite. Nova validacao documental02 usara recibos proprios.

# P11 — A qualificada, C corrigido; B executado com falhas preservadas

P11_NATIVE_PASS_FULL_VERIFY_FAILED. JDBC/nativo12.8.2:1 IT PASS e agregados iguais.
As duas rodadas passaram2162 unitarios,com4skips historicos. Full01:
492 ITs/105classes,1erro de lock apos rollback;JaCoCo PASS,Maven falhou.
Full02 foi interrompido apos79 ITs/22classes,2timeouts SQL em Manifestos;
nao completou VerifyPhysical. Escala e ManifestGates originais passaram
isoladamente (4+4casos),sem mudar codigo,asserts ou timeouts. Esses
diagnosticos nao substituem suite integral. P08 nao iniciou.
Readback final igual:0/0/453 auditorias,246tabelas/1816objetos;sem DDL/commit.
SQL sinalizou pressao fisica de memoria apos full02 (flag1),e flag0 apos
o diagnostico. Causalidade dos erros nao comprovada. Nao alterar terceiros.
Duas tentativas corretivas da rodada consumidas (native02 e full02);
sem renovacao automatica do teto/vigencia/limite. B permanece NAO_QUALIFICADO,
nao BLOQUEADO_POR_INPUT por ser uma falha tecnica de execucao.
Busca23 documentos/recibos:16 inputs externos nao comprovados nos artefatos
consultados,com origem e responsavel por papel. Nenhum nome/aceite inferido.
Relatorio:docs/catalogos/p11-fisico/RELATORIO.md.39/45 e67/115 preservados.
P09 nao reavaliado;sem fonte de negocio,producao,publicacao,deploy ou cutover.
Fotografias anteriores preservadas;validacao documental final em andamento.

# P11 — segunda falha SQL preservada; pressao de memoria observada

P11_SECOND_FULL_FAILED. Full02:2162 unitarios/4skips historicos;79 ITs
registrados em22classes,2 timeouts SQL em AnalyticLaboratoryManifestGatesIT.
Execucao interrompida apos falhas persistidas,apenas arvore Maven propria
conferida por PID/inicio/parent/attempt. Wrapper exit1,rollback confirmado;
readback dedicado igual ao inicial0/0/453,246tabelas/1816objetos.
Apos a falha,SQL declarou process_physical_memory_low=1;zero requisicoes
ativas/bloqueadas e transacoes read/write no alvo. Isso demonstra pressao
de recursos naquele instante,nao causalidade retroativa dos timeouts.
Diagnostico atual da classe original de quatro gates,sem mudar codigo/asserts/timeouts.
P07 nao qualificado;P08 nao iniciou. A local qualificada,C corrigido;16
requisitos externos sem novo aceite. Teto36000s/vigencia originais mantidos.
Evidencias:target/p11-fisico-20260921-01/;fotografias anteriores preservadas.

# P11 — diagnostico de escala PASS; nova suite integral em execucao

P11_P07_FULL_RECHECK_RUNNING. A classe original de quatro escalas passou
sem mudar codigo,asserts ou timeouts;rollback/agregados iguais. O erro da
primeira suite continua preservado e sua origem de bloqueio nao foi identificada
retroativamente. VerifyPhysical02 iniciado em p11-p07-verify-02,mesmo snapshot.
Plano sucessor pipeline-plan-02.json conserva anterior STOPPED e reduz
somente tetos futuros ainda nao reservados conforme0/1/6/7 etapas reais.
Teto36000s e vigencia originais mantidos;nenhum efeito ampliado. P08 depende
do novo PASS integral. Recibos:target/p11-fisico-20260921-01/diagnostic-summary.json.
39/45 e67/115 intactos;16 requisitos externos conservados.

# P11 — P07 interrompido por lock no readback; diagnostico local

P11_P07_LOCK_FAILURE_PRESERVED. VerifyPhysical01 executou2162 unitarios
(4skips historicos) e492 ITs/105classes:491 ITs passaram,1 erro SQL de lock
no quarto caso de RelationalLaboratoryScaleIT (repeticao256),linha142,
conferencia de contagens apos rollback. Escala1024 passou. JaCoCo PASS.
Nao qualificar P07 nem P08 com esta falha. Pipeline interrompido antes do pacote.
Wrapper confirmou rollback; readback dedicado preservou agregados0/0/453,
246tabelas/1816objetos. Diagnostico posterior:zero requisicoes ativas/bloqueadas,
transacoes e sessoes JDBC no alvo. Isso nao identifica retroativamente o bloqueador.
Classe original de quatro escalas em diagnostico reservado,sem mudar codigo,
asserts ou timeouts. Alvo exato,sinteticos/rollback,sem DDL/commit/producao.
Ledger:target/p11-fisico-20260921-01/;teto36000s e vigencia originais mantidos.
16 requisitos externos e39/45,67/115 conservados.

# P11 — JDBC/nativo qualificados; regressao integral em execucao

P11_NATIVE_QUALIFIED_P07_RUNNING. Pedido efetivo "conclua esses" adotado para A/B locais,
com localhost/ETL_SISTEMA_V2_SHADOW, sinteticos, rollback, sem DDL ou commit
de dominio. Ordem/limites/ledger proprios: target/p11-fisico-20260921-01/.
Nativo12.8.2 e JDBC12.8.2 passaram 1 IT, zero falha/erro/skip; DLL conferida
contra lock/Central, agregados antes/depois iguais (auditorias0/0/453).
Lock atualizou seis componentes e preservou POMs/licencas historicos.
P07 VerifyPhysical iniciado em copia isolada; ainda nao aprovado. P08 aguarda.
Busca solicitada:23 documentos/recibos relacionados aos16 requisitos externos;
provas reais existentes reaproveitadas, nenhum aceite nominal localizado.
Falha do executor native01 antes de SQL, diagnosticos offline01/02 e correcao03
preservados. Sem novo aceite:39/45 e67/115. P09 nao reavaliado.
Historico abaixo e a fotografia anterior; sucessao sera selada ao fechar a rodada.

Fechamento local pós-P11 validado: 2162 casos/247 classes, 4 skips históricos; JAR/oito libs construídos. Matriz10 e sucessão10 contraprovas, cadeia anterior25 e trilha PASS. Scans delimitados6+299+222 textos, zero achados. Recibos: docs/catalogos/p11-regressao-local/validacoes.json. A/B físicos e16 inputs externos seguem pendentes; C corrigido. Nenhum aceite/contador alterado.

# P11 pós-0220 — regressão unitária integral; SQL/pacote físico pendentes

P11_LOCAL_REGRESSION_RECORDED. 2162 casos/247 classes: zero falhas/erros,
4 skips históricos exatos. Identidades/multiplicidades iguais à prova P07 anterior.
Compilação, Enforcer, Spotless e Checkstyle PASS; JAR e oito bibliotecas construídos
com os pins atuais. Camada unitária completa; sem VerifyPhysical, cobertura
integral, DLL/SQL, execução do JAR ou selo novo. P07/P08 antigos permanecem
provas dos bytes históricos, sem transferir aceite à combinação nova.

Matriz sucessora em docs/catalogos/p11-regressao-local/matriz-atual.json corrige
networkCalls=0 herdado: preparação offline separada da rodada pública0220
(rede usada, sete GETs com recibo, total Maven/NVD não instrumentado) e desta
rodada local (zero rede externa/SQL; fixtures loopback). Históricos intactos.
P10/P11/P12/P14/P15/P21:16 requisitos externos abertos; FEED-ACHADOS atendido
no scan anterior, sem novo scan/aceite humano. P09 não reavaliado.39/45 e67/115.

A: falta autorização específica JDBC/nativo12.8.2 e ordem/ledger próprios.
B: falta VerifyPhysical/cobertura e pacote qualificado executado sob ordens
próprias. Lock histórico ainda fixa seis componentes antigos (quatro Jackson,
JDBC e DLL); DLL nova ausente no cache. O relatório delimita a atualização
desses pins/licenças/SBOM/reprodução e A/B/recusas necessários antes do selo.
C: corrigido por sucessão. Falhas do harness/recibos preservados; não são
regressões do runtime. Nenhum fonte Java, SQL, migration ou POM público mudou.
Relatório: docs/catalogos/p11-regressao-local/RELATORIO.md.

Fechamento P11 validado: sucessão pública 10 contraprovas, cadeia/trilha PASS, preparação 13 contraprovas, política 33 casos/30 recusas e seis regressões do parser. Recibos: docs/catalogos/p11-publico-corrigido/validacoes.json.

# P11 — correções aplicadas; feed público atualizado e auditoria PASS

P11_PUBLIC_FEED_PATCHED. Usuário autorizou acesso público Central/NVD sem
credenciais nesta retomada. POM: Jackson 2.18.11, JDBC 12.8.2.jre11 e pin
nativo 12.8.2.x64. NVD atualizado em 21/09/2026: 41.788 registros; scan real
de 13 dependências com zero achado/erro, threshold 0.0 e exit 0. Sem exceções.
205 testes/29 classes PASS; compilação, Enforcer, Spotless e Checkstyle
PASS. Corrigido parser de lista vazia/ausente; relatório real e seis regressões
PASS. Sem DLL nativa/SQL, produção, fonte de negócio, rotação ou deploy.
Relatório: docs/catalogos/p11-publico-corrigido/RELATORIO.md; resultado.json e
matriz-atual.json contêm provas/limites. Aceite nominal da baseline por Segurança
ainda não recebido; autorização de rede não é aceite humano. P10/P12/P14/P15/P21
conservam preparação e inputs; P09 não reavaliado.39/45 e67/115 intactos.
Falhas e manifests históricos preservados. Próxima ação: Segurança revisar e
aceitar nominalmente a baseline, ou admitir novo input de outro gate.

Validação desta revisão: sucessão P11 10 contraprovas; cadeia anterior 14 + 11; trilha/frota PASS; matriz 13 e preparação 24 contraprovas. Evidência: docs/catalogos/p11-cache-offline/validacoes.json.

# P11 — scan real com cache executado; quatro achados (21/09/2026)

P11_CACHED_SCAN_COMPLETED_FINDINGS_OPEN. A afirmação anterior de esgotamento local foi prematura:
existia cache NVD disponível. Dependency-Check12.2.0 auditou13 dependências
reais, gerou JSON/HTML, zero erros de scanner, exit1 pelo threshold0.0.
Quatro achados: Jackson2.17.2 — CVE-2026-54512/54514/54515; JDBC12.8.1 —
CVE-2025-59250. Cache consultado26/08, lastModified22/07; não é feed atualizado.
Candidato JDBC13.4.0 em cópia: 122 testes/16 classes PASS e alerta JDBC
ausente no novo scan; autenticação nativa/SQL não qualificados, POM público
preservado. Jackson corrigido não está no cache. Zero exceção/supressão nova.

Relatório: docs/catalogos/p11-cache-offline/RELATORIO.md; recibos/hash em
resultado.json. Cache original preservado; guard de rede com6 contraprovas;
ambiente sem segredos, sem fonte/banco produtivo, DLL nativa executada ou deploy.
B56 já tem11 consultas reais e tipos observados; SQL/principals locais também
existem. Essas provas são reutilizadas, sem alegar ausência universal de evidência.
P10/P12/P14/P15/P21 mantêm preparação offline; requisitos externos continuam.
P09 não reavaliado.39/45 e67/115, zero novos aceites. Falhas anteriores íntegras.
Próximo macrobloco: corrigir P11 com dependências/feed obtidos por acesso público
autorizado ou artefatos locais, e qualificar JDBC/nativo sob ordem própria.

# Correção local da sucessão e frota concluída (21/09/2026)

CORRECAO_LOCAL_POS0216. A conclusão de esgotamento em0216 foi prematura:
as duas falhas técnicas foram investigadas, corrigidas e testadas neste chat.
Test-Gpt56ChatTrail e Test-FrotaManifestosV2035cDecisionCatalog agora PASS.
A sucessão nova valida3652 arquivos da base P06,20 deltas com snapshots exatos
(18 preexistentes e2 validadores corrigidos), inventário atual e pins de frota.
14 contraprovas novas e11 P06 PASS; preparação das seis P com13 contraprovas
e mapa da trilha com24 contraprovas PASS. Scans delimitados3+21+294 textos sem
achado. 48 testes Java anteriores conferidos e preservados; runtime não mudou.

P10/P11/P12/P14/P15/P21: preparação offline concluída e corrigida. A parcela
externa continua BLOQUEADO_POR_INPUT apenas nos17 requisitos concretos da matriz:
P10 G02/owner do repositório; P11 FEED/Segurança; P12 G05/DBA/Ops/Segurança/
Compliance e data owner; P14 G03/fornecedor/dados e G04/Negócio; P15/P21 G04/
owners de referências e dimensões, mais fontes qualificadas aplicáveis. Não
chegou input novo, nome nominal, autorização ou aceite. Não há falha técnica
local conhecida remanescente neste recorte. P09 não foi reavaliado.

Relatório e recibos: docs/catalogos/continuidade-pos0216/. Falhas antigas de0216
e desta correção preservadas; manifestos, selos, ledgers e checkpoints anteriores
intactos. Preservados3674 dos3680 arquivos iniciais sem delta; quatro documentos
retêm os bytes anteriores como sufixo e apenas dois validadores foram alterados.
115 checkboxes/67 marcados; construção39/45, sem novos aceites. Não houve rede,
segredo, produção, fonte real, rotação efetiva, SQL, deploy, serviço, job, release,
paridade real, cutover, revisão humana ou aceite V2. Próximo macrobloco elegível:
intake offline do primeiro pacote sanitizado novo G02/FEED/G05/G03/G04.

# P10/P11/P12/P14/P15/P21 — preparação offline concluída (21/09/2026)

Frentes locais TESTADO_NA_CAMADA_OFFLINE; parcelas externas **BLOQUEADO_POR_INPUT**.
P10: G02/owner do repositório; P11: FEED/responsável de segurança; P12:
G05/DBA, Operações, Segurança e Compliance; P14: G03/fornecedor e owner de dados
e G04/Negócio; P15 e P21: G04/owners de referências e consumidores. Nomes
nominais não fornecidos permanecem ausentes. Nenhuma autorização foi herdada.

Matriz nova: docs/catalogos/preparacao-offline-p10-p21/matriz.json, seis P,
17 requisitos sanitizados com origem/papel, 36 pins e DAG das seis dimensões.
O relatório do mesmo catálogo separa baselines, identidade/fiscal, referências,
ambiente e publicação. Não houve fonte, segredo, banco, produção ou novo aceite.

Validação: 48 testes Java em dez classes, zero falhas/erros/skips, JDK17/Maven
offline em cópia isolada com settings vazios; Enforcer/Spotless/Checkstyle e
compilação PASS. Passaram política/implementação de vulnerabilidades (33 casos,
30 recusas), schema/lifecycle/pacote Windows estáticos, lifecycle sintético de
logs, referências, Usuários, quatro identidades pendentes, Raster (16 casos e
12 contraprovas), vertical atual de Manifestos e preparação da trilha.
Sete Actions têm SHA fixo; zero remote local; nenhuma CI remota foi executada.

Históricos preservados: a trilha ampla falhou novamente em
P06_SUCCESSION_HASH_docs/runbooks/continuidade-agentes.md. A validação histórica
de frota falhou no único drift de seus 14 anchors: mapper f99ad676…11384 →
f3865ac8…04d6. As decisões/manifests antigos não foram alterados; a vertical
atual e seus testes passaram, sem promover placa/nome/binding sintético a
identidade real. O novo verificador da preparação testa essa separação e não
substitui o gate histórico. Falhas auxiliares de sintaxe/glob/comparação e o
nome de teste inexistente foram diagnosticados; só XMLs executados contam.

P09/G01 não reavaliado, conforme checkpoint0214. Contadores **39/45 e 67/115**
inalterados. Próximo macrobloco: intake offline do primeiro pacote sanitizado
novo G02/FEED/G05/G03/G04; sem input novo, não repetir os holds. Qualquer efeito
posterior exige autorização própria, ledger prévio, limites e recuperação.

Não houve produção, fonte real, rotação efetiva, deploy, job, serviço, release,
paridade real, cutover, revisão humana ou aceite V2. Rodada e diffs:
target/preparacao-offline-p10-p21-20260921-01/.

Fechamento local: novo verificador PASS com 13 contraprovas e recibos privados;
preparação geral PASS (33 etapas/48 IDs). Scanner restrito ao catálogo novo
(3 arquivos) e aos validadores (292 arquivos) PASS, zero achado. Auditoria
conferiu 3.674 arquivos preexistentes, bytes históricos e 115/67 checkboxes.
Não foi repetida a varredura integral P09. Diagnósticos locais e diff próprio
constam de local-diagnostics.md e diff-check.json da rodada.

Fotografias seguintes permanecem históricas.

# P09/V2-041 — validação offline concluída; bloqueado por G01 (21/09/2026)

**BLOQUEADO_POR_INPUT.** A frente P09 executou integralmente o alcance offline
de V2-041. O intake permaneceu ausente em
`target/v2-041/sanitized-attestation.json`, conferido somente por presença no
caminho ignorado; nenhum conteúdo de atestado, `.env` ou segredo foi lido.
`Test-V2041RotationAttestation.ps1 -ContractOnly` passou como `CONTRACT_VALID`
(seis classes, sete papéis, três scans e 19 contraprovas; `evidence=NOT_EVALUATED`).

`Test-P08MnDelivery.ps1` preservou a integridade P08 M/N: dez recibos, 724
membros, package/readback pins e zero chamada de fonte. O autoteste do scanner
passou inicialmente 17 casos, mas a varredura integral encontrou uma falha
local de UTF-8 na enumeração Git para caminho Unicode, falsamente classificada
como `MISSING_CANDIDATE`. A correção mínima declara UTF-8 no stdout/stderr do
processo Git e sua nova contraprova Unicode; o autoteste final passou 18 casos
e a varredura integral passou com 3.680 candidatos, 3.671 textos, um binário,
zero não inspecionado/excessivo e zero achado. Saída sanitizada:
`target/p09-v2-041/`.

`gitleaks` não está disponível neste ambiente: as varreduras canônicas de
worktree e histórico não foram executadas, sem download, instalação ou scanner
substituto. Não houve processo próprio remanescente, rede, segredo, rotação,
revogação, recarga, health check, banco, DDL/DML, serviço, job, deploy,
produção, paridade real ou cutover.

Os validadores V2-041, autoteste do scanner e preparação da trilha passaram.
`Test-Gpt56ChatTrail.ps1` permaneceu vermelho em
`P06_SUCCESSION_HASH_docs/runbooks/continuidade-agentes.md`: o hash corrente
diverge do `after` preservado pelo manifesto P06, e P09 não alterou esse
runbook. O selo/manifest P06 não foi reescrito; a divergência histórica segue
aberta para seu owner, sem ser convertida em bloqueio G01 nem em sucesso.

O requisito concreto é G01, de **Segurança e Operações**: atestado sanitizado
operacional com referência restrita autenticada pelos owners, rotação e
invalidação das seis classes, inventário de consumidores vivos, continuidade
do único writer legado, rollback e resultados dos três scans; também é
necessário disponibilizar o Gitleaks aprovado/versionado para as duas
varreduras declaradas. Mesmo um recibo localmente válido só resulta em
`STRUCTURALLY_VALID_UNVERIFIED`, não em desbloqueio ou aceite nominal.

Relatório: `docs/catalogos/p09-v2-041/RELATORIO.md`. Contadores canônicos:
39/45 e 67/115, inalterados. P09 não autoriza V2-025d, rede, release, deploy
ou efeito posterior.

# P08 M/N — pacote executado e entrega local selada (21/09/2026)

**M e N estão ACEITO_NO_ESCOPO_LOCAL.** O pacote ampliado atual foi
reproduzido byte a byte (724 membros; ZIP `05700f87…79b4b`; manifesto
`45a5c532…6a73`). A/B pelo JAR extraído passaram, assim como VALUE, PRECISION,
KEY, MULTIPLICITY, OLD_REFERENCE, MISSING_USER, PIN_DRIFT e COMMAND; cada
recusa teve reserva própria, readback de rollback, zero DDL/commit/fonte e
nenhum processo próprio remanescente.

N passou regressão offline (2.162 unitários, 492 integrações, 105 classes,
cobertura e rollback), scanner (3.675 candidatos, 3.666 textos, um binário,
zero achados) e 17 autotestes do scanner, preparação (1 positivo/24 negativos)
e selo/readback dos mesmos bytes. Evidências: `p08-mn-*-23`,
`p08-mn-n-scanner-23` e `p08-mn-n-seal-23` em
`target/macrobloco-qualificacao-pacote-20260913-01/`; relatório
`docs/catalogos/campanhas-integrais/P08-MN-FECHAMENTO-20260921.md`.

Isto não é produção, paridade real, cutover, revisão humana ou aceite dos pais
V2. A sucessão P06 histórica não foi alterada; este fechamento é documental e
local. Os contadores canônicos permanecem 39/45 e 67/115.

# P08 — VerifyPhysical, JAR smoke e guardas atuais aprovados; M/N ainda abertos (21/09/2026)

A ordem independente `p08-mn-final-20260921-21` concluiu a `VerifyPhysical`
atual: Maven `exit0`, 492 testes, 247 classes unitárias e 105 classes de
integração, sem falhas/erros; rollback agregado confirmado, logs UTF-8 dentro
do limite e nenhum processo próprio remanescente. A referência histórica de
115 classes não é o total desta revisão; a suíte efetivamente descoberta e
executada contém 105 classes de integração.

O pacote local `p08-mn-package-primary-21` foi montado a partir dessa execução.
Seu JAR extraído passou o smoke completo (`inspect`, `plan`, `run`, `status`,
`resume`, `compare`), os 8 guardas de controle e os 21 guardas de entrada;
todas as mutações foram recusadas como contratadas antes de criar filho/JDBC
quando aplicável. Os recibos sanitizados ficam sob
`target/macrobloco-qualificacao-pacote-20260913-01/p08-mn-final-20260921-21/`
e nos quatro diretórios `p08-mn-package-*-21`.

Isto resolve o bloqueio de timeout e requalifica a parcela atual de runtime e
pacote simples, mas não encerra M/N: o pacote de 180 membros não contém os
artefatos declarados para executar as sequências A/B, e ainda faltam as
contraprovas diretas remanescentes, scanner, sucessão, selo/readback e gates
por parcela. Nenhuma fonte, produção, DDL, commit ou cutover foi usado.
Os contadores canônicos continuam 39/45 e 67/115.

# P08 — VerifyPhysical da revisão atual atingiu o teto; M/N bloqueados (21/09/2026)

A ordem independente `p08-mn-final-20260921-18` confirmou no `master` somente o
alvo autorizado ONLINE e executou uma única `VerifyPhysical` da revisão atual,
com as duas travas do perfil local. A tentativa atingiu o teto de3.600s,
terminou `exit124` e foi encerrada pelo próprio runner. O rollback agregado foi
confirmado, os logs ficaram dentro do limite e UTF-8 válido, e não restou
processo próprio. Os relatórios de integração que chegaram a finalizar não
tinham failure/error, mas a suíte foi incompleta e não constitui PASS.

Pelo critério de parada, não houve pacote final, reprodução, execução JAR,
smoke, guarda de controle, scanner, sucessão ou selo nesta ordem. M e N
permanecem abertos;39/45 e67/115 não mudam. Evidência sanitizada:
`target/macrobloco-qualificacao-pacote-20260913-01/p08-mn-final-20260921-18/ledger.json`
e `p08-mn-final-verify-18/result.json`. Nova tentativa exige ordem independente,
pré-flight novo e orçamento explicitamente revisto; os ledgers P07/V049/P08
anteriores permanecem imutáveis.

# P08 — teto de membros do runtime corrigido e guardas locais requalificados (21/09/2026)

A divergência do guard extraído tinha causa no runtime: o montador aceitava o
manifesto de724 membros, enquanto `QualifiedPackage` ainda limitava o parser e
o inventário a512/514. O contrato único agora explicita1.024 membros declarados
e1.026 arquivos totais (manifesto e pin incluídos). A falha histórica
`QUAL_JSON_ARRAY` foi preservada; não era defeito do pacote nem do mutante.

`QualificationBoundedContractsTest`, `ArchitectureRulesTest` e
`QualificationContractTest` passaram26 testes sem falhas/erros. O build offline
`p08-runtime-member-limit-build-17` terminou exit0 e gerou o candidato
`p08-runtime-member-limit-candidate-17` (724 membros; manifesto
`86e26ccd96389e588d254b88306b8d6513fb8fd453c599b38fc7f842c9b6680a`).
Seus21 guardas da entrada extraída passaram, inclusive `missing-member` com
`QUAL_JSON_MEMBERS`; `childrenCreated=0` e `jdbc=NOT_STARTED`.

M e N continuam **abertos**, não aceitos: faltam as contraprovas diretas,
smoke, guard de controle, scanner, sucessão, selagem e as evidências físicas
requeridas. Esta correção não leu nem alterou banco, não declara igualdade nova
para as auditorias `ctl` e não promove39/45 ou67/115. Relatório
`P08-FECHAMENTO-LOCAL-POS0216.md`, checkpoint0210 e ledger
`p08-runtime-member-limit-offline-ledger-17` registram a sequência; P07 e V049
permaneceram fora desta unidade.

# P08 — correção do empacotamento e A/B extraídos PASS; guards e selagem pendentes (21/09/2026)

O bloqueio inicial de pré-flight foi corrigido: as auditorias pertencem ao schema
`ctl`, não `dbo`. O contrato de pacote foi ajustado para comportar o índice
de68.060 bytes e as724 entradas declaradas das duas sequências, mantendo tetos
explícitos de256 KiB,1.024 membros de manifesto e1.026 entradas ZIP. O build
P07 e seus recibos permanecem imutáveis; a correção usa snapshot sucessor com
pins conferidos. Pacote primário e reprodução independente passaram com hashes
iguais; extração/manifesto passaram.

P08 A/B pelo JAR extraído passaram: cada sequência teve7etapas,133comparações,
231previews, exit0, sem timeout/limite de log e rollback confirmado. As
contraprovas VALUE (falha tardia,exit40) e COMMAND (recusa pré-SQL,exit20)
também passaram com rollback confirmado. Não houve fonte, DDL, commit ou produção.
M ainda não é aceita: faltam demais contraprovas diretas, smoke e guards
requeridos. N continua aberta: faltam integridade final,
sucessão, scanner, matriz e selo. A/L permanecem abertas;39/45 e67/115 não mudam.
Os ledgers e falhas intermediárias permanecem em
`target/macrobloco-qualificacao-pacote-20260913-01/`.

# P08 — pré-flight interrompido; pacote e selagem não iniciados (21/09/2026)

A ordem independente `p08-pacote-supervisor-selagem-20260921-01` foi aberta com
teto de 51.600 s e reserva única de 1.200 s para pré-flight. O `master` confirmou
somente o alvo autorizado ONLINE e a leitura explícita do alvo retornou os
agregados sanitizados de 246 tabelas de usuário e 1.816 objetos de usuário. A
leitura agregada de auditoria não completou porque uma relação de auditoria
esperada não estava disponível no alvo. Pela regra de parada, a reserva de
pré-flight foi consumida e o ledger foi fechado; não houve pacote, extração,
Maven, JAR, supervisor, sequência A/B, smoke, guard, JDBC de prova ou selagem.

P08 é **BLOCKED_PREFLIGHT_AUDIT_AGGREGATE_UNAVAILABLE**. A/L/M/N permanecem
abertos; C–K continuam históricos; 39/45 e 67/115 não foram promovidos. A
tentativa P07 PASS e a falha histórica POS0205 permanecem inalteradas. Evidência
sanitizada: `target/macrobloco-qualificacao-pacote-20260913-01/p08-pacote-supervisor-selagem-20260921-01/ledger.json`;
relatório e checkpoint0208 registram a condição. Novo efeito P08 exige ordem
independente, com contrato atual de agregados de auditoria verificável antes de
qualquer pacote.

# P07 — Replay corrigido e VerifyPhysical PASS; P08 não iniciado (21/09/2026)

P07 `p07-replay-pos0207-verify-01` passou uma única vez no alvo local autorizado.
A correção preserva a revisão BOOTSTRAP por INCREMENTAL/BACKFILL até REPLAY; V049
permaneceu inalterada. Offline:2.162 testes sem falhas/erros (4 skips históricos),
formatter/Checkstyle e trilha PASS. Física:exit0, Failsafe492 sem falha/erro/skip,
rollback, UTF-8/logs, agregados246tabelas/1.816objetos e processos próprios PASS.
Ledger fechado: `target/macrobloco-qualificacao-pacote-20260913-01/p07-replay-pos0207-ledger-01/ledger.json`.
P08 é **NOT_STARTED_ELIGIBLE_FUTURE_ORDER_REQUIRED**, sem pacote/extração/smoke/A-B/provas/guards.
A/L/M/N seguem abertos, C–K históricos e39/45,67/115 inalterados.

# P07 — VerifyPhysical falhou; P08 bloqueado (21/09/2026)

P07 `p07-pos0205-verify-01` executou uma vez, em
`localhost/ETL_SISTEMA_V2_SHADOW`, e terminou exit1 após54m43s. Rollback
confirmado,246tabelas preservadas, zero processo próprio remanescente, sem
timeout/limite de log/erro UTF-8. Failsafe492testes:2erros,
`EXP_PLAN_REPLAY_ORIGINAL_REQUIRED`, em AnalyticScenarioRuntimeIT e
QualificationReplayIT; zero failure/skip nessa suíte. JaCoCo cumpriu cobertura,
mas não converte a falha em PASS.

Diagnóstico: REPLAY encaminha a revisão BACKFILL anterior ao plano SQL que exige
BOOTSTRAP original. O byte de AnalyticScenarioRuntime coincide com o snapshotP03;
os dois ITs não estavam na campanhaP03 comparada. P08 não iniciou (sem exemplos,
pacote, extração, smoke, A/B, provas ou guards). A/L/M/N seguem EM_EXECUCAO;
C–K conservam somente aceites históricos;39/45 e67/115 inalterados. Ledger
P07/P08 fechado, teto57.600s não transferível: target/macrobloco-qualificacao-pacote-20260913-01/p07-p08-pos0205-ledger-01/ledger.json.

Relatório docs/catalogos/campanhas-integrais/P07-P08-POS0205.md; checkpoint0206.
Nova correção de bytes exige teste offline e ordem físicaP07 nova, com vigência,
orçamento e tentativa próprios; somente P07 PASS libera P08. Prefácios abaixo
preservam fotografias históricas.

# P06 — fechamento offline; pacote P07–P08 preparado

Revisão P06 concluída:3achados médios corrigidos e1baixo de compatibilidade preparado.
68testes dirigidos+18preflight canônico PASS; helpers10 checks offline, scanner17,
remoções5 e sucessor11 contraprovas. As duas pendências históricas de integridade
foram tratadas por evidência exata; gates dos bytes finais em final-validation/*complete*,
closure.json e review-manifest.json da rodada P06-REVISAO-POS0202-20260920T225525774Z.

P07 tecnicamente elegível após readback; efeitos físicos P07/P08 exigem nova ordem
com alvo/vigência/orçamento. Nenhum uso do saldo POS0198. Consumo físico zero.
Novo JAR requer requalificação; aceites anteriores preservados como históricos.
A/L/M/N continuam EM_EXECUCAO,39/45 e67/115 inalterados, sem seloN ou produção.

Checkpoint0205 SHA-256 000b1a0dd6246d9184647666e7cdebd4b6283a723e7daad20ba7ef0927ff939b; relatório docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.
P06_REVIEW_OFFLINE_COMPLETE; sucessão conserva0203 c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Prefácios seguintes são fotografias históricas.

# P06 — revisão técnica concluída; requalificação física pendente (20/09/2026)

P06_REVIEW_OFFLINE_COMPLETE. Três achados médios corrigidos (causa de rollback,
remoções históricas no scanner e sucessão) e compatibilidade dos helpers preparada.
68 testes dirigidos e18 preflight canônico PASS, sem perfil/ambiente JDBC;
scanner17contraprovas, remoções5, sucessão11 e trilha completa preliminar PASS.
Readback final: target/P06-REVISAO-POS0202-20260920T225525774Z/closure.json e final-validation.

P07 tecnicamente elegível após gates finais; efeitos físicos exigem ordem própria
com alvo, vigência e orçamento. Nenhuma reserva física, SQL/JDBC/fonte ou DDL nesta
rodada. Novo JAR requer requalificação C–K; aceites antigos e recibos preservados.
L inclui regressão P07 e não recebe aceite integral; A/M/N seguem abertos.
39/45 construção e67/115 aceites inalterados. Nenhum selo A–N/revisão humana/produção.

Relatório: docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.
Checkpoint0204 SHA-256 a2e33d720076e67baf1d05da854c1968db427ae3d6ad0e8f30f7d0bcdbfce1a3. Sucessão vincula0203:
c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Fotografias abaixo preservam resultados e falhas anteriores.

# P06 — correções offline verificadas; integridade final em validação

P06_REVIEW_OFFLINE_COMPLETE refere-se à revisão técnica; gates finais de integridade em validação.
68 testes dirigidos e18 preflight canônico PASS, scanner17contraprovas e remoções5PASS.
Nova sessão/JAR exige P07 físico sob ordem própria; L/A/M/N continuam abertos.
39/45 e67/115 preservados. Consumo físico zero. Checkpoint0203 SHA-256 c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Relatório: docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.

Prefácios seguintes preservam fotografias históricas.

# P04–P05 POS0198 — macrobloco concluído no escopo local (20/09/2026)

P04 QUALIFIED_SHADOW; I e J ACEITO_NO_ESCOPO. P05 QUALIFIED_SHADOW;
K ACEITO_NO_ESCOPO nas quatro escalas 2, 4, 8 e 16. Aceites locais da matriz
A–N conferidos pelos recibos p04-acceptance.json e p05-acceptance.json da
ordem P04-P05-POS0198-20260920-01. Não fecham pais V2, P27 ou prontidão produtiva:
os contadores canônicos continuam 39/45 e 67/115.

Fase A: 72 testes offline PASS após reproduções vermelhas preservadas.
Fase B: controlador histórico ArtifactDirected inalterado, teto 240 s,
18/18 PASS, exit 0, sem timeout, SQL ou reserva. Fase C: 20 ITs e 20 unidades
PASS; recibos de sucesso, cancelamento e falha tardia, preview terminal zero,
journals terminais e rollback nas 246 tabelas. Preparação P05: 8 testes offline
PASS. Fase D: 4/4 escalas físicas e 4 unidades PASS, sete etapas por escala,
19 saídas e 33 previews por etapa, planos SQL reais e limites preservados.

| Raízes | Duração medida (s) | Maior etapa (s) | Heap máximo amostrado (MiB) | JDBC calls |
| --- | --- | --- | --- | --- |
| 2 | 111,414 | 22,965 | 48,62 | 2910 |
| 4 | 203,807 | 37,652 | 51,54 | 2946 |
| 8 | 314,019 | 55,465 | 52,5 | 5406 |
| 16 | 431,37 | 73,438 | 54,64 | 5598 |

Agregados antes/depois iguais por escala e na campanha; nenhuma linha da
execução retida após rollback; zero processos próprios residuais. JAR P04/P05
idêntico; 2077 arquivos executáveis/schema conferidos contra o build P05.
Consumo final: P04 1/2, P05 1/1, total 2/3 campanhas; 7.200/10.800 s reservados.
P04 #2 não usada; saldo não renovado nem convertido em nova P05. Vigência
original até 2026-09-22T21:34:04Z preservada; não há efeito pendente.

Correções: consumidor explodido explícito, CodeSource e adulterações reais
de JAR/runtime/input/schema; eliminação da resolução Windows repetida por
ancestral, sem cache e com recusa de junction/symlink. P05 ganhou parada
antes de I/O após falha e agregados por escala. Sem mudança de schema/pom.

Preparação documental PASS (1 positivo + 24 negativos). Lacunas históricas
preservadas: trilha falha STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md;
scanner falha por oito MISSING_CANDIDATE preexistentes, zero outros achados.
Não alterar manifests antigos para ocultá-las. UTF-8/JSON/diff e preservação
da rodada são registrados separadamente em closure-verification.json.

Catálogo: docs/catalogos/campanhas-integrais/P04-P05-POS0198-20260920.md.
P06–P08 não executados. Sem fonte real, DDL, produção, deploy ou cutover;
amostras sintéticas não provam SLO, platô de heap ou paridade real.
Prefácios seguintes conservam fotografias históricas e estados intermediários.

# P04/P05 POS0198 — I/J aceitos; campanha P05 em execução

P05 foi reservada em 2026-09-20T22:19:54Z até23:19:54Z, após nova confirmação
do alvo local ONLINE, hash do aceite I/J, matriz, ledger, vigência, orçamento e
ausência de processos próprios. Consumo2/3campanhas,7200/10800s reservados;
P04#2 não utilizada. Uma única P05, escalas2/4/8/16, sem repetição.

Preparação P05 test-only passou8/8 offline: guarda serial antes de I/O,
bloqueio inclusive após falha tardia do JUnit, raízes em ordem e sem repetição.
Cada escala registra agregados antes/depois do rollback, terminal,19saídas e
33previews por etapa, mantendo1800s/240s. Sem mudança de src/main/schema.
Não inferir aceite K dos testes offline: a campanha física ainda está em curso.
Checkpoint0201 e ledger POS0198 identificam o processo e o deadline.

Prefácios seguintes são fotografias históricas.

# P04/P05 POS0198 — I/J aceitos no escopo shadow; P05 em preparação

P04#1 concluída:20IT e20unidades PASS,zero falhas/erros/skips,exit0,sem timeout.
Maven17m06s; reserva total21:52:44Z–22:10:09Z dentro3600s. Cinco XMLs íntegros,
duas travas verificadas, rollback e246contagens preservados,zero processo próprio.
Auditoria: execution_audit0/0,execution_page_audit453/453,page_audit0/0;
archive e field_audit0/0. Nenhum payload/ID de negócio neste resumo.

Recibos: sucesso7etapas/33previews/19saídas; cancelamento7/33/19; falha tardia
5etapas,33/33/33/33/0 e19saídas,FACT_EQUATION_DIVERGENCE preservado. Journals
terminais,owner/resume/isolamento e barreiras de commit/apply conferidos.
Máxima etapa31.072s; sequências160.487/156.930/90.134s,abaixo1800s.
Readback2076arquivos fonte/banco sem drift. p04-acceptance.json registra
**I=ACEITO_NO_ESCOPO; J=ACEITO_NO_ESCOPO**, estritamente local/sintético/shadow.
Não fecha paisV2,39/45 ou67/115, nem concede recuperação durável de domínio.

O verificador final foi corrigido para distinguir header.json/journal.lock
dos eventos numerados; falhas de classificação preservadas em WORKLOG, sem
reexecução física. P04 consumiu1/2tentativas e1/3campanhas; P05 ainda não
reservada. Preparar guarda serial de parada após falha e agregados por escala,
provar offline, então revalidar alvo/ledger e reservar a única P05.

Prefácios seguintes são fotografias históricas.

# P04/P05 pós-0198 — preflight canônico PASS; P04#1 em execução (20/09/2026)

FaseA concluída:72testes offline PASS. FaseB concluída pelo controlador
histórico inalterado ArtifactDirected240s:18/18,exit0,timedOut=false, UTF-8 e
XMLs íntegros no build correto;3587inputs atuais conferidos. Binding89.417s;
Maven3m40s. Recibo: preflight-verification.json na rodada POS0198.

Master confirmou alvo exato ONLINE; ledger/hash/vigência/saldo/processos
conferidos. P04#1 reservada21:52:44Z até22:52:44Z,3600s incluindo preparação;
watchdog externo e teto interno conservador3500s. Cinco ITs obrigatórias e20
unidades pelo perfil opt-in/duas travas. Consumo reservado1/3(3600/10800s),
P04 ainda EM_EXECUCAO, I/J sem aceite e P05 não reservado. Não repetir tentativa
com resultado pendente. Checkpoint0199 registra a campanha/processo observável.

Os parágrafos seguintes conservam a fotografia anterior desta mesma unidade.

Ordem nova `P04-P05-POS0198-20260920-01`, adotada em2026-09-20T21:34:04Z,
vigente até2026-09-22T21:34:04Z. Ledger próprio antes do código: máximo2P04,
1P05,3campanhas/10.800s,3600s cada; consumo físico0. Inventário inicial e
históricos preservados em `target/P04-P05-POS0198-20260920-01/`.

Duas causas separadas: Failsafe após jar:jar carrega aplicação do JAR e a
contraprova implícita explodida falha (1teste/1falha,exit1); Failsafe sem fase
jar usa classes explodidas e a reprodução foi contida aos75s. A pilha da JVM
própria mostrou resolução Windows repetida em QualificationJson.regular,
durante autoria real. Código JDK17 local confirma travessia integral por
toRealPath, repetida para cada ancestral pelo guard anterior.

Correção: loader explodido explícito e CodeSource conferido em ambos os
cenários; comparação real/no-follow do caminho integral uma vez por chamada,
mantendo inspeção de symlinks ancestrais, rehash e ausência de cache.
Compilação/Enforcer/Spotless/Checkstyle e20testes dirigidos PASS; junction
substituída após validação continua recusada. Binding Failsafe corrigido2/2
PASS em66.17s, incluindo14oráculos,duas sequências, quatro adulterações
(JAR/runtime/input/schema) e AssertionError. Regressão dos consumidores em
andamento; preflight canônico e qualificação shadow ainda não executados.
I/J IMPLEMENTADO_NAO_QUALIFICADO; P05 NOT_RESERVED_NOT_EXECUTED.
39/45 e67/115 preservados. Próximo efeito físico depende de preflight240s
verde, alvo exato, vigência/saldo e reserva nova; testes offline não concedem aceite.

Prefácios seguintes preservam fotografias históricas.

# P04/P05 pós-0196 — guard corrigido; preflight bloqueado sem Physical (20/09/2026)

A ordem finita `P04-P05-POS0196-20260920-01` foi registrada antes dos efeitos,
com vigência até2026-09-22T20:38:18Z, alvo sombra exclusivo e orçamento máximo
de três campanhas/10.800s. Ela preserva todas as reservas históricas e tem
**zero reserva/zero campanha física consumida**. Não houve consulta SQL, JDBC,
DDL, rede, fonte, produção ou P05.

O guard local foi corrigido de `sourceRoot` para o build isolado. Dez
contraprovas executaram o bloco exato do controlador corrigido: conjunto válido
PASS; ausência, XML inválido, zero testes, failure/error/skip, origem isolada,
divergência e o conjunto histórico5XML/1falha continuam `exit127`.
`QualificationSequenceSupervisorAssertionTest` passou2/2 em JDK17 e chama as
asserções privadas reais sem `setup/execute/resume`; a ponte preserva
`AssertionError` e guards de binding em prova separada.

O preflight canônico `p04-p05-pos0196-preflight-01` não passou: Maven
offline/JDK17, heap512 e teto240s terminou `exit124/timedOut=true` enquanto
`PackagedFixtureBindingIT` executava. A tentativa Failsafe direta também mostra
o diagnóstico de classpath (JAR usado como consumidor explodido, logo
fingerprint igual), e a seleção Surefire equivalente foi contida somente como
árvore própria em260.554s, sem resultado PASS. Não foi criada reserva porque o
gate exato não fechou. I/J seguem **IMPLEMENTADO_NAO_QUALIFICADO**, K/P05 segue
sem reserva/execução;39/45,67/115, oito MISSING_CANDIDATE e a falha histórica de
sucessão são preservados. Evidência: `P04-P05-POS0196-20260920.md`, ledger e
preflight no diretório de mesma ordem, checkpoint0197.

# P02 — diagnóstico causal offline pós-0194 (20/09/2026)

Diagnóstico documental concluído; **I/J continuam IMPLEMENTADO_NAO_QUALIFICADO**.
Não houve reserva, Physical, banco, rede ou P05. Mantêm-se39/45 e67/115.
Evidência: `docs/catalogos/campanhas-integrais/P02-DIAGNOSTICO-POS0194.md`,
`target/p02-diagnostico-pos0194/evidence.json` e checkpoint0196.

Retificação explícita da interpretação temporal de0195:2405.848s é o total da
classe de cinco testes, não uma sequência. Os casos somam2405.837s; máximo
758.817s. Os três cenários físicos têm @Timeout(1800) individual, incluindo a
ponte reflexiva; os dois de admissão não têm essa anotação. SEQ-01 fixa1800s
por sequência,240s por etapa, e a campanha mantém3600s. Recibos existentes:
sucesso328.0730455s, falha tardia254.5823196s, cancelamento317.3075448s;
maior etapa43.750s. Não há violação demonstrada de1800s nesta tentativa nem
falha de controle inferível da soma. O ledger histórico conserva
sequenceLimitExceeded=true como conclusão anterior retificada aqui; não foi
reescrito, nem o limite ampliado. Não se prova contenção adversarial no teto.

Dois defeitos locais demonstrados: (1) a asserção histórica exigia33 também
no terminal BLOCKED_DEPENDENCY, embora SEQ-06 e LocalArtifactSequence só
produzam previews em etapa aprovada; recibo tardio confirma33/33/33/33/0,
19 saídas por etapa e FACT_EQUATION_DIVERGENCE observável. A correção test-only
preexistente preserva essa distinção. (2) o guard de Invoke-Build.ps1 procura
XMLs em sourceRoot, enquanto Maven executa em build. Reaplicar somente o bloco
de leitura aos arquivos existentes reproduziu cinco ausências no workspace;
com build, zero ausências e uma falha real, ainda exit127. Correção mínima
definida: trocar somente a raiz de busca para build; guard não alterado.

Provas desta camada: reconciliação offline PASS, expectativa antiga rejeitada,
nova forma aceita e quatro adulterações em memória recusadas; compilação
estática javac17 dos dois testes PASS, sem executar métodos; spotless:check
Maven offline/JDK17 PASS (1162 arquivos). Preparação documental/self-test PASS;
a falha histórica STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md foi
reobservada, sem reparar manifests. O scanner e a preservação são registrados
no relatório/checkpoint; os oito MISSING_CANDIDATE não são resolvidos por P02.

Plano mínimo: corrigir/testar guard em escopo posterior, provar o helper Java
isolado sobre recibo/JSON sem chamar setup/execute/resume, depois preflight
da revisão exata. Nova Physical requer autoridade explícita finita, reserva
nova e todos os limites originais; não reutilizar P04-P05-POS0194-01. A correção
da asserção não qualifica I/J nem apaga o XML5/1/0/0. P05 permanece bloqueado
por precedência, sem autorização, reserva ou execução neste macrobloco.

Prefácios seguintes são fotografias históricas; a inferência temporal de0195
é superada exclusivamente pela reconciliação acima.

# Regra permanente — uma entrada, execução autônoma, uma saída (reforço20/09/2026)

O usuário reiterou: “se n encontrou a solucao, vc vai ler a documentacao,
testar e encontrar, mas n ficar pedindo autorizacao, pq vc ja tem”.
Uma entrada adota a tarefa; a saída é a entrega consolidada, não uma sequência
de ofertas para continuar. Antes de alegar bloqueio, consultar documentação,
código, testes e evidências; corrigir defeitos locais e verificar o resultado.
Falha de teste é trabalho técnico a resolver, não motivo automático para pedir
permissão. Não devolver correção offline elegível como tarefa de outro chat.
Atualizações e checkpoints não exigem resposta. Não pedir de novo autoridade
vigente; conservar limites explícitos, rastreabilidade e barreiras de segurança.
Dependência externa real deve ser demonstrada, sem fabricar aceite ou permissões.
Esta regra reforça a orientação permanente de12/09 e o registro de19/09 abaixo.

# P04/I-J — única campanha pós-0194 fechada sem qualificação (20/09/2026)

A ordem nova `P04-P05-POS0194-01` não reutilizou nem reabriu ledgers anteriores.
O preflight ArtifactDirected do snapshot corrente passou18/18, sem
falhas/erros/skips, com Enforcer/Spotless/Checkstyle PASS. Após confirmar no
`master` o alvo exato `localhost/ETL_SISTEMA_V2_SHADOW` e ausência de processo
próprio, consumiu-se a única reserva P04 de3.600s, com sequência1.800s,
etapa240s, SQL60s e heap512MiB. Escopo: dados sintéticos, Windows integrada e
rollback-only; sem DDL, fonte, V1, produção, P05/K ou P06+.

A campanha `p04-p05-pos0194-p04-01` fechou sem timeout, com logs UTF-8 abaixo
do limite, rollback/agregados byte-a-byte iguais e zero processo próprio. Os
XMLs no build isolado mostram sweep5/0/0/0, cancelamento3/0/0/0,
concorrência1/0/0/0, retomada6/0/0/0 e supervisor5/1/0/0
(testes/falhas/erros/skips). O supervisor levou2405.848s, acima do teto de
sequência1800s; sua falha é a expectativa de33 previews no cenário de oráculo
tardio, que observou0. Além disso, o guard do controlador
leu o caminho de relatórios do workspace, não o build isolado, e fechou em127
listando os cinco XMLs como ausentes. As duas condições são desqualificantes;
não se usa o XML existente para ocultá-las.

Após o fechamento, a expectativa test-only foi corrigida: preview vazio no
terminal bloqueado e33 nos estágios anteriores. Essa correção ainda não recebeu
nova prova física — e não a receberá nesta ordem, cuja reserva acabou. A tentativa
de `spotless:check test-compile` no Maven ambiente parou antes da fonte por
incompatibilidade JVM25/formatter fixado. Mesmo com a correção, a violação de
1800s e o guard de caminho continuariam a impedir aceite.

I/J permanecem **IMPLEMENTADO_NAO_QUALIFICADO**. A reserva está consumida e não
haverá repetição nesta ordem. P05/K não foi reservado nem executado e P06+ não é
elegível. Contadores históricos preservados:39/45 construção e67/115 aceites.
Scanner mantém oito MISSING_CANDIDATE e a falha histórica
`STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md` continua explícita, sem
reescrever manifests. Evidência sanitizada:
`target/P04-P05-POS0194-01/`,
`target/macrobloco-campanhas-integrais-20260915-01/p04-p05-pos0194-p04-01/`,
`docs/catalogos/campanhas-integrais/P04-P05-POS0194.md` e checkpoint0195.

# Macrobloco P04→P05 pós-0193 — P04 não qualificado; P05 não iniciado (20/09/2026)

Ordem `P04-P05-POS0193-01` registrada entre16:09Z e16:09Z+48h, exclusivamente
em `localhost/ETL_SISTEMA_V2_SHADOW`, dados sintéticos, Windows integrada,
rollback-only e as duas travas Maven. O preflight ArtifactDirected passou18/18,
sem falhas/erros/skips, com Enforcer/Spotless/Checkstyle verdes; o snapshot de
3.577 arquivos não teve drift. `Test-TrilhaPreparation.ps1` também passou.

As duas reservas P04 de3.600s foram consumidas e reconciliadas. P04-01 foi
contida após quatro ITs concluírem; havia recibo contratual de cancelamento com
7/7 etapas, maior etapa29.626ms e rollback, mas faltou o XML do supervisor.
Foi corrigido o controlador local para falhar quando qualquer IT solicitada não
produz XML íntegro; parser e contraprovas offline conferiram a recusa127 e o
subconjunto completo. P04-02 repetiu16 unitários e as quatro ITs parciais PASS,
mas supervisor ficou sem XML; o guard retornou127. A árvore Maven e seu último
filho próprio foram contidos por identidade. Após o último filho, agregados são
iguais, rollback confirmado, logs íntegros e não há processo próprio.

I/J permanecem **IMPLEMENTADO_NAO_QUALIFICADO**: o wrapper não substitui o
relatório de supervisor, sucesso/falha tardia/cancelamento de sequência e
recibos integrais completos. P05/K **não iniciou** por precedência e porque não
há terceira P04 nem transferência de reserva. P06 não é elegível. Contadores
preservados:39/45 construção e67/115 aceites. Evidência sanitizada/ledger:
`target/P04-P05-POS0193-01/`; tentativas em
`target/macrobloco-campanhas-integrais-20260915-01/p04-p05-pos0193-p04-{01,02}`.
Scanner conserva oito MISSING_CANDIDATE e a trilha conserva a falha histórica
de sucessão de RETOMADA, sem reescrita de manifest.

Continuação P04: corrigir a fixture contra o JAR e validar offline. A resposta
atual à proposta de correção/prova local determina prosseguimento sem reconfirmação;
limitar a prova proposta a uma campanha de uma tentativa3600 s, mesmos limites
e alvo, após preflight. Não significa tentativas ilimitadas, reutilização dos
ledgers fechados, P05, fonte, DDL ou produção. I/J continuam sem aceite até prova.

# P04 — correção local comprovada offline; aceite físico ainda pendente

Continuação de20/09 concluída no alcance comprovado: a regra permanente de
uma entrada/uma saída está destacada aqui e na TRILHA, sem nova pergunta de
autorização para correções locais. Não se limitou a propor a correção pendente.
Autoria da fixture e supervisor de teste agora usam as classes do mesmo JAR;
guard de runtime, src/main, schema, dependências e barreiras de commit intactos.

Preflight final p04-0190-bridge-final-offline:18/18 PASS, exit0, zero skip,
Enforcer/Spotless/Checkstyle verdes, logs íntegros. Conferiu14 oráculos,
verifyFiles das duas sequências, recusas de runtime/input/schema alterados,
recusa da sequência em classes soltas, propagação de AssertionError e driver
JDBC compartilhado pela ponte de teste. A autoria sozinha havia passado17/17,
mas não cobria o supervisor; a lacuna foi reproduzida e corrigida nesta rodada.

A única campanha física proposta nesta continuação foi consumida antes dessa
última correção:16 unidades e15 ITs PASS, sequência recusada antes do worker;
contenção própria em15:34:41Z, rollback e readbacks iguais, sem timeout e sem
processos remanescentes. Não foi reaberta nem repetida a reserva. Resultado
atual de I/J: IMPLEMENTADO_NAO_QUALIFICADO, por faltar prova física integral
da ponte final. P05/K não elegível;39/45 e67/115 preservados. Teste offline não
substitui essa prova. Nenhum aceite fictício nem orçamento físico ilimitado.

Detalhes e histórico preservados em
[continuidade P04](docs/catalogos/campanhas-integrais/P04-CONTINUIDADE-0190.md)
e target/p04-continuidade-0190. Verificações de fechamento conferem revisão,
preservação do worktree/índice, UTF-8/JSON/diff e integridade. Falhas históricas
de sucessão de RETOMADA e oito MISSING_CANDIDATE do scanner permanecem visíveis.

# P04 — correção adicional do supervisor em validação offline

A campanha p04-0190-physical-01 foi contida às15:34:41Z após diagnóstico offline
delimitado: a autoria já usava JAR, mas QualificationSequenceSupervisorIT ainda
instanciava o supervisor em target/classes, recusando o binding antes do worker.
BindingProbe reproduziu LOCAL_SCENARIO_ORACLE_BINDING sem JDBC. As17 provas
anteriores conferiam autoria/oráculos, não a camada em que o supervisor era
invocado; esta lacuna agora está explícita, sem converter o preflight em aceite.
16 unidades e15 ITs passaram; controlador OBSERVED/exit-1, timedOut=false,
rollbackConfirmed=true, logs íntegros. Nenhum aceite I/J ou P05.

Prosseguimento técnico sem nova pergunta: ponte test-only para executar os
cinco cenários com aplicação JAR no mesmo loader; timeout JUnit externo
preservado, asserções reais propagadas e loader fechado. Preflight adicional
p04-0190-bridge-offline verifica duas sequências completas em verifyFiles,
binding inválido de classes soltas e propagação de AssertionError. Resultado
pendente. A campanha física finita está consumida/fechada, sem repetição.

# P04 em continuidade — binding offline provado, campanha local em execução

Correção limitada a src/test: PackagedFixtureRuntime gera os exemplos com as
classes do mesmo JAR, em loader isolado com CodeSource conferido e fechamento
garantido. PackagedFixtureBindingIT conferiu14 oráculos e recusou3 alterações
de runtime/input/schema, sem JDBC. Preflight p04-0190-binding-offline:17/17,
exit0, Enforcer/Spotless/Checkstyle e logs íntegros. Guard src/main não mudou.

Após confirmar alvo ONLINE no master e ausência de processos anteriores,
foi reservada a campanha finita proposta e reiterada pelo usuário, sem nova
reconfirmação: `target/p04-continuidade-0190/ledger.json`, uma tentativa3600 s,
`p04-0190-physical-01`, cinco ITs e16 unidades. Não reutiliza as duas reservas
históricas nem representa autorização ilimitada. I/J continuam sem aceite até
resultado integral. Regra de interação não substitui prova ou limites.

# P04 — classpath corrigido; segunda tentativa parou em vínculo de runtime (20/09/2026)

Resultado da continuação solicitada: o bloqueio QUAL_PACKAGE_RUNTIME_CLASSPATH
foi corrigido e superado fisicamente. A segunda e última tentativa
`p04-requalificacao-physical-02` aprovou16 unidades e15 ITs: sweep/preview5,
cancelamento3, concorrência1 e retomada6 (antes2/6). Quatro workers de bootstrap
emitiram PASS_LOCAL/rollback=true. Não houve nova reserva nem terceira tentativa.

O primeiro worker de sequência emitiu FAILED/testPassed=false/exit2, motivo
CASE_EXECUTION_EXCEPTION e failureCode LOCAL_SCENARIO_ORACLE_BINDING, sem etapas
concluídas no relatório. Foi encerrada somente a árvore Maven comprovadamente
própria, preservando o controlador: result OBSERVED/exit-1, timedOut=false,
rollbackConfirmed=true, logUtf8Integrity=true e logLimitExceeded=false.
Readbacks before/after têm SHA-256 idêntico
`3e7eb885e665e829b77133b12d33fa6816ff41d09321aee2547f111342ef6e97`.
Não há processo próprio remanescente. Sem DDL, fonte, segredo ou produção.

Diagnóstico read-only do novo impedimento: no primeiro oráculo, input e schema
conferem; runtimeSha256 corresponde exatamente ao fingerprint das classes
descompactadas, e não ao JAR do worker. LocalArtifactScenario distingue essas
camadas por contrato. QualificationPackageFixture gera os exemplos dentro da
JVM de teste via IntegralCampaignPackageFixtures e os copia para o pacote.
Correção futura deve gerar/vincular a fixture ao mesmo JAR executado e validar
offline essa fronteira; não relaxar o guard nem repinar evidência histórica.
Não foi implementada uma segunda correção nem executada prova física adicional.

I/J permanecem IMPLEMENTADO_NAO_QUALIFICADO: worker, journal, retomada,
cancelamento, concorrência e sweep têm provas parciais, mas faltam sequência
integral, recibos parciais por etapa e cancelamento de sequência. P05/K não é
elegível. As duas tentativas da ordem foram consumidas (7200 s reservados;
segunda execução Maven de02:52:08Z até contenção03:07:19Z, abaixo3600 s).
Nova execução física exige nova autorização finita; 39/45 e67/115 preservados.

Evidências: adendo `target/macrobloco-p04-requalificacao-20260919-01/continuacao-0188.json`,
`target/p04-desbloqueio-0188/` e
[reconciliação P04](docs/catalogos/campanhas-integrais/P04-I-J-RECONCILIACAO-20260920.md).
Preparação/diff PASS. Trilha mantém FAIL histórico STATES_SUCCESSION_HASH de
RETOMADA; scanner mantém apenas oito MISSING_CANDIDATE preexistentes, sem novo
achado de conteúdo. JSON/UTF-8 estrito PASS em10 arquivos; inventário3566
preservado, seis arquivos preexistentes alterados dentro do delta autorizado,
índice Git intacto. Checkpoints0186/0187/0188 e ledger anterior preservados.

# P04 — diagnóstico causal retificado e correção offline (20/09/2026)

Continuação solicitada: resolver o bloqueio lendo a documentação. A inspeção
limitada, sem recursão, dos quatro `original-*/case-bootstrap/stderr.log` e
do stderr da sequência na physical-01 demonstrou `QUAL_PACKAGE_RUNTIME_CLASSPATH`.
O supervisor usa JAR mais `lib/*`; o verificador recusava qualquer classpath
com separador. A causa é local e anterior à execução do worker. A ausência de
recibo após 240 s de uma classe não prova timeout de etapa: SEQ-01 distingue
1800 s por sequência de 240 s por etapa. As conclusões contrárias de 0187 e
dos prefácios históricos abaixo ficam expressamente retificadas, sem apagar
a falha, a interrupção ou a primeira reserva consumida.

`QualifiedPackage` agora admite JAR único ou o conjunto expandido exato das
dependências seladas, com aplicação primeiro, sem extras, omissões, duplicatas
ou diretórios. Hashes, manifesto e verificação do pacote continuam obrigatórios.
Preflight `p04-fix0188-offline`: 16 testes PASS (12 existentes e quatro novos),
exit0, Enforcer/Spotless/Checkstyle verdes; não acessou banco. Regra SEQ-CP-01.

A segunda tentativa condicional já está coberta pelo pedido original após
correção causal validada offline. A continuação usa adendo próprio ligado ao
ledger histórico, sem renovar orçamento: máximo total duas tentativas/7200 s,
3600 s por tentativa, 1800/240/60 s para sequência/etapa/SQL, heap512 MiB.
I/J seguem IMPLEMENTADO_NAO_QUALIFICADO até prova física integral. P05/K e
P06–P08 não autorizados; 39/45 e 67/115 preservados. Inventário e cópias antes:
`target/p04-desbloqueio-0188/`; checkpoint0187 e ledger anterior imutáveis.

# Macrobloco P04 — requalificação física bloqueada; P05 não iniciado (20/09/2026)

Ordem efetiva do usuário: `P04-REQUALIFICACAO-20260919-01`, limitada a duas
tentativas físicas seriais de até 3.600 s, somente em
`localhost/ETL_SISTEMA_V2_SHADOW`, com dados sintéticos, Windows integrado,
rollback-only, commit de domínio bloqueado e as duas travas Maven. O ledger novo
é `target/macrobloco-p04-requalificacao-20260919-01/ledger.json`; ele não reutiliza
o ledger fechado `P04-P05-APOS-0185-01` nem autoriza P05/K.

O preflight isolado em JDK17 passou: `QualificationSupervisorTest` 1,
`QualificationContractTest` 7 e `QualificationJournalTest` 4 (12/12), com
Enforcer, Spotless e Checkstyle verdes. `Test-TrilhaPreparation.ps1` também
passou apenas como validação do mapa. A consulta read-only no `master` confirmou
o alvo exato, e nenhum processo da campanha anterior estava ativo.

`p04-requalificacao-physical-01` foi reservada e executada pelo controlador
existente. A fase `Physical` montou o JAR e as bibliotecas runtime seladas antes
do Failsafe. As 12 unidades, sweep/preview 5/5, cancelamento 3/3 e concorrência
1/1 passaram; a família de retomada teve 2/6 aprovados e 4 casos com saída 2.
A sequência não emitiu recibo antes do teto de 240 s por etapa. Foi encerrada
somente a árvore comprovadamente própria do controlador; o readback agregado
antes/depois é igual e confirma rollback. Não houve recibo final do controlador,
pois o encerramento limitado ocorreu antes dele gravá-lo.

I/J continuam **IMPLEMENTADO_NAO_QUALIFICADO/BLOQUEADO_POR_INPUT**. A evidência
não delimita com segurança uma causa local corrigível para os exits 2 sem a
coleta recursiva de fixture proibida; portanto a segunda tentativa não foi
reservada nem executada. P05/K permanece não iniciado, P03/B–H é somente
predecessor técnico local `ACEITO_NO_ESCOPO`, e P06–P08 continuam fora do
escopo. Contadores preservados: **39/45 construção e 67/115 aceites**.

Validações atuais: JSON e UTF-8 dos registros novos PASS,
`Test-TrilhaPreparation.ps1` PASS e `git diff --check` sem erro. O validador
documental `Test-Gpt56ChatTrail.ps1` permanece FAIL em
`STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`; é a falha histórica de
sucessão, preservada e não compensada por alteração de hash/manifesto.

# Macrobloco P04→P05 — P04 esgotado sem aceite; P05 não iniciado (20/09/2026)

Ordem adotada `P04-P05-APOS-0185-01`, exclusivamente em
`localhost/ETL_SISTEMA_V2_SHADOW`, com dados sintéticos, Windows integrado,
rollback-only e as duas travas Maven. Ledger próprio:
`target/macrobloco-p04-p05-apos-0185-01/ledger.json`; início
2026-09-20T01:40:20.5555786Z, vigência até 2026-09-22T01:40:20.5555786Z,
duas reservas P04 de 3.600 s consumidas, saldo de reserva 3.600 s que **não
autoriza P05** enquanto I/J não forem aceitos.

`p04-0185-01` foi OBSERVED_FAILED (exit 1), com readback agregado igual antes/
depois e rollback confirmado. Journal 4/4, cancelamento 3/3, concorrência 1/1
e sweep/preview 5/5 passaram; 6 cenários de retomada e 5 do supervisor pararam
antes do worker porque a fase `Physical` não montava o JAR exigido pela fixture.
O controlador local foi corrigido para gerar o JAR e as bibliotecas bloqueadas
antes da seleção dirigida; a montagem offline passou com JDK17, Enforcer,
Spotless e Checkstyle.

`p04-0185-02` criou o JAR e as dependências, também confirmou rollback agregado,
mas não fecha I/J: 4/6 cenários de retomada falharam ao montar o classpath do
filho com `Path.resolve("*")` no Windows. A tentativa foi interrompida no
limite de 240 s do passo após diagnóstico de coleta recursiva de fixture;
receipt OBSERVED exit -1, logs íntegros, nenhum processo próprio remanescente.
Foi corrigida a composição para manter `*` fora de `Path.resolve`, coberta por
`QualificationSupervisorTest`; teste offline dirigido 12/12 PASS. Falta uma
nova autorização/reserva física para provar essa revisão. Logo I e J são
**IMPLEMENTADO_NAO_QUALIFICADO/BLOQUEADO_POR_INPUT**, P05/K não foi iniciado,
P03 continua ACEITO_NO_ESCOPO apenas como predecessor técnico local, e P06–P08
permanecem fora do bloco. Sem DDL, migration, fonte real, commit de domínio ou
alteração de índice.

Contadores preservados: **39/45 construção e 67/115 aceites**. Evidências:
`target/macrobloco-campanhas-integrais-20260915-01/p04-0185-01/`,
`p04-0185-02/` e `target/macrobloco-p04-p05-apos-0185-01/`.

# Preparação integral P01–P33 — mapa e pré-condições conferidos (19/09/2026)

Pedido atual do usuário: preparar o terreno de toda a trilha antes de continuar
a construção. Entrega estritamente local/offline, sem execução física P04/P05,
nova reserva, SQL/JDBC, fonte, Maven, DDL, credencial, commit/push ou índice Git.
O inventário inicial preservou 2.983 entradas preexistentes do worktree, hashes
de 2.069 arquivos src/database, índice e cópias dos oito documentos/ledgers de base
em `target/preparacao-trilha-20260919-01/BASELINE.json`.

Entregas: [mapa P01–P33](docs/catalogos/preparacao-trilha/plano.json),
[guia operacional e pacote único de entradas](docs/runbooks/preparacao-integral-trilha.md)
e [verificador offline](scripts/validation/Test-TrilhaPreparation.ps1).
Os 33 P estão ligados aos 48 IDs abertos, dependências por fatia, G01–G08/FEED,
artefatos reutilizáveis e critérios de saída. `prepare` é trabalho a fazer, não
evidência; `executionAuthorized=false`. Não cria novo backlog nem aceita uma etapa.
Self-test executado: um caso positivo e 24 negativos PASS, incluindo ciclo,
dependência ausente, caminho inválido, aceite/permissão indevidos, drift de fonte
e dependência falsa entre sweep e fatos. PASS do mapa não substitui qualificação,
scanner integral, sucessão histórica, autorização ou gate de pacote.

Retificação de alcance do bloqueio0184: a ordem original da campanha estabelece
tetos por sequência/etapa/campanha e exige registrar teto/saldo, mas não fixa
expiração explícita ou número global de tentativas. O prompt P04 posterior exige
conferir saldo/vigência sem renovação e exclui P05–P08. Portanto, ausência desses
campos **não prova expiração nem esgotamento de um total numérico definido**;
também não concede saldo infinito ou nova campanha. A exigência precisa ser
reconciliada com a ordem efetivamente aplicável antes do próximo efeito, não
repetida como falha técnica do controlador. Não repetir autorização comprovada
vigente. O guia deixa uma proposta finita P04→P05 pronta para decisão, somente
se ainda necessária; ela NÃO foi adotada/reservada por este pedido de preparação.

P04/I–J permanecem sem aceite físico e P05 sem execução; resultados históricos
sql-01..06 OBSERVED/rollback agregado permanecem na0184, sem nova inspeção SQL.
B–H conserva o recorte técnico registrado; hashes de11 testes não são prova de
todos os consumidores atuais, nem IT in-process substitui JAR extraído de P08.
Essas verificações de suficiência estão preparadas, não declaradas resolvidas.
Falhas históricas de sucessão/scanner continuam próprias de P08, não de G01–G08.

Contadores inalterados: **39/45 construção (86,7%) e67/115 aceites (58,3%)**;
zero unidade/aceite novo. Cobertura33/33 do mapa não mede construção concluída.
A matriz45 mantém V2-017 não contado na métrica herdada, embora seu checkbox
canônico já esteja aceito: não somar/reabrir automaticamente esse item.
Próxima escolha deve usar a seção14 da trilha e o guia, conferindo inputs antes
de produzir prompt físico. Não gerar outro bloco para repetir um hold inalterado.
Prefácios abaixo permanecem históricos; fechamento desta preparação no checkpoint0185.

Verificações desta preparação: `gates-01/verification.json` confirmou UTF-8/diff,
índice e os2.069 hashes src/database preservados; `Test-Gpt56ChatTrail.ps1`
continua FAIL em `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`.
O scanner integral ultrapassou o watchdog offline de60s em `gates-01`, sem
conclusão; uma segunda execução read-only delimitada a180s (`scan-02/result.json`)
concluiu sem timeout:3.570 candidatos,3.561 textos e1 binário verificado,
zero oversized/não-texto inesperado, FAIL exclusivamente pelos8
`MISSING_CANDIDATE` das exclusões preexistentes. Nenhum novo achado de conteúdo.
Não restaurar exclusões ou reescrever manifests históricos para produzir PASS.
Logs e tentativas ficam em `target/preparacao-trilha-20260919-01/`;
`final-01/verification.json` vincula o fechamento documental, sem selo P08.

# P04 — gate de saldo/vigência bloqueia I–J; preflight offline verde (19/09/2026)

A reconciliação da rodada `p04-supervisor-preview-20260919-01` confirma que
`p04-supervisor-sql-01..06` têm `result.json` em `OBSERVED`, sem timeout e com
`rollbackConfirmed=true`; portanto não há tentativa de resultado desconhecido a
repetir. Cada `reservation.json` declara somente `budgetSeconds=3600` para sua
própria tentativa. O `LEDGER.md` não declara saldo cumulativo, vigência atual,
quantidade permitida nem tetos para uma nova reserva P04. O gate obrigatório
falhou antes de criar diretório, reserva, processo, conexão JDBC ou SQL:
P04/I–J é **BLOQUEADO_POR_INPUT** e P05/K não é elegível nem possui saldo próprio
demonstrado. O input exato é um ledger/autorização vigente que cubra uma nova
tentativa serial P04 em `localhost`/`ETL_SISTEMA_V2_SHADOW`, com rollback-only,
saldo, quantidade/escopo, vigência e todos os tetos declarados.

O preflight independente foi executado localmente em JDK17 (`17.0.20.1`) com
`JAVA_HOME` limitado ao processo: `QualificationContractTest` teve 7 testes,
0 falhas, 0 erros e 0 skips; Enforcer, Spotless e Checkstyle passaram. O primeiro
preflight com `JAVA_HOME` preexistente em JDK25 foi corretamente recusado pelo
Enforcer e não é evidência de código. Depois, `mvn --offline -DskipTests package`
passou com os mesmos gates. Nenhum SQL/JDBC, worker, DDL, migration, fonte,
commit de domínio, deploy, agenda, credencial ou alteração de índice ocorreu.
I/J não recebem recibo físico desta revisão; P05 não foi iniciada. Contadores
preservados:39/45 (86,7%) e67/115 (58,3%). Checkpoint:0184.

# P04 — fixture temporal corrigida; prova física pendente de saldo/vigência (19/09/2026)

`QualificationPackageFixture.sequenceCampaign` agora declara prazo lógico de
259200s. O valor cobre a primeira janela civil de 11/08/2037, que termina em
12/08 às03:00Z e, com o tick da fixture em14/08 às12:00Z, vence em15/08 às03:00Z.
Não altera os limites físicos (etapa240s, sequência1800s, tentativa3600s,
SQL60s, heap512MiB). `QualificationContractTest` passou7/7 offline em JDK17:
a configuração histórica86400 continua `LOGICAL_DEADLINE_EXCEEDED`, as três
configurações de sucesso/falha tardia/cancelamento são `PASS_LOCAL/DUE` e o tick
um segundo após a fronteira volta a ser recusado. `mvn --offline -DskipTests
package` também passou com Enforcer, Spotless e Checkstyle.

Os critérios técnicos B–H de P03 foram reconciliados como
**ACEITO_NO_ESCOPO somente para admitir P04 local**, com relação explícita de
critério, componente, pin atual, recibo físico e limitação em
`docs/catalogos/campanhas-integrais/P03-B-H-RECONCILIACAO-20260919.md` e na
matriz A–N. Os11 hashes de classes selecionadas ainda coincidem com
`p03-corrections-20260919/code-readback.json`; as provas físicas reutilizadas
são `p03-campaign-sql-07` (30unit/6IT) e
`p03-relational-counterproof-02` (41IT), com a falha histórica de
`p03-regression-sql-01` preservada e seus demais25IT reutilizados somente pelos
bytes coincidentes. Isso não aceita P03 agregado, A–N, P04/I–J, paridade real ou
produção.

P04/I–J permanece **BLOQUEADO_POR_INPUT** após a correção offline: as seis
tentativas `p04-supervisor-sql-01..06` têm `result.json` observado e rollback
agregado, cada `reservation.json` reserva3600s, mas o ledger/authorization
vigente não registra saldo cumulativo nem vigência para uma sétima reserva. Não
foi criada reserva nem executado SQL/JDBC/worker nesta unidade. O desbloqueio
exato é ledger/autorização vigente que informe saldo disponível e permita uma
nova tentativa serial P04 no alvo já delimitado; ela deverá usar o controlador,
nova reserva e os critérios I/J completos. Contadores preservados:39/45
(86,7%) e67/115(58,3%). P05–P08 continuam fora do escopo.

# P04 — causa do bloqueio comprovada no planejamento offline (19/09/2026)

Retificação do diagnóstico abaixo: nos três casos falhos de `p04-supervisor-sql-06`,
o `exit=2` pertence ao supervisor, não ao worker. Os resultados persistidos são
`BLOCKED_DEPENDENCY`; os journals têm RESERVED/DEFERRED, sem STARTED e sem
`process.json`/`receipt.json` do filho. A hipótese anterior de classpath não explica
essas falhas, pois a execução nem chegou ao lançamento do subprocesso.

Reprodução offline com as classes e campanhas preservadas de sql-06:
`QualificationPlanner.plan` retorna `LOGICAL_DEADLINE_EXCEEDED` nos três casos.
A fixture declara 11–14/08/2037, tick em 14/08 às 12:00Z e prazo lógico de86400s;
a primeira janela diária vence em13/08 às03:00Z. Alterando somente
`deadlineSeconds=259200` em cópia em memória, os três planos retornam
`PASS_LOCAL/DUE`. Isso comprova uma correção candidata da fixture, não execução
do worker, rollback interno ou aceite I/J. Nenhum código foi alterado nesta
investigação, nem houve nova prova SQL, DDL ou chamada de fonte.

Próximo passo delimitado: corrigir a fixture com regressão offline que preserve
a recusa por atraso; conferir os pins/critérios B–H e só então reservar nova prova
P04 pelo controlador. Não ampliar os limites físicos de240/1800/3600s, SQL60s ou
heap512MiB. A afirmação anterior de aceite de P03/B–H inteiro não está comprovada:
somente o recorte do checkpoint0181 permanece aceito; a matriz A–N ainda contém
frentes EM_EXECUCAO com proofs vazios e requer vinculação explícita das evidências.

Progresso reconferido:39/45 unidades de construção =86,7%;67/115 checkboxes
de aceite =58,3%, com48 abertos. Não são percentuais de prontidão produtiva nem
medidas equivalentes. Nenhum checkbox foi fechado por este diagnóstico.
Checkpoint0182 documenta causa, caminhos, limitações e a lacuna de continuidade
(RETOMADA ainda apontava apenas para P03). Os prefácios seguintes são históricos;
as afirmações sobre worker e aceite agregado acima retificadas não são vigentes.

# P04 — supervisor e preview: BLOQUEADO_POR_CORRECAO_LOCAL (19/09/2026; diagnóstico histórico retificado acima)

P03/B–H permanece **ACEITO_NO_ESCOPO** pelos recibos já registrados; P04/I–J não foi aceito.
Reconciliação de `p04-directed-01..09` e `p04-supervisor-sql-01..05`: todos têm
`result.json`; as tentativas SQL anteriores fizeram rollback e falharam no worker. A causa
inicial (classpath do subprocesso omitia `lib/*`) foi corrigida em
`QualificationSupervisor`; `p04-directed-10` compilou com `exit=0`.

`p04-supervisor-sql-06` foi executada serialmente pelo controlador, em
`localhost/ETL_SISTEMA_V2_SHADOW`, com `QualificationSequenceSupervisorIT`, perfil opt-in,
heap 512 MiB e rollback-only. Resultado observado: `exit=1`, sem timeout ou excesso de log,
agregados before/after idênticos e `rollbackConfirmed=true`; 5 IT, 3 falhas, 0 erros, 0 skips.
Sucesso, falha tardia e cancelamento ainda retornaram `exit=2` no worker; recusa de input e
owner vivo passaram. Não iniciar P05 nem promover I/J. Próximo input técnico: diagnosticar o
recibo/diagnóstico sanitizado do worker após o classpath corrigido e criar contraprova causal;
não repetir `sql-06` sem essa mudança.

# Recorte P03 comprovado — sucessão MC e referência tarifária (19/09/2026)

**ACEITO_NO_ESCOPO deste recorte**, sem concluir P03 inteiro ou pais V2. A/B sete etapas e referência três etapas aprovadas; cinco fatos/19saídas/33previews preservados.39/45 e67/115 inalterados. Relatório: [P03-CORRECOES-20260919](docs/catalogos/campanhas-integrais/P03-CORRECOES-20260919.md). Rodada: target/macrobloco-campanhas-integrais-20260915-01/; evidências consolidadas em p03-corrections-20260919/.

V103 declara conjuntos MC completos por origem incluída; revisões anteriores ficam SUPERSEDED, sem inferir aposentadoria por captura parcial/ausência de outra origem. V104 registra revisão tarifária independente do frescor e cria snapshot encadeado com fonte preservada. Replay da mesma combinação permanece no-op e fonte stale não altera tarifa. ADR0052, baseline e consumidores/validadores sincronizados. V001–V102 não foram editadas/reaplicadas.

Schema104 qualificado por upgrade02/baseline02 transacionais, catálogos idênticos, contraprovas MC e rollback. Instalação persistente exclusivamente DDL local: p03-schema-install-01 CONFIRMED; master/alvo/hashes conferidos,1816objetos/definições iguais ao qualificado,246tabelas e244contagens anteriores preservadas. Prova de equivalência do sufixo sobre102; não recriação física do banco. Recibos técnicos externos pelo mecanismo existente, sem criação de ledger SQL, grant ou dado de domínio persistente. Recuperação pós-instalação exige nova migration compensatória revisada.

Qualificação funcional composta da revisão entregue: **30unit e72IT distintos aprovados**, zero skips nos recibos. Campanha07:30unit+6IT PASS,19m53s, A/B7etapas/referência3/recomposição. Regression01:66IT,65PASS/1erro de preparação da nova contraprova MC (evidenceId sem prefixo synthetic-),7m40s, preservada como FAIL. Correção somente nos quatro nomes técnicos, sem mudar IDs de domínio/relações/cardinalidade/expectativas; relational-counterproof02:41IT PASS,2m11s, nova reserva600s. Os25IT das demais classes de01 são reutilizados por bytes idênticos. Rollback/agregados confirmados nas três provas físicas; sem timeout. Source freshness/hash e páginas preservados na referência; replay, stale, conflito contemporâneo, componente omitido, origem omitida, histórico, isolamento e falha COL→FRE verificados.

Readback:695arquivos de runtime idênticos às três revisões físicas;11classes selecionadas ligadas aos bytes testados; migrations instaladas iguais aos hashes qualificados, fixtures/oráculos da campanha inalterados,157manifests históricos intactos, índice e oito exclusões Git preservados. Nenhum processo próprio continua ativo. Build/Enforcer/Spotless/Checkstyle e checks estáticos de schema/progressive/COT/package guards PASS. Scanner self-test PASS; scanner integral mantém somente8MISSING_CANDIDATE preexistentes, e validator histórico mantém STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md. A base histórica isolada permanece PASS; não houve regravação de manifests ou PASS artificial.

P04–P08 não iniciados. Não é paridade real, aceite nominal, pacote final ou prontidão produtiva. Próximo macrobloco elegível: conferir o saldo de P03 e admitir P04(supervisor/preview) somente com seus predecessores atendidos; GPT-5.6 Terra/High conforme trilha, Astra/High se surgir nova decisão semântica. Não gerar prompt sem solicitação. Checkpoint final0181: docs/continuidade/checkpoints/0181-p03-recorte-comprovado-schema104.md; prefácios seguintes são fotografias históricas.

# Recorte P03 — regressão01 reconciliada; contraprova MC em execução

Regression01:66IT/65PASS/1erro da nova contraprova,zero skips/falhas de asserção,rollback confirmado. Causa: quatro evidenceId sem prefixo synthetic-, recusados no construtor antes de bind/resolve. Corrigidos só os nomes técnicos; produto/migrations/IDs de domínio/relações/expectativas intactos. Nova reserva p03-relational-counterproof-02, somente MatrixIT41,600s/512MiB/rollback. Campanha07 mantém30unit/6IT PASS; fonte preservada e A/B sete etapas comprovados. Checkpoint0180: docs/continuidade/checkpoints/0180-p03-regressao-e-contraprova-tecnica.md.39/45 e67/115; recorte aguarda prova final, P03 inteiro aberto.

# Recorte P03 — A/B e referência aprovados; regressão em execução

p03-campaign-sql-07 OBSERVED/exit0:30unit+6IT PASS,zero skips/falhas/erros. A/B sete etapas, referência três etapas com fonte/frescor preservados, recomposição e cinco fatos/19saídas/33previews. Rollback/agregados idênticos confirmados; schema104 qualificado/instalado. p03-regression-sql-01 em execução para contraprovas/consumidores atingidos. Checkpoint0179: docs/continuidade/checkpoints/0179-p03-campanhas-e-referencia-aprovadas.md. Recorte ainda aguarda regressão/readback; P03 inteiro não concluído;39/45 e67/115 preservados. Prefácios seguintes históricos.

# Recorte P03 — schema104 instalado, validação funcional em curso

V103/V104 instaladas no alvo local autorizado após upgrade02/baseline02 rollback PASS. Recibo p03-schema-install-01 CONFIRMED; catálogo1816objetos confere e244contagens preexistentes preservadas.27unitPASS em p03-directed-01; p03-campaign-sql-07 ainda em execução. Checkpoint0178: docs/continuidade/checkpoints/0178-p03-schema104-instalado-provas-em-curso.md.39/45 e67/115 preservados; nenhum aceite agregado. Prefácios abaixo históricos.

# Recorte P03 — evolução SQL autorizada e em execução (19/09/2026)

O pedido atual substitui a restrição anterior somente para corrigir sucessão MC e revisão tarifária com migrations V2 aditivas locais. V103/V104 preparadas; ADR0052 e baseline sincronizados. Upgrade02/baseline02 qualificados com rollback, catálogo qualificado idêntico e244contagens preservadas. Instalação e provas Java funcionais ainda pendentes. Checkpoint0177: docs/continuidade/checkpoints/0177-p03-migrations-qualificadas.md. Evidências em target/macrobloco-campanhas-integrais-20260915-01/p03-corrections-20260919/.39/45 e67/115 preservados. Os prefácios seguintes são históricos; o bloqueio de autorização de0176 foi removido pelo pedido efetivo, não por este registro.

# Estabilização P01→P02→recorte P03 — 19/09/2026

P01 reconciliado; P02 com causas demonstradas; recorteP03 BLOQUEADO_POR_INPUT (evolução SQL fora da autorização atual). Relatório: [ESTABILIZACAO-20260919](docs/catalogos/campanhas-integrais/ESTABILIZACAO-20260919.md). Evidências em `target/macrobloco-campanhas-integrais-20260915-01/stabilization-20260919/`; prova física `sequence-campaign-sql-06/` na mesma rodada.

Base0165:3505snapshots e7contraprovas de sucessão PASS na cópia histórica.04/05 e demais tentativas anteriores reconciliadas, processos próprios ausentes. Inventário inicial3540;delta25modificados/35novos. Oito exclusões Git preexistentes e manifests históricos preservados. Sucessão limitada às provas pelo inventário/delta/inputs; selagem/consumidor final P08 pendentes. O validator histórico no worktree continua FAIL por `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`.

Tentativa06 OBSERVED/exit1/rollbackConfirmed=true, agregados before/after idênticos, sem timeout ou drift de logs.27unitPASS;6IT:2PASS de recomposição,2erros A/B de campanha e2falhas de referência;zero skips. Compilação/Enforcer/Spotless/Checkstyle passaram. Maven19m24s sob teto3600s; sem retry. Fontes Java finais conferidas contra build testado. Schema/baseline/migrations e índice Git sem mudança nesta rodada.

Causas: na quinta etapa A/B, troca de origin_component deixa quatro MC rank1 concorrentes (dois antigos), quatro conflitos ONE_TO_ONE e zero links ativos; a cardinalidade da linhagem recusa corretamente. Na referência, SQL-05 expected2/observed0: V011/V085 mantêm tarifa/snapshot por frescor igual, V086 exige release nova. O executor interrompe no segundo resultado; expected3/actual2 escondia a comparação anterior. Teste agora expõe a divergência, preservando oráculo, sete/três etapas, identidades, cardinalidade e33previews. Instrumentação agrega somente contagens; dois novos casos unitários A/B validam preservação da fonte/revisões. Nenhum workaround funcional ou DDL aplicado.

A estabilização não está concluída. P03 e paisV2 permanecem abertos;39/45 e67/115 inalterados. Próximo macrobloco proposto: concluir este recorteP03 com sucessão relacional/revisão tarifária e provas A/B, GPT-6 Astra/High, condicionado a autorização explícita para preparar/qualificar/aplicar migrations aditivas locais e sincronizar baseline. A proibição DDL/migrations do pedido19/09 impede essa ação agora. P04–P08 não iniciados. Recomposição independente aprovada não é campanha integral, paridade real ou prontidão produtiva.

Self-test scanner PASS16casos; scanner final antes do checkpoint:3551candidatos/3542textos/1binário, FAIL somente pelos oito MISSING_CANDIDATE preexistentes, sem achado de conteúdo secreto. Git diff check exit0; diff próprio/UTF-8/115checkboxes(67marcados)/índice/schema/manifests conferidos em final-audit.json. Checkpoint final0176: docs/continuidade/checkpoints/0176-estabilizacao-diagnosticada-bloqueio-sql.md; falhas históricas preservadas.

# Uma entrada e uma entrega final por macrobloco (19/09/2026)

Regra explícita solicitada pelo usuário: cada prompt adotado inicia um macrobloco com uma entrada do usuário e termina com uma entrega final consolidada. Executar autonomamente as etapas internas cobertas, sem pedir “continue”, confirmação de rotina ou um novo prompt por tarefa; não encerrar a resposta apenas com plano ou resultado intermediário quando ainda houver trabalho elegível no escopo. Atualizações breves de andamento não exigem resposta e não substituem a entrega final.

Dimensionar o macrobloco a partir deste STATES e da [trilha revisão3.3](TRILHA_CONCLUSAO_POR_MODELO.md), com dependências e ponto de parada explícitos. Se surgir bloqueio real, preservar os limites, concluir o trabalho independente autorizado e consolidar na entrega o resultado parcial, a causa e o input necessário. A regra não cria autorização nem permite alegar conclusão sem prova. Próximo prompt continua sendo gerado somente quando solicitado.

Manutenção documental;33etapas,39/45 e67/115 preservados, sem execução do macrobloco sugerido no chat. Evidências em `target/trilha-entrada-saida-20260919-01/`; checkpoint0174. A falha histórica de sucessão permanece registrada, sem alteração de manifests ou aceite funcional novo.

# Próximo chat por macrobloco — regra vigente (19/09/2026)

Correção explícita do usuário: ele solicitará o prompt quando quiser trocar de chat. Nessa solicitação, cruzar este STATES com [TRILHA_CONCLUSAO_POR_MODELO.md](TRILHA_CONCLUSAO_POR_MODELO.md), revisão3.2, para selecionar um macrobloco coeso de tarefas que possam ser concluídas no mesmo chat, na ordem correta, e indicar um GPT/nível para o conjunto. Os 33 passos não equivalem a33chats. A passagem automática por tarefa descrita no prefácio0172 abaixo foi uma interpretação rejeitada e fica histórica.

O executor do macrobloco mantém o estado e as evidências atualizados, avança entre as etapas internas elegíveis sem pedir continuação por item e encerra no limite combinado ou bloqueio comprovado. Quando o usuário pedir o próximo prompt, selecionar novamente pelo estado atualizado, sem depender da memória da conversa. Os dois arquivos orientam a seleção; documentos operacionais, critérios e provas referenciados continuam obrigatórios antes da execução. Preparar o prompt não executa nem autoriza efeitos do macrobloco.

Manutenção documental: P01 ainda não executado;33etapas,48IDs abertos,39/45 e67/115 preservados. Rodada `target/trilha-macroblocos-chat-20260919-01/`; checkpoint0173. A falha histórica de sucessão permanece pendente, sem alteração de manifests. Nenhum aceite funcional novo.

Verificação desta correção: PASS documental para as33linhas de etapas/dependências/modelos idênticas,115checkboxes/67concluídos preservados,13links locais e diff sem erro. Não houve build, teste Java/SQL ou fonte; o validador histórico não foi repetido porque sua falha conhecida e a dependência de sucessão não mudaram.

# Passagem entre chats — manutenção documental (19/09/2026)

Pedido do usuário: deixar documentado como cada chat deve encerrar a tarefa e entregar, na própria resposta, um prompt completo para o próximo chat. A seção13 de [TRILHA_CONCLUSAO_POR_MODELO.md](TRILHA_CONCLUSAO_POR_MODELO.md), revisão3.1, define a instrução copiável, o conteúdo obrigatório e o modelo a ser preenchido pelo executor. O protocolo de continuidade e o prompt inicial passam a exigir essa entrega, com modelo/nível, estado comprovado, evidências, limites e próximo escopo elegível. O prompt não depende da memória do chat anterior e não concede novas autorizações.

Planejamento/documentação somente: P01 ainda não executado; ordem P01–P33, critérios canônicos, 39/45 e67/115 preservados. Rodada: `target/trilha-handoff-20260919-01/`; checkpoint documental0172. Verificação documental PASS: 33 linhas de etapas/modelos e115checkboxes/67concluídos idênticos, 13 links locais e UTF-8 conferidos; diff check exit0. O validador histórico repetiu `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md` (exit1), sem regravação de manifests para ocultar a falha. Não há nova prova Java/SQL/fonte nem aceite funcional.

# Trilha revisada por precedência — 19/09/2026

Pedido atual: revisar seriamente a trilha de conclusão pelo STATES e deixá-la em ordem de prioridade/dependências. A revisão P01–P33 de [TRILHA_CONCLUSAO_POR_MODELO.md](TRILHA_CONCLUSAO_POR_MODELO.md) substitui as recomendações ECO anteriores. Primeiro passo proposto para o próximo trabalho: P01/Terra Medium, reconciliação documental/read-only da campanha e seus bloqueios. A criação da trilha não executa P01 nem inicia nova campanha.

Decisões de ordem: terminar A–N respeitando L antes de M/N; prover ambiente/autorização antes de dados reais; separar contrato/identidade, adequação em sombra e V2-012a; V2-012a antecede bootstrap, relações aplicáveis antecedem V2-012b, e entradas PUBLISHED/paridade core antecedem fatos. V2-035a antecede seus consumidores; V2-035b segue as fontes usadas e não as bloqueia. V2-012c é posterior ao fato/contrato. Qualificação e release precedem ensaio/corte DATABASE_WIDE. Inputs externos são cobrados desde o início e bloqueiam apenas sua parcela. Não há corte por entidade presumido.

Planejamento documental, sem mudança dos critérios originais, 39/45 ou 67/115. Snapshots/evidências: `target/trilha-prioridades-20260919-01/`; checkpoint documental0171. Campanha técnica ainda EM_EXECUCAO conforme0168 e recibos posteriores. O seletor antigo do Bloco55 e as recomendações ECO nos prefácios seguintes são históricos; a autorização efetiva e os critérios canônicos continuam soberanos.

Verificação desta revisão: PASS documental para 33 etapas, dependências acíclicas, 32 conjuntos explícitos de predecessores, cobertura dos 48 IDs abertos, nove links locais e UTF-8; os 115 checkboxes canônicos e seus 67 concluídos permaneceram idênticos. O validador histórico foi executado e repetiu `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md` (exit1), já registrado antes desta manutenção. Não foi alterado para mascarar a falha. Scanner integral, build/Java, banco e fonte não foram repetidos nesta revisão documental; nenhum aceite funcional foi fechado.

# Revisão da economia por modelo — 19/09/2026

Após o usuário questionar a vantagem econômica de Sol, a trilha passa a usar custo total por entrega aceita: Terra Medium/High para execução delimitada; Astra Medium direto para diagnóstico e High para decisões críticas; Luna para consolidação. Sol torna-se alternativa opcional, sem bloco obrigatório, condicionada a consumo/resultado observado. A tarifa por token não demonstra custo final; não houve benchmark local nem promessa de economia. Os 27 blocos, cobertura dos 48 itens abertos, 39/45 e 67/115 permanecem. Roteiro: [TRILHA_CONCLUSAO_POR_MODELO.md](TRILHA_CONCLUSAO_POR_MODELO.md). Snapshots/logs: `target/trilha-economia-20260919-02/`; checkpoint documental0170. Validação documental PASS: 27 blocos (18 Terra, sete Astra, dois Luna), zero Sol obrigatório, cobertura48 e115checkboxes/67concluídos preservados. O validator histórico repetiu a mesma falha de hash registrada em0169; scanner integral/Java/banco não foram repetidos nesta revisão. Nenhum código, requisito, autorização ou aceite funcional mudou. As recomendações anteriores de Sol nos prefácios abaixo são históricas.

# Trilha econômica por modelo — manutenção documental (19/09/2026)

Pedido atual do usuário: criar na raiz uma trilha de conclusão por GPT e nível de esforço para economizar uso. Entrega documental: [TRILHA_CONCLUSAO_POR_MODELO.md](TRILHA_CONCLUSAO_POR_MODELO.md), com ECO-00–26, dependências, critérios, cobertura dos 48 checkboxes abertos e prompts de retomada. Os IDs ECO não consomem blocos funcionais nem concedem autorização. Terra é o executor padrão; Sol trata diagnóstico/integração; Luna consolida evidência explícita; Astra fica restrito às decisões críticas indicadas.

O estado funcional continua sendo a campanha A–N `EM_EXECUCAO`, base0165/schema102 conforme registros anteriores; 39/45 e 67/115 preservados. Esta manutenção não executou Java, SQL, fonte, CI, deploy ou cutover, nem requalificou a base. A leitura adicional dos recibos encontrou `sequence-campaign-sql-05/result.json` como OBSERVED/exit1/rollbackConfirmed=true e XMLs com quatro IT, duas falhas e dois erros; o WORKLOG distingue essa revisão da autoria posterior de sete etapas. A retomada técnica deve reconciliar essas evidências (ECO-00/Terra Medium), sem repetir efeitos pela indicação histórica da tentativa04 no checkpoint0168.

Inventário, snapshots anteriores e logs desta manutenção: `target/trilha-economia-20260919-01/`. Antes das edições, o validator existente `Test-Gpt56ChatTrail.ps1` já falhou com `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`; a lacuna de sucessão é preexistente e não será escondida alterando manifest histórico. Validação documental executada: PASS para 27 blocos, cobertura exata dos 48 itens abertos, 115 checkboxes/67 concluídos inalterados, dez links locais, UTF-8 e preservação dos bytes anteriores; diff sem erro de whitespace. O validator da trilha repetiu o mesmo erro de sucessão antes/depois. O scanner integral inspecionou 3.533 textos e um binário e terminou FAIL por oito MISSING_CANDIDATE, todos correspondentes a arquivos já deletados no inventário inicial; nenhuma exclusão foi feita nesta rodada. Logs e limitações estão no checkpoint0169; não há alegação de gate integral verde. Nenhum checkbox funcional foi alterado. Os prefácios seguintes conservam suas fotografias históricas.

# Campanhas integrais — progresso0168

EM_EXECUCAO. Checkpoint:docs/continuidade/checkpoints/0168-recomposicao-e-falhas-provadas-campanha-em-correcao.md; SHA256 7b5614655bdd48cc6127d63b103050687ab0a47210caf8198297cbb43cdde596. Recomposição2IT e falhasCOL4IT passaram na revisão03; campanha ampliada teve2erros corrigidos emprova04. Base0165/schema102,39/45 e67/115 preservados. Continuar A–N na mesmaordem sem perguntas/subagentes/continue; consultar WORKLOG/process/result. Pacote/escalas/gatefinal/entrega pendentes. Prefácios seguintes históricos.

# Campanhas integrais — progresso0167

EM_EXECUCAO. Checkpoint:docs/continuidade/checkpoints/0167-sequencia-e-agenda-provadas-recomposicao-em-curso.md; SHA256 307a4dc5319f1c62cd4627aca8dc7aefd53eddc2417f949b1646e6356a2d2cdc. 13unit e4IT novos comprovados na revisão indicada, rollback confirmado; recomposição e campanhas completas ainda em implementação. Base0165/schema102,39/45 e67/115 preservadas. Prosseguir A–N sem pergunta/subagente/continue; consultar WORKLOG e recibos. Prefácios seguintes históricos.

# Campanhas integrais — progresso0166

EM_EXECUCAO. Checkpoint:docs/continuidade/checkpoints/0166-sequencias-integrais-admissao-e-executor-em-prova.md; SHA256 0eacfa391198ccc445424c2a37cc72803321cb967e38d6b14598320a7431942e. Base0165/schema102 preservada;3505snapshots verificados. Executor e supervisão iniciais em prova; frentes A–N ainda pendentes de qualificação integral e pacote.39/45 e67/115 mantidos. Rodada:target/macrobloco-campanhas-integrais-20260915-01. Consultar WORKLOG/process/result antes de repetir. Pedido continua sem perguntas/subagentes/continue. Prefácios seguintes históricos.

# Campanhas integrais declaradas — EM_EXECUCAO

Nova capacidade local A–N adotada explicitamente em15/09/2026. Base0165/schema102,3505snapshots e seleção/selo/readback verificados;39/45 e67/115 preservados. Envelope/executor inicial e ligação ao supervisor implementados, ainda em qualificação. Agenda/recomposição, provas físicas, pacote e fechamento continuam pendentes; não é entrega final. Rodada:target/macrobloco-campanhas-integrais-20260915-01. Consultar WORKLOG/process/result antes de repetir efeitos. Sem perguntas/subagentes/continue; prosseguir nesta mesma ordem.

Prefácios seguintes são fotografias históricas.
# Execução do STATES — correções locais verificadas

**STATES_EXECUCAO_LOCAL_CONCLUIDA**, com fechamento M/N no selo externo/readback. Checkpoint:docs/continuidade/checkpoints/0165-states-execucao-local-concluida.md; SHA256 c55a15c9918094dd76ab1cae9edbff910c4b618b206b4dcac03e8b146f1d7d88. Cinco divergências corrigidas em cinco arquivos;2115unit/483IT, quatro skips históricos; JAR A2/B24 e ARTIFACT executados. Base0162/schema102, V099,39/45 e67/115 preservados.

Seleção atual: target/execucao-states-20260915-01/FINAL-DELIVERY.json e selo/readback apontados. Se ainda ausentes, continuar o fechamento local nesta mesma execução, sem pergunta ou nova ordem. Com todos os pins e PASS, escopo local entregue; parcelas externas G01–G08 específicas no catálogo execucao-states. Conferir efeitos desconhecidos antes de repetir. Prefácios seguintes são fotografias históricas.

# STATES — identidade fiscal em validação

EM_EXECUCAO. Checkpoint:docs/continuidade/checkpoints/0164-states-identidade-fiscal-em-prova.md; SHA256 d94a6dffd84867a1ac2c97b1c5549ad10d191dd83790926e21cd26f2783ac7ce. Quinto defeito reproduzido na composição de chaves fiscais; correção em prova. Quatro correções anteriores/42testes dirigidos passaram; full-verify-02 valida a revisão anterior e continua. Base0162/schema102,39/45 e67/115 preservados. Não é entrega final. Consultar WORKLOG/process/result em target/execucao-states-20260915-01 antes de repetir efeitos. Continuar provas, pacote/JAR e fechamento, sem nova ordem. Prefácios seguintes são históricos.

# Execução STATES — investigação e correções em andamento

EM_EXECUCAO conforme adoção explícita de target/preparacao-execucao-states-20260915-01/PROMPT-EXECUCAO-STATES.md. A condição anterior de admissão nominal foi revogada nesta execução. Três divergências decimais reproduzidas;60testes dirigidos passaram; integração SQL atual ainda em prova. Base0162/schema102,39/45 e67/115 preservados. Não é entrega final.

Checkpoint: docs/continuidade/checkpoints/0163-states-investigacao-decimal-em-prova.md; SHA256 b19bca21604069c65257846cda53ab562a2424a85e65147ad094fb51fca9cc19. Rodada: target/execucao-states-20260915-01/WORKLOG.md. Consultar processos/recibos antes de repetir efeitos. Continuar A–N sem perguntas/subagentes/continue. Prefácios seguintes são fotografias históricas.

# Cadeia integral — revisão local concluída

**CADEIA_INTEGRAL_LOCAL_CONCLUIDA**, com autoridade M/N no selo externo final/readback. Checkpoint: docs/continuidade/checkpoints/0162-cadeia-integral-concluida.md; SHA256 2c38ffc74ab401981b521963cbb734c6ba9bcbe03d2c73f71f3b237ae3027b37. Onze famílias, cinco fatos,19SQL, A2/B24 e campanha ARTIFACT pelo pacote extraído;2080unit/481IT, quatro skips históricos.39/45 e67/115 preservados, predecessor0154/V099 íntegros.

Seleção atual: target/macrobloco-cadeia-integral-20260914-01/FINAL-DELIVERY.json e selo/readback apontados. Com PASS e pins íntegros, esta entrada está entregue; restam somente parcelas externas G01–G08 no catálogo cadeia-integral-por-contratos. Se o seletor/selo ainda não existir, terminar o fechamento local nesta mesma execução, sem novo prompt. Não repetir efeitos desconhecidos. Os prefácios abaixo são fotografias históricas superadas por esta seleção.

<!-- CADEIA_INTEGRAL_EM_EXECUCAO checkpoint=docs/continuidade/checkpoints/0161-cadeia-integral-revisao-e-gate-completo.md sha256=e9c7261a2e25d0e552c4f9b528c31bb4702726e4f5ae5f44676f492148731bc7 predecessor=0154 -->

<!-- CADEIA_INTEGRAL_EM_EXECUCAO checkpoint=docs/continuidade/checkpoints/0160-cadeia-integral-lotes-e-regressao-v099.md sha256=4c089c5fdd5898c7fe65ddb42e26e5e2fe7bed45343d2c4da93c48b9370d350b predecessor=0154 -->

<!-- CADEIA-INTEGRAL EM_EXECUCAO 0159 1568918afa03796f2a383b150d1c91245f56e3b7c4834224919e67448cfb908e -->

Cadeia integral A–N em execução. Checkpoint: docs/continuidade/checkpoints/0159-cadeia-integral-replay-e-fronteiras-em-validacao.md; SHA256 1568918afa03796f2a383b150d1c91245f56e3b7c4834224919e67448cfb908e. Replay da mesma revisão, contraprovas de oráculo e isolamento por scope comprovados; revisão posterior, JAR extraído e fechamento em andamento. 0154/V099, 39/45 e 67/115 preservados. Não é entrega final.

<!-- CADEIA-INTEGRAL EM_EXECUCAO 0158 fd814051f695d0ba4ceba169b48d8f1b6096025762d48b45ebc8fd799cd6e1c4 -->

Cadeia integral A–N em execução. Checkpoint: docs/continuidade/checkpoints/0158-cadeia-integral-dois-conjuntos-e-preview-provados.md; SHA256 fd814051f695d0ba4ceba169b48d8f1b6096025762d48b45ebc8fd799cd6e1c4. A2/B24 passaram nos cinco fatos/19 SQL/33 previews; replay, JAR extraído e fechamento pendentes. 0154/V099, 39/45 e 67/115 preservados. Não é entrega final.

<!-- CADEIA-INTEGRAL EM_EXECUCAO 0157 ad36cb1d86f7a4ae721a302ba8100a62d80eafc7e838b294a4304e12e593c96f -->

Cadeia integral A–N em execução: [checkpoint 0157](docs/continuidade/checkpoints/0157-cadeia-integral-capturas-materializacoes-em-prova.md). Recibos e próximos passos no checkpoint; 0154/V099 e 39/45, 67/115 preservados. Não é entrega final.

<!-- CADEIA-INTEGRAL EM_EXECUCAO 0156 732d1e3d0335f125bf76dc7dd7aac00a34b0c1ac38b245e4cd35ce0180fd9573 -->

Cadeia integral A–N em execução: [checkpoint 0156](docs/continuidade/checkpoints/0156-cadeia-integral-contexto-oraculos-em-construcao.md). Recibos e próximos passos no checkpoint; 0154/V099 e 39/45, 67/115 preservados. Não é entrega final.

# Cadeia integral por contratos — EM_EXECUCAO

Pedido A–N integral adotado pelo usuário. Base 0154 validada; V099 e macrobloco anterior preservados. Construção **39/45** e aceites históricos **67/115**, sem reclassificação. Sem perguntas/subagentes/entrega parcial. V2-041 e G01–G08 preservados.

Checkpoint: docs/continuidade/checkpoints/0155-cadeia-integral-base-e-captura-explicita.md; SHA256 302483ecc12fc045fc54e8d85ceac7d311407446c812e9e6fbcfadb039951dbe.
Rodada: target/macrobloco-cadeia-integral-20260914-01; consultar WORKLOG.md, baseline-verification.json e recibos de tentativa. 3.349 snapshots/409 pins/115 membros ZIP conferidos; RED funcional registrado. Captura explícita em implementação, integração/pacote/provas finais pendentes. Prefácios abaixo são fotografias históricas.

# Contratos de entidades e correções básicas — revisão local

CONTRATOS_ENTIDADES_CORRECOES_LOCAIS_CONCLUIDAS na implementação e provas, com entrega válida
mediante o selo/readback desta revisão. Documentação das 11 entradas e 2.437 IDs; FRE-02/FRE-03
corrigidos; nove classes de página substituídas removidas. Usuários permanece snapshot deliberado.
Construção **39/45**, aceites **67/115**, zero aceite real novo. API sob hold V2-041.

Checkpoint: docs/continuidade/checkpoints/0154-contratos-entidades-e-correcoes-basicas.md; SHA256 78dd87ed08657aa560431713310dc45cf7b6a966f0ccb509d37066ea001e5429.
Catálogo: docs/catalogos/alinhamento-entidades/README.md; relatório: docs/catalogos/alinhamento-entidades/REVISAO.md.
Rodada e selo vigente desta correção: target/correcao-basica-e-contratos-entidades-20260914-01/final-seal.json e seal-readback.json.
O selo anterior A–N descreve a revisão anterior; seus pins e critérios permanecem preservados.
Se selo e readback desta correção conferirem, este pedido está entregue. Prefácios abaixo são históricos.

# Integração funcional local — revisão final

**INTEGRACAO_FUNCIONAL_LOCAL_CONCLUIDA**, com validade de entrega condicionada ao selo final/readback. Construção **39/45**; aceites históricos **67/115**; zero aceite real novo. Código, consumidores por arquivos e qualificação do JAR concluídos. Checks finais/diff/selo desta mesma execução ligados pelo selo fora do manifesto.

Checkpoint: docs/continuidade/checkpoints/0153-integracao-funcional-local-concluida.md; SHA256 4944f268548890a9c5a3334d9391c9b7e473ce6616dbfd110b9b9a917238140b. Catálogo: docs/catalogos/macrobloco-integracao-funcional. Rodada: target/macrobloco-integracao-funcional-20260914-01. Seleção atual: ler primeiro final-seal.json e seal-readback.json da rodada. Com PASS e pins íntegros, o trabalho local A–N está concluído e não deve ser repetido; restam somente as parcelas externas G01–G08 identificadas. Se o selo/readback ainda estiver ausente, terminar checks/diff/revisão/selo nesta mesma execução e fazer a entrega única, sem pedir novo prompt. Prefácios e seletores abaixo preservam fotografias históricas.

# Integração funcional — progresso0152

FUNCTIONAL_INTEGRATION_CANDIDATE. Revisão separada e cadeia de sucessão passaram; verify02 ainda ativo e pacote/selo finais pendentes. Construção39/45;aceites67/115. Checkpoint:docs/continuidade/checkpoints/0152-revisao-e-composicao-da-sucessao.md; SHA256 27173e5bb8f032deb5040810467896a1302a5d11192750d51f5a43b6f3d117f3. Pedido A–N integral continua sem perguntas/subagentes/continue/entrega parcial. Prefácios abaixo são históricos.

FUNCTIONAL_INTEGRATION_CANDIDATE — sucessão em validação, verify/pacote/revisão/selo finais pendentes. Checkpoint vigente0151 e pedido A–N integral preservados.

# Integração funcional — progresso 0151

EM_EXECUCAO A–N. Verify01 encontrou duas falhas de arquitetura antes das IT; corrigidas sem alterar gates,115 testes dirigidos passaram. Verify02 e pacote da revisão atual são os próximos passos. Construção39/45;aceites67/115 preservados.
Checkpoint: docs/continuidade/checkpoints/0151-fronteiras-arquiteturais-corrigidas.md; SHA256 ef3b62b9bad7ad7760423d9b788909d1398d8be9b4b067717a373e878990829c. Rodada: target/macrobloco-integracao-funcional-20260914-01. Pedido integral sem perguntas/subagentes/continue/entrega parcial; reconciliar tentativas. Prefácios abaixo históricos.

# Integração funcional — progresso 0150

EM_EXECUCAO A–N. Consumidores por arquivo, duas campanhas, cancelamento, retomada e recusas antes do efeito passaram no pacote. Regressão integral, revisão e sucessão finais em andamento; construção 39/45 e aceites 67/115 preservados.
Checkpoint: docs/continuidade/checkpoints/0150-consumidores-do-pacote-e-regressao-integral.md; SHA256 169c69c923b8ea8b59c5c40465eb5d37b2de5512317a97da1b674eb59dc23733. Rodada: target/macrobloco-integracao-funcional-20260914-01. Pedido integral continua sem perguntas/subagentes/continue/entrega parcial; reconciliar tentativas antes de retry. Prefácios abaixo são históricos.

# Integração funcional — progresso 0149

EM_EXECUCAO A–N. Identidades alternativas, cinco grãos adversariais, caps cumulativos e falha de lease qualificados. Pacote por arquivos em execução e rastreabilidade em revisão; fechamento integral ainda pendente. Construção39/45;aceites67/115 preservados.
Checkpoint: docs/continuidade/checkpoints/0149-oraculos-adversariais-e-pacote-por-arquivos.md; SHA256 ae9e28f251676903ce0b2323446c9f51a80f3a579e677e3d4777dde2c2ec660c. Rodada: target/macrobloco-integracao-funcional-20260914-01. Pedido integral continua sem perguntas/subagentes/continue/entrega parcial. Reconciliar tentativas antes de retry. Prefácios abaixo são históricos.

# Integração funcional — progresso 0148

EM_EXECUCAO A–N. Perfis locais e robustez por arquivos qualificados; captura alternativa, Raster/500 e escalas completas provados. Correção de identidade explícita do oráculo em qualificação; pacote e fechamento integral ainda pendentes. Construção39/45;aceites67/115 preservados.
Checkpoint: docs/continuidade/checkpoints/0148-perfis-oraculos-e-robustez-por-arquivos.md; SHA256 06c436def38b0267446b789d4fafc34537df9129f08263b37d0b94739b594a18. Rodada: target/macrobloco-integracao-funcional-20260914-01. Pedido integral continua sem perguntas/subagentes/continue/entrega parcial. Reconciliar tentativas antes de retry. Prefácios abaixo são históricos.

# Integração funcional — progresso0147

EM_EXECUCAO A–N. Coletas por artefatos/datas/snapshots alternativos epreview33:2IT aprovados. Raster e relações por arquivos integrados àcomposição;falha decancelamento reproduzida/corrigida,contraprovas eoráculoalternativo emqualificação. Semfechamento final. Construção39/45;aceites67/115 inalterados.
Checkpoint:docs/continuidade/checkpoints/0147-sweep-explicito-e-integracao-raster.md;SHA256 5a7a4fc4c5366064d72874f29d7cef4c260854022fb83160c5d9f87a1c4bdb0e. Rodada:target/macrobloco-integracao-funcional-20260914-01;reconciliarprocessos/resultados antes deretry. Pedido integral sem perguntas/subagentes/continue/entrega parcial;prefácios abaixo históricos.

# Integração funcional — progresso0146

EM_EXECUCAO A–N. Captura por artefatos:6unitários/8IT aprovados. Composição com oráculos independentes:2IT aprovados,19contratos/5grãos/35escopos e inputs alternativos. Sweep por data/snapshot explícitos e preview33 em qualificação;Raster,H–N,pacote e fechamento ainda pendentes. Construção39/45;aceites67/115 preservados.
Checkpoint:docs/continuidade/checkpoints/0146-captura-e-oraculos-independentes-em-integracao.md;SHA256 350e5c04200b8905debb2eaed78d2046f3f4d901fdc46e21e2d79bbb8a06160f. Rodada:target/macrobloco-integracao-funcional-20260914-01;reconciliar tentativas antes de repetir. Pedido integral continua sem perguntas/subagentes/continue/entrega parcial. Prefácios abaixo históricos.

# Integração funcional — progresso0145

EM_EXECUCAO A–N. Baseline0144 íntegra:3190arquivos/9672pins;RED da captura concreta confirmado. Entrada local/contrato/transporte/caracterização implementados e ainda em qualificação;JDBC,pacote,oráculos,sweep,Raster e H–N pendentes. Construção39/45 eaceites67/115 preservados.
Checkpoint:docs/continuidade/checkpoints/0145-integracao-funcional-baseline-e-captura.md;SHA256 55f4a882f4a30816e18efdf649bbda9e508d0755d90ad99abe48dce92167aa9f. Rodada:target/macrobloco-integracao-funcional-20260914-01/; consultar WORKLOG/resultados antes de repetir efeitos. Pedido integral adotado continua sem perguntas/continue/subagentes/entrega parcial. Prefácios abaixo são históricos.

# Fechamento da construção — entrega 0144

**AUDITORIA_E_CORRECOES_LOCAIS_CONCLUIDAS**, no escopo A–N local; validade exige o selo final íntegro. Duas correções de retomada, 423 IT e pacote reproduzível. Matrizes completas em partes abaixo do teto do scanner. Construção **39/45 → 39/45**; aceites históricos **67/115**. G01–G08 e funções condicionadas continuam pendentes, conforme relatório.
Checkpoint: docs/continuidade/checkpoints/0144-fechamento-de-bytes-e-verificacao-final.md; SHA256 ac52e2dcec5e5115ad1b228b33123eb7ce396ca11fd7cf7e2e1ef03d5fd58bec.
Relatório: docs/catalogos/macrobloco-fechamento-construcao/RELATORIO.md. Rodada: target/macrobloco-fechamento-construcao-20260913-01/.
Prefácios seguintes são históricos; o selo confere a revisão final e suas tentativas efetivas.

# Fechamento da construção — entrega0143

**AUDITORIA_E_CORRECOES_LOCAIS_CONCLUIDAS**, restrito ao escopo local A–N. Duas falhas de retomada corrigidas; verify com 1.976 unitários/4 skips históricos e 423 IT, incluindo as 417 anteriores. Pacote reproduzível e provas extraídas aprovados. Construção **39/45 →39/45**; aceites históricos **67/115** preservados. G01–G08 e faltas funcionais condicionadas permanecem abertos.
Checkpoint0143: docs/continuidade/checkpoints/0143-entrega-auditoria-e-correcao-de-construcao.md; SHA256 7b0e618640e066aaefd257cd748ee90bc69739e20be1dd64dc73ea99d383f257.
Relatório: docs/catalogos/macrobloco-fechamento-construcao/RELATORIO.md. Rodada: target/macrobloco-fechamento-construcao-20260913-01/.
O veredito exige final-seal.json existente e íntegro, com checks finais da mesma revisão. Se faltar, continuar fechamento sem perguntas/continue/subagentes ou resposta parcial. Os prefácios abaixo são históricos.

# Fechamento da construção — progresso0142

CLOSURE_LOCAL_CANDIDATE / EM_EXECUCAO A–N. Correção de retomada passou16IT/0falha/0erro/0skip,6novas+10anteriores;rollback confirmado. Verify integral/pacote/revisão/sucessão finais pendentes. Construção39/45 eaceites67/115 preservados. Checkpoint0142 SHA256 85961841ba8e91c95164d8016b4e41e708a81a07d3925ca680b11251c905cc00.
Rodada target/macrobloco-fechamento-construcao-20260913-01/; consultar WORKLOG.md e resultados antes de repetir efeito. Pedido integral continua, sem perguntas/continue/subagentes/entrega parcial.

# Qualificação/pacote — entrega0141

**CONSTRUÇÃO_LOCAL_CONCLUÍDA: A–N no laboratório sintético.** Campanha tipada,
oráculos19/673/971,35gates,agenda,journal/retomada e pacote independente passaram
verify03:1.976unitários/4skips históricos,417IT/0skip;17smokes,4escalas4/16/32/16,
25/21/8contraprovas e dois builds byte-idênticos. Construção37→39/45 (86,7%),
somenteV2-038/039;aceites67/115 preservados,sem promoção operacional/real.
Checkpoint0141 SHA256 46a01a4e000a48b895efae7b4cedfcfc93f916582dbd2bce635f9fa705851d06.
Relatório: docs/catalogos/macrobloco-qualificacao-pacote/RELATORIO.md.
O selo final emtarget/macrobloco-qualificacao-pacote-20260913-01/final-seal.json
confirma os checks da revisão; sua ausência indica fechamento pendente.

Os prefácios de progresso abaixo são fotografias históricas desta execução.

# Qualificação/pacote — progresso0140

EM_EXECUCAO A–N. Os17smokes/102comandos e8contraprovas de controle passaram.
Artefato/reprodutibilidade e1.996inputs/1.015classes-recursos conferiram. Escalas
4/16/32/16 ativas pelo mesmo pacote;finalização documental/sucessão/checks/selos
pendentes. Construção37/45,aceites67/115. Checkpoint0140 SHA256 a6c1284b1a87a9301b9834fdaaa99999dc9c7c7a5904c5880baff9896b72369b.

# Qualificação/pacote — progresso0139

EM_EXECUCAO A–N. Verify03 passou1.976unitários/4skips históricos e417IT/0skip;
378IT anteriores exatas preservadas. Cobertura e rollback aprovados. Dois builds
geraram pacotes idênticos;21recusas extraídas passaram. Smokes finais ativos,
escalas e selos pendentes. Construção37/45, aceites67/115.
Checkpoint0139 SHA256 13da42a15217b9f9f27e6b44d48e03de2fe0d5f77689687d411484a297571150.

# Qualificação/pacote — progresso0138

EM_EXECUCAO A–N. Composição passou30unitários+10IT, zero skips/rollback;
as nove IT antigas corrigidas passaram. Foundation02 passou cinco checks.
Verify03 integral ativo; entrega final e incremento dependem das provas restantes.
Construção37/45 e aceites67/115. Checkpoint0138 SHA256 b279c4ea807557e3faa1b6cbca0f55e9ac5dec0d0e40343d682ca4d913df70a8.

# Qualificação/pacote — progresso0137

EM_EXECUCAO A–N. Verify02 falhou seis IT antigas e dois gates de cobertura;
correções e nova suíte de composição em qualificação física. Falhas preservadas.
Sucessão candidata03 passou; deltas posteriores ainda precisam novo selo.
Construção37/45 e aceites67/115 mantidos. Checkpoint0137 SHA256 35253fdecea497147a5933da1b7ece8cac8968c049733e3032f4eb03f5dca242.

# Qualificação/pacote — progresso0136

EM_EXECUCAO A–N. Candidato07 passou VARIANTS/TEMPORAL e seis comandos.
Verify01 falhou em dois contratos de arquitetura; correções passaram47testes
e4IT físicas/0skip/rollback. Verify02 ativo; entrega final pendente.
Construção37/45 e aceites67/115 mantidos. Checkpoint0136 SHA256 56c065dc1c810366693c956a5243e9ce95c4490c4cafdcdca307d1d697cba0ab.

# Qualificação/pacote — progresso0135

EM_EXECUCAO A–N. Agenda física da matriz5workloads e fronteiras passou1IT;
três planos reais passaram em outraIT;0skip/rollback. ADMISSION candidato05
passou3adiamentos/6comandos sem filho. Pacote atual/barreiras/escalas/verify
reprodutibilidade e sucessão final pendentes. Construção37/45, aceites67/115.
Checkpoint0135 SHA256 f37ba9fc763650b74c3f9977d152e0affbd94b212e04e7071425019f65df6746.

# Qualificação/pacote — progresso0134

EM_EXECUCAO A–N. Candidato05 passou faults/concorrência/WAVES; dependente adiado
sem filho. Variante corrigida passou19saídas/35escopos em1IT física/0skip/rollback.
Agenda física completa, pacote atual/barreiras/escalas/verify/reprodutibilidade
e sucessão final pendentes. Construção37/45 e aceites67/115 mantidos.
Checkpoint0134 SHA256 79efa37e5d62a441d9ac61c0272cfbee0e015cffb39de11b6961f048e25c89c4.

# Qualificação/pacote — progresso0133

EM_EXECUCAO A–N. Candidato04 passou positivo/replay/recomposição/ausência e
oito mutações de controle. Revisão posterior passou12IT físicas/0skip/rollback,
com métricas das11entradas, limites compartilhados e oráculo divergente FAILED.
Variantes/agenda/pacote atual/escala/verify/reprodutibilidade/sucessão pendentes.
Construção37/45, aceites67/115. Checkpoint0133 SHA256 db41236471ee1dc32f2b71f0852a3d59ae18f489314da0aa33b4b2ea3ab642e8.

# Qualificação/pacote — progresso0132

EM_EXECUCAO A–N. Replay/correção nos quatro modos, quatro degradações e
971metadados físicos passaram em JDBC. Concorrência em correção; novo pacote,
retomada adversarial, variantes/agenda/escala/reprodutibilidade/verify pendentes.
Construção37/45 e aceites67/115 mantidos. Checkpoint0132 SHA256 3347e126d4ebb82d35db27d57ec0ae3c5ff9adcc8fea2593905d2015abb98fe0.

# Qualificação/pacote — progresso0131

EM_EXECUCAO A–N. Primeiro pacote candidato executou os seis comandos,19saídas
e35escopos. Quatro barreiras e teto/cancelamento JDBC passaram com rollback.
Replay/variantes/faults/concorrência/guardas finais/reprodutibilidade/verify
continuam obrigatórios. Construção37/45, aceites67/115.
Checkpoint0131 SHA256 4b1e22ec58abc02603a3b83fccbc16f437bab937fb053e4e0dddc0c484e36bcf.

# Qualificação/pacote — progresso0130

EM_EXECUCAO A–N. Verificador19saídas/35escopos passou com16raízes; janelas SQL
nos quatro modos, lookback, correção e blackout passaram em2IT com rollback.
Entrypoint/supervisor em compilação; campanha/pacote/variantes/verify pendentes.
Checkpoint0130 SHA256 fcdf089c0a4c9bebbe2a6e2ae5f818d117a7625eca09f6bdfaf9cce9839e60df.
Construção37/45; aceites67/115. Prosseguir até entrega integral.

# Qualificação/pacote — progresso0129

EM_EXECUCAO A–N. As19saídas têm provas dirigidas positivas com oráculos
independentes; SQL04 confirmada/reaparecida, SQL10 e integração ao harness
V2-012 passaram. Correção de20parâmetros UTC e leitura do lease comprovadas.
Variantes, executor/pacote, escala/verify e sucessão final seguem pendentes.
Construção37/45 e aceites67/115 mantidos. [Checkpoint0129](docs/continuidade/checkpoints/0129-oraculos-sql04-sql10-harness-e-utc.md), SHA256 c5ffadbcb1c3fba629e9e08b6203b0e68ef8d6471d7f9ac63ab5b73f9c0100e0.

# Qualificação/pacote — progresso0128

EM_EXECUCAO A–N. Oráculos19/673 mapeados;17saídas passaram na prova física de
valores/metadata e10na prova de linhagem. SQL04/SQL10/tempos/hash ainda exigem
qualificação integrada. Executor/pacote/smokes/verify/sucessão seguem obrigatórios.
Construção37/45 e aceites67/115 mantidos. [Checkpoint0128](docs/continuidade/checkpoints/0128-oraculos-tipados-e-linhagem-fisica.md), SHA256 ec6c1e3f969b2c8e6d3b1abe142828cb3420c64cb6f19f400ca00f48f78c84f9.

# Qualificação/pacote — progresso0127

EM_EXECUCAO A–N;9unitários dirigidos,3IT físicas e25guards de ZIP passaram.
Rollback confirmado; sem DDL. Oráculos673/executor/pacote/smoke/verify/sucessão
continuam obrigatórios. Construção37/45 e aceites67/115 inalterados.
[Checkpoint0127](docs/continuidade/checkpoints/0127-qualificacao-journal-extracao-e-cancelamento-jdbc.md), SHA256 f8f1a7747fe1d43f7ed762ebdcd867280f2db37864a9cd49995d5368122fdb03.

# Estado corrente — qualificação e pacote local em execução

EM_EXECUCAO A–N por adoção integral do pedido de13/09/2026. Contratos tipados de
campanha/configuração/gates e integração inicial ao planner civil:5testes dirigidos
passaram,0falhas/erros/skips, Java17 offline. Pacote/runtime/oráculos/provas físicas
e entrega selada continuam em execução. Construção37/45 e aceites67/115 preservados.
[Checkpoint0126](docs/continuidade/checkpoints/0126-qualificacao-pacote-base-e-contratos.md), SHA256 e082431a041b1abcdbac2f9aeded99cb48eea3b50f319c321b2ef1aac3370e61.
Evidências: target/macrobloco-qualificacao-pacote-20260913-01.

A entrega analítica abaixo é a fotografia histórica anterior, não o estado deste pedido.

# Estado corrente — macrobloco analítico entregue

**CONSTRUÇÃO_LOCAL_CONCLUÍDA: A–N,13/09/2026.** Raster, MAT01/02/05, seis dimensões,
19contratos e ausência sintética de Coletas estão integrados ao cenário/JAR.
Construção32→37/45 (82,2%); aceites históricos67/115 inalterados.
Java17:1946unitários com4skips históricos;378IT únicas/0skips;40processos JAR.
Somente localhost/ETL_SISTEMA_V2_SHADOW, dados sintéticos e rollback.

[docs/continuidade/checkpoints/0125-entrega-final-macrobloco-analitico-local.md](docs/continuidade/checkpoints/0125-entrega-final-macrobloco-analitico-local.md), SHA256 5e961eed19166392997c5094ef7d3d813781de1d6f1f252d6c64e3177748b2ab.
[Relatório](docs/catalogos/macrobloco-analitico/RELATORIO.md) e
[quadro45](docs/catalogos/macrobloco-analitico/quadro-construcao.json).
Os estados locais estão registrados também nas capacidades correspondentes abaixo;
os critérios originais dos pais e gates reais/operacionais continuam preservados.
## Orientação permanente do usuário — uma entrada, execução até a entrega final

Registrada por pedido explícito do usuário em 12/09/2026. Esta orientação rege
as tarefas deste projeto, inclusive retomadas e sessões futuras, dentro do
escopo adotado e das autorizações vigentes.

- **Uma entrada do usuário e uma entrega final do agente.** Conduzir a tarefa
  integralmente, com autonomia para investigar, decidir e executar o necessário
  até concluir o resultado autorizado.
- **Sem perguntas ou confirmações intermediárias e sem pedidos de “continue”.**
  Resolver as decisões técnicas com o contexto, os arquivos e as evidências
  disponíveis; não pedir novamente autorização já concedida para a ação.
- **Implementar, integrar, testar, revisar e corrigir até o final.** Usar os testes
  tanto para validar o trabalho quanto para descobrir pendências necessárias à
  conclusão. Corrigir as pendências locais descobertas e repetir as verificações
  afetadas antes do fechamento; não deixá-las para outro chat ou prompt.
- **Revisar o que já foi feito.** Conferir o código, o estado real e as provas
  anteriores, corrigindo inconsistências. Planejamento, documentação e declaração
  de sucesso não substituem implementação nem testes executados.
- **Prosseguir após compactações e entre frentes.** Retomar pelos arquivos de
  continuidade e pelas evidências, preservando o objetivo original. Checkpoints
  registram progresso; não encerram a tarefa nem exigem nova entrada do usuário.
- **Dependências externas bloqueiam somente os resultados que dependem delas.**
  Concluir todas as frentes independentes autorizadas. Quando restar impedimento
  externo real, registrar precisamente a pendência, a evidência, a dependência e
  o que falta para resolvê-la, sem apresentar esse resultado como concluído.
- **Entregar o resultado completo e verificável.** Incluir código e integração
  aplicáveis, testes e provas, diff revisado, relatório, pendências reais, avanço
  comprovado da construção e continuidade atualizada conforme o escopo adotado.
  Não encerrar com falha local conhecida que ainda possa ser corrigida no escopo.

Esta orientação conserva os limites de acesso, segurança e autorização já
vigentes; não cria autorização para ações externas, produtivas ou destrutivas
fora do escopo adotado. Nenhum aceite ou percentual muda por este registro.

Sucessão documental: checkpoint 0091 e
[manifesto da orientação](docs/continuidade/orientacao-entrada-unica/manifesto.json).
A fotografia selada da expansão e os registros abaixo permanecem históricos.

## Execução analítica — checkpoint0108

EM_EXECUCAO A–N; V088 instalada. Perfil31/suplemento12 deColetas compilados;2unitários passaram.
[Checkpoint0108](docs/continuidade/checkpoints/0108-coletas-contrato-e-suplemento-tipado.md), SHA256 313091e92f69e1506b3ee093d03cca7eb96833140f63d5e887cf3d7943e81903.
Reconciliar physical-collection-supplement01/session24458; prosseguir SQL03/04/K/J–N.

## Execução analítica — checkpoint0107

EM_EXECUCAO A–N; V087 instalada;5IT Cotações passaram,17SQL físicos. Sem processo ativo.
[Checkpoint0107](docs/continuidade/checkpoints/0107-cotacoes-consumo-fisico-verificado.md), SHA256 cd9bef5ba61f420542267a8208665ff33df0092a96660d36d254bb9f1109e06d.
Prosseguir SQL03/04/K/JDBC/JAR e demais provas/entregáveis; construção32/45.

## Execução analítica — checkpoint0106

EM_EXECUCAO A–N; construção32/45, aceites67/115. V086 instalada; runtime
Cotações compilou; SQL05 em prova física. Reconciliar physical-quotes01/session66022.
[Checkpoint0106](docs/continuidade/checkpoints/0106-cotacoes-tarifa-runtime-e-projecao.md), SHA256 7e4006739314855689d4db51dcf82cbadb81876df8a6b7f90f5c75cdfc1a6501.
Continuar todas as frentes até entrega integral.

## Execução analítica — checkpoint0105

EM_EXECUCAO A–N; construção32/45 e aceites67/115 intactos. V001–V082 instaladas.
[Checkpoint0105](docs/continuidade/checkpoints/0105-analitico-modelo-tipado-cotacoes.md),
SHA256 40d4995dad0e2727aa1bafcda87a4946fa0d92fcc03e5fad8142eb59c73078b1.
Modelo Cotações36campos e transporte compilados;2unitários passaram. Sem processo
ativo. Persistência/runtime/SQL05 em implementação;16SQL físicos anteriores.
Prosseguir integralmente as pendências A–N, incluindoSQL03/04/Sweep/JDBC/JAR.

## Execução analítica — checkpoint0104

EM_EXECUCAO A–N; construção32/45 e aceites67/115 intactos. V001–V082 instaladas.
[Checkpoint0104](docs/continuidade/checkpoints/0104-analitico-monitoramento-interno.md),
SHA256 3a702f2650f9a3165ba095923dc9bcb08f4f74df6a95a024a46e1d6ff484d99b.
SQL10 passou2IT físicos;16contratos com consumo positivo. Sem processo ativo.
Prosseguir SQL03/04/05, JDBC/JAR/Sweep e provas/entregáveis A–N restantes.

## Execução analítica — checkpoint0103

EM_EXECUCAO A–N; construção32/45 e aceites67/115 intactos. V001–V080 instaladas.
[Checkpoint0103](docs/continuidade/checkpoints/0103-analitico-consumo-inventario-financeiro.md),
SHA256 fa11cd7b0ee16d9f887bd01807de3ce8c9f27d2fa5ac3427de59069204e922d4.
SQL01/06/11/12 passaram6IT físicos;15contratos com consumo positivo. Export
metadata-fifteen-contracts-01:798colunas incluindo técnicas. Sem processo ativo.
Prosseguir SQL03/04/05/10, JDBC/JAR/Sweep e provas/entregáveis A–N restantes.

## Execução analítica — checkpoint0102

EM_EXECUCAO A–N; construção32/45 e aceites67/115 intactos. V001–V078 instaladas.
[Checkpoint0102](docs/continuidade/checkpoints/0102-analitico-gates-frota-manifestos.md),
SHA256 34a4534a42d42d4df75a115bcc66b6fca556756a4c253ecb34abd46029a97090.
Campanha physical-manifest-fleet-gates-01 passou9IT sobreV078; nenhum processo ativo.
Prosseguir oito SQL restantes, integraçãoJAR/Sweep e provasL–N obrigatórias.

## Execução analítica — checkpoint0101

EM_EXECUCAO A–N; construção32/45 e aceites67/115 intactos. V001–V078 instaladas.
[Checkpoint0101](docs/continuidade/checkpoints/0101-analitico-mat05-e-consumo-manifestos.md),
SHA256 07e8dc2e6d5e08cc61a408080e66a15868f2b4fe7dc5b2fb15e2d782e10f364e.
MAT05/SQL08/09 e correções temporais/alias passaram13IT sobreV077. Campanha
physical-manifest-fleet-gates-01/session69503 ativa sobreV078: reconciliar.
Onze contratos SQL implementados; oito restantes e integração/provasJ–N obrigatórios.

## Execução analítica — checkpoint0100

EM_EXECUCAO A–N; construção32/45 e aceites67/115 intactos. V001–V071 instaladas.
[Checkpoint0100](docs/continuidade/checkpoints/0100-analitico-referencias-frota.md),
SHA256 375c6c8cdf65e91bd4d6595947f62dc69836168e259c86a94ec63d0ac3e4690b.
OWNED_FLEET passou3IT físicos, incluindo matriz8, exceções, vigência e selos.
MAT05/SQL restantes e integração/provasJ–N continuam obrigatórios. Nenhum processo ativo.

## Execução analítica — checkpoint0099

EM_EXECUCAO A–N; construção32/45 e aceites67/115 intactos. V001–V070
instaladas. [Checkpoint0099](docs/continuidade/checkpoints/0099-analitico-relacoes-fretes-manifestos.md),
SHA256 8dc66305e4df644b9a360f1bf8e7a48591b368af4455065bb92ece9a10215fd8.
Relações explícitas DIRECT/CROSSWALK e selo sintético passaram3IT físicos.
Perfis de referências MAN/frota preparados; importador OWNED_FLEET e MAT05
ainda pendentes, assim como dez SQL e integração/provasJ–N. Nenhum processo ativo.

## Execução analítica — checkpoint0098

EM_EXECUCAO A–N; construção32/45 e aceites67/115 intactos. V001–V069
instaladas. [Checkpoint0098](docs/continuidade/checkpoints/0098-analitico-coletores-e-relogio-manifestos.md),
SHA256 6161af7224af8453aefe6a76b0e01ac15044c7aa173b3d7084071598ff2090e5.
Physical-manifest-clock-01 passou9IT: Coletores2, captura1, preparação6;
relógio MAN por segundo+nano, lineage, correção/stale e scalar MDF-e provados.
MAT05, dez SQL restantes, contraprovas e integração/provasJ–N seguem obrigatórios.
Nenhum processo ativo neste checkpoint. Próxima migration livre V070.

## Execução analítica — checkpoint0097

EM_EXECUCAO A–N; construção32/45 e aceites67/115 sem alteração. V001–V067
instaladas. [Checkpoint0097](docs/continuidade/checkpoints/0097-analitico-manifestos-tipados-e-coletores.md),
SHA256 87c4f773a76e8a6f2624c7adc9faf6411a49c446028605b17daf3987372b9013.
Manifestos92campos tipados/cohort e lifecycle, MAT02/JDBC em qualificação.
Physical-manifest-profile-02 passou4IT; preparação02 passou3IT e revelou
recusa de associação que revertia capturas anteriores, corrigida antes da fonte.
Campanha physical-collectors-02/session19009 ativa: reconciliar antes de repetir.
MAT05, dez SQL restantes e integração/provasJ–N seguem obrigatórios.

## Execução analítica — checkpoint0096

EM_EXECUCAO A–N; construção32/45 e aceites67/115 sem alteração. V001–V062
instaladas. [Checkpoint0096](docs/continuidade/checkpoints/0096-analitico-mat01-e-consultas-fretes-localizacao.md),
SHA256 c477c37ebf1500527e2b95c809faab4b99d50ebb48dcb778bf9772d2a49397ee.
MAT01 PE/CB e SQL02/07 possuem provas físicas dirigidas:4IT operacional,
3IT fallback e3IT consultas passaram, além de4IT atributos e17testes de arquitetura.
Ainda faltam contraprovas/integração completa, MAT02/05,10contratos restantes,
JAR composto, SweepK e fechamentoL–N. Sem marco de conclusão antecipado.

## Execução analítica — checkpoint0095

EM_EXECUCAO A–N. Construção32/45. V001–V057 instaladas, sem novo aceite.
[docs/continuidade/checkpoints/0095-analitico-usuarios-e-atributos-fretes.md](docs/continuidade/checkpoints/0095-analitico-usuarios-e-atributos-fretes.md), SHA256 7c3aecf22b98b4fdab8d1a3158dcf987e3768072df42537818cfcc180f903b3d.
Physical-users-05:2IT passou com21usuários, replay, update e recusa Relay;
SQL14–19 têm consumo físico (5IT dimensões +2IT usuários). Suplemento tipado
Fretes90atributos/V057 em prova: physical-freight-attributes-01 deve ser
reconciliado. MAT01/02/05, demais SQL e integração/provas finais ainda pendentes.
Prosseguir no escopo adotado, sem perguntas nem encerramento intermediário.

## Execução analítica — checkpoint0094

EM_EXECUCAO A–N; construção32/45, sem novo aceite. V001–V056 instaladas.
Checkpoint: [docs/continuidade/checkpoints/0094-analitico-referencias-e-dimensoes.md](docs/continuidade/checkpoints/0094-analitico-referencias-e-dimensoes.md), SHA256 f59e630ab3617dff6f2952a0ce89a6f82298dc3889e18cc522c3aa4e9eebf416.
Physical-references-01:3IT e physical-dimensions-02:5IT passaram sem falhas/skips;
physical-raster-transit-01:10IT passou. Cinco dimensões consumidas por capturas
Fretes/CAP; SQL19 aguarda integração do pipeline Usuários. MAT01/02/05 e demais
contratos/integração/provas finais seguem em execução. Evidências em
target/macrobloco-analitico-20260912-01/. Prosseguir, não encerrar aqui.

## Macrobloco analítico — captura Raster verificada (12/09/2026)

EM_EXECUCAO. V052–V054 instaladas/imutáveis; 18 unitários e 4 IT Raster
passaram sem falhas/erros/skips. 179 tabelas anteriores preservadas; DML
revertido. SQL-13 escrito, provas de consumo pendentes. Demais frentes A–N
em implementação; nenhuma unidade nova contada. Checkpoint0093: a6e17f02749db8c04d93824d56dc02156b0c11f8ba49e0b7420e8dd91f45126b.

## Macrobloco analítico — execução A–N adotada (12/09/2026)

EM_EXECUCAO. Request congelado em target/macrobloco-analitico-20260912-01/request.md.
Inventário inicial: 2704 arquivos preservados byte a byte. Sucessão 0091 e
expansão conferidas; validador/11 contraprovas passaram antes das alterações.
ADR0050 e inventário inicial das colunas registram os contratos de construção.
Raster, MAT01/02/05, seis dimensões, 19 saídas e ausência sintética pertencem
a este pedido. Nenhum novo mecanismo verificado ainda; construção 32/45.
Aceites reais e 67/115 permanecem separados. Checkpoint0092: 8c9ae111cc2906ea5c8ea5a0f052c2cc624f32d993da469828b21b9027ede849.

## Expansão — correção do índice final (12/09/2026)

EXPANSION_LOCAL_COMPLETE. O link do prefácio da trilha foi corrigido para
resolver a partir de docs/runbooks. closure-final-01-trail falhou nesse link;
a tentativa e os artefatos foram preservados em final-attempt-01/. Código,
SQL e JAR não mudaram; contagens e provas do checkpoint0089 continuam válidas.
Checkpoint0090 registra a correção; final-seal.json confere a revisão final
após validar novamente a sucessão, a trilha e o delta documental.

## Macrobloco expansão — A–N encerradas localmente (12/09/2026)

EXPANSION_LOCAL_COMPLETE. ACEITO_NO_ESCOPO da construção sintética local adotada.
Quatro verticais (CAP8636/FAT4924/INV10633/SIN6392),151 campos, relações/fila/
hidratação, referências seladas, MAT04/MAT03, seis consultas e entrada JAR
integradas e verificadas. V038–V051 instaladas e congeladas; próxima V052.

verify-03:1775 testes unitários,0 falhas/erros,4 skips históricos;233 IT físicas
(95 regressões+138 novas),0 falhas/erros/skips. Cobertura/arquitetura/estilo/
formatter passaram.870 fontes e746 classes JAR conferidas;21 processos JAR
passaram. Escalas16/64/256/64,148 planos reais sem spill/PlanAffectingConvert;
8 planos com ColumnsWithNoStatistics documentados. Sem platô de heap/SLO.
SQL062:143 tabelas preexistentes preservadas e36 novas sem resíduos.

Doze validadores estáticos, scanner integral/11 contraprovas e scanner do
delta passaram. Sucessão própria11 contraprovas, relacional11, temporal10+
12 históricas; continuidade/6 contraprovas, trilha e6 checks de runtime
passaram. Diff contra inventário2549,19 deltas com snapshots exatos; revisão
candidata conferiu165 textos e146 arquivos no scanner do delta. O selo final
da rodada confere manifesto/diff/inventário após esta atualização documental.

Construção funcional no universo congelado45:26/45→32/45 (57,8%→71,1%) com
mecanismo executável no subescopo declarado. Três unidades documentais foram
corrigidas simetricamente; fotografia29 original preservada. Evidência também
registrada nas capacidades V2-029–032 e MAT03/04.67/115 conserva somente
critérios de aceite históricos;191 rotas abertas,zero AGORA,sem B64 novo.

Não resta frente local A–N para outro chat. Identidade/cardinalidade reais,
política fiscal/grãos/unidades nominais, oráculos/janelas representativas,
atribuição de referência e Segurança/V2-041 permanecem nos gates próprios.
Sem aceitar os pais ou19 views por fixture; sem COMMIT/crash, banco novo,
gitleaks disponível/feed externo, API/.env/grants/produção/V1/scheduler/deploy/
commit/push. DML sempre rollback-only. Falhas intermediárias preservadas.

Relatório/matriz/contratos/comandos/manifesto/quadro:
[macrobloco-expansao](docs/catalogos/macrobloco-expansao/RELATORIO.md).
Diff/inventários/provas/selo: target/macrobloco-expansao-20260912-01/.
Checkpoint0089: docs/continuidade/checkpoints/0089-macrobloco-expansao-encerramento-local.md.

## Macrobloco expansão — candidata verificada em Java/SQL/JAR (12/09/2026)

EXPANSION_LOCAL_CANDIDATE, checkpoint0088. verify-03:1775unitários/4skips e
233IT/0skips,0falhas/erros; cobertura/estilo/arquitetura aprovados.870fontes e
746classes conferidas;21processos JAR passaram.148planos semspill/conversão
problemática,8avisosdeestatísticas;36tabelas novas semresíduos.
A–M executadas;Nmanifesto/diff/sucessão/revisão/continuidade emvalidação.
Nenhumaceite real/B64/checkboxnovo. Quadrofuncional26→32no universo45.

## Macrobloco expansão — regressão física executada, fechamento em correção (12/09/2026)

EM_EXECUCAO, checkpoint0087. verify-02:1616 unitários/4skips e233IT/0skips,
semfalhas/erros; build reprovado no gate de cobertura. 159 novas contraprovas
de campos/envelope passaram. SQL062 preservou143tabelas e36novas semresíduos.
Verify-03/JAR/scanner/sucessão final pendentes; quadro45 registra26→32
subescopos funcionais verificados, semaceite real novo.

## Quadro funcional vigente — regra de construção local

O quadro de construção usa as mesmas45 unidades congeladas no início. V2-016, V2-017 e V2-048 têm entregas documentais/governança; sua classificação inicial como mecanismo funcional verificado é corrigida tanto no antes quanto no depois, com fotografia original preservada. Um mecanismo executável local conta somente no subescopo declarado, sem fechar o pai ou seu aceite real. V2-013 tem kernel de preview verificado, mas integração de mutação continua ausente. [Quadro completo antes/depois](docs/catalogos/macrobloco-expansao/QUADRO-CONSTRUCAO.md).

## Macrobloco expansão — 151 campos verificados (12/09/2026)

EM_EXECUCAO, checkpoint0086. V051 corrige os três arrays de Sinistros;
physical-fields-02:4/0/0/0 com readback151 campos e agregados preservados.
verify-01 falhou em duas regras de arquitetura; directed-bounded-01 passou54
após adequação das APIs limitadas. Verify completo, JAR e entregaN pendentes.

## Macrobloco expansão — provas físicas e medição verificadas (12/09/2026)

EM_EXECUCAO,checkpoint0085. V050 corrige redução de valores inativos.
16 adversariais passaram;physical-scale-concurrency-01:8/0/0/0,quatro
procedures emduassessões e escalas16/64/256/64;148planosinspecionados
semavisosdespill/conversão. physical-edges-02:14/0/0/0,DST/arrays/prova
cumulativa/fiscal/pagamento. Agregados preservados. Verify-01 emexecução;
JARprocessos e entregaN/validadores/scanners ainda pendentes;semaceitereal.

## Macrobloco expansão — recomposição e entrada integradas (12/09/2026)

EM_EXECUCAO,checkpoint0084. V049 e executor ligam4 novas+Fretes/LOC,
bindings/fila/hidratação/referências/MAT04/MAT03;planos e recibos recuperados
por SQL na mesma transação. physical-recomposition-main-02:22/0/0/0,
quatro modalidades,9 fronteiras de falha,ordem inversa e6 consultas.
Agregados iguais por rollback. JARprocessos,escala/concorrência/verify e M/N
ainda pendentes;sem alegação de COMMIT/crash ou aceite real.

## Macrobloco expansão — duas cargas financeiras verificadas (12/09/2026)

EM_EXECUCAO,checkpoint0083. MAT03/V047 e comparação exata/V048 passaram
physical-revenue-02:12/0/0/0;MAT04 permanece verificada. Seis consultas JDBC
implementadas. physical-revenue-01 falhou por collation e foi preservada.
Agregados antes/depois iguais. K/L e M/N ainda em construção;sem aceite real.

## Macrobloco expansão — MAT04 verificada na camada física inicial (12/09/2026)

EM_EXECUCAO,checkpoint0082. V045 implementa MAT04 com grão de título,
carga SQL/current/history/recibo/linhagem e quinta consulta JDBC. physical-invoices-01:
10/0/0/0,expansão sem multiplicar valores,correção de data,replay,aging,nulos/zero/
decimal máximo e disposições bloqueadas;agregados preservados. V046 captura inputs
laterais sintéticos para MAT03,que ainda não tem carga. ADR0049 EXP17–18.
Restam MAT03,integração/recomposição/runner e provas finais M/N;sem aceite real.

## Macrobloco expansão — referências e quatro consultas verificadas (12/09/2026)

EM_EXECUCAO,checkpoint0081. V042–V044 instaladas/congeladas. Referências V2-035a
seladas e selecionadas explicitamente; quatro consultas JDBC tipadas/paginadas,
linhagem e conflito preservados. Rodada physical-captures-projections-01:33/0/0/0;
physical-capture-fences-02:16/0/0/0,recusas de fonte/tenant/contrato/páginas.
Agregados iguais por rollback. MAT04/MAT03,recomposição,runner e provas M/N
ainda não entregues; construção parcial verificada, sem aceite real novo.

## Macrobloco expansão — dependências e hidratação verificadas (12/09/2026)

EM_EXECUCAO, checkpoint0080. Dependências Fretes/Localização pelo pipeline real,
relações explícitas por TVP e fila/hidratação mínima integradas às quatro capturas.
V040/V041 instaladas/congeladas; physical-dependencies-02:6/0/0/0;
physical-relations-01:3/0/0/0,14 vínculos resolvidos,1 alvo hidratado,agregados
preservados por rollback. ADR0049 EXP11–13. H/I/J/K/L e provas finais M/N seguem
em construção; nenhum aceite real nem conclusão integral A–N foi declarado.

## Macrobloco expansão — quatro capturas verificadas, integração em curso (12/09/2026)

EM_EXECUCAO, checkpoint0079. Quatro pipelines/151 campos, DTO/binding lateral,
staging/aplicação SQL e auditoria reais implementados no laboratório. ADR0049;
V038/V039 instaladas fora das IT e congeladas. directed-02:15/0/0/0;physical-01:
12 testes Java/JDBC/SQL,zero falhas/erros/skips,agregados preservados por rollback.
Relações/referências/cargas/consultas/runner e provas M/N ainda em construção.
Nenhuma alegação de A–N completas; aceites reais e identidade/B56 preservados.

## Macrobloco expansão — construção local A–N adotada (12/09/2026)

EM_EXECUCAO. Pedido integral congelado em target/macrobloco-expansao-20260912-01/request.md.
Inventário2549 e snapshots verificados; checkpoint0078, SHA256 1d01d6092e0eaab75b9b8a63e5fc8e012a432cf053742c6cfec6bd302c3e9ab9.
Quatro verticais8636/4924/10633/6392, relações explícitas, referências consumidas,
MAT-04/MAT-03, seis consultas e cenário JAR constituem uma tarefa até a entrega.
Autorização local aditiva, Windows/shadow exato, DML rollback-only e DDL fora de IT.
Sem B64/aceite real novo;67/115 conserva somente critérios de aceite históricos.
A construção sintética não altera os holds históricos de identidade/B56.

## Macrobloco relacional — A–J encerradas localmente (11/09/2026)

RELATIONAL_LOCAL_COMPLETE. ACEITO_NO_ESCOPO da construção local adotada.
Captura real do pipeline com fixtures→staging tipado→bindings MC/CF→backlog→
hidratação→recomposição/replay→reconciliação e quatro comandos JAR integrados.
ADR0048; V029–V037 instaladas/congeladas em seis diretórios, fora da IT.
Checkpoint0077, SHA256 cdf9d89a2201d7ab7da0f472ea50efc43cddfc3e1c45ba92ca957044fa9f008c.

Verify-04:1582 testes,0 falhas/erros,4 skips históricos;95 IT reais sem skips,
incluindo39 regressões;797 fontes finais e645 classes JAR conferidas.10 casos
JAR aprovados. Escalas16/256/1024/256, máximo1 página/1 lote em voo;12 planos
SQL inspecionados sem spills/conversões problemáticas.4096 permanece falha
exploratória preservada; não há prova de platô de heap/SLO nem recovery COMMIT/crash.
SQL061 final:129 tabelas preexistentes preservadas,14 novas sem resíduos.

Migrations/baseline/progressive/Coletas temporal, seis checks de runtime e
continuidade com contraprovas aprovados. Scanner integral,11 contraprovas e
delta candidato67 arquivos sem findings; selo final próprio. gitleaks indisponível;
feed externo não consultado. Relatório/matriz/contratos/comandos/manifesto:
docs/catalogos/macrobloco-relacional/. Diff/inventários/recibos e selo final:
target/macrobloco-relacional-20260911-01/.13 deltas com snapshots byte a byte.

Não resta frente local A–J para outro chat. Sem B64/checkbox/aceite externo novo;
67/115,191 rotas,zero AGORA históricos preservados. Identidade/cardinalidade
reais,oráculo independente,correspondência,janela representativa,aceite nominal
e Segurança/V2-041 seguem nos subgates próprios; não se promovem R01/R02 ou
V2-046/V2-047/V2-012/V2-050/V2-038 agregados por fixture. Sem API/.env/grants/
reset/produção/V1/scheduler/deploy/commit/push. DML sintético sempre rollback-only.
## Macrobloco relacional — verificação integral e fechamento (11/09/2026)

RELATIONAL_LOCAL_CANDIDATE. Fluxo A–I verificado; J finaliza diff/continuidade.
Checkpoint0076, SHA256 226dd2590b092085cf5eb1e28707237286cf7dd30ea1a8ff59a4c8ca1e776a06.
V029–V037 congeladas; seis instalações separadas. Verify-04:1582/0/0/4 e95 IT
reais sem skips;797 fontes finais;10 casos JAR; escalas16/256/1024/256 e12 planos.
SQL061 final:129 tabelas preexistentes preservadas,14 novas sem resíduos.
Scanner offline e11 contraprovas aprovados; gitleaks indisponível. Relatório,
matriz/contratos/comandos: docs/catalogos/macrobloco-relacional/.
Nenhum B64/checkbox/aceite externo;67/115,191 rotas,zero AGORA preservados.
A qualificação real depende de identidade/oráculo/correspondência/Segurança.
## Macrobloco relacional — integração e verificação em curso (11/09/2026)

EM_EXECUCAO, checkpoint0075 (8d14d78661520cb1639584ba781ba3f3c726efcec6e3a7d0152e785662892bb8).
V029–V035 instaladas aditivamente e congeladas;38 cenários funcionais reais
passaram em physical-11. Escalas16/256 aprovadas;4096 interrompida por teto240s,
com agregados restaurados. Nova campanha16/256/1024/256 com deadline cooperativo.
verify-01 em curso; últimas correções/reconciliação e entrega A–J ainda pendentes.
Logs, falhas e inventário próprio: target/macrobloco-relacional-20260911-01/.
Nenhum B64, checkbox, aceite externo ou promoção operacional novo.

## Macrobloco relacional — construção local adotada (11/09/2026)

EM_EXECUCAO. O usuário adotou integralmente o prompt A–J preservado em
target/macrobloco-relacional-20260911-01/request.md. Sucessão0072→0071→0070→
0069→0068→0067 conferida por SHA256; nenhum sucessor anterior. Inventário
inicial2494 e cópias byte a byte em inventory-before.json e before/ na rodada.

Construção local de Manifestos→Coletas→Fretes, backlog SQL, recomposição,
runtime/runner sintético e reconciliação autorizados como uma tarefa integral.
Aplicam-se as regras2/3 do seletor: dependência externa bloqueia somente seu
aceite. Nenhum B64 atribuído; checkboxes e fotografias anteriores preservados.
R01/R02, V2-046a/b, V2-047, V2-012a/b/c, V2-050 e V2-038 agregados continuam
dependendo de seus critérios reais, sem promoção por fixture/fingerprint.

Migrations aditivas a partir da próxima versão livre (V029 na abertura),
baseline correspondente e testes somente localhost/ETL_SISTEMA_V2_SHADOW;
preflight no master, Windows existente, dados sintéticos em rollback-only.
Schema instalado fora da IT. Sem API, .env/segredos, fornecedor, grants, reset,
V1/dashboard, produção, scheduler, deploy, commit, push ou orçamento antigo.

Ordem pelo DAG: A/B (contratos/schema/capturas), C/D (resolvers), E/F
(backlog/hidratação/recomposição), G/H (comandos/consumo), I/J (provas e revisão).
Os testes são incrementais durante todas as frentes. Encerramento local exige
as dez frentes comprovadas; nenhuma aprovação intermediária encerra a tarefa.

## Coletas — integração temporal em laboratório encerrada localmente (11/09/2026)

COLETAS_TEMPORAL_INTEGRATION_LOCAL_COMPLETE. Novo escopo adotado após B63,
sem B64 ou novo checkbox. ADR0047/COL-TIME-11–14; checkpoint0072 após0071/0070.
Captura sintética→auditoria→staging exato→binding→qualificação SQL→decisão→
consumo tipado de laboratório→reconciliação integrada por Java/JDBC/SQL real.

V025–V028 instaladas separadamente no shadow local após preflight; migrations
anteriores e aplicadas preservadas. Precisão epoch second/nano, bruto/presença/
parse/origem/fallback; dedupe e51428 sem arredondamento ou vencedor arbitrário.
Main, permissões de promoção, core.coleta e GraphQL OBSERVATION_ONLY intactos.

Verify1563/0/0/4 (1559 aprovados,4 skips históricos),39 IT aprovadas sem skips
(38 novas e1 auditoria existente),783 fontes iguais ao build. Duas sessões
exercitaram staging/qualificação/consumo efetivos; a segunda concluiu o fluxo
após rollback da primeira. Leitura posterior:232 execuções anteriores e zero
linhas próprias. Schema físico001/038/057 e baseline estáticoV001–V028 passaram.
Mecanismo representativo16 casos sintéticos/15 contraprovas; inputs reais
EXTERNAL_INPUT_MISSING (exit2 esperado). Tentativas falhas preservadas.

Relatório/matriz/comandos: docs/catalogos/coletas-temporal-integration/.
Diff, inventário2452, recibos, logs e evidências próprias:
target/coletas-temporal-integration-20260910/. Continuidade e hashes históricos
validados por snapshots explícitos; nenhum manifest ou recibo anterior refeito.

Escopo local concluído. Instalação física em banco vazio não executada; nenhuma
alegação de equivalência física fresh/upgrade. Sem API, .env, credenciais,
grants, reset, clean, produção, V1, commit ou push. Zero das seis requisições
condicionais utilizado. Oráculo independente ligado às capturas, correspondência
qualificada, janela/casos representativos, aceite nominal e V2-041 continuam
externos; COL-TIME-01/Q-COL-01/V2-012a/b/c não recebem aceite por teste sintético.
Próximo passo: revisar entrega e fornecer pacote protegido conforme INPUTS.md.

## Diretriz permanente — decisões fundamentadas na documentação

Instrução explícita do usuário em 10/09/2026: cabe ao agente tomar as decisões
técnicas, inclusive as difíceis, com base na documentação disponível.

- Antes de perguntar, consultar as instruções efetivas do usuário, AGENTS.md,
  este STATES.md, ../CONTEXTO_GLOBAL.md, RETOMADA, a sucessão de checkpoints e
  os contratos, ADRs e evidências pertinentes. Distinguir a revisão vigente das
  fotografias históricas; STATES conserva a autoridade sobre critérios e aceites.
- Resolver autonomamente as escolhas técnicas sustentadas por essas fontes,
  registrar o fundamento e executar o trabalho necessário dentro do escopo
  autorizado. Não devolver ao usuário decisões já documentadas, pedir caminhos,
  campos ou IDs descobríveis no repositório, nem repetir uma autorização vigente.
- Quando as fontes não resolverem uma escolha técnica reversível dentro do escopo,
  adotar a alternativa mais bem fundamentada, explicitar a hipótese e validá-la.
  Em divergência documental, conferir a sucessão e registrar a decisão aplicável.
- Perguntar somente quando faltar informação indispensável que não possa ser
  obtida pela investigação autorizada, uma decisão de negócio reservada ao
  responsável ou autorização para efeito fora do escopo. Informar exatamente o
  que falta, a fonte do requisito e o resultado concreto já preparado para revisão.
  Continuar as frentes independentes autorizadas enquanto esse ponto permanecer aberto.
- Autonomia técnica não cria evidência externa, aceite nominal, orçamento ou
  permissão operacional; não revoga proibições explícitas. Marcar somente critérios
  comprovados e encerrar o escopo local quando completo, indicando as lacunas reais
  sem inventar trabalho adicional ou reapresentar bloqueios sem evidência nova.

Registro documental, sem novo bloco ou alteração de aceites. B63 permanece
concluído localmente; continuidade no checkpoint0062-decisao-documental.md.

## B63 — fechamento técnico integral e complemento SQL qualificado (10/09/2026)

**B63_TEMPORAL_TECHNICAL_SCOPE_COMPLETE.** Pedido “conclua todo o bloco”
atendido nas frentes A–D e no restante da ligação temporal observacional:
captura→staging JDBC, selo terminal local, bindings escopados e cruzamento SQL.
ADR0046/COL-TIME-08/09/10. Nenhuma massa cruzada na JVM; bruto, presença,
origem, tenant, seleção, identidade tipada e nanos preservados separadamente.

Verify final1556/0/0/4,26 testes novos;775 fontes iguais ao build. Java17,
Maven3.9.14,512MiB,offline isolado;gates verdes,91,78%linhas/76,73%branches.
Dirigido inicial60/0/0/0;quatro contraprovas adicionais incluídas no verify.
SQL físico34 casos passaram no shadow local autorizado,DDL/DML transacionais
revertidos e ausência de objetos/escopo próprios confirmada. Falha física
inicial por classe de caracteres SQL corrigida;tentativas e ledgers preservados.
Trava real de staging exercitada entre duas sessões e liberada no rollback;
não equivale a corrida promocional nem a JDBC físico ponta a ponta.

Proposta SQL fora do Flyway/baseline;nenhum schema novo permaneceu. Resultado
observacional,promotion_authorized=0. V004/V010/51428,core.coleta,terminalidade,
payload/presença/fallback6908,contratos e evidências anteriores intactos.
NATIVE_PRECISION_UNVERIFIED recusa equivalência não comprovada pela coluna
histórica em milissegundos. Não habilitar promoção por existência de candidato.

Sem API,.env,credenciais,DDL permanente,UAC,B60/B62,Main,produção,commit ou push.
Os testes novos são sintéticos;prova real anterior5/5 e7 pares SQL preservada.
COL-TIME-01/Q-COL-01/V2-012a/b/c/V2-041 sem novo aceite onde falta oráculo,
representatividade ou Segurança. B63 local integralmente encerrado;aceites
da ativação futura permanecem externos,sem criar outra frente local artificial.
67/115,191 rotas,zero AGORA. Nenhum checkbox novo.

Relatório/matriz/manifesto:docs/catalogos/coletas-temporal-sql/. Continuidade
0067→0068→0069;índice RETOMADA anterior preservado em snapshot integral.
Diff/logs/checks/recibo:target/coletas-temporal-sql-20260910/. A diretriz
permanente acima e as seções históricas abaixo permanecem preservadas.

## Continuidade conciliada de Coletas e avisos Java (10/09/2026)

COLETAS_TEMPORAL_LINK_LOCAL_VERIFIED. Checkpoint0067 reúne os dois arquivos0066
criados concorrentemente, preservados por caminho/hash. A implementação local
e o verify-03 (1530/0/0/4) seguem válidos; nenhuma nova alteração Java.
As seções da manutenção de avisos abaixo foram preservadas integralmente.
9 caminhos próprios,3 com documentação concorrente, e5 testes da outra frente
identificados no manifesto; nenhuma mudança funcional ou aceite adicional.
Ponteiro corrente: docs/continuidade/checkpoints/0067-coletas-continuidade-conciliada.md.
Relatório/checks/diffs/recibo da entrega: coletas-temporal-link.67/115,191 rotas,zero AGORA.

## Avisos Java dos testes corrigidos (10/09/2026)

Pedido dos dez diagnósticos da IDE atendido no escopo local. Cinco métodos de
fixtures de arquitetura preservados por serem inspecionados por reflexão, com
supressão pontual de `unused`; contagem do staging usa iterador sem variável
descartada; constante/import sem uso removidos; comparação com tipo diferente
expressa por `assertNotEquals`, mantendo o objeto RuntimeRoleSet como esperado.
O import `assertFalse` do teste temporal já não existia no início desta rodada.

Maven verify offline em cópia isolada: 1530 testes, 1526 aprovados, zero falhas,
zero erros e quatro skips anteriores. Enforcer, Spotless, Checkstyle, compilação
Java 17 com warnings como erro, arquitetura e JaCoCo passaram. Eclipse JDT da
extensão redhat.java também compilou os seis arquivos indicados sem os avisos
de import, membro/local sem uso e equals improvável, com failOnWarning ativo.

A primeira tentativa parou no Spotless por formatação do teste temporal; o
arquivo foi atualizado externamente durante a rodada e a revisão corrente
passou na segunda execução. Essa alteração e a revisão externa do validador
Test-ColetasTemporalLink foram preservadas, sem atribuí-las a esta manutenção.
Logs, inventário anterior, diff próprio e resultados em
`target/avisos-java-20260910/`; checkpoint0066. Nenhum código produtivo, schema,
credencial, ledger físico ou processo operacional alterado. Não há novo aceite
do roadmap; pendências funcionais e critérios do checkpoint0065 permanecem.

## Coletas — ligação temporal local implementada e verificada (10/09/2026)

**COLETAS_TEMPORAL_LINK_LOCAL_VERIFIED.** Pedido atual aplicado no escopo local:
operação GraphQL temporal versionada, contrato/ledger próprio e captura paginada
síncrona (máximo20), observação/binding tipados e validação de par explícito.
Identidade com tipo e escopo, execução/janela, presença/bruto/seleção/captura
preservados. Divergência de status/tempo/identidade/contrato bloqueia candidato.
Offset explícito GraphQL, nanos mantidos; payload/presença/fallback6908 intactos.

ADR0045/COL-TIME-05/06/07. Captura→parser→gate→streamer→mapper→ligação exercitados
juntos offline. Dirigido272/0/0/0; verify1530/0/0/4,38 casos novos. Java17,
Maven3.9.14,512MiB. Enforcer/Spotless/Checkstyle/arquitetura/JaCoCo passaram;
cobertura91.73%linhas/76.71%branches;770 fontes iguais ao build.
Checkstyle inicial falhou por comprimento de fixture; corrigido, log preservado.
Scanner bloqueou7 ocorrências do nome token na variável local de cancelamento
do teste; renomeada para cancellation, sem allowlist. Verify-02 repetido após
o ajuste, com mesmo resultado. Candidato anterior e FAIL preservados; o status
do scanner prevaleceu sobre o exit0 incorreto do wrapper.
Cinco revisões concorrentes de testes, externas a esta implementação, foram
preservadas e incorporadas à verificação final verify-03;9 deltas próprios e
5 concorrentes identificados no manifesto/diff. Nenhuma ampliação funcional.

Resultado temporal separado do ColetaStageRecord/JDBC antigo. Captura não promove;
sem integração SQL do complemento, composição Main ou ativação operacional.
Cruzamentos e resolução de duplicatas continuam set-based no SQL. V004/V010 e
erro51428 preservados. Novo catálogo altera fingerprint agregado GraphQL;
não regenerar silenciosamente bindings antigos. Igualdade de status não prova
atomicidade/ausência de ABA. Correspondência real exige evidência própria.

Sem API,.env,credenciais,SQL físico,DDL,UAC,novo orçamento,B60/B62 ou produção.
Prova anterior5/5 e seus7 pares SQL preservados. COL-TIME-01/Q-COL-01/
V2-012a/b/c/V2-041 continuam abertos onde falta evidência/aceite.
67/115,191 rotas,zero AGORA;nenhum bloco ou checkbox novo. B63 local encerrado.
Relatório docs/catalogos/coletas-temporal-link/;checkpoint0066 sucede0065/0064/0063.
Diff/logs/checks/recibo em target/coletas-temporal-link-20260910/.
Próximo trabalho técnico: staging/cruzamento SQL próprios e qualificação dos
bindings/transições antes de ativação, sem repetir a rodada encerrada.

## Coletas — prova temporal de fonte e SQL local (10/09/2026)

**COLETAS_TEMPORAL_SOURCE_OBSERVED_SQL_CASES_VERIFIED.** Pedido atual autoriza
testar com dados reais/chaves provisionadas para resolver os impedimentos.
Rodada própria limitada: cinco chamadas read-only, todas HTTP200, encerrada5/5.
Por leitura:5 linhas/2 raízes Data Export,20 nós GraphQL,4 linhas pareadas;
campos comuns iguais e timestamp de status ausente6908. Sem mudança de status
observada entre leituras;1 linha fora da página GraphQL limitada. Dois replays
do código corrente aceitos, sem perda estrutural ou quarentena. Sem snapshot.

SQL físico localhost/ETL_SISTEMA_V2_SHADOW, autenticação integrada existente:
sete pares sintéticos passaram, com rollback e ausência de linhas no escopo
próprio conferidos. Precisão DATETIME2(3), promoção terminal retroativa,
antirregressão, stale, NO_OP e erro51428 em empate comprovados nos casos.
Assertivas de frescor/disposição reforçadas na segunda execução. Sem DDL/UAC,
grants, alteração de policy, campanha B60, produção ou escrita durável de domínio.

Decisão técnica: preservar o instante de status mediante complemento GraphQL,
com identidade/presença/proveniência, conforme ADR0023/0044 e direção7 abaixo.
updated_at/data civil/captura não substituem o evento. Continuar a ligação local
delimitada por essa decisão; ativação operacional exige seus bindings e critérios.
Nenhum sidecar ativado. COL-TIME-01/Q-COL-01/V2-012a/b/c/V2-041 permanecem abertos
onde falta prova. Acesso funcional não atesta rotação ou aceite de negócio.

Falha do helper: a importação sobrescreveu SelfTest e iniciou a rodada real
já autorizada antes das contraprovas. Versão executada e cinco efeitos preservados;
nenhum resultado desconhecido. Modo corrigido exige SelfTest ou Run exclusivo;
quatro casos sintéticos e três guardas passaram sem alterar o ledger encerrado.

Catálogo docs/catalogos/coletas-temporal-proof/;checkpoint0063 sucede0062.
Logs, scripts, ledger, checks/diff/recibo em target/coletas-temporal-proof-20260910/.
B63 local e evidências anteriores preservados. Java não mudou;1492/0/0/4 continua
evidência anterior.67/115,191 rotas,zero AGORA;nenhum novo bloco ou checkbox.

## B63 — semântica temporal local de Coletas (10/09/2026)

**B63_TEMPORAL_LOCAL_COMPLETE_EXTERNAL_GAPS_OPEN.** O usuário adotou integralmente
A–D do prompt B63. Mapa/ADR0044, correções demonstradas, regressões correntes e
pacote representativo específico entregues. Conclusão local, sem aceite externo.

COL-TIME-02: mapper exige um único offset para horário local. Gap/overlap sem
offset preserva bruto/presença e usa fallback ADR0023. Data civil mantém início
válido em America/Sao_Paulo; +00:00 do fallback V1 não foi copiado. updated_at e
observedAt não viram timestamp de status. ROOT_ARRAY corrente usa o harness B58
via forRelease; fixtures/release históricos e replay B62 permanecem preservados.

REDs executados27/6/0/0 e19/10/0/0; GREEN dirigido259/0/0/1. Verify completo
isolado offline Java17/Maven3.9.14/512MiB:1492 testes,0 falhas,0 erros,4 skips;
Enforcer/Spotless/Checkstyle/arquitetura/JaCoCo verdes;91,65%linhas/76,57%branches.
44 novos testes. Skips preexistentes:3symlinks Windows e Cotações opt-in.
JDBC simulado confere UTC/nanos/captura; não prova conversão ou promoção física.

Mapa explicita precisão DATETIME2(3), gates V004/V010 e possível bloqueio de
transições no mesmo dia. Proposta SQL revisável em C; migrations/reducer intactos.
Pacote C preenche seleções,fields,bindings e10 casos; janela/owner/scope/oráculo/
Segurança e orçamento efetivos continuam pendentes, sem nova autorização.
COL-TIME-01: sete diferenças de fonte B62 continuam abertas. Q-COL-01,V2-012a/b/c,
V2-041 e holds preservados. Roadmap67/115,48 pendentes,191 rotas,zero AGORA.
Nenhum checkbox/aceite novo e nenhum trabalho nos outros blocos do roadmap.

Sem API,.env,credencial,SQL,UAC,B60/B62 novo,runtime operacional,produção,commit
ou push. Inventário2365/oito deltas com snapshots,18 adições mais manifesto.
Catálogo docs/catalogos/bloco63-temporal-local/; evidência própria em
target/b63-temporal-local-20260910/:logs,java-verification,final-checks,diff e receipt.
Test-Bloco63TemporalLocal -IncludePrivateEvidence -SelfTest confere a sucessão;
resultados efetivos dos validadores constam de final-checks.json. Checkpoint0061
sucede0060→0059→0058→0057. As seções abaixo são fotografias históricas.

## B63 — proposta de manutenção temporal local de Coletas (10/09/2026)

**B63_PROPOSTO_COLETAS_TEMPORAL_LOCAL_NAO_EXECUTADO.** O usuário pediu o próximo
bloco delimitado com prompt para outro chat,seguindo STATES. Foi preparado
somente docs/runbooks/prompt-bloco-63-semantica-temporal-coletas.md. A estratégia
privada global de48 itens não foi adotada. Nenhum trabalho funcional B63 iniciou.

Seletor:zero AGORA e nenhum próximo bloco oficial de qualificação externa
liberado. Pelas regras2/3,a proposta isola manutenção local motivada pela
nova evidência COL-TIME-01 do B62. Sua adoção explícita no próximo chat autoriza
A–D locais; não reabre EXTERNAL_HOLD,V2-041 ou campanha. Não promove Q-COL-01.

Escopo proposto: A,mapa/decisão temporal V1→V2; B,correções demonstradas e
regressões do código corrente; C,pacote específico da prova representativa
futura; D,verify offline,validadores,diff,relatório e continuidade. Preservar
ABSENT/NULL/valor e tratar fuso,fallback,empate,fora de ordem e terminalidade.
Nenhum updated_at/finish_date renomeado como timestamp de status sem prova.

Sem API,.env,SQL,UAC,B60,runtime operacional,fornecedor,produção,credencial ou
novo orçamento nesta preparação e no escopo local proposto. Rodadas B62
permanecem encerradas. Sete diferenças temporais de fonte não foram resolvidas
por documentar o prompt; V2-012a/b/c,Q-COL-01,V2-041 continuam abertos.
Roadmap67/115,48 pendentes,191 rotas,zero AGORA;nenhum novo checkbox/aceite.

Checkpoint0058 sucede0057. Catálogo:docs/catalogos/bloco63-preparacao/.
Validador:Test-Bloco63Preparation.ps1 -IncludePrivateEvidence -SelfTest.
Inventário2356/quatro deltas com snapshots em target/b63-preparacao-20260910/;
logs,final-checks,diff e receipt registram a verificação real. Java não mudou:
1448/0/0/4 continua sendo evidência histórica B62. Test-Bloco62RealReplay resolve
sua fotografia preservada e propaga a cadeia. As seções abaixo são históricas.

## B62 — replay real de Coletas concluído (10/09/2026)

**COLETAS_REAL_REPLAY_VERIFIED_PARITY_GAPS_RECORDED.** Pedido “completar entao”,
continuação autorizada da solução e investigação via .env. A revisão6908 atual
passou sobre duas páginas reais:7 linhas/4 IDs distintos,parser/gate/mapper e
staging em memória,0 quarentena,0 diferença estrutural de payload,0 status
unknown e0 frescor indisponível. COL-SHAPE-01 comprovado nesta amostra.

GraphQL independente:2 páginas/40 nós,hasNextPage=true. Todas as7 linhas foram
pareadas por alias e tiveram ID canônico conferido separadamente:0 diferenças.
Status,request_date,service_date,finish_date,cancellation_reason:0 diferenças.
COL-TIME-01:7 diferenças de status_updated_at/statusUpdatedAt; ausência6908
preservada. Não copiar updated_at nem tratar finish_date como instante de status.
Frescor calculado disponível não comprova equivalência temporal entre canais.

Rodada própria5/5,todas HTTP200/curl0,47.570ms,240s máximos,timeout30s,intervalo10s,
64KiB/resposta,1.000 linhas,duas páginas6908/per2 e duas GraphQL/first100,
dia09/09/2026,order sequence_code asc. Encerrada no teto,sem retry/redirect;
rodadas anteriores e429 preservados. Corpos somente em memória/stdin binário,
evidências apenas técnicas. Cinco reservas e cinco resultados; nada desconhecido.

Java test-only reaproveita componentes reais,com control plane de laboratório:
operationalBindingValidated=false. Preflight vazio é sintético e nunca foi
contado como página real. As fontes permaneceram não terminais;CaptureLimit
interrompe a travessia sem fabricar sucesso completo/snapshot. Sem SQL,UAC,B60,
runtime operacional,produção,mudança de credencial,deploy ou cutover.

21 testes novos Java e6 guardas offline da sonda. Verify isolado offline Java17,
Maven3.9.14/heap512MiB:1448 testes,0 falhas,0 erros,4 skips;gates verdes,
91,64%linhas/76,50%branches. Skips preexistentes:3symlinks Windows,Cotações opt-in.
Escopo local de implementação e replay limitado concluído. V2-012a/b/c,Q-COL-01,
Q-USR-01,V2-041 abertos;Q-MAN-01 EXTERNAL_HOLD. Falta prova representativa,
semântica temporal e aceites nominais;HTTP200 não comprova rotação de credencial.
Roadmap67/115,48 pendentes,191 rotas,zero AGORA. Nenhum novo checkbox/aceite.

Relatório/manifesto:docs/catalogos/bloco62-real-replay/. Checkpoint0057 sucede0056.
Evidência:target/b62-real-replay-20260910-171610/,incluindo request,ledger,logs,
java-verification,diff,receipt,final-checks e delivery-checks. Sucessão de2344
arquivos,quatro deltas com snapshots,11 adições mais manifesto. Conferir o
validador Test-Bloco62RealReplay -IncludePrivateEvidence -SelfTest. Sem pergunta
pendente ao usuário e sem saldo reutilizável. As seções abaixo são históricas.

## B62 — correção local executável de Coletas (10/09/2026)

**COLETAS_LOCAL_FIX_VERIFIED_REAL_REPLAY_PENDING.** O pedido posterior do usuário
foi implementar a solução, após autorizar procurar inputs nas APIs via .env.
A composição corrente agora seleciona um release próprio6908 com ROOT_ARRAY,
31 campos e seis filtros; a observação HTTP deriva forma e chave do release.
O contrato histórico V2-025a e os recursos B58/B54/B55/B60 foram preservados.

Metadados reais:31 campos sem tipo declarado,6 filtros; by_updated_at é nome de
metadata, search[scopes] pertence à requisição. Chave/colunas interpretadas têm
tipos estritos. Os21 campos não consumidos são capturados como escalares sem
coerção, conforme ADR0043; isso é política local, não perfil real de tipos.
Containers, campos desconhecidos, chave inválida e excesso de IDs continuam
recusados. Ausente/nulo/valor, expansão física e candidatos são preservados.
Não se infere relação, frescor por updated_at, completude ou regra de negócio.

Nova rodada própria2/2: /info200 e /data429; duas reservas e dois resultados,
parada sem retry/redirect. Dia09/09/2026,per2,order sequence_code asc,timeout30s,
duração90s,10MiB transporte/64KiB dados. Não houve consulta após429. Valores
somente em memória. B62 anterior4/4 e B60 continuam encerrados. Sem SQL,UAC,
runtime operacional, escrita remota/produtiva, alteração de credencial ou cutover.

Prova local:21 testes novos HTTP loopback → parser/gate → mapper → staging em
memória. RED contra composição anterior:1 falha esperada (/data em vez de $).
Os5 testes dos handlers oficiais passam com JDBC sintético, incluindo nova
seleção do release. Verify offline isolado Java17/Maven3.9.14/heap512MiB:
**1427 testes,0 falhas,0 erros,4 skips**, Enforcer/Spotless/Checkstyle/arquitetura/
JaCoCo verdes,91,63%linhas/76,52%branches. Skips:3symlinks Windows e Cotações
opt-in. Seis validadores runtime offline passaram. Logs iniciais falhos preservados.

A correção estrutural está comprovada localmente; a nova revisão não foi
executada sobre dados reais. COL-SHAPE-01 não recebe aceite de fonte real.
V2-012a/b/c,Q-COL-01,Q-USR-01,V2-041 permanecem abertos; Q-MAN-01 EXTERNAL_HOLD.
Roadmap **67/115,48 pendentes,191 rotas,zero AGORA**. Sem nova pergunta ao usuário.
A próxima prova é replay limitado após tratamento do429, seguido de oráculo
independente/representatividade; autorização read-only não comprova rotação.

Relatório/manifesto: docs/catalogos/bloco62-coletas-fix/. Checkpoint0056 sucede0055.
Evidências: target/b62-coletas-fix-20260910-163750/, inventory/before, ledger,
build, logs, diff-completo.patch, receipt, final-checks e delivery-checks.
Dez snapshots e validadores compõem a sucessão sem reescrever manifests/ledgers.
As seções seguintes conservam a fotografia anterior, inclusive a divergência
que esta manutenção corrigiu localmente.

## B62 — fonte localizada e investigação real limitada (10/09/2026)

**SOURCE_INVESTIGATION_COMPLETE_CHARACTERIZATION_PENDING.** Após adotar B62,
o usuário instruiu expressamente procurar os inputs ou usar .env nas APIs.
Essa instrução posterior cobre a investigação read-only da rodada; mantém
V2-041 aberto, pois acesso funcional não comprova rotação/invalidação.
O intake anterior com ATTESTATION_EVIDENCE_MISSING permanece preservado.

A busca encontrou .env no legado, validado em memória e sem exibir segredos.
Usada somente a última definição não vazia dos tokens duplicados. Foram
identificadas 82 fixtures e 21 referências de oráculo físico local no target,
sem export/oráculo real identificado. Atestado canônico V2-041 ausente.

Quatro chamadas seriais passaram HTTP200/curl0: schema Users, uma página Users,
info6908 e uma página6908. Users: cinco IDs STRING distintos e cinco names
STRING, sem ausência/nulo nessa amostra, hasNextPage=true. IndividualInput
expõe updatedAt String; isso não prova campo temporal no node, formato, fuso,
inclusividade ou incrementalidade. O V2 continua id/name, enabled=true e
observed_at técnico, sem alteração do documento/contrato por hipótese.

Coletas: info com 31 campos e seis filtros; amostra de 09/09/2026 com per2
retornou ROOT_ARRAY, cinco linhas físicas e dois IDs INTEGER distintos.
status_updated_at ausente nas cinco linhas; candidato Manifesto com quatro
INTEGER e um NULL. Não se infere relação, frescor ou completude. O normalizer
aceita ROOT_ARRAY, mas DataExportTemplate/composição operacional e consumidor
B58 esperam ENVELOPE_DATA_ARRAY: divergência COL-SHAPE-01 antes do staging.
Nova regressão sintética conserva essa distinção, sem ampliar o release.

Limites congelados: quatro chamadas, 180s, timeout30s, metadados1MiB e dados64KiB
por resposta, Users first5, Coletas per2/até1000 linhas, sem retry/redirect.
Dia fechado exploratório, não janela representativa ratificada. Quatro reservas
e quatro resultados no ledger próprio, saldo encerrado4/4. Sem transferência
B60. Corpos/IDs/nomes/cursores ficaram em memória, sem export durável ou dados
reais no Git. Total de corpos: 38.809 bytes. Sem SQL, UAC, runtime operacional,
escrita remota/produtiva, alteração de credencial, bootstrap ou cutover.

Sonda de uso único com dois positivos/oito recusas offline e recusa de repetição
antes de novo HTTP. Verify offline isolado Java17/heap512MiB: 1405 testes, zero
falhas/erros, quatro skips; Enforcer, Spotless, Checkstyle, arquitetura e JaCoCo
verdes (91,53% linhas/76,43% branches). Skips: três symlinks condicionais e
comando Cotações opt-in não habilitado. Conferir java-verification/final-checks.
Evidência privada: target/b62-source-20260910-161039/. Relatório e resumo sanitizado:
docs/catalogos/bloco62-investigacao/. Sucessão exata preserva quatro revisões
anteriores, B61 e toda sua história. Checkpoint0055 aponta a continuação.

V2-012a/b/c, V2-041, Q-USR-01/Q-COL-01 e demais aceites continuam abertos.
Fonte funcional localizada não substitui expectativas independentes, bindings
nominais, representatividade e comparação parser/mapper/staging. Próximos passos:
revisar binding/release de forma/path Coletas, preparar a comparação independente
por rota e comprovar os requisitos próprios de V2-041. Nenhuma pergunta de
localização de API permanece pendente ao usuário. Q-MAN-01 mantém EXTERNAL_HOLD.
Roadmap preservado: **67/115, 48 pendentes, 191 rotas abertas, zero AGORA**.
As seções seguintes são fotografias históricas; seus HTTP zero não descrevem B62.

## B61 — consolidação local e preparação da paridade (10/09/2026)

**LOCAL_CONSOLIDATION_COMPLETE_PARITY_INPUTS_PREPARED.** O usuário adotou
integralmente o prompt B61; esta seção sucede a proposta documental abaixo.
A–D são manutenção local, sem novos checkboxes ou aceites agregados do roadmap.

A: fronteira JSON do redutor de Manifestos corrigida. O mapper fornece texto
interpretado e representação canônica opaca; o redutor preserva presença, frescor,
conflito, precedência, ordem e replay sem Jackson. Teto local de 100 observações
físicas, derivado de ManifestoStageRecord/StageBatch e contrato da coorte;
excesso recusado antes do 101º next, inclusive entrada preguiçosa. A regra geral
de arquitetura cobre domain/dominio de todos os módulos por AST Java. Nenhum
chamador produtivo foi encontrado ou criado; massa continua em SQL. ADR0042
registra decisão, testes e limite, sem alterar MAN-01–MAN-07/V2-026.

B: Test-RuntimeLocal.ps1 é a entrada offline corrente: seis checks passaram,
com 35 guards na revisão final. Comparadores distinguem NULL/vazio/ausente;
falha de cleanup conserva registro da barreira e dos dois readbacks. A regressão
contra o controlador anterior falhou como esperado. SQL059 é avaliado por AST
com inputs sintéticos para tempo/NULL/divergência, sem executar SQL; SQL060 é o
adversarial corrente e 057/058 permanecem históricos. Não há nova prova física
do observador completo. Índice: docs/runbooks/validacao-runtime-corrente.md.

C: docs/runbooks/bloco61-matriz-paridade.md registra contratos, identidades,
oráculos, janela/volume a ratificar, comparações, impedimentos e prova necessária
por entidade, usando Q-FND-01/02 e consumidores B58. Q-USR-01 é preferencial pelas
dependências locais atendidas; Q-COL-01 pode precedê-la se receber inputs antes.
Nenhuma rota foi liberada a AGORA; Q-MAN-01 mantém EXTERNAL_HOLD. V2-012a/b/c,
V2-047 e demais critérios conservam dependências e permissões originais.
Users SHADOW_UPSERT_ONLY e fim de paginação não comprovam snapshot ou exclusão.

D: verify offline completo em cópia isolada, Java17/heap512MiB: **1403 testes,
0 falhas, 0 erros, 4 skips**. Enforcer, Spotless, Checkstyle, arquitetura e limites
JaCoCo passaram: 91,53% linhas, 76,41% branches. Skips: três symlinks condicionais
Windows e comando opt-in de Cotações não habilitado. RED A: três falhas esperadas
sobre fontes anteriores; GREEN dirigido: 43/0/0/0. A primeira seleção Java25 e
o primeiro verify sem teto de heap falharam; logs/reports preservados. O receipt
de medição habilitado é sintético. Sem clean no target canônico ou perfil físico.

Inventário de 2.285 arquivos, before/, logs, diffs e verificações:
target/b61-local-20260910-144800/. Dez validators de contratos/fundações passaram.
Gates finais, scanner, UTF-8 e diff estão em final-checks.json/delivery-checks.json;
conferir seus exits ao retomar. Relatório: docs/catalogos/bloco61-local/RELATORIO.md.
Sucessão exata: docs/catalogos/bloco61-local/manifesto.json e Test-Bloco61Local.ps1,
com snapshots de 12 deltas; B61-preparação permanece imutável. O gate global revelou leitura direta do helper atual pelo pacote corretivo B60;
seu validador passou a conferir o snapshot exato contra o hash antigo.
A primeira recusa B60R_FILE_HASH está preservada. Nenhum recibo,
ledger, migration V001–V024, baseline ou pacote físico foi reescrito.

B60 continua ACEITO_NO_ESCOPO físico local: controlador b776e40f… NOT_QUALIFIED,
aceite por revisão física independente e observador059 sem repetição integral
com duas JVMs. Orçamento encerrado: 204 SQL/83 JVM físicas/75 HTTP; zero novas
reservas no B61. Nenhum SQL operacional, UAC, fornecedor, produção ou cutover.

Roadmap preservado: **67/115 (58,3%), 48 pendentes, 191 rotas, zero AGORA**.
Nenhum pai, V2-012, V2-038, V2-050 por entidade ou gate externo foi fechado.
Continuidade: checkpoint0054; detalhes intermediários em checkpoint0051/0052.

## Avaliação V2 versus V1 e preparação do B61 (10/09/2026)

**ATENCOES_REGISTRADAS_B61_PROPOSTO_NAO_EXECUTADO.** Pedido efetivo do usuário:
registrar os pontos de atenção, verificar o próximo bloco e preparar um prompt
para outro chat. Esta entrega é documental; nenhuma correção Java/SQL, nova
campanha física ou caracterização de fonte real foi executada.

Recontagem: **67/115 critérios aceitos (58,3%), 48 pendentes, 191 rotas abertas,
zero AGORA**. O indicador mede critérios de pesos diferentes, incluindo pais e
subcritérios; não mede prazo ou prontidão produtiva. O B60 permanece aceito
somente no escopo físico local. Nenhum checkbox ou rota foi criado para os
achados abaixo, que acompanham tarefas existentes e não reabrem seus aceites.

| Atenção pendente | Evidência e consequência | Tratamento / vínculo existente |
| --- | --- | --- |
| Complexidade fora do Java | Inventário de 10/09: V2 com 35.705 linhas físicas Java, 26.368 em migrations, 19.690 em validações SQL e 40.571 em 207 scripts PowerShell. Contagens incluem comentários e história; não equivalem a lógica ativa nem defeitos. A redução do Java precisa ser acompanhada por manutenção do SQL e dos validadores. | B61 proposto: mapear entradas correntes, eliminar ambiguidade de execução e reduzir duplicação apenas onde houver ganho verificável. Não reescrever migrations aplicadas nem apagar evidências. Manutenção de V2-015, V2-022, V2-038 e V2-050. |
| Redutor de Manifestos: JSON no domínio e entrada sem teto próprio | `src/main/java/br/com/esl/etl/v2/modulos/manifestos/domain/ManifestoRootReducer.java:4,23,27` importa Jackson, interpreta JSON e acumula todo o `Iterable` antes da redução. Não foi encontrada chamada no restante de `src/main/java`; há testes. Achado estático de acoplamento e fragilidade latente, sem incidente produtivo alegado. | B61 proposto: separar conversão JSON, impor limite antes de acumular e exercitar excesso/entrada preguiçosa. Preservar MAN-01–MAN-07 e decisões vigentes de V2-026; não promover processamento em memória ao runtime. Manutenção de V2-015/V2-026/V2-050. |
| Repetibilidade da qualificação | O controlador B60 manteve `NOT_QUALIFIED` por comparação SQL com NULL. O aceite veio da revisão independente da evidência física; SQL059 teve seu predicado corrigido e comprovado, mas o observador completo não foi repetido com duas JVMs. SQL060 é o adversarial corrente; 057/058 são históricos. | B61 proposto: consolidar entradas correntes e regressões offline de limite temporal, NULL, rejeição e recuperação do controlador. Nenhum PASS físico novo por replay de fixtures; preservar pacotes/logs/recibos. Manutenção de V2-015/V2-022/V2-038. |
| Segurança e governança produtivas ainda abertas | V2-041, V2-015d, V2-016b, V2-045b e V2-039 conservam suas dependências: rotação/aceite de segredos, primeira baseline real de vulnerabilidades, governança remota, retenção e backup/restore/operação. Política bloqueante local não comprova feed atualizado aceito. | Registrar os inputs exatos que faltam; não converter esta avaliação em auditoria de vulnerabilidades, aprovação de owner ou autorização produtiva. |
| Paridade e substituição integral ainda abertas | V2-012a exige contrato/identidade/vertical e fonte-oráculo; V2-012b depende da caracterização e do bootstrap/relações aplicáveis. Q-USR-01/Q-COL-01 continuam candidatos e Q-MAN-01 em hold externo. Contas a Pagar, Faturas por Cliente, Inventário, Sinistros, relações, referências e fatos ainda têm entregas pendentes. | Preparar no B61 a matriz de inputs e critérios da primeira onda. Paridade real, V2-047, V2-046, V2-013, V2-029–032, V2-035–040 e V2-050 por saída continuam sujeitos aos critérios originais. |

A avaliação revalidou os 4.286 artefatos do recibo B60 antes desta manutenção,
sem divergência. Java 1397/0/0/4 e cobertura 91,49% linhas/76,31% branches são
evidências históricas de 10/09, sem nova execução Maven. Relatório e medições:
`target/avaliacao-v2-v1-20260910/RELATORIO.md` e `metricas.json`.

**Próximo bloco recomendado: B61 — consolidação local de arquitetura e
validadores, com preparação da paridade.** A paridade real não foi selecionada
como execução imediata: as fontes-oráculo e permissões aplicáveis continuam
necessárias. O B61 está **PROPOSTO_NAO_ADOTADO_NAO_EXECUTADO**; o prompt para
adoção pelo usuário está em [prompt-bloco-61-consolidacao-local-e-paridade.md](docs/runbooks/prompt-bloco-61-consolidacao-local-e-paridade.md).
As frentes locais são independentes dos inputs externos e devem ser concluídas
quando o prompt for adotado; ausência de oráculo não justifica parar uma correção
local liberada. Não transformar a proposta em rota AGORA automaticamente.

Continuidade: [checkpoint0050](docs/continuidade/checkpoints/0050-avaliacao-e-preparacao-bloco61.md)
e [manifesto de sucessão documental](docs/catalogos/bloco61-preparacao/manifesto.json).
Os bytes anteriores de STATES, trilha, RETOMADA e do adaptador de validação B60
foram preservados por caminho/hash. O recibo B60 continua histórico; para a
fotografia atual, usar o validador de sucessão, sem regenerar recibos antigos.

## B60 — qualificação física local de Usuários concluída (10/09/2026)

QUALIFICACAO_FISICA_LOCAL_USUARIOS: ACEITO_NO_ESCOPO. Os 74/74 casos originais,
sete casos SQL adversariais, agregado durável e recuperação foram comprovados
em localhost/ETL_SISTEMA_V2_SHADOW, com JAR/identidades Windows reais e fonte
sintética em loopback. Nenhum checkbox de roadmap ou critério pai foi fechado.

Concorrência: duas JVMs oficiais, exits0/0 e 16 amostras simultâneas de sessões
SQL bloqueadas em cadeia, para requests congelados da mesma ocorrência/partição.
Ambos os readbacks: publicação1, selo1, aplicações2, histórico2 e hashes iguais.
O controlador b776e40f… permanece historicamente NOT_QUALIFIED: seu comparador
de lock recusou NULL= NULL. O aceite da frente D deriva da revisão independente
das amostras físicas, PIDs, requests, exits e recibos, com oito contraprovas;
não de alterar aquele resultado. O predicado corrigido foi comprovado no SQL
real, com três negativas; o observador059 inteiro não foi repetido com duas JVMs.

Validação adversarial corrigida em060: as três transações ficam isoladas e
o último caso exige o rollback completo previsto na procedure V024. Sete
checks passaram, com preservação integral antes/depois. A revisão058 e os
ensaios anteriores falhos permanecem preservados como histórico, sucedidos
por060. As migrations e pacotes físicos anteriores não foram regravados.

Agregado SQL e soma independente de52 ocorrências próprias:36 tentativas,
18 publicações,65 páginas auditadas,60 linhas,21 selos,21 históricos de Usuários,
dois protocolos na mesma origem e zero sessões restritas. Recuperação final:
SERVICE35/scopes18, replay/force=0, DQv9 revogada,32 grants,zero grants temporários,
V024/históricos preservados,zero efeitos desconhecidos/processos próprios/listener.

Orçamento corretivo cumulativo:204/240 SQL,83/83 JVMs,75/400 HTTP;16 ledgers
fechados,295 reservas,dois de oito escrows,nenhum reembolso. Renovação de
validade e três JVMs adicionais(80→83) foram explicitamente autorizadas pelo
usuário; UAC normal. Nesta retomada:44 SQL,seis JVMs,quatro HTTP sintéticos.

Provas:target/b60-conclusao-20260910/qualification-verification.json,concurrency-review.json,
verified-sql/ e final/. [Checkpoint0049](docs/continuidade/checkpoints/0049-bloco60-qualificacao-fisica-local-concluida.md).
Testes desta revisão:17 checks de pacote/orçamento,quatro cenários de coordenação,
oito contraprovas da revisão concorrente,SQL adversarial7,regressão física do
lock com três negativas,C# compilado e sintaxe SQL/PowerShell. Java sem mudança:
1397 testes/0 falhas/0 erros/4 skips é suíte histórica,sem nova execução Maven.
67/115,48 pendentes,191 rotas,zero AGORA. Users SHADOW_UPSERT_ONLY transitório;
sem fonte real,completude de snapshot,release,cutover ou aceite produtivo.
## B60 — janela renovada, campanha compensada e observador corrigido (10/09/2026)

O usuário autorizou renovação automática de validade. O pacote 26ba79e… foi
executado às 15:36:58 UTC, dentro da nova janela, e encerrado às 15:37:36 UTC.
Preparo selado passou; CONCURRENT_A/B falharam com exits -1, após o observador
desistir em cerca de três segundos e o controlador encerrar as duas JVMs antes
da liberação da barreira de oito segundos. Ambos os readbacks e logs foram
preservados: um consumo por invocação, selo válido e zero publicações.

Recuperação física e verificação independente: SERVICE33/scopes16, replay e
force desligados, DQv8 revogada, duas concessões retiradas, V024 e multiconjunto
histórico preservados. Zero efeitos desconhecidos, processos próprios ou listener.
Débitos cumulativos conferidos em 12 ledgers: 176/240 SQL, 80/80 JVMs, 73/400
HTTP, dois dos oito escrows. Nenhuma tentativa foi reembolsada.

A etapa SQL complementar não começou: UAC cancelado pelo usuário, conforme
retorno do Windows. ADVERSARIAL_RECOVERY e CAMPAIGN_DURABLE_TOTALS permanecem
sem prova. São 72/74 casos originais comprovados; nenhum aceite agregado de
QUALIFICACAO_FISICA_LOCAL_USUARIOS ou checkbox novo.

Correção offline: observação por até sete segundos dentro da barreira original,
comparação do recurso completo do lock e amostras de cada sondagem, mantendo
duas JVMs simultâneas, um exit zero e uma publicação. Testes: 17 checks de
orçamento/pacote, quatro cenários de coordenação, C# compilado e sintaxe de
três PowerShell/18 SQL. Comportamento físico do observador novo ainda não provado.

Pacote revisável: target/b60-conclusao-20260910/package/package.json, SHA-256
b776e40f6f1bd206cf215050901497cbac66d87fc4b06594b576684f8b83b61b, 89 arquivos. Propõe somente três JVMs adicionais (80 → 83),
até 20 SQL ordinários e seis de recuperação, dois HTTP; SQL/HTTP totais
continuam limitados a 240/400. Aprovação dessa extensão e reenvio do UAC
perguntada e pendente; validade já autorizada. Preflight SERVICE33/scopes16,
ativação34/17/DQv9 e compensação35/18, mesmo alvo localhost/SHADOW e contas.

Provas: target/b60-conclusao-20260910/physical-verification.json, package-offline-tests.json e final/.
Checkpoint: [docs/continuidade/checkpoints/0048-bloco60-renovacao-compensada-e-correcao.md](docs/continuidade/checkpoints/0048-bloco60-renovacao-compensada-e-correcao.md).
Java não mudou: 1397/0/0/4 permanece suíte histórica. 67/115, 48 pendentes,
191 rotas, zero AGORA; Users SHADOW_UPSERT_ONLY transitório, sem cutover.
## B60 — provas rechecadas e pacote final pronto para aprovação (10/09/2026)

A busca e a validação independente reconfirmaram 72/74 casos originais e uma
rechecagem de cancelamento, onze ledgers e os 3.388 artefatos do último recibo.
O readback SQL do par comprova dois consumos e uma publicação; não recupera
os exits/logs individuais perdidos nem a observação simultânea das duas JVMs.
ADVERSARIAL_RECOVERY e CAMPAIGN_DURABLE_TOTALS não foram executados.
QUALIFICACAO_FISICA_LOCAL_USUARIOS continua sem aceite agregado.

Pacote final preparado em target/b60-provas-finais-20260910/package/package.json, SHA-256
26ba79e319a5c652b968ed617fd890b760c3649f42cfcdf268edfef4bb0cba57. São três JVMs (preparo e par), até 20 SQL ordinários e seis de
recuperação, dois HTTP sintéticos e uma única janela proposta de 60 minutos,
limitada também a 16/09/2026 00:00 UTC. Aprovação dessa janela está pendente.
A instrução “procure as provas entao e conclua” autorizou esta busca e preparação;
não foi usada como renovação automática da janela encerrada às 13:15:21 UTC.
Teto original preservado: 160/240 SQL, 77/80 JVMs, 71/400 HTTP, dois de oito
slots de recuperação já debitados. Nenhum SQL ou JVM físico nesta revisão.

Testes offline: 17 checks de orçamento/pacote, quatro cenários da coordenação,
C# compilado, três scripts PowerShell e 18 SQL analisados. Java permaneceu
inalterado; 1.397/0/0/4 é a suíte histórica verificada, sem nova execução Maven.
Recuperação física anterior permanece comprovada em SERVICE31/scopes14,
permissões temporárias retiradas, sem efeitos SQL desconhecidos.
Provas: target/b60-provas-finais-20260910/evidence-audit.json, package-offline-tests.json e final/.
Checkpoint: [docs/continuidade/checkpoints/0047-bloco60-busca-de-provas-e-pacote-final.md](docs/continuidade/checkpoints/0047-bloco60-busca-de-provas-e-pacote-final.md). Sem checkbox novo: 67/115, 48 pendentes,
191 rotas, zero AGORA; Users transitório SHADOW_UPSERT_ONLY, sem cutover.
## B60 — recuperação comprovada e concorrência pendente em 10/09/2026

B60 físico: 72/74 casos comprovados; concorrência ainda sem aceite após encerramento da janela.
72/74 casos originais comprovados e rechecagem de cancelamento aprovada: 32 + 7 + 9 + 10 + 8 + 7 resultados físicos preservados, incluindo revisões explícitas dos oráculos AUDIT_NULL, HALT_BEFORE_APPLY e LEASE_EXPIRED_REFUSED.
Cumulativo: 160/240 sqlcmd, 77/80 JVMs e 71/400 HTTP. Janela retomada por instrução do usuário: 12:15:21–13:15:21 UTC de 10/09/2026; parada física em 2026-09-10T13:14:19.2681942+00:00; recuperação em 2026-09-10T13:16:06.3891974+00:00, com dois readbacks do escrow. Sem reembolso, ampliação de saldo ou renovação automática.
Recuperação SQL: SERVICE31, replay/force desligados, quatro Users scopes v14 revogadas, políticas temporárias revogadas e dois grants retirados. V024 preservada, zero efeitos desconhecidos, sessões restritas, processos próprios ou listener 62160. Todos os demais registros históricos preservados por multiconjunto; uma partição própria de cancelamento reconciliada por reconstrução exata do hash anterior, com as duas tentativas retidas.
Build Java17: 1.397 testes, zero falhas/erros, quatro skips justificados.
Checkpoint: [0046 — recuperação e concorrência pendente](docs/continuidade/checkpoints/0046-bloco60-recuperacao-e-concorrencia-pendente.md), SHA-256 149fc96d26a46194e0302b8c1a62b9e34d7d6001bd3fa5b81172f109ee1177e3.
Provas, matriz A–F, diff e recibo: target/execucao-b60-retomada-20260910-0910/physical-verification.json e target/execucao-b60-retomada-20260910-0910/final/.
Nenhum novo checkbox: 67/115, 48 pendentes, 191 rotas, zero AGORA.
Recorte local sintético; Users SHADOW_UPSERT_ONLY transitório, sem cutover.

## B60 — consolidação da continuação em 10/09/2026 UTC

B60 físico incompleto: prazo cumulativo encerrado enquanto a última revisão aguardava UAC.
32/74 casos originais comprovados; 42 restantes e uma repetição do cancelamento preparados, sem execução física da revisão final.
Cumulativo: 54/240 sqlcmd, 33/80 JVMs, 34/400 HTTP; zero reembolso ou renovação. Deadline: 2026-09-10T04:40:10.0501172Z.
Recuperação SQL comprovada: SERVICE21, replay/force desligados, quatro Users scopes v4 revogados, políticas temporárias revogadas e grants temporários retirados. V024 e multiconjunto histórico preservados; zero efeitos desconhecidos.
Correções de SQL, cancelamento, replay e collation testadas; histórico preservado.
Checkpoint: [docs/continuidade/checkpoints/0044-bloco60-consolidacao-cumulativa.md](docs/continuidade/checkpoints/0044-bloco60-consolidacao-cumulativa.md), SHA-256 bb710c503a77d602ca4c0d02235ef314056987d2f53f406532d09e005926b77f.
Provas e recibo: target/execucao-b60-corretiva-20260910-0040/physical-verification.json e target/execucao-b60-corretiva-20260910-0040/final/.
Sem novo checkbox ou aceite dos critérios maiores; 67/115, 48 pendentes,
191 rotas, zero AGORA. Fonte sintética e Users SHADOW_UPSERT_ONLY; sem cutover.

## B60 — correção offline e pacote corretivo preparado (10/09/2026)

O bloqueio do manifesto foi reproduzido no JAR original e corrigido: nomes de
classes internas contendo `$` agora são aceitos, mantendo hash, caminho, escopo,
validade e ACL. Teste de regressão passou e continua recusando bytes alterados.
Maven offline/Java17 verify: **1.394 testes, zero falhas/erros, quatro skips**;
cobertura e demais gates de build aprovados. O bundle real corrigido passou na
verificação sem SQL; variantes expirada e inconsistente foram recusadas.

Pacote corretivo: database/proposals/bloco60-correcao/package.json, SHA-256
cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167.
São 224 arquivos e os mesmos 74 casos com IDs novos; parte de V024/SERVICE v19,
sem DDL, mantém limites e prevê compensação até SERVICE v21/Users scopes v4.
A autorização adicional perguntada ainda está pendente. Nenhuma campanha nova,
SQL físico novo ou renovação de orçamento foi executada nesta correção.

A campanha a3d28ade…db57e segue encerrada/NOT_QUALIFIED e compensada. Evidência
física não é substituída pelo PASS offline. QUALIFICACAO_FISICA_LOCAL_USUARIOS
permanece pendente; 67/115=58,26%, 48 pendentes, 191 rotas, zero AGORA.
Checkpoint 0042: docs/continuidade/checkpoints/0042-bloco60-correcao-offline-pacote-corretivo.md.
Manifesto: docs/catalogos/bloco60-correcao/manifesto.json.
Evidências e consolidação: target/b60-correcao-20260910/.

## B60 físico — campanha interrompida e compensada (10/09/2026 UTC)

**PHYSICAL_CAMPAIGN_STOPPED_COMPENSATED / TESTADO_NA_CAMADA**. O usuário
aprovou explicitamente o pacote SHA-256
`a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e`.
Executado em localhost/ETL_SISTEMA_V2_SHADOW sob suporte elevado por UAC normal,
com contas restritas existentes, dentro dos limites e validade originais.

Preflight V023/9.137 linhas/perfil/colisões passou. Upgrade e sufixo V024
qualificados em rollback com catálogo idêntico e preservação conferida;
V024 instalada e perfil temporário aprovado ativado. Isso não é baseline
física nova desde banco vazio. V001–V024 e o pacote aplicado são imutáveis.
O primeiro caso USUARIOS_RUN retornou 20/UNCONFIGURED, esperado 0; zero HTTP,
decisões, consumos, tentativas, selos, staging e publicações no readback.
A matriz parou: um caso executado/falho, zero aprovados, 73 não executados.

Compensação confirmada por PROFILE_RESTORED: SERVICE v19, replay/force=0,
32 grants, quatro scopes de Usuários v2 revogados e duas policies revogadas.
V024, bindings e históricos preservados. Ledger fechado NOT_QUALIFIED:
41 eventos, 19 reservas (17 sqlcmd, uma JVM, instalação), três commits
administrativos confirmados, zero UNKNOWN/escrow/renovação. Processos próprios
encerrados e loopback fechado. A preservação global após a matriz completa
não foi alcançada; preservação até a instalação e perfil pós-compensação passaram.

Diagnóstico offline: o bundle lista RuntimeUsersPhysicalProbe$Fault.class,
mas AdministeredArtifactVerifier rejeita `$` nos paths do manifesto. É uma
incompatibilidade concreta; o log físico não expõe sua causa interna exata.
Próxima preparação: reproduzir/corrigir offline numa revisão futura, preservando
o pacote aprovado. Nova campanha exige estado V024/SERVICE v19, pacote revisável
e aprovação específica; não repetir este OPEN, instalar V024 de novo ou renovar saldo.

[Resultado e sucessão](docs/catalogos/bloco60-fisico/README.md).
Ledger: target/bloco60-local/physical/a3d28adeb17775bc/ledger.jsonl.
Consolidação/diff/recibo: target/execucao-b60-aprovada-20260909-2334/.
QUALIFICACAO_FISICA_LOCAL_USUARIOS NÃO atribuído. Nenhum pai ou checkbox fechado:
67/115 = 58,26%, 48 pendentes, 191 rotas, zero AGORA. V2-022, Q-USR-01,
V2-012a/b/c, bootstrap, sweep, medição por entidade, E2E, release/cutover abertos.
Os registros de ausência de aprovação/execução abaixo são fotografias históricas.

## Correção dos gates globais após B60 — testada localmente

**GLOBAL_GATES_RECONCILED / TESTADO_NA_CAMADA**. A01/A02/A03 corrigidos após
auditoria dos 67 concluídos: lista explícita V001–V024; bindings de schema e
cutover no bootstrap; catálogo de cutover versão corrente/41 triplets.
Três testes novos reproduziram os erros antes da correção. Verify offline:
1393 testes, zero falhas/erros, quatro skips condicionais; Java17/heap512MiB.
Gates globais e sucessão histórica foram conferidos; ver o recibo final próprio.

[Catálogo da correção](docs/catalogos/manutencao-gates-pos-b60/README.md).
Evidências, diff e recibo: target/correcao-gates-pos-b60/. Encerramento condicionado
a final/receipt.json aprovado e íntegro. A auditoria anterior mantém seus FAILs.
Snapshots exatos preservam a cadeia B55–B60. No pacote físico preparado muda
somente o hash do validator B60 revisado; limites/validade não foram renovados.
O pacote continua sem aprovação e sem execução física. Não houve alteração de
migrations, grants, runtime, fonte, credencial, deploy, commit ou push.
67/115,48 pendentes,191 rotas,zero AGORA; nenhum checkbox/bloco funcional novo.
Os registros abaixo permanecem como fotografias históricas integrais.

## Bloco 60 — implementação offline e pacote físico revisável

**B60_IMPLEMENTED_OFFLINE_PHYSICAL_APPROVAL_PENDING / TESTADO_NA_CAMADA**.
A–F adotadas: V024/ADR0041, origem lógica multiprotocolo, recovery/DQ/SQL Users,
autoridade administrada própria, observer GraphQL e controladores físicos.
Verify-03:1390/0/0/5 (cinco skips preexistentes),Java17offline/512MiB; formatter,
lint,arquitetura,cobertura. Quatro comandos offline do JAR passaram. Não houve
SQL físico/preflight, instalação protegida, leitura DPAPI ou fonte real.

[Matriz A–F](docs/catalogos/bloco60-local/README.md),
[pacote físico](database/proposals/bloco60-local/README.md) e
[runbook](docs/runbooks/v2-022-bloco60-usuarios-sql-local.md). 74 requests/49UUIDs,
limites/validade próprios, readback/compensação e duas rotas SQL revertidas do
sufixo sobre V023. Não alegar baseline nova desde banco vazio. Flags force/replay
do principal e seu alcance temporário estão explicitados no pacote.

Seção4 exige aprovação única desse pacote, depois do fechamento independente,
antes de qualquer SQL. Budget físico:0; sem herança/renovação. Aceite máximo
QUALIFICACAO_FISICA_LOCAL_USUARIOS ainda NÃO atribuído; depende da execução real.
67/115,48pendentes,191rotas,zero AGORA; nenhuma mudança de checkbox/aceite pai.
V2-033 preservado; GraphQL transitório SHADOW_UPSERT_ONLY, sem prova de ausência.

B59 conferido antes das mudanças:386 artefatos,1572 arquivos,808 bindings e211
artefatos Java íntegros; recibo36a8b88b7efbcfd52859214cb91c647cea759335be46e684bcad7df5f210ecba.
Sucessão exata com snapshots, sem regravar hashes históricos. Encerramento
offline condicionado ao recibo e verificação final em target/bloco60-local/final.
Os históricos abaixo permanecem íntegros.

## Bloco 59 — integração local de Usuários/GraphQL testada

**B59_LOCAL_CLOSED / INTEGRACAO_LOCAL_USUARIOS_TESTADA** é comprovado somente por
[target/bloco59-local/final/receipt.json](target/bloco59-local/final/receipt.json),
passed=true e hashes íntegros. Sem essa prova, o último delta não está encerrado.
A–F entregam RuntimeUsersRequest, LocalUsuariosRuntime, composição operacional,
auditoria/lease/cancelamento, staging/promoção JDBC tipados, DQ e recuperação
vinculada à ocorrência. Matriz critério → teste → camada → evidência no
[catálogo B59](docs/catalogos/bloco59-local/README.md), decisão no ADR0040.

Verify-03: **1.378 testes, zero falhas, zero erros, cinco skips anteriores**;
Java17 offline/heap512MiB, build próprio, sem clean. Formatter, Checkstyle,
arquitetura e cobertura passaram;808 sourceBindings/211 artefatos. Verify-02
teve erro loopback e permanece falho no histórico; corrida de setup corrigida,
sem mudar timeouts/assertivas. JAR final: help/dry-run exit0, run/status exit20
UNCONFIGURED antes de fonte/SQL. Caminho positivo somente no harness sintético.

Fechamento:18 arquivos existentes alterados,40 adições incluindo manifest,
1514 preservados; snapshots exatos e sucessão sem exceção genérica. Cadeia privada,
dez guards B59, seis guards SQL estáticos, nove guards históricos, scanner com
onze autotestes, UTF-8/sintaxe e reverse --check vinculados ao recibo final.
Diff contra os1532 arquivos iniciais observados; nenhuma reversão aplicada.

SQL de recuperação para Usuários foi preparado em database/preparation/bloco59,
fora das migrations/baseline, **não executado nem compilado**. Qualificação física
exige adoção versionada, alvo/estado, identidade/grants, policy/scope/source_catalog,
rollback/transações/concorrência e autorização própria. Fonte ESL/Q-USR-01 e os
outros gates externos não são demonstrados por JDBC simulado.

Zero API real, SQL físico, .env/credenciais, instalação, serviço/agenda/deploy,
commit/push ou budget. SHADOW_UPSERT_ONLY, sem snapshot/ausência/incremental,
desativação, sweep ou publicação consumer-facing. V2-022 pai e demais pendências
externas permanecem; V2-033 não reabre. 67/115,48pendentes,191rotas,zero AGORA,
sem novo checkbox. As entradas abaixo são fotografias históricas preservadas.

## Bloco 59 — integração local de Usuários ao runtime adotada

**B59_EM_EXECUCAO**. Em 09/09/2026 o usuário adotou integralmente A–F de
`target/preparacao-bloco59/PROMPT-BLOCO-59-DETALHADO.md`, como fatia local de
V2-022/INTEGRACAO_LOCAL_USUARIOS. Número 59 conferido livre; 1.622 vínculos de
hash íntegros, incluindo 1.532 arquivos canônicos e 50 artefatos do recibo anterior.
Inventário/cópias: `target/bloco59-local/initial-inventory.json` e `initial/`.

Critérios: request GraphQL tipado sem alterar requests/fingerprints Data Export;
travessia real guard/parser/streamer/mapper/staging e auditoria antes do selo;
promoção com permits/DQ e recuperação vinculadas à ocorrência; composição
operacional positiva por dependências injetadas, recusa antes de fonte/SQL;
regressão das cinco verticais/B58 e fechamento do último delta com validadores,
diff próprio, reverse --check e recibo. Matriz A–F em
[catálogo B59](docs/catalogos/bloco59-local/README.md); nenhum critério concluído
somente por planejamento. Máximo: INTEGRACAO_LOCAL_USUARIOS_TESTADA.

Somente Java/testes offline sintéticos e preparação local necessária; zero API,
SQL físico, .env, credenciais, instalação, serviço, agenda, deploy/cutover,
commit/push ou orçamento novo. SHADOW_UPSERT_ONLY; sem snapshot, incremental de
Usuários, desativação, relações ou publicação consumer-facing. Não reabre V2-033;
não fecha V2-022 pai, Q-USR-01 ou demais gates externos. 67/115, 48 pendentes,
191 rotas abertas e zero AGORA preservados, sem novos checkboxes.
As entradas anteriores são fotografias históricas, preservadas integralmente.

## Fechamento da sucessão documental posterior ao B58

**POST_B58_DOCS_CLOSED** fica comprovado exclusivamente pelo
[recibo final](target/pos-bloco58-docs/final/receipt.json), passed=true e hashes
íntegros. Ausência ou falha exige concluir os gates e o diff desta manutenção.
Os cinco anexos ESL permanecem registrados individualmente abaixo, incluindo
Data Export na API REST. A cadeia passa a conferir esta revisão e conserva
separadamente as fotografias históricas, sem alterar recibos anteriores.

Escopo: quatro deltas existentes, 11 adições incluindo manifest e snapshots;
1.517 arquivos B58 preservados. Cadeia privada, nove guards atuais/nove B58,
scanner/autoteste, UTF-8 e reverse --check constam do recibo próprio.
Java 1.303/zero falhas/erros/cinco skips é prova histórica B58, sem nova execução.
[Catálogo](docs/catalogos/pos-bloco58/README.md) e
[checkpoint 0028](docs/continuidade/checkpoints/0028-pos-bloco58-fechamento-documental.md).

67/115, 48 pendentes, 191 rotas abertas e zero AGORA; nenhum aceite novo.
Esta conclusão resolve somente o fechamento documental local. Q-COT/Q-COL/Q-USR,
V2-012a, V2-041 e demais qualificações conservam seus oráculos, bindings/scopes,
janela e garantias pendentes. Nenhum Bloco 59, API, SQL, runtime físico,
instalação, credencial, agenda, deploy/cutover, commit/push ou orçamento novo.
As entradas anteriores “em execução” abaixo são fotografias históricas;
o recibo íntegro acima determina o fechamento desta sucessão.

## Sucessão documental posterior ao B58 — em execução

**POST_B58_DOCS_EM_EXECUCAO**. Usuário autorizou concluir a pendência documental
após os cinco anexos ESL. O inventário B58 diverge somente em STATES; as versões
histórica e observada estão preservadas. A recusa B58_HASH_STATES.md foi reproduzida.
Quatro deltas documentais exatos serão reconciliados sem mudar Java ou recibos
históricos. Gates privados, guards e novo recibo ainda pendentes.
[Checkpoint 0027](docs/continuidade/checkpoints/0027-pos-bloco58-sucessao-documental.md).
67/115, 48 pendentes, 191 rotas abertas, zero AGORA. Nenhum bloco funcional novo,
API, SQL, instalação, credencial, campanha, commit/push ou orçamento.

## Documentação ESL enviada pelo usuário — cinco anexos (09/09/2026)

Registrados individualmente os quatro textos enviados inicialmente e o quinto
acrescentado em seguida. São cópias textuais da coleção TMS ESL CLOUD, com
conteúdo sobreposto e diferentes operações expandidas. **Data Export integra
API REST**, conforme a [seção indicada pelo usuário](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5#825bbb56-797b-49c7-bb7f-39cf7b6e2537).
A coleção também possui uma seção API GraphQL.

| Material recebido | Conteúdo Data Export expandido e diferença relevante |
| --- | --- |
| [Anexo 1 — 3bfe6412](C:/Users/suporte/.codex/attachments/3bfe6412-b458-4add-b2f1-5121b9c15203/pasted-text.txt) | Introdução/regras gerais de Data Export, consulta de templates e de sua estrutura. |
| [Anexo 2 — 4714de68](C:/Users/suporte/.codex/attachments/4714de68-e48a-470a-8143-22b8abf72c19/pasted-text.txt) | Cinco operações: templates, estrutura, relatório JSON, solicitação XLSX/FTP e consulta do arquivo gerado. |
| [Anexo 3 — 8d66ad04](C:/Users/suporte/.codex/attachments/8d66ad04-ff69-4577-ba5e-c576822c5d17/pasted-text.txt) | Estrutura de template, relatório JSON, solicitação XLSX/FTP e consulta do arquivo gerado. |
| [Anexo 4 — d6ea8648](C:/Users/suporte/.codex/attachments/d6ea8648-ce24-4c27-94d5-8a64a81fa904/pasted-text.txt) | Solicitação XLSX/FTP e consulta do arquivo gerado; também expande a consulta dos itens de Fatura a Receber na parte financeira. |
| [Anexo 5 — 920c7fd5](C:/Users/suporte/.codex/attachments/920c7fd5-a2f1-4a8a-a9b4-52d35e940917/pasted-text.txt) | Cinco operações de Data Export e consulta dos itens de Fatura a Receber; complemento enviado por último. |

O índice REST dos anexos inclui Agente, Consolidação, Cubadora, Data Export,
Financeiro, Frete, Margem de Frete, Minuta Despacho, Nota Fiscal, Ocorrência,
Ocorrência Cliente, Middleware de recebimento de declaração/NF-e/frete,
Roteirizador, Usuário e Veículos. Entre os trechos efetivamente expandidos estão
Faturas a Receber, comprovantes de entrega por nota fiscal, recebimento de XML
CT-e para redespacho/subcontratação e consulta/cadastro/atualização de veículos.
Os textos também contêm exemplos GraphQL, como CT-es, classificação de carga,
taxas de câmbio, tipos de veículos, tipo de coleta, boleto e centro de custo.

Os links acima apontam aos anexos originais locais; não foram copiados exemplos
de payload, credenciais ou dados de negócio para este registro. Conteúdo presente
somente no índice não equivale a contrato detalhado conferido ou integração usada
pelo extrator. Registro exclusivamente em STATES.md, conforme solicitado,
posterior ao fechamento B58 e sem atualização dos recibos históricos.
Nenhuma chamada de API, implementação ou novo aceite decorre desta anotação.

## Bloco 58 — resultado local A–D

**B58_TESTADO_LOCAL_A_D / TESTADO_NA_CAMADA**. Consumidores test-only ligam os
bytes sintéticos de Coletas e Usuários aos parsers/guards/mappers atuais e ao
staging em memória, com expectativas independentes: 48 casos COL e 42 USR.
35 casos Coletas mostram diferença decisão/contrato antes de staging; essa
recusa não recebe crédito de passagem pelo mapper. Corrigido somente o defeito
reproduzido de calendário em três formatters Coletas; nenhum defeito produtivo
adicional confirmado em Usuários. Perfis históricos e 109 casos B57 preservados.

Verify offline Java 17/heap 512 MiB: **1.303 testes, zero falhas/erros, cinco skips**.
Enforcer, Spotless, Checkstyle e JaCoCo passaram. Skips: três condições symlink,
um recibo V2-050 opt-in e o comando COT desabilitado. Focados A98/B94/C124 são
execuções do B58, com sobreposição; não somar ao verify. Oito gates estáticos,
cadeia privada B58/B57/B55, continuidade/trilha, nove guards atuais e 15 históricos
passaram. Scanner offline: zero findings; autoteste: 11 casos.
[Catálogo e matriz](docs/catalogos/bloco58-local/README.md).
[Checkpoint 0026](docs/continuidade/checkpoints/0026-bloco58-fechamento-local.md).

O fechamento do último delta documental/diff depende exclusivamente de
**target/bloco58-local/final/receipt.json**, passed=true e hashes íntegros,
incluindo reverse --check sem aplicação. Ausência/falha exige concluir essa
etapa. Inventário inicial de 1.488 arquivos preservado; sucessão exata de sete
existentes e 33 adições incluindo o manifest, sem regravar manifest/recibo antigo.

67/115 aceites, 48 pendentes, 191 rotas abertas e zero AGORA; nenhum aceite novo.
Q-COL/Q-USR/Q-COT/Q-LOC/Q-FRE/V2-012a aguardam oráculos representativos autorizados,
bindings/scopes e garantias próprias. V2-012b/c, bootstrap, relações, current/history
completo e paridade SQL não foram qualificados por staging sintético. Artefatos e
papéis que podem fornecê-los constam da matriz. Windows/SQL preservado.
Zero API real, SQL inclusive leitura, runtime físico, instalação, credencial,
agenda, deploy/cutover, commit/push, efeito desconhecido ou consumo/renovação de
orçamento. Launcher COT não executado; input, pasta e tetos B57 preservados.
As entradas abaixo, inclusive B58 em execução/B58 não iniciado e totais Java
anteriores, são fotografias históricas preservadas, sucedidas por este resultado.

## Bloco 58 — Coletas e Usuários testados na camada local

**B58_EM_EXECUCAO**. A fatia local foi adotada expressamente pelo usuário.
Coletas passou em 98 testes focados e Usuários em 94, sem falhas/erros/skips.
São 48 casos novos Coletas e 42 Usuários com expectativas independentes;
relatórios distinguem parser, contrato, mapper, travessia e staging em memória.
Três formatters temporais de Coletas foram corrigidos após reprodução vermelha:
calendário estrito, bruto inválido preservado e fallback válido. Nenhum defeito
produtivo adicional confirmado em Usuários. Oito gates estáticos passaram.
Regressão C e fechamento D em execução; verify global e recibo final pendentes.
[Checkpoint 0023](docs/continuidade/checkpoints/0023-bloco58-usuarios-local.md).

67/115, 48 pendentes,191 rotas abertas, zero aceite novo ou AGORA.
Q-COL-01/Q-USR-01 e V2-012a/b/c continuam dependentes de oráculo representativo,
bindings/scopes e garantias específicas. Staging sintético não prova SQL,
completude, snapshot, relações ou paridade. Windows/SQL preservado.
Zero API real, SQL, campanha física, instalação, credencial, agenda, cutover,
commit/push, consumo/renovação de orçamento ou efeito desconhecido.
As afirmações anteriores de Bloco58 não iniciado e resultados Java anteriores
são fotografias históricas preservadas, sucedidas por esta adoção local.

## Complemento Bloco 57 — F1/F2/F3 tratados no escopo local

**B57_COMPLEMENTO_TESTADO_LOCAL**. As três pendências da auditoria receberam
implementação e prova local: caminhos LOC distinguidos e protegidos; executor
futuro COT 6906 com comando/preflight e limites; STATES/trilha/validadores
reconciliados por sucessão exata, com as duas versões STATES preservadas.

Verify offline atual: **1.170 testes, zero falhas/erros e cinco skips condicionais**.
Java17/heap512; Enforcer, Spotless, Checkstyle e JaCoCo passaram. Cinco skips:
três condições de symlink, um recibo V2-050 opt-in e o comando de fonte real
explicitamente desabilitado. Fonte não foi executada pela suíte.
O launcher passou em preflight sintético válido e recusou o input público
pendente; zero reserva ou credencial. Os 109 casos COT/LOC/FRE continuam sintéticos.

Onze checks de contratos/verticais passaram; B55 privado completo, continuidade
e trilha passaram. Nove guards atuais, seis históricos e scanner/autoteste11
passaram. Logs/hashes no [catálogo do complemento](docs/catalogos/bloco57-complemento/README.md)
e [checkpoint 0020](docs/continuidade/checkpoints/0020-bloco57-complemento-fechamento.md).
O fechamento do último delta documental/diff é comprovado somente por
target/bloco57-complemento/final/receipt.json, passed=true e hashes íntegros;
ausência/falha desse recibo exige concluir essa etapa, sem presumir PASS.

67/115, 48 pendentes e 191 rotas abertas; nenhum aceite adicional.
Q-COT-01/Q-LOC-01/Q-FRE-01 e V2-012a/b/c permanecem abertos. Ainda faltam
host/scopes, janela representativa, oráculo e garantias/adoção específica da fonte.
P08–P11/V2-029–032, FAT-02 e V2-041 mantêm suas dependências externas.
Nenhuma API real, SQL/DDL/DML, campanha física, instalação, credencial, agenda,
produção, commit/push, efeito desconhecido ou renovação/consumo de orçamento.
Windows/SQL permanece a direção técnica. Nenhum Bloco58 iniciado.
As entradas abaixo são fotografias históricas, inclusive estados intermediários
“em execução”; o resultado deste complemento sucede a afirmação ampla do B57 inicial.

B57_COMPLEMENTO_EM_EXECUCAO — F1/F3 testados: 31 testes focados passaram, incluindo executor COT com transporte injetado e caminhos LOC. F2: primeiro gate de sucessão passou; cadeia privada, guards e verify global pendentes. [Checkpoint 0019](docs/continuidade/checkpoints/0019-bloco57-complemento-executor.md). Zero fonte real ou aceite novo.

## Complemento Bloco 57 — fechamento da auditoria em execução

**B57_COMPLEMENTO_EM_EXECUCAO**. Usuário autorizou fechar as pendências F1/F2/F3.
F1 passou em 14 testes focados: comparação explícita das entradas String e
JsonNode de Localização, mais travessia do contrato/streamer/caso de uso local.
Números JSON são recusados na via JsonNode por falta de léxico e, no contrato
sintético B55, antes do staging. Nenhuma proteção foi removida ou regra alterada.
F3/executor futuro COT e F2/sucessão documental estão em execução, sem PASS presumido.
[Checkpoint 0018](docs/continuidade/checkpoints/0018-bloco57-complemento-localizacao.md).
STATES observado após B57 e fotografia entregue preservados separadamente; o
resultado histórico abaixo foi recuperado por sucessão explícita. Nenhum aceite,
API real, SQL, instalação, campanha, credencial, commit/push ou orçamento novo.
67/115 e 48 pendentes. A afirmação histórica “A–D tratadas” recebeu as ressalvas
da auditoria; a conclusão atual depende do recibo final deste complemento.

# Estado Atual — ETL Data Export V2

Atualizado em: 2026-09-09 — Bloco 57 testado localmente; gates reais pendentes

## Bloco 57 — resultado local A–D

**B57_CARACTERIZACAO_LOCAL / TESTADO_NA_CAMADA**. As quatro frentes locais foram
tratadas: consumidor test-only de envelopes → parser/mapper atual → expectativas
independentes, com 39 casos Cotações, 27 Localização e 43 Fretes; investigação
documental dirigida e pacote Cotações futuro bloqueado. São 109 casos sintéticos,
58 registros válidos e 51 recusas esperadas, todos compatíveis localmente.
Corrigidas a precisão/escala decimal no parser compartilhado e datas impossíveis
nos formatters com espaço de Cotações/Fretes. Nenhuma regra LOC foi alterada.

O verify offline final em Java 17/heap 512 MiB passou: **1.150 testes, zero falhas,
zero erros e quatro skips condicionais existentes**; Enforcer, Spotless,
Checkstyle e JaCoCo passaram. Saída isolada em target/bloco57-local/build-final;
log e relatórios por entidade vinculados em target/bloco57-local/java-result.json.
Não houve clean, perfil externo ou substituição de instalação protegida.

Os validators de contratos/identidades/verticais, B55 privado, continuidade,
trilha e seis contraprovas de sucessão passaram durante a construção. Q-FND-02
passou após realocar somente arquivos novos para fora de suas raízes congeladas.
O scanner offline e seus 11 casos de autoteste passaram, sem achados. O fechamento
da revisão documental final, com hashes, diff próprio e recuperação, é comprovado
somente pelo recibo target/bloco57-local/final/receipt.json e seus logs vinculados;
ausência ou falha desse recibo significa fechamento pendente, nunca PASS presumido.

[Catálogo B57](docs/catalogos/bloco57-local/README.md) e
[checkpoint 0017](docs/continuidade/checkpoints/0017-bloco57-fechamento-local.md).
Os dez checks finais estáticos passaram, com comandos/exits/hashes em
target/bloco57-local/final-checks-fe045bb3a1134e8fab2e56d57eb12118/results.json.
O recibo final também confere a sincronização deste último delta documental.
67/115 (58,3%), 48 pendentes e zero aceite novo. Q-COT-01/Q-LOC-01/Q-FRE-01 e
V2-012a/b/c continuam abertos: falta fonte-oráculo representativa e garantias
específicas. P08–P11 não receberam evidência nova pertinente; V2-029–032, FAT-02
e V2-041 mantêm suas dependências. Host lógico, scopes, janela e adoção da futura
consulta permanecem ausentes no pacote. Windows/SQL continua a direção técnica.
Zero API real, SQL/DDL/DML, campanha física, instalação, credencial, agenda,
produção, commit/push, orçamento consumido ou efeito externo desconhecido.
Os registros intermediários abaixo são fotografias preservadas, sucedidas por
este resultado; seus estados "em execução" não são a situação atual.


B57 validação intermediária: primeiro verify teve falha de isolamento de pastas históricas e cinco recusas de heap; sem defeito funcional adicional. Apenas arquivos novos foram realocados e processo limitado a 512 MiB. Q-FND-02, B55 privado, continuidade e seis guards passaram. [Checkpoint 0015](docs/continuidade/checkpoints/0015-bloco57-regressao-e-isolamento.md); verify atual ainda em execução, sem PASS presumido.

B57 atualização C/D: 43 casos FRE passaram, total de 109 casos COT/LOC/FRE. Corrigido formatter de data inválida em Fretes sob FRE-02/FRE-07; 24 testes focados verdes. Investigação dirigida e pacote futuro bloqueado entregues; P08–P11 sem evidência nova pertinente. [Checkpoint 0014](docs/continuidade/checkpoints/0014-bloco57-fretes-e-pacote.md). Verify/validadores finais em execução, zero aceite novo.

B57 atualização B: Localização integrada sem alterar seu mapper produtivo; 27 casos LOC e 39 COT passaram em 20 testes focados, incluindo oito guards. [Checkpoint 0013](docs/continuidade/checkpoints/0013-bloco57-localizacao.md). C/D e verify final pendentes; aceites inalterados.

## Bloco 57 — caracterização ligada ao código atual

**B57_CARACTERIZACAO_LOCAL**. Prompt preparado adotado pelo usuário. A/Cotações
TESTADO_NA_CAMADA: consumidor test-only de envelopes limitados, parser/mapper reais,
39 casos sintéticos com expectativas independentes e relatórios sanitizados.
Corrigidos perda de precisão/escala decimal no parser Data Export e aceite de
data impossível no formato com espaço em Cotações. B/Localização, C/Fretes e
D/documentos dirigidos continuam em execução autorizada. Gates reais permanecem
abertos; zero aceite novo, 67/115 e 48 pendentes. Testes focados: 34 passaram,
zero falhas/erros/skips; logs RED/GREEN em target/bloco57-local. Suíte completa
atual ainda pendente. [Checkpoint 0012](docs/continuidade/checkpoints/0012-bloco57-cotacoes.md)
e [catálogo B57](docs/catalogos/bloco57-local/README.md). Fotografias abaixo preservadas.

## Documentação ESL indicada pelo owner

Fonte principal para consultar os endpoints e contratos gerais ESL:
[TMS ESL CLOUD — documentação Postman](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5#1843cd33-1848-438c-875b-6262107c5e94).
O usuário forneceu esta documentação como referência completa da ESL e autorizou
sua consulta para investigar as lacunas. Consultá-la antes de classificar um
endpoint ou contrato ESL como desconhecido. A cobertura efetiva de cada requisito
deve ser conferida no conteúdo; a indicação não comprova automaticamente identidade,
cardinalidade, estabilidade ou completude de um template personalizado.
Registro e consulta documental não alteram aceites nem autorizam executar os
endpoints publicados. As fotografias históricas abaixo permanecem preservadas.

**ESL_DOCUMENTACAO_POSTMAN_REGISTRADA**. Coleção pública consultada: 191
requisições documentadas; o manual GraphQL referenciado contém 180 anchors de
operações. [Índices, evidências e limites](docs/catalogos/esl-documentacao/README.md)
e [checkpoint 0011](docs/continuidade/checkpoints/0011-documentacao-esl-postman.md).
As rotas de faturas/parcelas/itens e ocorrências fornecem caminhos concretos para
investigar P08–P11, mas não provam seu crosswalk com os templates personalizados.
As diferenças de transporte/paginação e restrições históricas ficam explícitas
para a próxima validação. Nenhuma chamada de dados ou mudança Java/SQL foi feita.
Progresso preservado: 67/115 (58,3%), 48 pendentes, zero aceite adicional.

## Bloco 56 — decisão Raster delegada e contrato local

**B56_RASTER_MANTIDO_CONTRATO_LOCAL_IDENTIDADE_PENDENTE**. O owner respondeu
“vc que decide, preciso continuar” à decisão manter/retirar Raster. Decisão:
**MANTER**, preservando a responsabilidade de viagens/paradas e Transit Time SM
declarada no legado. A delegação cobre decisão e trabalho local; não nomeia
aprovadores produtivos nem amplia os limites de fonte, SQL ou runtime.

V2-034a e V2-025c são os dois aceites originais concluídos neste escopo. O
[catálogo Raster](docs/catalogos/raster-contrato-local/README.md) contém decisão,
18 aspectos de contrato, 51 campos/aliases de DTO, proveniência de 14 arquivos
locais, oito envelopes/erros, sete limites e um contraexemplo de reordenação.
O contrato é TRANSITIONAL: tipos Java não provam wire types, amostra não prova
identidade e lotes curtos/vazios não provam completude. Doze contraprovas de
contrato passaram; logs iniciais em `target/bloco56-raster/contract-01.log` e
`contract-guards-01.log`. Não se alega parser ou vertical Raster implementados.

V2-009c permanece bloqueada por escopo/tipo/estabilidade da raiz e identidade
da parada; V2-034b não foi iniciada. P08–P11 e V2-029–032 permanecem bloqueadas.
Nenhuma consulta anterior foi repetida. Windows/SQL permanece a direção técnica.
Zero rede, SQL, runtime, grant, instalação, rotação, orçamento consumido ou
resultado desconhecido nesta continuação local. Os 1.138 testes Java são B55.

Progresso atual: **67/115 = 58,3%**, 48 pendentes, 191 rotas abertas, zero AGORA.
Fase 1: **39/52 = 75,0%**. Fase 3: **6/12 = 50,0%**. Contagem dos critérios
originais; decisão/contrato não são aceites de implementação da vertical.
Sucessão exata e recuperação no manifesto do catálogo; 1.396 arquivos anteriores
inventariados/copiados em `target/bloco56-raster/initial`, com oito snapshots
documentais/de validação em `docs/continuidade/historico/bloco56-continuacao`.
Ver [checkpoint 0009](docs/continuidade/checkpoints/0009-bloco56-raster-decisao-e-contrato-local.md)
e [checkpoint 0010](docs/continuidade/checkpoints/0010-bloco56-raster-validacao-e-retomada.md).
O recibo final de validação/diff é `target/bloco56-raster/final-verification.json`;
ausência desse recibo significa fechamento offline pendente, não PASS presumido.
As seções seguintes conservam as fotografias históricas e seus contadores.

## Bloco 56 — continuação com evidência remota nova

**B56_INVESTIGACAO_REMOTA_REGISTRADA_IDENTIDADES_BLOQUEADAS**. O usuário pediu
continuidade consultando APIs/documentação e reafirmou o uso dos `.env` depois
da pergunta sobre rotação. Aplicou-se a instrução posterior à investigação
limitada de leitura. V2-041 permanece pendente: acesso funcional não comprova
substituição/invalidação de credenciais. Nenhuma credencial foi alterada.

Onze consultas passaram com HTTP 200/curl 0: quatro `/info`, quatro páginas de
dois registros (04/09/2026) e três introspecções GraphQL, sem registros GraphQL.
Preservados somente nomes técnicos, tipos, contagens e documentação sanitizada.
Sem payload, URL da conta, token, cursor ou ID de negócio. As reservas/resultados
próprios não consomem nem transferem saldo B53/B54/B55. Zero SQL, runtime,
instalação, grants, agenda, escrita remota ou resultado desconhecido.

Inventário retornou mapping ARRAY de STRING (três itens nas duas linhas);
Faturas retornou os dois mappings como ARRAY de STRING (quatro e dois itens,
com um array de pedidos vazio). Contas a Pagar não expôs ID raiz nos paths
testados; Sinistros trouxe sequence INTEGER e número de nota STRING.
O schema declara DebitBase/CheckInOrder com IDs distintos de sequenceCode,
FreightBase.accountingCreditId e CreditBase.id; não declara o crosswalk desses
IDs com os templates investigados. DebitBase.installments aparece como lista
de CreditInstallment: inconsistência a esclarecer com o fornecedor. Não foi
encontrado InsuranceClaim nem consulta raiz de sinistros/inventário nesse schema.
Isso é observação do schema retornado, não inexistência universal.

[Pacote revisto e lacunas exatas](docs/catalogos/bloco56-continuacao/README.md),
[checkpoint 0008](docs/continuidade/checkpoints/0008-bloco56-validacao-da-continuacao.md)
e [evidências sanitizadas](docs/catalogos/bloco56-continuacao/evidencias/).
P08–P11 receberam evidência parcial nova, sem garantia de estabilidade, escopo,
identidade dos filhos ou decisão fiscal FAT-02. V2-029–032 continuam bloqueadas
pelas identidades; não se implementou domínio/runtime sobre hipótese.

As três sondas locais incluem testes offline e recusa de repetição pelo ledger.
Resultados finais dos validadores e diff próprio ficam em
`target/bloco56-continuacao/final-verification.json` e `final/receipt.json`.
Java não mudou; os 1.138 testes são históricos. Manifests, dados, ledgers,
migrations e aceites anteriores preservados por sucessão documental exata.
Progresso inalterado: **65/115 (56,5%), 50 pendentes, 193 rotas abertas, zero AGORA**.
As seções seguintes são fotografias históricas, inclusive seus contadores HTTP zero.

## Bloco 56 — preparação local entregue; identidades ainda bloqueadas

**B56_PREPARACAO_LOCAL_CONCLUIDA_IDENTIDADES_BLOQUEADAS**. A instrução inicial do
usuário adotou preparação, investigação estática permitida e implementação/testes
locais somente com dependências comprovadas. A preparação elegível foi entregue;
nenhuma das quatro identidades recebeu evidência nova suficiente para liberar
V2-029–032. Não houve implementação de domínio, mapper, migration ou runtime novo.
Windows/SQL permanece a direção técnica.

[Pacote B56](docs/catalogos/bloco56/README.md), [triagem A–H](docs/catalogos/bloco56/triagem.json),
[matriz dos critérios originais](docs/catalogos/bloco56/matriz-criterios.csv) e
[checkpoint 0004](docs/continuidade/checkpoints/0004-bloco56-fechamento-local.md).
São 51 referências e 14 âncoras V1 intactas, sem garantia nova do fornecedor ou
observação representativa nova. A matriz tem 26 linhas de rastreabilidade; não
são checkboxes, testes executados ou aceites. A/P09, B/P10, C/P08 e D/P11 mantêm
os resultados dos manifests originais; E/F/G/H dependem dessas identidades.

Quatro validators de identidade e quatro de contrato passaram offline. A matriz
V2-017a passou e foi reproduzida sem diferença: 401 artefatos, 2.437 campos,
75 regras e 16 classes. O pacote B56 passou no preflight e em 12 contraprovas;
UTF-8, sintaxe PowerShell, diff e scanner de segredos passaram, sem achados.
B55 integral passou inicialmente em leitura de provas privadas. O inventário
próprio preserva 1.338 arquivos iniciais e 27.823 arquivos privados B53/B54/B55.
A verificação da sucessão final e seus logs/exits estão em
`target/bloco56/final-verification.json`; diff e receipt em `target/bloco56/final/`.

STATES, trilha, RETOMADA e dois validators de continuidade têm sucessão exata com
snapshots anteriores preservados. Manifests, evidências, ledgers, Java, baseline
e V001–V023 anteriores não foram reescritos. 1.138 testes Java continuam sendo
prova histórica B55, sem nova suíte Java nesta preparação documental.

Uma solicitação consolidada pediu material já obtido e autorizado, com paths,
origem/versão e responsável. Sem resposta até o checkpoint, os quatro pacotes
registram o requisito/input exato faltante. Sem outra implementação independente
elegível, o trabalho local termina nesse limite; não se declara B56 funcional
integralmente concluído. Nenhuma campanha, SQL/HTTP, reserva B56, instalação,
concessão, transferência de saldo, renovação ou efeito físico desconhecido.

Contagem preservada: **65/115 (56,5%), 50 pendentes, 193 rotas abertas, zero AGORA**.
A manutenção pós-B55 abaixo é fotografia histórica: sua proposta foi sucedida
pela adoção local atual, sem reescrever o manifesto ou a autorização anterior.
## Continuidade dos agentes — manutenção documental após B55

**CONTINUIDADE_DOCUMENTADA_B56_NAO_INICIADO**. A pedido do owner, foram preparados
o [protocolo de continuidade](docs/runbooks/continuidade-agentes.md), o
[ponto de retomada](docs/continuidade/RETOMADA.md), um modelo de checkpoint e a
[proposta condicionada B56](docs/runbooks/prompt-bloco-56-identidades-e-verticais-condicionais.md).
O pedido de documentar e a disposição para trabalhar por horas não adotam novo
escopo físico, não transferem orçamento e não resolvem as quatro identidades em hold.
Propostas, resultados observados, evidências e aceites ficam separados; retomada
após compressão começa pelos arquivos, sem repetir efeitos de resultado incerto.

Manutenção documental e de validação, sem novo bloco funcional ou checkbox.
Contagens preservadas: **115 itens, 65 concluídos, 50 pendentes; 193 rotas abertas,
zero AGORA**. O manifesto de continuidade identifica os deltas documentais exatos
contra REVIEW_05; manifests, receipts, ledgers e artefatos B53/B54/B55 permanecem
imutáveis. A proposta B56 exige evidência nova antes de reabrir P08/P09/P10/P11.
O fechamento B55 abaixo continua sendo a fotografia de sua execução.

## Bloco 55 — cinco verticais operacionais em laboratório

Estado **LOCAL_A_J_COMPLETE**, com as dez frentes comprovadas no laboratório. A adoção do
owner cobre a seção 3 e o delta DQ posterior explicitamente autorizado. O JAR
oficial protegido v4 publicou Coletas, Fretes, Manifestos, Cotações e Localização.
As três novas verticais usam BACKFILL sintético, adapters tipados, redução SQL,
DQ, recibo e recuperação durável. A fonte permanece somente em loopback.

V022/V023 foram qualificadas em rollback, aplicadas e verificadas em nova conexão.
Catálogo `ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae`.
Perfil exato: 32 grants, 16 scopes, SERVICE v17/OPERATOR v1, scope original 1 v10
com demais originais v1 e validade `2026-10-07T22:34:30.615Z`, sem renovação.
A correção DQ preservou três políticas inválidas revogadas e acrescentou as três
revisões autorizadas; somente três estão ativas. Uma release tarifária, duas linhas.

`B55_SMOKE_03` passou nas cinco verticais, incluindo STATUS SERVICE/OPERATOR,
repetição sem HTTP e negativas de escrita/modo. `B55_MATRIX_01` passou em 34 casos
com fonte parcial, cancelamento, queda de JVM própria antes/depois da publicação,
retomada, lease expirada sem tomada, referência/material adulterados e conflito
NULL/VALUE de Manifestos. Os fracassos e falsos PASS anteriores permanecem excluídos.

`B55_TIME_01` comprovou seis janelas das três novas verticais, fevereiro bissexto,
execução fora de ordem sem salto, recuperação e alterações recusadas. Os três
falsos negativos do guard textual PERSIST foram reconciliados sem editar as
provas, em `target/bloco55/temporal-reconciliation.json`. Temporal Coletas/Fretes
reutiliza B54, com regressão física das duas no B55 e dependência no lote manual.
`B55_COMPARE_02` passou nos 30 casos SQL de comparação independente dos snapshots
publicados. Cinco pipelines medidos nas três escalas passaram; nove consumers
SQL tiveram planos estimados limitados inspecionados em `B55_PLANS_05`.

Lote manual: `B55_MANUAL_STOP` publicou as primeiras três ocorrências;
`B55_MANUAL_RESUME` confirmou essas três sem HTTP e publicou as 17 restantes.
O manifesto de 20 requests não foi reescrito. Oito mutantes de validação do lote
foram recusados antes de efeitos. `B55_MANUAL_OPERATOR` consultou as cinco sob
OPERATOR, sem HTTP. `B55_MANUAL_DEPENDENCY` recusou Coletas/Fretes e publicou as
três independentes. A chamada complementar executou após o pedido de prosseguir.
`B55_SQL_01` comprovou recusas SQL diretas e revogação tarifária em rollback.
Quatro expectativas incorretas de exit 30 foram reconciliadas com exit 40,
INCONSISTENT, zero HTTP e vínculos originais intactos no SQL independente;
`sql-fence-reconciliation.json` preserva os resultados originais sem reescrita.

Java 17 offline: **1.138 testes, zero falhas/erros, quatro skips preexistentes**;
Enforcer, Spotless, Checkstyle, arquitetura e cobertura passaram. As 584 entradas
originais do JAR qualificado conferem com a revisão v4 exercitada fisicamente.
Snapshot SQL `B55_READ_03`: 9.137 linhas; B55 com 56 tentativas, 42 publicações,
142 páginas, 96 entradas auditadas e 114 linhas de projeção. Zero sessão restrita
ou transação do observador. B53 conserva 95/75/8; B54 conserva 45/30/70/74.
Ledger próprio: **259 reservas, 11 campanhas encerradas, três lotes de 128**.
O terceiro lote foi declarado como margem corretiva da consulta final de collation;
125 unidades permanecem sem uso. Foram 208 HTTP e 149 invocações JAR, incluindo
falhas e PERSIST temporal; os totais não são contagem de testes aprovados.
Fechamento offline: inventário/diff B55 próprio, UTF-8, sintaxe PowerShell, secret
scan sem achados e contraprovas B55/B54 integram os validadores finais. O aceite
A–J exige todas as provas, inclusive as duas reconciliações de falsos negativos.
Os ledgers B53/B54, as 18 unidades restantes B54 e V001–V021 são preservados.

**B55_CANONICAL_REVIEW_WINDOWS_SQL_LOCAL:** a revisão dos critérios originais
fecha V2-042b, V2-042c, V2-022b e V2-042 para o mecanismo Windows/SQL adotado,
com os inputs reais existentes, capabilities/consumo/auditoria e JAR comprovados.
Fonte real não foi acrescentada como requisito desses três subgates. A estratégia
já adotada usa UUID aleatório na auditoria e SID somente no cadastro protegido;
a administração tem autoridade sobre o laboratório, sem ratificação produtiva.
V2-042 foi concluído após a conferência física dos novos consumers; V2-022 pai
conserva seus consumidores/políticas operacionais pendentes. O único subaceite
B55 de integração A–J foi fechado. Não há frente autorizada restante neste bloco.
Contagem corrente: **115 checkboxes, 65 concluídos, 50 pendentes; 193 rotas abertas**.

A matriz A–J, os critérios e a reprodução estão no
[runbook B55](docs/runbooks/v2-022-bloco55-cinco-verticais-local.md).
As seções anteriores à adoção, a seguir, são fotografias históricas preservadas.

## Próximo pacote preparado — proposta de Bloco 55

**Revisão ampliada a pedido do owner, ainda PREPARED_NOT_EXECUTED:** o pacote
passou de seis para dez frentes A–J. Além da integração das três verticais,
inclui BACKFILL por períodos, comparação SQL de saídas sintéticas reais do
laboratório com oráculo independente, medição dos cinco pipelines e execução
manual de lotes retomáveis de até 20 requests. O teto proposto é 384 unidades
em três lotes de 128, até 12 campanhas; limites por operação e concorrência
permanecem. Não houve adoção ou execução física por esta revisão documental.

A pedido do owner, foi preparado o [prompt integrado de runtime das cinco
verticais](docs/runbooks/prompt-bloco-55-runtime-cinco-verticais-astra.md).
Lacuna concreta conferida no código: o motor oficial seleciona somente
Coletas/Fretes; Manifestos, Cotações e Localização possuem bases shadow próprias,
mas ainda precisam de composição, adapters/consumidores e recuperação pelo JAR.
O pacote propõe integrar essas três, preservando os contratos e a regressão B54.

Estado **PREPARED_NOT_EXECUTED**. A mensagem de abertura contém a adoção explícita
do novo escopo local: três workloads BACKFILL, até sete grants SERVICE/seis scopes
novos, referência tarifária sintética delimitada, revisão protegida e até 384
unidades em ledger B55 separado. A ampliação F–I usa os mesmos direitos delimitados,
sem acrescer grants ou scopes ao pacote anterior. Nenhuma dessas concessões foi
aplicada nesta preparação.
O pacote exige conferir permissões exatas e preparar aplicação/verificação/
recuperação antes de qualquer efeito; não renova contas ou vigências.

Também exige revisar os aceites pela redação original, registrar implementações
comprovadas no escopo correto e manter somente impedimentos externos concretos.
Não fecha caixas nesta preparação: 114/60/54, 196 rotas abertas e zero AGORA
preservados. B53/B54, código Java, banco, artefatos e ledgers não foram alterados
para preparar a proposta; não há nova campanha ou sucessor operacional ativado.

A revisão confrontou os contratos e as implementações temporal, comparação e
medição existentes. Contas a Pagar, Faturas, Inventário e Sinistros permanecem
fora pelas lacunas de identidade/grão/relação documentadas; Usuários GraphQL
exige outro contrato operacional. Relações, fatos, views, fonte real e agenda
conservam seus requisitos. A revisão original B55 foi preservada em
`target/bloco55-expansion-review-20260908`; a ampliação não promete percentual.

## Bloco 54 — laboratório A–G concluído em 08/09/2026

**LOCAL_A_G_COMPLETE_NOMINAL_GATES_PENDING**. Construção local concluída e
qualificada: JAR oficial com Coletas/Fretes, autorização Windows/SQL, proteção do
artefato/TLS, recuperação/cancelamento, DQ/alertas e planejamento temporal integrado.
Suíte final v7: **1098 testes, zero falhas/erros, quatro skips preexistentes**;
estilo, arquitetura e cobertura aprovados. Diagnóstico, preview e RUN/STATUS manual
passaram. Relatório atual: [conclusão A–G](docs/runbooks/v2-022-bloco54-conclusao-local.md).

O owner autorizou as rodadas necessárias. Dois lotes de 96 unidades ampliaram o
teto B54 para 320: **302 reservas, 30 campanhas encerradas, 18 unidades restantes**,
sem devolução. Foram 136 invocações protegidas dos JARs, incluindo falhas, e 127 HTTP
somente em loopback. Test-classpath de autorização: 39 casos físicos, identificados
separadamente das travessias oficiais. Não há campanha ou agenda ativa.

Estado físico final: V001–V021, **25 grants, 6.723 linhas**, SERVICE mapping v17,
OPERATOR v1; oito scopes originais e dois REPLAY revogados. Scope 1 v10 e demais
sete v1; vigência original 2026-10-07T22:34:30.615Z, sem renovação. Catálogo
7a86e28ea68cecd8ba993bbea8df5c677e0848f27aa7b0412402165071736408.
B54: 45 tentativas, 30 publicações, 70 páginas e 74 entradas. B53 mantém ledger112,
95 tentativas, 75 publicações e oito EXTRACTING; zero sessões restritas/transações
próprias pendentes no pós-teste. V021 teve upgrade/cauda pendente em rollback e
aplicação verificada, sem novos grants. Dados sintéticos e falhas foram preservados.

As matrizes de calendário/limites e a retomada passaram. Os negativos finais
comprovaram DQ e sink obrigatório; os falsos PASS do antigo V7_SINKS_03 estão
expressamente excluídos por colisão com lease anterior. Evidências privadas em
`target/bloco54/completion-authorized-20260908`, com hashes no manifest atual.
Histórico da sexta campanha preservado em `runtime-bloco54-historical-sixth.json`.

Nenhum aceite nominal/produtivo foi inferido: **114 checkboxes, 60 concluídos,
54 pendentes; 196 rotas abertas e zero AGORA**. G06/G07/G08 conservam dependências
nominais, com sua implementação local comprovada. A comparação futura tem seis
provas sintéticas e oito inputs reais pendentes. Fonte real/V2-041, políticas e
oráculos de negócio, fatos/sweep, retenção, release e cutover mantêm gates próprios.
Banco/contas/TLS locais e testes independentes A–G não são impedimentos atuais.
As seções históricas abaixo não alteram a autorização nem os resultados atuais.

Fechamento dos validadores: integrado com evidência privada, progressivo, pacote
Windows e trilha PASS; seis contraprovas PASS; scanner 11/11 e 1.229 candidatos
sem achados. UTF-8 sem BOM, PowerShell, hashes V001–V021 e diff check PASS. Os
1.199 arquivos iniciais continuam presentes; diff próprio de 67 arquivos em
`target/bloco54/completion-authorized-20260908/continuation-review.patch`.

## Fotografia histórica — checkpoint intermediário da continuação

O owner autorizou executar as rodadas locais necessárias para concluir a construção.
Essa autorização posterior substitui os limites históricos de quantidade de campanhas
e permite novas reservas declaradas, mantendo alvo, contas, direitos, preservação e
limites por operação. O lote adicional 01 tem 96 unidades: teto cumulativo 224,
sem devolver reservas anteriores. Manifest/hash e ledger append-only estão em
`target/bloco54/completion-authorized-20260908`. Não há nova escolha de banco/contas.

Checkpoint intermediário, ainda **PARTIAL_NOT_ACCEPTED**: 143 reservas B54,
11 campanhas encerradas, V001–V020 preservadas, 25 grants, SERVICE mapping v17,
OPERATOR v1; oito scopes originais ativos e dois REPLAY revogados. Validade original
mantida. Catálogo 6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a;
última leitura exata: 5.676 linhas. B53 mantém ledger 112 e dados anteriores.

A sétima campanha confirmou REPLAY/FORCE_RUN, repetição sem extração, recusa do
operador, fronteira original intacta e alerta SQL de drift. Duas janelas de 2024
ficaram PROMOTED: a fixture antiga usava timestamp onde o mapper espera data civil,
deixando frescor desconhecido; o kernel recusou conteúdo divergente, erro 51428.
O diagnóstico em rollback preservou os dados. Uma consulta de contagem aproximada
incluiu objetos internos e acusou diferença; a leitura exata seguinte confirmou
5.548 linhas, schema intacto e zero transações, sem mudança confirmada nessa sonda.

A fixture v2 é separada e fornece data civil. Três novas janelas de Coletas de
2028, incluindo 29/02, publicaram pelo JAR v4; a repetição não extraiu. A publicação
da segunda janela ocorreu antes de persistir a primeira, e o reconciliador recusou
a lacuna: publicação confirmada com exit 30, sem PASS integral desse caso. A
sequência seguinte fechou a fronteira do plano até 02/03. STATUS temporal do
OPERATOR revelou chamada desnecessária ao reconciliador, cujo grant ele não tem.

Revisão v5 em qualificação: TLS validado antes da authority; STATUS restrito à
ocorrência; exportação offline de uma janela explícita de Fretes com seu
predecessor; verificação do artefato/ACL/hash no JAR; cancelamento da própria
invocação por entrada padrão opt-in, após autorização. Nenhum grant adicional.
Não declarar v5 aprovada antes dos novos testes completos e físicos. O baseline
completo aprovado continua v4: 1065 testes, quatro skips preexistentes.

As seções abaixo são fotografias históricas. Suas afirmações de campanha não
autorizada ou saldo 128 não descrevem a autorização atual. A sincronização final
de manifests/trilha/validators ocorrerá após a qualificação; nenhum aceite novo
foi marcado. Continuam pendentes as travessias físicas finais de artefato,
cancelamento/queda, Fretes temporal, matriz de períodos/limites e operação manual.

## Fotografia histórica — sexta campanha do Bloco 54 em 08/09/2026

Estado: **PARTIAL_NOT_ACCEPTED**. O owner autorizou continuar no saldo existente.
Foram concluídas a implementação da execução de uma janela temporal explícita,
o alerta obrigatório de contrato recusado e as provas restantes de revogação.
A revisão v4 passou em **1065 testes Java 17 offline, zero falhas/erros e quatro
skips preexistentes**; estilo, arquitetura e cobertura aprovados. O JAR protegido
v4 exportou três requests pelo `plan` offline e passou no diagnóstico de leitura.
Não houve execução operacional física dessa revisão nem extração temporal.

**Prova física nova:** seis casos de revogação/versão/policy recusaram o consumo
antigo, com decisão SQL anterior e zero consumo posterior. Dois processos Java
separados provaram consumo confirmado, encerramento antes do despacho de negócio
e nova autorização/consumo da mesma ocorrência. São oito casos novos em
**test-classpath**, além dos 31 anteriores; não são prova de queda do pipeline do
JAR oficial. As versões cresceram e todas as compensações de direitos conferiram.

**OPS02 e correção:** o ensaio preservou SERVICE revogado. A recuperação em outro
PowerShell recusou a atualização porque converteu a validade UTC sem sufixo como
horário local, deslocando-a três horas. A campanha encerrou. A correção usa parse
UTC explícito; cinco regressões de cultura/formato passaram. A compensação da
reserva 109 foi preparada com hash integral, locks e equivalência funcional,
confirmada no SQL e em nova conexão. SERVICE ficou v15; o scope original 1, v10;
OPERATOR e demais sete scopes, v1. Direitos e vencimento original foram mantidos.
A falha inicial e os arquivos de recuperação permanecem nas evidências.

**Preservação:** V001–V020 imutáveis, 25 grants, oito scopes, 5.328 linhas; catálogo
6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a.
B53 conserva 95 tentativas, 75 publicações, oito EXTRACTING e o ledger de 112
reservas. B54 mantém oito tentativas, quatro publicações, dez páginas e 12 entradas;
28 invocações operacionais/temporais dos JARs anteriores e 22 HTTP loopback.
Nesta continuação não foi iniciada fonte. Zero sessão restrita/transação pendente.

**Orçamento e pendências:** **109/128 reservas, seis campanhas encerradas e 19
unidades restantes**, sem devolução. Não há sétima campanha autorizada. O pacote
`database/proposals/bloco54-residual-runtime` fixa aplicação, hashes, 19 reservas,
verificação e compensação para REPLAY/FORCE_RUN, alerta, janelas fora de ordem/
repetição e configuração TLS inválida. Seu orçamento foi testado só em cópia.
Matriz completa de artefato/ACL, queda/lease/cancelamento no JAR, sink físico e
período mensal/fusos/degradação continuam pendentes; 19 casos não os fecham todos.
A comparação futura preserva as seis provas sintéticas e os oito inputs reais.

Nenhum aceite canônico novo: **114 checkboxes, 60 concluídos, 54 pendentes;
196 rotas abertas e zero AGORA**. G06/G07/G08, V2-042 e V2-022 seguem abertos.
Fonte ESL, ETL_SISTEMA, dashboards, contas, certificados, serviços e validade não
foram alterados; não houve DDL, grant adicional, agenda, deploy, commit ou push.
Evidências novas: `target/bloco54/resume-sixth`; relatório e operação manual
separam implementação, prova em harness e prova pelo JAR.

Conferência final: validadores integrado/Windows/schema/progressivo/trilha aprovados;
nove contraprovas recusaram aceites falsos, cinco regressões UTC e três do gravador
SQL passaram. Scanner offline: 1199 candidatos, 1198 textos, um binário conhecido,
zero finding; 11 self-tests aprovados. Snapshot de 1188 arquivos preservado, nenhum
ausente; 24 arquivos alterados e 11 novos, todos em UTF-8 estrito sem BOM. POM,
V001–V020, ledger e evidências anteriores foram mantidos. O diff desta continuação
e os recibos finais ficam em target/bloco54/resume-sixth.
## Fotografia histórica — quinta campanha do Bloco 54

O owner adotou a seção 3 do prompt e autorizou depois os três grants e uma única quinta campanha de até 15 minutos, respondendo “pode”. A campanha foi executada e encerrada. Estado: **PARTIAL_NOT_ACCEPTED**. O [relatório integrado](docs/runbooks/v2-022-bloco54-integrado.md), o [ADR 0036](docs/adr/0036-laboratorio-runtime-local-consumidores-e-operacao.md) e a [operação manual](docs/runbooks/v2-022-bloco54-operacao-manual.md) distinguem implementação, prova física e lacunas das frentes A–G.

**Comprovado na quinta campanha:** V019/V020 passaram por baseline e upgrade em rollback antes da instalação aditiva. Os três EXECUTEs foram aplicados e os 25 exatos conferidos em nova conexão. O JAR final passou em 16 casos sob SERVICE/OPERATOR: publicação de Coletas/Fretes, consultas, repetição sem HTTP/nova publicação, negações, parcialidade, drift, DAG e quatro invocações temporais. O launcher manual passou em STATUS e diagnóstico. A matriz em test-classpath separado confirmou 31 casos: SQL direto, ocorrência divergente, 22 campos adulterados, recibo adulterado/reusado, ack descartado, duas JVMs com um consumo e expiração da capability.

**Planejamento e limite da prova:** três janelas únicas por workload foram persistidas e reencontradas após reinício, de 28/02 a 02/03/2024, sem extração temporal. O SQL confirmou seis ocorrências de plano e nenhuma tentativa para elas. Preview offline passou anteriormente em sete cenários válidos e três recusas de gap/overlap. Falta a ligação operacional comprovada das janelas, conclusão fora de ordem, fronteira, reconciliação/degradação e período mensal auditado. Drift recusou a execução, mas não deixou alerta para essa ocorrência; o sink pelo JAR continua sem prova positiva.

**Falha e recuperação:** depois da primeira revogação de mapping, o harness tentou gravar saída SQL nula com WriteAllLines. O comando já havia confirmado; a falha de evidência encerrou a campanha. O caminho de contenção manteve SERVICE revogado, na versão 3. Após leitura exata, a compensação do mesmo caso reservado restaurou somente revoked, avançando a versão para 4; direitos, demais campos e vencimento original foram conferidos antes do commit e em outra conexão. Nenhum scope ou papel REPLAY/FORCE_RUN foi acrescentado. O defeito foi reproduzido sem banco e corrigido; três casos de regressão do gravador passaram. Isso não transforma a matriz interrompida em PASS.

**Banco e orçamento:** V001–V020 instaladas e imutáveis, 5.318 linhas, catálogo 6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a. Perfil atual: 25 grants, SERVICE mapping v4, OPERATOR v1, oito scopes v1, validade até 07/10/2026 preservada. As 95 tentativas, 75 publicações e oito EXTRACTING B53 continuam presentes; B54 soma oito tentativas, quatro publicações, 12 entradas auditadas e dez páginas. Zero sessão restrita/transação de usuário remanescente. Ledger B53 intacto: 112 reservas. B54: **99/128 reservas, cinco campanhas encerradas e 29 unidades restantes**, sem autorização para sexta campanha nem devolução de reservas. A recuperação compensou o caso AUTH08 já reservado; não iniciou nova experiência.

**Validação e artefatos:** Java 17 offline final v3: **1056 testes, zero falhas/erros e quatro skips preexistentes**, Enforcer/Spotless/Checkstyle/arquitetura/JaCoCo aprovados. Java/POM/migrations não foram alterados nesta continuação. Ao todo, 28 invocações de JAR nas duas revisões e 22 HTTP exclusivamente loopback; a matriz de 31 casos usa harness separado e não é contada como JAR oficial. Seis cenários de comparação SQL com oráculo independente passaram antes; a comparação real permanece recusada até preencher os oito inputs documentados. Resultados e falhas anteriores foram preservados, incluindo os erros corrigidos nas consultas de conferência.

**Conferência final desta continuação:** validators integrado/Windows/schema/progressivo/trilha aprovados; sete contraprovas de aceite falso recusadas e três regressões do gravador SQL aprovadas. Scanner: 1188 candidatos, 1187 textos, um binário conhecido e zero finding; 11 self-tests passaram. Snapshot de 1186 arquivos preservado, nenhum ausente, 14 alterados e dois novos; todos os 16 textos em UTF-8 estrito sem BOM e git diff --check aprovado. O diff próprio e a auditoria ficam nas evidências suplementares.

**Impedimentos concretos:** ainda faltam os casos restantes de revogação/policy, AUTH-04–06/13–14/16 completos, REPLAY/FORCE_RUN com papéis temporários, quedas/lease/cancelamento do JAR final, alerta positivo e matriz temporal completa. O diagnóstico atual usa -ObservabilityProfile. Banco, contas, UAC, schema e os três grants já estão resolvidos. As campanhas autorizadas terminaram; nenhuma sexta será aberta automaticamente. O [pacote residual](docs/runbooks/v2-022-bloco54-pendencias-fisicas.md) delimita aplicação, verificação e recuperação para revisão posterior.

Nenhum aceite canônico novo foi marcado: **114 checkboxes, 60 concluídos, 54 pendentes; 196 rotas abertas e zero AGORA**. G06/G07/G08, V2-042 e V2-022 permanecem abertos. Fonte real, paridade, governança produtiva, fatos mensais, sweep-apply e cutover conservam seus gates. Não houve conexão ao ETL_SISTEMA, fonte ESL, leitura de dashboards, renovação, serviço, deploy, commit ou push.

## Fotografia histórica — preparação anterior à adoção do Bloco 54

O owner pediu um próximo bloco grande a partir das pendências e da documentação. O [prompt integrado de runtime autorizado e operação local](docs/runbooks/prompt-bloco-54-runtime-operacional-local-astra.md) reúne sete frentes: governança de identidade existente; artefato/launcher/diagnóstico; matriz física de autorização; Coletas/Fretes pelo JAR oficial com fonte simulada em loopback e SQL real; planejamento temporal com consumidor; regressão/evidências; e preparação da comparação futura. A seção 6 cobre os 54 checkboxes abertos, sem criar um backlog paralelo ou antecipar gates de fonte, paridade e produção.

Esta é a abertura recomendada para o próximo pacote, substituindo os prompts históricos de B53 já executados. Reutiliza banco, contas, authority/TLS e credenciais protegidas existentes. A adoção futura explicita orçamento adicional limitado, ledger separado sem devolver reservas B53, alterações temporárias de mapping e preservação de dados. Qualquer nova concessão fora desse limite deve chegar como delta concreto revisável. Não há nova solicitação de nomes de contas/banco, nem leitura do ETL_SISTEMA.

**PREPARED_NOT_EXECUTED:** 114 checkboxes, 60 concluídos, 54 pendentes, 196 rotas abertas e zero AGORA permanecem inalterados; Bloco 54 não atribuído. Esta preparação não executa SQL/JDBC, processos sob contas restritas, fonte simulada, provisionamento ou testes Java. As provas físicas e a suíte de 1042 testes citadas no prompt pertencem ao fechamento anterior.

Validação documental: cobertura exata dos 54 IDs pendentes na seção 6 do prompt; checkboxes/rotas idênticos ao snapshot desta preparação; checker da trilha aprovado; nove links locais do prompt válidos; cinco arquivos em UTF-8 estrito sem BOM; git diff --check aprovado. Scanner offline: 1131 candidatos, 1130 textos, um binário conhecido, zero finding/oversized/não inspecionado. Snapshot e logs em target/preparacao-bloco54-20260907-201825. Somente o prompt novo e a sincronização documental foram alterados nesta preparação, sem novo aceite funcional.

## Provisionamento local já comprovado

Atualização de provisionamento em 07/09/2026: o owner informou que não havia contas e autorizou criá-las de forma segura. Foram criadas etl_v2_exec/etl_v2_view, dois logins SQL Windows e dois usuários no V2 com 22 grants exatos, sem Administradores/db_owner. Authority/mappings reais locais, oito escopos LOCAL_SHADOW/LOCAL_V2/LOCAL_V2 e TLS JDBC com certificado público fixado foram comprovados. Duas sessões Windows novas negaram acesso ao ETL_SISTEMA; JAR oficial executou status autorizado nas duas (NOT_FOUND/exit 10), e run do OPERATOR foi negado (20). Duas decisões ALLOW e dois consumos duráveis confirmados. Contas habilitadas por 30 dias, credenciais DPAPI protegidas; sem fonte, banco novo ou restart. Ledger cumulativo agora 112/128. Procedimento/evidência em [contas locais](docs/runbooks/v2-042-contas-locais-windows.md). A fotografia de fechamento original abaixo preserva seus resultados históricos.
## Fechamento do Bloco 53 — Windows/SQL, frentes A–F

P02Q/V2-022/QUALIFICACAO_FISICA_LOCAL comprovado: seis testes físicos completos passaram (zero falhas/erros/skips), com Coletas/Fretes, recibos de onze campos consultados somente no SQL por novas JVMs, rollback, ack perdido, concorrência READ/RESUME e apply/RESUME, lease, cancelamento, incremental/replay, COL-03, revogação de policy e publicação independente. V001–V017 instaladas e preservadas; baseline/migrations comparados fisicamente em rollback antes das instalações atômicas. Ledger cumulativo: 110/128 reservas, inclusive falhas, em quatro campanhas finitas; nenhuma limpeza de dados confirmados.

Também concluídas as implementações e provas independentes B–F: adapter Windows/SQL com authority administrada, auditoria append-only e consumo durável de capacidade, Main/handlers/dispatcher integrados após autorização, status somente leitura, política/plano temporal com store JDBC e reconciliação limitada. SQL real confirmou DENY idempotente e plano de três janelas entre JVMs. JAR direto passou help/version e recusou run/replay/status/force-run sem authority. Pacote de provisionamento com aplicação, verificação e compensação preparado e testado, sem aplicá-lo.

Suíte Java 17 offline completa: BUILD SUCCESS, 1042 testes, zero falhas/erros, quatro skips esperados; Enforcer, Spotless, Checkstyle, arquitetura e JaCoCo verdes, sem redução de gates. Campanha física final: 6/6 PASS em 259,1 s. Validators SQL 050/051/052 passaram no alvo moderno populado. Evidências, comandos, hashes, defeitos corrigidos e limitações no [runbook integrado](docs/runbooks/v2-022-bloco53-integrado.md), [ADR 0035](docs/adr/0035-authority-windows-sql-consumo-duravel-e-plano-temporal.md) e [provisionamento revisável](docs/runbooks/v2-042-provisionamento-windows-sql.md).

Contagem atual: **114 checkboxes, 60 concluídos e 54 pendentes; 52,6% documental**; 196 fatias abertas, nenhuma rota AGORA. Somente o subitem P02Q fecha. V2-022/V2-022b/V2-041/V2-042/V2-042b/c permanecem abertos. O provisionamento posterior comprovou principals/authority/TLS/grants e status autorizado locais; continuam pendentes escopos da fonte operacional, matriz completa de autorização/dispatcher pelo JAR, fonte externa autorizada e matriz temporal nominal. A conta administrativa atual não demonstra least privilege. Nenhum aceite foi inferido de fixture, escolha técnica ou porcentagem projetada.

Gates finais: progressivo, pacote Windows e trilha PASS; cinco contraprovas da trilha recusadas; scanner offline com 1127 candidatos/1126 textos/um binário e zero findings; UTF-8 estrito sem BOM em 497 textos alterados/novos e git diff --check PASS. Inventário SQL final: 95 tentativas, 273 entradas, 176 páginas, 75 publicações, 4978 linhas totais, zero transação aberta dos filhos próprios. Worktree preexistente preservado contra snapshot de 1084 arquivos; sem fonte externa, leitura de .env, download, conta/login/grant operacional, serviço, deploy, Git commit/push ou limpeza destrutiva. Schema/dados confirmados permanecem. As seções históricas abaixo conservam as evidências de seus próprios blocos; não descrevem o catálogo atual.
## Fechamento do Bloco 52/P02R — 07/09/2026

- **Escopo efetivamente comprovado:** pacote A+B+C+D local. Porta/adapter JDBC de leitura de resumo, selo após guard/auditoria reais, continuação SQL preparada sem reemitir permits, dispatcher/sessões integrados, razões sanitizadas e causa interna preservada. Identidade inclui ocorrência, ciclo/plano, namespace, modo/janela, replay e fingerprints. READ confirma recibo histórico sem reapply; RESUME revalida sob lock/lease SQL e usa os entrypoints existentes das duas verticais. Não chama stale recovery nem reinicia extração parcial.
- **Prova independente:** 22 testes novos em RuntimeDurableRecoveryIntegrationTest e 27 testes anteriores do motor passaram juntos (49 focados). Persistência sintética limitada grava somente scalars/resumos em arquivo; novos objetos e oito processos Java filhos próprios, em quatro pares escritor/leitor, recuperam apply/prepare das duas verticais sem sessão, guard, permit ou cache anterior. Os testes verificam zero fetch na retomada, um único apply, fronteira de replay ausente, preservação de publicação independente/dependente terminal BLOCKED e fechamento de conexões. Cobrem ack de start/selo/transição/prepare/apply, falta/adulteração de evidência, contrato/tenant/janela/replay/fingerprints, DQ reprovada/obsoleta, lease e mudança entre READ/RESUME, cancelamento e falha de leitura após commit.
- **Validação Java:** clean verify Maven offline com JDK 17.0.20.101, POM temporário equivalente exceto target/bloco52-build: BUILD SUCCESS, 1023 testes, zero falhas/erros, quatro skips esperados, Enforcer/Spotless/Checkstyle/arquitetura/JaCoCo aprovados. A primeira suíte completa encontrou duas expectativas V001–V014 nos testes estáticos de schema; foram atualizadas para exigir V015, sem redução de gates. Relatórios e logs ficam em target/bloco52-build e target/bloco52-final.log. A rodada focada está em target/bloco52-typed-receipt.log.
- **Artefatos/revisão:** [ADR 0034](docs/adr/0034-recuperacao-duravel-local-sem-reemissao-de-permits.md), [runbook P02R](docs/runbooks/v2-022-recuperacao-duravel-local.md), V015, baseline/manifests, validators 048/049, checker estático e testes de processo. Alterações preexistentes foram preservadas; V001–V014, permits e composição operacional não foram reescritos. Não se alega revisão humana, commit ou prova física.
- **Recibo exato e gates finais:** a revisão encontrou que COL-03 pode devolver contagens diferentes das genéricas. V015 agora grava recibo tipado imutável na transação dos efeitos e o devolve no readback; teste com contagens divergentes e comparação do corpo SQL preservado passaram. Os instantes do reader usam UTC explícito. Gate progressivo (incluindo P02R), Coletas, Fretes e trilha passaram; cinco contraprovas da trilha foram recusadas em snapshot próprio. Scanner self-test 9/9 e varredura de 1082 candidatos/1081 textos/um binário passaram, zero finding/oversized/não inspecionado. UTF-8 estrito sem BOM de 451 textos alterados/novos (inclui alterações preexistentes) e git diff --check passaram. O POM temporário foi removido após conferir equivalência; logs, build e snapshot de contraprovas ficam ignorados sob target. A revisão automática bloqueou a limpeza recursiva combinada por política, sem motivo detalhado; foi usada remoção pontual segura do POM, preservando o snapshot.
- **Limites e próximo pacote:** somente o subcheckbox V2-022/RECUPERACAO_DURAVEL_LOCAL é fechado. V2-022, V2-022b, V2-041 e V2-042b/c permanecem abertos. Sem rede, API, .env, credenciais, SQLCMD, JDBC físico, deploy, commit ou push. G08 deve qualificar V015 e as duas verticais em SQL Server descartável/local explicitamente autorizado: sintaxe/execução, transações, concorrência, lease/relógio, attention/rollback, driver/socket e reinício físico. Essa operação não é autorizada por P02R; não há próximo STATUS=AGORA.

## Preparação do próximo chat — proposta de Bloco 53/P02Q

Em 07/09/2026, o owner pediu um próximo pacote grande e um prompt para outro chat. Foi preparado o [prompt de qualificação física do motor](docs/runbooks/prompt-bloco-53-qualificacao-fisica-motor-astra.md), com A+B+C+D+E: harness físico opt-in, qualificação/correção SQL, recuperação entre JVMs com commits sintéticos, concorrência/fencing/cancelamento e regressão com evidências. A proposta distingue P02Q/V2-022/QUALIFICACAO_FISICA_LOCAL de G08/V2-022b, que exige os inputs e o adapter de V2-042b/c.

O texto para adoção no próximo chat explicita localhost/ETL_SISTEMA_V2_SHADOW, autenticação Windows existente, preflight, evolução versionada e eventual transição histórica somente se vazia/compatível, limites cumulativos e preservação dos dados sintéticos confirmados. Preparar esse texto não autoriza nem executa essas operações. Nenhuma conexão física, alteração funcional, nova fatia ativa ou checkbox foi realizada nesta preparação; Bloco 53 permanece não atribuído, com 59/113 itens concluídos e 196 fatias abertas. As evidências de B52 acima permanecem históricas e não são novas execuções de testes desta preparação.

Validação desta preparação documental: checker da trilha aprovado; linhas de checkboxes/rotas preservadas integralmente contra snapshots anteriores; links e referências conferidos; três documentos em UTF-8 estrito sem BOM; git diff --check aprovado. Scanner offline aprovado com 1083 candidatos, 1082 textos, um binário conhecido e zero findings. Não houve nova execução Maven, mudança de código, migration aplicada ou aceite funcional.

### Ampliação pedida pelo owner — motor, identidade e autorização

O owner considerou pequeno o avanço limitado a P02Q, pediu mais entregas com Astra xhigh e delegou a escolha da abordagem técnica de identidade. O [prompt ampliado do Bloco 53](docs/runbooks/prompt-bloco-53-pacote-integrado-runtime-astra.md) passa a ser a abertura recomendada; P02Q permanece sua especificação física. A direção escolhida para futura implementação é autenticação integrada Windows/SQL, mapping protegido, referência de auditoria opaca, sink durável, capacidades consumíveis e composição oficial, preservando deny-all sem configuração confiável. O pacote inclui também catch-up, fechamento mensal, política temporal e retomada de planos.

A escolha técnica não inventa contas, principals, owners, ACLs, autorização de grants ou prova operacional. O prompt exige concluir toda implementação/teste independente antes de solicitar eventual provisionamento em pacote concreto. Projeção condicional, mantendo apenas um novo subitem P02Q: 60/114 (52,6%) com P02Q isolado; 64/114 (56,1%) se também V2-042b, V2-042c, V2-042 e V2-022b forem integralmente comprovados; 65/114 (57,0%) somente se todos os aceites de V2-022 pai também passarem. Nenhum desses itens foi fechado nesta preparação; 59/113 e 196 fatias abertas permanecem as contagens efetivas. A análise consultou documentação pública Microsoft; não houve conexão SQL, inspeção de conta real, provisionamento, código funcional ou execução de testes Java.

Validação da ampliação documental: trilha aprovada; checkboxes/rotas intactos contra snapshots prévios; quatro documentos em UTF-8 estrito sem BOM, links e seis frentes conferidos; projeções recalculadas dos itens reais; git diff --check aprovado. Scanner offline: 1084 candidatos, 1083 textos, um binário conhecido e zero findings. Essas verificações não são aceites de implementação do Bloco 53.

## Escopo e finalidade deste documento

Este arquivo é o roteiro canônico para transformar o projeto <code>etl-extracao-dados-v2</code> na substituição limpa, profissional e segura do que comprovadamente funciona em <code>etl-extracao-dados</code>.

- O levantamento desta revisão ficou restrito aos repositórios <code>etl-extracao-dados</code> e <code>etl-extracao-dados-v2</code>, aos respectivos <code>AGENTS.md</code>, aos arquivos de estado e ao <code>CONTEXTO_GLOBAL.md</code>.
- O projeto de dashboards foi explicitamente excluído e não foi lido. Nomes de views foram inventariados apenas nos scripts SQL pertencentes ao ETL legado.
- A compatibilidade de consumo atribuída aos dashboards é protegida sem abrir esse projeto: o oráculo técnico deste roteiro são as 19 views e os cinco fatos pertencentes ao ETL, complementados obrigatoriamente pelo manifesto externo e aceite do consumidor em V2-037.
- A revisão combinou inventário estático com sondas <code>curl</code> read-only autorizadas pelo owner contra a API ESL em 29/08/2026. Não executou banco, DDL/DML, mutation, job, deploy, cutover, comando produtivo nem alteração de código.
- As respostas remotas foram processadas apenas em memória e resumidas em contagens/flags; nenhum payload, ID, nome, cursor ou segredo foi persistido no repositório. Raster não foi chamado, pois a autorização adicional citou a API ESL, não a API Raster.
- A autorização deste levantamento abrange criar o plano completo e caracterizar contratos ESL por leitura controlada. Ela não autoriza implementar domínios, criar banco/remoto/agendamento, escrever em produção ou desligar o legado.
- A solicitação final de 29/08/2026 autorizou preparar uma requisição <code>curl</code> por entidade Data Export e comparar seus pontos finais com a V1. Nenhuma nova chamada foi feita nesta rodada: a data de modificação observável do <code>.env</code> é anterior ao incidente de exposição e não comprova rotação/invalidação; V2-041 continua bloqueando qualquer reutilização externa da credencial.
- A auditoria final de contenção releu a V1 e comparou em memória, sem emitir valor ou hash, as definições sensíveis atuais do <code>.env</code> contra os demais arquivos e o histórico Git local do legado: nenhum valor atual reapareceu fora do próprio arquivo ignorado. Isso reduz a superfície comprovada no repositório, mas não desfaz a exposição no transcript nem substitui rotação/invalidação administrativa.
- O objetivo é portar capacidades, contratos e regras válidas, não copiar classes, scripts ou tabelas mecanicamente. Tudo deve ser redesenhado dentro da arquitetura da V2.

### Classificação das evidências

- **EVIDÊNCIA LOCAL NÃO VERSIONADA:** existe implementação e evidência local identificável, limitada ao cenário registrado; ainda não há commit/SHA que permita chamá-la de prova versionada.
- **EVIDENCIADO NO LEGADO:** há código ligado ao runtime, testes/SQL e/ou registro operacional, mas ainda exige teste de caracterização e paridade antes de entrar na V2.
- **OBSERVADO EM SONDA ESL:** comportamento visto por <code>curl</code> read-only na data/janela declarada; sem SHA/relatório reproduzível e sem generalizar garantia global do fornecedor.
- **PENDENTE DE DECISÃO/PROVA:** existe candidato ou comportamento histórico, porém identidade, contrato, completude ou regra de negócio ainda não foi validado.
- **NÃO PORTAR:** artefato obsoleto, duplicado, inseguro, acoplado a dashboard ou contrário à arquitetura-alvo.

Existência de arquivo, teste antigo ou workflow configurado não significa produção validada, revisão humana ou gate verde.

## Auditoria de marcações e avanço — 07/09/2026

A conferência posterior ao Bloco 51 identificou três fatias antigas concluídas somente em notas:
fundação de referências (V2-035a), dimensão interna de Usuários (V2-035b) e enforcement de
Usuários (V2-009d). Elas agora têm subcheckboxes próprios, espelhados na trilha, sem consumir
novo bloco nem concluir seus pais. A última entrega de travessia/motor já estava marcada corretamente.

Contagem atual: **113 checkboxes, 58 concluídos e 55 pendentes; 51,3% de itens documentais**.
Há **197 fatias abertas** na trilha. A mudança de 50,0% para 51,3% reconhece trabalho anterior;
não representa implementação nova nem mede esforço restante/prontidão operacional.

| Recorte estrutural | Concluído no escopo local registrado |
| --- | --- |
| Verticais Data Export base em sombra | 5/9 (55,6%): Coletas, Manifestos, Cotações, Fretes, Localização |
| Dimensões internas de V2-035b | 1/6 (16,7%): Usuários; contrato consumidor separado |
| Fatos/materializações de V2-036 | 0/5 concluídos |
| Contratos publicados de V2-037 | 0/19 concluídos |

Faltam na construção: recuperação durável e composição completa do runtime; quatro verticais
(Contas a Pagar, Faturas por Cliente, Inventário e Sinistros), com identidade/cardinalidade
previamente resolvidas; demais referências/dimensões; relações Manifesto–Coleta–Frete e
bootstrap/backfill; cinco fatos e 19 contratos SQL; integração efetiva de sweep e controles
operacionais. Raster continua condicional à decisão própria. A base compartilhada já existe;
esses componentes restantes impedem declarar a estrutura completa.

Os gates externos/operacionais são separados: rotação/identidade/autorização, baseline e owners,
fontes/oráculos, prova física de recuperação, paridade, escala/E2E, CI/release e cutover.
Na fotografia dessa auditoria, o próximo bloco permanecia **52/P02R**, sem execução iniciada por ela. O fechamento posterior de P02R está registrado acima.

Validação desta revisão: três validadores estáticos de referências/Usuários/dimensão passaram;
trilha sincronizada e contagens recalculadas. Os relatórios da última suíte confirmam 1001 testes,
zero falhas/erros e quatro skips; a suíte não foi reexecutada por esta alteração documental.
## Diagnóstico executivo

- **Bloco 51 — P02M:** motor local integrado e validado. Dois subcheckboxes locais concluídos, próximo P02R aberto; painel ao fechar o Bloco 51 com 55/110 checkboxes concluídos e 197 fatias abertas, sem inferir prontidão produtiva. Doze validadores estáticos, cinco negativos do validator da trilha, scanner 9/9 e varredura de 1061 candidatos/1060 textos/um binário passaram com zero finding. UTF-8 estrito de 331 arquivos textuais alterados/novos (inclui trabalho preexistente) e diff check passaram. Nenhum hold externo mudou.

- **Fotografia histórica da ingestão — 07/09/2026, anterior ao Bloco 51:** o direcionamento do owner para construir as partes complexas resultou na ligação travessia → mapper → staging pelo `DataExportStagingPipeline`, com consumidores tipados nas duas verticais. Páginas expandidas são divididas em lotes físicos de até 100, com número de batch por ocorrência, ordinal por lote, cancelamento cooperativo, validação do template/ordenação/guard e falha fechada. O callback de resposta copia somente uma linha por vez. A integração não chama promoção, publicação ou watermark. O `clean verify` com Java 17 passou em saída isolada com 974 testes, zero falhas/erros e quatro skips esperados; os 25 testes novos, Enforcer, Spotless, Checkstyle e JaCoCo passaram. A tentativa inicial de `clean` no diretório padrão falhou antes dos testes por interferência em `target/test-classes`; a repetição usou cópia temporária do POM comprovadamente equivalente exceto pelo `build.directory`, depois removida, sem alterar o POM canônico nem parar processos. ADR 0032 e a evidência detalhada de V2-022 registram os limites. Naquela fotografia havia 53/107 checkboxes e dispatcher/promoção ainda não estavam integrados. P02I/P02M acima reconhecem os aceites locais posteriores; V2-022b, recuperação após perda da JVM e gates externos continuam pendentes.

- **Bloco 50 — Q-MED-FND-01 — V2-050/FUNDACAO_MEDICAO_LOCAL (FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED):** concluída somente a fundação local, offline e test-only de medição multiescala. Os streamers produtivos existentes <code>DataExportPageStreamer</code> e <code>GraphQlPageStreamer</code> foram exercitados diretamente por integrações em 16, 256 e 4.096 páginas sintéticas de oito registros, sempre com uma página gerenciada em voo e zero página retida ao final; heap e duração foram registrados exclusivamente como diagnóstico variável da JVM. O mutante real baseado em <code>ArrayList&lt;Object&gt;</code> reteve páginas por toda a execução e foi rejeitado com <code>EXECUTION_WIDE_PAGE_RETENTION_DETECTED</code>; o detector de ciclos GraphQL usa estrutura fixa de 1 MiB. O RED dedicado registrou <code>V2_050_MEASUREMENT_FOUNDATION_MANIFEST_MISSING</code> e o RED Java falhou pelos tipos de medição ausentes. O GREEN passou 56 testes focados, 220 regressões afetadas isoladas e <code>clean verify</code> offline com 949 testes, zero falhas/erros e quatro skips esperados, além de Enforcer, Spotless, Checkstyle, JaCoCo, receipt real, 18 mutações adversariais, validator e scanner offline verdes. Fecha apenas o subcheckbox da fundação: <code>ENTITY_V2_050_GATE=OPEN</code>, <code>ENTITY_HEAP_PLATEAU_NOT_PROVEN</code>, <code>ENTITY_SQL_PLAN_NOT_EXECUTED</code> e <code>ENTITY_PUSH_DOWN_PLAN_NOT_PROVEN</code>; V2-050 pai, as dez rotas de entidade, as 24 rotas de saída e V2-038 permanecem abertos. Não houve rede, fonte, API, <code>.env</code>, segredo, banco, SQLCMD/Flyway, plano SQL real, DDL/DML, migration, alteração de <code>src/main</code>/runtime/<code>Main</code>/composition root/POM/workflow, deploy, publicação, sweep/prune ou cutover.
- **Bloco 49 — Q-SWP-FND-01 — V2-013/FUNDACAO_KERNEL_LOCAL (FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED):** concluída somente a fundação Java pura, provider-neutral, O(1), fail-closed e preview-only de classificação de uma responsabilidade de sweep por chamada. Os seis tipos em <code>plataforma/reconciliacao/sweep</code> compõem <code>ExecutionMode.SWEEP</code> e <code>SourceCompletenessStatus</code>, vinculam policy/scope/snapshot/evidências por SHA-256 canônico, exigem duas travessias e duas ausências realmente independentes e retornam no máximo <code>PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY</code> para uma raiz sintética; filhos, componentes, históricos, canais, referências, linhas e candidatos continuam bloqueados. A matriz fechada possui 33 responsabilidades nas 11 famílias canônicas, com zero <code>ENABLED</code>, zero <code>PROVEN_COMPLETE</code>, 19 <code>BLOCKED</code>, cinco <code>DISABLED</code> e nove <code>NOT_APPLICABLE</code>. O RED dedicado foi <code>V2_013_SWEEP_FOUNDATION_MANIFEST_MISSING</code> e o RED Java ocorreu por tipos ausentes; o GREEN executou 53 casos sintéticos, 52 mutantes fail-closed, 129 testes focados e <code>clean verify</code> offline com 893 testes, zero falhas/erros e dois skips, além de Enforcer, Spotless, Checkstyle, JaCoCo, gerador/validator e scanner offline verdes. Fecha apenas a fundação: V2-013 pai, todas as rotas Q-*-04, V2-034a/V2-034b e toda execução/mutação de sweep permanecem abertas. Não houve fonte, rede, <code>.env</code>, banco, SQL, filesystem no kernel produtivo, runtime wiring, anti-join, persistência, desativação, delete/prune, publicação ou cutover.
- **Bloco 48 — Q-FND-02 — V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE (FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES):** concluída somente a extensão aditiva e test-only do harness de caracterização, sem executar fonte ou oráculo. O lock literal dos 43 arquivos históricos de Q-FND-01 permaneceu íntegro, com agregado <code>ab99feb5e413deb44bf0dfc26f737453fa802cd6b293805ba82aaa1c56b9029a</code>. Foram acrescentados exatamente dois perfis de entidade e três canais isolados: Fretes/Data Export 6389 autoritativo, Fretes/GraphQL sidecar estritamente observacional e Localização/Data Export 8656. Todos permanecem <code>PREPARED_NOT_EXECUTED</code>/<code>ORACLE_REQUIRED</code>, com dados exclusivamente sintéticos. O RED estático registrou <code>Q_FND_02_MANIFEST_MISSING</code> e o RED Java registrou tipos ausentes; o GREEN executou 48 mutações reais fail-closed, validou schema fechado, bindings criptográficos, presença por path, identidade, frescor, status, limites e receipts sanitizados/atômicos. O fingerprint e os bytes do receipt ficaram estáveis com LF explícito e independentes do sistema operacional, com hash literal <code>327ca45986ab6ebe434f56b641053faa8a68f88d946636cdea22400321fd2dd4</code>. O <code>clean verify</code> offline passou com 780 testes, zero falhas/erros, dois skips esperados, Enforcer, Spotless, Checkstyle e JaCoCo verdes. Fecha somente esta fundação; V2-012/V2-012a/b/c, Q-FRE-01 e Q-LOC-01 permanecem abertas, e não houve caracterização, bootstrap, relação, paridade, publicação, sweep ou cutover.
- **Bloco 47 — V10 — V2-028/EXECUCAO_BASE_SHADOW de Localização de Cargas 8656 (IMPLEMENTADA_EM_SHADOW):** concluída a vertical base somente no shadow local autorizado e sem antecipar relação, caracterização ou publicação. O boundary local permanece deny-all; mapper/domínio tratam exatamente os 17 paths aceitos, source key exclusiva <code>/corporation_sequence_number</code> INTEGER type-tagged, presença <code>ABSENT/NULL/VALUE</code>, raw + <code>rawWireLexeme</code> + typed/parse/path/proveniência, números estritos em <code>BigDecimal</code> com escala preservada, frescor somente por <code>service_at</code>, timezone <code>America/Sao_Paulo</code> fail-closed em gap/overlap, status explícito e <code>status_branch_nickname=ABSENT/UNSOURCED_LEGACY</code>. Lotes físicos ficam limitados a 100 e os gateways JDBC usam batch/procedures set-based. V014 cria as quatro estruturas verticais, trigger de candidate set e os dois entrypoints fechados; a allowlist canônica passa a 41. O RED real ocorreu antes do código/migration por tipos/artefatos ausentes e <code>LOCALIZACAO_CARGAS_SHADOW_VERTICAL_MISSING</code>. O GREEN físico recompôs V001–V014 apenas em <code>localhost/ETL_SISTEMA_V2_SHADOW</code>, validou 046/047, replay/no-op, stale, empate/quarentena, preservação tri-state, precisão decimal, isolamento e concorrência, e terminou integralmente revertido em <code>estado=0|0|0</code>. O <code>clean verify</code> offline em JDK 17 passou com 714 testes, zero falhas/erros, um skip esperado, Enforcer, Spotless, Checkstyle e cobertura verdes. Fecha somente V2-028; fallback de volume por Fretes continua diferido para relação/publicação e V2-046a/V2-046b permanecem abertas.
- **Bloco 46 — V10A — V2-028a/DECISAO_LOCAL de Localização de Cargas 8656 (COMPLETE_LOCAL_DECISION_ONLY):** concluída somente a decisão local auditável de LOC-01–LOC-07, sem implementação. O ADR 0028 e o catálogo fechado vinculam o contrato V2-025b/8656 e a identidade P07, fixam `/corporation_sequence_number` inteiro como única source key escopada, surrogate canônico, presença <code>ABSENT/NULL/VALUE</code>, parsing numérico estrito, frescor exclusivamente por <code>service_at</code>, conflitos/nulos/out-of-order fail-closed, normalização/terminalidade explícita de status e <code>status_branch_nickname=ABSENT/UNSOURCED_LEGACY</code>. A regra downstream <code>COALESCE(volumes_localizacao, volumes_fretes, 0)</code> preserva zero local como valor real; Fretes permanece somente candidato por alias exato no mesmo escopo, e ambiguidade bloqueia sem join, FK, crosswalk ou <code>TOP 1</code>. O RED canônico foi <code>LOCALIZACAO_DECISION_CATALOG_MISSING</code>; o GREEN validou sete regras, 24 casos exclusivamente sintéticos e 13 mutações negativas. Fecha somente V2-028a; V2-028, V2-046a e V2-046b continuam abertas. Não houve Java produtivo, mapper, migration, SQL, banco, fonte, rede, relação, caracterização, bootstrap, publicação, sweep ou cutover.
- **Bloco 45 — V09 — V2-011/EXECUCAO_BASE_SHADOW de Fretes 6389 (IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING):** concluída a vertical base de Fretes somente em sombra local, sem antecipar relações. O mapper e os value objects preservam os sete únicos paths comprovados pelo contrato 6389, presença <code>ABSENT/NULL/VALUE</code>, tipos/parse state/path/proveniência, frescor único, terminalidade, performance independente, CT-e/finalizações sintéticos, financeiro sem regra inferida, sidecar GraphQL limitado aos dez paths aprovados e candidatos relacionais append-only. A migration V013 cria nove tabelas, cinco procedures fechadas e um trigger do candidate set; os validators 044/045, o runner e a prova concorrente executaram apenas em <code>localhost/ETL_SISTEMA_V2_SHADOW</code>, com Windows auth, dados sintéticos e rollback integral (<code>estado=0|0|0</code>). O RED canônico foi <code>FRETES_SHADOW_VERTICAL_MISSING</code>; o GREEN final passou com 684 testes Maven, zero falhas/erros, um skip esperado e todos os gates de estilo/cobertura. Fecha somente V2-011 como <code>IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING</code>; V2-046a/V2-046b e o agregado V2-017a permanecem abertos. A política continua <code>V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW</code> e <code>V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES</code>; V2-046b segue dona exclusiva do crosswalk final. Não houve rede, fonte, dado real, publicação, sweep, bootstrap, paridade ou cutover.
- **Bloco 44 — V08 — V2-011a/DECISAO_LOCAL de Fretes 6389 (COMPLETE_LOCAL_DECISION_ONLY):** concluída somente a decisão local, versionada e fail-closed de FRE-01–FRE-07. O catálogo fechado ancora o contrato Data Export 6389 e a identidade já decidida, congela presença <code>ABSENT/NULL/VALUE</code>, a única precedência <code>cte_created_at → cte_issued_at → criado_em → servico_em</code>, empate/nulo/out-of-order, terminalidade, performance oficial/fallback com proveniência, CT-e/finalizações, sidecars GraphQL independentes e limitados, candidatos Coleta–Frete apenas preservados, financeiro sem moeda/regra inferida, partição/overlap/replay e ausência/prune desligados. O RED obrigatório falhou por <code>FRETES_DECISION_CATALOG_MISSING</code>; o GREEN validou 7 regras, 14 casos sintéticos e 9 mutações negativas. A divisão mínima corrige a inconsistência do seletor: <code>V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW</code> e <code>V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES</code>. Assim, V2-046a não bloqueia decisão nem ingestão base; V2-046a/V2-046b permanecem abertas para seus gates relacionais, e V2-046b continua sendo a única dona do crosswalk final. V2-011, V2-046a e V2-046b permanecem abertos. Não houve mapper, migration, SQL, runtime, relação, caracterização, bootstrap, publicação ou cutover.
- **Bloco 43 — G05T — V2-015d/IMPLEMENTACAO_LOCAL_FAIL_CLOSED (LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING):** concluída a aplicação física no POM/workflow, o parser/harness e as contraprovas sintéticas. <code>MODELO_RECOMENDADO=TERRA_XHIGH | MODELO_EXECUTADO=SOL_ULTRA | MOTIVO=LOTE_UNICO_AUTORIZADO_PELO_OWNER</code>. A primeira suíte Maven final havia terminado vermelha em uma asserção documental preexistente que ainda tratava Q-BST-01 como caracterização por entidade; após a correção dirigida e nova autorização do owner, <code>.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify</code> passou no JDK 17.0.20.1 com 656 testes, zero falhas, zero erros, um skip esperado, Enforcer, Spotless, Checkstyle e cobertura verdes. Dependency-Check, perfil <code>security-audit</code>, rede, feed/NVD e findings reais não foram executados; G05 permanece <code>EXTERNAL_HOLD</code>.
- **Bloco 42 — G05A — V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED (POLICY_DECISION_LOCAL_COMPLETE_TERRA_IMPLEMENTATION_PENDING):** concluída a decisão técnica local fail-closed com threshold único <code>0.0</code>, JSON autoritativo, HTML humano, falha de scanner/relatório/configuração como bloqueio e catálogo versionado inicialmente vazio de exceções. O RED inicial provou a ausência dos seis artefatos; o gate final validou fingerprint <code>83c869...e056</code>, 33 cenários sintéticos, três PASS deliberados, 30 BLOCK e zero exceção efetiva. A verificação física permaneceu vermelha por 11 divergências reais, incluindo <code>failBuildOnCVSS=11</code> e upload <code>warn</code>, reservadas a G05T. POM/workflows ficaram sem diff; não houve rede, feed/NVD, Dependency-Check, segredo, <code>.env</code>, banco ou CI real.
- **Bloco 41 — Q-BST-01 — V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE (FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED):** concluída a fundação determinística de planejamento com 17 linhas — 11 fontes-entidade, histórico de Usuários e cinco fatos —, seis entidades planejáveis, cinco bloqueadas/condicionais e 34 cenários sintéticos. O red inicial falhou pela ausência do manifesto; o gate verde conferiu ordem, vocabulários, 16 fontes/fingerprints canônicos, partições, <code>T0/Tcut</code>, delta, identidade, reconciliação, restart e rollback. A divergência derivada de <code>PUB-07</code> foi reconciliada pelo gerador canônico e os validadores ancorados passaram. Não houve rede, banco, fonte, dado real, SQL, migration ou bootstrap; Q-*-01, as verticais ausentes e fontes históricas autorizadas continuam obrigatórios para cada execução Q-*-02.
- **Bloco 40 — G12 — V2-015e (CONCLUIDO_LOCAL_SHADOW_ROLLBACK_ONLY):** as divergências intercamadas de V010/V011 foram corrigidas sem criar migration substituta. Coletas agora aplica COL-03 no wrapper tipado: terminal retroativo vence aberto, terminal persistido não regride e aberto retroativo permanece stale; o V039 captura e valida as contagens tipadas. Cotações usa <code>sequence_code</code> canônico no intervalo positivo <code>BIGINT</code>, quarentena overflow antes do JDBC, nove presenças fail-closed, merge <code>ABSENT/NULL/VALUE</code> sem colapso e tarifa resolvida por UFs efetivas na tuple física completa; moeda, unidade, arredondamento e mínimo permanecem exclusivamente na referência governada. V010/V011 foram confirmadas como drafts nunca aplicadas no alvo autorizado e editadas in place; exercícios, concorrência e gate progressivo passaram somente em <code>localhost/ETL_SISTEMA_V2_SHADOW</code>, com dados sintéticos e rollback integral. O <code>clean verify</code> offline final em Java 17 passou com 656 testes, zero falhas/erros e um skip esperado. V2-015 agregada permanece aberta por V2-015c/V2-015d. No fechamento original havia zero rota <code>AGORA</code>; a auditoria posterior encontrou e executou Q-BST-01 como Bloco 41, sem reabrir G12.
- **Bloco 39 — V2-012/FUNDACAO_LOCAL (FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES):** a fundação test-only de caracterização agora possui quatro perfis independentes e quatro fixtures exclusivamente sintéticas para Coletas 6908, Manifestos 6399, Cotações 6906 e Usuários GraphQL <code>individual(enabled=true)</code>. O núcleo provider-neutral separa Data Export de GraphQL, aplica schema fechado, UTF-8 estrito, fingerprints SHA-256, vocabulários fechados, limites de linhas/bytes/páginas/tempo, avaliação fail-closed e receipts sanitizados/atômicos somente sob <code>target/v2-012-characterization</code>. As 166 estruturas sintéticas resultam em 13 aceitas e 153 recusadas; os quatro perfis permanecem <code>PREPARED_NOT_EXECUTED</code> e os gates, <code>ORACLE_REQUIRED</code>. O <code>clean verify</code> Maven offline em Java 17 passou com 609 testes, zero falhas/erros e um skip esperado de symlink no Windows; os 14 validadores finais, o self-test do scanner, o scanner offline completo e <code>git diff --check</code> passaram. Não houve rede, API, credencial, <code>.env</code>, payload real, banco, SQLCMD, migration, runtime produtivo, deploy, commit ou push. Q-USR-01, Q-COL-01, Q-MAN-01 e Q-COT-01 não foram iniciadas e continuam dependentes de seus oráculos; Q-MAN-01 permanece <code>EXTERNAL_HOLD</code>. D03/D04 continuam <code>BLOCKED</code>, o runbook Terra D03 continua ausente; no fechamento daquele bloco havia zero rota <code>AGORA</code> e o Bloco 40 ainda não estava atribuído.
- **Auditoria corretiva offline — Usuários/Manifestos (2026-09-05, sem bloco):** foram comprovidas e corrigidas duas violações de contrato sem consumir rota: Usuários agora verifica cancelamento entre o mapeamento da página e o staging; Manifestos seleciona deterministicamente <code>FINISHED_AT → CLOSED_AT → DEPARTURED_AT → CREATED_AT</code> quando o instante máximo empata. A regressão cobre falha de staging na segunda página e permutações de frescor/MDF-e. O <code>clean verify</code> Maven offline em Java 17 registrou 613 testes, zero falhas/erros e um skip esperado; dez validadores canônicos, o self-test/scanner offline, a auditoria UTF-8 e <code>git diff --check</code> passaram. Q-USR-01 e Q-MAN-01 não foram executadas nem tiveram estado alterado; não houve rede, credencial, <code>.env</code>, payload real, banco, SQLCMD, deploy, commit ou push.
- **Campanha corretiva offline — Coletas/Cotações/resiliência/checkpoint (2026-09-05, sem bloco):** Coletas recebeu cancelamento cooperativo antes de fetch/JDBC/batch/commit/promoção, <code>toString</code> de status redigido e contrato de batch físico corrigido. Cotações passou a exigir <code>sequence_code</code> inteiro positivo, faixa real de <code>DECIMAL(19,4)</code>, frescor/data de negócio coerentes, UF ASCII, NFC, moeda somente referenciada, quarentena exclusiva e promoção JDBC de uma única execução/linha com causa sanitizada e cancelamento. A infraestrutura compartilhada agora reaplica cancelamento após fetch/consumer e antes da terminalidade, limita também corpos HTTP de erro/204, valida <code>Content-Length</code>/<code>Retry-After</code> com dígitos ASCII, preserva nanos no backoff/sleep, propaga cancelamento à repartição, evita overflow no circuito e só permite watermark operacional em publicação incremental contígua. A regressão cruzada preservou Usuários e a precedência temporal de Manifestos e redigiu payload/chave/métrica dos value objects de Manifestos. Os reds focados comprovaram cada classe de defeito; a consolidação passou com 161 testes, e o único <code>clean verify</code> offline Java 17 passou com 654 testes, zero falhas/erros e um skip esperado. Onze validadores canônicos de domínio passaram; o self-test do scanner passou em nove casos e a varredura offline de 861 candidatos (860 textos e um binário verificado) terminou sem finding. As migrations V010/V011 permaneceram intactas naquele fechamento: ficaram pendentes, para ownership SQL futuro, a precedência retroativa de terminalidade de Coletas e as divergências <code>INT</code>/tri-state de Cotações. Nenhuma rota Q foi executada ou alterada, Q-MAN-01 continuou <code>EXTERNAL_HOLD</code>, e naquele momento havia zero rota <code>AGORA</code> e o Bloco 40 ainda não estava atribuído. Não houve rede externa, API, credencial, leitura de <code>.env</code>, payload real, banco, SQLCMD, DDL/DML, migration, runtime produtivo, deploy, commit ou push.
- **Diretriz do owner — conclusão acelerada e prontidão para testes (2026-09-07):** a prioridade é concluir no menor prazo seguro 100% do escopo necessário e iniciar imediatamente a bateria autorizada de testes externos/operacionais quando seus gates estiverem satisfeitos. Testes automatizados não ficam para o fim: a suíte offline mais recente registrou 949 testes, com 945 executados com sucesso, zero falhas, zero erros e quatro skips esperados; Enforcer, Spotless, Checkstyle e cobertura passaram. “Começar os testes” neste marco significa iniciar caracterização, paridade, replay, desempenho e operação contra oráculos/ambientes autorizados. Com Q-MED-FND-01 concluída somente como fundação test-only, o indicador intermediário é <code>53/107 = 49,5%</code>, com 54 checkboxes pendentes. Das nove verticais Data Export, cinco estão implementadas somente em shadow (<code>55,6%</code>): Coletas, Manifestos, Cotações, Fretes e Localização. Seis perfis de entidade de caracterização estão preparados, com sete canais no total e zero execução de oráculo, e permanecem zero dos cinco fatos e zero dos 19 contratos SQL concluídos. V2-013 pai, todas as rotas Q-*-04, V2-034a/V2-034b, V2-015, V2-046a/V2-046b, V2-047 agregada, V2-050 pai, todos os gates de escala por entidade/saída e V2-038 permanecem abertos. Não existe rota <code>AGORA</code> elegível: os demais avanços dependem dos subgates externos separados de V2-041, oráculos/fontes históricas, identidade/principals, remote/CI, feed/NVD, baseline de vulnerabilidades, referências, retenção/infra e owners consumidores; nenhum Bloco 51 foi atribuído.
- **Bloco 37 — V2-026 (IMPLEMENTADA_EM_SHADOW, rollback-only):** a vertical Manifestos 6399 agora possui mapper/DTO local, batches limitados a 100 observações, gateways internos deny-all, reducers MAN-01/MAN-02/MAN-04/MAN-07 e migration V012. A raiz é escopada por <code>INTEGER:sequence_code</code>; Pick e MDF-e pertencem exclusivamente à raiz, e <code>mdfe_status</code> continua escalar da raiz. O staging preserva observações físicas append-only, presença tri-state, competência/frescor UTC e candidatos Manifesto→Coleta apenas em <code>recon</code>, sem FK, lookup ou publicação. O exercício V043 e o gate progressivo V001–V012 passaram apenas em <code>localhost/ETL_SISTEMA_V2_SHADOW</code>, em transações revertidas; a prova em duas sessões confirmou contenção, isolamento por environment e readquisição do lock. A suíte Maven offline Java 17 passou com 585 testes. Não houve rede, API, credencial, payload real, banco externo, deploy, commit ou push. Qualificação histórica e externa começa somente em Q-MAN-01, que depende de oráculo ESL explicitamente autorizado; V2-046a, V2-012b, V2-013, publicação, sweep, fatos e cutover continuam abertos.
- **Bloco 38 — V2-035c (DECISÃO LOCAL FAIL-CLOSED, concluída; reavaliação V02 sem novo bloco):** a evidência local 6399 prova somente atributos de frota preservados no grão da observação física de Manifesto; não prova registros mestres. Veículos e Motoristas terminam separadamente como <code>BLOCKED</code>: não há source key estável, business key aceita, filial de cadastro, vigência ou rekey para nenhuma dimensão; placa não é identidade, nome nunca é identidade, e homônimos/genéricos continuam irresolvidos. Principal e cada reboque permanecem papéis distintos; contexto de filial e coocorrência veículo–motorista não formam chave nem relação. O catálogo <code>docs/catalogos/frota-manifestos-v2-035c/</code> e o ADR 0025 congelam presença, bruto/Unicode, frescor da observação, replay, conflito, ausência e quarentena. A reavaliação autorizada no chat não encontrou contrato técnico novo: os 14 hashes da V01 permanecem íntegros, o anexo aberto é apenas um prompt operacional anterior e a busca temporal pré-V02 encontrou zero arquivo novo após o fechamento. A V02 exige cumulativamente ID imutável do fornecedor ligado a source/tenant e lifecycle próprio para ambas as dimensões; placa/filial/reatribuição para Veículos; homônimos/genéricos/renomeação/merge/split/contrato para Motoristas; somente a separação principal/reboques ficou <code>PROVEN_LIMITED</code>. Como nenhum pacote atende todos os requisitos, não foi criada nova fatia, rota ou bloco; nenhum prompt Terra existe e D03/D04 não iniciam implementação.
- **Bloco 36 — P01 + V03 (DECISÃO LOCAL CONSERVADORA, sem implementação):** a reauditoria corretiva do 6399 refutou definitivamente o source path sintético <code>pick_sequence_code</code> e fixou <code>/mft_pfs_pck_sequence_code</code>. No corpus estático já versionado do legado, 228 linhas físicas representam 100 raízes, 193 chaves naturais raiz+pick e 43 chaves naturais raiz+MDF-e, sem colisão observada; 37 repetições raiz+MDF-e são a mesma observação de filho expandida por picks. P01 fecha somente no limite <code>COMPLETE_LOCAL_CONSERVATIVE_FAIL_CLOSED</code>: raiz por <code>/sequence_code</code> inteiro na tuple escopada, pick por raiz+<code>/mft_pfs_pck_sequence_code</code> inteiro e MDF-e por raiz+<code>/mft_mfs_key</code> textual exato; <code>/mft_mfs_number</code> é atributo correlato, nunca identidade. O par número+chave aparece junto em 80 linhas e ausente em 148; <code>/mdfe_status</code> é <code>VALUE</code> nas 228 e não diverge por raiz, portanto é escalar da raiz replicado pela expansão e sua presença isolada não cria filho nem quarentena de chave. Cardinalidades contratuais são 0..N, sem transformar os máximos observados em limites do fornecedor; colisão, root key inválida, número sem chave, par MDF-e assimétrico, rekey e conflito residual no mesmo frescor ficam em quarentena. V2-026a/V03 congela presença <code>ABSENT/NULL/VALUE</code>, o único frescor <code>finished_at → closed_at → departured_at → created_at</code>, reducers separados de raiz/pick/MDF-e, competência <code>departured_at</code> com fallback <code>created_at</code> e MAN-07 preservando zero como valor. A relação Manifesto→Coleta permanece apenas candidata para V2-046a. Nenhum Java, migration, schema, tabela, procedure, view, fato, grant, integração ou runtime foi criado; V04 é somente o próximo bloco, ainda não executado.
- **Bloco 35 — V2-027 (IMPLEMENTADA_EM_SHADOW, rollback-only):** COT-01/COT-02 receberam a decisão versionada `docs/catalogos/cotacoes-v2-027/decisao-v01.json`, mapper 6906 estrito com presença tri-state, os três instantes preservados e precedência `nfse_issued_at → cte_issued_at → requested_at`, staging/promoção JDBC set-based e migration V011 com manifesto/fingerprint/validator estrutural. `sequence_code` é canonicalizado como inteiro type-tagged, exige `source_instance` e `tenant_scope` explícitos e rejeita `DEFAULT/GLOBAL/SINGLETON`; os campos de UF usam exclusivamente `/data/qoe_qes_ony_sae_code` e `/data/qoe_qes_diy_sae_code` ancorados na matriz V2-017a. O exercício SQLCMD V041 passou somente em `localhost/ETL_SISTEMA_V2_SHADOW`, carregando baseline V011, gate progressivo 005 e validator COT-01/COT-02 dentro de uma única transação revertida: uma referência sintética `QUOTE_TARIFF` ratificada para `SHADOW` comprovou rota SP→RJ, vigência, moeda/unidade/arredondamento e `DECIMAL(19,4)`; retry, no-op por hash, out-of-order, replay, duplicata entre páginas, empate divergente em quarentena e ausência sem sweep também passaram. A prova em duas sessões confirmou contenção por namespace, isolamento por ambiente e nova aquisição após rollback. Após todos os exercícios, `core.cotacao`, as procedures de Cotações e `ctl.flyway_schema_history` continuaram ausentes. A tarifa exige `reference_release_id` explícito, família `QUOTE_TARIFF`, ratificação `SHADOW` não revogada, rota vigente única e `PRICED`; não há zero, wildcard, matriz legada ou release corrente implícita. Sweep, publicação, paridade, integração externa e cutover continuam bloqueados.

- A V2 possui uma fundação técnica melhor que a do legado: Java 17, Maven Wrapper, fronteiras modulares, cliente Data Export tipado, paginação streaming, limites, timeout, retry restrito, circuit breaker e auditoria mínima sanitizada de travessia.
- Ela ainda não executa um ETL de domínio completo. <code>Main</code> atende <code>--help</code>, <code>--version</code>, <code>config validate</code> e <code>dry-run</code>; estes dois últimos fazem apenas preflight sem efeitos. Não há composição operacional de banco, pipeline, scheduler, staging, promoção, persistência de negócio, reconciliação ou materialização.
- Coletas 6908 e Fretes 6389 já possuem domínio, mapper, staging/promoção, enforcement físico e implementação somente em shadow; ainda não possuem caracterização autorizada, bootstrap, relações, paridade, sweep, publicação ou cutover. A implementação de Fretes preserva somente candidatos relacionais e mantém V2-046a/V2-046b abertas.
- A fundação Flyway ativa usa V001/V002 para os schemas <code>ctl</code>, <code>stg</code>, <code>core</code>, <code>ref</code>, <code>mart</code>, <code>pub</code> e <code>recon</code>, ownership/roles mínimos e manifesto/fingerprint; V003–V006 acrescentam control plane, kernel técnico, lifecycle comum e o framework fail-closed de observabilidade/Data Quality. Nenhuma dessas migrations antecipa payload, view ou objeto de vertical. A prova local V001–V003 anterior, inclusive o schema <code>shadow</code>, foi preservada em <code>database/evidence/historical-shadow-v001-v003/</code> e não é migration ativa.
- Das nove verticais operacionais Data Export, Coletas, Manifestos, Cotações, Fretes e Localização estão implementadas somente em shadow; Contas a Pagar, Faturas por Cliente, Inventário e Sinistros ainda não possuem vertical completa. Usuários possui current/history apenas em shadow pela ponte GraphQL. Raster, dimensões/referências restantes, cinco fatos/materializações e contratos SQL publicados continuam pendentes ou condicionais.
- O legado continua sendo o único escritor produtivo e dono estrutural de <code>ETL_SISTEMA</code> até o cutover formal. V2-048a ratificou localmente <code>CUTOVER-DB-01/DATABASE_WIDE</code> como unidade segura enquanto rota e write-fence granulares não forem positivamente provados; a V2 permanece exclusivamente em sombra.
- O repositório V2 está na branch <code>main</code>, com baseline local <code>c59489cc70be9c113cdf444118dd1342c0b13903</code> e sem remote. CI, Gitleaks e Dependency Check continuam apenas configurados localmente, não ativos ou verdes em um provedor; a evidência versionada está limitada a esse histórico Git local, sem revisão humana, publicação ou prova de provedor.
- Todas as referências internas usam o nome canônico <code>STATES.md</code>; os links foram validados componente a componente com comparação case-sensitive. A política de linha versionada em <code>.gitattributes</code> fixa LF para texto e preserva CRLF somente nos launchers Windows.

## Objetivo do V2

Construir gradualmente um ETL Data Export-first, modular, testável e operável, preservando as regras de negócio e os contratos analíticos válidos do legado sem herdar seus acoplamentos e caminhos frágeis.

Coletas e Fretes continuam sendo a primeira onda:

- Migrar **Coletas** do GraphQL para o template Data Export **6908 — Relação de Coletas Detalhada**.
- Migrar **Fretes** do GraphQL para o template Data Export **6389 — Relação de Fretes Detalhada**.

O objetivo final, agora explicitamente mapeado, inclui também:

- Manifestos, Cotações, Localização de Cargas, Contas a Pagar, Faturas por Cliente, Inventário e Sinistros via Data Export.
- O alvo “todos via Data Export” abrange exatamente as nove entidades operacionais ESL deste catálogo: Coletas, Fretes e as sete verticais da linha anterior.
- Usuários do sistema só migra para Data Export se o fornecedor/owner entregar um template oficial e seu contrato passar pelos mesmos gates; até lá permanece na ponte GraphQL. O número legado <code>9901</code> não é template Data Export comprovado.
- Raster viagens/paradas pertence a outra API e continua condicional, se a operação aprovar sua permanência.
- Planejamento de janelas, backfill, late data, replay, recovery, fechamento mensal, reconciliação e execução recorrente.
- Auditoria, watermarks, locks, idempotência, quarentena, integridade e Data Quality.
- Referências governadas, dimensões, cinco fatos/materializações e contratos SQL pertencentes ao ETL.
- Empacotamento, observabilidade, segurança operacional, runbooks, dual-run, cutover pela unidade real de roteamento/fence e desativação controlada do legado.

Não haverá reescrita big-bang, dois escritores produtivos simultâneos ou desligamento implícito do GraphQL/legado.

## Arquitetura — estado atual e destino

- O destino é um monólito modular com toolchain fixada em Java 17, orientado a casos de uso e com um único composition root explícito.
- Fluxo-alvo: entrada CLI/scheduler → aplicação/orquestração → domínio → portas → adaptadores de fonte, persistência e observabilidade.
- O domínio não depende de HTTP, Data Export, GraphQL, JDBC, CLI, DTOs externos ou framework.
- DTO de API, modelo de domínio, registro de staging e modelo publicado são tipos distintos. Presença e ausência de campo devem ser representadas sem confundir “não veio” com “veio nulo”.
- MVC, se existir, fica somente na borda; não organiza o núcleo do ETL.
- JPA/Hibernate pode atender cadastros pequenos. Carga, staging, promoção, reconciliação, fatos e agregações permanecem SQL/JDBC set-based.
- O cliente Data Export genérico já suporta filtros tipados, timezone IANA, serialização segura, normalização de resposta, paginação streaming, limites de páginas/registros/bytes, retry limitado e circuit breaker.
- A travessia atual encerra qualquer resposta vazia como <code>COMPLETED</code>, embora a verificação permaneça <code>LOCAL_TERMINAL_UNVERIFIED</code>. Isso prova apenas que o cliente encontrou uma página vazia; não detecta vazio anômalo e não prova extração, staging, promoção, snapshot consistente, ordenação total ou cobertura global.
- A fundação comum ativa já possui schemas/roles, control plane, staging técnico com candidate set imutável, protocolo positivo atômico de aplicação técnica ao <code>core</code>, framework de observabilidade/DQ e gates de publicação. Payloads/domínios existem somente nas cinco verticais implementadas em shadow e continuam sem composição operacional de <code>DataSource</code> no runtime; login/socket timeout e cancelamento JDBC pertencem à composição de V2-022.
- A direção de schema ratificada no ADR 0006 é <code>ctl</code> (controle), <code>stg</code> (transitório tipado por execução), <code>core</code> (domínio canônico), <code>ref</code> (referências), <code>mart</code> (fatos), <code>pub</code> (contratos SQL) e <code>recon</code> (métricas/evidências). O modo sombra pertence ao ambiente/banco, não ao modelo de negócio; o schema vazio <code>shadow</code> foi retirado da história Flyway ativa em V2-019.
- GraphQL será adaptador transitório somente para dual-run, Usuários e campos sem equivalente comprovado. Cada dependência GraphQL precisa de motivo, campos, responsável, prazo e plano de remoção.

## Catálogo de fontes e identidade-alvo

O contrato ativo no Frankenstein e o contrato-alvo não podem ser misturados. Os valores de <code>per</code> abaixo são configurações legadas, não garantias do fornecedor nem defaults aprovados para a V2.

| Domínio | Contrato ativo no legado | Contrato-alvo V2 | Partição/ordem observada | Identidade/grão de trabalho | Estado |
|---|---|---|---|---|---|
| Usuários | GraphQL <code>individual(enabled=true)</code>, tratado pelo legado como snapshot | mesmo GraphQL como ponte; Data Export somente após template oficial, nunca <code>9901</code> inferido | cursor, máximo observado de 20 por página | <code>user_id</code> da fonte; current + history por mudança de hash | sonda parcial de paginação; nenhum template Data Export comprovado |
| Coletas | GraphQL principal | Data Export 6908; GraphQL apenas para paridade/campos ausentes | <code>picks.request_date</code>; <code>scopes.by_updated_at</code> somente complementar | <code>id</code> da fonte; <code>sequence_code</code> é business key | contrato local e vertical implementada somente em shadow; caracterização/relações/paridade pendentes |
| Fretes | GraphQL principal + 6389 para performance | Data Export 6389; GraphQL como ponte controlada | <code>freights.service_at</code>; <code>scopes.by_updated_at</code> ainda não é watermark | <code>id</code> da fonte; minuta é business key | contrato local e vertical implementada somente em shadow; relações/paridade pendentes |
| Manifestos | Data Export 6399 | Data Export 6399 | <code>manifests.service_date</code>; legado <code>sequence_code asc</code>/<code>per=100</code> | agregado/fato por <code>sequence_code</code>; detalhes em grão composto explícito | contrato local e vertical implementada somente em shadow; relação/paridade pendentes |
| Cotações | Data Export 6906 | Data Export 6906 | <code>quotes.requested_at</code>; legado <code>sequence_code asc</code>/<code>per=1000</code> | <code>sequence_code</code> | contrato local e vertical implementada somente em shadow; paridade pendente |
| Localização de Cargas | Data Export 8656 | Data Export 8656 | <code>freights.service_at</code>; <code>sequence_number asc</code> é aceito, mas o payload expõe <code>corporation_sequence_number</code> | <code>corporation_sequence_number</code> inteiro type-tagged, escopado por origem e tenant | contrato local e vertical implementada somente em shadow; relação/paridade pendentes |
| Contas a Pagar | Data Export 8636 | Data Export 8636 | <code>accounting_debits.issue_date</code>; busca aninhada; legado <code>issue_date desc</code>/<code>per=100</code> | candidato de parcela: <code>ant_ils_sequence_code</code>; identidade da raiz contábil não é exposta | sonda de uma página; cutover condicionado |
| Faturas por Cliente | Data Export 4924 | Data Export 4924 | <code>freights.service_at</code>; <code>unique_id asc</code> legado é aceito, mas não publicado no payload | candidato de linha: <code>id</code>; título lógico via crosswalk/cardinalidade | sonda de uma página; vertical ausente |
| Inventário | Data Export 10633 | Data Export 10633 | <code>check_in_orders.started_at</code>; legado <code>sequence_code asc</code>/<code>per=100</code> | candidato de agregado: <code>sequence_code</code>; filhos preservam expansão física tipada | sonda de uma página; vertical ausente |
| Sinistros | Data Export 6392 | Data Export 6392 | <code>insurance_claims.opening_at_date</code>; legado <code>sequence_code asc</code>/<code>per=100</code> | candidato: <code>sequence_code</code>; relações filhas não alteram o grão sem prova | sonda de uma página; vertical ausente |
| Raster | API Raster opcional | módulo condicional, desligado por padrão | viagens/paradas; legado divide ao atingir 500 | viagem <code>cod_solicitacao</code>; parada <code>cod_solicitacao+ordem</code> | não sondado; necessidade operacional pendente |

## Contratos já investigados

### Coletas — template 6908

- Raiz de busca: <code>picks</code>; a requisição usa <code>picks.request_date</code>, enquanto o <code>/info</code> publicou o nome desqualificado <code>request_date</code>.
- Filtro complementar: <code>scopes.by_updated_at</code>. Ele não substitui o filtro obrigatório da data de solicitação.
- O filtro temporal usa <code>yyyy-MM-dd HH:mm:ss - yyyy-MM-dd HH:mm:ss</code>, sem offset, interpretado em <code>America/Sao_Paulo</code>. O fuso não pode vir da máquina.
- O <code>updated_at</code> apareceu preenchido e com offset nas amostras; isso não prova monotonicidade, snapshot ou o fuso do filtro.
- O <code>/info</code> observado tinha 31 campos sem tipos. O <code>/data</code> confirmou <code>id</code> numérico, <code>sequence_code</code>, <code>updated_at</code> e campos de manifesto, mas a amostra desses campos tinha nulos e não prova cobertura.
- <code>per</code> limita entidades distintas por <code>id</code>; o relatório detalhado pode expandir uma entidade em várias linhas físicas.
- Uma janela fechada com <code>per=100</code> terminou na quarta página vazia: 246 entidades distintas em 258 linhas, sem sobreposição de <code>id</code> ou <code>sequence_code</code> entre páginas.
- Outra janela fechada teve 15 entidades tanto no Data Export quanto no GraphQL terminal, com relação 1:1 e conjuntos iguais por <code>id</code>/<code>sequence_code</code>. Repetições com <code>per=5</code> e <code>per=10</code> produziram o mesmo conjunto.
- A evidência comprova apenas as janelas sondadas. Não comprova ordem total, cursor, snapshot consistente ou cobertura global garantida pelo fornecedor.

### Fretes — template 6389

- Raiz de busca: <code>freights</code>; a requisição usa <code>freights.service_at</code> e expõe <code>scopes.by_updated_at</code>.
- O filtro temporal usa o mesmo formato local e o fuso obrigatório <code>America/Sao_Paulo</code>.
- <code>updated_at</code> apareceu preenchido nas amostras, mas permanece classificado como <code>UNVERIFIED</code>. Não pode avançar watermark sem prova de semântica e monotonicidade.
- O <code>/info</code> observado tinha 109 campos, sem tipos e sem listar <code>id</code>; o <code>/data</code> confirmou <code>id</code>, <code>updated_at</code>, <code>corporation_sequence_number</code> e <code>fit_p_m_pck_sequence_code</code>.
- <code>reference_number</code> não teve cobertura na amostra. IDs de contas GraphQL não possuem equivalente comprovado; campos <code>fit_ant_*</code> não podem ser inferidos como IDs contábeis.
- Uma janela fechada com <code>per=100</code> terminou na nona página vazia: 769 entidades distintas em 830 linhas, sem sobreposição de <code>id</code> ou minuta entre páginas.
- Outra janela fechada teve 34 entidades tanto no Data Export quanto no GraphQL terminal, com relação 1:1 e conjuntos iguais por <code>id</code>/minuta. Repetições com <code>per=10</code> e <code>per=25</code> produziram o mesmo conjunto.
- <code>fit_p_m_pck_sequence_code</code> é candidato de vínculo com Coletas, não FK comprovada e não substituto automático de <code>pick_item_id</code>.
- A paginação por número não deve ser tratada como determinística em empates. Staging, deduplicação, partição de janela e validação de completude são obrigatórios.

### Contratos ESL adicionais sondados em 29/08/2026

A autorização adicional do owner permitiu fechar tecnicamente lacunas dos demais contratos ESL. As chamadas foram seriais, read-only, sem redirect, com conexão de 10 s, teto de 30 s e 10 MiB por resposta, parada no primeiro erro e saída exclusivamente sanitizada. GraphQL usou uma <code>query</code> POST; nenhuma <code>mutation</code> foi enviada.

| Template | <code>/info</code> | Sonda <code>/data</code> em 15/07/2026, <code>per=3</code> | Conclusão segura |
|---|---|---|---|
| 6399 Manifestos | HTTP 200; 91 campos/18 filtros | GET diário: 4 linhas físicas/3 sequências; corpus estático legado já versionado: 228 linhas/100 raízes, 193 chaves raiz+pick e 43 raiz+MDF-e | GET + partição diária; raiz escopada por <code>sequence_code</code>, filhos naturais separados e nenhum máximo observado promovido a contrato do fornecedor |
| 6906 Cotações | HTTP 200; 37 campos/6 filtros | 3 linhas/3 <code>sequence_code</code>; sem <code>id</code> | <code>sequence_code</code> é a source key candidata comprovada na amostra |
| 8656 Localização | HTTP 200; 24 campos/8 filtros | 3 linhas/3 <code>corporation_sequence_number</code>; sem <code>id</code> nem campo <code>sequence_number</code> | separar o nome de ordenação legado da chave realmente publicada |
| 8636 Contas a Pagar | HTTP 200; 28 campos/9 filtros | 5 linhas físicas para <code>per=3</code>; 5 <code>ant_ils_sequence_code</code>; sem ID da raiz contábil | modelar parcela como filho; não declarar paginação completa sem identidade/contagem da raiz |
| 4924 Faturas por Cliente | HTTP 200; 52 campos/17 filtros | 3 linhas/3 <code>id</code> inteiros; <code>unique_id</code> ausente; documento não é universal | usar <code>id</code> como source key de linha e crosswalk separado para título/fretes |
| 10633 Inventário | HTTP 200; 26 campos/7 filtros | 27 linhas físicas/3 <code>sequence_code</code>; mapeamentos de invoices expandem a raiz | agregado por sequência e filhos tipados; nunca contar linha física como entidade |
| 6392 Sinistros | HTTP 200; 44 campos/6 filtros | 2 linhas/2 <code>sequence_code</code>; relações de minuta/invoice presentes | sequência como raiz; campos relacionais não compõem ID sem colisão provada |

Achados transversais:

- O 6399 respondeu HTTP 422 para uma janela mensal genérica, HTTP 404 para POST em <code>/data</code> e HTTP 200 para GET diário equivalente. O adaptador-alvo usa GET. Só classifica <code>REPARTITION/SHRINK_WINDOW</code> quando o código/categoria sanitizada provar “janela grande”; outro 422 é erro terminal. A redução é limitada e, na menor partição, falha em vez de aumentar timeout/repetir a mesma chamada.
- O <code>per</code> limita entidades-raiz em alguns relatórios, mas a resposta pode conter mais linhas físicas por expansão. Auditoria deve manter <code>physical_rows</code> e, somente quando a raiz for verificável, <code>source_entities/distinct_root_keys</code>; no 8636 essas duas métricas permanecem <code>UNVERIFIED</code> até ID/contador oficial.
- A primeira tentativa de percorrer duas páginas em várias famílias recebeu HTTP 429 no 8656 e parou, como exigido. Isso comprova throttling naquele cenário, não uma quota compartilhada do fornecedor. A V2 adota conservadoramente um limitador ESL global, orçamento compartilhado e respeito a <code>Retry-After</code> para evitar tempestade de clientes independentes.
- As sondas de uma página não provam terminalidade, ordem total ou snapshot. Essas propriedades continuam gates por vertical e não podem ser inferidas do HTTP 200.

### Matriz final de requisições cURL Data Export por entidade

Esta é a especificação executável da rodada final solicitada pelo owner, não evidência de que ela já ocorreu. A execução está bloqueada por V2-041. Depois da rotação e da comprovação de invalidação do segredo anterior, o owner ainda confirma janela e teto; então V2-025d executa serialmente as nove linhas, sem ampliar a rodada.

Cada linha exige primeiro um <code>GET /api/analytics/reports/{template}/info</code> e depois um único <code>GET /api/analytics/reports/{template}/data</code> mínimo. O formato lógico do segundo <code>curl</code> é:

<pre><code>curl.exe --silent --show-error --fail-with-body --request GET --get \
  --connect-timeout 10 --max-time 30 --max-filesize 10485760 \
  "{BASE_URL}/api/analytics/reports/{TEMPLATE}/data" \
  --data-urlencode "search[{ROOT}][{FIELD}]={RANGE}" \
  --data-urlencode "page=1" --data-urlencode "per=3" \
  --data-urlencode "order_by={ORDER}"
</code></pre>

O comando acima omite deliberadamente autenticação e valores reais. O runner injeta <code>Authorization: Bearer …</code> a partir do secret provider por canal protegido, sem imprimir ou persistir header/token e sem colocar segredo em documentação, URL, argumento de usuário ou relatório. Não seguir redirect, não usar GET com corpo, POST ou fallback de transporte, não repetir manualmente e não salvar payload. <code>per=3</code> é somente o teto conservador da sonda; não é configuração produtiva nem prova de quantidade de linhas físicas.

| Entidade | Template | Parâmetro(s) <code>search</code> da requisição | <code>order_by</code> de paridade | Chave que a sonda valida independentemente da ordem |
|---|---:|---|---|---|
| Coletas | 6908 | <code>search[picks][request_date]=AAAA-MM-DD HH:mm:ss - AAAA-MM-DD HH:mm:ss</code> | <code>sequence_code asc</code> | <code>id</code>; sequência é business key |
| Fretes | 6389 | <code>search[freights][service_at]=AAAA-MM-DD HH:mm:ss - AAAA-MM-DD HH:mm:ss</code> | <code>corporation_sequence_number asc</code> | <code>id</code>; minuta é business key |
| Manifestos | 6399 | <code>search[manifests][service_date]=AAAA-MM-DD - AAAA-MM-DD</code> | <code>sequence_code asc</code> | raiz agregada <code>sequence_code</code> + chaves de filhos pick/MDF-e |
| Cotações | 6906 | <code>search[quotes][requested_at]=AAAA-MM-DD - AAAA-MM-DD</code> | <code>sequence_code asc</code> | <code>sequence_code</code> |
| Localização de Cargas | 8656 | <code>search[freights][service_at]=AAAA-MM-DD - AAAA-MM-DD</code> | <code>sequence_number asc</code> somente para reproduzir a V1 | candidato publicado <code>corporation_sequence_number</code>; <code>sequence_number</code> não veio no payload observado |
| Contas a Pagar | 8636 | <code>search[accounting_debits][issue_date]=INÍCIO - FIM</code> e <code>search[accounting_debits][created_at]=DIA - DIA</code>, na mesma chamada de caracterização V1 | <code>issue_date desc</code> | parcela <code>ant_ils_sequence_code</code>; identidade da raiz continua ausente |
| Faturas por Cliente | 4924 | <code>search[freights][service_at]=AAAA-MM-DD - AAAA-MM-DD</code> | <code>unique_id asc</code> somente para reproduzir a V1 | <code>id</code> da linha; <code>unique_id</code> não veio no payload observado |
| Inventário | 10633 | <code>search[check_in_orders][started_at]=AAAA-MM-DD - AAAA-MM-DD</code> | <code>sequence_code asc</code> | raiz agregada <code>sequence_code</code> + filhos de invoice/frete |
| Sinistros | 6392 | <code>search[insurance_claims][opening_at_date]=AAAA-MM-DD - AAAA-MM-DD</code> | <code>sequence_code asc</code> | raiz <code>sequence_code</code> + cardinalidade das relações filhas |

Para 6908/6389, <code>scopes.by_updated_at</code> só entra em uma requisição complementar separada e aprovada, sempre junto da mesma partição de data de negócio; nunca substitui o filtro primário nem vira watermark por inferência. A tradução data-hora versus data civil, inclusão das bordas e timezone precisa de fixture antes de congelar o contrato. Para 8636, a contagem/oráculo tem de reproduzir simultaneamente <code>issue_date</code> e <code>created_at</code>; omitir um filtro compara outro conjunto e invalida a prova.

Baselines da V1 abaixo são evidência de paridade e dimensionamento, nunca defaults da V2:

| Template | Uso executável na V1 | <code>per</code> | Timeout | Caps por travessia |
|---:|---|---:|---:|---:|
| 6908 | ausente; Coletas usa GraphQL | — | — | — |
| 6389 | enriquece o Frete GraphQL por <code>corporation_sequence_number</code>; não ingere a entidade inteira | 1.000 | 120 s | 500 páginas/50.000 linhas |
| 6399 | ingestão de Manifestos | 100 | 120 s | 5.000 páginas/500.000 linhas |
| 6906 | ingestão de Cotações | 1.000 | 60 s | 500 páginas/10.000 linhas |
| 8656 | ingestão de Localização | 10.000 | 90 s | 1.200 páginas/150.000 linhas |
| 8636 | ingestão de Contas; janela global de emissão × dia de criação | 100 | 60 s | 500 páginas/10.000 linhas |
| 4924 | ingestão de Faturas por Cliente | 100 | 60 s | 1.200 páginas/150.000 linhas |
| 10633 | ingestão de Inventário | 100 | 90 s | 500 páginas/10.000 linhas |
| 6392 | ingestão de Sinistros | 100 | 60 s | 500 páginas/10.000 linhas |

A V1 usa GET com corpo por default e altera estado compartilhado do cliente ao tentar GET por query/POST; Manifestos ainda reduz <code>per</code>, aumenta timeout e chega a remover ordenação, e Contas tenta <code>per=50/25</code> com timeouts maiores. Nada disso será portado. O contrato V2 fixa <code>GET_WITH_QUERY</code> por template, isola configuração e separa identidade de auditoria de <code>order_by</code>; página numérica não é cursor determinístico.

### Fechamento bidirecional API → SQL → consumo

As contagens servem para detectar omissão, não para afirmar relação 1:1: a API pode expandir filhos, enquanto a tabela V1 contém colunas técnicas, derivadas e aliases. V2-017a precisa reconciliar cada caminho individualmente.

| Entidade | Campos no <code>/info</code> observado | Colunas físicas na tabela core V1 | Contratos ETL-owned afetados |
|---|---:|---:|---|
| Coletas | 31 | 45 | <code>vw_coletas_powerbi</code>, <code>vw_coletas_excluidas_origem</code>, <code>vw_dim_clientes</code>, MAT-05 |
| Fretes | 109 | 109 | <code>vw_fretes_powerbi</code>, <code>vw_inventario_powerbi</code>, <code>vw_dim_clientes</code>, MAT-01/MAT-02/MAT-03/MAT-05 |
| Manifestos | 91 | 100 | <code>vw_manifestos_powerbi</code>, <code>vw_fato_manifestos_dash</code>, <code>vw_coletas_powerbi</code>, dimensões de filial/veículo/motorista, MAT-02/MAT-05 |
| Cotações | 37 | 41 | <code>vw_cotacoes_powerbi</code> |
| Localização de Cargas | 24 | 27 | <code>vw_localizacao_cargas_powerbi</code>, <code>vw_fretes_powerbi</code>, MAT-01/MAT-03 |
| Contas a Pagar | 28 | 31 | <code>vw_contas_a_pagar_powerbi</code>, dimensões de filial/plano de contas |
| Faturas por Cliente | 52 | 38 | <code>vw_faturas_por_cliente_powerbi</code>, dimensões de cliente/filial, MAT-03/MAT-04 |
| Inventário | 26 | 32 | <code>vw_inventario_powerbi</code>, <code>vw_fretes_powerbi</code>, MAT-02 |
| Sinistros | 44 | 36 | <code>vw_sinistros_powerbi</code> |
| **Total operacional Data Export** | **442** | **459** | subconjunto das 19 views e todos os cinco fatos já mapeados |

O recorte completo acrescenta 18 colunas físicas de Usuários (<code>current</code> + histórico) e, se Raster for mantido, 58 colunas de viagem/parada: 477 colunas sem Raster e 535 com Raster. Coletas ainda possui 17 campos lidos apenas em <code>metadata</code>. Todo campo do <code>/info</code>/<code>/data</code>, selection set GraphQL, coluna V1, metadata-only, derivado, filho e coluna publicada precisa terminar classificado como <code>PRESERVE/PROMOTE/NORMALIZE/SPLIT/DERIVE/ALIAS/RETIRE</code>, com zero <code>UNCLASSIFIED</code>. Contagem diferente após nova sonda é drift a investigar, não autorização para apagar/adicionar coluna automaticamente.

### Usuários GraphQL sondados em 29/08/2026

- A query <code>individual(params:{enabled:true})</code> expõe <code>id</code>, <code>name</code> e cursor, mas nenhum campo temporal no contrato usado pelo legado.
- O servidor limitou a página a 20. Uma travessia controlada de 60 páginas leu 1.200 IDs distintos, sem duplicação, e ainda informou <code>hasNextPage=true</code>; a sonda parou no teto sem persistir cursor ou payload.
- Portanto, a interface é candidata a snapshot paginado, não incremental por <code>updatedAt</code>; a sonda não alcançou terminal nem provou snapshot consistente/cursor entre processos. Cursor só pode retomar a mesma execução se validade/TTL forem testados; caso contrário, reiniciar da página 1 em staging novo. Desativação exige V2-013 com duas observações independentes/guardrails.

### Limites desta autorização e incidente de segredo

- Nenhuma sonda autoriza escrita, mutation, banco, job, deploy, credencial nova, Raster ou cutover.
- A solicitação final do owner cobre a matriz read-only das nove entidades ESL, mas não supera o gate de segredo V2-041 nem altera permanentemente a allowlist mais estreita do <code>AGENTS.md</code>. Como não foi possível executar a rodada com segurança, depois da rotação o owner confirma novamente janela/teto antes de V2-025d; chamadas posteriores exigem nova autorização por rodada ou alteração formal da regra.
- Durante o inventário inicial do <code>.env</code>, valores atuais de credenciais foram acidentalmente exibidos no transcript da sessão. Eles não foram copiados para este arquivo nem para outro artefato, mas devem ser tratados como comprometidos: inventariar consumidores, coordenar revogação/rotação sem interromper o legado, atualizar o secret store e comprovar a invalidação de forma autorizada antes de novo uso externo/release.

## Mapa de capacidades a portar

| Capacidade do legado | Evidência atual | Estado na V2 | Direção |
|---|---|---|---|
| CLI, códigos de saída e composição do pipeline | ligada ao runtime e testada no legado | somente help/version | portar casos de uso mínimos e composition root explícito |
| Fluxo completo, intervalo, microbatch, backfill e late data | ligados ao runtime | ausente | redesenhar como DAG/registry tipado |
| Lock global com <code>sp_getapplock</code> | ligado ao runtime | ausente | portar comportamento e testar concorrência |
| Recovery/replay idempotente | tabela, ownership, TTL e estados no legado | ausente | consolidar no control plane |
| Auditoria de execução/página | funcional no legado; sintética no V2 | parcial | generalizar e compor no runtime |
| Watermark executivo | regra válida, implementação legada parcial | somente <code>UNVERIFIED</code> | separar fonte, execução e confirmação transacional |
| Backfill de Coletas e pós-hidratação de órfãos | ligado ao pipeline | ausente | portar regra sem arquivo de estado frágil |
| Reconciliação Coletas com duas confirmações | evidenciada e usada | backlog genérico | portar após snapshot Data Export comprovado |
| Sweep Manifestos/Faturas e prune Fretes | parcial/opt-in no legado | ausente | redesenhar por entidade; nunca presumir universal |
| Integridade autorizativa | ligada ao runtime | ausente | criar gate fail-closed |
| Data Quality | cinco famílias de checks; parte fail-open | ausente | reescrever, não copiar queries frágeis |
| Daemon/agendamento/fechamento mensal | operacional no legado | ausente | escolher um único modelo de execução por ADR |
| Observabilidade e MDC | funcional, porém duplicada em arquivos | inicial | logs estruturados, métricas, alertas e retenção |
| Segurança operacional | SQLite local com bypass possível | ausente | decidir identidade de serviço/RBAC; não copiar |
| Empacotamento executável | fat JAR no legado | JAR simples no V2 | decidir e criar artefato com smoke test |

### Inventário estrutural do legado

A auditoria estática encontrou 34 responsabilidades de tabelas finais no legado. Isso não confirma uso produtivo de cada objeto. A V2 não precisa reproduzir 34 tabelas com os mesmos nomes, mas deve mapear cada responsabilidade como **manter**, **consolidar**, **substituir** ou **retirar**:

- Operacionais: <code>coletas</code>, <code>fretes</code>, <code>manifestos</code>, <code>cotacoes</code>, <code>localizacao_cargas</code>, <code>contas_a_pagar</code>, <code>faturas_por_cliente</code>, <code>inventario</code>, <code>sinistros</code>, <code>raster_viagens</code> e <code>raster_viagem_paradas</code>.
- Usuários: <code>dim_usuarios</code> e <code>dim_usuarios_historico</code>.
- Referências: calendário, alias de região de destino, CNPJs de frota própria, atribuição de filial, região logística e status de coleta.
- Controle: logs/auditorias, histórico, watermark, inválidos, replay, quarentena e controle de migrations.
- Analítico: cinco tabelas fato/materializadas.

O legado possui 35 scripts em <code>database/tabelas</code>, mas um deles não representa uma nova tabela final. O alvo deve ser derivado de manifesto de schema, não da contagem de arquivos.

#### Matriz final das 34 responsabilidades de tabela

Nesta matriz, **MANTER** significa preservar a responsabilidade e o contrato necessário, não copiar o DDL; **SUBSTITUIR** redesenha o modelo no domínio V2; **CONSOLIDAR** absorve tabelas técnicas redundantes; **RETIRAR** elimina responsabilidade obsoleta; **CONDICIONAL** depende de decisão formal. V2-017 ratifica a classificação, V2-009d/migration da vertical fecha o físico e V2-040 confirma que nada ficou sem destino.

| Script/tabela legada | Grão/chave física observada | Destino limpo obrigatório | Decisão e tarefa dona |
|---|---|---|---|
| 001 <code>coletas</code> | PK <code>id</code>; <code>sequence_code</code> único | <code>core.coletas</code> com surrogate + source key/alias escopados | SUBSTITUIR — V2-009a/V2-010 |
| 002 <code>fretes</code> | PK <code>id</code>; minuta é business key | <code>core.fretes</code> com presença, frescor, financeiro e relações tipadas | SUBSTITUIR — V2-009a/V2-011 |
| 003 <code>manifestos</code> | identity + hash composto; múltiplas linhas físicas | raiz por sequência e filhos pick/MDF-e com chaves naturais | SUBSTITUIR — V2-009b/V2-026 |
| 004 <code>cotacoes</code> | PK <code>sequence_code</code> | <code>core.cotacoes</code> e tarifa em referência vigente | SUBSTITUIR — V2-027/V2-035a |
| 005 <code>localizacao_cargas</code> | PK legada <code>sequence_number</code> | <code>core.localizacao_cargas</code> com chave da fonte provada | SUBSTITUIR — V2-009b/V2-028 |
| 006 <code>contas_a_pagar</code> | PK legada <code>sequence_code</code> | raiz/parcela tipadas; ausência desabilitada sem identidade da raiz | SUBSTITUIR — V2-009b/V2-029 |
| 007 <code>faturas_por_cliente</code> | PK <code>unique_id</code> | linha de origem + título + relações CT-e/NFS-e/Frete por crosswalk | SUBSTITUIR — V2-009b/V2-030 |
| 008 <code>dim_calendario</code> | PK <code>data</code>; UQ <code>data_key</code> | calendário rolante governado, inclusive dia útil/faturamento | MANTER — V2-035a |
| 009 <code>log_extracoes</code> | evento identity por entidade/tempo | execução/etapa/contagens no control plane, sem log duplicado | CONSOLIDAR — V2-020/V2-023 |
| 010 <code>page_audit</code> | UQ execução + template + página | auditoria imutável de request/página/tentativa e terminalidade | CONSOLIDAR — V2-020/V2-023 |
| 011 <code>dim_usuarios</code> | PK <code>user_id</code> | estado atual de Usuários; publicação dimensional separada | SUBSTITUIR — V2-033/V2-035b |
| 012 <code>sys_execution_history</code> | execução identity | ciclo/plano/execução/estado durável | CONSOLIDAR — V2-020/V2-022 |
| 013 <code>sys_auditoria_temp</code> | tabela técnica sem PK canônica | nenhum objeto equivalente; evidência válida vai ao control plane/quarantine | RETIRAR — V2-020 |
| 014 <code>sys_execution_audit</code> | PK execução UUID + entidade | auditoria por entidade/partição e transições imutáveis | CONSOLIDAR — V2-020 |
| 015 <code>sys_execution_watermark</code> | PK somente por entidade | ledger por fonte/tenant/entidade/modo/partição e fronteira contígua | SUBSTITUIR — V2-020 |
| 017 <code>dim_usuarios_historico</code> | history identity ligado a <code>user_id</code> | histórico append-only somente em mudança de hash | MANTER — V2-033 |
| 018 <code>schema_migrations</code> | PK textual <code>migration_id</code> | histórico nativo Flyway + manifesto/fingerprint | RETIRAR/SUBSTITUIR — V2-019 |
| 019 <code>etl_invalid_records</code> | rejeição identity por entidade/data | <code>stg</code>/quarantine com motivo, regra, execução, SLA e replay | SUBSTITUIR — V2-021/V2-023/V2-045 |
| 020 <code>inventario</code> | PK hash <code>identificador_unico</code> | raiz por sequência e filhos tipados, sem hash como identidade | SUBSTITUIR — V2-009b/V2-031 |
| 021 <code>sinistros</code> | PK hash <code>identificador_unico</code> | raiz por sequência e ocorrências/relações explícitas | SUBSTITUIR — V2-009b/V2-032 |
| 022 <code>sys_replay_idempotency</code> | PK <code>idempotency_key</code> | idempotência e ownership no ledger do control plane | CONSOLIDAR — V2-020/V2-022 |
| 023 <code>sys_reconciliation_quarantine</code> | PK entidade + registro + janela | backlog/recon/quarantine por execução, regra e partição | SUBSTITUIR — V2-013/V2-020/V2-045 |
| 024 <code>raster_viagens</code> | PK <code>cod_solicitacao</code> | raiz Raster somente se a operação decidir manter | CONDICIONAL — V2-034a/V2-034b |
| 025 <code>raster_viagem_paradas</code> | PK <code>cod_solicitacao+ordem</code>; FK para viagem | filhos atômicos da viagem, somente se Raster for mantido | CONDICIONAL — V2-034a/V2-034b |
| 026 <code>localizacao_cargas_regiao_destino_alias</code> | PK nome do responsável | alias versionado de filial/região, com vigência e aprovação | MANTER — V2-035a |
| 027 <code>manifestos_frota_propria_cnpjs</code> | PK CNPJ normalizado | referência governada de frota própria; conteúdo produtivo exportado com autorização | MANTER — V2-035a |
| 028 <code>fato_gestao_vista_fretes</code> | UQ físico indicador + minuta + data; loader faz match só por indicador + minuta | fato operacional PE/CB em <code>mart</code>; grão exige ADR/prova | MANTER — V2-036/MAT-01 |
| 029 <code>fato_gestao_vista_coletores</code> | UQ data + filial + classificação | fato diário de coletores em <code>mart</code> | MANTER — V2-036/MAT-02 |
| 030 <code>fato_fretes_faturamento</code> | UQ físico frete + data; loader/validator tratam uma linha por frete | fato de faturamento em <code>mart</code>; grão exige ADR/prova | MANTER — V2-036/MAT-03 |
| 031 <code>fato_gestao_vista_faturas</code> | UQ físico <code>unique_id</code> + emissão; loader/validator usam só <code>unique_id</code> | fato de faturas; grão/rekey exigem ADR/paridade | MANTER — V2-036/MAT-04 |
| 032 <code>fato_gestao_vista_manifestos</code> | PK <code>sequence_code</code> | fato de Manifestos em <code>mart</code> | MANTER — V2-036/MAT-05 |
| 033 <code>regras_atribuicao_filial</code> | identity; UQ de pagador ativo | referência vigente pagador→filial, sem hardcode em procedure | MANTER — V2-035a |
| 034 <code>dim_regiao_logistica_rules</code> | identity; regra CEP ou cidade/UF | referência vigente, prioritária e sem ranges conflitantes | MANTER — V2-035a/COL-08 |
| 035 <code>dim_status_coleta</code> | PK código bruto | catálogo versionado bruto→canônico→rótulo | MANTER — V2-035a/COL-03/COL-04 |

O script 016 apenas altera estado/histórico de <code>dim_usuarios</code> e, por isso, está absorvido pelas linhas 011/017. Índices, checks, FKs e colunas do legado são evidência de contrato, não defaults: cada constraint V2 nasce com a migration da responsabilidade, teste positivo/negativo e justificativa de cardinalidade.

As migrations legadas <code>001</code>, <code>002</code> e <code>004–048</code> foram arquivadas; não existe migration <code>003</code>. São 47 arquivadas + 11 ativas (<code>049–059</code>) = 58 scripts de migration. O executor manual não lista a tabela 035 nem o índice 004 e depende das migrations 053/055; o validator 034 omite <code>dim_status_coleta</code>, <code>vw_coletas_excluidas_origem</code> e migrations 051–059. A V2 deve criar história Flyway limpa, manifesto gerado e equivalência entre banco novo e banco baselineado, sem confiar em contagem fixa ou no README legado.

O ledger SQL completo auditado é: 35 scripts de tabela/34 responsabilidades finais, cinco procedures, 19 views ETL-owned, quatro scripts de índices, 58 migrations e 24 validações. Os três auxiliares receberam classificação inicial em V2-017 e confirmação final em V2-040: <code>database/reprocessamento/001_reprocessar_fato_manifestos_competencia_operacional.sql</code> (substituir por comando/execução governada), <code>database/seguranca/024_configurar_permissoes_usuario.sql</code> (substituir por roles/grants Flyway) e <code>database/security_sqlite/001_init_auth_schema.sqlite.sql</code> (retirar com a autenticação local). Validações destrutivas e consumer-specific continuam não portáveis conforme as seções abaixo.

### Contratos SQL pertencentes ao ETL

Foram encontrados estaticamente cinco procedimentos de materialização e 19 views finais pertencentes ao ETL; uso produtivo e contrato consumidor ainda precisam de manifesto/aceite externo:

- Procedures: <code>sp_carga_fato_gestao_vista_fretes</code>, <code>sp_carga_fato_gestao_vista_coletores</code>, <code>sp_carga_fato_fretes_faturamento</code>, <code>sp_carga_fato_gestao_vista_faturas</code> e <code>sp_carga_fato_gestao_vista_manifestos</code>.
- Views operacionais/analíticas: <code>vw_faturas_por_cliente_powerbi</code>, <code>vw_fretes_powerbi</code>, <code>vw_coletas_powerbi</code>, <code>vw_coletas_excluidas_origem</code>, <code>vw_cotacoes_powerbi</code>, <code>vw_contas_a_pagar_powerbi</code>, <code>vw_localizacao_cargas_powerbi</code>, <code>vw_manifestos_powerbi</code>, <code>vw_fato_manifestos_dash</code>, <code>vw_bi_monitoramento</code>, <code>vw_inventario_powerbi</code>, <code>vw_sinistros_powerbi</code> e <code>vw_raster_sm_transit_time</code>.
- Views dimensionais: <code>vw_dim_filiais</code>, <code>vw_dim_clientes</code>, <code>vw_dim_veiculos</code>, <code>vw_dim_motoristas</code>, <code>vw_dim_planocontas</code> e <code>vw_dim_usuarios</code>.
- Tabelas materializadas correspondentes: <code>fato_gestao_vista_fretes</code>, <code>fato_gestao_vista_coletores</code>, <code>fato_fretes_faturamento</code>, <code>fato_gestao_vista_faturas</code> e <code>fato_gestao_vista_manifestos</code>.

#### Destino das 19 views ETL-owned

| View legada | Papel/destino V2 | Tarefa dona |
|---|---|---|
| <code>vw_faturas_por_cliente_powerbi</code> | projeção <code>pub</code> de faturas/títulos; nome legado só como alias aprovado | V2-030/V2-037 |
| <code>vw_fretes_powerbi</code> | projeção <code>pub</code> de Fretes com Localização/Inventário e regras operacionais | V2-011/V2-028/V2-031/V2-037 |
| <code>vw_coletas_powerbi</code> | projeção <code>pub</code> de Coletas, catálogo de status, região e relações deduplicadas | V2-010/V2-026/V2-033/V2-035b/V2-037 |
| <code>vw_coletas_excluidas_origem</code> | contrato explícito de auditoria/reconciliação de ausências | V2-010/V2-013/V2-037 |
| <code>vw_cotacoes_powerbi</code> | projeção <code>pub</code> de Cotações; tarifa vem de referência | V2-027/V2-035a/V2-037 |
| <code>vw_contas_a_pagar_powerbi</code> | projeção <code>pub</code> financeira | V2-029/V2-037 |
| <code>vw_localizacao_cargas_powerbi</code> | projeção <code>pub</code> de tracking/status | V2-028/V2-035a/V2-037 |
| <code>vw_manifestos_powerbi</code> | projeção <code>pub</code> da raiz canônica de Manifestos | V2-026/V2-037 |
| <code>vw_fato_manifestos_dash</code> | projeção neutra sobre <code>mart</code>; sufixo legado só por compatibilidade aprovada | V2-036/V2-037 |
| <code>vw_bi_monitoramento</code> | view operacional sobre <code>ctl</code>, não contrato público por default | V2-020/V2-023/V2-037 |
| <code>vw_inventario_powerbi</code> | projeção <code>pub</code> de Inventário com Fretes | V2-011/V2-031/V2-037 |
| <code>vw_sinistros_powerbi</code> | projeção <code>pub</code> de Sinistros | V2-032/V2-037 |
| <code>vw_raster_sm_transit_time</code> | contrato condicional, criado apenas se Raster for mantido | V2-034b/V2-037 |
| <code>vw_dim_filiais</code> | dimensão derivada e publicada por fontes canônicas | V2-035b/V2-037 |
| <code>vw_dim_clientes</code> | dimensão derivada de Coletas/Fretes/Faturas | V2-035b/V2-037 |
| <code>vw_dim_veiculos</code> | dimensão por placa + filial | V2-035b/V2-037 |
| <code>vw_dim_motoristas</code> | dimensão por nome + filial, com genéricos excluídos por regra versionada | V2-035b/V2-037 |
| <code>vw_dim_planocontas</code> | dimensão por descrição canônica | V2-035b/V2-037 |
| <code>vw_dim_usuarios</code> | projeção do estado/histórico governado de Usuários | V2-033/V2-035b/V2-037 |

As views 023/024 continuam fora por serem wrappers cross-database. Para as 19 acima, “destino” não congela nome/coluna legado: V2-037 exige fingerprint completo e aceite do consumidor; sem manifesto externo, a view permanece em sombra e nenhum alias de compatibilidade é publicado.

As cinco procedures legadas acoplam upsert e sweep por <code>@MarcarAusentesComoExcluidos</code>. A V2 deve separar materialização incremental de reconciliação de ausência, com checkpoints distintos. Além disso, todas convertem parâmetros de data nulos em janela móvel curta baseada em UTC (<code>hoje-3</code> até <code>hoje+1</code> exclusivo) e mantêm <code>@cargaCompleta=0</code>. O auxiliar de reprocessamento de Manifestos chama datas nulas afirmando recalcular “a fato inteira”, portanto executa apenas essa janela: é defeito evidenciado, não regra a portar. Na V2, modo e janela são obrigatórios/inequívocos; full/backfill enumera partições explícitas e prova cobertura. O DAG inicial é: fontes canônicas + referências → fatos de Fretes/Coletores/Faturamento/Faturas/Manifestos → views <code>pub</code>; cada fato só roda quando todas as entradas da janela estiverem publicadas.

Grãos dimensionais legados a preservar ou alterar com ADR e teste: filial por nome normalizado; cliente por nome normalizado; veículo por placa + filial; motorista por nome + filial, excluindo genéricos; plano de contas por descrição; usuário por <code>user_id</code>.

Nome, grão, colunas, tipos, nullability e filtros são candidatos a contrato de compatibilidade até inventário formal dos consumidores. O projeto de dashboards não deve ser aberto para essa etapa: o owner deve fornecer manifesto legado aprovado, contrato externo ou aceite nominal. A validação <code>042_validar_contrato_dashboard_performance.sql</code> é consumer-specific; somente uma asserção core comprovada pode ser reaproveitada. Nenhuma view pode executar escrita ou DDL cross-database.

## Regras de negócio que precisam sobreviver

Os identificadores abaixo são canônicos no roadmap e foram transferidos por V2-017 para <code>docs/catalogos/portabilidade/regras-negocio.csv</code>, com origem, caso sintético positivo/negativo, teste-alvo, contrato, owner e status. Implementação/aceite por vertical e confirmação final continuam nos gates indicados e em V2-040.

### Coletas

- **COL-01 — Identidade:** <code>6908.id</code> é a source key canônica da origem e mapeia para o <code>canonical_id</code> surrogate da V2; <code>sequence_code</code> é business key única, nunca substituto inferido do ID.
- **COL-02 — Frescor:** persistir e comparar o timestamp tipado <code>status_updated_at_em</code>, derivado de <code>status_updated_at</code>, com fallback documentado em <code>finish_date</code>, <code>service_date</code> e <code>request_date</code>; preservar também o valor bruto quando inválido.
- **COL-03 — Terminalidade:** fonte terminal pode superar estado aberto mesmo com timestamp retroativo; terminal persistido não regride para aberto. Catálogo mínimo observado: <code>pending</code>, <code>treatment</code>, <code>manifested</code>, <code>in_transit</code>, <code>draft</code>, <code>finished</code>, <code>done</code>, <code>canceled</code>/<code>cancelled</code>.
- **COL-04 — Status publicado:** preservar o contrato vigente: <code>done</code> → “Coletada”, <code>finished</code> → “Finalizada”; ambos terminais. A V2 armazena código bruto + código canônico + label versionada, para uma futura mudança de apresentação não reescrever o fato bruto.
- **COL-05 — Ausência:** snapshot completo e independente; primeira ausência vira candidata e já é publicada como “Excluída” por compatibilidade; segunda observação independente confirma soft delete. O registro confirmado continua visível na view principal e na view/auditoria de excluídas. Reaparecimento limpa todos os campos de ausência.
- **COL-06 — Proteção:** snapshot incompleto, vazio anômalo, chave nula ou página incerta não altera ativos.
- **COL-07 — Relações:** V2-010 preserva, com presença e proveniência, os campos candidatos ao vínculo; V2-046a prova/materializa Manifesto→Coleta e preserva aliases/componentes dos itens; depois que Fretes existir, V2-046b prova/materializa Coleta→Frete. V2-036 apenas consome os vínculos canônicos para receita/fatos. Nenhuma relação inferida bloqueia ou multiplica a ingestão base antes dessa prova.
- **COL-08 — Região logística:** CEP tem prioridade, depois cidade/UF; fallback deve permanecer explícito e regras devem ser versionadas.
- **COL-09 — Incremental:** <code>scopes.by_updated_at</code> é busca complementar de late data, não cursor/watermark confiável. O checkpoint é por partição concluída de <code>request_date</code> + overlap versionado.
- **COL-10 — Guardrails herdados como candidatos:** reconciliação usa lookback 90 dias, duas confirmações e lote 500; órfãos até 500/35% e inválidos até 500/2,5%. Backfill referencial traz ainda pré-backfill 5 dias, buffer 7, expansão dinâmica máxima 30, chunk de backlog 400, lookahead 1 e aborto do intervalo após duas falhas críticas consecutivas; falha direta, auditoria ausente e quebra referencial contam, enquanto bloco saudável zera o contador. Esses conjuntos têm finalidades diferentes e nenhum valor vira default V2 sem medição, owner, janela e teste de bloqueio/reset.
- **COL-11 — Derivados operacionais:** o catálogo legado publica <code>pending=Pendente</code>, <code>treatment=Em tratativa</code>, <code>manifested=Manifestada</code>, <code>in_transit=Em trânsito</code>, <code>draft=Rascunho</code>, <code>finished=Finalizada</code>, <code>done=Coletada</code> e <code>canceled/cancelled=Cancelada</code>; apenas os quatro últimos códigos são terminais. Ação da ocorrência segue a precedência exata: <code>finished/done→Coleta Realizada</code>; nos demais status, motivo de cancelamento não vazio; senão <code>canceled/cancelled→Coleta cancelada</code>; senão “Pendente”. Número de tentativas legado é 1 para qualquer terminal e 0 para aberto. Bruto/canônico/label/terminal permanecem separados; alteração desses derivados exige fixture, consumidor e paridade.

### Fretes

- **FRE-01 — Identidade:** <code>6389.id</code> é a source key técnica da origem e mapeia para o <code>canonical_id</code> surrogate; <code>corporation_sequence_number</code> não é ID.
- **FRE-02 — Promoção:** o frescor efetivo preserva a precedência legada <code>cte_created_at → cte_issued_at → criado_em → servico_em</code>, escolhendo o primeiro valor presente, e a V2 usa exatamente essa chave em dedupe e promoção. <code>updated_at</code> permanece <code>UNVERIFIED</code> e não participa do frescor/watermark até prova contratual. Empate tem desempate total determinístico; nulo e chegada out-of-order são testados. Terminais <code>finished</code>, <code>done</code>, <code>canceled</code> e <code>cancelled</code> não regridem para aberto, salvo transição de negócio explicitamente versionada; campos previamente conhecidos seguem FRE-03. Publicação compatível: <code>finished/done</code> → <code>finalizado</code> e <code>canceled/cancelled</code> → <code>cancelada</code>, preservando o bruto.
- **FRE-03 — Presença/performance:** uma resposta terminal que omite CT-e, finalizações ou objetos já conhecidos não pode sobrescrever esses campos com nulo sem regra explícita. O timestamp oficial de performance do 6389 tem precedência; <code>finished_at</code> é fallback marcado com proveniência, nunca substitui silenciosamente a origem oficial.
- **FRE-04 — Relação:** o vínculo legado comprovado usa <code>pick_item_id</code> e itens de Coletas. <code>fit_p_m_pck_sequence_code</code> é candidato que exige prova de cardinalidade.
- **FRE-05 — Prune:** desligado por padrão; quando autorizado, usa snapshot completo, duas ausências, quarentena e guardrails de volume/histórico. Incremental nunca comprova ausência. Os defaults legados — duas ausências, histórico 7, baseline 100, queda/razão 30% e bloqueio de zero — são apenas candidatos a calibrar, não política V2 automática.
- **FRE-06 — Financeiro:** CT-e, contas, receita, <code>reference_number</code>, filial e campos ausentes exigem paridade e owner de negócio antes do corte.
- **FRE-07 — Data de performance ambígua:** o legado tenta <code>MM/dd/yyyy</code> antes de <code>dd/MM/yyyy</code> e só troca para BR em parte dos casos comparando com <code>servico_em</code>; valores como <code>02/04/2026</code> podem mudar de sentido pela heurística. V2-025a caracteriza formatos inequívocos e o caso ambíguo sem normalizá-los nem antecipar regra de domínio. Até prova, data com dia e mês ≤12 vai à quarentena com bruto/proveniência, nunca herda a adivinhação. A relação com <code>servico_em</code>, a classificação/quarentena e as fronteiras temporais/DST pertencem aos testes da vertical V2-011.

### Manifestos

- **MAN-01 — Identidade/grão (decisão V03 congelada):** uma raiz lógica usa a tuple exata <code>(source_instance, tenant_scope, manifestos, INTEGER:&lt;sequence_code&gt;)</code>. Pick é filho 0..N por <code>(root_canonical_id, INTEGER:&lt;mft_pfs_pck_sequence_code&gt;)</code>; MDF-e é filho 0..N por <code>(root_canonical_id, STRING:&lt;mft_mfs_key&gt;)</code>, com <code>mft_mfs_number</code> somente como atributo correlato. <code>source_instance</code> e tenant vêm de configuração explícita, nunca do payload ou de sentinels <code>DEFAULT/GLOBAL/SINGLETON</code>. Root key ou scope ausente/nulo/inválido, colisão, rekey e divergência residual são preservados e quarentenados. Componente de filho ausente/nulo sem sinal material significa zero filho; número MDF-e sem chave válida ou chave sem número é assimetria preservada/quarentenada. <code>mdfe_status</code> é escalar da raiz replicado, não sinal de filho. Hash pode pré-comparar conteúdo, mas ordem, número MDF-e isolado, status isolado, coocorrência e hash nunca criam identidade ou relação.
- **MAN-02 — Frescor/status e consolidação (decisão V03 congelada):** dedupe e promoção de raiz e de cada filho usam a mesma escolha do primeiro <code>VALUE</code> temporal válido em <code>finished_at → closed_at → departured_at → created_at</code>, comparado em UTC. Campo temporal presente e inválido bloqueia a observação; ausência/nulo cai ao próximo, e nenhum valor válido mantém a observação fora de promoção. Mais velho não regride. Na coorte do maior frescor, reducers explícitos são aplicados primeiro e separadamente para raiz, pick e MDF-e, sem carregar valor de versão anterior; resultado canônico idêntico é replay/no-op e somente conflito residual vira <code>EQUAL_FRESHNESS_CONFLICT</code>, nunca keep-last, ordem, página ou hash. Campo raiz comum ignora <code>ABSENT</code>; somente <code>NULL</code> resulta nulo, um único <code>VALUE</code> repetido resulta valor e <code>NULL+VALUE</code> ou valores distintos viram conflito. O lifecycle <code>/status</code> conhecido usa <code>closed &gt; in_transit &gt; pending</code>; desconhecido único preserva bruto sem terminalidade inferida, e mistura divergente é quarentena. <code>/mdfe_status</code> segue o reducer escalar comum da raiz. Não há <code>SUM</code>, <code>MAX</code> genérico ou chegada posterior como reducer.
- **MAN-03 — Relação:** V2-026 preserva <code>mft_pfs_pck_sequence_code</code> e demais candidatos relacionais com presença/proveniência; V2-046a é a única dona de provar cardinalidade e materializar Manifesto→Coleta. Coocorrência, nome parecido, MDF-e ou lookup <code>TOP 1</code> não criam FK/join; candidato órfão não é normalizado silenciosamente para nulo. V2-046b fecha Coleta→Frete depois que Fretes existir; V2-036 apenas consome vínculos já resolvidos.
- **MAN-04 — Competência (decisão V03 congelada):** usar <code>departured_at</code> tipado válido; somente quando estiver <code>ABSENT/NULL</code>, usar <code>created_at</code> válido, preservando path, bruto, origem do fallback e instante UTC. Valor presente inválido é quarentena; ambos sem <code>VALUE</code> deixam competência <code>UNKNOWN</code> e bloqueiam consumidor posterior. Data de extração, <code>MAX</code> e fechamento/finalização não substituem essa regra.
- **MAN-05 — Fato:** placa sentinela <code>ACM0000</code> é excluída; receita soma fretes do manifesto e receita das coletas relacionadas; capacidade soma trator e carretas.
- **MAN-06 — Classificações:** distribuição, transferência, carga fechada, frota própria, agregado e terceiro/autônomo são regras de negócio. CNPJs e nomes hardcoded devem virar referência versionada, vigente e auditada.
- **MAN-07 — Reducer da expansão (decisão V03 congelada):** para <code>km</code>, <code>totalCost</code>, <code>manifestFreightsTotal</code>, <code>totalTaxedWeight</code>, <code>vehicleWeightCapacity</code>, <code>manifestItemsCount</code> e <code>finalizedManifestItemsCount</code>, dentro da coorte vencedora, <code>ABSENT</code> não afirma valor, <code>NULL</code> é lacuna complementável e um único <code>VALUE</code> canônico vence mesmo repetido; somente ausentes permanece <code>ABSENT</code>, somente nulos/ausentes resulta <code>NULL</code>. Zero é sempre <code>VALUE</code> real: zero+não zero ou quaisquer dois valores distintos viram <code>METRIC_VALUE_CONFLICT</code>, sem soma, <code>MAX</code> ou score. <code>capacidadeKg</code> é o oitavo atributo lógico, mas deriva exatamente do mesmo valor reduzido de <code>/mft_vie_weight_capacity</code> que <code>vehicleWeightCapacity</code>; não é um oitavo sinal independente nem pode divergir. Complemento por expansão registra proveniência <code>COMPLEMENTED_FROM_EXPANSION</code>.

### Cotações

- **COT-01:** grão e PK por <code>sequence_code</code>, usuário normalizado, hash/no-op e soft delete. O legado diverge: dedupe usa somente <code>requested_at</code>, enquanto promoção prioriza <code>nfse_issued_at</code>, depois <code>cte_issued_at</code>, depois <code>requested_at</code>. A V2 adota essa precedência como frescor compatível inicial e a aplica igualmente em dedupe/promoção, com teste de empate/out-of-order.
- **COT-02:** tarifa mínima por UF origem/destino é regra válida, mas o <code>CASE</code> legado deve virar tabela versionada com vigência, combinação não coberta e testes de paridade.

### Localização de Cargas

- **LOC-01:** candidato de grão por <code>corporation_sequence_number</code> publicado pela fonte, sujeito a V2-009b; <code>sequence_number</code> é somente o nome de ordenação legado aceito. Pesos e valores textuais devem ter conversão estrita para colunas tipadas.
- **LOC-02:** status, região/alias da filial destino, previsão, responsável, hash/no-op e soft delete são contrato.
- **LOC-03:** volume publicado usa <code>COALESCE(localizacao.invoices_volumes, fretes.invoices_total_volumes, 0)</code>.
- **LOC-04:** conversão inválida deve ir à quarentena; não pode virar nulo silencioso.
- **LOC-05:** dedupe legado usa <code>service_at</code> e keep-last quando ambos são nulos; o merge por hash não possui guarda monotônica. A V2 define uma única precedência temporal versionada, testa empate/nulo/out-of-order e bloqueia regressão; enquanto não caracterizada, conflito vai à quarentena.
- **LOC-06 — Normalização de status:** nulo/branco publica <code>sem_status</code>; demais valores usam lowercase + trim, sempre preservando o bruto. Status desconhecido não herda terminalidade/cancelamento e segue a política fail-closed antes da promoção/publicação.
- **LOC-07 — Campo sem fonte comprovada:** <code>status_branch_nickname</code> existe no modelo/publicação legado, mas o DTO/de-para auditado não possui source path. Fica <code>ABSENT/UNSOURCED_LEGACY</code>; só pode ser mapeado por evidência de fonte ou retirado com aceite do consumidor, nunca preenchido por fallback inventado.

### Contas a Pagar

- **CAP-01:** candidato de source key/PK publicada por <code>ant_ils_sequence_code</code>; filtro de negócio por <code>issue_date</code>. A V1 repete a janela global de <code>issue_date</code> e a cruza com um dia de <code>created_at</code>; a V2 só mantém essa segmentação após caracterização. Qualquer contador/oráculo deve repetir exatamente ambos os filtros da travessia, pois omitir <code>created_at</code> compara outro conjunto.
- **CAP-02:** <code>paid=true</code> publica “PAGO”; caso contrário, “ABERTO”.
- **CAP-03:** competência, fornecedor, filial, centro/plano de contas e valores são contrato. A fonte mapeia <code>ant_ils_atn_transaction_date → data_transacao</code>, <code>ant_ils_atn_liquidation_date → data_liquidacao</code> e <code>created_at → data_criacao</code>. O dedupe legado compara lexicograficamente criação/transação/liquidação/emissão/extração, mas a promoção usa o maior instante entre fim do dia de transação, fim do dia de liquidação e criação. A V2 adota a expressão da promoção igualmente no dedupe, com testes.
- **CAP-04:** <code>ant_ils_sequence_code</code> é candidato à chave da parcela publicada. Como o template não expõe ID da raiz <code>accounting_debit</code>, o V2 não usa <code>per</code> como prova de completude nem ativa sweep/cutover até contrato/contagem/cursor oficial ou oráculo independente de totais + chaves. Duas travessias com tamanhos distintos são apenas evidência complementar.
- **CAP-05 — Catálogos derivados:** após remover o namespace até o último <code>::</code>, tipo publica <code>Manual</code>, <code>Advance→Adiantamento</code>, <code>CiotBilling→Pagamento CIOT</code>, <code>DriverBilling→Pagamento Motorista</code> e <code>StorageInvoice→Nota Fiscal Armazenagem</code>. Classificação publica <code>variable_costs→4. Custos Variáveis</code>, <code>static_expenses→3. Custos Fixos</code>, <code>revenue→1. Receitas</code>, <code>deductions→2. Deduções</code>, <code>financial_result→5. Resultado Financeiro</code> e <code>taxes→6. Impostos</code>. Desconhecido preserva bruto e não assume classe conhecida; normalização/label são versionados.

### Faturas por Cliente

- **FAT-01:** <code>4924.id</code> é candidato a source key da linha observada, sujeito a estabilidade/tenant scope em V2-009b; <code>unique_id</code> não veio no payload e sua ordenação aceita não prova existência. Uma linha por título lógico exige crosswalk e cardinalidade próprios.
- **FAT-02 — Documento fiscal divergente:** o mapper legado dá precedência à NFS-e quando ambos vêm no payload e zera CT-e; as views/procedure, porém, testam número CT-e antes de NFS-e quando ambos já existem no banco. Isso normalmente fica mascarado pela supressão anterior e não forma uma regra coerente. A V2 preserva presença/proveniência das duas evidências no staging, caracteriza casos simultâneos em V2-025b/V2-030 e decide por ADR/paridade uma única precedência para core, fato e publicação; até lá o caso duplo é <code>UNRESOLVED</code> e bloqueia a saída. CNPJ é normalizado e status CT-e traduzido.
- **FAT-03:** uma fatura pode agrupar vários fretes. <code>fit_ant_document</code> não é identidade de Frete.
- **FAT-04:** aliases/rekey, cardinalidade título–CT-e/NFS-e–fretes e replay precisam ser determinísticos antes da persistência.
- **FAT-05 — Frescor:** o dedupe legado é keep-last sem timestamp, enquanto a promoção escolhe o maior de <code>data_baixa_fatura</code>, <code>data_vencimento_fatura</code>, <code>data_emissao_fatura</code>, <code>data_emissao_cte</code>, <code>fit_ant_issue_date</code> e <code>fit_ant_ils_original_due_date</code>. As cinco datas civis — todas exceto <code>data_emissao_cte</code> — comparam como fim lógico do dia; somente <code>data_emissao_cte</code> conserva hora/minuto/segundo. A V2 guarda tipos brutos, deriva a chave comparável em <code>America/Sao_Paulo</code> por início exclusivo do dia seguinte — sem literal frágil <code>23:59:59</code> — e usa a mesma expressão em dedupe/promoção. Testes cobrem nulo, empate, out-of-order, precisão, conversão e DST.
- **FAT-06 — Catálogos derivados:** status CT-e publica <code>authorized=Autorizado</code>, <code>cancelled=Cancelado</code>, <code>denied=Negado</code> e <code>pending=Pendente</code>; desconhecido preserva o bruto. Tipo de frete remove ocorrências literais de <code>Freight::</code> e aplica trim, mantendo também o valor original para auditoria.
- **FAT-07 — Campo sem fonte comprovada:** <code>serie_nfse</code> existe no DDL/publicação legado, mas não possui origem no DTO/mapper auditado. Fica <code>ABSENT/UNSOURCED_LEGACY</code> até source path comprovado ou retirada nominal; não é campo obrigatório da API por suposição.

### Inventário

- **INV-01:** <code>sequence_code</code> é candidato ao agregado observado, sujeito a V2-009b; a identidade legada por hash de sequência/minuta/mapeamento/data não será copiada. Linhas de frete/invoice são filhos tipados e mantêm seus componentes naturais.
- **INV-02:** dedupe prioriza <code>performance_finished_at</code>, depois <code>finished_at</code> e <code>started_at</code>.
- **INV-03:** comprovante anexado é cumulativo; verdadeiro não volta a falso. A detecção normalizada preserva a ordem de palavras-chave <code>comprovante</code> → <code>entrega</code> → <code>anexado</code> e combina evidências por OR.
- **INV-04:** relação por minuta e parsing textual precisam de fixtures e quarentena segura.

### Sinistros

- **SIN-01:** <code>sequence_code</code> é obrigatório e candidato ao agregado; a identidade legada combina sequência, ocorrência de invoice e minuta. Simplificar somente após prova de unicidade/estabilidade/tenant scope em V2-009b.
- **SIN-02:** o dedupe legado usa só <code>treatment_at</code>, enquanto promoção usa <code>COALESCE(treatment_at, opening_at_date)</code>. A V2 unifica nesse fallback em dedupe/promoção; tipo, solução e valores financeiros precisam de paridade.

### Usuários

- **USR-01:** uma linha atual por <code>user_id</code>; histórico somente quando o hash de atributos mudar.
- **USR-02:** desativação permanece desligada até V2-013 provar duas travessias completas/independentes, consistência e guardrails; reaparecimento reativa. Usuários de cancelamento/destruição de Coletas não podem multiplicar o grão.
- **USR-03:** GraphQL é a fonte transitória recomendada porque nenhum template Data Export de usuário foi identificado/autorizado; V2-017 ratifica. Não existe incremental temporal provado; substituição futura exige contrato equivalente e paridade.
- **USR-04 — Proibição de template inferido:** <code>9901</code> é somente <code>TEMPLATE_ID_AUDIT</code> no runtime GraphQL; documentos históricos que o rotulam “Usuários Sistema” não provam endpoint Data Export. Não chamar <code>/reports/9901</code> sem template oficial/autorização/contrato. A query efetiva pede apenas <code>id/name</code>; <code>updatedAt</code> do DTO e <code>origem_atualizado_em</code> ficam <code>ABSENT/UNREQUESTED</code> até seleção/prova explícita.

### Raster

- **RAS-01:** pai <code>cod_solicitacao</code> antes dos filhos; parada por <code>cod_solicitacao+ordem</code>, com FK e transação.
- **RAS-02:** definir frescor, snapshot, watermark, timezone IANA e reativação; não copiar overwrite cego nem <code>ZoneId.systemDefault()</code>.
- **RAS-03:** lote de 500 não prova terminalidade. Se habilitado, dividir até a menor janela permitida e ainda bloquear conclusão quando o cap persistir. A view aceita transit time de 0 a 43.200 minutos.
- **RAS-04:** módulo desligado por padrão; se habilitado, credencial ausente falha no startup, pai+filhos são atômicos e nenhuma falha pode ser convertida em sucesso/degradação silenciosa.
- **RAS-05 — Sentinela/tempo:** datas cujo texto começa em <code>1900-01-01</code> são nulas no contrato legado; a V2 preserva bruto/proveniência e decide por contrato, sem timezone/formato do host. Duração negativa ou acima de 43.200 minutos é inválida/nula na publicação e deve gerar métrica/fixture, não conversão silenciosa.
- **RAS-06 — Ordem da parada:** quando a fonte omite <code>Ordem</code>, a V1 usa posição <code>i+1</code> como parte da PK. Esse fallback é <code>UNRESOLVED</code> e não pode virar identidade canônica até ordem/natural key estáveis serem comprovadas.

### Fatos e materializações

- **MAT-01 — Fretes operacionais:** produz indicadores PE e CB; o grão definitivo está <code>UNRESOLVED</code> pela divergência abaixo. Excluir complementar; documento CT-e > NFS-e > pendente. PE referencia a previsão e CB referencia <code>servico_em</code>. Antes do <code>DATEDIFF</code>, previsão = <code>COALESCE(fretes.data_previsao_entrega, localizacao.predicted_delivery_at)</code> e finalização = <code>COALESCE(fretes.fit_dpn_performance_finished_at, fretes.finished_at)</code>. Performance nula → <code>EM ABERTO/0</code>, diferença ≤0 → <code>NO PRAZO/1</code>, diferença >0 → <code>FORA DO PRAZO/2</code>; proveniência dos fallbacks permanece auditável. O ranking legado por minuta pontua presença de finalização, diferença, filial e extração, depois ordena extração/ID decrescentes; é comportamento a caracterizar. Elegibilidade exclui soft-delete/cortesia; sem documento, exclui CNPJ de filial, valor ≤0,01 e substituto pendente; a variante “com valor” exige >0,01. PE ainda exige previsão, filial da performance, não cancelado e elegível; CB exige data do frete, não cancelado, elegível com valor e pagador fora da blacklist. <code>is_cubado</code> significa <code>total_m3&lt;&gt;0</code>. CNPJs, pagadores e tipos ficam em <code>ref</code>.
- **MAT-02 — Coletores:** dia + filial + classificação fixa <code>Geral</code>; excluir prefixos de carga fechada, acerto de motorista, frete retorno e viagem vazia. Emitidos deduplicam Manifesto por sequência/fallback de identificador e usam dia de <code>created_at</code> + filial emissora. Descarregamentos usam o mesmo Manifesto; a escolha legada da primeira filial lexicográfica após split/alias é <code>UNRESOLVED</code> e exige ADR, não cópia. Bipados/incompletos deduplicam Inventário por <code>sequence_code</code>, usam dia de <code>started_at</code> e os tipos Picking, Return, Receipt, Loading e Unloading; incompleto significa <code>finished_at IS NULL</code>. Filial do Inventário tem fallback para o Frete mais recente por extração/ID. <code>total=emitidos+descarregamentos</code> e percentual = <code>bipados*100/total</code>, zero quando denominador zero. Alias/filial são referências e o dia usa timezone de negócio injetado.
- **MAT-03 — Faturamento:** o grão definitivo está <code>UNRESOLVED</code>; o DDL possui <code>UNIQUE(frete_id, data_referencia_faturamento_date)</code>, mas loader/validator tratam uma linha por <code>frete_id</code>. Data real retroage pelo calendário ao dia útil. Lookup CT-e normaliza a chave e prioriza status real sobre <code>status_result</code>, depois extração, emissão e <code>unique_id</code>. Cancelamento é evidenciado por <code>cancelad/cancell</code> em status/resultado de Fatura e, havendo CT-e, pelo status cancelado do Frete; a proveniência é preservada. Bloqueio exige classificação contendo <code>bloqueio</code> e também <code>anulacao</code> ou <code>isolamento</code>. Soft-delete, data ausente, cancelamento, cortesia, bloqueio ou <code>is_elegivel_faturamento=0</code> zeram receita/subtotal. Regra ativa pagador→filial prevalece; volumes usam Localização→Fretes. Fronteiras temporais usam <code>America/Sao_Paulo</code>, não o offset/UTC incidental legado.
- **MAT-04 — Faturas:** o grão definitivo está <code>UNRESOLVED</code>; o DDL possui <code>UNIQUE(unique_id, data_emissao_fatura)</code>, mas loader/validator usam somente <code>unique_id</code>. Documento real é <code>fit_ant_document</code>, exceto os placeholders “faturado”/“aguardando faturamento”; número oficial prioriza CT-e sobre NFS-e. <code>data_base_prazo=COALESCE(fit_ant_issue_date,data_emissao_fatura)</code>; referência mensal cai ainda em emissão CT-e. Cliente usa CNPJ explícito, senão documento do pagador com 14 dígitos; chave é <code>cnpj:</code> ou <code>nome:</code>. Aging usa data de negócio injetada. Pagamento fica em <code>sem_fatura/baixado/sem_vencimento/vencido/a_vencer</code>; valor operacional = <code>COALESCE(fit_ant_value, valor_fatura, valor_frete, 0)</code>. O filtro legado só por <code>data_emissao_fatura</code> exclui nulos mesmo no falso “full” e é defeito a decidir. A view considera qualquer <code>fit_ant_document</code> “Faturado”, enquanto o fato rejeita os dois placeholders; V2-037/V2-036 exigem ADR e paridade única.
- **MAT-05 — Manifestos:** uma linha por <code>sequence_code</code>; competência é <code>MAX(COALESCE(departured_at,created_at))</code>; excluir soft-delete e placa <code>ACM0000</code>; status prioriza <code>closed&gt;in_transit&gt;pending</code>. Receita é total de fretes do Manifesto + fretes ativos relacionados aos <code>pick_items_ids</code> das Coletas; capacidade é trator + carreta 1 + carreta 2. Aplicam-se MAN-01–MAN-07. O <code>MAX/SUM/STRING_AGG</code> legado sobre expansão pode sintetizar/dobrar valores e não é contrato: V2-036 usa reducer por campo e soma cada Coleta/Frete canônico uma vez.
- Na V2, todos os fatos usam lock de carga, hash para no-op, janela <code>[início, fim exclusivo)</code>, upsert set-based e sweep separado. No legado, as cinco procedures acoplam sweep ao upsert por <code>@MarcarAusentesComoExcluidos</code>.

#### Divergências legadas conhecidas — não portar como contrato

- **Grão de fatos:** tabela 028 declara <code>UNIQUE(indicador,minuta,data)</code>, mas a procedure 001 faz match por <code>(indicador,minuta)</code>; tabela 030 declara <code>UNIQUE(frete_id,data)</code>, mas procedure 003/validator 040 exigem uma linha por <code>frete_id</code>; tabela 031 declara <code>UNIQUE(unique_id,emissão)</code>, mas procedure 004/validator 041 usam só <code>unique_id</code>. V2-036 mede cardinalidade histórica/consumidores e registra ADR que alinha chave canônica, <code>UNIQUE</code>, upsert, partição e validator. Até lá os três grãos são <code>UNRESOLVED</code> e o cutover fica bloqueado; o UQ físico não vence por default.
- **Full implícito quebrado:** nas cinco procedures, data nula vira <code>[CAST(UTC hoje-3 dias AS DATE), CAST(UTC hoje+1 dia AS DATE))</code> e <code>@cargaCompleta</code> permanece falso — pode abranger quatro datas civis, não uma carga full. O reprocessamento de Manifestos promete “fato inteira”, mas chama nulo/nulo e não alcança registros antigos. A V2 recusa nulo como full, exige modo/horizonte/partições persistidos e testa registro anterior a <code>UTC hoje-3</code>.
- **Agregação sintética:** <code>MAX</code> por coluna, <code>SUM</code> e <code>STRING_AGG</code> sobre linhas expandidas não provam que os valores vieram da mesma versão lógica nem que são distintos. Reducers, filhos e crosswalks canônicos substituem essa heurística.
- **Truncamento/vazamento de texto:** Manifestos possui 27 caminhos textuais limitados; o repositório legado aplica <code>truncate</code> silencioso em 26 deles e inclui prefixo do valor no warning, enquanto <code>contract_number</code> entra sem guarda em <code>NVARCHAR(50)</code> e pode falhar por overflow. Isso pode perder conteúdo/expor PII/comentário e não é compatibilidade. Limite/Unicode pertencem ao contrato, overflow vai à quarantine ou evolução aprovada de schema, e log registra somente campo/tamanho/código/hash aprovado — nunca prefixo do valor.

### Regras semânticas das 19 views ETL-owned

Estas regras complementam o fingerprint estrutural de V2-037. Toda view também exige manifesto exato de expressão, join/cardinalidade, fallback, enumeração, filtro e fixture; alias/rótulo pode mudar somente com aceite do consumidor.

- **PUB-01 — Fretes:** publicar somente ativos e não complementares. Documento oficial usa qualquer evidência CT-e antes de qualquer evidência NFS-e; volume usa Localização→Frete; região usa localização→UF→<code>SEM_REGIAO</code>; responsável usa filial destino→filial do Frete e chave→<code>sem_responsavel</code>. Performance da view é nula se previsão/finalização faltar (diferente de MAT-01); caso contrário, <code>DATEDIFF</code> publica no prazo/fora e faixas exatas 0, ±1/2/3 e acima de 3. Comprovante é “Sim” se existir Inventário ativo anexado. Status: <code>pending→pendente</code>, <code>finished/done→finalizado</code>, <code>canceled/cancelled→cancelada</code>, <code>in_transit→em trânsito</code>, <code>standby→aguardando</code>, <code>manifested→registrado</code>, <code>occurrence_treatment→tratamento de ocorrência</code>. Preservar ainda labels de cortesia, seguro, pagamento <code>bill/cash</code>, documento anterior <code>electronic</code>, DIFAL, globalizado e <code>globalized_type=none</code>, sempre junto ao bruto.
- **PUB-02 — Coletas:** primeira ausência candidata já publica “Excluída”; demais status usam o catálogo COL-11 e fallback bruto. Região usa CEP válido/prioridade antes de cidade+UF. Manifesto relacionado é escolhido sem multiplicar a Coleta; usuários ativos de cancelamento/destruição têm fallback para o ID. A view principal mantém candidatas/confirmadas por compatibilidade, enquanto a view de auditoria lista somente <code>excluido_na_origem=1</code> com status anterior.
- **PUB-03 — Cotações:** CT-e ou NFS-e emitido → “Convertida”; sem documento e comentário de reprovação não vazio → “Reprovada”; senão “Pendente”. Status CT-e é Emitido/Pendente, NFS-e Emitida/Pendente e refino CT-e Sim/Não. A matriz tarifária 5×5 observada é migrada para referência vigente por COT-02; combinação ausente é explícita.
- **PUB-04 — Financeiro:** Contas publica Pago=Sim somente quando <code>status_pagamento=PAGO</code> e conciliação como Conciliado/Não conciliado. Faturas por Cliente prioriza número CT-e sobre NFS-e, CNPJ explícito sobre documento normalizado de pagador com 14 dígitos e hoje marca qualquer <code>fit_ant_document</code> como Faturado; a divergência de placeholders com MAT-04 precisa de ADR antes do contrato final. Somente ativos são publicados.
- **PUB-05 — Localização:** <code>pending=Pendente</code>, <code>delivering=Em entrega</code>, <code>in_warehouse=Em armazém</code>, <code>in_transfer=Em transferência</code>, <code>manifested=Manifestado</code>, <code>finished=Finalizado</code>, <code>delivered=Entregue</code> e <code>canceled/cancelled=Cancelado</code>. Terminal inclui finalizado/entregue/cancelado em bruto ou canônico; flag cancelado só inclui cancelamento. Alias de região ausente publica <code>SEM_MAP</code>.
- **PUB-06 — Inventário e Sinistros:** Inventário traduz Loading/Unloading/Picking/Receipt/Return em Carregamento/Descarregamento/Picking/Recebimento/Retorno; <code>pending/finished</code> em pendente/finalizado; comprovante em Sim/Não; filial do Frete precede a do Inventário. Sinistros traduz <code>send_to_damages_dealing</code>/<code>send_to_incidents_dealing</code> para tratativa de avarias/incidentes e <code>redelivery_without_freight_charge</code> para redespacho sem cobrança de frete; desconhecido preserva bruto.
- **PUB-07 — Manifestos, monitoramento e dimensões:** views de Manifestos só expõem raiz/fato ativo; monitoramento pertence ao <code>ctl</code> e não é público por default. Filial é nome normalizado; Cliente é união deduplicada de remetente/destinatário/pagador/cliente ativos. Para Frota, <code>placa+filial</code>, <code>nome+filial</code> e a exclusão de nomes contendo “MOTORISTA” são somente comportamentos legados a caracterizar, nunca chaves ou filtros autorizados: o ADR 0025 mantém Veículos e Motoristas bloqueados até existir identidade estável, escopo, vigência e rekey comprovados. Plano de Contas agrupa descrição e usa “OUTROS / NÃO CLASSIFICADO” quando a classificação falta; Usuário publica apenas estado ativo por <code>user_id</code>. Toda normalização mantém linhagem.
- **PUB-08 — Raster condicional:** transit time usa primeiro valor direto entre 0 e 43.200 minutos; fallback é diferença entre início previsto e chegada prevista/fim, no mesmo intervalo. Origem/destino e CNPJs usam precedências do pai/parada e parsing versionado de rota; extração publicada é a mais recente entre pai/filho. Essa view só existe se V2-034a decidir manter e V2-034b provar pai/filhos, timezone e fixtures de parsing.

## O que deve ser redesenhado, não copiado

- Composition root espalhado, <code>AplicacaoContexto</code>, service locator, mapas estáticos e <code>System.properties</code> como canal de contexto.
- Classes/DTOs gigantes, repositório reflexivo genérico e regras de domínio misturadas a HTTP/JDBC.
- <code>MERGE</code> registro a registro; o alvo é staging por execução e promoção set-based.
- DDL/ALTER em runtime, runner manual de migrations e três fontes divergentes de schema.
- Queries de Data Quality fail-open que transformam falha SQL em zero/sucesso.
- GET com body, fallbacks ambíguos, timezone/clock do host e defaults apontando para produção/tenant.
- Estado principal do daemon, backlog ou checkpoint em arquivos <code>.properties</code>.
- Resultado de subprocesso inferido por texto de log e sucesso presumido quando o resumo falta.
- Dois mecanismos concorrentes de loop, menus batch gigantes e PM2 tratado como arquitetura.
- Payload/corpo bruto em log ou <code>metadata</code> sem classificação, minimização, criptografia, acesso e retenção.
- CNPJs, filiais, aliases, tarifas, status e faixas hardcoded em Java/procedures.
- Partições fixas até 2032 e índices com nome “dashboard” copiados sem plano/benchmark.
- Autenticação SQLite local como segurança central: execução direta do JAR hoje pode contorná-la.

## O que não será portado

- Os scripts legados <code>../etl-extracao-dados/database/views/023_publicar_wrappers_dashboard_dev_explicitos.sql</code> e <code>../etl-extracao-dados/database/views/024_publicar_wrappers_dashboard_prod_explicitos.sql</code>.
- Qualquer DDL/DML ou dependência do projeto/banco de dashboards.
- <code>faturas_graphql</code> como entidade autônoma; foi removida do legado e restaram apenas referências históricas.
- Runners <code>DataExportRunner</code> e <code>GraphQLRunner</code> deprecated.
- <code>sys_auditoria_temp</code>, o controle manual <code>schema_migrations</code> e duplicações de log/histórico que o novo control plane absorver.
- Migrations arquivadas/no-op/drop como sequência de instalação da V2.
- Limpeza destrutiva de domínio/fatos/histórico/auditoria, comando <code>--limpar-tabelas</code> e <code>../etl-extracao-dados/database/validacao/031_limpar_dados_todas_tabelas.sql</code>. Somente purge de staging temporário por TTL é permitido; control plane/quarantine/evidências são arquivados por partition move/cópia read-only, sem drop pela aplicação.
- CSV, introspecção, testes de API e comandos de diagnóstico no CLI produtivo por simples paridade. Se houver necessidade real, devem virar ferramenta administrativa separada, read-only, protegida e aprovada.
- Dependência do <code>.env</code> do repositório irmão, defaults produtivos e credenciais embutidas.

## Regras obrigatórias para um projeto limpo, profissional e seguro

### Arquitetura e código

- Um único composition root instancia dependências. Não usar service locator, singleton global mutável ou construção espalhada.
- Domínio puro e independente; aplicação orquestra; infraestrutura implementa portas; entradas somente convertem e validam.
- Tipos distintos para API, domínio, staging e publicação. Não propagar <code>Map&lt;String,Object&gt;</code> ou JSON bruto além da borda.
- Classes e funções pequenas, coesas e com nomes de negócio. Abstração só nasce para resolver repetição ou variação real.
- Configuração é tipada, imutável, validada no startup e tem precedência única. Ambiente/alvo são explícitos; não existe default produtivo.
- O toolchain e a imagem de build fixam Java 17; JDK usado, SO e versões de ferramentas entram na proveniência. Não aceitar faixa aberta <code>[17,)</code> para release.
- Relógio e timezone são injetados. Datas de negócio usam <code>America/Sao_Paulo</code> quando o contrato exigir; persistência temporal deve ter política explícita.
- Erro preserva causa técnica e contexto sanitizado. <code>ControlPlanePersistenceException</code> mantém a <code>SQLException</code> como causa sem expor SQL/segredo na mensagem; observabilidade posterior deve redigir a causa antes de registrá-la. Não capturar <code>Throwable</code>, ignorar exceção ou converter falha em sucesso.

### Dados e banco

- Todo DDL passa por Flyway. Runtime não cria/altera objetos.
- Banco novo por migrations e banco baselineado/atualizado devem produzir o mesmo fingerprint: objetos, colunas, tipos, nullability, defaults, PK/FK/checks, índices, views, procedures e grants.
- Separar credencial de migrator da credencial de runtime. Runtime recebe apenas <code>EXECUTE</code>/<code>DML</code> mínimo nos objetos autorizados, sem DDL ou acesso cross-database.
- Toda escrita é idempotente, parametrizada, auditável e ligada a <code>execution_id</code>.
- A unidade recuperável por entidade/partição é: extrair → persistir staging/páginas → validar/reconciliar staging → promover set-based + registrar auditoria + avançar checkpoint/publication pointer na mesma transação SQL. O padrão atual de abrir uma conexão JDBC por evento não atende esse protocolo.
- Carga usa staging por execução e promoção set-based/transacional. Enquanto registros sem chave ainda são quarentenados como artefatos físicos, as equações auditáveis são <code>physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows</code> e <code>distinct_root_keys = valid_rows/candidate_rows + quarantined_root_keys</code>; <code>quarantined_stage_rows</code> conta artefatos append-only e não deve ser confundido com raízes distintas. O apply final acrescenta <code>insert/update/noop</code> no subgate próprio.
- O modelo interno usa intervalo <code>[início, fim exclusivo)</code>. Um tradutor por fonte converte para a semântica ESL somente após teste exato de inclusão, precisão e DST; <code>endExclusive - 1 dia/segundo</code> é candidato, não fato provado. As classes atuais com fim inclusivo devem ser migradas sem deslocar/lacunar a janela. Filtros SQL são SARGable e não aplicam função sobre coluna indexada.
- Agregação, join massivo, dedupe e reconciliação ficam no SQL Server; a JVM não carrega massa para calcular conjuntos.
- A JVM fica limitada a HTTP/retry/paginação, parsing e validação da resposta limitada, mapping por registro, hash versionado por registro, bulk staging com backpressure, contadores <code>O(1)</code> e orquestração. Memória produtiva é <code>O(maxResponseBytes + batchBytes)</code>, independente da quantidade de páginas/janela, com no máximo uma página/lote em voo; é proibido manter <code>List/Map/Set</code> de registros, chaves ou hashes durante toda a execução.
- O SQL Server é autoritativo para dedupe/frescor cross-page, joins/crosswalks/sidecars, cardinalidade, anti-join de presença, current/history/reativação/desativação, <code>COUNT/SUM/GROUP BY</code>, paridade de conjuntos/hashes, Data Quality, promoção, dimensões e fatos. Java recebe somente contagens e amostras <code>TOP(N)</code> sanitizadas; nenhuma consulta/DML por linha ou <code>IN (...)</code> dinâmico com universo de chaves.
- Fontes independentes — por exemplo Fretes GraphQL, performance 6389 e enriquecimentos NFS-e — entram em stagings separados. A associação e o reducer são set-based, com constraint/window e cardinalidade medida; o Java não indexa uma fonte inteira em <code>Map</code> para cruzá-la no loop.
- Nenhum <code>MAX(id)+1</code>, ID por minuta/sequence inferido ou hash heurístico sem prova de estabilidade/colisão.
- Identidade canônica é escopada por instância da fonte e tenant/corporação: a unicidade física inclui <code>(source_instance, tenant_scope, entity, source_key)</code>. Um sentinel singleton só é permitido quando contrato e sondas provarem que a chave é global à instância.
- Ausência de campo e campo nulo são estados diferentes. Payload parcial não apaga valor conhecido sem regra de presença/frescor.
- Comprimento e política Unicode são definidos por campo de ponta a ponta. Overflow não é truncado silenciosamente: bloqueia/quarentena ou exige migration/contrato aprovado. Redaction nunca imprime prefixo do conteúdo; testes cobrem limite exato, acima do limite, comentário, documento e emoji/surrogate pair.
- Hard delete é proibido para domínio, auditoria, quarantine, evidências, fatos e históricos. Reaparecimento reativa registro. Mudança dessa regra exige alteração formal das regras superiores, não apenas TTL local.
- Somente staging técnico transitório pode ser purgado por <code>execution_id</code> após TTL e estado terminal. Auditoria/quarantine/evidências são movidas para arquivo frio/read-only por partição, preservando rastreabilidade; não são apagadas fisicamente pela aplicação.
- Incremental não prova ausência. Sweep usa snapshot completo independente, dry-run, confirmação múltipla, lock, guardrails e trilha.
- Ledger e checkpoint são separados por modo <code>incremental/bootstrap/backfill/replay/sweep</code>. Partições podem concluir fora de ordem, mas somente a fronteira incremental contígua, íntegra e publicada avança o watermark operacional; bootstrap/backfill/replay/sweep jamais saltam lacuna nem elevam esse watermark pelo maior período carregado.
- Particionamento e índices são guiados por plano/medição; nada de horizonte fixo ou índice duplicado por convenção antiga.

### Integrações e resiliência

- Cada fonte documenta endpoint/template, autenticação, raiz, filtros, ordenação, chave, paginação, timezone, limites, timeout, erros e retry.
- Transporte é fixo por contrato/template; sucesso/falha de uma vertical não altera método ou modo compartilhado de outra. <code>order_by</code>, source key/audit key e cursor são conceitos separados, e <code>page</code> numérico nunca é promovido a cursor determinístico sem garantia do fornecedor.
- Paginação só conclui pela condição terminal comprovada. Página faltante, cap atingido, resposta vazia anômala, envelope de erro ou identidade não verificável tornam a execução incompleta.
- Retry é limitado, com backoff/jitter, somente em operação idempotente. HTTP 429 respeita <code>Retry-After</code> válido sob teto configurado; ausência/valor inválido usa backoff. Nunca avança estado, mascara falha permanente ou repete escrita não idempotente.
- Circuit breaker, rate limiter compartilhado por origem ESL, timeout e limites de páginas/entidades/bytes são obrigatórios. Múltiplas verticais consomem um único orçamento e têm teste concorrente; cliente/template não cria quota independente.
- Watermark de fonte, janela planejada e checkpoint executivo são conceitos separados. Nenhum deles avança após execução parcial.
- Timeout existe por request, passo e ciclo. Cancelamento é cooperativo, fecha streams/conexões/executores e não vaza threads; worker isolado só é usado para dependência comprovadamente não cancelável e devolve resultado estruturado.
- Toda dependência GraphQL é transitória, inventariada e possui critério de remoção.

### Segurança e privacidade

- Segredos ficam fora de código, Git, system properties/<code>-D</code>, argumentos de linha, logs, testes e documentação. As opções legadas <code>api.dataexport.token</code> e <code>v2.shadow.runtime-password</code> foram removidas do runtime; qualquer chave/nome secreto via <code>-D</code> é recusado por teste, não apenas esses dois exemplos. Preferir secret store/identidade gerenciada e, transitoriamente, variável de ambiente protegida. Varredura de segredo ocorre antes do primeiro commit e em todo CI.
- Entradas de CLI, ambiente, arquivos, URL e SQL são validadas e parametrizadas. TLS e hostname não podem ser desabilitados.
- PII, dados financeiros e metadata precisam de matriz que defina classe, colunas, finalidade, chave/criptografia, perfis, mascaramento e TTL; “quando aplicável” sem decisão registrada não é aceite.
- Logs e auditorias registram IDs técnicos de execução, contagens, códigos, tamanhos e hashes não reversíveis aprovados; nunca token, documento pessoal ou payload bruto.
- Ações sensíveis exigem identidade/autorização no boundary real da aplicação, sem bypass por execução direta.
- Antes de qualquer escrita, preflight resolve e registra de forma segura ambiente, servidor e banco; recusa banco legado/produção sem allowlist e autorização de cutover; adquire lock e prova que não existe outro writer. A conta de runtime não possui DDL.
- Dependências têm versão, origem, licença e vulnerabilidade avaliadas; release produz SBOM e proveniência.

### Observabilidade e operação

- Logs são estruturados, UTF-8, sanitizados e correlacionados por execução/ciclo/entidade/página.
- Redaction tem testes com tokens, documentos e URLs; mensagens/campos/stack traces possuem limite por evento, limite total por execução e retenção em dias, para evitar vazamento e exaustão de disco.
- Métricas mínimas: duração, páginas, linhas físicas, entidades distintas, invalids, no-op, inserts/updates, retries, rate limit, lag e watermark.
- Falha parcial continua visível; pipeline não publica sucesso global quando etapa obrigatória falha. Checkpoint é por entidade/partição: entidades independentes já <code>PUBLISHED</code> podem confirmar o próprio checkpoint, enquanto a entidade parcial não avança e o ciclo global termina <code>DEGRADED/FAILED</code>.
- Estados funcionais são distintos: <code>PLANNED</code>, <code>EXTRACTING</code>, <code>EXTRACTED</code>, <code>STAGED</code>, <code>PROMOTED</code>, <code>RECONCILED</code> e <code>PUBLISHED</code>, além de <code>BLOCKED</code>, <code>SKIPPED</code>, <code>NOT_APPLICABLE</code>, <code>FAILED</code>, <code>CANCELLED</code> e <code>DEGRADED</code>. <code>COMPLETED</code> de paginação não equivale a conclusão ETL.
- Lock/lease evita dois ciclos ou dois escritores. O estado durável usa a chave semântica única de V2-020 <code>(environment, source_instance, tenant_scope, entity, mode, partition_start, partition_end)</code>; data de negócio é derivada/atributo da partição, não identidade alternativa. Registra lease/heartbeat, ciclo atual, último/próximo ciclo e execução pendente. <code>RUNNING</code> obsoleto é recuperado deterministicamente. Shutdown é gracioso.
- Estado durável fica no banco, não em PID/log/properties como fonte primária.
- A direção ratificada no ADR 0007 é CLI one-shot sob scheduler/supervisor externo; não haverá daemon interno. Scripts são launchers finos. Banco fornece singleton/lease; não usar PID file. <code>status</code>, <code>force-run</code> autorizado e parada graciosa são comandos estruturados. Fechamento mensal roda somente após ciclo elegível.
- Ciclos degradados consecutivos, backlog, máximo de reconciliações e lag têm limites/alertas; um ciclo incompleto nunca é ocultado pelo próximo.
- Runbooks cobrem configuração, deploy, rollback, replay, sweep, indisponibilidade de API/banco, rotação de segredo, backup/restore e incidentes.

### Testes, CI e entrega

- Regras de negócio: testes unitários determinísticos e fixtures legíveis.
- Fonte/paginação: testes de contrato para sucesso, nulo, duplicação, expansão física, erro 2xx, 4xx/5xx, 422, 429, timeout, cap e página terminal.
- Banco: testes de migration em banco vazio e upgrade/baseline, constraints, grants negativos, concorrência, replay e idempotência. Flyway em produção usa forward-fix; rollback de dados depende de backup/restore ou reconstrução da sombra ensaiada, sem prometer down migration genérica.
- Paridade: janelas fechadas com chaves, volumes, nulos, status, datas, relações, somas e fatos; divergência vira bug, correção intencional ou diferença de fonte com aceite nominal.
- Testar DST/timezone, late data, payload parcial, execução interrompida, snapshot incompleto, reativação e dois workers concorrentes.
- CI bloqueia formato, compilação/warnings relevantes, análise estática, testes, schema, segredos, CVEs, licenças/SBOM e smoke do artefato.
- Check desabilitado exige justificativa, issue, responsável e prazo. Threshold impossível, como CVSS 11, não conta como gate.
- Actions e ferramentas de supply chain devem usar versões imutáveis/hashes quando suportado.
- Toda evidência registra data, comando/profile, ambiente, contagens e SHA. Paridade versionada contém apenas métricas sanitizadas e referência a relatório restrito; nenhum ID/payload real entra no Git. Arquivo existente não equivale a execução verde.
- Antes de concluir: revisar diff, UTF-8/mojibake, segredos, contratos, migrations/baseline, rollback, documentação e evidências.

### Governança

- Toda regra nova/alterada tem ID, origem, exemplo, teste, contratos afetados, owner e status de decisão.
- ADR relevante deve estar realmente aceito antes de virar dependência; não misturar “proposto” e “aceito”.
- Débito técnico registra impacto, risco, prioridade, alternativa, responsável e critério de saída.
- <code>STATES.md</code> é atualizado após cada entrega e só marca o que foi efetivamente comprovado.
- Implementação, bootstrap e qualificação podem avançar por entidade; a unidade de cutover produtivo é a menor onda fechada pelo DAG que o roteamento e o write-fence realmente conseguem isolar. V2-048a não encontrou essa prova granular e fixou fail-closed <code>CUTOVER-DB-01/DATABASE_WIDE</code>. Corte menor só pode substituir essa unidade depois de endpoint/alias, consumidor, grants e bloqueio do writer legado serem conjuntamente provados e ensaiados.
- A V2 só poderá assumir DDL/DML produtivo de <code>ETL_SISTEMA</code> por transição formal de ownership. Dashboards permanecem fora e read-only.

## Evidências atuais e limitações

- Em 31/08/2026, <code>.\mvnw.cmd --batch-mode --no-transfer-progress clean verify</code> sob Temurin JDK 17.0.19+10 registrou 431 testes V2, zero falhas, zero erros e zero ignorados, com <code>BUILD SUCCESS</code>; Enforcer, Spotless em 344 arquivos, Checkstyle sem violação, compilação com warnings como erro, 14 regras arquiteturais e JaCoCo bloquearam no mesmo lifecycle.
- O relatório JaCoCo limpo da mesma rodada registra 5.713/6.220 linhas (91,85%) e 2.217/2.932 branches (75,61%). Todos os pisos bloqueantes por pacote foram satisfeitos.
- A integração opt-in do shadow local foi registrada com uma transação rollback-only e gateway sintético. Ela não prova runtime real, Flyway, domínio ou API.
- V2-015b foi revalidada com os artefatos atuais no SQL Server local autorizado <code>localhost/ETL_SISTEMA_V2_SHADOW</code>, autenticação integrada e transações rollback-only. O runner recompôs V001–V006, conferiu cinco manifests/fingerprints, constraints, índices e grants, exercitou transações, fencing, recovery por relógio SQL, publicação/replay/frontier, lifecycle, observabilidade/DQ e contenção em duas sessões, e provou rollback integral. A migração Flyway persistente continua condicionada à transição guardada confirmada pelo owner.
- <code>BusinessDateRange</code>/<code>SourceDateTimeRange</code> atuais usam fim inclusivo e persistem <code>window_end</code>. A decisão-alvo é fim exclusivo interno; inclusão de borda e precisão dos filtros ESL ainda não estão provadas e a tradução só pode ser fixada após testes de fronteira/DST, sem subtração antecipada.
- O runtime principal passou a rejeitar qualquer chave classificada como segredo em <code>-D</code>, CLI ou arquivo de configuração; Data Export só resolve o token por provider de variável protegida quando a fonte é habilitada, e a auditoria de sombra não aceita usuário/senha no JDBC. Os scripts de probe externos continuam separados, bloqueados por V2-041 e sujeitos à sua allowlist; eles não compõem o runtime nem são executados neste bloco.
- V2-020/V2-021 substituíram a auditoria JDBC histórica por control plane, staging e protocolo positivo evidence-bound. O candidate set imutável é aplicado set-based ao estado técnico de <code>core</code> com <code>INSERTED/UPDATED/REACTIVATED/NO_OP/STALE_NO_OP</code>; aplicação, reconciliação, eventos, pointer, lease e fronteira incremental contígua compartilham uma transação com lock por namespace, relógio SQL e retry exato por evidência imutável. V2-023 acrescentou policy DQ canônica por escopo, quatro checks set-based, thresholds, métricas de 23 campos, health/alertas, logs limitados e revalidação transacional antes da publicação. Modos não incrementais não avançam o watermark. A composição do streamer/runtime pertence a V2-022 e a retenção física/produtiva a V2-045b.
- O Dependency Check está com <code>failBuildOnCVSS=11</code>, portanto é informativo. A primeira baseline revisada com feed autorizado continua pendente.
- Os sete usos de Actions estão fixados por SHA de 40 hex, mas os workflows não foram executados por provedor, pois não há commit/remote; portanto ainda não constituem CI ativo ou verde.
- Os validadores SQL conferem manifests/hashes, objetos, constraints, índices, grants/denies, memberships e proibições estruturais. Os exercícios SQLCMD atuais também passaram no alvo local autorizado, inclusive transação externa, savepoint, retry pós-commit incerto, quarantine/lifecycle/DQ fail-closed, V006 sob <code>v2_migrator</code>, thresholds absoluto/percentual, crossing temporal do SLA, concorrência em duas sessões e rollback integral. O SHOWPLAN XML do lifecycle registrou um documento, 3.096 operadores relacionais, 610 seeks, 602 scans, 216 avisos de conversão e zero warning operacional; o SHOWPLAN V2-023 registrou um documento, 242 operadores, 57 seeks, 30 scans, 27 avisos de conversão e zero warning operacional. Eles confirmam compilabilidade/uso dos índices exigidos, não escala. Medição de escala por vertical continua pertencendo a V2-050.
- V2-017 corrigiu os estados dos ADRs 0001–0005 e acrescentou 0006–0009. V2-018 implementou e endureceu configuração/bootstrap/composition root. V2-042a acrescentou o ADR 0010, V2-043 o ADR 0011, V2-044 o ADR 0012, V2-045a o ADR 0013, V2-023 o ADR 0014, V2-048a o ADR 0015, V2-024 o ADR 0016 e V2-025a o ADR 0017; decisões físicas/produtivas continuam nos subgates externos correspondentes.
- V2-048a materializou 131 responsabilidades de cutover — inclusive 35 scripts/34 responsabilidades lógicas de tabela, cinco fatos, 19 contratos, 38 comandos, dois aliases e 15 launchers —, 49 nós de DAG e 11 fences. O catálogo deriva e valida V2-017, incorpora o fingerprint/roles/grants de V2-019 e falha diante de drift, decisão/status desconhecido, wrapper cross-database, rota inventada, fence incompleto ou confusão entre <code>SIMULATED_PNR</code> de V2-048b e o ponto de não retorno produtivo de V2-014.
- Artefatos locais do legado datados de 24/08/2026 registram 528 testes, duas falhas, zero erros e um skipped. As falhas esperavam timeout de 30 minutos e observaram duas horas em testes GraphQL. O legado não pode ser chamado de baseline verde sem nova execução hermética.
- O mapeamento inicial não executou banco, DDL/DML, job, deploy ou escrita externa. O Bloco 14 executou somente SQLCMD local autorizado sob rollback, build/testes e scanners offline. O Bloco 15 executou somente geração/validação documental, testes e scanners offline: não abriu banco, não iniciou JAR/ETL, job ou serviço, não chamou ESL/GraphQL/Data Export/Raster, não leu credenciais, não usou rede/SQL Server remoto e não efetuou deploy, release, cutover ou escrita persistente.
- Na rodada final de requisições por entidade, nenhum <code>curl</code> adicional foi disparado e a matriz permanece pendente em V2-025d. A auditoria de contenção posterior leu as definições sensíveis atuais exclusivamente em memória para comparação booleana local, sem imprimir/persistir valor ou hash; nenhuma coincidiu com outro arquivo ou com o histórico Git local.

## Histórico de tarefas concluídas/incorporadas

- **V2-001/V2-002/V2-007 — Histórico não recuperável:** não há commit, aceite ou artefato no working tree que permita reconstruir com segurança seus títulos/estados. Ficam registrados como **substituídos por V2-016/V2-017**, nunca como concluídos.
- **V2-003 — Catálogo inicial de regras Coletas/Fretes:** incorporado como evidência histórica. V2-017 materializou o catálogo canônico de 75 regras, versionado no baseline local <code>c59489cc70be9c113cdf444118dd1342c0b13903</code>.

- **V2-004/V2-005 — Contratos iniciais de Coletas/Fretes:** forma de filtros, paginação por entidade e identidade foram comprovadas nas janelas fechadas descritas acima. Isso não prova snapshot ou cobertura global.
- **V2-006/V2-006a — Plataforma Data Export:** cliente genérico, streaming serial, limites, transporte seguro, retry/circuit breaker e auditoria mínima de travessia por eventos estão implementados/testados localmente. Página vazia anômala, conclusão ETL e limitador global continuam pendentes.
- **V2-008 — Storage de auditoria em sombra local:** migrations V001–V003 e prova sintética rollback-only foram registradas no alvo local isolado. Não existe storage de domínio.
- **Partes locais de V2-015:** V2-015a foi fechada no G04 com a reexecução/registro dos gates no SHA local <code>0b910432f12d81a306072e24aa44885da94c62a1</code>: Wrapper, Java 17 estrito, formato/lint/warnings, testes, arquitetura, cobertura por pacote, scanner de segredos e Actions imutáveis. V2-015b está concluída com gate de dados rollback-only no SQL Server local. V2-015d ainda precisa de feed/política aprovada; a palavra CI exige V2-016b e execução real no provedor.

## Definition of Done comum às verticais

Uma vertical só pode ser marcada como concluída quando:

1. O contrato versionado registra fonte/template, raiz, filtros, ordenação, paginação, timezone, autenticação, limites, erros e retry.
2. Cada path do <code>/info</code>/<code>/data</code> ou selection set, campo DTO/metadata-only, coluna V1, derivado, filho/relação e coluna publicada possui matriz fonte → DTO com presença → domínio → staging → publicação/consumidor, incluindo cardinalidade, nullability, comprimento, Unicode, transformação e decisão <code>PRESERVE/PROMOTE/NORMALIZE/SPLIT/DERIVE/ALIAS/RETIRE</code>; há zero <code>UNCLASSIFIED</code>.
   Para número financeiro, a matriz também fixa moeda, unidade, precisão, escala e arredondamento; conversão silenciosa, truncamento e soma entre moedas/unidades incompatíveis falham.
3. Identidade, tenant/corporation scope, business keys, aliases, cardinalidade, frescor, dedupe e conflito estão provados; nulo/colisão vai à quarentena.
4. A extração traduz corretamente a janela interna, particiona, percorre até terminal comprovado e termina incompleta em qualquer lacuna/cap/erro. Duas travessias com <code>per</code> distintos medem repetibilidade, não completude. Sweep/cutover exige cursor/contagem/snapshot garantido pelo fornecedor ou oráculo independente com totais + conjuntos de chaves; sem isso, permanece em sombra/upsert sem inferir ausência.
5. Staging é isolado por execução; contagens fecham as equações de raw/distinct/duplicate/valid/quarantine; promoção + auditoria + checkpoint/publication pointer seguem transação/protocolo recuperável.
6. A execução percorre estados funcionais válidos até <code>PUBLISHED</code> ou um terminal explícito <code>BLOCKED/SKIPPED/NOT_APPLICABLE/FAILED/CANCELLED/DEGRADED</code>; reutilização conflitante de <code>execution_id</code>, mutação de histórico ou <code>COMPLETED</code> apenas de paginação falham.
7. Payload parcial não apaga valor conhecido; terminalidade, late data, out-of-order e reativação seguem regras testadas.
   Código/status desconhecido é preservado como bruto e tratado por política explícita; não cai silenciosamente em categoria conhecida. Se afetar terminalidade, elegibilidade, financeiro ou ausência, bloqueia promoção até classificação.
8. Checkpoint da entidade/partição só avança depois de terminalidade, staging/persistência, integridade e gates aplicáveis; falha de outra entidade afeta o status global, não desfaz checkpoint independente já publicado. Ledgers são isolados por modo e somente a fronteira incremental contígua publicada avança o watermark; sweep/bootstrap/backfill/replay nunca o avançam.
9. Quarentena tem threshold absoluto + percentual, equação, SLA, owner e replay. A política declara se uma execução pode terminar com inválidos; perda de registro da fonte nunca avança checkpoint por tolerância genérica.
10. Há testes unitários, de mapping, contrato, banco sombra, migration, replay, concorrência, falha, redaction, fronteira temporal e timezone.
11. Paridade em janela fechada compara chaves, volumes, nulos, status, datas, relações, somas e métricas específicas, sem versionar ID/payload real.
12. A vertical possui matriz <code>ABORT/RETRY/REPARTITION/DEGRADE/BLOCK/SKIP/CONTINUE_WITH_ALERT</code>, bloqueio de dependentes, exit code e budgets numéricos de duração, memória, IO, lag, throughput e quota.
13. Toda divergência ou decisão externa registra papel owner, pessoa/time aceitante, data, evidência, decisão e prazo; “owner pendente” não passa gate.
14. Observabilidade, segurança, retenção, runbook, forward-fix/restore, rollback de release e atualização deste arquivo estão concluídos.
15. Memória produtiva permanece <code>O(maxResponseBytes + batchBytes)</code>, com backpressure e no máximo uma página/lote em voo; teste com muitas páginas comprova platô de heap e proíbe coleção de vida da execução.
16. Dedupe, frescor, joins, crosswalks, presença/sweep, current/history, paridade, <code>COUNT/SUM</code>, DQ, dimensões e fatos são executados set-based no SQL Server, com planos/índices medidos; Java recebe somente contadores <code>O(1)</code> e amostra limitada.

## Tarefas pendentes

As tarefas abaixo são o backlog canônico. A ordem de execução está na seção seguinte; numeração não implica execução sequencial automática. Uma tarefa de implementação de vertical pode atingir o marco **IMPLEMENTADA_EM_SHADOW** quando está pronta para V2-012a; isso não significa que a vertical cumpriu a Definition of Done, foi publicada produtivamente ou pode sofrer cutover.

### Fase 0 — Baseline e governança

- [ ] **V2-041 — Rotacionar segredos expostos e fechar a superfície de vazamento.**
  - **Integração funcional local em14/09/2026 — V2-041:** Nenhum mecanismo funcional verificado nesta unidade; requisito original preservado. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Atestados autenticados de rotação/invalidação e continuidade das seis classes e do único writer legado, responsáveis/data por papel do intake existente. Secret store e jobs reais enumerados por Segurança/Operações. Rotação/health/continuidade reais, sem leitura de segredo nesta rodada. Nenhum checkbox ou numerador alterado.
  - **Tipo/fila:** <code>EXTERNAL_HOLD</code>. Não é bloco de implementação e não deve ser selecionado novamente por outro chat sem evidência operacional nova. Continua bloqueando uso externo das credenciais, sonda, health check remoto, release, deploy e cutover; não bloqueia código, testes sintéticos, documentação, baseline local ou desenho offline que não leia segredos.
  - **Entrega:** inventariar jobs/serviços/operadores que usam cada token; coordenar rotação ESL sem interromper o legado, com janela, dual-token/rollback se suportado e health check dos jobs produtivos; coordenar a credencial Raster com seu owner, sem testá-la enquanto não houver autorização Raster; atualizar secret store e executar secret scan.
  - **Aceite:** continuidade do único writer legado comprovada, valores antigos invalidados no momento coordenado, novos consumidores saudáveis e evidência sanitizada com owner/data. A remoção de <code>-D</code> e hardening de código pertencem a V2-018.
  - **Intake local preparado, sem bloco e sem evidência recebida:** <code>INTAKE_STATE=CONTRACT_READY_EVIDENCE_NOT_RECEIVED | MAX_LOCAL_OUTCOME=STRUCTURALLY_VALID_UNVERIFIED | INTAKE_UNLOCKS=NONE</code>. O catálogo <code>docs/catalogos/evidencia-rotacao-v2-041/</code> e o gate <code>scripts/validation/Test-V2041RotationAttestation.ps1</code> validam somente a estrutura de um atestado sanitizado mantido temporariamente sob <code>target/</code>. Eles não comprovam autenticidade, rotação, invalidação, continuidade ou aceite nominal; V2-041 e G01 permanecem <code>EXTERNAL_HOLD</code>, e nenhum sucessor ou Bloco 44 é autorizado por esse resultado.
  - **Evidência do preparo em 06/09/2026, sem bloco funcional:** o primeiro RED retornou <code>V2_041_ATTESTATION_CATALOG_MISSING</code>; o RED de integração recusou o estado ausente nos três documentos. O GREEN final do modo explícito <code>-ContractOnly</code> conferiu seis classes, sete papéis, três scans, fixture ancorada por SHA-256 e 19 contraprovas com reason code exato, declarando <code>CONTRACT_VALID</code> e <code>evidence=NOT_EVALUATED</code>. Omissão/combinação de modos e <code>-ValidateEvidence</code> sem recibo falharam com exit code 1; a trilha permaneceu em 46/102, 204 fatias abertas, zero <code>AGORA</code> e Bloco 44 não atribuído. O self-test do scanner passou em nove casos e a varredura offline passou sobre 883 candidatos/882 textos/um binário verificado, sem finding ou conteúdo não inspecionado. Não houve rede, leitura de <code>.env</code>, credencial, secret store, banco, job, deploy, commit ou push; Maven não é aplicável a esta manutenção Markdown/JSON/PowerShell.
  - **Inventário executável fechado pela V1:** ESL REST é configuração residual sem cliente/step produtivo, embora wrappers ainda carreguem o nome da variável; deve ser retirado, não migrado. GraphQL é ativo em Coletas, Fretes, Usuários, snapshots/reconciliação, auditoria e completude. Data Export é ativo nas sete verticais, snapshots de expurgo e enriquecimento 6389 de Fretes; esse último exige GraphQL + Data Export. Raster é módulo opcional/auto-habilitável no fluxo completo. SQL Server é obrigatório para repositórios, auditoria, locks, histórico, materializações e quase toda a CLI. O runtime operacional é o fat JAR/CLI e seu daemon Java filho; não existe Windows Service nem instalador de tarefa agendada versionado no repositório.
  - **Launchers consumidores:** execução completa, intervalo, teste de API, loop/daemon, auditoria, validação, retrofit, expurgo noturno e fechamento mensal; exportação/validação SQL e administração SQLite formam trilhas separadas. A V1 constrói GraphQL e Data Export avidamente no composition root, de modo que comandos isolados podem exigir ambos por efeito arquitetural; isso é defeito a remover, não consumidor de negócio a perpetuar. Scripts de teste/probe e <code>docs/legado</code> não são consumidores produtivos.
  - **Owner que o código consegue definir:** o repositório V1 é o owner técnico do writer e do schema SQL; o principal que executa o agendamento é o boundary de Operações. Pessoa/time nominal para administração ESL, Raster, credencial SQL e Task Scheduler não existe em código e continua metadado externo de governança, nunca nome inventado por IA.
  - **Secret scan auxiliar corrigido como evidência:** na V2, a enumeração atual encontrou 302 candidatos: 301 textos UTF-8 inspecionados e o JAR do Maven Wrapper validado pelo checksum fixo, zero achados, zero arquivo oversized e zero conteúdo não inspecionado. O self-test isolado cobre nove casos, inclusive marcadores sintéticos amplos, exemplo de ambiente, arquivo ignorado sensível, binário desconhecido, NUL embutido e não exposição do valor-fixture. No legado, permanece a evidência histórica <code>FAIL</code> com 53 sinais: 33 alertas de conteúdo em 24 posições/14 arquivos, 15 textos oversized não examinados, quatro arquivos sensíveis pelo nome e uma fixture Bearer. Nenhum valor foi reproduzido.
  - **Gitleaks canônico executado em 29–30/08/2026:** binário oficial 8.29.1 temporário, arquivo de release validado contra o checksum publicado e saída 100% redigida. <code>gitleaks dir</code> terminou <code>PASS</code>, zero achados, no espelho temporário exato dos 297 candidatos. No legado, a evidência anterior registrou 33 sinais no worktree e três no histórico local, todos redigidos. A V2 ainda não possui commit/SHA/histórico; portanto a varredura <code>gitleaks git --log-opts="--all"</code> deve ocorrer imediatamente após o primeiro baseline autorizado e novamente no remoto após o fetch de V2-016b.
  - **Lacunas do gate corrigidas em V2-016a:** a allowlist global <code>^target/</code> foi removida; exemplos <code>.env*</code> entram no conteúdo escaneado; exceções sintéticas são exatas por regra+caminho+literal; binário/oversized são inventariados com falha fechada; os workflows declaram config/redaction/scope e todas as Actions usam SHA imutável. <code>target</code> continua fora do Git pela enumeração/<code>.gitignore</code>, não por exceção capaz de esconder arquivo rastreado.
  - **Atualização em 04/09/2026:** a autorização explícita permitiu o primeiro commit local <code>c59489cc70be9c113cdf444118dd1342c0b13903</code>, com 696 arquivos. O scanner auxiliar passou no conjunto exato (696 candidatos; 695 textos, um binário verificado, zero finding) e o Gitleaks 8.29.1 passou no espelho indexado e no histórico de um commit, ambos com redaction. A política recebeu uma exceção mínima de <code>generic-api-key</code> para o caminho e o match estático exatos do inventário de comandos Windows do gerador de cutover; ela não amplia a exceção a outro arquivo, regra ou literal. Não há remote, push, CI de provedor ou mudança de credencial.
  - **Causa do agendamento não zero comprovada:** <code>\ETL Expurgo Orfaos Noturno</code> está habilitada/Ready, diariamente às 03:00, e retornou 1 em 29/08/2026. Há 30 logs consecutivos de 31/07 a 29/08 que param antes da linha <code>Executando:</code>. O JAR existe; o Windows PowerShell 5.1 aborta em <code>java -version 2&gt;&amp;1</code> porque <code>$ErrorActionPreference='Stop'</code> converte o <code>stderr</code> normal do Java em <code>NativeCommandError</code>. Reprodução local isolada confirmou a causa sem iniciar o ETL. Logo, o código 1 não é falha de negócio do expurgo: o Java e as materializações SQL nunca começaram.
  - **Correção operacional requerida no legado:** capturar a versão Java por <code>System.Diagnostics.Process</code> ou tratamento equivalente que avalie o exit code nativo sem converter <code>stderr</code> em exceção; envolver o bootstrap inteiro em tratamento que publique a falha; criar regressão em Windows PowerShell 5.1; revisar o principal <code>Interactive/Limited</code>, <code>StartWhenAvailable=false</code> e telemetria do Task Scheduler desabilitada; somente o owner executa o teste manual e confirma o próximo disparo. Essa correção pertence ao writer legado e não autoriza este roadmap a iniciar/reiniciar o job.
  - **Estado após a auditoria:** a contenção do repositório e o diagnóstico estão fechados; nenhuma credencial foi emitida, alterada, testada ou revogada e nenhum secret store/job/serviço foi modificado. Rotação, invalidação e health check continuam obrigatórios antes de qualquer rede/release/deploy, mas não bloqueiam o próximo bloco offline.

- [ ] **V2-016 — Criar baseline versionado e governança real do repositório.**
  - **Integração funcional local em14/09/2026 — V2-016:** POM, scanners e New-QualificationPackage.ps1 já fornecem build, gates e pacote local reproduzível. Esta revisão executou esses consumidores; a integração de CI/proteção remota depende de provider, remote/branch e owners ainda não fornecidos. Não foi criado workflow fictício. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Provider, URL de remote, branch protegida, owners e política de CI/publicação nominal. Repositório remoto e permissões reais de proteção/release. Checks do SHA publicado, proteção de branch e revisão humana. Nenhum checkbox ou numerador alterado.
  - [x] **V2-016a — Fechar o baseline local versionável.**
    - **Depende de:** nenhuma ação externa; V2-041 não é dependência.
    - **Entrega:** corrigir todas as referências para o casing canônico <code>STATES.md</code>; revisar <code>.gitignore</code> e conjunto exato a versionar; corrigir a política/scanner conforme V2-041; fixar Actions por SHA; criar CONTRIBUTING, SECURITY e LICENSE/NOTICE conforme decisões documentáveis; executar build, scanner auxiliar e Gitleaks <code>dir</code> redigido antes do primeiro commit. Criar o commit baseline somente quando houver autorização inequívoca.
    - **Aceite:** árvore local limpa para versionamento, links válidos em sistema case-sensitive, zero segredo/artefato gerado no conjunto candidato e comandos reproduzíveis registrados. Se o commit for autorizado, executar imediatamente Gitleaks <code>git</code> no SHA; se não for, encerrar V2-016a como <code>READY_FOR_BASELINE_COMMIT</code>, sem bloquear V2-017/offline.
    - **Estado/evidência em 30/08/2026:** <code>READY_FOR_BASELINE_COMMIT</code>, sem commit por ausência de autorização. São 302 candidatos atuais, nenhum em <code>target</code>, nenhum acima de 5 MiB, 301 textos e um único binário aprovado por caminho+checksum. Maven offline terminou <code>BUILD SUCCESS</code> com 286 testes, zero falha/erro/skip, Enforcer/Spotless/Checkstyle/arquitetura/JaCoCo verdes; scanner auxiliar e seu self-test terminaram <code>PASS</code>. A última execução disponível do Gitleaks 8.29.1 redigido permanece a do espelho exato anterior de 297 candidatos, zero achado; o binário não estava instalado nesta rodada e não foi baixado. Todos os links Markdown relativos resolvem com casing exato e todas as referências a Actions permanecem fixadas por SHA de 40 hex. CONTRIBUTING, SECURITY, LICENSE e NOTICE registram somente o estado comprovável, sem inventar owner, SLA, CI ativo, release ou licença. O histórico Git continua objetivamente inexistente e não bloqueia trabalho offline; o Gitleaks deve ser reexecutado contra o conjunto exato imediatamente antes do primeiro commit autorizado.
    - **Fechamento em 04/09/2026:** baseline local criado no SHA <code>c59489cc70be9c113cdf444118dd1342c0b13903</code>, com 696 arquivos e <code>mvnw verify</code> executado sob JDK 17 portátil verificado; os relatórios registraram 542 testes, zero falha/erro/skip e JaCoCo presente. O scanner auxiliar passou em 696 candidatos e Gitleaks 8.29.1 passou tanto no espelho indexado quanto em <code>git --log-opts="--all"</code> para o único commit. A governança remota de V2-016b permanece aberta.
  - [ ] **V2-016b — Ativar governança remota.**
    - **Depende de:** V2-016a, remote/provedor e autorização explícita do owner.
    - **Entrega:** configurar remote, branch protection, PR checks, CODEOWNERS/owners nominais e executar CI/Gitleaks no SHA publicado, sem ampliar permissões.
    - **Aceite:** histórico remoto completo escaneado após fetch autorizado, checks obrigatórios verdes e evidência ligada ao SHA. Não bloqueia implementação offline; bloqueia qualquer gate chamado de CI/release.

- [x] **V2-017 — Consolidar ADRs, catálogo de regras e baseline de portabilidade.**
  - **Integração funcional local em14/09/2026 — V2-017:** Nenhum mecanismo funcional verificado nesta unidade; requisito original preservado. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Aceites por campo pertencem à vertical dona; catálogo V2-017 já aceito, sem decisão nova para reabrir. Nenhuma configuração nova para o catálogo. Aplicação/paridade real tratadas nas respectivas unidades, sem segunda contagem. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-016a em <code>READY_FOR_BASELINE_COMMIT</code> ou concluída; não depende de V2-016b para trabalho offline.
  - **Entrega:** corrigir status dos ADRs e ratificar/ajustar as direções deste arquivo; classificar comandos, entidades, 35 scripts/34 responsabilidades de tabela, 58 migrations, quatro grupos de índices, constraints, seeds, 24 validações, cinco procedures, 19 views, três SQL auxiliares e 15 scripts Windows; criar matriz de proteção com classe/finalidade/colunas, minimização, criptografia/chaves, mascaramento, perfis, retenção e legal hold.
  - [x] **V2-017a — Materializar a matriz executável fonte → persistência → consumo por campo.** Uma linha para todo path de <code>/info</code>/<code>/data</code>/selection set, campo DTO ou apenas em <code>metadata</code>, coluna física V1, derivado/computado/técnico, filho/relação e coluna <code>pub/mart</code>. Colunas mínimas: entidade/versão/template/raiz; source path, tipo, presença e cardinalidade; solicitado; DTO/presence; tabela.coluna V1 ou classificação metadata/derivada; destino V2 <code>stg/core/ref/crosswalk/mart/pub/ctl/recon</code>; transformação/fuso/reducer/fallback; unidade/moeda/precisão/escala; PII/proteção/retenção; consumidor; decisão <code>PRESERVE/PROMOTE/NORMALIZE/SPLIT/DERIVE/ALIAS/RETIRE</code>; owner/evidência/fixture/status.
  - **Baseline de V2-017a:** reconciliar nominalmente 442 campos de metadata observados nos nove Data Exports contra 459 colunas das nove tabelas operacionais; depois os 18 campos físicos de Usuários e, se aplicável, os 58 de Raster. Incluir os 17 campos de Coletas hoje somente em metadata, raízes×filhos e todo output das 19 views/cinco fatos. Contagens são checks de drift, não mapeamento automático.
  - **Aceite:** matriz inicial versionada com fonte/oráculo, impacto, destino, dependências, owner-papel, campo de aceitante nominal/time, evidência esperada e status. Quando o nome/time não existe nos oráculos locais, registrar <code>TIME_NOMINAL_NAO_INFORMADO</code>, gate externo e bloqueio de publicação/cutover em vez de inventar identidade; V2-014/V2-040 exigem o aceitante real antes de qualquer corte. V2-040 — não esta tarefa — confirma a matriz final.
  - **Aceite de V2-017a:** zero <code>UNCLASSIFIED</code>; <code>UNRESOLVED</code> possui owner/prazo e bloqueia publicação/cutover. <code>metadata</code> nunca é destino implícito, e Raster retirado conserva linhas <code>NOT_APPLICABLE</code> em vez de desaparecer do inventário.
  - **Estado/evidência em 01/09/2026:** <code>READY_FOR_BASELINE_COMMIT</code>. Toda a entrega local de V2-017/V2-017a está fechada em <code>docs/catalogos/portabilidade</code>, gerada deterministicamente por <code>scripts/validation/Build-PortabilityCatalog.ps1</code> e validada por <code>Test-PortabilityCatalog.ps1</code>; os checkboxes permanecem abertos somente porque o repositório ainda não possui commit autorizado e, portanto, a matriz ainda é local versionável/reproduzível, não versionada em SHA. São 401 linhas de artefatos e 2.437 linhas de campo: 442 slots ordinais/opacos de <code>/info</code>, 270 paths candidatos separados de <code>/data</code>, 459 colunas operacionais, 18 de Usuários, 58 de Raster, 181 folhas GraphQL, 336 colunas dos cinco fatos e 673 outputs das 19 views. V2-025a acrescentou o path observado <code>/data/fit_p_m_pck_sequence_code</code> de 6389 como candidato bloqueado para publicação, sem promovê-lo a chave ou relação. O inventário inclui 170 constraints distintas e seis índices <code>UNIQUE</code> embutidos além dos quatro grupos/47 índices dedicados. O catálogo possui 75 regras com casos sintéticos positivos/negativos específicos e 16 classes/políticas de proteção, zero <code>UNCLASSIFIED</code> e nenhum destino V2 implícito <code>metadata</code>. Os 442 nomes atuais de <code>/info</code> não foram versionados e não são inferidos dos 270 candidatos de <code>/data</code>; todos os slots registram owner-papel, precedência <code>V2-041→V2-025d</code> e bloqueio de publicação. Versão/fingerprint, canal, campo/tipo DTO, grupo de linhagem e política de proteção são explícitos; match por nome continua provisório e bloqueado. Classificação automática de proteção permanece sujeita à ratificação produtiva de V2-045b. Nenhuma linha possui aceitante nominal/time versionado e, por isso, todas bloqueiam publicação/cutover. V2-025d substitui os ordinais por fingerprints/nomes sanitizados após V2-041, as fatias verticais fecham mapeamentos/reducers e V2-040 confirma a matriz final.
  - **Revalidação da fatia Usuários em 01/09/2026:** as 18 colunas físicas de current/history e as quatro folhas <code>USERS_SNAPSHOT</code> possuem destino, presença, transformação, proteção e gates explícitos com estado <code>IMPLEMENTED_IN_SHADOW</code>; ausência/<code>updatedAt</code> inventados foram retirados. O follow-up V009/V2-035b acrescentou <code>core.v_usuario_dimension_current_v1</code> como projeção interna current-only, sem grant, sem alterar a cardinalidade da matriz. Os três outputs legados <code>PUB-0013..PUB-0015</code> continuam exatamente <code>CONSUMER_CONTRACT_PENDING</code>, bloqueados até V2-037. A regeneração determinística preservou 401 artefatos, 2.437 campos, 75 regras, 16 classes e zero <code>UNCLASSIFIED</code>; o estado agregado <code>READY_FOR_BASELINE_COMMIT</code> não mudou.
  - **Fechamento em 04/09/2026:** o baseline de portabilidade foi registrado no SHA local <code>c59489cc70be9c113cdf444118dd1342c0b13903</code>. <code>Test-PortabilityCatalog.ps1 -VerifyGenerated</code> passou com 401 artefatos, 2.437 campos, 75 regras, 16 classes de proteção e zero <code>UNCLASSIFIED</code>; <code>Test-Gpt56ChatTrail.ps1</code> passou com 30 marcos históricos, 213 fatias abertas, 30/88 checkboxes concluídos e uma única rota <code>AGORA</code>. O scanner auxiliar/Gitleaks do conjunto exato e o Gitleaks histórico de um commit passaram. V2-017/V2-017a estão concluídas como baseline local versionado; publicação, cutover, nomes de aceitantes, V2-025d, mapeamentos/reducers verticais e a confirmação final de V2-040 continuam nos gates próprios.

- [ ] **V2-015 — Tornar qualidade e segurança gates reais.**
  - **Integração funcional local em14/09/2026 — V2-015:** Gates executáveis locais de estilo, arquitetura, cobertura e scanner; feed/CI produtivos pendentes. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Política de feed/CI e critérios de segurança operacional/aceitantes da revisão. CI remoto, TLS e principals do ambiente de release. Pipeline publicado, scanner com feed autorizado, revisão humana e qualificação operacional. Nenhum checkbox ou numerador alterado.
  - **Qualificação do pacote em13/09/2026: TESTADO_NA_CAMADA_LOCAL.** Verify03 passou estilo/warnings/arquitetura/cobertura .80/.60 sem redução;scanner e16contraprovas preservam casos antigos e recusam mutação de descrições públicas. SBOM CycloneDX1.6 confere com9artefatos;feed NOT_EXECUTED,sem herdar baseline externa. Validadores finais e logs íntegros ligados pelo selo. Não fecha V2-015c/d. Checkpoint0141.
  - **Depende de:** V2-016a para gates locais e V2-016b para qualquer gate chamado de CI/remoto.
  - [x] **V2-015a — Gate inicial:** Java 17 fixo, Wrapper, format/lint, warnings, testes, arquitetura, Gitleaks, threshold de linhas/branches por pacote orientado a risco e Actions imutáveis em SHA versionado. O gate arquitetural recusa API produtiva que devolva coleção ilimitada, repositório que materialize o universo de chaves e fallback que acumule a travessia inteira.
    - **Evidência pré-baseline em 30/08/2026:** <code>READY_FOR_BASELINE_COMMIT</code>. Toda a entrega local estava bloqueante e reproduzível: Maven/bytecode exigiam exclusivamente Java 17; Wrapper 3.9.14 e seu JAR/distribuição conferiam com os dois SHA-256 fixados; Spotless usa Google Java Format 1.24.0 compatível com JDK 17; <code>-Xlint:all</code> falha em warning; 286 testes passaram em <code>clean verify</code>. O JaCoCo aplica os pisos por pacote documentados e o gate arquitetural possui fixtures negativas/positivas para materialização e limites. O Gitleaks 8.29.1 redigido passou no espelho exato anterior dos 297 candidatos; o scanner auxiliar passou nos nove casos e nos 302 candidatos atuais. Todos os usos de Actions têm SHA de 40 hex e os relatórios locais/remotos têm procedimento e retenção de 14 dias.
    - **Fechamento G04 em 04/09/2026 15:26 -03:00:** no conjunto Git exato do SHA <code>0b910432f12d81a306072e24aa44885da94c62a1</code>, o Temurin JDK 17.0.19+10 e Maven Wrapper 3.9.14 executaram <code>.\\mvnw.cmd --batch-mode --no-transfer-progress clean verify</code> com <code>BUILD SUCCESS</code>, 542 testes, zero falha/erro/ignorado, Enforcer, Spotless (435 arquivos), Checkstyle, arquitetura, warnings como erro e JaCoCo por pacote bloqueantes. <code>Test-OfflineSecretScan.ps1</code> passou com nove casos e <code>Invoke-OfflineSecretScan.ps1 -Source .</code> passou no mesmo conjunto de 696 candidatos (695 textos, um binário verificado, zero finding). Gitleaks 8.29.1, com <code>--redact=100</code>, passou no espelho de 696 arquivos rastreados e em <code>git --log-opts="--all"</code> de dois commits, sem achados. Os sete usos de Actions nos dois workflows permanecem fixados por SHA de 40 hex; o JAR/distribuição do Wrapper mantêm os dois pins SHA-256. Não houve rede de fonte, credencial, banco, CI de provedor, deploy, release, commit ou push.
  - [x] **V2-015b — Gate progressivo de dados:** após V2-019/V2-020, SQL Server efêmero, migrations, manifesto/fingerprint, constraints e grants negativos.
  - **Estado/evidência em 31/08/2026:** <code>COMPLETO_LOCAL</code>. <code>Invoke-ProgressiveDataGate.ps1</code> passou contra <code>localhost/ETL_SISTEMA_V2_SHADOW</code> com autenticação integrada, alvo exato, UTF-8 e <code>-b</code>. O gate recompôs V001–V005 sob transação, validou os quatro manifests/fingerprints, objetos, constraints, índices, FKs compostas, roles e grants/denies; exercitou baseline e migrations individuais, savepoint, falha fechada, retry, relógio SQL, fencing, locks concorrentes de publicação/lifecycle em sessões distintas, cinco disposições de aplicação, frontier incremental, hard gate de contrato/configuração e lifecycle completo. Todos os sentinels confirmaram rollback integral; nenhum SQL Server remoto/produtivo foi usado e nenhuma alteração persistente ficou no alvo local.
  - **Revalidação em 01/09/2026 por V2-033:** o runner recompôs V001–V007, conferiu seis manifests/fingerprints e os validators estruturais atuais, executou baseline/migrations, kernel/publicação/permit, lifecycle, observabilidade/DQ e Usuários, além dos negativos 027/029, concorrência em duas sessões e três SHOWPLANs. Usuários registrou cinco statement paths, 205 seeks, zero conversão e zero warning operacional; lifecycle registrou o seek limitado do budget tipado e zero warning operacional. Todos os exercícios terminaram com rollback integral no mesmo alvo local, sem banco remoto ou efeito persistente.
  - **Revalidação em 01/09/2026 por V2-035b/Usuários:** o gate passou a recompor V001–V009 e oito manifests/fingerprints. Os validators/exercícios 035–037 comprovam a dimensão current interna, grão único, filtro ativo, identidade type-tagged, presença tri-state, histórico sem multiplicação, ausência de trim/grant e rollback; o SHOWPLAN dimensional registrou três documentos, cinco relops, três seeks, um scan legítimo da projeção completa e zero conversão/warning operacional. A allowlist continua com 26 grants, sem reader novo.
  - [ ] **V2-015c — Gate final de release por onda:** após V2-039b da onda, SAST, SBOM/licenças/proveniência e smoke do pacote nos SOs suportados; bloqueia V2-014 daquela onda.
  - [ ] **V2-015d — Corrigir e aceitar o gate de vulnerabilidades.** Trocar CVSS 11 por política bloqueante aprovada, executar com feed/NVD autorizado, classificar achados, registrar exceções com prazo/owner e aceitar a primeira baseline sem imprimir segredo.
    - [x] **V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED — Congelar a decisão sensível sem consultar feed.**
      - **Rota/bloco:** <code>G05A</code>, Bloco 42, GPT-5.6 Sol Ultra. Depende somente de V2-016a e V2-015a, ambas concluídas; V2-016b continua necessário antes de chamar qualquer resultado de CI/remoto.
      - **Entrega Sol:** versionar a decisão fail-closed para limiar/default técnico, score/severidade ausentes, relatório ausente ou malformado, erro do scanner, formatos obrigatórios e schema de exceção com justificativa, owner-papel e expiração. O catálogo e as contraprovas sintéticas tornam a decisão inequívoca, mas este bloco não altera <code>pom.xml</code>, workflow ou suppressions e não executa Dependency-Check/feed.
      - **Aceite Sol e evidência em 06/09/2026:** <code>POLICY_DECISION_LOCAL_COMPLETE_TERRA_IMPLEMENTATION_PENDING</code>. O RED obrigatório de <code>Test-DependencyVulnerabilityPolicy.ps1 -PolicyOnly</code> terminou com exit code 1 e <code>POLICY_CATALOG_OR_MANIFEST_MISSING</code>; após materialização, o mesmo gate passou com fingerprint <code>sha256:83c8698663f33b0e79ac987d72c7853fb59b12af42f31b07d1809377a454e056</code>, 33 casos sintéticos, três PASS, 30 BLOCK e zero exceção efetiva. <code>-VerifyImplementation</code> ficou vermelho com exit code 1 e 11 divergências físicas, incluindo <code>POM_FAIL_BUILD_ON_CVSS_NOT_ZERO current=11</code> e <code>WORKFLOW_ARTIFACT_MISSING_BEHAVIOR_NOT_ERROR current=warn</code>, sem enfraquecer o validator. O catálogo/manifesto, o runbook <code>v2-015d-decisao-politica-vulnerabilidades-sol.md</code> e o validator foram criados; 11 arquivos da campanha passaram UTF-8 estrito sem BOM e <code>git diff --check</code> passou. O validator da trilha confirmou 45/102 checkboxes, 45 marcos, 205 fatias abertas e G05T/Bloco 43 como única rota <code>AGORA</code>; o self-test do scanner passou 9 casos e a varredura offline passou com 878 candidatos, 877 textos, um binário verificado e zero finding. <code>pom.xml</code> e os workflows permaneceram sem diff. Não houve rede/feed/NVD, Dependency-Check, suppressions efetivas, findings/IDs/dependências reais, segredo, leitura de <code>.env</code>, banco ou CI remoto. V2-015d e V2-015 permanecem abertas.
    - [x] **V2-015d/IMPLEMENTACAO_LOCAL_FAIL_CLOSED — Aplicar mecanicamente a decisão congelada.**
      - **Rota/modelo:** <code>G05T</code>, Bloco 43, concluída localmente; depende de V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED, concluída no Bloco 42. <code>MODELO_RECOMENDADO=TERRA_XHIGH | MODELO_EXECUTADO=SOL_ULTRA | MOTIVO=LOTE_UNICO_AUTORIZADO_PELO_OWNER</code>.
      - **Entrega Terra:** aplicar no POM/workflow o limiar e os modos decididos, preservar <code>failOnError=true</code>, JSON/HTML, chave somente por ambiente e artefato obrigatório; implementar parser/harness e fixtures sintéticas para vulnerabilidade, score ausente, relatório/erro e exceção válida/expirada. Não baixar feed nem executar o perfil <code>security-audit</code>.
      - **Aceite Terra:** red/green focados, Maven offline sem perfil de feed, validators estáticos, scanner offline, UTF-8 estrito e <code>git diff --check</code> verdes. O resultado máximo é <code>LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING</code>; V2-015d e V2-015 agregadas permanecem abertas para G05 executar feed/NVD autorizado, aceite nominal, classificação de achados e primeira baseline real.
      - **Execução e retomada G05T em 06/09/2026 — concluída localmente:** a verificação física partiu de 12 divergências, incluindo <code>current=11</code>, <code>current=warn</code> e comando sem <code>clean</code>, e ficou verde após aplicar threshold <code>0.0</code>, três guardas Enforcer, fingerprint, validação pré/pós Maven e artefato obrigatório. O parser sintético bloqueou HTML ausente e uma vulnerabilidade score <code>0</code>, e aceitou o relatório limpo. Policy, implementação, manifesto de schema, scanner 9/9, varredura offline de 878 candidatos/877 textos/um binário sem finding, UTF-8 em 14 arquivos e diff check passaram antes do Maven. A primeira execução <code>.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify</code>, no JDK 17.0.20.1, terminou com 656 testes, uma falha, zero erros e um skip: <code>CharacterizationOfflineBoundaryTest.leavesEveryEntityQ01AndEveryRealCharacterizationStageOpen:103 expected false but was true</code>. O teste, em arquivo não rastreado preexistente, excluía Q-FND-01 mas não Q-BST-01; o lookahead foi corrigido para ambas as rotas administrativas e a regressão dirigida passou com <code>javac -encoding UTF-8</code> e invocação direta. Na retomada autorizada, o mesmo <code>clean verify</code> offline, ainda sem <code>security-audit</code>, terminou <code>BUILD SUCCESS</code> às 14:30:18 -03:00 com 656 testes, zero falhas, zero erros e um skip esperado; Enforcer, Spotless em 528 arquivos, Checkstyle sem violações e todos os checks de cobertura passaram. O resultado <code>LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING</code> foi concedido somente a esta subfatia local. V2-015d e V2-015 permanecem abertas para G05 executar feed/NVD autorizado, aceite nominal, classificação de achados/exceções e primeira baseline real; não existe rota <code>AGORA</code> elegível e o Bloco 44 não foi atribuído. Evidência detalhada: <code>docs/runbooks/v2-015d-implementacao-local-fail-closed-sol.md</code>.
  - [x] **V2-015e — Reconciliar os contratos Java/SQL de Coletas e Cotações antes da qualificação externa.**
    - **Depende de:** V2-010 e V2-027, ambas já implementadas somente em shadow. A repriorização explícita do owner materializou esta fatia como Bloco 40/G12; não reabre nem reescreve os fechamentos históricos dessas tarefas.
    - **Entrega:** corrigir exclusivamente as divergências comprovadas entre contratos congelados, Java e V010/V011: precedência retroativa de terminalidade COL-03; <code>sequence_code</code> positivo no intervalo <code>BIGINT</code> ponta a ponta; presença <code>ABSENT/NULL/VALUE</code> de Cotações sem apagamento por ausência; resolução tarifária sobre UFs efetivas e isoladas pela tuple completa; moeda de origem obrigatoriamente nula porque COT-02 a fornece somente pela referência governada.
    - **Aceite:** testes vermelhos executados e registrados antes da correção; fixtures sintéticas SQL rollback-only para terminal retroativo, não regressão terminal, stale aberto, limites <code>BIGINT</code>, tri-state, zero como valor, tarifa efetiva e isolamento entre tenants; validators estruturais, gate progressivo, testes Java offline e exercícios locais autorizados verdes. Alterar V010/V011 em place somente após confirmar que continuam drafts não publicados e nunca aplicados persistentemente; se houver histórico Flyway aplicado, usar uma única migration forward na próxima versão livre, nunca <code>repair</code>. Não autoriza fonte externa, Q-*-01, bootstrap, relação, sweep, publicação, release, deploy ou cutover.
    - **Estado e estratégia de migration em 06/09/2026:** <code>CONCLUIDO_LOCAL_SHADOW_ROLLBACK_ONLY</code>. O preflight por <code>master</code> confirmou exclusivamente <code>RTR-SVW-002/ETL_SISTEMA_V2_SHADOW/ONLINE</code>; o alvo registrou zero linhas 010/011 em <code>flyway_schema_history</code>, zero objetos V010/V011 antes da edição e o histórico Git não contém essas migrations. V010/V011 continuavam drafts locais, portanto foram corrigidas in place; não houve <code>repair</code>, V013 ou correção forward. O baseline já as inclui por <code>:r</code> e não exigiu alteração textual; manifests/fingerprints e validators derivados foram reconciliados.
    - **Reds registrados:** no JDK 17, <code>.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=CotacaoDataExportRecordMapperTest,CotacaoStageValueObjectsTest' test</code> executou 20 testes e falhou em dois pontos: overflow acima de <code>Long.MAX_VALUE</code> não era quarentenado e o value object o aceitava. O V039 original produziu <code>updated_rows=0/noop_rows=1/stale_noop_rows=1</code> para <code>pending@T2 → done@T1</code> e lançou 51876. O V041 original rejeitou <code>INTEGER:2147483648</code> com 51900 por narrowing <code>INT</code>, falhou 51914 ao aplicar <code>ABSENT</code> sobre UFs não efetivas e aceitou moeda de origem; os validators reforçados falharam antes das respectivas correções. A primeira integração também expôs o falso bloqueio de <code>ref.coleta_sequence_code_alias</code> no validator 030; foi aplicada uma allowlist exata somente para essa extensão V010. Uma iteração da matriz SQL ainda aceitou whitespace final pela semântica de igualdade do SQL Server e levou à exigência adicional de <code>DATALENGTH</code>. Duas falhas de invocação foram separadas dos defeitos: o primeiro Maven sob o Java 25 global parou no Enforcer antes dos testes e foi repetido com <code>JAVA_HOME</code> 17; uma chamada do V041 a partir da raiz não resolveu o <code>:r ..\transition</code>, encerrou a conexão sem materializar a baseline e foi repetida do diretório <code>database/validation</code>.
    - **Verdes registrados:** os 27 testes focados finais de mapper/value object/JDBC passaram; a quarentena liga <code>source_key</code>, payload e presença como <code>NULL</code> antes do bind. <code>Test-ColetasV2010ShadowVertical.ps1 -RunSqlExercise</code> capturou os rowsets 1003=<code>1|0|1|0|0|0</code>, 1004=<code>1|0|0|0|1|0</code> e 1007=<code>1|0|0|0|1|1</code>. O V041 aceitou 2147483648/9223372036854775807, rejeitou fisicamente zero, negativo, overflow, sinal, zero à esquerda, whitespace, decimal e dígito não ASCII; comprovou <code>ABSENT</code>, <code>NULL</code>, zero, replay, stale, tarifa A→B, update com UFs nulas e insert no tenant B com UFs ausentes sem empréstimo do tenant A. As duas falhas tarifárias esperadas foram 51914 com <code>XACT_STATE=-1</code>, sem reconciliação parcial, seguidas de rollback. Concorrência de Cotações em duas sessões e o gate progressivo final passaram; o pós-gate confirmou zero objetos V010/V011 e zero histórico 010/011.
    - **Gate final e limites:** o único <code>clean verify</code> offline final, em Temurin 17.0.20.1, passou com 656 testes, zero falhas, zero erros, um skip esperado de symlink no Windows e cobertura atendida. Os validadores de decisão/vertical/identidade/schema/gate/caracterização passaram; o self-test do scanner passou nove casos e o scanner offline examinou 863 candidatos, 862 textos e um binário verificado, sem finding, oversized ou conteúdo não inspecionado. A prova é exclusivamente sintética/local: V004 pode continuar registrando stale técnico enquanto V010 aplica o override tipado COL-03; não há alegação de caracterização, completude, paridade, tarifa/release operacional, publicação ou produção.
  - **Aceite:** cada subgate bloqueia somente a partir do ponto em que seus pré-requisitos existem; relatórios têm retenção e procedimento local equivalente.

### Fase 1 — Fundação executável e de dados

- [x] **V2-018 — Implementar configuração tipada, bootstrap e composition root.**
  - **Integração funcional local em14/09/2026 — V2-018:** Main → LocalDataLaboratoryMain/LocalProfileCharacterizationMain/LocalRasterArtifactMain/LocalArtifactScenarioMain/LocalCollectionSweepMain; manifests fechados e duas travas. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Configuração nominal de fonte/autoridade com identidade e escopo ratificados; nenhum segredo no JSON de dados. Artefato administrado, ambiente e alvo que o RuntimeTargetPreflight aceita explicitamente. TLS/principals/configuração externa operacional, não presumidos por dry-run. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-016a e V2-017. V2-041 bloqueia somente prova com segredo/rede e release, não a implementação/teste sintético desta tarefa.
  - **Entrega:** configuração imutável por ambiente/fonte, precedência única, secret provider, validação fail-fast, timezone/Clock injetados, alvo allowlisted, factories tipadas e composition root único.
  - **Aceite:** nenhum default produtivo, token/senha/segredo por <code>-D</code> ou dependência do <code>.env</code> irmão; teste recusa qualquer propriedade classificada como secreta no canal de system properties/CLI; preflight recusa alvo legado/produtivo não autorizado; <code>config validate</code> e <code>dry-run</code> não causam efeitos.
  - **Estado/evidência em 30/08/2026:** <code>COMPLETO_LOCAL</code>. <code>RuntimeConfigurationFactory</code> lê no máximo 64 KiB de um único arquivo <code>.properties</code> explícito, rejeita chaves duplicadas/desconhecidas/dormentes e aplica precedência única de variável <code>V2_*</code> não vazia sobre arquivo. <code>.env</code>, <code>-D</code>, opção CLI ou aliases de segredo falham antes da composição. Timeout, tentativas, atraso e tamanho de resposta possuem tetos bloqueantes; a fonte exige <code>GET_WITH_QUERY</code>. <code>EnvironmentSecretProvider</code> resolve token somente no uso operacional, nunca em <code>config validate</code>/<code>dry-run</code>. <code>RuntimeCompositionRoot</code> realiza preflight sem cliente HTTP, conexão, DDL, DML ou carga; shadow aprovado exige loopback, banco exato, criptografia e certificado não confiado recusado. Banco legado/produtivo, alvo não autorizado e fallback de transporte são recusados, e erro de CLI não expõe caminho/valor de configuração.

- [x] **V2-019 — Criar fundação de schema e baseline Flyway limpo.**
  - **Integração funcional local em14/09/2026 — V2-019:** Migrations/baseline e gates locais de schema; equivalência física fresh/upgrade atual não alegada. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Nenhuma regra de schema nova demonstrada para os consumidores deste bloco; V001–V098 atendem à integração local. Banco novo descartável e banco de upgrade explicitamente autorizados para DDL. Fresh/upgrade da mesma revisão e comparação de catálogo, sem usar rollback de DML como prova. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-016a e V2-017.
  - **Entrega:** schemas <code>ctl/stg/core/ref/mart/pub/recon</code>; absorver o <code>shadow</code> vazio; roles migrator/runtime; mecanismo de manifesto/fingerprint; migrations novas, sem copiar 001–059.
  - **Aceite:** banco vazio e baseline aprovado geram a mesma fundação, grants mínimos e validator a partir do manifesto. Objetos de cada vertical só entram depois do contrato/identidade correspondente; fingerprint completo é fechado em V2-037/V2-038/V2-040.
  - **Estado/evidência em 31/08/2026:** <code>COMPLETO_LOCAL</code>. V001/V002 mantêm a fundação de sete schemas sem objeto de vertical; V003–V006 acrescentam somente control plane, kernel, lifecycle e observabilidade/DQ comuns. O histórico fica em <code>ctl.flyway_schema_history</code> e não participa da comparação estrutural. O principal <code>v2_schema_owner</code>, sem login, detém os schemas mutáveis; <code>v2_migrator</code> não recebe <code>dbo</code>, ownership ou impersonação e publica apenas grants de procedures V2 por helper <code>dbo</code> com <code>EXECUTE AS OWNER</code> e allowlist exata atual de 24 triplets. <code>public</code> e roles dedicadas recebem denies de tabela/helper/impersonação; os gates aplicaram a baseline atual como migrator, recusaram grant fora da allowlist e reverteram tudo. Cinco manifests SHA-256, baseline SQLCMD, validators e checkers impedem drift estático. A execução local recompôs e validou V001–V006 dentro de rollback, sem persistir <code>flyway_schema_history</code> nem qualquer objeto/dado sintético.

- [x] **V2-020 — Generalizar control plane, auditoria, lock, replay e watermarks.**
  - **Integração funcional local em14/09/2026 — V2-020:** LocalExpansionRuntime/LocalRasterRuntime/LocalArtifactScenario → control plane/auditoria SQL; leases e rollback do mesmo caso de uso. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Política de recuperação operacional, janelas e critérios pós-COMMIT do control plane existente. Alvo material, backups e permissões de recuperação. Crash após COMMIT/ack perdido e restore de domínio com readback durável; journal local não substitui. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-018 e V2-019.
  - **Entrega:** estados <code>PLANNED…PUBLISHED</code> e terminais <code>BLOCKED/SKIPPED/NOT_APPLICABLE/FAILED/CANCELLED/DEGRADED</code>, ciclo/entidade/janela/página, catálogo de fontes, contagens/equações, publication pointer, lease/heartbeat, stale recovery, replay/idempotency e ledger de partições concluídas. A chave semântica única é <code>(environment, source_instance, tenant_scope, entity, mode, partition_start, partition_end)</code>; <code>execution_id</code> e tentativa são ocorrências separadas. Estratégia de janela, versão/fingerprint do contrato e versão/fingerprint da configuração são atributos imutáveis da ocorrência, não partes omitidas ou variantes concorrentes da chave.
  - **Aceite:** lease, replay, ledger, idempotência e publication pointer usam a mesma chave semântica; transições são válidas e imutáveis; segundo start/regravação conflitante falha; conclusão fora de ordem fica registrada, mas o watermark operacional avança somente pela fronteira incremental contígua publicada e em transação/protocolo recuperável; nenhum outro modo o eleva. Causa SQL é preservada e sanitizada; control plane controla estado/roteamento, enquanto registros rejeitados pertencem ao staging/quarantine.
  - [x] **V2-020a — Fechar a fundação offline/fail-closed do control plane.** Chave semântica e plano/fingerprints imutáveis; identidade textual exata sob <code>Latin1_General_100_BIN2</code>, comprimento bruto validado antes de normalização, trim limitado a U+0020 e reason codes canônicos sem case-fold; equações canônicas; fencing transacional e relógio SQL em registro, planejamento, start, heartbeat, página, contagens e transições; retries exatos de página/contagem/transição reconhecidos antes do fence sem aceitar divergência; fase interna <code>STAGING_KERNEL</code> reservada; replay sem autorreferência/cadeia e restrito ao mesmo namespace/janela; números de tentativa/transição atômicos, sem <code>MAX+1</code>; transições protegidas; least privilege; publicação desabilitada para o runtime enquanto o protocolo positivo não existir.
  - [x] **V2-020b — Implementar o protocolo positivo atômico de reconciliação/publicação/watermark e recovery final.** Vincular evidência reconciliada ao candidate set/aplicação, confirmar <code>RECONCILED/PUBLISHED</code> sem transição genérica, aplicar publication pointer e fronteira incremental contígua na mesma unidade recuperável e tornar recovery integralmente autoritativo pelo relógio SQL; provar concorrência/rollback em SQL Server local.
  - **Estado/evidência em 30/08/2026:** <code>COMPLETO_LOCAL</code>. V003 adiciona ownership composto de execução/partição, evento imutável de publicação e recovery runtime sem timestamp fornecido pelo caller, amostrando <code>SYSUTCDATETIME()</code> dentro da transação e devolvendo uma única contagem. V004 fornece o único entry point positivo <code>core.usp_apply_reconcile_publish_execution</code>: lock transacional exclusivo por namespace semântico, evidência reconciliada, transições dedicadas <code>PROMOTED→RECONCILED→PUBLISHED</code>, publication pointer, evento, liberação de lease e frontier incremental contígua na mesma transação; o último write revalida a lease pelo relógio do banco. Retry de publicação lê somente evidência imutável e continua correto após pointer/core posteriores; modos não incrementais nunca tocam o watermark. O runtime só recebe <code>EXECUTE</code> nas procedures donas; DML direto permanece negado. O gate local provou recovery 1→0, retry, lacuna/transitividade, concorrência em duas sessões e rollback integral.

- [x] **V2-021 — Criar kernel de staging e promoção set-based.**
  - **Integração funcional local em14/09/2026 — V2-021:** ExpansionCaptureSource → DataExportPageStreamer → batches/JDBC existentes → prepare/apply/materialização; nenhum staging alternativo. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Contrato de publicação e recovery de domínio já tipado; falta aceitação material, não outro staging. Alvo material com DDL instalado e autoridade de publicação. Durabilidade após COMMIT/crash e recuperação do recibo/publicação real. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-019 e V2-020.
  - **Entrega:** staging isolado por execução, quarantine, dedupe, presença, hash/no-op, freshness, reativação e protocolo set-based; contrato de transação entre promoção, auditoria e checkpoint; sink bulk síncrono com backpressure, flush/liberação e no máximo uma página/lote em voo.
  - **Aceite:** reprocessamento idempotente, rollback/retomada integral, equações reconciliadas; comprimento/Unicode validados sem <code>substring</code> silencioso nem valor em log; nenhum JDBC por evento, reflection genérica, DDL runtime, <code>MAX+1</code> ou merge linha a linha. Memória é <code>O(maxResponseBytes + batchBytes)</code>; fixture coloca o mesmo source key em páginas diferentes e prova dedupe/frescor determinístico por <code>ROW_NUMBER</code> sobre todo staging da execução/partição no SQL.
  - [x] **V2-021a — Fechar staging/quarantine/candidate set fail-closed offline.** Staging isolado e idempotente, retries exatos de linha/promoção reconhecidos sem reabrir escrita, ordinais únicos entre 1 e 10.000 por lote, cópia manual bounded que não confia em tamanho declarado, chaves técnicas binárias/exatas sem truncamento, dedupe determinístico sobre a execução inteira, conflitos de frescor em quarantine, fingerprints versionados, candidate set imutável, fencing por lease/execução atual, contagens reconciliadas que separam raízes de artefatos físicos e sink JDBC transacional.
  - [x] **V2-021b — Aplicar candidate set ao core no protocolo atômico final.** A aplicação insert/update/no-op/reativação, reconciliação, auditoria e checkpoint/publicação devem compartilhar a unidade transacional/recuperável de V2-020b e receber prova dinâmica de rollback/replay/concorrência no SQL Server local.
  - **Estado/evidência em 30/08/2026:** <code>COMPLETO_LOCAL</code>. <code>core.usp_prepare_staged_execution</code> fecha o candidate set/quarantine e <code>core.usp_apply_reconcile_publish_execution</code> o aplica ao estado técnico de <code>core</code> sem <code>MERGE</code> nem DML por registro. O plano set-based classifica <code>INSERTED</code>, <code>UPDATED</code>, <code>REACTIVATED</code>, <code>NO_OP</code> e <code>STALE_NO_OP</code>; conteúdo divergente com frescor igual/desconhecido e qualquer quarantine falham fechados. Evidência por candidato e agregado reconciliam toda a entrada, e o result set JDBC possui exatamente uma linha/11 campos e memória O(1). Testes sintéticos provaram as cinco disposições, rollback por savepoint, replay exato após alteração posterior do core, publicação/frontier e ausência de watermark em BACKFILL. Identidade, payload e grão de cada vertical continuam corretamente fora do kernel comum.

- [x] **V2-042 — Implementar identidade de serviço e autorização sem bypass.**
  - **Integração funcional local em14/09/2026 — V2-042:** Boundary de autorização e adapter Windows/SQL já provados no B55; governança produtiva não herdada. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Principal de serviço, grupos/papéis, policy/fingerprint/escopos e circuito de revogação administrados. Windows/SQL authority artifact, TLS, login/grants mínimos e alvos reais. Identidade administrada e revogação efetiva no ambiente de release. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-017 e V2-018.
  - **Entrega:** threat model; provedor/mecanismo de identidade escolhido; autoridade que atribui papéis; mapeamento de service principals/operadores; RBAC runtime para <code>run/replay/sweep/force-run/status</code>; validação no boundary real. <code>migrate</code> e <code>cutover</code> pertencem a principals/processos de deploy/governança separados, não ao JAR runtime.
  - **Aceite:** executar o JAR diretamente não contorna autorização; credencial bloqueada/expirada é recusada; runtime não tem DDL/cutover; testes positivos/negativos e auditoria sem segredo.
  - **Fechamento B55 em 08/09/2026:** mecanismo Windows/SQL adotado, JAR protegido, matriz B54 de 39 casos de identidade e negativas oficiais preservadas, regressão das cinco verticais e fences diretos B55 comprovados. Perfil exato 32/16, sem renovação; SQL não admite candidate set autoafirmado nem DML/DDL do runtime. Matriz por critério e hashes no runbook/manifest B55. Este aceite não ratifica governança produtiva nem fonte real.
  - [x] **V2-042a — Fechar threat model e boundary provider-neutral offline.** Boundary único no composition root oficial, verifier padrão deny-all, RBAC explícito para <code>run/replay/sweep/force-run/status</code>, capabilities com validade temporal e policy fingerprint, recusa de papéis/ações de deploy e evento de auditoria sanitizado sem credencial/claims/scope.
  - [x] **V2-042b — Registrar inputs externos de identidade e governança.** Exige provedor/mecanismo, authority/audiences, principals, autoridade de papéis, mapeamentos/revogação, principals SQL, sink durável e estratégia aprovada de pseudonimização; nenhum deles foi inventado.
  - [x] **V2-042c — Integrar e provar o adapter positivo no runtime real.** Implementar verifier, comparar fingerprint do mapeamento, sanitizar scopes, consumir capabilities uma única vez/antes do vencimento no dispatcher/handlers futuros e provar testes positivos/negativos pelo JAR oficial; depende de V2-042b.
  - **Estado/evidência em 30/08/2026:** <code>PARCIAL</code>, com V2-042a concluída offline. ADR 0010 e o threat model delimitam ativos, atores, trust boundaries, ameaças e controles. O pacote <code>plataforma.autorizacao</code> implementa identidade verificada abstrata, política RBAC, boundary, decisão/capability e contrato de auditoria; <code>RuntimeCompositionRoot</code> expõe o único entrypoint operacional atual, usa verifier deny-all sem adapter e sink indisponível fail-closed. Testes sintéticos cobrem o mapeamento provider-neutral dos motivos ausente/inválido/bloqueado/revogado, RBAC positivo/negativo, relógio reamostrado, avanço/regressão temporal, identidade futura/expirada, fingerprints e sanitização; não provam credencial/provider real nem sink durável. Isso prova somente o caminho oficial presente; não reivindica proteção contra reflection/classpath arbitrário nem satisfaz a execução direta do JAR enquanto não existirem adapter, dispatcher/handlers e inputs externos.

- [x] **V2-043 — Implementar resiliência e política de falha compartilhadas.**
  - **Integração funcional local em14/09/2026 — V2-043:** ExecutionDeadlines/CancellationToken reutilizados; savepoint Raster verifica cancelamento após lote e antes de seal/apply; três barreiras reais do supervisor. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Quotas/retry/backoff/deadline/SLO ratificados por fonte, especialmente semântica remota dos erros. Ambiente de carga representativa e limites operacionais aprovados. Latência, erro e cancelamento sob carga real com budgets ratificados. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-018 e plataforma local V2-006.
  - **Entrega:** limitador ESL global conservador, <code>Retry-After</code> com teto, backoff/jitter, circuit breaker, budgets por origem/vertical, timeout/cancelamento por request/passo/ciclo e matriz <code>ABORT/RETRY/REPARTITION/DEGRADE/SKIP/CONTINUE_WITH_ALERT</code>. 422 só vira <code>REPARTITION</code> por categoria comprovada, com menor partição e tentativas limitadas.
  - **Aceite:** teste concorrente multi-template valida a proteção escolhida sem tempestade de retry; nenhum thread/stream/conexão vaza; a entidade parcial não avança seu checkpoint, sem desfazer independente já publicado; dependentes e exit code seguem a matriz.
  - **Estado/evidência em 30/08/2026:** <code>COMPLETO_LOCAL</code>. O pacote <code>plataforma.resiliencia</code> implementa o governor a ser instanciado uma vez por origem, com uma requisição em voo, intervalo/embargo global, budgets atômicos por origem/workload, deadlines monotônicos e cancelamento cooperativo; a matriz tipada fecha ações, estados, dependências, checkpoint e categorias de saída sem classificar texto de exceção. A fábrica Data Export source-scoped compartilha cliente e circuitos por template e adapta o ciclo/workload recebido do governor para o mesmo bundle <code>/info</code>/<code>/data</code>; V2-022 deve compor o singleton da origem. O transporte assíncrono limita bytes/demanda e cancela conexão pendente. Retry idempotente tem tentativas, backoff/jitter e <code>Retry-After</code> sob teto; 422 só reparticiona por categoria estruturada <code>WINDOW_TOO_LARGE</code>, com unidade e budget explícitos. Testes sintéticos/loopback provaram um único governor entre workloads sem retry storm, embargo terminal, isolamento/half-open do circuito, budgets, timeouts, cancelamento com liberação de conexão/permit, repartição/refusa e preservação de publicação independente sem checkpoint parcial. ADR 0011 registra a decisão; tradutores de timezone/precisão/bordas e composição do orquestrador permanecem corretamente em blocos posteriores.

- [x] **V2-044 — Detectar drift de contrato da fonte antes da promoção.**
  - **Integração funcional local em14/09/2026 — V2-044:** ExpansionArtifact/LocalProfileArtifact/RasterArtifact/LocalArtifactScenario validam contrato/revisão/bytes e vínculo antes do efeito. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Release /info autenticado, transporte/paginação/filtros/wire type e critérios de drift ratificados por fonte. Canal autorizado, fonte e contrato nominal de referência. Atestação de fornecedor e drift real; pin local não autentica /info. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-018 e V2-020.
  - **Entrega:** dois fingerprints sanitizados e versionados por template/documento: metadata de campos/filtros de <code>/info</code> ou query GraphQL aprovada; e resposta observada de <code>/data</code>/GraphQL com envelope, caminhos, tipos JSON, cardinalidade, optionalidade/nullability e presença da chave, sempre sem valores. Canonicalização, validação estrita em runtime, diff classificado e allowlist de mudanças compatíveis são obrigatórios; <code>/info</code> isolado não prova tipo nem identidade. Introspection GraphQL ampla exige autorização própria.
  - **Aceite:** campo obrigatório ausente, tipo/cardinalidade/raiz/filtro/chave alterados bloqueiam promoção; campo opcional aditivo ou nulo previsto gera alerta/aceite conforme política; ambos os fingerprints e as versões de contrato/configuração entram na execução/release sem payload real.
  - **Estado/evidência em 31/08/2026:** <code>COMPLETO_LOCAL</code>. O pacote <code>plataforma.contrato</code> fornece releases com fingerprints independentes de metadata/resposta, canonicalização binária UTF-8 estrita, query GraphQL read-only aprovada, profiler limitado, boundary runtime deny-by-default, opacidade explícita/versionada de mapas, diff classificado e allowlist exata com descriptor estrutural. Amostras por página só podem ser projetadas para subconjuntos do shape aprovado; tipo, cardinalidade, path, escopo ou nullability fora dele falham fechados. O Data Export faz preflight de tokens antes da árvore, observa <code>/info</code> e cada página, exige sequência desde 1, resposta populada, terminal vazia e auditoria concluída; o fingerprint da <code>DataExportSourceConfiguration</code> efetiva, sem token, atravessa factory/bundle/gate e é comparado antes de qualquer I/O. <code>ContractPromotionPermit</code> é obrigatório nas duas procedures de promoção, que comparam contrato/configuração persistidos sob lock antes de retry ou mutação. Testes sintéticos cobrem drift de raiz/chave/tipo/cardinalidade/presença/filtro, aditivo/nulo previsto com alerta, Unicode inválido, limites, paginação/cancelamento, GraphQL provider-neutral e mismatch de configuração sem delegate. O gate SQL local exercitou mismatch em <code>STAGED</code>, <code>PROMOTED</code> e retry <code>PUBLISHED</code>, com erro sanitizado 51418 e rollback integral. Nenhuma sonda, credencial ou payload real foi usado.

- [ ] **V2-045 — Implementar retenção, arquivamento e purge governado de staging.**
  - **Integração funcional local em14/09/2026 — V2-045:** Lifecycle local com legal hold, dry-run, archive/restore limitado; TTL e armazenamento produtivos pendentes. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** V2-045a aceita. Para 045b: TTL por classe, legal hold/revogação, ratificação distinta Data Owner/Compliance, RTO/RPO e descarte. Storage/cold-WORM e ACL/cifragem/backups, chaves administradas, destino e scheduler específicos. Arquivo/restauração material íntegros no storage escolhido, controle de acesso e retenção/hold reais. Nenhum checkbox ou numerador alterado.
  - **Evidência local de campanha em13/09/2026: TESTADO_NA_CAMADA_LOCAL.** Journal/recibos/logs e tentativas falhas preservados em diretórios próprios;controle imutável recusa arquivo externo/truncado e fonte de outra revisão. Nenhum purge/limpeza,TTL nominal ou restore foi realizado;V2-045b continua aberto. Sem incremento de construção por retenção documental. Checkpoint0141.
  - **Depende de:** V2-019, V2-020 e matriz de dados de V2-017.
  - **Entrega:** TTL ratificado por data owner/compliance; purge somente de staging técnico; archive/partition move read-only para quarantine, auditoria, recon e evidências; lifecycle de logs na plataforma; legal hold, dry-run, métricas e restore.
  - **Aceite:** nenhum hard delete da aplicação em domínio/fato/histórico/auditoria/quarantine/evidência; staging só após terminal; least privilege, lock, teste de fronteira e trilha imutável.
  - **Estado agregado em 31/08/2026:** <code>PARCIAL</code>. Toda a fundação executável local está concluída em V2-045a; V2-045b permanece exclusivamente externa. O checkbox pai fica aberto até os controles físicos/produtivos serem ratificados e provados.
  - [x] **V2-045a — Fechar lifecycle local, bounded e fail-closed.** Migration V005, manifesto v2, ADR 0013 e runbook implementam policy/ratificação sem seed ou TTL default, legal hold com ledger íntegro, seleção terminal por trilha completa e limitada, plano/dry-run com caps explícitos, archive tipado/verificado das dez fontes de staging/evidência, atestação SHA-256 por linha, raiz de conteúdo/manifesto v3, purge somente de <code>stg.execution_candidate</code>/<code>stg.execution_record</code> e restore limitado em <code>recon</code>. Dedupe, reconciliação e lifecycle permanecem set-based no SQL Server. Quarantine, auditoria, recon, histórico, <code>core</code>, <code>mart</code> e <code>pub</code> nunca são hard-deleted por esse fluxo. Locks, relógio SQL, replay exato, recuperação, read-back e limites de 1..7 eventos terminais/ledger de hold até 4.096 linhas falham fechados; fingerprints Unicode são medidos antes da gramática ASCII/BIN2 e evidências de data owner/compliance devem ser distintas. Roles separadas governam policy, revisão, operação e restore; <code>v2_runtime</code> não recebe entrypoint de lifecycle. O comando manual PowerShell 7 de logs é opt-in, streaming e limitado por rows/bytes/files; fences, permit/completion receipts, marker e tombstone dão retomada segura. Policy disabled/legal hold revalida a operação apontada pelo marker atual e artefatos órfãos do escopo sem criar controle em origem nova. O marker é ponteiro mutável: integridade contínua de operações anteriores não é alegada pelo controle local. O gate completo V001–V005, exercícios 013–020, concorrência, SHOWPLAN, fixtures de logs, Maven e scanners passaram; todas as mudanças SQL foram revertidas no alvo local.
  - [ ] **V2-045b — Ratificar e provar controles físicos/produtivos.** <code>EXTERNAL_HOLD</code>; depende de V2-045a e de evidência externa nova. Data owner e compliance owner precisam ratificar policy/TTL produtivos de staging, logs e archive; DBA/Operações precisam provar cold storage/WORM ou imutabilidade equivalente, criptografia em repouso, capacidade, backup + restore drill, RTO/RPO e ACL/identidade de serviço produtivas. Os candidatos de 7/30 dias não foram semeados nem ativados; nenhum job, schedule, diretório de serviço, policy real, purge produtivo ou deleção Logback foi habilitado. Desbloqueia somente com esses artefatos sanitizados e autorizações dos owner-papéis, sem inventar responsável nominal.

- [ ] **V2-022 — Criar runtime, orquestrador e comandos operacionais mínimos.**
  - **Integração funcional local em14/09/2026 — V2-022:** Main local-* e ARTIFACT → LocalArtifactScenario → AnalyticScenarioRuntime com AnalyticExpansionSources/AnalyticRasterSources injetados. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Tradução nominal de wire/release/bindings para expansões/Raster e matriz real de workload com consumidores. Authority artifact, fonte/alvo e scheduler administrados. E2E operacional/recovery com identidade e COMMIT reais. Nenhum checkbox ou numerador alterado.
  - **Agenda de qualificação em13/09/2026: TESTADO_NA_CAMADA_PACOTE_E_JDBC.** Cinco políticas atuais consumidas por planner/coordinator/capturas/dispatcher,quatro modos/lookback/late/correção/DST23h/gaps/frontier e procedures de fato efetivas. TEMPORAL/ADMISSION do pacote final e verify03 passaram;blackout/prazo/not-due sem filhos. Captura DEGRADED preservada,sem avanço indevido de fonte. Não cria scheduler nem fecha V2-022 operacional. Checkpoint0141.
  - **Dependências agregadas:** V2-018, V2-020, V2-021, V2-042 e V2-043. Para impedir que inputs externos de identidade bloqueiem implementação e testes sintéticos offline, a entrega fica dividida nos subgates abaixo.
  - [x] **V2-022a — Implementar o runtime/orquestrador offline e deny-all.** Depende de V2-018, V2-020, V2-021, V2-042a e V2-043. Inclui planejador determinístico, plano persistido, registry/DAG, CLI/handlers, lease/heartbeat, recovery, cancelamento, comandos e exit codes exercitados com identidade sintética ou verifier deny-all, sem credencial, rede, schedule, deploy ou autorização positiva. Este subgate basta para implementar e testar verticais em sombra; não autoriza execução operacional.
    - **Fechamento em 04/09/2026 16:24 -03:00:** o pacote <code>plataforma.orquestracao</code> cria valores tipados para workload, modo, estratégia, partição, plano, registry/DAG, persistência de ocorrências <code>PLANNED</code> e controle de heartbeat/cancelamento. Dependência ausente, duplicidade, auto-dependência e ciclo falham fechados; a ordem topológica é determinística e a dependência Coletas→Fretes é expressável sem inferir vertical. A persistência sintética recupera stale, registra a fonte lógica uma vez, abre ciclo e registra somente <code>PLANNED</code>; ela não possui dispatcher, fonte, mapper, JDBC composto pela CLI, DDL, promoção ou execução de domínio. O JAR ganhou <code>plan</code> local e handlers <code>run/replay/sweep-preview/sweep-apply/force-run/status</code> que atravessam o deny-all de V2-042a e retornam <code>CONFIG_AUTH=20</code> antes de qualquer despacho. O ADR 0022 documenta o limite; nove testes de orquestração e 14 do entry point passaram sob Temurin 17.0.20.1+1, junto de <code>clean verify</code> com 552 testes, Spotless, Checkstyle, arquitetura e JaCoCo. Não houve credencial, rede, banco, schedule, deploy ou autorização positiva. V2-022b continua necessário para adapter positivo, capability consumível, auditoria durável e operação.
  - [x] **V2-022/TRAVESSIA_STAGING_LOCAL — Integrar travessia, mapper e staging de Coletas/Fretes.** Entrega anterior reconhecida como subcheckbox próprio, rota P02I, ADR 0032; 25 testes novos e baseline de 974 testes comprovados abaixo. Não recebe outro número de bloco.
  - [x] **V2-022/INTEGRACAO_LOCAL_COLETAS_FRETES — Compor dispatcher, auditoria, candidate set, DQ e promoção.** Bloco 51 / P02M, <code>LOCAL_INTEGRATION_COMPLETE_OPERATIONAL_GATES_PENDING</code>. Depende dos contratos locais V2-022a, V2-020/021/023, V2-042a, V2-043/044 e bases V2-010/011. Aceite: dispatcher serial pelo DAG, dependência liberada somente por recibo da mesma ocorrência/janela/namespace, verticais e adapters JDBC reais com fonte/protocolo sintéticos, batches de 100, cancelamento/heartbeat cooperativos, replay com origem, falhas fechadas e recuperação idempotente de prepare/apply com os mesmos permits vivos.
    - **Evidência local de 07/09/2026:** 76 testes focados passaram, incluindo 27 cenários novos de integração. Suíte completa: 1001 testes, zero falhas/erros e quatro skips esperados; fechamento dos demais gates e limites no [runbook do motor](docs/runbooks/v2-022-motor-local-coletas-fretes.md). ADR 0033 registra RUN-01–06, decisões e rollback. Sem migration, fonte externa, credencial, SQL físico, deploy, alteração da CLI ou autorização positiva.
    - **Limite explícito:** recibo tipado confirma somente o gateway exercitado. O simulador JDBC não prova transação, dedupe ou concorrência física do SQL Server. Perda de lease/ack de start ou transição exige <code>RECOVERY_REQUIRED</code>; reconstituição após perda da JVM ainda requer contrato de leitura durável. V2-022/V2-022b, V2-041 e V2-042b/c continuam abertas. A frase histórica de start somente PLANNED é corrigida pelo V003: PLANNED e EXTRACTING são registrados atomicamente no start.
  - [x] **V2-022/RECUPERACAO_DURAVEL_LOCAL — Leitura, evidência e retomada após perda da JVM, pacote integrado A+B+C+D local.** Bloco 52/P02R comprovado por 49 testes focados e clean verify de 1023 testes (zero falhas/erros, quatro skips), incluindo oito processos Java filhos próprios e persistência sintética independente. Selo nasce após guard/auditoria; recuperação confirma recibo ou continua com revalidação, sem reextração, permits fabricados ou efeitos duplicados. V015/048/049 somente preparados; SQL físico, CLI positiva, identidade e operação continuam fora deste aceite. Ver fechamento e runbook P02R.
  - [x] **V2-022/QUALIFICACAO_FISICA_LOCAL — P02Q, Bloco 53 integrado.** Qualificação física local completa das duas verticais, commits limitados, recuperação entre JVMs, matriz adversarial e regressão; evidências no runbook integrado. Gates de identidade restrita/JAR positivo/fonte/operação permanecem abertos.
  - [x] **V2-022/INTEGRACAO_LOCAL_CINCO_VERTICAIS — Integrar Manifestos, Cotações e Localização ao runtime e concluir os consumidores locais A–J.** Único subaceite B55: JAR protegido, segurança/SQL, recuperação, BACKFILL temporal, comparação independente, medição, lote manual e preservação comprovados. Provas e limites no runbook B55; não reabre V2-026/027/028 nem fecha V2-022 pai.
  - [x] **V2-022b — Integrar o dispatcher operacional ao adapter positivo de autorização.** Depende de V2-022a, V2-042b e V2-042c. Liga capabilities reais aos handlers, comprova consumo único/validade, auditoria durável e testes pelo JAR oficial. V2-038, V2-039 e qualquer operação fora do harness sintético dependem deste subgate.
    - **Fotografia histórica da ingestão em 07/09/2026, antes do Bloco 51:** o ADR 0032 delimita a ligação travessia → mapper → staging para Coletas/Fretes. `DataExportStagingPipeline`, `DataExportBatchStaging`, `ExtrairColetasDataExport` e `ExtrairFretesDataExport` reutilizam o streamer e os tipos existentes, dividem páginas expandidas em lotes físicos de até 100, preservam ordinais/batches por ocorrência e invalidam o contrato diante de falha ou cancelamento. `DataExportPageResponse.forEachRecord` entrega cópia defensiva por linha. A fatia de ingestão está validada localmente; dispatcher, recuperação durável, promoção, publicação e os gates externos permanecem pendentes. Naquela entrega ainda não havia subcheckbox; P02I acima agora reconhece somente seu aceite local, e P02M acrescenta a integração posterior.
    - **Evidência executada:** 25 testes novos em `DataExportStagingPipelineTest` cobrem os dois mappers/batches reais, 251 linhas físicas em lotes 100/100/51, continuação para outra página, fronteira exata de batch, página vazia, falha no segundo lote, cancelamento antes/durante/depois do mapper/staging/auditoria, budget, template/ordenação inválidos, outro guard da mesma fonte, terminal sem observação, novo início com outra ocorrência e recusa de reuso do guard. A rodada focada intermediária passou 62 testes; a versão final passou `clean verify` em JDK 17.0.20.1 com 974 testes, zero falhas/erros, quatro skips esperados e todos os gates de estilo/cobertura verdes. O POM temporário mudou somente o diretório de build para `target/ingestion-validation-20260907`, equivalência XML verificada; foi removido após o build. O primeiro clean padrão falhou na remoção de `target`, que recebeu `test-classes` durante a limpeza; não houve alteração/parada de indexador, Java global ou processo do usuário. Relatórios Surefire/JaCoCo e JAR de teste permanecem no diretório isolado ignorado pelo Git. Trilhas e validators estáticos de Coletas/Fretes passaram; scanner offline passou em 1.047 candidatos/1.046 textos/um binário verificado, zero findings; UTF-8 estrito e diff check passaram. Não houve fonte/rede externa, credencial, banco/JDBC real, DDL/DML, migration, mudança no `Main`/composition root, relação, publicação, deploy, commit ou push. Os testes locais de ingestão não provam completude, idempotência física/recovery ou desempenho do fornecedor.
  - **Entrega:** CLI one-shot; planejador determinístico e plano persistido, separando modo semântico <code>incremental/bootstrap/backfill/replay/sweep</code> de estratégia de janela <code>full/interval/microbatch</code>; filtros seguros por fonte/entidade/janela; registry/DAG; recovery, reconcile/sweep dry-run, validate, materialize, status e force-run; uso da chave semântica de V2-020; lease/heartbeat; shutdown. O plano inclui matriz por workload com timezone, cadência, estabilização/lookback, SLA/deadline, dependências, concorrência máxima, blackout, fechamento mensal e política de catch-up de execução perdida; horários do legado são evidência, não defaults.
  - **Aceite:** taxonomia estável de exit codes (<code>success/degraded/config-auth/lock/source-DQ/cancelled</code>); retomada do plano sem misturar namespaces nem pular partição incremental; stale <code>RUNNING</code>; conclusão fora de ordem segura; limite de reconciliações/ciclos degradados; fechamento mensal idempotente do mês civil anterior por <code>Clock</code>/timezone, com período auditado; catch-up não duplica nem salta janela; Coletas falha/bloqueia Fretes.

- [x] **V2-023 — Implementar framework de observabilidade, integridade e Data Quality fail-closed.**
  - **Integração funcional local em14/09/2026 — V2-023:** Framework de DQ, reconciliação e observabilidade com falha fechada. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Policies de DQ/TTL, thresholds, destino/owner de alertas e SLO operacionais. Canal de alertas e monitoramento autenticado, configuração ratificada. Entrega real dos alertas/health e thresholds sob carga representativa. Nenhum checkbox ou numerador alterado.
  - **Gates da campanha em13/09/2026: TESTADO_NA_CAMADA_PACOTE_E_JDBC.**35escopos/19saídas;dependência recusada não é aprovada pelo ramo independente. Métricas11entradas,equações SQL,recibos atômicos/nonce/PID e diferenças tipadas limitadas consumidos pelas17campanhas finais. Erro/truncamento/mutação não viramPASS;sem contagem duplicada da unidade. Checkpoint0141.
  - **Depende de:** V2-020, V2-021 e V2-045a. V2-045b continua obrigatória para ativação produtiva, mas não bloqueia a implementação/teste offline deste framework.
  - **Entrega:** logs estruturados/redaction/caps, métricas, health, alertas, equações de contagem e engine de checks/thresholds; checks de domínio entram em cada vertical. <code>COUNT/SUM</code>, cardinalidade e DQ sobre massa são queries/procedures SQL; Java soma apenas um conjunto fixo de resumos <code>O(1)</code>.
  - **Aceite:** indisponibilidade SQL/check inválido falha; execução parcial nunca vira sucesso; threshold absoluto/percentual, SLA de quarantine e retenção têm owner; nenhuma evidência versiona PII/ID/payload; queries devolvem contagens e <code>TOP(N)</code> sanitizado, nunca todas as chaves/linhas para comparação na JVM.
  - **Estado/evidência em 31/08/2026:** <code>COMPLETE_LOCAL</code>. V006 cria policies/checks imutáveis com scope e material canônicos, seleção latest deny-by-default, quatro checks comuns set-based, avaliações/resultados, métrica fixa de 23 campos, alertas, health e trigger final de publicação; não semeia policy produtiva. Thresholds estruturais são zero e o SLA combina absoluto + basis points, sendo revalidado no health e no trigger. Java expõe engine/resumo/permit O(1), métricas/health/alertas tipados, JDBC com query timeout/fetch hint sem <code>SET ROWCOUNT</code>, classificação sanitizada, correlação opaca LIFO, redaction e budget por componente/processo; o root de logs fica <code>OFF</code>. Os exercícios 022–024 e o SHOWPLAN 025 passaram em rollback, inclusive instalação por <code>v2_migrator</code>, retry, falha/partialidade, igualdade dos thresholds e crossing temporal. Maven, arquitetura, cobertura e scanners passaram. Login/socket timeout e cancelamento cooperativo continuam como responsabilidade de composição de V2-022, não como lacuna do framework offline; policy/TTL, entrega externa e retenção física produtivas continuam em V2-045b.

- [x] **V2-024 — Criar adaptador GraphQL transitório.**
  - **Integração funcional local em14/09/2026 — V2-024:** Adapter GraphQL transitório e contratos de query; não equivale à retirada operacional. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Lista nominal de dependências GraphQL remanescentes, identidade por canal e critério/prazo de retirada. Endpoints/credenciais autorizados e source release autenticado. Paridade e continuidade na retirada por consumidor. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-018, V2-020, V2-043 e V2-044.
  - **Entrega:** cursor streaming para Usuários e, somente onde necessário, dual-run/backfill/campos ausentes de Coletas/Fretes.
  - **Aceite:** tamanho máximo 20 para Usuários respeitado; cursor ausente/repetido, <code>hasNextPage</code>, cap, vazio anômalo e timeout tratados; mutation proibida; cada campo transitório tem prazo/owner/saída.
  - **Estado/evidência em 31/08/2026:** <code>COMPLETO</code>. Configuração tipada e dormente, composição fail-fast e três documentos GraphQL estáticos/read-only implementam Usuários com página máxima 20 e apenas os sidecars transitórios necessários de Coletas/Fretes, limitados a 100 conforme o comportamento local caracterizado. O gateway público é obrigatoriamente ligado ao gate de contrato; serializer/parser estritos, executor HTTP limitado, governor ESL compartilhado, retry/circuit breaker e streamer serial tratam MIME/status, UTF-8, bytes/nodes/páginas, timeout, cancelamento, <code>Retry-After</code>, cursor ausente ou repetido, <code>hasNextPage</code>, página raiz vazia anômala e falhas sem expor resposta/segredo. Mutation e consulta arbitrária não possuem superfície produtiva. O catálogo <code>graphql-transitorio.csv</code> liga exatamente 19 campos ao selection set e à matriz V2-017a, todos <code>SYNTHETIC_ONLY</code>, com owner-papel, prazo e gate de saída; esse estado bloqueia promoção por construção e não alega completude. Fixtures sintéticas cobrem Users, Freights e Picks em duas páginas, inclusive coleção filha vazia válida. Testes direcionados (84), suíte completa (485), arquitetura, cobertura, formatação, análise estática e scanners passaram sem rede, segredo ou chamada externa.
  - **Revalidação em 01/09/2026 por V2-033:** o registro histórico acima permanece válido para o fechamento original de V2-024, mas o estado atual do ledger avançou somente para as quatro folhas de <code>USERS_SNAPSHOT</code>: <code>IMPLEMENTED_IN_SHADOW</code>/<code>SHADOW_UPSERT_ONLY</code>, sempre <code>publicationBlocked</code>. As 15 folhas de Coletas/Fretes continuam <code>SYNTHETIC_ONLY</code>/<code>OBSERVATION_ONLY</code>. Nenhuma folha autoriza sweep, desativação, publicação ou cutover.

- [ ] **V2-025 — Formalizar contratos das fontes restantes.**
  - **Integração funcional local em14/09/2026 — V2-025:** Gates executáveis dos contratos locais de fontes; perfis não provam contratos reais completos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Contratos /info/release e wire autenticados, paginação/ordem/completude e tradução temporal inclusiva por template. Fonte/canal nominal autorizados. Caracterização real por template, distinta da validade estrutural dos arquivos sintéticos. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-016a, V2-017a, V2-018, V2-043 e V2-044 para fixtures/contratos offline; V2-041 continua obrigatória para qualquer chamada remota e para V2-025d.
  - **Entrega:** transformar as sondas 6908/6389/6399/6906/8656/8636/4924/10633/6392 e Usuários em contratos/fixtures sintéticas; medir terminalidade/ordem/repetibilidade e obter prova de completude independente. Fixar <code>GET_WITH_QUERY</code> por template, envelope raiz-array/<code>data</code>, página numérica, tradução temporal e audit key independente de <code>order_by</code>. Nova chamada remota exige autorização por rodada ou atualização formal da allowlist do <code>AGENTS.md</code>; Raster exige autorização própria.
  - [x] **V2-025a — Primeira onda/Usuários:** versionar e revalidar 6908, 6389 e GraphQL <code>individual</code> sem esperar os demais templates.
  - **Estado/evidência em 01/09/2026:** <code>COMPLETO_LOCAL</code>. O manifesto versionado <code>docs/catalogos/contratos-primeira-onda/manifesto.json</code> cobre 6908, 6389 e GraphQL <code>individual</code> em 42 aspectos, com vocabulários fechados e separação entre classificação e proveniência. Catálogos estruturais em <code>src/main</code> tornam os três releases consumíveis sem fixture de teste; fingerprints semânticos determinísticos abrangem versão, transporte/documento, filtros, ordenação, paginação, chave técnica, teto local de página e gate de completude e fazem parte do fingerprint de configuração efetiva. Para 6908, metadata <code>request_date</code> permanece distinta do filtro de query <code>picks.request_date</code>; para 6389, o filtro é <code>freights.service_at</code>, a ordem funcional comprovada foi corrigida para <code>corporation_sequence_number asc</code> e os campos de performance úteis do legado foram preservados sem herdar a heurística ambígua de data. O gate recusa ordem diferente e <code>per</code> fora de 1..100 antes de I/O; a configuração recusa timezone diferente de <code>America/Sao_Paulo</code>. GraphQL continua limitado a <code>individual(enabled=true)</code>, <code>id/name</code>, página máxima 20 e documento read-only fixo. O baseline transitório aceita <code>id</code> JSON integral ou textual não vazio: o <code>Long</code> legado prova o destino interno, mas não distingue token numérico de texto coercível; V2-009a passou a preservar essas representações como source keys type-tagged distintas, dentro de scope explícito e ligadas ao release, sem alegar estabilidade ou unicidade global. Não há <code>updatedAt</code>, ordenação, mutação ou template Data Export inferido. Catorze fixtures sintéticas e travessias por V2-044 com dois valores de <code>per</code> provam limites por IDs escalares, expansão física, página terminal local, nome nulo/ausente, retry/cancelamento e falha fechada; não alegam snapshot global. A tradução interna <code>[start,endExclusive)</code> para bordas inclusivas da fonte permanece <code>BUSINESS_DECISION_PENDING</code>. Como não existe garantia versionada do fornecedor nem oráculo independente aprovado, os três contratos são <code>TRANSITIONAL</code> e <code>BLOCKED_NO_COMPLETENESS_PROOF</code> para sweep/desativação/cutover. O gate de completude permite somente solicitar shadow upsert, ainda sujeito aos gates contratuais e das entidades; GraphQL permanece com promoção bloqueada enquanto seu ledger estiver <code>SYNTHETIC_ONLY</code>. Nenhuma rede, credencial, banco ou dado real foi usado. V2-025 permanece aberta para V2-025b/V2-025c/V2-025d.
    - **Revalidação em 01/09/2026 por V2-033:** a política de domínio local para <code>name</code> agora está <code>PROVEN</code> no escopo current/history: <code>ABSENT/NULL/VALUE</code> permanecem distintos e <code>ABSENT</code> não apaga valor conhecido. Os fingerprints do documento/shape remoto não mudaram e continuam sem provar nullability do schema, completude, snapshot ou política do consumidor publicado. O próximo gate do contrato de Usuários é V2-012a; somente shadow upsert está liberado.
  - [x] **V2-025b — Demais ESL:** um contrato independente por 6399, 6906, 8656, 8636, 4924, 10633 e 6392; cada um pode fechar separadamente. **Reconciliação documental em 06/09/2026:** os sete subcheckboxes já estavam concluídos e validados; o agregado foi alinhado sem consumir bloco funcional. V2-025 permanece aberta somente por V2-025c/V2-025d.
    - [x] **V2-025b/6399 — Manifestos:** <code>COMPLETO_LOCAL</code>, corrigido no Bloco 36 sem reabrir a fatia. O catálogo independente <code>docs/catalogos/contratos-esl-6399/manifesto.json</code> formaliza os 13 aspectos do template com vocabulários fechados, proveniência sanitizada, quatro fingerprints e seis fixtures exclusivamente artificiais. Ele fixa somente <code>GET_WITH_QUERY</code>, filtro candidato <code>manifests.service_date</code>, ordenação histórica <code>sequence_code asc</code>, paginação numérica e as evidências sanitizadas de <code>/info</code> (91 campos/18 filtros) e da janela diária (quatro linhas físicas/três candidatos <code>sequence_code</code>, sem <code>id</code> observado). A revisão <code>2026-09-04.v2-025b.2</code> refuta o nome sintético incorreto <code>pick_sequence_code</code>, alinha as fixtures a <code>mft_pfs_pck_sequence_code</code> e inclui <code>mft_mfs_key</code>; os valores continuam artificiais e não provam contrato atual do fornecedor. Identidade/grão/reducers são decididos somente pelos catálogos P01/V03, não pelo contrato V2-025b isolado. Tradução temporal, semântica de <code>per</code>, completude/snapshot e timezone da data civil continuam sem garantia externa; sweep, desativação e cutover permanecem bloqueados. Nenhuma rede, credencial, banco, payload novo, migration, código de produção, deploy ou cutover foi usado.
    - [x] **V2-025b/6906 — Cotações:** <code>COMPLETO_LOCAL</code>. O catálogo independente <code>docs/catalogos/contratos-esl-6906/manifesto.json</code> formaliza os 13 aspectos do template de Cotações com vocabulários fechados, proveniência sanitizada, quatro fingerprints e seis fixtures exclusivamente artificiais. Ele fixa somente <code>GET_WITH_QUERY</code>, filtro candidato <code>quotes.requested_at</code>, ordenação histórica <code>sequence_code asc</code>, paginação numérica e as evidências sanitizadas de <code>/info</code> (37 campos/seis filtros) e da janela (três linhas físicas/três candidatos <code>sequence_code</code>, sem <code>id</code> observado). A tradução de bordas temporais, a semântica de <code>per</code>, timezone da data civil e limites de erro permanecem classificados conforme a prova disponível. A chave de raiz está explicitamente <code>BUSINESS_DECISION_PENDING</code> e a ausência de expansão não infere filho, identidade, unicidade, estabilidade, tenant scope, cardinalidade, frescor, tarifa, reducer, relação, schema ou implementação. Sem garantia versionada do fornecedor ou oráculo independente, completude é <code>ABSENT</code>, e sweep/desativação/cutover seguem bloqueados. A implementação em sombra não é autorizada só por este contrato; depende de V2-009b, V2-027 e gates da entidade. Nenhuma rede, credencial, banco, dado real, migration, código de produção, deploy ou cutover foi usado.
    - [x] **V2-025b/8656 — Localização de Cargas:** <code>COMPLETO_LOCAL</code>. O catálogo independente <code>docs/catalogos/contratos-esl-8656/manifesto.json</code> formaliza os 13 aspectos do template de Localização com vocabulários fechados, proveniência sanitizada, quatro fingerprints e seis fixtures exclusivamente artificiais. Ele fixa somente <code>GET_WITH_QUERY</code>, filtro candidato <code>freights.service_at</code>, ordenação histórica <code>sequence_number asc</code>, paginação numérica e as evidências sanitizadas de <code>/info</code> (24 campos/oito filtros) e da janela (três linhas físicas/três candidatos <code>corporation_sequence_number</code>, sem <code>id</code> ou <code>sequence_number</code> publicado). A tradução de bordas temporais, a semântica de <code>per</code>, timezone da data civil e limites de erro permanecem classificados conforme a prova disponível; o <code>429</code> histórico de segunda página não prova quota global. A chave de raiz está explicitamente <code>BUSINESS_DECISION_PENDING</code> e o campo legado <code>status_branch_nickname</code> continua <code>UNSOURCED_LEGACY</code>; esta fatia não decide identidade, unicidade, estabilidade, tenant scope, cardinalidade, frescor, relação, schema ou implementação. Sem garantia versionada do fornecedor ou oráculo independente, completude é <code>ABSENT</code>, e sweep/desativação/cutover seguem bloqueados. A implementação em sombra não é autorizada só por este contrato; depende de V2-009b, V2-028 e gates da entidade. Nenhuma rede, credencial, banco, dado real, migration, código de produção, deploy ou cutover foi usado.
    - [x] **V2-025b/10633 — Inventário:** <code>COMPLETO_LOCAL</code>. O catálogo independente <code>docs/catalogos/contratos-esl-10633/manifesto.json</code> formaliza os 13 aspectos do template de Inventário com vocabulários fechados, proveniência sanitizada, quatro fingerprints e seis fixtures exclusivamente artificiais. Ele fixa somente <code>GET_WITH_QUERY</code>, filtro candidato <code>check_in_orders.started_at</code>, ordenação histórica <code>sequence_code asc</code>, paginação numérica e as evidências sanitizadas de <code>/info</code> (26 campos/sete filtros) e da janela (27 linhas físicas/três candidatos <code>sequence_code</code>, sem <code>id</code> observado); mapeamentos de invoices expandem a raiz. A tradução de bordas temporais, a semântica de <code>per</code>, timezone da data civil e limites/erros permanecem classificados conforme a prova disponível. Raiz e filhos estão explicitamente <code>BUSINESS_DECISION_PENDING</code>: esta fatia não decide identidade, unicidade, estabilidade, tenant scope, cardinalidade, frescor, dedupe, relação, parsing, comprovante, reducer, schema ou implementação. Sem garantia versionada do fornecedor ou oráculo independente, completude é <code>ABSENT</code>, e sweep/desativação/cutover seguem bloqueados. A implementação em sombra não é autorizada só por este contrato; depende de V2-009b, V2-031 e gates da entidade. Nenhuma rede, credencial, banco, dado real, migration, código de produção, deploy ou cutover foi usado.
    - [x] **V2-025b/8636 — Contas a Pagar:** <code>COMPLETO_LOCAL</code>. O catálogo independente <code>docs/catalogos/contratos-esl-8636/manifesto.json</code> formaliza os 13 aspectos do template de Contas a Pagar com vocabulários fechados, proveniência sanitizada, quatro fingerprints e seis fixtures exclusivamente artificiais. Ele fixa somente <code>GET_WITH_QUERY</code>, a caracterização conjunta dos filtros <code>accounting_debits.issue_date+created_at</code>, ordenação histórica <code>issue_date desc</code>, paginação numérica e as evidências sanitizadas de <code>/info</code> (28 campos/nove filtros) e da janela (<code>per=3</code> com cinco linhas físicas/cinco candidatos <code>ant_ils_sequence_code</code>, sem ID da raiz contábil). A tradução de bordas temporais, a semântica de <code>per</code>, timezone da data civil e limites/erros permanecem classificados conforme a prova disponível; <code>source_entities</code> e <code>distinct_root_keys</code> continuam <code>UNVERIFIED</code>. A identidade da raiz é <code>ABSENT</code> e a parcela está explicitamente <code>BUSINESS_DECISION_PENDING</code>: esta fatia não decide regra financeira, competência, status, valores, catálogo derivado, identidade, unicidade, tenant scope, grão, relação, cardinalidade, frescor, dedupe, reducer, schema ou implementação. Sem garantia versionada do fornecedor ou oráculo independente, completude é <code>ABSENT</code>, e sweep/desativação/cutover seguem bloqueados. A implementação em sombra não é autorizada só por este contrato; depende de V2-009b, V2-029 e gates da entidade. Nenhuma rede, credencial, banco, dado real, migration, código de produção, deploy ou cutover foi usado.
    - [x] **V2-025b/4924 — Faturas por Cliente:** <code>COMPLETO_LOCAL</code>. O catálogo independente <code>docs/catalogos/contratos-esl-4924/manifesto.json</code> formaliza os 13 aspectos do template de Faturas por Cliente com vocabulários fechados, proveniência sanitizada, quatro fingerprints e seis fixtures exclusivamente artificiais. Ele fixa somente <code>GET_WITH_QUERY</code>, filtro candidato <code>freights.service_at</code>, ordenação histórica <code>unique_id asc</code>, paginação numérica e as evidências sanitizadas de <code>/info</code> (52 campos/17 filtros) e da janela (<code>per=3</code> com três linhas físicas/três candidatos inteiros <code>id</code>, sem <code>unique_id</code> publicado). A tradução de bordas temporais, a semântica de <code>per</code>, timezone da data civil e limites/erros permanecem classificados conforme a prova disponível. <code>id</code> é somente candidato de linha e título lógico, documentos, fretes, identidade, unicidade, estabilidade, tenant scope, aliases/rekey, crosswalk e cardinalidade são <code>BUSINESS_DECISION_PENDING</code>. A precedência entre CT-e e NFS-e permanece <code>UNRESOLVED</code>; esta fatia não decide fiscal, CNPJ, status, valores, frescor, dedupe, reducer, schema, fato, publicação ou implementação. Sem garantia versionada do fornecedor ou oráculo independente, completude é <code>ABSENT</code>, e sweep/desativação/cutover seguem bloqueados. A implementação em sombra não é autorizada só por este contrato; depende de V2-009b, V2-030 e gates da entidade. Nenhuma rede, credencial, banco, dado real, migration, código de produção, deploy ou cutover foi usado.
    - [x] **V2-025b/6392 — Sinistros:** <code>COMPLETO_LOCAL</code>. O catálogo independente <code>docs/catalogos/contratos-esl-6392/manifesto.json</code> formaliza os 13 aspectos do template de Sinistros com vocabulários fechados, proveniência sanitizada, quatro fingerprints e seis fixtures exclusivamente artificiais. Ele fixa somente <code>GET_WITH_QUERY</code>, filtro candidato <code>insurance_claims.opening_at_date</code>, ordenação histórica <code>sequence_code asc</code>, paginação numérica e as evidências sanitizadas de <code>/info</code> (44 campos/seis filtros) e da janela (<code>per=3</code> com duas linhas físicas/dois candidatos <code>sequence_code</code>, sem <code>id</code> publicado); relações de minuta e invoice foram observadas sem identidade, cardinalidade ou filhos comprovados. A tradução de bordas temporais, a semântica de <code>per</code>, timezone da data civil e de hora e limites/erros permanecem classificados conforme a prova disponível. <code>sequence_code</code> é somente candidato de raiz; o hash legado, unicidade, estabilidade, tenant scope, grão, relações, filhos, frescor, horas, valores financeiros, dedupe, reducer, schema, fato, publicação e implementação são <code>BUSINESS_DECISION_PENDING</code>. Sem garantia versionada do fornecedor ou oráculo independente, completude é <code>ABSENT</code>, e sweep/desativação/cutover seguem bloqueados. A implementação em sombra não é autorizada só por este contrato; depende de V2-009b, V2-032 e gates da entidade. Nenhuma rede, credencial, banco, dado real, migration, código de produção, deploy ou cutover foi usado.
  - [x] **V2-025c — Raster condicional:** somente depois de V2-034a=manter e autorização Raster específica.
    - **Fechamento local em 08/09/2026:** decisão MANTER por delegação explícita do owner e autorização de preparação local desta frente. O catálogo <code>docs/catalogos/raster-contrato-local/contrato.json</code> formaliza 18 aspectos, 51 declarações/aliases e proveniência estática; V2-025c está <code>COMPLETO_LOCAL</code>, com contrato <code>TRANSITIONAL</code>. Dezesseis casos e doze contraprovas offline passaram. Endpoint/transporte e campos são evidência legada; wire type, bordas temporais, identidade, snapshot e completude remotos não foram provados. V2-009c e V2-034b seguem bloqueadas; não houve rede, credencial, banco ou código de domínio/runtime.
  - [ ] **V2-025d — Executar a rodada cURL finaleira Data Export-only e fechar a matriz V1.** Depois de V2-041 e reconfirmação owner de janela/teto, executar serialmente <code>/info</code> + uma página <code>/data</code> mínima para cada uma das nove requisições desta seção, usando somente <code>curl.exe</code>, respostas em memória, parada no primeiro não-2xx/429/limite não verificável e sem retry/fallback. Usuários só entra se houver template oficial; <code>9901</code> é proibido por inferência. Raster não entra.
  - **Aceite:** metadata, raiz, filtros, tradução temporal, ordenação, <code>per</code>, identidade de raiz/filho, paginação, timezone, limites/erros e classificações padronizadas <code>PROVEN/TRANSITIONAL/ABSENT/BUSINESS_DECISION_PENDING</code>; ordem não substitui identidade e página não é cursor. Para 8636, travessia e oráculo usam exatamente <code>issue_date+created_at</code>. V2-025d atualiza fingerprints/contagens da baseline 442×459 e cada fatia de V2-017a sem versionar dado real. Prova de completude registra owner, artefato/versão, escopo e data. Sem garantia versionada do fornecedor ou oráculo independente aprovado, a entidade fica <code>BLOCKED_NO_COMPLETENESS_PROOF</code> para sweep/cutover — especialmente 8636 e Usuários — embora upsert em sombra possa prosseguir.

- [ ] **V2-009 — Definir identidade e crosswalk de todas as entidades.**
  - **Integração funcional local em14/09/2026 — V2-009:** DeclaredWireRows/DeclaredExpansionRelations + QualificationOracles.Evidence.componentIdentity usam chaves tipadas declaradas, incluindo nomes alternativos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Crosswalks nominais com source system, tenant, wire type, papel, estabilidade e cardinalidade, inclusive raiz/parcela/componente e viagem/parada. Cadastro/release de identidade administrado. Identidade real sob reordenação/remoção/retorno e colisão; ordinal/minuta não são chaves. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-017/V2-017a e do subcontrato V2-025a/V2-025b/V2-025c da entidade.
  - **Ordem:** o desenho físico de cada domínio em V2-019/V2-021 depende desta decisão de identidade, não o inverso.
  - **Entrega:** especificar registry <code>(source_instance, tenant_scope, entity, source_key)</code> único para canonical ID, aliases versionados, proveniência, cardinalidade, rekey/conflito/reativação; <code>source_instance</code> é ID estável e não secreto da origem/conta lógica contratual, nunca token nem transporte; normalizar tenant/corporação ou usar sentinel singleton somente com prova de chave global; matrizes por entidade.
  - [x] **V2-009a — Primeira onda:** revalidar V2-004/V2-005 dentro de V2-025a; confirmar <code>id</code> para 6908/6389 e <code>user_id</code> para Usuários; business keys nunca viram ID técnico.
    - **Estado/evidência em 01/09/2026:** <code>COMPLETO_LOCAL</code>. ADR 0018 e o catálogo versionado <code>docs/catalogos/identidade-primeira-onda/manifesto.json</code> fecham exatamente <code>coletas</code>, <code>fretes</code> e <code>usuarios</code>, ligados aos três fingerprints de release V2-025a. A tuple é <code>(source_instance, tenant_scope, entity, source_key)</code> com comparação exata/case-sensitive; <code>source_instance</code> representa a origem lógica ESL compartilhada por Data Export/GraphQL e o control plane deve usar a família <code>ESL</code>, enquanto <code>tenant_scope</code> é explícito e recusa sentinels globais nesta onda. <code>canonical_id</code> permanece surrogate <code>BIGINT IDENTITY</code> futuro e não reutiliza source ID, business key, ordem, hash ou <code>core.entity_record_state.record_state_id</code>. 6908 usa <code>/id</code> integral e alias versionado <code>/sequence_code</code>; 6389 usa <code>/id</code> integral e alias <code>/corporation_sequence_number</code>, cuja resolução é 0..N/ambígua conforme a evidência legada; Usuários usa <code>/node/id</code> como <code>user_id</code>, sem transformar <code>name</code> em chave. O codec <code>first-wave-identity-v1</code> preserva <code>INTEGER</code>/<code>STRING</code> com tags distintas, limita 256 caracteres, processa uma observação por vez e redige valores. A policy O(1) classifica registro, replay, reativação, alias/rekey versionado e dez motivos fechados de quarentena; colisão/cardinalidade continuam preflight set-based físico de V2-009d. Repetição de raiz em linhas expandidas é replay, não conflito; alias ambíguo bloqueia somente a resolução dependente. Nomenclatura do harness/documentação foi corrigida de ID “canônico” para source ID. O catálogo não prova unicidade/completude global nem autoriza sweep/cutover. Não houve migration, SQL, banco, rede, credencial ou dado real.
  - [ ] **V2-009b — Demais ESL:** testar as hipóteses desta revisão — 6399 agregado <code>sequence_code</code>, 6906 <code>sequence_code</code>, 8656 <code>corporation_sequence_number</code>, 8636 parcela <code>ant_ils_sequence_code</code>, 4924 <code>id</code>, 10633/6392 agregado <code>sequence_code</code> — quanto a unicidade, estabilidade temporal, tenant scope e cardinalidade.
    - [x] **V2-009b/6906 — Cotações:** <code>COMPLETO_LOCAL</code>. O catálogo <code>docs/catalogos/identidade-cotacoes/manifesto.json</code> fecha, no escopo local caracterizado, <code>/sequence_code</code> inteiro como source key da raiz lógica escopada por <code>(source_instance, tenant_scope, cotacoes, source_key)</code>; o <code>canonical_id</code> futuro continua surrogate <code>BIGINT IDENTITY</code>. A evidência sanitizada sustenta a PK <code>sequence_code</code> legada, o DTO <code>Long</code> e uma janela com três linhas/três candidatos, sem <code>id</code> nem filho/expansão observados. Ela não prova unicidade global, estabilidade temporal, semântica de <code>per</code>, completude ou uma linha física por raiz fora da janela. Não há campo tenant comprovado e nenhum campo de negócio é promovido como tenant: o scope externo explícito é obrigatório e sentinels globais são recusados. Repetição divergente da mesma tuple bloqueia promoção até V2-027 definir frescor/reducer; não cria raiz, filho, alias ou rekey implícitos. <code>V2-009d</code> continua dono do enforcement físico, colisão, nulo, concorrência e replay da vertical. Sweep/desativação/cutover permanecem bloqueados. Nenhuma rede, credencial, banco, migration, código de produção, payload real, deploy ou cutover foi usado.
    - [x] **V2-009b/6399 — Manifestos:** <code>COMPLETE_LOCAL_CONSERVATIVE_FAIL_CLOSED</code>. A reauditoria P01 do Bloco 36 usa somente o contrato offline, V2-017a, DTO/mapper/repository/tabela/scripts e o corpus estático já versionado do legado; refuta <code>/pick_sequence_code</code> como source path e fixa <code>/mft_pfs_pck_sequence_code</code> inteiro. A raiz é <code>(source_instance, tenant_scope, manifestos, INTEGER:&lt;sequence_code&gt;)</code>; pick é <code>(root_canonical_id, INTEGER:&lt;mft_pfs_pck_sequence_code&gt;)</code>; MDF-e é <code>(root_canonical_id, STRING:&lt;mft_mfs_key&gt;)</code>, enquanto <code>mft_mfs_number</code> fica atributo correlato com presença/proveniência. O corpus delimitado contém 228 linhas, 100 raízes, 193 chaves pick e 43 chaves MDF-e; não há colisão raiz+pick/raiz+chave MDF-e, e 37 observações MDF-e repetidas por expansão colapsam no mesmo filho. Número/chave estão ambos presentes em 80 linhas e ambos ausentes/nulos em 148; <code>mdfe_status</code> é <code>VALUE</code> nas 228, sem divergência por raiz, logo é escalar da raiz e não sinal de filho. O contrato continua 0..N sem transformar máximos observados em garantia. Scope externo explícito é obrigatório; sentinels globais, hash, ordem, número/status MDF-e isolado e coocorrência são proibidos como identidade. Replay idêntico é no-op; colisão, rekey, root/child key inválida, assimetria número/chave ou conflito residual no mesmo frescor são preservados/quarentenados, mas ausência/nulo sem sinal material significa zero filho. O vínculo Manifesto→Coleta permanece candidato exclusivo de V2-046a, sem relação inferida. Esta decisão não prova unicidade global, estabilidade temporal, tenant no payload ou completude e não autoriza implementação, sweep, publicação ou cutover. V2-009b agregada permanece aberta pelas demais fatias.
    - [x] **V2-009b/8656 — Localização de Cargas:** <code>COMPLETE_LOCAL_BOUNDED</code>. O catálogo <code>docs/catalogos/identidade-localizacao-cargas/manifesto.json</code> fixa, somente no escopo local caracterizado, <code>/corporation_sequence_number</code> inteiro como source key da raiz lógica por <code>(source_instance, tenant_scope, localizacao_cargas, source_key)</code>; o canonical permanece surrogate. Não há business key, alias, rekey, crosswalk ou filho aceito; <code>sequence_number</code> é rejeitado como source alias e nenhum vínculo com Frete é inferido. A janela 3:3, o tipo/PK/dedupe legados e as fixtures sintéticas não provam unicidade global, estabilidade temporal, tenant no payload, cardinalidade fora da janela ou completude; scope externo explícito continua obrigatório e sweep/cutover ficam bloqueados. Replay reaproveita a raiz, mudança escalar é observação e divergência bloqueia promoção até V2-028. O wrapper próprio <code>Test-DataExport8656IdentityCatalog.ps1</code> passou; somente esta subdecisão do lote fecha.
    - [ ] **V2-009b/10633 — Inventário:** <code>UNRESOLVED_ROOT_FREIGHT_AND_INVOICE_MAPPING_IDENTITY</code>. O catálogo <code>docs/catalogos/identidade-inventario/manifesto.json</code> mantém <code>/sequence_code</code> inteiro apenas como candidato de raiz e retém o binding canônico: a janela 27:3 não resolve o hash legado composto/mutável. <code>/cnr_c_s_fit_corporation_sequence_number</code> é componente escalar candidato sem papel de filho/relação, e <code>cnr_c_s_fit_invoices_mapping</code> é <code>Object</code> no DTO; a expansão da fixture sintética não prova shape remoto. Não há business key, alias ou rekey aceito; replay canônico, raiz, componente Frete/minuta, paths/componentes naturais, colisão e cardinalidades seguem não classificáveis, sem relação inferida. O wrapper <code>Test-DataExport10633IdentityCatalog.ps1</code> passou e mantém P08 aberto até evidências versionadas e observações representativas próprias; V2-031 não foi iniciada.
    - [ ] **V2-009b/8636 — Contas a Pagar:** <code>BLOCKED_ROOT_IDENTITY_ABSENT_PHYSICAL_ROW_KEY_REFUTED_AND_GRAIN_UNRESOLVED</code>. O catálogo <code>docs/catalogos/identidade-contas-a-pagar/manifesto.json</code> registra <code>path/name=null</code> e nenhum wire type/source key para a raiz <code>accounting_debit</code>; portanto não existe binding canônico nem replay de raiz classificável. <code>/ant_ils_sequence_code</code> permanece candidata de parcela com tipo inconclusivo entre histórico parcial <code>Integer</code> e coerção legada <code>String→Long</code>; repetições com campo contábil divergente a refutam como chave da linha física. Não há alias/rekey/filho ou relação raiz–parcela–rateio aceito, e todas as observações são preservadas sem <code>keep latest</code>. O wrapper <code>Test-DataExport8636IdentityCatalog.ps1</code> passou e mantém P09 bloqueado até ID/tipos versionados da raiz e prova representativa de grão/cardinalidade; V2-029 não foi iniciada.
    - [ ] **V2-009b/4924 — Faturas por Cliente:** <code>UNRESOLVED_LOGICAL_TITLE_AND_CROSSWALKS</code>. O catálogo <code>docs/catalogos/identidade-faturas-por-cliente/manifesto.json</code> aceita <code>/id</code> inteiro somente como source key/canonical binding da linha escopada, nunca como canonical ID nem como identidade do título lógico. Documento, NFS-e, CT-e e <code>billingId</code> permanecem candidatas sem alias/rekey/crosswalk; a multiplicidade histórica refuta título como chave de linha e as heurísticas de hash/repoint não viram contrato. Os elementos físicos de <code>/invoices_mapping/*</code> e <code>/fit_fte_invoices_order_number/*</code> permanecem com identidade, relação e cardinalidade não resolvidas. Replay exato pode ser no-op apenas da linha; divergência bloqueia a promoção lógica. O wrapper <code>Test-DataExport4924IdentityCatalog.ps1</code> passou e mantém P10 aberto até estabilidade versionada do ID, crosswalks e regras fiscais/filhos próprias; V2-030 não foi iniciada.
    - [ ] **V2-009b/6392 — Sinistros:** <code>UNRESOLVED_ROOT_SOURCE_KEY_VERSUS_LEGACY_COMPOSITE_GRAIN</code>. O catálogo <code>docs/catalogos/identidade-sinistros/manifesto.json</code> mantém <code>/sequence_code</code> inteiro como candidato, sem binding canônico, porque a janela sintética 2:2 não decide entre ele e o hash legado de sequência+ocorrência de invoice+minuta. Os campos escalares de minuta e ocorrência são componentes candidatos, não business keys, aliases, filhos ou relações; unicidade, estabilidade, papéis e cardinalidade raiz–componentes seguem sem prova. Sem grão aceito, replay/reativação canônicos não são classificáveis; observações são preservadas e conflito/rekey fica em quarentena. O wrapper <code>Test-DataExport6392IdentityCatalog.ps1</code> passou e mantém P11 aberto até prova versionada/representativa de raiz, colisão, papéis e cardinalidade; V2-032 não foi iniciada.
    - **Resultado do lote do Bloco 34, corrigido pelo Bloco 36 em 04/09/2026:** <code>PARCIAL</code>, agora com P01 e P07 concluídas; P08/P09/P10/P11 permanecem exatamente <code>UNRESOLVED/BLOCKED</code>, portanto V2-009b agregada continua aberta. O catálogo P01 foi revisto contra a evidência estática versionada e ganhou prova própria de paths, componentes, colisões observadas, replay e cardinalidade conservadora; isso não altera as cinco decisões independentes restantes. Não houve inferência de relação/completude, nem rede, credencial, banco, migration, schema, mapper, staging, promoção, publicação, sweep, deploy ou cutover. <code>V2-025d</code> permanece bloqueada pelo hold externo de V2-041 em todas as entidades.
  - [ ] **V2-009c — Raster condicional:** depois que V2-034a decidir manter e V2-025c formalizar o contrato, provar viagem/parada antes de V2-034b.
    - **Investigação local em 08/09/2026:** V2-034a=MANTER e V2-025c local concluídas. O arquivo <code>docs/catalogos/raster-contrato-local/identidade-pendente.json</code> conserva <code>CodSolicitacao</code> e <code>ColetasEntregas[].Ordem</code> como candidatos. Faltam escopo, wire type/estabilidade de raiz e identidade estável da parada sob reordenação/remoção/retorno. O fallback legado por posição foi recusado por contraexemplo sintético, sem alegar colisão real. Nenhum binding canônico aceito; V2-034b permanece bloqueada.
  - [ ] **V2-009d — Enforcement por vertical:** junto à migration/persistência da vertical, implementar constraints e testes de colisão, nulo, concorrência e replay; não bloqueia o início do desenho após V2-009a/V2-009b/V2-009c aplicável, mas bloqueia <code>IMPLEMENTADA_EM_SHADOW</code>.
    - [x] **V2-009d/ENFORCEMENT_USUARIOS_LOCAL — Identidade e constraints de Usuários em sombra.** Reconhecimento documental da fatia já comprovada por V007/V2-033: chave escopada, wire type, colisões, replay e isolamento. Não conclui o enforcement agregado das demais entidades.
    - **Fatia Usuários fechada em 01/09/2026:** <code>COMPLETO_LOCAL</code> por V007/V2-033. <code>core.usuario.usuario_id</code> é surrogate <code>BIGINT IDENTITY</code>; a source identity type-tagged é única e isolada por <code>(environment_name, source_instance, tenant_scope, entity_name, source_key)</code> sob collation BIN2. Nulo/tipo inválido, colisão, replay exato/divergente, reativação, stale/no-op, empate divergente e concorrência foram exercitados; constraints, índices, locks, quarantine e grants mínimos foram validados no SQL Server local com rollback integral. Esta evidência fecha somente Usuários e não marca V2-009d agregada como concluída para outras verticais.
  - **Aceite de decisão:** source/canonical/business keys e conflitos provados no escopo caracterizado antes do schema de domínio. **Aceite físico:** V2-009d fecha com a vertical. Minuta/sequência/hash não são promovidos fora do contrato comprovado.
  - **Estado agregado em 04/09/2026 após o Bloco 36:** <code>PARCIAL</code>. V2-009a está completa no escopo da primeira onda; V2-009b/6906, V2-009b/6399 e V2-009b/8656 estão completas somente nos escopos locais delimitados; a fatia V2-009d de Usuários está fechada. V2-009b para 8636, 4924, 10633 e 6392 permanece aberta com blockers próprios; V2-009c e as fatias V2-009d das demais verticais continuam abertas para seus enforcements físicos. Os checkboxes pai, V2-009b agregada e V2-009d agregada continuam abertos.

### Fase 2 — Base compartilhada e primeira onda

- [x] **V2-033 — Implementar Usuários e histórico.**
  - **Integração funcional local em14/09/2026 — V2-033:** USER: LocalProfileArtifact → LocalCharacterizationProfile → mapper atual, consumidos por Main local-profile com dois arquivos e saída determinística. A captura de apoio no cenário completo permanece PACKAGED_ANALYTIC_SUPPORT_V1 explícita; relações/projeções existentes continuam exercitadas pelos19contratos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Oráculo GraphQL individual(enabled=true), correspondência nominal e critérios de completude do snapshot. Canal/release e identidades administrados. Paridade real current/history/dimensão e snapshot completo. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-009a, V2-020, V2-021, V2-023, V2-024 e V2-025a.
  - **Entrega:** interface paginada GraphQL <code>enabled=true</code>, staging por execução, current/history, hash/no-op e reativação; cursor de retomada somente se validade/TTL forem provados, senão restart seguro; sem incremental temporal inventado. Upsert, mudança de hash, current→history, reativação e ausência são operações set-based no SQL, não loop/repositório por usuário.
  - **Aceite:** USR-01–USR-04; uma linha atual por <code>user_id</code>; histórico somente em mudança; cap/erro/mutação incerta não desativa; desativação fica desligada até V2-012b/V2-013; relações com Coletas não multiplicam grão; a JVM não carrega o universo atual/snapshot em conjuntos; fatia V2-017a fechada e marco <code>IMPLEMENTADA_EM_SHADOW</code>.
  - **Estado/evidência em 01/09/2026:** <code>IMPLEMENTADA_EM_SHADOW</code>. <code>ExtrairUsuariosGraphQl</code> consome somente <code>individual(enabled=true)</code> em páginas de até 20, mantém uma página/microbatch em voo, stageia-a sincronicamente e, sem TTL/retomada provados, reinicia da primeira página em replay; nenhuma coleção global de IDs/current/snapshot existe. O mapper preserva source key <code>INTEGER</code>/<code>STRING</code>, presença <code>ABSENT/NULL/VALUE</code>, limites Unicode e quarantine sanitizada. V007 cria current/history, aplicação/reconciliação/DQ/lifecycle set-based, identidade escopada por ambiente, ordem técnica total, hash/no-op, stale, replay, mudança e reativação, sem <code>active=0</code>, view <code>pub</code>, <code>updatedAt</code> ou sweep. O runtime recebe apenas os dois entrypoints fechados e <code>SHADOW_UPSERT_ONLY</code>. O lifecycle inclui o custo tipado antes de oversized/<code>TOP</code>/cumulativos via iTVF correlacionada e seek limitado; 027 compara o budget com o archive e prova ausência de starvation, enquanto 029 bloqueia sidecar tardio generic-only sob row fence. V001–V007, manifests, constraints, índices, grants, baseline, concorrência, SHOWPLAN e exercícios rollback-only passaram no alvo local autorizado, inclusive cinco paths de Usuários com zero conversão/warning operacional; o build final passou 537 testes, 15 regras arquiteturais, formatação, análise estática e cobertura. As 18 colunas físicas e quatro folhas GraphQL de Usuários estão <code>IMPLEMENTED_IN_SHADOW</code> na matriz V2-017a. O follow-up do Bloco 21 criou em V009 somente a dimensão interna <code>core.v_usuario_dimension_current_v1</code>; V007 permanece historicamente sem objeto <code>pub</code>. Não houve rede, credencial, dado real, publicação, deploy, commit ou cutover. V2-012a, bootstrap/paridade, ausência V2-013, contrato consumidor V2-037, escala V2-050 e retenção produtiva V2-045b permanecem gates posteriores e não tornam este marco parcial.

- [ ] **V2-035 — Portar referências e dimensões governadas.**
  - **Integração funcional local em14/09/2026 — V2-035:** Seis dimensões e referências consumidas; identidade declarada, papéis, vigências e rekey. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Seis dimensões: cadastros de filial, cliente, veículo, motorista, plano de contas e usuário; papéis/vigências/rekey e referências fiscal/calendário/frota. Releases de referência e identidades nominais. Paridade dimensional e trocas de vigência reais. Nenhum checkbox ou numerador alterado.
  - **Aprofundamento local em13/09/2026: CONSTRUÍDO_LOCALMENTE_VERIFICADO.** As seis dimensões (Filiais, Clientes, Veículos, Motoristas, Plano de Contas, Usuários) estão consumidas por fatos/queries, com releases, papéis, vigências e rekey. Homônimo/placa repetida não funde identidade; política genérica de motorista é explícita. **ACEITE_REAL_PENDENTE:** registros/bindings nominais e paridade. Evidências: [matriz A–N](docs/catalogos/macrobloco-analitico/MATRIZ-A-N.md), [verificação](docs/catalogos/macrobloco-analitico/verification-summary.json), [colunas/linhagem](docs/catalogos/macrobloco-analitico/matriz-colunas-final.json). O checkbox de aceite agregado permanece aberto.
  - **Depende de:** V2-017 e V2-019.
  - **Entrega:** separar dados de referência administrados das seis dimensões derivadas (Filiais, Clientes, Veículos, Motoristas, Plano de Contas e Usuários); nenhuma dimensão derivada bloqueia a própria fonte que a alimenta.
  - [ ] **V2-035a — Referências/seeds governados:** calendário/feriados, status de Coleta, aliases/região, documentos/filiais operacionais, frota própria, atribuição de filial, pagadores excluídos de cubagem e matriz tarifária/financeira. Exigir export/baseline autorizado, proveniência e vigência de <code>manifestos_frota_propria_cnpjs</code>, <code>dim_regiao_logistica_rules</code> e linhas produtivas de <code>regras_atribuicao_filial</code>, pois o DDL não contém todo o conteúdo atual.
    - [x] **V2-035a/FUNDACAO_REFERENCIAS_LOCAL — Fundação offline de referências governadas.** Reconhecimento documental da entrega de 01/09/2026: V008, manifesto governed-references e evidências locais já registradas abaixo. Não conclui baseline produtivo, ratificação ou ativação.
    - **Estado/evidência em 01/09/2026:** <code>FOUNDATION_OFFLINE_COMPLETE_EXTERNAL_BASELINE_PENDING</code>. Este marco formal encerra a fatia offline executável do Bloco 20, mas mantém V2-035a e V2-035 agregadas abertas: export/baseline mutável autorizado, algoritmos/versionamentos produtivos de tokenização e normalização, importação por autoridade futura, ratificação independente, paridade e ativação continuam externos. V008 e o manifesto SHA-256 <code>ff2054321ccf6f4dcb72dedfdbae15dc1347ce01bdfe971bd70ce031cb24d5c0</code> modelam oito famílias e 14 tabelas tipadas de conteúdo, além de release, receipt, ratificação e revogação append-only. Não há linha produtiva, default, ponteiro current, fallback, importador/consumer ou <code>GRANT</code> publicado. Releases são escolhidas por ID explícito; registro é replay idempotente somente para envelope exato; receipt sela fingerprint, contagem física e bytes; ratificação exige aprovador distinto de autor/importador e evidência de aprovação separada; revogação preserva conteúdo e recusa dependência ativa de atribuição→filial no mesmo scope. Grão, vigência <code>[from,to)</code>, sobreposição, proveniência, versão de normalização e de token, comparação BIN2, UF/CEP, direção/moeda/escala tarifária e falha fechada sem correspondência são constraints/índices e guards set-based no SQL. O calendário é rolante, independente de idioma/<code>DATEFIRST</code>, limitado a 3.660 dias e lookback contínuo máximo de 31 dias; sua seed e a de status são somente candidatas a ratificação. O pacote canônico RFC 4180 é versionado, inclui todas as tabelas da família mesmo vazias, limita 100.000 linhas, 16 MiB e 1.000 linhas em voo e possui fixtures golden de bytes/hash. V001–V008, manifests, 030–034, corrida física content/seal, dois interleavings atribuição/filial, positivos, negativos isolados, migrator e SHOWPLAN passaram no SQL Server local com rollback integral; o plano V2-035a teve 18 relops, 15 seeks, zero scan/conversão/warning operacional. O build Java 17 passou 539 testes e os catálogos determinísticos foram regenerados sem alterar suas cardinalidades. O legado foi inspecionado somente em leitura para caracterização; nenhuma credencial ou rede foi usada e nenhum valor produtivo foi copiado, registrado ou emitido na V2, na documentação ou nas evidências. O subgate local está completo; a tarefa não recebe <code>[x]</code> até que todos os subgates externos acima sejam comprovados.
  - [ ] **V2-035b — Dimensões publicadas por domínio:** Filiais depende de V2-011/V2-026/V2-029/V2-030; Clientes de V2-010/V2-011/V2-030; Veículos/Motoristas dependem da decisão V2-035c e de V2-026; Plano de Contas de V2-029; Usuários publica apenas a view sobre current/history de V2-033. Fechar cada dimensão separadamente em sombra.
    - [x] **V2-035b/DIMENSAO_USUARIOS_LOCAL — Dimensão interna current de Usuários em sombra.** Reconhecimento documental da fatia de 01/09/2026: V009 e core.v_usuario_dimension_current_v1. Não conclui as outras cinco dimensões nem o contrato consumidor de V2-037.
    - **Fatia Usuários fechada em 01/09/2026:** <code>IMPLEMENTADA_EM_SHADOW_CONSUMER_CONTRACT_PENDING</code>. V009 cria somente <code>core.v_usuario_dimension_current_v1</code>, schemabound, current-only e set-based, com uma linha ativa por <code>usuario_id</code> canônico e identidade alternativa <code>(environment_name, source_instance, tenant_scope, source_key_token)</code>. A projeção preserva wire type, presença <code>ABSENT/NULL/VALUE</code>, nome sem trim e tempos técnicos UTC; não junta history, não expõe hash/execution ID/<code>active</code>, não cria índice, role, principal, <code>GRANT</code> ou <code>DENY</code>. Fixtures rollback-only provaram ativos/inativo, tokens <code>INTEGER</code>/<code>STRING</code> distintos, três presenças, duas linhas history sem multiplicação, timestamps sem transformação e ausência efetiva de <code>SELECT</code> para <code>public</code>/<code>v2_runtime</code>. O contrato <code>pub.vw_dim_usuarios</code>, aliases/trim, reader, manifesto consumidor, paridade, rota e cutover permanecem em V2-037. A fatia está completa; V2-035b e V2-035 agregadas continuam <code>[ ]</code> apenas pelas outras cinco dimensões e pelos subgates próprios já registrados.
  - **Compatibilidade a caracterizar:** documentos das filiais operacionais; aliases AGU/CAS/CPQ/CWB/NHB/REC/RJR/SPO; matriz veículo–motorista e exceção LM Transportes; CNPJs; seed Frigelar→CWB; calendário legado 01/12/2019–31/12/2032 com feriados fixos/móveis, Páscoa, opcionais, 20/11 desde 2024 e último dia útil.
  - **Aceite:** alvo usa calendário rolante, não horizonte fixo; grãos dimensionais declarados; chaves, vigência, motivo, versão, autor/aprovação, ranges sem sobreposição, seeds determinísticos e paridade.
  - [x] **V2-035c — Decidir a Frota de Manifestos para Veículos e Motoristas.**
    - **Depende de:** V2-026 concluída em sombra; não depende de rede, oráculo ESL, baseline externo, V2-035a, publicação ou cutover.
    - **Escopo decisório local:** avaliar em conjunto somente a evidência local versionada da vertical 6399 para declarar, por dimensão, source/canonical/business key, grão, presença, normalização, frescor/vigência, conflito, rekey, ausência e quarentena. Veículo principal, reboques, placa, nome/proprietário/capacidade, motorista, tipo de contrato e eventual filial são sinais distintos até prova explícita; não criar vínculo veículo→motorista, não tratar nome como identidade, não derivar filial e não transformar máximo observado em contrato.
    - **Entrega:** catálogo e ADR de decisão, matriz de evidências/casos sintéticos e validator determinístico; registrar para cada dimensão somente <code>EXECUTION_READY</code>, <code>PARTIAL_DECISION_ONLY</code> ou <code>BLOCKED</code>. Não criar Java, migration, schema, tabela, view, procedure, grant, runtime, relação, fato ou contrato <code>pub</code> neste bloco.
    - **Aceite e handoff obrigatório:** cada regra possui origem local, contraexemplo, teste e disposição fail-closed. Ao encerrar, atualizar primeiro este estado e depois a trilha; somente se Veículos estiver <code>EXECUTION_READY</code>, criar <code>docs/runbooks/frota-manifestos-v2-035b-execucao-terra.md</code> com o prompt Terra delimitado para D03, arquivos/artefatos exatos, invariantes congeladas, testes e proibições. Se Motoristas também estiver pronto, registrá-lo como candidato posterior, sem iniciá-lo no chat Terra de D03. Sem esse resultado, o handoff deve registrar o bloqueio e não fabricar prompt de implementação.
    - **Estado/evidência em 05/09/2026:** <code>COMPLETE_LOCAL_DECISION_ONLY</code>; <code>VEICULOS=BLOCKED</code> e <code>MOTORISTAS=BLOCKED</code>. O catálogo versionado <code>docs/catalogos/frota-manifestos-v2-035c/decisao-v01.json</code>, a matriz com 25 regras, 25 fixtures estritamente sintéticas, o README, o ADR 0025 e <code>Test-FrotaManifestosV2035cDecisionCatalog.ps1</code> registram origem, limite, contraexemplo, disposição e teste. A vertical 6399 preserva paths e <code>ABSENT/NULL/VALUE</code>, mas seu contrato transitório, P01, V03 e V04 só provam o grão/frescor de Manifesto: não há ID estável de veículo ou motorista, placa/nome aprovados como business key, filial proprietária/lotação, vigência própria, current/history ou rekey. Principal/reboques 1/2 ficam separados; contrato é atributo contextual; nome genérico não é descartado; conflito no mesmo frescor é quarentena; ausência incremental não produz sweep; coocorrência nunca cria join/FK Veículo→Motorista. O runbook Terra D03 permanece deliberadamente ausente e D03/D04 continuam bloqueadas; V2-035b e V2-035 permanecem abertas.
    - **Reavaliação V02 autorizada em 05/09/2026, sem novo bloco funcional:** <code>COMPLETE_LOCAL_REASSESSMENT_BLOCKED</code>. O owner autorizou usar somente o chat/worktree, mas não forneceu contrato dimensional. A busca pré-V02 após <code>2026-09-05T13:35:21.2424688-03:00</code> encontrou zero arquivo técnico novo; <code>pasted-text.txt</code>, de 31/08/2026 e SHA-256 <code>2d768f7f5474047ba3f4b36f74c59ca54bad2555d7efeab743fee6279db845c0</code>, é somente prompt operacional genérico; os 14 anchors da V01 continuaram íntegros. <code>decisao-v02.json</code>, <code>matriz-reavaliacao-v02.csv</code> e 13 fixtures simbólicas explicitam as lacunas: para Veículos faltam ID imutável do fornecedor ligado a <code>source_instance</code>/<code>tenant_scope</code>, semântica versionada de placa/filial/reutilização/reatribuição e lifecycle de vigência/current/history/ausência/conflito/rekey; para Motoristas faltam o mesmo ID escopado, política de homônimos/genéricos/renomeação/merge/split, contrato/filial/lotação e lifecycle equivalente. Somente principal/reboque 1/reboque 2 separados está <code>PROVEN_LIMITED</code>, insuficiente para D03. Portanto <code>VEICULOS=BLOCKED</code> e <code>MOTORISTAS=BLOCKED</code> permanecem; não se cria fatia nova sob V2-035, Bloco 39, migration, relação, implementação ou prompt Terra.

- [ ] **V2-047 — Definir e executar bootstrap histórico por entidade.**
  - **Integração funcional local em14/09/2026 — V2-047:** Planejamento/bootstrap local e recomposição por partição; histórico real representativo pendente. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Janela histórica, completude, lookback, orçamento e aceites por entidade do bootstrap/backfill. Fonte autorizada e alvo de recuperação material. Carga histórica representativa, recuperação e reconciliação com origem. Nenhum checkbox ou numerador alterado.
  - **Depende para planejar de:** V2-017, V2-019 e subcontrato/identidade aplicáveis V2-025a/V2-025b/V2-025c e V2-009a/V2-009b/V2-009c. **Para executar:** a vertical precisa estar <code>IMPLEMENTADA_EM_SHADOW</code> e V2-012a precisa estar aceita; Manifestos depende de V2-026. Raster só entra se V2-034a decidir manter e V2-034b estiver implementada; sem histórico necessário, sua linha fica <code>NOT_APPLICABLE</code> com aceite, nunca omitida.
  - [x] **V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE — Planejar deterministicamente o bootstrap sem executar fontes.**
    - **Rota/bloco:** <code>Q-BST-01</code>, Bloco 41, GPT-5.6 Sol Ultra. V2-017, V2-019 e os contratos/identidades aplicáveis permitem planejar seis entidades; as demais continuam bloqueadas por identidade/contrato ou pela decisão Raster, sem serem omitidas.
    - **Entrega local:** catálogo/manifesto, matriz executável das 11 fontes-entidade, histórico de Usuários e cinco fatos, runbook, cenários exclusivamente sintéticos e validator determinístico para estratégias candidatas, tuple de identidade, partições, protocolo <code>T0/Tcut</code>, delta/late data, contagens/digests agregados sanitizados, restart, rollback e proibição de avanço do watermark incremental.
    - **Aceite local:** todos os bindings canônicos são recalculados contra os catálogos existentes; cenários positivos e negativos falham fechados; nenhuma linha declara <code>EXECUTION_READY</code>; nenhuma fixture contém payload, ID, cursor ou dado real; nenhuma rede, banco, SQL, migration, bootstrap, caracterização, relação, publicação, fato ou cutover é executada. O resultado máximo é <code>FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED</code>, sem concluir V2-047 agregada nem qualquer Q-*-02.
    - **Estado/evidência em 06/09/2026:** <code>FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED</code>. O primeiro comando <code>pwsh -NoProfile -File .\scripts\validation\Test-V2047BootstrapPlanningCatalog.ps1</code> terminou vermelho porque <code>docs/catalogos/bootstrap-v2-047/manifesto.json</code> ainda não existia. Após a menor entrega coesa, o mesmo comando passou com 17 linhas, seis entidades elegíveis e 34 casos sintéticos — sete aceitos somente para planejamento bloqueado e 27 recusas, uma para cada reason code fechado. O gate confere ordem determinística, conjunto exato de 16 fontes canônicas, hashes, contratos/identidades, vocabulários, namespace <code>BOOTSTRAP</code> e efeito zero no watermark incremental.
    - **Gates e limites:** os nove validadores ancorados de schema, contratos, identidades, caracterização e portabilidade passaram; <code>Test-PortabilityCatalog.ps1 -VerifyGenerated</code> detectou o drift preexistente de <code>PUB-07</code> após o ADR 0025, e a regeneração mecânica produziu 401 artefatos, 2.437 campos, 75 regras, 16 classes e zero <code>UNCLASSIFIED</code>. O self-test do scanner passou nove casos; a varredura offline passou com 870 candidatos, 869 textos, um binário verificado e zero finding; 12 arquivos alterados/relevantes passaram UTF-8 estrito sem BOM e <code>git diff --check</code> passou. Maven, SQLCMD, gate progressivo e rollback de banco não são aplicáveis porque a fatia altera somente Markdown/JSON/CSV/PowerShell documental e não executa Java, SQL, migration ou banco. Nenhuma rede, API, <code>.env</code>, credencial, payload, cursor, ID/dado real, produção, bootstrap, Q-*-01/Q-*-02, relação, paridade, sweep, publicação, fato, release, deploy, cutover, commit ou push foi usada.
  - **Entrega:** matriz por entidade/fato/histórico escolhendo reextração ESL, migração validada read-only do legado ou arquivo read-only; horizonte disponível, partições, preservação de source/canonical IDs, transformações, contagens/hashes sanitizados e rollback. Migração do legado define ponto de corte <code>T0</code>, usa snapshot isolation/backup/export consistente e ordem referencial, captura o delta e late data após <code>T0</code> e o reaplica até <code>Tcut</code>. Acesso ao banco legado exige autorização, principal read-only, owner, prazo e remoção.
  - **Aceite:** histórico da entidade reconciliado antes do cutover e seguido pela paridade core V2-012b; nenhuma linha é copiada sem contrato/linhagem; snapshot consistente + replay de <code>(T0,Tcut]</code> fecham chaves/contagens sem lacuna ou duplicação; partições concluídas usam namespace <code>bootstrap</code>, inclusive fora de ordem, sem elevar watermark incremental pelo maior período nem encobrir lacunas; Manifestos necessários a V2-046a estão em <code>core.manifestos</code>; fonte-ponte expira e não vira dependência permanente.

- [ ] **V2-046 — Implementar relações e backfill referencial de Coletas sem ciclo.**
  - **Integração funcional local em14/09/2026 — V2-046:** AnalyticExpansionCapture recebe relações explícitas; DeclaredExpansionRelations conserva tipos/papéis/cardinalidade/revisão. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Crosswalk MC/CF e demais relações, cardinalidade/vigência/papéis reais e política de órfãos. Releases de relações/referências nominais. Paridade das relações sob rekey, reaparecimento e histórico. Nenhum checkbox ou numerador alterado.
  - **Isolamento comum:** execuções usam namespaces <code>backfill</code>/<code>replay</code>, não multiplicam grão e jamais avançam o watermark incremental. Não consultar silenciosamente o banco legado em runtime.
  - [ ] **V2-046a — Resolver Manifesto→Coleta e o backlog de Coletas.**
    - **Depende de:** V2-010, ingestão canônica V2-026, bootstrap de Manifestos V2-047, V2-020, V2-021 e V2-022; usa V2-025a/V2-009a para Coletas.
    - **Entrega:** provar cardinalidade de MAN-03 a partir das chaves preservadas; stagear fontes/sidecars separadamente e materializar Manifesto→Coleta por join/window/constraints SQL, preservando aliases/componentes dos itens para a etapa seguinte; calcular em <code>core.manifestos</code> o órfão mais antigo; lookback/expansão máximos, backlog durável, pré-backfill e hidratação cirúrgica pós-extração com tentativas/intervalo limitados e auditoria separada.
    - **Aceite:** alvo exato, cardinalidade, idempotência e SLA; candidatos 5/7/30 dias, chunk 400, lookahead 1 e limiar/reset de duas falhas são medidos e calibrados, nunca herdados por default; não contamina a janela principal/checkpoint; órfão persistente é visível/quarantenado; falha bloqueia/degrada Fretes conforme matriz.
  - [ ] **V2-046b — Resolver Coleta→Frete após a vertical de Fretes.**
    - **Depende para implementar de:** V2-046a e V2-011 em <code>IMPLEMENTADA_EM_SHADOW</code>. **Para fechar:** V2-012a de Coletas/Fretes e V2-047 de ambas quando houver bootstrap.
    - **Ordem:** reexecutar o crosswalk após cada bootstrap; somente depois disso pode começar a paridade core relacional.
    - **Entrega:** cruzar em SQL <code>manifest_item_pick_id</code>/<code>pick_items_ids</code>/filhos tipados de Coletas com <code>fretes.pick_item_id</code>; provar 1:1, 1:N e N:N, nulo, colisão e órfão; materializar crosswalk canônico Coleta/item→Frete com presença/cardinalidade/conflito e recomputar backlog sem reextrair nem multiplicar raízes. Java transporta/stageia; nunca monta <code>Map</code> da fonte para associar no loop.
    - **Aceite:** histórico e delta cobertos, zero associação heurística silenciosa, conflito/órfão medidos e replay idempotente; COL-07 fecha integralmente e V2-036 só consome o crosswalk publicado.

- [x] **V2-010 — Construir a vertical de Coletas 6908 em sombra.**
  - **Integração funcional local em14/09/2026 — V2-010:** COL: LocalProfileArtifact → LocalCharacterizationProfile → mapper atual, consumidos por Main local-profile com dois arquivos e saída determinística. A captura de apoio no cenário completo permanece PACKAGED_ANALYTIC_SUPPORT_V1 explícita; relações/projeções existentes continuam exercitadas pelos19contratos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** COL-07 nominal: relação 6908/temporal/Manifesto, identidade e garantia de snapshot completo. Fonte e release reais autorizados. Paridade, datas/fuso e ausência real pelo snapshot ratificado. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-009a, V2-018, V2-019, V2-020, V2-021, V2-022a, V2-023, V2-025a, V2-033, a fundação offline concluída de V2-035a, V2-043 e V2-044. Conteúdo produtivo de referências continua bloqueando publicação/cutover, não implementação sintética em sombra.
  - [x] **V2-010a — Decidir domínio, presença, frescor, status e schema lógico de Coletas.** O ADR 0023 e o catálogo <code>docs/catalogos/coletas-v2-010/decisao-v01.json</code> congelam COL-01–COL-11 para V02: source key <code>id</code> escopada e <code>sequence_code</code> somente alias; presença tri-state; frescor tipado com fallback; terminalidade; catálogo <code>coletas-status-v1</code>; ausência bloqueada até V2-013; relações apenas candidatas; <code>request_date</code> como partição e <code>scopes.by_updated_at</code> sem watermark; CEP antes de cidade/UF e guardrails históricos sem default. O schema é lógico, sem migration, tabela, view, grant, fonte, credencial, rede, banco, publicação, sweep ou cutover. O script <code>Test-ColetasV2010DecisionCatalog.ps1</code> passou localmente; mapper, staging, promoção, migration e testes de vertical continuam exclusivamente em V02.
  - **Fechamento de V02 em 04/09/2026 17:20:44 -03:00:** <code>IMPLEMENTADA_EM_SHADOW</code>. Foram implementados o mapper Data Export 6908 com presença tri-state, domínio de status/frescor, lote de staging, gateways JDBC de staging/promoção set-based, a migration <code>V010__create_coletas_shadow_vertical.sql</code>, constraints/allowlist mínima, manifesto, validações e exercícios rollback-only. No alvo previamente confirmado <code>localhost/ETL_SISTEMA_V2_SHADOW</code>, o baseline V001–V010 e o exercício V02 concluíram com <code>ROLLBACK</code>; a verificação posterior confirmou <code>ctl.execution_attempt</code>, <code>stg.coleta_record</code> e <code>core.coleta</code> ausentes e apenas o schema histórico <code>shadow</code>. Sob Temurin Java 17.0.20.1, <code>clean verify</code> executou a suíte com 563 testes, zero falhas, zero erros e zero ignorados; Enforcer, Spotless, Checkstyle e JaCoCo geraram seus artefatos. Passaram também <code>Test-SchemaFoundationManifest.ps1</code>, <code>Test-ColetasV2010DecisionCatalog.ps1</code>, <code>Test-ColetasV2010ShadowVertical.ps1</code> e o scanner offline de segredos (733 candidatos, 732 textos, um binário, zero findings). O JDK 17 foi configurado somente neste workspace em <code>.vscode/settings.json</code> e nas tasks Maven; as variáveis globais dos demais projetos ficaram no Java 25. Não foram criados objetos <code>pub</code>, sweep, desativação, relacionamento canônico Manifesto→Coleta ou Coleta→Frete, fonte/rede, credencial, deploy, cutover, commit ou push. Primeira/segunda ausência e reaparecimento permanecem <code>BLOCKED_NO_COMPLETENESS_PROOF/NOT_EVALUATED</code>, sem alterar <code>active</code>. Sem fonte/oráculo autorizado, a evidência continua sintética e não prova completude, paridade, publicação ou cutover. Rollback de migration: reexecutar a transação de <code>database/validation/039_exercise_coletas_shadow_vertical_rollback.sql</code> ou reverter V010 antes de qualquer aplicação futura.
  - **Entrega:** campo-presença, domínio, staging, partições <code>request_date</code>, overlap complementar <code>by_updated_at</code>, dedupe/promoção, status, preservação dos candidatos relacionais e DQ da entidade; Manifesto→Coleta fica em V2-046a e Coleta→Frete em V2-046b.
  - **Aceite de implementação:** COL-01–COL-06, preservação/proveniência de COL-07 e COL-08–COL-11; incompletude não reconcilia; <code>done</code> publica “Coletada”; primeira/segunda ausência e reativação testadas isoladamente; fatia V2-017a sem campo não classificado, replay idempotente e harness pronto para V2-012a; marco <code>IMPLEMENTADA_EM_SHADOW</code>. Sweep fica fora desta entrega e só pode ser habilitado por V2-013; COL-07 só fecha integralmente em V2-046b.

- [x] **V2-011 — Construir a vertical de Fretes 6389 em sombra.** <code>IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING</code> no Bloco 45/V09. A implementação base está validada somente no shadow local; nenhuma relação ou paridade foi antecipada.
  - **Integração funcional local em14/09/2026 — V2-011:** FRE: LocalProfileArtifact → LocalCharacterizationProfile → mapper atual, consumidos por Main local-profile com dois arquivos e saída determinística. A captura de apoio no cenário completo permanece PACKAGED_ANALYTIC_SUPPORT_V1 explícita; relações/projeções existentes continuam exercitadas pelos19contratos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Crosswalk FRE/COL, aliases e precedência financeira/fiscal/referências reais; updated_at não ratificado. 6389/sidecar autorizado e releases nominais. Paridade financeira/performance e origem do frescor. Nenhum checkbox ou numerador alterado.
  - **Divisão honesta de dependências:** V2-011a/decisão local depende somente dos contratos e decisões locais já fechados — V2-009a, V2-010, V2-022a, V2-025a e a portabilidade canônica. A implementação base V09 dependeu de V2-011a, V2-018, V2-019, V2-020, V2-021, V2-023, da fundação offline concluída de V2-035a, V2-043 e V2-044. Política canônica: <code>V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW</code>; <code>V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES</code>. V2-046b continua dona exclusiva do crosswalk final. Essa separação não declara relação pronta nem permite que a implementação base materialize join/FK/crosswalk.
  - [x] **V2-011a — Decidir localmente FRE-01–FRE-07 sem implementação nem relação.** <code>COMPLETE_LOCAL_DECISION_ONLY</code> no Bloco 44/V08. O ADR 0027, <code>docs/catalogos/fretes-v2-011/decisao-v01.json</code>, 14 casos sintéticos e <code>Test-FretesV2011DecisionCatalog.ps1</code> congelam identidade escopada por <code>/id</code>; alias <code>/corporation_sequence_number</code>; presença tri-state; precedência única de frescor; replay/empate/out-of-order; terminalidade; performance oficial 6389 antes de <code>/finished_at</code>; bruto, parse state e proveniência; sidecars GraphQL em staging independente/limitado; <code>pickItemId</code> somente candidato; financeiro sem inferência; partição <code>freights.service_at</code>; overlap versionado; ausência incremental sem efeito e prune desligado. O RED falhou pela ausência do catálogo e o GREEN passou com 7 regras, 14 casos e 9 mutações negativas. Não há mapper, migration, SQL, runtime, relação, caracterização, bootstrap, publicação ou cutover; o pai V2-011 e a implementação V09 permanecem abertos.
  - **Entrega:** stagings independentes para a entidade, performance 6389 e sidecars; partições <code>service_at</code>, overlap por partição, presença/frescor, performance com proveniência, CT-e/finalizações, preservação dos candidatos Coleta–Frete e financeiro. Associação da performance/minuta usa join/window SQL; o crosswalk final pertence a V2-046b.
  - **Aceite de implementação:** FRE-01–FRE-07; a precedência <code>cte_created_at → cte_issued_at → criado_em → servico_em</code> é única em dedupe/promoção e tem testes de nulo/empate/out-of-order; <code>updated_at</code> não vira frescor/watermark enquanto <code>UNVERIFIED</code>; timestamp oficial 6389 precede fallback de performance, sem aceitar data ambígua por heurística; dependência de Coletas falha de forma estruturada; nenhum sidecar inteiro em <code>Map</code>; a fatia 6389 aplicável de V2-017a recebe enforcement físico sem fechar o agregado indevidamente; prune desligado, replay idempotente e harness pronto para V2-012a; marco <code>IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING</code>.
  - **Fechamento em 06/09/2026 17:54 -03:00:** o RED real de <code>Test-FretesV2011ShadowVertical.ps1</code> recusou a ausência do mapper com <code>FRETES_SHADOW_VERTICAL_MISSING</code>. O GREEN implementa domínio/mapper/lotes limitados a 100, gateways JDBC separados, V013, manifesto/fingerprint, validators 044/045, runner fail-closed e concorrência. O contrato externo continua limitado aos sete paths locais comprovados; frescor/status/CT-e/finalizações permanecem marcados <code>SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE</code>. A prova física ocorreu somente em <code>localhost/ETL_SISTEMA_V2_SHADOW</code>, com Windows auth, transações revertidas e estado final <code>0|0|0</code>; o <code>clean verify</code> offline sob JDK 17 passou com 684 testes, zero falhas/erros e um skip esperado. V2-046a/V2-046b continuam abertas e V2-046b permanece dona exclusiva do crosswalk final.

- [ ] **V2-012 — Automatizar caracterização e paridade por entidade.**
  - **Integração funcional local em14/09/2026 — V2-012:** LocalCharacterizationProfile/ExpansionCharacterizer/RasterArtifact no JAR; LocalArtifactScenario + QualificationComparator + LocalFactOracle com oráculos independentes externos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Oráculos independentes aprovados com origem/release/schema/janela/escopo/chaves/grão/tolerância/ordem e critérios por entidade/saída. Canais de dados reais autorizados e alvo de comparação. V2-012a/b/c reais por entidade/saída com aceites do contrato original. Nenhum checkbox ou numerador alterado.
  - **Caracterização sintética em13/09/2026: TESTADO_NA_CAMADA_PACOTE_E_JDBC.** Harness existente ligado aos19contratos/673colunas/971metadados,oráculos tipados/linhagem e variantes não nulas/nulas. SQL04 positiva por confirmação independente e reaparecimento;VARIANTS/ABSENCE finais e verify03 passaram. Matriz final por coluna liga regra/origem/exemplo/comparador/casos e evidência. Fixture não fecha V2-012b/c;nenhum incremento adicional. Checkpoint0141.
  - **Entrega comum:** harness sanitizado e parametrizado por entidade/fato/contrato, com relatório por SHA e três gates distintas para não misturar caracterização pré-bootstrap, paridade core e paridade de saída downstream.
  - [x] **V2-012/FUNDACAO_LOCAL — Construir a fundação offline provider-neutral de caracterização.**
    - **Bloco/rota/resultado em 05/09/2026:** Bloco 39, <code>Q-FND-01</code>, <code>FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES</code>. O catálogo <code>docs/catalogos/caracterizacao-v2-012/</code>, o ADR 0026, o runbook Sol e <code>Test-V2012CharacterizationFoundation.ps1</code> congelam quatro perfis isolados: Coletas/6908, Manifestos/6399, Cotações/6906 e Usuários/<code>graphql-individual</code>, todos <code>PREPARED_NOT_EXECUTED</code>/<code>ORACLE_REQUIRED</code>. Quatro fixtures estritamente sintéticas, com hashes bruto e normalizado fixados, exercitam 166 cenários: 13 estruturas aceitas e 153 recusas fail-closed.
    - **Arquitetura e validação local:** o código existe somente em <code>src/test</code>; adapters in-memory separados para Data Export e GraphQL não alcançam rede, JDBC, runtime ou configuração externa. O loader exige UTF-8/schema fechado, o evaluator preserva tri-state, escopo/chave tipada, raiz/filho/status/temporalidade/ordem/paginação e limites rígidos, e o writer produz receipts sanitizados por substituição atômica somente no target delimitado. <code>mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify</code> passou em Java 17 com 609 testes, zero falhas/erros, um skip esperado de symlink no Windows e cobertura atendida; o teste de hardlink executou. O validator da fundação e os 12 gates correlatos de contratos, identidade, decisões e verticais passaram.
    - **Limites e próximos gates:** nenhuma caracterização de fornecedor, snapshot, completude ou paridade real foi executada ou alegada. Q-USR-01, Q-COL-01, Q-MAN-01 e Q-COT-01 continuam abertas até autorização e evidência de seus oráculos; Q-MAN-01 permanece <code>EXTERNAL_HOLD</code>. Não autoriza bootstrap, V2-047, V2-012b, V2-013, V2-050, V2-038, R01/R02, sweep, publicação, E2E ou cutover. V2-012 e V2-012a/b/c permanecem abertas; D03/D04 permanecem bloqueadas e sem runbook Terra. Após o fechamento existe zero rota <code>AGORA</code>, e o Bloco 40 fica não atribuído.
  - [x] **V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE — Estender a fundação test-only somente para Fretes 6389 e Localização 8656.**
    - **Rota/bloco/resultado:** <code>Q-FND-02</code>, Bloco 48, <code>FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES</code>. Dependeu das bases shadow V09/Bloco 45 e V10/Bloco 47. Os 43 arquivos históricos de Q-FND-01 permaneceram byte a byte inalterados e o lock agregado foi comprovado como <code>ab99feb5e413deb44bf0dfc26f737453fa802cd6b293805ba82aaa1c56b9029a</code>.
    - **Entrega:** o harness test-only recebeu exatamente dois perfis de entidade independentes <code>PREPARED_NOT_EXECUTED</code> e três canais isolados: Fretes/Data Export 6389, Fretes/GraphQL sidecar estritamente observacional e Localização/Data Export 8656. Fixtures exclusivamente sintéticas, limites, bindings/fingerprints e 48 mutações executadas cobrem os contratos/identidades e decisões FRE-01–FRE-07/LOC-01–LOC-07; todos os gates permanecem <code>ORACLE_REQUIRED</code> e <code>providerEvidence=NOT_EXECUTED</code>.
    - **Aceite e limites:** o RED dedicado registrou <code>Q_FND_02_MANIFEST_MISSING</code> e o RED Java registrou tipos ausentes. O GREEN provou isolamento dos canais, schema fechado sem coerção, presença por path, identidade, frescor, status, limites, bindings criptográficos e receipts sanitizados/atômicos; serialização com LF explícito produziu o mesmo hash literal cross-platform <code>327ca45986ab6ebe434f56b641053faa8a68f88d946636cdea22400321fd2dd4</code>. A suíte focal registrou 66 testes, com 65 executados e um skip condicional; o <code>clean verify</code> offline registrou 780 testes, zero falhas/erros e dois skips esperados. Fecha somente esta fundação; V2-012/V2-012a/b/c, Q-FRE-01, Q-LOC-01, bootstrap, relações, paridade, V2-013, V2-050, publicação e cutover continuam abertos. Não houve rede, fonte, <code>.env</code>, segredo ou banco.
  - [ ] **V2-012a — Caracterização inicial antes do bootstrap.**
    - **Depende, para a entidade instanciada, de:** subcontrato V2-025a/V2-025b/V2-025c aplicável, identidade V2-009 aplicável, vertical em <code>IMPLEMENTADA_EM_SHADOW</code> e fonte-oráculo.
    - **Aceite:** janela limitada representativa compara campos, tipos, presença, chaves, grão, status, timestamps/timezone, expansão física, contagens e relações já disponíveis; divergência é classificada. Comparação em memória só é permitida nesta sonda test-only com limites rígidos de linhas/bytes. Autoriza bootstrap controlado, nunca sweep/cutover.
  - [ ] **V2-012b — Paridade core após bootstrap/dual-run.**
    - **Depende de:** V2-012a, V2-047 quando houver histórico a carregar, V2-046a para Manifesto→Coleta e V2-046b quando o escopo incluir Coleta→Frete; não depende de fato ou view downstream.
    - **Aceite:** em janelas fechadas e repetidas, stageia <code>(execution/source/entity/key_hash,row_hash,métricas)</code> e compara IDs/conjuntos de chave, volumes, nulos, status, datas, relações, órfãos, financeiro e somas por <code>EXCEPT/NOT EXISTS/FULL OUTER JOIN/GROUP BY/SUM</code> no SQL; Java recebe contagens e <code>TOP(N)</code> sanitizado. Toda divergência é corrigida ou aceita nominalmente. O relatório não contém dado real no Git e esta gate habilita V2-013 e V2-036.
  - [ ] **V2-012c — Paridade de fatos e contratos publicados.**
    - **Depende por saída de:** V2-012b de todas as entradas, fato V2-036 quando aplicável e contrato V2-037 correspondente.
    - **Aceite:** compara set-based no SQL grão/chaves, cardinalidade, colunas/tipos/nullability, filtros, status, datas, relações, receita, CT-e, contas, somas e labels da saída com o oráculo aprovado; divergência é corrigida ou aceita nominalmente. Esta gate habilita V2-038/V2-014 para fato/view e não retroalimenta V2-036.

- [ ] **V2-013 — Implementar Sweep and Prune seguro por entidade.**
  - **Integração funcional local em14/09/2026 — V2-013:** CollectionSweepInput e CollectionSweepArtifact vinculam quatro snapshots à execução; LocalAnalyticCollectionSweep consome SweepResponsibilityPlanner e imprime 33 responsabilidades. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Política de presença/ausência por cada uma das 33 responsabilidades; bindings pai/filho e completude ratificados. Snapshot completo, scope, lease, release e autorização de entidade. Sweep nominal/reativação e preview/apply por responsabilidade. Nenhum checkbox ou numerador alterado.
  - **Aplicação sintética de Coletas em13/09/2026: TESTADO_NA_CAMADA_LOCAL.** O kernel existente agora alimenta aplicação SQL opt-in rollback-only: primeira ausência candidata, segunda observação independente confirma, reaparecimento limpa. Reutilização de evidência, snapshot parcial/vazio anômalo/nulo/receipt divergente e full de fato não desativam nem avançam checkpoint. SQL03/04 consomem esse estado. Isso aprofunda a unidade de construção já contada; não habilita sweep real, Raster ou outras entidades. Evidências: [matriz A–N](docs/catalogos/macrobloco-analitico/MATRIZ-A-N.md), [verificação](docs/catalogos/macrobloco-analitico/verification-summary.json), [colunas/linhagem](docs/catalogos/macrobloco-analitico/matriz-colunas-final.json). O checkbox de aceite agregado permanece aberto.
  - [x] **V2-013/FUNDACAO_KERNEL_LOCAL — Construir o kernel local fail-closed de classificação e planejamento de Sweep and Prune.**
    - **Rota/bloco/resultado:** <code>Q-SWP-FND-01</code>, Bloco 49, <code>FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED</code>. A fatia implementa somente o kernel puro/provider-neutral que avalia uma responsabilidade por chamada, sem coleção de chaves, callback, IO, SQL ou capability de mutação. A decisão positiva única é <code>PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY</code>, restrita a uma raiz sintética local; ela habilita zero entidades.
    - **Catálogo e invariantes:** o ADR 0030, o catálogo <code>docs/catalogos/sweep-v2-013/</code>, o gerador e o validator dedicados congelam 33 responsabilidades nas 11 famílias canônicas, com quatro estados de aplicabilidade, nove tipos de responsabilidade, zero <code>ENABLED</code> e zero <code>PROVEN_COMPLETE</code>. Policy, scope, snapshot e quatro provas O(1) usam bindings SHA-256 canônicos; traversal/absence exigem ordinais e ocorrências/runs distintos e coerentes. Terminalidade/página vazia nunca provam completude. Apenas <code>ROOT</code> pode satisfazer o happy path sintético; <code>CHILD</code> requer planner nominal pai→filho futuro e todos os demais tipos são inelegíveis.
    - **RED/GREEN e limites:** o RED estático terminou com <code>V2_013_SWEEP_FOUNDATION_MANIFEST_MISSING</code> e o RED Java comprovou os seis tipos ausentes antes da implementação. O GREEN executou 53 casos catalogados — um preview sintético positivo sem apply e 52 mutantes fail-closed —, 129 testes focados e 893 testes no <code>clean verify</code>, sem falha/erro e com dois skips esperados; Enforcer, Spotless, Checkstyle, JaCoCo, builder, validator, scanner e auditoria adversarial passaram. V2-013 pai e todas as rotas Q-*-04 permanecem abertas; V2-034a/V2-034b, V2-012b e prova nominal de snapshot/completude continuam obrigatórios. Não houve fonte, rede, banco, DDL/DML, migration, anti-join, persistência, desativação, delete/prune, relação, publicação, runtime wiring ou cutover.
  - **Depende de:** V2-020, vertical persistida, V2-012b aceita para a entidade e contrato de snapshot completo.
  - **Entrega:** matriz explícita para todas as raízes e filhos de Coletas, Fretes, Manifestos, Cotações, Localização, Contas a Pagar, Faturas por Cliente, Inventário, Sinistros, Usuários e Raster condicional, classificando cada linha como <code>ENABLED</code>, <code>DISABLED</code>, <code>BLOCKED</code> ou <code>NOT_APPLICABLE</code>. Cada linha registra chave de presença, prova de completude/snapshot, propagação pai→filho, confirmações, guardrails e motivo/evidência; presença é inserida página a página em <code>recon/stg</code>; snapshot terminal alimenta anti-join SQL, confirmação/liberação/reativação/soft-delete set-based.
  - **Aceite:** timeout, página faltante, cap, fonte vazia anômala, invalids acima da tolerância ou queda forte de volume bloqueiam mudança; somente soft delete; sweep nunca compartilha operação/transação de upsert; execução incompleta não entra no anti-join; JVM não busca todas as chaves core nem monta <code>IN (...)</code> dinâmico; ativação real só após gate nominal. Mesmo entidade sem prune possui linha de aplicabilidade aceita antes do cutover.

### Fase 3 — Demais verticais

Para V2-026–V2-032, <code>IMPLEMENTADA_EM_SHADOW</code> também exige fechar a fatia da entidade em V2-017a: todos os campos de metadata/payload/DTO, 305 colunas físicas V1 dessas sete tabelas, derivados e filhos classificados, com zero <code>UNCLASSIFIED</code> e nenhum <code>UNRESOLVED</code> usado na publicação.

- [x] **V2-026 — Implementar ingestão canônica de Manifestos 6399.**
  - **Integração funcional local em14/09/2026 — V2-026:** MAN: LocalProfileArtifact → LocalCharacterizationProfile → mapper atual, consumidos por Main local-profile com dois arquivos e saída determinística. A captura de apoio no cenário completo permanece PACKAGED_ANALYTIC_SUPPORT_V1 explícita; relações/projeções existentes continuam exercitadas pelos19contratos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** 6399: identidade/crosswalk raiz/pick/MDF-e, referências de frota/filial e competência nominais. Release nominal 6399 e referências vigentes. Paridade temporal/relacional e resolução de órfãos reais. Nenhum checkbox ou numerador alterado.
  - **Depende de:** a fatia 6399 de V2-009b, V2-018, V2-019, V2-020, V2-021, V2-022a, V2-023, o contrato 6399 de V2-025b, V2-043 e V2-044.
  - **Entrega:** raiz Manifesto + filhos pick/MDF-e em staging/core, MAN-01/MAN-02/MAN-04/MAN-07, frescor unificado, competência e reducers por campo; preservar com presença/proveniência as chaves necessárias a MAN-03. A resolução operacional MAN-03 fica em V2-046a; referências em V2-035a e MAN-05/MAN-06 no fato V2-036.
  - [x] **V2-026a — Decisão de raiz, filhos, frescor e reducers:** <code>COMPLETE_LOCAL_DECISION_ONLY</code> no Bloco 36. O catálogo <code>docs/catalogos/manifestos-v2-026/decisao-v03.json</code> congela MAN-01/MAN-02/MAN-04/MAN-07 sem DDL ou runtime. Toda folha preserva bruto, tipado, path, parse state, proveniência e presença <code>ABSENT/NULL/VALUE</code>. Raiz, pick e MDF-e são deduplicados/reduzidos separadamente na coorte do maior frescor <code>finished_at → closed_at → departured_at → created_at</code>; timestamps são comparados em UTC e valor presente inválido ou ausência total de frescor bloqueia promoção. Os reducers explícitos rodam antes da classificação do empate, resolvem repetições idênticas, precedência de lifecycle <code>/status</code> conhecido e complemento MAN-07 <code>NULL/ABSENT + VALUE</code> único; zero é valor real. Qualquer conflito residual no mesmo frescor é quarentena estável, sem chegada/hash/ordem, <code>SUM</code> ou <code>MAX</code>. O filho MDF-e usa chave como identidade e número como atributo preso ao mesmo registro físico; <code>mdfe_status</code> é escalar da raiz replicado e presença isolada não cria filho, não vira lifecycle do filho nem dispara quarentena. Competência usa saída e somente então criação. Candidatos Manifesto→Coleta são anexados/preservados para V2-046a sem FK, lookup, órfão zerado ou relação materializada. Ausência de raiz/filho permanece sem sweep, delete, desativação ou publicação. Esta folha não cria migration, schema, tabela, procedure, view, fato, grant, integração ou código de execução; por isso o pai V2-026 permanece aberto e V04 é o próximo gate.
  - **Aceite de implementação:** identidade candidata sem colisão no escopo, expansão e chaves relacionais preservadas, oito métricas de MAN-07 sem perda/multiplicação e conflito/zero testados; os 27 caminhos textuais legados têm limites explícitos — 26 hoje truncados e <code>contract_number</code> hoje sem guarda — e fixtures de limite/comentário/documento/emoji sem truncamento/vazamento; empate/out-of-order, replay e harness prontos para V2-012a; ausência desligada até V2-013; marco <code>IMPLEMENTADA_EM_SHADOW</code> sem alegar relação já resolvida.
  - **Fechamento em 05/09/2026 12:10 -03:00:** <code>IMPLEMENTADA_EM_SHADOW_ROLLBACK_ONLY</code>. V012 adiciona somente <code>stg.manifesto_observation</code>, candidatos reduzidos/Pick/MDF-e, <code>core.manifesto</code> e seus filhos sob FK de ownership, evidências <code>recon</code> append-only e quatro procedures fechadas; não há FK/lookup para Coletas, <code>pub</code>, sweep ou desativação. V043 comprovou em rollback as duas expansões físicas, replay, reducer/candidato, overflow UTF-16, preparação sem promoção e ausência sem efeito destrutivo; o lock do namespace comum foi provado em duas sessões com contenção, isolamento por environment e readquisição após rollback. Permanecem explicitamente fora: Q-MAN-01/oráculo externo, bootstrap/paridade, MAN-03/V2-046a, sweep, fatos, contratos consumidores, operação e cutover.

- [x] **V2-027 — Implementar Cotações 6906.**
  - **Integração funcional local em14/09/2026 — V2-027:** COT: LocalProfileArtifact → LocalCharacterizationProfile → mapper atual, consumidos por Main local-profile com dois arquivos e saída determinística. A captura de apoio no cenário completo permanece PACKAGED_ANALYTIC_SUPPORT_V1 explícita; relações/projeções existentes continuam exercitadas pelos19contratos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** 6906: contrato nominal e tabela tarifária com vigência/owner/rotas. Release tarifário e fonte autorizados. Paridade de cotação/conversão e tarifa por rota/vigência. Nenhum checkbox ou numerador alterado.
  - **Depende de:** a fatia 6906 de V2-009b, V2-018, V2-019, V2-020, V2-021, V2-022a, V2-023, o contrato 6906 de V2-025b, a fundação offline concluída de V2-035a, V2-043 e V2-044.
  - **Fechamento em 04/09/2026 22:19:22 -03:00:** <code>IMPLEMENTADA_EM_SHADOW</code>, restrita ao harness local e rollback-only. COT-01 preserva <code>ABSENT/NULL/VALUE</code>, usa <code>/sequence_code</code> inteiro type-tagged sob <code>source_instance</code>/<code>tenant_scope</code> explícitos, mantém <code>canonical_id BIGINT IDENTITY</code> e rejeita <code>DEFAULT/GLOBAL/SINGLETON</code>. Dedupe e promoção usam a mesma precedência <code>nfse_issued_at → cte_issued_at → requested_at</code>; empate com conteúdo divergente entra em quarentena <code>EQUAL_FRESHNESS_CONFLICT</code>, nunca por ordem de chegada. COT-02 usa exclusivamente <code>ref.tarifa_rota_uf</code>, com release <code>QUOTE_TARIFF</code> explícito, ratificado para <code>SHADOW</code> e não revogado; rota, vigência <code>[valid_from, valid_to_exclusive)</code>, <code>coverage_state</code>, moeda, unidade, arredondamento e <code>DECIMAL(19,4)</code> falham fechados. O V041 exercitou, em transação revertida, tarifa sintética, retry/no-op, out-of-order, replay, duplicata entre páginas, ausência sem sweep e conflito; a prova em duas sessões validou contenção, isolamento por ambiente e reacquisição após rollback. Mapper, batch e gateways possuem testes unitários/contratuais; gateways chamam procedures fechadas e mantêm uma página limitada em voo. Não foi criada publicação, view consumidora, fato, sweep, dispatcher positivo, integração externa ou operação produtiva.

- [x] **V2-028 — Implementar Localização de Cargas 8656.** <code>IMPLEMENTADA_EM_SHADOW</code> no Bloco 47/V10, estritamente no shadow local rollback-only; relação e publicação continuam diferidas.
  - **Integração funcional local em14/09/2026 — V2-028:** LOC: LocalProfileArtifact → LocalCharacterizationProfile → mapper atual, consumidos por Main local-profile com dois arquivos e saída determinística. A captura de apoio no cenário completo permanece PACKAGED_ANALYTIC_SUPPORT_V1 explícita; relações/projeções existentes continuam exercitadas pelos19contratos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** 8656: 17 paths ratificados localmente, mas origem do frescor, crosswalk minuta/Frete e filial destino nominais faltam. Release 8656 e vínculos reais autorizados. Caracterização/paridade/backfill e compatibilidade do léxico numérico. Nenhum checkbox ou numerador alterado.
  - **Depende de:** a fatia 8656 de V2-009b, V2-011, V2-018, V2-019, V2-020, V2-021, V2-022a, V2-023, o contrato 8656 de V2-025b, a fundação offline concluída de V2-035a, V2-043 e V2-044.
  - [x] **V2-028a — Decisão local LOC-01–LOC-07 concluída sem implementação.** <code>COMPLETE_LOCAL_DECISION_ONLY</code>; Rota <code>V10A</code>, Bloco 46, GPT-5.6 Sol Ultra. O ADR 0028, catálogo fechado, README, runbook e 24 casos exclusivamente sintéticos fixam identidade escopada, presença, volume/fallback, parsing numérico, frescor único por <code>service_at</code>, status e campo legado sem fonte. O validator fail-closed registrou RED <code>LOCALIZACAO_DECISION_CATALOG_MISSING</code> e GREEN com 7 regras/24 casos/13 mutações. Não criou mapper, Java produtivo, migration, SQL, banco, relação, caracterização, bootstrap, publicação, sweep ou cutover; o pai V2-028 continua aberto e a execução pertence exclusivamente a V10/Bloco 47.
  - **Aceite de implementação:** LOC-01–LOC-07; unicidade/estabilidade de <code>corporation_sequence_number</code>; alto volume streaming/staging; <code>status_branch_nickname</code> sem fonte não recebe fallback inventado; nulo/out-of-order, status desconhecido e conversão inválida em quarantine; índices medidos, harness V2-012a e marco <code>IMPLEMENTADA_EM_SHADOW</code>.
  - **Fechamento em 06/09/2026 19:55 -03:00:** o RED Java recusou os tipos produtivos ausentes e o validator estático recusou mapper/migration/manifesto ausentes com <code>LOCALIZACAO_CARGAS_SHADOW_VERTICAL_MISSING</code>. O GREEN implementa boundary deny-all, domínio/mapper dos 17 paths, lotes até 100, staging/promoção JDBC set-based, V014, manifesto/fingerprint, SQL 046/047, runner e concorrência. O parser preserva léxico numérico até 8.192 caracteres, usa <code>BigDecimal</code> exato/plano e quarentena o mutante de 5.001; acima do teto falha fechado. A prova física ocorreu somente em <code>localhost/ETL_SISTEMA_V2_SHADOW</code>, com Windows auth, baseline V001–V014, dados sintéticos, transações revertidas, contenção/isolamento/reacquisição e estado final <code>0|0|0</code>. O <code>clean verify</code> offline sob JDK 17 passou com 714 testes, zero falhas/erros e um skip esperado. Não houve fonte/rede, relação com Fretes, <code>pub</code>, sweep, bootstrap, paridade ou cutover; <code>FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION</code> permanece literal.

- [ ] **V2-029 — Implementar Contas a Pagar 8636.**
  - **Integração funcional local em14/09/2026 — V2-029:** CAP8636/28: ExpansionArtifact/ExpansionCaptureSource → LocalExpansionRuntime → JDBC e seleção Main/ARTIFACT; duas entradas independentes, replay, duplicata, tri-state/frescor/reducers. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** CAP8636: tradução de raiz/parcela/rateio, filtro issue_date+created_at e relação conta/carteira nominal. Release/identidade CAP e referências financeiras ratificados. Paridade real do mesmo conjunto filtrado e cardinalidade bancária. Nenhum checkbox ou numerador alterado.
  - **Construção local V2-029 em 12/09/2026:** CONSTRUÍDO_LOCALMENTE_VERIFICADO. CAP: 28 campos, raiz/parcela/rateio explícitos, captura/aplicação e consulta SQL/JDBC. Integra o cenário único por bindings, referências, recomposição e sessão rollback-only. Evidências: [matriz A–N](docs/catalogos/macrobloco-expansao/MATRIZ-A-N.md), [contrato e campos](docs/catalogos/macrobloco-expansao/CONTRATO.md), campanhas físicas do macrobloco e checkpoint0086. **ACEITE_REAL_PENDENTE:** Identidade real de raiz/parcela/rateio, cardinalidade, tipos pendentes e oráculo independente. O checkbox agregado conserva seu requisito original.
  - **Depende de:** a fatia 8636 de V2-009b, V2-018, V2-019, V2-020, V2-021, V2-022a, V2-023, o contrato 8636 de V2-025b, a fundação offline concluída de V2-035a, V2-043 e V2-044.
  - **Aceite de implementação:** CAP-01–CAP-05; <code>ant_ils_sequence_code</code> validado; travessia e contador/oráculo usam exatamente <code>issue_date+created_at</code>; raiz×parcela×centro de custo/plano contábil e reducers impedem repetir valor por expansão; mesma expressão de frescor em dedupe/promoção; competência/status/valores/catálogos prontos para V2-012a; sem sweep/cutover enquanto a raiz não estiver completa; marco <code>IMPLEMENTADA_EM_SHADOW</code>.

- [ ] **V2-030 — Implementar Faturas por Cliente 4924.**
  - **Integração funcional local em14/09/2026 — V2-030:** FAT4924/53: ExpansionArtifact/ExpansionCaptureSource → LocalExpansionRuntime → JDBC e seleção Main/ARTIFACT; duas entradas independentes, replay, duplicata, tri-state/frescor/reducers. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** FAT4924: título/documento/Frete, precedência fiscal e fonte/identidade da série NFS-e, sem inferir de CT-e. Release FAT/NFS-e e referências financeiras nominais. Paridade fiscal/valores/prazos e cardinalidade reais. Nenhum checkbox ou numerador alterado.
  - **Construção local V2-030 em 12/09/2026:** CONSTRUÍDO_LOCALMENTE_VERIFICADO. Faturas por Cliente: 53 campos, título/documento, política fiscal explícita, relações e consulta. Integra o cenário único por bindings, referências, recomposição e sessão rollback-only. Evidências: [matriz A–N](docs/catalogos/macrobloco-expansao/MATRIZ-A-N.md), [contrato e campos](docs/catalogos/macrobloco-expansao/CONTRATO.md), campanhas físicas do macrobloco e checkpoint0086. **ACEITE_REAL_PENDENTE:** Correspondência linha/título/documento/Frete e precedência fiscal nominal; série NFS-e sem fonte. O checkbox agregado conserva seu requisito original.
  - **Depende de:** a fatia 4924 de V2-009b, V2-011, V2-018, V2-019, V2-020, V2-021, V2-022a, V2-023, o contrato 4924 de V2-025b, a fundação offline concluída de V2-035a, V2-043 e V2-044.
  - **Entrega obrigatória inicial:** provar source ID ↔ título ↔ CT-e/NFS-e ↔ fretes antes de criar a promoção final.
  - **Aceite de implementação:** FAT-01–FAT-07; <code>4924.id</code> como source key somente após V2-009b e <code>unique_id</code> proibido sem contrato; <code>serie_nfse</code> sem fonte não é inventada; mesma expressão de frescor; alias/rekey/cardinalidade; arrays invoice/order viram filhos tipados; NFS-e/CT-e/CNPJ/status/catálogos; replay e harness V2-012a; sweep desligado até V2-013; marco <code>IMPLEMENTADA_EM_SHADOW</code>.

- [ ] **V2-031 — Implementar Inventário 10633.**
  - **Integração funcional local em14/09/2026 — V2-031:** INV10633/26: ExpansionArtifact/ExpansionCaptureSource → LocalExpansionRuntime → JDBC e seleção Main/ARTIFACT; duas entradas independentes, replay, duplicata, tri-state/frescor/reducers. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** INV10633: raiz/filho/minuta, ordenação e cardinalidade, estabilidade de componente e completude nominal. Release INV e crosswalks reais. Paridade do comprovante cumulativo/filhos e reordenação real. Nenhum checkbox ou numerador alterado.
  - **Construção local V2-031 em 12/09/2026:** CONSTRUÍDO_LOCALMENTE_VERIFICADO. Inventário: 26 campos, filhos físicos, comprovante cumulativo, relação explícita e consulta. Integra o cenário único por bindings, referências, recomposição e sessão rollback-only. Evidências: [matriz A–N](docs/catalogos/macrobloco-expansao/MATRIZ-A-N.md), [contrato e campos](docs/catalogos/macrobloco-expansao/CONTRATO.md), campanhas físicas do macrobloco e checkpoint0086. **ACEITE_REAL_PENDENTE:** Identidade real da raiz/componentes e cardinalidade/minuta; oráculo independente. O checkbox agregado conserva seu requisito original.
  - **Depende de:** a fatia 10633 de V2-009b, V2-011, V2-018, V2-019, V2-020, V2-021, V2-022a, V2-023, o contrato 10633 de V2-025b, V2-043 e V2-044.
  - **Aceite de implementação:** INV-01–INV-04; hash legado somente como alias; identidade/dedupe no escopo; <code>invoices_mapping</code> modelado em raiz/filhos com reducer que não multiplica valor/peso/volume; comprovante cumulativo; relação por minuta; parsing/quarantine, soft delete/reativação testados; harness V2-012a e marco <code>IMPLEMENTADA_EM_SHADOW</code>.

- [ ] **V2-032 — Implementar Sinistros 6392.**
  - **Integração funcional local em14/09/2026 — V2-032:** SIN6392/44: ExpansionArtifact/ExpansionCaptureSource → LocalExpansionRuntime → JDBC e seleção Main/ARTIFACT; duas entradas independentes, replay, duplicata, tri-state/frescor/reducers. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** SIN6392: identidade sequência/minuta/ocorrência, papéis/arrays e semântica temporal customer_communication_time. Release SIN/crosswalk e regra temporal ratificados. Paridade real de valores/arrays/horário e cardinalidade. Nenhum checkbox ou numerador alterado.
  - **Construção local V2-032 em 12/09/2026:** CONSTRUÍDO_LOCALMENTE_VERIFICADO. Sinistros: 44 campos, sequência/minuta/ocorrência distintas, arrays, valores e consulta. Integra o cenário único por bindings, referências, recomposição e sessão rollback-only. Evidências: [matriz A–N](docs/catalogos/macrobloco-expansao/MATRIZ-A-N.md), [contrato e campos](docs/catalogos/macrobloco-expansao/CONTRATO.md), campanhas físicas do macrobloco e checkpoint0086. **ACEITE_REAL_PENDENTE:** Identidade composta e papéis/cardinalidades; formato de customer_communication_time não ratificado. O checkbox agregado conserva seu requisito original.
  - **Depende de:** a fatia 6392 de V2-009b, V2-011, V2-018, V2-019, V2-020, V2-021, V2-022a, V2-023, o contrato 6392 de V2-025b, V2-043 e V2-044.
  - **Aceite de implementação:** SIN-01/SIN-02; hash legado somente como alias e identidade simplificada somente com prova; raiz×invoice/minuta/ocorrência e reducers financeiros explícitos; horas brutas + tipadas com formato/timezone; fallback de frescor igual no dedupe/promoção; traduções, financeiro e relação prontos para V2-012a; soft delete/reativação testados; marco <code>IMPLEMENTADA_EM_SHADOW</code>.

- [ ] **V2-034 — Decidir e implementar/retirar Raster.**
  - **Integração funcional local em14/09/2026 — V2-034:** RasterArtifact → AnalyticRasterSources → LocalRasterRuntime/parser/ledger/JDBC; repartição de500 e cancelamento sem publicação parcial. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** V2-034a=MANTER aceita. Falta 009c: CodSolicitacao e Ordem com wire type/escopo/estabilidade sob reordenação; completude/frescor nominal. Fonte/credencial Raster específica, release e habilitação condicional ratificados. Paridade viagem/paradas/Transit Time real e identidade de parada estável. Nenhum checkbox ou numerador alterado.
  - **Construção local em13/09/2026: CONSTRUÍDO_LOCALMENTE_VERIFICADO.** Raster condicional captura51declarações, mantém pai/paradas/ledger de completude, histórico, sentinela/DST, limite Transit Time, cap e binding sintético; integra o cenário11entradas/5fatos/19consultas, quatro modos, concorrência e JAR. **ACEITE_REAL_PENDENTE:** identidade/completude/frescor remotos e manifesto consumidor. Evidências: [matriz A–N](docs/catalogos/macrobloco-analitico/MATRIZ-A-N.md), [verificação](docs/catalogos/macrobloco-analitico/verification-summary.json), [colunas/linhagem](docs/catalogos/macrobloco-analitico/matriz-colunas-final.json). O checkbox de aceite agregado permanece aberto.
  - **Direção técnica:** fora da primeira onda e <code>NOT_APPLICABLE</code> por padrão, sem alerta por ciclo quando formalmente desligado.
  - [x] **V2-034a — Decisão:** mapear necessidade, owner, consumidores/impactos e decidir manter/retirar. Se retirar, aceite nominal e plano encerram a tarefa. Se manter, obter autorização específica e disparar V2-025c → V2-009c.
    - **Fechamento em 08/09/2026:** <code>MANTER_RASTER</code>, decisão técnica delegada pelo owner do projeto na mensagem “vc que decide, preciso continuar”. Necessidade: preservar viagens/paradas e Transit Time SM. Consumidores/impactos declarados: <code>vw_raster_sm_transit_time</code>, listas legadas de publicação DEV/PRD e pipeline condicional. Não se afirma uso atual em produção ou aceitante produtivo nominal. ADR 0039 e <code>docs/catalogos/raster-contrato-local/decisao.json</code> fixam decisão, owner da decisão, alternativas, impacto e autorização específica de trabalho local; V2-025c foi formalizada e V2-009c investigada, ainda bloqueada. O módulo fica desabilitado por padrão; V2-034 pai/V2-034b, aplicação física, rede, publicação e corte continuam abertos.
  - [ ] **V2-034b — Implementação condicional:** depende de V2-034a=manter, V2-025c, V2-009c, V2-017, V2-018, V2-020, V2-021, V2-022a e V2-023; aceitar RAS-01–RAS-05, pai/filhos atômicos, credencial fail-fast, timezone/sentinela, terminalidade/cap, zero órfão e replay. Execução operacional e E2E continuam dependentes de V2-022b.

### Fase 4 — Camada analítica pertencente ao ETL

- [ ] **V2-036 — Portar os cinco fatos/materializações.**
  - **Integração por arquivos MAT-01 em14/09/2026:** LocalArtifactScenario materializa MAT-01 pelo repository SQL existente; LocalFactOracle compara seu grão por conjuntos e multiplicidade e recusa missing/extra/duplicate/value. Provas: QualificationArtifactScenarioIT/QualificationFactOracleEdgesIT no verify-final-02 e campanhas artifact-small/large-final-01 do JAR. **Aceite nominal pendente:** Grão nominal Frete×PE/CB, minuta/ranking, origem de performance e referências filial/documentos ratificados.
  - **Integração por arquivos MAT-02 em14/09/2026:** LocalArtifactScenario materializa MAT-02 pelo repository SQL existente; LocalFactOracle compara seu grão por conjuntos e multiplicidade e recusa missing/extra/duplicate/value. Provas: QualificationArtifactScenarioIT/QualificationFactOracleEdgesIT no verify-final-02 e campanhas artifact-small/large-final-01 do JAR. **Aceite nominal pendente:** Filial empresarial/canônica e regra nominal de fallback MAN/INV por dia/classificação Geral.
  - **Integração por arquivos MAT-03 em14/09/2026:** LocalArtifactScenario materializa MAT-03 pelo repository SQL existente; LocalFactOracle compara seu grão por conjuntos e multiplicidade e recusa missing/extra/duplicate/value. Provas: QualificationArtifactScenarioIT/QualificationFactOracleEdgesIT no verify-final-02 e campanhas artifact-small/large-final-01 do JAR. **Aceite nominal pendente:** Grão financeiro nominal por Frete, vínculo FAT/FRE e calendário/atribuição retroativa aprovados.
  - **Integração por arquivos MAT-04 em14/09/2026:** LocalArtifactScenario materializa MAT-04 pelo repository SQL existente; LocalFactOracle compara seu grão por conjuntos e multiplicidade e recusa missing/extra/duplicate/value. Provas: QualificationArtifactScenarioIT/QualificationFactOracleEdgesIT no verify-final-02 e campanhas artifact-small/large-final-01 do JAR. **Aceite nominal pendente:** Grão nominal do título, chaves de normalização e precedência CT-e/NFS-e/prazos aprovados.
  - **Integração por arquivos MAT-05 em14/09/2026:** LocalArtifactScenario materializa MAT-05 pelo repository SQL existente; LocalFactOracle compara seu grão por conjuntos e multiplicidade e recusa missing/extra/duplicate/value. Provas: QualificationArtifactScenarioIT/QualificationFactOracleEdgesIT no verify-final-02 e campanhas artifact-small/large-final-01 do JAR. **Aceite nominal pendente:** Competência nominal, frota/papéis/vigência/capacidades e vínculos diretos/por Coletas ratificados.
  - **Construção local MAT-01 em13/09/2026: CONSTRUÍDO_LOCALMENTE_VERIFICADO.** Fato Fretes operacional por raiz canônica/PE/CB,90atributos, valores BRL/MAJOR, precedência fiscal, previsão/finalização/responsável, indicadores e recomposição com recibo. SQL02/07 e cenário/JAR consomem resultado;0,01, XML NFS-e, no-op/stale, incompletude, concorrência e escala testados. **ACEITE_REAL_PENDENTE:** paridade das entradas, referência financeira e política nominal.
  - **Construção local MAT-02 em13/09/2026: CONSTRUÍDO_LOCALMENTE_VERIFICADO.** Coletores por dia/filial/Geral; MAN/INV deduplicados antes da contagem; quatro exclusões, tipos, started/finished, fallback de filial unívoco, percentual/zero e correção dos recortes antigos/novos. CollectorsIT2+GatesIT17 e cenário/JAR/concorrência/escala passaram. **ACEITE_REAL_PENDENTE:** decisão empresarial de filial, grão/paridade reais.
  - **Construção local MAT-05 em13/09/2026: CONSTRUÍDO_LOCALMENTE_VERIFICADO.** Manifestos com92campos/coortes, competência/status, receita com arestas diretas e por Coleta deduplicadas, capacidade KG de trator/duas carretas e papéis vigentes; SQL08/09 e cenário/JAR consomem o fato. **ACEITE_REAL_PENDENTE:** Frota/bindings nominais, identidade/paridade de MAN/Coleta/Frete.
  - **Construção local MAT-04 em 12/09/2026:** CONSTRUÍDO_LOCALMENTE_VERIFICADO. Fato Faturas por título sintético: carga SQL, histórico, recibo, linhagem e consulta. Integra o cenário único por bindings, referências, recomposição e sessão rollback-only. Evidências: [matriz A–N](docs/catalogos/macrobloco-expansao/MATRIZ-A-N.md), [contrato e campos](docs/catalogos/macrobloco-expansao/CONTRATO.md), campanhas físicas do macrobloco e checkpoint0086. **ACEITE_REAL_PENDENTE:** Grão real do título, precedência fiscal nominal e paridade das entradas. O checkbox agregado conserva seu requisito original.
  - **Construção local MAT-03 em 12/09/2026:** CONSTRUÍDO_LOCALMENTE_VERIFICADO. Fato Faturamento por Frete sintético: carga SQL, histórico, recibo, linhagem e consulta. Integra o cenário único por bindings, referências, recomposição e sessão rollback-only. Evidências: [matriz A–N](docs/catalogos/macrobloco-expansao/MATRIZ-A-N.md), [contrato e campos](docs/catalogos/macrobloco-expansao/CONTRATO.md), campanhas físicas do macrobloco e checkpoint0086. **ACEITE_REAL_PENDENTE:** Grão e política financeira reais, calendário/atribuição nominal e paridade das entradas. O checkbox agregado conserva seu requisito original.
  - **Depende por fato de:** entradas em estado <code>PUBLISHED</code> no ambiente sombra, o que não significa cutover produtivo; paridade core V2-012b aceita para as entradas e somente os subconjuntos V2-035a/V2-035b realmente usados. Fatos que usam Manifesto→Coleta/Coleta→Frete dependem de V2-046a/V2-046b. Não depende de V2-012c.
  - **DAG exato:** Fretes operacional ← Fretes + Localização + pagadores excluídos/documentos de filiais; Coletores ← Fretes + Manifestos + Inventário + aliases/filiais; Faturamento ← Fretes + Localização + Faturas por Cliente + calendário + atribuição de filial; Faturas ← Faturas por Cliente; Manifestos ← Manifestos + Coletas + Fretes + frota própria.
  - **Entrega:** cinco cargas set-based testáveis; remover o acoplamento legado de <code>@MarcarAusentesComoExcluidos</code>; consumir os vínculos resolvidos por V2-046a/V2-046b e implementar MAN-05/MAN-06 no fato de Manifestos; preservar MAT-01–MAT-05 e parametrizar regras.
  - **Aceite:** MAT-01–MAT-05; os três grãos DDL×loader, a filial lexicográfica de MAT-02 e a precedência fiscal FAT-02/MAT-04 estão resolvidos por ADR + cardinalidade/paridade antes de publicar; chave, <code>UNIQUE</code>, upsert, partição e validator concordam. Hash-noop, locks, incremental/backfill, full explícito com registro antigo, sweep separado, datas/flags/somas/reducers ficam em paridade; partições renováveis e columnstore somente após benchmark.

- [ ] **V2-037 — Publicar contratos SQL core e compatibilidade controlada.**
  - **Integração funcional local em14/09/2026 — V2-037:** JdbcAnalyticQueries/QualificationComparator consomem oráculos de 19 contratos, 673 colunas e 971 metadados; 35 escopos. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Manifesto nominal do consumidor: 18 saídas externas, SQL10 interno, aliases/tipos/nulos/ordem e compatibilidade por versão. Consumidores e referências reais identificados. Paridade por contrato e aceite de compatibilidade. Nenhum checkbox ou numerador alterado.
  - **Construção local em13/09/2026: CONSTRUÍDO_LOCALMENTE_VERIFICADO.** Dezenove contratos pub.analytic_lab_sql_01..19,673colunas de negócio/971físicas, metadata SQL real e leitor JDBC tipado/limitado; todas as seleções tiveram resultado positivo no JAR. PUB01–08 implementadas com linhagem/filtros/labels/fallback; SQL10 interno e sem payload/segredo. **ACEITE_REAL_PENDENTE:** manifesto legado/consumidor aprovado e owner nominal; nenhum alias produtivo. Evidências: [matriz A–N](docs/catalogos/macrobloco-analitico/MATRIZ-A-N.md), [verificação](docs/catalogos/macrobloco-analitico/verification-summary.json), [colunas/linhagem](docs/catalogos/macrobloco-analitico/matriz-colunas-final.json). O checkbox de aceite agregado permanece aberto.
  - **Depende por contrato de:** vertical/fato correspondente, V2-036 quando aplicável e manifesto consumidor fornecido pelo owner.
  - **Entrega:** manifesto exato das 19 views ETL-owned com nome, ordem/nome/tipo/nullability das colunas, grão e filtros; nomes core neutros e aliases somente quando necessários. Toda coluna de saída entra em V2-017a com source/ref/expressão SQL, join/cardinalidade, fallback, enumeração, consumidor e fixture.
  - **Aceite:** oráculo é manifesto legado aprovado, contrato externo ou aceite nominal entregue pelo owner — nunca leitura do projeto de dashboards; regras PUB-01–PUB-08, contract tests, fingerprint completo, unicidade e zero output sem linhagem; views pesadas leem fatos; nenhum wrapper/DDL cross-database.

- [ ] **V2-050 — Provar memória limitada e push-down SQL por vertical/saída.**
  - **Integração funcional local em14/09/2026 — V2-050:** QualificationArtifactScaleIT mede toda cadeia 2/8 raízes; package ARTIFACT repete dois tamanhos com leitura/compare e cancelamento. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Budgets/SLO por vertical/saída e distribuição de carga representativa aprovados. Ambiente/capacidade e dataset nominal autorizados. Escalas reais e memória/latência ao longo da execução; sem promessa de platô a partir de duas massas. Nenhum checkbox ou numerador alterado.
  - **Medição da campanha em13/09/2026: TESTADO_NA_CAMADA_PACOTE_E_JDBC.** Onze observers reais,orçamento JDBC compartilhado entreSPIDs,64handles/cancelamento,limites rows/bytes/páginas/lotes e retenção final0. Escalas4/16/32/16 do mesmo pacote passaram;34×raízes−1 registros físicos,heap/duração/JDBC/lag medidos e3planos temporais novos. Verify03 conserva todasIT anteriores;sem platô/SLO/aceite real ou nova unidadeV2-050. Checkpoint0141.
  - **Medição do cenário analítico em13/09/2026: TESTADO_NA_CAMADA_LOCAL.** Escalas4/16/32/16, onze entradas/cinco fatos, uma página/lote em voo, Raster256 para cap/recursão,284planos reais sem spill/MissingIndex. Falha64 e execução64 anterior aprovada preservadas. Heap/tempos são diagnóstico; não fecham platô/SLO nem o pai V2-050. Evidências: [matriz A–N](docs/catalogos/macrobloco-analitico/MATRIZ-A-N.md), [verificação](docs/catalogos/macrobloco-analitico/verification-summary.json), [colunas/linhagem](docs/catalogos/macrobloco-analitico/matriz-colunas-final.json). O checkbox de aceite agregado permanece aberto.
  - [x] **V2-050/FUNDACAO_MEDICAO_LOCAL — Construir a fundação local multiescala test-only de medição.**
    - **Rota/bloco/resultado:** <code>Q-MED-FND-01</code>, Bloco 50, <code>FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED</code>. A fatia fecha somente o vocabulário, envelopes O(1), harness sintético e validação determinística locais; dependeu de V2-021, V2-023, Q-FND-01, Q-FND-02 e Q-SWP-FND-01 já concluídas apenas em suas fundações.
    - **Medição e contraprova:** as integrações invocam diretamente <code>DataExportPageStreamer</code> e <code>GraphQlPageStreamer</code> nas escalas exatas 16/256/4096, com oito registros por página. Os seis runs terminaram com <code>maxInFlightPages=1</code>, <code>finalInFlightPages=0</code>, <code>maxRetainedPages=0</code> e <code>finalRetainedPages=0</code>; o GraphQL mantém detector probabilístico fixo de 1 MiB. O mutante real <code>ArrayList&lt;Object&gt;</code> acumula páginas da execução e é recusado por <code>EXECUTION_WIDE_PAGE_RETENTION_DETECTED</code>. Heap/duração são <code>JVM_HEAP_DIAGNOSTIC_ONLY</code>, variáveis e não constituem plateau ou SLO.
    - **RED/GREEN e artefatos:** antes dos artefatos, o validator falhou com <code>V2_050_MEASUREMENT_FOUNDATION_MANIFEST_MISSING</code>; antes dos helpers, <code>clean test-compile</code> falhou por tipos de medição ausentes. O catálogo <code>docs/catalogos/medicao-v2-050/</code>, fixture espelhada, ADR 0031, runbook, runner/validator e 20 artefatos Java exclusivamente em <code>src/test</code> ficaram fechados por manifesto/sidecar. O manifesto possui SHA-256 <code>4c9772d3fc0a80f3bbbee5b38dd07423d512392b8e0892d8d9293556708c7428</code>, a fixture <code>ebe72952e41a786278cf75a8e0f493b797352cf5bad59c343d9053e748701937</code> e o receipt desta execução <code>77083f8203147355be7fe69e991712e699bdae6aef06f456f9f24e24137a05a8</code>; o hash do receipt não é alegado estável entre execuções. O GREEN passou 56 testes focados, as regressões obrigatórias, 220 testes isolados após a flutuação ambiental e 949 testes no <code>clean verify</code> final, com zero falha/erro e quatro skips esperados; 18 mutações reais, receipt, B49 FULL, scanner e UTF-8 passaram.
    - **Fronteira e gates abertos:** <code>ENTITY_V2_050_GATE=OPEN</code>, <code>ENTITY_HEAP_PLATEAU_NOT_PROVEN</code>, <code>ENTITY_SQL_PLAN_NOT_EXECUTED</code> e <code>ENTITY_PUSH_DOWN_PLAN_NOT_PROVEN</code>. V2-050 pai, as dez rotas Q-*-05, as 24 rotas O-*-02 e V2-038 permanecem abertos. Não houve rede, fonte/API, <code>.env</code>, segredo, banco, SQLCMD/Flyway, plano SQL real, DDL/DML, migration, alteração de <code>src/main</code>/runtime/<code>Main</code>/composition root/POM/workflow, oráculo externo, heap/SLO/escala produtivos, deploy, publicação, sweep/prune ou cutover.
  - **Depende por escopo de:** V2-021, V2-023, V2-012b e vertical/relacionamentos aplicáveis; para fato/view, também V2-036/V2-037 e V2-012c da saída.
  - **Entrega:** gate arquitetural + teste de carga sintético com muitas páginas e total de registros crescente; medir heap, bytes/lote, linhas em voo, duração e planos SQL. Auditar que extração apenas stageia e que dedupe/frescor, joins/crosswalks, presença/sweep, current/history, paridade/DQ e agregações executam set-based.
  - **Aceite:** heap alcança platô compatível com <code>O(maxResponseBytes + batchBytes)</code>; no máximo uma página/lote em voo; nenhum <code>List/Map/Set</code> ou repositório com universo da execução, consulta/DML por registro ou <code>IN (...)</code> dinâmico; duplicata entre páginas é resolvida no SQL; planos dos joins/windows/anti-joins são SARGable, usam índices medidos e retornam só contagens/<code>TOP(N)</code> sanitizado. Emite gate independente por vertical/fato antes de V2-038.

### Fase 5 — Qualificação, operação e cortes

- [ ] **V2-038 — Qualificar E2E, replay, recovery e desempenho por entidade/onda.**
  - **Integração funcional local em14/09/2026 — V2-038:** QualificationArtifactCase/Index e Action.ARTIFACT integram o supervisor/worker/journal atual; pins verificados antes de RESERVED. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Aceites de onda, oráculos reais, matriz de workload e critérios de recovery/qualidade. Ambiente operacional com fontes/principals e políticas ratificados. E2E nominal incluindo COMMIT/crash/restore e performance representativa. Nenhum checkbox ou numerador alterado.
  - **Conferência final dos bytes (0144):** matriz de 2.437 campos dividida em três partes seladas para respeitar o scanner de 5 MiB; campos originais e medida preservados. Correções somente de dados de auditoria/validação; fontes Java e pacote qualificado permanecem idênticos. Diffs e checks finais conferidos pelo selo, sem reaproveitar PASS documental invalidado.
  - **Fechamento da auditoria/correção local:** admissão durante owner anterior vivo e conclusão interrompida EVIDENCE/ROLLBACK corrigidas no supervisor. Verify final:1.976unitários/4skips históricos e423IT/81classes,417anteriores exatas+6novas,coverage/rollback;dois pacotes byte-idênticos,5smokes/30comandos,4casos de retomada extraída/12comandos,21/8contraprovas.39/45 e67/115 preservados; limites funcionais e G01–G08 no catálogo macrobloco-fechamento-construcao. Checkpoint0143; veredito exige selo final íntegro.
  - **Auditoria/correção local de retomada em 13/09/2026:** duas falhas reproduzidas no supervisor: nova admissão com proprietário anterior vivo e conclusão interrompida após EVIDENCE/ROLLBACK. A correção valida a cadeia antes de avançar, bloqueia nova admissão enquanto a reserva anterior não permite reconciliação e conclui somente evidência previamente selada, sem repetir worker. Seis novos casos e dez de composição passaram em resume-green-01, rollback confirmado; verify integral/pacote/sucessão finais ainda em execução. Nenhuma prova de COMMIT/crash/restore de domínio;39/45 e67/115 preservados. Checkpoint0142.
  - **Construção local em13/09/2026: CONSTRUÇÃO_LOCAL_CONCLUÍDA.** Campanha tipada integrada ao pacote,19oráculos/673colunas/971metadados/35gates, agenda física cinco workloads, ondas/replay/recomposição/ausência, concorrência/cancelamento e journal/retomada.17smokes e escalas4/16/32/16 passaram com rollback;verify03 passou1.976unitários/4skips históricos e417IT/0skip, preservando378anteriores. Capacidade local acrescenta uma unidade; aceite operacional/platô/SLO/paridade real continuam abertos. Relatório e verification-summary do catálogo macrobloco-qualificacao-pacote;Checkpoint0141.
  - **Depende por onda de:** V2-022b, V2-023, V2-050 e V2-012b para vertical core; fato/view exige também V2-012c da saída correspondente.
  - **Entrega:** janela, microbatch, backfill, late data, interrupção/retomada, reconciliação e materialização; fingerprint de schema/contrato; budgets numéricos por fonte/vertical para duração, memória, IO, lag, throughput, bytes e quota; medir a matriz de agenda de V2-022, inclusive concorrência, deadline, blackout e catch-up.
  - **Aceite:** reproduzível por SHA, nenhum duplo efeito, checkpoints corretos, concorrência/falha/cancelamento, platô de heap e limites de rows/bytes/in-flight comprovados, planos SARGable/indexados, agenda e budgets aprovados. Emite gate por entidade/onda sem esperar todas as demais.

- [ ] **V2-039 — Preparar pacote, segurança e operação de release.**
  - **Integração funcional local em14/09/2026 — V2-039:** New-QualificationPackage -ArtifactInputs indexa 236 membros de casos + índice e duas campanhas; global512 preservado; Main e dependências no ZIP. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Provider/owners/assinatura e política de release; matrizes workload/backup/retention nominais. Service account, TLS, scheduler, storage e alvo de release. CI no SHA publicado, assinatura e smoke/restore material no ambiente-alvo. Nenhum checkbox ou numerador alterado.
  - **Construção local em13/09/2026: CONSTRUÇÃO_LOCAL_CONCLUÍDA.** Builder/verificador/extrator/6comandos consomem manifesto,configuração,SBOM/proveniência e pacote172membros/9dependências. Dois builds offline do mesmo snapshot produziram ZIP/JAR byte-idênticos;1.996inputs/1.015classes-recursos conferidos;25/21/8contraprovas e21extrações finais novas passaram Windows/x64/Java17. Capacidade local acrescenta uma unidade;CI/assinatura/feed/owner/fundação operacional/release nominal permanecem abertos. Construção39/45,aceites67/115. Checkpoint0141.
  - [ ] **V2-039a — Fundação operacional:** depende de V2-016b, V2-015a, V2-018, V2-020, V2-022b, V2-041, V2-042c, V2-043, V2-045b e do desenho V2-048a; entrega JAR reproduzível, config externa, service account, TLS, scheduler configurado pela matriz de workload, health/alertas, backup/restore e runbooks antes do primeiro corte.
  - [ ] **V2-039b — Release candidate por onda:** depende de V2-039a e V2-038 da onda; entrega checksum/SBOM/proveniência, config/fingerprint congelados e smoke nos SOs suportados.
  - **Aceite:** fundação operacional existe antes do primeiro corte e é ampliada por onda; forward-fix/restore/reconstrução de sombra ensaiados; RTO/RPO/ownership; nenhum startup/restart produtivo automático por IA.

- [ ] **V2-048 — Ratificar e ensaiar a topologia material de cutover.**
  - **Integração funcional local em14/09/2026 — V2-048:** Nenhum mecanismo funcional verificado nesta unidade; requisito original preservado. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** V2-048b: plano CUTOVER-DB-01/DATABASE_WIDE, aceites por unidade, matriz de writers/fences e duas recuperações. Banco V2 dedicado, não shadow promovido/renomeado; principals/backups/alvos autorizados. Ensaio material integral, RTO/RPO e duas recuperações. Nenhum checkbox ou numerador alterado.
  - **Direção recomendada:** banco V2 dedicado, criado do zero pelas mesmas migrations/fingerprints do ambiente sombra; dados históricos via V2-047; contratos no schema <code>pub</code>; troca de endpoint/alias/configuração pelo owner. Não renomear o banco sombra, não publicar wrapper cross-database e não alterar <code>ETL_SISTEMA</code> in-place sem novo ADR/aceite. A menor unidade produtiva é a onda fechada pelo DAG e pela granularidade real de roteamento/fence; sem roteamento e write-fence por objeto, o corte é database-wide. Antes da primeira publicação produtiva V2, rollback pode voltar ao legado congelado; a primeira publicação aceita é o ponto de não retorno. Depois dele, o padrão obrigatório é roll-forward — restaurar/reconstruir a V2 e repetir o delta desde seu checkpoint/evidência imutável —, nunca reativar silenciosamente o writer legado defasado.
  - [x] **V2-048a — Ratificar desenho, unidade e fences:** depende de V2-017 e V2-019; mapear DAG, consumidores, granularidade de endpoint/alias/configuração, schemas/names, grants e write-fence por tabela/entidade. Provar se corte granular existe; caso contrário, registrar onda/banco indivisível, ponto de não retorno e plano de freeze.
    - **Estado/evidência em 31/08/2026:** <code>COMPLETO_LOCAL</code>. O ADR 0015 e o catálogo determinístico ratificam banco V2 novo/dedicado e <code>CUTOVER-DB-01/DATABASE_WIDE</code>, pois não existe prova conjunta de rota e write-fence granulares. As 131 responsabilidades cobrem exatamente o recorte V2-017, inclusive 34 tabelas lógicas, cinco fatos, 19 contratos — 18 consumer-facing e monitoramento interno por default — e 55 superfícies de invocação; o DAG possui 49 nós acíclicos e os 11 fences separam freeze, revogação material do writer legado, negação de autoridade <code>run</code> V2 antes do start, rota, ativação coordenada e recuperação. O manifesto incorpora o fingerprint integral da fundação V2-019, sete schemas e allowlist atual de 24 triplets. <code>PUBLISHED</code> em shadow não é PNR; V2-048b produz somente <code>SIMULATED_PNR</code> isolado e V2-014 continua dona da execução/PNR produtivos. Nenhuma migration, role, grant, target, alias, principal ou configuração produtiva foi criada/alterada.
  - [ ] **V2-048b — Ensaiar troca e recuperação:** depende de V2-037, V2-039 e bootstrap consistente V2-047 para todas as responsabilidades da unidade; executar carga histórica + delta até <code>Tcut</code>, freeze, parada/revogação do writer antigo, habilitação do novo e smoke. Ensaiar rollback pré-escrita para o legado congelado e, pós-escrita, restore/rebuild + replay do delta V2 dentro do RPO. Legado pode servir leitura explicitamente defasada durante incidente com aceite do owner, mas não voltar a escrever. Se a origem/evidência não permitir replay dentro do RPO, o cutover fica bloqueado.
  - **Aceite:** ensaio em ambiente isolado reproduz a unidade real de troca sem dois writers, write-fence efetivo, rollback antes da primeira escrita e roll-forward depois dela; fingerprint/grants/contagens/contratos conferem; RTO/RPO são medidos; consumidor/owner ratifica topologia, ponto de não retorno e recuperação.

- [ ] **V2-014 — Executar gate por unidade real de cutover.**
  - **Integração funcional local em14/09/2026 — V2-014:** Nenhum mecanismo funcional verificado nesta unidade; requisito original preservado. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Todos DoD/paridades/sweep aplicáveis, aceites de segurança/release/E2E/ensaio por unidade real. Alvo/writers/consumidores reais e janela de corte aprovados. Primeira publicação produtiva V2 aceita define PNR; depois somente roll-forward V2. Nenhum checkbox ou numerador alterado.
  - **Depende por unidade de corte de:** DoD e V2-012b de toda vertical; V2-012c quando houver fato/view; linha de aplicabilidade V2-013 de toda entidade; V2-015b, V2-015c, V2-015d, V2-037 quando houver contrato SQL, V2-038, V2-039 e V2-048b da onda/unidade.
  - **Gate:** dual-run/paridade assinados; schema fingerprint e migrations do artefato congelados; contrato SQL aprovado; sweep dry-run ou <code>DISABLED/BLOCKED/NOT_APPLICABLE</code> aceito; agenda/SLA; alvo/preflight; backup/restore e rollback ensaiados; owner, aceitante, janela e observabilidade.
  - **Aceite:** autorização nominal, antigo writer parado e suas credenciais de escrita revogadas antes de habilitar o novo; nenhum instante com dois writers; plano datado de remoção do adaptador/job antigo.

- [ ] **V2-040 — Fechar a matriz de paridade e desativar o legado com segurança.**
  - **Integração funcional local em14/09/2026 — V2-040:** Nenhum mecanismo funcional verificado nesta unidade; requisito original preservado. Evidência da revisão: verify-final-02 e provas correspondentes na matriz-45-unidades.json/verification-summary.json do macrobloco-integracao-funcional. **Parcela nominal/material preservada:** Todos cortes aceitos, janela de observação datada e zero destino pendente por dependência/consumidor. Legado/serviços/credenciais/infra identificados com owners e plano de recuperação. Zero acesso/escrita legada residual e continuidade comprovados materialmente. Nenhum checkbox ou numerador alterado.
  - **Depende de:** V2-014 concluído para todas as responsabilidades mantidas.
  - **Entrega:** confirmar a matriz final de comandos, entidades, tabelas, migrations, índices, constraints, seeds, validações, procedures, views, jobs, credenciais e contratos; fechar V2-017a para todos os paths/colunas/filhos/saídas; retirar GraphQL/probes/jobs/credenciais somente quando não houver consumidor.
  - **Aceite:** nenhuma responsabilidade funcional nem campo/saída sem destino; zero <code>UNCLASSIFIED</code>/<code>UNRESOLVED</code> publicado; rollback/decommission aprovados; legado read-only por período definido e depois arquivado; documentação final e ownership do V2 atualizados.

## Ordem obrigatória de execução

1. **Conter o incidente em duas filas:** V2-041 permanece em <code>EXTERNAL_HOLD</code> antes de nova sonda, uso externo de credencial, release ou deploy. Em paralelo, a fila offline começa por V2-016a e nunca reutiliza os valores sensíveis.
2. **Criar base rastreável:** V2-016a → V2-017/V2-017a; executar V2-015a/V2-015d assim que os pré-requisitos locais existirem. V2-016b pode avançar quando houver remote/autorização e precisa fechar antes de qualquer afirmação de CI/release.
3. **Fundação comum:** V2-018 e V2-019; depois V2-020, V2-021, V2-042, V2-043, V2-044 e V2-045a conforme suas dependências; executar V2-015b assim que V2-019/V2-020 existirem. V2-045b permanece no gate físico/produtivo e não interrompe a fila offline.
4. **Runtime/gates comuns:** framework V2-023 e V2-022a offline podem avançar com V2-042a deny-all; V2-022b só avança depois de V2-042b/V2-042c. As verticais em sombra dependem de V2-022a, enquanto E2E/operação/release dependem de V2-022b. Iniciar o desenho V2-048a e, depois dele, a fundação V2-039a sem esperar todas as verticais, mas sem contornar V2-045b para operação produtiva.
5. **Contratos por fonte:** após V2-041, reconfirmar janela/teto e executar V2-025d; V2-024 formaliza GraphQL e V2-025 fecha ESL/fatias de V2-017a. Fechar V2-009a antes da primeira onda e V2-009b somente para a vertical seguinte a implementar.
6. **Base compartilhada:** V2-033 e V2-035. A dimensão Usuários de V2-035 depende de V2-033; as demais referências podem avançar em paralelo.
7. **Primeira onda e dependência referencial:** V2-010 e a ingestão base V2-026 podem avançar em paralelo; V2-011 base pode avançar depois de V2-010 sem aguardar V2-046a. Quando cada vertical estiver em shadow, executar V2-012a → V2-047 por entidade em paralelo quando aplicável. V2-046a roda somente quando Coletas/Manifestos satisfizerem seus próprios gates; V2-046b roda depois de V2-046a e dos gates de Fretes. Política: <code>V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW</code> e <code>V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES</code>. Para cada entidade, depois do fechamento relacional aplicável: V2-012b → classificação V2-013 → V2-050 → V2-038 core. Fato/view segue V2-036/V2-037 → V2-012c → V2-050 → V2-038 da saída. A unidade fechada então executa V2-039/V2-048b → V2-015c/V2-015d → V2-014.
8. **Verticais adicionais:** executar V2-027, V2-028, V2-029, V2-030, V2-031 e V2-032 em ondas pequenas, repetindo fechamento V2-017a, bootstrap, caracterização, identidade, paridade, sweep aplicável, V2-050, qualificação e cutover. V2-034a decide Raster; somente “manter” habilita V2-025c → V2-009c → V2-034b.
9. **Analítico por dependência:** após V2-012b das entradas, executar V2-036 por fato e V2-037 por contrato; depois V2-012c → V2-050 → V2-038 da saída e incluí-la no V2-014 da unidade de corte.
10. **Encerramento:** executar V2-040 somente após todos os cortes, gates por onda, credenciais antigas revogadas e responsabilidades reconciliadas.

Tarefas independentes podem ser paralelizadas depois de seus pré-requisitos, mas nenhuma vertical pode contornar contratos, identidade, staging/control plane e Definition of Done.

### Regra de seleção do próximo bloco por outro chat

1. Tarefa marcada <code>EXTERNAL_HOLD</code> não é selecionada sem evidência externa nova fornecida pelo owner; o chat não repete inventário, runbook ou scanner para simular progresso.
2. O seletor escolhe a primeira tarefa offline cujas dependências offline estejam satisfeitas. Dependência de rede, owner nominal, remote, release ou produção bloqueia somente o subgate que realmente a utiliza.
3. Uma tarefa com parte local e externa deve ser dividida, como V2-016a/V2-016b, em vez de terminar o bloco inteiro como bloqueado.
4. Tarefa em <code>READY_FOR_BASELINE_COMMIT</code> não volta à fila de implementação quando toda a entrega local está validada e só resta o commit proibido sem autorização; seu SHA deve ser preenchido no primeiro baseline autorizado.
5. **Próximo bloco atual:** **Nenhum próximo bloco oficial está elegível.** Bloco 55/P02V concluído localmente A–J. Fonte real exige coordenação/atestado V2-041, saúde do writer, recorte/período/oráculos e aprovações de negócio; os demais consumidores mantêm seus critérios próprios. Sem execução recorrente autorizada.
   **Prompt executado no Bloco 52:** [pacote integrado P02R](docs/runbooks/prompt-bloco-52-recuperacao-duravel-astra.md). O texto de preparação é histórico; o aceite atual é exclusivamente local/sintético e está no fechamento acima.
   As repriorizações explícitas <code>PREP-05</code>, <code>PREP-11</code>, <code>P03</code>, <code>P04</code>, <code>P05</code> e <code>P06</code> foram concluídas, respectivamente, como Blocos 25, 26, 27, 28, 29 e 30. O Bloco 34 fechou P07, o Bloco 36 fechou P01/V03, o Bloco 37 executou V04 sem antecipar relação ou qualificação externa, o Bloco 38 fechou a decisão D00 sem iniciar D03/D04, o Bloco 39 fechou somente Q-FND-01, o Bloco 40 fechou somente G12/V2-015e, o Bloco 41 fechou somente Q-BST-01/V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE, o Bloco 42 fechou somente G05A/V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED, o Bloco 43 fechou somente G05T/V2-015d/IMPLEMENTACAO_LOCAL_FAIL_CLOSED, o Bloco 44 fechou somente V08/V2-011a, o Bloco 45 fechou V09/V2-011 como base shadow sem relações, o Bloco 46 fechou somente V10A/V2-028a como decisão local, o Bloco 47 fechou V10/V2-028 como base shadow, o Bloco 48 fechou somente Q-FND-02, o Bloco 49 fechou somente Q-SWP-FND-01 e o Bloco 50 fechou somente Q-MED-FND-01. V2-015d, V2-015, V2-012/V2-012a/b/c, V2-013, V2-050, V2-046a/V2-046b, V2-047 agregada e V2-038 permanecem abertas.
6. O painel operacional <code>docs/runbooks/trilha-de-chats-gpt-5-6.md</code> espelha somente o próximo bloco, a fila prática e a recomendação de modelo/custo. Este <code>STATES.md</code> continua sendo a fonte de verdade: em qualquer divergência, o seletor daqui vence. Ao encerrar um bloco, atualizar primeiro checkboxes/evidências/handoff aqui; depois trocar para <code>[x] STATUS=CONCLUIDO</code> somente a linha correspondente no painel. Manter exatamente uma linha não concluída <code>- [ ] STATUS=AGORA</code> somente quando houver sucessor elegível; caso contrário, manter zero e registrar o bloqueio concreto neste estado e no painel. Se a próxima fatia ainda não estiver materializada, acrescentar uma única linha de chat delimitada; macroetapa parcial nunca recebe conclusão agregada. A criação ou manutenção desse painel não consome um número de bloco funcional.
7. O usuário pode iniciar um chat enviando somente a ordem para ler este arquivo e o painel, seguida de uma linha completa da fila no formato <code>CHAVE=VALOR</code>. O chat relê por conta própria <code>AGENTS.md</code> e <code>../CONTEXTO_GLOBAL.md</code>, tolera diferenças meramente visuais introduzidas pelo renderer e não exige confirmação de “Bloco X”. Linha <code>STATUS=AGORA</code> coerente com este seletor é executada diretamente. Linha <code>STATUS=CANDIDATO</code> copiada pelo usuário é pedido explícito de repriorização: só pode receber o próximo número e virar o único <code>STATUS=AGORA</code> se todas as dependências estiverem satisfeitas; hold ou dependência pendente continua prevalecendo e deve ser informado de forma objetiva.

## Direções técnicas adotadas pelo roadmap

O framework comum destas direções foi ratificado pelos ADRs 0001–0009 em V2-017; o ADR 0010 ratifica somente o boundary provider-neutral/deny-all de V2-042a, o ADR 0011 ratifica a fundação offline de resiliência/falha de V2-043, o ADR 0012 ratifica o gate de drift de contrato pré-promoção de V2-044, o ADR 0013 ratifica somente o lifecycle local de V2-045a, o ADR 0014 ratifica o framework offline fail-closed de observabilidade/DQ de V2-023, o ADR 0015 ratifica o desenho local/database-wide de V2-048a, o ADR 0016 ratifica o adaptador GraphQL transitório limitado/read-only de V2-024 e sua exceção posterior restrita de Usuários, o ADR 0017 ratifica a baseline offline e o gate explícito de incompletude de V2-025a, o ADR 0018 ratifica somente a identidade/crosswalk da primeira onda em V2-009a, o ADR 0019 ratifica current/history de Usuários apenas em sombra por V2-033, o ADR 0020 ratifica somente a fundação offline sem conteúdo produtivo de V2-035a, o ADR 0021 ratifica somente a dimensão current interna de Usuários, o ADR 0022 ratifica o orquestrador offline deny-all de V2-022a, o ADR 0023 congela a decisão V01 de Coletas sem implementar a vertical, o ADR 0024 congela somente P01/V03 de Manifestos 6399 sem executar V04, o ADR 0025 congela/revalida o bloqueio dimensional fail-closed da Frota sem autorizar D03/D04, o ADR 0026 ratifica somente o harness test-only provider-neutral/fail-closed de V2-012/FUNDACAO_LOCAL sem executar caracterização por entidade, o ADR 0027 congela a decisão local de Fretes 6389, o ADR 0028 congela somente a decisão local de Localização 8656 sem implementar V2-028 e o ADR 0029 ratifica apenas a extensão aditiva Q-FND-02 com dois perfis de entidade/três canais preparados, sem executar oráculo. Decisões explicitamente pendentes — grãos e enforcement físico das demais entidades, tradução temporal inclusiva, garantia de completude/snapshot, provedor e RBAC físico, referências mutáveis/algoritmos/ratificação/ativação produtivos, TTL/policy produtivos, Raster, contratos consumidores, target/rota/principals e ensaio físico de cutover — continuam pertencendo aos gates indicados; não são apresentadas como aceitas antecipadamente.

O ADR 0030 ratifica somente o kernel local fail-closed e preview-only de Sweep and Prune, sem habilitar entidade ou apply. O ADR 0031 ratifica somente a fundação local multiescala test-only de medição, com métricas gerenciadas O(1), diagnóstico de heap não conclusivo e zero gate V2-050 por entidade/saída.

1. **Status de Coletas:** preservar <code>done</code> como “Coletada” e <code>finished</code> como “Finalizada”; armazenar bruto/canônico/label versionada.
2. **Usuários:** GraphQL <code>enabled=true</code> é a fonte transitória ratificada; a interface pagina em 20, current/history e <code>core.v_usuario_dimension_current_v1</code> estão implementados somente em sombra com presença tri-state e restart seguro. A view é interna e sem grant; <code>pub.vw_dim_usuarios</code> continua em V2-037. Terminalidade local não prova snapshot/completude nem validade de cursor entre processos; não existe incremental <code>updatedAt</code>, desativação por ausência, publicação externa ou cutover até os gates próprios.
3. **Identidade:** o framework ratificado usa surrogate <code>BIGINT IDENTITY</code> canônico; registry unique <code>(source_instance, tenant_scope, entity, source_key)</code>; aliases versionados. <code>source_instance</code> é identificador estável e não secreto da origem/conta lógica e, na primeira onda, é compartilhado entre os transportes ESL; tenant/corporação é parte da chave e não usa sentinel sem prova formal de singleton/chave global. Source IDs/business keys ficam explícitos, mas distintos do canonical ID. Em V2-009a, 6908/6389 usam <code>id</code> e Usuários usa <code>user_id</code>. Em V2-009b, 6906 e a raiz 6399 usam <code>sequence_code</code>; 8656 usa <code>corporation_sequence_number</code>. Para 6399, filhos locais escopados usam <code>mft_pfs_pck_sequence_code</code> e <code>mft_mfs_key</code>, enquanto o número MDF-e permanece atributo e toda relação com Coleta continua retida até V2-046a. A linha 4924 aceita <code>id</code>, mas título/crosswalks ficam retidos; 10633/6392 permanecem sem raiz aceita e 8636 sem ID de raiz. Rekey cria alias/histórico somente após prova versionada, nunca reescreve silenciosamente; conflito/nulo vai à quarantine. V2-009d aplica constraints set-based por vertical.
4. **Decisões de grão ESL:** 6908/6389 <code>id</code>; 6906 <code>sequence_code</code>; 8656 <code>corporation_sequence_number</code> sem filho observado; 6399 raiz por <code>sequence_code</code>, pick 0..N por raiz+<code>mft_pfs_pck_sequence_code</code> e MDF-e 0..N por raiz+<code>mft_mfs_key</code>, sempre no limite fail-closed do corpus versionado e sem vínculo inferido; 4924 somente linha por <code>id</code>, sem título lógico/crosswalk. Inventário 10633 e Sinistros 6392 mantêm <code>sequence_code</code> apenas como candidato contra os hashes compostos legados; Contas a Pagar 8636 não possui source key da raiz e <code>ant_ils_sequence_code</code> foi refutado como chave da linha física. Nenhuma decisão local prova unicidade global, estabilidade temporal, tenant no payload, completude, relação ou cardinalidade fora de seu limite registrado.
5. **Incremental/watermark:** unidade confiável é partição de data de negócio terminal e publicada. <code>by_updated_at</code> de 6908/6389 é overlap complementar para late data, não watermark. Sem cursor temporal monotônico provado, revarrer partições com lookback e dedupe idempotente.
6. **Janelas:** modelo interno <code>[start,endExclusive)</code>, <code>America/Sao_Paulo</code> por contrato; tradutor caracteriza se a ESL inclui bordas e sua precisão antes de aplicar qualquer subtração; testes cobrem fronteiras/DST/lacuna/duplicação.
7. **Campos ausentes:** GraphQL/legado atua como sidecar transitório somente para campo sem equivalente; presença/proveniência impedem overwrite por nulo. <code>fit_ant_*</code>, documento, minuta e nomes parecidos nunca são tratados como IDs equivalentes por inferência.
8. **Execução:** CLI one-shot + scheduler/supervisor externo, lease no SQL Server, nenhum daemon/PID. Pacote-alvo: JAR executável reproduzível Java 17 com config externa, checksum, SBOM e proveniência.
9. **Autorização:** identidade de serviço + RBAC no boundary real; runtime sem DDL, comandos sensíveis separados e auditados, sem SQLite local como controle central ou bypass por JAR.
10. **Schemas/transação:** usar <code>ctl/stg/core/ref/mart/pub/recon</code>; modo sombra é ambiente. Staging/DQ precede transação set-based que promove, audita e avança publication/checkpoint da entidade; sweep é execução/checkpoint separado.
11. **Completude:** HTTP 200, página vazia e duas travessias iguais não bastam isoladamente. Sweep/cutover requer cursor/contagem/snapshot garantido pelo fornecedor ou oráculo independente com totais e chaves; repetição com dois <code>per</code> é evidência complementar. 8636 e qualquer raiz sem prova ficam em sombra/upsert sem ausência.
12. **Falhas:** identidade/auth/config/schema/drift/SQL/DQ crítico → <code>ABORT</code>. A fundação de V2-043 faz 429/timeout de request/5xx seguirem retry limitado e orçamento compartilhado; sua composição operacional pertence a V2-022, enquanto probes/harnesses param no primeiro 429 e nunca exercitam retry. 422 de janela comprovadamente grande → <code>REPARTITION</code> limitado, demais 422 terminais; dependente obrigatório → <code>BLOCKED</code>. Raster formalmente desligado → <code>NOT_APPLICABLE/SKIPPED</code>; habilitado e indisponível degrada/falha conforme dependentes.
13. **Quarantine:** threshold default é zero para erro de identidade, financeiro, relação obrigatória ou perda de registro. Outras tolerâncias exigem valor absoluto + percentual medidos, owner, SLA/replay e equação completa; limites legados não são herdados automaticamente.
14. **Raster:** fora da primeira onda e desligado por padrão. Se V2-034a comprovar necessidade, o fluxo é autorização/contrato → identidade → módulo condicional, credencial fail-fast, pai/filhos atômicos e cap como incompletude.
15. **Retenção e proteção:** não persistir payload bruto por default; staging bem-sucedido 7 dias e staging falho 30 dias continuam candidatos, não prazos aprovados. V2-045a nasce sem policy/TTL semeado e permite purge somente das duas tabelas técnicas de staging depois de execução terminal, plano bounded, archive tipado/verificado e gates íntegros; auditoria/recon/quarantine/histórico/core/fatos/evidências não são hard-deleted. O lifecycle manual de logs permanece opt-in e prova integralmente apenas a operação indicada pelo marker atual. Nenhum prazo, job, cold storage/WORM, ACL, backup/restore ou efeito produtivo entra em vigor sem a ratificação e as provas externas de V2-045b.
16. **Checkpoint/status:** a chave semântica é <code>(environment, source_instance, tenant_scope, entity, mode, partition_start, partition_end)</code>; cada entidade/partição confirma o próprio ledger ao ser publicada. <code>execution_id</code>/tentativa e fingerprints de estratégia/contrato/configuração ficam registrados separadamente. Falha independente não desfaz publicação, mas o ciclo global termina degradado/falho e a entidade parcial não avança. Os modos são isolados e apenas a fronteira incremental contígua publicada move o watermark operacional.
17. **Topologia de cutover:** usar banco V2 novo, reproduzido por migrations, com schema <code>pub</code> e troca de endpoint/alias; não renomear shadow, instalar in-place ou usar wrapper cross-database. V2-048a ratificou <code>CUTOVER-DB-01/DATABASE_WIDE</code> até prova física conjunta de rota e write-fence granulares. Freeze/processo, application lock, lease ou scheduler parado isoladamente não são fence: o writer legado exige revogação/negação SQL comprovada e o V2 permanece sem membership/autoridade <code>run</code> efetiva até ativação coordenada. V2-048b ensaia em ambiente isolado e registra somente <code>SIMULATED_PNR</code>; a primeira publicação V2 aceita como autoritativa em produção é o PNR real de V2-014. Antes dele, pode-se retornar ao legado congelado conforme runbook ensaiado; depois, somente roll-forward por restore/rebuild + replay V2, com legado no máximo como leitura declaradamente defasada.
18. **Requisições Data Export:** as nove entidades operacionais usam <code>GET_WITH_QUERY</code> fixo segundo a matriz desta revisão; <code>order_by</code> nunca define sozinho identidade/completude. A V1 serve de baseline de filtro/resultado, mas seus fallbacks mutáveis, caps e timeouts não viram defaults. Usuários não possui template Data Export comprovado e Raster é outra API.
19. **Cobertura por campo:** V2-017a é o ledger bidirecional obrigatório entre fonte, 535 colunas físicas máximas do recorte, filhos, transformações e consumo. Diferença de contagem exige classificação; <code>metadata</code>, alias ou derivação não justificam omissão implícita.
20. **Processamento no banco:** Java tem memória limitada a resposta+lote e apenas transporta/valida/stageia/orquestra. SQL Server resolve dedupe/frescor, joins, presença, histórico, paridade/DQ e analítico set-based; V2-050 prova esse limite por vertical/saída antes da qualificação.
21. **Referências governadas:** conteúdo é append-only por release explícita, selado antes da ratificação e jamais recebe default/current implícito. Grão, vigência, dependências, sobreposição, contagem física e seleção são SQL set-based; chaves canônicas e tokens exigem versões exatas. Seed determinística é candidata, não aprovação. Export, algoritmo, importação, ratificação, paridade e ativação produtivos permanecem gates externos separados.

### Inputs externos ainda indispensáveis

Estes itens não podem ser descobertos pela API ESL nem decididos com segurança apenas pelo código:

Eles são gates do ponto em que passam a ser materialmente necessários; não impedem o baseline, arquitetura, implementação e testes sintéticos offline. Nenhum chat deve fabricar o input nem usar sua ausência para bloquear uma tarefa local independente.

- owner-papel, time/pessoa aceitante e manifesto/contrato nominal dos consumidores de 18 views consumer-facing e cinco fatos, além de escopo/grant interno explícito para a 19ª view de monitoramento, fornecidos sem abrir o projeto de dashboards;
- evidência sanitizada de rotação dos segredos expostos, invalidação do valor anterior e saúde do writer legado; depois disso, confirmação owner da janela e teto da rodada V2-025d;
- template Data Export oficial de Usuários, se existir, com nome/raiz/filtros/campos/garantias e autorização; sem esse artefato, a decisão válida continua sendo a ponte GraphQL e <code>9901</code> não pode ser sondado por inferência;
- servidor/banco produtivo final, janela de cutover, RTO/RPO e autorização nominal para parar/revogar o writer legado;
- provedor de identidade, autoridade que atribui papéis e service principals/operadores para runtime, deploy/migration e cutover;
- necessidade operacional e consumidores de Raster, além de autorização específica para consultar sua API;
- exigências legais/contratuais que alterem classificação, criptografia ou retenção técnica inicial;
- export/baseline autorizado das referências produtivas mutáveis que não estão integralmente semeadas nos scripts legados;
- contato/artefato versionado do fornecedor que garanta snapshot, paginação, contagem, identidade e retenção, ou oráculo independente aprovado com owner, escopo e data — especialmente para 8636 e Usuários;
- para reabrir a Frota, pacote versionado por dimensão com ID imutável do fornecedor ligado a source/tenant; para Veículos, placa/filial/reatribuição, lifecycle e identidade/papéis de principal/reboques; para Motoristas, homônimos/genéricos/renomeação/merge/split, contrato/filial e lifecycle; nenhuma dessas provas pode ser substituída por autorização, placa, nome, coocorrência ou frescor do Manifesto;
- remote Git, branch protection, CODEOWNERS e identidades formais de aprovação.

## Fontes-oráculo deste mapeamento

- Regras: <code>../CONTEXTO_GLOBAL.md</code>, <code>../etl-extracao-dados/AGENTS.md</code>, <code>AGENTS.md</code> e este <code>STATES.md</code>. O <code>states.md</code> legado está ausente do working tree por remoção preexistente e, portanto, não integrou a releitura atual.
- Runtime legado: <code>../etl-extracao-dados/src/main/java/br/com/extrator</code>, especialmente bootstrap, pipeline, comandos, integração, mapeamento, repositórios, reconciliação e validação.
- SQL legado: <code>../etl-extracao-dados/database/tabelas/001..035</code>, <code>procedures/001..005</code>, <code>views/011..025</code> exceto wrappers 023/024, <code>views-dimensao/019..024</code>, <code>indices/001..004</code>; 47 migrations arquivadas <code>001,002,004..048</code> + 11 ativas <code>049..059</code>; 24 validações <code>025..028,030..032,034..041,042</code> (dois scripts), <code>043..049</code>; e os auxiliares de <code>reprocessamento</code>, <code>seguranca</code> e <code>security_sqlite</code> inventariados acima.
- V2 atual: <code>src</code>, <code>database</code>, <code>docs</code>, <code>config</code>, <code>scripts</code>, <code>pom.xml</code> e workflows locais.
- O código legado é fonte de caracterização, não modelo arquitetural a copiar. Produção e negócio continuam sendo o oráculo final para qualquer paridade/cutover.

## Handoff operacional

- **Data/hora e trabalho executado:** 2026-09-07 00:49:22 -03:00; Bloco 50 — Q-MED-FND-01 — V2-050/FUNDACAO_MEDICAO_LOCAL, em GPT-5.6 Sol Ultra, estritamente local, offline e test-only.
- **Estado do trabalho:** <code>FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED</code>. Fecha somente o subcheckbox da fundação; V2-050 pai, as dez rotas Q-*-05, as 24 rotas O-*-02 e V2-038 permanecem abertos. A fotografia é 53/107, com 54 pendências e zero rota <code>AGORA</code> elegível; nenhum Bloco 51 foi atribuído.
- **RED, artefatos e hashes:** o primeiro <code>Test-V2050MeasurementFoundation.ps1 -ArtifactsOnly</code>, executado antes do catálogo, terminou com exit code 1 e <code>V2_050_MEASUREMENT_FOUNDATION_MANIFEST_MISSING</code>; o <code>clean test-compile</code> anterior aos helpers falhou pelos tipos de medição ausentes. Foram entregues exclusivamente sob <code>src/test</code> dez helpers/records e dez testes/integrações, fixture espelhada de seis cenários, ADR 0031, catálogo, manifesto/sidecar, runbook, runner e validator. O manifesto final é <code>4c9772d3fc0a80f3bbbee5b38dd07423d512392b8e0892d8d9293556708c7428</code>, a fixture <code>ebe72952e41a786278cf75a8e0f493b797352cf5bad59c343d9053e748701937</code>, o validator <code>7834a8e0e7b783e82e3434a764c8c95d689b295b3fb16db8ae2615a013641765</code> e o receipt desta execução <code>77083f8203147355be7fe69e991712e699bdae6aef06f456f9f24e24137a05a8</code>; heap/duração tornam o último hash deliberadamente variável entre execuções.
- **Medição e contraprova:** os dois streamers produtivos existentes foram invocados diretamente em 16/256/4096 páginas de oito registros. Data Export contabilizou 17/257/4.097 fetches para 16/256/4.096 páginas úteis; GraphQL contabilizou 16/256/4.096. Todos os runs provaram uma página gerenciada em voo e zero retenção terminal. O detector de ciclos GraphQL ocupa 1 MiB fixo. O mutante com <code>ArrayList&lt;Object&gt;</code> foi realmente executado e recusado por <code>EXECUTION_WIDE_PAGE_RETENTION_DETECTED</code>.
- **GREEN local:** a suíte focal/runner passou 56 testes, zero falhas/erros e um skip condicional; as regressões obrigatórias dos streamers, <code>FirstWaveCompletenessGateTest</code> e <code>MainTest</code> passaram. A primeira tentativa de <code>clean verify</code> registrou honestamente <code>BUILD FAILURE</code> após 856 testes, com 12 falhas, 82 erros e quatro skips, porque um indexador Java externo criado durante a execução removeu/repopulou classes e recursos já compilados em <code>target/test-classes</code>. Depois de o diretório estabilizar, 220 testes afetados passaram isoladamente sem skip e a repetição completa de <code>clean verify</code> sob JDK 17 terminou <code>BUILD SUCCESS</code> com 949 testes, zero falhas, zero erros e quatro skips esperados; Enforcer, Spotless, Checkstyle e os checks JaCoCo passaram. O validator passou <code>ARTIFACTS_ONLY+SELF_TEST</code> com seis cenários e 18 casos adversariais; o validator B49 passou em FULL antes do fechamento. O self-test do scanner passou nove casos e a varredura examinou 1.041 candidatos, 1.040 textos e um binário verificado, com zero finding; 32 arquivos passaram UTF-8 estrito sem BOM/LF e <code>git diff --check</code> ficou verde.
- **Fronteira e seletor:** <code>ENTITY_V2_050_GATE=OPEN</code>, <code>ENTITY_HEAP_PLATEAU_NOT_PROVEN</code>, <code>ENTITY_SQL_PLAN_NOT_EXECUTED</code> e <code>ENTITY_PUSH_DOWN_PLAN_NOT_PROVEN</code>. Nenhuma rede, fonte, API, <code>.env</code>, segredo, banco, SQLCMD/Flyway, plano SQL real, DDL/DML, migration, payload/ID real, mudança em <code>src/main</code>/runtime/<code>Main</code>/composition root/POM/workflow, oráculo externo, alegação de heap/SLO/escala produtivos, deploy, publicação, sweep/prune ou cutover foi acessada/executada. As Q-*-05 aguardam Q-*-03, as O-*-02 aguardam O-*-01 e V2-038 aguarda os gates correspondentes mais V2-022b; por isso não há sucessor elegível nem autorização para Bloco 51.

- **Data/hora e trabalho executado:** 2026-09-06 22:50:05 -03:00; Bloco 49 — Q-SWP-FND-01 — V2-013/FUNDACAO_KERNEL_LOCAL, em GPT-5.6 Sol Ultra, estritamente offline e preview-only.
- **Estado do trabalho:** <code>FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED</code>. Fecha somente o subcheckbox da fundação; V2-013 pai, as dez rotas operacionais Q-*-04 e V2-034a/V2-034b permanecem abertos. Após materializar V2-050/FUNDACAO_MEDICAO_LOCAL sem fechá-la, a fotografia é 52/107, com 55 pendências.
- **RED e artefatos:** o primeiro <code>Test-V2013SweepPreviewFoundation.ps1 -ArtifactsOnly</code>, executado antes do catálogo, terminou com exit code 1 e <code>V2_013_SWEEP_FOUNDATION_MANIFEST_MISSING</code>; os testes Java escritos antes das classes falharam por tipos ausentes. Foram entregues seis tipos produtivos puros em <code>plataforma/reconciliacao/sweep</code>, sete classes de teste, a fixture espelhada, ADR 0030, README, manifesto/sidecar, matriz CSV gerada, runbook, builder e validator. Os artefatos governados somam 23 paths fechados; o manifesto final é selado por <code>e384cdda999f499cb0fc3d939d6a8b8546e6dc8b2d7d7997e36f6fd5a7527ae9</code> e a matriz por <code>69ef33c8c095c200f4025264e38c0dd248fa4f12bde8722aaa00101c570d3bd9</code>.
- **Kernel, matriz e mutações:** o kernel avalia uma única responsabilidade com estado O(1), policy/scope/binding/four-proof envelope canônico e precedência fechada. Duas travessias completas e duas ausências independentes são necessárias, mas não substituem prova nominal externa; terminalidade, página vazia ou diferença de hash isolada nunca comprovam completude. O catálogo fixa 33 linhas/11 famílias em <code>BLOCKED=19</code>, <code>DISABLED=5</code> e <code>NOT_APPLICABLE=9</code>, sem <code>ENABLED</code>/<code>PROVEN_COMPLETE</code>. A fixture executou um happy path sintético sem apply e 52 mutantes com reason singleton/exato, incluindo todos os modos, tipos, thresholds, vínculos, independência, construção e ordem de precedência.
- **GREEN local:** a suíte focal sob Maven 3.9.14/JDK 17.0.20.1 passou 129 testes sem falha/erro/skip. O <code>clean verify</code> terminou <code>BUILD SUCCESS</code> em 01:43 com 893 testes, zero falhas, zero erros e dois skips esperados; sete regras Enforcer, Spotless sobre 607 arquivos, Checkstyle sem violações e todos os checks JaCoCo passaram. Builder <code>-VerifyGenerated</code>, validator <code>-ArtifactsOnly</code> e a bateria adversarial de schema/hash/sidecar/mapeamento/graph/rogue/reparse/wiring passaram fail-closed. O self-test do scanner passou nove casos; a varredura offline examinou 1.012 candidatos, 1.011 textos e um binário verificado, com zero finding, arquivo não inspecionado ou oversized; <code>git diff --check</code> passou.
- **Fronteira e próximo bloco:** nenhuma rede, fonte, API, <code>.env</code>, segredo, banco, SQLCMD/Flyway, DDL/DML, migration, payload/ID real, coleção de chaves, anti-join, persistência, desativação, delete/prune, relação, publicação, runtime wiring, deploy ou cutover foi acessado/executado. Q-MED-FND-01/Bloco 50 é a única rota <code>AGORA</code> e pode criar somente a fundação local multiescala test-only de medição; V2-050 pai continua aberto e heap/SLO/plano SQL/escala produtivos não podem ser alegados.

- **Data/hora e trabalho executado:** 2026-09-06 21:07:34 -03:00; Bloco 48 — Q-FND-02 — V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE, em GPT-5.6 Sol Ultra, estritamente offline, test-only e aditivo.
- **Estado do trabalho:** <code>FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES</code>. Fecha somente o subcheckbox Q-FND-02; V2-012, V2-012a/b/c, Q-FRE-01 e Q-LOC-01 permanecem abertos. Após materializar V2-013/FUNDACAO_KERNEL_LOCAL sem fechá-la, a fotografia é 51/106, com 55 pendências.
- **RED, artefatos e lock:** o validator criado antes dos artefatos terminou com exit code 1 e <code>Q_FND_02_MANIFEST_MISSING</code>; os testes escritos antes do Java produziram 30 erros de compilação por tipos ausentes. Foram entregues ADR 0029, catálogo/manifesto/sidecar SHA, runbook, validator dedicado, quatro resources aditivos e 16 classes exclusivamente em <code>src/test</code>. O lock literal dos 43 arquivos Q-FND-01 foi recalculado em ordem canônica e permaneceu <code>ab99feb5e413deb44bf0dfc26f737453fa802cd6b293805ba82aaa1c56b9029a</code>; nenhum desses arquivos foi alterado.
- **Perfis, mutações e determinismo:** exatamente dois perfis de entidade expõem três canais isolados. Fretes/Data Export 6389 permanece autoritativo somente nos sete paths contratados; o sidecar GraphQL de dez paths é observacional, sem autoridade de raiz/frescor; Localização/Data Export 8656 preserva os 17 paths e proíbe <code>/sequence_number</code>. Todos permanecem <code>PREPARED_NOT_EXECUTED</code>/<code>ORACLE_REQUIRED</code>, com <code>providerEvidence=NOT_EXECUTED</code>. Quarenta e oito mutações dinâmicas reais foram executadas e recusadas fail-closed; o registry vincula bytes/hashes de profile+fixture, o writer congela a coleção antes de validar/serializar, reavalia os resultados, redige campos e só conclui substituição atômica dentro do target permitido. O receipt usa LF explícito para bytes cross-platform determinísticos, com hash estrutural <code>327ca45986ab6ebe434f56b641053faa8a68f88d946636cdea22400321fd2dd4</code>.
- **GREEN local:** <code>Test-V2012FretesLocalizacaoProfileExtension.ps1 -ArtifactsOnly</code> passou com lock 43, dois perfis, três canais e 48 mutações catalogadas, declarando honestamente o gate Java separado. A suíte focal Qfnd02 registrou 66 testes, 65 executados e um skip condicional, sem falha/erro. O <code>clean verify</code> offline em Maven 3.9.14/JDK 17.0.20.1 terminou <code>BUILD SUCCESS</code> em 01:27 com 780 testes, zero falhas, zero erros e dois skips esperados; sete regras Enforcer, Spotless sobre 594 arquivos, Checkstyle sem violações e todos os limites JaCoCo atendidos. Todos os nove gates correlatos de fundação, contratos, identidades, decisões, verticais e portabilidade passaram; o self-test do scanner passou nove casos e a varredura final examinou 989 candidatos, 988 textos e um binário verificado, sem finding, oversized ou conteúdo não inspecionado. Os 29 arquivos de B48/fechamento passaram UTF-8 estrito sem BOM, NUL ou caractere de substituição; <code>git diff --check</code> passou.
- **Fronteira e próximo bloco:** nenhuma fonte, rede, API, banco, SQLCMD, <code>.env</code>, credencial, payload/ID real, runtime produtivo, migration, relação/crosswalk, bootstrap, paridade, sweep/prune, publicação, deploy ou cutover foi acessado/executado. Q-SWP-FND-01/Bloco 49 é a única rota <code>AGORA</code> e pode construir somente a fundação local fail-closed de V2-013; não pode habilitar sweep por entidade, cuja ativação continua dependente de V2-012b e prova nominal de snapshot/completude.

- **Data/hora e trabalho executado:** 2026-09-06 19:55:00 -03:00; Bloco 47 — V10 — V2-028/EXECUCAO_BASE_SHADOW de Localização de Cargas 8656, em GPT-5.6 Sol Ultra. Execução offline, com toda prova física restrita ao shadow local autorizado.
- **Estado do trabalho:** <code>IMPLEMENTADA_EM_SHADOW</code>. Fecha somente V2-028; V2-028a permanece decisão histórica. A nova fundação Q-FND-02 foi materializada aberta, portanto a fotografia passa a 50/105, com 55 pendências. V2-012a/b/c, V2-013, V2-025d, V2-035a materialização, V2-037, V2-038, V2-046a/b, V2-050 e gates externos/operacionais continuam nos próprios estados.
- **RED e implementação:** antes do produtivo/migration, os testes Java não compilavam pelos tipos ausentes e <code>Test-LocalizacaoCargasV2028ShadowVertical.ps1</code> recusou a ausência do mapper/vertical com <code>LOCALIZACAO_CARGAS_SHADOW_VERTICAL_MISSING</code>; o SQL 046 também recusou os objetos ausentes no baseline anterior. O GREEN entregou boundary local deny-all, request/porta/use case, mapper/domínio dos 17 paths, tri-state, reducers, batches até 100, gateways JDBC separados, V014, baseline, manifesto/fingerprint, validações 046/047, runner opt-in, concorrência e runbook.
- **Contratos e fail-closed:** source key somente <code>/corporation_sequence_number</code> INTEGER em escopo explícito; <code>/sequence_number</code> nunca alias/fallback; <code>service_at</code> é o único frescor e horários civis usam <code>America/Sao_Paulo</code>, recusando gap/overlap. O parser local limita registro a 1 MiB e token numérico a 8.192 caracteres, lê decimais como <code>BigDecimal</code> exato com escala/notação plana, preserva <code>rawWireLexeme</code> e mantém inválido em quarentena; testes e SQL cobrem 5.001 dígitos, teto excedido, <code>10.250000000</code>, <code>12345678901234567890.123456789</code> e <code>0.000000001</code>. Status é trim/lower com terminais exatos; desconhecido é não terminal; <code>status_branch_nickname</code> permanece <code>ABSENT/UNSOURCED_LEGACY</code>.
- **Banco e rollback:** o preflight consultou somente <code>master</code> e confirmou literalmente <code>localhost/ETL_SISTEMA_V2_SHADOW</code> com Windows auth. O runner recompôs V001–V014 em transação, passou 005/046/047 e provou constraints, 17 paths, replay/no-op, stale sem regressão, empate divergente/quarentena, VALUE→ABSENT para todos os opcionais, zero real, precisão decimal, isolamento por environment/tenant, contenção, liberação/reacquisição de lease e ausência sem desativar. Toda massa foi sintética; o estado agregado antes/depois permaneceu <code>0|0|0</code> após rollback.
- **Gates Java e estáticos:** o <code>clean verify</code> final offline em Maven 3.9.14/JDK 17.0.20.1 terminou <code>BUILD SUCCESS</code> em 01:34 com 714 testes, zero falhas, zero erros, um skip esperado, sete regras Enforcer, Spotless sobre 578 arquivos, Checkstyle sem violações e todos os checks JaCoCo atendidos. Passaram também decisão V2-028a (7 regras/24 casos/13 mutações), identidade 8656, contratos/identidade da primeira onda, schema foundation, gate progressivo, vertical Localização e portabilidade gerada/verificada (401 artefatos, 2.437 campos, 75 regras, 16 classes, zero <code>UNCLASSIFIED</code>). A trilha passou com 50 marcos, 202 fatias abertas, 50/105 checkboxes e Q-FND-02/Bloco 48 como única rota <code>AGORA</code>. O self-test do scanner passou nove casos e a varredura offline examinou 963 candidatos/962 textos/um binário verificado, sem finding, oversized ou conteúdo não inspecionado. Os 63 arquivos relevantes de B46/B47 passaram UTF-8 estrito sem BOM/NUL/caractere de substituição e <code>git diff --check</code> passou.
- **Limites e próximo bloco:** não houve rede, API/fonte, GraphQL, <code>.env</code>, segredo, credencial, dado/ID real, banco diferente do shadow local, Flyway persistente, relação Localização–Frete, FK/join/crosswalk/<code>TOP 1</code>, <code>pub</code>, fato, sweep/prune, bootstrap, paridade, deploy, cutover, commit, push, reset, clean ou stash. <code>FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION</code> permanece explícito; V2-046a/b seguem abertas. O Bloco 48 pertence exclusivamente a Q-FND-02, fundação offline test-only de perfis Fretes/Localização, sem executar caracterização nem alterar Q-FND-01.

- **Data/hora e trabalho executado:** 2026-09-06 18:24:00 -03:00; Bloco 46 — V10A — V2-028a/DECISAO_LOCAL de Localização de Cargas 8656, em GPT-5.6 Sol Ultra, estritamente offline e fail-closed.
- **Estado do trabalho:** <code>COMPLETE_LOCAL_DECISION_ONLY</code>. Fecha somente V2-028a; V2-028, V2-046a, V2-046b e os gates externos/operacionais continuam abertos. O indicador passa a 49/104, com 55 pendências.
- **RED e GREEN:** o validador foi criado antes do catálogo e terminou com exit code 1 e <code>LOCALIZACAO_DECISION_CATALOG_MISSING</code>. Após os artefatos, passou com 7 regras LOC-01–LOC-07, 24 casos exclusivamente sintéticos e 13 mutações negativas, incluindo regra ausente, identidade errada, fallbacks por <code>sequence_number</code>/nickname, keep-last, data de extração como frescor, coerção numérica, match de Fretes ambíguo, sweep, publicação, relação e fingerprints inválidos.
- **Decisão congelada:** source key somente <code>/corporation_sequence_number</code> INTEGER na tupla escopada e canonical surrogate; presença com bruto/tipado/parse/path/proveniência; <code>COALESCE(volumes_localizacao, volumes_fretes, 0)</code> com zero real e associação a Fretes apenas candidata; parsing estrito para volume/peso/valores sem inferir moeda, unidade ou arredondamento; frescor exclusivamente por <code>service_at</code>; antigo sem regressão, empate idêntico replay, empate/nulo divergente quarentena; status trim/lowercase, branco=<code>sem_status</code>, desconhecido não terminal; <code>status_branch_nickname=ABSENT/UNSOURCED_LEGACY</code>. Os valores históricos <code>10000/90/1200/150000</code> não são defaults V2.
- **Artefatos e gates:** ADR 0028; catálogo/README/24 casos sintéticos; runbook e <code>Test-LocalizacaoCargasV2028DecisionCatalog.ps1</code>. Passaram o gate focado, identidade/contrato 8656, decisão Fretes, portabilidade gerada/verificada (401 artefatos, 2.437 campos, 75 regras, 16 classes e zero <code>UNCLASSIFIED</code>) e trilha (49 marcos, 202 fatias abertas, 49/104 checkboxes, V10/47 única <code>AGORA</code>). O self-test do scanner passou nove casos. A primeira varredura recusou um token simbólico pela regra <code>INLINE_SECRET_ASSIGNMENT</code>; o fato foi reescrito sem perder o cenário, o SHA-256 da fixture foi atualizado e a varredura final passou sobre 924 candidatos/923 textos/um binário verificado, com zero finding, oversized ou conteúdo não inspecionado. Nove arquivos passaram UTF-8 estrito sem BOM/NUL/caractere de substituição e <code>git diff --check</code> passou. Maven e SQL são <code>N/A</code> porque nenhum Java/POM, migration, SQL ou banco foi tocado.
- **Limites e próximo bloco:** não houve rede, fonte, <code>.env</code>, segredo, credencial, banco, Java produtivo, mapper, migration, SQL, relação, caracterização, bootstrap, publicação, sweep, deploy, cutover, commit, push, reset, clean ou stash. A árvore suja preexistente foi preservada. V10/V2-028 recebe o Bloco 47 como única rota <code>AGORA</code>, somente para implementação base em shadow conforme a decisão; relação, paridade e gates externos permanecem separados.

- **Data/hora e trabalho executado:** 2026-09-06 18:06:36 -03:00; Bloco 45 — V09 — V2-011/EXECUCAO_BASE_SHADOW de Fretes 6389, em GPT-5.6 Sol Ultra. Execução estritamente offline, com prova física limitada ao shadow local autorizado.
- **Estado do trabalho:** <code>IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING</code>. Fecha somente o checkbox pai V2-011 pelo precedente V2-010/V2-010a; V2-011a permanece como decisão histórica. V2-046a/V2-046b, V2-017a agregado e todos os gates externos/operacionais continuam abertos. Após materializar V2-028a sem fechá-la, a contagem é 48/104, com 56 pendências.
- **RED e implementação:** antes dos artefatos de execução, <code>Test-FretesV2011ShadowVertical.ps1</code> terminou com exit code 1 e <code>FRETES_SHADOW_VERTICAL_MISSING</code> pela ausência do mapper. A implementação adiciona domínio e mapper 6389 estritos, política única de frescor, lotes físicos até 100, sidecar de exatamente dez paths, gateways JDBC de staging/promoção, migration V013, manifesto selado, validações 044/045, runner opt-in e prova de concorrência. Os sete paths Data Export efetivamente comprovados ficam separados dos envelopes sintéticos de frescor/status/CT-e/finalizações.
- **Banco, objetos e rollback:** o preflight consultou somente <code>master</code> e confirmou literalmente <code>localhost/ETL_SISTEMA_V2_SHADOW</code> com Windows auth. V013 contém nove tabelas, cinco procedures fechadas, um trigger de candidate set e cinco publicações de grant pela allowlist canônica de 39 entradas. O validator 044, o exercício 045 e duas sessões concorrentes passaram; isolamento por environment/tenant, contenção e reacquisição após rollback foram comprovados. Toda escrita usou dados sintéticos dentro de transações revertidas; raiz/staging/candidato terminaram em <code>estado=0|0|0</code>, iguais ao estado inicial.
- **Gates Java, estáticos e higiene:** após um RED de Spotless e o primeiro <code>clean verify</code> vermelho somente por cobertura insuficiente do pacote JDBC novo, foram acrescentados testes comportamentais de sucesso agregado, resultados malformados, cancelamento e rollback com falha suprimida, sem reduzir thresholds. O <code>clean verify</code> final offline em Maven 3.9.14/JDK 17.0.20.1 passou com 684 testes, zero falhas, zero erros, um skip esperado; sete regras Enforcer, Spotless, Checkstyle e todos os checks JaCoCo ficaram verdes. Passaram também decisão Fretes (7 regras/14 casos/9 mutações), contrato/identidade da primeira onda, vertical Fretes, schema foundation, gate progressivo, portabilidade gerada/verificada (401 artefatos, 2.437 campos, 75 regras, 16 classes, zero <code>UNCLASSIFIED</code>) e trilha (48 marcos, 203 fatias abertas, 48/104 checkboxes, V10A/46 única <code>AGORA</code>). O self-test do scanner passou nove casos e a varredura offline examinou 918 candidatos/917 textos/um binário, com zero finding, oversized ou conteúdo não inspecionado. Os 47 arquivos do bloco passaram UTF-8 estrito sem BOM/NUL/caractere de substituição; <code>git diff --check</code> passou.
- **Limites e segurança:** <code>V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW</code>; <code>V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES</code>. Candidatos Coleta–Frete são apenas preservados com presença/proveniência; V2-046b segue dona exclusiva do crosswalk. Não houve join/FK relacional, lookup <code>TOP 1</code>, backfill, paridade, <code>pub</code>, fato, sweep/prune, bootstrap, fonte/rede, <code>.env</code>, segredo, payload/ID real, legado, produção, deploy, commit ou push. A árvore suja preexistente foi preservada.
- **Próximo bloco:** Bloco 46 — V10A — V2-028a/DECISAO_LOCAL de Localização 8656, única rota <code>AGORA</code>. Ela pode somente congelar LOC-01–LOC-07 em ADR/catálogo/fixtures/validator/runbook offline; V10 implementação permanece candidata e sem número.

- **Data/hora e trabalho executado:** 2026-09-06 16:48:25 -03:00; Bloco 44 — V08 — V2-011a/DECISAO_LOCAL de Fretes 6389, em GPT-5.6 Sol Ultra, estritamente offline e fail-closed.
- **Estado do trabalho:** <code>COMPLETE_LOCAL_DECISION_ONLY</code>. Fecha somente o novo subcheckbox V2-011a. V2-011, V2-046a e V2-046b permanecem abertos; o indicador passa honestamente a 47/103, com 56 pendências. A fronteira é explícita: <code>V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW</code> e <code>V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES</code>.
- **RED e GREEN:** antes dos artefatos, <code>Test-FretesV2011DecisionCatalog.ps1</code> terminou com exit code 1 e <code>FRETES_DECISION_CATALOG_MISSING</code>. Depois da implementação, o mesmo gate passou com 7 regras FRE-01–FRE-07, 14 casos exclusivamente sintéticos, 9 mutações negativas, implementação aberta, base shadow desbloqueada e relação aberta.
- **Artefatos entregues:** ADR 0027; <code>docs/catalogos/fretes-v2-011/README.md</code>, <code>decisao-v01.json</code> e <code>fixtures/casos-v01.synthetic.json</code>; <code>docs/runbooks/v2-011a-decisao-fretes-sol.md</code>; e <code>scripts/validation/Test-FretesV2011DecisionCatalog.ps1</code>. O catálogo tem schema fechado, UTF-8 estrito, SHA-256 da fixture e bindings recalculados do contrato 6389, identidade V2-009a e dez paths de sidecar GraphQL.
- **Decisão congelada:** source key <code>/id</code> escopada e alias corporativo nunca identidade; <code>ABSENT/NULL/VALUE</code>; frescor único <code>cte_created_at → cte_issued_at → criado_em → servico_em</code>; empate divergente em quarentena; replay/no-op; terminal sem regressão; performance oficial antes do fallback com bruto/parse/proveniência e data ambígua bloqueada; CT-e/finalizações preservados; sidecars independentes e limitados; <code>pickItemId</code> apenas candidato; financeiro sem moeda/unidade/aritmética inferida; partição, overlap e late data separados; ausência incremental sem efeito e prune desligado.
- **Gates finais:** passaram o novo validator; os catálogos de contrato e identidade da primeira onda (3 contratos/42 aspectos/14 fixtures; 3 matrizes/10 motivos/6 ações); portabilidade gerada e verificada (401 artefatos, 2.437 campos, 75 regras, 16 classes, zero <code>UNCLASSIFIED</code>); e a trilha (47 marcos, 203 fatias abertas, 47/103 checkboxes, uma rota <code>AGORA</code>). O self-test do scanner passou 9 casos; a varredura offline examinou 889 candidatos, 888 textos e um binário verificado, com zero finding, oversized ou conteúdo não inspecionado. Nove arquivos do bloco passaram UTF-8 estrito sem BOM/NUL/caractere de substituição e <code>git diff --check</code> passou.
- **Migrations, objetos e Maven:** nenhum Java, POM, workflow, migration, SQL ou objeto de banco foi criado ou alterado nesta fatia; Maven, SQLCMD e gate de banco não são aplicáveis e não foram executados.
- **Segurança, preservação e próximo bloco:** não houve rede, API, <code>.env</code>, segredo, legado, payload/dado real, banco, produção, deploy, commit, push, reset, checkout, stash ou limpeza. A árvore suja preexistente foi preservada. V09 é a única sucessora elegível e recebe o Bloco 45 em Sol Ultra, exclusivamente para implementação base shadow/rollback; relação, paridade, publicação e cutover continuam proibidas e nos gates próprios.

- **Data/hora e trabalho executado:** 2026-09-06 14:30:18 -03:00; retomada e conclusão do Bloco 43 — G05T — V2-015d/IMPLEMENTACAO_LOCAL_FAIL_CLOSED no mesmo bloco, após autorização explícita do owner para completar o gate Maven. <code>MODELO_RECOMENDADO=TERRA_XHIGH | MODELO_EXECUTADO=SOL_ULTRA | MOTIVO=LOTE_UNICO_AUTORIZADO_PELO_OWNER</code>.
- **Estado do trabalho:** <code>LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING</code>. Fecha somente o subcheckbox local G05T. V2-015d e V2-015 permanecem abertas; G05 continua <code>EXTERNAL_HOLD</code> e nenhuma baseline de vulnerabilidades foi aceita.
- **Prova Maven de retomada:** com <code>JAVA_HOME</code> limitado ao processo em Temurin 17.0.20.1, <code>.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify</code> terminou <code>BUILD SUCCESS</code>, exit code 0, com 656 testes, zero falhas, zero erros e um skip esperado. Passaram as sete regras Enforcer, Spotless em 528 arquivos, Checkstyle com zero violações e todos os checks JaCoCo. O perfil <code>security-audit</code> não foi ativado.
- **Fechamento e seletor:** a correção mínima <code>FND|BST</code> eliminou a única falha documental da tentativa anterior sem fechar qualquer Q-*-01 de entidade. G05T passa a concluída, o total intermediário passa a 46/102 e nenhuma rota local reúne dependências para <code>STATUS=AGORA</code>; o Bloco 44 permanece não atribuído.
- **Gates finais:** policy e implementação física passaram com 33 casos sintéticos, três PASS, 30 BLOCK e zero exceção efetiva; o manifesto de schema passou. A trilha confirmou 46 marcos, 204 fatias abertas, 46/102 checkboxes e nenhuma rota <code>AGORA</code>. O self-test do scanner passou nove casos e a varredura offline examinou 879 candidatos, 878 textos e um binário verificado, sem finding, oversized ou conteúdo não inspecionado; 16 arquivos passaram UTF-8 estrito sem BOM e <code>git diff --check</code> passou.
- **Limites preservados:** não houve rede, internet, API, <code>curl</code>, NVD/feed, Dependency-Check, profile <code>security-audit</code>, secret/credencial, leitura de <code>.env</code>, finding/ID/dependência real, suppression efetiva, banco, SQLCMD, migration, produção, CI remoto, release, deploy, commit, push, reset, checkout, stash ou limpeza da árvore. Alterações preexistentes foram preservadas.

- **Data/hora e trabalho executado:** 2026-09-06 13:33:15 -03:00; tentativa do Bloco 43 — G05T — V2-015d/IMPLEMENTACAO_LOCAL_FAIL_CLOSED pelo runbook <code>v2-015d-implementacao-local-fail-closed-sol.md</code>. <code>MODELO_RECOMENDADO=TERRA_XHIGH | MODELO_EXECUTADO=SOL_ULTRA | MOTIVO=LOTE_UNICO_AUTORIZADO_PELO_OWNER</code>.
- **Estado do trabalho:** G05T permanece aberto em <code>GATE_MAVEN_FINAL_RED</code>; o resultado <code>LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING</code> não foi concedido. G05A/Bloco 42 continua validamente concluído. V2-015d e V2-015 permanecem abertas, G05 continua <code>EXTERNAL_HOLD</code> e o Bloco 44 não foi atribuído.
- **Implementação física aplicada:** o POM usa threshold <code>0.0</code> por propriedade governada, alias consumido pelo plugin, fingerprint da policy e três guardas Enforcer; preserva <code>failOnError=true</code>, JSON/HTML e chave NVD somente por ambiente. O workflow agendado/manual valida implementação, executa <code>clean verify</code> no profile somente quando futuramente autorizado, valida o relatório com <code>always()</code> e trata ausência do artefato como <code>error</code>. Parser, schemas/sets ordinais, tipos, paths e suppressions foram endurecidos; o README não chama a evidência local de baseline.
- **Reds e greens focados:** o RED físico reforçado registrou 12 divergências e exit code 1, incluindo <code>current=11</code>, <code>current=warn</code> e comando sem <code>clean</code>; após a correção, policy e implementação passaram com 33 casos, três PASS, 30 BLOCK e zero exceção efetiva. O relatório sintético sem HTML falhou com <code>HUMAN_REPORT_MISSING</code>; o limpo passou; uma vulnerabilidade sintética score 0 falhou com <code>UNEXCEPTED_VULNERABILITY</code>; o limpo restaurado passou.
- **Suíte Maven única e correção dirigida:** Java global 25 foi substituído apenas no processo pelo JDK 17.0.20.1 local. <code>.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify</code> executou exatamente uma vez, sem <code>security-audit</code>: Enforcer passou sete regras, incluindo as três novas; Spotless passou 528 arquivos e Checkstyle teve zero violação; o resultado final foi 656 testes, uma falha, zero erros e um skip. A falha era a asserção preexistente de <code>CharacterizationOfflineBoundaryTest</code>, que não excluía a rota administrativa Q-BST-01. O arquivo não rastreado preexistente foi preservado e somente o lookahead passou de <code>FND</code> para <code>FND|BST</code>; compilação direta em UTF-8 e regressão dirigida no JDK 17 passaram. Maven não foi repetido, logo o gate segue vermelho.
- **Gates finais ainda permitidos:** no estado aberto final passaram <code>Test-DependencyVulnerabilityPolicy.ps1 -PolicyOnly</code>, <code>-VerifyImplementation</code>, <code>Test-SchemaFoundationManifest.ps1</code>, <code>Test-Gpt56ChatTrail.ps1</code> e a regressão Java dirigida. O validator da trilha confirmou 45 marcos, 205 fatias abertas, 45/102 checkboxes e G05T/Bloco 43 como única rota <code>AGORA</code> pelo gate Maven vermelho. O self-test do scanner passou 9 casos; a varredura offline passou com 879 candidatos, 878 textos, um binário verificado e zero finding; 16 arquivos passaram UTF-8 estrito sem BOM e <code>git diff --check</code> passou.
- **Arquivos desta tentativa:** <code>pom.xml</code>, <code>.github/workflows/security.yml</code>, <code>README.md</code>, <code>scripts/validation/Test-DependencyVulnerabilityPolicy.ps1</code>, a asserção de <code>CharacterizationOfflineBoundaryTest.java</code>, o runbook de implementação, este estado, a trilha e seu validator. Os artefatos temporários de relatório sintético foram removidos pelo <code>clean</code> obrigatório. Alterações preexistentes da árvore amplamente suja foram preservadas.
- **Segurança, limites e retomada:** não houve rede, internet, API, <code>curl</code>, NVD/feed, Dependency-Check, profile <code>security-audit</code>, secret/credencial, leitura de <code>.env</code>, finding/ID/dependência real, suppression efetiva, banco, SQLCMD, migration, produção, CI remoto, release, deploy, commit, push, reset, checkout, stash ou limpeza. G05T/Bloco 43 permanece a única rota <code>AGORA</code>; uma retomada precisa de autorização para produzir nova prova Maven verde no estado corrigido, sem atribuir outro bloco.

- **Data/hora e trabalho executado:** 2026-09-06 13:15:16 -03:00; Bloco 42 — G05A — V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED, decisão versionada pelo runbook <code>v2-015d-decisao-politica-vulnerabilidades-sol.md</code> em GPT-5.6 Sol Ultra.
- **Estado do trabalho:** <code>POLICY_DECISION_LOCAL_COMPLETE_TERRA_IMPLEMENTATION_PENDING</code>. Fecha somente o subcheckbox de decisão G05A; V2-015d e V2-015 permanecem abertas. A policy fixa threshold <code>0.0</code>, fail-closed para scanner/relatório/configuração e exceções governadas; não representa aceite nominal nem baseline real.
- **Reds e correções:** o RED contratual obrigatório terminou com exit code 1 por <code>POLICY_CATALOG_OR_MANIFEST_MISSING</code>. Defeitos internos encontrados durante o TDD do validator — agrupamento booleano, coerção automática de datas, enumeração vazia, colisão com <code>$input</code> e binding de lista vazia — foram corrigidos sem servirem como evidência funcional. O RED físico final terminou com exit code 1 e 11 divergências reais, incluindo <code>failBuildOnCVSS=11</code>, ausência das propriedades/guardas/fingerprint, passos do workflow ausentes e <code>if-no-files-found: warn</code>.
- **Artefatos e decisão:** criado o catálogo <code>docs/catalogos/vulnerabilidades-v2-015d/</code> com README, policy, exceções inicialmente vazias, 33 fixtures sintéticas, manifesto e sidecar; o fingerprint da policy é <code>sha256:83c8698663f33b0e79ac987d72c7853fb59b12af42f31b07d1809377a454e056</code>. Criados também o runbook e <code>Test-DependencyVulnerabilityPolicy.ps1</code>, com modos separados de catálogo e implementação física.
- **Comandos e resultados:** <code>Test-DependencyVulnerabilityPolicy.ps1 -PolicyOnly</code> passou com 33 casos, três PASS deliberados, 30 BLOCK e zero exceção efetiva; <code>-VerifyImplementation</code> permaneceu no RED físico esperado. <code>Test-Gpt56ChatTrail.ps1</code> passou com 45 marcos, 205 fatias abertas, 45/102 checkboxes e G05T/Bloco 43 como única rota <code>AGORA</code>. O self-test do scanner passou 9 casos; <code>Invoke-OfflineSecretScan.ps1 -Source .</code> passou com 878 candidatos, 877 textos, um binário verificado e zero finding. Os 11 arquivos da campanha passaram UTF-8 estrito sem BOM, <code>git diff --check</code> passou e o diff de <code>pom.xml</code>/<code>security.yml</code>/<code>ci.yml</code> ficou vazio. Maven e Dependency-Check não foram executados no Bloco 42.
- **Preservação, limites e próximo passo:** nenhuma alteração preexistente foi removida e não houve commit, push, reset, checkout, stash ou limpeza. Não houve rede, internet, API, <code>curl</code>, NVD/feed, perfil <code>security-audit</code>, secret/credencial, leitura de <code>.env</code>, finding/ID/dependência real, suppression efetiva, banco, SQLCMD, migration, produção, CI remoto, release ou deploy. A única sucessora elegível é G05T/Bloco 43; G05 continua <code>EXTERNAL_HOLD</code>.

- **Data/hora e trabalho executado:** 2026-09-06 12:13:20 -03:00; Bloco 41 — Q-BST-01 — V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE, fundação offline de planejamento determinístico de bootstrap pelo runbook <code>v2-047-fundacao-planejamento-bootstrap-sol.md</code> em GPT-5.6 Sol Ultra.
- **Estado do trabalho:** <code>FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED</code>. Fecha somente o subcheckbox V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE; V2-047 agregada e todas as execuções Q-*-02 continuam abertas. A matriz cobre exatamente 11 fontes-entidade, um histórico de Usuários e cinco fatos, classifica seis entidades como planejáveis e cinco como bloqueadas/condicionais, sem declarar qualquer execução pronta.
- **Reds registrados:** antes dos artefatos, <code>pwsh -NoProfile -File .\scripts\validation\Test-V2047BootstrapPlanningCatalog.ps1</code> terminou com exit code 1 pela ausência exata de <code>docs/catalogos/bootstrap-v2-047/manifesto.json</code>. Depois da materialização da nova rota, <code>Test-Gpt56ChatTrail.ps1</code> também terminou vermelho porque o validator histórico contava Q-BST-01 como 61ª rota da família de caracterização; <code>Test-V2012CharacterizationFoundation.ps1</code> recusou a mesma rota administrativa como se fosse Q-*-01 de entidade. <code>Test-PortabilityCatalog.ps1 -VerifyGenerated</code> encontrou ainda o drift preexistente de <code>regras-negocio.csv/PUB-07</code>: o canônico anterior ainda permitia chaves de Frota que o ADR 0025/STATES já bloqueavam.
- **Correção e artefatos entregues:** criados <code>docs/catalogos/bootstrap-v2-047/README.md</code>, <code>manifesto.json</code>, <code>manifesto.sha256</code>, <code>matriz-bootstrap-v01.csv</code>, <code>fixtures/casos-v01.synthetic.json</code>, o runbook e <code>Test-V2047BootstrapPlanningCatalog.ps1</code>. O validator ancora 16 fontes canônicas, confere hashes e bindings dinâmicos de seis contratos/identidades, esquema/ordem fechados, 17 row IDs, namespace <code>BOOTSTRAP</code>, watermark incremental <code>NONE</code> e 34 cenários: sete aceitos apenas como planejamento bloqueado e 27 negativos com reason codes únicos. As regex de Q-*-01 passaram a usar allowlist positiva das dez entidades, sem confundir Q-FND/Q-BST. O catálogo de portabilidade foi regenerado mecanicamente pelo builder existente; somente <code>PUB-07</code> mudou para refletir o bloqueio de Frota já decidido.
- **Estratégia de migration, banco, objetos e rollback:** nenhum Java, POM, workflow, migration, baseline SQL, manifesto de banco ou objeto SQL foi alterado pelo bloco; por isso não houve estratégia in-place/forward, SQLCMD, preflight ou exercício de banco. O rollback desta entrega documental é a remoção coesa dos novos artefatos antes de qualquer consumidor, mas nenhuma limpeza/reversão foi executada. T0, Tcut, source instance, tenant, endpoint, principal, horizonte e conteúdo reais permanecem inputs futuros não vinculados.
- **Comandos e resultados verdes:** o validator Q-BST passou com 17 linhas, seis elegíveis, 34 casos e zero execução externa. Passaram também os validadores de schema foundation, contratos/identidade da primeira onda, contratos/identidade 6399, identidades 6906/8656, fundação V2-012 e portabilidade determinística; esta última confirmou 401 artefatos, 2.437 campos, 75 regras, 16 classes e zero <code>UNCLASSIFIED</code>. O self-test do scanner passou nove casos, a varredura offline passou com 870 candidatos/869 textos/um binário verificado e zero finding, 12 arquivos alterados/relevantes passaram UTF-8 estrito sem BOM e <code>git diff --check</code> passou. Maven, SQLCMD e gate progressivo não foram executados por não serem aplicáveis ao recorte exclusivamente documental/PowerShell; nenhum resultado Java/SQL anterior foi reapresentado como evidência deste bloco.
- **Arquivos alterados pelo bloco:** os sete artefatos do catálogo/runbook/validator Q-BST; <code>scripts/validation/Test-V2012CharacterizationFoundation.ps1</code>; <code>scripts/validation/Test-Gpt56ChatTrail.ps1</code>; a regeneração de <code>docs/catalogos/portabilidade/regras-negocio.csv</code>; este <code>STATES.md</code> e a trilha. Alterações preexistentes da árvore amplamente suja foram preservadas; não houve commit, push, reset, checkout ou limpeza.
- **Segurança, limitações e próximo passo:** não houve rede, internet, API, <code>curl</code>, feed, credencial, leitura de <code>.env</code>, payload/cursor/ID/dado real, banco, SQL, produção, Q-*-01/Q-*-02, bootstrap real, relação, paridade, sweep, publicação, fato, release, deploy ou cutover. A evidência não prova fonte histórica, completude, bordas do fornecedor, T0/Tcut, owner, execução ou prontidão produtiva. O próximo bloco elegível é o Bloco 42/G05A/V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED em Sol Ultra; depois dele, a implementação mecânica G05T usa Terra xhigh. Nenhuma das duas fatias consulta NVD ou conclui V2-015d.

- **Data/hora e trabalho executado:** 2026-09-06 00:03:12 -03:00; Bloco 40 — G12 — V2-015e, correção intercamadas local de V010/V011 pelo runbook <code>v2-015e-correcao-v010-v011-sol.md</code> em GPT-5.6 Sol Ultra.
- **Estado do trabalho:** <code>CONCLUIDO_LOCAL_SHADOW_ROLLBACK_ONLY</code>. Fecha somente V2-015e/G12; V2-015 agregada continua aberta por V2-015c/V2-015d. Na fotografia daquele fechamento o indicador era 42/99 e o Bloco 41 ainda não estava atribuído; a auditoria posterior encontrou e executou Q-BST-01 sem alterar a evidência de G12.
- **Implementação entregue:** V010 aplica terminal retroativo antes do stale nos dois <code>CASE</code> tipados e preserva não regressão/stale aberto. V011 impõe <code>1..9223372036854775807</code> e forma ASCII decimal canônica nas três barreiras SQL, valida os nove tokens de presença fail-closed, calcula atributos/UFs efetivos na mesma tuple e deriva todos os atributos tarifários apenas da release governada; Java limita o value object e converte overflow em quarentena sanitizada antes do JDBC.
- **Migrations e objetos SQL:** o preflight confirmou V010/V011 ausentes do histórico e do catálogo físico, por isso a estratégia foi edição in place, sem <code>repair</code> nem nova versão. Foram afetados os contratos de <code>stg.coleta_record</code>, <code>core.coleta</code>, <code>ref.coleta_sequence_code_alias</code>, <code>recon.coleta_root_presence_observation</code>, <code>stg.usp_stage_coleta_record</code>, <code>core.usp_apply_reconcile_publish_coletas</code>, <code>stg.cotacao_record</code>, <code>core.cotacao</code>, <code>recon.cotacao_root_presence_observation</code>, <code>stg.usp_stage_cotacao_record</code> e <code>core.usp_apply_reconcile_publish_cotacoes</code>, além dos resultados/triggers tipados correspondentes. O baseline já referenciava V010/V011; os fingerprints finais são Coletas <code>68d20e991a0fb7171656245383e7ae2afe166f0295b25644d82d0ce059f31b86</code>, Cotações <code>4dc1506cebccb48071b422fee4928a8dc09497235d15abe093a9c5cf21cb509a</code> e decisão COT <code>96a425c653e00889a1a4d6e97389e549468372ed252d66727191ca4aa0ea14ef</code>.
- **Reds e correções:** 20 testes Java focados produziram duas falhas para overflow; V039 lançou 51876 e exibiu terminal retroativo como noop/stale; V041 expôs narrowing <code>INT</code>, uso de UFs não efetivas e moeda de origem; validators reforçados também ficaram vermelhos. As menores correções coesas alinharam value object/quarentena/JDBC, ordem COL-03, <code>BIGINT</code>+<code>DATALENGTH</code>+BIN2, tri-state, tuple/tarifa e as provas. O gate progressivo inicialmente rejeitou a extensão V010 <code>ref.coleta_sequence_code_alias</code>; o validator 030 recebeu uma allowlist exata para esse único objeto, sem ampliar o contrato V008.
- **Comandos e resultados verdes:** os 27 testes Java focados finais passaram. <code>Test-ColetasV2010ShadowVertical.ps1 -RunSqlExercise</code> capturou/assertou os rowsets tipados; V041 passou as matrizes de identidade/presença/tarifa/tenant em duas transações revertidas; <code>Test-CotacoesShadowConcurrency.ps1</code> passou em duas sessões. <code>Invoke-ProgressiveDataGate.ps1</code> concluiu com sucesso todos os manifests, migrations, baseline, exercícios, negativos, concorrência e SHOWPLAN aplicáveis. O único <code>mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify</code> final, no Java 17.0.20.1, passou com 656 testes, zero falhas/erros, um skip esperado e JaCoCo verde. Os validadores focados e de caracterização passaram; scanner auxiliar 9/9, scanner offline 863 candidatos/862 textos/um binário e <code>git diff --check</code> passaram.
- **Arquivos alterados pelo bloco:** Java/testes em <code>CotacaoStageRecord</code>, <code>CotacaoDataExportRecordMapperTest</code>, <code>CotacaoStageValueObjectsTest</code> e <code>JdbcSqlServerCotacaoGatewaysTest</code>; V010/V011; validators/exercícios 038–041 e <code>support_cotacoes_v2_015e_cross_tenant.sql</code>; manifests/fingerprints de Coletas/Cotações; decisão Cotações e seus hashes derivados de caracterização; wrappers <code>Test-ColetasV2010ShadowVertical.ps1</code>, <code>Test-CotacoesV2027ShadowVertical.ps1</code>, <code>Test-V2012CharacterizationFoundation.ps1</code>; validator 030 e <code>Test-GovernedReferencesManifest.ps1</code>; este estado, a trilha e seu validator. Alterações preexistentes da árvore suja foram preservadas.
- **Rollback, segurança e limitações:** todos os dados, releases, objetos, policies e DDL/DML dos exercícios foram sintéticos e revertidos; a checagem final registrou zero objetos V010/V011 e zero linhas 010/011 em <code>flyway_schema_history</code>. Não houve rede, internet, API, <code>curl</code>, credencial, leitura de <code>.env</code>, dado/payload/cursor/ID real, banco remoto/produtivo, Q-*-01, bootstrap, relação, paridade, sweep, publicação, release, deploy, cutover, commit, push, reset, checkout ou limpeza. A evidência local não prova contrato do fornecedor, completude, paridade, release tarifária operacional ou prontidão produtiva; V004 mantém o stale técnico permitido e a decisão tipada V010 possui o ownership do override COL-03.

- **Data/hora e trabalho executado:** 2026-09-05 18:28:26 -03:00; Bloco 39 — Q-FND-01 — V2-012/FUNDACAO_LOCAL, fundação offline provider-neutral de caracterização e fechamento dos gates locais.
- **Estado do trabalho:** <code>FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES</code>. Fecha somente o novo subitem de fundação; V2-012 e V2-012a/b/c continuam abertos, e nenhuma caracterização por entidade foi iniciada ou concluída.
- **Arquitetura e artefatos:** criados quatro perfis e quatro fixtures sintéticas em <code>src/test/resources/contracts/v2-012/</code>, núcleo e testes exclusivamente em <code>src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/</code>, catálogo/manifesto em <code>docs/catalogos/caracterizacao-v2-012/</code>, ADR 0026, runbook <code>v2-012-fundacao-caracterizacao-sol.md</code> e validator <code>Test-V2012CharacterizationFoundation.ps1</code>. Data Export e GraphQL usam adapters in-memory distintos; schemas/vocabulários/hashes/limites/receipts são fechados, determinísticos, sanitizados e fail-closed.
- **Perfis, fixtures e resultados locais:** Coletas 6908, Manifestos 6399, Cotações 6906 e Usuários <code>graphql-individual</code> possuem um perfil/fixture cada, sempre <code>PREPARED_NOT_EXECUTED</code>/<code>ORACLE_REQUIRED</code>. As fixtures exercitam 166 cenários, com 13 estruturas aceitas e 153 recusas deliberadas; sucesso parcial não produz sucesso global.
- **Validações realmente executadas:** <code>mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify</code>, no JDK 17.0.20.101 exigido, passou no estado final com 609 testes, zero falhas, zero erros, um skip esperado do teste de symlink no Windows e cobertura atendida; o teste de hardlink executou. Passaram <code>Test-V2012CharacterizationFoundation.ps1</code>, os 12 validadores correlatos de contratos, identidade, decisões, verticais e Usuários e <code>Test-Gpt56ChatTrail.ps1</code>, totalizando 14 validadores finais. <code>Test-OfflineSecretScan.ps1</code> passou nove casos; <code>Invoke-OfflineSecretScan.ps1 -Source .</code> passou com 860 candidatos, 859 textos, um binário verificado e zero finding, oversized ou conteúdo não inspecionado; <code>git diff --check</code> passou.
- **Limites, segurança e próximo passo:** não houve rede, internet, API, <code>curl</code>, endpoint, credencial, token, <code>.env</code>, payload real, banco, JDBC remoto, SQLCMD, DDL/DML, migration, runtime produtivo, deploy, release, publicação, cutover, commit ou push. Q-USR-01, Q-COL-01, Q-MAN-01 e Q-COT-01 permanecem abertas para oráculos independentes autorizados; Q-MAN-01 permanece <code>EXTERNAL_HOLD</code>. D03/D04 continuam <code>BLOCKED</code>, e o runbook Terra D03 segue ausente. Existe zero rota <code>AGORA</code>; o Bloco 40 permanece não atribuído. O próximo passo real é obter autorização/evidência de cada oráculo e executar as caracterizações em fatias posteriores, sem antecipar bootstrap, relações, paridade, sweep ou E2E.

- **Data/hora e trabalho executado:** 2026-09-05 14:19:45 -03:00; reavaliação autorizada da decisão V2-035c de Frota, sem criar novo bloco funcional.
- **Estado do trabalho:** <code>COMPLETE_LOCAL_REASSESSMENT_BLOCKED</code>; <code>VEICULOS=BLOCKED</code> e <code>MOTORISTAS=BLOCKED</code> permanecem. A cláusula para criar nova fatia sob V2-035 não foi acionada porque nenhum pacote de evidência satisfaz todos os requisitos de qualquer dimensão.
- **Evidências realmente usadas:** os documentos obrigatórios foram relidos integralmente; os 14 anchors SHA-256 da V01 foram revalidados; o inventário temporal pré-V02 encontrou zero arquivo depois do fechamento em <code>2026-09-05T13:35:21.2424688-03:00</code>; o anexo <code>pasted-text.txt</code>, SHA-256 <code>2d768f7f5474047ba3f4b36f74c59ca54bad2555d7efeab743fee6279db845c0</code>, foi lido integralmente e classificado como prompt operacional genérico sem contrato de Frota; buscas estáticas nos artefatos 6399/V2-026/V012 confirmaram somente sinais de placa, nomes, contrato, filial contextual e papéis de reboque, sem ID dimensional ou lifecycle.
- **Lacunas e único resultado positivo limitado:** Veículos não possui ID imutável do fornecedor ligado a source/tenant, política de placa/filial/reutilização/reatribuição nem vigência/current-history/ausência/conflito/rekey; Motoristas não possui esse ID, política de homônimos/genéricos/renomeação/merge/split, semântica de contrato/filial/lotação nem lifecycle equivalente. Somente os paths/papéis separados de principal, reboque 1 e reboque 2 estão <code>PROVEN_LIMITED</code>; isso não fornece identidade cross-Manifesto.
- **Artefatos da reavaliação:** criados <code>decisao-v02.json</code>, <code>matriz-reavaliacao-v02.csv</code> e <code>fixtures/reavaliacao-v02.synthetic.json</code>; atualizados o README do catálogo, o ADR 0025, este estado, a trilha, <code>Test-FrotaManifestosV2035cDecisionCatalog.ps1</code> e <code>Test-Gpt56ChatTrail.ps1</code>. As 13 fixtures contêm somente pacotes simbólicos/tokens <code>SYNTH_*</code>; controles hipotéticos completos testam apenas a condição futura de abertura e não são alegados como evidência existente.
- **Migrations, objetos e implementação:** nenhum. Não houve Java, migration, schema, tabela, view, procedure, grant, relação, fato, contrato <code>pub</code> ou implementação dimensional. O prompt/runbook Terra D03 permanece ausente.
- **Validações e comandos realmente executados:** o parse preliminar dos JSONs V02 e o import da matriz confirmaram 11 requisitos, 13 fixtures, ambas as dimensões <code>BLOCKED</code> e hashes coerentes. A primeira execução de <code>pwsh -NoProfile -File .\scripts\validation\Test-FrotaManifestosV2035cDecisionCatalog.ps1</code> expôs somente a conversão automática do timestamp ISO pelo PowerShell; o gate foi corrigido para normalizar <code>DateTimeOffset</code> e a reexecução passou, exigindo V01+V02, lacunas, origens, limites, contraexemplos, testes, prova limitada dos reboques, zero nova fatia/rota/bloco/migration/relação e prompt Terra ausente. Passaram também <code>Test-DataExport6399ContractCatalog.ps1</code>, <code>Test-DataExport6399IdentityCatalog.ps1</code>, <code>Test-ManifestosV2026DecisionCatalog.ps1</code>, <code>Test-ManifestosV2026ShadowVertical.ps1</code> e <code>Test-Gpt56ChatTrail.ps1</code>, este com 40 marcos, 204 fatias abertas, 40/97 checkboxes e zero rota <code>AGORA</code>. <code>Test-OfflineSecretScan.ps1</code> passou nove casos; <code>Invoke-OfflineSecretScan.ps1</code> passou com 817 candidatos, 816 textos, um binário verificado, zero finding, oversized ou conteúdo não inspecionado; <code>git diff --check</code> passou. Maven e SQLCMD não foram executados porque esta reavaliação não alterou Java/SQL e SQLCMD foi explicitamente proibido.
- **Segurança, preservação e próximo passo:** não houve rede, API, <code>curl</code>, credencial, <code>.env</code>, payload real, banco, SQLCMD, deploy, commit ou push; o working tree preexistente e o legado somente leitura foram preservados e dashboards não foram lidos. D03/D04 mantêm hold, V2-035b/V2-035 continuam abertas, existe zero rota <code>AGORA</code> e o Bloco 39 permanece não atribuído até evidência qualificadora ou repriorização válida de rota elegível.

- **Data/hora e trabalho executado:** 2026-09-05 13:35:00 -03:00; Bloco 38 — D00/V2-035c — decisão local fail-closed de Frota de Manifestos.
- **Estado do trabalho:** <code>COMPLETE_LOCAL_DECISION_ONLY</code>; <code>VEICULOS=BLOCKED</code> e <code>MOTORISTAS=BLOCKED</code>. V2-035c fecha porque os dois resultados, limites, contraexemplos e testes foram registrados; V2-035b/V2-035 continuam abertas.
- **Decisão e limites:** placa/nome/filial/contrato não formam identidade; não há source key estável, grão dimensional promovível, lifecycle/rekey ou relação Veículo→Motorista. Principal e cada reboque continuam sinais distintos no Manifesto. A tri-state, o bruto/Unicode, o frescor/replay/conflito e a ausência não destrutiva de V2-026 são preservados somente no grão de observação e não provam master data.
- **Artefatos e integridade:** foram criados somente <code>docs/catalogos/frota-manifestos-v2-035c/</code>, ADR 0025 e o validator local correspondente. A matriz e as fixtures possuem 25 casos cada; os JSONs foram parseados localmente, o hash SHA-256 da fixture confere e todos os valores de cenário são tokens <code>SYNTH_*</code>. Nenhum Java, SQL, migration, schema, tabela, view, procedure, grant, runtime, relação, fato, contrato <code>pub</code> ou payload real foi criado.
- **Handoff bloqueado:** como Veículos não ficou <code>EXECUTION_READY</code>, <code>docs/runbooks/frota-manifestos-v2-035b-execucao-terra.md</code> não foi criado; D03/D04 não iniciam. Nenhum próximo bloco oficial está elegível: as rotas remanescentes têm dependência ou hold explícito, e o Bloco 39 não foi atribuído.
- **Validação de fechamento:** passaram <code>Test-FrotaManifestosV2035cDecisionCatalog.ps1</code>, os gates estáticos de contrato 6399, identidade P01, decisão V03 e integridade V04, <code>Test-Gpt56ChatTrail.ps1</code>, o self-test e o scanner offline completo e <code>git diff --check</code>. A primeira execução do novo gate recusou corretamente a origem <code>FM-SHARED-004</code> ainda não vinculada; o ADR 0024 foi então incorporado à lista fechada com SHA-256 e a reexecução passou. Maven e SQLCMD não foram executados porque não houve alteração Java/SQL; não houve banco, rede, deploy, commit ou push.

- **Data/hora e trabalho executado:** 2026-09-05 12:53:00 -03:00; preparação documental, por repriorização explícita do owner, do Bloco 38 — V2-035c — decisão local de Frota de Manifestos em Sol Ultra.
- **Estado do trabalho:** <code>PLANEJADO_NAO_INICIADO</code>. Nenhuma decisão nova de domínio, Java, SQL, migration, schema, tabela, view, procedure, grant, runtime, relação, fato ou contrato consumidor foi criada por esta preparação; V2-035b, D03 e D04 continuam abertos.
- **Plano/handoff preparado:** D00 é a única rota <code>AGORA</code> e deve decidir conjuntamente Veículos/Motoristas, sem inferir placa, filial, nome, reboque ou vínculo. O runbook <code>docs/runbooks/frota-manifestos-v2-035c-sol.md</code> contém o prompt copiável e exige que o Sol crie <code>frota-manifestos-v2-035b-execucao-terra.md</code> apenas se Veículos ficar <code>EXECUTION_READY</code>; esse futuro chat Terra executará somente D03, mantendo D04 candidata.
- **Validação desta preparação:** <code>Test-Gpt56ChatTrail.ps1</code> passou com 39 marcos históricos, 205 fatias abertas, 39/97 checkboxes e uma única rota <code>AGORA</code>; <code>Invoke-OfflineSecretScan.ps1</code> passou sem finding; <code>git diff --check</code> passou. Nenhuma rede, banco, credencial, payload real, deploy, commit ou push foi usado.

- **Data/hora e trabalho executado:** 2026-09-05 12:10:00 -03:00; Bloco 37 — V2-026 — execução completa local em shadow de Manifestos 6399 (V04).
- **Estado do trabalho:** <code>IMPLEMENTADA_EM_SHADOW_ROLLBACK_ONLY</code>. A fatia física V2-009d de Manifestos foi aplicada; V2-009b e V2-009d agregadas, V2-046a, V2-012a/b, V2-013, V2-047, V2-050, V2-038, fatos, contratos <code>pub</code> e cutover continuam abertos.
- **Arquivos, schema e decisões:** foram criados o domínio/aplicação <code>modulos/manifestos</code>, DTO/mapper de página 6399, batch limitado, gateways internos e capability deny-all; V012, manifesto/fingerprint, validator 042, exercício 043, probe concorrente e runbook. O banco de prova materializou e reverteu somente observações/candidatos Manifestos, raiz/filhos core, recon de presença/relação candidata e quatro procedures fechadas. A raiz/pick/MDF-e, <code>mdfe_status</code>, tri-state, frescor, competência, MAN-01/MAN-02/MAN-04/MAN-07, os 27 limites UTF-16 e a ausência não destrutiva seguem V03. A relação Manifesto→Coleta ficou append-only, sem FK ou lookup.
- **Provas reais:** validators 6399/identidade/V03, foundation/progressivo/V04 passaram; <code>006_exercise_progressive_data_gate_rollback.sql</code> e <code>043_exercise_manifestos_shadow_vertical_rollback.sql</code> passaram após preflight em <code>master</code>, exclusivamente em <code>localhost/ETL_SISTEMA_V2_SHADOW</code> e com rollback. O probe de concorrência confirmou contenção, isolamento por environment e readquisição. <code>Maven --offline clean verify</code> com Java 17 passou (585 testes, zero falhas/erros); scanner offline achou zero finding e <code>git diff --check</code> passou.
- **Limitações e próximo bloco:** todas as linhas/dados de prova são sintéticos e revertidos; não provam fornecedor, snapshot/completude, unicidade global, paridade, bootstrap, publicação ou operação. Não houve rede, API, <code>curl</code>, credencial, <code>.env</code>, payload real, banco externo, deploy, commit ou push. Q-MAN-01 exige oráculo ESL externo autorizado. Por repriorização documental explícita do owner em 05/09, somente **Bloco 38 — D00 — V2-035c — decisão de Frota de Manifestos, Sol Ultra** foi promovido; D03/D04 aguardam a decisão e não foram iniciadas.

- **Data/hora e trabalho executado:** 2026-09-04 23:24:00 -03:00; Bloco 36 — lote decisório corretivo local P01 + V03 para Manifestos/Data Export 6399, por autorização explícita do owner e sem iniciar V04.
- **Estado do trabalho:** <code>P01=COMPLETE_LOCAL_CONSERVATIVE_FAIL_CLOSED</code> e <code>V03/V2-026a=COMPLETE_LOCAL_DECISION_ONLY</code>. Somente os dois subitens delimitados fecham. Os pais V2-009b e V2-026 permanecem abertos; V04 foi apenas promovida como único próximo <code>STATUS=AGORA</code> e continua <code>NOT_STARTED</code>.
- **Evidência V2 analisada:** contrato 6399 e seis fixtures em <code>docs/catalogos/contratos-esl-6399/</code>; catálogo de identidade 6399 e baseline de identidade da primeira onda; ADR 0018; matriz, manifesto e regras V2-017a; <code>STATES.md</code>, trilha e seus validators. A revisão corrigiu o path sintético <code>pick_sequence_code</code>, estabilizou os dois envelopes aceitos e separou raiz, pick, MDF-e, status replicado, presença, frescor, competência e reducers.
- **Evidência legada analisada somente em leitura:** <code>DataExportPaginator.java</code>, <code>ManifestoDTO.java</code>, <code>ManifestoMapper.java</code>, <code>Deduplicator.java</code>, <code>ManifestoRepository.java</code>, <code>AbstractRepository.java</code>, <code>ManifestoEntity.java</code>; tabelas 003 e 032; procedure 005; views 018 e 025; migration 057; validators SQL 032, 037, 044 e 045; e <code>docs/legado/pipelines-antigos/02-apis/dataexport/manifestos.md</code>, SHA-256 <code>0993449c8e3a87ebaffea5c5fde0c8a31b1d399d53218debdadad3e20dcf5ef9</code>. O corpus foi processado apenas em agregados, sem emitir valores: 228 linhas/100 raízes/193 picks escopados/43 MDF-es escopados, 80 pares número+chave, 148 linhas sem o par e com status raiz presente, zero assimetria e zero divergência de status por raiz.
- **Decisão P01:** paths record-relative <code>/sequence_code</code>, <code>/mft_pfs_pck_sequence_code</code>, <code>/mft_mfs_key</code> e <code>/mft_mfs_number</code>; raiz escopada pela tuple explícita, pick por raiz+inteiro e MDF-e por raiz+chave textual de 44 dígitos. Número é atributo, <code>mdfe_status</code> é escalar da raiz replicado e nenhum deles vira identidade. Replay reutiliza a chave natural; colisão/rekey/conflito residual e assimetria número/chave são quarentena; componente ausente/nulo sem sinal material significa zero filho. Cardinalidade é 0..N sem cap inferido. Coocorrência, ordem e hash não criam identidade nem relação.
- **Decisão V03:** toda folha conserva <code>ABSENT/NULL/VALUE</code>, bruto, tipado, parse state, path e proveniência; dedupe/promoção usam o mesmo frescor <code>finished_at → closed_at → departured_at → created_at</code> em UTC. Reducers rodam somente na coorte vencedora e antes de classificar empate; não há stale backfill, keep-last, <code>SUM</code>, <code>MAX</code> genérico ou score. MAN-07 mantém zero, complementa somente um valor único dentro da coorte e deriva <code>capacidadeKg</code> do mesmo sinal de capacidade. Competência usa saída válida e fallback de criação somente por <code>ABSENT/NULL</code>. Candidatos Manifesto→Coleta ficam append-only para V2-046a, sem relação materializada; ausência não produz sweep/delete/desativação/publicação.
- **Arquivos alterados por este bloco:** <code>STATES.md</code>; contrato/README e fixtures sintéticas 6399; <code>docs/catalogos/identidade-manifestos/</code>; ADR 0024; <code>docs/catalogos/manifestos-v2-026/decisao-v03.json</code>; os três artefatos de portabilidade e seu gerador/casos/validator; validators contratuais, de identidade e V03; e, somente depois deste estado, trilha/validator da trilha. Nenhum arquivo Java, SQL, migration, configuração de runtime ou arquivo do legado foi alterado pelo Bloco 36.
- **Testes e integridade:** passaram <code>Test-DataExport6399ContractCatalog.ps1</code>, os seis wrappers de identidade 6399/8656/10633/8636/4924/6392, <code>Test-ManifestosV2026DecisionCatalog.ps1</code>, <code>Test-PortabilityCatalog.ps1 -VerifyGenerated</code>, <code>Test-Gpt56ChatTrail.ps1</code>, <code>Test-OfflineSecretScan.ps1</code>, <code>Invoke-OfflineSecretScan.ps1</code> e <code>git diff --check</code>. Maven não foi executado: este bloco não alterou Java; as mudanças Java já visíveis pertenciam ao working tree preexistente e foram preservadas.
- **Limitações factuais:** a evidência local não prova schema/fingerprint atual do fornecedor, unicidade global, estabilidade/rekey cross-window, tenant no payload, máximos remotos, timezone civil, snapshot/completude, relação Manifesto→Coleta, paridade, publicação, sweep, deploy ou cutover. V2-041 permanece <code>EXTERNAL_HOLD</code>; nenhuma rede, API, <code>curl</code>, credencial, <code>.env</code>, payload novo, banco, SQLCMD, deploy, commit ou push foi usado.
- **Preservação do working tree:** repositório confirmado como <code>etl-extracao-dados-v2</code>, branch <code>main</code>, HEAD <code>0b910432f12d81a306072e24aa44885da94c62a1</code>. O lote começou sobre working tree já sujo; mudanças preexistentes de database, Cotações, Coletas, orquestração e Java foram mantidas integralmente. O legado permaneceu em <code>main</code>, HEAD <code>b3b3a5546f4c866a3ad198095525753ce8ff7a40</code>, com as três mudanças preexistentes <code>.gitignore</code>, <code>AGENTS.md</code> e remoção de <code>states.md</code> intocadas.
- **Próximo bloco permitido:** **Bloco 37 — V2-026 — executar somente a vertical Manifestos 6399 em GPT-5.6 Terra XHigh (rota V04).** Este handoff não a executa e não autoriza V2-046a, V2-012a/b, V2-013, V2-025d, V2-035a externo, V2-037, V2-047, V2-050, V2-038, relação, publicação, sweep ou ação externa.

- **Data/hora e trabalho executado:** 2026-09-04 22:19:22 -03:00; Bloco 35 — V2-027 — Cotações 6906 completa em sombra.
- **Estado do trabalho:** <code>IMPLEMENTADA_EM_SHADOW_ROLLBACK_ONLY</code>. V2-027 foi concluída no escopo autorizado; não existe sucessor elegível para <code>STATUS=AGORA</code> sem inventar uma dependência satisfeita.
- **Arquivos, migrations e objetos SQL:** foram concluídos `docs/catalogos/cotacoes-v2-027/`, `V011__create_cotacoes_shadow_vertical.sql`, `database/manifest/cotacoes-shadow-vertical.{json,sha256}`, baseline, V002 allowlist, manifestos/fingerprints e validators 005/040/041, além dos mappers, batches, gateways JDBC e testes de Cotações. O ajuste final de V011 torna a observação de presença idempotente no retry. O exercício materializou apenas dentro do rollback `stg.cotacao_record`, `ctl.cotacao_promotion_result`, `core.cotacao`, `recon.cotacao_root_presence_observation`, `stg.usp_stage_cotacao_record` e `core.usp_apply_reconcile_publish_cotacoes`; nenhum objeto, dado ou histórico Flyway permaneceu.
- **Runtime e testes:** mapper, value objects/batch e gateways JDBC de Cotações têm testes unitários/contratuais; os gateways chamam somente procedures fechadas e a página fica limitada a 1.000 registros. Passaram o exercício V041 com validators 005 e 040, `Test-CotacoesV2027ShadowVertical.ps1`, a prova de duas sessões `Test-CotacoesShadowConcurrency.ps1`, `Test-SchemaFoundationManifest.ps1`, `Test-ProgressiveDataGate.ps1`, scanner offline, `git diff --check` e `Maven --offline clean verify` com Java 17 (577 testes, zero falha/erro). A trilha foi sincronizada após esta atualização de estado e o alvo confirmou rollback integral.
- **Limitações factuais e próximo gate:** a fundação, a referência `QUOTE_TARIFF` e os dados do V041 existiram exclusivamente no rollback e foram sintéticos; isso não prova paridade, release tarifária operacional, snapshot/completude, publicação ou operação externa. Não houve rede, API, credencial, `.env`, payload real, matriz legada, deploy, commit, push, publicação, sweep, integração externa ou alteração do legado. A fila fica sem <code>STATUS=AGORA</code> até uma dependência hoje bloqueada receber a evidência ou autorização nominal exigida.

- **Data/hora e trabalho executado:** 2026-09-04 18:40:39 -03:00; Bloco 34 — V2-009b — lote offline de decisões de identidade e grão para Manifestos 6399, Localização 8656, Inventário 10633, Contas a Pagar 8636, Faturas por Cliente 4924 e Sinistros 6392, com P01 como rota de entrada e P07–P11 incorporadas ao mesmo lote por ordem explícita.
- **Estado do trabalho:** <code>PARCIAL_LOCAL_COM_CINCO_DECISOES_ABERTAS</code>. Cada entidade recebeu decisão e evidência próprias; somente V2-009b/8656 fecha como <code>COMPLETE_LOCAL_BOUNDED</code>. V2-009b agregada permanece aberta porque 6399, 10633, 8636, 4924 e 6392 continuam <code>UNRESOLVED</code> ou <code>BLOCKED</code> sem a prova exigida.
- **Tarefas efetivamente concluídas:** foram criados os seis catálogos independentes <code>docs/catalogos/identidade-manifestos/</code>, <code>identidade-localizacao-cargas/</code>, <code>identidade-inventario/</code>, <code>identidade-contas-a-pagar/</code>, <code>identidade-faturas-por-cliente/</code> e <code>identidade-sinistros/</code>, cada qual com README e manifesto versionado. Também foram criados o gate comum <code>scripts/validation/Test-DataExportV2009bIdentityCatalog.ps1</code> e seis wrappers por template. Os catálogos registram separadamente source/canonical/business keys, unicidade, estabilidade temporal, tenant scope, cardinalidade, aliases/rekey/colisão/replay, raiz/filhos físicos e limites de evidência; fingerprints semântico/release, READMEs, fixtures e evidências legadas decisivas ficam ancorados por SHA-256. O gate recalcula ainda os quatro fingerprints dos contratos V2-025b vinculados.
- **Tarefas parciais e o que ainda falta:** 6399 aceita a raiz escopada por <code>/sequence_code</code> sob MAN-01, mas paths/chaves/cardinalidade de pick e MDF-e não foram provados; 10633 mantém raiz, componente frete/minuta e mapping de invoices sem identidade/shape/cardinalidade suficiente; 8636 não publicou ID da raiz, tem wire type inconclusivo na candidata de parcela e contraevidência à chave da linha física; 4924 fecha apenas a linha escopada por <code>/id</code>, não o título lógico, aliases ou crosswalks; 6392 não resolve <code>/sequence_code</code> contra o grão composto legado nem os papéis/cardinalidades dos componentes. As evidências de desbloqueio estão explícitas em cada catálogo. V2-026/V2-028/V2-031/V2-029/V2-030/V2-032 e V2-009d não foram iniciadas.
- **Arquivos alterados por este bloco:** criados os doze documentos dos seis catálogos e os sete validadores PowerShell; atualizados <code>STATES.md</code>, <code>docs/runbooks/trilha-de-chats-gpt-5-6.md</code> e <code>scripts/validation/Test-Gpt56ChatTrail.ps1</code>. Nenhum Java, configuração de runtime, migration, schema, mapper, staging, SQL, fixture contratual preexistente ou arquivo do legado foi alterado por este bloco.
- **Migrations e objetos SQL afetados:** nenhum. Banco não foi acessado; não houve DDL, DML, migration, schema, tabela, view, procedure, índice, role ou grant criado/alterado.
- **Testes, scanners e comandos executados:** os seis wrappers <code>Test-DataExport{6399,8656,10633,8636,4924,6392}IdentityCatalog.ps1</code> passaram individualmente, incluindo JSON estrito, vocabulários fechados, decisões por entidade, fingerprints, limites, contraevidências, fixtures exclusivamente sintéticas e hashes da evidência estática. Os quatro gates contratuais existentes para 10633, 8636, 4924 e 6392 também passaram; 6399 e 8656 tiveram os mesmos fingerprints de contrato recalculados pelo gate comum. <code>Test-Gpt56ChatTrail.ps1</code> passou com 35 marcos, 208 fatias abertas, 35/95 checkboxes concluídos e uma única rota <code>AGORA</code>. <code>Test-OfflineSecretScan.ps1</code> passou os nove casos e <code>Invoke-OfflineSecretScan.ps1</code> passou com 752 candidatos, 751 textos, um binário verificado e zero finding, oversized ou conteúdo não inspecionado. <code>git diff --check</code> passou. A suíte Java não foi executada porque o bloco é exclusivamente documental/JSON/PowerShell e não possui consumidor de runtime.
- **Decisões e ADRs produzidos:** nenhum ADR novo. O ADR 0018 e o contrato de identidade da primeira onda foram aplicados com canonical surrogate, tuple escopada, comparação exata, replay/quarentena fail-closed e proibição de inferir tenant, alias, relação ou rekey. Somente a decisão local limitada de 8656 autoriza fechar seu subcheckbox; nenhuma decisão autoriza schema, mapper, staging, promoção, publicação, sweep ou cutover.
- **Evidências sanitizadas e limites:** foram relidos integralmente os documentos de governança exigidos, os seis manifestos contratuais V2-025b, suas fixtures selecionadas exclusivamente sintéticas, o ADR 0018, o catálogo de identidade da primeira onda e os trechos estáticos necessários do legado. A evidência sustenta apenas os limites escritos nos seis catálogos: não prova unicidade global, estabilidade temporal, tenant no payload ou completude, e não transforma coocorrência em relação. Nenhum <code>.env</code>, credencial, payload real, ID real, cursor ou documento de negócio foi lido ou persistido; não houve rede de fonte/produção.
- **Bloqueios externos, alcance, owner e condição:** V2-041 permanece em <code>EXTERNAL_HOLD</code> e continua bloqueando segredo, rede, V2-025d, release, deploy e cutover até rotação, invalidação e continuidade comprovadas. Completude ausente mantém sweep/desativação/cutover bloqueados em todas as seis entidades. As cinco subdecisões abertas só retornam mediante a evidência própria enumerada nos respectivos manifestos; uma não bloqueia silenciosamente as demais.
- **Estado do working tree e preservação:** repositório confirmado como <code>etl-extracao-dados-v2</code>, branch <code>main</code>, HEAD <code>0b910432f12d81a306072e24aa44885da94c62a1</code>. O working tree já continha mudanças dos blocos anteriores; todas foram preservadas e nenhuma alteração alheia ao Bloco 34 foi revertida ou sobrescrita. O legado foi usado somente em leitura estática. Não houve commit, push, deploy, publicação ou ação externa.
- **Primeiro próximo passo executável:** abrir o próximo chat em <code>GPT-5.6 Terra — XHigh</code> e executar somente V2-027 para Cotações 6906 completa em sombra. As decisões abertas deste lote não voltam à fila sem evidência nova; a promoção documental do seletor não executou V2-027.
- **Número e identificação exata do próximo bloco permitido:** **Bloco 35 — V2-027 — Cotações 6906 completa em sombra**.

- **Data/hora e trabalho executado:** 2026-09-04 13:53:29 -03:00; Bloco 30 — V2-009b — identidade e grão 6906 (Cotações), por repriorização explícita do candidato <code>P06</code>.
- **Estado do trabalho:** <code>COMPLETO_LOCAL</code>. A fatia fechou somente a decisão de source key/grão no escopo documental e sintético caracterizado; não implementou a vertical.
  - **Tarefas efetivamente concluídas:** foram criados <code>docs/catalogos/identidade-cotacoes/README.md</code>, <code>manifesto.json</code> e <code>scripts/validation/Test-DataExport6906IdentityCatalog.ps1</code>. O catálogo vincula a decisão ao fingerprint de release do contrato <code>V2-025b/6906</code>, fixa <code>/sequence_code</code> como source key inteira e escopada, mantém <code>canonical_id</code> como surrogate futuro e declara a raiz uma cotação por tuple de registry. Ele também fixa ausência de alias/crosswalk/filho comprovado, quarentena fail-closed, limites de replay/rekey e a separação entre repetição de raiz e promoção/frescor posterior.
- **Tarefas parciais e o que ainda falta:** unicidade e cardinalidade só foram observadas no escopo de uma janela sanitizada de três linhas/três candidatos; estabilidade temporal, tenant no payload, snapshot, semântica de <code>per</code>, completude e ausência permanecem não provados. V2-027 continua dona de domínio, presença, frescor, dedupe/reducer, staging/core, schema e implementação; V2-009d continua dona do enforcement físico e V2-025d segue externo/bloqueado para ampliar evidência remota.
- **Arquivos alterados:** criados os dois documentos do catálogo de identidade de Cotações e seu validador; atualizados <code>STATES.md</code>, <code>docs/runbooks/trilha-de-chats-gpt-5-6.md</code> e <code>scripts/validation/Test-Gpt56ChatTrail.ps1</code>. Nenhum fonte Java, configuração de runtime, migration, script SQL, fixture de contrato existente ou arquivo do legado foi alterado.
- **Migrations e objetos SQL afetados:** nenhum. Banco e SQL Server não foram acessados; não houve DDL, DML, migration, schema, tabela, view, procedure, índice, role ou grant criado/alterado.
- **Testes, scanners e comandos executados:** antes da mudança, <code>Test-Gpt56ChatTrail.ps1</code> passou com 27 marcos, 215 fatias abertas e 27/87 checkboxes; <code>Invoke-OfflineSecretScan.ps1</code> passou com 693 candidatos, 692 textos, um binário verificado e zero finding. Após a mudança, o validador novo, a trilha e o scanner foram reexecutados e passaram conforme o handoff final. A suíte Java não foi executada, pois a fatia alterou somente Markdown/JSON e validadores PowerShell sem consumidor de runtime.
- **Decisões e ADRs produzidos:** nenhum ADR. A decisão aplica o registry já ratificado e não aprova tenant global, rekey, estabilidade temporal, completude, regra de frescor, reducer, tarifa, schema, publicação ou cutover.
- **Evidências sanitizadas:** foram relidos integralmente <code>STATES.md</code>, a trilha, os <code>AGENTS.md</code> V2/legado e <code>../CONTEXTO_GLOBAL.md</code>. A caracterização estática limitada releu o contrato/fixtures 6906 V2 e, no legado, configuração, DTO, mapper, deduplicador, repositório, tabela e documentação de Cotações. Nenhum <code>.env</code> ou credencial foi lido; nenhum payload, ID, cursor, documento ou dado de negócio foi persistido, versionado ou emitido. Não houve rede de fonte/produção, banco, deploy, release, restart, commit ou push.
- **Bloqueios externos, alcance, owner e condição:** V2-041 continua bloqueando segredo/rede/V2-025d/release/deploy/cutover até rotação, invalidação e continuidade comprovadas. A ausência de garantia do fornecedor ou de oráculo independente mantém 6906 bloqueado para completude, sweep, desativação e cutover. A ausência de tenant no payload não foi preenchida por inferência: cada futura execução deve prover <code>source_instance</code> e <code>tenant_scope</code> explícitos e rejeitar sentinels globais.
- **Estado do working tree e preservação:** V2 permanece em <code>main</code>, sem HEAD, remote ou commit, com arquivos untracked. O legado foi acessado somente em leitura estática e não recebeu alteração. Nenhuma mudança preexistente do usuário foi revertida, sobrescrita ou perdida; projetos de dashboards não foram lidos.
- **Primeiro próximo passo executável:** permanece o próximo bloco oficial: abrir o chat em <code>GPT-5.6 Sol — Ultra</code> e executar somente V2-009b para identidade e grão 6399 (Manifestos), com prova de unicidade, estabilidade, tenant scope e cardinalidade. O chat não deve iniciar V2-026, V2-025d, rede, credencial ou banco no mesmo bloco.
- **Número e identificação exata do próximo bloco permitido:** **Bloco 24 — V2-009b — somente identidade e grão 6399 (Manifestos)**.

- **Data/hora e trabalho executado:** 2026-09-04 13:38:24 -03:00; Bloco 29 — V2-025b — contrato offline Data Export 6392 (Sinistros), por repriorização explícita do candidato <code>P05</code>.
- **Estado do trabalho:** <code>COMPLETO_LOCAL</code>. A fatia entregou somente o contrato documental/sintético e preservou decisões de identidade, temporalidade, filhos/relações e financeiro para os gates próprios.
- **Tarefas efetivamente concluídas:** foram criados <code>docs/catalogos/contratos-esl-6392/</code> com README, manifesto versionado e seis fixtures JSON sintéticas, além de <code>scripts/validation/Test-DataExport6392ContractCatalog.ps1</code>. O contrato classifica transporte, metadata, envelope, filtro, tradução temporal, ordenação, <code>per</code>, candidata de raiz, relações minuta/invoice pendentes, paginação, timezone, limites/erros e completude. Os SHA-256 das fixtures e os fingerprints semântico, metadata, resposta e release foram calculados e validados localmente.
- **Tarefas parciais e o que ainda falta:** V2-025 permanece aberta para V2-025c condicional e V2-025d externo. Para Sinistros, V2-009b ainda precisa decidir/provar source key, unicidade, estabilidade, tenant scope, grão, relações, filhos e cardinalidade; V2-032 continua sem iniciar. V2-025b não decide hash legado, horas, timezone de campos, valores financeiros, tipo/solução, frescor, dedupe, reducer, schema, fato ou contrato consumidor.
- **Arquivos alterados:** criados <code>docs/catalogos/contratos-esl-6392/README.md</code>, <code>manifesto.json</code>, seis fixtures sintéticas e <code>scripts/validation/Test-DataExport6392ContractCatalog.ps1</code>; atualizados <code>STATES.md</code>, <code>docs/runbooks/trilha-de-chats-gpt-5-6.md</code> e <code>scripts/validation/Test-Gpt56ChatTrail.ps1</code>. Nenhum fonte Java, configuração, migration, script SQL ou arquivo do legado foi alterado.
- **Migrations e objetos SQL afetados:** nenhum. Banco e SQL Server não foram acessados; não houve DDL, DML, migration, schema, tabela, view, procedure, índice, role ou grant criado/alterado.
- **Testes, scanners e comandos executados:** <code>pwsh -NoProfile -File .\scripts\validation\Test-DataExport6392ContractCatalog.ps1</code> confirmou <code>PASS</code> para 13 aspectos, seis fixtures, SHA-256, fingerprints, vocabulários, bloqueios de capacidade e a forma exclusivamente sintética de duas linhas/dois candidatos de raiz sem <code>id</code>. <code>pwsh -NoProfile -File .\scripts\validation\Test-Gpt56ChatTrail.ps1</code> confirmou <code>PASS</code> para 27 marcos, 215 fatias abertas, 27/87 checkboxes e uma única rota <code>AGORA</code>. <code>pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1</code> confirmou <code>PASS</code> com 693 candidatos, 692 textos, um binário verificado, zero finding, oversized ou conteúdo não inspecionado. A suíte Java não foi repetida, pois o bloco alterou somente Markdown/JSON documental, fixture e validadores PowerShell sem consumidor de runtime.
- **Decisões e ADRs produzidos:** nenhum ADR. O catálogo registra candidatos e bloqueios, mas não toma decisão de identidade, grão, relações, filhos, temporalidade, valores, frescor, dedupe, reducer, schema, fato ou contrato consumidor; esses temas continuam nos gates próprios e nas tarefas posteriores.
- **Evidências sanitizadas:** foram relidos integralmente <code>STATES.md</code>, a trilha, <code>AGENTS.md</code>, <code>../CONTEXTO_GLOBAL.md</code> e, para caracterização estática limitada, a documentação, constantes, cliente, extractor, mapper, DTO, entidade, tabela e view de Sinistros do legado, sem copiar conteúdo de negócio para a V2. Nenhum <code>.env</code> ou credencial foi lido, e nenhum payload, ID, cursor, documento ou dado de negócio foi persistido, versionado ou usado no catálogo. Não houve rede de fonte/produção, banco, deploy, release, restart, commit ou push.
- **Bloqueios externos, alcance, owner e condição:** os holds existentes não mudaram. V2-041 continua bloqueando segredo/rede/V2-025d/release/deploy/cutover até rotação, invalidação e continuidade comprovadas; a ausência de garantia de fornecedor ou oráculo independente mantém 6392 bloqueado para completude, sweep, desativação e cutover. Nenhum hold externo bloqueava a formalização offline desta fatia.
- **Estado do working tree e preservação:** V2 permanece em <code>main</code>, sem HEAD, remote ou commit, com todos os arquivos untracked. O legado foi acessado somente em leitura estática e não recebeu alteração. Nenhuma mudança preexistente do usuário foi revertida, sobrescrita ou perdida; projetos de dashboards não foram lidos.
- **Primeiro próximo passo executável:** permanece o próximo bloco oficial: abrir o chat em <code>GPT-5.6 Sol — Ultra</code> e executar somente V2-009b para identidade e grão do 6399, com prova de unicidade, estabilidade, tenant scope e cardinalidade. O chat não deve iniciar V2-026, V2-025d, rede, credencial ou banco no mesmo bloco.
- **Número e identificação exata do próximo bloco permitido:** **Bloco 24 — V2-009b — somente identidade e grão 6399 (Manifestos)**.

- **Data/hora e trabalho executado:** 2026-09-04 13:27:38 -03:00; Bloco 28 — V2-025b — contrato offline Data Export 4924 (Faturas por Cliente), por repriorização explícita do candidato <code>P04</code>.
- **Estado do trabalho:** <code>COMPLETO_LOCAL</code>. A fatia entregou somente o contrato documental/sintético e preservou decisões fiscais, de identidade e de crosswalk para os gates próprios.
- **Tarefas efetivamente concluídas:** foram criados <code>docs/catalogos/contratos-esl-4924/</code> com README, manifesto versionado e seis fixtures JSON sintéticas, além de <code>scripts/validation/Test-DataExport4924ContractCatalog.ps1</code>. O contrato classifica transporte, metadata, envelope, filtro, tradução temporal, ordenação, <code>per</code>, candidato de linha, título/documento/frete pendentes, paginação, timezone, limites/erros e completude. Os SHA-256 das fixtures e os fingerprints semântico, metadata, resposta e release foram calculados e validados localmente.
- **Tarefas parciais e o que ainda falta:** V2-025 permanece aberta para o contrato independente 6392, V2-025c condicional e V2-025d externo. Para Faturas por Cliente, V2-009b ainda precisa decidir/provar source key, título lógico, unicidade, estabilidade, tenant scope, aliases/rekey e cardinalidade; V2-030 continua sem iniciar. V2-025b não decide precedência fiscal, CNPJ, status, valores, frescor, dedupe, reducer, schema, fato ou contrato consumidor.
- **Arquivos alterados:** criados <code>docs/catalogos/contratos-esl-4924/README.md</code>, <code>manifesto.json</code>, seis fixtures sintéticas e <code>scripts/validation/Test-DataExport4924ContractCatalog.ps1</code>; atualizados <code>STATES.md</code>, <code>docs/runbooks/trilha-de-chats-gpt-5-6.md</code> e <code>scripts/validation/Test-Gpt56ChatTrail.ps1</code>. Nenhum fonte Java, configuração, migration, script SQL ou arquivo do legado foi alterado.
- **Migrations e objetos SQL afetados:** nenhum. Banco e SQL Server não foram acessados; não houve DDL, DML, migration, schema, tabela, view, procedure, índice, role ou grant criado/alterado.
- **Testes, scanners e comandos executados:** <code>pwsh -NoProfile -File .\scripts\validation\Test-DataExport4924ContractCatalog.ps1</code> confirmou <code>PASS</code> para 13 aspectos, seis fixtures, SHA-256, fingerprints, vocabulários, bloqueios de capacidade e a forma exclusivamente sintética de três linhas/três candidatos inteiros sem <code>unique_id</code>. <code>pwsh -NoProfile -File .\scripts\validation\Test-Gpt56ChatTrail.ps1</code> confirmou <code>PASS</code> para 26 marcos, 216 fatias abertas, 26/86 checkboxes e uma única rota <code>AGORA</code>. <code>pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1</code> confirmou <code>PASS</code> com 684 candidatos, 683 textos, um binário verificado, zero finding, oversized ou conteúdo não inspecionado. A suíte Java não foi repetida, pois o bloco alterou somente Markdown/JSON documental, fixture e validadores PowerShell sem consumidor de runtime.
- **Decisões e ADRs produzidos:** nenhum ADR. O catálogo registra candidatos e bloqueios, mas não toma decisão fiscal, de identidade, grão, relação, crosswalk, frescor, dedupe, reducer, schema, fato ou contrato consumidor; esses temas continuam nos gates próprios e nas tarefas posteriores.
- **Evidências sanitizadas:** foram relidos integralmente <code>STATES.md</code>, a trilha, <code>AGENTS.md</code>, <code>../CONTEXTO_GLOBAL.md</code> e, para caracterização estática limitada, a documentação Data Export, extractor, mapper, DTO, tabela e view de Faturas por Cliente do legado, sem copiar conteúdo de negócio para a V2. Nenhum <code>.env</code> ou credencial foi lido, e nenhum payload, ID, cursor, documento ou dado de negócio foi persistido, versionado ou usado no catálogo. Não houve rede de fonte/produção, banco, deploy, release, restart, commit ou push.
- **Bloqueios externos, alcance, owner e condição:** os holds existentes não mudaram. V2-041 continua bloqueando segredo/rede/V2-025d/release/deploy/cutover até rotação, invalidação e continuidade comprovadas; a ausência de garantia de fornecedor ou oráculo independente mantém 4924 bloqueado para completude, sweep, desativação e cutover. Nenhum hold externo bloqueava a formalização offline desta fatia.
- **Estado do working tree e preservação:** V2 permanece em <code>main</code>, sem HEAD, remote ou commit, com todos os arquivos untracked. O legado foi acessado somente em leitura estática e não recebeu alteração. Nenhuma mudança preexistente do usuário foi revertida, sobrescrita ou perdida; projetos de dashboards não foram lidos.
- **Primeiro próximo passo executável:** permanece o próximo bloco oficial: abrir o chat em <code>GPT-5.6 Sol — Ultra</code> e executar somente V2-009b para identidade e grão do 6399, com prova de unicidade, estabilidade, tenant scope e cardinalidade. O chat não deve iniciar V2-026, V2-025d, rede, credencial ou banco no mesmo bloco.
- **Número e identificação exata do próximo bloco permitido:** **Bloco 24 — V2-009b — somente identidade e grão 6399 (Manifestos)**.

- **Data/hora e trabalho executado:** 2026-09-04 13:02:09 -03:00; Bloco 27 — V2-025b — contrato offline Data Export 8636 (Contas a Pagar), por repriorização explícita do candidato <code>P03</code>.
- **Estado do trabalho:** <code>COMPLETO_LOCAL</code>. A fatia entregou somente o contrato documental/sintético e preservou todas as decisões financeiras, de identidade e de reducer para os gates próprios.
- **Tarefas efetivamente concluídas:** foi criado <code>docs/catalogos/contratos-esl-8636/</code> com README, manifesto versionado e seis fixtures JSON sintéticas, além de <code>scripts/validation/Test-DataExport8636ContractCatalog.ps1</code>. O contrato classifica transporte, metadata, envelope, filtros conjuntos, tradução temporal, ordenação, <code>per</code>, identidade ausente da raiz, candidata de parcela, paginação, timezone, limites/erros e completude. Os SHA-256 das fixtures e os fingerprints semântico, metadata, resposta e release foram calculados e validados localmente.
- **Tarefas parciais e o que ainda falta:** V2-025 permanece aberta para os contratos independentes 4924 e 6392, V2-025c condicional e V2-025d externo. Para Contas a Pagar, V2-009b ainda precisa decidir/provar identidade e grão de raiz/parcela, unicidade, estabilidade, tenant scope, relacionamento e cardinalidade; V2-029 continua sem iniciar. V2-025b não decide regra financeira, status, competência, valores, catálogo derivado, frescor, dedupe ou reducer.
- **Arquivos alterados:** criados <code>docs/catalogos/contratos-esl-8636/README.md</code>, <code>manifesto.json</code>, seis fixtures sintéticas e <code>scripts/validation/Test-DataExport8636ContractCatalog.ps1</code>; atualizado <code>STATES.md</code>. Nenhum fonte Java, configuração, migration, script SQL ou arquivo do legado foi alterado.
- **Migrations e objetos SQL afetados:** nenhum. Banco e SQL Server não foram acessados; não houve DDL, DML, migration, schema, tabela, view, procedure, índice, role ou grant criado/alterado.
- **Testes, scanners e comandos executados:** <code>pwsh -NoProfile -File .\scripts\validation\Test-DataExport8636ContractCatalog.ps1</code> confirmou <code>PASS</code> para 13 aspectos, seis fixtures, SHA-256, fingerprints, vocabulários, bloqueios de capacidade e a forma exclusivamente sintética de cinco linhas/cinco candidatos sem ID da raiz. <code>pwsh -NoProfile -File .\scripts\security\Test-OfflineSecretScan.ps1</code> passou os nove casos. <code>pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1</code> passou com 675 candidatos, 674 textos, um binário verificado, zero finding, oversized ou conteúdo não inspecionado. A suíte Java não foi repetida, pois o bloco alterou somente Markdown/JSON documental, fixture e validador PowerShell sem consumidor de runtime.
- **Decisões e ADRs produzidos:** nenhum ADR. O catálogo registra candidatos e bloqueios, mas não toma decisão financeira, de identidade, grão, relação, frescor, dedupe, reducer, parsing, schema, fato ou contrato consumidor; esses temas continuam nos gates próprios e nas tarefas posteriores.
- **Evidências sanitizadas:** foram relidos integralmente <code>STATES.md</code>, a trilha, <code>AGENTS.md</code>, <code>../CONTEXTO_GLOBAL.md</code> e, para caracterização estática limitada, as constantes, cliente, extractor, DTO, entidade, tabela, view e configuração local de Contas a Pagar do legado, sem copiar conteúdo de negócio para a V2. Nenhum <code>.env</code> ou credencial foi lido, e nenhum payload, ID, cursor, documento ou dado de negócio foi persistido, versionado ou usado no catálogo. Não houve rede de fonte/produção, banco, deploy, release, restart, commit ou push.
- **Bloqueios externos, alcance, owner e condição:** os holds existentes não mudaram. V2-041 continua bloqueando segredo/rede/V2-025d/release/deploy/cutover até rotação, invalidação e continuidade comprovadas; a ausência de garantia de fornecedor ou oráculo independente mantém 8636 bloqueado para completude, sweep, desativação e cutover. Nenhum hold externo bloqueava a formalização offline desta fatia.
- **Estado do working tree e preservação:** V2 permanece em <code>main</code>, sem HEAD, remote ou commit, com todos os arquivos untracked. O legado foi acessado somente em leitura estática e não recebeu alteração. Nenhuma mudança preexistente do usuário foi revertida, sobrescrita ou perdida; projetos de dashboards não foram lidos.
- **Primeiro próximo passo executável:** permanece o próximo bloco oficial: abrir o chat em <code>GPT-5.6 Sol — Ultra</code> e executar somente V2-009b para identidade e grão do 6399, com prova de unicidade, estabilidade, tenant scope e cardinalidade. O chat não deve iniciar V2-026, V2-025d, rede, credencial ou banco no mesmo bloco.
- **Número e identificação exata do próximo bloco permitido:** **Bloco 24 — V2-009b — somente identidade e grão 6399 (Manifestos)**.

- **Sincronização documental posterior:** 2026-09-04 13:10:33 -03:00; após um desfazer acidental no editor, a trilha GPT-5.6 completa foi restaurada e reconciliada com o Bloco 27. Seus 25 itens históricos marcados correspondem exatamente aos 25 checkboxes concluídos deste estado, incluindo V2-025b/8636 como ROTA=P03, sem fabricar a numeração individual dos Blocos 1–21. As 217 fatias ainda abertas permanecem materializadas, V2-022 continua dividido em V2-022a offline e V2-022b operacional, e o próximo bloco oficial permanece o Bloco 24. O validator <code>scripts/validation/Test-Gpt56ChatTrail.ps1</code> impede divergência de contagens, rotas, status, tarefa atual e referências entre os dois documentos. Esta restauração não concluiu tarefa funcional nova nem consumiu número de bloco.

- **Data/hora e trabalho executado:** 2026-09-04 11:09:43 -03:00; Bloco 26 — V2-025b — contrato offline Data Export 10633 (Inventário), por repriorização explícita do candidato <code>PREP-11</code>.
- **Estado do trabalho:** <code>COMPLETO_LOCAL</code>. A fatia entregou somente o contrato documental/sintético e preservou todas as decisões de identidade, filhos e reducers para os gates próprios.
- **Tarefas efetivamente concluídas:** foi criado <code>docs/catalogos/contratos-esl-10633/</code> com README, manifesto versionado e seis fixtures JSON sintéticas, além de <code>scripts/validation/Test-DataExport10633ContractCatalog.ps1</code>. O contrato classifica transporte, metadata, envelope, filtro, tradução temporal, ordenação, <code>per</code>, chave candidata de raiz, expansão física de mapeamentos de invoices, paginação, timezone, limites/erros e completude. Os SHA-256 das fixtures e os fingerprints semântico, metadata, resposta e release foram calculados e validados localmente.
- **Tarefas parciais e o que ainda falta:** V2-025 permanece aberta para os contratos independentes 8636, 4924 e 6392, V2-025c condicional e V2-025d externo. Para Manifestos, V2-009b ainda precisa decidir/provar identidade, grão, unicidade, estabilidade, tenant scope e cardinalidade; V2-026 continua sem iniciar. Para Cotações, Localização e Inventário, as decisões/provas equivalentes permanecem em suas fatias V2-009b; V2-027/V2-028/V2-031 não foram iniciadas.
- **Arquivos alterados:** criados <code>docs/catalogos/contratos-esl-10633/README.md</code>, <code>manifesto.json</code>, seis fixtures sintéticas e <code>scripts/validation/Test-DataExport10633ContractCatalog.ps1</code>; atualizados <code>STATES.md</code> e <code>docs/runbooks/trilha-de-chats-gpt-5-6.md</code>. Nenhum fonte Java, configuração, migration, script SQL ou arquivo do legado foi alterado.
- **Migrations e objetos SQL afetados:** nenhum. Banco e SQL Server não foram acessados; não houve DDL, DML, migration, schema, tabela, view, procedure, índice, role ou grant criado/alterado.
- **Testes, scanners e comandos executados:** <code>pwsh -NoProfile -File .\scripts\validation\Test-DataExport10633ContractCatalog.ps1</code> confirmou <code>PASS</code> para 13 aspectos, seis fixtures, SHA-256, fingerprints, vocabulários, bloqueios de capacidade e expansão exclusivamente sintética de 27 linhas físicas/três candidatos <code>sequence_code</code>; nenhum código de produção foi executado. <code>pwsh -NoProfile -File .\scripts\security\Test-OfflineSecretScan.ps1</code> passou os nove casos. <code>pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1</code> passou com 665 candidatos, 664 textos, um binário verificado, zero finding, oversized ou conteúdo não inspecionado. A suíte Java não foi repetida, pois o bloco alterou somente Markdown/JSON documental, fixture e validador PowerShell sem consumidor de runtime.
- **Decisões e ADRs produzidos:** nenhum ADR. O catálogo registra candidata e bloqueios, mas não toma decisão de identidade, grão, frescor, dedupe, relationamento, parsing, comprovante, reducer, schema, fato ou contrato consumidor; esses temas continuam nos gates próprios e nas tarefas posteriores.
- **Evidências sanitizadas:** foram relidos integralmente <code>STATES.md</code>, a trilha, <code>AGENTS.md</code>, <code>../CONTEXTO_GLOBAL.md</code> e, para caracterização estática limitada, o DTO, extractor, mapper, tabela, view e configuração local de Inventário do legado, sem copiar conteúdo de negócio para a V2. Nenhum <code>.env</code> ou credencial foi lido, e nenhum payload, ID, cursor, documento ou dado de negócio foi persistido, versionado ou usado no catálogo. Não houve rede de fonte/produção, banco, deploy, release, restart, commit ou push.
- **Bloqueios externos, alcance, owner e condição:** os holds existentes não mudaram. V2-041 continua bloqueando segredo/rede/V2-025d/release/deploy/cutover até rotação, invalidação e continuidade comprovadas; V2-042b/V2-042c continuam bloqueando V2-022 até inputs e adapter de identidade; V2-016b, V2-015d, V2-035a e V2-045b mantêm seus inputs externos próprios. Nenhum deles bloqueia o contrato offline 10633.
- **Estado do working tree e preservação:** V2 permanece em <code>main</code>, sem HEAD, remote ou commit, com todos os arquivos untracked. O legado foi acessado somente em leitura estática e não recebeu alteração. Nenhuma mudança preexistente do usuário foi revertida, sobrescrita ou perdida; projetos de dashboards não foram lidos.
- **Primeiro próximo passo executável:** abrir o próximo chat em <code>GPT-5.6 Sol — Ultra</code> e executar somente V2-009b para identidade e grão do 6399, com prova de unicidade, estabilidade, tenant scope e cardinalidade. O chat não deve iniciar V2-026, V2-025d, rede, credencial ou banco no mesmo bloco.
- **Número e identificação exata do próximo bloco permitido:** **Bloco 24 — V2-009b — somente identidade e grão 6399 (Manifestos)**.

Execução ANA-31/32: modes02 reconciliado PASS9IT/0falhas/erros/skips. V093 preparada para ledger terminal e última observação Raster; baseline V089–093 sincronizado. Qualificação e instalação separadas autorizadas, alvo exato local, DML de provas rollback-only; próximo cenário/JAR e J–N permanecem pendentes.

Concorrência physical-analytic-concurrency-02: 4 IT, Raster passou; três cargas recusaram contenção mas perderam a transação do segundo dono (estado0 em vez1). V094 draft preserva as versões efetivas V059/V067/V078, acrescenta savepoint SQL/TRY-CATCH e XACT_ABORT OFF para isolar recusas. Qualificar/instalar separadamente no alvo local, depois repetir com oráculo inalterado de recuperação.

Composição parcial J: physical-analytic-expansion-composition-04 passou2IT (quatro modos, seis pipelines, hidratação real, MAT03/04 e fixture main MAN). Tentativas01–03 preservadas. V095 preparada para selos de composição em TVP até64, mantendo tabela/gates existentes; qualificar e instalar separadamente no alvo sombra, sem DML de domínio persistido.

ANALYTIC_LOCAL_CANDIDATE: sucessão documental em validação; sem conclusão local presumida.

Sucessão de integridade em qualificação: QUALIFICATION_LOCAL_CANDIDATE. A–N permanece EM_EXECUCAO; não é entrega final.
