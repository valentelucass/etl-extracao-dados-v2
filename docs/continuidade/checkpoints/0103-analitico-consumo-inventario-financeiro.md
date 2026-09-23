# 0103 — Consumo físico de Inventário, Sinistros e Financeiro

EM_EXECUCAO A–N: prosseguir integralmente sem perguntas/continue.
Predecessor0102 SHA25634a4534a42d42d4df75a115bcc66b6fca556756a4c253ecb34abd46029a97090.
Mesmos request/before2704/autorizações/snapshots/limites anteriores. Construção
32/45 e aceites67/115 intactos; sem marco final/agregado.

V001–V080 instaladas e imutáveis; baseline acompanha. Próxima livreV081.
V079 SQL11/12: qualificação03 e instalação01 confirmadas, contagens preservadas.
V080 SQL01/06 e binding fiscal: qualificação01 e instalação01 confirmadas.
Não há processo próprio ativo conhecido. Última sessão49695 foi reconciliada
exit0 em physical-financial-queries-01:6IT,0falhas/erros/skips,19,406s de testes,
2min06build, contagens físicas antes/depois preservadas.

As quatro saídas novas têm141colunas de negócio positivas e ordem/metadata reais;
total15contratos com consumo físico, quatro restantes03/04/05/10. JDBC limitado
genérico e integraçãoJAR ainda faltam. Índice não equivale a aceite final.

V079: grão componente corrente, filial INV direta ou por relação INV_FREIGHT
resolvida unívoca; SIN exige branch/vehicle binding. Arrays ordenados, tipo
CheckIn::Order::Loading traduzido, TIME7 com raw9, labels governados, replay,
ausência de veículo e exclusão lógica provados. O perfil manifest-references
102 teve correção das duas traduções SIN_DEALING; perfil básico75 preservado.

V080: SQL01 usa a mesma pub.ufn_expansion_fat e fiscal_policy que MAT04;
placeholder “Faturado” não é documento real. Carteira/instrução têm fontes
tipadas existentes. DTO legado FAT troca nominalmente remetente/rpt e
destinatário/sdr: preservado, documentado ANA24. SérieNFS não tem path: binding
explícito por run/componente/captura/revisão, NULL ou valor50, TVP64 imutável.
Sem binding e com NFS, saída dependente bloqueia; CAP segue. Recusa divergente
53684 preserva o estado anterior via savepoint. Nova captura exige binding novo.
CAP usa releases LABELS existentes e conta vinculada, conciliadoNULL preservado.

Falhas preservadas: physical-inventory-incidents-01 bloqueou no Checkstyle
(import e chaves), semIT. 02 executou3IT com1falha no esperado de hora: SQL
TIME7 retorna08:00:00.0000000. Corrigido esperado;03/session66432 passou3IT.
Depois, financial01/session49695 passou6IT com helpers estendidosCAP/FAT.
Tentativa de invocação PowerShell com Tests sem aspas recusou binding do
parâmetro antes de qualquer reserva; campanha válida01 usa string entre aspas.

Arquivos novos: V079/V080, dois IT de consultas, AnalyticFiscalAttribute,
JdbcAnalyticFiscalAttributes e matrizes inventario-sinistros/financeiro. ADR0050
ANA23/24 e MATRIZ-A-N atualizados. Geradores privados têm guard de arquivo
existente; nunca rerodar para sobrescrever migrations instaladas.

Próximas ações:

1. SQL10 monitoramento interno sanitizado; SQL05 Cotações exige reutilizar
   LocalFiveVerticalRuntime.cotacoes, transporte sintético fechado e tarifa
   governada; core.cotacao atual tem payload e nove campos tipados. Evitar
   depósito de campos; preparar demais atributos tipados, validar presença.
2. SQL03/04 e K: usar kernel FailClosedSweepPreviewKernel existente e
   aplicação de ausência estritamente sintética; concluir contratos restantes,
   leitor JDBC limitado e JAR11entradas/5fatos/19consultas.
3. Contraprovas C–I/J–L, escalas/planos/verify233IT/JAR, diff inicial, matrizes
   completas, estados funcionais e sucessão exata. Não encerrar antecipadamente.
