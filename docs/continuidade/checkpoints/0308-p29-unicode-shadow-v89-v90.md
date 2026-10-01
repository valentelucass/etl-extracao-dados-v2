# Checkpoint 0308 — Unicode do alvo shadow corrigido offline

## Autoridade e limite

- 28/09/2026, 09:12 UTC. Anterior [0307](0307-p29-redos-v83-pin-1281-suspenso.md), SHA-256 `BCD8FE2438184A8C902E1E6F3FE419841656875715C9973E438BB8BABE50D8B5`.
- Objetivo amplo: P01–P33 pelos critérios originais de `STATES.md`; nesta unidade, P29 triagem causal de `IMPROPER_UNICODE` com impacto na configuração shadow e regressão dos bytes finais.
- Segurança/Supervisor suspendeu execução física JAR/DLL JDBC 12.8.1 por CVE-2025-59250 até decisão explícita do usuário sobre o pin `AGENTS.md`. UAC também espera sinal do operador. Instalação, serviço, SQL, DDL, DLL e máquina anterior não foram acessados nesta unidade.
- `main...origin/main`; todos os deltas preexistentes, manifests, locks e checkpoints anteriores foram preservados. Sem mudança em `AGENTS.md`, POM, pin ou pacote por esta unidade.

## Mudança e prova

| Parcela | Resultado observado | Recibo privado |
| --- | --- | --- |
| Banco exato | `ShadowStorageProperties` aceitava `ETL_\u017FISTEMA_V2_SHADOW` e caixa ASCII diversa via `equalsIgnoreCase`; novo teste RED 10/1 falha. `String.equals` exige `ETL_SISTEMA_V2_SHADOW`; foco green 10/10. | `p29-unicode-focused-red-v87-private.log`, `p29-unicode-focused-green-v87-private.log` |
| Chave/flag JDBC | Chave `soc\u212AetTimeout` e flag TLS `fal\u017Fe` passavam via case-folding; duas contraprovas RED 12/2 falhas. ASCII obrigatório antes da comparação; formatter do primeiro green recusou layout, segundo green 12/12. Nenhuma URL foi usada em conexão. | `p29-unicode-properties-red-v88-private.log`, `p29-unicode-properties-green-v88-private.log`, `p29-unicode-properties-green-v88b-private.log` |
| Gate Java final | Espelho isolado v89, JDK17, Maven offline: `clean verify` exit0, 2.355 Surefire/280 XML/cinco skips, seis ITs offline, formatter, Checkstyle e JaCoCo 80/60. v87 era gate intermediário antes da segunda correção. | `target/ci-p10-20260927-01/clean-verify-v89-private.log` |
| PMD | 656 fontes, 36 achados brutos idênticos ao v83 por assinatura; disposição local 36/36 `PASS_LOCAL_REVIEWED_FINDINGS` contra 2.361 casos. Nenhum aceite nominal. | `target/ci-p10-20260927-01/pmd-v89/`, `pmd-disposition-v89/` |
| SpotBugs/FindSecBugs | Fontes e 1.135 classes/recursos no espelho v90 iguais por SHA ao build v89. 1.091 classes, zero erros/classes ausentes, 272 brutos/87 SECURITY/46 `IMPROPER_UNICODE`. Uma assinatura tipo+classe Unicode de `ShadowStorageProperties` saiu frente ao v84; demais assinaturas iguais. XML SHA-256 `6249729705FBA79CA6F1DEFDECC36CA5735C3D739CD1F44ED5F54ED3811ABD3E`. Dois caminhos de JDK17 inválidos falharam antes da análise, depois o JDK17 privado passou. | `target/ci-p10-20260927-01/spotbugs-v90-private.log`, `spotbugs-v90c-private.log`, `p29-sast-mirror-v90/target/spotbugsXml.xml` |
| Guardas e grafo | Graphify isolado atualizou 794 AST, 37.237 nós/95.246 arestas. Gitleaks v91 zero em 4.038 arquivos; scanner offline v90 zero em 4.038 candidatos; `Test-TrilhaPreparation.ps1` 33 etapas/48 IDs/nove pacotes PASS, UTF-8 estrito em seis arquivos e `git diff --check` PASS. Uma chamada acidental a `Test-Gpt56ChatTrail.ps1` recusou `HANDOFF_PATH` de escopo histórico e não foi tratada como prova da trilha P01–P33. | `target/ci-p10-20260927-01/graphify-update-p29-v90-private.log`, `gitleaks-p29-v91-private.log`, `offline-secret-scan-p29-v90-private.log`, `trilha-preparation-p29-v90-private.log` |

O [relatório](../../catalogos/p29-sast-preparacao-20260928/RELATORIO.md) e `STATES.md` distinguem a correção das três entradas da disposição de Segurança dos 272 alertas. `QualificationConfiguration` P08 já validava chave ASCII e banco/host literais; não foi alterada. Licenças/SBOM 9/9 e contraprova de adulteração de 0306 continuam históricos porque POM, lock e pacote não mudaram. P11 carece de relatório de feed atual e baseline aceita; P29 carece de política/aceite SAST integral, avaliação de licenças, RC único e smoke nos SOs suportados. Nenhum checkbox integral P08/P11/P29/G02 foi promovido.

## Próximas ações, até três

1. Continuar triagem causal dos 46 avisos Unicode restantes e demais alertas v90 em frentes offline, preservando inventário bruto e sem aceites nominais.
2. Segurança fornece regra/limiar/baseline, feed P11 e avaliação de licenças da revisão; usuário decide explicitamente o delta 12.8.2 de `AGENTS.md` antes de qualquer JDBC.
3. Após sinal do operador para UAC e pin resolvido, retomar instalação local e preflight exclusivo de `localhost/ETL_SISTEMA_V2_SHADOW`; release owner fixa RC/SOs/checks da mesma revisão. Se ausentes, seguir frentes P01–P33 independentes segundo o mapa.
