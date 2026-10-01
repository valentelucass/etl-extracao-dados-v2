# Checkpoint 0315 — UAC v2 iniciou, WMI recusou a primeira configuração

## Identificação e autoridade

- 28/09/2026, 23:21 UTC. Anterior [0314](0314-diagnostico-uac-cancelamento-windows-sem-retry.md), SHA-256 `CDF261AF35297FD14FAAB898E687D1690E27277D9A62EFF7A8C66ABC3D8AC812`.
- O operador corrigiu: **não cancelou** o UAC anterior. Autorizou expressamente uma nova tentativa legítima, limitada a SQL TCP em `::1`/`127.0.0.1`; falha ou incerteza exige parada sem retry. JDBC/Flyway/migrations/validações só poderiam seguir com loopback confirmado. Nenhum aceite P01–P33 é inferido.
- Escopo executado: investigação offline/read-only; preflight de serviço/WMI/socket e `lpc:localhost/master`/alvo exato; uma elevação reservada; reconciliação somente leitura. Nenhuma operação remota/produtiva/fonte.

## Diagnóstico da tentativa 0313

O evento PowerShell 4100 preserva a mensagem literal retornada pelo Windows,
sem atribuição pessoal. Não há evento correlato na janela nos canais UAC,
Winlogon, UserConsentVerifier, Shell-Core, Application ou System que prove
apresentação do prompt ou resposta. Contexto atual: sessão console 3 com
Explorer, token médio com Administradores em negação, UAC ativo,
`PromptOnSecureDesktop=1`, `ConsentPromptBehaviorAdmin=5`, AppInfo Running.
Hipótese delimitada: apresentação do desktop seguro pode ter falhado ou não
ter sido visível ao operador; não há evidência suficiente para apontar causa
específica. Nenhuma política foi alterada.

## Attempt distinto e evidência

| Gate | Resultado observado |
| --- | --- |
| WMI/serviço/socket read-only | `MSSQLSERVER` Running/Manual PID 2116; TCP/NP false; ListenAll=1; loopbacks desabilitados; zero listeners SQL/1433. |
| SQL read-only | `master` confirmou host/instância/alvo exatos, 17.0.1000.7 e dois arquivos. Alvo `ETL_SISTEMA_V2_SHADOW` tinha zero tabelas/views/procedures de usuário. |
| Reserva | `LOOPBACK_UAC_V2_RESERVED` no ledger privado; helper v2 SHA-256 `7D1CEA3DC022D6367CA4D0C1E4CDC69E782791DF7B53AC100604614DAD8DC1F6`, launcher SHA-256 `8EC5A91CEE7FA58E4394A0A3BB0F3F9C8C21624FAE603DE20DAFF802D91ED7D7`. Helper original e ledger 0313 intactos. |
| Invocação | Chamada direta do `.ps1` recusada pela política de scripts antes de `Start-Process`/UAC, sem recibos. Invocação com `-ExecutionPolicy Bypass` apenas no processo novo abriu **uma** elevação, janela normal; `Start-Process` retornou processo iniciado, helper saiu 1. Recibo de entrada do helper registra sessão 3. |
| Falha causal | Primeiro `SetNumericalValue(ListenOnAllIPs,0)` retornou `2147749924` = `0x80041024` = `WBEM_E_PROVIDER_NOT_CAPABLE`. Classe e método WMI existem; `MSSQL_ManagementProvider` iniciou com resultado 0. O motivo interno da recusa não foi demonstrado. Nenhuma segunda escrita WMI ou restart. |
| Reconciliação | Mesmo PID 2116, Running/Manual, TCP/NP false, ListenAll=1, zero IPs habilitados e listeners; registro TCP Enabled=0/ListenOnAllIPs=1. `master` e alvo passaram novamente, zero objetos de usuário. **Nenhum efeito observado.** |

O código `0x80041024` é definido pela Microsoft como provedor incapaz de
realizar a operação; `SetNumericalValue` documenta retorno zero como sucesso.
Referências: [WMI](https://learn.microsoft.com/en-us/windows/win32/api/wbemdisp/ne-wbemdisp-wbemerrorenum),
[método SQL Server](https://learn.microsoft.com/en-us/sql/relational-databases/wmi-provider-configuration-classes/servernetworkprotocolproperty-class/setnumericalvalue-method-servernetworkprotocolproperty-class?view=sql-server-ver17).
Ledger privado `target/shadow-local-rebuild-20260928-01/pin-1282-uac-v2-ledger.jsonl`, SHA-256 `8166F314DE9816FAA4F0DD7DA547679F3F5D0C52812E42BC1145B42BC6600D61`;
recibos v2 e outputs SQL estão no mesmo diretório. Nenhum JDBC, DLL carregada,
Flyway, migration, IT, validação física ou checkbox P01–P33 nesta unidade.

## Retomada imediata

1. Investigar somente em leitura por que o provedor SQL WMI não aceita `ListenOnAllIPs`; comparar com o método documentado do Configuration Manager. Não repetir `SetNumericalValue` como sonda.
2. Antes de outro efeito, apresentar uma correção causal verificável, novo preflight e reserva; obter novo sinal do operador para outro UAC. A tentativa v2 está encerrada sem retry.
3. Só depois de loopback exclusivo comprovado retomar gates JDBC/Flyway/schema/IT separados, com readbacks e parada em deriva. P07/P08 e P01–P33 integrais permanecem abertos.
