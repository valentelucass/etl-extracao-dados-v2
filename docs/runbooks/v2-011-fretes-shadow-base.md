# V2-011 — Fretes 6389 base shadow

Estado local pretendido: `IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING`. Esta fatia implementa somente a raiz base de Fretes no shadow. `V2-046a` e `V2-046b` continuam abertas, e `V2-046b` permanece a única dona do crosswalk final Coleta–Frete.

## Limite de evidência

O contrato local 6389 comprova somente estes sete paths Data Export: `/data/id`, `/data/updated_at`, `/data/reference_number`, `/data/fit_p_m_pck_sequence_code`, `/data/corporation_sequence_number`, `/data/finished_at` e `/data/fit_dpn_performance_finished_at`. Frescor (`cte_created_at`, `cte_issued_at`, `criado_em`, `servico_em`), status, CT-e e finalizações são apenas envelopes sintéticos de decisão, sempre rotulados `SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE`; não são alegados como contrato externo.

O sidecar GraphQL conserva exatamente os dez paths aprovados no catálogo, em staging independente com no máximo 100 edges. Ele não preenche identidade, raiz nem frescor. `fit_p_m_pck_sequence_code` e `pickItemId` são somente candidatos append-only com presença/proveniência: não há join, FK, lookup `TOP 1`, crosswalk ou backfill.

## RED preservado

Antes de criar mapper, domínio, persistência, migration ou manifesto, foi executado:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FretesV2011ShadowVertical.ps1
```

Resultado esperado e observado: exit code 1, com `FRETES_SHADOW_VERTICAL_MISSING` pela ausência de `FreteDataExportRecordMapper.java`. O reason code permanece estável no validator para impedir falso verde por artefato ausente.

## GREEN local

Sob JDK 17 configurado apenas no processo, o gate focado final executou 29 testes (mapper, sidecar, frescor, batch, page request e gateways), com zero falhas; Enforcer, Spotless e Checkstyle também passaram:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd --version
.\mvnw.cmd --offline --batch-mode --no-transfer-progress -Dtest='Frete*Test,JdbcSqlServerFreteGatewaysTest' test
```

O `clean verify` offline final executou 684 testes, com zero falhas, zero erros e um skip esperado; Enforcer, Spotless, Checkstyle e todos os checks JaCoCo passaram. O primeiro `clean verify` havia ficado vermelho somente porque o pacote JDBC novo estava abaixo dos thresholds de cobertura. A correção adicionou testes comportamentais de resultado agregado válido/malformado, cancelamento e rollback com falha suprimida, sem reduzir limites de cobertura.

O gate estático completo é:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FretesV2011ShadowVertical.ps1
```

## Prova física opt-in

A execução SQL é fail-closed e só aceita Windows auth no alvo literal `localhost/ETL_SISTEMA_V2_SHADOW`. O runner consulta `master` antes de tocar o banco alvo, roda 044/045, compara contagens antes/depois e executa a prova concorrente em duas sessões. Toda escrita sintética do exercício é revertida.

```powershell
pwsh -NoProfile -File .\scripts\validation\Invoke-FretesV2011ShadowValidation.ps1 -ExecuteLocalShadow
```

Resultado observado em 2026-09-06: validator 044 e exercício 045 verdes; contenção confirmada em
duas sessões, outro `environment` isolado e lock readquirido após rollback. A comparação física
terminou em `estado=0|0|0`, igual ao estado anterior para raiz, staging e candidato relacional.

Sem `-ExecuteLocalShadow`, o runner encerra com `FRETES_SHADOW_VERTICAL_MISSING`; isso é intencional. A prova cobre constraints, replay/no-op, out-of-order, empate divergente, terminalidade, tri-state, performance, sidecar, candidato não resolvido, isolamento por environment/tenant, contenção e reacquisição após rollback. Ela não comprova escala produtiva, completude, relação, paridade, publicação, sweep, bootstrap ou cutover.

## Rollback e diagnóstico

O arquivo `045_exercise_fretes_shadow_vertical_rollback.sql` contém `BEGIN TRANSACTION`/`ROLLBACK TRANSACTION`; o probe concorrente também faz rollback em todas as sessões. Qualquer divergência de alvo, objeto, grant, contagem final ou saída esperada interrompe o runner. Como os dados de prova são sintéticos e revertidos, nenhum ID ou payload de negócio deve aparecer em evidência ou documentação.
