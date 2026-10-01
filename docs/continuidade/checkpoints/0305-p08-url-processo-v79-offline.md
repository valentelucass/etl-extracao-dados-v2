# Checkpoint 0305 — P08 URL de processo e pacote 12.8.1 qualificados offline

## Identificação, critério e autoridade

- 28/09/2026 07:41 UTC; anterior: [0304](0304-p08-candidato-fisico-1281-offline.md), SHA-256 `A6ABF9B3A048BF6CBA886845E515F0D4920158357C8802696596A1C76C34606D`.
- Objetivo: avançar P01–P33 pelos critérios originais, com prioridade P10/G02 e P07/P08/P29 independentes. `STATES.md` é a autoridade; [mapa P01–P33](../qualificacao-p07-p33/mapa-p01-p33-20260927.md) é índice.
- Decisão do Supervisor: P08 deve consumir somente `V2_SHADOW_JDBC_URL` do processo, validada antes de master/worker, sem contrato estático nem certificado hardcoded. A decisão substitui a alternativa aberta em 0304.
- Usuário autorizou instalação de serviço SQL Server **nesta máquina** e criação exclusiva de `localhost/ETL_SISTEMA_V2_SHADOW`, mas ordenou não repetir UAC após cancelamento até o operador indicar prontidão. A máquina shadow antiga está fora do escopo e não foi acessada. Nenhum setup, serviço, SQL, DDL, DLL carregada, fonte real, push, merge, deploy ou cutover ocorreu nesta unidade.
- Branch `main...origin/main`; deltas preexistentes e todos os recibos RED/históricos preservados. `../CONTEXTO_GLOBAL.md` segue ausente na busca segura anterior.

## Alteração e decisão técnica

`QualificationConfiguration` lê somente a variável de processo, exige host
literal `localhost`, DB exato, autenticação integrada, TLS e escolha explícita
de certificado, limita timeouts e recusa credenciais, domínio, porta/instância,
opções extras/duplicadas e entrada ausente. Canonicaliza antes de reutilizar
`ShadowStorageProperties`; não persiste nem imprime a URL. `run`/`worker`
validam antes de controle/intent, e o supervisor passa ao filho apenas a URL
validada entre as variáveis `V2_*`. `status`/`resume` preservam readback offline.
O build físico exige URL de processo, enquanto `PackageShadow` a remove e
empacota JAR/DLL 12.8.1 apenas como bytes. O lock global 12.8.2 continua
separado. Testes de URL positivos/negativos e projeção pai→filho foram
incluídos. Um literal de fixture foi corrigido após RED do scanner; a v79
executou sobre esses bytes. PMD v77 teve novo achado, corrigido causalmente;
v78 voltou aos 36 achados históricos, sem supressão.

## Execução e evidência

| Gate | Camada e observado | Recibo privado |
| --- | --- | --- |
| Espelho v79 | 4.033 arquivos; `clean verify` JDK17/Maven offline exit 0, formatter/Checkstyle, 2.351 Surefire em 279 XML/cinco skips, seis ITs offline, JaCoCo 80/60 PASS. 1.547 arquivos críticos Java/POM/scripts/lock/README/escopo byte-idênticos ao worktree após a cópia. | `target/ci-p10-20260927-01/clean-verify-v79-private.log` |
| PMD/P29 local | 656 fontes, 36 achados brutos v78; disposição v79 36/36 `PASS_LOCAL_REVIEWED_FINDINGS` contra 2.351 testes; `nominalSecurityAcceptance=false`. | `pmd-v78/result.json`, `pmd-disposition-v79/result.json` |
| A/B shadow | `PackageShadow` A/B exit 0, sem URL/SQL/DLL executada. Mesmo lock 12.8.1, revisão `be9e54929e4d510d4803271c64f9a3850e9c894ca1bb24725d05c96ea2a0ec2d`, manifesto `007a779729ec36f3bfed3c85e41f7159d704982d9ffb4cd885cabdcbf5048d8f`, ZIP `4e707a9c146a4deca7e5bb75ca3e34b03e1633d2f2321e7b717225530addbc8e`; 186 membros/nove dependências/2.158 inputs. | `target/macrobloco-qualificacao-pacote-20260928-01/p08-shadow-url-v79-candidate-{a,b}/result.json` |
| JARs extraídos | A/B: `config-validate`, `dry-run`, `inspect`, `plan`, oito exit 0/stderr 0 em JDK17/512 MiB/30 s, sem JDBC. | `target/ci-p10-20260927-01/p08-shadow-preflight-v79-result.json` |
| Guardas | Quatro recusas de pacote/pin antes de Java; duas URL ausente/remota exit 2 antes de controle/SQL. Envelope sintético 25/25 PASS. | `p08-shadow-guards-v79-result.json`, `p08-envelope-guards-v79-private.log` |
| Regressão normal | Pacote 12.8.2 normal, lock global intacto, 188 membros; `config-validate` e `dry-run` puros PASS. | `p08-global-url-v79-candidate/result.json`, `run-p08-normal-pure-v79.ps1` |
| Scanner e grafo | Scanner offline v79 4.033 candidatos, zero achados; `graphify update . --no-cluster` exit 0. | `offline-secret-scan-v79-private.log`, `graphify-update-p08-v79-private.log` |

Estado dos candidatos: `PACKAGED_NOT_SMOKE_QUALIFIED`. Nenhum checkbox
integral P07/P08/P10/P29 foi promovido. O gate local de CI/cobertura está
verde; G02 ainda precisa de novo SHA/checks remotos e aceite próprio.

## Retomada imediata — até três ações

| Ordem | Ação concreta | Pré-condição | Prova esperada |
| --- | --- | --- | --- |
| 1 | Reconciliar UAC/serviço/setup e seguir [reconstrução local](../../runbooks/reconstrucao-shadow-local-20260928.md), com preflight read-only em `localhost/master` antes de qualquer `CREATE DATABASE`. | Operador sinaliza prontidão; nenhum estado parcial inesperado; alvo continua apenas `localhost/ETL_SISTEMA_V2_SHADOW`. | Serviço local e DB exato ou recusa por estado preexistente; migrations V001–V104, inventário, sintéticos/rollback e contagens somente após preflights. |
| 2 | Executar IT Maven opt-in 12.8.1 e P08 A/B física, com URL validada e escolha de certificado fundada no preflight. | Sombra local íntegra, JDBC opt-in, contagens antes/depois, orçamento e controle novos. | JDBC/rollback, smoke supervisor/worker, sucessão, selo/readback e comparação A/B sem drift. |
| 3 | Vincular G02/P29 aos aceites externos da mesma revisão. | Novo SHA/checks de CI, RC e decisões nominais de release owner/Segurança/licenças/SOs. | Evidência externa real, sem inferir aceite de prova local. |

Parar em UAC cancelado, host/banco diferente, DB/objeto preexistente, SQL
incerto, DLL 12.8.2 no shadow, drift de lock/contagens ou TLS inválido. O
pacote offline e a documentação não substituem campanha física nem aceite
nominal.
