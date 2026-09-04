# ADR 0020 — Referências governadas sem default produtivo

- **Status:** aceito para a fundação offline V2-035a
- **Data:** 2026-09-01

## Contexto

O legado comprova comportamentos úteis em calendário, status de Coleta, aliases/filiais,
classificação de frota, atribuição de filial, exclusões de cubagem, região logística e tarifa por
rota. Ele não é, porém, uma baseline produtiva confiável: parte das linhas existe apenas em
procedures, parte não está semeada no Git e todo o conjunto pode ter mudado em produção. Copiar
identificadores, nomes ou valores observados criaria um default produtivo sem owner, vigência ou
paridade.

O SQL legado também mistura estado corrente com seed, usa horizonte fixo no calendário, aceita
regras regionais ambíguas e transforma combinação tarifária ausente em zero. Esses comportamentos
não são preservados.

## Decisão

V008 cria uma fundação tipada no schema `ref`, sem inserir linha alguma:

- `ref.reference_release` registra família, escopo, versão, fingerprint, cardinalidade,
  proveniência, motivo, papel autor e vigência civil `[início,fim)`;
- `ref.usp_register_reference_release` registra/reobtém o envelope de modo idempotente, sem receber
  `GRANT`; envelope igual retorna `REPLAY` e identidade igual divergente falha;
- `ref.reference_import_receipt` sela, antes da ratificação, fingerprint, contagem física e limite
  de bytes. Conteúdo e recibo compartilham lock da release-mãe, e nenhum conteúdo entra depois do
  seal;
- ratificação e revogação são ledgers append-only por `(release, activation_scope)`. O papel
  aprovador é distinto tanto do autor quanto do importador; SHADOW e PRODUCTION podem
  promover/revogar a mesma release independentemente, e vigências concorrentes são rejeitadas por
  escopo. `approval_fingerprint` é o SHA-256 da evidência de aprovação, não uma cópia do fingerprint
  do conteúdo; a evidência externa deve atestar o `content_fingerprint` exato do recibo, que o banco
  já sela contra `source_fingerprint`, cardinalidade física e cronologia;
- todo consumidor futuro deverá receber `reference_release_id` explicitamente. Não há ponteiro
  “corrente”, wildcard, filial default, tarifa zero ou fallback produtivo;
- conteúdo usa tabelas por domínio, chaves BIN2, índices de lookup e triggers insert-only que
  serializam a release-mãe e rejeitam intervalos sobrepostos;
- região logística separa CEP e cidade/UF em tabelas diferentes. Isso torna o seletor exclusivo por
  construção e mantém a precedência CEP → cidade/UF no contrato consumidor;
- tarifa é direcional, `DECIMAL(19,4)`, com moeda, unidade e arredondamento. Ausência não produz
  valor; `UNAVAILABLE` exige montante nulo;
- documentos de filial, pagador e frota são apenas tokens keyed versionados de 64 hexadecimais.
  `token_scheme_version` integra grão, PK e lookup; a comparação exige versão + token em
  `EXACT_BIN2`, e uma versão desconhecida não casa. Documento bruto, chave de tokenização e
  identificador real não pertencem ao banco/repositório;
- aliases de filial/frota e cidade/UF recebem uma chave canônica já normalizada pelo owner.
  `normalization_version` — ou `city_normalization_version` — integra grão, PK e lookup; V008 não
  transforma essas chaves nem publica normalizador genérico. O match é `EXACT_BIN2` por versão e
  chave, sem fallback. Algoritmo, versão autorizada e fixtures de paridade são gates externos para
  qualquer importador/consumidor futuro;
- classificação de frota mantém duas políticas independentes. `DRIVER_OWNERSHIP` avalia membership
  documental, depois exceção tokenizada de owner; contrato ausente ou vazio após trim de U+0020
  resulta em `THIRD_PARTY`, e texto não vazio usa alias exato do contrato de ownership.
  `VEHICLE_DRIVER_CONTRACT` resolve primeiro o alias de veículo; a exceção de owner só vale quando
  a classe do veículo é `AGGREGATE`; sem exceção, contrato de motorista ausente ou vazio após trim
  de U+0020 vira `UNSPECIFIED`, enquanto texto não vazio exige alias exato, e então aplica a matriz
  tipada veículo–motorista. V008 permite uma matriz esparsa porque ainda não publica importador ou
  consumidor; célula ausente e texto não vazio desconhecido falham fechados. A política de cobertura
  (inclusive se deve preencher todo o 2×4) e sua paridade são gates externos do owner. Nome bruto,
  substring e heurística implícita são proibidos;
- atribuição de filial possui FK para uma release de `BRANCH_OPERATIONS`, deve caber nas duas
  vigências e só pode ser ratificada quando a dependência estiver ativa no mesmo escopo. O pacote
  portátil referencia a release de filial por `scope/version`; o `BIGINT IDENTITY` é resolvido no
  banco e nunca participa do hash. A revogação da filial é rejeitada enquanto houver atribuição
  ativa no mesmo escopo; a ordem segura é revogar atribuição e somente depois a filial, com ambos
  os interleavings concorrentes serializados;
- `ref.ufn_normalize_pick_status_v1` fixa o comportamento útil do legado para o domínio ASCII:
  trim, lowercase e troca de hífen/cada espaço por underscore. Status desconhecido não recebe
  terminalidade nem match inventado;
- `ref.v_status_coleta_seed_candidate_v1` e
  `ref.ufn_calendar_seed_candidate_v1` são candidatos determinísticos inertes. O calendário recebe
  intervalo explícito de no máximo 3.660 dias, independe de clock, `LANGUAGE` e `DATEFIRST`, calcula
  feriados móveis a partir da Páscoa e não fixa 2032 como horizonte. Se o início ativo não for útil,
  a candidata inclui somente o contexto contínuo necessário até o último dia útil anterior, limitado
  a 31 dias de lookback. Essas linhas entram em `source_row_count`, bytes canônicos e fingerprint,
  mas o consumidor enxerga apenas o intervalo ativo. Ratificação exige cobertura diária contínua,
  coerência fim de semana/feriado e a referência exata do último dia útil anterior;
- V008 publica apenas o registro idempotente do envelope, sem autoridade, e não publica importador
  de conteúdo nem `GRANT`. `v2_runtime`, `public` e `v2_migrator` continuam sem leitura/DML direto
  em `ref`; migrations futuras poderão expor somente entrypoints aprovados.

O manifesto `database/manifest/governed-references.json` é o contrato máquina-legível do futuro
importador: schemas físico e lógico exatos das 14 tabelas, UTF-8 NFC sem BOM, LF, RFC4180 com todo
valor não nulo entre aspas, `\N` não citado para nulo, escala/data/booleanos canônicos, ordenação
portátil por bytes UTF-8, índice de pacote sem colisão e SHA-256. Uma fixture dourada prova bytes,
header, nulo, vírgula, aspas, acento, caractere suplementar, hash da tabela e hash do pacote. Os
limites são 100.000 linhas, 16 MiB incluindo headers e 1.000 itens em voo; validação relacional é
set-based e a transação de conteúdo é all-or-nothing.

## Consequências

A fundação offline pode ser testada integralmente com dados sintéticos e rollback. Ela não prova
que qualquer linha corresponde à produção e não autoriza consumo, publicação ou cutover.

Antes de ratificar conteúdo mutável são obrigatórios export autorizado, contagem e fingerprint
sanitizados, tokenização aprovada, owner-papel, aprovação distinta e paridade. Políticas de feriado
operacional e os candidatos determinísticos também precisam de ratificação; “determinístico” não
significa “produtivo”.

Correções e revogações não atualizam ou apagam releases. Publica-se uma nova versão ou acrescenta-se
uma revogação. A retenção de evidências e a autoridade real do importador/aprovador permanecem gates
externos.

V2-035b continua separada: V008 não cria dimensão nem objeto em `pub`.
