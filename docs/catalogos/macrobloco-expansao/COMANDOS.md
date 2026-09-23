# Comandos reproduzíveis do laboratório

Execute na raiz deste V2, com Java17, Maven local e as dependências já disponíveis.
O schema V001–V051 já foi instalado no shadow e tem ledgers congelados. Não
reaplique essas instalações; as IT e os comandos abaixo não executam DDL.

O controlador de build cria uma cópia própria em target, aplica formatter e
executa Maven offline, com heap512MiB e teto900s. A tentativa deve ser nova:

```powershell
& scripts/validation/Invoke-ExpansionLaboratoryBuild.ps1 `
  -Attempt verificacao-local-nova -Phase VerifyPhysical
```

O perfil físico exige as duas travas Maven e autenticação integrada. O script
confere master/alvo e agrega contagens antes/depois; cada teste fecha com
ROLLBACK. Nunca use clean/reset para preparar o banco.

O JAR continua com seu Main padrão dormente. A entrada companheira é
`br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryMain`, no mesmo artefato.
O runner de prova inicia21 processos próprios com60s por caso, registra PID,
exit esperado/observado, logs e preservação dos agregados:

```powershell
& scripts/validation/Test-ExpansionLaboratoryJar.ps1 -Attempt jar-local-novo
```

Para uma invocação manual isolada, prepare os argumentos abaixo no PowerShell.
A variável de ambiente tem duração apenas no processo/shell local; use a
autenticação Windows existente. Não há usuário/senha nem arquivo .env.

```powershell
$java = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
$artifact = Join-Path (Get-Location) 'target/macrobloco-expansao-20260912-01/build/target'
$env:V2_SHADOW_JDBC_URL = 'jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=30000'
$lab = @(
  '-Xmx512m', "-Djava.library.path=$artifact/native",
  '-Dshadow.local.integration.enabled=true',
  '-Dshadow.local.integration.profile.active=true',
  '-cp', "$artifact/etl-dataexport-v2.jar;$artifact/lib/*",
  'br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryMain'
)
& $java @lab scenario --synthetic-expansion-lab --roots=2 --page-size=3
& $java @lab hydrate --synthetic-expansion-lab --roots=2
& $java @lab replay --synthetic-expansion-lab --roots=2 --days=2
& $java @lab status --synthetic-expansion-lab
& $java @lab query --synthetic-expansion-lab --projection=CAP --limit=1
```

Cada linha é uma invocação independente e prepara seu próprio cenário. Nenhuma
invocação recupera dados revertidos por uma anterior. `scenario` hidrata o alvo
inicialmente ausente e carrega MAT04/MAT03; `hydrate` prepara o caso degradado e
recompõe por BACKFILL; `replay` usa revisão BOOTSTRAP concluída e visita partições
inversas. `status` conserva a pendência sintética e devolve10. `query` percorre o
cenário e exerce uma página da projeção escolhida; a saída pública só informa
contagens/reconciliação, sem despejar detalhe. JDBC permite inspeção tipada
paginada do detalhe sintético durante a mesma sessão.

As seis opções de projeção são `CAP`, `FAT`, `INV`, `SIN`, `INVOICE`, `REVENUE`.
`--limit` aceita1–100 e `--after` um cursor numérico não negativo. Essas opções
pertencem somente a `query`. O teto de raízes é256, dias1–3 e produto até256;
página1–16, deadline interno240s. Dados e datas-base são fixtures empacotadas.
Referências, fonte, host, banco, URL, path, permit, GraphQL e modo arbitrário
não são configuráveis por flags do laboratório.

| Resultado | Exit |
| --- | --- |
| Cenário completo / consulta válida | 0 |
| Dependência incompleta, status degradado | 10 |
| Configuração, opt-in, fonte/alvo/path/budget recusados | 20 |
| Lock ocupado | 30 |
| Fonte/DQ/timeout de execução | 40 |
| Cancelamento | 50 |

A consulta estrutural é read-only e exige o alvo/auth exatos:

```powershell
sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 30 -b `
  -f 65001 -i database/validation/062_validate_expansion_laboratory.sql
```

As contagens finais devem ser comparadas às iniciais, incluindo os dados que já
existiam. Contagem global zero não é requisito. As 36 tabelas próprias do bloco
ficaram sem resíduos nas provas; a recuperação de DML é rollback da sessão.

Após a entrega selada, os validadores conferem os deltas e a sucessão:

```powershell
& scripts/validation/Test-ExpansionLaboratory.ps1 -IncludePrivateEvidence -SelfTest
& scripts/validation/Test-RelationalLaboratory.ps1 -IncludePrivateEvidence -SelfTest
& scripts/validation/Test-RuntimeLocal.ps1
```

Sem API, dados reais, grants, produção, scheduler ou cutover. A recuperação
demonstrada é leitura do SQL na mesma transação; COMMIT/crash real não foi
executado. Regras de grão e política fiscal são contratos sintéticos explícitos.
