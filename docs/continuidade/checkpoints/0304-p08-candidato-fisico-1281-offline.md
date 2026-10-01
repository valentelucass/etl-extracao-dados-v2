# Checkpoint 0304 — P08 par físico 12.8.1 preparado offline

## Identificação, critério e autoridade

- 28/09/2026 06:36 UTC; anterior: [0303](0303-p08-smoke-cli-extraido-offline-a-b.md), SHA-256 `C8F14FE03CA1C6B86D8449C2CBD97D58EAEEBFECD0D17C184AC5FECB68AA2CE9`.
- Critério corrente: resolver causalmente o conflito JAR/DLL entre lock P08 normal 12.8.2 e perfil shadow autorizado 12.8.1, com candidato físico reproduzível e guardas; não antecipar P08 integral. `STATES.md` é autoridade; [mapa P01–P33](../qualificacao-p07-p33/mapa-p01-p33-20260927.md) é índice.
- Usuário mantém a ordem de **não repetir UAC até o operador indicar prontidão**. Nenhuma SQL, DLL carregada, instalação, DDL, fonte real, push, merge, deploy ou cutover nesta rodada. Máquina shadow antiga, em outro host, não foi acessada. `../CONTEXTO_GLOBAL.md` permanece ausente na busca segura registrada.
- Branch `main...origin/main`; deltas preexistentes, lock 12.8.2 e recibos históricos foram preservados. Nenhum checkbox P01–P33 foi promovido.

## Alteração e decisão técnica

O lock normal contém `mssql-jdbc:12.8.2.jre11` **JAR** e
`mssql-jdbc_auth:12.8.2.x64` **DLL**. O perfil Maven opt-in shadow já fixa
JAR/DLL 12.8.1. O supervisor P08 abre `QualificationSqlEvidence.master` via
JDBC antes de lançar o worker; o seu launcher tinha apenas o JAR da aplicação
no classpath, enquanto o worker já recebia `lib/*` e `native/`. A correção
colocou `lib/*` no supervisor e exigiu par 12.8.1 no launcher físico antes de
iniciar Java.

`Invoke-QualificationBuild.ps1` ganhou `PackageShadow` offline, e
`New-QualificationPackage.ps1` ganhou variante candidata explícita. Novo
lock/README/POMs físicos ficam em
`docs/catalogos/macrobloco-qualificacao-pacote/`; o lock normal 12.8.2
permaneceu byte-idêntico (SHA-256
`8723c2509a63647dcd7e6edba6a0a008e32e7f4e66b5c2d2b2dbd9268b116eec`).
Os POMs 12.8.1 históricos têm bytes distintos dos POMs Maven Central atuais;
foram preservados, e o candidato inclui somente os POMs novos pinados. A DLL
Microsoft 12.8.1 no cache tem assinatura Authenticode `Valid`, 305.328 bytes,
SHA-256 `9a92363a42db34e9f27cedffe18b139d7bb6c93495cb8338f7f8dbb93f3ae538`.

## Execução e evidência

| Gate | Observado | Recibo privado |
| --- | --- | --- |
| `PackageShadow` A/B | Maven JDK17 offline `package -DskipTests`, duas travas shadow, URL removida do processo; ambos exit 0, nenhum SQL/DLL executada. Uma primeira tentativa RED após Maven por copyback indevido foi preservada e corrigida. | `target/ci-p10-20260927-01/p08-shadow-final-build-{a,b}-private.log`; `action-log.md` |
| ZIP A/B candidato | `SHADOW_LOCAL_12_8_1`, revisão `5f8caceff8e9c2065026d5b8821928730f51cc375ac65ed750a7139a6b772e43`, manifesto `bf9feb190d42cd3597db064659376507ae3fb2e4b76cf0e799d262219e530676`, ZIP `29185f5caccf90fe5d3ec037c7a58f10dac6c5c4a0769fa705d9a55d4f07d8c3`; 186 membros/nove dependências/2.157 inputs idênticos. | `target/macrobloco-qualificacao-pacote-20260928-01/p08-shadow-final-candidate-{a,b}/result.json` |
| JAR extraído A/B | Extração/envelope/pair PASS. Cada pacote: `config-validate`, `dry-run`, `inspect`, `plan` exit 0, stderr 0, JDK17/512MiB/30s por comando; sem worker/JDBC. | `target/ci-p10-20260927-01/p08-shadow-preflight-v72-result.json` |
| Guardas | Quatro recusas antes de Java: global 12.8.2 físico, variante de build trocada em ambos sentidos, pin de manifesto inválido. Envelope sintético 25/25 PASS. | `p08-shadow-guards-v72-result.json`, `p08-shadow-envelope-guards-v72-private.log` |
| Regressão normal | Build/package 12.8.2 normal passou com 188 membros; `config-validate` e `dry-run` puros do extraído passaram. Lock global intacto. | `target/macrobloco-qualificacao-pacote-20260928-01/p08-global-regression-candidate/result.json` |
| CI e gates offline | 1.204 Java/POM sem diferença ao espelho `clean verify` v66 (2.348 Surefire, seis IT offline, JaCoCo 80,34%/68,07%); parser dos três scripts PASS; `graphify update . --no-cluster` exit 0; trilha 33/48/nove PASS, scanner 4.031 candidatos/zero achados, `git diff --check` PASS. | `target/ci-p10-20260927-01/compare-java-pom-v66-v71.py`, logs v66/v72 |

Serviço/processos SQL locais não foram encontrados e `V2_SHADOW_JDBC_URL`
está ausente. Resultado do empacotador: `PACKAGED_NOT_SMOKE_QUALIFIED`.

## Lacuna e retomada — até três ações

O [preflight P08](../../runbooks/p08-shadow-12.8.1-preflight-20260928.md)
contém alvo e comandos preparados, sem efeito. O laboratório usa URL estática
local com `trustServerCertificate=true`; a IT Maven autorizada exige
`V2_SHADOW_JDBC_URL` validada. O owner técnico/Segurança deve decidir entre
adaptar esse contrato e refazer A/B ou autorizar nominalmente o contrato
estático/certificado para o alvo exato. Consistência de versão não basta para
inferir autorização do supervisor físico.

| Ordem | Ação | Pré-condição | Prova esperada |
| --- | --- | --- | --- |
| 1 | Reconciliar mídia/serviço e seguir instalação local do runbook; depois preflight `master` e apenas o banco ausente, V001–V104 e sintéticos/rollback. | Operador indica prontidão UAC; alvo continua exclusivamente `localhost/ETL_SISTEMA_V2_SHADOW`; nenhum estado parcial inesperado. | Serviço local/rede, schema/versões/contagens e rollback em recibos próprios. |
| 2 | Fechar decisão de URL/certificado P08; então qualificar IT Maven 12.8.1 e supervisor/worker A/B em bytes correspondentes. | Owner técnico/Segurança registra escolha, shadow material íntegro, contagens antes/depois. | JDBC/rollback, A/B física, recusas, sucessão, selo/readback, sem drift. |
| 3 | Obter checks/aceites externos G02/P29 e continuar as parcelas P11–P33 que receberem input próprio. | SHA/RC, política e artefatos nominais dos owners. | Evidência externa vinculada ao mesmo SHA/RC, sem aceite inferido. |

Parar em UAC cancelado, host/banco diferente, DB/objeto preexistente,
resultado SQL incerto, DLL 12.8.2 no shadow, deriva de lock ou decisão de
contrato ausente. Nenhuma dessas lacunas é suprida por documentação ou smoke
CLI puro.
