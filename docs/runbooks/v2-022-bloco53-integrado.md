# Bloco 53 — pacote integrado Windows/SQL

Execução local autorizada em 07/09/2026 conforme o [prompt ampliado](prompt-bloco-53-pacote-integrado-runtime-astra.md) e o [P02Q](prompt-bloco-53-qualificacao-fisica-motor-astra.md). As seis frentes pertencem ao mesmo bloco. O contexto Windows administrativo existente não comprova privilégio mínimo.

## Entregas e limites

| Frente | Implementação e prova local | Limite de aceite |
| --- | --- | --- |
| A — motor | Coletas/Fretes com JDBC real, staging/DQ/promoção/recuperação, commits limitados e novas JVMs | Somente localhost/ETL_SISTEMA_V2_SHADOW, dados sintéticos |
| B — identidade | Adapter Windows/SQL, parser fechado, pins administrados, preflight e recusas tipadas; gerador de provisionamento | Faltam principals restritos, authority/TLS/distribuição e escopos reais; V2-042b aberto |
| C — auditoria/consumo | V016 append-only, invocação idempotente, capacidade até 60 s, 22 campos de escopo, consumo único com fence e revalidação; DENY real entre JVMs | ALLOW/consumo em duas JVMs sob contas restritas dependem do provisionamento; V2-042c/V2-042 abertos |
| D — composição | Main/RuntimeCompositionRoot ligados ao boundary e handlers; fonte após consumo; status SQL somente leitura; JAR direto nega sem authority | JAR positivo e fonte real não executados; V2-022b/G08 e V2-041 abertos |
| E — temporal | Política explícita, planner/coordenador com store JDBC, catch-up limitado e persistência idempotente | Matriz nominal/ativação não ratificadas; V2-022 pai aberto |
| F — regressão | Java 17 offline, estilo/arquitetura/cobertura, scanner, SQL/manifestos, JAR e preservação | Não equivale a paridade, escala, cutover ou least privilege |

Decisões no [ADR 0035](../adr/0035-authority-windows-sql-consumo-duravel-e-plano-temporal.md) e [pacote de provisionamento](v2-042-provisionamento-windows-sql.md). O JAR não contém runtime-authority.properties nem bypass de teste. Consumo sem despacho exige nova invocação autorizada para a mesma ocorrência; não reutiliza capacidade nem cria outra máquina de estados. O begin do plano oficial deixou de executar recuperação global de leases, pois uma autorização de namespace não permite alterar ocorrências alheias.

## Preservação, schema e recuperação

Snapshot inicial de 1084 arquivos não ignorados em target/bloco53/initial, inventário SHA-256 e status Git adjacentes. Nenhum arquivo inicial removido. Não houve leitura de .env, fonte externa, download, alteração de conta/grant operacional/serviço, Git commit/push ou ação produtiva.

Preflight master somente leitura: alvo online, SQL Server 17.0.1000.7, compatibilidade 170, sem cluster/HADR; NTLM, contexto sysadmin/db_owner/CONTROL. Sete objetos históricos, sem histórico Flyway, auditoria exata 0/0/0. Validators 002/049/006 passaram com rollback integral antes da instalação.

Invoke-RuntimePhysicalSchema.ps1 -Install confirmou V001–V015 atomicamente depois de comparar baseline/migrations individuais em transações independentes revertidas. Fingerprint estrutural: e2aca4133c541b6f1e0d9bca09b35d6a79257d4433a44653ed406d34a0e9bf97. A comparação inclui módulos, colunas, índices, FKs, checks e permissões; normaliza somente sufixos aleatórios de nomes marcados pelo SQL como gerados pelo sistema.

Invoke-RuntimePhysicalEvolution.ps1 -Install qualificou/instalou V016/V017 aditivamente, preservando as 3079 linhas então existentes. Fingerprint final: 7d3d916b3e1723ecc9992d98c582d846eb930354fb8d21dfd53adbf35cdd5378. Ledgers imutáveis em target/bloco53/schema/installed-migrations.json e installed-additive-migrations.json; hashes V016/V017 no manifesto versionado database/manifest/runtime-windows-authority-temporal.json. Baseline final V001–V017; nenhuma migration aplicada foi reescrita. Sem flyway_schema_history fabricado.

Runners históricos recusam a topologia moderna populada. Não repetir reset/baseline/instalação sobre ela. Correções futuras exigem nova migration disponível, qualificação aditiva e autorização correspondente. Schema e dados confirmados permanecem; não há limpeza destrutiva. A correção JDBC de Coletas envolve o alias escalar no objeto JSON exigido por V010, preservando null e o contrato aplicado.

## Matriz física

RuntimeRecoveryLocalIntegrationIT usa somente fonte sintética/injeção JDBC no test-classpath. Guard, mapper, auditoria, control plane, staging, DQ, promoção e recuperação são reais. Readers iniciam outra JVM/DataSource e comparam onze campos do recibo com SELECT em outra conexão SQL; não transportam recibo/selo/permit por arquivo. Retomadas assertam zero fetch.

| Caso | Resultado exigido pelo teste |
| --- | --- |
| Duas verticais: normal/ack perdido depois do commit | PUBLISHED, onze campos iguais, publicação=1, candidatos=3, páginas=2; repetição sem efeito |
| Selo/prepare persistido; falha antes de apply | RECOVERY_REQUIRED seguido de promoção elegível única |
| Apply em transação física revertida | Nenhum efeito parcial; nova JVM retoma uma vez |
| Extração parcial com encerramento de filho próprio | PARTIAL_EXTRACTION; sem nova extração |
| Lease vencida ou expirada entre READ/RESUME | LEASE_LOST; sem renovação/roubo |
| Duas recuperações e corrida apply/RESUME | Barreiras entre duas JVMs; um conjunto de efeitos |
| Cancelamento sob lock e depois de commit | Attention/fechamento no prazo; readback do SQL confirmado |
| Incremental/replay separado | Replay vinculado à origem não avança fronteira incremental |
| COL-03 e publicação posterior | Recibo tipado updated=3 versus genérico stale=3; histórico preservado |
| Coletas legada sem recibo tipado | EVIDENCE_MISSING, sem reconstrução |
| Dependência Coletas→Fretes e ramo independente | FAILED/BLOCKED; dependente sem fetch; publicação independente preservada |
| Policy revogada | Histórico publicado continua legível; retomada selada recusa DQ_OBSOLETE |
| SQL adversarial 052 | 40 adulterações recusadas, terminal sem lease, COL-03 e publicações preservados |
| Authority sem configuração real | DENY durável idempotente entre duas JVMs, zero consumo |
| Plano temporal SQL | Três janelas; repetição em outra JVM sem duplicação; uma pendência limitada |

Ack perdido é exceção cliente após confirmação física, não outage de transporte. O teste parcial encerra somente filho próprio, sem restart do serviço. Testes positivos de autorização com SQL simulado cobrem duas threads, expiração/revogação, escopo, sink incerto e uso único; não substituem ALLOW físico sob principal restrito.

## Orçamento e reprodução

Ledger cumulativo target/bloco53/cumulative-reservations.txt. Reserva admite 16 entradas, quatro páginas e 512 derivados; teto compartilhado 128/2048/512/65536. Falhas não devolvem reservas. Quatro campanhas finitas de até 15 minutos, sem estender deadline nem apagar ledger; máximo duas JVMs filhas, duas conexões por JVM, quatro totais, filho até 60 s, SQL até 30 s. Plano temporal reserva suas três janelas antes da escrita.

Build usa POM temporário .bloco53.pom.xml equivalente ao canônico exceto build.directory=${project.basedir}/target/bloco53-build. Igualdade XML conferida removendo somente esse elemento; remoção pontual ao encerrar. Não usar clean canônico sobre ledger/evidências. Para reproduzir o build isolado, copiar o POM e adicionar somente esse elemento ao build; não usar profiles que baixem dependências.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
.\mvnw.cmd --offline --batch-mode --no-transfer-progress -f .bloco53.pom.xml clean verify
# Exige manifesto vigente e saldo; este comando não cria nova autorização:
.\mvnw.cmd --offline --batch-mode --no-transfer-progress -f .bloco53.pom.xml `
  -Pruntime-recovery-local-integration `
  '-Druntime.recovery.local.integration.enabled=true' `
  '-Druntime.recovery.local.integration.manifest=target/bloco53/campaign.properties' `
  process-test-classes failsafe:integration-test failsafe:verify
```

process-test-classes copia a DLL 12.8.1.x64 do cache após clean. A chamada direta failsafe sem essa fase falhou por DLL ausente antes do SQL; suas seis reservas foram preservadas. Não alterar PATH global/download. Validators modernos, com cwd database/validation: sqlcmd -S localhost -C -E -f 65001 -d ETL_SISTEMA_V2_SHADOW -l 10 -t 30 -b -i arquivo. 050 é rollback-only compatível com dados retidos, 051 estrutural sem provisionamento, 052 somente lê dados permanentes. Não repetir 002/006/049 históricos no alvo moderno.

## Regressão e artefato

Log target/bloco53/full-clean-verify.log: BUILD SUCCESS, 1042 testes, zero falhas/erros, quatro skips esperados; Enforcer/Spotless/Checkstyle/arquitetura/JaCoCo verdes, thresholds preservados. A primeira suíte detectou duas violações de coleções; limites reais e contrato de leitura paginada foram explicitados, a repetição justificada passou. Alterações posteriores do harness receberam compilação/estilo e campanha física dirigida; produção permaneceu igual à suíte completa verde.

JAR target/bloco53-build/etl-dataexport-v2.jar com oito dependências lib: help/version retornaram 0; run/status/replay/force-run retornaram 20 por authority ausente, sem fonte. Manifest/ZIP conferidos: nenhuma fixture física, RuntimeSyntheticJdbc ou configuração permissiva. Logs/requests sintéticos em target/bloco53/jar-official.

Provas temporais incluem dezembro/janeiro, fevereiro bissexto, mês civil anterior, dia de 23 h, gap/overlap recusados, fim exclusivo, blackout, estabilização, backlog, reinício, conclusão fora de ordem, degradação e replay. Coordenador lê até 64 resumos e retorna até quatro pendências; não carrega histórico completo nem agenda execução autônoma.

Scanner offline passou nove contraprovas; relatórios de scanner, UTF-8, diff, trilha, preservação e SQL ficam em target/bloco53, ignorados pelo Git. O fechamento abaixo registra os resultados finais executados.

## Provisionamento preparado, aplicação pendente

O [procedimento](v2-042-provisionamento-windows-sql.md) entrega parser fechado de nove entradas e gerador de aplicação, verificação em nova sessão, compensação preservando auditoria e quatro pins administrativos. Fixture gerada: dois database users para logins existentes, 22 grants de procedures exatas (20 SERVICE/dois OPERATOR), nenhum DML direto/schema-wide. SQL gerado não executado; replay/force-run sem papel na proposta inicial. Duplicatas, injeção, campos ausentes e pin divergente são recusados.

Faltam dois logins Windows restritos reais existentes, authority/TLS/responsável administrativo, escopos/vigência ratificados, distribuição protegida e referência da revisão. A seção 3 do prompt exige autorização específica sobre o pacote com esses fatos. Depois será necessária prova em novas JVMs sob cada principal/JAR oficial; fonte externa e matriz nominal mantêm seus gates. Essa dependência não impediu a implementação e os testes independentes B–F.

## Fechamento executado — 07/09/2026

Campanha física final corrigida: **6 testes, zero falhas/erros/skips, BUILD SUCCESS**, 259,1 s de testes, encerrada 18:27:25 -03 dentro do manifesto. Log: target/bloco53/physical-final-all-restored.log. A tentativa com DLL ausente fica preservada em physical-final-all.log. Ledger final: **110/128 reservas**, limites reservados 1760 entradas/440 páginas/56320 derivados; consumo SQL observado **95 tentativas, 273 entradas, 176 páginas, 75 publicações e 4978 linhas totais**. Há 75 PUBLISHED, dez FAILED, dois BLOCKED e oito EXTRACTING retidos pelos testes de falha/lease; não são operação ativa nem autorização para limpeza/renovação. Pós-flight: zero transação aberta dos filhos próprios.

Authority configurations/mappings/scopes: 0/0/0; decisões DENY=2, consumos=0, janelas temporais=6. SQL 050/051/052 final PASS. Hash estrutural continua 7d3d916b3e1723ecc9992d98c582d846eb930354fb8d21dfd53adbf35cdd5378, dezessete migrations iguais aos ledgers aplicados. Inventário em target/bloco53/postflight-final.log. Nenhuma reserva foi devolvida ou teto reiniciado; nenhum dado confirmado removido.

**Somente P02Q fechado: 60/114 (52,6%), 54 pendentes, 196 fatias abertas, zero AGORA; Bloco 54 não atribuído.** B–F entregues em implementação/testes independentes, com os gates operacionais específicos da tabela preservados. POM temporário conferido por igualdade XML e removido pontualmente; build/evidências permanecem ignorados sob target.

Validação final adicional: gate progressivo (inclui manifests/schema/Coletas/Fretes/P02R), pacote Windows e trilha PASS. Cinco contraprovas em snapshot próprio foram recusadas: aceite indevido V2-042b, P02Q ausente, Bloco 53 duplicado, painel falso e G08 promovido indevidamente. Scanner offline: 1127 candidatos, 1126 textos, um binário verificado; zero finding/oversized/não inspecionado. UTF-8 estrito sem BOM: 497 textos alterados/novos, incluindo o worktree preexistente; o literal U+FFFD usado pelo validator antigo foi preservado byte a byte, não é falha de decodificação. git diff --check PASS. Preservação: 1084 arquivos iniciais presentes, 26 alterados pelo bloco e 43 novos; inventários em target/bloco53/changed-initial-paths.json e new-paths.json. Nenhum arquivo inicial removido; somente o POM temporário próprio foi excluído.

## Autorização posterior para provisionamento

O owner confirmou no chat a permissão para o pacote descrito. Essa autorização passa a valer sem nova pergunta de aprovação. Preflight somente leitura confirmou zero login Windows individual habilitado sem sysadmin; a conexão sqlcmd com TLS verificado passou. O impedimento atual são as identidades reais para SERVICE/OPERATOR e sua configuração nominal. Nenhum grant ou identidade foi aplicado, e nenhum gate de autenticação real foi marcado. Detalhes e evidência no [runbook de provisionamento](v2-042-provisionamento-windows-sql.md).

## Provisionamento local realizado após autorização ampliada

O owner confirmou que as contas não existiam e autorizou criá-las. O [procedimento local](v2-042-contas-locais-windows.md) foi executado: duas contas Windows restritas, dois logins/usuários SQL, 22 grants, oito scopes, authority e distribuição local administrada com TLS validado. JAR oficial: status nas duas contas autenticou/consumiu e retornou 10 por NOT_FOUND; OPERATOR run retornou 20 antes de fonte. SQL confirmou dois ALLOW e dois consumos, zero sessão/transação restrita remanescente. Ledger 112/128; 95 tentativas e 75 publicações anteriores intactas, 4994 linhas totais. Não houve criação de outro banco, leitura/alteração de ETL_SISTEMA, restart ou fonte externa. O fingerprint de catálogo passou a df70c3666f94d6aaeefadc9f3842178611b8bf51bb9fa16d2f025eff21612f9f pelas permissões/usuários autorizados; migrations e código Java permanecem iguais. Validators 005/050/051 pertencem à fase anterior sem provisionamento; o pós-flight nominal usa 053.
