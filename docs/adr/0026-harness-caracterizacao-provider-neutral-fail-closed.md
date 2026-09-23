# ADR 0026 — Harness de caracterização provider-neutral e fail-closed

- Status: Aceito somente para a fundação local offline de V2-012
- Data: 2026-09-05
- Escopo: Q-FND-01/Bloco 39, harness `test-only`, sem caracterização de fornecedor

## Contexto

As futuras caracterizações de Coletas 6908, Manifestos 6399, Cotações 6906 e Usuários GraphQL
partem de contratos distintos. Elas não compartilham necessariamente `id`, `updated_at`, cursor,
paginação, filhos, expansão, timestamp, grão físico ou tradução temporal. Uma abstração orientada
ao menor denominador comum apagaria decisões já aceitas; uma abstração orientada a uma entidade
inventaria garantias nas demais.

A fundação precisa ser exercitável sem fornecedor e sem confundir fixture sintética com evidência
externa. Precisa também permitir que uma entidade falhe sem alterar o resultado das outras e
impedir que aceitação parcial produza sucesso agregado.

## Decisão

Adotamos um núcleo imutável e provider-neutral somente em escopo de testes. Cada perfil contém sua
configuração explícita de contrato, identidade, escopo, raiz/grão, filtros, paginação, ordenação,
temporalidade, status, limites e checks. Regras específicas permanecem em classes separadas por
entidade, sem switch central de política de negócio.

Adapters são isolados por fonte/oráculo. Esta fatia implementa exclusivamente adapters in-memory
para fixtures marcadas como sintéticas; não existe adapter real habilitado. Data Export não depende
de configuração GraphQL e GraphQL não depende de configuração ESL.

Cada observação é comparada de forma pura e acumulativa. Propriedade ausente, binding divergente,
evidência incompleta, métrica incoerente ou limite excedido gera resultado fail-closed apenas para
a entidade correspondente. O resumo positivo exige exatamente os quatro resultados elegíveis,
todos ainda `PREPARED_NOT_EXECUTED` e `ORACLE_REQUIRED`.

Fingerprints usam JSON canônico; mapas equivalentes não dependem da ordem de entrada. O receipt é
allowlisted, sanitizado, recebe tempo e identificador sintético por injeção e escreve somente sob
`target`, por substituição que não modifica o inode de um hardlink preexistente.

Fixture aceita significa somente que a estrutura sintética satisfaz o perfil local. Ela não
significa caracterização executada, prova do fornecedor, autorização externa, completude,
snapshot ou paridade real.

## Consequências

- Perfis e resultados continuam independentes e podem evoluir somente mediante suas decisões
  canônicas e oráculos próprios.
- O único resultado agregado positivo da fundação é
  `FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES`.
- Relatórios não carregam URL, endpoint, token, cabeçalho, payload, cursor bruto, ID de negócio,
  nome, documento, placa, filial, valor bruto, segredo ou conteúdo de registro.
- A execução dos quatro Q-*-01 permanece uma etapa futura e explicitamente autorizada; esta ADR não
  libera comportamento produtivo.

## Distinção de evidência

Os perfis e fixtures versionados são evidência local do desenho e das contraprovas. Somente uma
fatia posterior, com autorização, escopos e oráculo próprios, poderá produzir observação externa
sanitizada. O gate `ORACLE_REQUIRED` impede que os dois tipos de evidência sejam promovidos como
equivalentes.
