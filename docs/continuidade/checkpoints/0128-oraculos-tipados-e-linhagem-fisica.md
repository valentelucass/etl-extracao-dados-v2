# 0128 — Oráculos tipados e linhagem física

EM_EXECUCAO A–N,13/09/2026. Predecessor0127:
f8f1a7747fe1d43f7ed762ebdcd867280f2db37864a9cd49995d5368122fdb03.
Pedido integral em target/preparacao-macrobloco-qualificacao-pacote-20260913-01/
PROMPT-MACROBLOCO-QUALIFICACAO-PACOTE-LOCAL.md. Continuar até entrega final,
sem perguntas, subagentes ou redução do escopo após compactação.

Autorização permanece restrita ao V2, localhost/ETL_SISTEMA_V2_SHADOW,
Windows integrado, duas travas e transação sintética revertida. Sem migration,
COMMIT, V1/dashboard, feed, produção ou publicação. Inventário anterior2988
e snapshots imutáveis em target/macrobloco-qualificacao-pacote-20260913-01.

Implementações novas: QualificationComparator/Oracles, outputs.synthetic.json
com19contratos/673colunas explicitamente mapeadas; QualificationWireOracle e
QualificationLineageEvidence; DAG35nós em QualificationTopology e observer
QualificationMetrics. O leitor JdbcAnalyticQueries ganhou callback opcional de
metadata real de sys.columns/sys.types; chamadas anteriores conservam seu custo.
O comparador mantém somente contagens e24coordenadas de diferenças.

Esperados são literais dos inputs e regras manuais independentes, nunca resultados
selecionados. IDs substitutos de linhagem vêm de consultas limitadas aos recibos
da própria captura; valores de negócio continuam independentes. As falhas
iniciais corrigiram expectativas após investigação de inputs/políticas:
multiconjunto de documentos FAT, precedência de volumes LOC, rótulo governado
Pendente de COL, Unicode/case de COT, wire STRING JSON e grão dimensional por
chave vinculada/dia. Nenhuma vertical foi alterada para acomodar o oráculo.

Provas observadas (diretórios sob a rodada privada):

- comparator-directed-01:8unitários,3novos de comparação e5reexecutados.
- oracle-contract-directed-02:2unitários novos,0falhas/erros/skips.
- oracle-values-physical-04:1IT passou,0skip e rollback confirmado; valores
  de negócio e metadata de coluna de17saídas positivas. Tempos técnicos,
  Metadata estruturado, hash LOC, SQL04 e SQL10 não pertencem a essa prova.
- oracle-lineage-physical-02:1IT passou,0skip e rollback confirmado; dez
  saídas com Metadata conferiram presença/wire/raw/IDs e referências exatas.
- Falhas oracle-contract-directed-01, oracle-values-physical-01/02/03 e
  oracle-lineage-physical-01 preservadas com logs/resultados. A última foi
  Checkstyle, antes da execução da IT; não era divergência SQL.

Em execução ao escrever: oracle-output-physical-01, limite900s do build,
IT240s, processo próprio em process.json. Consultar result.json/estado antes
de repetir. Ela reúne comparador/linhagem/hash/tempos e SQL04 candidata,
confirmação independente e reaparecimento; ainda não há resultado declarado.
QualificationLocationOracle e32hashes de envelope foram gerados dos inputs e
exemplo de enquadramento independente. Ainda exigem prova física. Comparador
agora exige metadata; essa alteração também precisa regressão dirigida/final.

Permanecem pendentes: monitorSQL10, valores não nulos representativos adicionais,
harnessV2-012 consumido, executor de campanha com DAG/planner afetando SQL,
controle de filhos/journal/barreiras, concorrência real no caminho da campanha,
pacote completo/validação/SBOM exato, duas construções/smokes, escalas/verify e
sucessão final/diffs. Topologia/métricas ainda não têm consumidor de campanha.
Entrypoint QualificationLaboratoryMain ainda ausente. Não declarar673colunas
integralmente qualificadas, A–N concluídas ou pacote executado.

Construção37/45 e aceites67/115 mantidos. Nenhum aceite real fechado.

Próximas três ações:

1. Reconciliar oracle-output-physical-01, corrigir e completar SQL04/SQL10,
   hash/tempos, variantes e integração ao harness independente.
2. Integrar planner/janelas/DAG, executor/journal/filhos e concorrência JDBC;
   construir entrypoint e provar barreiras com rollback e recursos observados.
3. Qualificar pacote/reprodutibilidade/smokes/escalas/verify integral, preservar
  378IT anteriores, revisar diffs e emitir sucessor exato e selos finais.
