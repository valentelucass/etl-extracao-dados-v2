# ADR 0035 — Authority Windows/SQL, consumo durável e plano temporal

## Estado atual — 08/09/2026

Bloco 54 concluído no laboratório A–G: V021 aplicada, 25 grants, revisão v7 física,
1098 testes aprovados, 302 reservas e 30 campanhas encerradas. O [relatório atual](../runbooks/v2-022-bloco54-conclusao-local.md)
registra os resultados, os limites nominais, o diagnóstico V021 e a recuperação.
As afirmações de pendência/preparação/revisão final abaixo pertencem às fotografias
históricas indicadas; não são o procedimento atual. Preservadas para rastreabilidade.

## Histórico preservado

Data: 07/09/2026. Decisão técnica adotada pelo owner no Bloco 53.
Complementa ADRs 0022, 0032, 0033 e 0034.

## Atualização posterior de provisionamento local

O [procedimento autorizado](../runbooks/v2-042-contas-locais-windows.md) já comprovou
duas contas Windows restritas, dois logins/usuários SQL, 22 grants, oito scopes,
authority, ACLs e TLS da conexão JDBC de autorização. O artefato local administrado
executou STATUS sob ambas as contas (NOT_FOUND/10); OPERATOR RUN foi negado (20).
O JAR portátil continua sem recurso de authority e nega por padrão. A matriz física
completa de autorização/dispatcher e os gates de fonte/operação continuam pendentes.

As seções seguintes registram a decisão e as provas da primeira etapa de B53,
anterior a esse provisionamento. Suas referências a contas/TLS/grants ausentes são
históricas. A [proposta B54](../runbooks/prompt-bloco-54-runtime-operacional-local-astra.md)
parte da instalação comprovada e ainda não executa nem aceita os gates restantes.

## Identidade e confiança

Windows autentica a conexão JDBC integrada. O módulo SQL valida auth_scheme,
ORIGINAL_LOGIN/SUSER_SID, contexto, login Windows explícito habilitado, excesso de
privilégio e cadastro administrado. Nenhum nome, SID, role ou booleano recebido pela
CLI autentica. O adapter não fabrica issuer/audience OIDC: seus equivalentes são a
instância TLS, o banco, o identificador da authority, o módulo SQL administrado e o
escopo de execução. JDBC/DLL Microsoft permanecem em 12.8.1.jre11/12.8.1.x64.

A configuração de confiança é o recurso `runtime-authority.properties` incorporado
ao artefato pela administração: quatro propriedades fechadas, sem duplicatas, até
4 KiB. CLI, properties da JVM e ambiente não substituem esse recurso. O hostname
deve corresponder ao certificado TLS e a SERVERPROPERTY('ServerName'); o JDBC exige
encrypt=true/trustServerCertificate=false. O fingerprint deve coincidir com a matriz
RBAC compilada. A instalação local não ratifica certificado, distribuição ou ACLs.
O artefato entregue nesta rodada não contém esse recurso e permanece deny-all.

Mapping protegido: SID original apenas no banco, audit_reference UUID aleatório,
classe SERVICE/OPERATOR, papéis, versão, vigência e revogação. Runtime recebe somente
referência opaca e resumos. Não há SELECT/DML de cadastro nem impersonação no runtime.
Administradores são recusados como identidade operacional. A comparação de contexto
não usa EXECUTE AS OWNER. O adapter Windows é substituível; o núcleo continua Java 17
sem dependência de bibliotecas nativas de identidade.

## Decisão, consumo e falha entre consumo e despacho

V016 registra ALLOW/DENY e recibo append-only por invocation_id. Uma decisão ALLOW
inclui mapping/scope/policy e tempo SQL confiável. JSON tem exatamente 22 strings,
limites por campo, hashes e correspondência dos identificadores. O hash usa UTF-16LE,
como NVARCHAR/HASHBYTES. Ausência, duplicidade, truncamento, ack incompleto ou relógio
SQL indisponível negam; Java não inventa timestamp de auditoria.

Capacidade sem factory/constructor público, vinculada à ação, invocação, execução,
namespace, modo/janela, replay, ciclo/plano, contrato/configuração e validade de até
60 segundos. O pacote de provisionamento propõe 30 segundos. Consumo revalida cadastro,
versões, revogação, escopo e prazo sob locks SQL; unicidade por invocação e fence
IDENTITY persistem entre threads/processos/restarts. Heartbeat não renova autorização.
Um login Windows aberto não prova revogação imediata da conta no sistema operacional:
o módulo exige login SQL visível habilitado e mapping válido, sem prometer consulta AD.

Consumo at-most-once não é execução exactly-once. Perda do ack de consumo impede
despacho nessa invocação; não há retry automático. Para retomar, uma NOVA invocação é
autorizada sobre a MESMA ocorrência e material original. Seu plan hash não inclui o
novo invocation_id. O dispatcher lê o estado persistido: NOT_FOUND permite somente
o início idempotente original; publicação existente confirma recibo; continuação
exige o protocolo V015; parcial/terminal/lease perdida recusam. Não nasce outra máquina
de estados, nova ocorrência oculta ou permit de promoção serializado.

## Composição e privilégios

Main aceita `run|replay|force-run|status --config arquivo --request json`. Primeiro
valida configuração sem I/O de negócio, autentica/audita, consome e só então compõe
DataSource, guards, handlers e dispatcher reais. A requisição tem 19 strings fechadas,
até 16 KiB, limites explícitos e um workload por capacidade. O caminho de fonte usa
o contrato empacotado e o guard existente; testes locais fornecem a fonte sintética.
Fonte/credencial externa não foi executada. Force-run conserva DQ, lease e contrato.
Sweep/materialize/reconcile não ganham sucesso vazio nem ativação operacional.

Status usa `ctl.usp_runtime_status`, fechado em READ, para não conceder SEAL/RESUME
ao observador. O início de despacho não varre leases de outros namespaces. A API
legada de persistência integral continua distinta; ela não é o caminho da CLI nova.
Migrate/cutover continuam separados. Conexões de segurança e de workload são separadas,
com login/query/socket limitados e confirmação do alvo em cada conexão de workload.

O gerador de provisionamento prepara dois usuários para logins Windows já existentes,
cadastro e grants de procedures exatas: SERVICE executor/observer, OPERATOR observer.
Não aplica SQL, não cria login, não altera conta/ACL, não concede role ampla. Replay e
force-run não são concedidos por esse pacote inicial. A operação futura ainda precisa
de nova sessão real sob cada conta e dos testes positivos/negativos pelo JAR.
Grants de entrypoints legados autorizam a execução desses módulos no banco; não são
um isolamento multitenant contra um principal que execute SQL arbitrário fora do
artefato confiável. Essa premissa, a distribuição/ACL do artefato e permissões herdadas
devem integrar a revisão operacional, sem serem comprovadas pelo teste como owner.

## Tempo e recuperação do plano

Política explícita por workload: IANA, modo/estratégia, dia/mês civil, lookback,
estabilização, SLA/deadline, concorrência, blackout e limites de backlog/reconciliação/
degradação. Gap/overlap de fuso recusam fronteira ambígua; não deslocam silenciosamente
a janela. Intervalos têm fim exclusivo, inclusive fevereiro bissexto e dezembro/janeiro.

V017 persiste material/version/hash e até 64 janelas por chamada. UUID determinístico
por namespace/partição, unicidade e confronto do plano tornam repetição idempotente.
SQL retorna TOP limitado de resumos. O coordenador só avança o resumo contíguo até a
primeira lacuna: conclusão fora de ordem não a salta. NOT_STARTED retém a ocorrência
original; tentativa ativa exige recuperação; terminal exige replay explícito. Excesso
de degradação recusa. Nenhum timer, daemon, scheduler ou matriz nominal foi ativado.
O CLI `plan` preexistente continua offline; a API temporal tem consumidor JDBC testado,
e sua ativação por política de negócio permanece dependente da matriz ratificada.

## Evidência e evolução

V001–V015 foram qualificadas em baseline/individual e instaladas após comprovar a
topologia histórica vazia. V016/V017 foram comparadas em transações aditivas revertidas
e instaladas sem reset, dados removidos, mapping ou grant operacional. As migrations
aplicadas são imutáveis; correção posterior exige nova versão. Antes do commit,
rollback integral; depois, evolução compensatória preservando auditoria e dados.

O laboratório usa fonte sintética, SQL físico e processos próprios limitados. Perda
de ack é instrumentação cliente após commit real, não queda de transporte. Os testes
positivos de autorização em JDBC simulado não equivalem a uma conta restrita aprovada.
O [relatório integrado](../runbooks/v2-022-bloco53-integrado.md) discrimina as provas.

## Evolução local B54 — checkpoint parcial

O [ADR 0036](0036-laboratorio-runtime-local-consumidores-e-operacao.md) registra a
adoção do laboratório B54, o uso das contas reais, TLS compartilhado, V018 aplicada,
JAR inicial com duas publicações e consumidor temporal one-shot. V019/V020 ficam
pendentes; V020 corrige o vínculo entre escopo declarado e ocorrência SQL existente
mais o limite inicial da fronteira e o período da janela temporal. O UAC da retomada
de 08/09 funcionou, mas a qualificação recusou o hash previsto antes de commit.
A correção foi conferida por leitura e aguarda qualificação na campanha suplementar
proposta. O owner autorizou os três grants; eles ainda não foram aplicados. A revisão
final instalada na área protegida não herda a prova física da revisão inicial.
Ver [resultado B54](../runbooks/v2-022-bloco54-integrado.md); G06/G07/G08 abertos.
