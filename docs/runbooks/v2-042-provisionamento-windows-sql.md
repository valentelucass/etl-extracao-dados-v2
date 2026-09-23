# Provisionamento Windows/SQL — contas locais instaladas e verificadas

> Atualização executada: etl_v2_exec e etl_v2_view criadas com autorização do owner, dois logins/usuários SQL, 22 grants exatos e status autorizado pelo JAR em sessões Windows novas. Consulte [contas locais e evidência](v2-042-contas-locais-windows.md). As pendências de contas/TLS nos registros abaixo descrevem a fase anterior; não devem ser solicitadas novamente.


O mecanismo foi adotado pelo owner no Bloco 53. Schema V016/V017 instalado somente no
SQL local; authority/mapping/scopes permanecem vazios. A conta do editor é administrativa
e foi recusada como prova operacional. Nenhum login, usuário operacional, membership,
grant ou ACL foi alterado.

## Autorização confirmada pelo owner

Após o fechamento local, o owner confirmou no chat que o agente tem permissão.
Essa confirmação autoriza o provisionamento descrito: dois database users para
logins Windows existentes, mapping/scopes, authority e 22 grants exatos no banco
local existente. Não solicitar novamente autorização para esse mesmo pacote.
A pendência atual são as identidades reais adequadas ao contrato.

Preflight somente leitura executado após a confirmação: alvo online; um login Windows
individual habilitado, administrativo; zero login Windows individual habilitado sem
sysadmin, excluídas identidades internas de serviço. Nenhum nome/SID foi exposto.
A conexão sqlcmd com TLS exigido e sem trustServerCertificate passou; isso não
substitui a validação do truststore Java e do hostname no adapter oficial.
Evidência sanitizada: target/bloco53/provisioning-authorized-preflight/summary.txt.
Nenhuma conta, login, usuário ou permissão foi alterado nessa verificação.

É necessário identificar as duas contas Windows existentes destinadas à execução
(SERVICE) e à consulta/status (OPERATOR). O pacote atual exige os logins já cadastrados
no SQL Server; como não há candidatos individuais não administrativos cadastrados,
o cadastro SQL dessas contas deverá constar do diff nominal antes da aplicação.
Não atribuir a função de execução à conta administrativa do editor.
## Entradas reais ainda necessárias

Preencher uma cópia protegida de `config/runtime-windows-provisioning.example.json`:
hostname real compatível com TLS/SERVERPROPERTY, UUID administrado da authority,
fingerprint RBAC do artefato, dois logins Windows individuais já existentes e distintos
(SERVICE e OPERATOR), vigência UTC explícita de até 31 dias, até 16 escopos exatos e
referência da revisão. Campos null são recusados. Não usar a conta administrativa
atual como service account, nem inferir contas ou owners a partir do editor.

Também comprovar: responsável administrativo, certificado TLS confiável e distribuição
do JAR/recurso/native DLL protegida por administração separada. O gerador não configura
certificados, contas, serviços ou ACLs. Não inventa aprovação para localhost produtivo.

## Geração e revisão

```powershell
scripts/validation/New-WindowsRuntimeProvisioningPackage.ps1 `
  -Inputs <arquivo-protegido-com-fatos-reais.json> `
  -OutputDirectory <caminho-absoluto-do-repo>/target/bloco53/revisao-identidade
```

O diretório precisa ser novo. A geração não conecta ao SQL. O parser recusa duplicatas,
chaves extras, valores ausentes, nomes inadequados, escopos/volume excessivos e injeção.
Os arquivos gerados contêm os nomes administrativos necessários e ficam fora do Git.

| Arquivo | Efeito proposto |
| --- | --- |
| 01-apply-reviewed.sql | Transação no alvo exato: dois database users para logins Windows individuais existentes, cadastro pseudônimo, scopes e 22 grants exatos no total |
| 02-verify-new-session.sql | Preflight agregado em conexão integrada nova sob cada identidade; sem imprimir SID/login |
| 03-revoke-preserve-audit.sql | Revoga somente os grants propostos, invalida mapping/scopes por nova versão e desabilita authority; preserva dados/auditoria/usuários |
| runtime-authority.properties | Quatro pins a incorporar ao artefato pela administração; não passar por CLI/env |

SERVICE recebe 20 procedures: autorização/status/recuperação; registro de fonte/ciclo/
tentativa, heartbeat, página, contagens, transição e fronteira incremental; staging,
prepare e apply das duas verticais; DQ e plano/resumo temporal. OPERATOR recebe somente
autorização e status. Não há grant global de schema, CONTROL, db_owner, impersonação,
DML direto, fonte externa, varredura global de leases ou membership em v2_runtime.
Replay/force-run permanecem sem papel concedido. O gerador exige database users ainda
ausentes para que reversão de grants não remova permissões preexistentes; outro estado
exige um diff específico. Privilégios herdados precisam ser conferidos sob a conta real.

## Aplicação futura e prova

A autorização para o pacote descrito foi confirmada no chat. Materializar primeiro
os arquivos com fatos reais e conferir checksums, alvo, identidades,
escopos, vigência, módulos e recuperação. Não aplicar o pacote sintético usado nos testes.
Os validators 005/051 atuais descrevem a topologia sem provisionamento operacional;
qualquer ampliação de permissões requer evolução revisada do contrato de verificação.

Após autorização: contexto administrativo separado aplica a transação. Abrir processos
novos sob SERVICE e OPERATOR; executar preflight, JAR oficial e negativos de role ausente,
scope/policy alterados, consumo duplicado, expiração, revogação e sink indisponível.
SQL fixtures com EXECUTE AS não substituem autenticação Windows real. O JAR deve usar
o diretório `lib` empacotado e a DLL 12.8.1.x64 já aprovada, com java.library.path explícito;
não alterar PATH global. O certificado deve validar sem trustServerCertificate=true.

Uma fonte externa continua em V2-041/EXTERNAL_HOLD: status positivo não comprova run
completo. O laboratório testou o pipeline sintético e a auditoria DENY real; não testou
ALLOW sob principal restrito, nem simula essa evidência com factory no JAR.

Se a transação administrativa falhar antes do commit, reverter. Após commit, usar a
compensação revisada; não apagar decisões, consumos ou ocorrências. Consumo sem despacho
exige nova invocação autorizada para a mesma ocorrência, nunca reutilização do recibo.
