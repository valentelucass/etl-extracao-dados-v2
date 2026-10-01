# 0383 — P08 Runtime: PowerShell portátil e PackageShadow A/B offline

- Data: 2026-09-29. Anterior: [0382](0382-p08-package-shadow-pwsh-ausente-offline.md), SHA-256 `919758090CA75AABC98207C79274B9A9A6A95DCB32A58D9C6970F60ACF467946`.
- Instrução efetiva: obter **somente** PowerShell 7.5.11 win-x64 ZIP da release oficial indicada, em diretório novo do usuário fora do repositório; verificar preflight, SHA-256 e assinatura Microsoft antes de executar. Rodar scanner/validadores e PackageShadow A/B sobre os 2170 hashes atuais, sem alterar fontes, comparar revision/manifest/ZIP/payload/extração/hashes. A correção explícita permite `-DskipTests` somente na fase PackageShadow porque o `clean verify` integral dos mesmos bytes passou em 0381. Zero SQL/JDBC/DLL executada/IT física/rede de fonte/serviço/Flyway/segredo/deploy.
- Estado: **CANDIDATOS_A_B_OFFLINE_VALIDADOS; PACKAGED_NOT_SMOKE_QUALIFIED**. Não é JaCoCo shadow, Gate 1, P08 nem aceite P01–P33. AGENTS/STATES/RETOMADA/runbook e 0374/0381/0382 consultados; `../CONTEXTO_GLOBAL.md` continua ausente. `maestri list` identificou Codex como Supervisor. Runtime editor único dos artefatos desta unidade.

## Preflight, aquisição e execução do PowerShell

Antes do download, o recibo `target/p08-runtime-0383-package-shadow-offline/portable-preflight.json`, SHA `4161557934762755CB5BE2BB257905E229D75DF45F8D85673EF706CB8E242D1A`, registrou Windows/processo `AMD64` x64, destino **inexistente** `C:\Users\lucas\p08-pwsh-7.5.11-portable-0383` fora do checkout, **692.253.749.248 bytes livres** (mínimo 1 GiB), impacto e recuperação. O impacto previsto foi um ZIP e extração isolada, sem MSI/winget/UAC/serviço/PATH global/sobrescrita; em falha, parar sem retry/fallback/execução e reter os bytes isolados para revisão.

Uma única chamada `curl.exe` a `https://github.com/PowerShell/PowerShell/releases/download/v7.5.11/PowerShell-7.5.11-win-x64.zip`, com HTTPS, redirects HTTPS, `--retry 0` e teto 600 s, baixou **115.092.585 bytes**. SHA-256 observado `75CDAB18DB9C8AC32F02E82149698166551E42D2A904DA4B3A60FB5FCB3AD021` igual ao pin fornecido **antes da extração**. ZIP de 933 entradas sem caminho absoluto/traversal foi extraído em `runtime/` novo. `Get-AuthenticodeSignature` no `pwsh.exe` extraído retornou **Valid**, `CN=Microsoft Corporation, O=Microsoft Corporation`, issuer Microsoft Code Signing PCA 2024, thumbprint `AB172913A2960A224809EE8A0C371CD47A079B72`; SHA do executável `0B5037541DD06D536DF5AB1E153DF321EED8418249B8CD505429D3EE6EA520A0`. Só após isso o caminho absoluto executou e reportou **7.5.11 x64**. Recibo `portable-verification.json`, SHA `499C3056ADB01BF48A60C7C306359297BF89652FDB08CC25225965DCE74ABD47`. Nenhum PATH global foi mudado. O JDK 17.0.20.1 já provisionado fora do repo teve `java.exe` SHA `1977F302375ADBB920D41DAC65C7E22EB9C2ED8E1E8D6258964154FF16F14406` e foi restrito ao processo.

## Build A/B e validação de bytes

Reserva offline `build-reservation.json`, SHA `11A68EBCB8CCB560E2AEE620ECBA304C06429FDFCA346E4803CF9411725C4AE6`, conferiu os **2170/2170** hashes de `0382` sem drift e destino novo `target/macrobloco-p08-runtime-0383-offline-20260929-01`. A/B rodaram serialmente por `Invoke-QualificationBuild.ps1 -Phase PackageShadow`, com JDK 17, orçamento 900 s por tentativa, `--offline`, URL JDBC removida do ambiente do processo e sem `spotless:apply`. Ambos terminaram exit 0, `inputIntegrity=true`, **4135 entradas de build iguais** entre A/B. Spotless `check` declarou **1210 limpos/0 mudanças**, Checkstyle **0 violações**. Os logs registram `Tests are skipped` pela fase de pacote permitida; **não** houve nova execução de testes ou JaCoCo check nesta fase. O `clean verify` integral de 0381 mantém SHA `C4267100537646FD8A57B5CE23208F8B0E836A302BC05056B752FBD0C468E21B` e prova somente a camada base offline.

`New-QualificationPackage.ps1 -Candidate -ShadowLocalCandidate` gerou A e B sem alterar fonte. O validador privado novo `target/macrobloco-p08-runtime-0383-offline-20260929-01/validate-candidates.ps1`, SHA `D89CF23A068B4CE36768C5929332A82D459609915B78EA2B2C8C2EAFA05A95E5`, usa `QualificationPackage.psm1`, reconstrói a revision do inventário congelado, valida payload/manifest e extrai cada ZIP em destino novo. Para **cada** candidato conferiu hashes dos **188 membros** declarados e dos **190 arquivos** extraídos (incluindo manifest e sidecar) contra o payload, e os **2170 hashes** no worktree/build; A/B coincidiram. Recibo `candidate-a-b-validation.json` SHA `B9A2DF7699FDB4F2D11D3A05F2DFEB2A43BC263806A50F3ED8EF410D83338EF7`:

| Pin atual, somente candidato offline | SHA-256 A = B |
| --- | --- |
| Revision | `16B013BF258A76E54E451CDF0039F598EECF3D23D1D8C8246AE86F93F119E1EB` |
| Manifest | `576C60B115DEA15D3B4C4D1271780500DD54A8271C873A648B37222A5EEAC171` |
| ZIP | `64A01528AF95F209D662598E7C02D135FB7B6C8E44A240779DD2CD99FC912CC6` |
| Lock 12.8.2 | `7CA07D0509DFE2300BBBE0077EC2B83E5F177A57E857E29D64DAEEE660763465` |

Pins 0374 ficam preservados como fotografia histórica, não representam os dois testes Runtime atuais. `native-staging.json` marcou DLL `PASSIVE_BYTES_ONLY`, `loaded=false`; cópia de byte no pacote não foi execução/uso da DLL.

## Validadores, FAILs e limites

| Camada | Resultado observado | Evidência privada |
| --- | --- | --- |
| Scanner | Self-test **20 PASS**; checkout **4136 candidatos, 4135 textos, 1 binário verificado, 0 achados, PASS** sob pwsh 7.5.11. | `scan-selftest.stdout.log` SHA `41A0DA0F58B92E6E35F3B7C2C4822FB1620AAC5ADC56183E88ED48DF0864A066`; `scan.stdout.log` SHA `30B3D105B30E878D7E90FAE5D808A0C7AD140751D07A131E39A55893F4C6FE01`. |
| Trilha P01–P33 | `Test-TrilhaPreparation.ps1 -SelfTest` **PASS**: 1 positivo + 24 negativos; 33 etapas, 48 IDs abertos, nove pacotes, `executionAuthorized=false`. | `trilha-preparation.stdout.log` SHA `4721067C08D845B7B866D6D36C6990883685B881FCC27205E0D393255B8671F3`. |
| Guardas de pacote/schema | `Test-QualificationPackageGuards.ps1` **27 PASS**; schema guards sobre build A **4 PASS**, só em cópia privada. | `package-guards/result.json` SHA `25A2AB10EE441BC05D6C7682AE43F985C55A411FCE4521B619F18E8B3A89C9D6`; `schema-guards-result.json` SHA `4E614C2886A7ADEFF618570A259E3B09B8F7EA95639448B711C111F653C10E73`. |
| Guardas extraídos, primeira tentativa | **FAIL preservado**, 1 caso: exit 2 esperado, `QUAL_PACKAGE_RUNTIME_LOCATION` em vez de `QUAL_JSON_MEMBERS`; launcher escolheu Java 25 do PATH herdado e o guard exige Java 17. Nenhum controle criado. | `extracted-guards-a/result.json` SHA `9842EAA2A4DBB9F91484F93D8BFCF8119FB62D8CE99E4A25BF7F0DC07F2CB0CF`. |
| Guardas extraídos, nova tentativa | Com JDK 17 prefixado **somente no PATH do processo**, **22/22 PASS**, `jdbc=NOT_STARTED`, `childrenCreated=0`; fonte/artefato original intacto. | `extracted-guards-a-jdk17/result.json` SHA `9C6A2E08DC1D740C37D8AFDCA0A0C891118D4317A4A829DEC32B96EFDAA44A5C`. |
| Validadores históricos distintos | `Test-ContinuidadeAgentes.ps1` e `Test-Gpt56ChatTrail.ps1` retornaram **FAIL `HANDOFF_PATH`**; esse escopo histórico já era conhecido e não valida a trilha P01–P33 atual. Sem mascarar ou promover PASS. | `continuity.stderr.log`/`chat-trail.stderr.log` SHA `7BA1C2EAE60D0523848590F2ABD7E002E696A89A3BC9EC8DA7AA0EA2C775F057`. |

Recibo agregado privado `target/p08-runtime-0383-package-shadow-offline/final-receipt.json`, SHA `5AD2DEC505800333AFC600696256C4718332BC2C00D28B7C826D1F0879031163`. Nenhum processo próprio ou reserva física remanescente. Nenhum SQL/JDBC/IT física, rede de fonte, Flyway, serviço, segredo, deploy, smoke físico ou mudança em fonte/POM/script versionado. `graphify update` não se aplica porque o único código novo é validador privado ignorado em `target`, sem alteração de código do repositório.

## Diff, risco e retomada

Diff desta unidade: novo diretório portátil **fora** do repo; recibos/candidatos/validador privado sob `target`; acréscimos em `STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md`, `RETOMADA.md` e este checkpoint. O primeiro FAIL de guard e os dois `HANDOFF_PATH` continuam registrados. Risco residual: candidatos A/B são determinísticos e íntegros **na camada offline**, mas não executaram testes de pacote, JaCoCo shadow, smoke ou IT física; o XML 0351 e as 8 falhas/74 classes faltantes 0354 continuam. O PowerShell portátil permanece isolado fora do repo, sem integração global; recuperação, se futuramente decidida, é remover apenas esse diretório depois de conferir ausência de processos próprios, nunca alterar sistema/serviço.

1. Supervisor revisa os recibos e pins A/B como **candidatos offline**, preservando os FAILs e sem marcar P08/P01–P33 aceitos.
2. Eventual gate físico distinto exige escopo/reserva/preflight novos e Banco como único executor SQL/JDBC/ledger; esta unidade não autoriza esse efeito.
3. Runtime fica ocioso após o handoff desta resposta; não inicia callback enquanto Codex estiver `WORKING`.
