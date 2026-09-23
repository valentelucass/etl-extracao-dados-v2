# Checkpoint 0259 — documentação ESL e proteção contra concorrência local

Data: 22/09/2026. Sucede o checkpoint
`docs/continuidade/checkpoints/0258-sonda-financeira-0209-http429.md`
(SHA-256 `e6898dcf09b8c31fb74786fff0b124c54497bf28346584a5cf09790b162879f7`).

## Objetivo e autorização

O usuário forneceu a documentação ESL e pediu investigação profunda para
superar o travamento. A análise permaneceu em leitura local dos documentos e
alteração/teste offline das três sondas read-only permitidas. Não houve nova
consulta à ESL, produção, escrita, banco, DDL/DML, agenda, deploy ou corte.

## Decisão sustentada

A documentação confirma `per` máximo de 100 e intervalo mínimo de dois
segundos no mesmo IP; não oferece cabeçalho, pausa de recuperação ou parâmetro
adicional que explique o `429` observado. A rota de exportação FTP e o uso de
outro IP não são soluções adotáveis nesta frente: ampliariam o efeito externo ou
contornariam a limitação de origem.

Foi incluída uma trava interprocessos local compartilhada pelas sondas de
contrato, identidade e financeira. Ela impede somente concorrência entre sondas
V2 nesta máquina, antes de ler `.env` ou abrir rede; não atribui nem elimina
uso externo do mesmo IP.

## Execução e evidência

| Passo | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Leitura dos trechos ESL | documentação local | confirma limite `per=100`, página vazia e regra de 2 segundos | `docs/continuidade/probes/2026-09-22-documentacao-esl-trava-local-429.md` |
| Parser e autoteste | local, sem rede | quatro scripts válidos; autoteste financeiro com zero chamadas | mesmo recibo |
| Contenção do mutex | local, sem rede | três sondas recusaram com zero chamadas e `LOCAL_CONCURRENT_PROBE` | mesmo recibo |

## Retomada imediata

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente autorizada |
| --- | --- | --- | --- | --- |
| 1 | Confirmar que o IP estará livre de outras integrações e obter o limite efetivo da ESL se houver nova negativa | responsável da origem/ambiente fornece a condição | janela operacional verificável | manter a relação financeira aberta |
| 2 | Registrar nova ordem independente e executar uma única travessia financeira | condição anterior, teto e parada preservados | resumo terminal ou nova parada sanitizada | não repetir automaticamente a rodada que recebeu `429` |

Não há relação financeira, receita, CT-e ou campo de conta confirmado. A
condição de parada continua sendo HTTP não-2xx, `429`, contrato/identidade
inválidos, limite não verificável, página não terminal no teto ou orçamento
atingido.

## Verificação final

`git diff --check` passou sem erro de whitespace. O validador
`Test-ContinuidadeAgentes.ps1` retornou `HANDOFF_PIN` (exit 1); nenhum
manifesto ou ledger histórico foi alterado para mascarar esse drift. Isso não
altera os testes locais nem reclassifica o `HTTP_429` remoto.
