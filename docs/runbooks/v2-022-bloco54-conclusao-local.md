# Bloco 54 — conclusão do laboratório A–G

Em 08/09/2026, **LOCAL_A_G_COMPLETE_NOMINAL_GATES_PENDING**. A construção e a
qualificação local solicitadas foram concluídas. O owner autorizou as rodadas
necessárias; dois lotes declarados de 96 unidades ampliaram o teto de 128 para
320, sem devolver nenhuma reserva. As 302 reservas e 30 campanhas estão encerradas;
restam 18 unidades declaradas. Não há campanha, agenda ou execução recorrente ativa.

Esta é a fotografia atual. Os relatórios da sexta e da sétima campanhas, seus
manifestos preparados e suas falhas permanecem históricos. O manifest atual é
[runtime-bloco54.json](../../database/manifest/runtime-bloco54.json); o anterior
foi preservado byte a byte como `runtime-bloco54-historical-sixth.json`.

## Entrega e prova por frente

| Frente | Entrega comprovada | Evidência e limite |
| --- | --- | --- |
| A | Windows/SQL integrado; SERVICE executor/observer, OPERATOR observer; UUID pseudônimo, decisão/consumo duráveis, revogação e compensação com versões crescentes | 25 grants, dois mappings, oito scopes originais e dois REPLAY revogados; vigência original; governança nominal produtiva permanece própria |
| B | JAR verifica área protegida, ACL, manifesto e dependências antes de SQL; TLS validado na autorização e no workload; launcher e diagnóstico | v5 negativos de configuração/pin/policy/env/CLI/-D; retestes de ZIP válido adulterado e DENY de leitura; v7 manual positivo |
| C | Consumo único, vínculo de 22 campos, recibos, versões, expiração, duas JVMs concorrentes, guard de consumidores SQL e falha do sink | 39 casos Windows/SQL em test-classpath, acompanhados das travessias oficiais; bloqueio SQL próprio antes do commit recusou sem decisão/consumo/tentativa |
| D | Coletas/Fretes pelo Main, HTTP loopback e JDBC reais; staging, DQ, publicação/status, replay/force e recuperação | 136 invocações protegidas de JAR no total, incluindo falhas; 127 HTTP; cancelamento, lease, queda própria, DQ e alerta obrigatório comprovados |
| E | Preview sem efeitos, exportação por janela, persistência autenticada e execução temporal; retomada e fronteira contígua | Bissexto, fora de ordem, virada de ano, 23/25 h, gap/overlap, mês anterior de 696 h, blackout, backlog, reconciliação, degradação, stale e incremental com lookback |
| F | Java 17 offline, builds isolados, migrations versionadas, oráculos SQL independentes, ledger e pós-verificação | 1098 testes, zero falhas/erros, quatro skips anteriores; estilo, arquitetura e cobertura aprovados; B53 preservado |
| G | Pacote de comparação futura e exportação somente leitura, seis cenários com oráculo sintético independente | Igualdade, dinheiro/status, duplicidade, ausência, fronteira e incompletude; execução real recusa oito inputs ausentes |

## Correções finais e schema

O STATUS temporal deixou de tentar reconciliar planos com o grant do operador.
O workload recusa alvo/TLS inválidos antes da autorização. O próprio JAR verifica
a instalação administrada; a segurança não depende apenas do launcher.
`--control-stdin` permite cancelar a invocação autorizada com `cancel` e newline.
O evento obrigatório de contrato recusado usa o sink SQL com escopo de V019.

O reconciliador agora seleciona as ocorrências declaradas do plano, dentro da
página SQL limitada, sem confundir intervalos de planos diferentes com lacunas
do próprio plano. O adapter JDBC verifica ordenação da página; a continuidade
pertence ao coordenador. Fretes exporta a dependência Coletas da janela exata.

V021 vincula a autorização de retomada ao intento temporal durável, incluindo
policy, janela, ciclo, idempotência, estratégia e hash. O guard operacional de
V020 continua intacto. Foram qualificados upgrade e **somente a cauda pendente do
baseline**, ambos em rollback; depois V021 foi aplicada e verificada em nova
conexão. Não houve reinstalação histórica. São zero grants novos nesta correção.
Aplicação, verificação e recuperação constam no
[pacote V021](../../database/proposals/bloco54-temporal-continuation/README.md) e no
[ADR 0037](../adr/0037-retomada-do-plano-temporal-e-fronteira-incremental-local.md).

No INCREMENTAL, o filtro de atualização inclui o lookback de uma hora, preservado
em todas as páginas; a fronteira de publicação continua separada da extração.
O primeiro start inicializa a fronteira declarada pelo consumidor SQL existente;
reinícios não a redefinem. Duas publicações e sua repetição sem HTTP comprovaram
isso. As duas políticas DQ adicionais têm quatro checks estritos por entidade,
clonados da política sintética existente; dez linhas novas permaneceram. A palavra
RATIFIED dessas fixtures designa somente o laboratório, sem ratificação de negócio.

## Evidências reproduzíveis e falhas preservadas

Base privada: `target/bloco54/completion-authorized-20260908/`. Cada rodada contém
requests congelados, hashes, scripts efetivamente testados, resultados e observações
SQL independentes. O manifest fixa os hashes dos resultados. IDs de negócio,
payloads, SIDs e credenciais não são publicados nos documentos.

| Grupo | Evidência aceita |
| --- | --- |
| `residual-runtime` (fora da base acima) | REPLAY Coletas/Fretes, origem/fronteira intactas, repetição, FORCE completo/parcial, OPERATOR negado, alerta SQL; compensação v17 e scopes revogados |
| `V5_AUTH_01`, `V5_ARTIFACT_02` | Negativos de configuração e integridade; restauração exata dos bytes e ACL |
| `V5_RUNTIME_01`, `V5_RUNTIME_03` | Queda antes do despacho/depois da publicação, nova invocação, cancelamento, lease expirado e recusa de takeover |
| `V6_FRETES_01`, `V6_CALENDAR_01`, `V7_RECOVERY_01` | DAG e ordem 2/1/3, calendário, retomada da persistência e do mês sem extração, policy alterada recusada |
| `V7_LIMITS_01`, `V7_INCREMENTAL_01` | Limites temporais, stale retido, filtro HTTP real em quatro páginas e avanço SQL incremental |
| `V7_SINKS_06` | Controle DQ válido publicou; policy desconhecida extraiu e falhou; sink de alerta bloqueado falhou, retomada fez zero HTTP |
| `V7_AUTH_SINK_01` | Timeout de range lock próprio antes do commit da decisão: exit 20, zero HTTP/decisões/consumos/tentativas/publicações |
| `V7_MANUAL_01` | Diagnóstico e preview, RUN/0 com publicação, STATUS/0 nas duas contas; contagens e perfil final |
| `V021_SCHEMA_01` | Upgrade/cauda em rollback, aplicação e leitura independente; nenhum grant |

As falhas anteriores continuam registradas. A fixture v1 usou timestamp onde o
mapper exige data civil e deixou frescor desconhecido; o kernel recusou publicar
conteúdo divergente. Duas tentativas PROMOTED antigas e leases expirados permanecem.
A fixture v2 é separada. Publicação com exit 30 não foi convertida em PASS integral:
as falhas de reconciliação/persistência foram corrigidas e retestadas na v7.

A primeira adulteração de ZIP provocou recusa da JVM, não do verifier; o primeiro
ensaio ACL não removeu leitura efetiva. Ambos foram substituídos por retestes
corretos. `V7_SINKS_01/03/05` colidiram com lease antigo da partição de 01/01/2024;
não são provas de DQ/sink, **inclusive os dois flags PASS equivocados de 03**.
O manifest os exclui expressamente. 02 recusou no preflight; 03/04 também revelaram
compartilhamento/buffering do log do controlador, corrigidos antes de 06.

Descartar ack no cliente após commit é instrumentação, não outage de transporte.
Os ensaios de sink usaram range lock em conexão própria com timeout/rollback e
observação SQL de bloqueio. Quedas encerraram somente JVMs próprias. O SQL Server
não foi reiniciado. Nenhuma dessas técnicas é apresentada como outra camada.

## Inventário final e preservação

Alvo único: `localhost/ETL_SISTEMA_V2_SHADOW`; V001–V021 aplicadas e imutáveis.
Catálogo: `7a86e28ea68cecd8ba993bbea8df5c677e0848f27aa7b0412402165071736408`.
6.723 linhas totais. B54: 45 tentativas, 30 publicações, 70 páginas e 74 entradas
auditadas. B53: 95 tentativas, 75 publicações, oito EXTRACTING e ledger de 112
reservas com SHA `dc45f84046fa1c2a6184dc181cc9bf1b39878c5c94ed4574e22d761f0cfc3df1`.
Pós-verificação: zero sessões restritas e zero transações próprias pendentes.

SERVICE mapping v17; OPERATOR v1; scope original 1 v10 e demais sete v1. Os dois
scopes adicionais REPLAY estão revogados; REPLAY/FORCE temporários já foram
retirados. A validade original é `2026-10-07T22:34:30.615Z`, sem renovação. O
administrador local autorizado administra instalação/mappings, não recebe por
isso autoridade sobre a fonte, indicadores, compliance ou produção. A identidade
é Windows integrada, authority/destinatário são o alvo SQL e os módulos/pins
administrados; UUID é pseudônimo de auditoria. Não se inventou issuer OIDC/tenant.

Consumo e consumidores SQL revalidam validade/versões; heartbeat não renova a
autorização. Não se promete invalidar instantaneamente uma sessão Windows aberta.
Conta expirada/desabilitada impede novo filho; mapping/capability vencidos recusam
consumo; manifesto vencido ou pin/certificado inválido recusam conexão/artefato.
Não houve ensaio alterando relógio, vencimento de conta ou certificado real.
Negativos de pin e capability foram físicos; demais fronteiras temporais usam
testes de contrato e diagnóstico de vigência. A manutenção exige inventário do
item vencido, novo pacote com hashes/validade explícita, verificação em sessão nova
e recuperação preservando versões/auditoria; não se renova silenciosamente.

O JAR final é `target/bloco54/reviewed-bundle-v7`, instalado em
`C:\ProgramData\EslEtlV2\app-bloco54\838d2316fa53f412`. Manifest SHA:
`838d2316fa53f412131d25a3c0c9cb397805fb05cecc3693b68daa37507e95fa`.
Revisões anteriores, DPAPI e dados sintéticos permanecem. ACL limita escrita a
Administrators/SYSTEM e permite somente leitura/execução às contas restritas.

## Operação e verificação

Em PowerShell 7 elevado normalmente, dentro do projeto, diagnóstico atual:

```powershell
& scripts/validation/Invoke-Bloco54Manual.ps1 -Command diagnose `
  -Bundle target/bloco54/reviewed-bundle-v7 `
  -ExpectedManifestSha256 838d2316fa53f412131d25a3c0c9cb397805fb05cecc3693b68daa37507e95fa `
  -ObservabilityProfile -RetainedReplayScopes -TemporalSchemaProfile
```

O diagnóstico é somente leitura. O fluxo manual exercitado está em
`V7_MANUAL_01/tested-Invoke-Bloco54ManualCompletion.ps1`. RUN/STATUS usam request e
recibo de configuração congelados por hash, CaseId e invocationId novos, conta
restrita e reserva antes dos efeitos. Ocorrência conhecida conserva executionId
e todos os campos semânticos. O endpoint loopback da configuração é fixo em 62128;
trocar porta muda fingerprint e impede recuperar com configuração divergente.
Não reutilizar IDs de campanha encerrada nem rodar scripts históricos de DDL.

`plan --config ... --temporal ...` é preview sem efeitos; com `--request` exporta
requests e `--window` seleciona uma janela. `run --temporal` persiste o plano;
um request exportado executado por `run` atravessa extração/publicação. Exit 0 de
persistência não prova publicação. Exit 10 é STATUS sem publicação, 20 recusa de
autorização, 30 resultado não confirmado, 40 falha de fonte/contrato/DQ/lease e
50 cancelamento. Repetir uma ocorrência conhecida não reextrai dados.

Verificação offline atual, sem nova campanha:

```powershell
& scripts/validation/Test-Bloco54Integrated.ps1 -IncludePrivateEvidence
& scripts/validation/Test-ProgressiveDataGate.ps1
& scripts/validation/Test-WindowsRuntimePackage.ps1
& scripts/validation/Test-Gpt56ChatTrail.ps1
& scripts/security/Test-OfflineSecretScan.ps1
& scripts/security/Invoke-OfflineSecretScan.ps1
```

O build final isolado é `target/bloco54-build-v7`. Log completo:
`verify-Full-20260908T160406.log` na base privada. Não executar clean canônico.
O validator histórico verifica a fotografia das seis primeiras campanhas; o
atual exige os resultados finais, preserva falhas e recusa falso aceite nominal.

Fechamento dos arquivos: validator integrado com evidência privada, gate
progressivo, pacote Windows histórico e trilha passaram. Seis contraprovas do
validator passaram; o scanner passou nos 11 autotestes e em 1.229 arquivos
candidatos (1.228 textos e um binário permitido), sem achados. UTF-8 estrito sem
BOM, sintaxe PowerShell, hashes das 21 migrations e `git diff --check` passaram.
O listener loopback próprio terminou. Os 1.199 arquivos do snapshot inicial
continuam presentes; o diff desta continuação cobre 67 arquivos e está em
`target/bloco54/completion-authorized-20260908/continuation-review.patch`, separado
das alterações que já existiam antes deste trabalho. Nenhum commit foi criado.

## Limites e próximos inputs concretos

Não resta implementação ou teste físico independente desta matriz de laboratório.
O fechamento A–G não fecha os pais V2-042/V2-022 nem os gates nominais G06/G07/G08:
a política operacional fora do laboratório, seu escopo de identidade e o aceite
de negócio continuam sem ratificação. Nenhuma nova caixa canônica foi marcada:
114 totais, 60 concluídas, 54 pendentes; 196 rotas abertas e zero AGORA.

Inventário dos aceites V2-022: RUN/REPLAY/FORCE_RUN/STATUS e planejamento temporal
foram exercitados localmente; sweep-preview não habilita entidade nem sweep-apply;
reconcile de plano não faz paridade; mês civil não materializa os cinco fatos.
Comandos sem consumidor autorizado continuam recusados. Agendamento, retenção,
release, escala real, consumidores e corte produtivo exigem suas próprias provas.

A [comparação preparada](../catalogos/comparacao-bloco54/contract.json) aguarda:
instância da fonte, recorte da empresa, período aprovado, referência confiável de
identidade, referência financeira, moeda/arredondamento, aprovação dos rótulos de
status e destino das evidências. O pacote já identifica os papéis responsáveis e
os contratos de exportação; não é necessário escolher banco ou contas novamente.
V2-041 mantém o hold de credenciais da fonte. A primeira rodada real precisa desse
pacote preenchido e do seu escopo explícito; nenhuma conexão ao ETL_SISTEMA ou ESL
foi feita neste bloco. Produção, dashboards, novos serviços, agenda, deploy,
commit/push e cutover permanecem fora da entrega.
