# B16 Cotações — aceite interno de sombra

## Decisão

Em 22/09/2026, o usuário decidiu concluir B16 de Cotações por aceite interno de
sombra, pois não dispõe de contrato/release oficial do fornecedor. A decisão
vale somente para a vertical B16 e não representa contrato, aprovação ou
garantia da ESL.

## Base aceita

- `sequence_code` inteiro como candidata a source key escopada, nunca ID
  canônico;
- filtro `quotes.requested_at`, ordenação observada e campos consumidos pelo
  mapper;
- validação local de V2-027 e teste dirigido do mapper;
- expansão física observada dentro de 100 entidades distintas e interrupção
  fail-closed diante de envelope inválido.

## Limites inegociáveis

Este aceite não prova tenant, unicidade/estabilidade global, grão oficial,
paginação terminal, snapshot/completude, tarifa, regra de negócio, oráculo ou
paridade. Ele não autoriza P17, aplicação de dados, produção, cutover, deploy,
agenda, alteração de credencial, DDL/DML ou qualquer escrita.

Contrato/release do fornecedor, referência tarifária e janela/oráculo
independente seguem exigidos para P17 e para qualquer etapa posterior que
dependa de paridade, publicação ou produção.
