# Checkpoint 0310 — parada segura; instalação shadow local preparada

## Autoridade e estado

- 28/09/2026, 09:38 UTC. Anterior [0309](0309-p29-unicode-runtime-boolean-v95-v96.md), SHA-256 `E956B586A6FA7C1A9025E547C78F41D389DE4A240241CA10E510F61FB93BB905`.
- Objetivo: avançar P01–P33 até os inputs externos reais; antes do desligamento, deixar uma unidade verificável e a instalação local pronta para uma tentativa. `STATES.md` continua autoridade canônica; [mapa](../qualificacao-p07-p33/mapa-p01-p33-20260927.md) é índice.
- O usuário autorizou instalar SQL Server nesta máquina e criar somente `localhost/ETL_SISTEMA_V2_SHADOW`, mas mandou **não repetir UAC até o operador indicar prontidão**. Segurança/Supervisor suspendeu físico JAR/DLL/JDBC 12.8.1 por CVE-2025-59250 até decisão explícita do usuário para mudar o pin `AGENTS.md` a 12.8.2. Nenhuma dessas duas respostas chegou nesta unidade.
- `main...origin/main`; deltas preexistentes, 0304–0309 e locks históricos preservados. Nenhum novo UAC, setup, serviço, SQL, DDL, DLL, JDBC, acesso à máquina antiga, push, merge, deploy ou RC.

## Preflight somente leitura e comando pronto

| Item | Observado | Limite |
| --- | --- | --- |
| Máquina/serviço | Windows `LUCAS`; `MSSQLSERVER` ausente; nenhum processo `sqlservr`/`setup`/mídia ativo; diretório `%LOCALAPPDATA%\ETL-V2-SQL2025-Media` ausente; C: 672,4 GiB livres. | Fotografia anterior ao efeito; repetir imediatamente antes do UAC. Não prova ausência de banco em uma instância futura. |
| Mídia | `target/ci-p10-20260927-01/tooling-microsoft/SQLEXPR_x64_ENU.exe`, 748.772.024 bytes, SHA-256 `74AA90C11202A5524E769B9BC22531BAEF22D91E9B2D2E8C3CB99E89A65C5297`, Authenticode `Valid`/Microsoft Corporation, igual a `media-receipt.json`. `sqlcmd.exe --version` v1.10.0. | Nenhuma execução da mídia ou conexão SQL. |
| Escolha de instalação | SQL Server 2025 Express x64, mídia 17.0.1000.7, instância padrão `MSSQLSERVER`, Windows auth, serviço Manual, `SQLEngine`, TCP/Named Pipes off, memória máxima 2 GiB, collation `Latin1_General_100_CI_AS_SC`; quotas de banco no runbook. | [Requisitos Microsoft](https://learn.microsoft.com/en-us/sql/sql-server/install/hardware-and-software-requirements-for-installing-sql-server-2025?view=sql-server-ver17) incluem Express/Windows 11 Pro x64/.NET 4.7.2. |
| Correção antes do efeito | O [runbook](../../runbooks/reconstrucao-shadow-local-20260928.md) adicionou `/SUPPRESSPRIVACYSTATEMENTNOTICE` a `/Q`, conforme [comando oficial](https://learn.microsoft.com/en-us/sql/database-engine/install-windows/install-sql-server-from-the-command-prompt?view=sql-server-ver17). Preparou extração da mídia assinada e setup da árvore completa em ordem serial, inspeção de assinatura/logs e recuperação de estado parcial. | O operador deve clicar **Sim** no UAC de extração e, se aparecer, no de setup. Cancelamento/incerteza encerra a tentativa; sem retry/reparo/limpeza. |

Após sinal do operador, conferir novamente hash/assinatura, host, serviço/processos, diretório e quota; reservar ledger com janela de 30 minutos e executar **uma** extração elevada. Só após exit0/árvore completa/setup autenticado e ausência de instalação parcial, reservar e executar **uma** instalação elevada com os argumentos exatos do runbook. Ler PID/exit, `Summary.txt`/`Detail.txt` e estado de serviço. Qualquer deriva interrompe. Em seguida, no máximo preflight read-only via `sqlcmd -S lpc:localhost -E -C -d master -b -l 5 -t 10`, exigindo host local, instância/edição/Windows auth/storage e ausência do banco exato; não fazer `CREATE DATABASE` se já existir ou houver incerteza. O DDL permitido é exclusivo do banco novo, com quotas e leitura posterior no mesmo alvo. Migrations/IT/JDBC esperam a decisão do pin 12.8.2 e gates respectivos.

## Trabalho independente e lacunas

- O lote SAST v96 preserva 272 alertas brutos: SECURITY 87, MALICIOUS_CODE 66, BAD_PRACTICE 60, STYLE 42, MT_CORRECTNESS oito, PERFORMANCE cinco, CORRECTNESS quatro. SECURITY contém 46 Unicode, 22 alertas SQL em 19 sinks, nove hashes, seis REDOS, dois jitter, um comando e um sincronismo. A triagem causal anterior e provas v95/v96 constam de 0307–0309. O Supervisor pediu próximo lote por causa/alcançabilidade, contraprovas para defeitos reais e um gate final; nenhuma disposição/aceite nominal novo foi feito nesta preparação.
- P08 físico: falta operador pronto, instância/banco local e autorização do novo pin/qualificação do pacote. P11: relatório de feed atual e baseline de Segurança. P29: política/aceite SAST, licenças, RC/SOs/checks da mesma revisão. P10/G02: SHA remoto/checks/owner externos. Nenhuma prova offline substitui esses requisitos.

## Próximas ações, até três

1. Se o operador sinalizar prontidão UAC, executar a única tentativa de instalação local com preflight/ledger/readback acima; se não, manter pausa sem nova sonda UAC.
2. Se o usuário autorizar o pin 12.8.2, aplicar a [proposta delimitada](../../catalogos/p29-sast-preparacao-20260928/PROPOSTA-SHADOW-12.8.2.md) e validar perfis/pacotes/gates offline antes de qualquer JDBC.
3. Na ausência dessas respostas, continuar lote SAST por causa e alcançabilidade e frentes P01–P33 offline elegíveis; registrar parada segura antes de desligar, sem prometer aceite integral externo.
