# Checkpoint 0331 — P08/V105: sonda UUID/BIN2 somente leitura

## Autoridade e limite

- Supervisor autorizou, após [0330](0330-p08-it-v105-selftest-pass-release-assert-fail.md), apenas diagnóstico sintético de `ref.reference_release.scope_code` no `localhost/ETL_SISTEMA_V2_SHADOW`. Nenhuma IT, JDBC, Flyway, replay, DDL, DML ou linha de domínio.
- Banco executou somente `sqlcmd -S lpc:localhost -E` com banco explícito e autenticação Windows. Runtime é o único editor da IT; Banco não a editou. FAIL 0330 preservado. P08 segue aberto.
- Ledger privado `target/shadow-local-rebuild-20260928-01/p08-v105-0331-scope-case-ledger.jsonl`, SHA-256 `49EAF3F444E0760032CAC452D1A240A862C060C5AB641AF569B434602235A704`. Script SELECT-only `p08-v105-0331-scope-case-probe.sql`, SHA-256 `9CEDC5A0D81618B064A9328143DE3135F6F4A791A2DD77C683C23B2308DF03EB`.

## Preflight, sonda e readback

- Reserva antes do preflight. Master, alvo e contagens saíram 0 e repetiram os hashes 0330: `FC9044AA34788EB0581CC52D7BBEE77032075FBA7E3C6A038C37A52DE3902762`, `CA025A54ECFDABC8853C8892E02BCE86A2DA047DD15680A8E389BFC300BC58D8`, `3C1015B95C285C7926C5A10F6F7C22666DB6FECC4D28E4338B544C57066B1BC6`. Histórico 106 = SCHEMA+105 SQL, zero falhas; contagens globais monitoradas zero; zero consumidores. Serviço PID 20404, listeners apenas `::1` e `127.0.0.1`.
- O verificador local marcou um FAIL falso porque comparou os listeners em ordem fixa incorreta. Reclassificou **os mesmos recibos**, com comparação por conjunto; nenhum SQL de preflight foi repetido. O FAIL e a causa constam no ledger.
- Uma consulta sintética gerou `uniqueidentifier` na sessão e retornou somente metadados e flags. Exit 0; output privado SHA-256 `D6C22FAA1236B36E1AD724BED6A63A5FED82FF67620DBB7EBEAACA55AC57231C`. Nenhum UUID/ID foi impresso. `scope_code`: `nvarchar`, 256 bytes (`nvarchar(128)`), `Latin1_General_100_BIN2`.

| Comparação `Latin1_General_100_BIN2` | Igual? |
| --- | --- |
| Texto nativo de `CONVERT(NVARCHAR(36), uniqueidentifier)` × lowercase | 0 |
| Texto nativo × `CONVERT(NVARCHAR(36), CONVERT(UNIQUEIDENTIFIER, lowercase))` | 1 |
| Lowercase × texto reconvertido | 0 |

- O UUID sintético tinha letras hexadecimais maiúsculas no texto nativo e nenhuma minúscula. Isso distingue as formas neste ensaio. A collation BIN2 diferencia a caixa; converter o texto lowercase a `uniqueidentifier` e renderizar novamente reproduz o texto nativo. O mecanismo explica por que a consulta textual lowercase da IT do FAIL 0330 não encontrou `scope_code` montado no SQL. A sonda não observou o UUID específico da IT nem a executou novamente.
- Readback independente reservado após a sonda: master/alvo/contagens saíram 0 com **os mesmos três hashes** do preflight; serviço/listeners iguais. Nenhum delta persistido. Nenhum retry/fallback.

## Próximas ações

1. Runtime concluir e testar offline a correção da IT sob sua edição exclusiva; Supervisor revisar diff e evidência.
2. Qualquer nova IT física exige autoridade própria, preflight e reserva novos pelo Banco.
3. Preservar FAILs 0329/0330, limites do backup 0325 e P08 aberto; nenhuma promoção de aceite integral, smoke ou produção.
