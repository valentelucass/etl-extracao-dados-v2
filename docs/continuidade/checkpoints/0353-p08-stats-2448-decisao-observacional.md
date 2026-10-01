# 0353 — P08: decisão observacional sobre 2448 grupos de estatísticas

- Data: 2026-09-29. Anterior: [0352](0352-p08-stats-2448-v105-revisao-offline.md), SHA-256 `1838D39BFE72E2BACE2BEF3766C1C5F169AB5556A21715F639EC5DAE2867F5A3`.
- Autoridade: decisão expressa do Supervisor após a revisão offline 0352, restrita ao registro de um novo ponto observacional. Nenhum gate físico autorizado nesta unidade.
- Decisão: aceitar **2448 grupos**/SHA `E92AA86CDDAB2B7ADB918F6F1310AA2D86D7702962999E12FA134B15E81DB2E0` e **064 pós**/SHA `78F6F2664AD673D815D01C7B281DF2D606AF8DE419FAE1A9BC48816582E6D2E3` somente como referência para preflight futuro. Igualdade ou novo delta deve ser aferido no gate pertinente; observação não é aprovação de efeito.
- Histórico preservado: **2235 grupos**/SHA `178325C1E554AC608F6469D6FA908AB2343A9CD35DDA7F199BE3CBBB18D7B17C`, 064 pré/SHA `A0B50FFD1CB4FBDB3F8E51D5875FC3B7094B905F71741C7271CC3E3A90F8B865`, **FAIL estrito 0351** (JaCoCo shadow e delta físico), FAILs anteriores e limites do backup 0325. Gate 1/P08 abertos; smoke A/B não executado.
- Limite de DDL: futura alteração de `ctl.execution_audit.failure_category` requer migration nova, guard de dependências/estatísticas, preflight, impacto, recuperação e autorização próprios. Não remover ou atualizar estatísticas por esta decisão.
- Resultado desta unidade: somente documentação; zero SQL/JDBC/IT/Flyway/smoke ou mudança de metadata. Runtime inventaria elegibilidade das ITs para avaliação do Supervisor.

## Próximas ações

1. Supervisor receber inventário Runtime e definir se há novo gate elegível.
2. Em eventual gate autorizado, aferir novamente alvo, dados/schema/histórico, 064 e inventário global de estatísticas antes do efeito.
