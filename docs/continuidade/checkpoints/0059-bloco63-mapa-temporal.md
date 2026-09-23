# Checkpoint0059 — B63 mapa temporal e regressões em preparação

10/09/2026. Predecessor0058: 7bb55c397f48751e961722e62eaf19c13463ba9a14e7fb0466d3521bd9702717.
Prompt B63 integralmente adotado pelo usuário; A–D locais em execução.
Inventário de2365 arquivos e cópias exatas em target/b63-temporal-local-20260910/.
Preparação passou11 guardas, replay B629, B61local11, com evidência privada.
Nenhuma API,credencial,.env,SQL,UAC,campanha ou perfil físico; nada desconhecido.

A: mapa e ADR0044 escritos. Defeito COL-TIME-02: atZone interpreta silenciosamente
gap/overlap como instante. Decisão: exigir um único offset local, preservar bruto
inválido e usar fallback ADR0023. Data civil mantém início válido em São Paulo.
V1 +00:00 e perda de precisão SQL são diferenças explícitas; não copiar/reinterpretar.
V010 bloqueia múltiplos hashes e kernel anterior pode impedir terminal em empate;
proposta de qualificação SQL futura, sem alterar migrations/reducer.

B: novos testes autorais escritos; RED dirigido em build privado em execução.
Não alegar sucesso antes de conferir logs/red-gap-overlap.log. Mapper ainda original.
C/D pendentes locais; qualificação externa continua separada. Sem checkbox/aceite.
Recuperação: deltas contra inventory/before; preservar testes falhos e recibos B62.

Próximas ações:
1. Conferir RED e corrigir apenas parsing local/ligação test-only corrente.
2. Executar regressões e preparar casos/inputs específicos C.
3. Verify offline isolado e validadores; finalizar sucessão, diff e recibo.
