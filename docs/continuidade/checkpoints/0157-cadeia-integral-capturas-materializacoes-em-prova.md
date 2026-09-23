# 0157 — Cadeia integral: capturas e materializações em prova

- **EM_EXECUCAO**, sem entrega ou aceite. O pedido integral A–N continua após compactações,
  sem perguntas, subagentes ou conclusão parcial. Predecessor entregue 0154; V099, 39/45 e
  67/115 preservados. Checkpoints 0155/0156 são progresso histórico.
- Rodada privada `target/macrobloco-cadeia-integral-20260914-01`, com baseline de 3.349 arquivos,
  409 pins, 115 membros do ZIP anterior e sete contraprovas de sucessão conferidos.
- V100 aplicada uma vez em `scope-migration-01`: RED por CK_expansion_run_scope, GREEN de
  escopo sintético e quatro limites, provas revertidas e contagens inalteradas. Readback
  `scope-readback-03` confere dez definições e V099 byte-idêntica. As tentativas 01/02 de
  readback falharam em opções/formatação de sqlcmd, sem mutação; resultados reconciliados.
- V101 aplicada uma vez em `freight-migration-01`, após RED físico de release compartilhada;
  somente a guarda de versão da captura relacional mudou. `freight-readback-01` confere o
  corpo instalado. Não repetir migrations instaladas; eventual ajuste exige nova versão.
- `integral-admission-02`: 24 testes passaram. `integral-boundaries-red-01`: nove casos,
  oito passaram e um RED esperado mostrou ABSENT interpretado como VALUE no oráculo lateral.
  Admissão com 24 raízes/duas páginas de Usuários e recusas de contexto, vigência e drift passaram.
- Cinco tentativas físicas encerradas com rollback confirmado: 01 expôs revisão de referência
  fixa no plano; 02 release de Fretes não integrada; 03 protocolo GraphQL não registrado;
  04 fingerprint DQ fixo em LOCAL_V2; 05 chegou ao verificador e expôs aritmética de chave
  na linhagem de Coletas. Correções aplicadas nos consumidores existentes; não são PASS da cadeia.
- `integral-physical-05` também comprovou GREEN dos três casos ABSENT/NULL/vazio. Os dois
  conjuntos A/B usam períodos civis, IDs, relações, valores e páginas diferentes; oráculos
  escritos antes da consulta, com diagnósticos separados e nunca usados como esperado.
- `integral-physical-06` está em execução: conferir result.json, processo e relatórios antes
  de repetir. O runner futuro registra arquivos canônicos mais novos sem sobrescrevê-los;
  toda prova pertence ao inventário inputs.json e ao build isolado daquela tentativa.
- Teto de entrada integral ajustado para 32 raízes, coerente com os comparadores limitados.
  Referências, suplementos e seis páginas-fonte são obrigatórios. Provas de sucesso total,
  propagação adversarial, replay/recuperação, sweep/33 responsabilidades e JAR extraído pendentes.
- Catálogo em construção: `docs/catalogos/cadeia-integral-por-contratos/README.md`. Revisão das
  45 unidades, candidatos sem uso, gates finais, pacote, diffs aplicados, sucessão e selo pendentes.
- V2-041/G01–G08 preservados. Somente localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado,
  migrations V2 autorizadas e dados sintéticos com commit bloqueado/rollback. Sem segredos,
  API, V1 executada, produção, deploy/cutover, commit/push ou índice Git real.

## Próximas ações

1. Reconciliar a tentativa 06, corrigir divergências contra os oráculos e comprovar todos os
   cinco fatos/19 SQL nos dois conjuntos, incluindo processo filho do JAR extraído.
2. Integrar sweep/preview ao contexto explícito e concluir P01–P24: mutações, bordas, replay,
   falhas, isolamento, rollback, duas escalas e medições limitadas.
3. Concluir revisões de construção/classes, regressão V099 e suítes/gates/scan; fechar pacote,
   matrizes A–N/45, sucessão, diffs realmente aplicados em cópia, selo/readback e entrega única.
