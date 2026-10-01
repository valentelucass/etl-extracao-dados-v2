# Checkpoint 0321 — validadores estruturais e auditoria JDBC no shadow migrado

## Identificação, autoridade e limite

- 28/09/2026, Builder Banco e Persistência; anterior [0320](0320-p08-flyway-migrate-validate-local.md)
  SHA-256 `EAF74A4F1D42F075E51C893B2ED52F969CF1F489D6F13135F61615509816F5AB`.
- Decisão atual do Supervisor: não executar `013`, `015`, `023`, `031`, `032`
  ou `036` de `database/validation/` no shadow já migrado, pois incluem
  `transition/001_reset_historical_shadow_for_schema_foundation.sql` e
  `CREATE USER`; a exceção V002 não os cobre. Qualificar e executar somente
  validadores estruturais compatíveis no alvo local exato. Handoff posterior
  do Runtime autorizou a IT de auditoria selecionada com preflight, reserva
  e contagens independentes; vedou `ShadowJdbcTransportReadOnlyIT` no schema
  já populado.
- Alvo exclusivo: `localhost/ETL_SISTEMA_V2_SHADOW`, SQL Server Express local,
  Windows auth. `sqlcmd -C` foi concessão de certificado apenas neste teste
  local; não é aceite TLS P12. Nenhuma fonte real, remoto, produção, cutover,
  identidade nova, `clean`, `repair`, `drop` ou retry.

## Classificação, preflight e impacto

- `001_validate_schema_foundation.sql` SHA-256
  `A6814EE8662495DAEB3B97A542A40C9B912E75565BE8D7264285002DCE5DCFB1`:
  leituras de `sys.*`, permissões, allowlist existente e inserções só em
  variáveis de tabela; sem include sqlcmd, reset, DDL/DML durável, procedure
  executada ou transação. Limite: uma chamada `sqlcmd -E -C` com alvo
  explícito e timeout 30 s; recuperação: preservar recibo e consultar
  histórico/catálogo/contagens, sem repetição cega.
- Preflight novo `master`/alvo saiu 0/0: instância `LUCAS/MSSQLSERVER`
  Express local, transporte shared memory, banco exato online, zero login
  `v2_schema_owner`, 105 linhas Flyway (uma `SCHEMA` + 104 `SQL` bem-sucedidas,
  zero falhas), sete schemas V2 com owner sem login. Serviço PID 20404;
  somente `::1:1433` e `127.0.0.1:1433`. A primeira apresentação textual
  de listeners chamou `Join-String` indisponível no PowerShell 5.1; a
  comparação por conjunto e o SQL já haviam passado. Uma apresentação v2
  foi verificada antes da reserva, sem novo efeito.
- Snapshot agregado inicial e final SHA-256
  `78ED7BD652FD28452C861AEC5A36CF3877422AAC8AC1096C2F5A8136FE892B7B`:
  105/104/0 histórico, 1.819 objetos, sete principals e schemas V2,
  247 tabelas, 146 linhas agregadas, `ctl.execution_audit=0`,
  `ctl.page_audit=0`. Cada gate SQL teve preflight/reserva/readback próprios.
  Os logs privados não são fonte de payload nem foram copiados para docs.

## Resultados observados

| Gate | Camada e resultado | Recibo privado |
| --- | --- | --- |
| `001` | `sqlcmd`, exit 0, mensagem de sucesso, readback sem delta | `p08-validation-0321-001.out` SHA-256 `E6EDCFD764595944DA607B8939A165482D4CFB5633A4F8088119521FB697065D` |
| Demais validadores | 20 gates antes da IT: 17 PASS, 3 exit 1; 20 readbacks sem delta. Após IT, `061` PASS e readback sem delta. Total 22 executados: 19 PASS, 3 FAIL. | `p08-validation-0321-results.json` SHA-256 `EBC6EBABE72B84ACB8AE36E77031D11020CA1736A202FF4FD089DBA9E76CC37C`; `061` SHA-256 `18DCB0984D6BDD76DF01CC574ACFBFCCF9A1AB0CF12C60DB84E09D9BF2CE6CFF` |
| `003` | Exit 1, 11 achados: `CLOCK_AUTHORITY` 1, `COLLATION` 9, `NORMALIZATION` 1; inclui colunas de Flyway/auditoria. | `p08-validation-0321-003-validator.out` SHA-256 `001D904FA31CD4C555C656F19EF5A6B626F971EC54B822E4FFD0FBE205FE7A84` |
| `005` | Exit 1, 505 achados: `CONSTRAINT` 171, `OBJECT` 334 no inventário progressivo diante de V001–V104. | `p08-validation-0321-005-validator.out` SHA-256 `9D133CC814953C327B35AAFB6F9B500C8EB52BB0EFF2B27B091DBF997F5E7D7A` |
| `030` | Exit 1, 28 achados: `COLLATION` 1, `OBJECT` 27 em referências e extensões. | `p08-validation-0321-030-validator.out` SHA-256 `B51E6D48151A9FE19B5659056EE3F8B22B1BE7C6D09A55408A50E89396597C28` |
| IT de auditoria | Uma chamada Maven offline/JDK17 com perfil `shadow-local-integration`, flag `enabled=true`, URL exata local somente no ambiente do processo e seleção exclusiva de `ShadowAuditLocalIntegrationIT`: Failsafe 3/3 PASS, zero skips, `BUILD SUCCESS`; Spotless e Checkstyle PASS. Gateway sintético e conexão compartilhada bloqueiam `commit`; `ROLLBACK` e comparação de contagens na IT. | `p08-validation-0321-audit-it.private.log` SHA-256 `918F8BD108B3A2F47F9D7BEA6A8A6CE0A3038BB6CA622FD2983091273E6C1E7D`; relatório Failsafe SHA-256 `34DD95BEB3F1B26BB2D32EE143376C3AE678075C5A62D2336C8D9316445E091D` |
| Readback da IT | `master`/alvo exit 0/0; snapshot externo antes/depois byte-idêntico, auditorias 0/0, 105/104 Flyway, objetos/principals/schemas estáveis, listeners loopback sem delta. | `p08-validation-0321-it-target-after.out` SHA-256 `78ED7BD652FD28452C861AEC5A36CF3877422AAC8AC1096C2F5A8136FE892B7B` |

Passaram `001`, `007`, `009`, `012`, `021`, `026`, `035`, `038`, `040`, `042`,
`044`, `046`, `048`, `051`, `055`, `056`, `057`, `061` e `062`. Entre 28 arquivos
`*validate*.sql`, cinco wrappers `showplan` fazem reset histórico e não
foram executados; `053` exige dois logins/usuários Windows não autorizados e
também ficou sem execução. A IT de transporte de schema vazio não foi chamada.

Ledger físico novo
`target/shadow-local-rebuild-20260928-01/p08-validation-0321-ledger.jsonl`
SHA-256 `BE82739B1F127109EED4158FAB96699373F59980674AF4E88093CAD288E7D976`.
Os recibos, falhas, migrations, ledgers e checkpoints anteriores foram
preservados. O `CONTEXTO_GLOBAL.md` no relativo `../` prescrito segue ausente.
Não houve aceite integral P07/P08 ou P01–P33.
Após a sincronização documental, `Test-TrilhaPreparation.ps1` passou em
PowerShell 7.6.6: 33 etapas, 48 IDs abertos, nove pacotes, sem SQL/rede/Maven;
log SHA-256 `B631C3CF43CFD377A3CB36774F1CF30171075154BF66E01D6B783E164225D75C`.
`git diff --check` e UTF-8 estrito passaram.

## Retomada imediata — até três ações

1. Supervisor integra os handoffs de Runtime, Regras e Fontes com esta prova
   física, mantendo P08 e P16 abertos e sem tratar os 19 validadores verdes
   como aceite agregado.
2. Owner de schema/validadores investiga os recibos `003`, `005`, `030` contra
   V001–V104 e define ajuste versionado ou critério atualizado; preserve as
   falhas e não execute reset nem as seis validações com `CREATE USER`.
3. Qualquer próximo teste físico depende de novo preflight/reserva e critério
   específico; a IT JDBC de auditoria já passou nesta unidade, sem promover
   operação recorrente ou produção.
