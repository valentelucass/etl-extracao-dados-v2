# Checkpoint 0288 — preparação do ignore para GitHub — 23/09/2026

## Identificação e objetivo

- Anterior: `0287-matriz-nove-contratos-http429.md`, SHA-256 `603739fb07f113809351d176480746572956fb6751ec05beba729abb8e28a5d2`.
- Objetivo do usuário: ajustar o `.gitignore` antes de subir o repositório ao GitHub.
- Estado: `TESTADO_NA_CAMADA` Git local; nenhuma publicação foi feita.
- Critérios: arquivos privados ignorados, exemplos versionáveis, ausência de achados no scanner offline.

## Autorização e limites

- A instrução efetiva cobre edição local do `.gitignore` e validação; não houve autorização nem execução de commit ou push.
- Alvo: somente este repositório V2. Sem rede, fonte externa, banco ou alteração de credenciais.
- Orçamento e ledger físicos: não se aplicam a esta edição local. Recuperação: reverter somente as cinco linhas novas do `.gitignore` se necessário.

## Alterações e decisões

- `.gitignore`: ignora cópias reais de `config/application.example.properties`, `config/contract-test.example.properties` e `config/runtime-windows-provisioning.example.json`, incluindo variantes locais específicas.
- `STATES.md`: registra a prova e os limites desta unidade.
- Mudanças preexistentes na árvore foram preservadas. Não houve exclusão ou remoção do índice.

## Execução e evidência

| Passo | Camada | Observado |
| --- | --- | --- |
| `git check-ignore` | Git local | Cópias privadas e `.env`, `target/`, `logs/` ignorados; três `.example` e Maven Wrapper visíveis |
| `git diff --check -- .gitignore` | Git local | Exit 0 |
| `Invoke-OfflineSecretScan.ps1` | Scanner offline | `PASS`, 3978 candidatos, 3969 textos, um binário verificado, zero achado, zero não inspecionado |

- Efeitos sem confirmação: nenhum. Processo próprio ativo: nenhum. Aceite de publicação, revisão humana e cutover: nenhum.
- O ignore não retira arquivos já rastreados; os caminhos privados adicionados não estavam rastreados nesta verificação.

## Retomada imediata

1. Revisar o conjunto completo de mudanças e arquivos novos antes de `git add`/commit.
2. Manter credenciais apenas fora do Git; se algum arquivo sensível já tiver sido publicado em outro histórico, tratar esse histórico separadamente.

Condição de conclusão desta unidade: regras específicas conferidas e scanner offline sem achados. Publicação é ação posterior do usuário.
