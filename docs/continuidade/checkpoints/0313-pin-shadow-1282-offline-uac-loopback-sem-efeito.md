# Checkpoint 0313 — shadow 12.8.2 offline; UAC loopback sem efeito

## Identificação e autoridade

- 28/09/2026, 22:10 UTC. Anterior [0312](0312-p29-http-uri-v102-offline.md), SHA-256 `E0E9154C2F5FF25E7123609983DC7FEDA6125500FBEEC83D9A4FF284DEB6180C`.
- Instrução efetiva: usuário autorizou expressamente trocar pin shadow JAR/DLL 12.8.1→12.8.2 e avançar migrations e provas físicas somente em `localhost/ETL_SISTEMA_V2_SHADOW`, com gates separados, readback, rollback sintético e transporte exclusivamente loopback. UAC cancelado/incerto exige parada sem retry. P09+ externos, produção, remoto, fonte real e cutover fora do escopo.
- `STATES.md` é canônico. Nenhum checkbox P01–P33 foi promovido. O único Builder alterou arquivos; árvore suja preexistente e 12.8.1 históricos foram preservados.

## Delta e gates offline

| Passo | Resultado observado | Recibo privado |
| --- | --- | --- |
| Pin, lock e perfil | `AGENTS.md`, dois perfis shadow e guardas ativos em 12.8.2; nove entradas no lock, sete não JDBC idênticas, duas JDBC iguais ao lock normal; 18 hashes artefato/POM e nove origens conferidos; POMs efetivos IT/migration/normal em 12.8.2. | `target/shadow-local-rebuild-20260928-01/pin-1282-ledger.jsonl`, `pin-1282-effective-pom-*.xml` |
| PackageShadow A/B | Ambos exit 0, `SHADOW_LOCAL_12_8_2`, 186 membros/nove dependências/2.159 inputs, revisão `8617f00768d0ef1f75eb7fb2ca7dd75406eb54fe0cc5ed3c58b83fadef70d97f`, manifesto `ea623a77fd342d676414e80671daa4a0e5cbb22d7defe976f8d917ad23576918`, ZIP A=B SHA-256 `C7BDFA580F6EF206B125D73064A29F2E080A5C9B38E0F4F3E321D6A6149F5DFB`. DLL apenas bytes passivos. | `target/macrobloco-qualificacao-pacote-20260928-02/` |
| Extração/puro/guardas | A/B extraídos com envelope verificado; `config-validate`, `dry-run`, `inspect`, `plan` 8/8; guardas 21/21, exit 2/código esperado/nenhum controle. Pacote ainda `PACKAGED_NOT_SMOKE_QUALIFIED`. | `p08-shadow-1282-extract-{a,b}`, `pure-*.log`, `p08-shadow-1282-guards-a/result.json` no round acima |
| Build JDK17 | Primeiro `clean verify` falhou: `JAVA_TOOL_OPTIONS` do invocador entrou no JSON de dois subprocessos IT, e JaCoCo `bootstrap` caiu a 0,78. Attempt/log preservados. Em espelho novo v2 sem essa variável, `clean verify` passou 2.360 Surefire/zero falha ou erro/cinco skips, seis ITs offline/zero erro e JaCoCo. Não foi uma IT física. | `pin-1282-clean-verify{,-v2}.stdout.log`, `pin-1282-verify{,-v2}-result.json` |
| Análise local | PMD 656 fontes/36 achados; catálogo ativo repinado apenas nos hashes/linhas alterados pelo P29, revisão local 36/36 PASS. SAST SpotBugs/FindSecBugs 272 brutos/87 SECURITY/46 Unicode, multiconjunto tipo+classe igual v102; XML SHA-256 `CD2A20624F9057CA6391320469DDCB59145B3D00D5AC26C72D978D02DA7A7CB2`. Scan de segredos 4.047 candidatos/zero achado, trilha 33/48/9, dois validadores estáticos de schema PASS. Sem aceite nominal de Segurança. | `pin-1282-pmd-*`, `pin-1282-sast-*`, logs do round |

O primeiro invocador `mvnw.cmd` para POM efetivo teve duas recusas de parsing de argumento antes do build; a chamada Java direta ao wrapper passou. A primeira disposição PMD recebeu caminho inválido; a segunda recebeu array de caminhos mal interpretado pela CLI; a terceira apontou hashes/linhas antigos dos dois arquivos P29 e a quarta passou após repin causal. Todos os attempts permanecem. Cache local do feed OWASP/NVD ausente: P11/SCA atual e aceites de Segurança/release continuam externos e abertos.

## Preflight SQL e parada UAC

- `MSSQLSERVER` Running/Manual, Windows auth e Shared Memory. Preflight `master` inicial recusou a condição conjunta porque `AUTO_CLOSE=ON` pode apresentar `sys.databases.collation_name=NULL` com banco fechado. Reconciliação read-only confirmou `ONLINE`, compatibilidade 170, dois arquivos e collation `Latin1_General_100_CI_AS_SC` no próprio alvo. Preflight v2 separou verificações em `master` e alvo, ambos exit 0: zero tabelas/views/procedures de usuário, sem schema V2/Flyway.
- A configuração candidata, baseada no WMI SQL Server 17, alteraria `ListenOnAllIPs` para 0, habilitaria somente `::1` e `127.0.0.1` e reiniciaria uma vez o serviço; Named Pipes permaneceriam off. O helper foi reservado e teve SHA-256 `6259DF3F9574C0FF8F2360B2585D9934FF5687FF395F9AA6E519FA3D5A82EC89`. A única chamada `Start-Process -Verb RunAs` retornou `InvalidOperationException` após aguardar, sem recibo do helper. Classificação: UAC cancelado/indisponível ou resposta incerta, **sem retry**.
- Readback após a chamada: mesmo PID 2116, serviço Running/Manual, TCP=false, NP=false, `ListenOnAllIPs=1`, zero IPs habilitados, zero listeners SQL. `master` e alvo passaram novamente, banco vazio. Nenhum efeito de configuração, restart, migration, Flyway, JDBC, DLL carregada, validação física, domínio, produção ou remoto foi observado. Os outputs SQL e a decisão estão no ledger privado `pin-1282-ledger.jsonl` (SHA-256 `AAF61FF42B79A8C54E75766B0908762E4DE1C8C8AEEFA98E3BD614656B73B654`).

## Retomada imediata

1. Obter novo sinal de prontidão do operador para um UAC legítimo; conferir novamente WMI, serviço, sockets e banco, reservar attempt novo e **não** repetir o helper atual automaticamente.
2. Se loopback exclusivo passar, provar primeiro JDBC/Flyway Windows auth read-only com JAR/DLL 12.8.2 e URL de processo validada; separar `flyway:info`, V001–V104, `validate` e inventário, parando em qualquer drift.
3. Após schema conferido, executar validadores sintéticos/IT rollback-only com contagens antes/depois e depois P07/P08 físicos em controles próprios. P11 feed/SCA e aceites externos seguem abertos.
