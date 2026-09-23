# Checkpoint 0256 — sonda financeira interrompida pelo limite em 02/09

Data: 22/09/2026. Sucede o checkpoint
`docs/continuidade/checkpoints/0255-sonda-financeira-caminho-corrigido.md`
(SHA-256 `75678581d987dbcda2bd54fadbc7b53d9135f9286aab0fb25e2b917f49965ce9`).

## Objetivo e autorização

O objetivo permanece obter evidência sanitizada da relação financeira em uma
data fechada compatível com o envelope aprovado. O pedido atual do usuário,
"teste", acionou a próxima data ainda não executada pela sonda financeira:
02/09/2026. A autorização vigente cobre somente leitura em memória pelas sondas
permitidas, Data Export 6908/6389/4924 e GraphQL estático como auditoria; ela
não cobre escrita, produção, banco, DDL/DML, agenda, deploy ou corte.

O teto desta campanha foi conferido antes do efeito: `per=100`, no máximo três
páginas por fonte, até dez chamadas seriais, intervalo de três segundos,
timeout de até 30 segundos e resposta de até 10 MiB. Não há ledger físico
aplicável porque a operação é leitura externa em memória; a recuperação é parar
no primeiro limite ou erro de contrato, sem retry/fallback.

## Execução e evidência

| Passo | Camada | Limites | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Sonda financeira 02/09 | fonte real somente leitura | 3 páginas/fonte; 10 chamadas | 3 chamadas; parada segura por página não terminal de Coletas | `docs/continuidade/probes/2026-09-22-financeiro-0209-limite.md` |

As três páginas de Coletas foram válidas e verificáveis, mas a terceira não era
terminal. Fretes, Faturas 4924 e GraphQL não foram chamados. Logo não há prova
de ausência de Fretes, identidade de Fretes, relação Frete–Coleta, receita,
CT-e, Fatura ou equivalência financeira. Nenhum aceite foi fechado.

Não houve escrita, banco, DDL/DML, commit, agenda, deploy ou corte. O resultado
não deve ser repetido nesta data sem mudança material; aumentar páginas ou
chamadas requer uma ordem específica de volume.

## Verificação final

O autoteste local da sonda passou sem rede, cobrindo normalização CT-e,
identidade, atributo vazio, página terminal, relações ausentes, forma de
`edges` e query estática. `git diff --check` passou sem erro de whitespace; os
avisos CRLF/LF são preexistentes. O validador de continuidade retornou
`HANDOFF_PIN`, recusa histórica que foi preservada sem alterar manifestos ou
ledgers para escondê-la.

## Retomada imediata

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa |
| --- | --- | --- | --- | --- |
| 1 | Testar outra data fechada ainda não executada | Compatível com o mesmo teto | recibo sanitizado terminal ou parada segura | não repetir 02/09 |
| 2 | Avaliar relação financeira | Fretes e Faturas presentes e fontes terminais | comparação sanitizada de conjuntos/relações | solicitar ordem específica de volume se não houver amostra compatível |

Condição de parada: HTTP não-2xx/429, erro de contrato, identidade inválida,
limite não verificável/excedido, teto de chamadas ou página não terminal. A
frente financeira permanece `IMPLEMENTADO_NAO_QUALIFICADO` para relações e
campos financeiros.
