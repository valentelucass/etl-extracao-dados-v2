# Checkpoint0051 — fronteira de Manifestos testada no B61

10/09/2026. Anterior: [0050](0050-avaliacao-e-preparacao-bloco61.md), SHA-256
0388f01a0264ec4574aa198fd9abef8eb3df4772bd6244ee5a3c9284cd6c29c4.

Pedido efetivo: usuário adotou integralmente o prompt B61 e pediu concluir A–D.
Autorização: edições locais, Java 17/PowerShell offline, loopback sintético e
continuidade. Sem SQL operacional, UAC, fornecedor, produção ou campanha física.
Objetivo integral continua em execução; A está TESTADO_NA_CAMADA local.

Inventário: `target/b61-local-20260910-144800/inventory.json`, com 2.285 arquivos
públicos e cópias exatas em `before/`; worktree anterior preservado. Preflight
`Test-Bloco61Preparation -IncludePrivateEvidence -SelfTest` passou: 4.286
artefatos do recibo e dez guards. Build novo em `build/target`, caminho efetivo
comprovado em `build-path.log`; nenhum clean executado.

Mapper interpreta o status, domínio recebe texto e representação opaca; redução
recusa excesso antes do 101º next, pelo teto contratual local de 100. Sem ligação
ao runtime ou alteração SQL. Decisão: ADR0042; MAN-01–MAN-07 preservadas.

| Prova | Camada | Resultado/evidência |
| --- | --- | --- |
| Novos testes contra fontes anteriores | Java 17 offline | 26 testes, três falhas esperadas (Jackson, 101 entradas, sequência preguiçosa); `a-red.log`, reports preservados |
| Fontes corrigidos e testes dirigidos | Java 17 offline | 43/0/0/0; `a-green.log` |
| Seleção inicial do JDK | Ferramenta local | Spotless recusou Java25 vindo de JAVA_HOME; log preservado, seleção corrigida no processo para Java17; sem atualização de dependência |

B identificou falha de cleanup no controlador real; correção e 30 guards offline
passaram, mas consolidação/entrega B–D ainda pendentes neste checkpoint. Nenhum
aceite de roadmap, paridade ou observador físico foi fechado. Sem efeito externo
desconhecido ou processo operacional próprio ativo.

Próximas ações (mesma autorização):
1. Concluir runner corrente e regressões B; preservar RED do cleanup anterior.
2. Entregar matriz C usando Q-FND-01/02 e manter oráculos/holds explícitos.
3. Executar verify isolado completo e gates D; sincronizar STATES/trilha/RETOMADA,
   novo checkpoint e sucessão sem alterar hashes históricos.
