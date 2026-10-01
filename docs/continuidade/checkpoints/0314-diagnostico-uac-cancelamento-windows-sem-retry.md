# Checkpoint 0314 — diagnóstico offline do cancelamento UAC

## Identificação, objetivo e autoridade

- 28/09/2026, 22:19 UTC. Anterior [0313](0313-pin-shadow-1282-offline-uac-loopback-sem-efeito.md), SHA-256 `E2FFE31EB8D8FCCF93D8E9847B038438E460344686AAA1C18A5F2287EA4B3E9D`.
- Objetivo: determinar a causa da `InvalidOperationException` da tentativa única de `Start-Process -Verb RunAs` e inspecionar o helper reservado, sem novo UAC nem tocar serviço, TCP, SQL, JDBC ou migrations.
- Unidade `P08-UAC-CAUSAL-0314`: critério era separar recusa do operador, cancelamento da invocação e defeito causal verificável. Escopo exclusivamente leitura de arquivos/eventos e registro documental. `STATES.md` permanece canônico.

## Evidência e conclusão

| Prova | Camada | Resultado observado |
| --- | --- | --- |
| Evento `Microsoft-Windows-PowerShell/Operational` 4100, record 47081, 19:08:05.529 -03:00 | Windows local, leitura offline | `Start-Process` reportou `InvalidOperationException,Microsoft.PowerShell.Commands.StartProcessCommand` com mensagem “A operação foi cancelada pelo usuário.” XML SHA-256 `158CE9E647E3D1DF02C81897C08AC453EBDC625FE0CF1CAE5FC9EB331069219D`. |
| Scriptblock 4104 às 19:06:03.235 | Windows local, leitura offline | Confirma chamada `Start-Process` com `RunAs`; o launcher anterior imprimiu apenas tipo da exceção, suprimindo a mensagem causal no stdout. |
| Executável, argumentos e helper | Sistema de arquivos e parser PowerShell 5.1, leitura offline | Executável e script existem; argumentos vinculam ao parâmetro `UseShellExecute`; helper sem erro de parser e SHA-256 `6259DF3F9574C0FF8F2360B2585D9934FF5687FF395F9AA6E519FA3D5A82EC89`. Sem recibo do helper ou evento de seu scriptblock. |
| Reconciliação anterior, checkpoint 0313 | Evidência histórica, sem nova consulta física | Mesmo PID/configuração e banco vazio após a tentativa. Nenhum efeito observado. |

O Windows reportou **cancelamento da solicitação de elevação antes do helper**.
O registro não permite atribuir o cancelamento a um gesto deliberado do operador
nem excluir uma condição da interface. Não há defeito causal de construção da
chamada ou do helper demonstrado. Logo não existe correção mínima verificada
que autorize outro helper ou retry. Para um novo attempt autorizado, melhorar
somente a observabilidade do launcher: registrar mensagem/FQID e código nativo
sanitizados quando houver. Isso não é correção do cancelamento.

Recibo privado: `target/shadow-local-rebuild-20260928-01/pin-1282-uac-diagnostic-0314.json`, SHA-256 `8B56D8995B6E8DC7CD29605E4408A8AEB6599CD3B6B8068F41F2FF276BE51B32`.
Ledger original `pin-1282-ledger.jsonl` intacto, SHA-256 `AAF61FF42B79A8C54E75766B0908762E4DE1C8C8AEEFA98E3BD614656B73B654`; helper original intacto. Nenhum novo UAC, processo elevado, efeito físico, SQL ou checkbox nesta unidade.

## Retomada imediata

1. Aguardar sinal novo de prontidão do operador para UAC; sem ele, manter parada física vigente. Não reutilizar a tentativa 0313.
2. Após esse sinal, conferir preflight atual read-only de máquina, serviço, sockets, `master` e alvo exato; reservar tentativa distinta com limite e recuperação antes de qualquer efeito. Incluir captura completa e sanitizada da falha de lançamento.
3. Somente após transporte loopback exclusivo comprovado, retomar gates JDBC/Flyway/migrations/IT separados conforme runbook, parando em deriva ou resposta incerta. P07/P08 e P01–P33 integrais permanecem abertos.
