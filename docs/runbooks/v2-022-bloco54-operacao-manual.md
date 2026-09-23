# Operação manual do laboratório B54

## Estado atual — 08/09/2026

Bloco 54 concluído no laboratório A–G: V021 aplicada, 25 grants, revisão v7 física,
1098 testes aprovados, 302 reservas e 30 campanhas encerradas. O [relatório atual](v2-022-bloco54-conclusao-local.md)
registra os resultados, os limites nominais, o diagnóstico V021 e a recuperação.
As afirmações de pendência/preparação/revisão final abaixo pertencem às fotografias
históricas indicadas; não são o procedimento atual. Preservadas para rastreabilidade.

## Histórico preservado

Alvo único localhost/ETL_SISTEMA_V2_SHADOW. Contas etl_v2_exec e etl_v2_view
existentes. Nenhum comando cria agenda ou renova vigência. O
[ADR 0036](../adr/0036-laboratorio-runtime-local-consumidores-e-operacao.md)
define identidade/TLS; o [relatório](v2-022-bloco54-integrado.md) distingue as provas.

## Revisões e diagnóstico

A revisão física inicial tem manifest
d382d4d39164cdb62918c21f55966e91a6c348a91a001d9de5828b8f39eeb208.
Foi instalada em app-bloco54/d382d4d39164cdb6. Os avisos de logback sobre escrita
no diretório RX não impediram as duas publicações confirmadas no SQL.

A revisão final, em target/bloco54/reviewed-bundle-v3, tem manifest
5d2632ab3577ea803eb451ef59ea80c537023c88f7f1f0b7bca471ffc0f2b23f.
Acrescenta três recursos administrativos e substitui somente logback.xml pelo
console estruturado. O diff verifica todos os entries e dependências. Não contém
fixtures/harness/verifier fake. Essa revisão exige V019/V020 e três grants novos;
V019/V020 e os três grants foram qualificados/aplicados na quinta campanha
explicitamente autorizada. O JAR final passou em 16 casos restritos; STATUS pelo
launcher e diagnóstico em novo PowerShell passaram. Usar PowerShell 7 com UAC
normal. A campanha encerrou com 99/128 reservas. Essa é a fotografia da quinta campanha; ver a atualização da sexta abaixo. Diagnóstico e preview offline não reabrem campanha.

Invoke-Bloco54Manual.ps1 -Command diagnose exige -Bundle e
-ExpectedManifestSha256. Confere hashes, ACL/owner, contas habilitadas e vigência,
validator 053 e consumers 055, somente por leitura. O diagnóstico 053 é do perfil
original de 22 grants. Após o delta, usar -ObservabilityProfile para os 25 grants.
Depois do ensaio de replay, verify-retained-replay.sql confere também os dois
scopes SERVICE/REPLAY revogados, sem tolerar outros scopes adicionais.

-Command install copia somente para app-bloco54. Cópia interrompida pode preencher
arquivos ausentes se os existentes conferirem; divergência/junction/owner/ACL
inesperado ou validade expirada impede prosseguir. App, secrets e revisão anterior
são preservados. Não executar novamente o instalador completo de contas.

Troca de senha, validade, conta, grupo, certificado, serviço ou firewall exige
pacote separado com inventário, aplicação, verificação e compensação. Expiração
não autoriza renovar. O owner não precisa escolher novamente contas ou banco.

## Uma invocação

O launcher exige request JSON até 16 KiB, seu SHA-256, um CaseId novo e campanha
ativa no ledger. -StartCampaign abre campanha apenas se houver saldo e campanha
disponíveis; não reabre uma encerrada. Para uma sequência, o controlador abre
uma campanha e chama o launcher sem essa opção.

Parâmetros operacionais: -Command run/status/replay/force-run, -Account,
-Bundle, -ExpectedManifestSha256, -Request, -ExpectedRequestSha256 e -CaseId.
O agente calcula hashes a partir dos arquivos reais. RUN/REPLAY/FORCE_RUN usam
SERVICE; STATUS pode usar OPERATOR. A autorização final continua no JAR/SQL.

Uma nova autorização usa invocationId novo. Para retomar, manter executionId,
cycleId, idempotency e demais campos da ocorrência. Usar também
-ConfigurationReceipt e -ExpectedReceiptSha256, apontando ao recibo
manual-CASE-configuration.json da primeira invocação. Ele congela manifest,
configuração e porta: trocar a porta muda o fingerprint. Porta ocupada recusa o
teste; o launcher não usa um servidor de outro processo.

O launcher reserva antes do filho/socket, copia configuração/request para área
protegida, cria fonte somente 127.0.0.1 e passa token sintético só na memória do
filho. Limite: 60s, 16KiB de stdout/stderr, sem retry automático. Encerramento
atinge apenas o filho e servidor próprios. Não há PID file, daemon ou serviço.

| Exit | Interpretação |
| --- | --- |
| 0 | Ler reason: publicação confirmada; temporal confirma somente persistência |
| 10 | STATUS autorizado sem publicação confirmada; NOT_FOUND é explícito |
| 20 | Autorização/configuração de identidade recusada |
| 30 | Resultado técnico não confirmado; readback antes de nova tentativa |
| 40 | Fonte, contrato, qualidade ou dependência recusados |
| 124 | Filho excedeu limite; controlador o encerrou, resultado incerto |

Reason e correlação são mais específicos que exit. Após perda de ack, consultar
a ocorrência em outra sessão. HTTP 200 não prova publicação.

## Temporal e recuperação

plan --config ... --temporal config/laboratory/bloco54-temporal-coletas.json
é offline, effects=0. run --temporal consome RUN por janela, persiste até quatro
janelas e usa o coordenador para resumos de pendências/degradação/fronteira.
A persistência/restart foi comprovada em três janelas por workload, sem extração ou scheduler. A revisão v4 implementa a ligação executora com prova física ainda pendente; exige requests operacionais e policy
DQ próprios. Em Fretes, dependencyRequest fixa a ocorrência Coletas predecessora.

Invoke-Bloco54AuthorityMatrix.ps1 aceita -UseActiveCampaign para compartilhar
uma campanha já ativa, sem abrir outra. -ObservabilityProfile
seleciona o postflight de 25 grants; não os aplica.

OPS02 deixa SERVICE revogado e grava previamente o contrato de recuperação.
Em outra instância PowerShell, Restore-Bloco54HarnessMapping.ps1 exige o hash
desse contrato, o hash da compensação, vigência original e igualdade integral
do estado esperado. Restaura apenas revoked e incrementa mapping_version.
Conflito ou prazo vencido mantém a recusa. OPS02 foi executado na sexta campanha e recuperado sob a reserva 109 após corrigir a conversão UTC; a falha inicial permanece registrada.

REPLAY/FORCE_RUN temporários não chegaram a ser concedidos. O ensaio continua pendente: snapshot
dos dois mappings/oito scopes, habilitação SERVICE por até 15 minutos, no máximo
dois scopes REPLAY locais, provas e compensação com versões crescentes. Scopes
novos ficam revogados. OPERATOR não ganha escrita.

## Preservação

B53 mantém 112 reservas, 95 tentativas, 75 publicações e oito EXTRACTING.
B54 usa ledger separado, sem devolução de reservas. Não rodar clean no target
canônico, reset, stale recovery global ou limpeza de auditoria/dados.
Logs privados ficam em target/bloco54; documentos versionados têm somente
contagens, resultados e hashes de artefatos técnicos.

## Revisão v4 e checkpoint da sexta campanha

Bundle: target/bloco54/reviewed-bundle-v4; manifest
f2fe15f0ddaa3f8f92c07aef54e909df917eb8e3055b652b40af375f99e1a3bd.
Instalação/ACL/diagnóstico e exportação offline passaram. RUN dessa revisão não foi
executado: a campanha encerrou no OPS02, posteriormente compensado. Banco com
25 grants, SERVICE v15, scope 1 v10, demais versões v1 e oito scopes originais.
B54: 109/128 reservas, seis campanhas encerradas; sétima ainda não autorizada.

O comando direto `plan --config ... --temporal politica.json --request blueprint.json`
emite um array de requests JSON. Salvar um objeto por arquivo, conferindo seu SHA-256;
executar cada janela pelo launcher normal com `-Command run`, sem `-Temporal`.
O campo temporalPolicy é documento estrito e temporalWindow é o ordinal da janela.
Manter todos os campos ao repetir; alterar somente invocationId. O blueprint de
Fretes exige dependencyRequest Coletas explícito para a mesma janela. O launcher
reserva separadamente persistência e predecessor quando presentes, e ajusta a
fixture à data civil sintética autorizada. Não executa automaticamente o array.

`run --temporal` conserva a modalidade anterior de persistência. Exit 0 dessa
modalidade não significa extração. No request vinculado, conferir publicação SQL
e TEMPORAL_EXECUTION_RECONCILIATION, além do exit. Cinco regressões de parsing UTC
impedem deslocar a validade na recuperação em outra cultura/fuso.

Após eventual REPLAY com scopes retidos, o diagnóstico aceita
`-ObservabilityProfile -RetainedReplayScopes` e usa a validação exata de dez scopes,
sendo dois revogados. Hoje usar somente -ObservabilityProfile. O pacote residual
contém os 19 testes preparados e sua compensação, sem autorizar nova campanha.
