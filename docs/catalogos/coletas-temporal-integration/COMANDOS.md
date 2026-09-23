# Comandos e camadas da execução

Executados a partir da raiz V2; logs em
target/coletas-temporal-integration-20260910/. Não contêm dados de negócio.

## Instalação já concluída

Invoke-ColetasTemporalIntegrationSchema.ps1 fez preflight no master, verificou
o alvo exato e autenticação existente, congelou SQL/hashes e reservou ledger
antes de aplicar. As invocações usaram estes argumentos:

| Etapa | Argumentos além de -EvidenceDirectory |
| --- | --- |
| schema-qualification-01 e02 | nenhum: DDL transacional com rollback |
| schema-install-01 | -Install: V025–V027 |
| schema-correction-qualification-01 | -Correction: V028 com rollback |
| schema-correction-install-01 | -Correction -Install: V028 |

O EvidenceDirectory de cada invocação foi o subdiretório homônimo da execução.
Não repetir instalação: o preflight recusa objetos já existentes. Não foi usado
Flyway dentro da IT. SQLCMD usou `-S localhost -C -E`, `-d` explícito,
`-b`, timeout de login5s e comando30s. Nenhum grant, usuário ou banco foi criado.

## Build e testes

O runner preservado Invoke-Build.ps1 copia somente arquivos seguros do projeto
para build/ e registra argumentos e exit. Execução final:

```powershell
& target/coletas-temporal-integration-20260910/Invoke-Build.ps1 -Phase physical-06 -Physical -Goal 'spotless:apply,verify'
```

Esse nome de fase já existe e não deve ser reutilizado. Maven executou:

```text
--offline --batch-mode --no-transfer-progress -Dv2.measurement.receipt=true
-Pshadow-local-integration -Dshadow.local.integration.enabled=true
spotless:apply verify
```

V2_SHADOW_JDBC_URL foi definida somente no processo do runner para localhost,
ETL_SISTEMA_V2_SHADOW, integratedSecurity=true, encrypt=true,
trustServerCertificate=true, loginTimeout=5, socketTimeout=30000.
Validação própria recusa URL remota/outro banco/credenciais SQL. A DLL12.8.1.x64
foi resolvida pelo perfil Maven e copiada em target/native do build isolado;
PATH global permaneceu intacto. Não se leu .env ou credencial.

## Verificações independentes

```powershell
& scripts/validation/Test-ColetasTemporalIntegrationBaseline.ps1 -InstalledReceipts
& scripts/validation/Test-ColetasTemporalRepresentativeInputs.ps1 -SelfTest
& scripts/validation/Test-ProgressiveDataGate.ps1
& scripts/validation/Test-FirstWaveContractCatalog.ps1
& scripts/validation/Test-ColetasV2010ShadowVertical.ps1
& scripts/security/Invoke-OfflineSecretScan.ps1
& scripts/security/Test-OfflineSecretScan.ps1
& scripts/validation/Test-ColetasTemporalIntegration.ps1 -IncludePrivateEvidence -SelfTest
```

SQLCMD executou somente leitura de 001_validate_schema_foundation.sql,
038_validate_coletas_shadow_vertical.sql e057_validate_coletas_temporal_integration.sql
no alvo explícito. O runner progressivo que reseta banco/exercita outras entidades
não foi usado. A prova de concorrência está na IT real, com query timeout8s,
lock timeout1,5–1,8s e espera limitada12s; não é uma sonda isolada de applock.

Sem InputPath/SelfTest, Test-ColetasTemporalRepresentativeInputs retorna exit2
esperado com EXTERNAL_INPUT_MISSING; a saída foi preservada separadamente.
Scanners, scripts e verificações documentais têm logs próprios e não são
contabilizados como casos JUnit. O diff foi gerado contra inventory-before.json,
não contra HEAD, para preservar as alterações preexistentes do usuário.
