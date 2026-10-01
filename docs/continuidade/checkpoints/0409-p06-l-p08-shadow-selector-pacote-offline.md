# 0409 — P06/L e P08: seletor shadow corrigido, base e pacote offline

- Data: 2026-09-30 18:22 UTC. Anterior: [0408](0408-p08-p07-package-shadow-ab-base-offline.md). A fotografia privada 0411 permanece histórica; esta unidade usa `target/macrobloco-p08-runtime-0412-offline-20260930-01` e `target/p06-l-shadow-selector-20260930-01`.
- Escopo: três `include` explícitos em `pom.xml` para `SequenceReferenceIT`, `SequenceRecompositionIT` e `SequenceFailureIT` no Failsafe do perfil `shadow-local-integration`. Contra o snapshot 0411, **somente `pom.xml` mudou** nos inventários integral e de pacote; SHA novo `3C7EFC2A6FDD0EAEF99FB916F2D864D80CCA9E90A357522FDE27829AAE30FFAC`. Nenhum critério, skip, limiar, teste ou falha anterior foi removido.

## Prova causal sem SQL

O POM antigo tinha 15 includes e omitia as três classes; o novo tem 18. `help:effective-pom` offline com perfil/trava shadow contém as três; o efetivo base contém zero delas. As três fontes existem e as três classes foram compiladas no gate base. Três mutantes de omissão foram recusados. Recibo `selector-proof.json` SHA `285A7DAD3AB1333E3A849AFCC538AB734BBD8ACB8A8546E6B7A9BB2037CB0722`. A primeira chamada do effective POM falhou por divisão do argumento `-D...` no PowerShell 5; a chamada com argumentos entre aspas passou, sem IT. **Seleção não equivale a execução física nem a aceite de P06/L.**

Snapshot novo: 4166 arquivos/1214 Java, sem `target`/`.env`, inventário integral SHA `460351E4607A4298A0AA00A8CA836ECB13A12DAAB0A5AF143AE50C408650E67D`; pacote 2174, SHA `EF8F29B27987563B38D846D459C9FDB8380BE9B3486702534AC59EE40365745B`. JDK17 Maven `--offline clean verify`, sem perfil shadow, URL, skip ou DLL carregada: **PASS** Enforcer/Spotless/Checkstyle; Surefire **2378/0/0/5**, Failsafe offline **6/0/0/0**, JaCoCo `report/check` e Failsafe `verify`. Log SHA `C302CCBF670B328CDC785A5CF80BF6937B20A31033DB65FF0231C920982D9248`; recibo de base SHA `94B29B537547E1E7AF64FA619D8586BC7B8041C747134069B2377D9ED04F2461`. Scanner offline **4167 candidatos/4166 textos/um binário permitido/zero achados**, log SHA `56E9E0882CA189EBD4CBFB89942BD1FC0C1ADC17E12B4A3C4F39EA93A3ED1922`.

Somente após esse PASS, PackageShadow A/B independentes usaram o mesmo snapshot, Maven offline `package -DskipTests` com perfil shadow apenas para empacotar 12.8.2 e DLL passiva. Validação vinculou o recibo e SHA do log da **base 0412**, comparou 2174 hashes, manifest/ZIP/payload A/B byte a byte, 188 membros e 190 arquivos extraídos por candidato. CLI extraída `config-validate`/`dry-run` A/B: quatro exits 0, JDK17, sem ambiente `V2_*`/JDBC. O primeiro recibo de validação citava 0411 por metadata privada; cópia preservada. A primeira revalidação parou em `QUAL_EXTRACT_DESTINATION` porque o destino anterior existia; FAIL preservado em `validator-rerun-fail.log` SHA `757B255F6955CD30C96D0AC6A978A29C17579EA809AB3F5CA48AD1113FC93502`. Revalidação em destinos novos passou; mutante de SHA da base foi recusado antes da extração (`base-receipt-mutant.log`). Nenhum candidato foi reconstruído.

| Pin candidato 0412, A = B | SHA-256 |
| --- | --- |
| Revision efetiva | `4763D5AF123BC685D3EDDF19BD0726416577CC80A0CD81B9B930CEDA22849130` |
| Manifest | `C7CF032CDA8CC8ECC59580D5E9D090B2C5BEFAF7C5BA44FD97F61A55880A49DE` |
| ZIP | `71904040C6B193B407DA63C9619053DBB755514EEDDCD02F504C3CD2036C7924` |
| Lock 12.8.2 | `7CA07D0509DFE2300BBBE0077EC2B83E5F177A57E857E29D64DAEEE660763465` |

Recibos privados: A/B SHA `946C90B1792C11DA08772753853783972A592C641BBC767933AEAF04AAF03475`; CLI SHA `7B6C192867CCA8ADD3A07985F13A903C9481E44F30A530152477EF675C11B75B`; entrega SHA `BCB4AAE06ED36D4FB7131528961A76B10EBCDAFE999D88A462F73FA26B0E7503`. Estado **PACKAGED_NOT_SMOKE_QUALIFIED**. Os pins 0408/0411 continuam históricos, sem serem reescritos.

Validação documental: `Test-TrilhaPreparation.ps1` PASS (33 etapas, 48 IDs abertos); `Test-ContinuidadeAgentes.ps1` conserva FAIL histórico `HANDOFF_PATH`, sem alteração de seu contrato. Logs em `target/macrobloco-p08-runtime-0412-offline-20260930-01`; o FAIL não foi promovido a PASS.

Handoff Regras: **somente P03/B** foi aceito nos bytes 0408 por igualdade SHA de fonte/teste e sete dependências com snapshot/A-B. Essa prova restrita também vale nos bytes **0412**: entre 0411 e 0412 só o POM mudou, sem alterar fonte/teste/dependências de B, e o XML 0412 `LocalArtifactSequenceTest` confirma **27/0/0/0**, SHA `0D16AB6D1E04EA6BE7876C4457F6146B3B35A7983E46F5EDF2A9ACEAEE226E3A`. Não repetir B sem mudança. P03 agregado, C–H e L/P06 continuam abertos; a inclusão Failsafe nova apenas elimina uma omissão de seleção. Oito FAILs/74 classes faltantes de [0354](0354-p08-107-it-gate-fail-readback.md), STOP de [0402](0402-p08-metodo5-stop-os-elevacao-incerta.md), método 5 sem PASS, JaCoCo shadow, A/B físicos, P07/P08 e Gate 1 permanecem abertos. **107 ITs é a cardinalidade histórica de 0354; a seleção futura não foi inventariada/executada nesta unidade.** Os 67/115 são checklist histórico, não medida do aceite integral atual.

## Próximas ações, no máximo três

1. Supervisor revisa diff e recibos; passa a Banco os pins 0412. Banco é dono do ledger, SQL/JDBC e de qualquer IT física; uma investigação dirigida da primeira falha 0354 é elegível só em unidade própria, uma IT por vez, com preflight, reserva e readback novos e telemetria sanitizada `START`/`END`.
2. Método 5 exige disponibilidade do operador para UAC ou mecanismo autorizado de observação/limite; não há PASS por este pacote. Não repetir A/B base sem mudança de bytes.
3. Integrador conserva aceites restritos e FAILs históricos, sem promover checkbox de P06/L, P07/P08, P03 agregado ou P01–P33. Zero SQL/JDBC físico, Flyway, serviço, UAC, rede de fonte, deploy, release ou cutover nesta unidade.
