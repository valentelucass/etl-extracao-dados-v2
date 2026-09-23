# Checkpoint0076 — verificação integral e entrega candidata (2026-09-11)

RELATIONAL_LOCAL_CANDIDATE. A–I implementadas e verificadas; J finaliza a
validação documental, hashes e diff. Nenhum trabalho local foi transferido
para outro pedido. Anterior0075-relacional-matriz-funcional-e-reconciliacao.md,
SHA256 8d14d78661520cb1639584ba781ba3f3c726efcec6e3a7d0152e785662892bb8.

V029–V037 instaladas e congeladas em seis diretórios de instalação, cada qual
com preflight master, Windows e qualificação com rollback antes da instalação.
V036 preserva a identidade de strings sem padding; V037 conserva MDF-e de
até128 dígitos conforme representação canônica existente. Nenhuma migration
aplicada foi reescrita. SQL061 final passou:129 tabelas preexistentes sem drift,
143 atuais,14 novas sem resíduos. DML sintético sempre rollback-only.

verify-04 passou:1582 testes gerais,0 falhas/erros,4 skips históricos;95 IT
reais sem falhas/erros/skips (12 fluxo,40 matriz,4 escala,39 regressões anteriores).
797 fontes finais conferem com o build. JAR:10 processos/casos aprovados;
645 classes produtivas conferidas, SHA256 do JAR
6bc256fa2b60eaa7c8788818b3d7056d72ac95a890b265a8e674a07e7fe431d0.
Fonte sintética atravessa pipeline existente; claim/consumo concorrentes reais;
reabertura do adapter na mesma transação não é recovery após COMMIT/crash.

Escalas finais16/256/1024/256 passaram, máximo1 página/1 lote em voo,
12 planos reais sem spill ou PlanAffectingConvert. Há sorts e Index Scan no
dedupe pequeno; heap e repetição não provam platô/SLO. Exploração4096 excedeu
240s, observou1 spill e permanece falha preservada, com rollback reconciliado.

Scanner offline integral passou com0 findings;11 contraprovas passaram.
gitleaks indisponível; nenhum feed externo ou aceite V2-041. Validadores
de migrations/baseline/progressive/Coletas temporal passaram com V037.
Rodada: target/macrobloco-relacional-20260911-01/. Inventário inicial2494 e
before/ byte a byte são a base do diff, independente do HEAD preexistente sujo.

Próximas ações já autorizadas:
1. Gerar manifesto/diff candidato com snapshots dos13 deltas preexistentes.
2. Executar validadores de continuidade/runtime, contraprovas e scanner do delta;
   corrigir somente falhas demonstradas, preservando logs e fotografias antigas.
3. Fechar relatório/matriz, STATES/trilha/RETOMADA e checkpoint sucessor;
   regenerar hashes/diff e verificar a entrega final.

Sem B64, checkbox, aceite real ou promoção novos. Somente localhost/
ETL_SISTEMA_V2_SHADOW. Sem API/.env/segredos/grants/reset/produção/V1/scheduler/
commit/push. Identidade/cardinalidade reais, oráculo independente, correspondência,
janela representativa e Segurança permanecem apenas nos seus subgates externos.
