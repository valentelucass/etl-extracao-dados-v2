# ADR 0009 — Janelas, ledgers e publicação atômica

- Status: Aceito por V2-017; control plane e protocolo técnico atômico implementados localmente em
  V2-020/V2-021; composição do runtime segue em V2-022
- Data: 2026-08-30

## Contexto

Os contratos usam datas civis, datas-hora locais, late data, página numérica e expansões. O legado mistura watermark executivo, frescor e janelas móveis; procedures acoplam materialização e sweep. Página vazia ou duas travessias iguais não bastam para promover ausência.

## Decisão

O modelo interno de janela é `[start,endExclusive)` em timezone IANA explícito. Tradutores de fonte caracterizam bordas e precisão antes de converter formatos inclusivos; nenhuma subtração artificial de segundo/milissegundo é presumida.

A chave semântica durável de execução/partição é:

`(environment, source_instance, tenant_scope, entity, mode, partition_start, partition_end)`

- `execution_id`, tentativa e fingerprints de estratégia/contrato/configuração são atributos imutáveis da ocorrência.
- Modos incremental, bootstrap, backfill, replay e sweep possuem ledgers isolados.
- Apenas a fronteira incremental contígua e `PUBLISHED` move o watermark operacional. Outros modos nunca o avançam.
- Estados funcionais seguem `PLANNED → EXTRACTING → EXTRACTED → STAGED → PROMOTED → RECONCILED → PUBLISHED` ou terminal explícito. `COMPLETED` de paginação não é conclusão ETL. V2-020 persiste essa máquina e a bloqueia contra transição conflitante; V2-021 conecta a promoção set-based e V2-022 compõe o runtime.
- `PROMOTED` confirma somente o candidate set imutável. Antes da aplicação, V2-023 exige avaliação
  DQ completa e aprovada para a mesma execução/policy; não há novo estado funcional nem atalho para
  publicar sem essa evidência.
- Staging é isolado por execução. A preparação fecha um candidate set imutável; aplicação técnica,
  evidência por candidato, reconciliação, eventos, publication pointer, fronteira incremental e
  liberação de lease usam uma única transação SQL; o último write revalida a lease pelo relógio do
  banco antes do commit. O retorno à JVM contém apenas contadores e
  timestamps `O(1)`; dependentes leem somente a versão publicada.
- Sweep tem snapshot, ledger e checkpoint próprios. Sem prova de completude, termina `BLOCKED` e não altera ativos.
- Uma entidade independente já publicada confirma o próprio checkpoint; falha de outra torna o ciclo global degradado/falho sem desfazer publicação válida.

## Consequências

- Late data é encontrada por revarredura/overlap versionado e dedupe idempotente; `by_updated_at` não vira watermark sem prova.
- Fechamento mensal e fatos só rodam após todas as entradas da janela estarem publicadas.
- Replay conflitante, mutação de histórico, avanço fora de ordem e publicação parcial falham de modo observável.
