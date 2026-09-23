# 0109 — Coletas: preparação verificada e consumo em prova

EM_EXECUCAO A–N; continuar até entrega integral sem perguntas/continue.
Predecessor0108 SHA256313091e92f69e1506b3ee093d03cca7eb96833140f63d5e887cf3d7943e81903.
Request/before2704/limites vigentes; construção32/45, aceites67/115. V089–091
instaladas, baseline sincronizado; próxima candidataV092. Nenhum aceite novo.

Provas reconciliadas:

- physical-collection-supplement01/session24458:2IT passaram,0falhas/erros/skips.
  Resultado posterior ao0108;12campos positivos e divergências de retry/escopo/usuário.
- V089 qualify-preparation01 falhou collation, preservado;02 passou;03 passou
  após acrescentar imutabilidade de lineage/receipt. install-preparation01 passou.
- physical-collection-preparation01/session53689:checkstyle star import falhou
  antes de teste; corrigido. physical-collection-preparation02/session81111:
  3IT passaram,0falhas/erros/skips,8.254s testes/1min43build. Before/after preservados.
- V090 qualify/install-collection-regions01 passaram. V091 qualify-query01
  falhou collation da versão de normalização;02/install-query01 passaram.
- physical-collection-queries01/session77669:checkstyle NeedBraces falhou antes
  de teste; corrigido. physical-collection-queries02/session91750 EM_EXECUCAO:
  reconciliar exit/retorno/reports antes de repetir. Selecionadas CollectionsIT3
  e CollectionQueriesIT3. Não presumir PASS.

Implementação:

- V089:31campos DE concretos em stg.analytic_collection_attributes;
  26campos de raiz (exceto cinco pck_mik_ de filhos) em snapshots imutáveis.
  Presence/wire/raw, decimal28,8,DATE,instant7+nano9; coortes físicas coerentes,
  lineage completa. Preparação verifica captura/contrato reais, usa precedência
  terminal/exact-time do core relacional existente. ABSENT preserva/NULL limpa,
  empate de relógio com divergência recusa53751. Replay/stale avança somente
  último visto/extração, snapshot de negócio preservado.
- LocalAnalyticCollectionRuntime compõe LocalRelationalRuntime+preparação com
  savepoint; JdbcAnalyticCollectionPreparation retorna física/raízes/updates/noops.
  Provas:3duplicatas→1raiz, replay/receipt idempotente, NULL/ABSENT, terminal
  antigo vence aberto novo, captura conflitante reverte e recuperação funciona.
- V090/JdbcAnalyticCollectionRegions/collection-regions.synthetic.json:
  famíliaLOGISTICS_REGION existente, TVP100/32KiB, release/receipt/ratificação
  SHADOW sintéticos, escopo run/revisão/vigência, retry EXCEPT completo.
  Normalização trim-upper-nfc-v1, importação Java exige NFC;2regras sintéticas
  CAMPINAS/SP eCEP13000000–13099999 demonstram prioridadeCEPantescidade.
- V091:core.analytic_collection_supplement_effective escolhe último valor
  nãoABSENT de cada campo somente em snapshots efetivos; NULL limpa. Linhagem
  porcampo/supplementId. SQL03 contém41colunas de negócio, SQL04 contém13,
  comcolunas técnicas adicionais. Ainda aguardam prova física.
  Branch é binding explícito; região releasevigente; usuárioativo anexadoao
  mesmorun porchave explícita, senãoIDbruto. NumeroManifesto vemMCatual+coorte
  MAN+estadoativo, comordemnominallegada sequenceDESC/idDESC; ordemnãoéidentidade.
  RegiãoColeta é cidade/UF conformemapperlegado;aliasesDEregiãocapturadosseparados.
  Metadata31+12+linhagem; StatusCOL11, DATETIME7 comnano9preservado/auditado.
- recon.analytic_collection_absence é schema de estado usado nasviews, sem
  procedure de aplicação ainda. Nenhuma exclusão foi implementada/alegada.
  SQL04filtroconfirmed;SQL03candidataExcluída/combitfalse foi previstoemDDL,
  masKprecisa implementar/provar transições reais e limpar campos no reaparecimento.

Próximas ações:

1. Reconciliar physical-collection-queries02/session91750, corrigir até passar;
   exportar metadados19SQL, atualizarcatálogos eADR ANA27/28 com provas.
2. Implementar K: snapshots sintéticos completos com contratos/receipts reais,
   kernel Sweep existente, candidata→confirmada emduasobservaçõesindependentes,
   replaynãoincrementa;reaparecimentolimpa semDELETE. SQL03/04/isolamento/falhas.
3. Completar J–N ependências C–I:leitor/JAR11/5/19,modos/hidratação/delta,
   adversariais/concorrência/escalas/verify233IT/diff/sucessão/relatóriofinal.

Sugestão técnica paraK (não implementada, rever antes de agir): kernel exige
4provas independentes, ordinais1/2travessias e3/4ausências, todosrun/occurrenceFP
distintos. Cada observação de ausência pode compor4capturas reais da mesma
fixture fechada, mantendo contador de confirmação1porobservação;segundaobservação
deve ter4outrascapturas. Evita fabricarreceipts ou relaxar kernel. Descriptor
fixture fechado/universo/range/omitFirst eSQLconfere conjunto completo, volumes,
auditoria de páginas, contrato eescopos; não somente terminal. Captura éBACKFILL
do pipeline existente; reconciliação SWEEP éaplicação separada opt-in.
Reaparecimento deve atualizar estado parazero (V091constraint ainda só1/2;
V092aditiva precisará ajustarconstraint parazero/null antes de aplicação).

Geração privada build-collection-*.cjs preserva existência de migrations;
não rerodar geradores instalados. Correções pós-instalação exigem nova migration.
Reconciliar por ledger.jsonl/execution.log dosattempts de schema;execução Java
usa <attempt>-exit.json/reports. Nomes curtos acima têm prefixo de diretório
target/macrobloco-analitico-20260912-01; nomes exatos nos arquivos de evidência.
