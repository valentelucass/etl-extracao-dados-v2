# Bloco 54 — execução integrada local

## Estado atual — 08/09/2026

Bloco 54 concluído no laboratório A–G: V021 aplicada, 25 grants, revisão v7 física,
1098 testes aprovados, 302 reservas e 30 campanhas encerradas. O [relatório atual](v2-022-bloco54-conclusao-local.md)
registra os resultados, os limites nominais, o diagnóstico V021 e a recuperação.
As afirmações de pendência/preparação/revisão final abaixo pertencem às fotografias
históricas indicadas; não são o procedimento atual. Preservadas para rastreabilidade.

## Histórico preservado

Execução adotada pelo owner em 07/09/2026; modelo solicitado Astra xhigh.
Estado atual: PARTIAL_NOT_ACCEPTED. Nenhum aceite novo foi concluído.
Autoridade: mensagem do owner e seção 3 do prompt B54. Alvo único
localhost/ETL_SISTEMA_V2_SHADOW; contas etl_v2_exec e etl_v2_view existentes.

## Bloco 54 — continuação da sexta campanha em 08/09/2026

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
## Fotografia histórica — quinta campanha autorizada em 08/09/2026

O owner respondeu “pode” ao pedido explícito de uma rodada suplementar de até
15 minutos no saldo existente. O UAC normal funcionou. A campanha abriu às
03:59:19 UTC e encerrou às 04:02:13 UTC: 69 reservas novas, total B54 99/128.
O horário exato está no ledger. Não houve sexta campanha nem devolução de reserva.
O pacote aprovado tinha manifest SHA-256
e5f53caf92dbc45880ac148815b461610a893236c7d337b06d2c0ee63e85672e.
O manifest atual também registra o resultado da aplicação; não substitui esse hash histórico.

| Frente | Resultado novo comprovado | Limite ainda aberto |
| --- | --- | --- |
| A | Duas contas reutilizadas; 25 grants exatos; mapping recuperado com validade original e versão crescente | Governança nominal e matriz integral exigidas por G06 |
| B | Revisão final protegida executada em 16 casos; STATUS pelo launcher e diagnóstico em PowerShell novo passaram | Matriz física completa de adulteração/ACL/TLS negativo |
| C | V019/V020 qualificadas por baseline/upgrade rollback e instaladas; 31 casos do harness Windows/SQL passaram | Revogação/policy interrompidas, AUTH-13–14 e demais lacunas do pacote residual |
| D | Coletas/Fretes publicados pelo JAR final; repetição, consultas, recusas e DAG passaram | Replay/force autorizados temporariamente, quedas/lease/cancelamento, alerta positivo pelo JAR |
| E | Persistência/restart de três janelas por workload; SQL confirmou seis ocorrências únicas, sem extração | Execução das janelas e matriz física temporal completa |
| F | Recibos independentes, recuperação, catálogo, contagens, sessões e ledger conferidos; regressão do gravador corrigido passou | Suíte Java é a final v3 anterior, com os mesmos bytes Java/POM; falhas físicas permanecem registradas |
| G | Pacote e seis comparações SQL sintéticas anteriores preservados; oito inputs reais seguem explícitos | Fonte/oráculo reais fora deste laboratório; nenhum aceite de paridade |

### Schema e concessão aplicados

As duas qualificações em rollback produziram o catálogo
dd5c399395f00482f9f5cae17c1e8f9e7d0b53752f3d3abaa03c3b980de8e999
com as mesmas 5.133 linhas do preflight, e retornaram ao catálogo anterior.
Os cinco hashes de módulos conferiram antes do commit. Foram instaladas somente
V019/V020, sem editar V001–V018. Aplicados os três EXECUTEs previamente autorizados;
nova conexão confirmou exatamente 25. Não houve permissão de tabela ou role ampla.
As migrations V019/V020 agora são aplicadas e imutáveis. Os scripts de instalação
não são comandos de repetição sobre o estado atual; usar verify.sql para leitura.

### JAR final, manual e matriz de autorização

Manifest físico final:
5d2632ab3577ea803eb451ef59ea80c537023c88f7f1f0b7bca471ffc0f2b23f.
JAR SHA-256:
4da0955863e7fe15e7b8aad5f0289c74f941a472499d82b9f8e060b72fe51632.
A fonte externa ao JAR ficou exclusivamente em 127.0.0.1, com 11 HTTP nesta campanha.

Os 12 casos operacionais confirmaram: STATUS ausente nas duas contas (10);
OPERATOR RUN e SERVICE FORCE sem papel negados (20); Coletas e Fretes com
predecessor explícito publicados (0, três HTTP cada); STATUS publicado e repetição
(0, zero HTTP, publicação única); parcialidade (40, três HTTP, sem publicação);
Fretes dependente bloqueado antes de fetch (40, zero HTTP); drift (40, dois HTTP,
sem publicação). O último caso não produziu alerta na correlação SQL: prova de
recusa de contrato, sem PASS do sink obrigatório de alerta pelo JAR.

Quatro invocações temporais adicionais passaram: persistir e reiniciar Coletas e
Fretes. Cada uma retornou três janelas e zero extração; SQL confirmou três
ocorrências únicas por namespace, de 28/02/2024 03:00 UTC a 02/03/2024 03:00 UTC,
sem tentativa para essas janelas. A fronteira permaneceu no início; isso não prova
avanço após execução nem conclusão fora de ordem. O STATUS manual preservou porta,
configuração/fingerprint e ocorrência, abriu outra sessão OPERATOR e retornou 0.
O diagnóstico somente leitura confirmou pacote/contas e perfil de 25 grants.

A matriz separada usa a classe Bloco54AuthorityHarness, fora do JAR, sob SERVICE.
Seu classpath usa a revisão inicial compatível; não atribuir esses casos ao JAR
oficial final. Passaram 31 provas: dois cenários de SQL direto, 22 campos de escopo,
recibo adulterado, reutilização, descarte do ack de autorização e consumo, duas
JVMs para a mesma invocação e expiração em 31s. A corrida teve exits 0 e 20, uma
decisão e um consumo. Ack descartado pelo cliente não é outage de transporte.

### Falha do harness e compensação efetiva

AUTH08_MAPPING_REVOKED reservou a unidade 99, confirmou revoked=1/mapping v2 e
falhou ao gravar stdout nulo com WriteAllLines. O caminho de contenção confirmou
revoked=1/v3 e encontrou o mesmo defeito de log. O perfil final recusou a conta
revogada e o controlador encerrou a campanha. Os 31 PASS anteriores permanecem;
o caso de revogação não ganhou PASS porque a observação do filho não foi concluída.

A investigação leu o estado real e confirmou zero sessão restrita. Foi preparado
recovery-reviewed.sql com hash integral esperado, locks, exatamente uma linha,
alteração apenas de revoked e mapping_version, equivalência funcional com o
snapshot anterior e verify.sql dentro da transação. A compensação do caso já
reservado confirmou SERVICE v3→v4/revoked=0, prazo original intacto, e nova conexão
revalidou os 25 grants. Não houve nova invocação nem campanha de testes. A recuperação
real não é o ensaio OPS02 original, que continua não executado.

A causa foi reproduzida offline como MethodInvocationException/ArgumentNullException.
O retorno de sqlcmd agora é sempre uma coleção, inclusive vazio; saída Unicode e
exit não zero continuam preservados. Três regressões passaram sem banco. A contenção
também exige hash de readback e não incrementa versão de mapping já revogado.
Nenhum byte Java/POM/migration foi alterado por essa correção PowerShell.

### Postflight e reprodução

Alvo: localhost/ETL_SISTEMA_V2_SHADOW. Catálogo final
6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a;
5.318 linhas, aumento agregado B54 de 324 sobre 4.994. B53 conserva 95 tentativas,
75 publicações e oito EXTRACTING; B54 soma oito tentativas, quatro publicações,
12 entradas auditadas e dez páginas, abaixo dos tetos. São 28 invocações de JAR
nas duas revisões, 22 HTTP no bloco e 99 reservas em cinco campanhas encerradas.
Nenhum papel REPLAY/FORCE_RUN ou scope extra foi criado. SERVICE mapping v4,
OPERATOR v1 e oito scopes v1; validade SQL original até 07/10/2026 preservada.
Zero sessão restrita e zero transação de usuário na conferência final.

Evidências privadas em target/bloco54/resume-approved-supplemental:
results.json, qualify-upgrade.sql.log, qualify-baseline.sql.log,
install-pending.sql.log, apply-approved-grants.sql.log, recovery-review.json,
recovery-apply.log, recovery-verify-new-connection.log, final-preservation-v3.log
e final-catalog.log. A matriz está em target/bloco54/authority-matrix-results.json.
O manifest versionado fixa os hashes das dez evidências finais relevantes.
As primeiras consultas de conferência falharam por coluna/collation e por calcular
namespace em UTF-8; a implementação usa UTF-16LE. Os scripts/logs falhos ficaram
retidos, e a v3 de leitura confirmou os oráculos corretos, sem alterar o banco.

Reproduzir as verificações offline com Test-Bloco54Integrated.ps1
-IncludePrivateEvidence, Test-Bloco54SqlEvidence.ps1, Test-WindowsRuntimePackage.ps1,
Test-ProgressiveDataGate.ps1, Test-SchemaFoundationManifest.ps1 e
Test-Gpt56ChatTrail.ps1. O diagnóstico SQL atual usa verify.sql e 055; 053 permanece
como prova da fase histórica de 22 grants, sem relaxar seu guard.

Verificação final desta continuação: validator integrado com evidências privadas,
trilha, gate progressivo, manifesto de schema e pacote Windows histórico passaram.
Sete contraprovas em cópia isolada recusaram aceite falso de revogação, replay,
alerta, temporal, novo checkbox e retorno indevido de versão. O diagnóstico SQL
055 confirmou os 19 consumidores; verify.sql confirmou o perfil atual de 25.
Scanner: 1.188 candidatos, 1.187 textos, um binário conhecido, zero finding;
11 self-tests passaram. Snapshot de 1.186 arquivos preservado, nenhum ausente;
14 arquivos alterados e dois novos, todos os 16 em UTF-8 estrito sem BOM.
Java/POM/V001–V020 permaneceram iguais. Diff próprio, arquivos novos e auditoria
estão em session-only-existing.patch, session-new-files.json e
final-filesystem-audit.json da pasta suplementar. git diff --check passou.

A campanha terminou com 29 unidades de saldo e nenhum novo aceite canônico.
O [pacote residual](v2-022-bloco54-pendencias-fisicas.md) delimita aplicação,
verificação e recuperação posteriores. Nenhuma sexta campanha está autorizada.
As seções abaixo preservam as fotografias e falhas anteriores.

## Histórico: primeira retomada de 08/09/2026 — quarta campanha

O owner autorizou o delta de três grants após revisão documental e qualificação.
A revisão confirmou seu consumidor real e o privilégio mínimo. O UAC normal
funcionou, e o bundle final foi instalado na área protegida. A quarta campanha
executou somente a primeira qualificação em rollback: ela recusou o hash previsto
do módulo V020 antes de qualquer commit ou grant. O controlador encerrou a campanha.

A causa estava no cálculo de hash preparado pelo agente: o SQL Server conserva
espaço inicial do batch e normaliza CREATE OR ALTER para CREATE com espaços.
Bloco54SqlModules.psm1 preserva essa representação; a correção foi conferida por
leitura de quatro módulos já instalados. A V020 ainda não tem PASS de qualificação.
Ela também vincula início/fim, modo e estratégia da janela temporal ao escopo
consumido. O pacote exige agora cinco módulos exatos, sem novo grant além dos três.

Postflight após rollback: mesmo catálogo 0eca062dbfc89a1f02223cfc33e2b04c85816d610b23806817b607479d389cd0,
5.133 linhas, perfil original 053 aprovado. Mappings e scopes não foram alterados.
O ledger está em 30/128 reservas e quatro campanhas encerradas. As 98 unidades
restantes não reabrem uma campanha encerrada. A campanha suplementar única de
15 minutos foi preparada e solicitada, mas ainda não autorizada nem iniciada.
As três permissões já estão autorizadas e não dependem de nova escolha do owner.

Evidências em target/bloco54/resume-approved: results.json,
qualify-upgrade.sql.log, rollback-catalog.log, rollback-profile.log,
module-hash-normalization.log, budget-module-tests.log e o snapshot desta retomada.
O teste de orçamento usou somente uma cópia sintética: recusou quinta campanha
normal, ausência de autorização e uma sexta; não alterou o ledger real.
Parser, pacote Windows e gate progressivo passaram. O JAR fez sete previews
válidos e três recusas de gap/overlap, sem SQL/HTTP. O primeiro oráculo de horário
inexistente em São Paulo estava errado; seu resultado original permanece retido,
e a expectativa foi corrigida conforme a política já implementada/testada.

Conferência final da retomada: 1.180 arquivos do snapshot íntegros e presentes;
19 arquivos alterados e seis novos, todos os 25 em UTF-8 estrito sem BOM. Java,
POM e V001–V018 permanecem iguais ao início da retomada. Diff próprio preservado
em resume-approved/session-only-existing.patch e session-new-files.json.
Scanner offline: 1.186 candidatos, 1.185 textos, um binário conhecido e zero finding.
Trilha, validator integrado, pacote Windows, gate progressivo e git diff --check
passaram. SQL confirmou zero transação de usuário ativa, mesmos dados B53 e
19 consumidores protegidos da V018. Não houve nova suíte Java: seus bytes não
mudaram, e a prova de 1.056 testes continua a do verify final v3 anterior.

As seções seguintes preservam o checkpoint anterior da primeira entrega.

## Fotografia anterior à implementação

Snapshot de 1.131 arquivos em target/bloco54/initial, inventário SHA-256 em
initial-files.json. Ledger B53 confirmado: 112 reservas, SHA-256
dc45f84046fa1c2a6184dc181cc9bf1b39878c5c94ed4574e22d761f0cfc3df1.
Cópia somente de leitura em b53-ledger-readonly-copy.txt; orçamento B54 separado,
até 128 unidades, quatro campanhas de 15 minutos, sem devolução de reservas.

Preflight físico executado: master confirmou somente o alvo ONLINE; validator 053
aprovou instalação nominal, 22 grants e oito scopes. Catálogo confirmado
df70c3666f94d6aaeefadc9f3842178611b8bf51bb9fa16d2f025eff21612f9f,
4.994 linhas; as 17 migrations conferem com ambos os ledgers instalados.
Conexões sqlcmd integradas com TLS verificado (-N), timeout até 30 segundos.
Sessão do editor não elevada; a leitura de ProgramData foi recusada por ACL.
Uso de DPAPI e cópia protegida exigem o contexto administrativo por UAC normal
já autorizado; o instalador B53 não será reexecutado.

## Matriz requisito → implementação atual → evidência → lacuna → teste → aceite

Esta matriz foi registrada antes de alterar código. IDs detalhados seguem a seção 5
do prompt; resultados de mocks não serão promovidos a prova Windows/JAR/SQL.

| Requisito | Código atual | Prova existente | Lacuna observada | Caso novo | Aceite canônico |
| --- | --- | --- | --- | --- | --- |
| A: identidade e governança | WindowsSqlRuntimeAuthorization, V016 | 053 físico; duas contas e oito scopes | Inventário atual, validade e revogação documentados por controle | AUTH-01–09, OPS-03 | V2-042b/G06 |
| B: TLS uniforme | RuntimeAuthorityConfiguration e BoundedRuntimeDataSource | TLS da autorização em B53 | Workload admite URL LOCAL_EPHEMERAL permissiva e não vincula hostname/certificado administrados | AUTH-16, OPS-01 | V2-042c/G07 |
| B: artefato/launcher/diagnóstico | Main e instalador B53 | STATUS/10 em duas contas | Launcher de manutenção independente e integridade de pacote completo | AUTH-04–06, OPS-01–03 | G07; V2-039 permanece próprio |
| C: binding/consumo durável | RuntimeAuthorizationScope, V016 | Testes de 22 campos; DENY físico e STATUS limitado | Matriz adversarial sob contas reais, duas JVMs e ack | AUTH-07–14 | G07 |
| C: SQL direto | 20 grants SERVICE e dois OPERATOR | Inventário exato 053 | Procedures de workload não exigem consumo; avaliar e corrigir fences requeridos | AUTH-15 | V2-042 pai |
| D: contrato/mapper pelo JAR | RuntimeOperationalRequest, catálogo primeira onda, mappers | RuntimeOperationalExecutionTest usa SQL sintético | Shape empacotado não inclui frescor aceito pelo mapper Fretes; prova física positiva ausente | RUN-01–04, RUN-15 | G08 |
| D: recuperação/DAG | RuntimeDispatcher, LocalColetasFretesRuntime, V015 | 6 ITs B53; handlers de uma ocorrência | CLI composta, dependência e novas sessões restritas | RUN-05–14 | G08; V2-022 pai |
| D: drift/métricas/health | RuntimeOperationalExecution, framework V2-023 | Gateway existente | Sink de drift sempre lança exceção, observer HTTP noop; três grants não pertencem aos 22 atuais | RUN-15, AUTH-15 | G08; delta adicional revisável |
| E: planejamento temporal | RuntimeTemporalPolicy/Planner/Coordinator, V017 | Casos offline e três janelas entre JVMs B53 | Consumidor one-shot autenticado, política laboratorial estrita e ligação à execução | TIME-01–08 | V2-022 pai |
| F: regressão/evidência | POM, validators e ITs existentes | 1.042 testes históricos | Build isolado final, campanha/ledger B54 e contraprovas próprias | Todos, por camada | Somente aceites comprovados |
| G: comparação futura | Q-FND-01/02, V2-047 e V2-050 | Perfis e medição sintéticos | Dois conjuntos independentes, comparação SQL e pacote real incompleto recusado | CMP-01–03 | Preparação; V2-012/047/050/038 abertos |

## Limites de aceitação

Os checkboxes originais permanecem a autoridade. Não criar sete novos aceites.
Governança local não inventa principals produtivos, tenant ESL, owner consumidor,
política de produção, fonte real, paridade, completude, release ou cutover.
Novos grants recebem pacote exato de aplicação, verificação e compensação;
nenhuma ampliação implícita dos 22 grants. Dados B53 e seus oito EXTRACTING ficam
preservados. Compensações de mapping usam versões crescentes e validade original.

## Resultado desta execução — checkpoint revisável

Estado: PARTIAL_NOT_ACCEPTED. Todas as frentes receberam trabalho; nenhuma
recebe aceite integral por inferência. O cancelamento da elevação Windows
impediu a campanha seguinte sob as contas restritas. O controlador não iniciou,
não abriu campanha e não mudou mappings. As campanhas 1–3 estão encerradas:
29 reservas de 128, nenhuma devolvida; restam 99 unidades e uma campanha de 15 min.

| Frente | Entregue/provado | O que ainda impede o aceite |
| --- | --- | --- |
| A | Inventário real, vigência, papéis, UUID/sink e manutenção documentados no ADR 0036; postflight 053 | Provas restantes de revogação e vínculo SQL; G06 aberto |
| B | TLS compartilhado, pin obrigatório, revisão protegida inicial e revisão final verificada; launcher manual/diagnóstico/recuperação preparados | Revisão final e launcher ainda sem execução restrita; G07 aberto |
| C | V018 aplicada, 19 consumidores com guard; protocolo Java e harness físico preparados | Matriz adversarial não iniciada por UAC cancelado; V020 pendente fecha lacunas encontradas na revisão |
| D | 11 casos reais pelo JAR inicial; duas publicações, status/repetição e parcial/drift sem publicação | REPLAY/FORCE_RUN, quedas/leases/ack/cancelamento e DAG ainda sem prova física B54; revisão final exige delta |
| E | Políticas estritas Coletas/Fretes; preview do JAR final, persistência autenticada por janela e coordenador composto; testes offline | Persistência/restart/reconciliação temporal ainda sem campanha restrita B54; ligação de requests executores permanece explícita |
| F | Java 17 offline completo: 1056 testes, 0 falhas/erros, 4 skips preexistentes; estilo/arquitetura/cobertura aprovados; preservação SQL conferida | Falta a parte física indicada nas demais linhas |
| G | Pacote de comparação com proveniência estática, orçamento e oito inputs reais pendentes; seis cenários SQL passaram | Comparação real é recusada e continua fora do bloco |

## Provas físicas executadas

Revisão instalada/provada: manifest
d382d4d39164cdb62918c21f55966e91a6c348a91a001d9de5828b8f39eeb208.
Nova sessão Windows por invocação; SQL integrado com pin; fonte TCP 127.0.0.1,
fora do JAR. Observação independente por outra conexão SQL limitada.

| Caso | Resultado observado |
| --- | --- |
| AUTH01_STATUS_SERVICE | NOT_FOUND / 10, zero HTTP |
| AUTH02_STATUS_OPERATOR | NOT_FOUND / 10, zero HTTP |
| AUTH03_RUN_OPERATOR | DENY / 20, zero HTTP |
| AUTH03_FORCE_SERVICE | DENY / 20 sem papel, zero HTTP |
| RUN01_COLETAS | SUCCESS / 0, três HTTP, duas linhas, duas páginas auditadas, uma publicação |
| RUN03_COLETAS_STATUS | PUBLISHED / 0, zero HTTP |
| RUN05_COLETAS_REPEAT | SUCCESS / 0 na mesma ocorrência, zero HTTP e sem nova publicação |
| RUN02_FRETES | SUCCESS / 0, três HTTP, duas linhas, duas páginas auditadas, uma publicação |
| RUN04_FRETES_STATUS | PUBLISHED / 0, zero HTTP |
| RUN09_PARTIAL | SOURCE_DQ / 40, três HTTP, FAILED, uma página/duas linhas, zero publicação |
| RUN15_DRIFT | SOURCE_DQ / 40, dois HTTP, FAILED, zero página/linha/publicação |

Foram 11 requisições HTTP no total. Status prévio consta no log de cada invocação;
a consulta SQL posterior naturalmente já enxerga PUBLISHED nas ocorrências
publicadas depois. Não usar essa consulta tardia para reescrever o status inicial.

CMP-01–03 foram materializados em seis casos SQL independentes: EQUAL,
MONEY_STATUS, DUPLICATE, ABSENT, BOUNDARY e INCOMPLETE. Oráculo literal separado;
as tabelas de teste e resultados foram revertidos. Isso não prova paridade real.

A V019 foi criada/exercitada em rollback: resumo de duas publicações, falha e
ausência, além de alerta com correlação conferida. SHA do arquivo qualificado:
42b8baae9c6681b3b5d1201d7062e33c7e34562eb5c643c7c269186128888d4c.
A revisão posterior identificou que o fence precisa conferir a ocorrência SQL
existente e a criação da fronteira. V020 prepara essa correção; não foi executada
nem qualificada fisicamente. O pacote de grants exige seus hashes de módulo
antes de conceder permissões. Não se atribui a prova de V019 à V020.

## Estado final confirmado no SQL

V001–V018 permanecem instaladas. V019/V020 estão somente preparadas no repositório;
o baseline inclui a cauda pendente, sem reaplicar versões históricas.
V018 aplicada, SHA-256:
0e418d19fe93ea3611f6d083ffa77e8e58e764ed0f5a180eb1d3a33e6f971f49.

Catálogo final:
0eca062dbfc89a1f02223cfc33e2b04c85816d610b23806817b607479d389cd0.
Total: 5.133 linhas; incremento sintético confirmado, sem limpeza destrutiva.
Postflight 053 aprovou exatamente 22 grants, dois mappings e oito scopes;
ambos mappings e todos scopes seguem versão 1, replay/force desligados e
validade original até 2026-10-07T22:34:30.615Z. Validator 055 aprovou os 19 guards.

A observação final preservou as 95 tentativas, 75 publicações e oito EXTRACTING
anteriores. B54 acrescentou quatro tentativas e duas publicações. Zero transação
de usuário ativa no alvo na conferência final. Isso comprova contagens e o
recorte das novas ocorrências; não é um checksum retroativo de cada linha B53.

O ledger B53 segue 112 reservas e o hash original. Não houve conexão ao
ETL_SISTEMA, fonte ESL, dashboards, produção, serviço, scheduler, deploy ou
alteração de contas/certificado/senhas. Nenhuma tentativa de contornar UAC.

## Revisão final, operação e comparação

Revisão final preparada: manifest
5d2632ab3577ea803eb451ef59ea80c537023c88f7f1f0b7bca471ffc0f2b23f.
Java final com fonte/cliente/mapper reais, dependência explícita, tentativas HTTP
contadas e resumos SQL por ocorrência. Valores não medidos permanecem UNKNOWN;
o zero histórico de bytes auditados não é medição de bytes de rede.
Três recursos administrativos foram adicionados ao JAR; somente logback.xml
foi substituído pelo console laboratorial. Todos os outros entries e bibliotecas
foram conferidos. Nenhum material de teste foi incorporado.

O JAR final executou plan temporal offline: effects=0, três janelas, sem backlog
ou blackout. A persistência não é extração e não ativa agenda. Ver
[operação manual](v2-022-bloco54-operacao-manual.md),
[delta de observabilidade](../../database/proposals/bloco54-observability/README.md)
e [comparação futura](../catalogos/comparacao-bloco54/README.md).

Os três grants propostos são resumo por ocorrência para SERVICE/OPERATOR e alerta
por ocorrência para SERVICE. Apply/verify/recover e instalação somente da cauda
pendente estão prontos, com hashes e sem renovação. Exigem autorização adicional
porque a seção 3 não amplia os 22 grants. Não foram aplicados.

## Rastreabilidade e aceites

Evidências privadas em target/bloco54: ledger.jsonl, initial-files.json,
restricted-controller-result.txt, logs/requests dos 11 casos, observe-jar.log,
qualify-upgrade.log, qualify-baseline.log, qualify-v019.log, final-catalog.log,
final-preservation.log, final-provisioning-053.log, final-consumers-055.log,
full-verify-final-v3.log, final-jar-temporal-plan-v3.log e diffs de entries do JAR.
O manifest sanitizado é database/manifest/runtime-bloco54.json.

Validações offline finais: pacote Windows, gate progressivo de schema, trilha e
validator integrado B54 passaram. Os dois validadores de schema foram atualizados
para exigir exatamente V001–V020 no repositório; isso não afirma instalação de
V019/V020. Contraprovas recusaram migration extra e baseline sem V020. As quatro
contraprovas de aceite falso B54 também foram recusadas. Scanner offline: 1.180
candidatos, 1.179 textos, um binário conhecido e zero finding; 11 self-tests passaram.
Parser PowerShell e compilação do controlador C# passaram sem iniciar fonte/SQL.

A revisão desta sessão é separada do estado inicial em
target/bloco54/session-only-existing.patch e session-new-files.json. Os 1.131
arquivos do snapshot continuam íntegros, sem arquivo preexistente ausente; V001–V017
mantêm os bytes iniciais. UTF-8 estrito sem BOM e git diff --check passaram.
O POM canônico foi preservado; o POM temporário diferia somente no build directory
e foi retirado depois da conferência. Invoke-Bloco54OfflineVerify.ps1 prepara essa
mesma isolação para uma execução futura; o PASS Java registrado é do verify v3.

Primeiro erro de instalação V018 teve rollback, foi registrado e não devolveu
reserva. Erros de testes/estilo/configuração foram corrigidos antes do verify final.
O classpath instrumentado para perda de ack é explicitamente de teste; não
representa queda física da rede nem substitui a travessia oficial do JAR.

Permanecem 114 checkboxes, 60 concluídos, 54 pendentes e 196 rotas abertas.
G06/V2-042b, G07/V2-042c, G08/V2-022b, V2-042 e V2-022 permanecem abertos.
V2-022 pai também exige consumidores/comandos/matriz nominal próprios; os cinco
fatos mensais, sweep-apply, comparação e política produtiva não são entregues por
persistir uma janela. Nenhum aceite de fonte, paridade, V2-012/047/050/038 ou
cutover foi marcado.
