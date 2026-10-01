# Checkpoint 0306 — P29/P11 SAST amplo bruto e evidência de release aberta

## Identificação, critério e autoridade

- 28/09/2026 08:03 UTC; anterior: [0305](0305-p08-url-processo-v79-offline.md), SHA-256 `E1C864BC09C1E2C3C9A2F115B17F6109C1EACE482F581F1E5A19AD291C20AA24`.
- Objetivo: avançar P01–P33; nesta unidade, P29/V2-039b/V2-015c (RC, SAST, licenças, proveniência, SOs) e P11/V2-015d (feed, achados e baseline). `STATES.md` é canônico; [mapa P01–P33](../qualificacao-p07-p33/mapa-p01-p33-20260927.md) indexa critérios/owners.
- Instrução efetiva: o Supervisor pediu prontidão técnica independente enquanto UAC aguarda operador, sem inventar política, limiar, aceite ou RC. A autorização do usuário para instância local segue condicionada à prontidão após cancelamento; nenhum UAC foi repetido.
- Branch `main...origin/main`, deltas e recibos históricos preservados. Nenhum SQL, DLL carregada, serviço, DDL, fonte de negócio, push, merge, deploy ou cutover ocorreu. A máquina shadow antiga não foi acessada.

## Passos e prova

| Passo | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Inventário | Leitura do critério/artefatos | P11 exige política/feed/baseline aceita; P29 exige RC único, SAST/licenças/proveniência e SOs. PMD dez regras e Gitleaks não eram SAST integral. | `STATES.md`; [relatório](../../catalogos/p29-sast-preparacao-20260928/RELATORIO.md) |
| SpotBugs + FindSecBugs | SAST exploratório offline quanto a banco/fonte; artefatos do scanner resolvidos só no Maven Central | Plugin 4.10.4.1/engine 4.10.4/FindSecBugs 1.14.0, Java17, Max/Low, zero filtros/supressões. 656 fontes e 1.135 artefatos iguais ao build v79; 1.091 classes, zero erro/classe ausente, 273 alertas brutos, 88 SECURITY. Primeiro RED por settings privado `offline=true` preservado; correção só no espelho. | `target/ci-p10-20260927-01/p29-sast-v80-result.json`, `p29-sast-mirror-v80/target/spotbugsXml.xml` SHA-256 `47ed49fee7058090ce71d43f106e06a664193ea6d602e61784108e0964fe8224` |
| SBOM/licenças | Pacotes A/B/normal existentes, sem reempacotar | Shadow e normal: lock 9, SBOM 9, POMs pinados 9/9; licença nativa proprietária distinta do JAR MIT. Cópia privada com licença alterada e envelope re-hasheado passou guarda externa e foi recusada no `inspect` por `QUAL_SBOM_LOCK_CORRESPONDENCE` antes de JDBC. Primeiro RED do harness por CRLF corrigido em arquivo privado. | `target/ci-p10-20260927-01/p29-license-counterproof-v80-private.log`; `p08-shadow-url-v79-candidate-a/result.json` |
| P11 policy/report | Sintético, sem NVD/segredo | `-PolicyOnly` e `-VerifyImplementation`: 33 casos/30 recusas PASS, zero exceções. `-ReportPath` ausente recusou `MACHINE_REPORT_MISSING`. Último scan público 21/09 é histórico e cobre POM normal. | `target/ci-p10-20260927-01/p11-current-report-absence-v80-private.log`; `docs/catalogos/p11-publico-corrigido/RELATORIO.md` |
| CVE/versão | Fontes oficiais, sem executar DLL | A variante shadow 12.8.1 exigida por AGENTS é apenas de teste; [Microsoft](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver) corrigiu CVE-2025-59250 em 12.8.2 e o [NVD](https://nvd.nist.gov/vuln/detail/CVE-2025-59250) inclui 12.8.1 na faixa afetada. O normal 12.8.2 não tem prova física P08 atual. | Locks separados e XML/relatório P29 |
| Guardas finais | Espelho documental da revisão | Gitleaks e scanner offline 4.036 arquivos/candidatos, zero achados; trilha 33 etapas/48 IDs abertos/nove pacotes; `git diff --check` e UTF-8 estrito PASS. | `target/ci-p10-20260927-01/gitleaks-p29-v80-private.log`, `offline-secret-scan-p29-v80-private.log` |

O estado SAST é `RAW_FINDINGS_OPEN_NO_NOMINAL_ACCEPTANCE`. A triagem pontual
de SQL por enum fechado e do ProcessBuilder em lista de argumentos não dispôs
os 273 achados. Nenhum finding foi suprimido nem convertido em vulnerabilidade
aceita/descartada por contagem. Scanner Java não cobre PowerShell/SQL, histórico
remoto, licenças legais ou SOs. O POM de projeto, fontes Java e pacotes v79
permaneceram byte-idênticos; por isso PMD e `clean verify` não foram repetidos.

## Retomada imediata — até três ações

| Ordem | Ação | Pré-condição | Prova esperada |
| --- | --- | --- | --- |
| 1 | Segurança e release owner fixam cobertura/ruleset/limiar/baseline/exceções/aceitante SAST, feed/SCA atual e licenças, versão JDBC, SOs e configuração do RC. Triar achados por evidência, sem supressão automática. | Decisão nominal datada e revisão/RC exatos; não usar variante shadow 12.8.1 como release. | Política e classificação dos 273 achados, baseline P11, licença/RC e smoke nos SOs da mesma revisão. |
| 2 | Após operador sinalizar prontidão, reconciliar instalação e fazer preflight exclusivo em `localhost/master`; então seguir P07/P08 física conforme runbooks. | UAC liberado pelo operador; alvo/estado exatos, sem instalação/banco parcial. | Serviço local, schema V001–V104, sintéticos/rollback, contagens e A/B física, ou recusa segura. |
| 3 | Obter G02/CI remoto e os inputs das demais P09–P33 no [mapa](../qualificacao-p07-p33/mapa-p01-p33-20260927.md). | Owners entregam SHA/checks/atestados específicos; nenhuma publicação inferida. | Aceites vinculados à mesma revisão sem checkbox por documentação. |

Parar em UAC cancelado, alvo SQL divergente, novo resultado incerto, política
ausente tratada como aceite, supressão sem owner ou tentativa de usar 12.8.1
como RC. Não há aceite integral P11/P29/G02/P07/P08 nesta unidade.
