# Checkpoint 0139 — verify integral e dois pacotes idênticos

## Estado observado

EM_EXECUCAO A–N do pedido integral de 13/09/2026. Verify físico integral03
terminou com exit0: 1.976 unitários, quatro skips históricos, 417 IT em80classes,
zero falhas/erros/skips de integração. As378IT anteriores permanecem por identidade
exata;39casos em19classes novas. Cobertura .80/.60 preservada e aprovada,
rollback confirmado e logs UTF-8 íntegros. Regressão-final-02 passou.

O primeiro pacote veio desse verify. O segundo veio de rebuild independente
offline do mesmo snapshot, sem repetir a suíte já aprovada. Ambos têm172membros,
nove dependências e1.996inputs. ZIP SHA256
54889e1d2153881856a0e68be17b8e770904351d77be10e7b960c56b98aa92d9;
manifesto b008ff7bcc5b4e4468db6de01a8bb2e7e9a0395363a4c6c8803e2e9d560e3b2f;
revisão 3b969cef84a2367e304748b930bbc66b95b3273ad93b5270a55f9d75b31dc540.
O verificador detalhado fonte/classe/recurso/JAR está ativo; igualdade dos hashes
dos builders ainda não substitui sua conclusão.

As21contraprovas extraídas finais passaram antes de JDBC/filhos/controle,
incluindo caminho nativo250 recusado pelo limite local240. Smokes finais estão
ativos pelo pacote distribuível, serialmente; a primeira usa o exemplo empacotado.

## Evidência e reconciliação

Diretório: target/macrobloco-qualificacao-pacote-20260913-01/.
Resultados fechados: verify-physical-qualification-03, regression-final-02,
rebuild-qualification-01, qualification-final-01, qualification-repro-01,
extracted-final-01. Artifact-final-01/session48539 e
smokes-final-01/session32386 estavam ativos ao registrar; conferir resultados
antes de repetir. WORKLOG conserva progresso posterior e tentativas falhas.

Não houve mudança em main/test após verify03. A correção do verificador de
regressão distingue dois factories unitários históricos, conferindo48/53
ocorrências contra XML anteriores; não deduplica IT ou remove testes.
Esse delta documental/script tornou a sucessão candidata04 desatualizada;
regenerar sucessor exato antes dos validadores estritos finais.

## Escopo e próximos passos

Construção37/45 e aceites67/115 permanecem até a qualificação completa.
Somente localhost/ETL_SISTEMA_V2_SHADOW, duas travas, Windows integrado,
sintéticos rollback-only. Nenhum DDL/COMMIT de domínio/fonte real/produção.

1. Concluir auditoria de artefato e17smokes; preservar/reconciliar qualquer falha.
2. Executar oito contraprovas do controle e escalas4/16/32/16 do mesmo pacote.
3. Consolidar provas, relatório/comandos/quadro45, STATES funcional, sucessão,
   diffs e todos os validadores finais antes da entrega única A–N.
