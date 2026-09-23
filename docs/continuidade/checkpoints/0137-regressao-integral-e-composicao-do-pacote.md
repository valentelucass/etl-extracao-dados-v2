# Checkpoint0137 — regressão integral e composição do pacote

13/09/2026. EM_EXECUCAO A–N, até entrega única final. Construção37/45,
aceites67/115. Anterior0136, SHA256
56c065dc1c810366693c956a5243e9ce95c4490c4cafdcdca307d1d697cba0ab.

Verify02 terminou FAILED:1976unitários, zero falhas/erros, quatro skips
históricos;407IT, uma falha, cinco erros, nenhum skip. Rollback confirmado,
logs UTF-8 íntegros. A sessão98239 foi reconciliada e terminou1.
Os seis problemas eram RasterProofsIT lendo UTC como fuso local e ScaleIT
tratando novos callbacks de USER/COT/RASTER como DataExportPageResponse.
Também falharam os gates de cobertura .80/.60: qualificacao .74/.58 e
bootstrap .74/.58. Nenhum limite ou teste foi removido.

Old-raster-scale-physical-01 passou as quatro provas Raster, incluindo UTC;
falhou as cinco escalas por dois callbacks ainda não diferenciados. Sessão13399
terminou1/rollback confirmado. Correção subsequente separou USER/COT por bytes,
preservou gauges Data Export/Raster, conferiu27r-1registros DE e encerramento
com zero página/lote retido. A hipótese de cópia do parser mencionada em
comentário não era a causa; as pilhas preservadas identificam COT/captureClosed.

Nova QualificationPackageCompositionIT monta fixture independente usando o JAR
da fase Maven package e dependências reais da mesma revisão. Não usa pacote
antigo nem inventa recibo de verify. Testa seis adulterações entre documentos,
cinco ações físicas do worker, três campanhas de supervisor com filho próprio,
cancelamento/perda de recibo/retomada e admissão sem filho. São dez casos JUnit
(a primeira prova contém seis mutantes). Esta suíte ainda não está qualificada.

ATIVO: composition-physical-01, sessão29920, PackagePhysical, orçamento1200s,
Java17/heap512, somente localhost/ETL_SISTEMA_V2_SHADOW, duas travas, sintéticos
rollback-only. Executa30unitários de qualificação e19IT: nove antigas e dez novas.
Quatro IT Raster observadas PASS; demais resultados finais pendentes.
Não editar Java existente nem iniciar outra campanha SQL até reconciliar
result.json, processos e cópia de formatter. Ler WORKLOG para fatos posteriores.

Sucessão candidata03 passou os sete grupos: qualificação/analítico/expansão/
relacional/temporal/continuidade/contraprovas. Runtime-current-02 passou seis
checks, scanner-retained-03 passou16mutantes. Manifesto36d83f160778a0c196d53b1e243e672973f7118cea8286fcd5826b8b6d9dd17d
com37alterados/2951preservados/157novos. Deltas posteriores tornam esse manifesto
histórico candidato; regenerar sucessor antes dos próximos checks estritos.

Test-QualificationArtifact escrito/AST: compara fontes, classes, recursos,
dependências, JAR e bytes dos ZIPs de builds distintos, além de fonte privada
alterada após build. Ainda não executado. Builder agora inclui README, licenças
e schemas da montagem nos inputs de revisão. Pacote final deve incorporar isso.

Próximas ações:
1. Reconciliar composition-physical-01, corrigir falhas necessárias, verify03
   integral e comparação exata das378IT antigas, skips e cobertura.
2. Dois builds independentes do snapshot aprovado, pacote final, smokes atuais,
   exemplo empacotado, quatro barreiras e escalas4/16/32/16.
3. Concluir relatório/comandos/quadro45/matrizes/provas, revisão e todos os
   scanners/schema/runtime/contraprovas; sucessão/diffs/selos finais exatos.

Sem produção, fonte real, DDL, COMMIT de domínio, grants, serviço/scheduler,
feed/NVD, commit/push ou limpeza. Não encerrar parcialmente nem pedir continue.
