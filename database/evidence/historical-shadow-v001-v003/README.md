# Evidência histórica — prova local V001–V003

Este diretório preserva, sem alteração funcional, a prova local de auditoria de sombra criada em
2026-08-25. Ela não integra o caminho Flyway ativo, não deve receber migrations novas e não é
aplicável a um banco V2 novo.

Os scripts documentam exclusivamente a auditoria sintética anterior de `ctl`, os schemas então
reservados `stg`, `shadow` e `recon`, e a role histórica `v2_shadow_runtime`. A evidência continua
útil para o teste JDBC rollback-only já existente, mas `V2-020` substituirá sua semântica de control
plane. A fundação ativa começou em `database/migrations/V001__create_v2_schema_foundation.sql`.

Nenhum script deste diretório deve ser passado ao Flyway ou executado como baseline de um novo banco.
