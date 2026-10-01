# 0355 — acesso SQL ao shadow: preflight recusou consumidores

- Data: 2026-09-29 UTC. Anterior: [0354](0354-p08-107-it-gate-fail-readback.md), SHA-256 `881BE312B8D49BAAE47E8FD12585867928365450EF518698EE4D25D4B9796440`.
- Objetivo explícito posterior: login SQL `etl_shadow_reader` somente leitura para conector VS Code, exclusivamente no shadow local. O gate P08 0354 foi encerrado e reconciliado como FAIL antes desta unidade; nenhum serviço ou modo de autenticação foi alterado durante Maven/readback.
- Autoridade e limite: nova exceção estrita em `AGENTS.md` §1 e `STATES.md` antes do efeito. O usuário determinou parada sem retry se qualquer preflight falhasse. Sem remoto, produção, fonte real, `sa`, escrita ou outro banco.
- Reserva: `target/shadow-local-rebuild-20260928-01/p08-v105-0355-sql-auth-physical-ledger.jsonl`, SHA-256 `5C0E576E76413F58D31A5BD0DE86219BDD655044B321E28D3D6F22B596CB942D`. Preflight read-only no `master` e shadow exatos com Windows auth; impacto restrito a catálogo, recuperação por parada sem alteração.
- Resultado `master`: exit 0, marcador `AUTH_MASTER_PREFLIGHT_OK`; modo efetivo Windows-only=1, `LoginMode` no registro=1, `sa` desabilitado, login dedicado ausente, shadow online. Saída SHA `08A9EF112370C4F7D715BD125868F290271A3B34E3A04B54051185112F72F453`.
- Resultado alvo: `sqlcmd -E` no `ETL_SISTEMA_V2_SHADOW` saiu 1 com **54913/SHADOW_CONSUMERS_PRESENT** antes da linha de resultado. Saída e ledger preservados; a quantidade e identidade dos consumidores não foram provadas nesta unidade. O snapshot adicional de stats/064 não foi executado após essa recusa.
- Decisão: **parada sem retry**. Nenhuma senha foi gerada, nenhum arquivo DPAPI foi criado, nenhum `CREATE LOGIN`/`CREATE USER`, `LoginMode` alterado ou serviço reiniciado. O acesso VS Code permanece indisponível. O FAIL P08 0354, stats 2536 não aceitas como baseline, Gate 1/P08 e smoke A/B abertos são independentes.

## Próximas ações

1. Supervisor identificar/cessar consumidores por via autorizada e decidir nova unidade de preflight; não repetir esta tentativa.
2. Somente após preflight integral verde, reservar separadamente segredo/login, mudança de modo/restart e verificação SQL/Windows auth, mantendo `sa` desabilitado e listeners loopback.
