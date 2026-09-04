# Operar observabilidade, integridade e Data Quality

Este runbook cobre somente o framework local V2-023. Ele não autoriza credenciais, chamadas ESL ou
Raster, banco remoto, policy produtiva, entrega externa de alertas, deploy, purge ou cutover.
V2-041 e V2-045b continuam governando esses efeitos externos.

## Comportamento seguro por default

- V006 não semeia policy, threshold, TTL, owner nominal ou schedule. Sem policy ratificada, DQ e
  publicação falham fechadas.
- O SQL Server calcula cardinalidade, equações, SLA e thresholds. A JVM recebe uma linha agregada e
  nunca carrega o universo de chaves.
- A publicação exige permit de contrato/configuração e permit DQ da mesma execução. O trigger do
  evento de publicação revalida `PASSED`, todos os checks, policy vigente, candidate set e SLA no
  relógio atual do banco dentro do commit.
- A policy e o escopo possuem fingerprints canônicos. Somente a última policy efetiva pode ser
  usada e precisa estar `RATIFIED`; revogar a última deixa o escopo deny-by-default. Thresholds dos
  três checks estruturais são sempre zero.
- Logs, métricas, alertas e health aceitam campos fechados. Não use mensagem livre, stack trace,
  payload, URL, documento, tenant ou identificador de negócio como evidência.
- Resultados DQ, métricas e alertas são duráveis. Não os apague manualmente; retenção produtiva
  depende de V2-045b.

## Limites e evidência permitida

O budget de log é explícito por instância estática de componente/processo: no máximo 1.000.000 de
eventos primários e 256 MiB estimados, mais no máximo um resumo de exaustão se ele ainda couber no
teto de bytes. O root do Logback fica `OFF`; apenas os dois adapters Data Export listados na
configuração alcançam os appenders. Métricas e alertas aceitam sequências 1..4.096 por
execução/correlação. A métrica possui exatamente 23 campos escalares e equações fechadas de
staging/aplicação. A policy possui exatamente quatro checks comuns:
`COUNT_EQUATION`, `PAGE_TERMINALITY`, `PROMOTION_RECONCILIATION` e `QUARANTINE_SLA`.

O diagnóstico detalhado exige `TOP(N)` entre 1 e 32. A saída permitida contém apenas ordinal/código/
estado/reason code do check, contagens, basis points e horário. Não copie valores de parâmetros,
chaves ou linhas para logs, tickets, handoffs ou artefatos versionados.

## Interpretar readiness

`ctl.usp_observe_platform_health` e `PlatformHealthProbe.readiness` retornam um único snapshot:

| Estado | Reason code | Ação local |
|---|---|---|
| `UP` | `PLATFORM_READY` | Nenhuma falha agregada conhecida. Isso não prova completude da fonte. |
| `DEGRADED` | `RUNNING_STALE` | Investigar a execução pelo control plane, sem forçar publicação. |
| `DOWN` | `DQ_INCOMPLETE` | Tratar avaliação parcial/ausente; não publicar nem fabricar resultado. |
| `DOWN` | `DQ_FAILED` | Revisar somente contagens e checks sanitizados; corrigir causa e repetir. |
| `DOWN` | `QUARANTINE_SLA_EXCEEDED` | Acionar o owner-papel do SLA; preservar a evidência. |
| `DOWN` | `SQL_UNAVAILABLE` ou shape inválido | Restaurar a dependência local; indisponibilidade nunca vira `UP`. |

Falha de sink não reduz a severidade da causa. Observabilidade marcada como não crítica pode gerar
`DEGRADED`, mas falha na prova DQ ou SQL permanece bloqueante.

O timeout JDBC limita a execução do statement e precisa ter segundos inteiros. Ele não limita por
si só aquisição de conexão, login ou socket. Ao compor V2-022, Operações/DBA devem fornecer limites
explícitos de login/socket e o runtime deve cancelar o statement cooperativamente; até lá, o
framework não alega essa composição.

## Validar offline

Use JDK 17 para os gates Maven. O gate SQL aceita exclusivamente `localhost`, Windows
Authentication e `ETL_SISTEMA_V2_SHADOW`; todos os cenários executáveis são sintéticos e
rollback-only.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
.\scripts\validation\Test-ObservabilityDataQualityManifest.ps1
.\scripts\validation\Invoke-ProgressiveDataGate.ps1
```

O último comando recompõe V001–V007, executa concorrência, os SHOWPLANs, os exercícios 022–024 e a
extensão tipada de Usuários no exercício 027, além do fence de sidecar tardio no exercício 029.
O 023 aplica V006 sob a role `v2_migrator`; o 024 separa limite absoluto/percentual, igualdade e
revalidação temporal de SLA no health/trigger; o 025 compila os cinco entrypoints, exige seeks e os
índices governados, zero warning operacional e nenhuma referência a outro database. Sucesso também
exige publicação idempotente, rollback integral diante de DQ reprovada e retorno do banco ao estado
anterior.

## Diagnóstico e retomada

1. Preserve `execution_id`, policy version/fingerprint e candidate set já vinculados; não altere a
   ocorrência para fazê-la passar.
2. Consulte primeiro o snapshot agregado de readiness. Se necessário, solicite somente a amostra
   sanitizada e limitada da procedure de observação.
3. Corrija a causa na etapa dona: paginação/terminalidade, staging/promotion, quarantine ou policy.
4. Repita a avaliação com os mesmos parâmetros apenas quando o estado persistido for idêntico. Retry
   divergente falha e precisa de nova ocorrência governada.
5. Só prossiga com o permit emitido pelo engine após `PASSED` completo. Nunca desabilite o trigger
   fora dos exercícios históricos rollback-only que isolam gates anteriores.

A correlação de log do streamer síncrono guarda somente hash opaco, restaura frames em ordem LIFO e
não atravessa threads. Se V2-022 introduzir executor/future, propague a referência opaca
explicitamente no task boundary; não use `InheritableThreadLocal`, MDC com ID bruto ou contexto
ambiente sem fechamento.

Pare imediatamente se o alvo não for o banco local autorizado, se surgir dado real, se a análise
exigir credencial/rede ou se a correção depender de policy/owner produtivo. Registre apenas alcance,
reason code, contagens e o owner-papel necessário.
