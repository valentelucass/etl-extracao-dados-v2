# 0158 — Dois conjuntos completos e preview explícito provados

## Identificação e autorização

- **EM_EXECUCAO**. O objetivo continua sendo o pedido integral A–N adotado pelo usuário,
  em `target/preparacao-macrobloco-cadeia-integral-20260914-01/` (prompt, diagnóstico,
  plano, matriz P01–P24 e baseline). Uma entrega final; sem perguntas, subagentes ou
  encerramento com trabalho local corrigível pendente. Continuar após compactações.
- Predecessor entregue 0154; progresso anterior 0157, SHA256
  `ad36cb1d86f7a4ae721a302ba8100a62d80eafc7e838b294a4304e12e593c96f`.
  Baseline privada: 3.349 arquivos, 409 pins e 115 membros anteriores conferidos.
- **39/45 e 67/115 inalterados**. V099 e macrobloco anterior preservados. V2-041;
  somente localhost/ETL_SISTEMA_V2_SHADOW, autenticação Windows, duas travas Maven,
  migrations V2 versionadas e provas sintéticas com rollback. Sem segredos, API,
  V1 executada, produção, serviços, deploy/cutover, commit/push ou índice Git real.

## Decisões e evidência

Rodada: `target/macrobloco-cadeia-integral-20260914-01`. Toda tentativa tem inventário,
reserva, processo, logs e resultado; arquivos canônicos posteriores não são sobrescritos.

| Prova | Observado |
| --- | --- |
| physical-06 | Encerrada, rollback; oráculo MAT03 recusou data financeira fora da vigência. |
| physical-07 | Checkstyle recusou linha longa no diagnóstico; sem execução da cadeia. |
| physical-08 | Cinco fatos exatos e 18/19 SQL; três erros do esperado SQL02 identificados por contrato. |
| sweep-admission-01 | 30 testes passaram, incluindo admissão A2/B24, presença, limites e drift. |
| physical-09 | A2 integral passou; cinco IT históricas de sweep passaram; B24 revelou ordenação legada no esperado. |
| physical-10 | **A2/B24: 11 fontes, cinco fatos, 19 SQL e 33 previews passaram**; cinco bordas de sweep passaram. |
| physical-10 falhas | Cancelamento, deadline e drift passaram; lease reverteu toda a transação e mostrou erro no esperado pós-falha do harness, corrigido. |

- Corrigida a autoria sintética: total agregado MAN acompanha o Frete declarado;
  billingReferenceDate dentro da vigência. SQL02 distingue valor total de freight_value,
  e o esperado usa data e criado_em explícitos. SQL01/11/12 usam a identidade explícita
  do componente; SQL19 ordena as chaves textuais declaradas, inclusive 1006 antes de 717.
  Nenhum resultado SQL foi usado para gerar esperado.
- V100/V101 continuam instaladas com readback. **V102 instalada uma vez com sucesso em
  sweep-migration-02**. Tentativa 01 falhou ao compilar constraint no mesmo batch da coluna;
  ausência da coluna/procedure foi reconciliada antes da correção. Não repetir DDL instalado.
  `sweep-readback-01` confere os dois módulos e V099. Contagens de domínio inalteradas.
- Sweep v2 é obrigatório no input integral. Quatro travessias explicitamente pinadas,
  universo com até 32 chaves/contagens. SQL confere chaves e contagens por raiz, além de
  auditoria, contrato, páginas e terminal. Recusa página perdida, chave trocada, distribuição
  errada com mesmo total e owner divergente. Prepare é idempotente; Java e SQL recusam apply
  para universo declarado. O caminho V092 e suas provas históricas permanecem.
- Métricas physical-10: A2 55 páginas/130.571 bytes/92 registros/41 batches, máximo12,
  39.101ms; B24 251 páginas/1.565.871 bytes/1.104 registros/245 batches, máximo20,
  97.933ms. Onze famílias observadas; Usuários B tem duas páginas/24 registros.
  In-flight final zero; heap antes/depois registrado por conjunto, sem extrapolação produtiva.
- Em construção: comparação integral de múltiplos ciclos (monitor20 por ciclo, coorte
  explícita), replay da revisão3 e revisão4 com valor independente 166.37500000 versus
  143.25000000. Ainda sem PASS dessa ampliação. `integral-physical-11` em execução;
  reconciliar processo/resultado antes de repetir. Tests: ReplayIT e ResilienceIT.

## Próximas ações

1. Reconciliar physical-11; concluir replay, revisão posterior, recusas de oráculo,
   propagação adversarial, falha de materialização e isolamento, corrigindo causas locais.
2. Executar o JAR extraído do pacote nos dois conjuntos; concluir revisão das 45 unidades,
   entidades/V1 somente leitura e classes/duplicações, regressões V099 e suítes/gates finais.
3. Concluir matrizes A–N/45 e P01–P24, pacote/schema, scanner integral com índice privado,
   sucessão M, diffs aplicados em cópia e byte-verificados, relatório G01–G08 e selo/readback.

Não há bloqueio externo para esses passos locais. G01–G08 conservam somente as parcelas
comprovadamente dependentes, sem reclassificação ou dupla contagem. Isto não é entrega final.
