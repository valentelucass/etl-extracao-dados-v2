# Checkpoint 0332 — P08/V105: IT case-only selecionada PASS, rollback sem delta

## Autoridade e escopo

- Supervisor, após os handoffs Runtime 0330 e Banco [0331](0331-p08-v105-uuid-bin2-sonda-readonly.md), autorizou uma única execução física de `ExpansionLaboratoryReferencesIT#caseOnlyLabelReplayUsesContentComparisonAndRollsBack` no `localhost/ETL_SISTEMA_V2_SHADOW`. Banco não editou IT, código, POM, migration ou schema.
- Pins conferidos antes e depois: IT SHA-256 `00CC2578A21120DEBF4EB05AB69185F3D646F62AE5D11F599943CB1C7906353E`; V105 `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17`. Recibos prévios de 063 PASS `6CE5EF46A0AA267F9AC7085E2FC3BF73D943E619643F97626D2DCB359AED967E` e `flyway:validate` normal PASS `AEF5660134F3E88605A1300D93B92C9F936E0AEF1E3B250344C00B86022EF603` preservados; não foram reexecutados.
- Ledger físico `target/shadow-local-rebuild-20260928-01/p08-v105-0332-selected-it-ledger.jsonl` SHA-256 `87A48A2DE65A98B9265BE77BB5E865F56F437224F67A51931BBBE70F2F71A196`. Runner privado novo `p08-v105-0332-run-selected-it.ps1` SHA-256 `F4032FCA3BC02158EE72E3069A0C0D575FF6DBF6E84759B94547816DFAE031E6` reutilizou a montagem de seis segmentos já aprovada no self-test 0330, apenas no ambiente do processo; log 0330 não foi sobrescrito.

## Gate físico e resultado

- Preflight reservado, `sqlcmd -S lpc:localhost -E` com master e banco exato: 106 linhas Flyway = SCHEMA+105 SQL, zero falhas, V105 sucesso; contagens globais monitoradas zero; zero consumidores. Serviço SQL em execução, listeners somente `::1` e `127.0.0.1`. Snapshot 064 estável. Master/alvo/contagens/064 saíram 0 com SHA-256, respectivamente, `FC9044AA34788EB0581CC52D7BBEE77032075FBA7E3C6A038C37A52DE3902762`, `CA025A54ECFDABC8853C8892E02BCE86A2DA047DD15680A8E389BFC300BC58D8`, `3C1015B95C285C7926C5A10F6F7C22666DB6FECC4D28E4338B544C57066B1BC6`, `EF6C39570B7E6D4B1C881B205E07A8EF92BFF4001E9B783AF1AA86D1B8DED1E4`.
- Reserva própria antecedeu **uma** chamada Maven offline com JDK17, perfil `shadow-local-integration`, `-Dshadow.local.integration.enabled=true` e `-Dit.test` do método exato. Maven exit 0; Failsafe **1 run, 0 failure, 0 error, 0 skipped**, somente esse método. O teste concluiu seus asserts de 4 releases, 4 receipts, 15 labels, 4 selections/IDs, replay idêntico, rejeição case-only com erro 53437 e `EXP_REF_CONTENT_DIVERGENT`, `XACT_STATE` admissível e sessão nova vazia. O valor específico de `XACT_STATE` não foi emitido; a conclusão é a aprovação dos asserts do método.
- Log privado `p08-v105-0332-selected-it.private.log` SHA-256 `D119D2606CDA2433598F5038874B0DAF947093748716AA40225FD8787CA4327F`. Reports TXT/XML/summary arquivados com SHA-256 `DC7660E128F23A053750D93C6482DAFD9C71C81B98621E987A3C02073D509FC7`, `F84E537D015DEDD219379ACD1EC5ABE68FA8FB48507BB2C88EFA6C4A67FA946E`, `300C3BD846F17C9C6604624B5E9D6703484B73DAF9E5FA72BC17D31B371CA87D`. Nenhum contém URL literal.
- Readback externo independente, reservado após a IT, repetiu **exatamente** os quatro SHA do preflight, histórico 106/105/zero falhas, contagens globais zero, serviço/listeners loopback e zero consumidores. Nenhum delta persistido. Não houve retry, SQL replay adicional, outra IT, smoke, restore ou DDL.

## Limites e retomada

1. Supervisor integra este PASS **somente** do método selecionado; P08 permanece aberto, sem aceite integral ou prontidão produtiva.
2. Qualquer próximo gate físico exige autorização, preflight e reserva próprios. Runtime mantém a edição exclusiva da IT.
3. Preservar FAILs 0329/0330, sonda 0331 e limites do backup 0325: ausência prévia/tamanho/SHA físico antes do `COPY_ONLY INIT` e restauração não foram provados.
