# Checkpoint 0043 — retomada autorizada e correção de lote SQL

- Data: 10/09/2026 UTC; mesmo B60.
- Anterior: 0042-bloco60-correcao-offline-pacote-corretivo.md;
  SHA-256 e40a84e3b8c98047b557951680af3a28a0db72d412026ab8cfab2963607f91f8.
- Objetivo efetivo: continuar até solucionar, inclusive a pendência apresentada.
- Decisão: a nova instrução do usuário foi dada após a apresentação do pacote
  cc4f84cb…a167 e autoriza resolver a pendência concreta; não exigir palavras
  formais de aprovação. A interpretação anterior da parada foi restritiva demais.
- Registro integral e limites: target/execucao-b60-corretiva-20260910-0040/USER-INSTRUCTION.txt.
- Inventário inicial: 1.865 arquivos e cópias before/ no mesmo diretório privado.

## Primeira tentativa corretiva

Pacote cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167
executado sob suporte elevado pelo UAC normal. Preflight do alvo V024/SERVICE19,
colisões e multiconjunto histórico passaram. Ativação recusada por erro SQL134:
cinco variáveis duplicadas entre profile-before e profile-active no mesmo lote.
Nenhuma JVM iniciada. Oito sqlcmd foram debitados; não houve commit de ativação.

Readback SQL: UNACTIVATED, SERVICE19, quatro scopes existentes inativos, zero
políticas v2 ativas e zero grants temporários. Perfil before passou; multiconjunto
histórico preservado. UNKNOWN conservador do submit resolvido por esse readback;
ledger histórico mantém seus bytes, com explicação em first-attempt-verification.json.

## Correção e continuação

- Novo activate.sql usa GO antes do perfil ativo: separa variáveis, conserva
  conexão/transação e COMMIT único. ScriptDom160 reproduziu cinco duplicidades
  no original e zero na revisão. Sem alteração de migrations ou Java.
- Pacote: database/proposals/bloco60-continuacao/package.json;
  SHA-256 0560a96925160b1e17a01692a56abd91e1806fb45bc24ef01a6f485489c7c024.
- 242 arquivos; JAR, bundles, 74 requests, oráculos e recuperação preservados.
- Envelope cumulativo: oito sqlcmd já debitados, zero JVM/HTTP; mesmos 80 JVMs,
  240 sqlcmd/400 HTTP e deadline original 10/09/2026 04:40:10.0501172 UTC.
  Novo ledger de revisão não renova orçamento ou tempo. Dez checks de limite passaram.
- Lançamento normal UAC solicitado uma única vez; a liberação do Windows é um
  controle do sistema operacional, distinto da autorização de trabalho no chat.
- Nenhum novo checkbox ou aceite físico até observar toda a matriz e recuperação.

## Próximas ações

1. Acompanhar o lançamento UAC já solicitado; não abrir solicitação duplicada.
2. Conferir cada caso e a compensação; preservar falhas, saldos e evidências.
3. Consolidar o resultado autoritativo no próximo checkpoint e atualizar STATES/trilha.
