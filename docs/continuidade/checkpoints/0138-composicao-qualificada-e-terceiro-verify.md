# Checkpoint0138 — composição qualificada e terceiro verify

13/09/2026. EM_EXECUCAO A–N até entrega única final. Construção37/45,
aceites67/115. Anterior0137, SHA256
35253fdecea497147a5933da1b7ece8cac8968c049733e3032f4eb03f5dca242.

Composition-physical-01 passou30unitários e17das19IT. As nove provas antigas
passaram: quatroRaster/9,570s e cincoScale/106,2s, incluindo4/16/32/16 eRaster256.
As cinco ações físicas do worker também passaram. Duas provas do supervisor
falharam por caminho absoluto da DLL de268caracteres. Sessão29920 terminou1;
rollback e UTF-8 confirmados, evidência falha preservada.

Native-path-diagnostic-01 comprovou System.load do mesmo DLL SHA256
9a92363a42db34e9f27cedffe18b139d7bb6c93495cb8338f7f8dbb93f3ae538:
268caracteres recusados/exit2;173caracteres carregados/exit0. Sem JDBC/rede,
logs brutos UTF-8 íntegros. O diagnóstico ad-hoc anterior tinha encoding inválido
no console e é apenas TOOL_OUTPUT_ONLY; a evidência autoritativa é diagnostic01.

QualifiedPackage agora recusa NATIVE_AUTH acima de240caracteres absolutos,
limite conservador local antes de JDBC. A fixture usa target/qf/ID curto,
com DLL em221caracteres. Não houve mudança global de PATH, registro ou instalação.
README/ADR documentam o limite. Test-QualificationExtractedGuards ganhou21ºcaso,
com caminho nativo250 e JAR mais curto; sua execução final ainda está pendente.

Composition-physical-02 passou30unitários e10IT/380,2s, zero falhas/erros/skips.
Sete mutantes de conteúdo/cadeia/caminho, cinco ações reais worker, três filhos
supervisionados (normal/cancelamento/perda de recibo), retomada sem duplicação,
cadeia de logs e admissão sem filho passaram. Sessão38533 terminou0/8min16;
rollback e integridade UTF-8 confirmados. Nenhuma falha anterior foi removida.

Foundation-current-02 passou os cinco checks, logs íntegros: scanner3149arquivos,
zero achados;16contraprovas retidas;25guards de envelope, schema e gate progressivo.
Sessão98362 terminou0. Guards de envelope em package-guards-7f8c9b0044ad427ea7cacd11287da972.

ATIVO: verify-physical-qualification-03, sessão53964, orçamento3600s,
Java17/heap512/offline, duas travas e SQL fixo localhost/ETL_SISTEMA_V2_SHADOW,
sintéticos rollback-only. Nenhuma outra campanha SQL ativa. Não editar Java
existente até reconciliar resultado e cópia de formatter. Resultado integral
desconhecido:378IT anteriores devem permanecer exatas, além das novas, com
quatro skips unitários históricos e nenhum skip novo. Cobertura ainda depende
desse verify; os gates .80/.60 continuam intactos.

Test-QualificationArtifact foi escrito/AST: compara inputs, classes/recursos,
JAR e ZIP de dois builds e fonte privada adulterada após build. Run-FinalCampaigns
privado foi escrito/AST:17smokes com exemplo empacotado e quatro escalas, serial,
480s por campanha completa, com reconciliação antes de repetir. Ambos ainda não
foram executados. Builder inclui README/licenças/schemas nos inputs congelados.

Próximas ações:
1. Observar verify03, corrigir qualquer falha necessária e executar a conferência
   de378IT exatas, skips, cobertura e artefato. Atualizar sucessão candidata.
2. Dois builds do snapshot aprovado, pacote final, todos os smokes/guards atuais,
   exemplo empacotado, quatro barreiras e escalas4/16/32/16.
3. Relatório/comandos/quadro45/matrizes/provas, revisão, scanners/schema/runtime/
   continuidade/contraprovas finais; diffs, inventários, sucessão e selos exatos.

Sem produção, fonte real, DDL, COMMIT de domínio, grants, serviço/scheduler,
feed/NVD, commit/push ou limpeza. Prosseguir após compactações, sem perguntas,
subagentes ou encerramento parcial. WORKLOG conserva fatos posteriores.
