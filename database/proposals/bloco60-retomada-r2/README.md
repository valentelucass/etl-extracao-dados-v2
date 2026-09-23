# B60 — correção da vigência das políticas DQ

A tentativa 8117708c…c0ea encontrou SQL2601: v3 reutilizava a mesma chave
(scope_fingerprint, effective_from_utc) de v2. Ativação foi revertida; readback
UNACTIVATED/SERVICE21 e multiconjunto histórico preservado. Oito sqlcmd debitados,
zero JVMs e zero commits. Falha e ledger permanecem imutáveis.

Esta revisão usa 2026-09-10T00:00:00.125 para v3, conserva v2 em .124 e recalcula
os dois fingerprints canônicos e referências nas requests. Um preflight explícito
verifica a ausência das novas chaves de vigência. Identidades das requests, casos,
limites, SQL de negócio, JAR, bundles, autoridade e critérios permanecem iguais.

Deadline imutável da retomada: 2026-09-10T13:15:21.8816492 UTC. Cada ledger novo
usa esse mesmo valor; não abre outra janela. Total já debitado: 62 sqlcmd,
33 JVMs e 34 HTTP, contra tetos 240/80/400. Até 43 JVMs para concluir a matriz.
Mesma recuperação SERVICE23/scopes6 revogados/policies v3 revogadas/dois grants
removidos, preservando dados, histórias, evidências e V024.

Exclusivo localhost/ETL_SISTEMA_V2_SHADOW, mesmas contas existentes e UAC normal;
fonte apenas sintética. Testes: duas colisões reproduzidas, fingerprints validados,
43 requests preservadas, três scripts SQL analisados e nove checks do orçamento.
