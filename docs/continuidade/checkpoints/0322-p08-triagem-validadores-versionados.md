# Checkpoint 0322 — triagem causal dos validadores P08

## Identificação, autoridade e limite

- 29/09/2026 UTC (28/09 local), Builder Banco e Persistência; anterior
  [0321](0321-p08-validadores-auditoria-local.md) SHA-256
  `3B3B58A752FCB09C8563E86970B2EEF661B830D823880BE08B6CE01A19B3BC6D`.
- Ordem atual: triar 003/005/030 contra V001–V104, baseline, manifests e
  critério original, preservando FAIL; propor ou implementar escopo por versão.
  Defeito de schema só por migration V2 nova com autorização/preflight próprios.
- Sem migrations/validadores repetidos, DDL/DML, reset, CREATE USER, remoto,
  produção, fonte real ou aceite P08. SQL somente leitura foi executado apenas
  em `lpc:localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth, com ledger físico.

## Causa comprovada

| Recibo 0321 preservado | Confronto causal | Pendência real |
| --- | --- | --- |
| `003` 11 FAIL | V024 substituiu `ctl.usp_control_plane_register_source` e mudou os literais procurados pelo validador V003; ordem comprimento/trim, lock/clock, persistência `@now` e comparação BIN2 conferidas. Cinco collations são da tabela Flyway. | V025 criou três colunas NVARCHAR de `ctl.execution_audit` sem BIN2, contrariando `database/manifest/control-plane.json`. |
| `005` 505 FAIL | Cabeçalho/inventários são V001–V017; 503 achados são V025 ou posteriores e dois são constraints técnicas Flyway. Baseline atual inclui V001–V104. | Criar inventário fechado V104 derivado de migrations e baseline, sem autoaprovar catálogo implantado. V017 histórico ainda não foi reexecutado. |
| `030` 28 FAIL | Fundação V008 com extensão V010; 27 objetos ref vêm de V042/V055/V056/V065/V071/V080/V084/V090. | V042 deixou `ref.expansion_lab_label.label` e TVP homólogo com collation padrão CI; procedure consome ambos por `EXCEPT`. |

Plano de escopo e V105 em
`docs/runbooks/p08-validadores-versionados-v104-20260928.md` SHA-256
`91F4DBFDA0A82C69E26E9265F45D97F3EBC6E826905DD3830B2B9A1010254E7D`.
Os SQL originais permanecem imutáveis. Proposta: 003 no intervalo V003–V023,
005 exatamente V017, 030 V008–V041, cada faixa ainda sujeita a fixture exata;
novo validador V104 mantém asserts semânticos e collation da aplicação.

## Execução e evidência

- Offline PowerShell 7.6.6: checkers de control plane, progressivo e
  referências passaram na camada estática; prova causal privada rejeitou três
  mutações V024 e reconciliou 505/28. Recibo
  `p08-triage-0322-causal-offline.out` SHA-256
  `48DECB874F4DB0D9F3013637ED5BF50A01F800D938878A2F3DF68D1F6FA561E9`;
  mapa de origens v2 SHA-256
  `5C6141AC98D004D33CFA80B1224B29E1ED40CC1E22C9B5C6CAA5D0F6D8D3DA87`.
  `Test-Bloco60SqlContract.ps1` falhou por `HANDOFF_PATH`/sufixo V024 no
  baseline V104; log SHA-256
  `E05E4D1E52DB00BBAE49B83997726289D274DD81C40C9944D10C4367F917D35A`.
- Catálogo SQL: preflight `master`/alvo exit 0/0, serviço PID 20404 e apenas
  listeners `::1:1433`/`127.0.0.1:1433`; reserva antes da consulta, exit 0,
  readback idêntico. Output SHA-256
  `5F8721831B1BD874415A2486C4389923DB1B822668586B41AC04B619B709D328`.
- Impacto SQL: duas tentativas de preflight recusaram formatação/BOM do
  `sqlcmd` antes da reserva; outputs preservados. Invocador corrigido,
  preflight idêntico a 0321, reserva, única consulta agregada exit 0: auditoria,
  labels e releases de expansão têm zero linhas; há índice/checks de
  auditoria e procedure dependente do TVP. Output SHA-256
  `5A452BF4561CEE660B192305CFD70F2251406451DF001C17529170D53794250D`.
  Snapshot pós-consulta SHA-256
  `78ED7BD652FD28452C861AEC5A36CF3877422AAC8AC1096C2F5A8136FE892B7B`,
  igual a 0321: Flyway 105/104/0, 1.819 objetos, sete principals/schemas,
  247 tabelas, 146 linhas agregadas, auditorias 0/0.
- Ledger novo
  `target/shadow-local-rebuild-20260928-01/p08-triage-0322-ledger.jsonl`
  SHA-256 `5A19AD1720B3BDE5DBDC4387DBFDC50EEA1F32879A5F78CBE6B7C9780F7CB9E4`.
  Logs e FAIL originais de 0321 preservados. `../CONTEXTO_GLOBAL.md` segue
  ausente; nenhuma hipótese histórica substitui estado autoritativo.
- `Test-TrilhaPreparation.ps1` passou em PowerShell 7.6.6: 33 etapas, 48 IDs
  abertos, nove pacotes, sem SQL/rede/Maven; recibo SHA-256
  `B631C3CF43CFD377A3CB36774F1CF30171075154BF66E01D6B783E164225D75C`.
  `git diff --check` e leitura UTF-8 estrita dos cinco documentos passaram.

## Retomada imediata — até três ações

1. Supervisor revisar proposta de validador V104 e V105; para V105, obter
   autoridade exata sobre bytes, alvo, impacto e recuperação antes de qualquer
   `migrate`. A dependência TVP/procedure/JDBC deve ser coordenada com Runtime.
2. Builder Banco preparar prova offline do novo validador com mutações e
   inventário derivado das migrations; preservar 003/005/030 originais.
3. Somente após revisão e autoridade, executar gates físicos necessários no
   shadow com preflight/reserva/readback novos e `flyway:validate` separado.
   P08 permanece aberto até a evidência agregada dos critérios originais.
