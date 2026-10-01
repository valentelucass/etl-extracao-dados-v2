# Checkpoint 0320 — V001–V104 aplicadas e validadas no shadow local

## Identificação, autoridade e limite

- 28/09/2026, Builder Banco e Persistência; anterior [0319](0319-p08-migrate-preflight-auth-conflict.md)
  SHA-256 `6ECA80D8DD6BDBAB9C65A1361D98E21738F9BDBF965829879014264FF32743BB`.
- O Supervisor, sob delegação expressa do usuário nesta sessão, autorizou uma
  exceção **somente** para `CREATE USER v2_schema_owner WITHOUT LOGIN` na V002
  versionada e inalterada, no banco local exato, para ownership dos sete
  schemas V2. A exceção foi escrita em `AGENTS.md` §1 e `STATES.md` antes de
  `migrate`; não cobre qualquer outro usuário, login, senha, credencial,
  associação de identidade, remoto, produção ou script de validação que
  crie usuário. Não há autorização de cutover ou aceite integral P08.
- Alvo: `LUCAS/MSSQLSERVER`, `localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth,
  SQL Express local. ODBC `-C`/JDBC `trustServerCertificate=true` foram apenas
  opções explícitas deste teste local, sem aceite TLS P12. Nenhum endpoint de
  fonte, dado real ou ambiente produtivo foi usado.

## Pré-condições e impacto conferidos

- PowerShell 7.6.6 portátil já preservado em `target/ci-p10-20260925-01/`:
  ZIP 106.328.873 bytes, SHA-256
  `02FE458BE20493FBDF43F61EA20610B811EE6C738AB1676C61B9CFCD1A33C860`
  igual ao digest do metadado oficial local; `pwsh.exe` SHA-256
  `BFB46AF89433268872DDB43D1CA7A3F433452EE91ED356A9786940F90118E285`,
  assinatura Microsoft válida, versão 7.6.6. O checker original
  `Test-ProgressiveDataGate.ps1` saiu 0; log SHA-256
  `1BB674AF268604948C4C62C5139BF3DB8D220BCB5DF8AD563D9F58467ED44BD1`.
  O erro anterior em PowerShell 5.1 permanece histórico, sem contorno do gate.
- Scripts ativos V001–V104: 104 arquivos, 3.322.636 bytes, byte a byte
  iguais ao espelho Maven v4 do `flyway:info`; baseline 104 referências
  únicas. V002 SHA-256
  `6E962AF63A639380CD84E479C94F003DD80497FB1632C9CF29BD9983A94772B2`;
  `pom.xml` ativo/espelho SHA-256
  `9D1313FEBF940EE4617547AF9F8452D169C9A12350E5937D4D7700E691215A98`.
  JDK17, JAR e DLL 12.8.2 mantiveram os hashes do checkpoint 0318. No SQL
  ativo, havia um `CREATE USER` em V002 e nenhum `CREATE LOGIN` ou
  `CREATE CREDENTIAL`.
- O preflight reservado em `lpc:localhost/master` e no banco explícito saiu
  0/0: Express sem cluster, alvo online com dois arquivos, collation esperada,
  zero schemas/objetos/principals V2 e histórico ausente. Serviço
  Running/Manual PID 20404, apenas listeners `::1:1433` e
  `127.0.0.1:1433`; C: tinha 670,5 GiB livres. O primeiro guard de socket
  falhou antes do SQL por comparar os endereços na ordem textual errada;
  leitura isolada mostrou o conjunto correto, e uma reserva nova usou
  comparação por conjunto. O attempt falho foi preservado.
- Impacto autorizado: DDL versionado V2, roles, um usuário interno sem login,
  ownership, objetos SQL e histórico Flyway duráveis no único shadow local.
  Recuperação ante falha/resposta incerta: preservar o banco e os recibos,
  consultar histórico/catálogo autoritativos, sem `clean`, `repair`, `drop`
  ou retry. Nenhuma reversão automática foi necessária.

## Gates e evidência observada

| Gate | Camada e resultado | Recibo privado |
| --- | --- | --- |
| Checker progressivo | PowerShell 7.6.6, script original, exit 0; manifests, migrations, baseline e contratos estáticos passaram | `target/shadow-local-rebuild-20260928-01/p08-migrate-0320-progressive-checker.out` SHA-256 `1BB674AF268604948C4C62C5139BF3DB8D220BCB5DF8AD563D9F58467ED44BD1` |
| Preflight novo | `sqlcmd -E -C`, master/alvo exit 0/0; vazio e local | `p08-migrate-0320-master-preflight-v2.out` SHA-256 `220B5F4061AF206E1E38AC24BF39FD4837EE18D823C7483E3136C66E2877067C`; alvo SHA-256 `189E3DAC7D096E7614294A7B78C3EA7F471F8F7CD704A61FC61694678D093DCA` |
| `flyway:migrate` | Uma invocação Maven offline/JDK17/Flyway 9.22.3 no espelho byte-idêntico, Windows auth, exit 0 | `p08-migrate-0320-flyway-migrate-v1.private.log` SHA-256 `A33E8E4D60728C05EB279F93B7CC6E249D8E13E380034881C5EFC9F1699AF6D4` |
| Readback após migration | Primeira consulta recusou `HISTORY_COUNT_MISMATCH` por exigir 104 linhas totais; a consulta seguinte mostrou uma marca `SCHEMA` e 104 migrations `SQL` bem-sucedidas, zero falhas, versões distintas 1–104, sete schemas owned, um usuário sem login, 1.819 objetos e sete principals V2. Master mostrou zero login de mesmo nome; PID/listeners inalterados. Nenhum retry de migration | `p08-migrate-0320-diagnostic-readback.out` SHA-256 `CC86B988CD479B682BB0943B1EA9DD07083F1BEB96A44E601104622A4C041582` |
| `flyway:validate` | Gate separado, uma invocação Maven offline/JDK17, exit 0 | `p08-migrate-0320-flyway-validate-v1.private.log` SHA-256 `58F8A69C00DFA127DA98CE72A3614648E526245E86171C62A14AC00E358F7D14` |
| Readback após validate | Master/alvo exit 0/0; histórico 105/104, 1.819 objetos, sete schemas/ownership, zero login e listeners loopback sem delta | `p08-migrate-0320-master-postvalidate.out` SHA-256 `D9291BFED9E5062876B34E3C59B8EDB656FDBEAC22952BDF127A4FDE4F8430C0`; alvo SHA-256 `44144CDC4E6548DFA47CEBC18C41B10AA539667E46AB95E5ABBCFED3C50870B1` |

Ledger físico novo
`target/shadow-local-rebuild-20260928-01/p08-migrate-0320-ledger.jsonl`
SHA-256 `C5F2144D825499FCA14B79ACAF09826083C95FB4C1F25212D3B02CFBE775B385`.
O ledger 0319, scripts, logs de falha e checkpoints anteriores permanecem
intactos. Logs Maven privados podem conter a URL JDBC local; não copiá-los
para relatório público. `CONTEXTO_GLOBAL.md` continua ausente no relativo
`../` previsto; as fontes canônicas locais foram lidas.
`Test-TrilhaPreparation.ps1` passou sob PowerShell 7.6.6 com 33 etapas,
48 IDs abertos e nove pacotes; log SHA-256
`B631C3CF43CFD377A3CB36774F1CF30171075154BF66E01D6B783E164225D75C`.
O scanner offline de segredos passou em 4.058 candidatos, zero achado,
oversized ou conteúdo não inspecionado; log SHA-256
`B6161DEAA6ABE577BDC36D291984CD6CF95C118D41C60CF9667566F2B33620B1`.
Não houve build Java amplo nesta unidade de SQL/documentação; as alterações
Java dos outros Builders mantêm suas provas próprias.

## Escopo não executado e retomada — até três ações

- Varredura estática achou `CREATE USER` em seis scripts SQL de validação:
  `013`, `015`, `023`, `031`, `032` e `036` de `database/validation/`.
  Nenhum foi executado; a exceção V002 não os cobre. Não houve IT JDBC de
  auditoria pós-schema, dados sintéticos de validação, pacote, fonte real,
  produção, deploy ou aceite integral P07/P08/P01–P33.
1. Supervisor/owner revisa separadamente a autorização e os efeitos dos seis
   scripts com criação temporária de usuários, sem ampliar automaticamente
   a exceção V002.
2. Se autorizadas, classificar e executar validações SQL sintéticas com
   rollback e contagens agregadas antes/depois, em reservas próprias.
3. Coordenar com Runtime e Qualificação a IT de auditoria pós-schema e os
   gates de pacote/integração, sem promover P08 até critérios agregados.
