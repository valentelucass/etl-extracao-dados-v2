# Checkpoint0077 — macrobloco relacional encerrado localmente (2026-09-11)

RELATIONAL_LOCAL_COMPLETE. ACEITO_NO_ESCOPO da construção local A–J adotada
pelo usuário. Nenhuma frente deste pedido foi transferida para outro chat.
Anterior0076-relacional-verificacao-integral-e-entrega-candidata.md, SHA256
226dd2590b092085cf5eb1e28707237286cf7dd30ea1a8ff59a4c8ca1e776a06.
Sucessão0075→0074→0073→0072 e fotografias predecessoras preservadas.

Capturas sintéticas de Manifestos/Coletas/Fretes passam pelo pipeline real;
SQL resolve MC/CF por bindings tipados e cardinalidade declarada, mantém
raízes, recibos, revisão e backlog. Claim/hidratação mínima resolve órfãos;
histórico/delta/backfill/replay usam planejamento/control plane existentes.
Reconciliação distingue captura vazia de incompleta. Runner opt-in one-shot
oferece scenario/hydrate/replay/status com destino/budgets restritos e exits
sanitizados. Main segue dormente; fixture não concede permit operacional.

V029–V037 aplicadas em seis instalações fora da IT, após master/Windows e
qualificação com rollback. Hashes congelados, baseline V001–V037 ordenado.
SQL061 final aprovado:129 tabelas preexistentes sem alteração de contagem,
143 atuais,14 novas sem resíduos. Nenhum DML sintético durável.

verify-04:1582 testes,0 falhas/erros,4 skips históricos;95 IT reais aprovadas,
sem skips, incluindo39 regressões anteriores.797 fontes finais iguais ao
build. Formatter, Checkstyle, arquitetura e cobertura aprovados. Dez casos
em processos JAR aprovados e645 classes conferidas; JAR SHA256
6bc256fa2b60eaa7c8788818b3d7056d72ac95a890b265a8e674a07e7fe431d0.
Duas sessões disputaram claim/consumo efetivos; após rollback do primeiro
dono o segundo capturou/resolveu/consumiu. Reabrir adapter na mesma transação
prova reidratação SQL, sem alegação de recovery durável após COMMIT/crash.

Escalas16/256/1024 e repetição256 aprovadas; máximo1 página e1 lote em voo,
17 registros por lote observado, zero página retida ao final.12 planos reais
inspecionados: seeks/sorts, Index Scan no dedupe pequeno,0 spills/conversões
problemáticas.4096 permanece falha exploratória por teto240s,1 spill observado
e rollback reconciliado. Medições locais não comprovam platô de heap ou SLO.

Validadores de schema/baseline/progressive/Coletas temporal aprovados;
continuidade nova com11 contraprovas, predecessora com10 e12 históricas;
runtime local com6 checks. Scanner offline integral e11 contraprovas aprovados;
delta candidato67 arquivos sem findings. Selo final confere os hashes após
o fechamento documental, sem repetir Maven por mudanças apenas documentais.
gitleaks indisponível e feed externo não consultado; nenhum aceite V2-041.

Relatório/matriz/contratos/comandos/manifesto:
docs/catalogos/macrobloco-relacional/. ADR0048 registra REL-LAB-01–10.
Evidências, inventários2494 antes/final, diff.patch, diff-review.patch,
delivery-summary.json e final-delivery-verification.json:
target/macrobloco-relacional-20260911-01/.
13 deltas preexistentes possuem snapshots byte a byte; HEAD sujo não foi base.
STATES, trilha e RETOMADA sincronizados. Logs falhos e checkpoints imutáveis.

Sem B64, novo checkbox ou aceite externo;67/115,191 rotas,zero AGORA históricos.
Somente localhost/ETL_SISTEMA_V2_SHADOW, Windows, sintético rollback-only.
Sem API/.env/segredos/grants/reset/produção/V1/scheduler/deploy/commit/push.
Fresh install contra upgrade não teve prova física em banco vazio/reset;
somente equivalência estática de arquivos ordenados e upgrade local comprovado.

Continuidade: não resta implementação local A–J. Próximos resultados reais
dependem especificamente de identidade/cardinalidade e bindings ratificados,
oráculo independente/correspondências, janela representativa e aceite nominal,
Segurança/V2-041. Não executar nova coleta nem promover R01/R02, V2-046a/b,
V2-047, V2-012a/b/c, V2-050 ou V2-038 por causa desta fixture. Qualquer novo
trabalho segue seu escopo e a autorização efetiva; este checkpoint não a amplia.
