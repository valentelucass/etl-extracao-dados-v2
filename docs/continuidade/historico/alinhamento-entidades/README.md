# ETL Data Export V2

Extrator em sombra, Data Export-first, para a migração controlada das nove verticais ESL, com GraphQL transitório para Usuários/paridade e Raster condicional. O V2 não escreve no banco produtivo nem substitui o legado sem gate formal de paridade e cutover.

## Integração local por arquivos

O JAR caracteriza seis perfis existentes, CAP/FAT/INV/SIN e Raster; captura as
quatro expansões e Raster por arquivos com bindings explícitos; executa o cenário
analítico com entrada e oráculo separados e observa o sweep sintético de Coletas
com contexto declarado. Trocar os arquivos não exige recompilar. A comparação
percorre 19 contratos, cinco grãos e 35 escopos. Todos os efeitos SQL permanecem
restritos ao laboratório local, com duas travas e rollback obrigatório.

[Comandos do pacote](docs/catalogos/macrobloco-qualificacao-pacote/PACKAGE-README.md)
e [contrato da composição](docs/adr/0052-integracao-local-por-artefatos.md).
Integridade de arquivos não autentica o fornecedor nem concede aceite nominal.

## Laboratório relacional sintético — Manifestos, Coletas e Fretes

O entry point opt-in `RelationalLaboratoryMain` compõe capturas, bindings,
backlog SQL, hidratação, histórico/delta/replay e reconciliação local. Os comandos
`scenario`, `hydrate`, `replay` e `status` usam fixtures empacotadas e sempre
revertem os dados; V029–V037 são instaladas separadamente. A execução padrão
continua dormente. [Comandos e budgets](docs/catalogos/macrobloco-relacional/COMANDOS.md),
[contrato sintético](docs/catalogos/macrobloco-relacional/CONTRATO.md) e
[provas e limitações](docs/catalogos/macrobloco-relacional/RELATORIO.md).

## Runtime local das cinco verticais — Bloco 55

O JAR oficial protegido já publicou Coletas, Fretes, Manifestos, Cotações e
Localização no laboratório Windows/SQL, com fonte em loopback. BACKFILL temporal,
recuperação, comparação SQL independente e medição dos pipelines passaram.
O lote manual de 20 requests foi interrompido e retomado pelo estado durável.
Java 17 offline: 1.138 testes, zero falhas/erros e quatro skips preexistentes.

O Bloco 55 concluiu A–J no laboratório: 42 publicações sintéticas, STATUS sob
OPERATOR, negativas SQL e isolamento de dependências comprovados.
[Operação, provas e limites](docs/runbooks/v2-022-bloco55-cinco-verticais-local.md),
[ADR 0038](docs/adr/0038-runtime-cinco-verticais-e-consumidores-locais.md) e
[manifest de evidências](database/manifest/runtime-bloco55.json).
Não executar `clean` sobre o target que contém os históricos B53/B54/B55.

## Motor local de Coletas/Fretes — fotografia do Bloco 51

O Bloco 51 compõe dispatcher, contrato/travessia, staging auditado, candidate set, DQ e
promoção pelos permits existentes. Dependências exigem recibo de publicação; cancelamento,
lease e resultados incertos falham fechados. A composição explícita fica em
`LocalColetasFretesRuntime`; o entry point oficial continua deny-all.

A prova usa verticais e adapters JDBC reais com fonte e protocolo SQL sintéticos: 1001
testes no `clean verify`, zero falhas/erros e quatro skips esperados. Não comprova SQL físico
ou retomada após perda da JVM. Veja [escopo, validação e próximo pacote local](docs/runbooks/v2-022-motor-local-coletas-fretes.md)
e [ADR 0033](docs/adr/0033-motor-local-coletas-fretes-e-recuperacao.md).
## Princípios

- Um cliente Data Export genérico; contratos específicos para `6908`, `6389`, `6399`, `6906`, `8656`, `8636`, `4924`, `10633` e `6392`.
- Filtro de data de negócio obrigatório e `scopes.by_updated_at` como escopo irmão, nunca dentro da raiz da entidade.
- Staging tipado/minimizado e processamento set-based de dedupe, promoção, reconciliação e agregações no SQL Server; payload bruto não é persistido por default.
- Partições idempotentes, overlap versionado e ledgers separados por modo; `by_updated_at` não é watermark sem prova contratual.
- Runtime CLI one-shot sob scheduler externo, com lease/heartbeat no SQL Server e sem daemon/PID interno.
- Sem segredos no repositório, linha de comando ou `-D`. Injete-os somente pelo secret provider e por variáveis de ambiente protegidas.

## Comandos locais

O Maven Wrapper fixa o Maven 3.9.14 e valida a distribuição pelo SHA-256
registrado em `.mvn/wrapper/maven-wrapper.properties`. O gate aceita somente um
JDK 17 (`[17,18)`); JDK 16, 18 ou posterior falha antes da compilação. A release
do compilador e o bytecode também permanecem em Java 17.

### Windows (PowerShell)

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
.\mvnw.cmd --batch-mode --no-transfer-progress package
& "$env:JAVA_HOME\bin\java.exe" -jar target\etl-dataexport-v2.jar --help
```

### Linux/macOS (shell)

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress package
"$JAVA_HOME/bin/java" -jar target/etl-dataexport-v2.jar --help
```

Na primeira execução, o wrapper pode baixar a distribuição fixada para o cache
local do Maven. O workflow versionado está configurado para executar o mesmo
wrapper com Java 17, mas só poderá ser chamado de CI ativo após a governança
remota de V2-016b e uma execução real no provedor.

Se `JAVA_HOME` não estiver definido, configure-o para um JDK 17. Confirme com
`java -version` e `./mvnw --version` (ou `mvnw.cmd --version` no Windows) que o
mesmo JDK 17 inicia o Maven; uma instalação mais nova não é aceita pelo gate.

O ADR 0007 fixa o alvo como JAR executável Java 17 reproduzível. O artefato atual ainda não é fat JAR; formato fat/thin, distribuição e avisos consolidados de licença continuam pendentes dos gates de empacotamento/release.

## Qualidade e segurança locais

`verify` executa Enforcer, Spotless, Checkstyle, compilação com `-Xlint:all` e
falha em qualquer warning, testes, regras arquiteturais e JaCoCo bloqueante. O
JaCoCo exige, individualmente em todo pacote, pelo menos 80% de linhas e 60% de
branches. Os pisos orientados a risco são 90%/70% para configuração, 85%/65%
para Data Export e 90%/70% para persistência, sempre na ordem linhas/branches.

O gate arquitetural examina somente fontes/classes produtivos atuais e possui
regressões positivas e negativas próprias. Ele bloqueia uma API externamente
visível que exponha coleção, array ou coleção encapsulada sem contrato limitado
explicitamente inventariado; métodos sem limite/campos de adapters de
persistência que materializem coleções; e containers mutáveis acumulados no loop
de uma travessia/fallback. Batches/páginas com limite explícito e fallback finito
são aceitos. Coleções de uma única página, requisição ou documento de metadata
ficam em uma allowlist pequena, justificada e validada contra APIs ainda
existentes; o harness de teste não é API produtiva.

Para formatar deliberadamente os fontes Java antes da validação:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply
```

O scanner auxiliar enumera exatamente os arquivos rastreados ou não ignorados pelo Git, valida o único binário aprovado por checksum e falha fechado para conteúdo não inspecionado. Antes da varredura real, execute as regressões do próprio scanner:

```powershell
.\scripts\security\Test-OfflineSecretScan.ps1
.\scripts\security\Invoke-OfflineSecretScan.ps1 -Source .
```

O gate canônico usa a política versionada em `.gitleaks.toml`. Com o binário
`gitleaks` confiável instalado localmente, rode a árvore de trabalho com escopo,
configuração e redaction explícitos:

```powershell
gitleaks dir --config .gitleaks.toml --redact=100 --exit-code=1 --no-banner .
```

Execute o Gitleaks com a árvore sem `target`; em um baseline já versionado, o
workflow cria um espelho exato de `git ls-files` antes da varredura. A saída deve
permanecer redigida e não deve ser transformada em relatório que contenha o
matching original.

O scanner auxiliar nunca imprime o conteúdo encontrado e não substitui Gitleaks
nem a varredura do histórico. Imediatamente depois do primeiro baseline Git,
execute também:

```powershell
gitleaks git --config .gitleaks.toml --redact=100 --exit-code=1 --no-banner --log-opts="--all" .
```

A ordem de rotação, as condições de parada e a evidência permitida estão no runbook
[`docs/runbooks/conter-e-rotacionar-segredos.md`](docs/runbooks/conter-e-rotacionar-segredos.md).

O perfil separado de vulnerabilidades de dependências é fail-closed e opt-in. A
política versionada fixa `failBuildOnCVSS=0.0`, bloqueia qualquer vulnerabilidade
não exceptuada, preserva `failOnError=true` e exige JSON e HTML em
`target/dependency-check/`. Antes de uma execução autorizada, valide localmente o
catálogo e o vínculo físico sem consultar feed:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DependencyVulnerabilityPolicy.ps1 -PolicyOnly
pwsh -NoProfile -File .\scripts\validation\Test-DependencyVulnerabilityPolicy.ps1 -VerifyImplementation
```

Uma execução real do perfil exige rede/feed e autorização próprias, além de
`NVD_API_KEY` exclusivamente por variável de ambiente. Quando autorizada, usa:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Psecurity-audit clean verify
```

O workflow agendado/manual valida a implementação antes do Maven, valida depois o
JSON como contrato de máquina e exige também o HTML humano. Relatório ausente é
erro, nunca warning; `continue-on-error`, threshold por `-D` e suppression ad hoc
são proibidos. A campanha local V2-015d não executou esse perfil, feed ou NVD, e
seu resultado não é baseline aceita.

Os workflows versionados estão configurados para verificar formato/testes e segredos em push/PR, além da auditoria de dependências semanal ou manual. Eles ainda não constituem CI ativo ou verde: isso exige remote, proteção e evidência de execução em V2-016b.

Quando a governança remota existir, o workflow preservará por 14 dias os
relatórios Surefire, Checkstyle e JaCoCo de cada tentativa. Localmente, os mesmos
artefatos ficam em `target/surefire-reports`, `target/checkstyle-result.xml` e
`target/site/jacoco` até o próximo `clean`; copie-os para o repositório de
evidências aprovado antes de limpar quando uma execução precisar ser retida.

O workflow de auditoria exige o secret `NVD_API_KEY` e falha antes da consulta
quando ele não estiver cadastrado. Isso evita iniciar a auditoria sem a entrada
externa obrigatória; erro do scanner, relatório parcial, ausente ou inválido
continua bloqueante.

O baseline de portabilidade V2-017/V2-017a é gerado e validado offline:

```powershell
.\scripts\validation\Build-PortabilityCatalog.ps1
.\scripts\validation\Test-PortabilityCatalog.ps1 -VerifyGenerated
```

Ele cobre 442 slots opacos de `/info`, 270 paths candidatos separados de `/data`, 459 colunas operacionais, 18 de Usuários, 58 de Raster, 673 outputs de views, 336 colunas de fatos e as 75 regras canônicas com casos sintéticos positivos/negativos. Slots cujo nome técnico atual não está versionado têm owner/gate e bloqueiam publicação; nenhuma nova sonda é feita por esses comandos.

O desenho material de cutover V2-048a também possui catálogo determinístico e gate offline:

```powershell
pwsh -NoProfile -File .\scripts\validation\Build-CutoverTopologyCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-CutoverTopologyCatalog.ps1 -VerifyGenerated
```

A evidência local não prova roteamento nem write-fence por entidade. Por isso, o catálogo fixa
`CUTOVER-DB-01` como `DATABASE_WIDE` até prova física conjunta em V2-048b, cobre 131 artefatos de
responsabilidade, 49 nós de DAG e 11 fences. O ponto de não retorno é somente a primeira publicação
V2 aceita como autoritativa em produção; qualquer `PUBLISHED` em shadow permanece evidência técnica.
O teste não abre rede ou banco e recusa endpoint/nome produtivo inventado, wrapper cross-database e
application lock tratado como fence material.

## Configuração

Use [config/application.example.properties](config/application.example.properties) como catálogo não
secreto e informe uma cópia explícita por `--config`. A precedência única é:
variável de ambiente `V2_*` não vazia, depois o arquivo indicado. Propriedades de
sistema não configuram o runtime e qualquer nome classificado como secreto em
`-D`, CLI ou arquivo é recusado. O provider transitório conhece somente
`V2_DATAEXPORT_TOKEN` e `V2_GRAPHQL_TOKEN`; cada segredo só é materializado pela
factory HTTP da fonte no caminho operacional autorizado. Validação e dry-run não
leem esses valores, e não há leitura implícita de `.env` deste ou de outro
repositório. Enquanto V2-041 permanecer em `EXTERNAL_HOLD`, nenhum dos dois
segredos ou endpoints remotos pode ser usado.

Os únicos comandos de configuração disponíveis neste bloco não abrem conexão,
não criam cliente HTTP e não disparam carga:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress package
& "$env:JAVA_HOME\bin\java.exe" -jar target\etl-dataexport-v2.jar config validate --config config\application.example.properties
& "$env:JAVA_HOME\bin\java.exe" -jar target\etl-dataexport-v2.jar dry-run --config config\application.example.properties
```

O preflight atual permite somente `LOCAL_SHADOW`; se a auditoria local for
habilitada, aceita apenas o banco isolado `ETL_SISTEMA_V2_SHADOW` em loopback.
Ele recusa `ETL_SISTEMA`, `esl_cloud`, bancos de dashboards e qualquer alvo
não autorizado antes de a composição de execução existir. Nenhum desses
comandos libera rede, uso de segredo, DDL, DML ou cutover.

As ações operacionais futuras passam pelo boundary do composition root oficial mesmo na invocação
direta do JAR por esse entry point; wrapper e scheduler não constituem a fronteira. O estado atual
é `deny-all`.
A política offline separa `status`, `run`, `replay`, `sweep preview`, `sweep apply` e `force-run`,
enquanto migrations e cutover permanecem fora do runtime. Provider, authority, principals e seus
mapeamentos são `EXTERNAL_INPUT_REQUIRED`, portanto V2-042 ainda não está globalmente concluído.
Consulte o [modelo de ameaças](docs/seguranca/modelo-ameacas-autorizacao-runtime.md) e o
[ADR 0010](docs/adr/0010-boundary-identidade-autorizacao-runtime.md).

Quando Data Export ou GraphQL estiver habilitado, os respectivos campos `*.resilience.*` do catálogo
de configuração são obrigatórios e validados em conjunto. Se ambos estiverem habilitados, identidade
da instância, tenant e policy precisam coincidir. A fundação de V2-043 usa um governor único
por origem ESL, uma requisição em voo, budgets por origem/workload, deadlines monotônicos de
request/passo/ciclo, `Retry-After` sob teto, backoff/jitter e circuitos isolados por template. A
composição source-scoped deve manter um único `EslRequestGovernor`, criar seus ciclos e passá-los a
uma `DataExportHttpGatewayFactory` reutilizável e, para o adaptador transitório, a uma
`GraphQlHttpGatewayFactory` vinculada por identidade ao mesmo governor. As fábricas compartilham
transporte/circuitos, mas não criam o singleton do governor. Os construtores diretos continuam restritos a sondas e testes de
compatibilidade. Essa integração ao runtime one-shot pertence a V2-022 e não é acionada por
`config validate` ou `dry-run`. Consulte o
[ADR 0011](docs/adr/0011-resiliencia-esl-e-politica-de-falhas.md).

Toda ocorrência promovível também fica vinculada a um release de contrato com fingerprints
independentes de metadata e resposta observada. O gate valida o shape página a página, exige
metadata, resposta populada e terminalidade da travessia, e aplica somente allowlist exata de
mudança compatível com alerta. Paths runtime vêm do baseline ou de adição previamente aprovada;
somente objetos explicitamente declarados em policy podem ficar opacos, e seu conteúdo ainda conta
para os tetos values-only. A factory Data Export sela a configuração efetiva sem token e o gate
confere fingerprint, template, form, chave, limites e boundary antes de I/O. `/info`, query ou
página vazia isolados não provam identidade nem completude. O permit resultante é exigido tanto na
preparação quanto na publicação SQL; a publicação exige adicionalmente o permit de Data Quality da
mesma execução. Consulte o
[ADR 0012](docs/adr/0012-drift-de-contrato-antes-da-promocao.md).

O adaptador GraphQL transitório de V2-024 possui somente três documentos `query` estáticos:
Usuários e os sidecars estritamente necessários de Coletas e Fretes. Usuários aceita no máximo 20
nodes por página; os sidecars, 100. A travessia é serial, mantém apenas a página atual e um detector
de repetição de cursor de memória fixa, recusa página vazia anômala, cursor ausente/repetido, caps,
timeout e cancelamento, e trata `pageInfo.hasNextPage=false` apenas como terminalidade local — nunca
como prova de completude do dataset. As 19 folhas ativas estão no
[ledger transitório](docs/catalogos/graphql-transitorio.csv): as quatro folhas de Usuários estão
`IMPLEMENTED_IN_SHADOW` sob `SHADOW_UPSERT_ONLY`; as 15 folhas dos sidecars continuam
`SYNTHETIC_ONLY`/`OBSERVATION_ONLY`. Todas permanecem bloqueadas para publicação e cutover. Fixtures
e servidor loopback validam o desenho sem credencial ou rede externa. Consulte o
[ADR 0016](docs/adr/0016-adaptador-graphql-transitorio.md).

V2-025a versiona os contratos offline de `6908`, `6389` e GraphQL `individual` no
[catálogo da primeira onda](docs/catalogos/contratos-primeira-onda/README.md). O vocabulário de
classificação é separado da proveniência da evidência; filtros, raiz, ordem, `per`, identidade,
paginação, timezone e limites participam de fingerprints determinísticos. No 6389, a ordem é
`corporation_sequence_number asc`, enquanto `id` permanece a chave auditável. Os três contratos
ficam `BLOCKED_NO_COMPLETENESS_PROOF` para sweep/desativação/cutover e permitem apenas que registros
observados prossigam aos gates de upsert em sombra. Consulte o
[ADR 0017](docs/adr/0017-contratos-primeira-onda-e-completude.md).

V2-009a fecha a matriz offline no
[catálogo de identidade da primeira onda](docs/catalogos/identidade-primeira-onda/README.md):
`(source_instance, tenant_scope, entity, source_key)` resolve um `canonical_id` surrogate, aliases de
negócio permanecem versionados e tokens GraphQL integrais/textuais não são colapsados. O catálogo
processa uma observação por vez e deixa dedupe, cardinalidade, crosswalk e rekey set-based para
V2-009d. Valide-o com
`pwsh -NoProfile -File .\scripts\validation\Test-FirstWaveIdentityCatalog.ps1`. Consulte o
[ADR 0018](docs/adr/0018-identidade-e-crosswalk-da-primeira-onda.md).

V2-033 implementa Usuários somente em sombra sobre essa identidade. Cada página GraphQL de até 20
nodes vira um microbatch limitado; `id` conserva os tipos `INTEGER`/`STRING`, `name` conserva
`ABSENT/NULL/VALUE` e a JVM não acumula o snapshot. V007 mantém current em `core.usuario`, histórico
append-only em `core.usuario_history`, quarantine e reconciliação tipadas, com dedupe, conflito,
hash/no-op, ordem total e reativação set-based no SQL Server. Ausência, cap, falha e terminalidade
local nunca escrevem `active=0`; sweep continua bloqueado por V2-012b/V2-013. V007 não cria objeto
em `pub`, cursor persistido, `updatedAt` inventado ou chamada ao template `9901`. Consulte o
[ADR 0019](docs/adr/0019-usuarios-current-history-em-sombra.md) e o
[runbook de Usuários](docs/runbooks/usuarios-current-history-em-sombra.md).

A fatia Usuários de V2-035b acrescenta em V009 somente a projeção schemabound e versionada
`core.v_usuario_dimension_current_v1`. Ela mantém uma linha por `usuario_id` canônico ativo,
preserva identidade de origem type-tagged, presença e nome sem trim e expõe apenas timestamps
técnicos UTC. Não junta histórico, não cria índice, role ou grant e permanece inacessível a
`public`/`v2_runtime`. `pub.vw_dim_usuarios`, aliases legados, manifesto consumidor, paridade,
roteamento e cutover continuam em V2-037. Consulte o
[ADR 0021](docs/adr/0021-dimensao-current-de-usuarios-em-sombra.md).

V2-035a acrescenta em V008 apenas a fundação offline de referências governadas. Envelope, recibo
de conteúdo, ratificação e revogação são append-only; calendário/status,
filiais/aliases/documentos tokenizados, matriz e exceções de frota, atribuição vinculada à release
de filiais, exclusão de cubagem, região CEP ou cidade/UF e tarifa direcional possuem grãos, vigência
e índices próprios. Versões de normalização/tokenização integram os grãos e lookups `EXACT_BIN2`,
sem fallback entre versões. A migration não insere linha, não ratifica release, não cria ponteiro corrente
e não concede acesso direto ao runtime. O manifesto fixa schemas lógicos/SQL, serialização canônica
e fixture dourada; status e calendário são candidatos determinísticos inertes. Todas as baselines
mutáveis continuam bloqueadas por export, fingerprint, owner e ratificação externos. Consulte o
[ADR 0020](docs/adr/0020-referencias-governadas-sem-default-produtivo.md) e o
[runbook de referências](docs/runbooks/importar-ratificar-e-revogar-referencias.md).

O storage de sombra começa desabilitado. A fundação ativa de banco está em
[database/](database/README.md): migrations Flyway limpas criam os schemas
`ctl/stg/core/ref/mart/pub/recon`, os papéis mínimos e o control plane V2-020 em `ctl`. Ele mantém
fonte/ciclo/partição/tentativa, eventos imutáveis, lease, páginas e contagens. V2-020b/V2-021b
fecham o protocolo positivo em um único entrypoint SQL: aplicação técnica ao `core`, evidência por
candidato, reconciliação, `PUBLISHED`, pointer, fronteira incremental contígua e liberação da lease
compartilham o mesmo commit e o relógio do SQL Server; a lease é revalidada no último write. O entrypoint genérico de `ctl` continua
fail-closed para impedir publicação sem essa evidência. V2-021
acrescenta o kernel técnico mínimo de staging em `stg` e quarantine append-only em `recon`: guarda
somente chave opaca, hashes, presença, frescor e motivo sanitizado, nunca payload, URL ou identidade
canônica de vertical. A preparação deduplica a execução inteira com `ROW_NUMBER` e persiste um
candidate set imutável sem efeitos de publicação; somente
`core.usp_apply_reconcile_publish_execution` o aplica de forma set-based como
insert/update/reativação/no-op/stale-no-op e confirma pointer/checkpoint. O bootstrap
atual não abre conexão; a composição do runtime com esse control plane pertence a V2-022. Não
aponte qualquer variável de teste local ao banco legado ou ao banco do Dashboard.

V2-023 acrescenta em V006 o framework comum de observabilidade, integridade e Data Quality.
`COUNT`, cardinalidade, equações, SLA e thresholds absoluto + percentual são avaliados set-based no
SQL Server; Java conserva apenas um resumo fixo `O(1)`. A ausência de policy ratificada, resultado
parcial, mismatch, falha SQL ou DQ reprovada impede publicação antes do commit. Logs usam JSON com
campos fechados, correlação opaca, redaction e budget por componente/processo; o root de logging
fica negado. Policies são vinculadas a scope/hash canônicos, e SLA é revalidado no health e no
trigger. Métricas não aceitam dimensões livres; health e alertas devolvem/persistem somente códigos
e contagens sanitizados. A migration não ativa policy, TTL, alerta externo ou sonda remota. Consulte o
[ADR 0014](docs/adr/0014-observabilidade-e-data-quality-fail-closed.md) e o
[runbook de observabilidade e DQ](docs/runbooks/observabilidade-integridade-data-quality.md).

V2-045a acrescenta em V005 o lifecycle local governado de staging: policy ratificada por
evidências e papéis distintos,
legal hold, plan/dry-run limitado, archive tipado e verificado, purge restrito a staging terminal e
restore materializado somente em `recon`, com read-back interno e nenhuma exportação pública de
linhas. As dez fontes preservadas recebem archive tipado, atestado SHA-256 por linha, raiz de
conteúdo e manifesto v3. Policy/hold precisam coincidir com seus ledgers; a trilha terminal inteira
precisa ter origem, cardinalidade, sequência, relógio, grafo semântico e último evento coerentes com
a execução; retry de purge recusa staging reaparecido. O mecanismo nasce
desabilitado, sem política ou TTL semeado; `v2_runtime` não
recebe seus entrypoints e cada função operacional possui role separada. O lifecycle manual de logs
é opt-in, bounded, archive-first e cercado por locks/receipts/marker persistente; `maxHistory`
continua zero. Mesmo policy disabled ou em hold revalida a operação apontada pelo marker atual e
artefatos órfãos do escopo antes de retornar, sem criar artefatos em uma origem nova. O comando não
prova a integridade contínua de operações anteriores já substituídas no marker. Nada disso comprova
cold storage/WORM, backup/restore físico, ACL produtiva ou
RTO/RPO. A ratificação produtiva de staging, logs e archive permanece em V2-045b `EXTERNAL_HOLD`;
os candidatos de 7/30 dias não foram ativados. Consulte o
[ADR 0013](docs/adr/0013-retencao-arquivamento-e-purge-governado.md) e o
[runbook de retenção e restore](docs/runbooks/retencao-arquivamento-e-restore.md).

As chaves técnicas persistidas usam comparação binária exata. Entradas de procedure são medidas
antes de trim/conversão para impedir colisão por truncamento; somente espaço U+0020 é removido onde
o contrato prevê, e reason codes canônicos não sofrem case-fold. As contagens
separam raízes distintas, duplicatas, registros físicos sem chave e artefatos append-only de
quarantine; elas não tratam cada artefato quarentenado como uma nova raiz. O adapter também exige
ordinal 1..10.000 único dentro de cada lote antes de abrir a transação JDBC e limita a cópia sem
confiar no tamanho declarado pela coleção. Retries exatamente coincidentes reconhecem o efeito
persistido; escrita nova ou divergente continua exigindo fencing/lease.

O gate local V2-015b verifica oito manifests/fingerprints, migrations V001–V009, constraints, shapes
exatos de PK/UQ/FK/IX, menor privilégio, contenção entre duas sessões, retries e o protocolo positivo
no SQL Server de sombra em exercícios rollback-only. O exercício 011 acrescenta mismatch de
versão/SHA de contrato e configuração antes das duas fases de promoção; os exercícios 013–020
cobrem lifecycle, ratificação/hold/evento terminal adulterados, owner-context do migrator,
fingerprint ASCII e SHOWPLAN sem efeito. O validator 021, os exercícios 022–024 e o SHOWPLAN 025
comprovam framework DQ, aplicação por `v2_migrator`, thresholds absoluto/percentual, SLA temporal,
retry exato, health agregado e recusa atômica de publicação sem `PASSED` completo.
O validator 026 e o exercício 027 acrescentam current/history de Usuários, presença tri-state,
identidade por ambiente, conflito, no-op/stale/replay/reativação, DQ tipada, grants e lifecycle
minimizado. O probe concorrente deriva a fórmula canônica do lock diretamente de V004 e a comprova
em duas sessões; essa evidência é composicional com o exercício real dos entrypoints, não uma
alegação de duas execuções completas simultâneas. O exercício 028 atribui os quatro access paths e o
seek do apply tipado aos respectivos statements, exige zero conversão/warning operacional e reverte
tudo. O exercício 027 mede o custo integral pelo planner e pelo archive e comprova que o budget
tipado é aplicado antes de oversized/`TOP`, sem starvation do item menor posterior. O exercício 029
recusa com fence transacional o sidecar tipado tardio após uma execução generic-only terminal. O
validator 030 e os exercícios 031–033 acrescentam a fundação `ref`, seeds candidatos, calendário
rolante pós-2032, intervalos/overlap, ratificação/revogação, grants negativos e prova da role
migrator, com rejeições destrutivas para a transação em escopos rollback-only isolados e sempre sem
baseline produtiva. O probe concorrente comprova em duas sessões o namespace de ratificação por
family/scope/activation, a exclusão física entre conteúdo e seal e os dois interleavings entre
ratificação de atribuição e revogação da filial; o banco de prova efêmero é descartado. O exercício
034 compila quinze access paths sem warning operacional. O validator 035 fixa o shape, o grão e a
ausência de grant da dimensão current de Usuários; 036 prova filtro ativo, identidade type-tagged,
presença tri-state, histórico sem multiplicação e rollback integral, enquanto 037 compila os três
paths dimensionais com zero conversão/warning e aceita o scan legítimo da leitura completa. Execute
`.\scripts\validation\Invoke-ProgressiveDataGate.ps1`; ele aceita somente `localhost` e
`ETL_SISTEMA_V2_SHADOW` como alvo canônico; o probe concorrente cria e remove via `master` apenas
`ETL_V2_REF_PROBE_<guid>`, sem deixar objetos persistentes. Os checkers estruturais PowerShell não
equivalem a essa execução dinâmica; qualquer migration ou manifesto ativo alterado exige nova
revalidação local.

O perfil Maven `shadow-migrations-windows-auth` não roda em `verify`, não cria banco e usa somente
Windows Authentication para a fundação Flyway ativa. O alvo local de evidência de 2026-08-25 ainda
não tem `flyway_schema_history`; ele exige a transição guardada documentada antes de receber
`flyway:migrate`. A prova V001–V003 e a integração JDBC rollback-only permanecem preservadas em
`database/evidence/historical-shadow-v001-v003/`, fora do caminho Flyway ativo.

Os testes remotos de contrato são separados do runtime: use [config/contract-test.example.properties](config/contract-test.example.properties) e o [runbook de revalidação](docs/runbooks/revalidar-contrato-dataexport-coletas-fretes.md). Eles exigem `-Pcontract-tests -Dcontract.tests.enabled=true`; sem qualquer variável `CONTRACT_*`, a IT remota é ignorada e não abre rede. A configuração opcional `CONTRACT_4924_*` habilita apenas a sonda financeira auxiliar existente; a vertical 4924 pertence a V2-030 e ainda não é composta no runtime. O único artefato permitido é o resumo sanitizado transitório em `target/contract-evidence/`. V2-041 mantém qualquer execução externa suspensa até evidência operacional nova; sua liberação, isoladamente, não autoriza o harness Java, que continua fora da allowlist de rede até autorização própria ou alteração formal de `AGENTS.md`.

## Estado e decisões

- Backlog, riscos e evidências: [STATES.md](STATES.md)
- Próximo chat e escolha entre GPT-5.6 Terra xhigh/Sol ultra: [docs/runbooks/trilha-de-chats-gpt-5-6.md](docs/runbooks/trilha-de-chats-gpt-5-6.md)
- Regras de contribuição: [AGENTS.md](AGENTS.md)
- Guia para contribuir: [CONTRIBUTING.md](CONTRIBUTING.md)
- Política de segurança: [SECURITY.md](SECURITY.md)
- Identidade e autorização do runtime: [docs/seguranca/modelo-ameacas-autorizacao-runtime.md](docs/seguranca/modelo-ameacas-autorizacao-runtime.md)
- Termos do código e avisos de terceiros: [LICENSE](LICENSE) e [NOTICE](NOTICE)
- Decisões: [docs/adr](docs/adr)
- Baseline executável de portabilidade e proteção: [docs/catalogos/portabilidade](docs/catalogos/portabilidade/README.md)
- Identidade/crosswalk da primeira onda: [docs/catalogos/identidade-primeira-onda](docs/catalogos/identidade-primeira-onda/README.md)
- Topologia, unidade e fences de cutover: [docs/catalogos/cutover](docs/catalogos/cutover/README.md)
- Runbook declarativo de freeze/cutover/recuperação: [docs/runbooks/freeze-cutover-e-recuperacao.md](docs/runbooks/freeze-cutover-e-recuperacao.md)
- Caracterização histórica 6908/6389: [docs/catalogos/contratos-dataexport-coletas-fretes.md](docs/catalogos/contratos-dataexport-coletas-fretes.md)
- Regras herdadas, rastreabilidade e gates de paridade: [docs/catalogos/regras-negocio-coletas-fretes.md](docs/catalogos/regras-negocio-coletas-fretes.md)
