# V2-022 — Motor local de Coletas/Fretes (Bloco 51)

> Atualização Bloco 53: qualificação física concluída, V001–V017 instaladas e frentes Windows/SQL implementadas. Consulte o [registro integrado](v2-022-bloco53-integrado.md); as provas e limitações abaixo são históricas do bloco original.


## Escopo registrado antes da implementação

Pedido explícito do owner em 07/09/2026: compor dispatcher, ingestão, candidate set, DQ,
promoção e recuperação local em um pacote verificável. A fotografia anterior de zero rotas
elegíveis não considerava essa separação local, agora autorizada. V2-022a, V2-020/021/023,
V2-042a, V2-043/044 e as bases shadow V2-010/011 satisfazem os predecessores locais.
V2-022 e V2-022b continuam abertas; nenhum hold externo muda.

Aceite: despacho tipado na ordem do registry; dependência exige recibo de publicação da
ocorrência e janela corretas; execução independente confirmada é preservada; componentes
reais de travessia, mapper, staging e gates; cancelamento e heartbeat cooperativos; replay
com origem explícita; falhas de auditoria e lease fechadas; publicação incerta só pode
retomar pelo protocolo idempotente com os mesmos permits. Provas offline devem distinguir
explicitamente simulação de JDBC de concorrência/recuperação física de SQL Server.

## Decisões aplicáveis e divergências históricas

- ADRs 0007/0009: one-shot; fronteira contígua exclusivamente incremental, calculada no SQL.
- ADRs 0010/0022: CLI oficial deny-all; identidade e auditoria de autorização externas pendentes.
- ADRs 0011/0012/0014: sem retry de transporte no dispatcher; contrato e DQ obrigatórios,
  falha da auditoria invalida a travessia; aggregate não inventa sucesso.
- ADRs 0018/0023/0027/0032: IDs escopados, presença tri-state, lotes físicos de 100;
  candidatos relacionais e sidecars separados, sem crosswalk ou completude inferidos.
- O SQL V003 é autoritativo: start registra PLANNED e EXTRACTING na mesma transação,
  adquirindo lease. O comentário histórico de persistência somente PLANNED é inexato.
- V004/V010/V013 já possuem retry evidence-bound do apply; não se cria publicação genérica
  nem se altera migration. Um resultado perdido não autoriza FAILED/CANCELLED persistido
  sobre uma publicação que pode ter sido confirmada.
- Contratos 6908/6389 continuam transitórios. A tradução de bordas inclusivas da origem e
  os campos sintéticos de decisão de Fretes não se tornam contratos do fornecedor.

## Limites operacionais

Sem credenciais, fonte externa, deploy, scheduling, push, dashboards ou alteração da CLI.
O perfil JDBC autorizado permite somente auditoria em conexão que bloqueia commit e reverte;
não autoriza a prova Java/JDBC física integral de promoção. Essa prova permanece pendente.
Retomada local com permits vivos não equivale a reconstituição de permits após perda do processo;
essa última requer um contrato de leitura durável e composição operacional próprios.

## Evidência

Bloco 51/P02M concluído localmente em 07/09/2026. V2-022/TRAVESSIA_STAGING_LOCAL
reconhece separadamente a entrega anterior (ADR 0032/974 testes); a nova subentrega
V2-022/INTEGRACAO_LOCAL_COLETAS_FRETES fecha apenas o pacote definido acima.

- Testes focados: 76, zero falhas/erros/skips; 27 são cenários novos do motor.
- `spotless:apply clean verify` offline: BUILD SUCCESS, 1001 testes, zero falhas/erros,
  quatro skips esperados; final em 13:42:58 -03:00, duração 01:46. Enforcer, Spotless,
  Checkstyle, arquitetura e todas as regras JaCoCo passaram sem alterar thresholds.
- Temurin 17.0.20.1+1; POM temporário equivalente ao canônico, mudando somente `<build><directory>`
  para `target/runtime-validation-20260907`. Não houve parada de processos do editor.
  Surefire, JaCoCo e JAR permanecem nessa saída ignorada; log em `target/runtime-verify.log`.
- Doze validadores estáticos passaram: FirstWaveContractCatalog, FirstWaveIdentityCatalog,
  ColetasV2010DecisionCatalog, ColetasV2010ShadowVertical, FretesV2011DecisionCatalog,
  FretesV2011ShadowVertical, ControlPlaneManifest, StagingPromotionKernelManifest,
  StagingLifecycleManifest, ObservabilityDataQualityManifest, SchemaFoundationManifest e
  Gpt56ChatTrail (todos `Test-*.ps1`). Nenhum exercício físico foi invocado.

## Arquivos e comportamento entregues

- `RuntimeDispatcher`, `RuntimeExecutionSession`, bindings e resultados tipados: DAG serial,
  start no momento do despacho, heartbeat inicial e cooperativo, aggregate, cancelamento,
  preservação independente e recuperação idempotente com permits vivos.
- `RuntimeExecutionRequest`, `RuntimeWorkloadRegistry`, `RuntimePlanPersistence`: origem
  explícita de replay, rejeição de ocorrência/chave duplicadas antes de IO e abertura do ciclo.
- `DataExportRuntimeWorkload`, `ControlPlaneDataExportExtractionAudit`, `ContractRunGuard`:
  binding de ocorrência, `/info`, observações, páginas auditadas e transições condicionadas.
- `LocalColetasFretesRuntime`, `ShadowPromotionGateway` e as duas portas de promoção:
  composição dos casos de uso/adapters existentes; nenhuma regra de domínio foi copiada.
- `LocalRuntimeIntegrationTest` e `RuntimeSyntheticJdbc`: verticais reais, batches 100/100/51,
  dependência bloqueada, falhas de stage/auditoria/DQ/lease, cancelamentos, replay, nova
  tentativa, relógio regressivo e ack perdido de start/transição/prepare/apply.
- ADR 0033, README, STATES, trilha e validator: aceites locais separados e histórico preservado.

## Próximo bloco e rollback

Bloco 52/P02R, não iniciado: readback durável tipado e reconstituição verificada após perda
da JVM, com protocolo de recuperação e timeouts/cancelamento da composição. Essa preparação
local é executável sem identidade externa. Validação Java/JDBC física integral exige outra
autorização; o perfil permitido hoje bloqueia commit e cobre somente auditoria.

O resultado agregado é uma fotografia: recuperar uma sessão retorna novo resultado, sem
liberar automaticamente dependentes que já foram bloqueados. Heartbeat cooperativo não
prova renovação durante chamadas bloqueantes mais longas que a lease. Fonte, parâmetros de
janela e policies reais continuam responsabilidade da futura composição autorizada.

Rollback: retirar bindings da composição local e reverter somente os arquivos deste bloco
após revisar o diff; preservar alterações preexistentes. Não há DDL, migration, escrita
produtiva ou implantação a desfazer. A evidência é local não commitada.

## Fechamento de segurança e revisão

Scanner self-test: 9/9. Varredura: 1061 candidatos, 1060 textos, um binário conhecido,
zero finding, zero arquivo oversized ou não inspecionado. UTF-8 estrito sem BOM de 331
arquivos textuais alterados/novos passou (inclui alterações preexistentes); `git diff --check`
passou. O validator da trilha passou no baseline e rejeitou cinco mutações: conclusão local
ausente, conclusão operacional indevida, rota AGORA duplicada, retirada da proibição de rede
e número de bloco incorreto. Nenhum threshold ou teste foi desativado.

Os quatro skips da suíte são três provas de symlink não suportado no filesystem e um receipt
V2-050 que exige opt-in. Não são skips dos 27 cenários novos do motor. O POM temporário foi
removido depois de reconfirmar a equivalência XML. Não se alega revisão humana ou commit.

## Evolução posterior — Bloco 52/P02R

A limitação histórica dos permits vivos foi tratada pelo pacote integrado de
[recuperação durável local](v2-022-recuperacao-duravel-local.md). O novo caminho consulta
resumos persistidos e confirma recibos ou continua sob revalidação SQL preparada,
sem reextração nem reemissão pública de permits. Inclui recibo tipado atômico de
Coletas em V015 e prova com novos processos Java. Isso não altera os limites da prova
física ou da composição operacional deny-all. A baseline atual está no STATES.md.
