# VM — 0419: ambiente PS7 pronto, preparação da trilha PASS — 02/10/2026

[STATES.md](STATES.md) e [checkpoint 0419](docs/continuidade/checkpoints/0419-vm-powershell-portatil-trilha-e-continuidade.md). PowerShell portátil oficial 7.6.6 com ZIP/digest/assinatura Microsoft verificados; sem PATH/instalação global/serviço. Preparação da trilha PASS (33 estágios/48 abertos/nove inputs, sem autorização de execução) e 20 autotestes do scanner PASS sob PS7. Continuidade FAIL histórico HANDOFF_PATH: `target/tres-etapas-20260922-01/closed-receipt.json` ausente; não fabricar evidência nem renovar budget. Offline 0418 válido na camada. Primeira sonda ainda depende dos inputs da fonte/G01; sa depende de escopo/segredo local. Nenhum checkbox, contador, P08 físico ou aceite produtivo promovido.

# VM — 0418: qualificação offline aprovada na camada; primeira leitura pendente — 02/10/2026

[STATES.md](STATES.md) e [checkpoint 0418](docs/continuidade/checkpoints/0418-vm-qualificacao-offline-integrada-primeira-leitura.md) são a autoridade desta unidade. Base 2433/0/0/5 Surefire, seis ITs offline de integridade e JaCoCo PASS; código principal/POM/migrations inalterados. Comparador final 9/0/0/0 com quatro novos métodos de recusa. Scanner PS5.1 corrigido: 20 autotestes, manifesto mutante recusado e varredura de 4199 candidatos sem achados. Graphify AST atualizado. Sem aceite P08 físico, fonte real, checkbox novo ou contador alterado. Sonda 6908 diagnóstica pode preceder P17–P29, após origem/tenant/dia, condição G01/autoridade da rodada e teto; não é extração completa nem promoção SQL. Pedido para usar sa aguarda escopo/local seguro da credencial; schema/Flyway/Windows/listeners continuam pendentes. Validadores PS7 de continuidade/trilha não executados nesta VM; não presumir PASS.

# Coletas 6908 — 0416: IT shadow opt-in sintética compilada, gate offline FAIL JaCoCo — 01/10/2026

[STATES.md](STATES.md) e [checkpoint 0416](docs/continuidade/checkpoints/0416-coletas6908-it-opt-in-offline-gate-jacoco-aberto.md), SHA `EF9C11CA530DF0275A8FF7522F46BEE05D132BD43DB443A71AC24C3B426A82F0`. Runner limitado até terminal vazio, expected pré-mapper e comparação escopada em sessão rollback-only têm provas dirigidas offline; `Coletas6908PilotShadowIT` sintética compila e está incluída apenas no Failsafe `shadow-local-integration` com duas travas, mas **não foi executada**. `Main` recusa `--execute`. Base `clean verify` sem shadow falhou apenas no JaCoCo `persistencia.coletas` (linhas 0,74 < 0,80; branches 0,57 < 0,60); Banco cobre os ramos antes de nova qualificação. Nenhuma fonte real/SQL físico ou aceite agregado; P07/P08/Gate 1/P01–P33 seguem abertos, 67/115 histórico e STOP 0415 preservado.

# P08 — 0414: guard fail-closed, rótulo BLOCKER offline e UAC método 5 pendente — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0414](docs/continuidade/checkpoints/0414-p08-guard-blocker-e-uac-offline.md), SHA `460D08EC24BB3279D85BB0F90E394523EF15CD5403FF9CEC6CF98BBF3457745B`. Banco mudou somente dois CASE de rótulo `BLOCKER` em candidato privado 55813/55814, com parser/diff/mutantes offline PASS; predicados e `THROW` intactos, sem relaxar guard. Recibo SHA `BC1A493DBC284D4662DB2417BF7A0446080E3680946A07CE26E6F3EA1DDB9FE5`. O valor bruto R6 não foi preservado: `BLOCKER=NONE` histórico irrecuperável, SYSTEM `OTHER/IO` não atribuído à IT, R6 **STOP_POST_55813** mantido. Runtime SHA `5A878DFCE80477613122509448C52B91500103528E44F077E4DC6F03B89F0C90`: candidato 0403 não limita UAC pré-spawn; método 5 necessita operador interativo disponível para o helper/pwsh pinados ou mecanismo isolado autorizado/provado. Nenhuma execução ou aceite novo; R1–R6 e FAILs preservados. **P07/P08/Gate 1, 107 ITs históricas, JaCoCo shadow, A/B físicos abertos; 67/115 histórico.**

# P08 — 0413: R6 raster STOP_POST_55813; XML/fase não aprovam POST — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0413](docs/continuidade/checkpoints/0413-p08-0412-r6-raster-stop-post-55813.md), SHA `278C16246FBA289588722F9F4B8A58D329CC46E25A251B5CA710A0DA49A63237`. Banco R6 no snapshot 0412/2174 inputs: uma `AnalyticLaboratoryRasterProofsIT#splitCapturesProveEveryLeafWhileMinimumWindowCapRollsBack`, XML **1/0/0/0**, `RASTER_APPLY` **SUCCESS 619 ms**, `sampleCount=0`; timeout 0354 não reproduzido ou explicado. POST formal 55813 FAIL: atividade SYSTEM `OTHER/IO`, blocker `NONE`, observada **após Maven**, sem atribuição à IT. Recibo `target/p08-diagnostic-0412-20260930-06/final-receipt.json` SHA `6E69C6C1C34AD93DC6A9DF45EF13B294303657F26CCAC03B5A86F34C8EB1EC00`: **`TERMINAL_STOP_POST_55813`**, `accepted=false`. Readback posterior SHA `F4E4C9DA1214D1315A0174ABDFF7A51CD1959A408E9D59DB4B8A9EC25408AAD4` sem processo próprio ou delta durável, stats **2544**, não converte STOP em PASS nem prova rollback runtime explícito. R1–R5/0354 preservados; P07/P08/Gate 1, **107 ITs históricas**, JaCoCo shadow, A/B físicos e P01–P33 abertos; **67/115 histórico**. Runtime não executou SQL/JDBC/Maven/sonda; Supervisor avalia eventual unidade distinta com Banco.

# P08 — 0412: R5 STOP_POST_55813 apesar de XML verde — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0412](docs/continuidade/checkpoints/0412-p08-0412-r5-stop-post-55813.md), SHA `6DA9A6CB044A4E021A2C1C54B4B40278757A55613E61794B5E2723E05DF92552`. Banco R5 no snapshot 0412, 2174/2174 inputs, PRE/guard PASS, uma IT `AnalyticLaboratoryObservationModesIT#quotesPublishFourModesWithExplicitTariffAndIdempotentBusinessSnapshot`: XML **1/0/0/0**, `SOURCE_REGISTER` seq. 3 antes de `FRONTIER_REGISTER` seq. 4. Prova offline inicial **FAIL 19/20**, corrigida para **20/20**; histórico preservado. O POST formal recusou **55813** pelo predicado de atividade SYSTEM, com zero atividade de usuário. Recibo `target/p08-diagnostic-0412-20260930-05/final-receipt.json` SHA `C84B26D4D25983BDF7D5CA8EDAB924455C96599C4C9372BBCF5F220A9900A2AE`: **`TERMINAL_STOP_POST_55813`**, sem retry ou aceite. Reconciliação posterior SHA `2666E08A4E38B1DDA671733DD70623AFE564B8751CD45EA6F8B817E7603945A1` sem processo próprio/delta durável, stats PRE=readback **2544**, não aprova o POST recusado nem prova rollback runtime explícito. R1–R4/0354 preservados; **P07/P08/Gate 1, 107 ITs históricas, JaCoCo shadow, A/B físicos e P01–P33 abertos; 67/115 histórico**. Banco executa R6 raster separada sem resultado incorporado; Runtime não executou SQL/JDBC/Maven.

# P08 — 0411: R4 dirigida PASS sem causa histórica observada — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0411](docs/continuidade/checkpoints/0411-p08-0412-r4-it-dirigida-pass-sem-espera.md), SHA `7FAF98B3449BE466EED9F8303799EC5E32D5F6CA609A03446F6E58123BB52A35`. Banco fechou R4 no snapshot 0412, 2174/2174 inputs, request/reserva e ledger próprios: recibo `target/p08-diagnostic-0412-20260930-04/final-receipt.json` SHA `40E9F3C927365C5B24540A4BBDFAD4C2FB02D5DF528264463824B86B54D283E2`, ledger SHA `EB5448E4351362784E7959FDE0FB7602DC1709718849935821959124D94D15A2`. Apenas `AnalyticExpansionCaptureIT#packagedManifestObservationsUseTypedPreparationAndDuplicateLineage` PASS Failsafe **1/0/0/0**; `REFERENCE_IMPORT` **211 ms**/`REFERENCE_EXECUTE` **180 ms** SUCCESS, `sampleCount=0`, causa da espera 0354 não observada. PRE/POST iguais, stats **2544→2544** sem baseline durável; contrato de rollback no close, **sem recibo runtime explícito**. R1–R3/FAILs 0354 preservados; **P07/P08/Gate 1, 107 ITs históricas, JaCoCo shadow, A/B físicos e P01–P33 sem aceite agregado; 67/115 histórico**. P03/B só restrito 0412; C–H/L e P03 agregado abertos. Banco executa R5 separada, ainda sem resultado incorporado; Runtime não executou SQL/JDBC/Maven.

# P08 — 0410: R1/R2/R3 no snapshot 0412 sem aceite físico — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0410](docs/continuidade/checkpoints/0410-p08-0412-r1-r3-paradas-diagnosticas.md), SHA `9169A2446FF1F2D275CF911AD457EC42464A1CA238392A885258B66C753FD7E4`. Banco: R1 parou em `STOP_CONTROLLER_DEADLINE_PARSE_PRE_GUARD`, sem guard/Maven/JDBC; R2 fez guard PASS e iniciou Maven **1x**, mas o controlador falhou no compartilhamento de stderr, deixando execução IT/JDBC transitória **UNKNOWN**; readback/DMV posterior não achou processo ou efeito SQL residual, sem recibo explícito de rollback. R3 corrigiu e testou o runner só em Windows sintético; no preflight físico SQL **55813 `SHADOW_CONSUMERS_PRESENT`** parou antes de guard/Maven/IT. Recibos privados exatos e ledgers fechados no checkpoint; 2174/2174 inputs 0412 preservados. **Não repetir R2/R3 nem abrir R4**; próximo passo de Banco é identificar o consumidor em investigação read-only separada, resultado pendente. P07/P08/Gate 1, P03 agregado/C–H/L, JaCoCo shadow e A/B físicos seguem abertos; P03/B mantém só o aceite restrito 0412, sem promoção de checkbox ou dos contadores históricos. Runtime não executou SQL/JDBC.

# P06/L e P08/P07 — 0409: seleção shadow e A/B offline 0412 — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0409](docs/continuidade/checkpoints/0409-p06-l-p08-shadow-selector-pacote-offline.md), SHA `798C0D1528CFC01CE0ABD838B9CAC8331ECC456CCAB0F3C186FBA28500363236`. `pom.xml` 0412 adiciona só as três ITs Sequence ausentes na seleção Failsafe shadow; effective POM com perfil/trava inclui 3/3, base 0/3, três mutantes recusados, sem IT física. Base JDK17 offline sem shadow/skip PASS Surefire 2378/0/0/5, Failsafe offline 6/0/0/0, Enforcer/Spotless/Checkstyle/JaCoCo; scanner zero achados. A/B PackageShadow 12.8.2 no mesmo snapshot PASS byte a byte/extração/2174 hashes/CLI offline; pins revision `4763D5AF123BC685D3EDDF19BD0726416577CC80A0CD81B9B930CEDA22849130`, manifest `C7CF032CDA8CC8ECC59580D5E9D090B2C5BEFAF7C5BA44FD97F61A55880A49DE`, ZIP `71904040C6B193B407DA63C9619053DBB755514EEDDCD02F504C3CD2036C7924`; guard privado exige recibo/SHA da base 0412, FAIL anterior preservado. 0408/0411 históricos. Handoff Regras aceita só P03/B nos bytes 0408, também provado em 0412 por fonte/teste/sete dependências inalterados e XML 0412 `LocalArtifactSequenceTest` 27/0/0/0; C–H, L/P06 e P03 agregado seguem abertos. **PACKAGED_NOT_SMOKE_QUALIFIED**: 0354 oito FAILs/74 classes, método 5 sem PASS, JaCoCo shadow, A/B físicos, P07/P08/Gate 1/P01–P33 sem aceite integral; 107 ITs é cardinalidade histórica de 0354, sem inventário futuro; 67/115 apenas histórico. IT diagnóstica física 0412 encaminhada a Banco por Supervisor, resultado/readback **pendente**; Runtime não executou SQL/JDBC.

# P08/P07 — 0408: candidatos A/B offline, sem aceite agregado — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0408](docs/continuidade/checkpoints/0408-p08-p07-package-shadow-ab-base-offline.md), SHA `27CFA988984CDFD033ED99D7BA73BAF8025A358B5029D743CB5E69E2BDE6512F`. Gate base JDK17 `--offline clean verify` sem shadow/skip passou Surefire 2377/0/0/5, Failsafe offline 6/0/0/0, Enforcer/Spotless/Checkstyle e JaCoCo check; três FAILs instrumentais de `JAVA_TOOL_OPTIONS` permanecem documentados. Só após PASS, PackageShadow A/B 12.8.2 passivo, com `-DskipTests` limitado a `package`, produziu revision `6014FC5E66CCBCC9394F183E76721C9D8BEA2239FEDD962E3CFD99C365B86331`, manifest `F7E1A0A09FAD5DB8750AB5D18E6A8EC160E967E251F957129506CF177AEECA89` e ZIP `298B1CB7AEB0BE97DCD871DADD89B3CAABC717FEB21BAC6EBEF562EBF958485E`, iguais byte a byte. CLI deny-all A/B, extração/hashes e guardas offline passaram. **PACKAGED_NOT_SMOKE_QUALIFIED**: oito FAILs/74 classes 0354, método 5 sem PASS, 107 ITs, JaCoCo shadow, A/B físicos, Gate 1/P08/P07/P01–P33 seguem abertos. Sem SQL/JDBC físico, DLL carregada, serviço, Flyway ou fonte real.
# P08/P07 — 0407: assinatura void JDBC qualificada offline na camada — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0407](docs/continuidade/checkpoints/0407-p08-p07-banco-contractbatch-void-offline.md), SHA `EBBDFA0ADF7C36B8C270650F45E81A7F7559067DF88A4929291683AAADF3DAED`. Ambas as sobrecargas `contractBatch` retornam `void` e descartam somente contagens ignoradas, preservando uma execução, fase sanitizada, exceção, timeouts e rollback. Maven offline dirigido **25/0/0/0**: arquitetura 17, escopo CI 3 e JDBC falso 5; Enforcer/Spotless/Checkstyle PASS, scanner zero achados e Graphify atualizado. **STOP_BASE/FAILs 0406 permanecem; sem novo `clean verify` integral ou A/B.** Oito erros/74 classes 0354, método 5 sem PASS, P08/P07/Gate 1/JaCoCo/107 ITs/A-B abertos. Supervisor revisa; Runtime é próximo responsável pelo gate integral independente.

# P08/P07 — 0406: base offline FAIL, pacote A/B não gerado — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0406](docs/continuidade/checkpoints/0406-p08-p07-base-offline-fail-sem-candidatos.md), SHA `E502C0AD395DCA87D55CDD6DAAE01018293A3019BE69F290C472A5DC0FAE6257`. Snapshot sem `target`/`.env` 4162 arquivos/1214 Java; inventário de pacote **2174** (quatro fontes adicionadas, cinco callsites alterados contra 0383). `clean verify` JDK17 offline sem shadow chegou ao Surefire **2377/3 falhas/0 erros/5 skips**: duas violações arquiteturais no retorno `int[]` de `JdbcStatementEvidence.contractBatch` sob Banco e catálogo de cobertura desatualizado. Runtime corrigiu somente o catálogo, preservou duas provas dirigidas FAIL e obteve prova dirigida final **3/0/0/0**; arquitetura JDBC continua FAIL. Sem Failsafe/JaCoCo check do gate base, PackageShadow, manifest, ZIP, CLI ou pin candidato. Scanner zero achados e trilha de preparação PASS; continuidade histórica FAIL `HANDOFF_PATH`. Pins 0383 permanecem históricos. Zero SQL/JDBC físico, DLL, perfil shadow ou efeito OS/serviço; oito FAILs/74 classes 0354, método 5 sem PASS, P08/P07/Gate 1/P01–P33 abertos.

# P08 — 0405: fases JDBC internas offline, sem aceite físico — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0405](docs/continuidade/checkpoints/0405-p08-banco-fases-jdbc-offline.md), SHA `C8A838272FB9D84994DAC7845CC120814CE5D26FB30D42616C5BADF98BB4BB91`. Banco acrescentou `CONTRACT_BATCH` no `executeBatch` e `REFERENCE_EXECUTE` no `executeQuery` exatos, com tempo monotônico, outcome e metadados SQL sanitizados. Testes offline com statements falsos **5/0/0/0** preservam resultado/exceção por identidade e contêm falha do sink; Enforcer/Spotless/Checkstyle, scanner zero achados e Graphify PASS. Trilha de preparação PASS; continuidade histórica mantém FAIL `HANDOFF_PATH`. Runtime 0404 intacto. Fonte Java 1214, pins 0383/espelhos 0395–0402 históricos; novo efeito exige revisão/pins próprios. Zero SQL/JDBC físico, IT, reserva/ledger ou perfil shadow; oito FAILs/74 classes de 0354, STOPs, método 5 sem PASS, Gate 1/JaCoCo/P08/107 ITs/A-B abertos. Supervisor é o próximo responsável.

# P08 — 0404: evidência de fase Runtime offline, gate histórico intacto — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0404](docs/continuidade/checkpoints/0404-p08-runtime-fases-timeouts-offline.md), SHA `B2CD7BF3DF2F79B9653A1B128777683300A4E6EA7BCF5DF2B5A78CE3B5CF000A`. Instrumentação sanitizada nos limites `REFERENCE_IMPORT`, `EXPANSION_START` e `RASTER_APPLY` preserva exceção, rollback e timeouts; batch `JdbcExpansionLaboratory.registerContracts:101` continua interno ao Banco e precisa do patch exato descrito no checkpoint para distinguir o statement. Build Maven offline sem perfil shadow PASS, testes sintéticos Surefire/Failsafe 4/0/0/0 cada, scanner 0 achados e graphify atualizado; trilha de preparação PASS, continuidade histórica FAIL `HANDOFF_PATH`; três FAILs instrumentais da unidade foram corrigidos e logs preservados. Nenhuma IT/SQL/JDBC físico, nenhum diagnóstico causal novo dos sete timeouts. Oito FAILs 0354, 74 classes faltantes, Gate 1/JaCoCo/P08/A-B/P01–P33 abertos, sem alterar histórico, guards, pins ou ledger.

# P08 — 0403: launcher 0402 reconciliado sem efeito novo — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0403](docs/continuidade/checkpoints/0403-p08-launcher-os-0402-reconciliacao-readonly.md), SHA `CAAA6ED1E3C642D437A18000B97CD298C539ED360C02D442C8BFACB0C67CEA1D`. Artefatos 0402 intactos. `Start-Process -Wait` sem timeout e `catch` genérico perderam erro/PID/exit; o transcript confirma interrupção do invocador, mas não recusa UAC nem execução do filho. Única fotografia OS não elevada: serviço/loopback/zero cliente, nenhum helper ativo observado, nenhum recibo tardio; execução histórica indeterminada, imagem não provada. Núcleo privado de classificação e fluxo injetado recusaram 12 negativos cada, sem ativação; spawn UAC ainda requer operador/limite autorizado. Recibo de reconciliação SHA `61DE86AEF5B10A0A055D31547EC498DCF6BE714132520F57FD1B889AFF62B054`. **Zero reserva/elevação/SQL/Maven/JDBC/IT; método 5 sem PASS, 2544 sem baseline.** Supervisor decide unidade nova com acesso UAC/observação de spawn e invocador efetivo provado offline antes de qualquer efeito.

# P08 — 0402: método 5 parado no preflight OS elevado — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0402](docs/continuidade/checkpoints/0402-p08-metodo5-stop-os-elevacao-incerta.md), SHA `325DD66F85E81F00739223F3424EB22964E5DB788273B734818D12B30FE0C64A`. Nova rodada método 5 com espelho 2170/2170, seletor/fixture/guards e prova offline UTC/ScriptDom/argv até `PHYSICAL_INVOCATION_READY` PASS; política de linhas exatas recusou 23 mutantes. Reserva 900 s nova; única tentativa de sonda elevada retornou `ELEVATED_LAUNCH_UNCERTAIN_OR_REFUSED`, sem imagem/assinatura/SHA do processo. Readback OS não elevado `Running/Manual`, PID único/loopback/zero cliente, sem resolver imagem. **STOP_OS: zero SQL, guard físico, Maven/JDBC/IT/XML ou stats PRE/POST**, sem retry; inputs pós 2170/2170 sem drift. Ledger fechado em 217,987 s, recibo SHA `D86C077FE826A96DBEA31A98B82A9FF9A0EBBFD8B9BE723C8EF247C50D998B84`, ledger SHA `FA016C4A69B199255190DBAE73DDF980C81CE344E47FF0F6D8B2F3989D8EDC33`. Método 5 sem PASS; 2544 é histórico, sem baseline. Métodos 1–4 só PASS locais; 6/107 ITs/A-B/P08/Gate 1/JaCoCo abertos. Supervisor decide unidade distinta para sonda elevada, sem reaproveitar reserva/request.

# P08 — 0401: método 4 reclassificado apenas localmente — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0401](docs/continuidade/checkpoints/0401-p08-metodo4-revisao-exact-rows-local.md), SHA `14FB0B79D877FC66A25917CB1BED0385595CEEA6CA641B940900F55F1F194FF4`. Supervisor adotou revisão por conjunto de linhas exatas; quatro outputs 0400 pinados, PREs/POSTs iguais e POST superconjunto exato de PRE: **2538→2544**, seis adições auto de uma coluna sem user/filtro/índice, zero remoções/substituições, fora V105/audit/mart/recon/ctl. Classes não auto, segurança, Flyway, agregados e 064 iguais; mutantes offline **22 recusados**, recibo SHA `74335B6ED5B7A8CE34F8BA3873516EEFF3510BEE604CD93891C14D6A2B26365D`. **`PASS_METHOD4_LOCAL_OBSERVED_WITH_AUTO_STATS_DELTA`** é aditivo: STOP_POST, ledger, XML e outputs 0400 intactos; sem nova execução, causa/instante ou baseline 2544. Métodos 1–4 só PASS locais; P08 agregado/5–6/107 ITs/A-B/Gate 1/JaCoCo abertos. Banco prepara apenas método 5 em rodada nova sob a ordem vigente, condicionada a prova offline integral.

# P08 — 0400: método 4 STOP_POST; Failsafe verde não admite delta stats — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0400](docs/continuidade/checkpoints/0400-p08-metodo4-stop-post-stats.md), SHA `AB35DF0E543EB0FDD73E6EB0BB2865241F31C87F0B0E446DD27A76B9AB41A3F2`. Espelho novo 2170/2170, pins 0383 candidatos, seletor único e prova offline UTC/guards/ScriptDom/argv até `PHYSICAL_INVOCATION_READY` PASS; classificador prospectivo 0399 recusou 21 mutantes. Reserva nova 900 s, OS/PRE SQL master/shadow PASS Flyway 106/105/0, agregados/064, stats PRE integral 2538 somente contemporâneo. Guard 55104 PASS antes de uma Maven offline do método 4; XML Failsafe **1/0/0/0**. POST master/alvo/segurança/contagens/064 iguais, OS posterior PASS, mas stats **2538→2544** em duas leituras iguais: seis linhas auto adicionadas, quatro grupos de coluna existentes receberam linha, dois grupos novos, zero linhas removidas, fora dos recortes V105/audit/mart/recon/ctl. Predicado autorizava no máximo um grupo novo, portanto **STOP_POST sem PASS do método 4**; diagnóstico SHA `1140B1C845DB0E0DD0BCC95D42D6EB2A481410E25B0D15D916982EE97AB65CF9`. Ledger fechado em 153,751 s sem retry, recibo SHA `4BEF0746BEACCA2456A495E5370064963D0B76EAD6D1A9076387A4642C7599AF`, ledger SHA `51A84A37CFB102945730D119C37793E610D38E5AA5B4F4DE903B2B5F2BFAFD2A`. Inputs pós 2170/2170 sem drift; 2544 sem baseline/causa/instante. Métodos 1/2/3 apenas PASS locais; 5/6/P08 agregado, 107 ITs/A-B, Gate 1/JaCoCo abertos. Supervisor decide unidade distinta, sem repetir método 4 ou avançar por suposição.

# P08 — 0399: método 3 PASS local sem delta de stats — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0399](docs/continuidade/checkpoints/0399-p08-metodo3-pass-local-sem-delta-stats.md), SHA `C4F82B793D17103E05756EFA6FA244B15E7E76D43149835AE4BCB4CE16FA07FE`: novo espelho 2170/2170, pins 0383 e seletor único; prova offline UTC/guards/ScriptDom/argv PASS com 16 negativos e classificador prospectivo de stats PASS com 21 mutantes recusados. Reserva nova 900 s, OS/PRE SQL exatos PASS Flyway 106/105/0, agregados/064, stats PRE 2538 somente da rodada. Guard 55104 PASS antes de uma Maven offline, XML Failsafe **1/0/0/0** método 3; POST SQL/OS/contagens/064 iguais, stats **2538→2538** duas leituras iguais. Ledger PASS em 165,826 s, recibo SHA `E13AB24DF55096366B56255CC6E16BB338ECFB67F658E4EB1AF212513F443E51`, ledger SHA `4F35CA408EC044BBAB27224F82F41B260E923B822CEAEB16E0F8787AEA433E23`. PASS apenas local do método 3; métodos 1 e 2 PASS local preservados, STOP 0396 intacto, 2538 sem baseline, métodos 4–6/P08 agregado abertos.

# P08 — 0398: método 2 PASS apenas na evidência local, por revisão aditiva — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0398](docs/continuidade/checkpoints/0398-p08-metodo2-reclassificacao-local-aditiva.md), SHA `23E6DB5D7B4F37B2E9A2FA2098506A58F2B0781BC0D0B94F717A14DEEF020745`. Supervisor adotou o predicado restrito 0397; aplicação offline aos arquivos imutáveis 0396 gerou recibo novo SHA `821123B1BA8480C80382EFDBADB9B695F7E57E850BDD1052EC092CE1A4C88AF0`. Método 2 agora `PASS_METHOD2_LOCAL_OBSERVED_WITH_AUTO_STATS_DELTA`, mantendo o STOP/ledger originais, sem atribuir causa/instante nem usar 2538 como baseline. P08 agregado aberto. Método 3 exige nova prova/rodada física; sem repetição de métodos 1/2.

# P08 — 0397: classificação offline do delta 0396; proposta sem aceite — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0397](docs/continuidade/checkpoints/0397-p08-metodo2-delta-stats-classificado-offline.md), SHA `ACFCB56423E4452AA4E401FEC70C914F41AD448530A191BD30D0B1BF46D1F829`: mesmos inventários e SQL/escopo, quatro hashes pinados, dez campos/linha e unicidade. PRE 2537 idêntico em duas leituras; POST 2538 idêntico em duas leituras. Um grupo de coluna adicionado, zero removidos/modificados; auto criado, não filtrado/indexado, fora das quatro colunas V105 e de audit/mart/recon/ctl. Classificador privado recusou 19 negativos; recibo SHA `E7F12AB36A5C5AE13F61AA6708F7A0602DF80B85C32669866B23AA8B0AEDD088`. Proposta de predicado restrito permite revisão do resultado físico local com os recibos existentes, **sem atribuir causa/instante, sem alterar STOP_POST 0396, sem PASS/checkbox do método 2 nem baseline 2538**. Supervisor decide adoção; nenhuma reserva/OS/SQL/Maven/JDBC/IT nova. Quatro métodos restantes/P08 abertos.

# P08 — 0396: método 2 sem PASS por delta POST stats — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0396](docs/continuidade/checkpoints/0396-p08-metodo2-failsafe-pass-post-stats-delta.md), SHA `B74A0BDE296B0EA53FB602CB4EC52592DEC9B3700A64A0E1ED49117598743A97`: método 2 único, espelho novo 2170/2170, pins 0383/guards/seletor/argv e request/reserva novos com prova offline duas culturas/16 negativos PASS. Reserva nova 900 s, OS elevado e PRE SQL master/shadow PASS Flyway 106/105/0, agregados/064, stats 2537 histórico só referência da rodada. Guard 55104 PASS antes da única IT; Maven/Failsafe **1 teste/0 falha**. POST recusou `STATS_0390_SNAPSHOT_MISMATCH`: outputs já capturados comparados offline revelaram **2537→2538**, uma linha stats adicionada/zero removidas; master/alvo/guards/agregados/064 sem delta, OS PASS. Sem POST receipt e sem PASS do método 2; 2538 não é baseline. Diagnóstico inicial de booleanos preservado e corrigido aditivamente, recibo corrigido SHA `694D595D8DDD632F7455156578D372AB2A8836B0CD7F70663AE6DB583C34FD2B`. Ledger STOP_POST em 206,527 s, recibo SHA `FA081B0328F076F26627B07333E28620AA016F96DA07DE2DD65DB469CE634A26`, ledger SHA `80ED9B864FE183C63904A9E27715D585D08A8FC964AE7C7E298F797E5DB3A7A2`. Método 1 PASS local 0395 preservado; quatro restantes e P08 abertos. Supervisor decide investigação distinta; não repetir método 2.

# P08 — 0395: método 1 PASS local, P08 aberto — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0395](docs/continuidade/checkpoints/0395-p08-metodo1-it-local-pass-rollback-readback.md), SHA `C3C46A5108CA132DC8F0B7ACC1C522C58A5F9D697AEEBC6AAF1B2F87653228B7`: parsers privados UTC compartilhados; espelho novo 2170/2170 e prova offline integral do request/reserva congelados em duas culturas/16 negativos, pins 0383/guards/argv/seletor PASS. Reserva nova 900 s, OS elevado PASS, PRE SQL master/shadow PASS Flyway 106/105/0, agregados/064 e stats 2537 iguais ao 0390 só como referência da rodada. Guard 55104 PASS antes da única IT; Maven exit 0, Failsafe 1 teste/0 erro/falha/skip. POST SQL/OS e inventário stats integral idêntico ao PRE; rollback inferido do fechamento pinado e readback agregado, sem recibo JDBC direto. Verificação SHA `969A39BCC2F0D48C01AEEE2C78AA30CED8DCDEC3D1B265D05DB6008A348321DA`; ledger fechado em 309,561 s, recibo SHA `321C8AC6B331A9E635DEB5E8865CCC9CED49087EC79B80C1977417ECE9190662`, ledger SHA `B697CA945B8B58CEF8F4877AEC9183F893B5713FFBCA60A0F7B300C6F1466D4D`. PASS só do método 1 local; P08/gates/cinco métodos restantes abertos, FAILs e 8 erros/74 classes preservados. Supervisor avalia; não repetir método 1.

# P08 — 0394: PRE UTC offline PASS; runner UTC offline FAIL — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0394](docs/continuidade/checkpoints/0394-p08-pre-utc-pass-runner-utc-offline-fail.md), SHA `A43DF21269BA56A85BECCF7ED1BB19938EA7B7C6E4F8391875B6EBBDE1B24D0A`: espelho novo 2170/2170, pins 0383/guards/seletor/argv/ScriptDom160 PASS. Request OS final congelado, mesma precondição offline PASS com seis negativos; parser UTC corrigido apenas no PRE privado passou em duas culturas e recusou seis negativos. Reserva candidata permaneceu offline, **sem ledger físico**. Prova da linha efetiva do runner Maven reproduziu falha `DateTime` localizado → `DateTimeOffset.Parse`; gate completo FAIL e parada antes de OS/SQL/Maven/IT/stats PRE/POST. Recibo sanitizado SHA `DCF1CACE4F133AD2D09D4124C5DA078401DC5ACBE070D60E5F0733C77C3B10C3`. P08/método 1 abertos; 2537 só histórico. Próxima unidade requer escopo para corrigir também o parser privado do runner, prova offline completa e reserva nova.

# P08 — 0393: request OS congelado PASS; PRE recusou antes de SQL — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0393](docs/continuidade/checkpoints/0393-p08-request-congelado-pass-snapshot-data-parse-fail.md), SHA `9B0D62377CA0C991A5CB414B460961E740A349BD0FFE3325A8F25BA10A86D614`: espelho novo 2170/2170, request final gerado uma vez e congelado SHA `23A173FCE611C39C89549600F54F69659977814C195DB9D7BFE75AA8D4678937`. A mesma rotina do helper validou offline o arquivo (`PRECONDITION_VALID`) e recusou seis negativos. Reserva física nova de 900 s; única sonda elevada OS PASS para imagem pinada/assinada, PID único, serviço `Running/Manual`, somente loopback e zero cliente. O primeiro PRE falhou **antes de sqlcmd** por conversão automática de `deadlineUtc` pelo `ConvertFrom-Json` sem `-DateKind String` e parse localizado posterior. Sem retry: **zero SQL/guard 55104/Maven/JDBC/IT/stats PRE/POST**; readback OS posterior PASS. Recibo parcial/ledger originais preservados e correção aditiva de duração para **92,09 s**: recibo final SHA `B03E835BA45A56E9F5F9745E46CF29B3E6C2811CE7423EFE9DFFEFA9BE668096`, ledger SHA `67DC0BD4A5C6D685CEB958EBE120D55061EE21F16501A65E9AE6A373D79F7CF5`. P08 e método 1 abertos; 2537 segue histórico, sem baseline/PRE. Próxima unidade requer prova offline do parser UTC antes de nova reserva.

# P08 — 0392: método 1 recusado antes de OS/SQL — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0392](docs/continuidade/checkpoints/0392-p08-metodo1-preflight-elevado-recusado-sem-sql.md): espelho novo 2170/2170 sem `target`/`.env`, pins candidatos 0383 e seletor/argv/guards PASS offline; primeira falha sintática do teste preservada. Reserva nova 900 s. Uma chamada elevada recusou no estágio `PRECONDITION`: recibo offline escreveu `osProbeSha256`, helper esperava `probeSha256`. Sem retry, nenhuma consulta OS interna comprovada, **zero SQL/guard/Maven/JDBC/IT/stats PRE/POST**; readback OS independente Running/Manual, loopback/zero cliente. Recibo SHA `C16A4DD4B945DAC4339F31DBF90B4B83228460D755D9B70C37633688F8233344`, ledger fechado SHA `34031614449463A24F1BD4AE25FF5086E4DEAEDC380B64725217995B1A487C5E`, 122,176 s. 2537 permanece histórico, P08 aberto. Próximo gate requer novo contrato offline com negativo de campo ausente, reserva nova e decisão do Supervisor.

# P08 — 0391: tipo do delta de stats classificado offline — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0391](docs/continuidade/checkpoints/0391-p08-stats-delta-classificado-offline.md): 12 inventários históricos individuais de 0376/0380/0378 idênticos, 2536 grupos; output 0390 comparável com 2537. Um grupo adicionado, zero removidos/modificados; adicional automático, sem filtro/índice, fora das colunas bloqueadoras V105 e de tabela com nome de auditoria. Seis negativos offline PASS; recibo SHA `484BE59C86E6EC27541BD3835658BA24625DCF129A8B6617E0AFC4BDB35AEB9C`. Inventário individual estava disponível, então zero novo SQL/OS/reserva/ledger/IT. Causa e estado atual não provados; 2537 sem baseline aceita, P08 aberto.

# P08 — 0390: guard master revisto PASS; stats observacionais +1 — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0390](docs/continuidade/checkpoints/0390-p08-master-target-guard-revisado-stats-delta.md): regra P08-MASTER-TARGET-01 removeu apenas a existência de outro banco em 55804 na cópia privada; 55801–55803 e guard shadow 55811–55814 intactos. Diff exato, ScriptDom160, argv/sanitização e sete mutantes PASS offline; primeiro FAIL de sintaxe do teste preservado. Reserva nova 600 s; OS elevado PASS. Uma sequência read-only `sqlcmd -E -C` em master/shadow explícitos passou segurança, Flyway 106/105/0, agregados e 064, mas **parou em STATS_OBSERVATION_DELTA: 2537 atuais ante 2536 históricos**, sem novo baseline/retry/IT. Readback OS PASS; recibo SHA `94FB492683467BFB6892C966ED535248142DD09E19D9469667CA2ED471689525`, ledger SHA `85F7A3325517CD0DA98F71C50D5F1B5D3B6D19DB632391BC66F2BAB6E9DC2EAA`, 111,338 s. Nenhuma conexão aos cinco outros bancos, portanto grants do reader neles não foram excluídos. P08 aberto; Supervisor decide próximo gate em unidade/reserva próprias.

# P08 — 0389: diagnóstico 55804 separou predicados no master — 30/09/2026

[STATES.md](STATES.md) e [checkpoint 0389](docs/continuidade/checkpoints/0389-p08-diagnostico-55804-master-read-only.md): ScriptDom160/escopo/argv/marcadores e negativos PASS offline; FAIL inicial por caminho de parser ausente preservado antes da reserva. Reserva nova 600 s. Sonda OS elevada única PASS imagem assinada/hash pinado, PID/loopback/zero cliente. Uma chamada read-only `sqlcmd -E -C` no `master` retornou alvo ONLINE=1, **cinco outros bancos de usuário online**, zero offline/outros estados, zero outras sessões no shadow e zero flags agregadas de request/transação. Só o predicado “nenhum outro banco de usuário” foi falso **nesse snapshot**, sem provar grants do reader ou ramo histórico 0388. Zero conexão shadow, nenhum guard composto/IT/retry; readback OS posterior PASS. Recibo SHA `E86495290C789255CEDD73B54433E87DD602DA87B66F673F4AA6951F578281CE`; ledger SHA `4B3932A1C1195D7EFAA7C9C2E781E34C97D2ED6FBC52540296FEC0DAFA846E9B`, 90,398 s. P08 aberto, stats 2536 históricos e FAILs/74 classes preservados. Supervisor decide gate novo para privileges do reader e consumidor/transação intacto.

# P08 — 0388: OS elevado PASS; SQL master recusou 55804 — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0388](docs/continuidade/checkpoints/0388-p08-os-elevado-pass-guard-master-55804.md): prova offline parser/argv/helper, sonda Win32 no próprio processo, quatro negativos, hash/assinatura PASS. Reserva nova 600 s; uma leitura OS elevada comprovou processo do `MSSQLSERVER` vinculado à imagem instalada Microsoft assinada e SHA pinado, `Running/Manual`, listeners só loopback e zero cliente. Uma chamada SQL não elevada em `lpc:localhost/master` recusou **55804 `TARGET_OR_CLIENT_GUARD_MISMATCH`**; ramo exato desconhecido, zero chamada ao shadow e nenhum marcador SQL aceito. Readback OS posterior manteve PID/loopback/zero cliente. Recibo SHA `1926C14210377CC4AEA545792FF541E212FC01EC3C649BA60BA353E6422483A3`, ledger SHA `8A0B8A1BAF91BEAB60250FEDEC17416DB7D3BE70C0BCA7D8C17037F50D4DF311`, **134,182 s**, sem retry. P08 aberto, stats 2536 históricos, FAILs e 74 classes faltantes preservados. Próximo gate distinto precisa separar predicados 55804 sem relaxar consumidor/transação.

# P08 — 0387: sonda Win32 recusada antes de reserva/SQL — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0387](docs/continuidade/checkpoints/0387-p08-sonda-imagem-os-acesso-negado-sem-sql.md): sonda privada `OpenProcess(0x1000)`/`QueryFullProcessImageNameW` sem elevação/depuração passou no próprio processo e vinculou PID único ao `MSSQLSERVER`, mas a abertura limitada do processo do serviço foi recusada pelo Windows com código 5. Parser e quatro casos negativos de fail-closed PASS; prova positiva de caminho/hash/assinatura da imagem carregada ausente. Parada **antes de reserva/SQL**, zero `sqlcmd`/output e nenhum efeito no serviço. Recibo SHA `08C35D76DA447E5B20C7154FCE24CDF1D9D12CE520A0DD3B148AB0CCD0103719`; nenhum ledger físico novo. P08/master/shadow não revalidados, stats 2536 históricos, FAILs e 74 classes faltantes preservados. Supervisor decide acesso/contexto permitido para a prova OS em unidade futura; caminho configurado do serviço não substitui a imagem do processo.

# P08 — 0386: readback SQL parou no preflight OS — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0386](docs/continuidade/checkpoints/0386-p08-readback-sql-preflight-os-identidade-nao-provada.md): cópia privada do invocador corrigiu `$input` para `SqlInputFile`; parser/argv 2 positivos e 5 negativos PASS offline, preservando FAIL 0385. Reserva read-only nova 600 s com impacto/recuperação; preflight OS confirmou serviço `Running/Manual`, PID único, loopback e zero cliente TCP, porém **não provou a identidade do executável do processo**. Guard recusou antes de `sqlcmd`: zero SQL/outputs, sem retry ou efeito no serviço. Ledger SHA `49BEEB3B544BF39443A00E44A71BB856188BFA656128AF5A618FCE322F0C5D2E`, recibo SHA `3C278F0208BADF4ED7E149E133D67B7C86E78D88BBF736933BE21CBBD80B62F4`, duração corrigida aditivamente em 128,677 s. P08, IT e readback de master/shadow continuam abertos; 2536 stats apenas históricos, FAILs 0354/0378–0380 e 74 classes faltantes preservados. Reabertura exige prova OS de imagem do processo e nova reserva, sem inferir pela configuração do serviço.

# P08 — 0385: start local PASS no OS; readback SQL congelado antes de `sqlcmd` — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0385](docs/continuidade/checkpoints/0385-p08-servico-local-iniciado-sql-readback-congelado.md): preflight do `MSSQLSERVER` `Stopped/Manual`, binário Microsoft assinado, modo 2 no registro e TCP somente loopback PASS; reserva nova 600 s. Um único `Start-Service` elevado retornou 0. Dois readbacks OS registraram `Running` com PID e listeners apenas `::1`/`127.0.0.1`, sem outro servidor/cliente TCP. O primeiro readback SQL falhou no invocador privado por `SqlFile` vazio **antes de chamar `sqlcmd`**; nenhuma propriedade de `master`/shadow foi revalidada, sem retry ou reversão automática. Recibo SHA `994ADB76940F5F55D28AFB40AD62C82C04CC2B06019722E34BE417BE0FF01B17`, ledger fechado SHA `4AB2F1BEC2899D7DE6638EB329D5FEA3843C7F27911EA51D9B16E404C6207A65`. Zero SQL, JDBC/IT/Maven, DDL/DML/Flyway ou restart; stats 2536 somente históricas. Gate 1/P08 e FAILs preservados. Próximo gate SQL read-only exige unidade/reserva próprias e correção offline do invocador; start do serviço não se repete.

# P08 — 0384: método 1 parou no preflight com serviço local parado — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0384](docs/continuidade/checkpoints/0384-p08-metodo1-preflight-servico-parado.md): nova unidade de 900 s para somente o método 1 de 0377. Pins candidatos A/B 0383 e espelho novo **2170/2170** passaram offline; guard privado 55104 preservou SHA 0380. Reserva foi feita antes de SQL. O único preflight recusou `SERVICE_NOT_RUNNING` antes de `sqlcmd`; readback OS confirmou `MSSQLSERVER` **Stopped**, sem PID/listener, saída SQL ou processo próprio. **Zero SQL, guard, Maven/JDBC/DLL/IT**, sem retry/start/restart. Recibo SHA `C26CD9A408A128DD342848110404C0C7163ED62A1F0934E309397CA2422F40A8`; ledger fechado SHA `297474EC35492C0ADF45A73E4F70FE0B2CC0EE7B5E98967D4F2FCBA230A0FF0E`. Stats 2536 não medidas nesta rodada e seguem históricas/observacionais; FAILs 0354/0378–0380, oito erros/74 classes faltantes, Gate 1/P08 abertos. Supervisor decide nova unidade quando a pré-condição de serviço mudar ou houver autoridade específica para intervenção.
# P08 — 0383: candidatos PackageShadow A/B offline repinados — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0383](docs/continuidade/checkpoints/0383-p08-package-shadow-ab-pwsh-portatil-offline.md): PowerShell 7.5.11 portátil em diretório novo fora do repositório foi baixado uma vez da release oficial, verificado por SHA-256 pinado e assinatura Microsoft válida antes de executar por caminho absoluto. Inventário 0382 **2170/2170** estável; builds A/B Maven offline copiaram **4135 entradas iguais**, Spotless/Checkstyle ativos, `inputIntegrity=true`; `-DskipTests` somente na fase de pacote conforme ordem corrigida, com `clean verify` 0381 como prova separada. Candidatos offline A/B têm revision `16B013BF258A76E54E451CDF0039F598EECF3D23D1D8C8246AE86F93F119E1EB`, manifest `576C60B115DEA15D3B4C4D1271780500DD54A8271C873A648B37222A5EEAC171`, ZIP `64A01528AF95F209D662598E7C02D135FB7B6C8E44A240779DD2CD99FC912CC6`, 188 membros e payload/extração/2.170 hashes validados. Scanner e preparação P01–P33 PASS; guardas pacote/schema/extraído PASS 27/4/22. Primeiro guard extraído FAIL com Java 25 foi preservado e explicado; nova tentativa com JDK 17 no PATH apenas do processo passou. Validadores históricos de continuidade/chat-trail mantêm `HANDOFF_PATH` FAIL. Recibo SHA `5AD2DEC505800333AFC600696256C4718332BC2C00D28B7C826D1F0879031163`. Nenhum fonte editado, SQL/JDBC/DLL executada, IT física ou rede de fonte. **PACKAGED_NOT_SMOKE_QUALIFIED**; JaCoCo shadow/Gate 1/P08/P01–P33 abertos, 0354 e demais FAILs preservados.

# P08 — 0382: PackageShadow A/B não produzido; PS7.5 ausente — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0382](docs/continuidade/checkpoints/0382-p08-package-shadow-pwsh-ausente-offline.md): inventário de 2170 insumos atuais congelado, com somente dois hashes diferentes de 0374, ambos testes Runtime de 0381. A revision `16B013BF258A76E54E451CDF0039F598EECF3D23D1D8C8246AE86F93F119E1EB` é cálculo offline, **não pin validado**. O ambiente tem PowerShell 5.1 e não tem `pwsh.exe`; build, pacote e módulo exigem `#Requires -Version 7.5`. O ramo `PackageShadow` também contém `-DskipTests`, contrário ao pedido sem skip. Assim, **zero package A/B**, zero novos manifest/ZIP/payload/extração e nenhuma comparação destes artefatos; os pins 0374 não foram substituídos. Scanner 0381 continua ERROR/FAIL e trilha PS7 não executada, sem PASS. Recibo privado SHA `181C4EBACC137A6CBF3F1CFA09F9C03B9804A314B0FCCE9E7A00785F5C2C8A54`. Nenhum efeito físico, instalação ou mudança de código; FAILs históricos, Gate 1/P08/P01–P33 preservados abertos.

# P08 — 0381: JaCoCo shadow classificado offline; Gate 1 aberto — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0381](docs/continuidade/checkpoints/0381-p08-jacoco-shadow-classificacao-offline.md): XML 0351 contra regras shadow atuais identificou `expansao` (+82 linhas/+33 branches), `relacional` (+126/+33), 42/53 classes abaixo dos limites e 29 sem linha coberta. A seleção de uma IT em 0351 não exercita o escopo do check; 0354 parou após 33/107 classes, antes de JaCoCo. Dois ajustes estreitos em testes Runtime removeram impedimentos da execução **offline sem perfil**: junction via `powershell.exe` e retomada sintética direta pelo Supervisor, mantendo CLI `status` e opt-in SQL. O `clean verify` final JDK17 offline passou com 2368 Surefire/5 skips, 6 Failsafe offline e JaCoCo base; aplicação aritmética das regras shadow ao XML desse build recusa 3 pacotes e 53/53 classes. Inventário candidato A/B 0374: 2170 entradas, somente os dois testes tocados diferem agora; pins antigos exigem requalificação offline. Scanner sob Windows PowerShell 5.1 deu ERROR e PowerShell 7 ausente impediu validadores de trilha; nenhum PASS dessas camadas. **Não é PASS shadow, Gate 1 ou P08**; FAILs históricos/estatísticas e A/B físico continuam abertos. Zero SQL/JDBC/Flyway/rede/efeito físico nesta unidade.

# P08 — 0380: guard recusa request interna BACKGROUND/PAGE — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0380](docs/continuidade/checkpoints/0380-p08-guard-request-sistema-page-locks.md) registram a unidade distinta pós-0379. Guard privado fail-closed com detalhe categórico passou ScriptDom, diff, sanitização e 67 casos sintéticos; espelho novo 2170/2170, pins 0374 candidatos e preflight local PASS. O guard recusou SQL 55104 para uma request de sistema `BACKGROUND`, espera família `PAGE`, duração 1–10 s e locks compartilhados 3+ no shadow; comando/espera exatos ficaram `UNKNOWN`. **Zero Maven/IT**, sem retry; readback estável nos recortes medidos, 2536 stats só observacionais. P08/Gate 1/JaCoCo/A-B, oito erros históricos e 74 classes faltantes seguem abertos. Supervisor decide eventual gate novo; nenhuma tarefa interna ou causa histórica foi inferida.
# P08 — 0379: guard instrumentado recusou request de sistema — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0379](docs/continuidade/checkpoints/0379-p08-guard-instrumentado-request-sistema.md) registram a unidade pós-0378. Prova offline privada de 30 casos passou; espelho novo 2170/2170, pins 0374 candidatos e preflight local PASS. O guard preservou a recusa de request ativa e retornou SQL 55104 com flags limitadas de uma request de sistema vinculada ao shadow; **zero Maven/IT**, sem retry. Readback Flyway/dados/schema/064/socket/stats estável nos recortes medidos, stats 2536 apenas observacionais. P08/Gate 1/JaCoCo/A-B, oito erros históricos e 74 classes faltantes seguem abertos; Supervisor decide eventual gate distinto.
# P08 — 0378: seis esperas interrompidas no guard do primeiro método — 29/09/2026

[STATES.md](STATES.md) e [checkpoint 0378](docs/continuidade/checkpoints/0378-p08-seis-esperas-guard-request-active.md) registram a retomada explícita após 0377. Espelho novo 2170/2170 e pins 0374 candidatos, reserva/preflight local PASS. Guard anterior ao Maven recusou SQL 55104 `REQUEST_ACTIVE`; **0 Maven/0 IT**, método 1 sem execução e métodos 2–6 não iniciados, sem retry. Readback master/shadow/Flyway/dados/schema/064/socket/stats sem delta nos recortes; 2536 stats permanecem somente observacionais. P08, Gate 1, JaCoCo e A/B seguem abertos, com oito erros históricos e 74 classes faltantes preservados. Supervisor revisa a recusa antes de nova autoridade física; Banco continua único executor SQL/JDBC/ledger.
# SQL auth reader — gates ADO.NET locais PASS; VS Code UI não testada — 29/09/2026

[Checkpoint 0363](docs/continuidade/checkpoints/0363-sql-auth-reader-ado-gates-pass-vscode-pendente.md). Preflight `sqlcmd -E -C` master/shadow, OS/DPAPI/loopback/zero consumidores PASS; autenticação TCP reader e identidade exata PASS. SELECT/VIEW DEFINITION concedidos, UPDATE negado SQL 229 com rollback 0→0, leitura `msdb.dbo.sysjobs` negada SQL 229 em gates separados. Readback externo master/shadow/OS sem delta observado. Ledger SHA `44F0585104856620E54FE97201B36B7813055EF7415906BCE62F17A55B7CFE3C`. UI do VS Code não exercitada; FAILs 0360–0362 e P08/2536 stats separados.

# SQL auth VS Code — 0362 preflight certificado FAIL; nenhuma SQL auth — 29/09/2026

[Checkpoint 0362](docs/continuidade/checkpoints/0362-sql-auth-preflight-cert-fail-sem-tentativa.md). Self-test offline de cadeia tipada de exceções e DPAPI no mesmo usuário PASS v2, primeiro FAIL preservado. OS preflight PASS; `sqlcmd` master falhou antes de marcador por ODBC 18/cadeia de certificado não confiável, porque a chamada omitiu `-C`; sem retry. Readback só OS estável. Nenhuma prova cliente SQL auth ou permissão foi alcançada; ledger SHA `CEE4866260F0E803CE5063DAFA4F5E14CB739AA365967090E3E11C27926B6B96`. Supervisor decide novo gate; P08 separado.

# SQL auth VS Code — cliente 0361 FAIL; prova de acesso ainda aberta — 29/09/2026

[Checkpoint 0361](docs/continuidade/checkpoints/0361-sql-auth-cliente-fail-wrapped-sem-delta.md). Preflight local Windows/OS e teste offline do builder PASS; uma tentativa SQL auth por TCP saiu 1 na abertura/SELECT com `MethodInvocationException`, sem código SQL interno capturado. UPDATE/rollback e isolamento de outro banco não foram executados. Readback master/shadow/OS sem delta observado; ledger SHA `9EC4390CF19FC151782BB9DAAA2573D735FFDD3429E387811D98BF02B7321F69`. Login/DPAPI seguem provisionados, mas conector não qualificado. Sem retry; Supervisor decide novo gate diagnóstico. FAIL 0360 e P08/2536 stats preservados.

# SQL auth VS Code — principal criado, teste de cliente não qualificado — 29/09/2026

[Checkpoint 0360](docs/continuidade/checkpoints/0360-sql-auth-reader-criado-prova-cliente-fail.md). Preflight shadow zero user sessions/transações, uma sessão `SQLServerCEIP` fora do alvo. Uma mudança de modo/um restart PASS, Windows auth/loopback/`sa` disabled preservados; login `etl_shadow_reader`, user do shadow, `db_datareader`, `VIEW DEFINITION` e DPAPI restrito criados. Teste local SQL auth **FAIL antes de conexão comprovada** por `System.ArgumentException` do builder PowerShell 5.1; protótipo offline corrigido, sem retry. Readback Windows/catalog/contagens sem delta. Ledger SHA `6B6DBCB8F725A310A5BD33E7019EB27A0965ADC618B8474626634896D8FB884E`. P08 não promovido.

# SQL auth VS Code — SPID 73 ausente no master, sem prova de zero usuários — 29/09/2026

[Checkpoint 0359](docs/continuidade/checkpoints/0359-spid73-ausente-master-minimo.md). Dois gates read-only pontuais em `master` responderam com marcadores antes/depois e exit 0: SPID 73 ausente de `dm_exec_sessions` e `dm_tran_session_transactions`. Sem inferir que a sessão era interna nem que não há outros consumidores; gate SQL auth não aberto. Readback OS PID 20404/loopback/zero TCP cliente; sem shadow, restart ou login. Ledger SHA `9257F50BE06694FE598035B7C74DA31C9C1DBFD7E94E7FF2EAC241315E5F2AE7`. P08/0354 FAIL preservado.

# SQL auth VS Code — DMV 0358 expirou sem marcador; classificação pendente — 29/09/2026

[Checkpoint 0358](docs/continuidade/checkpoints/0358-spid73-dmv-timeout-sem-auth.md). `master` local Windows auth PASS, mas a única consulta de metadados no shadow expirou sem marcador, mesmo com exit 0 do `sqlcmd`. `is_user_process` da SPID 73 e zero consumidores **não comprovados**; readback OS preserva PID 20404/listeners loopback/zero cliente TCP. Sem retry ou efeito SQL auth. Ledger SHA `B8B7FBA237C30B86976879B60D017D06F7CE92FB5237E8B0D8DAF53F883FA0F6`; P08 permanece independente.

# SQL auth VS Code — consumidor SPID 73 ativo, sem efeito — 29/09/2026

[Checkpoint 0357](docs/continuidade/checkpoints/0357-spid73-transacao-ativa-parada.md). Sonda curta `master` PASS com marcador; DMV shadow encontrou **SPID 73/`sa`/sleeping/1 transação aberta**, host/programa/PID nulos, sem vínculo provado com 0354 ou VS Code. Parada sem KILL ou cliente próprio a fechar. Serviço/socket loopback estáveis; memória baixa e evento SQL 10311 próximos do timeout 0356 são indício, não causa provada. **Zero consumidores falso, SQL auth não configurada.** Ledger SHA `3C0B4BA747A9C5B0DB4D8FA4B1BDBC3236ACA930150A5420B3C24231C03E6598`; P08/0354 FAIL preservado.

# SQL auth VS Code — diagnóstico 0356 parou no master — 29/09/2026

[Checkpoint 0356](docs/continuidade/checkpoints/0356-consumidores-master-timeout-sem-sql.md): nova reserva de diagnóstico, mas `master` devolveu `Timeout expired` sem marcador; nenhum SPID/cliente/transação classificado e zero consumidores não provado. Serviço PID 20404 continua Running, dois listeners loopback, zero sockets TCP cliente locais; sessões Shared memory seguem possíveis. Sem SQL de alvo, retry, KILL ou mudança de autenticação. Ledger SHA `D3C3C96196B02D81527003EF61601AE0D63D12C92F1FDCC1E4F42966AAE43FAF`. Supervisor decide nova sonda; P08 0354 FAIL preservado.

# SQL auth VS Code — preflight 54913, nenhuma alteração — 29/09/2026

[Checkpoint 0355](docs/continuidade/checkpoints/0355-sql-auth-preflight-consumers-stop.md): `master` confirmou Windows-only/registro 1, `sa` desabilitado e login dedicado ausente; alvo recusou preflight por `SHADOW_CONSUMERS_PRESENT`. Parada sem retry ou efeito: nenhuma senha/DPAPI, principal, mudança de modo ou reinício. Supervisor deve decidir nova unidade com zero consumidores. Gate P08 0354 FAIL e stats 2536 não aceitas como baseline permanecem.

# P08 — gate 107 ITs FAIL com readback reconciliado — 29/09/2026

[Checkpoint 0354](docs/continuidade/checkpoints/0354-p08-107-it-gate-fail-readback.md): espelho novo/pins/CSV 108→107 com exclusão única conferidos. Surefire 2368/0/0/5; Failsafe 33 classes, 123 testes/8 erros, interrompido sem retry; JaCoCo não alcançado. Dados/schema/histórico/064/socket sem delta; stats 2448→2536 por 88 grupos automáticos sem filtro/índice e sem coluna V105, ainda **não aceitos como baseline**. Gate físico FAIL, P08 e smoke A/B abertos. Nova solicitação de acesso SQL local somente leitura segue como unidade separada depois da reconciliação.

# P08 — decisão 0353: 2448 stats observacionais, Gate 1 aberto — 29/09/2026

Supervisor aceitou **2448 grupos**/SHA `E92AA86CDDAB2B7ADB918F6F1310AA2D86D7702962999E12FA134B15E81DB2E0` e 064 pós/SHA `78F6F2664AD673D815D01C7B281DF2D606AF8DE419FAE1A9BC48816582E6D2E3` apenas para novo preflight. Permanecem 2235/064 pré e **FAIL estrito 0351**; Gate 1/P08 abertos, A/B físico pendente. Futuro DDL em `ctl.execution_audit.failure_category` requer novo guard, autorização e recuperação. [Checkpoint 0353](docs/continuidade/checkpoints/0353-p08-stats-2448-decisao-observacional.md). Sem efeito físico nesta unidade; Runtime inventaria elegibilidade das ITs.

# P08 — classificação offline de stats pós-0351; Gate 1 aberto — 29/09/2026

Banco confrontou os dumps brutos: 2235→2448 grupos, 213 novos automáticos sem filtro/índice em 38 tabelas, zero removidos/modificados; um em `ctl.execution_audit.failure_category`. Só o contador `V105_STATISTICS_BLOCKERS` do 064 mudou 1→2; dados/schema/histórico/socket estáveis e inventário pós-064 idêntico. V105 tinha guarda pré-DDL, cumprida; futura ALTER dessa coluna precisa plano novo. Proposta de 2448/064 pós apenas como ponto observacional, pendente de decisão Supervisor, preservando FAIL Maven/064 0351, Gate 1/P08 abertos e A/B parado. Evidências e limites: STATES/[checkpoint 0352](docs/continuidade/checkpoints/0352-p08-stats-2448-v105-revisao-offline.md). Nenhum SQL/JDBC/IT/Flyway/smoke/metadata nesta unidade.

# P08 — Maven no espelho parou no Surefire — 29/09/2026

[Checkpoint 0349](docs/continuidade/checkpoints/0349-p08-fullclass-mirror-surefire-fail.md). Espelho novo sem `target`, pins 0345-runtime-fix e 2170 fontes sem drift; preflight/reserva shadow local 106/105/0, zero consumidores, loopback, `064`/contagens e 2235 stats estáveis. Chamada única Maven JDK17 offline classe completa com Surefire: **2368/2 failures/0 errors/5 skips**, exit 1 por SHA antiga de `AnalyticScenarioRuntime` no escopo de cobertura e teste de migrations limitado a V104 ante V105. Failsafe **não iniciou** (0 XML/0 recibos), JaCoCo checks não alcançados; oito cenários sem prova. Readback externo master/alvo/dados/schema/histórico/`064`/stats/socket sem delta, 2235→2235. Recibo SHA `1D3D080851134A14EB8349495835C24194979848043100207B45E549DD9F2845`; **gate FAIL**, sem retry/smoke. Supervisor coordena revisão offline; FAILs anteriores, Gate 1/A/B/P08 e backup 0325 preservados.

# P08 — 2235 stats: ponto observacional, gate ainda aberto — 29/09/2026

[Checkpoint 0348](docs/continuidade/checkpoints/0348-p08-stats-2235-observacional-offline.md). Comparação offline 0347, critérios 0343: 1088→2235 grupos, +1147 automáticos (`auto_created=1`, sem manual/filtro/índice) em 79 tabelas, zero removidos/contagens modificadas, zero nas quatro colunas V105. `064`, domínio/auditoria, schema, histórico e recibo de sockets iguais; stats pós-`064` estáveis. Script PS7 offline exit 0 e recibo SHA `2AEC08F37D3B711370D05FE7BC30A2E4AC519478F8AFF7F8507C25495C2CDF07`. Supervisor aceitou 2235/SHA `178325C1E554AC608F6469D6FA908AB2343A9CD35DDA7F199BE3CBBB18D7B17C` **só para preflight futuro**; FAIL estrito 0347, 873→1088/215 de 0342, JaCoCo e Gate 1/A/B/P08 abertos. Readback incremental por cenário proposto, dependente de seletor individual Runtime e nova autoridade/reserva. Nenhum SQL/JDBC/IT/Flyway/smoke nesta unidade.

# P08 — diagnóstico único após binding; autoestatísticas retêm gate — 29/09/2026

[Checkpoint 0347](docs/continuidade/checkpoints/0347-p08-diagnostico-binding-autoestatisticas.md). Revisão independente do SQL Runtime vs PK/FK/triggers V024 e sessão rollback não achou violação; pins Runtime/teste/IT e PackageShadow A/B novos conferidos, 2170 fontes sem drift. Preflight/reserva novos no shadow exato: 106/105/0 Flyway, zero consumidores, loopback, `064`, contagens e 1088 grupos stats. Método diagnóstico selecionado: Failsafe **1/0 failure/0 skip**, recibo `PASS_LOCAL`, rollback/before=after, sem 51301; Maven exit 1 por JaCoCo cobertura após IT. Readback externo igual em dados/histórico/objetos/principals/`064`/socket, mas stats globais **1088→2235, +1147 grupos auto/sem filtro/sem índice em 79 tabelas**; zero remoções, estado pós estável. Readback estrito FAIL e parada, sem retry ou nova baseline aceita. FAILs 0342/0345 e histórico 873→1088 preservados; Gate 1, A/B físico e P08 abertos. Supervisor decide. Sem catalog-only, outras ITs, smoke, DDL/Flyway/restore.

# P08 — predicados 51301 revisados offline; estado físico ainda incerto — 29/09/2026

[Checkpoint 0346](docs/continuidade/checkpoints/0346-p08-51301-predicados-revisao-offline.md). V024: com catálogo existente, `active=0` ou binding do kind pedido ausente causa `51301`. O binder sintético anterior à captura tem outro `THROW 51301/SOURCE_PROTOCOL_NOT_REGISTERED` quando não há catálogo ativo com `GRAPHQL`; o recibo 0345 não contém módulo/linha nem flags transacionais. Fluxo estático usuários `GRAPHQL` → segundo binding `DATA_EXPORT` → cotações favorece V024 como origem do wrapper, sem distinguir ramo. Baseline/063/056/064 e readbacks não fotografam `active`/bindings; gate catalog-only persistido proposto, **não executado**, com preflight/reserva/readback novos e saída apenas agregada. FAILs 0342/0345 e 1088 stats preservados; Gate 1, A/B físico e P08 abertos. Sem SQL/JDBC/IT/Flyway/smoke ou edição de schema/Runtime.

# P08 — diagnóstico físico único reconciliado; qualificação aberta — 29/09/2026

[Checkpoint 0345](docs/continuidade/checkpoints/0345-p08-diagnostico-fisico-causa-sql-rollback.md). Pins IT/Runtime e PackageShadow A/B 0345, input drift zero e preflight local passaram; uma IT Failsafe selecionada executou 1 método/1 failure/0 skip, Maven exit 1. Recibo sanitizado trouxe `SOURCE_PROTOCOL_NOT_REGISTERED`/SQL **51301**, rollback e agregados iguais. Readback independente master/alvo/contagens/`064`/stats/socket sem delta (1088→1088). Assinatura coincide com guard V024, sem ramo/causa de catálogo comprovados nem correção. Handoff Runtime 0345 A/B segue pacote apenas offline; oito cenários e smoke físico não executados aqui. FAIL 0342 873→1088/215 stats preservado; Gate 1 e P08 abertos. Sem retry/Flyway/DDL/restore.

# P08 — handoff Runtime de diagnóstico integrado offline — 29/09/2026

Hashes dos dois arquivos Runtime e do teste novo conferidos; 3 XML Surefire existentes em JDK17 somam **33/33 PASS** (2+4+27), zero skips/errors. A mudança prepara recibo sanitizado com causa fechada e número SQL positivo, mas a causa SQL real dos sete `ANA_QUOTE_CAPTURE_RECOVERY_REQUIRED` de 0342 segue **desconhecida**. Número do teste é fixture. [Checkpoint 0344](docs/continuidade/checkpoints/0344-p08-runtime-failure-evidence-offline-handoff.md). FAIL 0342, delta geral 873→1088/215 autoestatísticas, FAIL 0336/0341 e limites backup 0325 preservados; Gate 1/2 e P08 abertos. Sem SQL/JDBC/IT/Flyway/smoke nesta unidade; Runtime prepara pacote offline, ainda não qualificado fisicamente.

# P08 — revisão offline das estatísticas 0342 — 29/09/2026

Dumps pré/pós 0342 comparados por linha: 873→1088 grupos, 215 novos pares tabela-coluna em 62 tabelas, todos automáticos sem filtro/índice; zero grupos removidos/alterados, zero nas quatro colunas V105. `064` idêntico observa apenas stats/dependências do recorte V105, não o inventário geral. Sem `stats_id` ou inventário depois do 064 pós, contagem exata de objetos e origem de cada grupo não são provadas. Critério futuro separa baseline observacional pós-0342 do FAIL estrito, exige inventário global por gate e revisão de qualquer auto metadata nova. Evidência/limites no STATES/[checkpoint 0343](docs/continuidade/checkpoints/0343-p08-auto-stats-0342-revisao-offline.md). P08 e Gate 2 A/B abertos; nenhum novo efeito SQL ou edição Runtime.

# P08 — IT física de composição 8 run/7 FAIL; smoke A/B parado — 29/09/2026

Seletor Maven corrigido alcançou Failsafe offline sem SQL. Pins, preflight e reserva novos passaram no shadow local. Uma IT selecionada JDK17/Maven offline executou oito cenários, sete falharam com `ANA_QUOTE_CAPTURE_RECOVERY_REQUIRED`, um CANCELLED esperado; oito recibos rollback/antes=depois. Readback master/alvo/contagens/064/socket igual, stats gerais 873→1088 com 215 autoestatísticas novas, nenhuma manual/filtrada. Gate 1 não reconciliado, Gate 2 A/B não iniciado, P08 aberto. Evidências e limites no STATES/[checkpoint 0342](docs/continuidade/checkpoints/0342-p08-it-composicao-fail-auto-stats.md); FAIL 0336/0341 e backup 0325 preservados.

# P08 — Gate 1 Maven falhou antes da IT; smoke A/B não iniciado — 29/09/2026

Pins 0340 e A/B novos conferidos; preflight local com reserva passou após duas recusas do wrapper antes de SQL preservadas. Uma chamada Maven offline/JDK17 com duas travas saiu 1 no Surefire por `-Dtest=none` sem `surefire.failIfNoSpecifiedTests=false`; Failsafe/JDBC e os oito cenários não iniciaram. Readback independente master/alvo/contagens/064/873 stats/socket sem delta. Gate 2 A/B inelegível e não extraído. Ledger, hashes, causa e limites no STATES/[checkpoint 0341](docs/continuidade/checkpoints/0341-p08-gate1-it-maven-fail-sem-sql.md). P08 aberto, sem retry, DDL/Flyway/restore; FAIL 0336 e backup 0325 preservados.

# P08/V105 — revisão offline do recurso físico V105 — 29/09/2026

Banco comparou linha a linha o JSON V105 do Runtime com a saída DMV Unicode-safe 0339: 971/971 nomes e campos técnicos iguais; catálogo 0336 também igual nos campos seguros. V105×V098 contém exatamente 181 collations CI_AS→100_CI_AS_SC e cinco larguras, zero outros campos. V098 SHA intacto; cabeçalho V105 conferiu hashes de arquivo, digests canônicos e recibos 0336/0338/0339. Versão/compat/collation do alvo e hashes das 19 definições foram reconferidos nos recibos 0339; fontes V043/V052/V080/V093/V105 conferem manifesto. Recibo privado e limites no STATES/checkpoint 0340. Sem SQL/JDBC/Flyway ou edição de resource/guard/schema. Isto qualifica **somente a concordância offline de metadados**; Runtime ainda testa/empacota, FAIL 0336 e P08 aberto.

# P08/V105 — DMV read-only independente das 971 colunas — 29/09/2026

Gate reservado `localhost/ETL_SISTEMA_V2_SHADOW`: preflight master/alvo/Windows auth/socket loopback/zero consumidores, 106 Flyway/zero falhas, 873 grupos de stats. Uma chamada DMV para 19 views saiu 0: 971 colunas, zero erro/ocultas, nomes Unicode lossless em hex UTF-16LE e ordem exata; subsequência das views 01/13 igual a 0338. DMV 971/971 concorda com catálogo 0336 nos metadados técnicos, sem usar nomes danificados daquele dump. Contra V098, exatamente 181 collations CI_AS→100_CI_AS_SC e cinco larguras, sem outro delta. Hashes das 19 definições de view e fontes V043/V052/V080/V093 conferidos; readback master/alvo/contagens/stats/definições/socket idêntico. Digests/recibo/ledger no STATES e checkpoint 0339. Confirma estado atual, não causa histórica ou contrato V105 aprovado. FAIL 0336, recusas 0338, A/B históricos e P08 aberto; nenhum DDL/JDBC/Flyway/smoke/restore.

# P08/V105 — DMV read-only independente das views 01/13 — 29/09/2026

Preflight/reserva local confirmaram master/alvo Windows auth, serviço/listeners loopback, SQL Server 17.0.1000.7/compat170/collation 100_CI_AS_SC no alvo, SCHEMA+105 SQL/zero falhas, zero consumidores e 873 stats estáveis. Recusas pré-DMV de certificado (`-C` omitido) e collation nula no `master` com `AUTO_CLOSE=1` foram preservadas e corrigidas sem executar a DMV. Uma chamada DMV para `SELECT *` descrito de SQL-01/13 saiu 0: 119 colunas, zero erros/ocultas, nomes lossless em hex UTF-16LE, 119/119 metadados iguais ao catálogo 0336. Contra V098: 63 collations CI_AS→100_CI_AS_SC nas duas views, cinco larguras; 181 collations no total de 971 continuam prova catalog-only 0336. Hashes de V043/V052/V080/V093 conferiram manifesto, hashes de módulos ativos e digests das matrizes no checkpoint 0338. Readback master/alvo/contagens/stats/socket idêntico; nenhum delta observado. FAIL 0336 e P08 aberto; sem causa histórica das larguras provada, snapshot V105 aprovado ou novo smoke.

# P08/V105 — reconciliação offline do contrato físico — 29/09/2026

Banco comparou V098 e catálogo 0336 por ordem/ordinal e metadados: 971 colunas em cada, 186 diferenças de campo/181 colunas (181 collations CI_AS→100_CI_AS_SC, cinco bytes de largura). Manifesto/hashes V001–V105 e baseline na ordem conferiram; V099–V105 não redefinem as views nem as fontes das larguras. V105 muda quatro colunas persistidas fora dessas views, além da TVP/procedure dependente. SQL-01 e SQL-13 têm linhagem de expressões sem cast final em V043/V080 e V052/V093; a inferência exata entre capturas continua sem prova independente. Banco atual tem default 100_CI_AS_SC desde 0311; snapshot V098 é histórico. Recibo privado e proposta de contrato V105 com proveniência independente no STATES/checkpoint 0337. Nenhum SQL/JDBC/Flyway ou edição de migration/baseline/manifest/snapshot; A FAILED, B não extraído, P08 aberto.

# P08/V105 — smoke A FAILED no contrato físico V098; B parado — 29/09/2026

A/B ZIP, manifesto e V105 conferiram os pins; 4.081 inputs A/B iguais,
com drift só nos três documentos compartilhados pós-package. A foi extraída
em caminho novo, configuração externa limitou campanha a 420 s. Após
preflight local novo estável, uma chamada física `run` A saiu 2:
`QUAL_PHYSICAL_SCHEMA_DRIFT`, controle terminal FAILED. Rollback e
reconciliação confirmados; readback independente master/alvo/contagens/064/
stats/socket não teve delta. Diagnóstico catalog-only isolou o guard que
compara 971 colunas V098 com o catálogo V105 atual: 181 collations e cinco
comprimentos divergentes, sem novo stat. Erros de invocador anteriores à
criação de filho foram preservados. `status/resume/compare` e B não foram
executados, conforme a ordem autorizada. Detalhes/limites no STATES e
checkpoint 0336; P08 aberto e FAIL 0333 preservado.

# P08/V105 — 063/064 PASS, fotografia pós-IT estável — 29/09/2026

Preflight local reservado confirmou master/alvo, Windows auth, sockets
loopback, zero consumidores, histórico SCHEMA+105 SQL/zero falhas e
agregados de domínio/auditoria estáveis. Em gates distintos, 063 saiu 0
com `EPOCH_V105_STRUCTURAL_PASS`; 064 saiu 0 com SHA idêntico ao snapshot
pós-IT 0333. Inventário catalogado de 873 grupos ficou byte-idêntico
imediatamente após cada script: nenhuma estatística adicional. A estatística
`auto_created=1` em `ref.expansion_lab_label.label` permanece sem filtro,
sem criação manual ou índice. Readback externo master/alvo/contagens/socket
igualou preflight. O novo critério de baseline **observacional pós-IT**
passou nesta unidade; o FAIL estrito de 0333 continua histórico. Ledger,
hashes e limites no STATES/checkpoint 0335. IT 5/5 PASS é evidência
separada; **P08 não recebe aceite integral**.

# P08/V105 — autoestatística estável; 063/064 adiados por risco — 29/09/2026

Preflight master/alvo/socket reservado PASS. Catálogo mostrou uma
estatística automática sobre `ref.expansion_lab_label.label`, sem filtro
ou índice, `last_updated` agregado disponível; readback idêntico.
`AUTO_CREATE_STATISTICS=ON` e predicados de dados nos scripts 063/064
impediram garantir que sua execução não criaria outra estatística;
**não foram executados** nesta unidade. O delta 0333 é compatível com
metadado automático normal pós-V105, mas o critério físico estrito de
064 sem delta permanece falho. Ledger e limites no STATES/checkpoint
0334; P08 aberto, sem aceite integral ou remoção automática.

# P08/V105 — classe IT 5 PASS, 064 metadados divergentes — 29/09/2026

Com preflight master/alvo/064/contagens/socket PASS, uma execução
Maven offline/JDK17 da classe `ExpansionLaboratoryReferencesIT` teve
5 testes, 0 falhas/erros/skips, incluindo replay case-only. Readback
externo manteve master/alvo/contagens/socket, mas 064 mudou de 0 para
1 estatística automática em `ref.expansion_lab_label.label`. Diagnóstico
metadata-only identificou a estatística; nenhum dado de domínio mudou.
**Gate físico sem delta não passou**, embora a camada de testes tenha
passado. Ledger/logs/limites no STATES e checkpoint 0333; sem retry ou
remoção automática. P08 segue aberto, Supervisor decide próximo gate.

# P08/V105 — IT case-only selecionada PASS, rollback sem delta — 29/09/2026

Com IT/V105 pinadas e preflight master/alvo/064/contagens/socket local
PASS, uma chamada Maven offline/JDK17 com perfil e duas travas selecionou
apenas `ExpansionLaboratoryReferencesIT#caseOnlyLabelReplayUsesContentComparisonAndRollsBack`:
1 run, 0 fail/error/skip. Passaram asserts de 4 releases/receipts,
15 labels, 4 selections, replay idêntico, erro case-only 53437,
`XACT_STATE` admissível e sessão nova vazia. Readback externo repetiu
exatamente os quatro SHA pré e listeners loopback; zero delta. Logs,
reports, ledger e limites estão no STATES/checkpoint 0332. FAILs
0329/0330 e sonda 0331 preservados. **P08 aberto**, sem smoke ou aceite
integral.

# P08/V105 — sonda UUID/BIN2 read-only confirma mecanismo do FAIL 0330 — 29/09/2026

Preflight master/alvo/contagens/socket local PASS, histórico SCHEMA+105 SQL
sem falhas, zero consumidores. Uma sonda sintética sem UUID/ID impresso
confirmou `scope_code nvarchar(128) Latin1_General_100_BIN2` e flags BIN2:
nativo × lowercase 0; nativo × lowercase reconvertido via
`uniqueidentifier` 1. Readback independente repetiu hashes e socket,
zero delta. Um falso FAIL de ordem de listeners no verificador foi
reclassificado com os mesmos recibos, sem repetir SQL. Ledger/limites no
STATES e checkpoint 0331. Runtime detém correção da IT; nenhuma execução
de IT nesta unidade. FAIL 0330 preservado; P08 aberto.

# P08 — self-test URL passou; IT V105 falhou no assert de releases — 29/09/2026

Montagem da variável JDBC corrigida só no ambiente privado. Primeira
invocação offline foi bloqueada pela política PowerShell 5.1 antes do
Java; runner PowerShell 7.6.6 executou o mesmo script e provou seis
segmentos/chaves exatas, localhost, banco exato, Windows auth e guard
`ShadowStorageProperties` PASS sem DriverManager/JDBC. Preflight novo
master/alvo/contagens/064/socket igualou 0329, zero consumidores.

Uma IT física selecionada com duas travas chegou ao JDBC, mas Failsafe
teve 1 run/1 failure/0 skip: linha 149 esperava 4 releases no escopo
e observou 0, antes do replay divergente. Erro 53437 e XACT_STATE
não foram testados. Readback SQL/socket/contagens/064 após o FAIL
foi idêntico, sem delta. Logs/report/ledger e limites no STATES e
checkpoint 0330; hipótese de comparação BIN2 de UUID pendente de
revisão Runtime/Supervisor, sem edição de IT. P08 aberto; sem retry.

# P08 — uma IT V105 selecionada falhou no guard local de URL — 29/09/2026

Preflight master/alvo/064 e contagens globais passou em
`localhost/ETL_SISTEMA_V2_SHADOW`: histórico SCHEMA+105 SQL/zero falhas,
zero consumidores, listeners só loopback, contagens de releases/receipts/
labels/selections/auditorias zero. Bytes V105/IT e PASS 063/064/validate
0328 conferidos. Uma invocação Maven offline com perfil/duas travas e
`-Dit.test` selecionou exatamente um método físico: 1 erro, 0 skipped.
A construção do ambiente PowerShell agrupou as propriedades da URL num
segmento; `ShadowStorageProperties` recusou `databaseName` antes de JDBC.
Portanto 53437, XACT_STATE e rollback não foram provados. Readback
master/alvo/contagens/064/socket após o FAIL foi idêntico, zero delta.
Log, report e ledger 0329 preservados; sem retry/fallback ou outra IT.
P08 continua aberto. Supervisor decide eventual nova autorização.

# P08 — V105 aplicada uma vez no shadow local; gate estrutural passou — 29/09/2026

Espelho temporário `target/` com V001–V104 byte-idênticas ao manifesto 0319
e sem V105: `flyway:info` com localização de processo mostrou só as 104
aplicadas; `flyway:validate` normal saiu 0. Preflight novo master/alvo/064,
backup VERIFYONLY, sockets, zero consumidores e cinco pins passou. No
diretório original, `info` mostrou apenas V105 pendente; uma chamada
`flyway:migrate` com POM normal saiu 0. Readback autoritativo confirmou
SCHEMA+105 SQL, V105 sucesso único, zero falhas, mesmos objetos/tabelas/
principals e contagem global +1 apenas pela linha Flyway.

`064` confirmou BIN2 nas quatro colunas e TVP, agregados de domínio sem delta;
`063` imprimiu `EPOCH_V105_STRUCTURAL_PASS`; `flyway:validate` normal no
original passou. Gates tiveram reservas e readbacks próprios. Ledger, hashes
e limites no STATES/checkpoint 0328. FAILs 0326/0327 e limites do backup
0325 preservados; nenhum restore/JDBC/replay/IT/smoke, remoto ou produção.
Pacote Runtime repinado permanece offline. P08/P01–P33 seguem abertos.

# P08 — 0327: prevalidate Community 9.22.3 recusou `versioned:pending`; V105 intacta — 29/09/2026

Novo gate read-only autorizado: hashes V001–V104 e cinco pins conferidos;
preflight master/alvo/064, VERIFYONLY, PID/listeners e zero consumidores
reproduziu 0326. `flyway:info` mostrou SCHEMA + V001–V104 `Success` e somente
V105 `Pending`, sem anomalia. Uma chamada `flyway:validate` com apenas
`-Dflyway.ignoreMigrationPatterns=versioned:pending` saiu 1: o plugin Flyway
Community 9.22.3 reconheceu a propriedade, mas `versioned` exige Teams.
Readback independente confirmou 105 linhas (SCHEMA+104 SQL), zero falhas,
V105 ausente e contagens/bytes/serviço/sockets sem delta. Zero `flyway:migrate`,
fallback, restore, DDL, JDBC, replay ou smoke. O FAIL 0326 e o novo FAIL
permanecem; detalhes e hashes no STATES/checkpoint 0327 e ledger físico.
O backup 0325 mantém ausência prévia, comprimento/SHA físico e restore sem
prova. Pacote Runtime ZIP `80CC18349268E07002E3E395FCE0239EDD95F1639F7AC81E7046C78877483499`
é somente evidência offline. Supervisor decide novo critério/gate compatível
antes de V105. P08/P01–P33 continuam abertos.

# P08 — gate V105 parado antes de migrate pelo validate prévio — 29/09/2026

Novo preflight local de master/alvo, sockets, histórico 105/104/zero falhas,
`064`, 104 migrations aplicadas byte-idênticas, cinco hashes fixados e
`RESTORE VERIFYONLY WITH CHECKSUM` do backup 0325 passou. O backup segue com
limites históricos de ausência prévia/comprimento/SHA físico não comprovados e
sem restauração testada. `flyway:validate` prévio foi reservado e saiu 1:
Flyway detectou V105 resolvida no checkout e ainda pendente no banco. A
alternativa `ignoreMigrationPatterns='*:pending'` não foi usada, pois o
Supervisor exigiu parada ante preflight divergente, sem retry/fallback.
Readback master/alvo/064/sockets após o FAIL repetiu o estado anterior;
**zero chamadas migrate**. Ledger e recibos no checkpoint 0326. Runtime
entregou pacote offline A/B repinado, ZIP idêntico SHA-256
`80CC18349268E07002E3E395FCE0239EDD95F1639F7AC81E7046C78877483499`,
27 guardas, quatro mutantes, 21 extraídos, sete Maven e config/dry-run PASS,
sem smoke físico. Nenhum 063/064/validate pós-V105, restore, JDBC ou replay;
P08/P01–P33 continuam abertos. Supervisor decide novo critério/autoridade.

# P08 — cópia local pré-V105 feita uma vez; verificação SQL passou com limite de ACL — 29/09/2026

Por autorização restrita do Supervisor, Banco reservou e executou um único
`BACKUP DATABASE` do shadow local V104 para o caminho nativo proposto, com
`COPY_ONLY, INIT, CHECKSUM`; `sqlcmd` exit 0, 1.698 páginas. Gate separado:
`RESTORE VERIFYONLY WITH CHECKSUM`, `HEADERONLY`, `FILELISTONLY` e histórico
`msdb` passaram; database e dois arquivos lógicos conferem, metadata registra
backup set de 13.983.744 bytes, copy-only/checksums. Snapshot Flyway,
contagens e sockets antes/depois não mudaram. O terminal não lê a ACL nem o
arquivo físico, e a leitura `OPENROWSET(BULK)` recebeu acesso negado; SHA-256
e comprimento físico do `.bak` seguem não comprovados. Pelo mesmo motivo,
`Test-Path` falso antes da chamada não prova ausência prévia: `INIT` poderia
ter substituído um arquivo órfão sem histórico. O ledger acrescentou correção
sem apagar a reserva original. A primeira invocação
de verificação foi rejeitada por opções incompatíveis do cliente antes de
conectar, com FAIL preservado. Não houve retry do backup, alteração de ACL,
restore, migrate, DDL, JDBC ou replay. Ledger e recibos no checkpoint 0325;
Supervisor decide a suficiência desta prova para eventual autorização de
migrate. P08/P01–P33 permanecem abertos.

# P08 — guardas V105 e validador 063 provado em V104, sem migrate — 29/09/2026

V105 passou a recusar antes de DDL drift de estatísticas, dependências do TVP,
corpo da procedure, predicados dos dois checks e opções físicas do índice;
snapshot 064 observa esses estados. O gerador corrigiu nove constraints sem
nome omitidas e o inventário V104/V105 agora fecha 224. Gates físicos somente
leitura preservaram os dois FAIL diagnósticos iniciais de 063 (erro de SET
option e inventário incompleto); a versão final falhou em V104 só por cinco
collations CI, TVP CI e V105 pendente, sem falso inventário. 064 passou com
definições/opções revisadas 1/1/1/1, bloqueadores zero e histórico/contagens
sem delta. Evidência e hashes em `STATES.md`/checkpoint 0324.

Nenhum backup, restore ou migrate foi executado. O plano local usa diretório
nativo da instância, checksum/VERIFYONLY e restauração apenas com autoridade
separada; ACL de escrita do serviço não foi comprovada. O pacote Runtime 105
offline A/B anterior ficou obsoleto após mudança de bytes V105 e exige
regeneração/reteste por Runtime. P08/P01–P33 seguem abertos; Supervisor revisa
a nova proposta antes de qualquer efeito V105.

# P08 — V105 e validador estrutural preparados, sem execução SQL — 29/09/2026

Migration V105, validador 063, snapshot 064, baseline V105 e dois manifests
novos preparados para revisão. O inventário fechado derivado das migrations
tem 569 objetos, 23 TVPs, 1.019 constraints nomeadas, 215 sem nome e 100
índices; V104 permanece FAIL por `MIGRATION_PENDING` e collations. A procedure
recriada é textualmente igual à V042, conservando dez parâmetros e recibo de
quatro IDs. Evidência e hashes no `STATES.md`/checkpoint 0323.

Teste offline causal PASS (três mutantes de ordem/BIN2, cinco de collation,
quatro de inventário), checkers de fundação/progressivo PASS; nenhuma prova
física V105, SQL Server ou Flyway foi chamada. Runtime confirmou contrato
JDBC/TVP em handoff offline, mas a prova funcional e o ajuste do empacotador
104→105 ainda pertencem ao gate futuro. Os FAIL originais 003/005/030,
V001–V104 e ledgers permanecem. P08/P01–P33 seguem abertos; Supervisor
revisa bytes, impacto, recuperação e readback antes de autorizar qualquer
migrate.

`Test-TrilhaPreparation.ps1` passou (33 etapas, 48 IDs abertos, nove pacotes).
O primeiro scanner offline saiu 1 porque `.mjs` não estava na lista de texto;
a saída FAIL consta no terminal, sem recibo de arquivo próprio. Após incluir
`.mjs` nessa lista, a varredura completa
passou com 4.070 textos e zero achados, sem exceção de arquivo.

# P08 — triagem causal dos três validadores FAIL — 28/09/2026

Os FAIL de 0321 permanecem: `003` 11, `005` 505 e `030` 28. A leitura de
migrations V001–V104, baseline, manifests e catálogo separou critérios de
versão dos desvios atuais: V024 substituiu a forma literal esperada pelo 003;
503 achados do 005 e 27 objetos do 030 vêm de migrations posteriores aos
inventários originais; duas constraints do 005 e cinco colunas do 003 são da
tabela técnica Flyway. Persistem quatro collations de aplicação sem BIN2:
três campos de `ctl.execution_audit` (V025) e `ref.expansion_lab_label.label`
(V042), além do campo correspondente no TVP. Proposta de escopo e reparo em
`docs/runbooks/p08-validadores-versionados-v104-20260928.md`.

Testes offline causais e três checkers próprios passaram; checker Bloco 60
falhou por `HANDOFF_PATH` histórico e foi preservado. Duas leituras SQL
diagnósticas tiveram preflight, reserva e readback próprios; o snapshot final
foi idêntico ao de 0321. Ledger 0322 SHA-256
`5A19AD1720B3BDE5DBDC4387DBFDC50EEA1F32879A5F78CBE6B7C9780F7CB9E4`.
Nenhum dos três validadores foi repetido, nenhuma migration nova foi aplicada,
e nenhum aceite integral P08/P01–P33 foi promovido. V105 e validador V104
exigem desenho/revisão e gate próprios. Detalhes no `STATES.md` e checkpoint
0322.

# P08 — validação estrutural local e auditoria JDBC rollback-only — 28/09/2026

Com as 104 migrations já aplicadas, o Builder Banco e Persistência qualificou
`001_validate_schema_foundation.sql` estaticamente e o executou uma vez com
`sqlcmd -E` no banco local exato: exit 0 e readback sem delta. Dos 28
`*validate*.sql`, cinco wrappers `showplan` com reset histórico e `053` com
identidades Windows não autorizadas foram excluídos. Os 22 elegíveis rodaram
em gates separados com reserva e readback: 19 PASS, três falhas preservadas
(`003` com 11, `005` com 505, `030` com 28 divergências). Nenhum reset,
`CREATE USER`, retry ou alteração durável ocorreu.

Depois do schema validator, a IT selecionada `ShadowAuditLocalIntegrationIT`
rodou uma vez com Maven offline/JDK17, duas travas e URL no ambiente local do
processo: 3/3 Failsafe PASS, Spotless/Checkstyle PASS; rollback e contagens de
auditoria 0/0 antes/depois confirmados por SQL independente. O validador `061`
rodou após a IT e passou. `ShadowJdbcTransportReadOnlyIT` não rodou por exigir
schema vazio. O ledger físico SHA-256
`BE82739B1F127109EED4158FAB96699373F59980674AF4E88093CAD288E7D976`
e os recibos estão no `STATES.md`/checkpoint 0321. P07/P08 e P01–P33 seguem
abertos; Supervisor integra o handoff do Runtime e triagem dos três gates
estruturais falhos, sem fonte real, remoto ou produção.
`Test-TrilhaPreparation.ps1` passou após o registro (33 etapas, 48 IDs
abertos, nove pacotes); `git diff --check` e UTF-8 estrito passaram.

# P08 — migrations V001–V104 e Flyway validate locais passaram — 28/09/2026

Exceção estrita de V002 para `v2_schema_owner WITHOUT LOGIN` registrada antes
do efeito em `AGENTS.md`/`STATES.md`. PowerShell 7.6.6 portátil verificável
executou o checker progressivo original com PASS. Preflight novo confirmou
somente `localhost/ETL_SISTEMA_V2_SHADOW` vazio e listeners loopback; uma
invocação `flyway:migrate` aplicou V001–V104. Readback SQL confirmou uma marca
`SCHEMA` e 104 migrations `SQL` bem-sucedidas, zero falhas, sete schemas com
ownership do usuário sem login e nenhum login no servidor. `flyway:validate`
passou em gate separado; readback posterior sem delta. O primeiro assert de
histórico falho por omitir a marca `SCHEMA` está preservado, sem retry de
migration. Ledger, hashes e limites no `STATES.md` e checkpoint 0320.

Seis scripts SQL de validação contêm `CREATE USER` por varredura estática e
não foram executados; exigem revisão/autorização própria. P07/P08 e P01–P33
continuam sem aceite integral por esta prova local. Sem fonte real, remoto,
produção, `clean`, `repair`, `drop` ou cutover.
Validador da trilha PASS (33 etapas/48 IDs abertos/nove pacotes) e scanner
offline PASS (4.058 candidatos, zero achado) sob PowerShell 7.6.6; recibos em
`STATES.md`/checkpoint 0320. Nenhum build Java amplo foi repetido por esta
unidade SQL/documental.

# P08 — preflight V001–V104 sem migration; conflito de usuário interno — 28/09/2026

Unidade 0319 do Builder Banco e Persistência: preflight novo em
`localhost/ETL_SISTEMA_V2_SHADOW` passou, 104 migrations contíguas conferem
byte a byte com o espelho do `flyway:info`, baseline lista 104/104 e readback
confirmou banco ainda vazio. `V002` contém `CREATE USER ... WITHOUT LOGIN`,
enquanto `AGENTS.md` §1 proíbe criar usuário; a autorização condicional atual
não resolve o impacto. `flyway:migrate`/`validate` não foram chamados. O
checker progressivo ficou sem execução válida no PowerShell 5.1; o checker da
fundação passou. A próxima decisão cabe ao usuário/owner, seguida de gate e
reserva novos. P07/P08 e P01–P33 não recebem aceite.

Handoffs paralelos inspecionados: Fontes encerrou P09–P15 local read-only, com
G01/G03 externos; Regras corrigiu retenção do tempo nativo de Coleta sob
referência temporal nula, com RED/GREEN sintéticos, P16 aberto; Runtime antecipou
as duas travas de `run`/`resume`/`worker`, com RED/GREEN offline, P08 aberto.
Diff estreito e relatórios locais conferidos, sem gate integrado. Detalhes e
recibos estão em `STATES.md` e no checkpoint 0319; ledgers anteriores preservados.
`Test-TrilhaPreparation.ps1` exigiu PowerShell 7 e não validou neste terminal
com Windows PowerShell 5.1; `graphify update .` saiu 0.

# P08 — JDBC read-only e Flyway info no shadow local — 28/09/2026

Unidade encerrada para handoff, sem promover P07/P08 nem P01–P33. O launcher
direto inicial falhou antes de Java (PowerShell 5.1 sem `ArgumentList`); o
Supervisor rejeitou seu sucessor por contornar o perfil e JDK 17. Ambos os
artefatos/recibos permanecem no ledger privado. Em espelho isolado Maven
JDK17, a nova `ShadowJdbcTransportReadOnlyIT` usa o validador P08 efetivo,
URL de processo, duas travas do perfil e rollback em `finally`. Testes offline:
3/3 recusas/aceites de URL; guarda ASCII da máquina passou e conexão sem URL
foi pulada. Bytecode confirmou rollback também no handler de exceção.

Primeiro attempt físico: conexão Windows auth ao banco correto, falha de
assert por `LUCAS` versus `Lucas`; readback sem delta. Corrigido com guarda
ASCII e teste de Unicode, um reattempt físico autorizado passou IT 2/2,
confirmando JDBC TCP somente loopback, zero objetos/histórico e rollback.
`flyway:info` separado saiu 0 com 104 migrations pendentes; readback confirmou
`ctl`/histórico ausentes e banco vazio. Não houve migrations, validações
sintéticas pós-schema, produção, remoto ou cutover. Ledger
`pin-1282-jdbc-flyway-ledger.jsonl` SHA-256
`53E780FEE03643FD752812F8DC9AA04923E1C48C42F0497FAE327923C6485EE3`;
log Flyway privado contém URL local e não integra a trilha pública. Detalhes
e conflitos de arquivos no checkpoint 0318.

# P08 — TCP SQL exclusivamente loopback comprovado — 28/09/2026

Attempt v3 autorizado uma vez e reservado separadamente: preflight
WMI/socket e `master`/alvo vazio passou; helper elevado saiu 0 após
`SetFlag(false)`, habilitar somente IP19=`::1` e IP20=`127.0.0.1`, TCP por
último e um restart. Readback independente: PID 2116→20404,
TCP=true/NP=false/ListenAll=0, outras 22 entradas desligadas, exatamente
`::1:1433` e `127.0.0.1:1433`, banco shadow ainda sem objetos de usuário.
Ledger privado `pin-1282-uac-v3-ledger.jsonl` SHA-256
`70C5FB2E32CDE84457EBE930324DF1FC3605F3816AA2AB3B3DA999A09EF0CA74`.
Gate de transporte local PASS; JDBC/Flyway/migrations/IT e P07/P08 integrais
ainda abertos neste checkpoint. Attempts anteriores preservados.

# P08 — proposta WMI `SetFlag`/`SetEnable` em leitura — 28/09/2026

`P08-WMI-METHOD-0316` inspecionou o provedor SQL Server 17 sem novo efeito:
`ListenOnAllIPs` é opção 0/1 com `PropertyType=0`; o método numérico usado no
attempt v2 retornou `0x80041024` sem mudança. `SetFlag(Boolean)` e
`ServerNetworkProtocolIPAddress.SetEnable()` existem e são documentados;
IP19/IP20 mapeiam unicamente para `::1`/`127.0.0.1`, outras 22 entradas
desligadas. A proposta com guardas, rollback condicionado e prova posterior
de listeners está em
`docs/runbooks/p08-loopback-wmi-flag-proposta-20260928.md`. Sucesso do novo
método ainda não foi testado. Ledgers/attempts preservados, sem UAC/escrita,
JDBC/Flyway/migration nesta unidade. P07/P08 e P01–P33 integrais abertos.

# P08 — nova elevação iniciou; WMI recusou ListenOnAllIPs — 28/09/2026

O operador informou que não cancelou a tentativa 0313. O evento 4100 é
somente a mensagem retornada pelo Windows; logs locais não provam exibição nem
resposta ao prompt anterior. Novo preflight WMI/socket e `master`/alvo vazio
passou. Attempt `LOOPBACK_UAC_V2` distinto: uma invocação do launcher foi
recusada por política de scripts antes do UAC; com exceção limitada ao processo,
uma elevação abriu o helper. Ele recusou a primeira escrita WMI com
`0x80041024` (`WBEM_E_PROVIDER_NOT_CAPABLE`) e saiu 1, antes de restart.
Readback confirmou mesmo PID/configuração, zero listeners e alvo vazio.
Sem retry, JDBC, Flyway ou migrations. Ledger privado
`pin-1282-uac-v2-ledger.jsonl`; P07/P08 e P01–P33 integrais abertos.

# P08 — cancelamento da elevação identificado no evento PowerShell — 28/09/2026

Diagnóstico offline `P08-UAC-CAUSAL-0314` corrigiu a classificação da
`InvalidOperationException`: evento PowerShell 4100/record 47081, às 19:08:05,
registra “A operação foi cancelada pelo usuário” em `Start-Process`.
O evento não determina o gesto do operador. Executável/script presentes,
parâmetros válidos, helper com hash intacto/zero erro de parser e sem recibo;
nenhum defeito causal do helper foi demonstrado. Ledger/attempt 0313 preservados;
nenhum UAC, serviço, TCP, SQL, JDBC ou migration nesta unidade. O próximo
attempt requer sinal novo do operador, preflight e reserva próprios. P07/P08 e
P01–P33 integrais continuam abertos. Recibo privado `pin-1282-uac-diagnostic-0314.json`.

# P07/P08 — pin shadow 12.8.2 offline; UAC de loopback sem efeito — 28/09/2026

`STATES.md` registra o resultado canônico. O pin 12.8.2 foi autorizado e
aplicado aos perfis/guardas shadow; histórico 12.8.1 preservado. A/B
reproduzíveis, comandos puros 8/8, recusas 21/21, `clean verify` v2
2.360 Surefire/seis ITs offline e cobertura passaram; PMD 36/36 local e SAST
272/87/46 sem assinatura nova. A primeira tentativa de verify e a consulta
`master` recusada por `AUTO_CLOSE` foram preservadas e diagnosticadas. O
preflight v2 confirmou banco vazio. A única elevação UAC para TCP loopback
falhou antes do helper; readback mostrou nenhuma mudança em serviço, protocolo
ou banco. Sem retry, migration, JDBC ou IT física. P07/P08, P11/P29 e P01–P33
integrais seguem abertos; novo sinal UAC do operador é o input físico exato.

# P29 — lote HTTP/URI v102; P07/P08 físicos abertos — 28/09/2026

`STATES.md` registra a evidência canônica. O teste causal Data Export
`application/jsonp` foi RED (uma falha) e passou após exigir media type ASCII
exato; contraprovas URI dos nove alertas Unicode de três configurações HTTP
passaram 3/3, com limite técnico explícito. Regressão dirigida 49/49;
SpotBugs/FindSecBugs v102 em 656 fontes preservou 272 alertas brutos,
87 SECURITY e 46 Unicode, sem aceite nominal. Serviço/banco shadow locais
foram materializados no checkpoint 0311, mas o schema continua vazio.
`AGENTS.md`/perfis/PackageShadow 12.8.1 seguem suspensos até decisão explícita
sobre 12.8.2; nenhum JDBC, migration ou IT. P01–P33 conservam seus checkboxes
e P07/P08/P11/P29/G02 continuam abertos.
# P29/P08 — pin 12.8.1 físico suspenso; LOC-04 corrigida — 28/09/2026

Segurança/Supervisor suspendeu JDBC e pacote físico 12.8.1 por
CVE-2025-59250 até decisão explícita do usuário para alterar `AGENTS.md`.
Nenhum perfil/lock ativo foi migrado; [delta 12.8.2](docs/catalogos/p29-sast-preparacao-20260928/PROPOSTA-SHADOW-12.8.2.md)
define alvo, hashes, validações e rollback sem execução. Um alerta SAST
apontou o regex decimal 8656: texto de 8.193 zeros passava o teto de token
numérico e se tornava valor tipado. Teste RED e correção LOC-04 para limitar
antes de regex/`BigDecimal` passaram 11/11; `clean verify` v83 passou
2.352 Surefire/cinco skips, seis ITs offline, formatter/Checkstyle e JaCoCo
80/60. PMD v83 manteve 36 achados brutos e disposição local 36/36 PASS;
SpotBugs/FindSecBugs v84 preservou 273 brutos/88 SECURITY, sem supressão
ou aceite nominal. Triagem técnica localizou 42 achados distintos, incluindo
19 sinks SQL de 22 alertas sobrepostos e nove comparações de hash; nenhum
foi descartado nominalmente. Gitleaks/scanner v85 em 4.038 arquivos/
candidatos passaram com zero achados; trilha 33/48/9, UTF-8 e diff PASS.
Os REDs v81/v82 de invocador estão preservados.
P08 físico, P11 baseline/feed e P29 SAST/licenças/RC/SOs seguem abertos;
UAC espera operador, nenhum SQL/DLL/serviço foi executado.

# P29/P11 — SAST amplo exploratório e SBOM confrontado — 28/09/2026

Um scanner adicional em espelho das 656 fontes/1.135 artefatos v79 executou
SpotBugs 4.10.4 + FindSecBugs 1.14.0, sem filtros ou supressões: 1.091 classes,
zero erros/classes ausentes, 273 alertas brutos (88 SECURITY). O XML e seus
hashes foram preservados; amostras de SQL dinâmico/ProcessBuilder foram
inspecionadas sem dispor os demais alertas. Nos pacotes normal/shadow, nove
dependências corresponderam a nove componentes SBOM e nove POMs pinados.
Uma cópia com licença do SBOM adulterada e envelope re-hasheado foi recusada
pelo guard sem JDBC. A política P11 atual passou 33 contraprovas, mas não há
relatório de feed atual; a variante shadow 12.8.1 exigida só para testes está
na faixa afetada pelo CVE-2025-59250 corrigido em 12.8.2. P29/P11 seguem
sem aceite nominal, SAST integral governado, RC único e smoke físico/SOs.
[Relatório técnico](docs/catalogos/p29-sast-preparacao-20260928/RELATORIO.md)
e `STATES.md` separam prova executada de decisões pendentes. Nenhum checkbox
foi promovido e o UAC continua pausado.
Os guardas finais documentais passaram: Gitleaks e scanner offline em 4.036
arquivos/candidatos, zero achados; trilha 33 etapas/48 IDs abertos/nove
pacotes, UTF-8 estrito e `git diff --check` sem erro. Não equivalem a
aceite de Segurança.

# P08/P29 — gate v79 nos bytes da fixture corrigida — 28/09/2026

`STATES.md` registra a prova canônica: `clean verify` JDK17 offline v79 saiu
0 com 2.351 Surefire/cinco skips, seis ITs offline, formatter/Checkstyle e
JaCoCo 80/60; PMD manteve 36 achados brutos, disposição local v79 36/36 PASS.
O scanner offline passou com zero achados após correção de uma fixture. A/B
shadow 12.8.1 v79 reproduziram revisão, manifesto e ZIP; oito comandos puros
de JARs extraídos, seis recusas dirigidas e 25 guardas de envelope passaram.
O pacote normal 12.8.2 preservou seu lock e dois comandos puros passaram.
Sem SQL, DLL carregada ou novo UAC. P07/P08 físicos e aceites externos de
G02/P29 seguem abertos; nenhuma caixa integral foi promovida.

# P08/P29 — URL de processo validada e pacote 12.8.1 A/B offline — 28/09/2026

Decisão do Supervisor adotada: o laboratório exige `V2_SHADOW_JDBC_URL`
validada para `localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth, TLS e timeouts
limitados; a escolha de certificado vem da URL, sem fallback estático. `run`
e `worker` recusam antes de controle/SQL, e o supervisor passa ao filho só a
URL validada entre `V2_*`. `status`/`resume` preservam readback offline.
`clean verify` v78 em espelho saiu 0 (2.351 Surefire/cinco skips, seis ITs
offline, JaCoCo 80/60), PMD v78 manteve 36 achados brutos e disposição local
36/36 PASS, sem aceite nominal. Dois ZIPs 12.8.1 finais reproduziram revisão,
manifesto e SHA; oito comandos puros extraídos, seis recusas dirigidas e 25
guardas gerais passaram. O lock 12.8.2 normal ficou intacto e recebeu
regressão offline. [STATES.md](STATES.md) conserva pins/recibos; o
[runbook](docs/runbooks/p08-shadow-12.8.1-preflight-20260928.md) registra
preflight físico preparado. Sem SQL, DLL carregada ou novo UAC; P07/P08
físicos e aceites G02/P29 seguem abertos.

# P08 — par JDBC/JAR físico 12.8.1 preparado, sem SQL — 28/09/2026

Uma variante candidata `PackageShadow` pinou JAR 12.8.1.jre11 e DLL
12.8.1.x64, com POMs/SBOM/proveniência próprios; o lock normal 12.8.2 foi
preservado e recebeu regressão offline. O supervisor agora recebe `lib/*`
antes de seu preflight `master`, e o launcher recusa pacote 12.8.2 no caminho
físico antes de iniciar Java. Dois builds/ZIPs A/B 12.8.1 tiveram hashes
idênticos; oito comandos puros dos JARs extraídos, quatro recusas dirigidas e
25 guardas de envelope passaram. `STATES.md` e o
[runbook](docs/runbooks/p08-shadow-12.8.1-preflight-20260928.md) trazem pins,
evidências e comandos preparados. A URL estática do laboratório P08 ainda
precisa de decisão do owner técnico/Segurança frente à URL opt-in da IT Maven.
Nenhuma DLL/SQL/UAC foi executada; P07/P08 físicos e aceites externos seguem
abertos.

# P08 — CLI offline do JAR extraído A/B, sem aceite físico — 28/09/2026

O wrapper P08 ganhou `config-validate` e `dry-run` sobre `Main`, após
verificação do manifesto, com a configuração deny-all incluída no pacote;
nenhuma flag shadow, DLL ou classe JDBC entra nesse caminho, e `V2_*` herdadas
são removidas do processo filho. O README separa os comandos puros e suspende
os comandos físicos porque o lock contém DLL 12.8.2 e o perfil shadow exige
12.8.1. Dois ZIPs candidatos A/B são byte-idênticos (188 membros, nove
dependências); ambos foram extraídos e os quatro comandos CLI saíram 0,
sem stderr, com LOCAL_SHADOW, fontes/auditoria desligadas e deny-all. Duas
recusas dirigidas e 25 guardas de envelope passaram. `STATES.md` registra
hashes/logs e a prova limitada. P08 físico, A/B de supervisor, rollback,
sucessão e selo seguem abertos; UAC não foi repetido.

# P07/P08/P10/P29 — UAC em pausa, mídia inspecionada offline — 28/09/2026

O usuário orientou **não repetir UAC até o operador estar pronto**. A
autorização do alvo `localhost/ETL_SISTEMA_V2_SHADOW` permanece; nenhuma
extração elevada, instalação, conexão SQL ou DDL foi executada nesta rodada.
A inspeção read-only da mídia Microsoft encontrou 201 CABs/201 arquivos,
sem nomes de travessia, mas com nomes planos duplicados. O 7-Zip 26.03
standalone extraiu apenas amostras em `target/`; o `SETUP.EXE` amostrado tem
assinatura Microsoft válida e **não foi executado**. A árvore de instalação
completa não foi comprovada por esse método. O runbook agora traz sequência
exata para extração autorizada após UAC, preflight no `master` e recuperação
de estado incerto. P07/P08 físicos seguem sem prova, P10/G02 e P29 mantêm
os aceites externos próprios; nenhum checkbox foi promovido.
Trilha, scanner offline, Gitleaks em espelho privado, UTF-8 estrito e
`git diff --check` passaram nos documentos finais, com recibos em `target/`.

# P07/P08/P10/P29 — autorização local e P08 A/B candidato offline — 28/09/2026

O usuário autorizou instalar SQL Server local e criar exclusivamente
`localhost/ETL_SISTEMA_V2_SHADOW`; a sombra antiga continua em outra máquina
e não foi acessada. A mídia oficial SQL Server 2025 Express x64 passou
tamanho, SHA-256 e assinatura Microsoft. A extração acionou UAC antes de
qualquer instalação e foi cancelada; token atual não elevado, zero serviços,
SQL ou DDL. O runbook registra instância `MSSQLSERVER`, rede desabilitada,
collation, quotas, preflight `master` e recuperação. P07 físico continua
dependente da elevação e dos gates materiais.

O controlador P08 separou `Package`/`PackageDirected` offline do perfil
shadow: dois builds candidatos com driver global 12.8.2 e DLL 12.8.2
**passiva**, conferida pelo lock, saíram 0. Pacotes A/B de 187 membros têm
mesma revisão, manifesto e SHA-256 ZIP; extração segura A validou 189 entradas.
O estado continua `PACKAGED_NOT_SMOKE_QUALIFIED`: o launcher do pacote ativa
perfil shadow com DLL 12.8.2, proibida pela decisão do Supervisor, portanto
não foi executado. P08 final precisa de solução de lock/runtime 12.8.1,
IT/SQL, smoke, guardas e aceites. `STATES.md` contém hashes e recibos;
nenhum checkbox P01–P33 foi promovido.

# P07/P08/P10/P29 — v66 final offline, shadow reconstrução pendente — 28/09/2026

`STATES.md` registra a prova canônica e o [mapa P01–P33](docs/continuidade/qualificacao-p07-p33/mapa-p01-p33-20260927.md)
mantém os critérios e owners. Nos bytes finais v66, `clean verify` Java 17
offline saiu 0: 2.348 Surefire, cinco skips, seis ITs offline, zero falhas;
JaCoCo `bootstrap` 4.384/5.457 linhas e 1.942/2.853 ramos, limiares 80/60
intactos. Perfis `shadow-local-integration` e
`shadow-migrations-windows-auth` resolvem driver/DLL 12.8.1; global 12.8.2
permanece. O shadow opt-in v67 compilou e copiou só a DLL 12.8.1 a
`target/native`, sem URL, JDBC ou SQL. PMD bruto manteve 36 achados; a
disposição local 36/36 passou contra 278 XML e 2.348 casos v66, sem SAST ou
aceite nominal. A sombra anterior está em outra máquina e não foi acessada;
não há serviço/instância local conferida, e a decisão específica do usuário
para instalar/criar `localhost/ETL_SISTEMA_V2_SHADOW` está pendente via
Supervisor. A campanha P07/P08 física de 22/09 não se transfere. O lock P08
12.8.2 diverge do build Package que ativa o perfil shadow 12.8.1; pacote
físico/smoke requer reconciliação. G02 remoto/CI no novo SHA e P29 nominal
seguem com owners. Nenhum checkbox P01–P33 foi promovido.

# P07/P08/P10/P29 — v60 offline e POMs pinados corrigidos; gates externos abertos — 28/09/2026

`STATES.md` registra o critério, a prova e os limites; o
`docs/continuidade/qualificacao-p07-p33/mapa-p01-p33-20260927.md` contém as
33 etapas por critério original. O espelho v60 tem 1.313 arquivos `src/`,
`pom.xml` e nove POMs de terceiros conferidos por SHA-256, sem `.env`.
`mvnw -o clean verify` com Java 17 saiu 0: 2.348 Surefire, cinco skips e
seis ITs offline sem falhas/erros; JaCoCo `bootstrap` preserva
4.384/5.457 linhas e 1.942/2.853 ramos contra 80/60. A guarda nova recusa
drift de bytes nos nove POMs publicados, após reproduzir/corrigir cinco
conversões CRLF→LF que tornavam o pacote P08 inválido. A contraprova
selecionada de correspondência do pacote passou sem SQL. PMD 7.17.0
executou dez regras/656 fontes, manteve 36 alertas brutos visíveis; a
disposição técnica dos 36 passou com 2.348 testes/48 vínculos, sem SAST
integral ou aceite nominal. Scanner offline e Gitleaks locais passaram antes
do checkpoint, zero achados; Graphify atualizado. P07/P08 físicos, G02
remoto e RC/P29 continuam sem aceite; nenhum checkbox integral foi promovido.

# P10/G02 — gate Ubuntu local v56 verde; shadow e remoto abertos — 28/09/2026

O mapa P01–P33 foi atualizado por critério em
`docs/continuidade/qualificacao-p07-p33/mapa-p01-p33-20260927.md`; `STATES.md`
é a autoridade de evidência/aceite. Nesta revisão, `clean verify` JDK 17 saiu 0:
2.348 Surefire, cinco ITs offline, zero falhas/erros, cinco skips. JaCoCo
`bootstrap` passou 80/60 com 4.384/5.457 linhas e 1.942/2.853 ramos. Os
1.313 arquivos `src/` e `pom.xml` são byte a byte iguais ao espelho v56;
scanner offline final passou em 4.022 candidatos, zero achados; Gitleaks na
árvore limpa final também saiu 0, zero achados. Graphify foi atualizado
(37.108 nós, 95.003 arestas) e a validação documental passou. Shadow físico
permanece sem configuração para prova no checkpoint 0300; PMD
fixado não estava no cache local. P07/P08 de 22/09 seguem históricos.
P10/G02 não recebeu aceite: faltam shadow local e owner/publicação/checks
remotos em novo SHA. Não houve push, merge, deploy, SQL ou cutover.

# P10/G02 — checkpoint 0299 de fechamento documental; gates abertos — 25/09/2026

`STATES.md` e checkpoint 0299 reconciliam recibos/processos e 45 inputs
idênticos ao espelho v31; nenhum código/POM/teste ou efeito remoto foi
repetido. `clean verify` v31 continua exit 1 apenas no JaCoCo `bootstrap`
(0,642 linhas/0,558 ramos contra 0,80/0,60); 2.318 Surefire e duas ITs
offline sem falhas/erros, cinco skips. Scanner/Gitleaks v32 passaram em 4.009
arquivos indexados; validador documental passou. Shadow IT não executada,
Graphify update local falho, HEAD remoto sem novo SHA/checks
verdes e owner G02 pendente. Nenhum aceite ou checkbox novo; sem push, merge
ou deploy. Próximo input técnico e externo exato no checkpoint 0299.

# P10/G02 — checkpoint 0298 de cenário SQL e oráculo puro; JaCoCo aberto — 25/09/2026

`LocalArtifactScenario$SqlExecution` separa metadata/captura/preview JDBC,
preservando preflight e decisão de resultado no Ubuntu. Pin/include shadow
e mutantes passaram. Oráculo puro provou chave de execução escopada,
ordinal e fronteira temporal ±1 ms. Espelho v31 executou 2.318 Surefire
e duas ITs offline sem falhas/erros, cinco skips; `clean verify` saiu 1
somente em JaCoCo `bootstrap` (0,642 linhas/0,558 ramos contra 0,80/0,60).
Scanner indexado passou em 4.007 candidatos; Gitleaks zero. `STATES.md`
e checkpoint 0298 trazem delta/limites. Shadow físico não executado; G02
aberto sem SHA remoto verde/owner ou publicação.

# P10/G02 — checkpoint 0297 de captura JDBC relacional; JaCoCo aberto — 25/09/2026

`LocalRelationalRuntime$SqlCapture` separa política/sessão JDBC da admissão
de dia, data, modo/replay e cancelamento, testada sem SQL. Pin, include
shadow, `.class` e mutantes passaram. Espelho v28 executou 2.316 Surefire
e duas ITs offline sem falhas/erros, cinco skips; `clean verify` saiu 1
apenas em JaCoCo `bootstrap` (0,631 linhas/0,547 ramos contra 0,80/0,60).
Scanner final v29 Git indexado passou em 4.007 candidatos; Gitleaks zero.
Inputs Maven v28/v29 foram comparados byte a byte.
`STATES.md` e checkpoint 0297 trazem delta/limites. Shadow físico não
executado; G02 aberto sem SHA remoto verde/owner ou publicação.

# P10/G02 — checkpoint 0296 de captura JDBC de usuários; JaCoCo aberto — 25/09/2026

`LocalAnalyticUsersRuntime$SqlCapture` separa a transação JDBC da admissão
modo/replay, testada sem SQL no Ubuntu. Fonte pinada, include shadow e
mutantes de fonte/POM passaram. Espelho v26 executou 2.315 Surefire e duas
ITs offline sem falhas/erros, cinco skips; `clean verify` saiu 1 apenas no
JaCoCo `bootstrap` (0,620 linhas/0,542 ramos contra 0,80/0,60). Scanner
final v27 Git indexado passou em 4.006 candidatos; Gitleaks zero. Inputs
Maven v26/v27 foram comparados byte a byte. `STATES.md` e
checkpoint 0296 detalham delta/limites. Shadow físico não executado; G02
aberto sem SHA remoto verde/owner ou publicação.

# P10/G02 — checkpoint 0295 de cadeia SQL do Supervisor; JaCoCo aberto — 25/09/2026

O corpo SQL de `runChild` foi separado na classe interna exata e o guarda
puro de opt-in foi testado sem SQL. Pins, POM e mutantes preservam a inclusão
no check shadow. O espelho v25 executou 2.314 Surefire e duas ITs offline,
zero falhas/erros, cinco skips; `clean verify` saiu 1 somente no JaCoCo
`bootstrap` (0,610 linhas/0,538 ramos, contra 0,80/0,60). Scanner Git
indexado passou em 4.004 candidatos; Gitleaks zero. Shadow físico não
executado. `STATES.md` e checkpoint 0295 têm o traço, delta, limites e
próxima ação. G02 aberto sem SHA remoto verde/owner ou publicação.

# P10/G02 — checkpoint 0294 de Supervisor/CLI offline; JaCoCo aberto — 25/09/2026

O harness offline validou reserva sem worker, retomada de falha sintética
selada e rejeição de log alterado. `QualificationSupervisor` ganhou 52 linhas
e 26 ramos no XML limpo. O espelho v23 executou 2.313 Surefire e duas ITs
offline sem falhas/erros, cinco skips; `clean verify` saiu 1 só em JaCoCo
`bootstrap` (0,597 linhas, 0,527 ramos contra 0,80/0,60). Scanner indexado
passou em 4.002 candidatos, Gitleaks zero. Shadow físico não executado.
`STATES.md` e checkpoint 0294 registram limites e próximo item. G02 aberto
sem SHA remoto verde/owner, push, merge ou deploy.

# P10/G02 — checkpoint 0293 de ranking causal e clean verify aberto — 25/09/2026

O espelho v22 limpo executou 2.313 Surefire e uma IT offline sem falhas/erros,
cinco skips históricos; `clean verify` saiu 1 só no JaCoCo `bootstrap`
(0,589 linhas, 0,519 ramos após exclusões exatas, contra 0,80/0,60).
Controle de cancelamento puro ganhou testes de nonce, motivo, prazo e barreira;
a transformação sintética de frete ficou no Ubuntu e o enriquecimento JDBC
teve pin/check shadow exatos. Scanner indexado passou em 4.001 candidatos e
Gitleaks teve zero achados. Shadow físico não executado. `STATES.md`, catálogo
causal e checkpoint 0293 contêm evidência e próximo item. G02 aberto sem novo
SHA remoto verde/owner, push, merge ou deploy.

# P10/G02 — checkpoint 0292 de verifier/executor/metadata; JaCoCo aberto — 25/09/2026

O `verify` v21 executou 2.309 Surefire e uma IT offline sem falhas/erros,
cinco skips históricos, mas saiu 1 no JaCoCo `bootstrap` (0,565 linhas e
0,508 ramos após exclusões exatas, contra 0,80/0,60). Verifier, executor e
metadata têm SQL separado por classe interna pinada, mantendo caminhos puros
no Ubuntu. Scanner no espelho indexado passou em 3.996 candidatos e Gitleaks
teve zero achados. Shadow físico não executado. `STATES.md` e checkpoint 0292
registram evidência e próximo item. G02 segue aberto, sem novo SHA remoto
verde/owner, push, merge ou deploy.

# P10/G02 — checkpoint 0291 de classes físicas; JaCoCo aberto — 25/09/2026

O espelho v19 indexado reproduziu 2.303 testes Surefire e uma IT Failsafe
offline sem falhas/erros, cinco skips históricos; `verify` saiu 1 apenas no
JaCoCo `bootstrap` (0,51 linhas e 0,46 ramos depois de exclusões exatas,
contra 0,80/0,60). O scanner passou com 3.995 candidatos e Gitleaks teve zero
achados. Fontes mistas e classes internas SQL têm manifesto fail-closed;
os caminhos puros permanecem no Ubuntu. O teste shadow físico não executou.
`STATES.md` e checkpoint 0291 contêm evidência e próximo item. G02 segue
aberto, sem SHA remoto verde/owner, push, merge ou deploy.

# P10/G02 — diagnóstico de CI e JaCoCo ainda aberto — 25/09/2026

No HEAD remoto `b9dac416737ac69c98d0e63715411b7c62fa03f7`, `verify` e
`secret-scan` falharam; `dependency-audit` foi skipped. O candidato v10 teve
2.300 testes Surefire e uma IT Failsafe offline sem falha/erro, cinco skips
históricos, scanner indexado em 3.991 arquivos e Gitleaks sem achados. O
`verify` continua vermelho apenas na cobertura JaCoCo de `bootstrap`.
`qualificacao` passou seu gate Ubuntu após exercício puro do pacote e
separação de uma classe SQL exata, que o gate shadow continua a exigir.
O teste físico shadow não foi executado. `STATES.md` e checkpoint 0290 têm
os limites e evidências; nenhum aceite G02, SHA remoto novo, push ou deploy.

# Matriz ESL — segunda rodada interrompida por HTTP 429 — 22/09/2026

Sob nova ordem read-only do usuário, a sonda ampliada preservou nomes técnicos
sanitizados e fingerprints de `/info` para 6908 (31/6) e 6389 (110/16).
Coletas teve uma página válida de três linhas/três entidades sob `per=3`.
Fretes respondeu HTTP 429 na quarta chamada; os sete templates seguintes
não foram consultados. O recibo classificou o corpo como JSON inválido, mas o
status 429 está registrado; a sonda foi corrigida offline para classificar
HTTP antes do corpo. O erro de desembrulho de array de um item também foi
corrigido e coberto por autoteste. Nenhum checkbox mudou: 67/115. Estado no
cabeçalho de `STATES.md`, checkpoint 0287 e recibo privado em `target/`.

# Matriz mínima dos nove contratos ESL — tentativa e correção — 22/09/2026

Uma rodada read-only pontual, sob ordem explícita do usuário, tentou o perfil
da matriz V2-025d com 18 chamadas máximas. Parou após quatro HTTP 200:
6908 apresentou metadata 31/6 e quatro linhas físicas com limite por `id`
verificável; 6389 apresentou metadata 110/16, mas a página foi recusada pelo
parser novo com `DATA_ENVELOPE_INVALID`. Sem payload salvo, o shape exato não
foi provado. O parser passou a aceitar objeto único/vazio, como o cliente
existente, e a validar `id` escalar/`per` de 6908/6389; autoteste offline
passou, sem replay da rede. Nenhum checkbox mudou: 67/115. Estado no cabeçalho
de `STATES.md` e checkpoint 0286; recibo privado sanitizado em `target/`.

# Paridade de identidade Coletas 6908 — 22/09/2026

A sonda read-only autorizada comparou duas travessias Data Export completas
(`per=50/100`) com GraphQL terminal numa janela fechada independente. Foram
cinco chamadas de oito permitidas, sem erro; quatro linhas físicas de uma
entidade em cada travessia, seguida de página vazia, e uma entidade GraphQL.
Chave natural e ID canônico coincidiram nos três conjuntos. Esta unidade de
sonda está concluída, mas nenhuma caixa V2-025d/V2-012/P17/P20/V2-041 foi
fechada; seus critérios excedem esta janela. Estado e limites no cabeçalho de
`STATES.md` e checkpoint 0285, recibo sanitizado privado em `target/`.

# Primeira observação real Coletas/Fretes — 22/09/2026

Sob decisão explícita do usuário de prosseguir read-only com a credencial
existente, três sondas `curl` serializadas concluíram: Coletas perfil inicial
5/5 HTTP 200, travessia 4/4 HTTP 200 com 326 linhas/252 entidades; Fretes
5/5 HTTP 200 com 410 linhas/400 entidades. Ambas as travessias pararam no
teto sem terminal, sem retry, sobreposição de `id`, SQL ou escrita. `/info`
6389 agora declara 110 campos e `id`, mas não `finished_at`; a baseline local
é sintética e exige release real antes de runtime. Nenhum P17/P20, V2-041,
paridade ou cutover foi fechado. Cabeçalho de `STATES.md` e checkpoints
0283/0284 são a referência; recibos sanitizados privados em `target/`.

# Próximo passo: primeira amostra antes de P17–P29 — 22/09/2026

A revisão da ordem de gates confirmou que P17–P29 dependentes de dados reais
são cobrados na campanha, não antes de sua primeira amostra. P07/P08 e B16
interno já têm prova local; não há trabalho local pendente elegível. A próxima
ação material é G01 de Segurança/Operações e, após comprovação e janela/teto
reconfirmados, a sonda `curl` allowlisted de Coletas 6908. Cotações 6906
continua rota posterior com autoridade própria; Java ESL também exige escopo
próprio. Nenhuma chamada, teste, checkbox ou contador novo. Estado
autoritativo no cabeçalho de `STATES.md`; checkpoint 0282.

# Smoke offline do JAR V2 — 22/09/2026

Após o pedido de iniciar testes, `config validate` e `dry-run` do JAR existente
passaram com JDK 17 e configuração exemplo: `LOCAL_SHADOW`, fontes e auditoria
desligadas, autorização operacional `deny-all`. Atestado V2-041/G01 ausente no
intake; nenhuma leitura ESL/SQL, aceite P17–P29 ou mudança de contador. Os itens
de qualificação alegadamente resolvidos não vieram com evidência. A triagem
posterior confirmou zero trabalho local elegível na matriz P07–P33; o próximo
input é dos owners externos. Resultado autoritativo no cabeçalho de `STATES.md`; checkpoint
`0281-smoke-offline-jar-v2.md`.

# ETL_SISTEMA V1 — teste de acesso delimitado em 22/09/2026

A pedido do usuário, foi testada somente a conexão local read-only a
metadados do `master`: alvo V1 existente, acesso disponível, sessão Windows
atual com papel `sysadmin` (1/1/1, `sqlcmd` exit 0). A verificação parou
antes de consultar qualquer objeto do V1 porque falta identidade read-only
aprovada; `etl_v2_view` tem negativa de acesso ao banco V1 registrada na
trilha anterior. Camada provada: conectividade e metadados, sem dado de
negócio, paridade, oráculo ou aceite B17–B29. A retomada exige owner SQL/V1
definir acesso de leitura delimitado e comparabilidade de grão/janela.
Resultado autoritativo no cabeçalho de `STATES.md`.

# Sequenciamento dos gates de dados reais — decisão do usuário em 22/09/2026

Os insumos de paridade que dependem de dados reais serão cobrados na primeira
campanha da entidade em que o V2 efetivamente ler dados reais em sombra.
Isso não dispensa P17–P29: os gates aplicáveis devem passar antes de publicação
autoritativa, sweep/apply de ausência ou cutover. A primeira leitura ainda
depende de V2-041/G01 e de autorização específica de canal/runtime, entidade,
janela, limites e alvo sombra. `ETL_SISTEMA` da V1 é candidato a referência,
mas seu uso exige autorização de consulta read-only com escopo e validação de
grão, tempo e divergências; esta decisão não autorizou conexão ao V1.
Nenhum aceite ou checkbox foi alterado. Estado autoritativo em `STATES.md`.

# Próxima conversa — habilitação futura de leitura ESL pelo V2

O handoff no cabeçalho de `STATES.md` fixa a próxima rota: V2-041/G01
permanece `EXTERNAL_HOLD` até atestado autenticado de rotação/invalidação e
continuidade do writer legado por Segurança/Operações. V2-025d, quando
desbloqueado e com janela/teto reconfirmados, cobre somente `curl` controlado.
Extração ESL real pelo JAR requer autorização própria de canal, entidade,
limites e alvo de sombra. B16 interno e o código local estão qualificados no
alcance registrado; B17–B29 e a sonda 4924 não receberam novo input/aceite.
Esta nota não concede autorização nem registra nova execução.

# Código de sombra — equivalência integral de bytes e gate fresco inconclusivo

Em 22/09/2026, a nova suíte física foi interrompida no teto registrado de
45 minutos após 91 classes de IT sem erro; as 246 tabelas preservaram as
contagens agregadas. A comparação SHA-256 de 1.281 arquivos `src/main` e
`src/test` e do `pom.xml` com a cópia histórica `pos0236-p07-verify-01/build`
foi idêntica. Essa cópia teve `verify` PASS, JaCoCo PASS, 2.263 unitários e
492 ITs, com rollback. Nos 172 arquivos SQL de banco comparados, somente o
validador read-only de Manifestos difere; ele passou contra V022 instalada.
Assim, o código de runtime desta revisão conserva prova integral por bytes
iguais, embora o `verify` fresco não tenha terminado. P17–P29 e 4924 não
receberam novo aceite. Evidência e limites no cabeçalho de `STATES.md`.

# B17 Cotações — continuação cURL sem terminal

Em 22/09/2026, a ordem read-only limitada para 6906 na janela 03/09
consumiu quatro chamadas de seis possíveis. `/info` e páginas 2–3 retornaram
HTTP 200; a página 2 teve 100 linhas/100 candidatos distintos, e a página 3,
62 linhas/60 distintos. A página 4 voltou HTTP 200 com envelope inválido para
o contrato local e encerrou a rodada sem retry. A mesma posição já falhara
em outra janela após página curta. Sem contrato versionado de terminal, tarifa
e oráculo independente, P17–P20 continuam abertos. O recibo sanitizado está
em `docs/continuidade/probes/2026-09-22-6906-continuacao-0309.md`.

# `.env` V2 na raiz por determinação do usuário

Em 22/09/2026, o arquivo privado único, com quatro chaves API e oito Raster,
foi movido de `.codex-local/.env` para `.env` na raiz; as quatro sondas e o
exemplo foram alinhados. O arquivo é ignorado e não rastreado pelo Git. O
scanner passou a aceitar somente esse caso exato, mantendo findings para
`.env` rastreado e `.envrc` ignorado; 20 autotestes e a varredura integral
passaram com zero findings. O finding inicial para `.env` na raiz está
preservado em `STATES.md`. Nenhuma chamada externa ou aceite B17–B20 decorre
da configuração.

# Configuração privada de API e Raster no V2

Em 22/09/2026, sob ordem do usuário, `.codex-local/.env` recebeu somente as
quatro chaves de API necessárias às sondas e as oito chaves Raster solicitadas,
tomadas da última definição não vazia do legado, sem exposição de valores.
`.env.example` contém apenas placeholders; as quatro sondas preferem o arquivo
privado e mantêm fallback legado. O scanner integral passou com zero findings;
o finding anterior para `.env` na raiz foi corrigido sem enfraquecer o scanner.
Configuração não é execução Raster, autorização produtiva ou aceite P17–P20.

# Sonda cURL 6906 — nova janela, paginação ainda não terminal

Em 22/09/2026, sob ordem read-only delimitada no cabeçalho de `STATES.md`,
o autoteste da sonda passou e duas chamadas cURL de 6906 retornaram HTTP 200.
O `/info` indicou 37 campos/seis filtros; a página inicial teve 101 linhas
físicas para 100 candidatos `sequence_code` distintos válidos no limite da
sonda. A ordem parou por teto não terminal, sem retry ou página seguinte.
Nenhum P17–P20 foi aceito; a fonte não forneceu oráculo independente, tarifa
aprovada, completude ou contrato/release oficial.

# B16 — cobertura interna de sombra das onze entidades

Em 22/09/2026, a política explícita do owner para B16 foi aplicada às oito
entidades ainda sem subaceite: Manifestos, Coletas, Fretes, Localização,
Faturas, Inventário, Sinistros e Raster condicional. A prova offline dirigida
passou 94/94 testes em JDK 17; a tentativa física anterior parou com 72 erros
na trava de opt-in, antes de conexão/validação. `STATES.md` registra o alcance
por entidade. Com Cotações, CAP e Usuários já aceitos internamente, a linha
agregadora B16 do checklist foi fechada somente para sombra. P16 canônico,
P17–P29 e os aceites reais permanecem abertos.

# B16 Usuários — aceite interno de sombra

Em 22/09/2026, a política explícita do owner para B16 foi aplicada a Usuários.
V2-033 e a ponte GraphQL current/history permanecem apenas em sombra; os testes
dirigidos `ExtrairUsuariosGraphQlTest` e `RuntimeUsersIntegrationTest` passaram
45/45 offline em JDK 17. A sublinha interna de Usuários foi fechada, sem alterar
P16/P17, oráculo nominal, completude do snapshot, paridade ou autorização externa.
Evidência e limites estão no cabeçalho vigente de `STATES.md`.

# Amostra Data Export — Cotações e Contas a Pagar sem aceite externo

A ordem read-only explicitamente autorizada de 22/09/2026 consumiu quatro
chamadas HTTP 200: `/info` e a primeira página de 6906 e 8636. A amostra 6906
tem 100 linhas físicas e `sequence_code` numérico/distinto nessa página; a de
8636 tem 86 linhas, sem `accounting_debit_id`, e 85 valores distintos para 86
ocorrências de `ant_ils_sequence_code`. Ela confirma a ausência de chave de
raiz de CAP e não prova contrato/release, tarifa, oráculo, paridade ou qualquer
aceite P16–P29. Recibo sanitizado: `docs/continuidade/probes/2026-09-22-6906-8636-amostra-readonly.md`.
Não repetir nem ampliar essa ordem sem condição e autorização próprias.
O teste dirigido `Test-CotacoesV2027ShadowVertical.ps1` passou depois da amostra,
validando a vertical V2-027 somente na camada local/rollback-only. Assim, a linha
local correspondente de B16 foi marcada em `BLOCOS_ETAPA_2.md`; contrato oficial,
tarifa e oráculo ainda impedem qualquer aceite da entidade.
Na sequência, uma observação sanitizada dos nove campos usados pelo mapper 6906
não encontrou incompatibilidade de presença/tipo, e
`CotacaoDataExportRecordMapperTest` passou 10/10 sob JDK 17. A segunda linha de
B16 foi marcada para o alinhamento técnico observado; não promove contrato,
completude, tarifa, oráculo ou paridade.
A ordem paginada seguinte recebeu HTTP 200 na página 2, mas a resposta excedeu
100 linhas físicas com `per=100`; ela parou com
`PHYSICAL_ROW_BOUND_EXCEEDED`. A expansão não tem grão/identidade verificável
sob essa ordem, portanto não abriu novo aceite nem deve ser repetida sem novo
contrato e ordem.
Na ordem corretiva posterior, o classificador local pré-testado confirmou que a
página 2 continha 112 linhas físicas para 100 `sequence_code` inteiros,
escalares e não nulos distintos; a página 3 tinha 49 linhas para 48 distintos.
Isso permite caracterizar expansão física apenas dentro do teto operacional,
sem promover a candidata a chave canônica. A página 4 respondeu HTTP 200 com
envelope de dados inválido, e a sonda parou com `DATA_ENVELOPE_INVALID` após
quatro das sete chamadas; páginas 5–7 não foram consultadas. Não há terminal,
completude, contrato, tarifa, oráculo, paridade ou aceite adicional. Recibo:
`docs/continuidade/probes/2026-09-22-6906-paginacao-expansao-envelope.md`.
Por decisão explícita do usuário, B16 de Cotações foi então concluída por aceite
interno de sombra, não por contrato do fornecedor. Ele consolida somente as
provas locais e observadas acima; mantém P17, tarifa, oráculo, completude,
paridade, publicação e produção bloqueados. Decisão:
`docs/continuidade/decisoes/2026-09-22-b16-cotacoes-aceite-interno-sombra.md`.
O validador de identidade 6906 passou e preserva os limites de tenant,
estabilidade, expansão, sweep e cutover.
O usuário também dispensou contrato/release oficial como pré-requisito genérico
de B16 para as demais entidades. Isso fecha a política, mas não transforma
nenhuma vertical em aceita: cada uma continua a exigir evidência técnica própria
para seu aceite interno de sombra. P17, paridade e produção não foram alterados.
Contas a Pagar é a primeira unidade individual concluída sob essa política: os
28 campos, raiz/parcela/rateio explícitos, filtro `issue_date + created_at` e
rollback-only já registrados para V2-029 foram requalificados pelo
`ExpansionLaboratoryContractTest` offline (15 testes, zero falhas/erros).
O aceite é somente interno de sombra; `accounting_debit_id` continua ausente na
amostra 8636, e `ant_ils_sequence_code` não é inferido como chave de raiz/linha.
P17, referência financeira, oráculo, paridade, publicação e produção continuam
bloqueados. Checkpoint 0270.

# Sonda financeira — caminho de Coletas corrigido

A sonda financeira concluiu 07/09 em seis chamadas, sem parada: Data Export e
GraphQL tiveram conjuntos de identidade iguais para 19 Coletas. A falha local
era uma chave nula em lista de atributos vazia e foi corrigida/testada sem rede.
Fretes e 4924 estavam vazios, portanto vínculo/receita/CT-e/Fatura e equivalência
financeira continuam sem prova de negócio. Checkpoint 0255 e recibo sanitizado:
`docs/continuidade/checkpoints/0255-sonda-financeira-caminho-corrigido.md` e
`docs/continuidade/probes/2026-09-22-financeiro-0709-sucesso-tecnico.md`.

# Sonda financeira — parada segura por volume de Coletas

Na data 01/09, a sonda financeira parou corretamente após duas páginas válidas
de Coletas porque a segunda não era terminal. Ela não chamou Fretes, 4924 ou
GraphQL. É limitação de volume da sonda, não erro da ESL. Checkpoint 0253 e
recibo sanitizado: `docs/continuidade/probes/2026-09-22-financeiro-pagina-limite.md`.

# Correção de escopo — GraphQL somente como auditoria transitória

O usuário confirmou que o V2 mantém Data Export como fonte. GraphQL é permitido
apenas como comparação de auditoria e não integra o runtime. Esta correção
substitui a restrição prospectiva sobre a etapa financeira; a sonda 6389 anterior
continua sendo, corretamente, evidência de Data Export sem GraphQL.

# Sonda Data Export — Coletas e Fretes aceitos sem GraphQL

Fretes (6389) também passou na janela 01–07/09 apenas com Data Export: cinco
chamadas, HTTP 200 e JSON válido, sem parada. Coletas e Fretes têm agora
evidência de contrato/paginação limitada à janela, não vínculo ou financeiro.
O 4924 fica fora desta trilha porque sua sonda permitida exige GraphQL para
comparar conjuntos. Checkpoint 0252 e recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-6389-7d-sucesso.md`.

# Sonda Data Export — Coletas aceita na janela de sete dias

Após validar localmente a sonda e os contratos, Coletas (6908) foi consultada
de 01–07/09 em uma ordem menor: cinco chamadas, três segundos de intervalo,
todos os status HTTP 200 e JSON válido, sem parada. A fonte voltou a aceitar a
consulta; isso não comprova paridade GraphQL nem cobertura global. Checkpoint
0251 e recibo sanitizado: `docs/continuidade/probes/2026-09-22-dataexport-6908-7d-sucesso.md`.

# Sonda Data Export — resultado desconhecido na segunda partição

A ordem independente de sete dias (08–14/09) não devolveu stdout, exit code nem
sessão ao controlador. O processo não ficou ativo e a sonda não persiste recibo,
portanto não se conhece o resultado remoto. A janela não será repetida nem terá
status inferido. Checkpoint 0250 e recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-7d-resultado-desconhecido.md`.

# Sonda Data Export — limite da fonte na janela de 30 dias

O pedido de amostra nos 30 dias fechados foi particionado porque a sonda limita
cada janela a sete dias. A primeira partição foi interrompida pela ESL com HTTP
429 na primeira leitura de dados, após metadado 6908 HTTP 200 e duas chamadas
totais. A parada foi imediata: não houve retry, Fretes, GraphQL, 4924, escrita,
banco, deploy ou corte. Checkpoint 0249 e recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-30d-rate-limit.md`.

# Sonda Data Export — consulta recente aceita, sem amostra de Fretes

Após revisar a documentação ESL, a sonda limitada do template 6389 para uma
data recente recebeu HTTP 200 e JSON válido. Não havia registros no dia, logo
o acesso e o formato foram confirmados, mas identidade e paridade continuam
pendentes. A recusa HTTP 422 anterior, em janela histórica, segue sem causa
estruturada; a regra histórica do fornecedor é apenas hipótese. Checkpoint 0248
e recibo sanitizado em `docs/continuidade/probes/2026-09-22-dataexport-6389-recente-vazio.md`.
Próximo insumo: data recente, fechada e sabidamente com Fretes. Sem escrita,
banco, GraphQL, 4924, deploy ou corte.

# Sonda Data Export — sucessão documental pendente

O check de whitespace não encontrou erro; o validador de continuidade recusou o
novo delta com `HANDOFF_PIN`. Os manifests e ledgers históricos ficaram
imutáveis, logo a falha foi preservada e exige sucessão documental própria. O
resultado da fonte continua: parada segura no HTTP 422, sem GraphQL, 4924,
escrita, deploy ou corte. Checkpoint 0247, SHA-256
`0ba9d7610490291592eda15b006716384d1201e806d34cf1ee22aa1faf24337e`.

# Sonda Data Export em sombra — parada segura em 22/09/2026

Sob autorização explícita do usuário, a sonda read-only de contrato usou 7/7
chamadas do teto próprio. Metadados de 6908 receberam HTTP 200, mas o primeiro
pedido de dados recusado recebeu HTTP 422; entidades e paginação não puderam ser
verificadas. A regra de parada interrompeu a rodada: GraphQL e 4924 não foram
chamados, sem retry, escrita, banco, deploy ou corte. Não há aceite novo nem
alteração de 39/45 e 67/115. Evidência sanitizada e checkpoint:
`docs/continuidade/probes/2026-09-22-dataexport-shadow-stop.md` e
`docs/continuidade/checkpoints/0246-sonda-dataexport-sombra-interrompida.md`
(SHA-256 `3883661bb6249a49cd024ddfdec67f612ecd7c1a5fa7bd54695a5c0419f32c28`).
Nova consulta depende de diagnosticar a recusa e registrar ordem própria.

# Encaminhamento vigente após a etapa 1 local

P09–P15: trabalho local elegível identificado esgotado, aceites externos pendentes. Próximo prompt é etapa 2 (P16–P29), nunca retorno automático à etapa 1 por checkbox aberto. Etapa 3 continua P30–P33. Ler [encaminhamento](docs/continuidade/tres-etapas/handoff/ENCAMINHAMENTO.md), [prompt 2](docs/continuidade/tres-etapas/handoff/ETAPA_2.txt) e [prompt 3](docs/continuidade/tres-etapas/handoff/ETAPA_3.txt). Selecionar fatias pelo delta e pelas dependências usadas, sem inventar trabalho ou liberar gates. Nenhum P recebeu aceite nesta manutenção; 39/45 e 67/115 preservados. STATE e checkpoint 0245 registram o alcance; as diretrizes abaixo permanecem históricas quando conflitarem com este encaminhamento.

# Diretriz vigente: finalizar em três etapas

O usuário adotou três etapas amplas: **1 = P09–P15; 2 = P16–P29; 3 = P30–P33**. Seguir [GUIA_E_PROMPTS](docs/continuidade/tres-etapas/GUIA_E_PROMPTS.md) ao preparar os próximos chats. Esta diretriz sucede a escolha de pequenos agrupamentos das seções 11 e 13; os detalhes por P continuam como dependências e critérios internos da etapa.

Pedidos como “dê o prompt da etapa 2” ou “da etapa 3” geram um único bloco pronto para copiar, preenchido com o STATES e os recibos atuais. Preparar o prompt não executa a etapa. Não exigir que o usuário escolha cada P, repita contexto ou peça continuação por tarefa. Preservar critérios, autorizações, limites e evidências; não prometer que toda operação termina em três chats ou que o tempo obrigatório de observação desaparece.

Conferência desta manutenção: tentativa documental anterior interrompida no teto e preservada; leitor de arquivos otimizado mantendo os checks. Resultado autoritativo em target/tres-etapas-20260922-01/closed-receipt.json, após sua execução. P07/P08 físicos não foram repetidos.

# Qualificação local P07/P08 e sucessão PASS

Provas físicas, pacote e validações documentais atuais conferidas. Relatório/matriz: docs/continuidade/qualificacao-p07-p33/. P09–P33 preservam critérios externos; 39/45 e 67/115 inalterados. Scanner/readback final e encerramento são comprovados pelos recibos privados da rodada, não pela fotografia desta trilha. Ver STATES e checkpoint0243.

# P07/P08 locais aprovados; concluir sucessão documental

Build, testes, cobertura, integridade, reprodução do pacote, JAR extraído, guardas, A/B, variantes e readback PASS nos bytes atuais. P09–P33 preservam critérios e inputs externos; 39/45 e 67/115 intactos. Ver STATES e checkpoint 0242. O fechamento documental exige seus próprios recibos.

Conferência posterior a 0242: correções documentais do leitor XML e do cálculo de revisão estão em execução; provas físicas permanecem intactas. Ver STATES e recibos privados da recuperação do compositor. Não há novo aceite enquanto a validação documental estiver pendente.

Leitor XML/revisão: 15 contraprovas PASS. Replay integral preservou nova recusa por Unicode no pipe; correção UTF-8 privada em execução, conforme STATES. Nenhum resultado físico foi alterado.

Recuperação do compositor concluída: 15 contraprovas estruturais, três de UTF-8 e replay integral03 PASS. Resultado físico derivado e matriz materializados; validar agora a sucessão/cadeia/trilha e o fechamento final, sem repetir provas físicas.

# P07 local aprovado nos bytes atuais

2263 unitários/4 skips históricos e 492 integrações/105 classes; build, cobertura, identidades e rollback PASS. P08 pronto, ainda pendente de execução. Ver STATES e checkpoint 0241. Nenhum aceite nominal novo.

# Unitários completos e gate técnico reconferido

2.263 casos unitários, zero falha/erro e quatro skips históricos. PMD composto PASS sobre 251 XML do build atual; 36 alertas brutos preservados. Recibo do scanner corrigido, mesmos 18 testes PASS. Integração física ainda ativa, sem PASS P07/P08 presumido. Ver STATES e checkpoint 0240.

# P07 integral em execução

Controlador privado corrigido após falha pré-Maven, preservada. Segunda reserva FULL ativa; nenhum PASS integral presumido. Ver STATES e checkpoint 0239. P08 depende dos gates completos de P07; P09–P33 conservam seus critérios e dependências.

# P07–P33 — segurança técnica local validada

285testes/77novos PASS;1172Java conferidos. PMD36alertas brutos com36disposições técnicas verificadas,sem supressão;40contraprovas PASS. Pré-flight SQL sombra PASS e ordem nova300/43200s reservados. P07físico/P08 ainda pendentes;G01 e demais critérios externos não promovidos. Busca explícita de documentação preservou provas reais existentes e confirmou falta do atestado. Ver STATES/checkpoint0238.

# Execução P07–P33 — contraprovas em qualificação

Reconciliação0236 PASS3841arquivos;88casos dirigidos RED com49falhas reproduzidas,sem erro/skip. Correções JDBC aplicadas;GREEN em execução. Nova ordem física própria preparada,nenhumSQL nesta fotografia. Disposição PMD específica/ADR0054 e matriz74linhas em preparação;P07/P08 ainda pendentes.39/45 e67/115 preservados. O objetivo permanece todos os critérios atéP33,sem converter inputs externos em PASS. Estado autoritativo:STATES.md;checkpoint0237.

Sucessão (10+15+18 contraprovas) e trilha PASS nesta rodada; scanner/readback finais registrados no recibo de fechamento após execução. Nenhuma aprovação física/nominal nova.

# Avanço técnico de segurança e resiliência — 22/09/2026

Oito defeitos concretos corrigidos após análise PMD10regras e revisão dos caminhos sinalizados: três perdas de causa em finally, dois statements não fechados em falha, permit perdido em recusa de conexão, fechamento Raster com causa substituída e Error HTTP tratado como retry.24regressões novas; Maven test offline final: 2186 casos,zero falhas/erros,4skips históricos. Enforcer,Spotless,Checkstyle e compilação com warnings fatais PASS. Nenhum SQL real ou fonte remota.

Gate local reutilizável em scripts/security/Invoke-LocalStaticAnalysis.ps1,com12contraprovas aprovadas. PMD analisou653Java pelas10regras;40alertas iniciais→37remanescentes,sem supressão. Triagem técnica explícita em docs/continuidade/avanco-seguranca/triagem.json;alerta não equivale automaticamente a vulnerabilidade e o gate permanece FINDINGS_OPEN. Não fecha SAST integral/V2-015c.

Delta Java exige nova qualificação física P07/P08 dos bytes alterados; resultados0233/0235 permanecem históricos. A suíte local desta rodada não reivindica JDBC,coverage físico,release ou corte.16inputs externos anteriores,39/45 e67/115 preservados. Nenhum aceite nominal novo. Unit01 falhou antes dos testes por seleção relativa do formatter; causa Windows/regex absoluto corrigida sem mudar POM,limites ou assertions. Unit02 preservada com2175casos/5erros por heap da JVM filha fora do teto512MiB (13regressões novas PASS),corrigido no ambiente privado;unit03 parou em EmptyBlock antes dos testes,corpo TWR corrigido;unit04 dos fontes finais.

Relatório: docs/continuidade/avanco-seguranca/RELATORIO.md. Fechamento documental consultável em validacoes.json e target/avanco-seguranca-20260922-01/closed-receipt.json quando existir;ledger físico anterior intacto.

# P09–P33 — conclusão das parcelas locais executáveis (22/09/2026)

Revisão por25etapas,11entidades,6dimensões,5fatos e19contratos concluída. Não foi demonstrado outro delta funcional local elegível; os requisitos reais/operacionais continuam delimitados por fatia e papel em docs/continuidade/entrega-p09-p33/matriz.json.16inputs externos anteriores preservados,mais os critérios já canônicos de G01,oráculos/histórico/ausência/consumidores/escala/SAST e G06–G08. Nenhum aceite nominal novo;39/45 e67/115.

Correção concreta P29: README atualizado para DLL12.8.2,alcance do feed e compatibilidade v1. Sucessor documental offline:2ZIPs idênticos7941140bytes/730membros,725membros sem mudança;SBOM/extração/inspect+3plans PASS. Não é novo P08 físico nem RC produtivo. P07/P08 de0233 reutilizados somente no alcance dos bytes preservados:2162unitários/4skips históricos,492IT/105classes. Verificação dirigida conferiu60XMLs únicos/363casos aprovados.

Executados agora:intakeG01 com19contraprovas,scanner self-test18,sucessão10+15,trilha e PMD offline2regras em652fontesJava,zero violações/erros. PMD não é aceite SAST integral. Duas falhas documentais por IBM850 foram preservadas;correção somente UTF8 no processo filho passou,sem ambiente global ou ajuste de limites. Falha inicial da reconciliação por escopo excessivo do snapshot também preservada/corrigida.

Nenhum Java,SQL,POM,migration,schema,dependência,assertion,timeout ou massa alterado. Maven foi usado apenas para PMD privado offline;suíteJava/JDBC não repetida. Sem rede,segredos,banco,COMMIT de domínio,produção,publicação,deploy ou corte. Ledger0233 CLOSED e intacto;nenhuma campanha física aberta. Relatório,matriz e resultado em docs/continuidade/entrega-p09-p33/;fechamento autoritativo e processos em target/conclusao-p09-p33-20260922-01/closed-receipt.json após readback.

# P09–P33 — execução local e revisão por fatia (22/09/2026)

Em execução: 3798 arquivos reconciliados com o fechamento0233,176 referências íntegras; nenhum delta posterior de runtime/schema. P07/P08 permanecem provas locais reutilizáveis, ledger anterior CLOSED e intacto. A primeira comparação chamou de runtime o snapshot completo; os quatro deltas documentais já selados foram identificados, sem mudança posterior. Recibos preservados em target/conclusao-p09-p33-20260922-01/.

Três frentes independentes conferiram11 entidades,seis dimensões,cinco fatos,19 contratos (18 externos+SQL-10 interno),ausência e operação. Até aqui não há defeito funcional adicional demonstrado. Correção concreta P29 em curso: README do pacote distingue DLL12.8.2,scan técnico versus aceite nominal e compatibilidade v1 versus onze entradas atuais. Sucessor documental do envelope será validado offline; runtime/JAR,schemas,fixtures e oráculos conservarão seus bytes/provas. Nenhum novo aceite físico ou nominal presumido.

G01 ausente no caminho de intake; Gitleaks não disponível no PATH. Os16 requisitos externos anteriores continuam; o escopo P09–P33 inclui também oráculos/janelas/histórico,ausência,manifestos de consumidores,escala e G06–G08. Não repetir a busca sem nova evidência.39/45 e67/115 preservados. Usuário pediu rapidez e testes locais; nenhuma fonte,segredo,banco,infraestrutura,publicação ou produção autorizada por inferência.

Fechamento documental pós0227 PASS:sucessão atual/cadeia histórica/trilha,autoteste do scanner,scans delimitados e UTF-8 conferidos. P07/P08 qualificados nos novos bytes;16inputs externos permanecem. Evidência:docs/catalogos/requalificacao-pos0227/validacoes.json.39/45 e67/115 inalterados.

LOCAL_REQUALIFICATION_P07_P08_PASS. P07 integral e P08 dos novos bytes concluídos no escopo local:2162unitários/4skips históricos,492ITs/105classes,build/cobertura PASS;pacotes byte a byte iguais,smoke,A/B,8variantes,8+21guardas PASS e agregados preservados.16inputs externos permanecem;39/45 e67/115. Relatório:docs/catalogos/requalificacao-pos0227/RELATORIO.md. Validação documental final ainda pendente.

Smoke01 interrompido antes do JAR/JDBC por pwsh duplicado no PATH privado; causa reproduzida e correcao offline RED/GREEN comprovada. Readback/processos PASS. P07 integral e ZIPs730membros identicos preservados;P08 ainda pendente. Checkpoint0231. Nova tentativa smoke02 dentro da mesma ordem,sem ampliar limites.16inputs externos e contadores intactos.

P07 integral pós0227 PASS:2162unitários/4skips históricos;492ITs/105classes,sem falhas/erros;build/cobertura,identidades exatas e readback PASS. Runtime sem alteração. P08 ainda condicionado à própria execução completa. Checkpoint0230;16inputs externos,39/45 e67/115 preservados.

Macroblocos3/4 reconferidos:23 artefatos íntegros,16 requisitos externos sem novo input suficiente; evidência e owners em docs/catalogos/requalificacao-pos0227/investigacao-inputs.json. Checkpoint0229. Nenhum aceite novo;P07 ainda em execução,P08 condicionado.

# Requalificação pós-0227 — pré-flight conferido

P07/P08 em execução autorizada local; nova ordem finita própria em target/requalificacao-pos0227-20260922-01/. Pré-flight PASS, agregados iguais, sem processos próprios ou pressão SQL sinalizada na amostra. Snapshot2079 arquivos de runtime sem drift. Falhas anteriores e16 inputs externos preservados;39/45 e67/115. Checkpoint0228; P07/P08 ainda sem novo aceite.

Validacao documental da rodada fisica PASS: falhas P07 preservadas, sucessao/cadeia/trilha e scans delimitados conferidos. A qualificada/C corrigido; B nao qualificado e P08 nao iniciado.16 requisitos externos permanecem;39/45 e67/115 intactos. Recibos:docs/catalogos/p11-fisico/validacoes.json.

Falha documental preservada: partial-closure-01 parou na composicao por typo Import-sourceText gerado nesta rodada. Corrigido para Import-Module; nao houve SQL nem promocao de aceite. Nova validacao documental02 usara recibos proprios.

# P11 — A qualificada, C corrigido; B executado com falhas preservadas

P11_NATIVE_PASS_FULL_VERIFY_FAILED. JDBC/nativo12.8.2:1 IT PASS e agregados iguais.
As duas rodadas passaram2162 unitarios,com4skips historicos. Full01:
492 ITs/105classes,1erro de lock apos rollback;JaCoCo PASS,Maven falhou.
Full02 foi interrompido apos79 ITs/22classes,2timeouts SQL em Manifestos;
nao completou VerifyPhysical. Escala e ManifestGates originais passaram
isoladamente (4+4casos),sem mudar codigo,asserts ou timeouts. Esses
diagnosticos nao substituem suite integral. P08 nao iniciou.
Readback final igual:0/0/453 auditorias,246tabelas/1816objetos;sem DDL/commit.
SQL sinalizou pressao fisica de memoria apos full02 (flag1),e flag0 apos
o diagnostico. Causalidade dos erros nao comprovada. Nao alterar terceiros.
Duas tentativas corretivas da rodada consumidas (native02 e full02);
sem renovacao automatica do teto/vigencia/limite. B permanece NAO_QUALIFICADO,
nao BLOQUEADO_POR_INPUT por ser uma falha tecnica de execucao.
Busca23 documentos/recibos:16 inputs externos nao comprovados nos artefatos
consultados,com origem e responsavel por papel. Nenhum nome/aceite inferido.
Relatorio:docs/catalogos/p11-fisico/RELATORIO.md.39/45 e67/115 preservados.
P09 nao reavaliado;sem fonte de negocio,producao,publicacao,deploy ou cutover.
Fotografias anteriores preservadas;validacao documental final em andamento.

# P11 — segunda falha SQL preservada; pressao de memoria observada

P11_SECOND_FULL_FAILED. Full02:2162 unitarios/4skips historicos;79 ITs
registrados em22classes,2 timeouts SQL em AnalyticLaboratoryManifestGatesIT.
Execucao interrompida apos falhas persistidas,apenas arvore Maven propria
conferida por PID/inicio/parent/attempt. Wrapper exit1,rollback confirmado;
readback dedicado igual ao inicial0/0/453,246tabelas/1816objetos.
Apos a falha,SQL declarou process_physical_memory_low=1;zero requisicoes
ativas/bloqueadas e transacoes read/write no alvo. Isso demonstra pressao
de recursos naquele instante,nao causalidade retroativa dos timeouts.
Diagnostico atual da classe original de quatro gates,sem mudar codigo/asserts/timeouts.
P07 nao qualificado;P08 nao iniciou. A local qualificada,C corrigido;16
requisitos externos sem novo aceite. Teto36000s/vigencia originais mantidos.
Evidencias:target/p11-fisico-20260921-01/;fotografias anteriores preservadas.

# P11 — diagnostico de escala PASS; nova suite integral em execucao

P11_P07_FULL_RECHECK_RUNNING. A classe original de quatro escalas passou
sem mudar codigo,asserts ou timeouts;rollback/agregados iguais. O erro da
primeira suite continua preservado e sua origem de bloqueio nao foi identificada
retroativamente. VerifyPhysical02 iniciado em p11-p07-verify-02,mesmo snapshot.
Plano sucessor pipeline-plan-02.json conserva anterior STOPPED e reduz
somente tetos futuros ainda nao reservados conforme0/1/6/7 etapas reais.
Teto36000s e vigencia originais mantidos;nenhum efeito ampliado. P08 depende
do novo PASS integral. Recibos:target/p11-fisico-20260921-01/diagnostic-summary.json.
39/45 e67/115 intactos;16 requisitos externos conservados.

# P11 — P07 interrompido por lock no readback; diagnostico local

P11_P07_LOCK_FAILURE_PRESERVED. VerifyPhysical01 executou2162 unitarios
(4skips historicos) e492 ITs/105classes:491 ITs passaram,1 erro SQL de lock
no quarto caso de RelationalLaboratoryScaleIT (repeticao256),linha142,
conferencia de contagens apos rollback. Escala1024 passou. JaCoCo PASS.
Nao qualificar P07 nem P08 com esta falha. Pipeline interrompido antes do pacote.
Wrapper confirmou rollback; readback dedicado preservou agregados0/0/453,
246tabelas/1816objetos. Diagnostico posterior:zero requisicoes ativas/bloqueadas,
transacoes e sessoes JDBC no alvo. Isso nao identifica retroativamente o bloqueador.
Classe original de quatro escalas em diagnostico reservado,sem mudar codigo,
asserts ou timeouts. Alvo exato,sinteticos/rollback,sem DDL/commit/producao.
Ledger:target/p11-fisico-20260921-01/;teto36000s e vigencia originais mantidos.
16 requisitos externos e39/45,67/115 conservados.

# P11 — JDBC/nativo qualificados; regressao integral em execucao

P11_NATIVE_QUALIFIED_P07_RUNNING. Pedido efetivo "conclua esses" adotado para A/B locais,
com localhost/ETL_SISTEMA_V2_SHADOW, sinteticos, rollback, sem DDL ou commit
de dominio. Ordem/limites/ledger proprios: target/p11-fisico-20260921-01/.
Nativo12.8.2 e JDBC12.8.2 passaram 1 IT, zero falha/erro/skip; DLL conferida
contra lock/Central, agregados antes/depois iguais (auditorias0/0/453).
Lock atualizou seis componentes e preservou POMs/licencas historicos.
P07 VerifyPhysical iniciado em copia isolada; ainda nao aprovado. P08 aguarda.
Busca solicitada:23 documentos/recibos relacionados aos16 requisitos externos;
provas reais existentes reaproveitadas, nenhum aceite nominal localizado.
Falha do executor native01 antes de SQL, diagnosticos offline01/02 e correcao03
preservados. Sem novo aceite:39/45 e67/115. P09 nao reavaliado.
Historico abaixo e a fotografia anterior; sucessao sera selada ao fechar a rodada.

Validação pós-P11: regressão unitária inteira, construção local e sucessão/trilha PASS. Matriz de rede corrigida por rodada;16 inputs externos e A/B físicos pendentes. Evidência: docs/catalogos/p11-regressao-local/validacoes.json.

# P11 pós-0220 — delta P07/P08 delimitado e matriz de rede corrigida

P11_LOCAL_REGRESSION_RECORDED. 2162 casos/247 classes, zero falhas/erros,
4 skips históricos; JAR/oito libs construídos localmente. Sem SQL/nativo,
VerifyPhysical/cobertura ou pacote qualificado executado; aceites P07/P08 são
históricos. A/B físicos dependem de ordem própria; C corrigido por sucessão.
16 inputs externos permanecem; FEED-ACHADOS já atendido no scan0220.
Relatório e matriz atuais: docs/catalogos/p11-regressao-local/.
39/45 e67/115, zero novo aceite. P09 sem novo input ou reavaliação.

Validações finais P11/trilha PASS; baseline técnica sem vulnerabilidade. Aceite nominal de Segurança não inferido. Evidência: docs/catalogos/p11-publico-corrigido/validacoes.json.

# P11 — dependências corrigidas e auditoria pública validada

P11_PUBLIC_FEED_PATCHED. Jackson 2.18.11/JDBC 12.8.2: 205 testes PASS,
NVD atualizado,13 dependências/zero achado,parser corrigido/seis regressões.
Sem novo aceite humano ou V2;39/45 e67/115. P10/P12/P14/P15/P21 mantêm
preparação. Relatório/matriz: docs/catalogos/p11-publico-corrigido/.
Próxima ação: aceite nominal da baseline por Segurança; SQL/nativo não executados.

Validação atual PASS na camada local; P11 mantém quatro achados reais abertos. Recibos: docs/catalogos/p11-cache-offline/validacoes.json. Matriz vigente: docs/catalogos/p11-cache-offline/matriz-atual.json.

# P11 — scan real local concluído, correção de achados pendente

P11_CACHED_SCAN_COMPLETED_FINDINGS_OPEN.13 dependências,4 achados,JSON/HTML e exit1 esperado
da política0.0; cache antigo sem aceite de frescor. Candidato JDBC13.4.0:
122 testes/16 classes PASS,sem alerta JDBC no cache;3 Jackson permanecem.
POM público e provas anteriores preservados. P10/P12/P14/P15/P21 continuam
preparados offline. P09 não reavaliado. Sem novos aceites39/45/67/115.
Ver docs/catalogos/p11-cache-offline/RELATORIO.md; próximo passo é obter
dependências corrigidas/feed e qualificar o candidato, sem repetir B56.

# Correção local pós0216 concluída

CORRECAO_LOCAL_POS0216. Trilha ampla e catálogo de frota PASS após corrigir a
validação entre revisões.14 contraprovas da sucessão +11 P06 PASS;13 da matriz
e24 da preparação PASS. P10/P11/P12/P14/P15/P21 preparados integralmente na
camada offline;17 requisitos externos com origem/owner-papel preservados.
Relatório: docs/catalogos/continuidade-pos0216/RELATORIO.md. Falhas antigas
continuam históricas; novo PASS não as reescreve.39/45 e67/115 sem mudança.
Próximo macrobloco: intake offline de input sanitizado novo; nenhum efeito
externo ou aceite é liberado. P09 não foi reavaliado.

# P10/P11/P12/P14/P15/P21 — preparação offline concluída

STATES registra seis frentes locais validadas e parcelas externas BLOQUEADO_POR_INPUT.
48 testes Java/10 classes PASS, gates estáticos e contratos/contraprovas PASS.
17 requisitos externos, 36 pins e dependências por dimensão estão em
[relatório da preparação](docs/catalogos/preparacao-offline-p10-p21/RELATORIO.md).
P10→G02; P11→FEED; P12→G05; P14→G03/G04; P15/P21→G04 e suas fontes aplicáveis.
P09 não foi reavaliado. Divergências históricas P06 e pin de frota preservadas,
sem regravar selo/manifesto. 39/45 e 67/115 inalterados; nenhum aceite V2.
Próximo macrobloco: intake offline de input sanitizado novo; nenhum efeito externo
elegível sem autoridade própria. A numeração histórica P08–P11 dos catálogos de
identidade não é a sequência P01–P33 desta trilha; o mapeamento vigente é P14.

Verificação final do recorte: 13 contraprovas, 36 pins, dez XMLs/48 testes
conferidos; scanners delimitados 3+292 arquivos sem achado. Preparação geral
PASS. O gate histórico da trilha continua vermelho em P06, preservado.

# P09/V2-041 — alcance offline concluído; G01 pendente (21/09/2026)

P09 concluiu a validação local de V2-041 e está
`BLOQUEADO_POR_INPUT`: o atestado sanitizado G01 não está presente e não foi
aberto qualquer `.env` ou segredo. O intake contratual passou; a integridade
P08 M/N foi reconferida (dez recibos, 724 membros e zero fonte). O scanner
auxiliar foi corrigido para decodificar caminhos Git UTF-8, recebeu contraprova
Unicode e passou 18 autotestes; a varredura final passou com 3.680 candidatos,
zero achado e zero arquivo não inspecionado/excessivo.

`gitleaks` não existe no ambiente, portanto não houve scan canônico de
worktree/histórico, instalação ou equivalente manual. Segurança e Operações
devem fornecer G01: atestado sanitizado com referência restrita autenticada,
as seis classes/consumidores, continuidade do writer, invalidação, rollback e
três scans, além do Gitleaks aprovado para as duas varreduras locais. Nenhum
resultado local desbloqueia V2-041, V2-025d, rede, release, deploy ou cutover.
39/45 e 67/115 permanecem inalterados. Ver
`docs/catalogos/p09-v2-041/RELATORIO.md` e checkpoint0214.

Os validadores de intake, scanner e preparação passaram. A trilha ampla mantém
a falha histórica `P06_SUCCESSION_HASH_docs/runbooks/continuidade-agentes.md`;
P09 não alterou o runbook nem reescreveu o manifesto/selo P06 para ocultá-la.

# P08 M/N — fechamento local selado (21/09/2026)

M/N concluídos no escopo local: pacote ampliado de 724 membros, A/B e oito
recusas via JAR extraído, regressão, scanner, autotestes, selo e readback
passaram. Evidência em `p08-mn-*-23` e relatório
`docs/catalogos/campanhas-integrais/P08-MN-FECHAMENTO-20260921.md`. Permanecem
vedadas inferências de produção, paridade real, cutover, revisão humana ou
aceite dos pais V2; 39/45 e 67/115 não mudam.

# P08 — divergência do guard resolvida no runtime; M/N continuam abertos (21/09/2026)

O pacote com724 membros era válido, mas o runtime ainda limitava o manifesto a
512 membros e o inventário a514 arquivos. `QualifiedPackage` passou a expor o
contrato único de1.024 membros e1.026 arquivos. A falha histórica
`QUAL_JSON_ARRAY` permanece preservada como diagnóstico; o candidato corrigido
retorna `QUAL_JSON_MEMBERS` para `missing-member`.

Os testes focados passaram26 casos sem falha/erro. O build
`p08-runtime-member-limit-build-17` produziu candidato de724 membros; seus21
guardas extraídos passaram com `childrenCreated=0` e `jdbc=NOT_STARTED`.
Não houve SQL, leitura de `ctl`, fonte ou produção. M/N não foram aceitos:
contraprovas, smoke, guard de controle, scanner, selo/readback e sucessão seguem
pendentes. A/L e os contadores39/45 e67/115 não mudaram. Ver POS0216, ledger
`p08-runtime-member-limit-offline-ledger-17` e checkpoint0210. P07 e V049
seguem imutáveis e fora do macrobloco; as fotografias abaixo são históricas.

# P08 — pré-flight bloqueado antes do pacote (21/09/2026)

P08 recebeu a ordem física independente `p08-pacote-supervisor-selagem-20260921-01`.
O alvo autorizado foi confirmado ONLINE no `master`; os agregados de schema foram
246 tabelas e 1.816 objetos. A linha de base agregada de auditoria não pôde ser
completada, porque uma relação de auditoria esperada não estava disponível no
alvo. A regra da ordem encerrou a única reserva de pré-flight (1.200 s) e fechou
o ledger sem pacote, extração, Maven, JAR, supervisor, A/B, smoke, guards ou selo.

P08 está **BLOCKED_PREFLIGHT_AUDIT_AGGREGATE_UNAVAILABLE**; A/L/M/N continuam
abertos, C–K e os contadores39/45 e67/115 permanecem históricos. Ver relatório
`docs/catalogos/campanhas-integrais/P08-PACOTE-SUPERVISOR-POS0208.md`, ledger da
rodada e checkpoint0208. Uma execução futura requer nova ordem e contrato de
agregados de auditoria verificável; não repetir esta tentativa.

# P07 — Replay corrigido e VerifyPhysical PASS; P08 requer ordem própria

P07 `p07-replay-pos0207-verify-01` passou uma única vez após corrigir a linhagem
BOOTSTRAP de REPLAY, sem alterar V049:2.162 unidades (4 skips históricos),492 ITs
sem falha/erro/skip, rollback e agregados preservados,zero processo próprio. P08
não iniciou e só é elegível sob ordem futura própria. A/L/M/N abertos;39/45,67/115
inalterados. Ver relatório, ledger e checkpoint0207.

# P07 — VerifyPhysical falhou; P08 bloqueado

A ordem P07/P08 POS0205 consumiu sua única VerifyPhysical P07:
`p07-pos0205-verify-01` encerrou exit1, rollback confirmado e zero PID próprio,
mas Failsafe encontrou dois erros `EXP_PLAN_REPLAY_ORIGINAL_REQUIRED` em
AnalyticScenarioRuntimeIT e QualificationReplayIT. P08 não iniciou. O contrato
P07 exige verify verde, logo nenhuma requalificação C–K, aceite L ou pacote M/N
foi inferido. Contadores seguem39/45 e67/115. Ver
docs/catalogos/campanhas-integrais/P07-P08-POS0205.md e checkpoint0206; nova
Physical requer ordem finita depois da correção offline de linhagem REPLAY→BOOTSTRAP.

# P06 — fechamento offline; pacote P07–P08 preparado

Revisão P06 concluída:3achados médios corrigidos e1baixo de compatibilidade preparado.
68testes dirigidos+18preflight canônico PASS; helpers10 checks offline, scanner17,
remoções5 e sucessor11 contraprovas. As duas pendências históricas de integridade
foram tratadas por evidência exata; gates dos bytes finais em final-validation/*complete*,
closure.json e review-manifest.json da rodada P06-REVISAO-POS0202-20260920T225525774Z.

P07 tecnicamente elegível após readback; efeitos físicos P07/P08 exigem nova ordem
com alvo/vigência/orçamento. Nenhum uso do saldo POS0198. Consumo físico zero.
Novo JAR requer requalificação; aceites anteriores preservados como históricos.
A/L/M/N continuam EM_EXECUCAO,39/45 e67/115 inalterados, sem seloN ou produção.

Checkpoint0205 SHA-256 000b1a0dd6246d9184647666e7cdebd4b6283a723e7daad20ba7ef0927ff939b; relatório docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.
P06_REVIEW_OFFLINE_COMPLETE; sucessão conserva0203 c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Prefácios seguintes são fotografias históricas.

# P06 — revisão técnica concluída; requalificação física pendente (20/09/2026)

P06_REVIEW_OFFLINE_COMPLETE. Três achados médios corrigidos (causa de rollback,
remoções históricas no scanner e sucessão) e compatibilidade dos helpers preparada.
68 testes dirigidos e18 preflight canônico PASS, sem perfil/ambiente JDBC;
scanner17contraprovas, remoções5, sucessão11 e trilha completa preliminar PASS.
Readback final: target/P06-REVISAO-POS0202-20260920T225525774Z/closure.json e final-validation.

P07 tecnicamente elegível após gates finais; efeitos físicos exigem ordem própria
com alvo, vigência e orçamento. Nenhuma reserva física, SQL/JDBC/fonte ou DDL nesta
rodada. Novo JAR requer requalificação C–K; aceites antigos e recibos preservados.
L inclui regressão P07 e não recebe aceite integral; A/M/N seguem abertos.
39/45 construção e67/115 aceites inalterados. Nenhum selo A–N/revisão humana/produção.

Relatório: docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.
Checkpoint0204 SHA-256 a2e33d720076e67baf1d05da854c1968db427ae3d6ad0e8f30f7d0bcdbfce1a3. Sucessão vincula0203:
c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Fotografias abaixo preservam resultados e falhas anteriores.

# P06 — correções offline verificadas; integridade final em validação

P06_REVIEW_OFFLINE_COMPLETE refere-se à revisão técnica; gates finais de integridade em validação.
68 testes dirigidos e18 preflight canônico PASS, scanner17contraprovas e remoções5PASS.
Nova sessão/JAR exige P07 físico sob ordem própria; L/A/M/N continuam abertos.
39/45 e67/115 preservados. Consumo físico zero. Checkpoint0203 SHA-256 c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Relatório: docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.

Prefácios seguintes preservam fotografias históricas.

# Trilha — P04/I–J e P05/K aceitos no escopo local (20/09/2026)

O macrobloco POS0198 terminou: preflight canônico 18/18 no teto original
de 240 s; P04 20 ITs/20 unidades; P05 quatro escalas 2/4/8/16 e quatro
unidades, todos PASS. I, J e K ACEITO_NO_ESCOPO na matriz A–N, com recibos
completos, rollback e agregados preservados, bytes conferidos e zero processos.
Consumo 2/3 campanhas, 7.200/10.800 s; P04 #2 não usada. Estados autoritativos
e limites no prefácio vigente do STATES; evidência no catálogo POS0198.

39/45 e 67/115 inalterados. P05 local não fecha V2-050/P27 representativo.
P06–P08 permanecem sem execução nesta ordem. As lacunas históricas de
sucessão da trilha e oito ausências do scanner foram preservadas e registradas.
Fotografias anteriores abaixo não descrevem o aceite vigente.

# Trilha — POS0198: P04/I/J aceitos; P05 em execução

P05 reservada22:19:54Z–23:19:54Z após revalidar alvo/aceite/ledger/processos.
Preparação test-only8/8 PASS: parada serial após falha e agregados por escala.
Escalas2/4/8/16 ainda em execução, sem aceite K por inferência. Consumo2/3
campanhas,7200/10800s; P04#2 não utilizada. Limites,39/45 e67/115 preservados.

# Trilha — POS0198: P04/I/J aceitos no escopo shadow; P05 elegível

P04#1 PASS:20IT/20unidades,cinco XMLs íntegros,exit0/sem timeout,rollback e
246contagens preservados,zero processo próprio. Sucesso/cancelamento7etapas;
falha tardia5etapas com33/33/33/33/0. Máxima etapa31.072s; sequências abaixo
1800s. Fonte/banco2076arquivos conferidos. I/J ACEITO_NO_ESCOPO local,
conforme p04-acceptance.json e STATES; nenhum paiV2 ou contador histórico fecha.

P05 agora elegível após preparação da parada serial e dos agregados por escala,
prova offline e nova confirmação de alvo/ledger. Ainda sem reserva P05.
Consumo1/3campanhas; máximo original2P04+1P05,sem reutilizar reservas.

# Trilha — P04/P05 POS0198: preflight PASS; qualificação P04 em curso

STATES mantém a autoridade:72testes offline e preflight canônico18/18 PASS,
exit0/sem timeout no teto240s; controlador histórico inalterado. Duas causas
separadas corrigidas: contraprova explodida implícita sob Failsafe/JAR e
resolução repetida de ancestrais Windows. Binding/guards preservados e testados.

Ordem POS0198 adotada21:34:04Z, expira2026-09-22T21:34:04Z. Master local
confirmado; P04#1 reservada21:52:44Z por3600s, cinco ITs/duas travas/sintéticos
rollback-only. Consumo1/3; I/J IMPLEMENTADO_NAO_QUALIFICADO até prova integral.
P05 NOT_RESERVED_NOT_EXECUTED, condicionado ao aceite I/J nesta ordem.
39/45 e67/115 e fotografias históricas preservados. Relatório:
[POS0198](docs/catalogos/campanhas-integrais/P04-P05-POS0198-20260920.md).

# Trilha — P04/P05 pós-0196: guard corrigido; preflight bloqueia Physical

Ordem `P04-P05-POS0196-20260920-01` registrada com vigência absoluta48h e
saldo físico intacto. O guard do controlador agora lê exclusivamente os XMLs do
build isolado; sua matriz efetiva10/10 passou, preservando a recusa127 para os
cinco XMLs históricos com supervisor5/1/0/0. A asserção real do supervisor foi
executada2/2 offline/JDK17, incluindo33/33/33/33/0 e quatro mutações recusadas.

O preflight canônico da revisão corrigida (`ArtifactDirected`, JDK17,
heap512MiB,240s) fechou `exit124/timedOut=true` durante
`PackagedFixtureBindingIT`. Diagnóstico adicional: seleção Failsafe direta usa
o JAR como classe explodida e invalida a contraprova por fingerprint igual; a
seleção Surefire correspondente excedeu240s e foi contida como árvore própria.
Não houve SQL, JDBC, Physical nem reserva. I/J continuam
IMPLEMENTADO_NAO_QUALIFICADO; P05/K permanece inelegível e não executado.
39/45 e67/115, oito ausências e históricos preservados. Consultar
`P04-P05-POS0196-20260920.md`, ledger e checkpoint0197 antes de novo efeito.

# P02 — diagnóstico offline pós-0194 concluído, sem aceite I/J

STATES é a autoridade. Checkpoint0196 e
`docs/catalogos/campanhas-integrais/P02-DIAGNOSTICO-POS0194.md` retificam a
leitura de2405.848s: total de cinco testes, máximo individual758.817s;
recibos de sequência abaixo1800s e etapas abaixo240s. Não houve aumento de
limite, nova reserva ou Physical; o ledger histórico permanece intacto.

Asserção histórica33 no terminal bloqueado é defeito test-only; recibo confirma
33/33/33/33/0. Guard127 é defeito de caminho sourceRoot/build e não foi alterado:
a raiz correta encontra cinco XMLs e ainda recusa a falha real do supervisor.
Contraprovas documentais, javac17 e Spotless offline/JDK17 passaram; não se
executou o helper Java nem a integração. Sucessão histórica continua falha.

I/J IMPLEMENTADO_NAO_QUALIFICADO; P05/K sem reserva/execução e bloqueado por
precedência.39/45 e67/115 preservados; oito MISSING_CANDIDATE permanecem.
Correção mínima e prova offline estão delimitadas no relatório; nova Physical
depende de autoridade finita nova e preflight da revisão corrigida. P03–P08
não foram executados. Prefácios abaixo conservam a fotografia anterior, cuja
interpretação temporal fica explicitamente retificada por P02.

# Regra de execução — uma entrada e uma saída (reforço20/09/2026)

Uma entrada do usuário → ler a documentação e evidências → implementar,
integrar, testar, diagnosticar e corrigir → uma entrega final consolidada.
Não pedir “continue” ou autorização repetida entre etapas já cobertas. Não
encerrar com uma correção local apenas proposta quando ela pode ser executada.
Teste falho exige investigação e correção técnica, não uma nova passagem ao
usuário. Checkpoints/atualizações são internos ao trabalho e não exigem resposta.
Esta orientação permanente do STATES rege também retomadas e futuros macroblocos.
Limites explícitos e barreiras de segurança permanecem; dependência externa real
precisa de evidência, e progresso parcial nunca recebe aceite fictício.

# Trilha — P04/I-J pós-0194 não qualificado; P05 não executado

`P04-P05-POS0194-01` consumiu sua única reserva P04 local depois de preflight
ArtifactDirected18/18 PASS e confirmação do alvo sombra. A campanha terminou
sem timeout, com rollback agregado e sem processo próprio, mas não cumpriu o
critério integral: o supervisor levou2405.848s, acima de1800s, e os XMLs
isolados registram uma falha em cinco testes (expectativa33, observado0 no
cenário de oráculo tardio). O guard
também retornou127 ao procurar relatórios no workspace em vez do build isolado.
Esses fatos impedem aceite mesmo com quatro ITs sem falhas.

A expectativa test-only do terminal bloqueado foi ajustada após o fechamento
(preview vazio,33 nos anteriores), sem reexecutar a prova. A tentativa estática
parou antes da fonte pela incompatibilidade do Maven/JVM25 com o formatter fixado;
a reserva acabou e o excesso de sequência/guard de caminho continuam impeditivos.

I/J seguem IMPLEMENTADO_NAO_QUALIFICADO; P05/K não foi reservado/executado e
P06+ permanece fora do escopo. Não repetir a tentativa nem transferir sua
reserva.39/45 e67/115 não mudam. Evidência: `P04-P05-POS0194.md`, ledger
`target/P04-P05-POS0194-01/` e checkpoint0195; prefácios abaixo são históricos.

# Trilha — macrobloco P04→P05 pós-0193: P04 sem aceite, P05 não executado

A ordem finita `P04-P05-POS0193-01` consumiu as duas tentativas P04 no alvo
sombra autorizado. Preflight JAR/fixture/supervisor18/18 passou e quatro ITs
parciais mais16 unitários passaram nas campanhas; contudo o XML obrigatório de
`QualificationSequenceSupervisorIT` não foi produzido em nenhuma tentativa
contida. O controlador local foi corrigido e testado offline para transformar
essa ausência em falha127, sem aceitar o exit do wrapper. Rollback/agregados e
processos próprios foram reconciliados após cada contenção. I/J seguem
IMPLEMENTADO_NAO_QUALIFICADO; K/P05 não iniciou e P06 não é elegível. Não há
terceira P04 ou conversão de saldo.39/45 e67/115 permanecem. Consultar
`STATES.md`, checkpoint0194 e ledger privado antes de nova autoridade.

P04 em continuidade: corrigir fixture/JAR e provar offline; prosseguir com a
campanha local finita proposta na resposta anterior, sem nova reconfirmação,
registrando reserva própria e preservando ledgers fechados. Sem P05/produção.

# P04 — correção local testada18/18; qualificação física não fechada

Regra permanente de uma entrada/uma saída reforçada; defeito local foi corrigido
e testado sem nova pergunta. Autoria e supervisor do IT usam o mesmo runtime
JAR; driver de teste compartilhado, asserções propagadas, guard produtivo intacto.
p04-0190-bridge-final-offline PASS18/18, gates verdes,14 oráculos/duas sequências
e contraprovas. A única campanha física desta continuação foi consumida antes
da ponte final, com16 unidades/15 ITs PASS, sequência não qualificada e rollback
confirmado. Não repetir ledgers nem promover prova offline a aceite físico.
I/J IMPLEMENTADO_NAO_QUALIFICADO; P05/K não elegível,39/45 e67/115 inalterados.
Referência: P04-CONTINUIDADE-0190.md e checkpoint final desta rodada.

# P04 — prova física contida; correção do supervisor segue offline

BindingProbe demonstrou que o supervisor do IT ainda executava classes soltas
enquanto a fixture corrigida estava ligada ao JAR. Campanha própria fechada:
16 unidades/15 ITs PASS; sequência sem aceite, rollback confirmado, sem timeout.
A correção test-only prossegue sem pedir confirmação: mesmo JAR no supervisor
e na autoria, contraprova de propagação de asserção e verifyFiles completo.
Preflight p04-0190-bridge-offline em execução; I/J não aceitos, P05 não iniciado.

# P04 em continuidade — preflight17/17 e prova física em execução

SEQ-FIX-01 implementada somente na autoria de testes;14 oráculos ligados ao
JAR,3 adulterações recusadas. Preflight17/17/gates PASS. Campanha própria
`target/p04-continuidade-0190/ledger.json`: uma tentativa3600 s, mesmos limites
e alvo local, sem reutilização de reservas históricas. Physical em execução;
I/J não aceitos antecipadamente, P05 não iniciado,39/45 e67/115 inalterados.

# Trilha — P04: classpath provado, sequência bloqueada por runtime (20/09/2026)

Segunda tentativa da ordem P04-REQUALIFICACAO-20260919-01 consumida:16 unidades
e15 ITs PASS, incluindo retomada6/6 e quatro workers PASS_LOCAL com rollback.
Worker de sequência FAILED/LOCAL_SCENARIO_ORACLE_BINDING: oráculo vinculado a
classes descompactadas, worker vinculado ao JAR. Input/schema conferem.
Árvore própria encerrada; controlador observou rollback e agregados iguais,
logs íntegros, sem timeout ou processo remanescente. Duas tentativas totais,
nenhuma terceira; nenhuma correção adicional sem a respectiva qualificação.
I/J não aceitos integralmente; P05/K permanece não elegível.39/45 e67/115.
Próximo trabalho: corrigir a geração/vinculação da fixture em revisão nova,
validar offline e obter nova autoridade finita antes de qualquer prova física.
Consulte STATES e P04-I-J-RECONCILIACAO-20260920.md; históricos abaixo preservados.

# Trilha — P04 com causa corrigida offline; prova física pendente (20/09/2026)

O diagnóstico de 0187 foi retificado no STATES: leitura rasa de cinco stderr
comprovou QUAL_PACKAGE_RUNTIME_CLASSPATH, não timeout de etapa. Correção
proporcional e 16 testes offline PASS tornam elegível a segunda tentativa
condicional da mesma ordem P04-REQUALIFICACAO-20260919-01. Não há novo orçamento
nem autorização P05. I/J continuam não qualificados; 39/45 e 67/115 inalterados.
Históricos e ledger fechado são preservados; a continuação será registrada
em adendo, sem reaproveitar reserva consumida.

# Trilha — requalificação P04 bloqueada; P05/K não elegível (20/09/2026)

A ordem nova `P04-REQUALIFICACAO-20260919-01` não reutilizou o ledger fechado
anterior e consumiu uma tentativa P04. O preflight JDK17 foi 12/12 com os gates
de build verdes; o alvo local foi confirmado no `master`. A fase Physical criou
o JAR e as bibliotecas runtime antes do Failsafe. Unidades, sweep 5/5,
cancelamento 3/3 e concorrência 1/1 passaram, mas retomada ficou em 2/6, com
quatro exits 2, e a sequência excedeu seu teto de etapa sem recibo. A árvore
exata da tentativa foi interrompida, com rollback agregado igual antes/depois.

Não foi delimitada uma correção local proporcional e validável sem diagnóstico
recursivo de fixture proibido. Logo, a segunda tentativa condicional não foi
reservada, I/J não são aceitos e P05/K não iniciou. P03/B–H continua apenas
predecessor técnico local; P06–P08 continuam fora do escopo. Evidência:
`target/macrobloco-p04-requalificacao-20260919-01/ledger.json` e checkpoint0187.
Contadores permanecem 39/45 e 67/115.

# Trilha — P04 executado sem aceite; P05/K não elegível (20/09/2026)

O macrobloco adotado `P04-P05-APOS-0185-01` consumiu suas duas tentativas P04
locais. Ambas têm recibo conhecido e readback de rollback igual. A primeira
demonstrou que `Physical` não criava o JAR da fixture; a correção direcionada
foi validada offline. A segunda alcançou a criação do JAR, mas revelou que o
supervisor constrói o wildcard de classpath com `Path.resolve("*")`, inválido
no Windows; 4/6 retomadas falharam e o passo de recusa excedeu 240 s na coleta
recursiva da fixture. A correção e o contraprova unitária estão presentes, mas
não têm reexecução física coberta.

I/J permanecem **não aceitos localmente**; o sweep/preview parcial não substitui
worker, receipt selado, retomada e completude integral. P05/K não foi reservado
nem executado porque a precedência I/J não fechou. O saldo numérico do ledger
não transfere a autorização P04 falha a P05. P03/B–H conserva apenas o recorte
técnico aceito; 39/45 e 67/115 permanecem inalterados. P06–P08 seguem fora de
escopo. Consulte checkpoint0186 e ledger antes de qualquer nova autorização.

# Trilha — preparação integral P01–P33 disponível (19/09/2026)

Fotografia vigente: preparação local solicitada pelo usuário, **sem campanha física
nova e sem aceite P04/P05**. O [guia de preparação](docs/runbooks/preparacao-integral-trilha.md)
e o [mapa verificável](docs/catalogos/preparacao-trilha/plano.json) cobrem33 passos,
48 IDs abertos e entradas G01–G08/FEED. Consulte a seção14 antes de selecionar
outro macrobloco. O verificador offline passou com um positivo e24 negativos;
ele valida o mapa, não autoriza efeito nem substitui os gates históricos.

Retificação do bloqueio de saldo/vigência: a ordem original define limites por
sequência/etapa/campanha, mas não data final ou quantidade global numérica.
O prompt P04 manda conferir saldo/vigência e não renova a campanha. Ausência
de campo não prova expiração/esgotamento, nem autoriza tentativas ilimitadas.
Conferir a autoridade aplicável uma vez; se ainda faltar decisão, usar o pacote
finito já preparado no guia, sem pedir ao usuário que invente limites técnicos.
A proposta não está adotada. Preparação offline não fica bloqueada por saldo SQL.

Prioridade local restante: suficiência do predecessor P03 → P04/I–J → P05/K;
depois P06 e P07→P08, cada qual com suas condições. Reutilizar a causa temporal
comprovada. Conferir consumidores/revisão, não apenas hashes de testes; prova
in-process não substitui execução do JAR de P08. Fontes, governança e ambiente
têm filas de inputs preparadas, sem novas dependências artificiais entre elas.
39/45 e67/115 preservados; preparação não acrescenta percentual de construção.
Os prefácios abaixo são históricos, não ordens para repetir P01 ou outro hold.

# Trilha — P04: correção temporal offline; bloqueio de saldo/vigência (19/09/2026)

A fixture de sequência agora usa prazo lógico259200s, estritamente para cobrir a
janela civil sintética de11–14/08/2037 no tick de14/08 às12:00Z. A regressão
offline em `QualificationContractTest` mantém a recusa da configuração86400 e de
um tick posterior à nova fronteira, e admite as configurações de sucesso, falha
tardia e cancelamento. Build offline com JDK17, Enforcer, Spotless e Checkstyle
passou. A mudança não altera timeout, heap, SQL nem o contrato que recusa
deadline vencido.

P03/B–H foi vinculado aos recibos/pins vigentes e está ACEITO_NO_ESCOPO somente
como predecessor técnico da P04 local; ver
`docs/catalogos/campanhas-integrais/P03-B-H-RECONCILIACAO-20260919.md` e a
matriz A–N atualizada. P03 agregado, os demais fronts A–N e qualquer aceite real
permanecem abertos. P04/I–J não pode iniciar prova física: seis reservas P04 de
3600s são históricas observadas, mas não há saldo cumulativo nem vigência
auditáveis para uma sétima. O próximo trabalho elegível é somente receber e
conferir esse ledger/autorização, então reservar e executar P04 serialmente; P05
continua fora do escopo.39/45 e67/115 permanecem inalterados.

# Trilha — P04: bloqueio temporal anterior ao worker (19/09/2026)

Conforme a retificação no STATES e checkpoint0182, os três casos falhos de sql-06
foram DEFERRED/BLOCKED_DEPENDENCY antes de STARTED. Reprodução offline comprovou
LOGICAL_DEADLINE_EXCEEDED: prazo de um dia incompatível com o tick da fixture de
três dias. Cópia em memória com259200s permitiu os três planos (PASS_LOCAL/DUE),
sem executar worker/SQL. Correção de fixture e regressão ainda pendentes; não
concluir I/J nem atribuir a falha a classpath. Nenhum limite físico foi ampliado.

P03 conserva apenas o recorte aceito em0181; vincular critérios/pins B–H antes
de nova prova física P04. A matriz A–N não fornece ainda essa comprovação agregada.
39/45 construção (86,7%) e67/115 aceites (58,3%) preservados; nenhum percentual
representa produção. P05–P08 permanecem fora do escopo. Prefácios abaixo históricos.

# Trilha — P04 bloqueado por correção local (19/09/2026; diagnóstico histórico retificado acima)

P04/I–J não aceito. Após corrigir o classpath do worker para incluir as dependências seladas,
`p04-directed-10` compilou, mas `p04-supervisor-sql-06` terminou `exit=1`: 5 IT, 3 falhas,
0 erros/0 skips e rollback agregado confirmado. Sucesso, falha tardia e cancelamento ainda
retornam `exit=2`; recusa pré-SQL e owner vivo passaram. Preservar recibos e diagnosticar a
causa adicional antes de qualquer retry. P05–P08 continuam fora de escopo.

# Trilha — revisão 3.6: recorte P03 comprovado (19/09/2026)

Conforme STATES: sucessão MC e revisão tarifária corrigidas por V103/V104 qualificadas e instaladas somente em localhost/ETL_SISTEMA_V2_SHADOW. A/B sete etapas, referência três etapas e recomposição PASS; qualificação composta30unit/72IT distintos, rollback e bytes confirmados. A falha intermediária da contraprova(evidenceId) permanece registrada e foi resolvida por nova reserva da classe afetada, sem alterar relações/expectativas. [Relatório e recibos](docs/catalogos/campanhas-integrais/P03-CORRECOES-20260919.md).

P03 inteiro e paisV2 continuam abertos;39/45 e67/115 preservados. P04–P08 não iniciados. Próximo macrobloco: conferir explicitamente o saldo dos critérios P03 e então admitir P04(supervisor/preview), GPT-5.6 Terra/High; nova decisão semântica exige Astra/High. Não presumir o aceite do predecessor nem repetir a campanha aprovada sem mudança causal. Demais ordem/dependências/modelos P01–P33 preservados. Scanner/trilha históricos continuam falhos pelos motivos registrados, sem selagemP08. Novo prompt apenas quando solicitado.

As revisões/prefácios abaixo são históricos; os critérios e a tabela de dependências continuam vigentes.

<!-- Revisão3.5/checkpoint0180: regression01 reconciliada65PASS/1erro de evidenceId no teste novo; MatrixIT corrigida em prova02. Campanha07 PASS preservada. -->
<!-- Revisão3.5: campanha07 PASS30unit/6IT, A/B7etapas e referência3; rollback confirmado. Regressão dirigida em curso, checkpoint0179. P03 inteiro aberto. -->
<!-- Revisão3.5: V103/V104 instaladas localmente e qualificadas; checkpoint0178; campanha07 em curso, P03 não concluído. -->
<!-- Revisão 3.5 em execução: autorização P03 atual cobre V103/V104 locais; checkpoint0177. Instalação e provas funcionais pendentes. Fotografia3.4 abaixo preservada. -->
# Trilha de conclusão — prioridade, dependências e modelo GPT

**Revisão 3.4 — 19/09/2026. P01 reconciliado; P02 diagnosticado; recorteP03 bloqueado para evolução SQL.**


Resultado da estabilização solicitada: [relatório técnico](docs/catalogos/campanhas-integrais/ESTABILIZACAO-20260919.md), tentativa `sequence-campaign-sql-06`.27unit e2ITrecomposição PASS;2erros A/B por sucessão MC e2falhas de referência por SQL-05 vazio;rollback/agregados confirmados. Diagnóstico/asserções corrigidos, sem correção funcional SQL. P03 inteiro e paisV2 não concluídos;39/45 e67/115 preservados. A tabela abaixo conserva a ordem e os critérios; a fotografia05 é histórica.

Próximo macrobloco proposto: terminar este recorteP03 com sucessão relacional e revisão de tarifa, GPT-6 Astra/High. **Dependência concreta:** autorização explícita para preparar, qualificar e aplicar migrations aditivas somente no SQL local, com baseline e contraprovas; o pedido19/09 proíbe DDL/migrations. Sem essa mudança de escopo, P03 físico permanece bloqueado e P04 não é liberado. Sucessão finalP08/scanner integral continuam pendentes; não modificar manifests históricos nem restaurar as oito exclusões para contornar os gates. Não gerar prompt sem solicitação.

Checkpoint final0176: [diagnóstico e bloqueio SQL](docs/continuidade/checkpoints/0176-estabilizacao-diagnosticada-bloqueio-sql.md). Auditoria final preservou115checkboxes/67marcados, índice/schema/manifests; diff/UTF-8 PASS. Scanner integral FAIL apenas8exclusões preexistentes; validator histórico FAIL de sucessão no worktree. Provas detalhadas e snapshots em stabilization-20260919, sem selo finalP08.

**Quando você pedir o próximo prompt:** ler esta trilha junto com o `STATES.md`, selecionar um macrobloco coeso para o mesmo chat e indicar seu GPT/nível. O pedido copiável está na seção11; as regras de agrupamento estão na seção13. Não existe obrigação de trocar de chat ou gerar prompt a cada tarefa.

**Uma entrada e uma entrega final por prompt:** o usuário inicia o macrobloco uma vez; o executor trabalha até o resultado ou bloqueio comprovado, sem pedir continuação por etapa. A entrega final consolida todo o escopo executado.

Este roteiro foi reorganizado a partir dos critérios de `Tarefas pendentes` e da ordem obrigatória do [STATES.md](STATES.md), confrontados com a campanha técnica em andamento. **P01–P33 substituem os IDs ECO das versões anteriores**, que eram planejamento e não foram executados por esta trilha. Não são novos blocos funcionais nem novos aceites do projeto.

O objetivo é terminar o trabalho que falta, reaproveitando a construção já comprovada. A ordem das tarefas vem antes da escolha de modelo. O roteiro não concede autorização de fonte, banco, infraestrutura, release ou produção.

## 1. Ponto de partida histórico da revisão — atualizar pelo prefácio vigente

| Situação | O que fazer com ela |
| --- | --- |
| Campanha A–N `EM_EXECUCAO`, [checkpoint técnico 0168](docs/continuidade/checkpoints/0168-recomposicao-e-falhas-provadas-campanha-em-correcao.md) | Fechar essa campanha antes de abrir outra frente de construção local abrangente. Os checkpoints 0169–0171 são documentais. |
| Base0165/schema102 registrados; 39/45 unidades de construção e 67/115 checkboxes | Preservar. Os números medem coisas diferentes; não são percentual de prontidão produtiva. Os 48 checkboxes abertos incluem pais e subitens. |
| `sequence-campaign-sql-05/result.json`: OBSERVED, exit1, rollback confirmado | A tentativa tem resultado conhecido. Os XMLs registram quatro IT, duas falhas e dois erros. Ainda é preciso reconciliar a revisão testada com a autoria atual; não repetir automaticamente a tentativa. |
| WORKLOG registra mudança posterior para sete etapas | O resultado da tentativa05 pertence ao snapshot anterior. Faltam provas da revisão atual, quatro escalas, pacote e fechamento. |
| Validador histórico falha em `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`; scanner integral anterior apontou oito `MISSING_CANDIDATE` | Problemas já existentes e registrados. P01 classifica a sucessão e as exclusões; P08 só fecha com evidência válida da revisão entregue. Preservar alterações do usuário e manifests históricos. |
| Verticais básicas, expansões, seis dimensões, cinco fatos e 19 saídas têm construção local registrada | Checkbox agregado aberto não significa “implementar tudo de novo”. Confrontar com o contrato real e alterar somente o delta necessário. |
| G01–G08 continuam com parcelas externas pendentes | Receber os inputs indicados na seção 4. Nenhum modelo substitui fornecedor, owner, ambiente ou autorização. |

Fontes da retomada: [AGENTS.md](AGENTS.md), [contexto global](../CONTEXTO_GLOBAL.md), [RETOMADA](docs/continuidade/RETOMADA.md), [protocolo](docs/runbooks/continuidade-agentes.md), [contrato da campanha](docs/catalogos/campanhas-integrais/CONTRATO.md), [matriz A–N](docs/catalogos/campanhas-integrais/matriz-a-n.json) e [gates externos](docs/catalogos/campanhas-integrais/ENTRADAS-E-EFEITOS-EXTERNOS.md). Recibos completos ficam em `target/macrobloco-campanhas-integrais-20260915-01/`.

## 2. Como seguir a ordem

1. **Precedência local: P01 → P02 → P03 → P04 → P05 → P06 → P07 → P08.** Começar no primeiro resultado ainda pendente da fotografia vigente, não reiniciar P01 a cada chat. P02 pode ser dispensado se a causa mecânica já estiver demonstrada; registrar a justificativa. Não repetir entrega comprovada na revisão correta.
2. **Depois: escolher a menor prioridade P09–P33 que esteja elegível para o escopo.** A coluna “Depende de” determina a precedência. Um bloqueio não permite pular sua dependência, mas permite executar outra linha independente.
3. **Instanciar por entidade, fato ou contrato.** Exemplo: `P17/Coletas`, `P20/Cotações`, `P24/MAT-04`. Um resultado de Cotações não libera Fretes; uma pendência de Inventário não bloqueia o que não o utiliza.
4. **Solicitar os inputs externos desde P01**, pelo quadro da seção 4, sem gastar chats repetidos para reinventariar o mesmo bloqueio. Esta orientação prepara a lista para o usuário; não autoriza enviar mensagens a terceiros.
5. **Concluir cada etapa pelo resultado de saída.** Registrar revisão, camada, evidência, limitações e próximo P elegível. Só marcar o ID canônico no STATES quando seu critério original inteiro estiver atendido.

Nos passos repetíveis, dependências significam **a fatia usada**, não toda a tarefa agregada. Uma dependência não aplicável precisa de motivo aceito; não se apaga a linha. Preparação documental pode anteceder o efeito, mas não conta como execução ou aceite físico.

## 3. Prioridade imediata — fechar a campanha local

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P01 — Reconciliar estado, revisão e bloqueios locais** | **Terra / Medium** | Nenhuma etapa nova; leitura dos documentos obrigatórios | Comparar checkpoint0168, WORKLOG, inputs, resultados04/05, relatórios e autoria de sete etapas; conferir processos próprios antes de repetir efeito. Inventariar a sucessão documental e as oito exclusões preexistentes; separar divergência legítima de arquivo perdido. Listar apenas inputs externos ainda faltantes. | Checkpoint técnico atualizado com revisão atual, provas reaproveitáveis, falhas conhecidas, lacunas de integridade e próximo teste permitido. Não restaura arquivo, altera índice ou reescreve hash histórico por inferência. Inicialmente read-only/documental; qualquer efeito posterior conserva seus gates. |
| **P02 — Diagnosticar a falha restante** | **Astra / Medium** | P01 | Investigar a causa atual em `IntegralCampaignIT`/`SequenceReferenceIT`, capturas, revisões de suplemento, releases, relógio e oráculos. Separar defeito do harness de defeito do produto. Não presumir que a correção da04 resolveu a05. | Causa demonstrada, correção delimitada e contraprova que preserva o contrato. Se houver decisão crítica não resolvida, subir a High com questão específica. Se a causa já está provada em P01, registrar P02 dispensado e seguir. |
| **P03 — Corrigir e qualificar a sequência atual** | **Terra / High** | P01; P02 quando necessário | Completar as lacunas B–H: entrada, executor, modos, agenda, referências, recomposição, oráculos e isolamento. Provar campanhas A/B na autoria atual de sete etapas, mantendo tetos e ordem Coletas→Fretes. | Provas dirigidas e IT aprovadas da mesma revisão; cinco fatos/19 saídas por etapa, replay/referências/recomposição e falhas observadas. Não mudar o esperado apenas para acompanhar a implementação. |
| **P04 — Fechar supervisor e preview** | **Terra / High** | P03 | Frentes I/J: journal, cancelamento, recibos parciais, admissão de retomada, isolamento e 33 previews. Reaproveitar mecanismos existentes. | Sucesso e recusas demonstrados, rollback confirmado, evidência parcial preservada. Preview não concede apply; transação perdida não vira recovery durável de domínio. |
| **P05 — Medir as quatro escalas locais** | **Terra / Medium** | P04 | Frente K: executar as quatro escalas admissíveis e medir heap, lote, linhas em voo, SQL e duração no caminho novo. Corrigir somente gargalo comprovado. | Recibos por escala e limites cumpridos. Amostra sintética limitada não prova SLO nem platô produtivo. Defeito de desenho vai a Astra Medium; depois retorna para implementação delimitada. |
| **P06 — Revisar o delta técnico antes do pacote** | **Astra / Medium** | P05 | Frente L, parte de revisão: conferir invariantes de transação, identidade, oráculos, compatibilidade e suficiência das provas A–K. Revisar somente o delta e as dependências atingidas. | Achados tratados; correções voltam à prova afetada. Não transferir falha local para G01–G08 nem alegar revisão humana. |
| **P07 — Executar o gate final da revisão** | **Terra / Medium** | P06 | Frente L, regressão: build, testes, formatter/lint/análise estática e verificações existentes exigidas para a revisão final. Separar skips previstos de prova ausente. | Gates aplicáveis aprovados e vinculados ao código que será empacotado. Nova alteração relevante invalida a prova afetada e exige sua revalidação. |
| **P08 — Executar pacote, selar e fechar A–N** | **Terra / High** | P07 | Primeiro M: executar JAR extraído/supervisor, A/B e recusas. Depois N: diff/overlay, matrizes, scanner, sucessão legítima, selo/readback e continuidade. Usar Luna apenas para resumo de evidência já decidida. | Artefato executado e entrega local íntegra, com A–N rastreáveis. Resolver os bloqueios locais de integridade na revisão atual, preservando históricos/exclusões legítimas. Não declarar entrega selada se o gate pertinente continua vermelho. Atualizar STATES → trilhas → validadores → checkpoint. |

**Resultado de P08: entrega local da campanha.** Não fecha automaticamente os pais V2-012/013/035/036/037/038/039/050 nem qualquer aceite real.

Correspondência da campanha: **A→P01; B–H→P02/P03; I/J→P04; K→P05; L→P06/P07; M/N→P08.** G acompanha os consumidores C–F. A evidência anterior válida pode satisfazer uma frente; a matriz `EM_EXECUCAO` não deve ser promovida só por existir código.

## 4. Inputs que precisam ser preparados desde o início

Este quadro deriva dos gates já existentes. Reutilizar seus catálogos e pendências; não criar nova coleta burocrática. Responsáveis são papéis, até haver nome/time confirmado.

| Prioridade do input | Quem fornece | Artefato que falta | Libera |
| --- | --- | --- | --- |
| **Primeira: G01 / V2-041** | Segurança e Operações | Atestados de rotação/invalidação, consumidores e saúde do writer legado; autorizações próprias por fonte | P09 e, depois, chamadas autenticadas/operacionais cobertas. Não bloqueia P01–P08 sintéticos. |
| **Primeira: G03, por fonte** | Fornecedor e owner de dados | Identidade/grão/tipos/cardinalidade, filtros, snapshot/completude, oráculo, janela e limites | P13–P20 da entidade. Antecipar pedidos difíceis de CAP/FAT/INV/SIN/Raster sem sondar fonte não autorizada. |
| **Primeira: G04, por consumidor** | Negócio e donos de referências/saídas | Baseline de referências, regras fiscais, série NFS-e, frota, ausência; manifesto nominal das 18 saídas externas e escopo da 19ª interna | P14/P15/P21–P26. Não consultar o projeto de dashboards para inventar o contrato. |
| **Antes de CI: G02** | Owner do repositório | Remote/provedor, aprovadores, proteções, autorização de publicação | P10. Não bloqueia correção offline ou sonda que tenha autorização própria. |
| **Antes do efeito material: G05 / V2-045b** | DBA, Operações, Segurança e Compliance | Ambiente V2 dedicado, principals, TLS, storage, retenção ratificada, backup/restore, RTO/RPO, quota/janela e autorização por efeito | P12 e execuções reais P15–P29 pertinentes. Não converter autorização rollback-only em permissão de COMMIT/crash/restore. |
| **Antes do feed: V2-015d** | Responsável de segurança | Feed/NVD autorizado, aceite de baseline e tratamento nominal de achados/exceções | P11; obrigatório antes do release/corte. Não bloqueia sonda ESL independente só por ser “rede”. |
| **Na convergência: G06/G07/G08** | Donos da operação, consumidores e corte | Pacote, rota, Tcut, executores, janela, aceites de ensaio/corte, observação e retirada | P31/P32/P33, respectivamente. Autorizações são distintas. |

## 5. Preparar as condições reais, sem criar dependência falsa

P09–P13 podem ser preparados enquanto a campanha local termina. **Efeitos externos continuam condicionados ao input e à autorização exatos.** A prioridade numérica desempata tarefas elegíveis; P10 não precisa esperar P09, nem P13 precisa esperar a construção inteira do ambiente se sua sonda for somente em memória.

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P09 — Fechar rotação e continuidade** | **Terra / High** | P01; G01 | V2-041: conferir o procedimento coordenado e os atestados por classe/consumidor; rotação efetiva pelos responsáveis conforme autorização. | Invalidação anterior e continuidade comprovadas, evidência sanitizada e limites válidos para a próxima fonte. Remover texto de segredo do código não substitui rotação. |
| **P10 — Ativar governança remota** | **Terra / Medium** | P01; V2-016a já entregue; G02 | V2-016b: publicar somente o conjunto aprovado; remote, protections, CODEOWNERS e CI/Gitleaks no SHA remoto autorizado. | Histórico escaneado, checks obrigatórios executados e aprovadores reais. Teste local não recebe o nome CI. |
| **P11 — Aceitar baseline de vulnerabilidades** | **Terra / Medium** | P01; política/implementação locais já prontas; autorização do feed | V2-015d: executar gate real, classificar achados e tratar exceções pelos responsáveis. Reusar a política fail-closed. | Baseline aceita e evidência de correção/exceção válida. Decisão de segurança inédita vai a Astra High e ao aceitante competente. Não exigir conclusão desta etapa para toda consulta independente. |
| **P12 — Qualificar ambiente e fundação operacional** | **Terra / High** | P09 e P10 para fechamento operacional; G05; subgates locais V2-042/022b já comprovados | V2-045b/V2-039a: conferir banco V2 dedicado, schema fresh/upgrade quando autorizado, identidade/grants, TLS, retenção, storage, backup/restore, RTO/RPO, config, scheduler e alertas. Qualificar os critérios próprios do runtime V2-022 necessários às execuções seguintes. | Ambiente e capacidade operacional no escopo autorizado; provas materiais separadas das provas rollback-only. Não fechar V2-022 agregado só porque V2-022b está marcado. Se sua agenda/recuperação ainda falha, corrigir antes do consumidor dependente. Não é V2-038 final nem startup produtivo automático. |
| **P13 — Reconfirmar contratos de fonte** | **Terra / High** | P09; G03 e autorização da fonte/rodada | V2-025d: rodada serial conforme allowlist e limites para as nove requisições Data Export; atualizar fingerprints e matriz de campos. Aceitar contrato válido já fornecido na fatia apropriada, sem repetir sonda desnecessária. | Contrato por entidade com tipos, filtro, janela, paginação, identidade candidata e lacunas explícitas. A agregada025d só fecha ao cumprir toda a rodada requerida. Usuários continua GraphQL ratificado; Raster tem contrato/autorização próprios; não inferir template 9901. |

P12 precisa estar apto **antes de receber dados reais ou executar prova material no alvo**. Isso é diferente de exigir infraestrutura produtiva para testes sintéticos locais já autorizados. Se P12 estiver bloqueado, P13/P14 e a preparação documental de outras frentes podem avançar dentro do seu alcance.

## 6. Da fonte à paridade core — repetir por entidade

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P14 — Fechar identidade e decisões semânticas pendentes** | **Astra / High** | P13 na entidade, ou contrato versionado equivalente válido; prova G03/G04 | V2-009b/8636/4924/10633/6392 e V2-009c. Decidir raiz/filho, título/documento, crosswalk, tenant, rekey, grão e precedência fiscal quando aplicável. Não reabrir identidade já aceita sem drift. | Decisão sustentada, casos de colisão/cardinalidade e ADR/contrato quando necessário. Se o problema é falta de evidência, registrar exatamente qual; não inventar chave. Uma identidade inequívoca já aceita dispensa novo diagnóstico Astra. |
| **P15 — Ratificar referências usadas** | **Terra / High** | P12 para importação material; G04 | V2-035a: qualificar baseline autorizado, releases, vigência, proveniência, calendário, filial, frota e tarifas das famílias realmente usadas. | Referências ratificadas e verificadas no escopo, sem seed/default produtivo inventado. **Não inclui o fechamento das dimensões derivadas**, que ocorre em P21. |
| **P16 — Adequar e provar a vertical em sombra** | **Terra / High** | P12 para efeito material; P13; P14 quando pendente; P15 somente para referência usada | V2-009d, V2-029/030/031/032/034b e deltas das verticais básicas se houver drift. Comparar contrato real com código existente; ajustar somente o necessário. | Implementação apta em sombra com enforcement, mapper/presença/frescor, staging/promoção, DQ, replay e provas pertinentes. Não fechar pai com binding sintético. Fonte pronta não precisa esperar dimensão que será derivada dela. |
| **P17 — Aceitar a caracterização inicial** | **Terra / High** | P16, contrato/identidade aplicáveis e fonte-oráculo autorizada | V2-012a: comparar janela representativa e limitada antes do bootstrap: campos, tipos, presença, chaves, grão, status, tempo, expansão e relações disponíveis. | Relatório de divergências resolvidas/classificadas e aceite da fatia012a. Inspeção `/info` de P13 sozinha não satisfaz esta etapa. |
| **P18 — Planejar e executar bootstrap histórico** | **Terra / High** | P17 e ambiente P12; autorização da origem/destino | V2-047: reusar planejador; escolher reextração/export/ponte autorizada, T0, horizonte, partições e delta até Tcut. Executar somente quando a vertical estiver IMPLEMENTADA_EM_SHADOW. | Histórico reconciliado por entidade; namespace bootstrap separado, sem watermark incremental elevado pelo maior período. Linha sem histórico necessário recebe NOT_APPLICABLE aceito. Migração read-only do legado requer autorização própria. |
| **P19 — Fechar as relações MC e CF** | **Terra / High** | P18 de Manifestos para MC; P18 de Coletas/Fretes quando aplicável para CF; runtime V2-022 apto em P12 | Primeiro V2-046a: Manifesto→Coleta/backlog/hidratação, com bases010/026. Depois V2-046b: Coleta→Frete, com046a e011. Reexecutar crosswalk após cada bootstrap pertinente. | Cardinalidade, órfãos, conflito, replay e SLA provados em SQL; sem heurística silenciosa nem multiplicação de raízes. A base Fretes não espera046a para existir; **a paridade relacional espera a relação**. |
| **P20 — Aceitar paridade core** | **Terra / High** | P17; P18 se houver histórico; P19 somente nas relações usadas | V2-012b: comparar conjuntos, chaves, nulos, status, datas, relações, somas e histórico em janelas fechadas/repetidas, set-based. | Divergências corrigidas ou aceitas nominalmente, evidência por entidade. Não depende de fato/view downstream. Libera os consumidores core, a política de ausência e os fatos que usam essa entrada. |
| **P21 — Fechar dimensões derivadas** | **Terra / High** | P16/P18/P20 das fontes usadas e P15 aplicável | V2-035b: finalizar conteúdo, papéis, vigência/rekey e paridade das seis dimensões a partir das entradas qualificadas. | Dimensão no grão aprovado e prova própria. Fonte→dimensão, nunca dimensão→própria fonte. Contrato de publicação da dimensão continua em P25. |

### Ordem das entidades dentro de P13–P20

Esta é a prioridade operacional sugerida quando as respectivas entradas estiverem disponíveis. Não cria dependência entre entidades independentes. Pular uma entidade bloqueada libera somente a próxima que **não dependa dela**.

| Prioridade | Entidade | Precedência material e principal pendência |
| --- | --- | --- |
| 1 | Usuários | Base033 já existe; snapshot GraphQL real e completude/paridade. Apoia Coletas e dimensão Usuários; não inventar incremental temporal. |
| 2 | Manifestos | Base026 existe e não precisa aguardar relação MC para ingerir. Bootstrap de Manifestos precede046a. Provar pick/MDF-e/crosswalk reais. |
| 3 | Coletas | Base010 existe; Usuários/referências pertinentes aptos. Bootstrap e046a alimentam o fechamento relacional. |
| 4 | Fretes | Base011 existe; depende da base de Coletas. Bootstrap/046b precedem paridade do escopo Coleta→Frete. |
| 5 | Cotações | Independente das relações MC/CF; pode avançar antes quando pronta. Exige referência tarifária ratificada para seu consumidor. |
| 6 | Localização | Base028 depende de Fretes; falta vínculo/tempo nominal e paridade. Não esperar CAP/FAT/INV/SIN se não os utiliza. |
| 7 | Contas a Pagar | Raiz/parcela/rateio8636 ainda sem prova nominal; não bloquear as seis anteriores. Alimenta Plano de Contas e Filiais. |
| 8 | Faturas por Cliente | Depende de Fretes e identidade/título/crosswalk4924; série NFS-e precisa de fonte própria. Alimenta MAT-03/MAT-04. |
| 9 | Inventário | Depende de Fretes e identidade de raiz/componentes10633; alimenta MAT-02. |
| 10 | Sinistros | Depende de Fretes e identidade/arrays/semântica temporal6392; qualificação própria. |
| 11 | Raster condicional | Decisão MANTER já existe, mas identidade009c/completude/frescor e autorização próprios ainda são necessários. Fora da primeira onda; ausência não autoriza omitir sua responsabilidade mantida no encerramento. |

Dependências exatas de P21, pelo STATES: **Filiais ← Fretes/Manifestos/CAP/FAT; Clientes ← Coletas/Fretes/FAT; Veículos/Motoristas ← Manifestos + decisão035c/provas de frota; Plano de Contas ← CAP; Usuários ←033.** A existência local das seis dimensões não substitui os cadastros/bindings nominais.

## 7. Ausência, fatos e saídas — ramos independentes após o core

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P22 — Aceitar a política de ausência** | **Astra / High** | P20 por entidade; contrato de snapshot e G04 | V2-013: classificar as 33 responsabilidades, presença raiz/filho, confirmações, propagação, guardrails e reativação com os responsáveis. | Aplicabilidade nominal ENABLED/DISABLED/BLOCKED/NOT_APPLICABLE fundamentada. Ausência de completude não vira ENABLED. A linha aceita é necessária ao corte, mesmo sem prune. |
| **P23 — Implementar/provar apply quando permitido** | **Terra / High** | P22; autorização da entidade; snapshot completo e ambiente P12 | V2-013: completar somente os consumidores de ausência habilitados; provar preview/apply separado de upsert e as recusas de parcialidade, cap, vazio anômalo e reuso de evidência. | Soft delete/reativação e salvaguardas comprovados onde aplicáveis; para linha sem apply, registrar motivo aceito e não executar mutação. Não transforma033/013 locais em sweep real universal. |
| **P24 — Qualificar os cinco fatos** | **Terra / High** | P20 das entradas; P15/P19/P21 apenas nos subconjuntos usados | V2-036: adequar cargas existentes ao grão/regra nominal e provar consumo de entradas PUBLISHED no ambiente sombra, cardinalidade, no-op, partição e recomposição. | Fato em paridade com regras MAT-01–05 e decisões fiscais/filial resolvidas. Não depende de012c, que vem depois. Não espera P23 quando o fato não exige apply de ausência. |
| **P25 — Fechar contratos SQL de consumo** | **Terra / High** | P16/P20 para core; P21 para dimensão; P24 quando houver fato; manifesto consumidor G04 | V2-037: conferir os 19 contratos, nomes/ordem/tipos/nullability/grão/filtros e linhagem completa. SQL10 interno tem escopo próprio; 18 saídas são externas. | Contrato aprovado, teste/fingerprint e compatibilidade. Nenhum wrapper cross-database; não ler dashboards como oráculo. Contrato core independente não espera todos os fatos. |
| **P26 — Aceitar paridade analítica** | **Terra / High** | P25 e P20 de todas as entradas; P24 quando aplicável | V2-012c: comparar fato/saída com oráculo nominal, set-based, incluindo fiscal, somas, relações, labels e filtros. | Divergências corrigidas ou aceitas por saída; liberar qualificação dessa saída, sem retroalimentar dependência do fato. |
| **P27 — Provar memória e SQL por escopo** | **Terra / Medium** | P20 para core; P26 para fato/view, com036/037 aplicáveis | V2-050: medir heap, lotes, linhas/bytes em voo, planos/IO e duração em volume representativo autorizado. Reusar harness local. | Gate por vertical/fato com platô/limites e push-down demonstrados. Medição local P05 não fecha automaticamente este aceite. Investigar só gargalo observado. |

**Precedência, em termos simples:** core → política/ausência e core → fatos/contratos são ramos separados. Um bloqueio de sweep não impede construir ou comparar uma saída independente; continua bloqueando o aceite que realmente exija aquela capacidade.

### Dependências dos fatos: não esperar uma entidade sem necessidade

Todos exigem paridade core aceita e entradas PUBLISHED em sombra. A tabela reproduz o DAG de V2-036; relações/dimensões adicionais só entram quando efetivamente usadas.

| Fato | Entradas e referências exigidas |
| --- | --- |
| MAT-01 — Fretes operacional | Fretes + Localização + pagadores excluídos/documentos de filiais |
| MAT-02 — Coletores | Fretes + Manifestos + Inventário + aliases/filiais |
| MAT-03 — Faturamento | Fretes + Localização + Faturas por Cliente + calendário/atribuição de filial |
| MAT-04 — Faturas | Faturas por Cliente; grão de título e precedência fiscal nominal |
| MAT-05 — Manifestos | Manifestos + Coletas + Fretes + frota própria; vínculos MC/CF usados |

Assim, MAT-01 pode avançar antes de Inventário; MAT-02 aguarda Inventário; MAT-04 não precisa esperar MAT-03. Escolher o primeiro fato com todos os inputs prontos, sem impor uma cadeia artificial MAT-01→MAT-02→MAT-03→MAT-04→MAT-05.

## 8. Convergência para operação e conclusão do projeto

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P28 — Qualificar E2E/recovery/desempenho** | **Terra / High** | P12, P27 e P20 ou P26 do escopo; P23 só para funcionalidade de ausência habilitada | V2-038 e parcelas restantes de022: agenda, blackout, catch-up, replay, cancelamento, concorrência, durabilidade e recovery sob autorização específica. | Qualificação reproduzível por SHA, budgets e checkpoint/efeitos corretos. Prova com COMMIT/crash/restore não é substituída por rollback local. Gate por entidade/saída, sem antecipar corte. |
| **P29 — Congelar RC e gates de release** | **Terra / Medium** | P10, P11, P12 e P28 da onda/unidade; G01 vigente | Primeiro V2-039b: pacote/config/checksum/proveniência/SBOM e smoke. Depois V2-015c: SAST/licenças/segurança e SOs suportados no mesmo RC. | Artefato e relatórios coerentes, pronto para revisão/ensaio. Alteração de código/config relevante exige requalificar o impacto e congelar outra revisão. |
| **P30 — Revisar a unidade real de corte** | **Astra / High** | P29; P18/P20/P25/P26/P28 de todas as responsabilidades usadas e P22 aceito | V2-048b/V2-014: confrontar topologia048a, cobertura, Tcut, writer único, fences, ponto de não retorno, rollback pré-escrita e recuperação V2 pós-escrita. | Pacote de ensaio concreto sem lacunas da unidade **CUTOVER-DB-01 / DATABASE_WIDE**. Revisão técnica não substitui aceite humano nem autoriza provisionar ou cortar. |
| **P31 — Ensaiar troca e recuperação** | **Terra / High** | P30; G06 | V2-048b: ensaio material isolado, carga histórica+delta, freeze, fences negativos, troca e duas recuperações, pelos executores autorizados. | Ensaio aceito, RTO/RPO medidos e somente SIMULATED_PNR. Falha retorna à causa; não vira aprovação de corte. |
| **P32 — Executar o corte autorizado** | **Terra / High** | P31, DoD/gates completos da unidade e G07 | V2-014: conferir autorização nominal/datada, backup/rota/abort, revogação do writer antigo antes do novo e smoke pelos responsáveis. | Primeira publicação produtiva aceita, writer único e PNR registrado. Depois do PNR, recuperação aprovada permanece no V2; legado não volta a escrever. |
| **P33 — Encerrar responsabilidades e retirar o legado** | **Terra / Medium** | P32 para todas as responsabilidades mantidas; observação/retenção e G08 | V2-040: reconciliar comandos, dados, campos, views, jobs, credenciais, consumidores e ownership; executar retirada/arquivamento explicitamente autorizados. | Nenhuma responsabilidade mantida sem destino; evidência e documentação final. GraphQL/adapters só saem quando não houver consumidor e existir substituição válida. |

**Qualificar por entidade não significa cortar por entidade.** O STATES mantém DATABASE_WIDE até prova e ratificação de outra topologia. Raster condicional ou qualquer responsabilidade não aplicável exige tratamento nominal na unidade; não pode sumir da matriz para facilitar o corte.

## 9. Modelos escolhidos depois das dependências

| Modelo | Uso nesta trilha | Regra de economia |
| --- | --- | --- |
| Terra Medium | Reconciliação, execução de gates, medição, CI, pacote/RC prescritos e documentação final | Padrão quando procedimento e critério já são claros. |
| Terra High | Java/SQL, transações, integração, paridade, provas físicas e selagem técnica | Manter escopo delimitado; diagnóstico profundo não deve virar várias tentativas cegas. |
| Astra Medium | Diagnóstico P02 e revisão P06 | Receber reprodução, invariantes, diff e evidência; devolver decisão executável. |
| Astra High | Identidade P14, ausência P22 e revisão do corte P30 | Reservar para ambiguidade semântica e consequências difíceis de recuperar. Se decisão já estiver comprovada, reaproveitar. |
| Luna Low/Medium | Resumo, links e transcrição de resultados explícitos dentro de uma etapa | Auxílio opcional. Não criar um chat só para copiar logs; não atribuir selo, decisão de aceite ou revisão de segurança a Luna. |
| Sol | Alternativa opcional | Nenhuma etapa exige passagem por Sol. Usar somente se evidência de consumo/qualidade da sua tarefa justificar. |

A escolha é recomendação de engenharia, não benchmark deste repositório. Medir **custo total até cumprir o mesmo aceite**, incluindo contexto, raciocínio, correções e revalidação. Nas taxas Standard publicadas, Astra custa 2,5× Sol por categoria de token; isso não prova que a entrega custará mais ou menos. Com proporções iguais de entrada/cache/saída, gastar menos de 40% dos tokens totais torna Astra mais barato; não foi medido aqui. [OpenAI Docs — preços](https://learn.chatgpt.com/docs/pricing).

Não usar Max/Ultra/Fast por padrão. Ultra envolve delegação e não integra este fluxo sem subagentes. xhigh só após High deixar uma questão concreta sem solução. Nomes/disponibilidade variam pelo cliente; Low pode aparecer como Light. [OpenAI Docs — modelos e esforço](https://learn.chatgpt.com/docs/models).

Uma etapa não exige um chat novo: executar passos contíguos no mesmo modelo enquanto o contexto for útil, salvando checkpoints. Ao trocar de modelo, levar um resumo curto com paths/recibos e o problema exato. Não repetir suíte, scanner ou investigação de hold sem mudança que justifique, preservando todos os gates obrigatórios.

## 10. Cobertura dos itens abertos do STATES

Todos os 48 IDs abertos da fotografia lida estão mapeados abaixo. Pais agregados e suas fatias não contam como implementações independentes. Nenhuma linha desta trilha marca aceite funcional.

| IDs abertos | Etapas responsáveis |
| --- | --- |
| V2-041 | P09 |
| V2-016, V2-016b | P10 |
| V2-015, V2-015c, V2-015d | P07, P11, P29 |
| V2-045, V2-045b | P12 |
| V2-022 | P01–P08, P12, P28 |
| V2-025, V2-025d | P13 |
| V2-009, V2-009b, V2-009b/10633, V2-009b/8636, V2-009b/4924, V2-009b/6392, V2-009c, V2-009d | P14, P16 |
| V2-035, V2-035a, V2-035b | P15, P21 |
| V2-047 | P18 |
| V2-046, V2-046a, V2-046b | P19 |
| V2-012, V2-012a, V2-012b, V2-012c | P17, P20, P26 |
| V2-013 | P04 local; P22/P23 reais |
| V2-029, V2-030, V2-031, V2-032, V2-034, V2-034b | P14/P16 e gates por entidade posteriores |
| V2-036 | P24 |
| V2-037 | P25 |
| V2-050 | P05 local; P27 representativo |
| V2-038 | P28, reutilizando somente provas aplicáveis P03–P08 |
| V2-039, V2-039a, V2-039b | P08 local; P12/P29 operacionais |
| V2-048, V2-048b | P30/P31 |
| V2-014 | P32 |
| V2-040 | P33 |

## 11. Pedido para gerar o próximo prompt

Use o mesmo pedido neste chat ou em qualquer chat posterior, depois que o resultado do trabalho estiver registrado. Você não precisa escolher os passos nem o modelo antecipadamente:

```text
Leia STATES.md e TRILHA_CONCLUSAO_POR_MODELO.md atualizados.
Com base nos dois, monte o próximo macrobloco coeso que possa ser executado
inteiro no mesmo chat, respeitando prioridades, dependências e autorizações.
Agrupe as tarefas compatíveis para economizar contexto e trocas de chat.
Escolha o GPT e nível adequados ao conjunto e justifique brevemente.
Entregue aqui o prompt completo, preenchido e pronto para copiar no novo chat,
com objetivo, etapas em ordem, contexto/evidências, limites, validações,
critérios de conclusão e ponto de parada. Apenas prepare o prompt; não execute.
Inclua a regra de uma entrada minha e uma entrega final consolidada,
sem pedidos de continuação ou encerramentos intermediários por tarefa.
```

**Na fotografia atual:** usar o prefácio vigente do STATES e a preparação da seção14. A indicação antiga de reiniciar P01 a partir da0168 ficou superada pelas reconciliações até0184. Conferir somente o delta e os critérios ainda não demonstrados. Antes de entregar prompt P04→P05, comprovar o alcance da ordem aplicável e a suficiência técnica; se faltar decisão, usar o pacote já delimitado, sem entregar mais um prompt de execução que termine no mesmo gate conhecido.

## 12. Critério de conclusão e limites da revisão

**Concluir a campanha local:** P01–P08 com provas da revisão entregue e integridade preservada. **Concluir o projeto:** critérios originais dos itens abertos efetivamente atendidos, qualificação e operação reais aceitas, corte da unidade autorizado/executado e V2-040 encerrado com evidência.

Esta revisão conferiu a precedência documental e as fontes de estado; não executou os 33 passos nem demonstrou prontidão produtiva. Se um contrato/autoridade/input mudar, ajustar a dependência atingida com evidência, mantendo o restante do roteiro estável. Versões anteriores e motivos desta revisão estão preservados na rodada `target/trilha-prioridades-20260919-01/` e no checkpoint0171.

## 13. Selecionar um macrobloco por chat a partir dos dois arquivos

**Regra de entrega direta:** um macrobloco só deve ser aberto quando contiver ao
menos uma unidade com resultado verificável possível no escopo autorizado. O
executor deve concluí-la integralmente, incluindo correção e teste causal, em
vez de produzir manutenção documental ou reexecutar provas sem delta. Registros
de continuidade e caixas da etapa são atualizados como consequência do resultado.
Sem unidade elegível, responder com o bloqueio e input exatos, sem fabricar um
novo prompt intermediário.

**Fluxo adotado pelo usuário:** ele pede um prompt; o chat lê `STATES.md` e esta trilha, escolhe o próximo macrobloco e seu GPT/nível, e entrega o prompt aqui na conversa. O usuário o leva ao próximo chat. Esse executor realiza o macrobloco e atualiza os registros; quando o usuário pedir outro prompt, a seleção se repete sobre o estado atualizado. Gerar uma passagem automaticamente ao fim de cada tarefa foi uma interpretação rejeitada.

| Arquivo | Papel na escolha |
| --- | --- |
| `STATES.md` | O que foi realmente concluído, o que falta, critérios canônicos, evidências, bloqueios e autorizações registradas |
| `TRILHA_CONCLUSAO_POR_MODELO.md` | Prioridade, precedência, modelos por tipo de trabalho e regras para agrupar o próximo chat |

Os dois são a base da seleção; consultar os critérios e referências da frente para comprovar elegibilidade. AGENTS, contexto global, continuidade e evidências citadas continuam obrigatórios para a execução. Não exigir a memória do chat anterior nem reler indiscriminadamente todo o histórico.

### Contrato de uma entrada e uma saída

Reforço explícito de20/09/2026: se ainda não encontrou a solução local, ler a
documentação, testar, diagnosticar e corrigir dentro do escopo autorizado.
Não confundir falha técnica com falta de autorização nem solicitar novamente
o que já foi concedido. Executar todas as correções/verificações independentes
elegíveis antes de consolidar eventual dependência externa comprovada.

Cada prompt adotado deve permitir **uma entrada do usuário → execução autônoma do macrobloco → uma entrega final consolidada**. Não pedir “continue”, “posso seguir?” ou confirmação de rotina entre tarefas já cobertas. Não encerrar apenas com planejamento, resumo intermediário ou oferta de continuar enquanto houver trabalho elegível dentro do macrobloco. Checkpoints ficam nos arquivos, sem exigir nova interação por checkpoint.

Atualizações breves de andamento, quando necessárias, não exigem resposta do usuário e não são entregas finais. Antes de compor o macrobloco, conferir os inputs conhecidos para reduzir interrupções. Se surgir bloqueio real de informação, autorização, integridade ou limite, não ultrapassá-lo: concluir o que for independente e autorizado e consolidar na entrega final o resultado parcial, o impedimento e o input exato necessário. Uma saída única não garante conclusão diante de dependência externa ausente e não autoriza inventar dados ou permissões.

### Como dimensionar o macrobloco

1. **Começar pelo estado real:** verificar o resultado da frente atual e o primeiro P elegível. Se há entrega parcial, avaliar sua correção antes de avançar. Resultado desconhecido exige reconciliação; bloqueio precisa de input identificado, não de nova tentativa sem mudança.
2. **Agrupar por entrega coesa:** reunir etapas/fatias que compartilhem contexto, arquivos, ambiente e um resultado verificável. Dependências externas ao macrobloco devem estar satisfeitas; as internas entram em ordem e só liberam a etapa seguinte quando sua prova/aceite estiver atendido. Não juntar tarefas desconectadas apenas por usarem o mesmo GPT.
3. **Escolher um GPT/nível para o conjunto:** partir das recomendações individuais da seção9 e selecionar o menor nível suficiente para o trabalho mais exigente incluído. Uma medição Medium pode permanecer junto da correção Terra High quando isso reutiliza contexto. Separar diagnóstico/revisão Astra quando incluí-lo encareceria todo o restante; Sol continua opcional. Justificar a escolha pelo trabalho, sem prometer economia medida.
4. **Definir um tamanho executável:** incluir o máximo de trabalho coeso com entradas, implementação, validações e ponto de parada delimitados. Não há número fixo de P por chat nem garantia de duração. Dividir quando surgir mudança substancial de contexto/modelo, decisão sem evidência, aceite externo, risco/volume ou limite de campanha incompatível. Um macrobloco pode conter somente uma etapa se houver motivo concreto.
5. **Continuar dentro do escopo:** o executor avança pelas etapas internas elegíveis sem pedir “continue” entre elas, registra checkpoints após unidades coerentes e para no limite combinado. Preserva os tetos e autorizações de cada efeito; agrupar tarefas não cria campanha física nova nem renova saldo.
6. **Deixar o estado pronto para a próxima escolha:** ao encerrar, sincronizar STATES → trilha → verificações, salvar/conferir checkpoint e atualizar RETOMADA. Registrar resultado, provas e pendências; gerar outro prompt somente quando solicitado. Se tudo foi concluído com evidência, não inventar outro macrobloco.

### Agrupamentos candidatos, sujeitos ao estado de cada pedido

São exemplos de empacotamento de trabalho para um chat, não novos IDs funcionais nem seleção automática. Recalcular o alcance pelos inputs, evidências e autorizações vigentes.

| Entrega do chat | Etapas que podem caber juntas | GPT / nível | Fronteira do macrobloco |
| --- | --- | --- | --- |
| Reconciliação da campanha | P01 e suas verificações documentais | Terra / Medium | Parar com a revisão/falha delimitada; decidir depois se P02 é necessário |
| Diagnóstico delimitado | P02, somente se houver lacuna causal | Astra / Medium | Causa, correção proposta e contraprova suficientes para execução |
| Sequência, supervisão e escalas locais | P03 → P04 → P05 | Terra / High | Provas da revisão atual, dentro das reservas/limites existentes; não incluir revisão P06 |
| Revisão técnica | P06 | Astra / Medium | Achados e correções verificáveis; não declarar gate aprovado sem execução |
| Gate e pacote da campanha | P07 → P08 | Terra / High | Regressão antes do JAR; fechar M/N somente com integridade/provas válidas |
| Uma vertical até o histórico aceito | Fatias P16 → P17 → P18 de uma entidade | Terra / High | Contrato/identidade/ambiente prontos; parar em aceite/input externo ainda faltante |
| Relações e paridade core | Fatias P19 → P20 que usam as mesmas bases | Terra / High | Históricos/bases prontos e relações necessárias provadas antes da paridade |
| Uma saída analítica qualificada | Fatias P24 → P25 → P26 → P27 de um fato/saída | Terra / High | Entradas core/referências/manifesto/oráculo prontos; sem incluir outros fatos por conveniência |

Para etapas não exemplificadas, aplicar o mesmo critério. Um intervalo não autoriza cumprir etapas com dependências pendentes. Não combinar toda a campanha A–N em Astra só para evitar a troca, nem fragmentar uma implementação coesa em chats de tarefas mecânicas.

### O que entregar quando o usuário pedir o prompt

Informar brevemente **“Macrobloco: objetivo — etapas/fatias; GPT / nível; motivo do agrupamento; ponto de parada”**. Depois entregar um único bloco `text` completo, já preenchido com os dados reais. Não entregar apenas um link ou campos para o usuário completar.

O prompt deve identificar o resultado esperado, trabalho incluído/excluído, ordem interna, critérios de saída por etapa, arquivos/evidências aproveitáveis, pendências, limites e responsabilidade de atualizar os dois documentos ao final. Autorizações são referenciadas com origem, alvo e alcance, nunca inventadas. Preparar o prompt é planejamento; não executa o macrobloco.

Estrutura para o chat preencher ao gerar o prompt solicitado:

```text
Projeto/workspace: {projeto e caminho real}.
GPT/nível indicado: {modelo e esforço para o macrobloco}.
Macrobloco deste chat: {objetivo e lista ordenada de P/fatias/IDs V2}.
Resultado final: {entrega verificável e ponto de parada}.
Fora deste macrobloco: {etapas/efeitos excluídos e motivo da fronteira}.

Regra de interação: uma entrada minha e uma entrega final consolidada.
Execute autonomamente o macrobloco, sem pedir continue ou confirmação de
rotina e sem encerrar em plano/resultado intermediário se ainda houver
trabalho elegível no escopo. Atualizações breves não exigem minha resposta.
Se surgir bloqueio real, preserve os limites, conclua o trabalho independente
autorizado e informe na entrega final a causa e o input necessário.

Leia STATES.md e TRILHA_CONCLUSAO_POR_MODELO.md atualizados.
Cumpra AGENTS.md, ../CONTEXTO_GLOBAL.md, docs/continuidade/RETOMADA.md
e docs/runbooks/continuidade-agentes.md; confira as referências da frente.
Estado de partida: {o que está comprovado/parcial/bloqueado, revisão do
worktree, checkpoint e evidências locais; HEAD sozinho pode não conter tudo}.
Dependências externas ao macrobloco: {provas de atendimento e inputs pendentes}.
Arquivos/runbooks para atuar: {paths e finalidade}.
Evidência reaproveitável: {testes/recibos, revisão/camada e limitações}.
Falhas ou efeitos desconhecidos: {o que conferir antes de repetir, ou ausência
comprovada; processos/ledger pertinentes sem segredo nem dado de negócio}.
Autorização e limites: {origem/data, alvo, operações, vigência, tetos e saldo
quando aplicáveis; explicitar alcance ausente sem autorizar por inferência}.

Execute o macrobloco nesta ordem: {etapas incluídas e critério que libera
cada etapa interna, comandos/validações pertinentes e evidência de saída}.
Não peça continuação entre etapas internas já cobertas; pare se uma
pré-condição falhar, um limite for atingido ou o escopo exigir decisão externa.
Não salte uma dependência, não amplie o macrobloco nem use subagentes.
Preserve alterações preexistentes e provas históricas; não repita trabalho
comprovado sem mudança que justifique revalidar. Ausência de artefato deve
ser registrada, nunca compensada com prova inventada.

Ao encerrar, registre o resultado real, testes e pendências no STATES,
sincronize a trilha/verificações, salve e confira checkpoint e atualize RETOMADA.
Apresente o que foi concluído e o que restou, sem fechar aceite por planejamento.
Deixe os dois arquivos prontos para selecionar o próximo macrobloco.
Gere o próximo prompt somente quando eu solicitar.
```

Os campos entre chaves são preenchidos pelo gerador com fatos ou lacunas explícitas. O novo chat precisa ter acesso ao workspace e aos artefatos citados; o prompt não contém segredos/payloads nem substitui evidência ausente.

Esta revisão corrige o fluxo de uso, preservando prioridades, dependências, modelos individuais e aceites de P01–P33. Registro: checkpoint0173 e `target/trilha-macroblocos-chat-20260919-01/`. A regra automática da revisão3.1/checkpoint0172 fica histórica.

A revisão3.3 explicita uma entrada e uma entrega final por macrobloco, sem mudar seu escopo ou os aceites. Registro: checkpoint0174 e `target/trilha-entrada-saida-20260919-01/`.

## 14. Terreno preparado para os próximos macroblocos

O [guia integral](docs/runbooks/preparacao-integral-trilha.md) é o ponto único de
preparação: origem dos limites, conjunto P04→P05 delimitado, proposta finita
somente se necessária, critérios/testes reutilizáveis, coleta G01–G08/FEED e
agrupamentos posteriores. O [mapa](docs/catalogos/preparacao-trilha/plano.json)
mantém33 etapas com dependências condicionais por fatia e todos os48 IDs abertos.
Não é autorização nem substitui as tabelas/aceites desta trilha e do STATES.

Antes de gerar o próximo prompt, executar a verificação local:

```powershell
pwsh -NoProfile -File scripts/validation/Test-TrilhaPreparation.ps1
```

Um PASS comprova coerência/cobertura documental, **não prontidão física**.
Conferir evidência e autoridade da fatia antes de propor sua execução. Não inventar
expiração ou saldo global; também não interpretar ausência como autorização
ilimitada. Não pedir novamente o que já estiver comprovado e vigente. Bloqueio
conhecido sem mudança deve levar ao pacote de entrada preciso ou a trabalho
independente autorizado, não a outro preflight dispendioso e idêntico.

Preparação não recebe checkbox funcional. A construção segue39/45 (86,7%) e os
aceites67/115 (58,3%); o delta deste trabalho é0 ponto percentual nesses contadores.
Cada percentual futuro deve identificar quais critérios serão realmente fechados,
sem dupla contagem de pais/fatias nem reclassificação silenciosa de V2-017.
# Qualificação técnica de sombra — correção de validador, gate JaCoCo aberto

Em 22/09/2026, por ordem do usuário, a revisão corrente foi verificada sem
fonte externa nem banco produtivo. A regra `PREPARE` do validador read-only de
Manifestos foi corrigida para reconhecer a procedure V022 instalada; o
validador passou após a correção. Cinco provas estáticas de verticais, nove
validadores SQL read-only, gates de schema/dados, scanner e a IT JDBC sintética
rollback-only passaram. A suíte unitária teve 2.263 testes sem falha/erro e
cinco ignorados; `mvn verify` terminou **vermelho no JaCoCo por cobertura por
pacote**, sem redução de limiar. Contagens de auditoria local ficaram 0/0.
O resultado e os limites estão no cabeçalho de `STATES.md`; logs locais em
`target/shadow-technical-delivery-20260922-01/`. B17–B29, fonte, negócio e
produção seguem sem aceite; não houve nova sonda 4924.
# P29 — guardas Unicode do alvo shadow nos bytes v89 — 28/09/2026

O nome do banco em `ShadowStorageProperties` aceitava `ETL_\u017FISTEMA_V2_SHADOW`
por `equalsIgnoreCase`; chave `soc\u212AetTimeout` e flag
`trustServerCertificate=fal\u017Fe` também passavam após case-folding. Três
contraprovas RED e green exigem agora a grafia exata do banco e ASCII para
chaves/flags; `QualificationConfiguration` P08 permanece separada.
`clean verify` v89 nos bytes finais passou 2.355 Surefire/cinco skips, seis
ITs offline e JaCoCo 80/60. PMD v89 conservou 36 brutos e disposição local
36/36 PASS. SpotBugs/FindSecBugs v90 analisou 1.091 classes: 272 alertas
brutos/87 SECURITY, zero erros/classes ausentes. Um `IMPROPER_UNICODE` saiu
do inventário tipo+classe; 46 dessa categoria e 272 totais continuam abertos,
sem aceite nominal de Segurança. O JDK17 foi corrigido apenas no invocador
privado do scanner após duas recusas anteriores. P08 físico 12.8.1/JDBC e UAC
seguem suspensos pelas decisões vigentes; sem SQL, DLL, serviço ou DDL.
[Relatório técnico](docs/catalogos/p29-sast-preparacao-20260928/RELATORIO.md).
# P29 — booleanos de runtime com ASCII obrigatório — 28/09/2026

`RuntimeConfigurationFactory` aceitava `fal\u017Fe` como `false` nas flags
opt-in por `equalsIgnoreCase`; teste sintético de arquivo/ambiente saiu RED
30/1, e a guarda ASCII anterior à comparação passou 30/30 mantendo `FALSE`
ASCII. `clean verify` v95 nos bytes finais passou 2.356 Surefire/cinco skips,
seis ITs offline, JaCoCo 80/60. PMD v95 manteve 36 brutos; as seis entradas
de disposição que citam fonte/teste alterados foram vinculadas aos SHA
atuais, com 36/36 PASS local v95b após recusa de hash da primeira tentativa.
SpotBugs/FindSecBugs v96 manteve 272 brutos/87 SECURITY/46 Unicode e zero
erros/classes ausentes; nenhum achado foi aceito nominalmente. P08 físico
12.8.1/JDBC e UAC seguem suspensos, sem SQL/DLL/serviço/DDL ou checkbox
integral. [Relatório P29](docs/catalogos/p29-sast-preparacao-20260928/RELATORIO.md).
# P07/P08/P29 — instalação local pronta, parada segura — 28/09/2026

Preflight read-only: `MSSQLSERVER` ausente em `LUCAS`, sem processo SQL/setup
ou árvore de extração; mídia Microsoft 17.0.1000.7 teve assinatura válida,
SHA idêntico ao recibo e 748.772.024 bytes; `sqlcmd` v1.10.0 está disponível.
O runbook fixou comando de extração/setup e incluiu a opção de aviso de
privacidade exigida pela documentação Microsoft para `/Q`. O operador ainda
não sinalizou prontidão; nenhum UAC, serviço, SQL, DDL, DLL ou JDBC foi
executado. O pin 12.8.1/JDBC permanece suspenso e a proposta 12.8.2 aguarda
decisão explícita. Inventário SAST v96 foi agrupado por causa para triagem
em lote, sem disposição/aceite nominal novos nem checkbox integral.
# P08 — revisão independente offline dos dois contratos Surefire 0349 — 29/09/2026

Banco conferiu no espelho 0349 que os dois hashes de escopo de `AnalyticScenarioRuntime` ficaram antigos após binding V024; a classificação SQL/JDBC e exclusão shadow continuam coerentes. Os 104 nomes esperados pelo teste de schema igualam o prefixo ativo, V105 é o único sufixo, e os 105 SHA conferem com inventário epoch e baseline. Repin duplo revisado e lista exata V001–V105 preservam os guardas; mutantes específicos foram propostos, não executados. Evidência e limites: STATES/[checkpoint 0350](docs/continuidade/checkpoints/0350-p08-surefire-contratos-revisao-offline.md). Sem SQL/JDBC/IT/Flyway/smoke, edição Runtime ou aceite Gate 1/P08; FAIL 0349 e histórico anterior preservados.
# P08 — oito cenários físicos PASS, gate Maven/metadata FAIL — 29/09/2026

Após pins Runtime 0350, Banco executou uma classe `QualificationPackagePhysicalCompositionIT` em espelho novo e shadow local com reserva. Surefire 2368/0/0/5 e oito cenários Failsafe ativos PASS; JaCoCo base PASS, **shadow FAIL** e Maven exit 1. Readback sem delta de dados/schema/histórico/socket, mas stats gerais 2235→2448 (+213 auto) e 064 `V105_STATISTICS_BLOCKERS` 1→2 por novo grupo em `ctl.execution_audit.failure_category`. Recibos, ledger e limites em STATES/[checkpoint 0351](docs/continuidade/checkpoints/0351-p08-fullclass-failsafe-pass-jacoco-stats-fail.md). **Gate 1/P08 abertos**, A/B físico não iniciado, sem retry/DDL/Flyway/restore; FAILs históricos e backup 0325 preservados.
