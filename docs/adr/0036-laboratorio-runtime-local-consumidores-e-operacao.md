# ADR 0036 — laboratório B54, consumidores duráveis e operação manual

## Estado atual — 08/09/2026

Bloco 54 concluído no laboratório A–G: V021 aplicada, 25 grants, revisão v7 física,
1098 testes aprovados, 302 reservas e 30 campanhas encerradas. O [relatório atual](../runbooks/v2-022-bloco54-conclusao-local.md)
registra os resultados, os limites nominais, o diagnóstico V021 e a recuperação.
As afirmações de pendência/preparação/revisão final abaixo pertencem às fotografias
históricas indicadas; não são o procedimento atual. Preservadas para rastreabilidade.

## Histórico preservado

Data: 07–08/09/2026. Decisão local adotada pela mensagem do owner que executa o
[prompt B54](../runbooks/prompt-bloco-54-runtime-operacional-local-astra.md).
Complementa o ADR 0035; nenhum principal, tenant ou ambiente produtivo é aprovado.

## Identidade, administração e confiança

O mecanismo efetivo é autenticação integrada Windows com comparação do caller
original/effective no SQL. A authority é o UUID administrado no recurso protegido
e em ctl.runtime_authority_configuration. Seu destinatário é a combinação exata
de instância local, database, authority e policy compilada; não há issuer/audience
OIDC, Entra ou AD inventado. O SID real fica somente no catálogo administrativo.
O sink grava audit_reference UUID por mapping, decisão imutável e consumo único
por invocation_id; logs usam correlação opaca de execution_id técnico.

| Principal local | Direitos atuais | Escopo atual |
| --- | --- | --- |
| etl_v2_exec / SERVICE | executor e observer; 22 EXECUTEs | LOCAL_SHADOW / LOCAL_V2 / LOCAL_V2; Coletas/Fretes; BACKFILL/INCREMENTAL |
| etl_v2_view / OPERATOR | observer; três EXECUTEs | Mesmos quatro scopes; STATUS apenas |

SERVICE está na versão 4 após compensação exata de uma revogação de teste; OPERATOR permanece v1 e os oito scopes originais permanecem v1. Vigência SQL original:
2026-09-07T22:33:30.615Z a 2026-10-07T22:34:30.615Z.
O certificado público existente vence em 2027-08-26T16:58:49Z. As contas Windows
também têm expiração própria, verificada pelo launcher; não se infere validade
Windows a partir da validade SQL.

Somente Administrators/SYSTEM administram app-bloco54. O contexto administrativo
usa os DPAPIs existentes em SecureString para iniciar filhos Windows restritos;
não executa o pipeline como administrador. Não administra ESL, banco produtivo,
tenant, owner consumidor, agenda ou autorização de novos grants por essa função.

PinnedSqlTrustManager exige exatamente o leaf público incorporado no artefato,
válido no relógio do sistema; não usa fallback permissivo nem truststore global.
AdministeredSqlConnection é compartilhado pela authority e pelo DataSource de
workload. A identidade do servidor é vinculada ao pin e ao preflight SQL exato.
Não se alega OCSP, CRL online ou atestação remota do código. A expiração do pin
recusa a conexão e exige pacote revisado; não troca certificado do servidor.

## Consumidores e limites da revogação

V018 passou na comparação das evoluções pendentes de baseline/upgrade, foi aplicada
e permanece imutável. Os 19 consumidores de workload já concedidos exigem decisão
ALLOW efetivamente consumida pelo principal, ainda vigente, com mapping, scope,
papéis, versões e policy atuais. Cada procedure vincula os campos do seu efeito
ao material congelado, conforme seu contrato. Não há SESSION_CONTEXT autoafirmável.

Isso fecha o uso desses consumidores sem consumo correspondente; não transforma
SQL em atestação de que uma chamada partiu do JAR. Outra sessão do mesmo principal
pode usar o mesmo escopo consumido enquanto válido. Os dois casos SQL direto sob conta
restrita passaram na quinta campanha; a lista de guards por procedure está no validator 055.
O administrador do banco continua dono do catálogo e das validações sintéticas,
mas a authority do runtime o recusa. Não se apresenta esse administrador como
prova de menor privilégio.

A capability dura 30 segundos. CONSUME e o início de cada procedure revalidam SQL;
heartbeat não renova autorização. Uma procedure já admitida pode concluir sua
transação após uma revogação concorrente. Query/socket têm limites de 10–20s e o
filho tem teto de 60s; não há promessa de interrupção instantânea do SO. Uma sessão
Windows aberta não equivale a bloqueio imediato quando a conta expira.

As mutações autorizadas de laboratório exigem snapshot/hash, diff e compensação
preparados antes. A restauração recupera direitos funcionais com versões maiores,
preserva o vencimento original e mantém eventuais scopes REPLAY novos revogados.
Conflito ou commit incerto exige readback; nunca retry cego ou limpeza de auditoria.

## Artefato, fonte e contratos

O contrato bloco54-synthetic-v1 é separado dos catálogos nominais. Só um recurso
administrativo no JAR o ativa, limitado ao loopback literal 127.0.0.1, às duas
verticais e à vigência original. Metadata e resposta incluem os campos de frescor
dos mappers reais. Fonte, fixtures e instrumentação de protocolo ficam fora do JAR.

A revisão física inicial publicou duas ocorrências e confirmou status/repetição
e recusas de parcialidade/drift. Revisões posteriores adicionam dependência
explícita Coletas→Fretes, resumo SQL e reconciliação temporal; o resultado físico
da revisão inicial não é atribuído a esses bytes posteriores.

A dependência é fornecida no campo dependencyRequest de Fretes. Ela congela a
ocorrência Coletas e consulta STATUS com consumo próprio antes de compor Fretes.
O JSON temporal fixa a DAG laboratorial, mas persistir um plano não despacha suas
janelas: RUN temporal persiste cada janela com recibo próprio e reconcilia resumos;
a execução posterior exige request operacional e autorização próprios.

V019 acrescenta dois entrypoints por ocorrência para resumo e alerta. Foi
qualificada em rollback e instalada; a migration não concede permissões.
O pacote adicional aplicou os três EXECUTEs autorizados: resumo para SERVICE/OPERATOR
e alerta para SERVICE. A revisão nova recusa o perfil laboratorial antes do HTTP
se esse consumidor obrigatório estiver ausente. Não se preenchem métricas de
watermark, lag ou bytes de rede com zeros. Bytes auditados historicamente como
zero são identificados como auditados; a medida de rede permanece UNKNOWN.

## Aceite e recuperação

O launcher manual valida pacote, arquivos, vigência e ACL, reserva o orçamento,
inicia uma sessão restrita e devolve a saída/exit code. Não cria serviço, PID file,
daemon ou agenda. A instalação parcial só copia arquivos ausentes de uma revisão
cujo conteúdo existente já confere; divergência é recusada.

Os resultados e impedimentos atuais estão no [relatório B54](../runbooks/v2-022-bloco54-integrado.md).
G06/G07/G08 e V2-022 pai permanecem abertos enquanto faltarem suas provas.
Nenhum resultado sintético fecha fonte, paridade, fatos mensais ou cutover.

V020, qualificada e aplicada na quinta campanha autorizada, estende o fence ao material da ocorrência SQL
existente e ao início da fronteira INCREMENTAL. A revisão de V018 identificou essas
lacunas; o pacote de observabilidade exige V020 antes de qualquer grant adicional.

Na quinta campanha autorizada em 08/09, V019/V020 passaram em baseline/upgrade
rollback e nos cinco hashes reais de módulos antes da instalação. V020 também
vincula datas/modo/estratégia temporal ao consumo. Os 25 grants foram conferidos
em outra conexão. O JAR final passou em 16 casos, mais STATUS manual e diagnóstico;
seis janelas temporais persistidas não equivalem a execução dessas janelas.

A matriz separada confirmou 31 casos antes da falha do gravador de saída SQL vazia,
após commit de uma revogação. SERVICE permaneceu revogado; readback exato e
compensação do mesmo caso restauraram revoked=0/v4, sem baixar versões ou renovar.
Oito scopes permanecem v1; nenhum papel replay/force ou scope adicional foi criado.
O gravador foi corrigido e passou em três regressões offline. A matriz interrompida,
o sink de alerta pelo JAR e demais casos físicos continuam sem aceite integral.

No checkpoint histórico da quinta campanha, B54 tinha 99/128 reservas; a sexta foi autorizada depois, conforme atualização abaixo.
A falha anterior de previsão do hash SQL e seus logs de rollback continuam no
relatório histórico; não descrevem mais o schema instalado. O pacote residual
delimita as provas que ainda faltam, sem reinterpretar o aceite dos pais.

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
