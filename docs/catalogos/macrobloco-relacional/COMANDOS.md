# Comandos locais do laboratório

Pré-requisitos: Java17, Maven compatível, V029–V037 já instaladas no shadow local
e autenticação Windows existente. Instalação de schema é operação separada das
IT. Não reaplique os arquivos instalados; os ledgers e hashes estão na rodada
`target/macrobloco-relacional-20260911-01/`. Main padrão permanece dormente.

O runner aceita somente `scenario`, `hydrate`, `replay` e `status`, seguidos de
`--synthetic-relational-lab`. Opções finitas: `--roots=1..32`,
`--page-size=1..100`, `--days=1..3`. Defaults de fixture:4 raízes,17 por página,
1 dia; data sintética2036-04-01. O run permite10.000 linhas,32 claims,3 tentativas,
lease60s, retry2s, expansão1 dia e1.024 páginas. Não são defaults de negócio.

Cada comando cria e reverte um cenário próprio. `status` mostra o cenário
semeado com órfãos e retorna10. `scenario` e `hydrate` semeiam Manifestos/Fretes,
recompõem as Coletas estritamente reclamadas na fila e resolvem a cadeia.
`replay` executa BOOTSTRAP e REPLAY das mesmas partições pela leitura de recibos
SQL. O rollback impede consultar o run de um processo anterior.

Build/teste completo no diretório isolado, com um nome novo por tentativa:

```powershell
pwsh -NoProfile -File scripts/validation/Invoke-RelationalLaboratoryBuild.ps1 `
  -Attempt minha-verificacao-01 -Phase VerifyPhysical
```

Esse script faz preflight master/alvo, configura Java17 e heap512MiB somente no
processo, usa Maven offline com o perfil `shadow-local-integration` e
`-Dshadow.local.integration.enabled=true`, preserva logs e confere agregados.
Não executa clean, Flyway ou DDL. O pacote fica em `build/target` dentro da rodada.

Prova do JAR empacotado e das recusas antes de conexão indevida:

```powershell
pwsh -NoProfile -File scripts/validation/Test-RelationalLaboratoryJar.ps1 `
  -Attempt meu-jar-01
```

Para executar um comando individual na mesma máquina autorizada:

```powershell
$labBuild = Join-Path (Get-Location) 'target/macrobloco-relacional-20260911-01/build/target'
$labJar = (Get-ChildItem -LiteralPath $labBuild -Filter '*.jar').FullName
$env:V2_SHADOW_JDBC_URL = 'jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=30000'
& 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/java.exe' `
  '-Xmx512m' '-Dshadow.local.integration.enabled=true' `
  '-Dshadow.local.integration.profile.active=true' `
  "-Djava.library.path=$labBuild/native" -cp "$labJar;$labBuild/lib/*" `
  br.com.esl.etl.v2.bootstrap.RelationalLaboratoryMain `
  scenario --synthetic-relational-lab --roots=2 --page-size=3
```

O comando altera somente o ambiente do shell corrente. A DLL é a dependência
Microsoft já utilizada pelo perfil; não se altera PATH global. O script de
prova do JAR oferece preflight, teto30s por processo, logs e conferência do rollback.

Exits existentes:0 SUCCESS,10 DEGRADED,20 CONFIG_AUTH,30 LOCK,40 SOURCE_DQ,
50 CANCELLED. Saída contém contagens e categorias, sem payload/chaves. Caminho,
fonte GraphQL/real, host adicional, outro banco, flags desconhecidas e budgets
indevidos são recusados. Nenhuma opção ativa serviço ou agenda.

Validações complementares: `Test-RelationalLaboratory.ps1 -SelfTest
-IncludePrivateEvidence`, `Test-ColetasTemporalIntegrationBaseline.ps1
-InstalledReceipts`, `Test-SchemaFoundationManifest.ps1`,
`Test-ProgressiveDataGate.ps1`, `Test-RuntimeLocal.ps1` e os scanners offline
existentes. `database/validation/061_validate_relational_laboratory.sql` é uma
consulta estrutural sem DML, executada depois de fechar todas as sessões da prova.
