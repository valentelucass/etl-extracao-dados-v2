# 0098 — Coletores físicos e relógio exato de Manifestos

EM_EXECUCAO A–N; nenhuma entrega final, construção32/45 e aceites67/115 intactos.
Predecessor0097-analitico-manifestos-tipados-e-coletores.md,
SHA256 87c4f773a76e8a6f2624c7adc9faf6411a49c446028605b17daf3987372b9013.
Mesma autorização integral do request congelado, mesmo alvo localhost/
ETL_SISTEMA_V2_SHADOW, DML sintético rollback-only e DDL versionado reservado.
Sem produção, APIs de negócio, V1 executado, dashboards, grants, commits ou limpeza.

V001–V069 instaladas/imutáveis; baseline acompanha; próxima livre V070.
Instalações e qualificações em target/macrobloco-analitico-20260912-01/.
Builds isolados Java17/heap512MiB/900s; antes/depois agregados conferem.
Inventário inicial e trabalho preexistente permanecem preservados.

Resultados reconciliados:

- physical-collectors-02/session19009:6IT passou. CollectorsIT2 e
  ManifestPreparationIT4, sem falhas/erros/skips. Corrigido import FiscalPolicy
  do teste e a associação inexistente é recusada por leitura antes da captura.
  Capturas anteriores ficam preservadas na mesma transação rollback-only.
- Coletores: raiz MAN expandida, duas raízes INV com componentes/repetições,
  bindings distintos, contagens100%, incompletude, receipt idêntico/novoNOOP;
  correção de dia/descarga, partições antigas zeradas/inativas, denominador zero,
  exclusão e reativação explícita passaram. Casos adicionais continuam em
  MAT02-REGRAS.md, não considerar frenteF integralmente fechada.
- V068: função de relógio MAN por segundo UTC+nano; a captura relacional
  original V036 foi sucedida preservando seus gates e as demais entidades.
  Cohort raiz/MDF-e e aplicação usam o par exato. Preparação/snapshots armazenam
  o mesmo par; timestamp histórico(3) não determina mais o vencedor.
  mdfe_status corrigido para COHERENT da raiz, conforme MAN04.
- V069: snapshot_lineage preserva todas as observações físicas vencedoras,
  incluindo quando a invocação atual é stale. FK/trigger recusam outro
  run/raiz/cohort e alteração retroativa.
- qualify-manifest-exact-clock-01 falhou por compilação de constraints no
  mesmo batch do ADD COLUMN. GO acrescentados antes de instalar, tentativa
  preservada. Qualify02/install01 e qualify/install-lineage01 passaram.
- physical-manifest-clock-01/session3861:9IT passou sem falhas/erros/skips:
  Collectors2, Capture1, Preparation6. Inclui diferença de1ns em páginas
  diferentes, offset-03, stale sem regressão, scalar mdfe_status conflitante,
  todos92campos tipados, precisão, métricas complementares e filhos distintos.

Nenhum processo ativo conhecido ao checkpoint. Não repetir migrations/geradores
instalados. Último build: build-physical-manifest-clock-01. Relatórios/exit e
before/after estão no diretório privado da tentativa.

Pesquisa adicional: Microsoft Learn DATEDIFF_BIG e datetimeoffset consultados;
PESQUISA.md cita URLs oficiais/revisão e limite100ns do tipo SQL. ADR0050 ANA17
documenta representação exata. Fonte remota consultada foi somente documentação
pública, sem credencial ou dados de negócio.

Investigação seguinte, ainda sem implementação MAT05:

- SQL08 e09 legados projetam praticamente os mesmos105/106campos preparados
  do fato; SQL09 acrescenta Local de Descarregamento. O inventário congelado
  está em contratos-colunas-inicial.json. Não despejar todas as colunas juntas.
- Fontes V1 dirigidas: database/procedures/005_criar_sp_carga_fato_gestao_vista_manifestos.sql
  e tabelas/032_criar_tabela_fato_gestao_vista_manifestos.sql. Foram lidos
  receita, filtros, classificação/frota e matriz de contrato; restante requer
  leitura dirigida para concluir cada coluna. Não executar V1.
- Receita legada soma Coleta→pick_items→Frete e total do MAN, mas o contratado
  exige dedupe do mesmo Frete que chegar por via direta e pela Coleta.
  V029/V031 oferecem core.relational_lab_link MC/CF, ativo, binding_id/revision,
  origin/target/component; recon.vw_relational_lab_components preserva componentes.
  Ainda falta binding explícito entre Frete relacional e dependência Frete da
  expansão e caminho MAN→Frete direto; IDs numericamente iguais não autorizam join.
- Frota C possui registros/bindings TRACTOR/TRAILER1/TRAILER2/DRIVER, capacidades
  KG10000/5000/7000. V008 já define ref.frota_propria_documento,
  classificacao_frota_alias/matriz/excecao_token na família OWNED_FLEET;
  reutilizar esse mecanismo para regras de ownership/contrato, não duplicá-lo
  automaticamente em novo framework. Ainda não há consumidor Java desses objetos.
- JdbcAnalyticReferences já tem importFixture limitado32768bytes e até100linhas
  por coleção, além do pacote básico75linhas; permite release sintética adicional
  governada. Preservar padrão/provas existentes ao ampliar as referências.

Pendências de revisão importantes: timestamps iguais com offsets distintos podem
ser interpretados como conflito pelo comparador raw de campos TIME; decidir e
provar equivalência UTC preservando raw/offset. Rever SQL THROW/XACT_ABORT e
savepoints nos casos que precisam preservar operações anteriores. No-op deve
distinguir atributos do fato e novas evidências de captura. Nada disso está
declarado corrigido sem teste.

Próximas ações:

1. MAT05: contrato de relações/ponte explícita, referências OWNED_FLEET e regras
   MAN05/06; fato por raiz/competência, lineage e SQL08/09 com metadata/valores.
2. Completar F/C/D/E e outros SQL01/03–06/10–12; leitor JDBC limitado, runtime
   composto JAR11verticais/5fatos/19consultas e SweepK com kernel existente.
3. Provas L–M (concorrência, escalas/plano, verify/233IT/JAR) e fechamentoN por
   diff contra before, matrizes completas, estados funcionais e sucessão exata.

As pendências0097/0096 permanecem. Continuar sem perguntas/continue, inclusive
após compactações. Não encerrar por checkpoint nem contar unidades parciais.
