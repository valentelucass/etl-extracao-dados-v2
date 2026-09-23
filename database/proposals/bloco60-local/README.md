# B60 — pacote físico preparado, aguardando aprovação

Este pacote ainda **não foi executado**. Nem mesmo o preflight SQL foi chamado.
A seção 4 do pedido adotado exige aprovação única após o fechamento independente.
O hash de `package.json` identifica exatamente scripts, 74 requests, fixtures,
configurações, JAR e controladores que serão submetidos à aprovação. Hash não é
aprovação. Nenhum orçamento de B53–B59 foi transferido ou renovado.

## Alvo e identidades

Único servidor de conexão: `localhost`; único banco de trabalho:
`ETL_SISTEMA_V2_SHADOW`. Servidor/máquina esperados: `RTR-SVW-002`, sem cluster/AG.
`master-target.sql` somente consulta `DB_NAME`, `SERVERPROPERTY`,
`CONNECTIONPROPERTY`, `ORIGINAL_LOGIN` e a linha desse banco em `sys.databases`.
Nenhum outro banco de aplicação será consultado.

Administrador: `RTR-SVW-002\suporte`, token Windows elevado já existente.
Executor: `RTR-SVW-002\etl_v2_exec`; observador: `RTR-SVW-002\etl_v2_view`.
Não criar conta/login/usuário/credencial. Somente depois da aprovação, o executor
lê os dois arquivos DPAPI já provisionados em `C:\ProgramData\EslEtlV2\secrets`.
Não há leitura de `.env` ou credencial do fornecedor. Credenciais ficam em
SecureString; o token da fonte é aleatório, sintético e existe só no processo.

Preflight exige exatamente o catálogo V023 com SHA-256
`ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae`,
9.137 linhas, origem `LOCAL_V2` ativa e tipo histórico `DATA_EXPORT`.
Esses valores são expectativas históricas, ainda não reconfirmadas fisicamente.
`profile-before.sql` confere permissões, versões, scopes e flags completos.
`collision-preflight.sql` recusa UUIDs/ciclos/invocações já usados, chaves `B60_`
e faixa numérica reservada desde 9.000.000 na origem/tenant. Sessões já abertas
dos dois usuários restritos também interrompem o ensaio.

## Permissões e autoridade temporárias

`activate.sql` faz uma única transação explícita com os seguintes deltas:

| Objeto | Esperado antes | Ativação | Compensação |
|---|---|---|---|
| EXECUTE direto do serviço | 32 procedimentos | + stage/apply tipados de Usuários, total 34 | Revoke desses dois, total 32 |
| Mapping SERVICE | versão 17; replay/force=0 | versão 18; replay/force=1 | versão 19; replay/force=0 |
| Scopes | 16 existentes | + quatro `usuarios`: SERVICE/OPERATOR × BACKFILL/REPLAY, versão 1 | Quatro revogados, versão 2; total 20 preservados |
| Policies DQ | Históricas preservadas | Duas novas, quatro checks estritos cada | Somente as duas novas revogadas |
| Protocolo da origem | DATA_EXPORT histórico | binding explícito adicional GRAPHQL | Binding preservado |

Os flags replay/force pertencem ao principal no modelo vigente. Durante a
ativação, `force_run=1` também alcança os scopes BACKFILL ativos das cinco
verticais; seus scopes REPLAY históricos continuam revogados. Esta é uma
consequência explícita do delta proposto, não uma permissão isolada por workload.
O controlador só executa os 74 requests congelados e recusa sessões restritas
preexistentes. Não se presume isolamento contra outro administrador da máquina.

Scopes/contratos/configuração continuam vinculados à autorização consumida.
OPERATOR conserva apenas leitura, inclusive com scope Usuários. A validade
original do mapping não é renovada; a campanha e os artefatos impõem janela
própria de `2026-09-09T00:00:00Z` a `2026-09-16T00:00:00Z` (fim exclusivo).
Authority ID: `fd966e17-71c7-4071-9ccc-bedcaecffcd0`.
Policy de autoridade:
`c76b345af620f21984d3b2d3cec10b0064e53f3b3440984c3f484c834977cc5d`.

`quality-references.json` contém materiais canônicos UTF-16LE, escopos e hashes:

| Modo | Versão DQ | Fingerprint |
|---|---|---|
| BACKFILL | bloco60-usuarios-backfill-v1 | 731a538266821669b3a11eb863ac8479eb331802add7fd81b7d9d7a869bb11c7 |
| REPLAY | bloco60-usuarios-replay-v1 | 625e47d33521140374fcbcdbe4843b4ea7e74709250cfd005a37aee3f1273967 |

As referências Data Export mantêm os contratos sintéticos B55 já ratificados;
fixtures recebem somente namespace/intervalos sintéticos da campanha. Nenhuma
dessas referências qualifica a fonte real ou desempenho produtivo.

## Instalação e equivalência SQL

V024 é aditiva; V001–V023 e a preparação B59 são imutáveis. A baseline preserva
integralmente seu prefixo anterior e acrescenta somente `:r` V024. O método
isolado proposto tem duas transações independentes sobre o prefixo V023 exato:

1. `qualify-upgrade.sql`: corpo V024 literal, validação 056, catálogo, rollback.
2. `qualify-baseline-suffix.sql`: include do sufixo V024, validação 056, catálogo,
   rollback. O controlador compara ambos os catálogos e a preservação após cada
   rollback, antes de instalar V024 com um commit.

Isso prova a equivalência composicional do prefixo já qualificado com o novo
sufixo. **Não é uma nova instalação física a partir de banco vazio** e não será
relatada como tal. Não se executam os exercícios históricos que recriam schema.

`preservation.sql` compara hashes completos por tabela antes/depois das duas
qualificações e da instalação. `preserved-row-hashes.sql` emite somente hashes
SHA-256 de linhas completas, em lotes de 512 hashes; o controlador exige que o
multiconjunto anterior permaneça presente depois da campanha. Exceção fechada:
uma linha SERVICE de `runtime_identity_mapping`, coberta por profiles exatos.
Tabelas novas de vínculo têm verificação própria na validação 056. Payloads,
chaves e valores de linha não saem desse leitor. Colisão ou dado histórico
alterado impede qualificação. Resultado excedente a 1 MiB também interrompe.

## Artefatos e execução

`target/bloco60-local/bundle-v2` contém quatro variantes: administrada válida,
authority trocada, manifesto vencido e configuração inconsistente. As três
últimas só podem recusar. O pacote externo prende seus bytes, inclusive as
inconsistências intencionais. Classes produtivas do JAR oficial são preservadas;
o probe fica em diretório separado de test-classpath. RUN/REPLAY/FORCE_RUN/
STATUS/diagnóstico/cancelamento positivos usam a CLI e composition root reais.
Injeções de falha usam o mesmo runtime/autoridade/JDBC reais, interceptando o
ponto da chamada sem fabricar permit ou substituir persistência.

Após aprovação, instalação temporária somente em
`C:\ProgramData\EslEtlV2\app-bloco60\<16 primeiros caracteres do hash interno>`.
Administrators/SYSTEM têm controle; os dois usuários existentes apenas leitura
e execução. Sem serviço, PATH global, trust store, hosts ou instalação global.
Um JAR padrão separado precisa continuar recusando autoridade ausente.

Fonte única: `http://127.0.0.1:62160`, servidor temporário do próprio controlador.
GraphQL só recebe a query estática `V2UsersSnapshot`, `first=20` e dados
sintéticos. Cinco templates DE permanecem sintéticos. Não existe chamada ESL
ou Raster real neste pacote.

## Limites próprios

| Recurso | Teto reservado/efetivo |
|---|---|
| Janela | 09–16/09/2026 UTC; 60 minutos desde OPEN, sem novo OPEN |
| JVMs | 80 no total; matriz usa 74; duas simultâneas; 60 s e heap 512 MiB cada |
| sqlcmd | 240 total: até 232 ordinários e oito de escrow para leitura/compensação após parada |
| Processos | Duas JVMs + dois sqlcmd + controlador = cinco simultâneos |
| Sessões SQL | Até quatro por JVM + dois sqlcmd = dez simultâneas |
| Por JVM | 256 tentativas de conexão; 512 submissões JDBC; reserva conservadora de 4.096 commits internos |
| Agregado SQL | 20.720 conexões tentadas; 41.200 submissões/arquivos sqlcmd; reserva máxima 327.920 commits internos |
| sqlcmd | 45 s/processo, 30 s/comando, 10 s/login, 1 MiB de saída |
| HTTP | 400 requisições totais; cinco por JVM incluindo retry; sem refund |
| Paginação | Quatro páginas/JVM; GraphQL 20 nós/página, 80/JVM; DE 16 linhas e 512 derivadas/JVM |
| Fonte | 800 nós GraphQL oferecidos; 8 MiB de requisições recebidas; 16 MiB de respostas oferecidas |
| Resposta/log | 1 MiB por resposta; 16 KiB por saída JVM |

Submissão sqlcmd é um arquivo SQL fechado, podendo conter vários batches.
Commits internos são um teto conservador reservado de oito por submissão JDBC
dos procedimentos fechados (512 × 8), não telemetria exata de commits do engine.
As três transações administrativas duráveis previstas são instalação, ativação
e compensação; as qualificações e barreira são revertidas. Readbacks não fazem
commit persistente. Publicações são medidas pelos recibos SQL por execução,
não inferidas de exit. Os 49 UUIDs de execução limitam as ocorrências sintéticas.
Bytes/nós de resposta contam o que foi oferecido pelo servidor; desconexão pode
impedir transmissão completa. HALT pode impedir o hook de estatística JVM:
nesses casos permanece a reserva inteira e o readback SQL é obrigatório.

## Ledger, parada e recuperação

`Bloco60Budget.psm1` grava JSONL durável com sequência, SHA-256 anterior e hash
do evento, sob lock exclusivo. RESERVE precede cada efeito; falha não devolve
saldo. UNKNOWN impede outro efeito ordinário até RECONCILE com hash do readback.
Somente o par concorrente nomeado pode ter duas JVMs pendentes, sob a barreira
SQL explicitamente reservada. Não há renovação automática de vigência ou saldo.

Ao primeiro erro, timeout, excesso, divergência de alvo/perfil/hash ou falta de
readback, o controlador interrompe o fluxo, termina apenas handles dos filhos
próprios, fecha loopback e tenta a compensação exata, precedida de leitura.
`recovery-state.sql` distingue UNACTIVATED, ACTIVE, COMPENSATED e MIXED_STOP.
Estado misto exige parar; não há reparo inferido. Se a tentativa de compensação
já foi submetida e o estado continua incerto/ativo, não será reenviada. Readback
e nova decisão material são necessários, sem apagar a reserva anterior.

`-RecoverOnly` usa o mesmo hash aprovado, evidência/ledger existente e escrow;
confere o alvo e o estado e só compensa se ainda autorizado e não submetido.
Não repete cenários nem instalação. Não converte resultado incerto em sucesso
da campanha. Preserva schemas, vínculos, linhas sintéticas, current/history,
staging, decisões, consumos, DQ, auditoria e recibos. Não há sweep/delete/clean.
Artefatos protegidos permanecem para revisão, expirando pela janela própria.

## Matriz e comando após aprovação

`matrix.json` e `requests/` são a lista fechada dos 74 casos, com exit, HTTP e
oráculos SQL explícitos. Cobrem as seis verticais, leitura, recusas de autoridade,
DQ incompleta, perdas de confirmação, seis HALTs antes/depois de prepare/selo/
apply, retomadas, lease, replay, force, no-op/update/conflito e concorrência.
PARTIAL comprova a primeira página staged/auditada sem publicar. O probe de
consume descarta a confirmação somente após drenar a conclusão JDBC. Os probes
de ACK simulam perda no cliente depois de executar; a verdade é o readback.
As provas HALT são perda real do processo quando executadas fisicamente.

O par concorrente precisa de dois PIDs próprios distintos bloqueados pelo PID
SQL próprio no mesmo applock. Saídas simultâneas sem essa observação não passam.
`057` testa recibos e parâmetros adversariais no SQL, com IDs congelados.

Comando preparado (não executado):

```powershell
pwsh -NoProfile -File scripts/validation/Invoke-Bloco60Physical.ps1 -ApprovedPackageSha256 <SHA256_DE_PACKAGE_JSON_APROVADO>
# Somente recuperação de campanha já aberta, sem repetição:
pwsh -NoProfile -File scripts/validation/Invoke-Bloco60Physical.ps1 -ApprovedPackageSha256 <MESMO_HASH> -RecoverOnly
```

Aceite máximo futuro: `QUALIFICACAO_FISICA_LOCAL_USUARIOS`, somente após todos
os oráculos físicos e recuperação passarem, com revisão de evidência/ledger.
Este pacote preparado não recebe esse aceite. GraphQL permanece transitório,
SHADOW_UPSERT_ONLY; terminalidade não prova completude/ausência/snapshot.
