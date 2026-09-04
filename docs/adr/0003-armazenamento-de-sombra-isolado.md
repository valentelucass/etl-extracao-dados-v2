# ADR 0003 — Evidência local de armazenamento de sombra isolado

- Status: Substituído como fundação pelo ADR 0006 e V2-019; preservado como evidência histórica
- Data: 2026-08-25
- Revisão: 2026-08-30

## Contexto

Antes do roadmap consolidado, o V2 precisava demonstrar que a auditoria de leitura poderia ser persistida sem tocar `ETL_SISTEMA`. Foi criado e exercitado um alvo SQL Server local isolado, sem payload ou dado de domínio.

## Decisão preservada

As migrations históricas `V001`–`V003` e a integração opt-in registram uma prova local de auditoria `ctl`. Em 2026-08-25, o alvo local isolado foi criado com autenticação integrada, as migrations foram aplicadas, procedures exercitadas em transação revertida e a pós-verificação encontrou zero linhas. Não houve conexão com `ETL_SISTEMA`, fonte remota, job ou ambiente compartilhado.

Essa evidência permanece válida somente para o comportamento já testado:

- composição JDBC explicitamente opt-in;
- armazenamento sanitizado de execução/página/contagens/falha categorizada;
- ausência de token, URL, header, payload, ID de negócio ou hash de ID;
- rollback integral do exercício sintético.

## Partes substituídas

O schema `shadow` e a topologia restrita a Coletas/Fretes não são o desenho-alvo. O ADR 0006 define `ctl/stg/core/ref/mart/pub/recon`; V2-019 absorveu o schema vazio `shadow` e criou história Flyway limpa, sem copiar V001–V003 como baseline produtivo nem as migrations 001–059 do legado.

- `stg` será tipado/minimizado conforme ADR 0002; payload integral não é persistido por default.
- `4924` é a vertical Faturas por Cliente de V2-030, embora a sonda existente permaneça test-only até essa implementação.
- A topologia de cutover não promete rollback automático para GraphQL/legado. Antes da primeira escrita V2, o writer antigo congelado pode ser reativado conforme ensaio; depois do ponto de não retorno, a recuperação é roll-forward por restore/rebuild e replay V2.
- O banco de sombra é recomendado como banco V2 novo, mas endpoint, alias, grants, RTO/RPO e unidade de corte precisam ser ratificados em V2-048a/V2-048b.

## Consequências

- A prova SQL V001–V003 foi movida para `database/evidence/historical-shadow-v001-v003/` e não é lida pelo Flyway ativo.
- O código e o teste JDBC opt-in continuam como evidência histórica local, não como fundação final de schema.
- A fundação física ativa pertence a V2-019; V2-020 substituirá a semântica provisória de auditoria/control plane.
- O alvo local não autoriza ambiente compartilhado, credencial, backup, retenção, release ou uso produtivo.
