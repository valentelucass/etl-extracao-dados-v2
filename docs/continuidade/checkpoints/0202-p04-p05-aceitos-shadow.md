# Checkpoint 0202 — P04/P05 aceitos no escopo local — 20/09/2026

## Identificação e objetivo

- Anterior: `0201-p05-quatro-escalas-em-execucao.md`, SHA-256
  `5ad651a21a1c4bfbb236f1e0ece44c28f5f36d66ef34a11db0daa537651d3cc0`.
- Objetivo: desbloquear o preflight canônico, qualificar P04/I–J e executar
  P05 somente após o aceite dos predecessores; uma entrada e uma entrega final.
- Estado: macrobloco concluído no escopo autorizado. P04/P05 QUALIFIED_SHADOW;
  I/J/K ACEITO_NO_ESCOPO local. Não equivale a aceite de pais V2 ou produção.
- Critérios: request congelado da ordem POS0198, STATES vigente, trilha §13,
  matriz A–N e contrato SEQ-FIX-02/SEQ-SCALE-01.

## Autorização e limites

- Pedido efetivo: macrobloco anexado pelo usuário, congelado em
  `target/P04-P05-POS0198-20260920-01/request.txt`; sem subagentes.
- Alvo exclusivo: localhost / ETL_SISTEMA_V2_SHADOW; autenticação Windows
  existente, sintéticos, duas travas opt-in e rollback de toda escrita.
- Sem fonte, rede de negócio, DDL, credencial, produção, deploy ou cutover.
- Ledger único da rodada: adoção 2026-09-20T21:34:04Z, expiração original
  2026-09-22T21:34:04Z, sem renovação. Consumo P04 1/2, P05 1/1, total 2/3;
  7.200/10.800 segundos reservados. P04 #2 não usada; P05 não pode ser repetida.
- P05 reservada 22:19:54Z, encerrada 22:41:48Z, antes do prazo 23:19:54Z.
  P04 terminou 22:10:09Z, antes de seu prazo 22:52:44Z.
- Não há ação pendente de aprovação nem operação de resultado desconhecido.

## Alterações e decisões

- Inventário inicial de 3.585 arquivos, snapshots `before/` e índice Git
  preservados na rodada. Nenhuma ausência nova; schema, pom e controlador
  histórico inalterados. Contadores canônicos preservados: 39/45 e 67/115.
- Correção produtiva mínima em QualificationJson: resolver caminho integral
  uma vez por chamada, mantendo inspeção de links, validação e rehash sem cache.
- Testes: consumidores JAR/explodido explícitos, CodeSource, adulterações reais,
  AssertionError, junction substituída, parada P05 antes de I/O após falha,
  isolamento e agregados por escala. Nenhuma fórmula de negócio alterada.
- Documentos sincronizados começando por STATES, depois trilha, matriz A–N,
  contrato, catálogo e ledger. Fotografias intermediárias/históricas preservadas.
- Rejeitado: aumentar 240 s, pular binding, promover testes offline a aceite
  físico, reescrever manifests antigos ou usar saldo para repetir P05.
- Não comprovado: SLO, platô de heap produtivo, paridade de fonte, recuperação
  durável de domínio, P06–P08 e prontidão produtiva.

## Execução e evidência

| Critério | Camada / comando | Observado | Evidência privada na rodada |
| --- | --- | --- | --- |
| Causas | Failsafe offline limitado | Vermelho real de binding; timeout diagnóstico, pilha própria em resolução Windows | red-jar-failsafe, red-failsafe |
| Correção | Maven offline/JDK 17 | 72 testes PASS; Enforcer/Spotless/Checkstyle | compile-and-path, green-binding-failsafe, regression-path-consumers |
| Gate canônico | Controlador histórico ArtifactDirected, 240 s | 18/18 PASS, exit 0, sem timeout/SQL/reserva | preflight-verification.json |
| P04/I–J | Cinco ITs, perfil opt-in, reserva UTC 3.600 s | 20 ITs/20 unidades PASS; recibos/journals, rollback e agregados | p04-acceptance.json |
| Admissão P05 | Testes offline da guarda real | 8/8 PASS, incluindo falha tardia | p05-offline-admission |
| P05/K | SequenceScaleIT, quatro escalas, reserva UTC 3.600 s | 4 ITs/4 unidades PASS; sete etapas por escala, 19 saídas/33 previews, 12 planos SQL | p05-acceptance.json |
| Fechamento | Preparação, trilha, scanner, JSON/UTF-8/diff | Preparação PASS; duas lacunas históricas; preservação/diff PASS | final-validation, closure-verification.json |

- P05: durações medidas 111,414 / 203,807 / 314,019 / 431,370 segundos;
  maior etapa 73,438 s; maior heap amostrado 54,64 MiB. Limites 1.800 s por
  escala, 240 s por etapa e heap 512 MiB preservados.
- Todas as escalas terminaram PASS_LOCAL, sem linhas da execução após rollback,
  com 246 contagens agregadas iguais antes/depois por escala e na campanha.
  Zero itens em voo/páginas retidas ao término; isolamento conferido pelos testes.
- 2.077 arquivos de execução/schema P05 conferidos; JAR P04/P05 idêntico.
  XMLs íntegros, nenhum failure/error/skip e zero processos próprios residuais.
- Erros de leitura do verificador P04 (header/journal.lock) preservados e
  corrigidos sem repetição física. No fechamento, diff apontou linha em branco
  excedente no novo catálogo; correção restrita ao EOF, recheck PASS.
- Preparação: 1 positivo + 24 negativos PASS. Trilha: FAIL histórico
  `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`. Scanner: FAIL por oito
  MISSING_CANDIDATE preexistentes, zero outros achados. Não esconder esses FAILs.
- Rollback de código: diff próprio contra `before/`, preservando trabalho anterior;
  revisão restaurada exige requalificação. Nenhuma migration a reverter.

## Retomada imediata — até três ações

1. Conferir STATES, relatório POS0198, ledger/hash e closure-verification.json
   antes de qualquer novo trabalho. Não repetir campanhas já consumidas.
2. Se houver novo escopo explícito, usar I/J/K locais como evidência predecessora
   e delimitar a frente seguinte; P06–P08 não foram executados nesta ordem.
3. Tratar lacunas históricas de sucessão/scanner somente por manutenção própria,
   preservando manifests e exclusões anteriores; não condicionam novo efeito
   nesta rodada, pois não resta efeito autorizado necessário ao macrobloco.

Condição de conclusão satisfeita: provas das fases A–D conferidas, aceites locais
registrados, consumo preservado e fechamento documental verificável. RETOMADA
aponta este checkpoint somente após gravação, leitura e hash conferidos.
