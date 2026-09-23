# Checkpoint 0264 — prompts orientados a conclusão verificável

Data: 22/09/2026. Sucede o checkpoint 0263 e materializa a diretriz explícita
do usuário de que cada prompt deve concluir trabalho real, de forma séria, sem
rodadas artificiais de documentação ou testes repetidos.

## Alteração

`AGENTS.md`, `STATES.md`, a trilha, o runbook de continuidade e
`BLOCOS_ETAPA_2.md` agora convergem na mesma regra: selecionar uma unidade com
critério/evidência/limite, executar no mesmo chat todas as partes independentes
autorizadas, corrigir defeito demonstrado e validar causalmente. Checkpoint,
trilha e checklist são registros da entrega; não constituem objetivo autônomo.

Quando não houver unidade elegível, o executor deve informar de modo curto o
input ou a autoridade exatos e não fabricar tarefas, prompts, aceites ou caixas.
Uma linha do checklist é marcada somente no trabalho que registrou a evidência
correspondente em `STATES.md`.

## Verificação e limite

`git diff --check` passou, sem erro de whitespace; os avisos CRLF/LF são
preexistentes. `Test-ContinuidadeAgentes.ps1` retornou `HANDOFF_PIN` (exit 1),
como nas fotografias anteriores. O manifest/ledger histórico não foi alterado
para mascarar esse resultado.

Esta é mudança documental de governança: não executou fonte, banco, SQL, Java,
deploy, corte ou aceite externo e não altera os critérios pendentes da etapa 2.
