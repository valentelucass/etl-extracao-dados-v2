# 0126 — Qualificação e pacote: base preservada e contratos dirigidos

Estado: EM_EXECUCAO A–N. Pedido único adotado integralmente em 13/09/2026:
target/preparacao-macrobloco-qualificacao-pacote-20260913-01/PROMPT-MACROBLOCO-QUALIFICACAO-PACOTE-LOCAL.md.
Sem número de bloco novo, sem subagentes, sem renovação de budget histórico.

Base: 2988 arquivos canônicos fotografados byte a byte, inclusive não rastreados,
em target/macrobloco-qualificacao-pacote-20260913-01/before. Inventory-before e
Git-before preservados. Os cinco pins do pedido conferem; o predecessor vigente
é o checkpoint0125/manifesto analítico0389d441f3390f61206bc6ea3403cdb5001b2c32a1502bc98d884806e7a04d3f.
Os validadores analítico e expansão passaram com SelfTest/IncludePrivateEvidence
antes de qualquer delta canônico. Logs predecessor-analytic/expansion e recibo
predecessor-result.json estão na rodada atual.

Implementados inicialmente QualificationJson/Configuration/Campaign/Gate/Planner,
configuração sintética e runner isolado Invoke-QualificationBuild.ps1. O planner
civil existente é consumido. Contratos fecham JSON, tipos, limites, enums, DAG,
pins e travas locais. POM fixa outputTimestamp para a futura prova reproduzível.
QualifiedPackage e ADR0051 adicionados depois da prova dirigida; ainda sem prova.

Verificado: contracts-directed-01/result.json, exit0, cinco testes em
QualificationContractTest, zero falhas/erros/skips, Java17/Maven offline;
Spotless/Checkstyle/compilação aprovados. Não é verify integral, teste de pacote,
qualificação JDBC ou aceite de qualquer frente completa. Nenhum JDBC/DDL foi
executado pelo novo macrobloco até este checkpoint.

Construção permanece37/45; aceites históricos67/115. V2-038/V2-039 são candidatas
a incremento somente após mecanismo integrado e A–N integralmente verificado.
O conteúdo local novo ainda não está selado na cadeia de sucessão. Preservar os
manifestos antigos; reconciliar deltas exatos no fechamento, sem atualizar pins
antigos ou criar exceção genérica de docs.

Próximas ações:

1. Concluir builder/verificador/extrator, SBOM/licenças/proveniência e testes
   adversariais; usar nova tentativa, não sobrescrever contracts-directed-01.
2. Integrar runtime, agenda efetiva, oráculos independentes673, gates por saída,
   journal/filhos/cancelamento; reservar antes de todo JDBC sintético rollback-only.
3. Executar provas físicas, smoke extraído, reprodutibilidade, escala e verify
   integral; corrigir pendências e então entregar N com diff e sucessão selada.

Continuar o mesmo pedido após compactação, sem perguntar ou encerrar entre frentes.
