# Modelo de ameaças — identidade e autorização do runtime

## Estado atual — 08/09/2026

Bloco 55 concluído A–J: G06/G07/G08 e V2-042 foram comprovados pelos seus critérios
originais no mecanismo Windows/SQL adotado. Os inputs de identidade existentes,
UUID de auditoria, capabilities, consumo, dispatcher e JAR não dependem de
fonte real. A extensão tem 32 grants/16 scopes, mantém a validade original e
comprovou os fences dos novos consumers sob SERVICE, com recusas 52840/229,
material inconsistente sem HTTP e vínculos originais intactos no SQL.
Ver a [matriz de critérios B55](../runbooks/v2-022-bloco55-cinco-verticais-local.md)
e seu manifest de aceites. A administração autorizada é somente do laboratório.

### Fotografia anterior — Bloco 54

Bloco 54 concluído no laboratório A–G: V021 aplicada, 25 grants, revisão v7 física,
1098 testes aprovados, 302 reservas e 30 campanhas encerradas. O [relatório atual](../runbooks/v2-022-bloco54-conclusao-local.md)
registra os resultados, os limites nominais, o diagnóstico V021 e a recuperação.
As afirmações de pendência/preparação/revisão final abaixo pertencem às fotografias
históricas indicadas; não são o procedimento atual. Preservadas para rastreabilidade.

## Histórico preservado

- Escopo: subgate offline V2-042a
- Data: 2026-08-30
- Estado: modelo e política provider-neutral definidos; V2-042 permanece aberto

## Atualização após provisionamento local — 07/09/2026

O mecanismo Windows/SQL foi adotado e o [provisionamento local](../runbooks/v2-042-contas-locais-windows.md)
foi executado sob autorização posterior: duas contas restritas reais, authority,
22 grants, oito scopes, distribuição protegida, TLS JDBC de autorização e STATUS
autorizado/consumido em novas sessões Windows. O OPERATOR teve RUN recusado. Isso
resolve a ausência local de principals/TLS e a prova inicial de privilégios; não
fecha a matriz de autorização, revogação, dispatcher ou operação com fonte real.

As seções de V2-042a e a tabela da primeira etapa de B53 abaixo preservam seus
limites históricos. As afirmações de provider/principals ainda não fornecidos não
descrevem mais a instalação local. A [proposta B54](../runbooks/prompt-bloco-54-runtime-operacional-local-astra.md)
exige reconciliar os controles com essa evidência e completar as provas restantes,
incluindo a superfície dos grants SQL diretos, sem antecipar V2-042b/c.

## Estado de segurança na entrega offline V2-042a

O composition root oficial permanece em `deny-all` para ações operacionais protegidas. A escolha do provedor de
identidade, da authority confiável, dos identificadores de principals e dos mapeamentos entre
principals e papéis é `EXTERNAL_INPUT_REQUIRED`. Nenhum valor provisório, identidade local ou
mapeamento inventado por este repositório pode liberar execução.

A fronteira foi ligada ao composition root usado pelo entry point oficial, portanto vale para
chamada pelo scheduler, wrapper, script ou invocação direta do JAR por esse entry point. O launcher
externo não é uma fronteira de segurança. Nenhuma combinação de argumento CLI,
propriedade de sistema, arquivo de configuração ou variável de ambiente pode atribuir identidade
ou papel ao próprio chamador.

O subgate não afirma resistência a código arbitrário no classpath, reflection ou composição direta
de classes internas. Os handlers operacionais ainda não existem; cada um deverá receber e consumir
uma capacidade válida no dispatcher antes de V2-042 global poder ser concluído.

Comandos locais sem efeito operacional — ajuda, versão, validação de configuração e `dry-run` —
podem permanecer disponíveis sem principal verificado, desde que não resolvam segredo, não abram
rede, não criem conexão e não executem DDL/DML. Qualquer novo comando é protegido por padrão até
ser classificado explicitamente.

## Ativos e objetivos

Esta fronteira protege:

- credenciais e material de autenticação, que nunca devem aparecer em argumentos, configuração,
  logs, eventos ou exceções;
- dados de sombra, ledgers, leases, checkpoints, publication pointers e trilha de auditoria;
- integridade de `run`, `replay`, `sweep`, `force-run` e consultas de estado;
- separação entre identidade de serviço, operador humano e identidade de migração/cutover;
- disponibilidade controlada: falha de identidade ou auditoria não pode virar autorização.

O objetivo é garantir autenticação por uma authority aprovada, autorização mínima por ação,
credenciais temporalmente válidas e auditoria sanitizada de toda decisão. A autorização não
substitui os fences de lease, estado, reconciliação, publicação ou qualidade de dados.

## Fronteiras de confiança

1. O scheduler ou operador inicia o processo, mas não é confiável para declarar papéis.
2. Um adaptador de identidade futuro valida a credencial perante a authority escolhida e produz
   apenas uma identidade verificada, temporalmente limitada e com referência opaca.
3. A fronteira de autorização valida estado, vigência e política antes da futura composição de
   qualquer handler operacional ou resolução de seus segredos.
4. O control plane e os adapters persistentes aplicam ainda permissões SQL mínimas e fences
   transacionais; autorização no Java não transforma o runtime em owner do banco.
5. O sink de auditoria registra a decisão sanitizada. Indisponibilidade ou recusa do sink para uma
   ação protegida causa negação; não existe modo silencioso ou fail-open.

O provider, o mecanismo de credencial, a authority, os audiences, os tempos de validade, os
principals e suas atribuições não estão definidos aqui. Todos são entradas externas obrigatórias,
com owner e evidência de aprovação fora do Git.

## Principals e política RBAC

Há duas classes de principal verificável:

- `SERVICE`: identidade não humana do scheduler/supervisor;
- `OPERATOR`: identidade humana usada em ação interativa autorizada.

Os papéis são capacidades independentes. Não existe papel `ADMIN`, wildcard, hierarquia implícita
ou herança que agregue todas as ações. A decisão exige todos os papéis da linha:

| Ação | Papéis mínimos | Observação |
| --- | --- | --- |
| `status` | `OBSERVER` | Consulta somente o estado operacional sanitizado. |
| `run` | `EXECUTOR` | Executa uma partição/modo já permitido pelos demais gates. |
| `replay` | `EXECUTOR` + `REPLAY` | Não ignora idempotência, escopo nem fences. |
| `sweep preview` | `OBSERVER` + `SWEEP_REVIEW` | Produz somente avaliação, sem aplicar ausência. |
| `sweep apply` | `EXECUTOR` + `SWEEP_APPLY` | Exige evidência de completude e protocolo próprio. |
| `force-run` | `EXECUTOR` + `FORCE_RUN` | Continua sujeito a lease, contrato, DQ e write-fence. |

`migrate` e `cutover` não pertencem ao conjunto de ações do runtime JAR. Devem usar processos,
identidades e concessões separados, com janela, aprovação e rollback próprios. A identidade do
runtime não recebe DDL, ownership, alteração de grants nem permissão de cutover/publicação fora do
protocolo definido no control plane.

## Fluxo de decisão fail-closed

Para cada ação protegida, a implementação deve seguir uma única ordem:

1. obter uma credencial pelo mecanismo externo aprovado, sem materializá-la em argumento ou log;
2. validar assinatura/prova, authority, audience e vínculo com o principal;
3. rejeitar credencial ausente, inválida, ainda não válida, expirada, bloqueada ou revogada;
4. resolver o mapeamento aprovado de principal para papéis, sem aceitar papéis autoafirmados;
5. exigir todos os papéis da ação na política interna versionada;
6. registrar decisão sanitizada na auditoria obrigatória;
7. somente após decisão positiva compor o handler, resolver segredos operacionais e iniciar I/O.

Timeout, erro de parsing, resposta ambígua, authority indisponível, mapeamento inexistente e falha
da auditoria terminam em negação. A comparação entre o fingerprint informado pela authority e o
mapping aprovado depende do adapter externo de V2-042b/c e ainda não libera caminho positivo.
Cache, quando futuramente aprovado, não pode ultrapassar expiração/revogação nem aceitar política
de versão diferente.

O boundary amostra o relógio confiável antes e depois da verificação. Avanço durante a verificação
é considerado ao validar a expiração; regressão entre as amostras é indisponibilidade temporal e
termina em negação, sem usar o instante retrocedido para emitir capacidade.

## Auditoria e minimização

O contrato de evento para uma decisão à qual uma fonte de tempo confiável pôde ser estabelecida
contém, no mínimo:

- timestamp UTC gerado por fonte confiável;
- correlation/decision ID opaco;
- ação; o escopo operacional sanitizado será incluído quando os contratos dos handlers existirem;
- resultado `ALLOW` ou `DENY`, reason code fechado e fingerprint da política interna.

Somente quando o verifier produziu uma identidade válida, o evento também contém referência opaca,
classe (`SERVICE` ou `OPERATOR`) e fingerprint declarado pelo adapter de autoridade. Recusas que
ocorrem antes de uma identidade ser verificada deixam esses três campos ausentes por desenho. O
formato opaco não é evidência de pseudonimização, e a conformidade do fingerprint externo com o
mapping aprovado permanece pendente de V2-042b/c.

O evento não contém credencial, token, claim bruto, username, e-mail, SID, subject externo, header,
URL sensível, payload, tenant nominal, documento ou stack trace com entrada não confiável. Mensagem
de erro pública usa apenas reason code e correlation ID. O value object prova somente formato e
redaction; o futuro adapter deve provar que a referência é produzida com pseudonimização opaca,
estável apenas no domínio de auditoria aprovado e não reversível pelo runtime.

Se nem o instante confiável puder ser obtido, a tentativa é negada com reason code temporal, mas o
contrato atual não fabrica um evento com timestamp. A integração durável deve definir como registrar
essa falha sem aceitar o relógio não confiável.

O composition root atual ainda não possui sink durável: seu sink indisponível transforma qualquer
tentativa operacional em `AUDIT_UNAVAILABLE`. Os testes com sinks sintéticos em memória provam o
schema/minimização e o comportamento fail-closed, não persistência operacional de auditoria.

A auditoria de autorização não prova sucesso da operação. O control plane registra separadamente
estado, tentativa, lease e resultado da execução.

## Ameaças e controles

| Ameaça | Controle obrigatório | Evidência esperada |
| --- | --- | --- |
| Invocação direta do JAR contorna wrapper | Boundary no composition root oficial; futuros handlers só podem ser ligados ao dispatcher depois da decisão | Teste chama o composition root oficial diretamente e recebe negação |
| Chamador autoatribui principal ou papel | Nenhuma identidade/papel em CLI, `-D`, arquivo ou ambiente; somente resultado verificado do adapter | Testes negativos para cada canal de entrada |
| Credencial roubada, repetida ou fora da vigência | Validação de authority/audience, janela temporal e revogação/bloqueio; capacidade ligada à ação/UUID/expiração | Casos ausente, inválido, futuro, expirado, bloqueado e revogado; consumo único ainda pendente do dispatcher |
| Relógio avança ou retrocede durante a verificação | Reamostrar antes da decisão, aplicar a amostra avançada e negar regressão como indisponibilidade temporal | Testes de expiração durante a verificação e de relógio regressivo |
| Confused deputy entre serviço e operador | Classe é auditada; mappings externos explícitos definem quais papéis cada classe pode receber | Conformance do mapping por classe em V2-042b/c |
| Papel amplo permite ação lateral | Capacidades aditivas, sem `ADMIN`/wildcard; todos os papéis mínimos exigidos | Teste de cada combinação incompleta |
| Falha do provider ou da auditoria libera carga | `deny-all` e auditoria obrigatória fail-closed | Testes de timeout, exceção e indisponibilidade |
| Injeção ou vazamento em log | Boundary único V2-023, evento estruturado, reason codes fechados, referências opacas, redaction e budget | Testes com entrada maliciosa, caps e inspeção de saída; não substitui a auditoria de identidade V2-042b/c |
| Runtime eleva privilégio no SQL Server | Principal SQL mínimo, sem DDL/cutover/ownership e procedures/fences dedicados | Gates negativos de grants e migrations |
| Decisão antiga usada após troca de política | Fingerprint/versionamento e validade limitada; dispatcher revalida expiração e consumo | Mismatch externo e consumo único pendentes de V2-042b/c |
| Alteração de classpath/binary troca o boundary | Artefato reproduzível, checksum/proveniência e release gate futuro | Evidência de empacotamento/release, ainda pendente |

## Entradas externas e condições de desbloqueio

Permanecem `EXTERNAL_INPUT_REQUIRED`:

- principals Windows reais restritos e responsabilidade administrativa;
- instância/banco/módulo administrados, certificado TLS e pins do artefato (sem issuer/audience OIDC);
- identificadores opacos dos principals de serviço e operadores;
- owner e matriz aprovada de mapeamento principal → papéis;
- SLA/comportamento de revogação, bloqueio e indisponibilidade;
- sink durável de auditoria e domínio aprovado de pseudonimização;
- identidades SQL separadas de runtime, migração e cutover.

Após essas decisões, um adapter específico deve passar testes de conformidade positivos e
negativos, incluindo falhas do provider e do sink. Até lá, o único adapter de runtime aceitável é
o não configurado, que nega todas as ações protegidas.

## Critério do subgate offline

V2-042a pode registrar como evidência apenas o modelo de ameaças, a política provider-neutral, o
default deny-all, os testes unitários/offline e a sanitização da auditoria. Isso não conclui
V2-042 globalmente e não autoriza rede, credenciais reais, release, deploy, operação externa,
migração nem cutover.

## Implementação Windows/SQL — Bloco 53

O owner adotou o mecanismo Windows/SQL; não falta outra decisão genérica de provider.
[ADR 0035](../adr/0035-authority-windows-sql-consumo-duravel-e-plano-temporal.md) define
trust store/TLS, recurso administrativo no artefato, policy compilada e módulo CALLER.
O cadastro protegido e sua referência UUID aleatória não podem ser alterados pelo runtime.
A auditoria ALLOW/DENY é append-only; recibo e consumo exigem ack completo. Sem conexão
ou relógio SQL confiável, há recusa sanitizada e nenhuma data inventada.

| Ameaça | Controle implementado e evidência | Limite restante |
| --- | --- | --- |
| CLI aponta para uma authority controlada pelo chamador | Recurso de quatro pins no artefato administrativo; sem override CLI/env; TLS valida servidor | Distribuição e ACL reais precisam de prova externa |
| Sysadmin/editor se apresenta como executor | SQL recusa privilégio excessivo; prova física rollback sob o contexto atual | SERVICE/OPERATOR restritos ainda não fornecidos |
| Resposta perde ack após auditoria/consumo | Adapter drena resultados; falha não entrega ALLOW nem despacha; teste de dupla utilização e ack incerto | ALLOW/CONSUME concorrente entre JVMs sob conta real ainda depende de provisionamento |
| Capacidade de status usada para executar | Escopo inclui ação/plano completo; consumidor compara; procedure de status só aceita READ | Provar grants efetivos no principal observador real |
| Policy/mapping revogados depois da decisão | Consumo compara versões/escopo/vigência novamente sob locks SQL | Não se promete consulta contínua ao Windows/AD nem revogação instantânea de sessão existente |
| Queda entre consumo e início do workload | Intent aponta para ocorrência original; nova invocação reautoriza e lê/retoma a mesma ocorrência | Teste operacional completo exige conta/fonte aprovadas |
| Workload autorizado varre outras ocorrências | Dispatch não chama recuperação global de leases; namespaces e dependência física testados | API administrativa legada e SQL arbitrário precisam de segregação na operação |
| Replay/catch-up avança sobre execução perdida | SQL guarda janela/UUID; resumo TOP limitado; coordenador bloqueia avanço contíguo na primeira lacuna | Matriz temporal nominal ainda não ratificada |

As permissões propostas são EXECUTE de objetos exatos. Entry points legados não se tornam
um firewall multitenant por receberem esse grant: quem puder emitir SQL arbitrário com a conta
SERVICE tem as capacidades desses módulos. A revisão operacional deve proteger o artefato e
seu contexto de execução e avaliar permissões herdadas. Nenhum grant foi aplicado para encobrir
essa distinção. Um teste como owner ou verifier no test-classpath não fecha V2-042b/c.

## Revisão B54 — superfície efetiva e lacunas ainda abertas

O JAR final executou sob SERVICE/OPERATOR com fonte loopback e TLS com pin na
authority e no workload. V018 protege 19 consumidores. V019/V020 foram qualificadas
em rollback e instaladas na quinta campanha, com cinco hashes reais de módulos
conferidos antes do commit. V020 vincula claims ao material da ocorrência SQL,
ao início da fronteira INCREMENTAL e às datas/modo/estratégia do plano temporal.
Os três grants autorizados foram aplicados, totalizando 25 EXECUTEs exatos;
entrypoints genéricos, tabelas e roles amplas não foram concedidos.

A matriz real em test-classpath separado confirmou 31 casos: dois de SQL direto,
22 campos adulterados, recibos, descarte de ack pelo cliente, corrida com um único
consumo e expiração. Isso não atesta a origem do código: outra sessão do mesmo
principal pode usar escopo consumido ainda válido. Administrador não é runtime
admissível. Descarte de ack não equivale a outage físico de transporte.

Revogação é revalidada no consumo e início de procedure; uma transação já admitida
pode concluir após revogação concorrente. Heartbeat não renova capability e não
há garantia de bloqueio instantâneo de sessão Windows. A primeira revogação de
teste confirmou no SQL e encontrou falha no gravador de saída nula. SERVICE ficou
revogado; compensação após readback exato restaurou revoked=0, avançou a versão
para 4 e preservou todos os outros campos e a validade original. OPERATOR e oito
scopes continuam v1; nenhum replay/force foi concedido. O defeito do gravador foi
corrigido e testado offline; o caso físico interrompido permanece sem PASS.

Faltam a matriz completa de revogação/policy, negativos de artefato/ACL/pin, queda
entre consumo/despacho e recuperação, replay/force, temporal executado e alerta
positivo pelo JAR. O drift atual recusou a ocorrência mas não gravou alerta.
G06/G07/G08 continuam abertos. B54 tem 99/128 reservas e cinco campanhas encerradas,
na fotografia anterior à autorização da sexta. O [ADR 0036](../adr/0036-laboratorio-runtime-local-consumidores-e-operacao.md)
e o [relatório B54](../runbooks/v2-022-bloco54-integrado.md) delimitam as provas atuais;
as seções anteriores conservam o contexto histórico em que foram registradas.

## Continuação da sexta campanha — regras e evidência atuais

B54-TIME-BIND-01: a seção E do prompt exige um consumidor temporal verificável.
`plan --temporal politica.json --request blueprint.json` emite até quatro requests
sem I/O de negócio. `run --request janela.json` valida ordinal, ocorrência, ciclo,
namespace, fuso, limites e política. Cada primeira tentativa exige consumo para
execução e outro para persistência do plano; a retomada de ocorrência conhecida
usa o caminho durável sem fonte e sem repetir a persistência com contrato diferente.
Exemplo: publicar 29/02 antes de 28/02 mantém a fronteira em 28/02; preencher 28/02
permite avançar até 01/03. RuntimeTemporalBindingTest cobre vínculo, adulteração,
exportação, Fretes/Coletas e reconciliação. É implementação/teste offline; a prova
física desse consumidor permanece pendente. Política de laboratório, sem owner
produtivo, scheduler ou aprovação de horários. Fretes exige predecessor explícito
da mesma janela; blueprints de janelas diferentes devem receber esse predecessor.

B54-DRIFT-ALERT-01: a seção D exige sink obrigatório. ContractDriftException aciona
um alerta CRITICAL CONTRACT_DRIFT_REJECTED com correlação da ocorrência, sob o
consumo existente; erro no sink recusa a operação. RuntimeRequiredDriftAlertTest
cobre alerta único, causa original, falha de sink e falhas não relacionadas.
Responsabilidade de contrato: papel contract-owner já usado no laboratório.
A recusa física anterior não virou prova de alerta; o JAR v4 ainda precisa do caso.

B54-UTC-01: o contrato interno de recuperação contém datetime2 UTC sem sufixo.
Interpretá-lo pelo fuso da estação deslocou o vencimento em três horas e recusou
OPS02, sem alterar a validade no banco. ParseExact/AssumeUniversal/AdjustToUniversal
preserva o instante original. Test-Bloco54RecoveryUtc.ps1 cobre duas culturas e
três formatos inválidos. A compensação exata da reserva 109 confirmou SERVICE v15,
scope 1 v10 e demais versões v1, perfil funcional original e 25 grants.

A sexta campanha confirmou seis corridas de mapping/scope/policy e dois casos de
consumo/encerramento/novo consumo em test-classpath. A queda não executou despacho
de negócio e não fecha recuperação do pipeline oficial. Total físico do harness:
39 casos, com as falhas históricas preservadas. B54 ficou em 109/128 reservas,
seis campanhas encerradas, 5.328 linhas e zero sessão/transação remanescente.
O JAR v4 foi instalado/diagnosticado e passou em exportação offline, sem run físico.
Java final: 1065 testes, zero falhas/erros, quatro skips preexistentes e gates verdes.
O pacote residual prepara 19 unidades; sétima campanha ainda não autorizada.
