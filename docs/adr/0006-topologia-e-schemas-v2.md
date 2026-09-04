# ADR 0006 — Topologia e schemas do banco V2

- Status: Aceito por V2-017; implementação local concluída em V2-019
- Data: 2026-08-30

## Contexto

O protótipo local reservou `ctl/stg/shadow/recon`, enquanto o roadmap exige domínio, referências, fatos e contratos SQL separados. “Sombra” descreve o ambiente de comparação, não uma camada de negócio. O legado também contém DDL, migrations, executor e validator divergentes.

## Decisão

Adotar um banco V2 novo, dedicado e reproduzível por Flyway, separado de `ETL_SISTEMA`, com schemas:

| Schema | Responsabilidade |
|---|---|
| `ctl` | catálogo de fontes, execuções, leases, páginas, partições, checkpoints e publication pointers |
| `stg` | registros tipados/minimizados por execução e presença; sem payload bruto por default |
| `core` | entidades canônicas, filhos e relações aprovadas |
| `ref` | referências versionadas, aliases e dimensões pequenas |
| `mart` | cinco responsabilidades de fatos e agregações set-based |
| `pub` | views/contratos SQL aprovados por consumidor |
| `recon` | quarentena, DQ, paridade, divergências e evidências sanitizadas |

- O schema vazio `shadow` é absorvido/retirado em V2-019.
- Migration e runtime usam identidades separadas. Runtime não possui DDL nem acesso ao legado; grants são por necessidade e testados negativamente.
- Um manifesto de schema gera fingerprint, validator e comparação banco vazio versus baseline aprovado. As migrations 001–059 do legado são evidência, não história executável do V2.
- `pub` não executa DDL/DML cross-database. Endpoint/alias e contratos só são publicados após manifesto de consumidor e gate.
- O banco V2 permanece sombra até cutover. A menor unidade de corte depende de roteamento e write-fence provados em V2-048a.
- Qualquer proposta futura de topologia in-place exige novo ADR, aceite explícito em V2-048a e prova de que não cria dois writers; não é uma alternativa implícita deste ADR.

## Consequências

- V001/V002 formam a fundação de schemas/papéis; V003 cria o control plane de V2-020 em `ctl`.
  As migrations V001–V003 do protótipo anterior foram preservadas em
  `database/evidence/historical-shadow-v001-v003/` como evidência histórica e não participam da
  história Flyway ativa.
- Views, fatos e referências entram somente depois de contrato/identidade/regra da responsabilidade dona.
- Backup/restore, RTO/RPO, retenção e topologia compartilhada exigem ensaio e owners; este ADR não provisiona ambiente.
