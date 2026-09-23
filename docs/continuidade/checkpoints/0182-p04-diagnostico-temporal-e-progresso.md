# Checkpoint0182 — bloqueio temporal P04 e progresso conferido

## Identificação e objetivo

- Data: 2026-09-20T00:02:47Z (19/09/2026 em America/Sao_Paulo).
- Anterior: `0181-p03-recorte-comprovado-schema104.md`, SHA256
  `5f3c09a8352e9f40002ae660f23a74c73baac4f59e08b1b22686fa7d9d2e6147`.
- Pedido atual: encontrar a solução do bloqueio, conferir STATES/trilha,
  entregas, registros e percentuais.
- Estado: causa TESTADA_NA_CAMADA offline; correção candidata não implementada;
  P04 não aceito. Critérios continuam no STATES e na trilha P03/P04.

## Autorização e limites

- Investigação read-only dos artefatos locais e reprodução do planejador em memória.
- Sincronização documental coberta pelo pedido do macrobloco; sem nova autorização
  inferida para SQL, DDL, fonte real, produção, P05–P08, commit/push ou limpeza.
- Antes da edição: conferidos cabeçalhos atuais, checkpoint0181 e seus hashes;
  alvo limitado a STATES, trilha, este checkpoint novo e RETOMADA.
- Recuperação: remover apenas os prefácios desta unidade, preservando os históricos.
- Nenhuma reserva física nova ou renovação de orçamento. Nova execução física
  depende de pins B–H, saldo/vigência, mudança causal e reserva pelo controlador.

## Alterações e decisões

- Worktree extensamente alterado antes desta investigação, incluindo exclusões;
  nenhuma dessas mudanças foi descartada. Java, SQL, matrizes e ledgers preservados.
- Hashes dos documentos antes desta unidade:
  STATES `74baf43fdeada5d43e60f82fd85ee5f31edb586e96bf392b203c2a7ce17f0fb2`;
  trilha `4b7ce91647a92269be1a9dc4d46d462cf0e4a51ac94981b9a922f06021de9f12`;
  RETOMADA `eb28ea8df72a0d6ef1aad4945cb9029b7e07e18af692b1258997845ace6a361f`.
- Alterações desta unidade: quatro documentos acima, somente diagnóstico/progresso
  e continuidade. Não há alteração funcional nem novo aceite.
- Rejeitado diagnóstico anterior de falha do worker/classpath para os três casos
  sql-06: o planejamento recusa antes de iniciar filho. Não remover a mudança de
  classpath preexistente sem avaliação própria; ela não prova a causa desta falha.
- P03 inteiro/B–H não declarado aceito: o checkpoint0181 aceita só um recorte;
  matriz-a-n.json conserva EM_EXECUCAO e proofs vazios nas frentes A–N.

## Execução e evidência

Base privada: `target/macrobloco-campanhas-integrais-20260915-01/`.

| Passo | Camada/comando | Observado | Evidência |
| --- | --- | --- | --- |
| Reconciliar última prova | Leitura, sem repetição SQL | sql-06 OBSERVED/exit1, sem timeout; controlador registra rollbackConfirmed=true | `p04-supervisor-sql-06/result.json` |
| Conferir IT | XMLs preservados | 5 casos, 3 falhas, 0 erros/skips; recusa de input e owner vivo passam | `p04-supervisor-sql-06/build/target/failsafe-reports/` |
| Localizar camada falha | campaign-result e journal | Três BLOCKED_DEPENDENCY; RESERVED/DEFERRED, sem STARTED/process/receipt do filho | `p04-supervisor-sql-06/build/target/qf/**/control-*/` |
| Reproduzir causa | JDK17 jshell, classes e lib do build sql-06; QualificationJson.read, QualificationCampaign.parse e QualificationPlanner.plan | Três LOGICAL_DEADLINE_EXCEEDED | Mesmos três campaign.json preservados; algoritmo QualificationPlanner |
| Contraprova candidata | deepCopy em memória; somente deadlineSeconds=259200 | Três PASS_LOCAL/DUE; não iniciou worker | Mesmo planejador e mesmos inputs, exceto prazo lógico |
| Reconferir progresso | Leitura de matriz-45-unidades.json e contagem de checkboxes STATES | 39/45=86,7%; 67/115=58,3%; 48 abertos | Matriz e estado canônico; nenhum novo aceite |

Detalhe reproduzível: `QualificationPackageFixture.sequenceCampaign` usa início
2037-08-11, fim exclusivo2037-08-14, tick2037-08-14T12:00:00Z, America/Sao_Paulo,
maximumCatchUp1 e deadlineSeconds86400. A primeira janela termina em
2037-08-12T03:00:00Z e vence em2037-08-13T03:00:00Z, anterior ao tick.
Com259200s, vence em2037-08-15T03:00:00Z. O prazo lógico não é o teto físico
de execução: stage240s/sequence1800s/attempt3600s/SQL60s/heap512MiB permanecem.

Não inferir rollback interno executado a partir do indicador agregado do controlador:
os três filhos não iniciaram. Não inferir ausência de outros defeitos após a admissão.
Nenhuma nova suíte Maven, cobertura, prova JDBC ou gate integral foi executado nesta
unidade documental; as falhas históricas de scanner/sucessão permanecem pendentes.
Não há efeito físico novo sem confirmação nem processo iniciado que reste ativo
nesta investigação; isso não é uma auditoria global de processos de terceiros.

## Retomada imediata — até três ações

| Ordem | Ação | Pré-condição | Prova esperada |
| --- | --- | --- | --- |
| 1 | Corrigir prazo da fixture e adicionar regressão offline, incluindo atraso real | Preservar guards, limites físicos e contrato | Campanhas de sequência admitidas; atraso continua recusado |
| 2 | Vincular critérios B–H às evidências/pins e registrar lacunas da matriz A–N | Recibos P03 e revisão atual conferidos | Predecessores comprovados ou bloqueio explícito, sem aceite inferido |
| 3 | Nova prova dirigida P04 pelo controlador | 1/2 atendidos, saldo/vigência e reserva novos conferidos | I/J completos na mesma revisão, rollback e agregados; ou nova causa concreta |

- Bloqueio imediato é local de fixture; não requer fornecedor nem mudança de banco.
- Parar diante de predecessor não comprovado, teto, falha ou efeito desconhecido.
- Conclusão de P04 exige todos os critérios I/J; PASS do planejador não os substitui.
- RETOMADA deve apontar aqui após leitura deste arquivo. Fotografias anteriores,
  tentativas falhas e manifests não foram reescritos para produzir PASS.
