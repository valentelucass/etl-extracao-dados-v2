# Bloco 59 — integração local de Usuários ao runtime

Escopo adotado em 09/09/2026: A–F de
`target/preparacao-bloco59/PROMPT-BLOCO-59-DETALHADO.md`.
Estado: B59_LOCAL_CLOSED somente com final/receipt.json íntegro e passed=true. Aceite:
INTEGRACAO_LOCAL_USUARIOS_TESTADA, somente a fatia de V2-022.

## Matriz de requisito, implementação e prova

Classes abaixo em `src/test/java/br/com/esl/etl/v2/`. Comandos completos, exits
e relatórios em `target/bloco59-local/<run>/`. A prova final Java é
`java-result.json`: somente seu run vincula a revisão final de src/pom.

| Frente | Requisito e implementação | Critério → teste | Camada / resultado / pendência |
| --- | --- | --- | --- |
| A | RuntimeUsersRequest fechado, configuração/limites/fingerprint próprios; Data Export preservado | RuntimeUsersOperationalRequestTest: rejectsIncompatibleClosedRequests, freezesBindingsAndDoesNotIncludeNewAuthorizationInOccurrenceFingerprint; RuntimeOperationalRequestTest; guard DATA_EXPORT_FINGERPRINT_CHANGED_AND_REHASHED | Java offline e material DE comparado ao inicial; users-directed-02 e verify-03 verdes; sem pendência local |
| B | LocalUsuariosRuntime compõe gate/parser/streamer/extrator/mapper/staging; auditoria e token único com lease | RuntimeUsersIntegrationTest: rootRunsRealUsersPipelineAfterUniqueConsumptionAndStatusDoesNotComposeSource, invalidTraversalCannotSealPrepareOrApply, ungatedParserResponseCannotFabricateCompletion; RuntimeUsersSessionTest: leaseIsRenewedDuringTraversalAndTokensRemainIdenticalAcrossGateAndStreamer; GraphQlOperationalAuditTest | Adapters reais, transporte/JDBC sintéticos: identidades INTEGER/STRING, nomes ABSENT/NULL/VALUE, operação estática/página20/bindings10, cursores inválidos, caps, falha parcial e auditoria; implementação completa |
| C | Promoção tipada, candidate/DQ, selo após DQ e recovery vinculado | RuntimeUsersIntegrationTest: rejectedQualityCannotSealOrApplyUsers, validDurableReceiptRecoversUncertainOutcomeWithoutRefetchOrDuplicateApply, uncertainAttemptWithoutSealRequiresReadbackAndCannotRepeatEffects, recoveryRejectsTamperedReceiptWithoutSourceOrApply, recoveryMustRejectNullAggregateEvenWhenJdbcWouldConvertItToZero, replayUsesNewExecutionAndStartsWithoutAnyPriorCursor; RuntimeUsersSessionTest: usersLivePermitCannotRepeatAnUncertainApplyWithoutDurableReadback, typedNoopReceiptIsReturnedWithoutSubstitutingGenericInsertedCounts | Bindings reais de prepare/apply com recibos sintéticos, sem algoritmo current/history em Java; SQL preparado com seis guards estáticos, não executado/compilado |
| D | Root congela scope, passa pelo WindowsSqlRuntimeAuthorization concreto/CONSUME e executor real; STATUS sem fonte | RuntimeUsersIntegrationTest: authorizationCounterexamplesRefuseBeforeAnyBusinessComposition, changedFrozenScopeIsRefusedBeforeBusinessComposition, statusForAbsentOccurrenceDoesNotEvenBuildSourceGateway; positivo B/D compartilhado | Harness conserva fronteira; JAR help/dry-run exit0 e run/status UNCONFIGURED exit20; quatro provas repetidas e verdes no JAR verify-03; sem pendência local |
| E | Cancelamento, recuperação, cinco verticais e caracterização B58 | RuntimeUsersIntegrationTest: operationalCancellationStopsWithoutSealOrPromotion, persistenceFailuresCannotSealOrApply; ExtrairUsuariosGraphQlTest; RuntimeFiveVerticalPipelineTest e suíte completa | users-directed-01:70/0/0/0; users-directed-02:113/0/0/0; verify-01:1377/0/0/5; verify-02:1378/0/1/5, falha preservada; verify-03:1378/0/0/5, exit0 após correção do fixture loopback; sem pendência local |
| F | Sucessão exata, história, cadeia privada, guards, scanner, UTF-8, diff e recibo | Test-Bloco59Local -IncludePrivateEvidence; Test-Bloco59LocalGuards; Test-Bloco55Integrated -IncludePrivateEvidence -RequireComplete; checks finais | Baseline PASS com1622 vínculos; cadeia privada, dez guards B59 e nove históricos verdes; fechamento válido somente com recibo final/receipt.json íntegro, após checks do último delta |

## Falhas reproduzidas e causas corrigidas

Todos os runs abaixo tiveram exit1, preservado. Não foram convertidos em PASS
por wrapper, skip ou redução de gates.

| Run | Resultado observado | Correção |
| --- | --- | --- |
| red-request-01 / 02 | Formatter / Checkstyle; não são RED funcional | Formato/imports |
| red-request-03 | Schema Data Export recusava GraphQL | Extensão tipada própria |
| red-audit-01 | 4 testes, 3 falhas: conclusão fabricada/contagens/auditoria aceitas | Lifecycle, bindings, sequência e confirmação persistida |
| red-cancel-01 | 12 testes, 1 falha: clock disparava cancelamento e staging ainda ocorria | Checagem por nó e após construir todo o lote |
| red-recovery-01 | 14 testes, 1 falha: recovery só admitia Data Export | GRAPHQL/USERS_SNAPSHOT, BACKFILL ou REPLAY/FULL |
| red-dq-seal-01 | 3 testes, 1 falha: selo durável antes de DQ reprovada | Ordem específica de Usuários; Data Export preservado |
| red-null-receipt-01 / 02 | Primeiro: erro de compilação do teste; segundo:38 testes/1 falha real, NULL como zero | requiredCount verifica wasNull e valor não negativo |
| red-graphql-attempts-01 | 1 teste/1 falha: zero GraphQL alegado sem medição | total=UNOBSERVED; governor preservado |

`verify-01/java-result.json` e diagnósticos JAR preservam a prova anterior à
última correção. O diagnóstico GraphQL não afirma zero tentativas HTTP; páginas
e nós seguem auditados, mas o contador legado só mede Data Export.

## Camadas, limites e recuperação

Usuários permanece GraphQL transitório, individual(enabled=true), id/name e
pageInfo, página máxima20. hasNextPage=false é terminalidade local, sem prova de
snapshot/completude/ausência. observed_at e janela são técnicos; sem updatedAt,
incremental, template inventado, desativação, sweep ou relações novas.
SHADOW_UPSERT_ONLY; sem publicação para consumidores, bootstrap ou cutover.

Cinco skips anteriores ao bloco: três testes de links simbólicos não suportados
neste Windows, um opt-in de medição V2-050 e o comando opt-in de fonte de Cotações
B57 ausente. Métodos/motivos completos no java-result. Nenhum skip foi adicionado.

Current/history, dedupe, conflito e hash/no-op continuam set-based em V007.
Recibos sintéticos verificam protocolo JDBC; não comprovam transações,
concorrência ou durabilidade física. V022 ainda não admite Usuários. Delta
concreto em `database/preparation/bloco59/usuarios-runtime.sql`, fora das
migrations/baseline, não aplicado/compilado. Próxima dependência: adoção versionada,
alvo/estado, rollback, identidade/grants, policy/scope e source_catalog compatíveis
sob autorização própria, conforme runbook. Campanhas B53–B57 não são herdadas.

Inventário inicial:1532 arquivos em `target/bloco59-local/initial/` e
`initial-inventory.json`. Recuperação usa esse estado observado, nunca Git HEAD.
Snapshots dos18 arquivos alterados preservam as revisões anteriores;
manifests/recibos históricos imutáveis. Diff e reverse --check em final/; recibo vincula o último delta,18 revisões,
40 adições e1514 preservados. Nenhuma reversão aplicada.

Zero API real, SQL físico, .env/credenciais, instalação, serviço, agenda,
deploy/cutover, commit/push ou consumo/renovação de budget. 67/115, 48 pendentes,
191 rotas e zero AGORA preservados. A–F não criam checkboxes nem aceites de pais.
Não reabre V2-033 nem fecha V2-022 pai, Q-USR-01 e demais qualificações externas.

Decisão: [ADR0040](../../adr/0040-usuarios-graphql-no-runtime-operacional-local.md).
Operação: [runbook B59](../../runbooks/v2-022-bloco59-usuarios-local.md).

## Fechamento, comandos e artefatos

- Maven: `target/bloco59-local/Invoke-Java.ps1 -Run verify-03 -Build build-verify-03 -Goals @('spotless:apply','verify')`; --offline, Java17, heap512MiB, sem clean; exit0,196 suites,1378 testes,0 falhas/erros,5 skips; todos os gates de cobertura aprovados.
- Verify-02 falhou em permitsOnlyOneAuxiliaryInfoCallBeforeOpeningAnotherConnection: chamada ao servidor loopback indisponível em13,154s. investigate-loopback-01 passou8/0/0/0. A revisão encontrou accept com timeout5s iniciado antes da construção do HttpClient; sete fixtures agora constroem o probe antes de submeter a resposta. Timeouts/assertivas/retries preservados. A IOException original teve causa sanitizada; não se afirma subtipo não registrado. Verify-03 validou a correção na suíte inteira.
- Java: [resultado e motivos dos skips](../../../target/bloco59-local/java-result.json),808 sourceBindings e211 artefatos. JAR: [quatro resultados](../../../target/bloco59-local/jar-result.json).
- Cadeia: Test-Bloco55Integrated -IncludePrivateEvidence -RequireComplete atravessa B55/continuidade/B56/B57/B58/pós-B58/B59 preservando fotografias. Dez contraprovas B59 (aceite falso, efeitos, budget, progresso, rebase, diretório, alteração alheia, checkbox e fingerprint DE) e seis SQL estáticas. Os nove guards pós-B58 executam na cópia inicial, sem confundir a revisão histórica com a atual.
- Scanner: zero achados; onze autotestes. Schema foundation preservado. Trilha mantém67/115,48pendentes,191rotas,0AGORA. Comandos/exits finais em checks-index.json e diretórios ali vinculados.
- [Diff próprio](../../../target/bloco59-local/final/diff.patch), [revisão](../../../target/bloco59-local/final/review.md), [recuperação](../../../target/bloco59-local/final/recovery.md), [recibo final](../../../target/bloco59-local/final/receipt.json). Ausência ou hash divergente impede fechamento aprovado. O recibo final é emitido somente depois dos gates, incluindo o último delta documental.

Sem pendência local A–F quando o recibo acima estiver íntegro. Dependências físicas
ou etapas não adotadas permanecem explicitamente separadas na seção de camadas.
