# P29/P11 — prontidão técnica de SAST, SBOM e vulnerabilidades

## Lote HTTP/URI v102 — contraprova causal e scanner final

O XML v96 continha nove `IMPROPER_UNICODE` nas três configurações HTTP
`DataExportClientSettings`, `DataExportProperties` e
`GraphQlClientSettings`. Três testes novos verificam a cadeia real: host
`localho\u017Ft` não vira host URI e é recusado pelos três construtores;
`http\u017F` não vira esquema URI; caixa ASCII `HTTP://LOCALHOST` continua
admitida para loopback. A classificação técnica vale só para estas entradas,
sem isentar os demais 37 achados Unicode ou dispensar o aceite de Segurança.

Na varredura causal da mesma borda de metadados HTTP, a resposta sintética
`Content-Type: application/jsonp` era marcada `jsonContentTypeDeclared=true`
pelo prefixo no executor Data Export. A primeira execução do teste parou no
formatter; após corrigir apenas o formato, o RED executou uma asserção e
falhou porque esperava `false` e observou `true`. A correção troca prefixo
por media type inteiro antes dos parâmetros, com comparação ASCII de caixa;
`application/jsonp` e `application/json-seq` são falsos, enquanto
`APPLICATION/JSON` e `application/json; charset=UTF-8` são verdadeiros.
Uma contraprova direta recusa long-s Unicode no token. Esta mudança afeta o
diagnóstico, não a leitura do body nem o status/retry. Regra
`HTTP-JSON-MEDIA-TYPE-01`; owner de negócio não conhecido.

O primeiro scanner v101, ainda com `toLowerCase` após guarda ASCII, acrescentou
um alerta Unicode na classe alterada (273 brutos/88 SECURITY). O attempt
foi preservado; o final v102 usa `Pattern.CASE_INSENSITIVE` sem
`UNICODE_CASE` e passou compilação JDK17/Checkstyle zero e
SpotBugs 4.10.4.1 + FindSecBugs 1.14.0 em 656 fontes, sem erro.
O XML final tem 272 brutos/87 SECURITY/46 Unicode e o mesmo multiconjunto
tipo+classe v96; SHA-256
`02439F26ACAFEFE2E84ACCA42021DC7EB2374B06A6D7CBD0BD9BA140DC613D4E`.
A regressão final selecionada passou 49/49 em seis classes, sem IT ou JDBC.
Logs e espelhos imutáveis desta unidade estão sob
`target/shadow-local-rebuild-20260928-01/p29-*`. Nenhum alerta foi ocultado,
suprimido ou aceito nominalmente.

## Revisão v95/v96 — flags opt-in Unicode

A rotina comum `RuntimeConfigurationFactory.ConfigurationValues.booleanValue`
alimenta `dataexport.enabled`, `graphql.enabled` e `shadow.audit.enabled`.
`equalsIgnoreCase` reconhecia `fal\u017Fe` como `false`; em arquivo, a flag
de auditoria shadow desligava com essa grafia. Um teste com valor no arquivo
e no ambiente teve RED 30/1: a primeira chamada não lançou exceção. O
parser exige ASCII antes da comparação e ainda permite `FALSE` ASCII;
foco green 30/30. O primeiro invocador v93 parou no formatter do teste antes
de qualquer asserção; v94 preserva o RED causal.

O `clean verify` v95 isolado em JDK17 offline passou 2.356 Surefire em
280 XML/cinco skips, seis ITs offline, formatter, Checkstyle e JaCoCo 80/60.
PMD v95: 656 fontes, 36 achados brutos; só o endereço de linha de `parseLong`
mudou 596→599. Seis disposições que citam a fonte ou teste alterados foram
revisadas/vinculadas aos SHA atuais, e o validador v95b passou 36/36 contra
2.362 casos; v95 recusou duas referências de teste ainda com SHA anterior.
Não houve classificação de Segurança. SpotBugs/FindSecBugs v96 examinou
1.091 classes com zero erros/classes ausentes, 272 brutos/87 SECURITY/46
Unicode, mesmo multiconjunto tipo+classe v90; XML SHA-256
`71214946789B0FC3C89B2B584396848ECEDEC7E81FA924EB9A92FB95F05CA09A`.
A guarda ASCII é prova dinâmica adicional; nenhum alerta foi ocultado,
suprimido ou aceito nominalmente. Não há nova avaliação de licença/SBOM,
pois POM/lock/pacote permanecem iguais ao inventário 9/9 anterior.

## Revisão v89/v90 — entradas Unicode do alvo shadow

FindSecBugs v84 assinalou oito `IMPROPER_UNICODE` em
`ShadowStorageProperties`. Três contraprovas executáveis mostraram aceites
indevidos antes de qualquer JDBC: `ETL_\u017FISTEMA_V2_SHADOW` como nome do
banco via `equalsIgnoreCase`, `soc\u212AetTimeout` como chave de propriedade
via `toLowerCase` e `trustServerCertificate=fal\u017Fe` como `false` via
`equalsIgnoreCase`. Os testes RED foram, respectivamente, 10/1 falha e
12/2 falhas para as duas últimas entradas; após exigir `String.equals`
para o banco e ASCII para chaves/flags, o foco passou 12/12. O perfil P08
`QualificationConfiguration` já valida chaves ASCII e banco/host literais;
ele não foi alterado. A política mais ampla de host de
`ShadowStorageProperties` permanece distinta da autorização P08.

O espelho final v89 passou `clean verify` offline, 2.355 Surefire em 280 XML,
cinco skips, seis ITs offline, formatter, Checkstyle e JaCoCo 80/60. PMD
v89: 656 fontes, 36 brutos com assinaturas iguais ao v83, disposição local
36/36 contra 2.361 casos. Fontes e 1.135 classes/recursos do scan v90
conferiram por SHA com o v89. SpotBugs 4.10.4.1/FindSecBugs 1.14.0 v90:
1.091 classes, zero erros/classes ausentes, 272 alertas brutos/87 SECURITY,
46 `IMPROPER_UNICODE`; o único delta de assinatura tipo+classe contra v84
é uma remoção de `IMPROPER_UNICODE` em `ShadowStorageProperties`. XML bruto
SHA-256 `6249729705FBA79CA6F1DEFDECC36CA5735C3D739CD1F44ED5F54ED3811ABD3E`.
O scanner ainda assinala operações de case-folding precedidas por guardas
ASCII; nenhuma das 272 instâncias foi aceita ou suprimida nominalmente.
Dois invocadores de scan usaram caminhos de JDK17 inexistentes e foram
recusados antes de analisar; o terceiro usou o JDK17 privado e passou.
Não houve JDBC, SQL, DLL, UAC ou mudança de pin/lock/SBOM.

`STATES.md` conserva os critérios e o estado canônico. A varredura inicial usa as
mesmas 656 fontes Java principais e 1.135 artefatos de classes/recursos do
`clean verify` v79; a comparação SHA entre espelho de build e de scanner deu
zero diferenças. O POM do repositório e os pacotes v79 não foram alterados.

## Revisão v83/v84 após contraprova LOC-04

O parser de Localização de Cargas aceitava um decimal **textual** com 8.193
zeros, embora o token numérico JSON já tivesse teto de 8.192. O teste focado
falhou 1/11 antes da correção porque a entrada virava valor tipado; depois
passou 11/11 com quarentena antes de regex/`BigDecimal`. O bruto é preservado.
A ADR 0028 registra essa aplicação da regra LOC-04. O `clean verify` v83
passou 2.352 Surefire/cinco skips, seis ITs offline e JaCoCo 80/60; PMD
v83 conservou os mesmos 36 achados e a disposição local passou 36/36.
Os REDs v81/v82 foram erros do invocador (PATH de `pwsh`, depois
`JAVA_TOOL_OPTIONS` herdada) e estão preservados nos logs privados.

SpotBugs/FindSecBugs v84 examinou 1.091 classes, zero erro ou classe
ausente. Seu XML SHA-256
`A630C04A46F93B1F97FFA716FA5DF293F039125E27DAC32D875570490BCE0F0A`
continua com 273 alertas, 88 SECURITY; o multiconjunto de tipo e classe
permaneceu igual ao v80. O detector ainda sinaliza o regex decimal, pois
o XML estático não dispõe a contraprova do teto antes do matcher. Nenhum
alerta foi ocultado nem aceito nominalmente.

| Conjunto bruto examinado | Observação técnica limitada | Estado |
| --- | --- | --- |
| Seis `REDOS` | Um caminho decimal textual acima de 8.192 era executável e foi corrigido/testado; quatro regex de `ContractMetadata` recebem texto de até 256 caracteres e o temporal de Fretes usa comprimentos fixos. | Seis alertas seguem brutos; Segurança decide regra/disposição. |
| Dois `PREDICTABLE_RANDOM` | `ThreadLocalRandom` alimenta exclusivamente jitter de backoff em Data Export/GraphQL nas rotas observadas, sem geração de token ou credencial. | Sem aceite nominal. |
| Um `COMMAND_INJECTION` | O worker P08 usa `ProcessBuilder` com vetor de argumentos e diretório de pacote selado, sem invocação de shell nesse ponto. | Composição/inputs ainda sujeitos à revisão integral. |
| Cinco alertas rank 7–8 | Foram localizados três retornos ignorados (`await` com prazo e dois `setSavepoint`), uma condição redundante em `CycleOutcome` e sincronização em `Cycle.this`. | Sem correção especulativa; investigar efeito e concorrência no escopo governado. |
| 16 `SQL_INJECTION_JDBC` e seis `SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING` | Há três sinks sobrepostos, portanto 19 sinks distintos. As montagens observadas usam SQL literal, escolhas booleanas, enums fechados (`Fact`, `AnalyticSqlContract`, verticais) ou um recurso SQL de caminho fixo com teto de 8.192 bytes; valores de run, identidade e escopo são vinculados por `set*`. Exemplos: `LocalFactOracle`, `JdbcAnalyticQueries`, `JdbcExpansionStaging`, gateways de promoção e auditoria. | Não foi observada concatenação de texto de entrada nesses 19 sinks; os 22 alertas seguem sem disposição nominal, e a prova física/revisão de fluxo completo permanece. |
| Nove `UNSAFE_HASH_EQUALS` | As comparações vistas envolvem SHA/fingerprint de pacote, recurso, recibo ou frescor de registro (`QualificationWorker`, `QualifiedPackage`, políticas Fretes/Localização), não senha/token nos trechos inspecionados. | Exposição e timing não foram atestados; nove alertas abertos. |

Estes 42 exames técnicos de achados distintos não reduzem o inventário de 273.
O [delta proposto
para 12.8.2](PROPOSTA-SHADOW-12.8.2.md) não altera a regra canônica; o
candidato físico 12.8.1 está suspenso por decisão provisória de Segurança.

## Ferramentas e alcance executado

| Camada | Versão/licença e configuração | Resultado e limite |
| --- | --- | --- |
| SAST exploratório Java | [SpotBugs Maven Plugin 4.10.4.1](https://central.sonatype.com/artifact/com.github.spotbugs/spotbugs-maven-plugin) Apache-2.0; [engine 4.10.4](https://central.sonatype.com/artifact/com.github.spotbugs/spotbugs/4.10.4) LGPL-2.1; [FindSecBugs 1.14.0](https://central.sonatype.com/artifact/com.h3xstream.findsecbugs/findsecbugs-plugin/1.14.0) LGPL-3.0. Java 17, esforço Max, threshold Low, sem filtros/supressões. | 1.091 classes, zero erros/classes ausentes, 273 alertas brutos. Scanner de bytecode Java principal; não cobre PowerShell, SQL, histórico remoto ou SOs. |
| PMD local anterior | PMD 7.17.0, dez regras delimitadas; 36 achados brutos e disposição técnica 36/36 v79. | Reutilizado por hash; não repetido nem convertido em SAST integral. |
| Segredos | Scanner offline e Gitleaks v79: zero achados no alcance registrado em 0305. | Não substitui análise de código nem histórico remoto G02. |
| Composição/P11 | Política versionada threshold 0.0, zero exceções; `-PolicyOnly` e `-VerifyImplementation` atuais PASS com 33 casos/30 recusas. | Nenhum relatório Dependency-Check atual foi produzido; o validador físico recusou `MACHINE_REPORT_MISSING`. Resultado público P11 de 21/09 é histórico e cobria o POM normal, não o candidato shadow 12.8.1. |

O XML bruto `target/ci-p10-20260927-01/p29-sast-mirror-v80/target/spotbugsXml.xml`
tem SHA-256
`47ed49fee7058090ce71d43f106e06a664193ea6d602e61784108e0964fe8224`.
`p29-sast-v80-result.json` na mesma rodada privada registra contagens,
configuração e fingerprint do inventário. O primeiro RED foi resolução Maven
recusada por settings privados `offline=true`; a correção ficou apenas no
espelho e o scan seguinte terminou sem erro. Os JARs do scanner no cache têm
SHA-256 `405114389d4c93ab6ce0ddb09bad5b0f1da95ffac0bb36c04094066c5b01e338`
(plugin), `a88cad2e0ea9bb74b908ce82ae89416c61fa8f8ea5cfcc9368b1baac2da878d2`
(engine) e `6fa340344fa433ff46c2985dab1010e8bc739f9395c983594a5240095e92abc8`
(FindSecBugs). Nenhum deles foi incluído no produto.

## Achados preservados e triagem inicial

| Categoria | Alertas brutos |
| --- | ---: |
| SECURITY | 88 |
| MALICIOUS_CODE | 66 |
| BAD_PRACTICE | 60 |
| STYLE | 42 |
| MT_CORRECTNESS | 8 |
| PERFORMANCE | 5 |
| CORRECTNESS | 4 |

Os 88 SECURITY incluem 47 `IMPROPER_UNICODE`, 16 `SQL_INJECTION_JDBC`, nove
`UNSAFE_HASH_EQUALS`, seis `SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING`,
seis `REDOS`, dois `PREDICTABLE_RANDOM`, um
`USO_UNSAFE_OBJECT_SYNCHRONIZATION` e um `COMMAND_INJECTION`. O XML preserva
caminho, linha, tipo e rank de cada achado. A triagem pontual verificou que
`JdbcAnalyticQueries` monta identificadores a partir de `AnalyticSqlContract`
(enum fechado), `JdbcExpansionQueries` escolhe função por enum fechado e o
`ProcessBuilder` P08 recebe uma lista de argumentos derivada do pacote selado.
Essas observações delimitam **exemplos**, sem isentar os demais achados ou
aprovar um baseline. O alerta `REDOS` do parser decimal de Localização permanece
para análise específica do limite de token; nenhuma correção especulativa foi
feita. Não há regra SAST integral, limiar, baseline, exceções e aceitante
ratificados por Segurança; os 273 achados ficam abertos.

## Licenças, SBOM e contraprova

Nos dois pacotes extraídos v79, lock e SBOM CycloneDX 1.6 têm nove dependências/
componentes, e os nove POMs correntes conferem com os hashes pinados. O shadow
traz 22 arquivos de licença/POM/schema e o normal 24; há metadata histórica no
diretório, sem implicar dependência distribuída. O JAR JDBC declara MIT;
`mssql-jdbc_auth` declara Microsoft Proprietary License; Logback tem expressão
`EPL-1.0 OR LGPL-2.1-only`. Isso é inventário técnico, não aprovação jurídica.

O validador existente `QualificationPackageContents` confronta dependências,
componentes, licença, PURL, origem, hash do binário, POM e proveniência. Para
provar o alcance, uma cópia privada alterou apenas uma licença do SBOM e
recalculou SBOM, manifesto e sidecar. O envelope externo passou, mas o `inspect`
do JAR extraído saiu 2 em `QUAL_SBOM_LOCK_CORRESPONDENCE` antes de JDBC. O RED
inicial do harness era newline CRLF no sidecar; foi preservado e corrigido
sem mudar o pacote original. A proveniência segue `vulnerabilityFeed=NOT_EXECUTED`,
`signed=false`, `publishedCi=false`, `operationalApproval=false`.

## Lacunas de aceite e próximo input

O pacote shadow exige 12.8.1 por `AGENTS.md` **só para teste local**. A
[Microsoft registra a correção de CVE-2025-59250 em 12.8.2](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver),
e o [NVD lista 12.8.1 na faixa afetada](https://nvd.nist.gov/vuln/detail/CVE-2025-59250).
O pacote normal tem 12.8.2, mas não recebeu a qualificação física P08 na
revisão atual. Logo não existe RC de versão única com prova física, SCA atual,
SAST aprovado e smoke nos SOs suportados. O manifesto candidato declara
Windows/x64; o release owner precisa fixar os SOs suportados do RC exato.

- **P11 / Segurança:** fornecer/autorizar feed atual e classificar baseline
  da revisão, inclusive o candidato físico 12.8.1 e a DLL nativa; aceitar
  nominalmente ou decidir remediação/exceção com owner e prazo conforme política.
- **P29 / Segurança e release owner:** ratificar escopo/regras/limiar/baseline/
  exceções do SAST integral, avaliar licenças, fixar versão JDBC de release,
  configuração/fingerprint/RC e SOs; vincular checks, SCA, smoke e aceites à
  mesma revisão. Não usar este scan exploratório como aprovação automática.
- **P07/P08 físico / operador:** indicar prontidão antes de novo UAC. Depois,
  seguir o preflight exclusivo de `localhost/ETL_SISTEMA_V2_SHADOW`; não acessar
  a máquina antiga nem executar SQL agora.

Nenhuma versão, supressão, exceção, política, licença ou checkbox de P11/P29
foi aprovada por esta preparação.

Guardas finais da unidade: Gitleaks 8.29.1 no espelho privado de 4.036
arquivos e scanner offline em 4.036 candidatos (4.035 textos, um binário)
terminaram sem achados. O validador de trilha passou com 33 etapas, 48 IDs
abertos e nove pacotes; `git diff --check` e UTF-8 estrito passaram. Recibos
em `target/ci-p10-20260927-01/*p29-v80-private.log`.

Na revisão v85 após LOC-04 e os documentos de suspensão/proposta, Gitleaks
8.29.1 no espelho de 4.038 arquivos e scanner offline em 4.038 candidatos
(4.037 textos, um binário) passaram com zero achados. Trilha 33/48/9,
UTF-8 estrito em nove documentos e `git diff --check` também passaram.
Os recibos novos são `target/ci-p10-20260927-01/gitleaks-p29-v85-private.log`
e `offline-secret-scan-p29-v85-private.log`; o exame SQL/hash posterior é
documental e não altera fonte Java/POM v83.
