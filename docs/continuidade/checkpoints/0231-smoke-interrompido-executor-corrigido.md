# 0231 — Smoke interrompido antes do JAR; executor corrigido

Estado: CORRIGIDO_NA_CAMADA_OFFLINE_P08_PENDENTE. 2026-09-22T02:41Z.
Anterior:0230-p07-integral-pos0227-qualificado.md;
SHA-256 1f5bede8bdfac38d0c2bf75703c09cc0191b159b2f55859cddf2347f8c0494ea.
Pedido, alvo e limites da ordem0228 mantidos; nenhuma renovação de orçamento.

P07 integral permanece aprovado. Dois ZIPs novos têm730membros/9componentes
e7940550bytes idênticos. O primeiro smoke falhou ao iniciar inspect porque
Get-Command resolveu o mesmo pwsh.exe duas vezes; a conversão para string
produziu um caminho composto inválido. Nenhum processo inspect,JAR ou JDBC
foi iniciado; extração e reserva permanecem preservadas em pos0227-smoke-01.
A cadeia dependente parou em p08-result.json,sem promover a falha.

Prova offline RED:commandCount2,normalizedDistinct1,nenhum filho/JAR/SQL.
Correção mínima:normalizar/deduplicar exclusivamente PATH do filho privado.
GREEN:commandCount1,normalizedDistinct1,filho pwsh exit0,nenhum JAR/SQL.
Sem mudança global,terceiros,Java,POM,SQL,script público de smoke,ZIP,
assertion,massa ou timeout. A prova P07 e o pacote não foram invalidados.
Readback dedicado após falha PASS:agregados idênticos e nenhum Java próprio.

Evidências:target/requalificacao-pos0227-20260922-01/{smoke-correction.json,
path-red.json,path-green.json,smoke-failure-readback-01/result.json}.
O runner anterior foi preservado em run-step-before-path-normalization.cjs.
Nova tentativa prevista:smoke-02,primeira correção física de no máximo duas.
O ledger continua único da ordem atual,sem reabrir o anterior fechado.

Próximas ações:
1. Reservar/executar smoke02 e etapas restantes pelo p08-plan-02.json.
2. Conferir A/B,8variantes,8+21guardas,envelope e readback antes de P08 PASS.
3. Selar nova sucessão,validar,conferir diff/checkpoint e fechar ledger.
Recuperação:parar dependentes,preservar recibos,reconciliar antes de corrigir.
16inputs externos,39/45 e67/115 preservados;sem DDL/commit/produção.
