# Checkpoint 0309 — booleanos de runtime fail-closed em ASCII

## Autoridade e estado

- 28/09/2026. Anterior [0308](0308-p29-unicode-shadow-v89-v90.md), SHA-256 `B815F7BB5197CFD5772D4AF6FF85E6C0F419EE4A7CE07FD578BB9A034B88A365`.
- Critérios P29/P11/P08 e P01–P33 permanecem em `STATES.md`; esta unidade avança a triagem `IMPROPER_UNICODE` da configuração opt-in, sem aceite nominal de Segurança ou checkbox integral.
- Decisões vigentes: nenhuma execução física JAR/DLL/JDBC 12.8.1 por CVE-2025-59250 antes de decisão explícita do usuário sobre `AGENTS.md`; nenhum novo UAC antes de sinal do operador. O par 12.8.2 é proposta, não pin adotado.
- Branch `main...origin/main`, deltas preexistentes e 0304–0308 preservados. Sem SQL, DLL, UAC, serviço, DDL, máquina anterior, push, merge ou deploy.

## Mudança e evidência

| Parcela | Observado | Recibo privado |
| --- | --- | --- |
| Flag opt-in | `RuntimeConfigurationFactory.ConfigurationValues.booleanValue` aceitava `fal\u017Fe` como `false` em arquivo, permitindo desabilitar `shadow.audit.enabled` com grafia Unicode. Teste novo de arquivo e ambiente RED v94: 30/1 falha; guarda ASCII anterior ao `equalsIgnoreCase` passou v95 30/30 e preservou `FALSE` ASCII. v93 parou somente no formatter do teste, antes de asserções. | `target/ci-p10-20260927-01/p29-unicode-boolean-red-v93/v94-private.log`, `p29-unicode-boolean-green-v95-private.log` |
| Build final | Espelho v95, JDK17/Maven offline, sem URL shadow: `clean verify` exit0, 2.356 Surefire em 280 XML/cinco skips, seis ITs offline, formatter, Checkstyle, JaCoCo 80/60. | `target/ci-p10-20260927-01/clean-verify-v95-private.log` |
| PMD e disposição | 656 fontes/36 achados brutos; `parseLong` deslocou linha 596→599, mesmos tipos/métodos. Seis entradas do catálogo que citam fonte ou teste mudados receberam SHA atuais após revisão do fluxo existente. Primeira disposição recusou `DISPOSITION_TEST_SOURCE_HASH` em duas referências adicionais; v95b passou 36/36 contra 2.362 casos. Sem aceite nominal. | `target/ci-p10-20260927-01/pmd-v95/`, `pmd-disposition-v95/`, `pmd-disposition-v95b/` |
| SAST amplo | SpotBugs/FindSecBugs v96: 656 fontes e 1.135 classes/recursos iguais ao build v95 por SHA; 1.091 classes, zero erros/classes ausentes, 272 alertas brutos/87 SECURITY/46 Unicode. Mesmo multiconjunto tipo+classe do v90. XML SHA-256 `71214946789B0FC3C89B2B584396848ECEDEC7E81FA924EB9A92FB95F05CA09A`. | `target/ci-p10-20260927-01/p29-sast-mirror-v96/target/spotbugsXml.xml` |
| Guardas | Graphify isolado atualizou 794 AST, 37.247 nós/95.267 arestas. Gitleaks v97 em 4.039 arquivos e scanner offline v96 em 4.039 candidatos: zero achados. Trilha 33 etapas/48 IDs/nove pacotes PASS, UTF-8 estrito sete arquivos, JSON válido e `git diff --check` PASS. | `target/ci-p10-20260927-01/graphify-update-p29-v96-private.log`, `gitleaks-p29-v97-private.log`, `offline-secret-scan-p29-v96-private.log` |

`STATES.md`, [relatório P29](../../catalogos/p29-sast-preparacao-20260928/RELATORIO.md) e trilha distinguem RED/green de aceite. A correção não muda POM/lock/SBOM; o inventário 9/9 e contraprova de adulteração de 0306 permanecem históricos. P11 ainda carece de feed/baseline atuais e Segurança; P29 carece de política SAST integral, avaliação de licenças, RC/SOs e aceite da mesma revisão; P08 físico carece de pin autorizado, operador/UAC, sombra local e JDBC/rollback.

## Próximas ações, até três

1. Seguir triagem causal das 46 instâncias Unicode e demais 272 achados v96, com prioridade a entradas externas alcançáveis; preservar achados sem supressão ou aceite nominal.
2. Aguardar decisão explícita do usuário sobre atualizar `AGENTS.md` para JAR/DLL 12.8.2; Segurança fornece política/feed/baseline/licenças e release owner fixa RC/SOs/checks da mesma revisão.
3. Somente após sinal do operador e pin resolvido, retomar preflight de instalação/sombra local exclusiva `localhost/ETL_SISTEMA_V2_SHADOW`; até lá, continuar frentes P01–P33 offline independentes.
