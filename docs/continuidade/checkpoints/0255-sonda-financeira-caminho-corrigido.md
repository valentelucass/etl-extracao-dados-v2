# Checkpoint 0255 — sonda financeira com caminho de Coletas corrigido

Data: 22/09/2026. Sucede o checkpoint 0254.

## Fotografia

Objetivo do usuário: aplicar as correções necessárias até superar a falha e
obter resultado, sem nova confirmação de rotina. A autorização vigente manteve
somente leitura em memória via as três sondas permitidas, templates
6908/6389/4924 e GraphQL estático de auditoria; sem produção, escrita, banco,
DDL/DML, agenda, deploy ou corte.

A falha da auditoria GraphQL de Coletas foi isolada a uma lista de atributos
vazia contendo indevidamente uma chave nula. `New-EntityIndex` foi corrigida
para normalizar essa lista. As proteções complementares de identidade ausente,
item ausente, grupo de receita ausente, forma de `edges` e resumo sanitizado
permanecem ativas. Parse PowerShell e autotestes padrão/três páginas passaram,
sem chamada de rede.

Recibo sanitizado:
`docs/continuidade/probes/2026-09-22-financeiro-0709-sucesso-tecnico.md`.

## Resultado e limite preservado

A repetição controlada de 07/09, justificada pela correção local validada,
consumiu seis das dez chamadas, terminou sem parada e confirmou igualdade de
identidade entre Data Export e GraphQL para Coletas. Fretes e Faturas 4924
estavam vazios; logo relações e equivalências financeiras não foram aceitas.

O limite segue inalterado: `per=100`, até três páginas por fonte, dez chamadas
seriais, três segundos entre chamadas, timeout de até 30 segundos e resposta de
até 10 MiB. A data 07/09 não deve ser executada novamente sem nova mudança
material. Próxima investigação financeira requer outra data fechada, já coberta
pela autorização vigente, que tenha Fretes dentro desse envelope; se não houver,
aumentar volume depende de ordem específica.

Não houve escrita, banco, DDL/DML, agenda, deploy ou corte.

## Verificação final

`git diff --check` passou sem erro de whitespace; os avisos CRLF/LF pertencem a
arquivos preexistentes. O validador histórico
`Test-ContinuidadeAgentes.ps1` continua recusando o delta com `HANDOFF_PIN`.
Nenhum manifesto ou ledger histórico foi alterado para ocultar esse drift.
