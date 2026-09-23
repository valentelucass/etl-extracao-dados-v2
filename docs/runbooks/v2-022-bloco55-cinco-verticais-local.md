# Bloco 55 — operação das cinco verticais no laboratório

Situação em 08/09/2026: **LOCAL_A_J_COMPLETE**. As dez frentes estão comprovadas
no laboratório: cinco verticais pelo JAR, autorização, recuperação, temporal,
comparação, medição e operação manual. A chamada complementar executou após o
pedido do owner para prosseguir. Nenhuma frente autorizada permanece pendente.

Alvo único: `localhost/ETL_SISTEMA_V2_SHADOW`. Fonte GET literal `127.0.0.1`,
templates 6908/6389/6399/6906/8656. SERVICE=`etl_v2_exec`, OPERATOR=`etl_v2_view`.
Validade original: `2026-10-07T22:34:30.615Z`. Nenhuma fonte real foi executada.

## Matriz de entregas A–J

| Frente | Entregável e componente | Camada e evidência | Estado e limite |
| --- | --- | --- | --- |
| A | Seleção fechada `RuntimeVertical`, requests/fingerprints e revisão G06/G07/G08 | Java 17; `RuntimeOperationalExecutionTest`, `LocalFiveVerticalRuntimeTest`; matriz canônica abaixo | Implementado; fonte real não é pré-requisito da identidade local |
| B | `LocalFiveVerticalRuntime`, três extractors tipados, JDBC Manifestos, Cotações/Localização existentes compostos | JAR oficial → HTTP loopback → JDBC → SQL; `B55_SMOKE_03`, `B55_MATRIX_01` | Cinco fluxos publicados; relações Manifesto→Coleta e Localização→Frete diferidas |
| C | V022/V023, fences SQL, recibo tarifário, projection snapshot, grants/scopes exatos | Qualificação rollback/commit/nova conexão; `B55_SCHEMA_APPLY_02`, `B55_V023_APPLY`, `B55_DQ_FIX_01`, `B55_READ_03`, `B55_SQL_01` | Concluído; recusas diretas sob SERVICE, sem DML/DDL/SHOWPLAN concedidos ao runtime |
| D | JAR protegido v4, RUN/STATUS, cancelamento e restart de JVM própria | `B55_SMOKE_03`; `B55_MATRIX_01`: 34 chamadas JAR e nove verificações derivadas; complementos SQL e manuais | Concluído; revogação tarifária comprovada na função SQL real em rollback, demais travessias pelo JAR |
| E | Build isolado e preservação | `verify-03.log`: 1138/0/0/4; `final-jar-equivalence.json`: 584 entradas iguais; snapshot SQL e review próprio | Concluído: gates, preservação, UTF-8, sintaxe, secret scan e contraprovas |
| F | Policies e exports BACKFILL sintéticos, store SQL e reconciliação temporal existentes | `B55_TIME_01`, `temporal-reconciliation.json`; Coletas/Fretes reutilizam temporal B54 e regressão B55 | Seis janelas novas, ano bissexto, 2 antes de 1, fronteira sem salto e retomada; sem watermark incremental novo |
| G | Contrato e runner SQL de comparação por ocorrência publicada | `B55_COMPARE_02`: 30 casos de igualdade/divergência/duplicidade/ausência/janela/incompletude | LOCAL_RUNTIME_OUTPUT_COMPARISON_PROVEN_SYNTHETIC_ONLY; paridade real aberta |
| H | Cinco pipelines reais com sink limitado de teste e planos SQL dos nove consumers | 15 receipts de escala do `verify-03`; `B55_PLANS_05`, candidate/apply/recovery por nova entidade | Limite em voo/liberação comprovados; planos estimados, sem benchmark/platô produtivo |
| I | Launcher fino de até 20 requests congelados e retomada pelo SQL | `B55_MANUAL_STOP`, `B55_MANUAL_RESUME`, `B55_MANUAL_OPERATOR`, `B55_MANUAL_DEPENDENCY`; oito mutantes offline | Concluído; OPERATOR confirma cinco sem HTTP, falha Coletas bloqueia só Fretes e três independentes publicam |
| J | ADR 0038, matriz, inventários, manifest e validators de fase | STATES primeiro, trilha depois; hashes, diff B55 e contraprovas | Concluído; único subaceite P02V/B55 e aceites existentes comprovados, pais externos preservados |

Os IDs são referências técnicas de provas privadas em `target/bloco55`, sem
payload ou ID de negócio neste relatório. O snapshot corrente comprova 9.137
linhas no banco, 56 tentativas/42 publicações/142 páginas/96 entradas auditadas
e 114 linhas de projeção B55. Os estados de teste são preservados: três
CANCELLED, sete FAILED, três EXTRACTING com lease expirada e um STAGED em conflito.
Não são sessões ativas nem autorização para stale recovery global.

## Critérios originais e revisão dos aceites

| Gate e requisito original | Componente/inputs reais | Prova reutilizada e nova | Decisão |
| --- | --- | --- | --- |
| G06 / V2-042b: mecanismo, authority/audiences, principals | Windows integrado; SQL local exato, hostname/TLS/pins e módulos administrados; duas contas existentes | Provisionamento autorizado B53; inventário B54 preservado; perfil `B55_READ_03` | Comprovado para o mecanismo adotado |
| G06: autoridade dos papéis, mappings/revogação e principals SQL | Administração do laboratório autorizada pelo owner; mappings/scopes versionados, expiração original, EXECUTEs exatos | ADR 0035 e runbook das contas; matriz real B54 e perfil 32/16 B55 | Comprovado; não atribui autoridade de negócio ou produção |
| G06: sink durável e estratégia aprovada de pseudonimização | Decisão/consumo append-only V016; UUID aleatório de auditoria, SID somente no cadastro protegido | Estratégia do mecanismo existente B53/B54, reutilizado pela adoção da seção 3; recibos reais sem SID/claims no JAR | Comprovado no laboratório adotado; não inventa issuer OIDC nem política corporativa |
| G07 / V2-042c: verifier, fingerprint e sanitização | `WindowsSqlRuntimeAuthorization`, recurso de authority administrado, escopo fechado e SQL autoritativo | Matriz Windows/SQL B54 com 39 casos; regressão Java final B55 | Comprovado; G06 satisfeito no mesmo escopo |
| G07: uso único/antes do vencimento no dispatcher/handlers; positivos/negativos JAR | Consumo SQL antes de compor a fonte, revalidação durável, recusa sem efeito e auditoria | JAR v7 B54, JAR v4 B55, `B55_SMOKE_03` e `B55_MATRIX_01` | Comprovado; nenhum sucesso do mock substitui o JAR |
| G08 / V2-022b: capabilities reais aos handlers, uso único/validade e auditoria durável | Composition root oficial, dispatcher e handlers das cinco; identidade Windows/SQL real | Publicações JAR e consumo 1; OPERATOR negado, restart/no-op e material tarifário vinculado | Comprovado; V2-022a/G06/G07 atendidos |
| V2-042 pai: JAR sem bypass, credencial bloqueada/expirada recusada, sem DDL/cutover, testes/auditoria sem segredo | Boundary oficial e perfil restrito exato, authority protegida, módulos SQL com consumo vinculado | Matriz real B54 de 39 casos e negativas oficiais; SMOKE/MATRIX B55, recusas 52840/229 sob SERVICE e vínculos originais SQL após material adulterado | Concluído no mecanismo Windows/SQL adotado; não é ratificação de governança produtiva |
| V2-022 pai: comandos/consumidores e política operacional integral | Runtime local existente e suas extensões | Comandos sem consumidor autorizado continuam recusados; matriz/cadência/SLA/blackout operacionais dependem do recorte adotado | Aberto; publicação sintética não implementa fatos/views/sweep/agenda |
| P02V / V2-022/INTEGRACAO_LOCAL_CINCO_VERTICAIS | Pacote único A–J | Matriz acima e validator privado completo | Concluído; integração local sintética das cinco, sem duplicar aceites de domínio |

O manifest `database/manifest/runtime-bloco55-acceptances.json` fixa os três
subgates existentes e V2-042, seus inputs e hashes. A contagem é 65/115 (56,5%),
com 50 pendências e 193 rotas abertas. Somente um subaceite local foi acrescentado
e concluído: P02V. A base de verticais
continua 5/9; agora as cinco atravessaram o runtime local.

## Evidências e falsos resultados preservados

`B55_SMOKE_01` recusou a pasta protegida antes de SQL. Os antigos PASS de negativas
nessa rodada não são aceitos. `B55_SMOKE_02` publicou Coletas/Fretes e bloqueou
as três novas pela definição DQ incorreta. A correção foi autorizada depois do
pacote concreto: manifest `c07fe2c271d9f0a94e57b752a4bacc6d5d4b99a9581cbf254652bec939e36abb`.
Há três versões v1 revogadas e três v2 ativas; as linhas e erros ficam no histórico.

Em `B55_TIME_01`, três receipts PERSIST têm `passed=false` porque o controlador
procurou um rótulo textual errado. O JAR retornou `TEMPORAL_PERSISTED windows=2
extracted=0 scheduler=0`; a consulta SQL independente confirmou duas janelas por
vertical. `Test-Bloco55TemporalEvidence.ps1` confere os logs originais, o SQL,
as fronteiras e as novas invocações. A reconciliação é um novo receipt; os
três resultados originais não foram editados.

Em `B55_SQL_01`, quatro receipts têm `passed=false` por expectativa incorreta de
exit 30. A recuperação durável retorna INCONSISTENT/SOURCE_DQ (40), sem HTTP nem
alteração do estado. `Test-Bloco55SqlFenceEvidence.ps1` cruza logs, requests,
cinco recusas SQL diretas e a leitura independente `B55_READ_03`: três vínculos
originais completos, três publicações/nove páginas/nove saídas inalteradas,
quatro consumos das invocações recusadas e referência tarifária original intacta.
O novo `sql-fence-reconciliation.json` preserva os quatro resultados originais.
A consulta `B55_READ_02` falhou por collation; a correção BIN2 explícita passou
em `B55_READ_03`, consumindo três novas reservas. Nenhuma falha não reconciliada
é promovida a PASS. Há 149 invocações JAR e 208 HTTP, incluindo tentativas falhas.

A comparação inicial parou por collation nas tabelas temporárias, antes de alterar
dados persistentes. `B55_COMPARE_02` é a rodada válida. As primeiras tentativas
de SHOWPLAN falharam na forma de execução/escopo de tabelas temporárias/tamanho.
`B55_PLANS_05` contém os nove consumers válidos; cada statement é um documento
ShowPlanXML completo limitado a 1 MiB. Isso é diagnóstico estimado como administrador,
distinto da aplicação real sob conta restrita.

## Operação manual e limites

Revisão aprovada: `target/bloco55/reviewed-bundle-v4`, manifest SHA
`a586b69ca27ade4082c363186067e3213db455398090f594f229960df5032c13`.
Pasta física: `C:\ProgramData\EslEtlV2\app-bloco55\a586b69ca27ade40`.
Somente Administrators/SYSTEM escrevem; as contas de runtime recebem RX.
Os segredos DPAPI existentes são lidos em memória pelo controlador administrativo;
não se pede senha pelo chat, não se instala conta e não se imprime token.

Preview offline, sem SQL/HTTP ou reserva:

```powershell
& scripts/runtime/Invoke-Bloco55ManualBatch.ps1 `
  -Manifest target/bloco55/manual/positive/manifest.json `
  -ManifestSha256 19e0e7f307c4bea191382cdcb6beccd47f3d6452c336a8726a780d66cbd6e293 `
  -RunId B55_PREVIEW_REVIEW -Operation Preview
```

O manifesto tem até 64 KiB e cada request até 16 KiB. Valida todos os campos,
hashes, paths sem reparse, alvo, configuração, DQ, tarifa, IDs, DAG e datas antes
do primeiro efeito. Somente Coletas→Fretes é dependência. RUN/STATUS usam nova
invocationId, conservando execução, janela, contrato, configuração e referências.
`-StopAfter` encerra explicitamente após um número de requests. Retomar é outra
chamada com o mesmo manifesto e novo RunId; não altera o plano congelado.

RUN e STATUS físicos exigem PowerShell 7 elevado normalmente, campanha aberta
e saldo no ledger B55. O launcher chama o mesmo JAR, sob conta restrita. Uma
campanha não pode coexistir com Maven/medição. O controlador limita cada filho
a 60 s/16 KiB e encerra somente seus processos; SQL/request têm teto de 30 s.
Até duas JVMs runtime e quatro conexões SQL. Resposta de fonte até 1 MiB.

Orçamento adotado: até 384 unidades, três lotes de 128, até 12 campanhas de
15 minutos. Até 16 entradas/quatro páginas/512 derivados por unidade; totais
6144/1536/196608 e 2048 HTTP. Reserva append-only antes do efeito, sem devolução.
Final: 259 reservas, 11 campanhas fechadas, três lotes declarados. O terceiro
lote foi declarado após preservação/perfil conferidos, como margem corretiva
da consulta de vínculos. Manifest SHA
`d14d44d358f90ce3798f159ad9d1d16aee469f7a05ca312c7f6ee9341104492f`.
Restam 125 unidades não utilizadas; os 18 créditos B54 não entram neste ledger.

A chamada `Invoke-Bloco55RemainingPhysical.ps1`, SHA
`4100006d8e94ca7b9fa48cb4271699f96d596c16663c86309c26eca44a6edce4`, reúne
STATUS OPERATOR de cinco requests, lote negativo de cinco e negativas SQL.
Ela executou na campanha 9 e não abre campanha sozinha nem reapresenta UAC
automaticamente. Os receipts estão nos três diretórios citados; não há chamada
pendente. Ausência de receipt continua impedindo aceite no validator.

O launcher também oferece `-Operation Diagnose`: confere o lote inteiro, ACL e
artefato protegido, catálogo, perfil de direitos e agregados próprios no SQL.
É leitura administrativa limitada, com três reservas; não inicia fonte nem JAR.
A fase SQL da chamada complementar inclui esse diagnóstico antes dos negativos.

Resumo distingue PUBLISHED, ALREADY_CONFIRMED, DEPENDENCY_BLOCKED, CANCELLED,
FAILED e UNCERTAIN. Integridade/preflight/limite interrompem o lote. Falha de
Coletas bloqueia somente o Fretes dependente; as três demais podem continuar.
O resumo é limitado a 64 linhas/16 KiB e consulta somente ocorrências próprias.

## Recuperação e verificação

Não repetir instaladores nem aplicar novamente migrations confirmadas. Consultar
`applied.json` e receipts em conexão nova antes de decidir qualquer correção.
As migrations aplicadas são V001–V023. A baseline inclui as duas caudas novas,
qualificadas em rollback; não foi reinstalada sobre dados B53/B54.

| Situação | Aplicação/verificação preparada | Recuperação |
| --- | --- | --- |
| Direitos novos | `bloco55-runtime-extension/activation/apply.sql` e `verify-profile.sql` | `recovery.sql`: revogar somente novas concessões/scopes, preservar versões/dados |
| Schema V022/V023 confirmado | `manifest.json`, `verify-schema.sql`, receipts de catálogo | `recovery.md`: nova migration aditiva; sem down/reinstalação |
| DQ incorreta | `bloco55-dq-correction/apply.sql`, `verify.sql`, receipt `B55_DQ_FIX_01` | `recovery.sql` revoga revisões corretivas se necessário; não reativa inválidas nem apaga histórico |
| Ocorrência conhecida | RUN/STATUS com nova invocação e materiais originais | SQL retorna recibo ou recusa estado incompleto; não reextração escondida |
| Fonte parcial/cancelamento/lease expirada | Receipt, estado próprio e logs bounded | Preservar a ocorrência; sem takeover/stale global ou limpeza |

Verificações offline reproduzíveis:

```powershell
& scripts/validation/Test-Bloco55Acceptances.ps1 -IncludePrivateEvidence
& scripts/validation/Test-Bloco55Integrated.ps1 -IncludePrivateEvidence -RequireComplete
& scripts/validation/Test-Bloco55CompletionGuards.ps1
& scripts/validation/Test-Bloco55TemporalEvidence.ps1
& scripts/validation/Test-Bloco55SqlFenceEvidence.ps1
& scripts/validation/Test-Bloco55ManualBatch.ps1
& scripts/validation/Test-Bloco54Integrated.ps1 -IncludePrivateEvidence
& scripts/validation/Test-SchemaFoundationManifest.ps1
& scripts/validation/Test-ProgressiveDataGate.ps1
& scripts/validation/Test-WindowsRuntimePackage.ps1
& scripts/validation/Test-Gpt56ChatTrail.ps1
```

Build final: Java 17, Maven offline, POM temporário idêntico ao canônico salvo
`build.directory=target/bloco55-build`, sem `clean`, gates intactos. DLL veio
somente do cache anterior. A equivalência das entradas do JAR final com a revisão
protegida foi conferida depois do verify, sem reinstalação desnecessária.

O manifest de evidências aponta para o diff/inventário próprio do B55 e seus
receipts. O diff usa o snapshot inicial da sessão, preservando o worktree anterior;
o manifest gerado é entregue separadamente para evitar um hash de si mesmo.
`-RequireComplete` exige a matriz integral e, com `-IncludePrivateEvidence`,
reconfere os receipts físicos, hashes e reconciliações. As contraprovas B55 e
B54 recusam aceites indevidos, provas ausentes, duplicação e contagens falsas.

## Próximos inputs realmente externos

O pacote de comparação B54 e o intake V2-041 continuam reutilizáveis. Para uma
futura rodada real faltam coordenação/atestado de rotação e saúde do writer,
recorte da fonte/empresa, período e oráculos independentes, moeda/arredondamento,
rótulos de status e aprovações de negócio/destino das evidências. Nenhum desses
inputs é deduzido das fixtures. Não conectar à fonte para descobri-los.

Identidades não resolvidas de 8636/4924/10633/6392, relações, fatos, views,
bootstrap real, sweep, escala/paridade, release e cutover mantêm seus critérios.
Não houve ETL_SISTEMA, produção, agendamento, rotação, commit ou push.
