# Reprodução da entrega analítica local

Execute na raiz do V2, com PowerShell 7.5, Java 17 e dependências Maven já
disponíveis offline. O alvo exclusivo é localhost/ETL_SISTEMA_V2_SHADOW com
autenticação Windows. O banco desta entrega já contém V001–V098; os comandos
abaixo não aplicam DDL. Cada nova execução usa um nome de tentativa inédito.
Não reutilize nomes das provas preservadas.

## Conferir a entrega sem executar JDBC

```powershell
pwsh -NoProfile -File scripts/validation/Test-AnalyticLaboratory.ps1 -SelfTest -IncludePrivateEvidence
pwsh -NoProfile -File scripts/validation/Test-ExpansionLaboratory.ps1 -SelfTest -IncludePrivateEvidence
pwsh -NoProfile -File scripts/validation/Test-SchemaFoundationManifest.ps1
pwsh -NoProfile -File scripts/security/Invoke-OfflineSecretScan.ps1
```

O primeiro comando verifica a sucessão, os 19 contratos, as 673 colunas de
negócio, o inventário estrutural, o quadro de 45 unidades e a revisão das provas.
`-IncludePrivateEvidence` requer o diretório target preservado nesta máquina.
O selo final privado registra os resultados da revisão documental entregue.

## Reproduzir build e testes físicos

```powershell
pwsh -NoProfile -File scripts/validation/Invoke-AnalyticLaboratoryBuild.ps1 -Attempt verify-reproducao-01 -Phase VerifyPhysical -BudgetSeconds 1800
```

O runner cria uma fotografia isolada, registra preflight/contagens no alvo exato,
usa Maven offline, heap de 512 MiB e as duas travas do perfil JDBC sintético.
Registra resultado e rollback, preserva falhas e limita a execução a 1.800 s.
O verify desta entrega é `verify-physical-analytic-02`: 1.946 unitários com quatro
skips históricos e 361 IT sem skip. Depois dele, somente duas classes de teste
mudaram. Foram verificadas integralmente por:

```powershell
pwsh -NoProfile -File scripts/validation/Invoke-AnalyticLaboratoryBuild.ps1 -Attempt physical-reproducao-gates-01 -Phase Physical -Tests AnalyticLaboratoryCollectorsGatesIT,AnalyticLaboratoryFreightFallbackIT -BudgetSeconds 900
```

A prova preservada correspondente é `physical-analytic-closing-gates-02`:
20 IT, incluindo 17 novas. A união por última revisão qualificada de cada classe
resulta em 378 IT únicas, sem somar novamente as três IT reexecutadas.

## Executar os comandos do JAR aprovado

```powershell
pwsh -NoProfile -File scripts/validation/Test-AnalyticLaboratoryJar.ps1 -Attempt jar-reproducao-01 -BuildAttempt verify-physical-analytic-02 -TestAttempt physical-analytic-closing-gates-02
```

Para um novo verify integral da revisão atual, informe o novo `BuildAttempt` e
omita `TestAttempt`. O parâmetro complementar só aceita alteração de testes IT
qualificados fisicamente; fontes principais e resources devem ser os do verify.
O runner confere inventário e bytes empacotados antes de qualquer JDBC.

Os 40 processos exercitam `scenario`, `recompose`, `replay`, `status`,
`query --contract=SQL-01` até `SQL-19`, quatro degradações, doze recusas e o Main
padrão. A entrada é `br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryMain`, sempre
com `--synthetic-analytic-lab` e as travas do perfil; o runner monta classpath,
DLL local, ambiente e argumentos exatos. Cada comando recompõe sua própria
fixture e faz rollback. `status` não consulta persistência de outra execução.
Pode-se selecionar, por exemplo, `-CaseNames scenario,query-13`; isso é uma
prova dirigida e não substitui a campanha integral de 40 casos.

Limites: 300 s por caso, 1.200 s por campanha, heap de 512 MiB. Código 0 indica
sucesso local; 10 indica degradação esperada; 20 indica configuração recusada.
Em `result.json`, cada código é comparado ao resultado esperado e ao conteúdo
sanitizado. Um código 10/20 esperado constitui contraprova aprovada.

O artefato aprovado está em
`target/macrobloco-analitico-20260912-01/build-verify-physical-analytic-02/target/`.
SHA256 do JAR:
`a86320f3069758d7ac02e35881242a478f97b39ebc4ab70b2eb5f43f235fed1d`.
`jar-analytic-01/artifact-identity.json` vincula esse artefato à revisão de fontes
e testes; `result.json` e agregados antes/depois registram os 40 resultados.
