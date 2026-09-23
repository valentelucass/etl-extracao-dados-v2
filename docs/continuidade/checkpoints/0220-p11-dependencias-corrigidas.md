# Checkpoint 0220 — dependências corrigidas e auditoria pública PASS

P11_PUBLIC_FEED_PATCHED. 2026-09-21T22:19:33.662Z.
Anterior: docs/continuidade/checkpoints/0219-auditoria-p11-cache-local.md;
SHA-256 24f9ddb37631a4a637876fc980b38e02c59dd49f192685daa36917117a08de84.
Objetivo: concluir a correção técnica P11 após autorização pública, preservando
o trabalho offline e os critérios integrais de P10/P11/P12/P14/P15/P21.
Estado: TESTADO_NA_CAMADA_JAVA_NVD; aceite nominal da baseline pendente.

## Autoridade e limites

Usuário: “autorizo, encerre logo”, em resposta ao pedido exato de acesso público
ao Maven Central/NVD sem credenciais para corrigir dependências e atualizar
a auditoria. Ledger prévio em target/p11-publico-20260921-01/network-ledger.jsonl
e execution-ledger.jsonl. GETs públicos com timeout e limites; Maven/scan em
ambiente limpo, settings próprios e JVM limitada aos dois hosts HTTPS. O NIO
do Windows usa PipeImpl interno; não foi criado serviço de aplicação. Nenhum
segredo/.env/certificado do projeto foi lido, copiado ou modificado; TLS usou
a confiança padrão do runtime. Sem SQL, fonte de negócio, produção, rotação,
deploy, job, release, cutover, subagentes ou nova autoridade de outro gate.

## Alterações e recuperação

POM: Jackson 2.17.2 → 2.18.11; JDBC 12.8.1 → 12.8.2.jre11; autenticação nativa
12.8.2.x64, sem executá-la. Mantida a linha JDBC 12.8, sem promover o candidato
13.4 da etapa anterior. Licenças de JARs permanecem Apache 2.0/MIT; a DLL
Microsoft proprietária conserva qualificação própria pendente.

Parser real corrigido para materializar a coleção vazia/ausente como array: o
relatório limpo antes falhava em Count. Seis testes comprovam a correção sem
aceitar vulnerabilidade score zero, múltiplos achados, null ou supressão.
Novo módulo registra a sucessão; a preparação verifica o POM da sua fotografia,
e o sucessor confere o POM atual. Sem reescrever manifests ou selos anteriores.
Oito deltas sobre 3.722 arquivos; 3.714 intactos, oito snapshots exatos. Quatro
documentos conservam todo o conteúdo anterior como sufixo. Recuperação pelo
diff/snapshots próprios em docs/continuidade/historico/p11-publico-corrigido/,
preservando edições posteriores; nunca restaurar o worktree inteiro.

## Execução e evidência

| Camada | Observado | Evidência |
| --- | --- | --- |
| NVD público | 41.788 registros atualizados; Last Checked 21/09/2026 19:08:25 -03 | scan-02 / JSON real |
| Dependency-Check 12.2.0 | 13 dependências, 0 vulnerabilidades, 0 erro, exit 0; threshold 0.0 | JSON e HTML, resultado.json |
| Java direcionado | 205 testes/29 classes, 0 falhas/erros/skips; compilação completa/Enforcer/Spotless/Checkstyle PASS | test-02 e XMLs |
| Parser efetivo | Relatório real PASS; 6 regressões; 33 casos/30 recusas da política | policy-report-02 / collections-02 / policy-implementation |
| Sequência histórica | 10 contraprovas novas; composição anterior PASS | validation-p11 / succession |
| Trilha e preparação | PASS; seis P/17 requisitos, 13 contraprovas; contadores intactos | validation-trail / preparation |
| AST e scan delimitado | Cinco scripts válidos; catálogo sem achado | ast / scan-catalog |

Catálogo: docs/catalogos/p11-publico-corrigido/. Resultado SHA-256
c9f48273c4b950cbdb1d60ec956987670889c07ccb2c4ca99eb638ae1c03503c.
Validações SHA-256 0f8985dd2e0987265960360ab73ff1683fdf473e59b9cc9533344cf56437e4ea.
Relatórios completos e XMLs têm hashes fixados. Rodada: target/p11-publico-20260921-01/.
Não se alega suíte integral, cobertura nova, teste SQL ou autenticação nativa.

Falhas preservadas: quatro vulnerabilidades do checkpoint0219; primeiro scan
com pipe NIO recusado; três erros do harness de testes por destino de recursos
e helper de junction; falha real do parser, primeira asserção com nome de erro
incorreto e erro de sintaxe do gerador documental antes de qualquer publicação.
Cada problema técnico foi corrigido e testado; as provas RED permanecem.
O cliente NVD registrou recuperação transitória e concluiu a atualização; não
foi repetido efeito desconhecido. Cache NVD original e evidências anteriores
intactos; cache novo é isolado. Nenhum teste/processo próprio fica em execução.

## Retomada — até três ações

1. Segurança revisar e aceitar nominalmente esta baseline técnica e seu escopo.
   A autorização de download não equivale a revisão humana ou aceite nominal.
2. Qualificar autenticação nativa/SQL da combinação nova sob autorização própria
   de laboratório, antes de qualquer uso operacional; não executar sob esta ordem.
3. Admitir o próximo input sanitizado G02/G05/G03/G04 para P10/P12/P14/P15/P21;
   origens e owners-papel permanecem na matriz atual, sem nomes inventados.

P11 técnica corrigida e auditada; P11 integral aguarda aceite nominal. As demais
P mantêm preparação offline e seus requisitos externos; provas B56/SQL anteriores
foram preservadas e não invalidadas. P09 não reavaliado, sem G01 novo.
Contadores 39/45 e 67/115; zero novos aceites V2. Salvar/conferir este checkpoint
antes de RETOMADA; depois conferir novamente selo e cadeia sobre a revisão final.

Não houve produção, fonte real de negócio, rotação efetiva, deploy, paridade real,
cutover, revisão humana ou aceite V2. Nenhum desses resultados foi alegado sem prova.
