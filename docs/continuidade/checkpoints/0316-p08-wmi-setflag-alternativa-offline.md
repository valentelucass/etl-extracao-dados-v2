# Checkpoint 0316 — alternativa WMI tipada preparada offline

## Identificação, autoridade e critério

- 28/09/2026, 23:28 UTC. Anterior [0315](0315-uac-v2-wmi-provider-not-capable-sem-efeito.md), SHA-256 `DF7F20AB19538718842A94FDB08E303937F521CF18FDCF6E82C2215E2D85CB60`.
- O usuário pediu investigação somente leitura/offline do `0x80041024`, alternativa mínima para TCP SQL exclusivamente em loopback, guardas, rollback e prova de não ampliar rede. Proibiu novo UAC, escrita WMI/registro, restart, SQL DDL/JDBC/migration até revisão e novo sinal para efeito.
- Critério desta unidade: separar retorno comprovado do provedor de causa inferida e preparar uma sequência verificável sem executar mutação. `STATES.md` é canônico.

## Evidência observada

| Camada | Observado |
| --- | --- |
| Attempt v2 preservado | Primeiro `SetNumericalValue(ListenOnAllIPs,0)` retornou `2147749924` = `0x80041024` (`WBEM_E_PROVIDER_NOT_CAPABLE`); readback comprovou nenhum efeito. Ledger `pin-1282-uac-v2-ledger.jsonl` SHA-256 `8166F314DE9816FAA4F0DD7DA547679F3F5D0C52812E42BC1145B42BC6600D61`, intacto. |
| Classe WMI SQL 17, leitura | `ServerNetworkProtocolProperty` expõe `SetFlag(BoolValue:Boolean)`, `SetNumericalValue(NumValue:UInt32)`, `SetStringValue(StrValue:String)`; `ListenOnAllIPs`/`Enabled` têm `PropertyType=0`, valor 0/1; `KeepAlive` tem tipo 1/valor 30000. `ServerNetworkProtocolIPAddress` expõe `SetEnable()` e `SetDisable()`; `ServerNetworkProtocol` expõe `SetEnable()`. |
| Estado de rede, leitura | 24 entradas com endereço, IP19=`::1` e IP20=`127.0.0.1` únicas, ambas Active=1/Enabled=0/porta1433/dinâmica vazia. Outras 22 Enabled=0. TCP/NP off. `IPAll` possui porta, mas a [documentação de Listen All](https://learn.microsoft.com/en-us/sql/tools/configuration-manager/tcp-ip-properties-protocols-tab?view=sql-server-ver17) especifica configuração por IP quando `Listen All=No`. |
| Documentação oficial | [SetFlag](https://learn.microsoft.com/en-us/sql/relational-databases/wmi-provider-configuration-classes/servernetworkprotocolproperty-class/setflag-method-servernetworkprotocolproperty-class?view=sql-server-ver17), [SetEnable por IP](https://learn.microsoft.com/en-us/sql/relational-databases/wmi-provider-configuration-classes/servernetworkprotocolipaddress-class/setenable-method-servernetworkprotocolipaddress-class?view=sql-server-ver17) e [SetEnable do protocolo](https://learn.microsoft.com/en-us/sql/relational-databases/wmi-provider-configuration-classes/servernetworkprotocol-class/setenable-method-servernetworkprotocol-class?view=sql-server-ver17) são métodos expostos pela instância e descritos pela Microsoft. |

Conclusão comprovada: o setter numérico não conseguiu modificar
`ListenOnAllIPs` nesta tentativa. A incompatibilidade do setter com uma opção
booleana é **inferência técnica forte**, não causa interna provada do retorno;
`SetFlag(false)` e `SetEnable()` dos dois IPs são candidatos documentados,
**ainda não testados nesta máquina**. Nenhum efeito ocorreu nesta unidade.

## Proposta pronta para revisão

[Runbook da alternativa](../../runbooks/p08-loopback-wmi-flag-proposta-20260928.md),
SHA-256 `260FAC51DC77A610DAE43FD7A9E037843ACA4269555441CEFAC6967D0704582B`.
Seu passo exato exige preflight atual e reserva distinta; faz
`SetFlag(false)` em ListenAll, `SetEnable()` apenas em IP19/IP20, verifica
todos os outros IPs desligados, habilita TCP por último e reinicia uma vez.
Readback exige listeners do PID apenas `::1:1433` e `127.0.0.1:1433`, sem
wildcard/remoto/porta extra. Em falha, parar e reconciliar; rollback é plano
separado, nunca automático. Proposta estática não é prova física de isolamento.
Recibo privado `target/shadow-local-rebuild-20260928-01/pin-1282-wmi-method-analysis-0316.json`,
SHA-256 `5D57AD79B277A236D34626EF04D4AE37CAD1ECB05A9989FFAFF46DFEDE55CCB5`.

## Retomada imediata

1. Obter revisão da alternativa tipada; se aprovada e houver novo sinal para efeito, fazer preflight fresco e reserva de attempt distinto. Não reutilizar helper v2 nem repetir o método numérico.
2. Em eventual execução, interromper no primeiro retorno diferente de zero/drift; exigir prova de sockets exclusivamente loopback antes de JDBC/Flyway.
3. P07/P08 e P01–P33 integrais, release, remoto/produção e cutover seguem abertos. Nenhum UAC, escrita, restart, SQL DDL, JDBC ou migration ocorreu nesta unidade.
