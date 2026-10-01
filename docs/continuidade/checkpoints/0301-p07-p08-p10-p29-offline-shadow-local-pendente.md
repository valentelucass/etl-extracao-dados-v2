# Checkpoint 0301 — gate offline v66; sombra local e G02 abertos

## Identificação e objetivo

- Checkpoint: 0301, 2026-09-28 05:05 UTC, P01–P33 / P07–P08–P10–P29.
- Anterior: `docs/continuidade/checkpoints/0300-p10-g02-ubuntu-verde-shadow-remoto-abertos.md`, SHA-256 `64bd358930f4166b4a6014543314ce6222d17982a9c1a308508e0b656d8c3507`.
- Objetivo: fechar a parcela local CI/cobertura da revisão atual e avançar as parcelas offline independentes, preservando critério original e aceites externos.
- Estado: `TESTADO_NA_CAMADA` para Maven/PMD/pacote offline; `BLOQUEADO_POR_INPUT` somente para serviço/banco local, provas materiais e aceites/CI externos. Nenhum P integralmente promovido.
- Autoridade: `STATES.md`, `AGENTS.md`, matriz e [mapa P01–P33](../qualificacao-p07-p33/mapa-p01-p33-20260927.md).

## Autorização, alvo e preservação

- A instrução do usuário limita a reconstrução a `localhost/ETL_SISTEMA_V2_SHADOW`. A sombra anterior está em **outra máquina**, que não foi acessada. A autorização de 25/08 permite migrations V2 e validações sintéticas revertidas no banco local exato após preflight, mas não autoriza instalar serviço ou `CREATE DATABASE`. O Supervisor encaminhou pergunta específica ao usuário e declarou não ter autoridade para aprovar esses efeitos; resposta pendente.
- O único Builder visível preservou branch `main...origin/main`, todo delta preexistente, ledgers/manifests/checkpoints e logs RED. `../CONTEXTO_GLOBAL.md` não foi localizado na busca segura. Nenhum SQL, conexão de fonte, push, merge, deploy, cutover, serviço ou banco foi criado.
- O alvo técnico, impacto, recuperação, migrations, baseline, validadores, sintéticos, rollback e contagens estão no [runbook de reconstrução](../../runbooks/reconstrucao-shadow-local-20260928.md). `target/ci-p10-20260927-01/action-log.md` contém pré-condição/alvo/limite/recuperação antes dos efeitos locais; recibos extensos ficam em `target/` privado.

## Decisões e alterações nesta unidade

- O Supervisor fixou `mssql-jdbc_auth:12.8.1.x64`. `pom.xml` fixa driver `12.8.1.jre11` e DLL `12.8.1.x64` em **ambos** os perfis shadow; valores globais 12.8.2 e lock P08 histórico foram preservados. A [documentação Microsoft](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties?view=sql-server-ver16) requer a DLL versionada para NativeAuthentication. Avaliação Maven efetiva confirmou seis propriedades; a árvore do perfil migrations resolveu driver 12.8.1. A DLL 12.8.1 foi apenas copiada a `target/native` para compilação opt-in; não foi carregada nem versionada.
- Cinco POMs do catálogo haviam sofrido conversão CRLF→LF no checkout, divergindo do lock. Os bytes publicados pinados foram restaurados, `.gitattributes` desliga normalização apenas nesse diretório, e IT offline nova recusa drift dos nove POMs. O lock P08 12.8.2 ainda diverge de `Package/PackageDirected`, que ativa shadow 12.8.1; não se alegou pacote físico qualificado nem se executou DLL 12.8.2 no perfil shadow.
- PMD 7.17.0 foi resolvido e executado offline: 36 achados brutos, zero parser errors, sem supressão. A disposição técnica local 36/36 passou sobre XML v66; Segurança/release owner ainda devem aceitar política SAST/licenças/SOs e RC.
- Flyway Maven plugin/extensão SQL Server 9.22.3 fixados foram obtidos em cache Maven Central. `flyway:help` é um goal inexistente nesta versão e falhou antes de SQL; nenhum `info/migrate/validate` foi executado. O uso da autenticação Windows no plugin com DLL 12.8.1 exige revisão específica antes do goal SQL, porque a autorização atual da DLL descreve a IT local opt-in.

## Execução e evidência

| Critério | Camada | Observado | Recibo |
| --- | --- | --- | --- |
| P10/P07 build | Espelho v66, Java 17, Maven offline, sem URL | `clean verify` exit 0; Spotless/Checkstyle/Enforcer/pacote; 2.348 Surefire, cinco skips, seis Failsafe offline, zero falhas/erros; 1.313 fontes/POM e 20 POMs do catálogo idênticos ao worktree por SHA | `target/ci-p10-20260927-01/clean-verify-v66-private.log`, SHA-256 `b522aa9cbd1fcd1158277336f0e57c30ec3063308a3358f339839388410db438` |
| P10 cobertura | JaCoCo bootstrap v66 | 4.384/5.457 linhas (80,34%) e 1.942/2.853 ramos (68,07%); limites 80/60 intactos, exclusões apenas classes físicas SQL exatas | XML `candidate-repo-mirror-v66/target/site/jacoco/jacoco.xml`, SHA-256 `6b42f524f5cd6e0ee18093e6bdd8c86bd2900204364a1ce837edd2435398d320` |
| JDBC shadow | Espelho v67, duas travas opt-in, **sem URL/SQL** | `process-test-classes` exit 0, 656 fontes principais/547 testes; única DLL `12.8.1.x64` em `target/native`, SHA-256 `9a92363a42db34e9f27cedffe18b139d7bb6c93495cb8338f7f8dbb93f3ae538` | `target/ci-p10-20260927-01/shadow-profile-process-test-classes-v67-private.log`, SHA-256 `9b7300139f28b717a738797dccfee85c4d261b87a3379b44a77f8645382fcdf3` |
| P29 PMD | Dez regras/656 fontes e 278 XML/2.348 casos v66 | Raw `FINDINGS_OPEN` 36; disposição 36/36 `PASS_LOCAL_REVIEWED_FINDINGS`, zero supressões, 48 vínculos, sem aceite nominal | `target/ci-p10-20260927-01/pmd-disposition-v66/result.json`, SHA-256 `9c8c6cfea8063bd16bad639d518e6990289a3dd03dd21b94f77f5479cfe5ff5e` |
| Schema | Arquivos somente | V001–V104 contíguas, baseline com 104 includes; 36 manifests, 67 validators; dois validadores estáticos PASS v60, sem prova física | `target/ci-p10-20260927-01/schema-foundation-static-v60-private.log`, `progressive-data-static-v60-private.log` |
| Estrutura | Graphify AST final | `update . --no-cluster` exit 0 no Python isolado após crash anterior do launcher Python 3.14; 37.128 nós/95.033 arestas | `target/ci-p10-20260927-01/graphify-update-v66-private.log`, SHA-256 `8e1e113b011b03febde4d68b628f0d4bc619930a5d791baa8f1660d151a6f416` |

Resultados RED preservados: invocação PowerShell sem aspas do `-D` v64; duas raízes inválidas do validador PMD v63/v63b; `flyway:help` sem goal. As correções de invocação foram testadas em outputs novos. Não se interpreta falha de ferramenta como falta de autorização geral.

## Lacunas, inputs e retomada imediata

| Ordem | Próxima ação | Pré-condição/input verdadeiro | Prova esperada | Frente independente |
| --- | --- | --- | --- | --- |
| 1 | Concluir scanner/Gitleaks, trilha, UTF-8/diff nos docs finais | Worktree v66 estável | Recibos locais sem achados e checkpoint validado | Nenhuma dependência externa |
| 2 | Reconstruir e provar P07/P08 na sombra local | Usuário decide instalação e `CREATE DATABASE` ou DBA provisiona; informar instância/edição/versão, storage/collation/backup/recuperação; preflight `master` exato; resolver auth Flyway e lock P08 | V001–V104, inventário/baseline, sintéticos/rollback, contagens iguais, IT JDBC e pacote A–N atuais | P11 aceite de baseline se Segurança entregar |
| 3 | Fechar G02/P29 externos no SHA aprovado | Owner do repositório fornece provider/remote/branch/conjunto/proteções/aprovadores e autoriza publicação; Segurança/release owner entrega política/aceites | histórico remoto escaneado após fetch autorizado, três checks reais CI/Gitleaks; SAST/licenças/SOs/RC nominal | P12/P13 somente com seus artefatos/autoridades próprios |

- Condição de parada: host diverso de `localhost`, banco diverso de `ETL_SISTEMA_V2_SHADOW`, ausência de preflight/rollback, resultado SQL desconhecido, DLL 12.8.2 em shadow, sonda fora da allowlist, publicação ou cutover sem autorização.
- Condição de conclusão integral: cada critério original P01–P33 na camada exigida e com seu aceite externo. A prova offline v66 não promove checkbox de shadow, G02, P08, P29 ou produção.
