# ADR 0012 — Drift de contrato antes da promoção

- Status: Aceito e implementado offline por V2-044; baselines por fonte pertencem aos contratos das
  verticais
- Data: 2026-08-31

## Contexto

Uma resposta HTTP válida não demonstra que a fonte manteve schema, identidade ou forma de
paginação. O endpoint Data Export `/info` descreve campos e filtros, mas isoladamente não prova o
shape efetivo de `/data`, a presença da chave ou a semântica do tipo retornado. Da mesma forma, uma
query GraphQL armazenada sem a resposta observada não protege envelope, cardinalidade ou
nullability. Promover staging após validar somente transporte permitiria publicar dados sob um
contrato diferente daquele registrado no início da ocorrência.

V2-044 fecha esse risco com fixtures sintéticas, pois V2-041 mantém credenciais e sondas externas
suspensas. V2-024 liga o adapter GraphQL transitório a essa fronteira. V2-033 promove somente as
quatro folhas do documento de Usuários para `IMPLEMENTED_IN_SHADOW`/`SHADOW_UPSERT_ONLY`; as 15
folhas dos sidecars de Coletas/Fretes permanecem `SYNTHETIC_ONLY`/`OBSERVATION_ONLY`. Isso não é
baseline observado no fornecedor nem autorização externa; baselines reais por documento continuam
nas tarefas donas das fontes.

## Decisão

Cada `SourceContractRelease` contém dois componentes independentes e um fingerprint composto:

- metadata sanitizada e ordenada de campos/filtros de `/info` ou de uma query GraphQL estática
  aprovada, incluindo categoria de tipo fechada e hash do texto de tipo declarado exato;
- shape de resposta observado, com raiz, envelope, paths, tipos JSON, cardinalidade,
  presença/optionalidade, nullability e path da chave, sem valor ou contagem de negócio;
- versão semântica do contrato e fingerprints SHA-256 com separação de domínio e encoding binário
  length-prefixed. O encoder UTF-8 recusa sequências UTF-16 malformadas em vez de substituí-las;
  vetores sintéticos fixos protegem o encoding contra mudança acidental.

O documento GraphQL aceito é uma operação `query` nomeada, somente leitura, com variáveis e seleção
fechada. Mutation, subscription, introspection, fragment, alias, literal, comentário, operação
anônima e variável não declarada são recusados. O texto é descartado depois da canonicalização; o
release conserva apenas o fingerprint. Introspection ampla não é uma alternativa implícita e exige
autorização própria.

O profiler possui tetos explícitos de profundidade, paths e nós, além dos limites de bytes e JSON
estrito do transporte. Um preflight de tokens aplica o teto de nós antes da construção da árvore.
No runtime, o profiler só pode materializar paths presentes no baseline ou paths aditivos
previamente autorizados por uma `ContractAllowance` exata. Um mapa dinâmico somente é opaco quando
um `ContractOpaquePath` versionado declara explicitamente aquele path de objeto; arrays, escalares,
descendentes implícitos e qualquer objeto apenas "sem filhos conhecidos" não recebem opacidade. O
conteúdo opaco ainda é percorrido values-only para aplicar tetos de nós e profundidade, sem reter
nomes ou paths de suas chaves. Essa fronteira impede que IDs ou códigos de negócio virem
fingerprint, diff ou alerta. O modo permissivo existe apenas para autoria offline com fixtures
sintéticas já sanitizadas e não é aceito pela configuração runtime.

O `ContractValidator` produz diff limitado e classificado. São sempre bloqueantes:

- campo obrigatório ausente;
- raiz, chave, filtro, documento aprovado ou tipo declarado alterado;
- tipo JSON ou cardinalidade incompatível;
- campo novo obrigatório ou cuja optionalidade não pôde ser provada.

Adição comprovadamente opcional, primeiro shape concreto de um campo antes observado apenas como
nulo e widening de nullability são compatíveis somente quando a política vinculada ao mesmo
baseline contém a assinatura e o descriptor estrutural exatos da mudança. Como uma página é apenas
uma amostra, presença em todas as linhas, conjunto parcial de tipos e uma página somente nula podem
ser projetados para esse descriptor somente quando são subconjuntos dele; path, escopo,
nullability, cardinalidade e tipos fora do aprovado continuam bloqueados. Cada mudança aceita emite
um alerta sanitizado uma única vez por ocorrência. Uma permissão para outro path, escopo ou shape
não é reutilizada.

Metadata e ao menos uma resposta populada são obrigatórias. A travessia inteira valida página por
página em estado limitado e precisa de evidência terminal própria da fonte; observação parcial,
salto/repetição de página ou falha do consumer/audit invalida tudo. Para Data Export, somente as
formas array são promovíveis neste estágio, porque possuem página terminal vazia sequencial. Raiz
objeto continua normalizável fora do gate, mas não pode configurar uma ocorrência promovível sem
um contrato futuro de terminalidade. Para GraphQL, V2-024 valida `pageInfo` em cada página, vincula a
resposta e a conclusão auditada à mesma ocorrência e exige terminal local `hasNextPage=false`.
Cursor ausente/repetido, página vazia, cap, falha do consumer ou audit invalida a evidência. Em
nenhum caso terminalidade de paginação prova completude do dataset, snapshot ou cobertura para
sweep.

`ContractExecutionBinding` vincula release, política, configuração runtime e limites ao
`ControlPlaneStart`. Somente `ContractRunGuard.complete()` emite um `ContractPromotionPermit`, e
isso ocorre depois de metadata, página populada e terminalidade válidas. O permit transporta
versões e fingerprints dos dois componentes e da configuração, nunca payload.

No Data Export, o fingerprint runtime é derivado da `DataExportSourceConfiguration` efetiva e
inclui identidade técnica da origem, endpoint apenas dentro do hash, timezone, transporte, limites,
retry e resiliência; o token é excluído. A factory operacional carrega esse fingerprint junto da
configuração de observação no bundle. O gate compara fingerprint, template, form, raiz, chave,
limites e boundary com o binding antes de qualquer delegate `/info` ou `/data`; bundle manual ou
configuração divergente falha sem abrir I/O.

No GraphQL, o fingerprint runtime exclui token e inclui identidade, endpoint somente dentro do hash,
limites, retry e policy ESL. A factory exige a mesma instância de governor e cancelamento do ciclo;
o gate package-private compara documento, fingerprint, limites e boundary antes do delegate. Para
Coletas/Fretes, qualquer folha `OBSERVATION_ONLY` faz uma travessia local válida registrar
terminalidade, mas `ContractRunGuard.complete()` recusa emitir permit de promoção. Em Usuários, as
quatro folhas precisam estar integralmente sob `SHADOW_UPSERT_ONLY`; somente então o permit pode
alimentar o current/history de sombra de V2-033. Publicação, sweep, completude e cutover continuam
proibidos por gates próprios.

As duas transições do kernel SQL exigem o mesmo permit persistido na ocorrência:
`core.usp_prepare_staged_execution` e `core.usp_apply_reconcile_publish_execution` recebem versão e
SHA do contrato e da configuração. Sob os locks da ocorrência, antes de fast path de retry ou
mutação, comparam os quatro valores em collation binária; divergência gera erro sanitizado 51418.
Isso é fencing de consistência da ocorrência, não assinatura criptográfica nem autorização do
caller. O exercício 011 prova rollback-only que mismatch não prepara, aplica, publica, duplica
evento nem avança pointer/lease.

V2-023 adiciona um segundo gate sem enfraquecer esse fencing: a preparação continua exigindo o
`ContractPromotionPermit`; a aplicação exige também um `DataQualityPromotionPermit` da mesma
execução. O trigger transacional revalida `PASSED`, policy e candidate set inclusive no caminho de
retry, antes de aceitar o evento de publicação.

## Regras de evolução

- Qualquer alteração intencional no encoding incrementa a versão do componente e atualiza os
  vetores fixos após revisão.
- Mudança semântica do contrato incrementa sua versão mesmo quando os componentes ainda são
  estruturalmente representáveis.
- Allowlist é pequena, versionada, vinculada a um baseline e revisada a partir de fixture
  sanitizada; não é wildcard nem modo permissivo permanente.
- Baseline real requer a rodada autorizada da tarefa dona da fonte. Evidência sintética não deve ser
  descrita como contrato observado no fornecedor.
- Replay/retry refaz a validação da travessia e reutiliza o efeito SQL apenas quando o permit
  coincide exatamente com o persistido.

## Consequências

- `/info`, query ou HTTP 200 isolados não liberam staging para promoção.
- Drift tardio ou evidência terminal inválida impede tanto preparação quanto publicação.
- Valores, payload, IDs, cursores e documentos não entram em release, permit, alerta ou logs.
- Java mantém apenas uma resposta/microbatch e resumos estruturais limitados; dedupe,
  reconciliação e promoção permanecem set-based no SQL Server.
- V2-041 continua bloqueando credenciais, sondas externas, release, deploy e cutover; este ADR não
  altera esse estado.

## Alternativas rejeitadas

- **Confiar somente em `/info` ou introspection:** não comprova o shape efetivamente retornado nem
  identidade.
- **Guardar payload de exemplo:** amplia retenção e exposição sem ser necessário para detectar
  drift.
- **Descobrir qualquer chave de objeto no runtime:** confunde schema com mapa dinâmico e pode
  versionar dado de negócio.
- **Allowlist por categoria ou prefixo:** aceita mudança diferente daquela revisada.
- **Validar só antes do primeiro insert ou só na publicação:** permite staging/promoção sob permits
  divergentes e retries inconsistentes.
- **Tratar página vazia como prova de completude:** confunde fim da travessia com cobertura do
  dataset.
