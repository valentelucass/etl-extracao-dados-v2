# P06 — revisão offline da campanha local

Revisão técnica concluída; código corrigido e testado offline. P07 elegível
tecnicamente após readback dos gates desta revisão, mas seus efeitos físicos
exigem ordem própria. L permanece EM_EXECUCAO até regressão P07. A, M e N não
recebem aceite; nenhum selo de entrega A–N, revisão humana ou produção é alegado.

## Revisão e proveniência

Rodada privada: `target/P06-REVISAO-POS0202-20260920T225525774Z/`.
Pedido congelado em `request.txt`; revisão inicial em `baseline.json`, bytes em
`before/`, índice e alterações preexistentes em `git-index.sha256`/`git-status.txt`.
HEAD `0b910432f12d81a306072e24aa44885da94c62a1` não representa o worktree:
o inventário é a referência exata. `own-diff.patch` contém apenas esta rodada.

`predecessor-check.json` confere os 12 membros do final-manifest POS0198 e os
17 arquivos do closure. `qualified-evidence-readback.json` vincula resultados,
XMLs, JAR, medições e planos preservados; compara 2076 inputs executáveis do
preflight/P04 e 2077 de P05 contra o inventário inicial P06. O único delta dos
dois primeiros é SequenceScaleIT, alterado e qualificado na preparação/P05.
Os erros iniciais do verificador P06 (tratar esse delta conhecido como drift
imprevisto; procurar hash JAR em campo inexistente no recibo P04) são erros de
reconciliação offline, corrigidos sem repetir campanha ou alterar recibo.

P04:20 ITs; P05:4 ITs nas escalas2/4/8/16. XMLs sem failure/error/skip,
JAR anterior `65487e3b844d3a7e3623be52a762372652716a345ef30e27ce393035c79f6c66`,
agregados246 iguais antes/depois; medições e12 planos de P05 conferidos.
Ledger POS0198 continua fechado e intocado:2/3 reservas,7200/10800s, P04#2
não usada. **Consumo físico desta revisão: zero.** Saldo anterior não é autorização.

## Achados e correções

| ID / severidade | Local e cenário concreto | Invariante, reprodução e correção | Prova e consequência |
| --- | --- | --- | --- |
| P06-01 / média | ColetaTemporalLaboratorySession.close: rollback lança SQLException e physical.close também lança | O finally substituía a causa de rollback pela de close. Teste proxy real do método:4 casos,1 falha antes da correção. try-with-resources mantém rollback primária, close suprimida e limpa tracking em finally | ColetaTemporalLaboratorySessionCloseTest:4 PASS; sucesso, cada falha isolada, dupla falha e fechamento idempotente. Altera JAR: requalificação física de consumidores obrigatória |
| P06-02 / média | Invoke-OfflineSecretScan: ls-files --cached inclui oito arquivos legitimamente removidos | MISSING_CANDIDATE era indistinguível de perda. VerifiedHistoricalRemovals confere hash fixo f23b401f… do manifesto alinhamento, entradas after=null e snapshots before íntegros; apenas ausências desse conjunto são reconhecidas | 17 contraprovas scanner,5 contraprovas de remoções. Arquivo ausente arbitrário continua FAIL; manifesto/snapshot adulterados e snapshot ausente recusados. Nenhuma restauração nem alteração do índice |
| P06-03 / média | StatesExecutionSuccession exigia bytes0165 nos caminhos ativos posteriores | RETOMADA histórica esperada aa9c2b85… versus fechamento POS0198 5b901055…; havia42 diferenças, todas com snapshots originais conferidos. Novo P06ReviewSuccession valida delta explícito, conjunto completo, snapshots, hashes atuais e predecessor fixo e3955db8…; módulo antigo passa a consultar a fotografia anterior | Manifesto novo em docs/catalogos/p06-revisao; testes negativos de escopo, predecessor, before/after, remoção, path, arquivo preservado/novo e inventário. Não reescreve manifests anteriores nem aceita exclusão genérica |
| P06-04 / baixa | Helpers privados fixavam rodada15/09, dependiam de snippet/before ausentes na13/09 e registravam schema102 | Cópias versionadas com RoundName, controlador incorporado, oráculo33 congelado com hash e schema104 lido do payload verificado; originais intactos. Finally contém processo próprio se ocorrer erro no controlador | Test-P06ExecutionHelpers:10 checks offline de parser, raízes/default, oráculo e traversal antes de efeitos; execução física pendente |

Origem das regras técnicas: pedido P06 e AGENTS §§4–8. Não alteram regra de
negócio, fórmula, identidade canônica, schema ou dependência. Responsável de
negócio não informado; revisão realizada por agente, sem aceite humano.

## Inspeção técnica e limites

Foi revisado o delta POS0198 nos seis Java prioritários, junto a
ColetaTemporalLaboratorySession, LocalArtifactSequence/Scenario, PinnedLocalJson,
QualificationSupervisor/Worker, CampaignJournal, QualifiedPackage e consumidores
de oráculos/fixtures. A conexão compartilhada suprime commit da API, recusa
autocommit/escape explícito e reverte ao fechar; não é sandbox contra SQL
arbitrário ou adaptador malicioso. Somente adaptadores internos autorizados entram.

Supervisor vincula intenção, nonce, PID/start, pacote, configuração, journal e
recibo; retomada reconcilia owner antes de executar. Falta de terminal não vira
sucesso. Cancelamento monitora statements reais; deadline/limite de logs e
contenção têm recibos. O worker atual não cria subprocessos; destroyOwned encerra
o filho direto identificado, não prova contenção de descendentes arbitrários.
Controllers Maven usam Kill(true) para sua árvore. P07/P08 devem conferir saída
e ausência de todos os processos próprios, inclusive timeout/erro de fechamento.

LocalArtifactSequence limita cadeia/raízes/páginas, aplica predecessor imediato,
ordem Coletas→Fretes, sourceRevision separada de executionRevision e oráculo por
etapa. Preview executa em savepoint e é revertido; resultado divergente interrompe
etapas e não concede apply. Falha tardia conserva recibos anteriores e preview
terminal zero. Não há retomada de domínio durável após perda da transação.

Oráculos usam expectativas congeladas/regras separadas da leitura SQL obtida.
Compartilham especificação sintética e recursos; são contraprovas locais, sem
independência de fornecedor ou prova de paridade real. Binding verifica runtime,
input e schema; o loader explícito distingue JAR de target/classes, confere
CodeSource e propaga AssertionError. Compartilha apenas o driver JDBC com o
loader Maven para autenticação nativa, restrição test-only. A prova offline
reexecutada valida14 oráculos, A/B e adulterações de JAR/runtime/input/schema.

QualificationJson recusa arquivo não regular, links ancestrais e ADS, resolve
caminho integral sem cache e revalida a cada acesso. PinnedLocalJson calcula o
hash dos mesmos bytes limitados que entrega ao parser. Isso não constitui
atomicidade de toda árvore de filesystem contra escritor hostil concorrente;
pacote/fixtures devem permanecer sob posse exclusiva durante qualificação.
Os testes de junction cobrem troca entre leituras, não uma corrida invisível
no meio de uma chamada. Não foi introduzida alegação de sandbox de filesystem.

SequenceScaleIT admite2/4/8/16 serialmente antes de fixture/I/O, bloqueia em corpo
incompleto ou callback tardio, gera run distinto e compara246 agregados por
escala. Medições são amostras limitadas, não SLO/platô de heap produtivo.

## Matriz de reutilização A–K na revisão corrigida

| Frente | Prova anterior preservada | Cobertura da revisão atual / gate |
| --- | --- | --- |
| A | Baseline0165, sucessão histórica e inventários preservados | Integridade local corrigida; A não fecha por documentação |
| B | 25 testes de LocalArtifactSequenceTest, P03 | Contrato inalterado; reexecutado offline nesta revisão |
| C | A/B sete etapas, IntegralCampaignIT | Histórico SQL preservado; sessão mudou, repetir em P07 e JAR em P08 |
| D | Replay e referência, P03 | Identidade/regras inalteradas; nova sessão/JAR requer P07 |
| E | Agenda e ordem COL→FRE, P03/P04 | Requalificar consumidores na suíte P07 |
| F | SequenceReferenceIT/RecompositionIT, schema104 | Schema e oráculos preservados; repetir com sessão corrigida |
| G | 41 ITs de contraprovas P03 e bindings POS0198 | Contraprovas históricas preservadas; novo runtime/JAR exige reautoria de pins e A/B, sem mudar expectativas |
| H | SequenceFailureIT e IntegralContextIsolationIT | Requalificar erro/isolamento/rollback P07 |
| I | Journal/cancelamento/owner/retomada P04 | Prova antiga válida para bytes antigos; repetir supervisor/cancelamento/retomada com JAR novo |
| J | 33 previews, falha tardia e rollback P04 | Repetir IntegralSweepEdgesIT e supervisor na revisão corrigida |
| K | Quatro escalas/12 planos/246 agregados P05 | Medições históricas preservadas; não transferir QUALIFIED_SHADOW ao JAR corrigido; repetir SequenceScaleIT em P07 |

Aceites históricos B–K não são apagados. O campo reviewP06 da matriz registra
a necessidade atual de requalificação. A/L/M/N continuam EM_EXECUCAO e os
contadores permanecem39/45 e67/115.

## Verificação offline executada

- Maven offline/JDK17, sem V2_SHADOW_JDBC_URL nem perfil shadow: red-close4/1falha,
  green-close68/0falha/0erro/0skip; Enforcer, Spotless, Checkstyle, javac e regras
  arquiteturais. Logs, comandos e XML vermelho preservados na rodada.
- Controlador histórico Invoke-Build.ps1, ArtifactDirected, teto240s, seleção
  canônica de cinco classes:18/18 PASS, Maven3m40s, sem timeout; novo JAR e14
  oráculos/A–B. Resultado em p06-review-artifact-01 da campanha15/09.
- Scanner17 contraprovas; remoções5 contraprovas; sucessão e trilha/preparação,
  JSON/UTF-8/diff/readback finais em final-validation e closure.json da rodada.
  Resultados históricos vermelhos permanecem em trail-before.log e POS0198.
- Test-P06ExecutionHelpers:10 checks, ValidateOnly sem criar tentativa, SQL ou
  processo; não equivale a teste do corpo físico dos helpers.
- P07 verify completo, cobertura completa, IT SQL e P08 extraído não executados.

## Pacote de execução P07–P08 (preparado, não autorizado)

P07 exige regressão completa da revisão: usar
`scripts/validation/Invoke-QualificationBuild.ps1 -Attempt p07-pos0203-verify-01 -Phase VerifyPhysical -BudgetSeconds 3600`
em nova reserva, somente após ordem física própria. O perfil opt-in executa
verify/JaCoCo e todos os ITs configurados. Conferir seleção efetiva do POM e XMLs:
IntegralCampaignIT, SequenceReferenceIT, SequenceRecompositionIT, SequenceFailureIT,
IntegralContextIsolationIT, QualificationControlIT, QualificationCancellationIT,
QualificationConcurrencyIT, QualificationResumeAdmissionIT,
QualificationSequenceSupervisorIT, IntegralSweepEdgesIT e SequenceScaleIT são
obrigatórios, além da regressão existente. Não substituir verify por teste dirigido.
`scripts/validation/Test-QualificationRegression.ps1 -BuildAttempt p07-pos0203-verify-01`
confere suíte histórica/novos casos e coverage; ele usa a rodada
macrobloco-qualificacao-pacote-20260913-01, assim como esse builder. O controlador
canônico ArtifactDirected permanece240s. Nenhum skip novo pode justificar PASS.

P08 parte desse mesmo build aprovado. Autorar fixtures por
`scripts/validation/New-SequenceExamples.ps1 -BuildAttempt p07-pos0203-verify-01 -OutputName p08-pos0203-examples-01`,
em JVM17 com classpath test-classes, JAR desse build e suas lib/*;
usar diretório novo e conferir fingerprint. A cópia versionada usa RoundName
validado e default13/09; o helper histórico15/09 fica intacto.
Empacotar por `scripts/validation/New-QualificationPackage.ps1 -BuildAttempt p07-pos0203-verify-01 -OutputName p08-pos0203-package-01 -ArtifactInputs <diretorio-autorado>`
sem Candidate; repetir em output novo para comparar bytes/revisão, sem recompilar
fontes no meio. Expand-QualificationPackage exige hash do arquivo, destino novo
e AllowedRoot; Test-QualificationPackage confere membros/manifesto/dependências.

Executar o Invoke-Qualification.ps1 **do payload extraído**, nunca o arquivo-fonte
isolado. Para cada A/B usar `-Campaign <payload>/config/campaign.sequence-a.json`
ou sequence-b, `-ManifestSha256 <hash-do-recibo>`, controles novos e, em ordem,
inspect/plan/run/status/resume/compare (Campaign em plan/run; Control em
run/status/resume/compare). Configuration vem do pacote validado. Cada chamada
precisa de controlador externo com PID/start, deadline, logs e reconciliação.
O launcher tem teto3660s; reservar incluindo esse teto e readbacks. A/B têm
sete etapas, cinco fatos,19 saídas e33 previews/etapa, rollback e terminal.

Recusas extraídas: `Test-QualificationExtractedGuards.ps1 -PackageAttempt p08-pos0203-package-01 -Attempt p08-pos0203-guards-01`, mais
Test-QualificationPackageGuards.ps1 para ZIP/path/hash; controles adulterados
por Test-QualificationControlGuards.ps1 após smoke compatível na mesma rodada.
`Test-QualificationPackageSmoke.ps1 -PackageAttempt p08-pos0203-package-01 -Attempt p08-pos0203-smoke-01 -UsePackagedCampaign`
prova o exemplo SCENARIO; **não substitui A/B SEQUENCE**. Seus controles podem
alimentar ControlGuards. VALUE/PRECISION/KEY/MULTIPLICITY/OLD_REFERENCE/MISSING_USER/
PIN_DRIFT/COMMAND devem ser provados com
`scripts/validation/Invoke-SequenceJarProof.ps1 -Attempt p08-pos0203-a-value-01 -Package p08-pos0203-package-01 -Case sequence-a -Variant VALUE`,
variando Case/Variant e atribuindo Attempt novo para cada prova no ledger futuro.
A cópia versionada já usa RoundName compatível; Package continua sendo nome
simples, nunca caminho arbitrário. Os ITs correspondentes em P07
não substituem essas recusas no artefato extraído.

Autorização ainda necessária: alvo exato localhost/ETL_SISTEMA_V2_SHADOW,
Windows integrada, duas travas, somente sintéticos rollback-only, vigência,
quantidade de tentativas e teto cumulativo cobrindo VerifyPhysical3600s,
supervisor A/B até3660s por chamada física e provas de recusa/smoke. Definir
nominalmente cada reserva antes de efeito; este relatório não fixa saldo,
não reserva campanha e não reutiliza POS0198. Preparação de controladores é
pré-condição da ordem executável, não autorização presumida para rodá-los.

Parar ao primeiro timeout, falha inesperada, drift de bytes/schema, ausência de
XML/recibo, escala omitida, agregado diferente ou processo residual. Conter só
árvore própria identificada; resultado desconhecido exige reconciliação antes
de repetir. Sem DDL, mudança de dependência, fonte real, COMMIT de domínio ou
produção. Falha consome a reserva; não ampliar240s, devolver saldo ou promover
recusa esperada a falha ignorada. Compare final deve ligar código→JAR→oráculo→XML→recibo.

Recuperação do código: revisar own-diff.patch contra before da rodada, aplicar
somente reversão explícita dos hunks próprios e requalificar. Nenhuma migration
a reverter; nunca apagar logs/snapshots/FAILs. Hash é integridade, não autoria,
aprovação humana ou aceite físico. A sucessão P06 não é selo N.
