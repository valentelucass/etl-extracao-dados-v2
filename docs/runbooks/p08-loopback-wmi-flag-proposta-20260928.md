# P08 — proposta revisável para TCP SQL somente em loopback

Estado: **PROPOSTO_NAO_EXECUTADO**. Nenhum comando de mutação deste roteiro foi
executado. O attempt v2 terminou sem efeito em `0x80041024`; não o repetir.
A instrução atual proíbe novo UAC, escrita WMI/registro, restart, JDBC e
migrations até revisão e novo sinal para efeito.

## Fato, inferência e limite

- Fato executado: o helper elevado v2 chamou primeiro
  `SetNumericalValue(NumValue=0)` na propriedade `ListenOnAllIPs` e recebeu
  `2147749924` (`0x80041024`, `WBEM_E_PROVIDER_NOT_CAPABLE`). O mesmo PID,
  TCP desligado, `ListenOnAllIPs=1`, zero IPs habilitados/listeners e alvo vazio
  foram confirmados depois. Ledger privado
  `target/shadow-local-rebuild-20260928-01/pin-1282-uac-v2-ledger.jsonl`.
- Fato somente leitura: `ServerNetworkProtocolProperty` expõe
  `SetFlag(BoolValue:Boolean)` e `SetNumericalValue(NumValue:UInt32)`.
  `ListenOnAllIPs` e `Enabled` têm `PropertyType=0`, `PropertyValType=4` e
  valores 0/1; `KeepAlive` tem `PropertyType=1` e valor numérico 30000.
  `ServerNetworkProtocolIPAddress` expõe `SetEnable()`/`SetDisable()`.
  O namespace é `root\Microsoft\SqlServer\ComputerManagement17`.
- Inferência técnica, **não prova de sucesso futuro**: a chamada numérica
  escolheu o método incompatível com uma flag booleana. O método documentado
  para flags é `SetFlag(Boolean)`; habilitar IPs pela classe própria é mais
  direto que alterar sua propriedade `Enabled` numericamente. O retorno do
  provedor demonstra apenas que a chamada anterior não foi executável.
  Ainda não sabemos se `SetFlag`/`SetEnable` serão aceitos nesta instalação.

Referências oficiais: [SetFlag](https://learn.microsoft.com/en-us/sql/relational-databases/wmi-provider-configuration-classes/servernetworkprotocolproperty-class/setflag-method-servernetworkprotocolproperty-class?view=sql-server-ver17),
[SetEnable de IP](https://learn.microsoft.com/en-us/sql/relational-databases/wmi-provider-configuration-classes/servernetworkprotocolipaddress-class/setenable-method-servernetworkprotocolipaddress-class?view=sql-server-ver17),
[SetEnable de TCP](https://learn.microsoft.com/en-us/sql/relational-databases/wmi-provider-configuration-classes/servernetworkprotocol-class/setenable-method-servernetworkprotocol-class?view=sql-server-ver17),
[Listen All](https://learn.microsoft.com/en-us/sql/tools/configuration-manager/tcp-ip-properties-protocols-tab?view=sql-server-ver17),
[configuração por IP](https://learn.microsoft.com/en-us/sql/database-engine/configure-windows/configure-the-database-engine-to-listen-on-multiple-tcp-ports?view=sql-server-ver17),
[código WMI](https://learn.microsoft.com/en-us/windows/win32/api/wbemdisp/ne-wbemdisp-wbemerrorenum).

## Inventário local lido, sem mudança

Na instância padrão `MSSQLSERVER` da máquina `LUCAS`, TCP e Named Pipes estão
desligados, `ListenOnAllIPs=1`. Há 24 entradas com endereço: `IP19=::1`,
`IP20=127.0.0.1`, ambas `Active=1`, `Enabled=0`, porta estática 1433 e porta
dinâmica vazia; as outras 22 têm `Enabled=0`. A classe
`ServerNetworkProtocolIPAddress` tem uma instância exata para cada `IP19` e
`IP20`, ambas desabilitadas. `IPAll` tem porta configurada, porém a opção
`Listen All=No` faz prevalecer as configurações por IP segundo a Microsoft.
Nenhum listener SQL/1433 existe agora. As consultas read-only `master`/alvo
confirmaram banco `ETL_SISTEMA_V2_SHADOW` vazio após o attempt v2.

## Sequência candidata após revisão e novo sinal do operador

1. Reservar **attempt distinto** com helper/launcher/queries novos e hashes,
   um UAC no máximo, um restart no máximo. Preflight read-only imediatamente
   antes do efeito: identidade local/instância padrão; serviço Running/Manual,
   PID; TCP/NP desligados; `ListenOnAllIPs=1`; conjunto de entradas com endereço
   congelado e somente `IP19=::1`/`IP20=127.0.0.1` reconhecidas como loopback;
   22 outras `Enabled=0`; loopbacks `Active=1`, `Enabled=0`, `TcpPort=1433`,
   `TcpDynamicPorts` vazio; porta 1433 livre e nenhum listener do PID. Conferir
   `lpc:localhost/master`, alvo exato vazio e saídas próprias. Drift interrompe.
2. No helper elevado, repetir esses guardas **antes** da primeira escrita.
   Resolver uma única instância de `ServerNetworkProtocolProperty` para
   `ListenOnAllIPs` e uma instância de `ServerNetworkProtocolIPAddress` para
   cada `IP19` e `IP20`. Não aceitar outro endereço/label, nem alterar `IPAll`,
   porta, Named Pipes, firewall ou outro protocolo.
3. Chamar somente
   `Invoke-CimMethod -InputObject $listenAll -MethodName SetFlag -Arguments @{ BoolValue = $false }`.
   Exigir `ReturnValue=0`, reler `ListenOnAllIPs=0` em WMI e registro. Se falhar,
   parar sem tentar outro setter.
4. Chamar `SetEnable()` na instância `ServerNetworkProtocolIPAddress` de
   `IP19`, reler `Enabled=true`; depois em `IP20`, reler `Enabled=true`.
   Após cada chamada exigir `ReturnValue=0` e todas as outras entradas ainda
   desligadas. TCP de protocolo permanece desligado nessa fase.
5. Revalidar máquina, PID, `ListenOnAllIPs=0`, exatamente os dois loopbacks
   habilitados, demais desabilitados, porta/dinâmica invariantes e ausência de
   listeners. Chamar `SetEnable()` na instância `ServerNetworkProtocol` TCP,
   exigir retorno zero e readback TCP=true/NP=false. Repetir guarda completo.
6. Reiniciar `MSSQLSERVER` **uma vez**. Exigir Running/Manual, PID novo e
   exatamente dois listeners de seu PID, `::1:1433` e `127.0.0.1:1433`;
   recusar `0.0.0.0`, `::`, qualquer endereço não loopback, porta extra ou
   listener SQL adicional. Conferir ainda registro/WMI e preflight read-only
   `master`/alvo vazio. Só essa prova comprovará transporte restrito local.

Esta ordem mantém TCP de protocolo desligado até `Listen All=No`, ambos os
loopbacks habilitados e as outras 22 entradas verificadas desligadas. Não há
abertura de firewall. A [semântica de Listen All](https://learn.microsoft.com/en-us/sql/tools/configuration-manager/tcp-ip-properties-protocols-tab?view=sql-server-ver17)
e o readback de sockets são necessários juntos; o plano estático por si só
**não comprova** ausência de exposição após restart.

## Recuperação e rollback

Qualquer retorno diferente de zero, resposta incerta, divergência de conjunto
de IPs, recibo ausente, listener extra ou falha de serviço: **parar sem retry,
restart adicional, limpeza ou rollback automático**. Reconciliar somente em
leitura WMI, registro, serviço/PID, sockets e `master`/alvo; registrar cada
alteração parcial. O estado inicial a restaurar, se um rollback vier a ser
explicitamente autorizado, é TCP disabled, `ListenOnAllIPs=1`, IP19/IP20
disabled, demais entradas invariantes, NP disabled e zero listeners. Para
evitar exposição, um rollback futuro deve desligar TCP antes de restaurar
`ListenOnAllIPs=1`; reiniciar para fechar listeners só após autorização e
readback, sem mudança no banco. Nenhuma etapa de rollback está autorizada ou
executada por esta proposta.
