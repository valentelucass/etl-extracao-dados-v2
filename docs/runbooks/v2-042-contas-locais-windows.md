# Contas locais do V2 — procedimento autorizado em 07/09/2026

O owner esclareceu que as contas não existem e autorizou uma configuração segura.
O banco do V2 já é ETL_SISTEMA_V2_SHADOW. ETL_SISTEMA permanece produtivo e não é
alvo de conexão, DDL, consulta de dados ou alteração. Não criar outro banco, serviço,
job, agendamento ou reiniciar o SQL Server.

## Pacote concreto

- Duas contas Windows locais, etl_v2_exec e etl_v2_view, sem grupo Administradores,
  com expiração em 30 dias; pertencem somente ao grupo padrão Usuários.
- Dois logins Windows SQL e dois usuários exclusivamente no banco V2 existente.
- SERVICE recebe as 20 procedures exatas do gerador; OPERATOR recebe somente
  autorização e status. Nenhum db_owner, schema-wide ou DML direto.
- Authority/mapping de validade máxima de 30 dias, somente LOCAL_SHADOW/LOCAL_V2/LOCAL_V2,
  Coletas/Fretes BACKFILL/INCREMENTAL. Replay/force-run permanecem sem papel concedido.
- Senhas aleatórias geradas em memória; armazenadas somente com DPAPI do administrador
  em C:\ProgramData\EslEtlV2\secrets, ACL Administradores/SYSTEM. Não aparecem no Git,
  argumentos, variáveis de ambiente de processos filhos, logs ou respostas.
- Artefato local administrado sob C:\ProgramData\EslEtlV2\app, somente leitura/execução
  para as duas contas. Classes/recursos do JAR original são idênticos; única adição:
  runtime-authority.properties. Nenhuma fixture entra no JAR.
- Truststore JKS dedicado contém exclusivamente o certificado público que já está
  vinculado ao SQL Server no registro local. Não exporta chave privada, instala raiz
  global, altera certificado do servidor nem reinicia serviço. JDBC real passou
  encrypt=true/trustServerCertificate=false com hostname exato e esse truststore.
  O JKS não contém segredo; sua integridade depende do hash revisado e ACL administrativa.

Os artefatos nominais, hashes e SQL estão em
`target/bloco53/provisioning-local-accounts/reviewed-bundle`.
`installation-manifest.json` vincula todos os arquivos. A preparação e o modo sem
Apply não alteram contas/banco. Paths fora do pacote, arquivos alterados, contas/logins
preexistentes e instalação já existente são recusados para preservar estado alheio.

## Execução

`Install-LocalRuntimeAccounts.ps1` exige administrador do Windows antes de qualquer
mudança. A sessão atual do editor não está elevada; usar apenas a confirmação UAC
normal do Windows. Não há bypass de UAC nem uso do SQL Server para elevar o host.

```powershell
# Em PowerShell como administrador, na raiz do projeto:
$package = Join-Path $PWD 'target/bloco53/provisioning-local-accounts/reviewed-bundle'
$reviewHash = Get-Content 'target/bloco53/provisioning-local-accounts/reviewed-manifest.sha256' -Raw
scripts/validation/Install-LocalRuntimeAccounts.ps1 `
  -PackageDirectory $package -ManifestSha256 $reviewHash -Apply
```

Duas ocorrências técnicas são reservadas no ledger cumulativo antes da execução;
nenhuma reserva é devolvida. A verificação não extrai dados: novas sessões sob cada
conta verificam permissões, recusa de SELECT direto de identidades e HAS_DBACCESS da
produção igual a zero, sem entrar no banco produtivo. O JAR oficial executa status
nas duas contas: NOT_FOUND corresponde a DEGRADED/exit 10, com autenticação e consumo
reais; isso não é publicação bem-sucedida. OPERATOR run deve retornar 20 antes de fonte.
Cada filho próprio tem 60 s, saída limitada a 16 KiB e SQL até 30 s; execução serial.

## Falha e recuperação

A transação SQL inclui logins, usuários, mapping, authority e grants; erro antes do
commit reverte essa transação. Contas Windows não são transacionais: em falha posterior
elas são preservadas e somente as recém-criadas pelo script são desabilitadas.
Senhas DPAPI e logs ficam protegidos para recuperação administrativa; não apagar
usuários, schema, recibos ou dados. Não repetir Apply quando a instalação já existe.
A compensação SQL revisada revoga os grants e invalida mapping/scopes/authority,
preservando dados e auditoria. Qualquer reparo exige examinar o resultado concreto.

O resultado sanitizado será gravado em installation-result.txt no pacote e no diretório
administrativo. Nenhum PASS operacional é presumido da autorização, preparação ou
prompt UAC. Fonte externa, comparação/paridade e matriz operacional continuam nos
seus gates; criar contas não executa extração produtiva.

## Verificações realizadas antes da aplicação

Preflight: host local fora de domínio, nomes propostos livres, módulo LocalAccounts
presente, serviço Secondary Logon já em execução; sem alteração de serviço. Java/JDBC
TLS inicialmente recusou truststore padrão, e passou com o certificado público exato
em JKS dedicado. A tentativa PKCS12 sem senha não carregou trust anchors; foi descartada
como configuração de cliente, sem enfraquecer TLS. Comparação de todas as entradas do
JAR original confirmou código inalterado. Parser/validador do instalador aprovados;
Apply não elevado recusou antes de criar diretório, conta ou login.

Referências técnicas: [New-LocalUser](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.localaccounts/new-localuser?view=powershell-5.1)
e [certificados de conexão SQL Server](https://learn.microsoft.com/en-us/sql/database-engine/configure-windows/configure-sql-server-encryption?view=sql-server-ver17).

## Resultado executado

LOCAL_ACCOUNTS_PROVISIONED_AND_VERIFIED: contas habilitadas, dois logins/usuários e 22 grants exatos. Novas sessões Windows SERVICE/OPERATOR: PASS; acesso à produção ausente e SELECT direto de identidades recusado. JAR oficial nas duas contas: status autorizado, NOT_FOUND/DEGRADED (10), sem fonte; OPERATOR run recusado (20). Dois ALLOW e dois consumos no SQL; zero sessão/transação remanescente. Validator 053 PASS. Ledger 112/128; nenhum novo registro de extração/publicação: anteriores 95/75 preservados.

Duas falhas de preparação foram corrigidas sem apagar material: Description de conta maior que o limite Windows de 48 caracteres; depois, colisão PowerShell entre switch Apply e variável de resultado SQL. A segunda transação SQL já havia confirmado quando a atribuição PowerShell falhou; por isso SQL_COMMITTED=False naquele relatório é um marcador cliente, não prova de rollback. A consulta independente confirmou o commit, e ResumeInstalledAccounts verificou hashes/grants/mappings antes de reabilitar somente as duas contas e concluir testes. Não se repetiu o SQL de aplicação. Os relatórios das tentativas e o material DPAPI não utilizado foram preservados.

Evidência final: target/bloco53/provisioning-local-accounts/reviewed-bundle/installation-result.txt e postflight.log. Credenciais ficam sob C:\ProgramData\EslEtlV2\secrets, acessíveis apenas à administração; não solicitar senha pelo chat. A vigência de 30 dias exige renovação administrativa antes de uso contínuo. Fonte externa e instalação como serviço continuam fora desta execução.

Fechamento: ACLs conferidas em contexto administrativo somente leitura — secrets permite apenas Administradores/SYSTEM; as contas possuem somente leitura/execução do artefato e estão no grupo Usuários, sem Administradores. Passaram validator 053 físico, gate progressivo, pacote Windows, trilha, três contraprovas do instalador (produção/grants extras/path externo), UTF-8 sem BOM dos 11 arquivos afetados e git diff --check. Scanner offline: 1130 candidatos/1129 textos/um binário verificado, zero finding/oversized/não inspecionado. As 17 migrations aplicadas permaneceram iguais aos ledgers; os 1084 arquivos iniciais continuam presentes. Java de produção não mudou nesta etapa: foi usado o JAR já aprovado pela suíte completa de 1042 testes, acrescido apenas do recurso administrativo; não se repetiu Maven sem mudança Java.

## Contas reutilizadas no Bloco 54

As contas existentes executaram o JAR inicial e final. V019/V020 e os três grants
posteriormente autorizados foram qualificados/aplicados na quinta campanha de
08/09/2026. Perfil atual: SERVICE com 22 EXECUTEs e OPERATOR com três, sem acesso
a tabelas/DDL nem papel adicional. SERVICE mapping v4 após compensação comprovada
de uma revogação de teste; OPERATOR v1 e oito scopes v1. Vencimento original mantido,
sem REPLAY/FORCE_RUN ou scope novo. Zero sessão restrita/transação remanescente.

O [procedimento manual B54](v2-022-bloco54-operacao-manual.md) foi exercitado em
STATUS e diagnóstico; usar -ObservabilityProfile para o perfil atual de 25 grants.
O [relatório](v2-022-bloco54-integrado.md) registra a matriz parcial de 31 casos e a
recuperação. Não repetir o instalador de contas, renovar, trocar senha ou criar
substitutos: contas, banco, UAC e concessões já estão resolvidos. A quinta campanha
terminou com 99/128 reservas B54; nenhuma sexta está autorizada.
