# P08: triagem dos validadores 003, 005 e 030 no shadow V104

Este documento propõe o escopo de execução dos validadores. Os scripts e os
recibos FAIL de 0321 permanecem intactos. O alvo observado foi somente
`lpc:localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth; nenhuma migration ou
validação falha foi repetida nesta triagem. Evidência física e hashes estão no
checkpoint 0322.

## Assinatura e causa

| Script | Critério original e intervalo aplicável | Falha em V104 | Evidência causal |
| --- | --- | --- | --- |
| `003_validate_control_plane.sql` | Contrato V2-020b/V003 de `ctl`, antes da substituição de `ctl.usp_control_plane_register_source` em V024 | 11: `CLOCK_AUTHORITY` 1, `NORMALIZATION` 1, `COLLATION` 9 | V024 mudou nomes/formatação: valida comprimento antes de `LTRIM/RTRIM`, captura `SYSUTCDATETIME()` após o lock, persiste `@now` e compara protocolo com BIN2. Três asserts do 003 procuram fragmentos literais de V003. Das nove collations, cinco pertencem à tabela técnica Flyway, uma é esse assert literal e três são colunas persistidas sem BIN2 criadas por V025. |
| `005_validate_progressive_data_gate.sql` | Cabeçalho e inventários fechados declaram V001–V017 | 505: 334 objetos, 171 constraints | 333 objetos e 162 constraints são posteriores a V017; `ctl.page_audit` e sete constraints também vêm de V025. Duas constraints restantes pertencem à tabela técnica `ctl.flyway_schema_history`. Não há prova de objeto clandestino nos 505, mas também não há inventário fechado V104 aprovado. |
| `030_validate_governed_references.sql` | Fundação V008; a única extensão `ref` nominal já permitida no script vem de V010 | 28: 27 objetos, uma collation | Os 27 objetos pertencem a V042, V055, V056, V065, V071, V080, V084 e V090. V042 criou `ref.expansion_lab_label.label` sem BIN2; o tipo de tabela `ref.expansion_lab_label_batch.label` também herda a collation padrão. |

O catálogo confirmou `Latin1_General_100_CI_AS_SC` no banco e em
`ctl.execution_audit.status`, `.traversal_verification`, `.failure_category`,
`ref.expansion_lab_label.label` e no campo `label` do tipo de tabela. O
manifest `database/manifest/control-plane.json` declara BIN2 para toda coluna
`NVARCHAR` persistida do control plane; `030` impõe BIN2 ao texto semântico de
`ref`. A tabela técnica Flyway não integra o contrato de collation do aplicativo.

## Escopo proposto sem perder a prova histórica

1. Manter os três SQL originais e seus recibos. Um orquestrador futuro deve
   conferir histórico Flyway contíguo e escolher 003 somente entre V003–V023,
   005 somente em V017 e 030 entre V008–V041. O 005 ainda precisa de prova em
   fixture V017 exata para classificar as duas constraints técnicas Flyway;
   não se declara PASS histórico apenas por inspeção.
2. Para V104, criar **outro** validador versionado. Ele deve verificar a
   semântica sucessora de V024 (comprimento antes de normalizar, lock antes de
   relógio, timestamp persistido e comparação BIN2), manter o assert de BIN2
   em todas as colunas persistidas `ctl` da aplicação e em `ref`, e excluir
   somente a tabela técnica Flyway por identidade exata. A lista fechada de
   objetos/constraints deve vir de migrations e baseline revisados, com
   allowlist explícita por versão; não usar o catálogo implantado para
   autoaprovar objetos extras.
3. Adicionar casos offline que mutem separadamente a ordem comprimento/trim,
   lock/clock, comparação BIN2 e cada collation persistida. Só então executar
   o novo validador, uma vez, no shadow com preflight, reserva e readback
   independentes. Os 003/005/030 de 0321 continuam FAIL históricos no V104.

## Correção de schema proposta, ainda sem autorização de efeito

Uma V105 nova, versionada e revisada, deve corrigir as três colunas V025 de
`ctl.execution_audit` e `ref.expansion_lab_label.label`; V025/V042 já aplicadas
não podem ser editadas. Para `status`, mapear e reconstruir
`CK_ctl_execution_audit_status` e
`IX_ctl_execution_audit_status_started_at` em uma transação testada. Para a
referência, planejar também o campo `label` do tipo de tabela e a procedure
`ref.usp_import_expansion_references`, cujo `EXCEPT` compara tabela e TVP; a
substituição do tipo precisa tratar a dependência da procedure e o contrato
JDBC sem perda de conteúdo ou mudança de assinatura inadvertida. Incluir
pré-condições de metadados, contagens e validação de dados existentes;
atualizar baseline e manifest/checadores como parte da entrega versionada.

No shadow atual, `ctl.execution_audit`, `ref.expansion_lab_label` e releases
`EXPANSION_LABELS` têm zero linhas, mas isso não dispensa plano de recuperação
para um futuro alvo com dados. A V105 requer autorização própria para alvo,
bytes, impacto e recuperação; só depois cabem preflight, `flyway:migrate` uma
vez, readback e `flyway:validate` em gate separado. Nenhum DDL, reset,
`CREATE USER`, produção, remoto ou fonte real está autorizado por este plano.
