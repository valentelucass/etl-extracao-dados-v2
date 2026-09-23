# 0071 — schema instalado e integração em estabilização

11/09/2026. Estado: IMPLEMENTADO_NAO_QUALIFICADO integralmente.
Anterior: 0070-coletas-integracao-temporal-inicial.md,
SHA256 b419e081df0637614e30ff80b3b2701e9d3e51dc2f2d58430ea400e1b7311576.
Objetivo e autorização: pedido congelado em
target/coletas-temporal-integration-20260910/request.txt; escopo local completo
após B63, sem B64 ou novo aceite. Limites de AGENTS.md permanecem.

Inventário inicial2452 preservado em inventory-before.json e before/ no mesmo
diretório próprio. Nenhum reset, clean, commit, API, .env ou credencial.
Alvo confirmado no master: localhost/ETL_SISTEMA_V2_SHADOW, Windows existente.

## Alterações e evidência

ADR0047/COL-TIME-11–14. V025–V027 instaladas em schema-install-01 após duas
qualificações DDL com rollback. V028 instalada separadamente em
schema-correction-install-01: o EXCEPT físico demonstrou conflito de collation
na presença do alias; correção aditiva alinha ao BIN2 da vertical V010.
As quatro migrations aplicadas são imutáveis; nenhuma linha de teste permanece.

| Prova | Resultado observado | Evidência no diretório próprio |
| --- | --- | --- |
| Dirigido Java | 60/0/0/0 | logs/directed-03.log |
| Novo writer exato | 7/0/0/0, JDBC simulado | logs/physical-03.log |
| IT tentativa01 | erro de data na fixture, auditoria antiga passou | physical-01-failsafe/ |
| IT tentativa03 | conflito físico de collation corrigido por V028 | physical-03-failsafe/ |
| IT tentativa04 | consumo executado; assert do guard e transação concorrente corrigidos | physical-04-failsafe/ |
| Verify tentativa05 | 1563/2/0/4; duas allowlists antigas de migrations | physical-05-surefire/ |
| Schema físico somente leitura | 001,038,057 PASS; 20 objetos novos | logs/schema-readonly-*.log |
| Baseline e gate progressivo estáticos | PASS; V001–V028 | logs/baseline-01.log, progressive-static-01.log |
| Inputs representativos | 16 casos sintéticos e15 contraprovas PASS | logs/representative-03.log |
| Inputs reais ausentes | EXTERNAL_INPUT_MISSING, exit2 esperado | logs/representative-missing-01.log |

physical-06 está reservado e em execução: verify e39 casos IT esperados,
transações compartilhadas sem commit; consultar recibo antes de repetir.
Não há resultado físico desconhecido de instalações anteriores: ledgers e
leituras posteriores confirmam V025–V028. Efeito pendente da IT é transitório.

## Retomada imediata

1. Conferir physical-06, corrigir somente falhas demonstradas e preservar tentativas.
2. Concluir relatório, matriz, snapshots dos deltas e integridade dos validadores.
3. Conferir fontes iguais ao build, scanners, diff e checkpoint de encerramento.

Sem aceite externo fechado. Oráculo independente ligado às capturas, janela e
casos representativos, correspondência qualificada, aceite nominal e V2-041
continuam faltantes. Isso bloqueia apenas ativação real; zero das seis chamadas
condicionais utilizado. Não repetir prova de ausência nativa já encerrada.
