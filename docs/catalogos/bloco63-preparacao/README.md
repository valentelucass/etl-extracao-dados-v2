# Preparação B63 — semântica temporal de Coletas

B63_PROPOSTO_COLETAS_TEMPORAL_LOCAL_NAO_EXECUTADO,10/09/2026.
O usuário pediu o próximo grande bloco delimitado com prompt para outro chat,
seguindo o STATES. Foi preparada somente a manutenção local derivada de
COL-TIME-01 observada no B62; a estratégia global de48 itens não foi adotada.

[Prompt integral](../../runbooks/prompt-bloco-63-semantica-temporal-coletas.md).
[Checkpoint0058](../../continuidade/checkpoints/0058-preparacao-bloco63-coletas-temporal.md).

| Frente futura | Resultado local esperado |
| --- | --- |
| A | Mapa V1/V2 de presença, parsing, fuso, frescor, dedupe/promoção e decisão |
| B | Correções demonstradas e regressões sobre código corrente, sem fonte inventada |
| C | Pacote específico da futura prova representativa COL-TIME/Q-COL |
| D | Verify offline, validadores, diff, relatório e continuidade |

O seletor mantém zero AGORA e nenhum próximo bloco oficial de qualificação
externa elegível. Esta é proposta de manutenção local para adoção explícita,
compatível com as regras2/3; não remove EXTERNAL_HOLD nem inicia Q-COL-01.
Sem API,.env,SQL,UAC,B60,runtime operacional,fornecedor,produção ou novo orçamento.
67/115,48 pendentes,191 rotas; nenhum aceite ou checkbox alterado.

Sucessão: quatro arquivos evoluídos com snapshots exatos;2356 arquivos de partida.
STATES/trilha recebem somente prefixo;RETOMADA aponta ao0058;Test-Bloco62RealReplay
resolve a fotografia anterior e propaga os deltas verificados. Manifestos,
checkpoints,probes e receipts B62 permanecem íntegros. Validar com
`scripts/validation/Test-Bloco63Preparation.ps1 -IncludePrivateEvidence -SelfTest`.

Evidência privada em `target/b63-preparacao-20260910/`:inventário,before,
passo-inicial,logs,final-checks,diff e receipt. Consultar final-checks.json para
resultados efetivamente executados. Java não foi alterado nem reexecutado nesta
preparação;1448/0/0/4 continua sendo evidência histórica B62.
