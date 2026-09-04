# Importar, ratificar e revogar referências

Este runbook descreve o contrato futuro de V2-035a. A fundação atual é offline: publica o registro
idempotente do envelope sem `GRANT`, mas não publica transporte/importador de conteúdo. Não execute
export, importação real, ratificação produtiva ou ativação sem as autorizações do `STATES.md`.

## Pré-condições

Exija, para uma única família e escopo:

1. export autorizado pelo `DATA_OWNER`, sem segredo ou documento bruto no Git;
2. formato e colunas exatamente iguais ao manifesto de referências;
3. `source_artifact_ref` opaco, contagem, SHA-256 e vigência finita;
4. documentos já convertidos pelo mecanismo keyed aprovado, com `token_scheme_version`, e chaves
   canônicas já normalizadas pelo owner, com `normalization_version` explícita;
5. papel aprovador distinto tanto do papel autor quanto do papel importador;
6. janela de mudança, banco-alvo e rollback autorizados por Plataforma/DBA e Operações quando o
   escopo não for local.

V2-041 em `EXTERNAL_HOLD` proíbe usar credenciais ou buscar a baseline externamente. Nenhum valor
encontrado em `.env`, procedure ou log substitui o export do owner.

## Validação offline do pacote

O importador futuro deve recusar o pacote antes de qualquer promoção quando:

- exceder 100.000 linhas, 16 MiB canônicos incluindo headers ou microbatch de 1.000 linhas;
- não for UTF-8 sem BOM/LF canônico ou contiver controle/Unicode inválido;
- houver coluna desconhecida/duplicada, campo obrigatório ausente, `NULL` em campo não-nullable ou
  valor fora do tipo/tamanho;
- identidade `(family_code, scope_code, release_version)` já existir com outro fingerprint;
- grão, intervalo, faixa CEP, rota, token, uma das 27 UFs ou cardinalidade forem inválidos;
- uma regra temporal ou faixa CEP se sobrepuser a outra na mesma release.

Tokens só são identidade junto de `token_scheme_version`; aliases/cidades canônicas só são
identidade junto de `normalization_version` (ou `city_normalization_version`). Ambos participam do
grão, ordenação, PK e lookup. A comparação é `EXACT_BIN2` sobre versão + valor. Não transforme a
chave recebida durante a importação e não aceite versão por aproximação: algoritmo/versionamento
autorizados e fixtures de paridade do owner são pré-condição externa do consumidor. Versão
desconhecida não encontra regra e falha fechada, sem fallback.

Siga literalmente o manifesto: todos e somente os arquivos da família, inclusive vazios; UTF-8 NFC
sem BOM; LF; header lógico exato; todo valor não nulo citado por RFC4180; `\N` não citado para nulo;
tipos escalares canônicos; e ordenação do grão por bytes UTF-8 unsigned. Calcule o SHA-256 de cada
CSV e depois o índice de pacote `GOVERNED_REFERENCES_V1`; esse hash deve ser simultaneamente
`source_fingerprint` e `content_fingerprint`. A fixture dourada do manifesto é o teste de
interoperabilidade. Não carregue o dataset completo em `List`, `Map` ou `Set`; stageie microbatches
limitados e faça dedupe, overlap e validação set-based no SQL Server.

Em `BRANCH_ATTRIBUTION`, o CSV contém `branch_release_scope_code` e `branch_release_version`. O
importador resolve ambos contra uma release `BRANCH_OPERATIONS` e só então preenche
`branch_reference_release_id`; serializar o identity local torna o artefato não portátil e é
proibido.

## Importação e ratificação

Quando o importador owner-only for aprovado em migration posterior, a transação de conteúdo deverá:

1. chamar `ref.usp_register_reference_release`; `CREATED` inicia e `REPLAY` reutiliza somente o
   envelope integralmente igual, enquanto qualquer divergência é conflito;
2. adquirir o lock da release e confirmar que ela ainda não possui recibo;
3. inserir somente nas tabelas tipadas da família;
4. validar contagem, grão, FKs, intervalos, tokenização e ausência de overlap;
5. inserir exatamente um `ref.reference_import_receipt` com contrato, fingerprint, linhas e bytes;
6. confirmar release, conteúdo e recibo no mesmo commit ou reverter tudo, ainda sem ratificação.

Depois do commit, um aprovador independente confere fingerprint, contagem e paridade. Somente uma
segunda transação, sob o lock de ratificação, acrescenta o ledger append-only para SHADOW ou
PRODUCTION. Importação e ratificação não compartilham transação, principal nem papel; falha de
aprovação mantém a release inerte e auditável. `approval_fingerprint` é o SHA-256 da própria
evidência de aprovação e não precisa ser igual ao fingerprint do conteúdo; essa evidência deve
atestar explicitamente o `content_fingerprint` exato do recibo, já selado pelo banco contra o
envelope e a cardinalidade física. Para calendário, a ratificação ainda prova cobertura
contínua, coerência entre dia útil/fim de semana/feriado e último dia útil exato. Quando o início da
vigência não for útil, inclua apenas o contexto contínuo até o último útil anterior, limitado a 31
dias de lookback; conte essas linhas no `source_row_count`, bytes e fingerprint, mas nunca as exponha
fora do intervalo ativo. Para atribuição, prove a release de filiais ativa no mesmo escopo.

Não conceda `SELECT`/DML direto ao runtime. O consumo futuro deverá usar `release_id` explícita,
data civil explícita e uma view/procedure set-based aprovada por ownership chain. Para região,
consulte primeiro CEP e somente sem match consulte cidade/UF. Para tarifa, zero nunca representa
ausência. Em `DRIVER_OWNERSHIP`, aplique membership documental e exceção tokenizada de owner; depois,
entrada NULL ou vazia após trim de U+0020 resulta em `THIRD_PARTY`, enquanto texto não vazio exige
alias exato do contrato de ownership. Em `VEHICLE_DRIVER_CONTRACT`, resolva o alias exato do veículo
e aplique a exceção de owner somente à classe `AGGREGATE`; caso contrário, entrada de motorista NULL
ou vazia após trim de U+0020 vira `UNSPECIFIED`, e texto não vazio exige alias exato, antes da matriz
veículo–motorista. Texto não vazio desconhecido ou célula ausente falha fechado. Antes de publicar
um consumidor, o owner deve autorizar a política de cobertura (inclusive decidir se todo o 2×4 é
obrigatório), fornecer fixtures de paridade e exigir sua validação set-based no futuro entrypoint;
nunca volte a substring/nome bruto. Para status, use o normalizador versionado e não invente match
ou terminalidade para desconhecido.

## Revogação e recuperação

Revogar acrescenta uma linha a `ref.reference_release_revocation` para um `activation_scope`; não
apaga nem altera conteúdo nem revoga automaticamente o outro escopo. Publique nova versão para
correção e reexecute paridade antes de promovê-la.

Uma release de `BRANCH_OPERATIONS` não pode ser revogada enquanto uma `BRANCH_ATTRIBUTION` ativa a
referenciar no mesmo escopo. Revogue primeiro a atribuição e depois a filial. Não inverta a ordem;
os dois caminhos concorrentes são serializados e o perdedor deve reler o estado e falhar fechado.

Em falha antes do commit, faça rollback integral e repita com a mesma identidade/fingerprint. Em
commit incerto, consulte apenas identidade, fingerprint e contagem sanitizados: iguais significam
no-op; qualquer divergência exige quarentena operacional e investigação pelo owner-papel.

## Evidência permitida

Registre apenas família, versão opaca, contagens, fingerprints, estados e reason codes. Nunca
registre linha, nome, documento, token, chave de tokenização, URL, tenant, cursor ou payload real.

O gate local é:

```powershell
.\scripts\validation\Invoke-ProgressiveDataGate.ps1
```

Ele recusa servidor remoto e usa `localhost/ETL_SISTEMA_V2_SHADOW` como alvo canônico. O probe
concorrente cria via `master` somente um banco descartável com nome allowlisted
`ETL_V2_REF_PROBE_<guid>`, aplica V001+V008, remove suas fixtures e o descarta em `finally`; os
demais exercícios são rollback-only. Nenhum banco ou objeto do probe pode permanecer após o gate.
