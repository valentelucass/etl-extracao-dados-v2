# Comandos e evidências

Todos os runners/tentativas estão em target/macrobloco-integracao-funcional-20260914-01. Eles reservam diretório novo, preservam resultados negativos e recusam reutilização. Não executar autores one-shot novamente.

- Invoke-Build.ps1 -Attempt verify-final-02 -Phase VerifyPhysical -BudgetSeconds 3600: Java17/Mavenoffline, profile+enabled,512MiB; argumentos efetivos em process/result.json.
- Invoke-FinalPackageProofs.ps1 -Attempt final-package-proofs-01: regressão de identidades, exportação sintética, pacote qualificado, build independente Package, segundo ZIP, vínculo de fontes/classes e provas por extração.
- Test-ArtifactCampaign.ps1: inspect/plan/run/status/resume/compare, dois tamanhos e três barreiras, incluindo OUTCOME_UNKNOWN antes do recibo. Cada comando tem reserva, processo, log e exit.
- Test-FileConsumers.ps1:51 comandos do JAR; resultados e recusas esperadas em file-consumers-final-01.
- Measure-ArtifactCancellation.ps1 -Attempt cancel-measurements-final-02 -CampaignAttempt artifact-cancel-measured-final-02: amostras JDK de heap durante o cancelamento após preparação, mesmo JAR e caps.
- Test-ArtifactInputGuards.ps1 e Test-ArtifactResume.ps1: quatro mutações sem início de efeito e quatro fronteiras de retomada.
- Invoke-StaticChecks.ps1 -Group Foundation/Runtime/Succession/Closure/Functional -Final, com tentativa nova por grupo: scanners, guards, schema, runtime e cadeia completa.
- node author-diffs.cjs diff-final-01: diff completo/revisão, git apply --check/apply e igualdade integral de bytes sobre before. core.longpaths apenas nesses comandos.
- node seal-final.cjs; node seal-final.cjs --verify: selo fora do ciclo e readback integral.

Para uso dos comandos do produto, seguir PACKAGE-README.md dentro da extração. O pacote contém dois exemplos de campanha ARTIFACT; um oráculo alterado deve declarar os pins da sua entrada, schema e JAR. O formato continua sintético e não concede autorização real.
