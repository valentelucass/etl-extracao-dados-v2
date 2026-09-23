# Checkpoint0198 — causas do preflight corrigidas e provadas offline

Data:2026-09-20T21:45Z. Objetivo: executar P04/P05 conforme a ordem finita
POS0198, sem subagentes nem confirmações rotineiras. Estado: TESTADO_NA_CAMADA
offline; preflight canônico pendente. Anterior:0197, SHA-256
4c73d0fadeff82a040a45341a795b447306659498732dea954447f9001f89186.

Autoridade: prompt congelado em target/P04-P05-POS0198-20260920-01/request.txt;
ledger próprio adotado21:34:04Z, expira2026-09-22T21:34:04Z. Somente sombra
localhost/ETL_SISTEMA_V2_SHADOW, Windows integrada, sintéticos e rollback.
Máximo2P04+1P05,3600s cada,3campanhas/10800s; físico consumido0. Nenhum SQL,
JDBC, perfil físico, DDL, fonte, produção ou reserva física nesta unidade.

Inventário baseline.json e cópias before/ preservam o worktree inicial.
Delta: QualificationJson, PackagedFixtureRuntime, PackagedFixtureBindingIT,
novo QualificationJsonPathTest, STATES e contrato SEQ-FIX-02. O controlador
histórico permanece byte a byte igual. Não há modificação de schema/dependência.

Reprodução red-jar-failsafe: exit1,1teste/1falha; XML mostra Failsafe com JAR
como aplicação. Reprodução sem jar:jar: contida aos75s, sem PASS; pilha própria
mostra WindowsLinkSupport.getRealPath/QualificationJson.regular em autoria
real. JDK17 local resolve cada componente; guard repetia isso para cada prefixo.
Correção preserva validação integral com/sem links e symlinks ancestrais,
sem cache. Loader explodido passa a ser explícito e CodeSource é verificado.

Provas: compile-and-path20/20, green-binding-failsafe2/2(66.17s),
regression-path-consumers50/50; zero falhas/erros/skips. Enforcer, Spotless,
Checkstyle e javac17 PASS.14oráculos,duas sequências, quatro adulterações
(JAR/runtime/input/schema), AssertionError e junction substituída recusados.
Logs e XMLs completos nos diretórios homônimos da rodada; nenhuma simulação
substituiu binding real. Processos próprios das provas encerrados.

I/J seguem IMPLEMENTADO_NAO_QUALIFICADO; P05 NOT_RESERVED_NOT_EXECUTED.
39/45 e67/115 preservados; nenhum aceite físico/canônico foi fechado.

1. Executar o controlador histórico ArtifactDirected240s na revisão atual;
   conferir exit/XML/UTF-8/bytes. Parar antes de P04 se falhar.
2. Só após PASS, conferir alvo no master, vigência/saldo/processos e reservar
   P04#1 antes do efeito; qualificar I/J integralmente.
3. Só após aceite integral I/J, reservar P05 e medir2/4/8/16; encerrar estado,
   trilha, matriz, validadores e checkpoint, sem P06–P08.

Não renovar saldo/vigência nem reescrever ledgers/hashes históricos.
