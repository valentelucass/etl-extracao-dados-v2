# Prompt ampliado para outro chat — Bloco 53, motor e autorização integrados

Preparado em 07/09/2026. O owner pediu mais escopo por chat usando Astra xhigh e
delegou a escolha da melhor abordagem de identidade. Este é o prompt de abertura
recomendado, substituindo a proposta limitada a P02Q. Sua preparação não inicia
execução, não concede permissões e não fecha gates.

## Mensagem para enviar no próximo chat

```text
Trabalhe no projeto:
C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2

Leia e execute integralmente:
docs/runbooks/prompt-bloco-53-pacote-integrado-runtime-astra.md

Quero o Bloco 53 ampliado com Astra xhigh: qualificação SQL física, identidade
Windows integrada, auditoria durável, autorização consumível, dispatcher/CLI
integrados e planejamento temporal. Execute todas as frentes locais em conjunto.

Adoto a direção técnica Windows/SQL descrita neste prompt. Autorizo o escopo
físico da seção 2 do prompt P02Q referenciado: somente localhost e o banco
ETL_SISTEMA_V2_SHADOW já existente, autenticação Windows existente, evolução
versionada do schema e commits sintéticos limitados. A transição histórica
só é permitida se comprovadamente vazia e compatível. Estou ciente de que
schema e dados sintéticos confirmados permanecerão no banco, sem limpeza destrutiva.

Não encerre ao terminar somente P02Q. Conclua também implementação e provas
locais das demais frentes. Para qualquer provisionamento de identidade ou
permissões fora daquele escopo, entregue primeiro o pacote concreto para revisão.
Não invente contas, aprovações ou evidências externas. Marque apenas os aceites
comprovados, preserve mudanças preexistentes e mantenha os limites operacionais.
```

## 1. Resultado esperado e composição do pacote

Trabalhe exclusivamente em etl-extracao-dados-v2. Leia integralmente AGENTS.md,
STATES.md, ../CONTEXTO_GLOBAL.md, a trilha de chats e este prompt. Leia os ADRs
0007, 0010, 0011, 0012, 0014, 0022, 0032, 0033 e 0034, o modelo de ameaças de
autorização, database/README.md e os runbooks do motor e da recuperação locais.

Reconfira o estado no início. A fotografia desta proposta é 59/113 itens concluídos,
196 fatias abertas, P02R/B52 local concluído e 1023 testes com quatro skips esperados.
Bloco 53 ainda não atribuído. Preserve avanços posteriores e todo o worktree preexistente.

O bloco reúne as frentes abaixo, sem fragmentá-las em novos chats para desenho,
interface, implementação e testes. Decisões técnicas rotineiras pertencem ao agente.

| Frente | Trabalho concreto | Aceite canônico perseguido |
| --- | --- | --- |
| A | Qualificar e corrigir o motor/recuperação em SQL físico | P02Q/V2-022/QUALIFICACAO_FISICA_LOCAL |
| B | Implementar identidade Windows/SQL e materializar o contrato de provisionamento | V2-042b, somente após inputs reais comprovados |
| C | Auditoria durável, autorização vinculada ao escopo e consumo único | V2-042c e V2-042, conforme provas completas |
| D | Composição oficial, dispatcher e handlers com autorização positiva | V2-022b/G08, conforme execução real autorizada |
| E | Planejamento temporal, catch-up, fechamento mensal e reconciliação do plano | Aceites restantes de V2-022 |
| F | Regressão integrada, privilégio mínimo, empacotamento e evidências | Fechamento somente dos itens comprovados |

Esta ampliação não torna G06/G07/G08 automaticamente elegíveis. Registre quais inputs
existem, implemente o trabalho independente e satisfaça as dependências na ordem real.
Uma fronteira implementada e ainda desabilitada é entrega de código, mas não fecha
integração operacional. Não trocar esse limite por uma porcentagem desejada.

## 2. Direção técnica escolhida

A direção para a primeira implementação é **autenticação integrada do Windows no
SQL Server, com autorização explícita governada no banco**. Ela aproveita o mecanismo
integrado já previsto no projeto. Não pressupõe que exista Active Directory, Entra ID,
domínio, conta de serviço ou principal restrito já provisionado.

O JDBC Microsoft oferece `integratedSecurity=true` e autenticação nativa no Windows;
a implementação deve usar o driver/DLL já fixados no repositório. A documentação
oficial descreve esse mecanismo em [autenticação integrada JDBC](https://learn.microsoft.com/en-us/sql/connect/jdbc/using-kerberos-integrated-authentication-to-connect-to-sql-server?view=sql-server-ver17).

Separe três responsabilidades concretas:

1. Windows/SQL Server autentica a conexão pelo mecanismo integrado; nome, papel,
   SID ou usuário informados por CLI, propriedade, arquivo ou ambiente não autenticam.
2. Um módulo SQL fechado resolve o principal original autenticado, consulta a política
   administrada, valida escopo/vigência/revogação e devolve somente resumo limitado.
3. O boundary Java compara as políticas, exige auditoria e entrega uma capacidade
   vinculada à invocação/ação/plano/escopo. O consumidor revalida no uso efetivo.

`ORIGINAL_LOGIN()` identifica o login inicial mesmo se módulos mudarem o contexto;
identidade de `EXECUTE AS OWNER` não pode ser confundida com a do chamador.
Conferir resolução de SID, tipo de autenticação, contexto e recusas no SQL real;
não usar apenas um username da JVM. Ver [ORIGINAL_LOGIN](https://learn.microsoft.com/en-us/sql/t-sql/functions/original-login-transact-sql?view=sql-server-ver17)
e [SUSER_SID](https://learn.microsoft.com/en-us/sql/t-sql/functions/suser-sid-transact-sql?view=sql-server-ver17).

O cadastro protegido relaciona a identidade autenticada a referência de auditoria
opaca e aleatória, classe SERVICE/OPERATOR, atribuições, escopos, versão e vigência.
A referência não deriva de hash público de SID/nome e não é cadastrável pelo runtime.
Desenhe revogação, troca de mapping e pseudonimização conforme o threat model.
O runtime não lê a tabela de identidades nem recebe SID, login ou claims brutos.

A authority confiável é a instância/base/módulo explicitamente configurados por
administração separada. Validar alvo e identidade do servidor; a configuração do
chamador não pode apontar para uma authority de sua escolha. Não fabricar issuer ou
audience OIDC para este mecanismo: documente seus equivalentes reais de confiança e
escopo de destinatário. Instância local de teste não ratifica uma authority produtiva.

Sem cadastro válido, prova de autenticação, policy, sink ou prazo confiável, negar.
Revogação e versão devem ser revalidadas no consumo; login Windows previamente aberto
não prova que a conta continua habilitada. Não prometer revogação imediata de sessão
existente sem mecanismo que a demonstre. Use validade curta explícita, sem cache
positivo além da validade e sem heartbeat capaz de estender autorização antiga.

O adapter Windows é uma borda substituível; não quebrar compilação/portabilidade do
núcleo nem habilitar fallback permissivo em outro SO. Atualize o ADR e o threat model
durante a implementação, preservando as garantias anteriores.

## 3. Limites e provisionamento

A frente A reutiliza integralmente as seções 2 e 3 do
[prompt P02Q](prompt-bloco-53-qualificacao-fisica-motor-astra.md): alvo exato, preflight,
transição vazia, commits sintéticos, orçamento cumulativo, deadlines e preservação de
dados. O orçamento é compartilhado pelo bloco inteiro; não duplicá-lo por frente.
O escopo de schema preparado pode incluir auditoria/autorização deste pacote, sem
seedar identidades ou permissões operacionais.

Para B–F, a ampliação autoriza implementar adapters, SQL versionado, composição,
handlers, política temporal, testes e scripts de provisionamento revisáveis. O
default oficial continua deny-all sem a configuração confiável completa. A frase
anterior do P02Q que mantém toda composição inalterada vale para a prova A isolada;
neste pacote, a composição pode evoluir com os gates de B–D efetivamente satisfeitos.

Não alterar contas Windows, senhas, logins SQL, memberships, permissões de serviço,
ACLs do host ou grants operacionais nesta preparação. Não presumir que a conta do
editor seja a conta de serviço, nem atribuir-lhe EXECUTOR automaticamente. Produza
antes um pacote de provisionamento com identidade/alvo, permissões exatas, verificação,
impacto e recuperação; a aplicação exige autorização específica ao resultado concreto.

Descobertas de identidade, quando necessárias à execução futura autorizada, devem ser
restritas e sanitizadas: não despejar usuários, SIDs, grupos ou permissões do host.
O modelo decide como configurar; nomes/principals/owners reais continuam fatos a verificar.
Se faltar conta restrita, entregue o verificador/preflight e o script já pronto para
ela, sem abandonar as implementações independentes ou fazer várias perguntas genéricas.

Contas com `sysadmin`, ownership ou direitos administrativos não comprovam least
privilege; `sysadmin` possui capacidade administrativa ampla, conforme a
[documentação de roles SQL Server](https://learn.microsoft.com/en-us/sql/relational-databases/security/authentication-access/server-level-roles?view=sql-server-ver17).
`EXECUTE AS` em uma fixture não prova autenticação de outra conta Windows em nova JVM.
Não criar um modo especial que dispense essas recusas para deixar o teste verde.

Migrations administrativas e o runtime usam contextos separados. Uma permissão nova
de recuperação/auditoria deve ser de procedure exata e justificada, nunca schema-wide,
CONTROL, db_owner, impersonação ampla ou DML direto. Pode ser preparada em migration;
sua publicação operacional e a atribuição ao principal dependem da revisão específica.
Testes sintéticos de privilégios existentes permanecem rollback-only.

Não executar fonte ESL/GraphQL, rede de autenticação externa, jobs, scheduler, restart
de serviço, deploy, cutover, Git commit/push ou produção. V2-041 mantém seu hold para
credenciais/fontes externas. Implementar código de uma operação não autoriza executá-la
fora do laboratório; eventual rodada real exige seu alvo e autorização próprios.

## 4. Implementação integrada obrigatória

### A. Motor físico completo

Execute o pacote P02Q A+B+C+D+E do runbook referenciado. Provar ambas as verticais,
recibos exatos, perda de ack após commit, nova JVM, continuação elegível, rollback,
concorrência real das procedures, lease, cancelamento e ausência de efeito duplicado.
Reutilizar o mesmo motor/adapters nas frentes seguintes. Não encerrar o bloco ao
concluir A se existir trabalho independente de B–F ainda autorizado.

### B. Adapter de identidade concreto e contrato de configuração

Implementar a resolução Windows/SQL descrita na seção 2, parser estrito, preflight
de authority/principal e result set limitado. Tratar resposta ausente/duplicada,
identidade SQL/password, mudança de contexto, privilégio excessivo, policy inválida,
timeout e revogação como recusas tipadas com causa interna preservada.

A conexão usada exclusivamente para autenticação/auditoria pertence ao control plane
de segurança e tem permissões limitadas. Os clientes de fonte e os handlers de negócio
só são compostos após autorização. Não criar dependência circular exigindo autorização
de workload para registrar sua própria negação, nem resolver credenciais ESL antes dela.

Fechar o schema de configuração e produzir o pacote de provisionamento para principals
SERVICE e OPERATOR. Escolhas técnicas já foram delegadas; só escalar fatos/autorizações
que a máquina não pode fornecer. V2-042b exige todas as entradas reais, não apenas
o ADR de escolha do mecanismo.

### C. Auditoria durável e capacidade de uso único

Implementar sink JDBC/SQL append-only para decisões ALLOW/DENY, receipt de gravação,
idempotência por invocação, versão de mapping/policy, referência opaca e razões
sanitizadas. Falha ou ack incerto da auditoria não autoriza a operação. Definir a
recusa temporal sem inventar timestamp quando o relógio confiável estiver indisponível.

Vincular a capacidade a ação, invocação, ambiente/source/tenant/workload, modo/janela,
plano/fingerprints e validade. Comparar o escopo efetivo antes da execução; nenhuma
capacidade de status autoriza run/replay/force-run ou outro namespace.

Consumo único deve resistir a duas threads, duas JVMs, restart e resposta perdida.
Persistir o consumo com unicidade/fence e vincular à ocorrência durável. Não confundir
consumo at-most-once com execução exactly-once: definir recuperação da falha entre
consumo e despacho, sem reutilização indevida nem ocorrência definitivamente órfã.
Preservar o protocolo de idempotência do motor; não criar duas máquinas de estados.

Cobrir vencimento entre autorização e uso, política revogada/trocada, dupla utilização,
capacidade adulterada, erro do sink e replay de recibo. Nem booleano, JSON de configuração
ou factory pública pode fabricar ALLOW. O mapeamento administrativo é distinto das
capacidades emitidas para execução.

### D. CLI oficial e dispatcher com consumidor real

Ligar o boundary ao RuntimeCompositionRoot/Main oficial, aos handlers e ao dispatcher.
Validar configuração sem I/O de negócio, autenticar, auditar, consumir a capacidade e
só então despachar o workload aprovado. `run`, `replay`, `status` e `force-run` devem
ter comportamento concreto; force-run não contorna lease, DQ, contrato ou janela.

Manter a separação de migrate/cutover e o modelo one-shot, sem daemon/scheduler interno.
Sweep/materialize/reconcile respeitam os gates dos consumidores reais: não criar
sucesso vazio, sweep habilitado ou fato fictício para completar a superfície CLI.
Capabilities de autorização são distintas dos permits de contrato/DQ/promoção.

Testar o JAR oficial diretamente, sem depender do wrapper para proteger a operação.
Casos positivos de uma authority/principal realmente aprovados e negativos de
role ausente, configuração adulterada, revogação e auditoria devem ser executáveis.
Fixture no classpath ou verifier de teste não equivale a prova pelo JAR oficial.

Use fontes sintéticas no harness permitido, sem embutir um bypass de autenticação
no artefato. Um comando status positivo não prova run completo. Se a prova de um
handler exigir fonte externa ainda bloqueada, entregar implementação e testes
locais completos, registrar a lacuna e manter seu aceite operacional aberto.

### E. Plano temporal e demais aceites do runtime

Implementar a política explícita por workload: timezone IANA, modo/estratégia,
cadência, lookback/estabilização, SLA/deadline, concorrência, blackout, fechamento
mensal e catch-up. Persistir versão/fingerprint e não aceitar defaults extraídos
silenciosamente do relógio ou dos horários históricos do legado.

O planejador deve calcular e persistir janelas limitadas, sem execução recorrente
autônoma. Provar mês civil anterior, dezembro/janeiro, fevereiro bissexto, gap/overlap
de fuso, limite exclusivo, blackout, execução perdida, limite de backlog e replay.
Não carregar todo o histórico na JVM para calcular lacunas; usar resumos SQL bounded.

Reinício do plano e conclusão fora de ordem não duplicam nem saltam partições ou
watermark. Lease vencida, tentativa parcial e ciclo degradado exigem protocolo
explícito; preservar publicação independente e bloqueio Coletas→Fretes. Testar shutdown,
cancelamento e taxonomia dos exit codes. Valores operacionais reais da matriz são
configuração ratificada; exemplos sintéticos exercitam a lógica sem ativar agenda.

Antes de fechar V2-022 pai, conferir **todos** os aceites originais no STATES.md,
inclusive comandos, catch-up, fechamento mensal, limites de reconciliação/degradação
e configuração operacional. Código parcial, handler placeholder ou configuração
nominal ausente deixam o pai aberto, mesmo com P02Q e V2-022b aceitos.

### F. Regressão e fechamento do conjunto

Entregar testes comportamentais que atravessem as integrações reais e reproduzam
defeitos encontrados. Executar testes focados durante o trabalho, suíte Java 17
offline completa no fechamento, estilo/arquitetura/cobertura, validators afetados,
scanner offline, UTF-8 estrito sem BOM e diff check. Não reduzir gates ou criar skips
para mascarar falha. Não repetir suíte completa sem mudança/falha que a justifique.

Separar evidência de código local, SQL físico sob owner, principal restrito real,
JAR oficial e fonte externa. Cada PASS precisa de comando/caso/resultado observável.
Registrar baseline/migrations/checksums, alterações confirmadas, reprodução e
recuperação. Preservar mudanças alheias e remover somente temporários próprios seguros.

Sincronizar STATES/trilha/validator. Manter um bloco integrado e as rotas canônicas
necessárias, sem duplicar checkboxes para A–F ou inventar fechamentos de pais.
Nenhum teste sintético ratifica identidade, operação, paridade, escala ou cutover.

## 5. Meta de avanço e limites da projeção

Mantendo a contagem atual e criando somente o subitem P02Q:

| Aceites efetivamente alcançados | Contagem projetada | Percentual |
| --- | --- | --- |
| Somente P02Q | 60/114 | 52,6% |
| P02Q + V2-042b + V2-042c + V2-042 + V2-022b | 64/114 | 56,1% |
| Todos acima + todos os aceites de V2-022 pai | 65/114 | 57,0% |

O alvo ampliado é a cadeia completa; os números são condicionais aos aceites reais,
não promessa de aprovação nem medição de esforço. Não fabricar uma conta restrita,
rodada externa ou política operacional para alcançar a linha desejada. Se a estrutura
do backlog mudar legitimamente, recalcular em vez de conservar a projeção à força.

Caso provisionamento/aprovação seja indispensável, terminar primeiro o código, os
testes independentes e o pacote concreto de aplicação. Fazer uma solicitação final
consolidada com alvo, impacto e recuperação, identificando o requisito aplicável.
Se o usuário já tiver autorizado exatamente esse pacote, executar sem perguntar novamente.

O handoff final informa: frentes implementadas, provas executadas, diffs/arquivos,
itens existentes efetivamente fechados, percentual calculado e impedimentos específicos.
Continuar até terminar todas as frentes autorizadas; não devolver apenas interfaces,
um novo documento de arquitetura ou outro prompt como substituto da implementação.
