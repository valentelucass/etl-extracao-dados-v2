# P02 — diagnóstico causal offline da tentativa pós-0194

20/09/2026. Diagnóstico concluído, sem aceite I/J. Escopo exclusivo:
`P04-P05-POS0194-01`, sem repetir campanha, consultar banco/rede, alterar guard,
reservar Physical ou executar P05. Não há implementação nova neste documento.

## Evidência e reconciliação

Raiz histórica: `target/macrobloco-campanhas-integrais-20260915-01/p04-p05-pos0194-p04-01/`.
Ledger/worklog: `target/P04-P05-POS0194-01/`. Evidência nova, sanitizada e com
paths/hashes de arquivos: `target/p02-diagnostico-pos0194/evidence.json`.
Reprodutor exclusivamente de leitura: `target/p02-diagnostico-pos0194/reconcile.ps1`.
Não imprime payload, identidade de negócio, credencial ou conteúdo de readback.

| XML existente no build isolado | Testes/falhas/erros/skips | Segundos |
| --- | --- | --- |
| IntegralSweepEdgesIT | 5/0/0/0 | 73.602 |
| QualificationCancellationIT | 3/0/0/0 | 1.073 |
| QualificationConcurrencyIT | 1/0/0/0 | 6.135 |
| QualificationResumeAdmissionIT | 6/0/0/0 | 292.326 |
| QualificationSequenceSupervisorIT | 5/1/0/0 | 2405.848 |

São20 ITs, uma falha, nenhum erro/skip; stdout registra também16 unitários
aprovados, BUILD FAILURE e48:40min totais, abaixo3600s. `result.json` registra
exit127, timedOut=false, rollbackConfirmed=true. A releitura confirmou igualdade
byte-a-byte de before/after, UTF-8 estrito e logs de12846/713 bytes, abaixo16MiB.
Igualdade agregada não prova igualdade linha a linha; os três recibos também
registram rollback=true/testPassed=true e journal terminado em TERMINAL.

Identidades técnicas de processo/nonce/start dos três workers conferem com seus
recibos. Journals de sucesso/falha: RESERVED, STARTED, EVIDENCE, ROLLBACK,
TERMINAL; cancelamento inclui BARRIER. O processo Maven tem workingDirectory
no build isolado. Zero processo próprio ao fechamento é a observação conservada
no ledger/worklog; P02 não reinspecionou processos vivos nem reconstruiu uma
árvore completa a partir de um PID. Nenhum novo processo de integração foi criado.

## Limite temporal: soma da classe, não excesso da sequência

Contrato anterior à tentativa: `CONTRATO.md`, SEQ-01 (1800s por sequência,
240s por etapa); ledger (3600s por campanha, SQL60s, heap512MiB). Não existe
nesses critérios um teto1800s para a classe inteira de cinco cenários.

| Caso do supervisor | Tempo XML (s) | @Timeout |
| --- | --- | --- |
| ownedCancellationPreservesObservedStageEvidenceWithoutGrantingSuccess | 601.010 | 1800s |
| sequenceInputMismatchIsRefusedBeforeReservationOrSql | 260.827 | Ausente |
| liveSequenceOwnerRefusesResumeBeforeAnotherSequenceCanBeReserved | 235.245 | Ausente |
| laterSequenceOracleFailurePreservesCompletedStageEvidenceAndRollsBack | 549.938 | 1800s |
| sequenceSuccessBindsSevenStageReceiptsAndAllThirtyThreePreviews | 758.817 | 1800s |

Soma2405.837s; diferença de0.011s para o total da classe. Os três cenários
físicos são independentes: cada chamada cria sua fixture/campanha/supervisor.
Os dois testes de admissão também criam fixtures; seus tempos não são etapas
SQL e não devem ser comparados ao teto240s. O custo de setup está dentro do
tempo XML; não se atribui toda a diferença ao SQL sem medição específica.

`PackagedFixtureRuntime.runIfExploded` invoca o método carregado do JAR de forma
síncrona, por reflexão, e propaga Exception/Error. A anotação do método externo
continua envolvendo a chamada inteira; não vira timeout global da classe nem
desaparece porque o método interno não é despachado pelo JUnit.

`QualificationPackageFixture.sequenceCampaign` fixa maximumSeconds=1800.
`QualificationSupervisor.execute` inicia deadline por campanha; `runChild`
usa o mínimo desse deadline e `QualificationArtifactCase.caseSeconds` (1800
para SEQUENCE). `LocalArtifactSequence.execute` aplica ExecutionDeadlines total
e por etapa; esses controles não usam o tempo agregado da classe. O prazo lógico
da fixture259200s refere-se à janela civil e não substitui limite de processo.

| Recibo existente | finished − started (s) | Etapas | Maior etapa (s) |
| --- | --- | --- | --- |
| PASS_LOCAL | 328.0730455 | 7 | 43.750 |
| BLOCKED_DEPENDENCY | 254.5823196 | 5 | 30.458 |
| CANCELLED | 317.3075448 | 7 | 30.667 |

Conclusão demonstrada:2405.848s é soma de cenários, três deles sob @Timeout
individual. A violação contratual inferida em0195 não se sustenta; não há
evidência de falha de instrumentação/controle nessa soma. O campo histórico
`sequenceLimitExceeded=true` e os textos anteriores permanecem intactos como
conclusão retificada por este documento. Não se amplia limite nem se concede
aceite. A execução abaixo dos tetos não prova cancelamento adversarial no exato
limite: checkpoints cooperativos e a tolerância de contenção do supervisor
precisam de contraprova própria caso se pretenda qualificar esse comportamento.

## Causa do guard127 e responsabilidade

Em `Invoke-Build.ps1`, `$build = Join-Path $attemptRoot 'build'` e
`$info.WorkingDirectory=$build`. Entretanto, o bloco de relatórios usa
`Join-Path $sourceRoot 'target/failsafe-reports'`. Sem Snapshot, sourceRoot é o
workspace; com Snapshot, ainda é a origem copiada, não o build da tentativa.
O stdout do Maven aponta para build/target/failsafe-reports, onde estão os XMLs.

Reexecutou-se apenas o bloco de leitura do guard, extraído do texto, sem executar
Invoke-Build, Maven, SQL ou reservas. Com a raiz histórica: cinco ausências,
exit127. Com a raiz build: zero ausências, uma suíte falha, exit127. Portanto,
o falso diagnóstico de ausência é local ao controlador; o Failsafe produziu
seus relatórios e já falhara pela asserção. O guard não deve corrigir a asserção,
procurar recursivamente XMLs antigos ou aceitar apenas o exit do wrapper.

Menor correção futura: trocar **somente `$sourceRoot` por `$build` na busca de
XMLs**, conservando nomes exigidos e recusas por ausência/falha/erro/skip/zero
testes. Não alterar o guard neste macrobloco. Contraprova offline suficiente:
raízes isoladas em fixture, XML completo válido aceito; ausente, malformado,
vazio, failure/error/skip e relatório somente na origem recusados. A execução
P02 demonstrou os dois caminhos reais; não alega ter executado toda essa matriz
nem ter corrigido o controlador. Mesmo após essa troca, o XML histórico falho
deve continuar recusado.

## Preview bloqueado: defeito de asserção, sem enfraquecer o contrato

SEQ-06 exige33 previews em **etapa aprovada**. Em LocalArtifactSequence.execute,
`verified.selected().state() == PASS_LOCAL` produz verifyPreviews(...); qualquer
outro estado produz List.of(). O resultado é registrado antes de interromper
a cadeia, preservando etapas anteriores. Isso coincide com o recibo tardio:
estados PASS_LOCAL×4/BLOCKED_DEPENDENCY, previews33/33/33/33/0,19 saídas em todas
as cinco etapas e uma divergência direta FACT_EQUATION_DIVERGENCE no terminal.
Sucesso e cancelamento têm sete etapas aprovadas com33 previews cada; CANCELLED
é estado do recibo da campanha, não BLOCKED_DEPENDENCY do estágio terminal.

A cópia histórica de QualificationSequenceSupervisorIT exigia33 em todas as
iterações; o stack trace aponta assertStageEvidence. A versão canônica já
corrigida passa0 apenas para o terminal tardio e mantém33 antes dele. O código
de produção e PackagedFixtureRuntime coincidem byte-a-byte com o build histórico;
a correção de expectativa não muda runtime, estado, rollback ou raiz do guard.

Contraprovas executadas sobre agregados JSON: forma antiga rejeita o recibo;
forma corrigida aceita; quatro adulterações em memória são recusadas (preview
anterior0, terminal33, terminal PASS_LOCAL e18 saídas). Isso demonstra o predicado
e o dado histórico, não executa a asserção Java. `assertDirectFactFailure` e o
assert do journal vêm depois da antiga falha: P02 conferiu os dados existentes,
mas não os promove a teste Java histórico que teria passado.

Contraprova Java futura suficiente para a asserção: sob JDK17, carregar somente
o helper assertStageEvidence corrigido sobre recibo sanitizado ou JSON de cinco
estágios e invocar por reflexão; verificar as quatro recusas acima, falha direta
e sete etapas33 dos outros cenários. Não chamar métodos @Test de supervisor,
setup(), execute(), resume() ou a autoria da fixture como substituto. Os três
cenários físicos chamam JDBC/subprocessos e exigem outra autorização; a ponte
runIfExploded, por si só, não torna um teste offline.

## Verificação estática e decisão

- `javac --release 17 -proc:none`, JDK17.0.20.1, compilou somente os dois
  arquivos test-only citados contra o classpath já disponível do build isolado,
  com saída em target/p02-diagnostico-pos0194/compiled: exit0. Nenhuma classe
  compilada foi copiada ao build histórico ou ao código canônico.
- Maven `--offline --batch-mode --no-transfer-progress spotless:check`, JAVA_HOME
  do JDK17 apenas no processo filho, sem perfil físico: exit0,1162 arquivos
  limpos,20.205s. Resolve a lacuna JVM25/formatter; não é test-compile integral,
  Checkstyle, suíte Java, verify ou prova JDBC. Nenhuma dependência foi atualizada.
- Test-TrilhaPreparation -SelfTest: PASS,1 positivo/24 negativos e33 etapas.
  A sucessão continua recusada em STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md;
  manifests não foram regenerados. Logs/resultados e diff exclusivos em
  target/p02-diagnostico-pos0194; scanner conserva os oito MISSING_CANDIDATE.

| Defeito ou condição | Correção/prova mínima | Exige Physical? |
| --- | --- | --- |
| Interpretação do total como uma sequência | Retificação atual, XMLs por caso, recibos e contrato anterior | Não |
| Expectativa terminal33 | Correção test-only preexistente, prova isolada do helper e contraprovas | Não para a asserção; sim para nova qualificação integral |
| Busca XML na origem | Uma troca de raiz e matriz de guard isolada | Não para o guard |
| Qualificação de I/J na revisão corrigida | Preflight da revisão exata, XMLs completos, recibos/limites/rollback/processos e guard correto | Sim, nova autoridade finita e reserva nova |
| P05/K | I/J aceitos e autorização própria aplicável | Continua bloqueado e fora de P02 |

Parada cumprida: apenas diagnóstico/documentação/estática; nenhum aceite,
Physical, nova reserva, P05, DDL, Flyway, SQL, fonte, V1, rede, credencial,
deploy, commit ou push. Input externo futuro: autorização quantitativa nova
para a prova integral, com alvo, tetos e recuperação explícitos; não se pede
confirmação nesta entrega nem se reutiliza a reserva consumida.39/45 e67/115
permanecem. Rollback desta manutenção seria apenas retirar os acréscimos
documentais pelo diff P02, preservando todo material preexistente.
