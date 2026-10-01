# 0382 — P08 Runtime: PackageShadow A/B bloqueado antes do build

- Data: 2026-09-29. Anterior: [0381](0381-p08-jacoco-shadow-classificacao-offline.md), SHA-256 `0F36516D99F2E5D5A0EAC6C43938CD0C2CBCC473B817D0FE1CA6AF0F9C9EB229`.
- Instrução efetiva: refazer A/B nos bytes atuais, congelar inventário, usar os scripts existentes sem skip/apply/limiar reduzido e comparar revision, manifest, ZIP, payload, extração e hashes. Sem PowerShell 7 disponível, não instalar ferramenta; classificar causa e encerrar. Unidade estritamente offline, sem SQL/JDBC/DLL/IT física, serviço, rede, Flyway, segredo, deploy, smoke ou fonte real. Runtime é editor único de seus artefatos e de STATES/trilha/RETOMADA/checkpoint.
- Estado: **BLOQUEADO_ANTES_DE_PACKAGE_POR_AMBIENTE_E_CONTRATO_DE_SCRIPT**. Nenhum candidato A/B novo foi produzido; P08/Gate 1/P01–P33 continuam abertos. `../CONTEXTO_GLOBAL.md` segue ausente. AGENTS/STATES/RETOMADA/runbook e 0374/0381 foram consultados; `maestri list` identificou Codex como Supervisor.

## Inventário congelado e comparação possível

O inventário qualificado 0374 `candidate-a/qualified-source-inventory.json`, SHA `2DA891C3DED88DF894B8D82FFFBFC5E19F2C46E08ECE71B03B51732316713B3D`, contém 2170 entradas. Leitura inicial sob Windows PowerShell 5.1 agrupou o array JSON em um objeto de pipeline e recusou `BASE_INVENTORY_COUNT` **antes de criar recibo**; a leitura corrigida passou. Todos os 2170 caminhos ainda existem. O inventário atual está em `target/p08-runtime-0382-package-shadow-blocked-offline/current-source-inventory.json`, SHA `D43A5BF7EE106D64351E76A7A7F4F473C375136697A7270868193A36EF543A12`. Comparado ao inventário 0374, **2168 hashes iguais e somente dois diferentes**:

| Arquivo de teste Runtime | SHA 0374 | SHA atual |
| --- | --- | --- |
| `QualificationJsonPathTest.java` | `22446B9018AC30D98C5132BC094AA61F0092F520B24FCE23E3CBD37594B40A00` | `06B2F55998150AF5E07AEE1C482F7F5E9CD1A19B38827417695DBC68B1E92B94` |
| `QualificationPackageIntegrityIT.java` | `E479A7E64A9DB0F7B2DEBBBD530D96ABF8D442C46CDAC570D94ABA04A22FFCAA` | `1492C600A4EEC9A923A2819A716FA80AE1F35BF35A0C6B7F0DBC9EF04FF0626E` |

A fórmula de revision do empacotador reproduziu o pin histórico 0374 `C05B97B06CFC06F5DE8B3A5B2CC6516B61F21EA642E1740C7F538A95EF517565` a partir desse inventário. Sobre os bytes atuais, calculou `16B013BF258A76E54E451CDF0039F598EECF3D23D1D8C8246AE86F93F119E1EB`. Este último é **somente cálculo de fonte**; não existe build/pacote novo que o valide como revision de candidato. Recibo estruturado `target/p08-runtime-0382-package-shadow-blocked-offline/receipt.json`, SHA `181C4EBACC137A6CBF3F1CFA09F9C03B9804A314B0FCCE9E7A00785F5C2C8A54`.

## Causa da parada e limites da evidência

`Get-Command pwsh.exe` não encontrou comando. O shell disponível é `powershell.exe` **5.1.26100.9444**. Os três componentes necessários — `scripts/validation/Invoke-QualificationBuild.ps1`, `New-QualificationPackage.ps1` e `QualificationPackage.psm1` — têm `#Requires -Version 7.5` na linha 1. Assim, o fluxo existente não pode produzir/validar A/B neste ambiente. Independente dessa versão, o ramo `PackageShadow` do primeiro script adiciona `package -DskipTests` (linha 83), em conflito com a instrução desta unidade de não usar skip. Não alterei script nem baixei limiar ou contornei requisito.

| Critério | Observado nesta unidade |
| --- | --- |
| Congelar e comparar inventário de fonte | **PASS offline** para 2170 entradas; dois drifts exatos; revision apenas calculada. |
| Build `PackageShadow` A/B sem skip/apply | **NÃO EXECUTADO**: PowerShell 7.5 ausente e script atual passa `-DskipTests`. Zero tentativas de package, zero saída de build nova. |
| Manifest/ZIP/payload/extração/hash A/B atuais | **AUSENTES**: nenhum candidato novo; nenhuma comparação ou PASS possível. Pins 0374 preservados como históricos e inválidos para os dois testes atuais. |
| Scanner e trilha | Scanner 0381 mantém **ERROR/FAIL** sob 5.1; não foi repetido nem promovido. Validadores de trilha que exigem PS7 não foram executados; nenhum PASS. |

Sem processo próprio, reserva física ou estado de efeito incerto. Os recibos e candidatos 0374 e os FAILs 0351/0354/0378–0380 foram preservados; não houve Maven novo, SQL/JDBC/DLL/IT, serviço, rede, Flyway, credencial, deploy, smoke ou fonte real. O `clean verify` offline 0381 conserva seu PASS **somente da camada base**; a cobertura shadow permanece sem prova atual, 0354 mantém 8 erros e 74 classes não executadas, sete esperas sem causa fechada e 2536 stats somente observacionais. Nenhum P08/P01–P33 foi aceito.

## Diff, risco e retomada

Nesta unidade foram adicionados apenas o inventário/recibo privados em `target` e este checkpoint, e atualizados `STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md` e `RETOMADA.md`; **zero mudança de código Runtime, POM ou script**. Risco: o pin de revision calculado não garante igualdade de manifest, ZIP ou payload e não pode sustentar gate; `-DskipTests` no fluxo atual impede cumprir o pedido mesmo em shell compatível. Reversão documental, se necessária, é nova emenda de estado; não apagar histórico ou recibos.

1. Supervisor revisa o recibo dos 2170 hashes e a parada; mantém pins 0374 históricos e Gate 1/P08 abertos.
2. Para uma nova unidade A/B, usar ambiente com PowerShell 7.5 já autorizado ou obter autorização específica para provisioná-lo; corrigir o fluxo `PackageShadow` para obedecer à exigência sem skip antes de invocar o script.
3. Só após build A/B novo, conferir revision/manifest/ZIP/payload/extração/hash e validar scanner/trilha no shell requerido. Nenhum PASS físico decorre desse passo offline.

Handoff desta resposta ao Codex; conforme a mensagem efetiva, este terminal não aguarda callback enquanto o Supervisor está `WORKING` e encerra o turno.
