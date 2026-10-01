# Checkpoint 0311 — instância e banco shadow local criados

## Identificação e autoridade

- 28/09/2026, 20:47 UTC. Anterior [0310](0310-parada-segura-instancia-shadow-local-preparada.md), SHA-256 `74CCF804727678A35A2170E9AA1005E6C76DABBC354F203DBD7B9A2F7E443752`.
- Objetivo: materializar uma única instância SQL Server local e exclusivamente `localhost/ETL_SISTEMA_V2_SHADOW`, com UAC legítimo, preflight em `master` e readback.
- Instrução efetiva: o usuário sinalizou prontidão para o UAC antes pausado e reafirmou a instalação local; o pin JDBC 12.8.1 permanece suspenso por Segurança e 12.8.2 não recebeu decisão explícita.
- Estado: `TESTADO_NA_CAMADA` para instalação e banco vazio; P07/P08 físicos continuam abertos. `STATES.md` é canônico.

## Limites e recibos

- Alvo `LUCAS/MSSQLSERVER`, SQL Server 2025 Express x64 17.0.1000.7, Windows auth, serviço Manual, TCP/Named Pipes off, memória máxima 2 GiB. Banco exato `ETL_SISTEMA_V2_SHADOW`, sem acesso remoto.
- Recibos privados: `target/shadow-local-rebuild-20260928-01/ledger.jsonl`, `create-shadow-database.sql` (SHA-256 `3C784CFC052D3CA14BC6BDBF0B77A5158B8A04C5CE8069192B76CBDF1D836B53`) e `create-shadow-database-output.txt`. Log do fornecedor: `%ProgramFiles%\Microsoft SQL Server\170\Setup Bootstrap\Log\20260928_174032\Detail.txt` (SHA-256 `8101FDFDB68933BE3C06D5F5466BA814676EC2137F6045954EB5A978DF7436E4`); `Summary.txt` na raiz `Log` (SHA-256 `6A63BB417018955E707D09992BB786BFE410E5C462CBEF3206E9E77904B40C66`).
- A árvore suja preexistente foi preservada. Nenhum arquivo de código, migration, pin, lock ou manifesto histórico foi modificado nesta unidade. Recuperação de falha futura: preservar serviço, banco, arquivos e logs; consultar `master` antes de qualquer nova ação, sem retry/drop/limpeza.

## Execução e evidência

| Passo | Camada | Esperado | Observado |
| --- | --- | --- | --- |
| Preflight | Windows/mídia | host, assinatura/hash, ausência de estado prévio | `LUCAS`, mídia 748.772.024 bytes e SHA/assinatura iguais ao recibo; 676,3 GiB livres; serviço/processos/extração ausentes. |
| Extração UAC | Windows | uma execução, exit 0, setup íntegro | PID 12048, exit 0; 202 arquivos, `SETUP.EXE` Microsoft `Valid`, versão 17.0.1000.7; serviço ainda ausente. |
| Setup UAC | Windows | uma execução, exit 0 e `Passed` | PID 32316, exit 0; `Summary.txt` `Passed`, Express/SQLENGINE, serviço `Manual`, TCP/NP 0, memória 2048 MiB. |
| Serviço e master | SQL local/read-only | Shared Memory, alvo ausente | Serviço iniciado; nenhum listener TCP; instância padrão Express, Windows auth, `master`, alvo 0 em `sys.databases` e `sys.master_files`; MDF/LDF ausentes. Primeira consulta recusada por alias SQL reservado, corrigida sem efeito. |
| Create | SQL local/DDL | um banco, quotas exatas | Um único `CREATE DATABASE` exit 0. Readback `ONLINE`, compatibilidade 170, collation `Latin1_General_100_CI_AS_SC`, dados 64/2048 MiB, log 32/512 MiB, crescimento 16 MiB; zero tabelas/views/procedures de usuário e zero Flyway. |

Validações finais: UTF-8 estrito sem BOM nos quatro arquivos tocados, `git diff --check`, `Test-TrilhaPreparation.ps1` (33 etapas/48 IDs/nove pacotes) e secret scan offline (4.042 candidatos/zero achado) passaram. O validador histórico `Test-Gpt56ChatTrail.ps1` recusou `HANDOFF_PATH` de escopo já documentado em 0308; não foi contado como PASS. Build Java não se aplica a esta unidade operacional/documental.

Nenhum processo da campanha segue ativo. Não houve migrations, IT/JDBC, DLL 12.8.1, mudança do pin 12.8.2, dado de domínio, job, outro banco, produção ou remoto. Não se marca P07/P08 integral por esta evidência.

## Retomada imediata

1. Aguardar decisão explícita do usuário e Segurança sobre o pin `AGENTS.md`/JAR/DLL 12.8.2 antes de qualquer migration ou prova JDBC dependente; preservar o banco vazio.
2. Após decisão válida, revisar runbook/gates e qualificar o schema e P07/P08 em reservas próprias, sem reaproveitar ledgers históricos.
3. Continuar apenas frentes offline independentes já autorizadas enquanto o pin estiver suspenso.