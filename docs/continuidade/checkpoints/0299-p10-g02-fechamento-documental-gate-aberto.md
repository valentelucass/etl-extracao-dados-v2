# Checkpoint 0299 — P10/G02, fechamento documental sem aceite — 25/09/2026

## Alvo e limite

- Anterior: [0298](0298-p10-g02-cenario-sql-oraculo-puro-gate-aberto.md), SHA-256 `779053a985ab14a47be6f90fcdb97d031e5147dacf114f8cc4bc2b30b975e5d9`.
- Direção vigente: finalizar e documentar a rodada; sem novas implementações, testes de cobertura ou extrações. HEAD local/remoto consultado: `b9dac416737ac69c98d0e63715411b7c62fa03f7`.
- Efeito desta unidade restrito a `STATES.md`, este checkpoint, `RETOMADA.md` e trilha. Recuperação: revisar apenas o delta documental desta unidade; preservar código candidato, índice Git real, `.gitattributes`, `.gitignore`, `AGENTS.md` e backups. Sem push, merge, deploy, banco ou fonte externa.

## Reconciliação e prova

| Passo | Camada | Resultado observado |
| --- | --- | --- |
| Recibos/processos | `target/ci-p10-20260925-01/` privado | Recibos v31 de `clean-verify`, scanner, Gitleaks e validador de trilha existem. Não havia Maven/pwsh do candidato ativo; processo Java alheio permaneceu intocado. Nenhum efeito incerto foi repetido. |
| Bytes | Worktree e espelho Git v31 | 45 inputs modificados/não versionados sob `pom.xml`, `.gitleaks.toml` e `src/` comparados por SHA-256: zero ausentes/diferentes. `git diff --check` saiu 0. Nenhum código/POM/teste editado nesta unidade. |
| Testes já executados | JDK 17, heap 512 MiB, XML v31 | 2.318 Surefire e duas ITs offline, zero falhas/erros, cinco skips históricos. `clean verify` saiu 1 **somente no gate JaCoCo `bootstrap`**: 3.705/5.768 linhas (0,642), 1.647/2.950 ramos (0,558) contra 80/60. Faltam 910 linhas e 123 ramos. Não se alegou novo `verify` nem CI verde. |
| Segurança já executada | Espelho v31 indexado | Scanner: 4.007 candidatos, 4.006 textos, um binário, zero achados/não inspecionados. Gitleaks 8.29.1: zero achados. |
| Segurança do fechamento | Espelho v32 novo, HEAD+overlay, índice somente nele | Scanner **PASS**: 4.009 candidatos, 4.008 textos, um binário, zero não inspecionados/oversized/achados. Gitleaks 8.29.1: **no leaks found**. Recibos privados `scanner-v32-private.log` e `gitleaks-v32-private.log`. As anotações de resultado posteriores são apenas documentais. |
| Validadores documentais | Worktree | `Test-TrilhaPreparation.ps1` exit 0: 33 stages, 48 IDs abertos, nove inputs, execução não autorizada. `git diff --check` exit 0; UTF-8 estrito sem BOM nos quatro arquivos documentais tocados. Recibo privado `trilha-v32-private.log`. |
| Shadow | Perfil opt-in físico | **IT não executada**: `V2_SHADOW_JDBC_URL` ausente no processo. Gate estrutural continua exigindo localhost/`ETL_SISTEMA_V2_SHADOW`, autenticação integrada Windows, sintéticos, rollback e 80/60. |
| Graphify | Atualização AST local | Duas tentativas anteriores de `graphify update . --no-cluster` falharam com `-1073741819`; não repetidas sem diagnóstico. O grafo pode estar defasado. |
| Remoto/owner | HEAD exato, read-only | `dependency-audit` skipped, `secret-scan` failure, `verify` failure; log oficial 403. Nenhum novo SHA/check verde, pacote G02 ou aceite nominal do owner. |

`STATES.md` foi sincronizado antes deste checkpoint. P10/G02 e o `verify`
permanecem **abertos**; nenhum checkbox foi promovido. Recibos brutos ficam
apenas no diretório ignorado `target/ci-p10-20260925-01/`.

## Próximo input/ação exato

1. Sob nova direção técnica, cobrir causalmente ou classificar com prova os caminhos puros restantes de `bootstrap` sem baixar 80/60, depois repetir `clean verify` nos bytes finais. Esta rodada documental não abre essa implementação.
2. Para prova física, fornecer `V2_SHADOW_JDBC_URL` que passe as travas locais e executar o perfil opt-in autorizado com evidência sintética/rollback; até então registrar **não executada**.
3. Owner do repositório fornecer G02 nominal (remote/provedor, aprovadores, proteções e autorização de publicação). Um agente autorizado deve publicar um novo SHA e observar os três checks nesse SHA; o HEAD atual falho e testes locais não constituem aceite.

Diagnosticar o crash Graphify separadamente antes de reexecutar o update.
Não fazer push, merge ou deploy nesta rodada.
