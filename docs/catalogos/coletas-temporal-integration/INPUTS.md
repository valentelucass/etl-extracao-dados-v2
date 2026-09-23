# Inputs representativos de Coletas

O mecanismo executável é
`scripts/validation/Test-ColetasTemporalRepresentativeInputs.ps1`.
Não consulta fontes nem concede promoção. O exemplo em
`src/test/resources/contracts/coletas-temporal-integration/representative/`
é integralmente sintético, inclusive seu oráculo de eventos separado.

```powershell
pwsh -NoProfile -File scripts/validation/Test-ColetasTemporalRepresentativeInputs.ps1 -SelfTest
pwsh -NoProfile -File scripts/validation/Test-ColetasTemporalRepresentativeInputs.ps1 -InputPath <pacote-local>/package.json
```

Sem InputPath e sem SelfTest, exit2 e EXTERNAL_INPUT_MISSING são esperados.
Uma entrada divergente gera erro INPUT_* específico; não há retry ou chamada de
rede. A validação de fixtures não aceita automaticamente o pacote real.

## Contrato versionado de entrada

`package.json` versão1 declara entidade coletas, modo SYNTHETIC ou
REPRESENTATIVE, fonte e tenant explícitos, data civil da janela e duas capturas.
Cada captura possui captureId, executionId, kind, contrato/versionamento,
fingerprint, terminalidade e artefato relativo com SHA256. O documento capturado
repete esses vínculos. Tipos esperados: DATA_EXPORT_6908 e GRAPHQL_TEMPORAL.
Não inferir identidade pela semelhança do texto: cada binding liga os dois
captureIds e chaves tipadas INTEGER/STRING ao caseId do oráculo.

O oráculo fica em artefato separado, com owner, originKind e originReference.
REPRESENTATIVE exige INDEPENDENT_EVENT_LOG e atestação de Segurança declarada.
Cada expectativa repete escopo, data, capturas e chaves e declara estado/instante
esperados; transições incluem estado e instante anteriores. Arquivo igual a uma
captura, vínculo divergente, caso ausente ou valor discordante são recusados.
Hashes atestam integridade, não autenticidade, independência material ou aceite.
A declaração securityAttestationValidated precisa ser conferida pelo responsável
contra a evidência vigente de V2-041; o parser não autentica essa declaração.

O parser compara epoch second e nove dígitos de nanos. Offsets equivalentes
concordam; 1ns diferente diverge. Presença nativa e da referência admite
ABSENT/NULL/VALUE e distingue parse válido de inválido. A ausência do instante
nativo não transforma a data civil em instante real do status.

Cobertura mínima16: aberto; transições done, finished e cancelamento; nativo
ausente/nulo/inválido; offset equivalente; submilissegundo; empate; ordem inversa;
expansão da raiz; duplicata; referência ausente/nula/inválida. Os dados concretos
precisam sustentar o rótulo de cada caso. Fixtures demonstram o mecanismo;
qualificação representativa ainda exige revisão material das evidências.

## Dependência externa concreta

Fornecer um pacote protegido com correspondências qualificadas entre capturas
terminais, janela/casos conhecidos e expectativas provenientes de eventos
independentes, identificadas pelo responsável. Acrescentar a evidência vigente
de Segurança/V2-041 e o aceite nominal exigido por COL-TIME-01/Q-COL-01 e
V2-012a/b/c. Não colocar payloads ou identificadores reais no Git.

O pacote representativo válido retorna INPUTS_VALID_REQUIRE_NOMINAL_REVIEW;
realQualificationAccepted e operationalPromotionAuthorized permanecem false.
Somente essas entradas permitem reavaliar o propósito de nova coleta. A rodada
condicional de até seis requisições não foi iniciada: faltam esses requisitos,
e repetir a ausência nativa de 6908 não acrescentaria a prova necessária.
