# Checkpoint 0307 — LOC-04 causal fechada; físico 12.8.1 suspenso

## Autoridade e estado

- 28/09/2026; anterior [0306](0306-p29-p11-sast-sbom-baseline-abertos.md), SHA-256 `603BB0043B942ECEBA551F0043EF5A616DEE27E89D8A30CD45C6918C9697FF66`.
- Critérios: P29/V2-015c/V2-039b requer SAST/licenças/RC/SOs da mesma revisão; P11/V2-015d requer feed/baseline aceitos; P08 físico requer pacote e JDBC/rollback. `STATES.md` é canônico; [mapa P01–P33](../qualificacao-p07-p33/mapa-p01-p33-20260927.md) é índice.
- Segurança/Supervisor suspendeu **toda execução física 12.8.1/JDBC** por CVE-2025-59250 até decisão explícita do usuário para mudar `AGENTS.md`. O UAC continua aguardando sinal do operador. AGENTS, POM, locks, ZIPs e 0304/0305 não foram alterados para 12.8.2.
- `main...origin/main`, deltas preexistentes preservados; sem SQL, DLL carregada, UAC, serviço, DDL, máquina antiga, fonte real, push, merge, deploy ou cutover.

## Prova da unidade

| Parcela | Camada e resultado | Evidência |
| --- | --- | --- |
| LOC-04/REDOS | O teste novo de decimal textual com 8.193 zeros falhou 1/11 antes: valor era aceito como tipado. O parser agora aplica teto compartilhado de 8.192 antes de regex/`BigDecimal`; entrada vira quarentena e preserva bruto. Teste focado depois 11/11 PASS. ADR 0028 atualizada. | `p29-redos-focused-red-v81b-private.log`, `p29-redos-focused-green-v81-private.log`; código e teste 8656 |
| Gate Java final | Espelho v83: `clean verify` offline JDK17 exit0, 2.352 Surefire em 280 XML/cinco skips, seis ITs offline, formatter, Checkstyle e JaCoCo 80/60. RED v81: PATH sem `pwsh` para teste; RED v82: `JAVA_TOOL_OPTIONS` herdada contaminou JSON de duas ITs. Ambos preservados, sem alteração de produto para corrigi-los. | `target/ci-p10-20260927-01/clean-verify-v81/v82/v83-private.log` |
| PMD | 656 fontes, 36 achados brutos iguais ao v78 por tipo/classe/linha/método; disposição local 36/36 `PASS_LOCAL_REVIEWED_FINDINGS` contra 280 XML/2.358 casos. Primeiro invocador de disposição recusou caminho incorreto; v83b passou. Sem aceite nominal. | `target/ci-p10-20260927-01/pmd-v83/`, `pmd-disposition-v83/` e `pmd-disposition-v83b/` |
| SpotBugs/FindSecBugs | v84 em 656 fontes/1.135 classes-recursos idênticos ao v83: 1.091 classes, zero erro/classe ausente, 273 brutos/88 SECURITY. Tipo+classe não mudou; o REDOS decimal ainda consta no XML apesar do teto executado. XML SHA-256 `A630C04A46F93B1F97FFA716FA5DF293F039125E27DAC32D875570490BCE0F0A`. | `target/ci-p10-20260927-01/p29-sast-mirror-v84/target/spotbugsXml.xml`; [triagem técnica](../../catalogos/p29-sast-preparacao-20260928/RELATORIO.md) |
| Par 12.8.2 proposto | Lista exata de AGENTS, dois perfis, novo lock/README, scripts/guardas, hashes do par, provas offline e rollback, sem aplicar o pin. Lock 12.8.1 fica histórico. | [Proposta](../../catalogos/p29-sast-preparacao-20260928/PROPOSTA-SHADOW-12.8.2.md); [runbook 12.8.1 marcado suspenso](../../runbooks/p08-shadow-12.8.1-preflight-20260928.md) |
| Triagem SQL/hash | 16 `SQL_INJECTION_JDBC` + seis `SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING` resultam em 19 sinks distintos após três sobreposições. A inspeção encontrou seletores fechados/literais/recurso SQL fixo e valores vinculados, sem concatenação de texto de entrada observada. Nove `UNSAFE_HASH_EQUALS` são comparações vistas de SHA/fingerprint de pacote/recibo/frescor, não de senha/token nos trechos examinados. Nenhum dos 31 alertas foi aceito nominalmente. | XML v84; [relatório com agrupamento e limites](../../catalogos/p29-sast-preparacao-20260928/RELATORIO.md) |
| Guardas finais | Espelho v85 de 4.038 arquivos, Gitleaks zero; scanner offline 4.038 candidatos/4.037 textos/um binário, zero achados; trilha 33/48/9, UTF-8 estrito nove docs e `git diff --check` PASS. Fonte/teste/POM continuam iguais aos bytes v83. | `target/ci-p10-20260927-01/gitleaks-p29-v85-private.log`, `offline-secret-scan-p29-v85-private.log` |

O scan v84 ainda tem 273 alertas brutos. Foram examinados tecnicamente 42
achados distintos: seis REDOS, dois avisos de jitter `PREDICTABLE_RANDOM`,
um `COMMAND_INJECTION`, cinco rank 7–8, 19 sinks SQL e nove hashes.
Nenhuma classificação
nominal, exceção, supressão, política ou aceite de Segurança foi inferida.
P11 continua sem relatório de feed atual; P29 continua sem RC/aceite/SOs e
P08 físico sem execução.

`graphify update .` pelo atalho `~/.local/bin` e duas variantes retornaram
0 sem output nem atualização (mtime do grafo anterior à LOC-04). O executável
do ambiente isolado foi chamado diretamente com `update . --no-cluster` e
reconstruiu AST 789/789: 37.230 nós/95.228 arestas, `graph.json` atualizado.
Recibo `target/ci-p10-20260927-01/graphify-update-p29-v84-private.log`.
O no-op do atalho fica registrado; grafo novo só é atribuído à execução direta.

## Próximas ações, até três

1. Segurança revisa os 273 achados v84 e a cobertura SAST/SCA/licenças, fornece feed/baseline P11 e aceite/exceções com owner/prazo para revisão exata. Não suprimir por contagem ou por esta triagem.
2. Usuário decide explicitamente se `AGENTS.md` pode fixar JAR/DLL 12.8.2 para sombra local. Só então aplicar [delta](../../catalogos/p29-sast-preparacao-20260928/PROPOSTA-SHADOW-12.8.2.md), novos A/B, gates e relatório P11; nada físico antes de sinal UAC do operador e preflight do alvo exato.
3. Release owner fixa versão única, RC/configuração, SOs suportados e mesmo SHA/checks; continuar frentes P09–P33 independentes do banco conforme [mapa](../qualificacao-p07-p33/mapa-p01-p33-20260927.md).

Não há checkbox integral novo P08/P11/P29/G02 nesta unidade. Preservar todos
os REDs e manifests históricos; nenhum teste offline substitui JDBC ou aceite.
