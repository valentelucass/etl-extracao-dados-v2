# Proposta de Bloco 54 — runtime autorizado e operação local integrada

Preparado em 07/09/2026 a pedido do owner, após o Bloco 53 e o provisionamento
local efetivamente executado. Modelo solicitado para a execução: Astra xhigh.
Estado atual: **LOCAL_A_G_COMPLETE_NOMINAL_GATES_PENDING**, em 08/09/2026.
Conclusão, autorização posterior de novos lotes, evidência final e limites no
[relatório atual](v2-022-bloco54-conclusao-local.md). O escopo e os limites originais
abaixo ficam preservados como contrato histórico; dois lotes explícitos de 96
unidades ampliaram o teto para 320, sem devolver reservas. 302 unidades consumidas
em 30 campanhas encerradas. Nenhum aceite nominal/produtivo foi inferido.
A execução e suas pendências estão no [relatório B54](v2-022-bloco54-integrado.md).

O resultado pretendido é executar e recuperar Coletas/Fretes pelo JAR oficial,
sob as contas restritas existentes, com SQL real e uma fonte simulada em loopback;
completar as provas de autorização; integrar o planejamento temporal e entregar
uma operação manual verificável. A fonte simulada fica fora do JAR. A implementação
atravessa o cliente HTTP, os contratos e os adapters reais, sem acessar a ESL.

## Mensagem para abrir a execução

```text
Trabalhe no projeto:
C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2

Leia e execute integralmente:
docs/runbooks/prompt-bloco-54-runtime-operacional-local-astra.md

Execute o Bloco 54 integrado com Astra xhigh, concluindo as frentes A–G.
Mantenho a direção Windows/SQL e as autorizações locais já concedidas.
Reutilize ETL_SISTEMA_V2_SHADOW, etl_v2_exec e etl_v2_view já provisionados.
Não toque no ETL_SISTEMA nem peça novamente a escolha de banco ou contas.

Adoto o escopo adicional de laboratório da seção 3, inclusive o orçamento novo
de até 128 unidades, a fonte simulada somente em loopback, evolução aditiva do
schema e alterações temporárias de mapping estritamente delimitadas ali.
Preserve integralmente o ledger e os dados do Bloco 53; o novo orçamento não
devolve reservas anteriores. Schema, auditoria e dados sintéticos confirmados
permanecerão no banco, sem limpeza destrutiva.

Conclua implementação, testes físicos, JAR, planejamento e operação manual no
mesmo bloco. Prepare qualquer concessão adicional fora da seção 3 como pacote
concreto de aplicação, verificação e recuperação. Use as autorizações existentes
quando já cobrirem exatamente a ação. Não invente evidência de fonte, paridade,
política produtiva ou aceite. Preserve alterações preexistentes.
```

## 1. Ponto de partida e trabalho que não deve ser refeito

Revalidar os fatos no início da execução. A fotografia abaixo é histórica, não
um resultado de nova conexão/teste nesta preparação.

| Item | Evidência mais recente disponível | Consequência para o Bloco 54 |
| --- | --- | --- |
| Roadmap | 114 checkboxes: 60 concluídos, 54 pendentes; 196 rotas abertas; zero AGORA | Não criar sete checkboxes para as frentes nem prometer percentual |
| Motor e recuperação | Blocos 51–53; seis ITs físicas finais verdes; 95 tentativas e 75 publicações | Reutilizar o motor; repetir somente a regressão afetada e as novas travessias |
| Java | Java 17, 1042 testes, zero falhas/erros e quatro skips esperados | Baseline anterior; nova alteração exige validação própria |
| Banco | localhost / ETL_SISTEMA_V2_SHADOW; V001–V017 aplicadas; 4994 linhas totais após provisionamento | Banco populado, migrations aplicadas imutáveis; nenhuma reinstalação histórica |
| Identidade | etl_v2_exec SERVICE executor/observer; etl_v2_view OPERATOR observer | Não criar substitutos, reutilizar administrador como runtime ou pedir nomes novamente |
| Permissões | Dois logins/usuários Windows, 22 grants exatos (20 SERVICE e dois OPERATOR), oito scopes locais | Replay e force-run ainda não concedidos; nada de db_owner/CONTROL/DML direto |
| Confiança | Authority administrada; artefato protegido; JDBC de autorização com certificado público local validado | Não falta decidir provider, criar certificado de servidor ou reiniciar SQL |
| Credenciais | DPAPI do administrador, diretório protegido; contas e mappings com validade de 30 dias | Conferir vigência efetiva; não imprimir/decriptar para texto nem renovar silenciosamente |
| JAR restrito | STATUS em duas contas: autorizado, NOT_FOUND/exit 10; OPERATOR RUN: negado/20 | Prova parcial; não é extração/publicação pelo JAR nem matriz completa de autorização |
| Orçamento anterior | 112 reservas de 128; quatro campanhas encerradas | Preservar ledger; não reabrir campanha ou reciclar saldo como autorização nova |

O catálogo após provisionamento tem SHA-256
`df70c3666f94d6aaeefadc9f3842178611b8bf51bb9fa16d2f025eff21612f9f`.
O catálogo anterior sem contas tinha outro hash; a diferença autorizada de
principals/permissões não autoriza ignorar drift em módulos, tabelas ou constraints.

Os principais oráculos locais estão em
`target/bloco53/provisioning-local-accounts/`: resultado da instalação, postflight,
catálogo, ACLs e validator 053. Os hashes das migrations aplicadas estão em
`target/bloco53/schema/installed-migrations.json` e
`installed-additive-migrations.json`. Se faltarem artefatos, reconstruir evidência
por leitura limitada; não fabricar histórico Flyway nem reinstalar o banco.

As primeiras seções de alguns documentos de B53 antecedem a criação das contas.
A seção final de provisionamento realizado e o
[procedimento das contas locais](v2-042-contas-locais-windows.md) registram o avanço
posterior. Preservar a cronologia e corrigir afirmações apresentadas como atuais.

## 2. Leitura e conferência obrigatórias

Ler integralmente AGENTS.md, STATES.md, ../CONTEXTO_GLOBAL.md e a
[trilha](trilha-de-chats-gpt-5-6.md). As autorizações explícitas posteriores do owner
prevalecem sobre as restrições históricas mais estreitas, somente no escopo concedido.

Ler os ADRs 0007, 0010, 0011, 0012, 0014, 0022, 0032, 0033, 0034 e
[0035](../adr/0035-authority-windows-sql-consumo-duravel-e-plano-temporal.md), o
[modelo de ameaças](../seguranca/modelo-ameacas-autorizacao-runtime.md),
[database/README.md](../../database/README.md), os manifests e os runbooks de
[motor](v2-022-motor-local-coletas-fretes.md),
[recuperação](v2-022-recuperacao-duravel-local.md),
[B53](v2-022-bloco53-integrado.md),
[provisionamento](v2-042-provisionamento-windows-sql.md) e contas locais.

Confrontar essas decisões com Main, RuntimeCompositionRoot,
RuntimeOperationalRequest, RuntimeOperationalExecution, LocalColetasFretesRuntime,
WindowsSqlRuntimeAuthorization, RuntimeAuthorizationScope, os componentes temporais,
os adapters JDBC e V016/V017. Conferir os grants exatos e os efeitos de cada procedure.
Também ler os catálogos e runbooks de V2-012/V2-047/V2-050 usados na frente G.

Produzir antes da implementação uma matriz requisito → código atual → prova existente
→ lacuna → caso de teste → aceite canônico. Não redigir outro desenho abstrato para
substituir a implementação deste bloco.

## 3. Escopo adicional de laboratório para adoção

Esta seção descreve a execução futura proposta. Sua preparação não aplica as ações.
Não confundir a adoção do novo orçamento com a autorização anterior já consumida.

### Alvos e efeitos delimitados

- Banco de aplicação único: **localhost/ETL_SISTEMA_V2_SHADOW**. Master somente
  para preflight de metadados do alvo. Nenhuma conexão ao ETL_SISTEMA, esl_cloud,
  DASHBOARDS, DASHBOARDS_DEV, outro host ou outra base de negócio.
- Reutilizar somente etl_v2_exec e etl_v2_view. Sem nova conta, login, database,
  mudança de senha, grupo Windows, certificado de servidor, serviço ou firewall.
  Credenciais DPAPI existentes podem ser usadas em memória pelo contexto
  administrativo autorizado para iniciar os próprios filhos. UAC normal quando
  necessário; nenhuma forma de contornar a elevação ou usar SQL para elevar o host.
- Fonte de teste externa ao artefato: listener temporário em **127.0.0.1**, porta
  livre atribuída pelo sistema, sem bind público, redirect, proxy ou DNS externo.
  Somente GET dos recursos `/info` e `/data` dos templates 6908 e 6389, com fixtures
  inteiramente sintéticas. O código atual já admite HTTP em loopback: não relaxar
  HTTPS fora dele. Servidor e cliente limitados, encerrados ao fim da campanha.
- Token da fonte simulada, se exigido pelo cliente real, é efêmero e sintético,
  criado em memória e entregue somente ao ambiente do filho. Nunca usar .env,
  token ESL, credencial do legado, identidade falsa de autorização ou payload real.
  Limpar overrides herdados; não alterar ambiente global.
- Código, testes, scripts e migrations aditivas do V2 podem evoluir. Qualificar
  baseline versus upgrade em transações revertidas antes de instalar nova versão.
  V018 é apenas a próxima candidata na fotografia atual: conferir colisões.
  DDL confirmado e dados sintéticos ficam preservados; sem down migration/reset,
  repair, purge, DELETE/TRUNCATE de negócio/auditoria ou restauração destrutiva.
- Preservar o pacote B53. Preparar o novo artefato em
  `target/bloco54/reviewed-bundle`; a cópia protegida de ensaio pode ocupar somente
  `C:\ProgramData\EslEtlV2\app-bloco54`, com Administradores/SYSTEM para escrita e
  RX para as duas contas. Recusar conteúdo preexistente não pertencente à campanha.
  Sem sobrescrever `app`/`secrets`, trocar a distribuição anterior ou instalar serviço.
- Os 22 grants atuais não se ampliam automaticamente. Concessões novas, mesmo
  para métricas/alertas/health, exigem primeiro pacote concreto com objetos exatos,
  conta, justificativa, verificação e compensação. Aplicar se autorização existente
  cobrir exatamente o resultado; caso contrário apresentar esse delta ao final,
  após concluir todo trabalho independente. Nenhum grant schema-wide ou DML direto.

### Mappings temporários para provas adversariais e replay

O escopo proposto inclui alterar administrativamente somente os dois mappings
locais já existentes e seus oito scopes em LOCAL_SHADOW/LOCAL_V2/LOCAL_V2, para
casos de versão, vigência, revogação e divergência de policy. Não trocar SID,
audit_reference, classe da conta ou identity da authority. Não desligar conta/login
Windows para simular revogação; testar esse mecanismo por alternativas isoladas e
registrar separadamente a prova que faltar sobre bloqueio do sistema operacional.

Para provar os handlers REPLAY e FORCE_RUN, o pacote de ensaio pode acrescentar
temporariamente esses dois papéis **somente ao SERVICE**, dentro da mesma janela
de campanha de até 15 minutos, e até dois scopes REPLAY (Coletas/Fretes) no mesmo
namespace. OPERATOR continua observer. Nenhum papel SWEEP, DDL, migração ou cutover.
Não fabricar capability em Java: o SQL real precisa emitir e consumir a decisão.

Antes de qualquer alteração, registrar e verificar um diff exato, hashes, versão,
prazo e compensação. Encurtar a vigência do mapping durante a concessão excepcional;
o prazo limita o risco mesmo se o controlador cair. Casos são seriais e exigem
ausência de uso alheio dessas contas. Ao fim, retirar os papéis adicionais e revogar
os scopes de teste novos, mantendo suas linhas; restaurar os direitos originais
com **versões crescentes**, sem revalidar capability anterior ou estender a validade
original das contas. Restaurar valores funcionais não é restaurar versão antiga.

Se houver queda/ack incerto, ler o estado real antes de compensar. Falha de
compensação impede novo despacho; preservar evidência e deixar o mapping afetado
revogado/expirado. Não repetir cegamente o instalador B53. Provar também a recuperação
do próprio harness a partir desse estado parcial, sem apagar registros.

### Orçamento único do Bloco 54

| Recurso | Teto cumulativo proposto |
| --- | --- |
| Unidades reservadas | 128 novas unidades para todas as frentes e tentativas |
| Definição da unidade | Cada nova invocação protegida, ocorrência de fixture sem invocação ou janela temporal; reservar antes de I/O, sem cobrança duplicada da mesma invocação/ocorrência vinculada |
| Por unidade | Até 16 entradas, quatro páginas e 512 linhas derivadas, incluindo auditoria |
| Total de novas entradas/páginas/derivados | 2048 / 512 / 65536; medidos também por diferença agregada no SQL |
| HTTP simulado | Até 1024 requests no bloco, 1 MiB por resposta, quatro páginas por unidade; sem chamadas externas |
| Concorrência | Até duas JVMs de runtime filhas; quatro conexões SQL totais, incluindo observador/controlador |
| Prazos | SQL/request até 30 s; filho até 60 s; até quatro campanhas de 15 min cada |
| Saída de filho | Até 16 KiB; erro/timeout/estouro encerra apenas os processos próprios |

Contar negações, falhas, repetição e nova invocação sobre a mesma execução; não
devolver reserva. Testes unitários sem I/O não consomem esse orçamento físico.
O servidor simulado fica no controlador ou processo não JVM, sem abrir outra JVM
de runtime além do teto. Limitar também suas saídas e vida útil.

Ledger B54 novo, exclusivo, durável e fechado por campanha; guardar hash/cópia de
leitura do ledger B53, sem editá-lo. Pela fotografia atual, 112 anteriores + 128
novas = no máximo 240 unidades reservadas acumuladas nas duas entregas. Preflight
deve conferir a fotografia e explicar qualquer avanço legítimo antes de prosseguir.
Nenhum orçamento/deadline pode ser ampliado automaticamente. Priorizar a matriz
obrigatória e consolidar cenários para caber nos limites; ao atingir teto, concluir
o trabalho offline e registrar precisamente o que não foi executado.

São excluídos: ESL/GraphQL reais, feed/NVD/download, produção, sondas de legado,
Git commit/push, remote/CI, scheduler/job, restart, deploy produtivo e cutover.
Comparação futura com ETL_SISTEMA será preparada na frente G, sem leitura dessa base.

## 4. Frentes integradas obrigatórias

### A. Consolidar governança de identidade com os fatos existentes

Entregar inventário sanitizado e versionado do mecanismo Windows/SQL, authority e
destinatário equivalentes, administração de papéis, principals existentes, escopos,
vigências, revogação, pseudonimização UUID e sink durável. Referenciar autorização
real do owner e prova local; não inventar Entra/AD, issuer OIDC, compliance owner,
principal produtivo ou tenant ESL. Segredos/SIDs/nomes pessoais não entram no Git.

Definir quem pode administrar a configuração local já instalada e o que fica fora
da sua autoridade. Explicitar o prazo real de revogação: consumo revalida SQL,
heartbeat não prorroga autorização, sessão Windows aberta não prova bloqueio
imediato no sistema operacional. Dar tratamento verificável a expiração de conta,
mapping, capability, pin e certificado, sem renovar nada por conveniência.

Reconciliar ADR 0035, threat model, runbooks, manifests, STATES e G06. A ausência
histórica de contas/TLS deixa de ser impedimento atual; dados nominais da fonte e
governança produtiva conservam seus próprios limites. V2-042b fecha apenas se todos
os seus critérios estiverem documentados e comprovados no escopo de aceite real.
Não usar apenas o resultado do instalador como substituto dessa matriz.

### B. Artefato confiável, launcher fino e diagnóstico local

Entregar launcher manual one-shot que valida hash/configuração/vigência/alvo,
inicia uma nova sessão restrita com as credenciais já protegidas e devolve reason
code/exit code e correlação. Não criar menu que esconda erro, retry automático,
arquivo PID, daemon ou estado concorrente ao SQL. Invocação direta do JAR precisa
ter as mesmas recusas; segurança não pode depender do launcher.

Conferir integridade de todos os entries do JAR e das dependências; a incorporação
do recurso administrativo deve ter diff verificável. Fonte simulada, verifier
fake, SQL simulado e chaves de bypass permanecem fora do pacote oficial.

Auditar TLS em **ambas** as conexões: autorização e workload. A prova anterior da
conexão de segurança não comprova configuração idêntica do DataSource de negócio.
Corrigir inconsistências sem trustServerCertificate permissivo, alteração global
de truststore ou troca do certificado do SQL. Manter compilação Java 17 portátil;
outro SO ou trust material ausente deve recusar o adapter Windows.

Entregar diagnóstico somente leitura de instalação/validade/permissões; distinguir
NOT_FOUND (status autorizado, exit 10) de falta de autorização (20) e publicação
comprovada (0). Preparar procedimento concreto de manutenção/renovação e atualização
de artefato, com recuperação da falha após commit e validade original preservada.
Não executar renovação ou reinstalação completa como parte do diagnóstico.

### C. Completar autorização física, consumo e auditoria

Executar a matriz da seção 5 sob as contas Windows reais e SQL real. Cobrir a
vinculação integral dos 22 campos de escopo, fingerprints, expiração, mudança de
mapping/policy, replay de recibo, uso único e falha antes/depois de commit.

Exigir observação independente em outra conexão limitada: decisão e consumo
duráveis, ocorrência original, nenhum despacho sem ack completo. Duas JVMs competem
pela mesma capacidade/invocação; threads ou EXECUTE AS não substituem essa prova.
Casos de protocolo instrumentados no test-classpath são úteis, mas identificados
como tal e acompanhados das travessias de JAR possíveis sem instrumentação.

Mapear a superfície de SQL direto dos 22 grants. O ADR reconhece que os entrypoints
legados não são um firewall multitenant. Demonstrar quais efeitos cada conta pode
produzir fora do JAR e se isso respeita a garantia declarada. Se houver bypass de
autorização requerido pelo aceite, corrigir com consumidor e fence reais; não mudar
o threat model para aceitar a falha nem resolver com db_owner. Concessões novas
seguem o pacote concreto da seção 3; nenhum fence em SESSION_CONTEXT autoafirmável.

Registrar negação sanitizada quando possível; indisponibilidade de relógio/sink
não pode fabricar timestamp, ALLOW ou sucesso. Preservar cause internamente sem
vazar input, SID, token, connection string ou payload na saída pública.

### D. Executar o JAR oficial de ponta a ponta com fonte em loopback

Usar o Main real via `java -jar`, nova sessão SERVICE, configuração administrada,
HTTP real para o servidor sintético e JDBC real. Exercitar Coletas e Fretes desde
`/info`/`/data` até streamer, mapper, staging, candidate set, DQ, publicação e status.
Provar recibos e contagens exatos no SQL; HTTP 200 isolado nunca é aceite.

O request mantém fingerprints e namespace coerentes com a configuração efetiva;
não editar catálogo para aceitar qualquer shape/host. As fixtures devem reproduzir
o contrato local aprovado, inclusive campos obrigatórios e página terminal. Nenhum
teste representa completude, paginação ou dados efetivos do fornecedor.

Provar RUN e, no intervalo governado da seção 3, REPLAY/FORCE_RUN; status antes e
depois; repetição da mesma ocorrência; recuperação após queda própria; ack perdido;
parcialidade; lease; DQ; contrato; cancelamento e dependência Coletas→Fretes.
Para ocorrência conhecida, a nova invocação não recompõe fonte nem reextrai.
Replay referencia a origem e não move a fronteira incremental. Force-run não rouba
lease nem ignora qualidade/contrato. OPERATOR nunca ganha poder de escrita.

Completar lacunas reais de composição, inclusive o sink de alertas de drift hoje
recusado em RuntimeOperationalExecution, a instrumentação de tentativas e os
resumos de métricas/health. Reutilizar o framework V2-023; falha no sink obrigatório
recusa a operação. Sem sucesso vazio para satisfazer CLI ou dependência ainda fechada.

### E. Planejamento temporal com consumidor e retomada reais

Ligar a política/plano/coordenador já existentes a um comando ou caso de uso
one-shot verificável. Separar preview offline de persistência/execução autenticada;
`plan` não pode adquirir efeitos silenciosamente. Toda nova ação protegida precisa
de RBAC, fingerprint, recibo e consumidor reais; grants novos seguem seção 3.

Entregar arquivo de política **de laboratório** explícito, estrito e versionado
para os dois workloads: IANA, modo/estratégia, cadência, estabilização/lookback,
SLA/deadline, DAG, concorrência, blackout, reconciliações e degradação. Marcar valores
como sintéticos; não copiar horário do legado ou apresentar aprovação produtiva.

Provar persistência idempotente, reinício, conclusão fora de ordem e fronteira
contígua sem salto, catch-up, backlog, stale RUNNING, limites de reconciliação e
degradação. Incluir fevereiro bissexto, dezembro/janeiro, dia curto/longo, gap/overlap
de fuso e mês civil anterior com período auditado. Consultas retornam até 64 resumos,
até quatro pendências, sem carregar histórico completo na JVM.

Fechamento mensal de janela não equivale a materialização dos cinco fatos ainda
pendentes. Reconcile de plano não equivale a paridade; sweep-preview não equivale
a sweep-apply. Manter comandos sem consumidor admissível com recusa explícita.
Inventariar todos os aceites originais de V2-022; pai permanece aberto se faltar
qualquer comando, consumidor, matriz nominal ou prova exigida.

### F. Qualificação integrada e evidência reproduzível

Completar código e testes focados por defeito, depois uma suíte Java 17 offline
completa sobre a implementação final, com estilo/arquitetura/cobertura intactos.
Não acrescentar skips ou relaxar constraints para passar. Repetir suíte completa
somente quando mudança, falha ou risco novo justificar.

Isolar build/evidências de B54. `clean` canônico destruiria material ignorado sob
target: usar build directory próprio e conferir equivalência do POM temporário
fora dessa única diferença. Copiar DLL do cache na fase prevista após clean;
não executar Failsafe direto sem o preparo necessário nem baixar dependências.

Guardar antes/depois: schema/migrations, grants, mappings/scopes e versões,
contagens, ledger, decisões/consumos, sessões/transações e hashes de artefato.
Dados antigos, inclusive oito EXTRACTING sintéticos retidos, não são autorização
para stale recovery global, limpeza ou renovação. Encerrar somente filhos próprios.

Usar validator 053 para a instalação nominal pós-provisionamento e criar/evoluir
validação específica das novas mudanças com asserts exatos. Validators históricos
005/050/051 pressupõem fase anterior; 002/006/049 não são reinstaladores do banco
moderno. Preservar os testes históricos e selecionar a fase correta, sem retirar
guards para acomodar o catálogo novo. Após replay de teste, admitir somente os
scopes adicionais explicitamente revogados e as versões avançadas previstas.

### G. Preparar a comparação futura e os próximos desbloqueios

Reutilizar V2-012/Q-FND-01/Q-FND-02, o planejamento de V2-047 e a medição V2-050.
Entregar um pacote de revisão para a primeira comparação de Coletas/Fretes:
campos/métricas/regra, grão, namespace, janelas, orçamento, origem dos oráculos,
proveniência, owner-papel e condições de aprovação. Conferir o catálogo já
versionado e o legado estático autorizado; não abrir o projeto de dashboards.

Exercitar a ligação do relatório de comparação com dois conjuntos sintéticos
independentes: igualdade, divergência monetária/status, duplicidade, ausência,
fronteira temporal e dado incompleto. Não usar o mesmo reducer para produzir
resultado e oráculo. Cálculos sobre massa ficam no SQL; saída sanitizada limitada,
sem IDs de negócio, payload ou coleção de todas as chaves na JVM.

Preparar consultas/contratos de exportação somente leitura e o procedimento de
primeira rodada real, com alvo e impacto concretos, **sem conectar ao ETL_SISTEMA**,
criar cross-database views/synonyms ou conceder acesso a ele. Se faltar contrato
nominal, deixar campo explicitamente pendente; script de execução real deve recusar
o pacote incompleto. Não executar fonte real, bootstrap ou comparativo produtivo.

Essa frente entrega preparação e prova sintética de ligação; não fecha V2-012a/b/c,
V2-047, V2-038, V2-050, paridade ou cutover. Consolidar os inputs restantes em uma
única lista concreta, explicada para o owner sem exigir que ele invente nomes
técnicos, principals ou informações que o agente consegue verificar localmente.

## 5. Matriz mínima de provas novas

Todos os casos recebem ID, camada exercitada, conta, comando, reserva, efeito
esperado/observado e log sanitizado. Não contar PASS de mock como PASS de JAR/SQL.

| Casos | Prova obrigatória | Observação independente |
| --- | --- | --- |
| AUTH-01–03 | SERVICE run/status permitidos; OPERATOR status permitido/run negado; replay/force negados sem papéis | Nova sessão Windows por conta, decisão/consumo e zero fonte na negação |
| AUTH-04–06 | Config/pin/policy adulterados; autoatribuição CLI/env/-D; namespace/ação/modo divergentes | Nenhum cliente de negócio/fetch antes da autorização válida |
| AUTH-07–09 | Vigência/expiração entre decisão e consumo; mapping/scope revogados; versão/policy trocada | Consumo recusado, nenhuma operação com a capacidade antiga |
| AUTH-10–12 | Duas JVMs, mesma invocação; recibo reusado/adulterado; falha/ack incerto do sink | Um consumo no máximo, auditoria idempotente, despacho somente com confirmação |
| AUTH-13–14 | Queda entre consumo e despacho; nova invocação após reinício | Mesma ocorrência/material, sem orphan permanente, duplicação ou fetch indevido |
| AUTH-15–16 | Grants e efeitos por SQL direto; truststore/artefato/ACL inválidos | Nenhum DDL/ownership/autoatribuição, efeito efetivo conforme threat model; falha TLS não tem fallback |
| RUN-01–04 | Coletas e Fretes completos; status NOT_FOUND e PUBLISHED | JAR oficial, HTTP loopback, JDBC físico, recibos/contagens exatos |
| RUN-05–08 | Reexecução, replay explícito, force-run autorizado e seus fences | Efeitos únicos; origem correta; fronteira incremental intacta no replay |
| RUN-09–12 | Parcial/lease/ack perdido/cancelamento; retomada em nova JVM | Sem promoção indevida, vazamento de conexão ou extração de ocorrência conhecida |
| RUN-13–15 | Coletas bloqueia Fretes; ramo independente preservado; drift/DQ/sink falham | Dependente sem fetch, publicação anterior preservada, reason/exit code corretos |
| TIME-01–04 | Catch-up/restart/out-of-order, lacuna, limite de backlog, namespaces | Janelas/ocorrências únicas e fronteira contígua no SQL |
| TIME-05–08 | Mês civil anterior, bissexto/virada de ano, fuso/blackout, reconciliação/degradação | Período auditado, limites respeitados, nenhuma agenda ativada |
| OPS-01–03 | Launcher direto/JAR, estado parcial de harness, compensação/expiração de mapping | Mesma segurança, direitos originais restaurados com versões novas ou bloqueio explícito |
| CMP-01–03 | Comparação sintética igual/divergente/incompleta | Oráculo independente; nenhum aceite real de paridade |

Ack descartado pelo cliente após commit prova essa janela de falha; não descrever
como outage real de transporte. Perda física de conexão, quando ensaiada, afeta
somente conexão/processo de teste próprio, nunca o serviço SQL. Tempos e polling
do controlador pertencem aos deadlines da campanha.

## 6. Cobertura de todas as pendências atuais

A seleção abaixo cobre os **54 checkboxes abertos** da fotografia, incluindo pais.
As 196 rotas detalhadas continuam na trilha; não criar outro backlog concorrente.

| Pendências existentes | Tratamento neste bloco e condição restante |
| --- | --- |
| V2-042, V2-042b, V2-042c, V2-022, V2-022b | Caminho principal A–F; perseguir aceites existentes com provas reais, sem fechamento automático de pais |
| V2-041 | Hold de rotação/invalidação e saúde do writer; não depende das senhas novas das contas Windows; nenhuma credencial real de fonte usada |
| V2-016, V2-016b | Remote/proteção/CI ainda exigem alvo e autorização; preservar trabalho local, sem commit/push |
| V2-015, V2-015c, V2-015d | Gates locais executados em F; baseline real de vulnerabilidades/feed e release por onda conservam seus gates |
| V2-045, V2-045b | Retenção/WORM/backup/restore/RTO/RPO produtivos permanecem pendentes; sem purge ou invenção de TTL aprovado |
| V2-025, V2-025d | Contratos locais alimentam D/G; rodada Data Export real depende de V2-041, janela e teto próprios |
| V2-025c, V2-009c, V2-034, V2-034a, V2-034b | Raster depende de decisão de necessidade/consumidores e autorização específica; não entra no laboratório |
| V2-009, V2-009b, V2-009b/10633, V2-009b/8636, V2-009b/4924, V2-009b/6392, V2-009d | Contratos de identidade/enforcement conservados; nenhuma chave inferida para fechar as quatro verticais retidas |
| V2-029, V2-030, V2-031, V2-032 | Implementação depende dos grãos/identidades e contratos acima; não expandir o runtime para essas verticais neste bloco |
| V2-035, V2-035a, V2-035b | Baselines mutáveis e dimensões/consumidores precisam das provas próprias; não semear referência produtiva |
| V2-046, V2-046a, V2-046b | Relações/crosswalks reais não são consequência da publicação sintética de Coletas/Fretes; conservar os gates de relação |
| V2-012, V2-012a, V2-012b, V2-012c, V2-047 | Preparar comparação e primeira rodada em G; sem oráculo aprovado não executar caracterização/bootstrap/paridade reais |
| V2-013 | Sweep permanece sem entidade habilitada/completude; testar proteção, não ativar apply |
| V2-036, V2-037 | Fatos e contratos consumidores precisam de grão/owner/contrato; mês calculado e relatório sintético não são materialização/publicação |
| V2-050, V2-038 | Medições e E2E locais de F/G ajudam a preparação; escala, oráculo e qualificação por entidade/onda continuam separados |
| V2-039, V2-039a, V2-039b | Launcher/diagnóstico/artefato em B/F avançam trabalho local; scheduler, retenção, segurança, release e infraestrutura ainda têm dependências |
| V2-048, V2-048b, V2-014, V2-040 | Topologia/ensaio/corte/desativação exigem gates anteriores e autorização nominal; nenhum efeito neste bloco |

## 7. Aceites e sincronização canônica

As frentes são partes de um único bloco de execução, não sete novos blocos. A
preparação mantém zero AGORA e Bloco 54 não atribuído. Quando o owner adotar a
execução, reconciliar G06 → G07 → G08 conforme dependências satisfeitas e registrar
um único pacote B54, sem duplicar numeração/aceites. Não promover rota ainda sem
inputs; executar suas implementações independentes sob o escopo local explícito.

O validator atual fixa 114/60/54, G06/G07/G08 abertos e zero AGORA. Esses guards
representam a fotografia de B53, não uma proibição eterna de concluir as tarefas.
Na execução futura, evoluí-los junto da evidência e das rotas; manter contraprovas
de aceite falso, duplicação e divergência de painel. Nesta preparação não alterá-los.

| Aceite perseguido | Evidência mínima para marcar |
| --- | --- |
| V2-042b / G06 | Todos os inputs/governança do escopo real registrados e aprovados; faltas produtivas separadas sem redefinir o aceite para esconder ausência |
| V2-042c / G07 | Adapter real, matriz positiva/negativa, consumo único/vigência/revogação, sanitização e JAR oficial; dependência G06 satisfeita |
| V2-042 pai | Todos os critérios originais de identidade/RBAC/JAR/auditoria e ausência de DDL/cutover satisfeitos, incluindo proteção das ações ainda indisponíveis |
| V2-022b / G08 | G06/G07 aceitos, dispatcher/handlers reais com autorização e auditoria duráveis, evidência direta pelo JAR; fonte local simulada identificada como tal |
| V2-022 pai | Todos os comandos e consumidores admissíveis, política/matriz exigida, plano/catch-up/mês/recovery/limites/exit codes aceitos; qualquer falta conserva o pai aberto |

Não antecipar gates de fonte, identidade de negócio, referências, completude,
relações, paridade, escala ou produção. Se os quatro primeiros checkboxes forem
comprovados, a fotografia passaria a 64/114 (56,1%); 65/114 (57,0%) somente com o pai
V2-022 integralmente aceito. São consequências condicionais da contagem, não meta
para ajustar critérios. Nenhum novo subcheckbox apenas para valorizar preparação.

## 8. Entrega final e verificações

Entregar implementação completa A–G dentro dos limites; casos executados e lacunas
por camada; relatório integrado com comandos, resultados e recuperação; diff desta
sessão separado das alterações preexistentes; baseline/migrations/hashes; inventário
final de direitos, scopes temporários revogados, contas/vigências e ledger cumulativo.

Executar os validators afetados, scanner offline, UTF-8 estrito sem BOM e
`git diff --check`. Guardar logs sem segredo e evidências físicas fora do Git,
preservando o snapshot inicial. A suíte Java/SQL anterior não vira evidência nova.

O handoff explica em português simples o que já se pode executar localmente, qual
prova ainda falta e qual ação concreta exige outro escopo. Preparar primeiro todo
pacote adicional: alvo, SQL/permissões exatos, impacto, preflight, verificação e
recuperação. Não repetir perguntas sobre banco/contas já resolvidas. Se houver
impedimento real, concluir as frentes independentes antes de pedir o único input
que não possa ser obtido com as autorizações e ferramentas existentes.
