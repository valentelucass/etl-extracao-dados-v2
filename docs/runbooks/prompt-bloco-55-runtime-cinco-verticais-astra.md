# Proposta de Bloco 55 — runtime das cinco verticais em sombra

Preparado em 08/09/2026, após o Bloco 54, para continuar a construção solicitada
pelo owner. **PREPARED_NOT_EXECUTED**: esta preparação não inicia campanha,
amplia permissões, marca checkbox nem atribui um bloco funcional concluído.
Modelo sugerido conforme preferência do owner: Astra xhigh.

**Revisão ampliada de 08/09/2026:** dez frentes A–J, incluindo processamento por
períodos, comparação do resultado aplicado no laboratório, medição dos fluxos
das cinco verticais e execução manual de lotes retomáveis. O teto proposto passa
a 384 unidades em três lotes finitos. A revisão original de seis frentes/128
unidades está preservada em `target/bloco55-expansion-review-20260908/docs/runbooks/`.
Esta revisão continua PREPARED_NOT_EXECUTED; não abriu campanha nem aplicou direitos.

## Resultado pretendido

Executar **Manifestos 6399, Cotações 6906 e Localização de Cargas 8656** pelo mesmo
motor que já executa Coletas/Fretes, usando as bases shadow existentes. Entregar
travessia, staging, DQ, aplicação em sombra, status e recuperação por nova JVM para
as três, além da regressão afetada das duas anteriores. A comprovação é local,
sintética e pelo JAR oficial; o número de verticais base continua **5/9**. O avanço
é a integração operacional local dessas cinco, hoje comprovada somente para duas.

Entregar também: BACKFILL temporal das cinco com retomada sem salto; comparação
SQL entre a saída das execuções sintéticas e referências independentes; medição
do fluxo mapper/staging das cinco em múltiplas escalas; launcher serial de até
20 requests congelados, com estado durável continuando no SQL. Esses consumidores
reutilizam B54, Q-FND-01/02, Q-BST-01 e Q-MED-FND-01 e possuem aceites próprios
abaixo. Nenhum deles habilita fonte real, agenda, bootstrap real ou produção.

Este pacote implementa uma extensão concreta de V2-022. Não reabre V2-026/027/028,
já concluídas no escopo das suas bases shadow, nem repete a matriz completa B54.
Fonte real, paridade e cutover mantêm os próprios critérios. A extensão do
laboratório a três workloads e seus direitos está delimitada abaixo para adoção.

## Mensagem pronta para executar em outro chat

```text
Trabalhe em C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2.
Leia e execute integralmente docs/runbooks/prompt-bloco-55-runtime-cinco-verticais-astra.md.
Execute o Bloco 55 ampliado com Astra xhigh, concluindo as frentes A–J.

Adoto o escopo de laboratório da seção 3: integrar Manifestos, Cotações e
Localização ao runtime existente, com fonte exclusivamente loopback e SQL local
ETL_SISTEMA_V2_SHADOW. Reutilize etl_v2_exec e etl_v2_view. Autorizo o novo teto
finito de até 384 unidades em três lotes de 128, a evolução versionada do schema,
a revisão protegida e os deltas exatos de grants/scopes descritos nessa seção,
após preparar e conferir
seu pacote de aplicação, verificação e recuperação. Não peça novamente banco,
contas ou autorização para ações que estejam exatamente nesse escopo.

Preserve integralmente B53/B54, os ledgers, artefatos, dados e alterações
preexistentes. Não reutilize as 18 unidades restantes de B54 nem renove validade.
Dados sintéticos e auditoria confirmados permanecem, sem limpeza destrutiva.

Inclua BACKFILL temporal das cinco verticais, comparação de saídas sintéticas
com oráculos independentes, medição dos fluxos e execução manual de lotes
retomáveis. Preserve os limites por operação, contas, modos e direitos da seção 3.
Conclua implementação, testes, JAR, operação manual e aceites locais comprovados
no mesmo bloco. Para requisito fora do escopo, prepare o pacote concreto e
continue as frentes independentes. Não execute fonte real, ETL_SISTEMA, produção,
agendamento, rotação de credenciais, commit ou push.

Revise os aceites pela redação original e pelas provas: feche o que estiver
integralmente comprovado no escopo correto, preservando requisitos externos
concretos. Sincronize STATES.md, trilha e validadores. No fim, explique em
português simples o que ficou funcionando, testes e impedimentos reais.
```

## 1. Base que deve ser preservada

Ler AGENTS.md, STATES.md, ../CONTEXTO_GLOBAL.md, a trilha e o
[fechamento B54](v2-022-bloco54-conclusao-local.md). Conferir o manifest
`database/manifest/runtime-bloco54.json` e suas evidências privadas; se houver
divergência, diagnosticar por leitura limitada antes de qualquer mutação.

Fotografia B54: Java 17, 1098 testes sem falha/erro, quatro skips preexistentes;
V001–V021 aplicadas; 25 grants; dois mappings; oito scopes originais e dois REPLAY
revogados; SERVICE v17, OPERATOR v1, scope 1 v10 e demais originais v1. Validade
original `2026-10-07T22:34:30.615Z`. Catálogo
`7a86e28ea68cecd8ba993bbea8df5c677e0848f27aa7b0412402165071736408`.
6.723 linhas; B54 tem 45 tentativas/30 publicações/70 páginas/74 entradas,
302 reservas e 30 campanhas fechadas. B53 conserva 112 reservas, 95 tentativas,
75 publicações e oito EXTRACTING. O JAR v7 protegido é a revisão B54 de referência.

Não reeditar migration aplicada, limpar target canônico, reinstalar baseline,
executar stale recovery global ou usar partições antigas com lease expirado como
se fossem controles positivos. Preservar os falsos PASS históricos de sink/DQ como
excluídos. Testes desta extensão usam ocorrências e partições sintéticas próprias.

## 2. Lacunas identificadas no código

| Requisito | Código existente e lacuna concreta | Prova necessária |
| --- | --- | --- |
| Seleção de workload | `LocalColetasFretesRuntime` tem duas fábricas; `RuntimeOperationalExecution` escolhe Coletas ou Fretes por ternário | Cada template seleciona seu próprio adapter; template desconhecido recusa antes de I/O |
| Request e fingerprints | `RuntimeOperationalRequest.partition()` também mapeia somente Coletas/Fretes; dependência aceita apenas Coletas→Fretes | Request estrito, namespace/entidade/modo e configuração efetiva vinculados integralmente |
| Identidade/paginação | Observação do runtime usa `/id`; os contratos de domínio de 6399/6906/8656 têm identidades próprias | Separar identificador técnico de paginação de source key do domínio; não universalizar `/id` nem inferir chave por semelhança |
| Manifestos | Mapper, reducer de raiz/filhos, portas e SQL V012 existem; não há adapter JDBC de Manifestos no pacote produtivo de persistência inspecionado | Completar adapters e redução entre páginas com memória limitada e candidato verificável no SQL |
| Cotações | Mapper e JDBC V011 existem; promoção exige `referenceReleaseId` tarifário explícito | Tarifa sintética escopada e vinculada; ausência/revogação/mismatch bloqueiam, sem default nem cálculo financeiro inventado |
| Localização | Mapper/JDBC V014 existem; fallback de volume de Fretes permanece diferido | Aplicar somente a base shadow, mantendo a recusa de relação/fallback não autorizado |
| Recuperação SQL | V018 contém ramificações específicas de Coletas/Fretes em status/recovery/promoção | Recuperar cada nova entidade com recibos reais, sem reextração de ocorrência conhecida nem promover por procedure da entidade errada |
| Direitos reais | Perfil atual contém somente scopes das duas entidades e 25 grants | Delta mínimo com consumers/fences; teste direto SQL e OPERATOR sem escrita |
| Temporal | `RuntimeTemporalOperation`/`RuntimeTemporalBinding` restringem workloads e documentos a Coletas/Fretes/B54 | Políticas e tradutores BACKFILL das cinco, persistência e retomada física |
| Comparação | B54 compara dois conjuntos literais; Q-FND-01/02 têm perfis test-only | Comparar a saída do runtime com referências independentes, mantendo as provas antigas intactas |
| Medição | Q-MED-FND-01 mede os streamers em 16/256/4096 páginas | Exercitar também os cinco mappers, batches e consumidores de staging, com retenção limitada |
| Operação em lote | `Invoke-Bloco54ManualCompletion.ps1` orquestra três casos fixos de prova | Comando manual limitado para lote congelado, sem novo scheduler nem estado concorrente ao SQL |

Ler também ADRs 0024, 0028 e 0032–0037; os catálogos de identidade e de contrato
6399/6906/8656; os catálogos de Manifestos/Cotações/Localização; runbooks
`manifestos-v2-026-shadow.md` e `v2-028-localizacao-cargas-shadow.md`; migrations
V011/V012/V014 e seus testes. Selecionar exercícios antigos somente como contrato:
os que reinstalam baseline não são executáveis contra o catálogo populado atual.
Ler o framework comum `DataExportStagingPipeline`, `DataExportRuntimeWorkload`,
DQ, observabilidade, temporal e os guards V018/V020/V021 antes de estendê-los.
Ler os runbooks `v2-050-fundacao-medicao-local-sol.md`,
`v2-047-fundacao-planejamento-bootstrap-sol.md` e o catálogo
`docs/catalogos/comparacao-bloco54/`. O `clean` e os marcadores de bloco desses
runbooks são históricos: preservar artefatos e usar saída isolada nesta extensão.

## 3. Escopo adicional proposto para adoção

Esta seção é uma autorização proposta no texto de abertura; não é autorização
executada pela preparação deste arquivo. A execução adotada fica restrita a:

- Único banco: `localhost/ETL_SISTEMA_V2_SHADOW`; master somente metadados do alvo
  antes de DDL. Nenhuma conexão a ETL_SISTEMA/esl_cloud/dashboard/outro host.
- Contas existentes `etl_v2_exec` e `etl_v2_view`; DPAPI protegido somente em
  memória para lançar filhos próprios via elevação normal. Nenhuma nova conta,
  senha, grupo, login, certificado de servidor, serviço, firewall ou agenda.
- Fonte sintética externa ao JAR, literal `127.0.0.1`; somente GET de `/info` e
  `/data` dos templates 6908/6389/6399/6906/8656. Token aleatório só em memória,
  sem fonte ESL/GraphQL, redirect, download ou retry manual para esconder erro.
- Novas três entidades somente **BACKFILL/SHADOW_UPSERT**. Preservar os modos já
  existentes de Coletas/Fretes. Não habilitar REPLAY/FORCE/sweep para as novas,
  incremental sem contrato temporal próprio ou dependência entre entidades sem
  relação ratificada. Recusa explícita faz parte do aceite.
  A ampliação temporal divide BACKFILL por períodos e reutiliza esses mesmos
  scopes; não concede INCREMENTAL, BOOTSTRAP, REPLAY ou FORCE às novas entidades.
- Schema aditivo por V022 ou próxima versão livre conferida, refletida no baseline.
  Qualificar upgrade e cauda pendente em rollback, aplicar somente a nova versão,
  conferir em nova conexão. Não modificar V001–V021 nem reinstalar o histórico.
- Preparar primeiro `database/proposals/bloco55-runtime-extension/` com manifesto
  de hashes, estado anterior exato, `apply.sql`, `verify.sql`, recuperação e
  condições de parada. A concessão adotada admite **até sete EXECUTE novos para
  SERVICE**, exclusivamente sobre: `stg.usp_stage_manifesto_observation`,
  `core.usp_prepare_manifesto_candidate_set`, `core.usp_apply_reconcile_publish_manifestos`,
  `stg.usp_stage_cotacao_record`, `core.usp_apply_reconcile_publish_cotacoes`,
  `stg.usp_stage_localizacao_carga_record`, `core.usp_apply_reconcile_publish_localizacao_cargas`.
  Conceder somente os efetivamente necessários, após os guards; não conceder
  `stg.usp_stage_manifesto_reduced_candidate` diretamente ao runtime. Máximo de
  32 grants totais, nenhum DML direto/DDL/ownership/db_owner ou grant novo OPERATOR.
- Até **seis scopes novos**, três workloads × SERVICE/OPERATOR, somente BACKFILL
  no namespace `LOCAL_SHADOW/LOCAL_V2/LOCAL_V2`. OPERATOR conserva apenas observer.
  No máximo 16 scopes totais, incluindo os dois REPLAY revogados de B54. Nenhum
  papel extra nem alteração dos direitos originais. Versões crescem se necessário;
  nunca restaurar versão antiga ou estender a validade original. Se policy/pin
  precisar mudar, preservar comprovadamente as garantias de consumo/recuperação
  antigas; qualquer mudança fora do delta exato exige pacote separado.
- Até três policies DQ sintéticas para BACKFILL, com checks completos e hash;
  uma release tarifária sintética de Cotações, até quatro linhas de tarifa,
  escopo SHADOW explícito, moeda/unidade/rounding sintéticos documentados. Nunca
  promover essas referências a baseline de negócio ou enfraquecer policy existente.
- Nova revisão somente em `C:\ProgramData\EslEtlV2\app-bloco55`, após preparar
  os bytes e manifesto. Administrators/SYSTEM únicos writers, contas restritas RX.
  Preservar `app`, `app-bloco54`, `secrets` e todas as revisões anteriores. Conferir
  entradas/dependências do JAR e ausência de fixture, harness ou bypass no artefato.
- Comparação e inspeção de planos usam o controlador administrativo local apenas
  como observador das ocorrências próprias de B55. Consultas parametrizadas e
  limitadas ao namespace/execuções declaradas; sem grant de SELECT/SHOWPLAN para
  SERVICE/OPERATOR, novas views de consumo, leituras do legado ou scan global.
  Oráculos sintéticos são tabelas temporárias de conexão, até 64 raízes e 128
  observações/filhos por comparação, com rollback/descarte ao fechar a conexão.
  Não alterar o core para fabricar divergência; mutantes pertencem ao oráculo.
  Reservar antes os blocos de 16 entradas/512 derivados necessários à fixture;
  o limite por comparação não aumenta o limite individual de uma unidade.
- Launcher de lotes reutiliza os mesmos grants/scopes. Arquivo manifesto até
  64 KiB, até 20 referências de requests (cada request até 16 KiB), no máximo
  quatro janelas por workload e cinco workloads; sem comando/shell livre. Resumo
  final até 64 linhas/16 KiB. O lote cabe na campanha vigente; não renova prazo.
- A medição multiescala de 16/256/4096 páginas e oito registros por página ocorre
  somente no test-classpath sem HTTP/JDBC, uma JVM por vez, heap limitado a 512 MiB,
  até 120 s por cenário e 15 minutos por rodada de medição. Os ensaios físicos
  continuam com os tetos abaixo. Não rodar Maven/medição junto da campanha física.

Orçamento novo B55: **até 384 unidades**, em **três lotes declarados de 128** e
até **12 campanhas de 15 minutos**. Ledger
próprio `target/bloco55/ledger.jsonl`, reserva durável antes de efeito, sem devolução.
Os 18 restantes de B54 não migram. Incluir instalação, DDL, seed, negação, tentativa,
dependência, reinício e repetição conforme unidades conservadoras do controlador.
Por unidade: 16 entradas, quatro páginas, 512 linhas derivadas. No bloco: até
6144 entradas, 1536 páginas, 196608 derivados e 2048 HTTP; até 1 MiB por resposta.
Até duas JVMs runtime e quatro conexões SQL incluindo controlador/observador;
SQL/request até 30 s, filho até 60 s e 16 KiB de saída. Somente processos próprios.
Sem ampliação automática de teto ou prazo; ao atingir limite, fechar campanha,
concluir trabalho offline e registrar a prova restante sem PASS fictício.

Planejar o lote 1 para integração/schema/JAR, o lote 2 para temporal/comparação/
operação e o lote 3 como margem corretiva justificada por falhas ou mudanças.
Não gastar unidades para atingir a cota. Antes de cada lote seguinte: fechar
campanhas anteriores, conferir preservação e saldo, declarar casos/tetos/hash e
registrar a extensão previamente adotada no ledger. Preparação deste prompt e
testes sem I/O físico não gastam unidades. O budget maior não aumenta concorrência,
bytes por resposta, direitos ou acesso a dados; não permite executar outro domínio.

Não tratar o teto como previsão de suficiência. A dificuldade principal continua
sendo o pipeline de Manifestos e seus reducers entre páginas; falha nessa frente
não impede concluir Cotações/Localização e ferramentas que não dependam dela.

Antes de qualquer efeito novo, conferir alvo, preservação, diff do pacote e
autorização efetiva da sessão. Se cobrir exatamente a ação, executar sem outra
pergunta. Se não cobrir, terminar implementação/testes independentes e apresentar
o pacote completo para decisão; a aprovação é a última etapa dessa concessão.

## 4. Frentes integradas A–J

Sequência: A define contratos/aceites; B e C integram cada vertical; D prova sua
travessia. F–I avançam assim que seus consumidores estiverem disponíveis; E executa
a suíte completa somente sobre o conjunto final; J fecha evidências e estado.
Entregas intermediárias são checkpoints do mesmo B55, não encerramentos artificiais.

### A. Delimitar aceites e completar composição

Produzir matriz requisito original → componente → prova B54 reutilizada → lacuna
nova → teste → aceite. Revisar G06/G07/G08 pela redação canônica: não exigir fonte
real para uma prova exclusivamente de autorização Windows/SQL local, nem tratar
aprovação de laboratório como governança produtiva. Se faltar requisito, nomeá-lo
e apontar sua origem. Os guards de contagem representam fotografias históricas;
não proíbem concluir trabalho comprovado, tampouco autorizam inventar aceites.

Substituir seleções binárias por seleção fechada de workloads concretos com
consumidores reais. Evitar service locator, reflexão, mapper genérico de domínio
ou fallback para Fretes. Cada adapter mantém identidade, contratos, fingerprints,
schema de request, efeitos admissíveis e referência DQ próprios. Input inválido
recusa antes da criação do cliente de negócio. Preservar o caminho Coletas/Fretes.

### B. Construir as três travessias completas

Reutilizar streamer/pipeline/limites. Mapper e batch tipados, uma página/lote em
voo, sem coleção da execução, SQL por registro ou dedupe silencioso na JVM.
Manifestos exige raiz, pick e MDF-e com frescor/presença/reducer entre páginas,
sem materializar a relação Manifesto→Coleta; Cotações exige a release tarifária
vinculada ao request/consumo e ao recibo durável; Localização conserva parsing,
status e fallback diferido. Nenhuma chave, referência ou relacionamento inferido.

Levar as três até candidate set, DQ, aplicação técnica em sombra e recibo próprio.
O nome `publish` de uma procedure não autoriza tabela/view consumidora produtiva.
Conhecer uma ocorrência deve permitir leitura/recovery sem reconstruir gateway
ou extrair novamente. A mesma entidade/request/reference deve continuar vinculada
após restart; uma mudança de material exige nova ocorrência ou recusa explícita.

### C. Integrar segurança, persistência e recuperação físicas

Evoluir SQL direto/status/recovery/observabilidade e os novos consumidores com
fences reais. Não confiar em SESSION_CONTEXT autoafirmável, enum do CLI, permissões
do administrador ou candidate set fornecido pelo chamador. Escopo consumido,
ocorrência, namespace, contrato, DQ, tarifa, lease e vigência precisam concordar.
Preservar o uso único; observer nunca promove ou recupera com escrita.

Preparar, qualificar, aplicar e verificar o pacote da seção 3 somente dentro da
adoção efetiva. Antes de commit, rollback; depois, recuperação por versão nova,
revogação explícita de concessões novas quando necessária e manutenção de dados.
Falha depois de commit exige leitura independente, sem reinstalação cega. Novo
validator de fase deve comparar grants/scopes exatos; não relaxar o histórico B54
para aceitar qualquer catálogo futuro. Registro B54 vira fotografia preservada.

### D. Provar pelo JAR e entregar operação manual

Matriz mínima, com ID/camada/reserva/conta/exit esperado e observado/contagens SQL:

| Família | Casos novos |
| --- | --- |
| AUTH | SERVICE RUN/STATUS e OPERATOR STATUS por nova entidade; OPERATOR RUN negado; template/entidade/namespace trocados; recibo/reference adulterados; modos novos não habilitados recusados |
| PIPE | Cada vertical completa em duas páginas com terminal; duplicata entre páginas, expansão raiz/filhos, nulo/conflito de frescor, fonte parcial e cancelamento sem promoção |
| REF | Cotações com referência válida; ausente, revogada ou de outro escopo recusada; repetição recupera a referência original |
| REC | Queda de JVM própria antes/depois do ponto relevante, nova invocação, no-op/idempotência; ocorrência conhecida faz zero HTTP; lease não é roubado |
| SQL | Chamada direta sem consumo/para outra ocorrência e candidate set indevido recusados; reads limitados, rollback e ausência de sessão/transação residual |
| REG | Coletas/Fretes continuam funcionando; dependência e autorização antigas preservadas; repetir somente regressões afetadas da temporal/recuperação |
| OPS | JAR protegido, diagnose, RUN e STATUS pelo launcher fino; resumo técnico confirmado no SQL, sem menu/daemon/retry automático |

Consolidar casos para caber no orçamento, sem trocar prova física por mock.
Oráculo independente em conexão SQL limitada; saída agregada, sem IDs/payloads.
Registrar separadamente Java sem I/O, test-classpath, JAR oficial, SQL real e
HTTP sintético. Falha de negócio esperada só passa se atingir a fronteira testada:
lease antigo impedindo tentativa não é prova de DQ, tarifa ou sink.

### E. Qualificação final e preservação

Testes focados por defeito, depois uma suíte Java 17 offline completa sobre o
código final, com gates de estilo/arquitetura/cobertura intactos. Não acrescentar
skips nem baixar dependências/feed. Isolar build de B55; POM temporário equivalente
exceto build.directory; DLL somente do cache. Nunca clean de target canônico.

Hashes e inventários antes/depois: arquivos preexistentes, V001–V021, catalog,
grants, mappings/scopes, validade, B53/B54, novas tentativas/publicações e logs.
Separar diff B55 do worktree preexistente. Encerrar somente filhos próprios e
campanhas; UTF-8, secret scan, schema/baseline, validators e diff check obrigatórios.

### F. BACKFILL temporal integrado das cinco verticais

Estender o planejador/binding existente, com políticas versionadas e explicitamente
sintéticas para as três novas entidades. Fixar por contrato o campo de data,
fuso, precisão e tradução de borda; não copiar `by_updated_at` ou a semântica de
Coletas para uma fonte diferente. Quando a borda real não estiver caracterizada,
o tradutor é qualificado apenas contra a fixture de laboratório e a ativação real
continua recusada. Sem campo temporal admissível sequer no contrato local, recusar
esse caso explicitamente e concluir as demais verticais; não inventar filtro.

Filtros candidatos conferidos nos três manifests: `manifests.service_date`
(6399), `quotes.requested_at` (6906) e `freights.service_at` (8656), em faixa
civil YYYY-MM-DD. Os três registram `BUSINESS_DECISION_PENDING` para inclusão,
precisão e DST. Fixar esses comportamentos somente na policy/fixture sintética
versionada do laboratório; conservar a pendência no contrato do fornecedor.

Usar no máximo quatro janelas/64 resumos por leitura, BACKFILL sem avanço de
watermark incremental e sem inventar modo BOOTSTRAP. `plan` continua sem efeitos;
persistência e cada execução protegida consomem recibos próprios. Provar para as
novas três: plano persistido, execução 2 antes de 1, fronteira sem salto, conclusão
da lacuna, retomada por nova invocação sem extração e policy/request alterados
recusados. Exercitar uma borda de calendário compartilhada e os tradutores próprios;
reutilizar as provas genéricas de fuso B54 quando os bytes/contratos não mudarem.

São esperados arquivos de política e exemplos operáveis, exports por janela,
provas SQL de período/continuidade e regressão Coletas→Fretes. Não materializar
relação Manifestos→Coletas ou Localização→Fretes ao organizar a ordem das janelas.

### G. Comparar saídas efetivas do laboratório

Reutilizar perfis/regras Q-FND-01/02 e a base B54, acrescentando um consumidor de
projeções das ocorrências sintéticas realmente aplicadas pelo JAR. O lado observado
vem do SQL dessas ocorrências; o esperado é fixture independente com proveniência,
grão e regras declarados, sem chamar o mesmo mapper/reducer para gerar ambos.
Vincular a projeção à ocorrência/janela publicada; não comparar silenciosamente
o core corrente de uma execução posterior com o recibo antigo. Capturar a projeção
coerente após a aplicação ou usar evidência durável suficiente; falta de vínculo
temporal produz resultado não confirmado, nunca igualdade inferida.

Por vertical, provar igualdade e divergência de pelo menos um campo contratado,
duplicidade, ausência, janela divergente e entrada incompleta. Preservar presença
ABSENT/NULL/VALUE, moeda/unidade e raiz/filho. Não exigir uma métrica financeira
onde não há fonte contratada; classificar como não aplicável e testar outro campo
realmente suportado. Para Cotações, incluir a referência tarifária; para Manifestos,
raízes e filhos; para Localização, parsing/status e proibição de fallback indevido.

SQL calcula conjuntos/contagens/somas admitidas; Java/PowerShell recebe somente
resumos limitados. Cada resultado informa template, versão/hash do contrato,
proveniência sintética, contagens e códigos de divergência, sem payload/ID de
negócio. Dado físico não é alterado para simular erro. Estado máximo:
`LOCAL_RUNTIME_OUTPUT_COMPARISON_PROVEN_SYNTHETIC_ONLY`.

Entregar runner/contrato/relatório reproduzível, mantendo o pacote B54 imutável.
Modo real continua recusado quando faltar oráculo, identidade, V2-041 ou autorização.
Esta prova não fecha paridade real, V2-012a/b/c, bootstrap V2-047 ou cutover.

### H. Medir o fluxo completo e conferir o trabalho no SQL

Reutilizar Q-MED-FND-01; estender seus cenários para o pipeline de cada uma das
cinco verticais, com mapper e batch reais e sink de teste com contagem limitada.
Não substituir pelo teste antigo apenas do streamer nem transportar massa inteira
na JVM. Nas três escalas declaradas, conferir registros/quarentenas/lotes, pico de
uma página/lote em voo, liberações após erro/cancelamento e ausência de retenção
pela execução. Um mutante que retém páginas/lotes deve ser recusado e identificado.

Registrar heap/duração como diagnóstico da JVM; contadores de retenção não provam
sozinhos platô de heap, desempenho real ou escala produtiva. Dedupe/redução SQL
entre páginas recebe suas provas físicas pequenas em D/G, distintas do sink de
medição. Produzir receipt por fluxo com camada, escala, caps, medições e limites.

Para os novos consumidores de candidate set/apply/recovery, inspecionar planos
locais limitados a uma execução própria e índices utilizados, com consulta de
até 30 s e artefato privado até 1 MiB por plano. Pode-se usar estimado sem executar
DML apenas para esse diagnóstico; não chamar plano estimado de desempenho medido.
O plano limitado vai para arquivo privado, sem despejá-lo no stdout do filho;
o limite de 16 KiB da saída sanitizada continua valendo.
Corrigir consulta por registro, varredura de namespace inteiro ou falta de índice
com efeito demonstrado, qualificando migration nova quando necessária. Sem
benchmark pesado, estatísticas globais alteradas ou grant de SHOWPLAN ao runtime.

Estado máximo inclui limites gerenciados e inspeção SQL local comprovados; os gates
V2-050/V2-038 por entidade/saída conservam os requisitos de paridade/escala próprios.

### I. Executar e retomar um lote manual limitado

Entregar um launcher fino com manifesto de referências/hashes de requests já
congelados. O manifesto organiza a operação; ocorrências, decisões, consumos,
leases e resultados permanecem exclusivamente no SQL. Sem banco local de estados,
PID file, serviço, timer, auto-retry ou Agendador de Tarefas.

Validar o lote inteiro antes do primeiro efeito: schema fechado, limites, paths,
artefato/configuração/alvo, IDs coerentes, DAG e referência tarifária. O plano
manual tem até cinco workloads/quatro janelas cada, executados serialmente; cada
invocação usa sua sessão restrita e reserva própria. Não existe capability global
do lote que dispense autorização individual.

Resultado deve separar publicado, já confirmado, bloqueado por dependência,
cancelado, falhou e incerto. Falha Coletas bloqueia apenas seu Fretes dependente;
as três entidades sem essa dependência não herdam um bloqueio global inventado.
Preservar a política de falha explícita. Falha de preflight/integridade/limite ou
risco de vazamento encerra o lote, sem continuação automática.

Provar interrupção própria no meio do lote; retomada explícita com invocationId
novo e os mesmos materiais semânticos; ocorrência conhecida sem HTTP ou efeito
duplicado; request alterado recusado. O manifesto congelado continua imutável e
cada rodada de retomada registra suas novas invocações como recibo, não reescreve
o plano original. Reutilizar o caminho durável do JAR, sem uma recuperação paralela
implementada no PowerShell. O fim do lote não amplia o prazo da campanha.

Entregar exemplo e prova positiva com as cinco, negativo de dependência e resumo
bounded consultável também sob OPERATOR. A comparação G pode consumir seus recibos;
o launcher nunca consulta todas as chaves de negócio. Um lote manual aprovado não
constitui autorização para execução recorrente.

### J. Fechar a entrega e apontar o próximo desbloqueio real

Atualizar primeiro STATES, depois trilha e validators. Marcar aceites somente
após as provas, com escopo exato. Se a extensão local não tiver checkbox próprio,
usar um único subaceite de V2-022 para a integração das três verticais, vinculado
a um único B55; não marcar novamente V2-026/027/028 nem criar seis novos pais.
Recalcular contagens; manter contraprovas de aceite falso, duplicação e painel.

Entregar runbook de operação/recuperação, manifest final, matriz de casos e diff.
Não terminar apenas com plano, adapters incompletos ou uma única vertical se houver
trabalho independente adotado restante. Se alguma frente física exigir concessão
fora do escopo, entregar o pacote pronto e continuar as demais, sem sucesso vazio.

Incluir uma matriz final A–J com entregável, camada exercitada, evidência e limite.
As extensões de comparação/medição/operação ficam associadas ao mesmo B55 e aos
aceites existentes aplicáveis; qualquer subaceite local novo deve ser necessário,
ter escopo único e preservar o pai aberto quando faltar requisito. Não gerar
checklists duplicados para aumentar percentual.

Reutilizar o pacote de comparação B54 e o intake V2-041. Consolidar só os inputs
realmente externos: coordenação/atestado da rotação e saúde do writer; recorte da
fonte, período/oráculos de comparação e aprovações de negócio. Não pedir ao owner
nomes técnicos, banco ou contas que já podem ser verificados. Não conectar à fonte
para descobrir esses dados enquanto o gate correspondente estiver aberto.

## 5. Limites da ampliação conferidos no roadmap

| Possível acréscimo | Decisão desta revisão | Motivo concreto |
| --- | --- | --- |
| Temporal, comparação sintética, medição de pipeline e lote manual | Incluir F–I | Consumidores úteis das mesmas cinco verticais, apoiados em frameworks e contratos existentes |
| Contas a Pagar 8636 | Manter fora | `ant_ils_sequence_code` não foi aceito como chave da raiz; V2-009b/8636 e V2-029 bloqueados |
| Faturas 4924 | Manter fora | ID da linha aceito não resolve título/crosswalk/CT-e/Frete exigidos pela promoção final |
| Inventário 10633 e Sinistros 6392 | Manter fora | Manifestos de identidade conservam a raiz/grão não resolvidos |
| Usuários GraphQL no mesmo pacote | Não ampliar nesta revisão | Outro transporte e semântica de snapshot/cursor; não cabe no contrato BACKFILL Data Export das cinco nem nos sete grants delimitados |
| Relações, cinco fatos e views consumidoras | Manter gates atuais | V2-046/V2-036/V2-037 dependem de vínculos, paridade de entradas e contratos próprios |
| Fonte real, rotação, agenda, release e cutover | Manter fora | V2-041 e coordenação operacional/nominal não são resolvidos por fixtures |

A decisão usa os manifests de identidade 8636/4924/10633/6392, a seção de
dependências de V2-036/037/046/050 no STATES e os limites de Q-BST/Q-MED/Q-FND.
Nenhum JSON sintético preenche uma lacuna de identidade real ou cria completude.

## 6. Como avaliar o avanço

O resultado deve mostrar três novas verticais atravessando o motor local já
existente, com recibos e recuperação comprovados, além das entregas F–I no escopo
local descrito. Ganho de contagem decorre de
aceites reais; não existe promessa de percentual. Os quatro domínios com identidade
ainda bloqueada, as relações, os cinco fatos, views, sweep, fonte real, retenção,
release e corte produtivo continuam com suas dependências próprias. Esta proposta
não os autoriza nem transfere a eles o aceite de uma publicação sintética.
