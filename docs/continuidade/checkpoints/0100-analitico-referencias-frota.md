# 0100 — Referências de frota consumidas

EM_EXECUCAO A–N. Prosseguir até entrega integral sem perguntas/continue.
Predecessor0099 SHA256 8dc66305e4df644b9a360f1bf8e7a48591b368af4455065bb92ece9a10215fd8.
Request, before2704, limites e autorizações locais dos checkpoints anteriores
preservados. Construção32/45; aceites67/115 intactos. Nenhum aceite agregado.

V071 instalada e imutável; V001–V071, baseline acompanha. Próxima livreV072.
qualify-owned-fleet-01 e install-owned-fleet-01 confirmados; snapshots/ledgers
em target/macrobloco-analitico-20260912-01/. DDL sem DML de domínio.
physical-owned-fleet-01/session62561 reconciliada exit0:3IT,0falhas/erros/skips,
4,480s de testes; contagens antes/depois preservadas. Nenhum processo ativo.

V071 reutiliza quatro tabelas OWNED_FLEET de V008 e seus selos imutáveis.
Seleção sintética por run/revisão/vigência; quatro TVPs limitadas100; importador
JdbcAnalyticFleetReferences limita32768bytes, valida versão/proveniência/token/
normalização, JSON sem duplicatas/trailing, usa sessão rollback-only/savepoint.
Recibo registra bytes reais e SYSUTCDATETIME após criação do release.
ref.ufn_analytic_fleet consome documento/nome tokenizado, aliases e matriz,
preserva proveniência e distingue referências/ownership/contrato não resolvidos.
AnalyticLaboratoryFleetReferencesIT prova8combinações,2exceções, normalização,
ausência/expiração/outro run, replay exato/divergente e mutação selada recusada.
JdbcAnalyticReferences.importManifestPackaged foi acrescentado sem mudar básico75.
Correção aritmética de0099: perfil MAN contém81labels+13registry+8exclusions=
102linhas,27labels novas sobre básico75. Catalogue já tinha102; agora prova física.

Ainda não há MAT05 consumidor; função frota verificada isoladamente não fechaG.
MANclock/equivalência de offsets/no-op lineage/savepoint adversarial seguem0099.

Próximas ações:

1. MAT05 SQL/JDBC sobre snapshot tipado92, lifecycle e dimensões, composição
   DIRECT e MC/CF/CROSSWALK; dedupe cada Frete e receita/capacidade com lineage.
2. SQL08/09 e dez SQL pendentes, leitor limitado; completar contraprovas C–I,
   JAR11verticais/5fatos/19consultas e SweepK.
3. L–N concorrência/escalas/planos/verify233IT/JAR e revisão/diffs/673colunas/
   sucessão exata/estados funcionais. Não finalizar antes dos critérios integrais.
