# B60 — implementação offline e pacote físico de Usuários

**B60_IMPLEMENTED_OFFLINE_PHYSICAL_APPROVAL_PENDING**. Código, SQL versionado,
controladores e regressão offline entregues. A aprovação física da seção 4 é a
dependência remanescente: o usuário aprova o hash do
[pacote concreto](../../../database/proposals/bloco60-local/README.md), permitindo
ao administrador Windows já provisionado executar a matriz e a recuperação.
Nenhum SQL/preflight foi executado. Nenhum aceite físico agregado foi atribuído.

| Frente/requisito | Implementação | Camada e comando/teste | Resultado observado/evidência | Dependência física |
|---|---|---|---|---|
| A — mesma origem, protocolos explícitos, identidade imutável | ADR0041/V024: bindings de origem e execução; catálogo/fingerprints preservados | `Test-Bloco60SqlContract -SelfTest`; contratos Java | 14 contraprovas estáticas; [SQL](../../../database/migrations/V024__bind_source_protocols_and_users_runtime.sql) | Seis RUN, mutações de tenant/entidade/protocolo/contrato/configuração, 056 e preservação |
| B — terminalidade, auditoria, prepare/DQ/selo/apply/recovery | V024; entrypoints V007 preservados com fences; leitura Users por execução/protocolo | SQL estático + adapters reais com JDBC sintético; Maven verify | [verify-03](../../../target/bloco60-local/verify-03/exit.json), [Java vinculado](../../../target/bloco60-local/java-result.json) | Compilação/transações reais, DQ incompleta, recibos adversariais, duas rotas de rollback do sufixo |
| C — autoridade/JAR | Janela B60, manifest/ACL, consumo real, CLI oficial; quatro variantes do artefato | `AdministeredArtifactManifestTest`, testes de Windows authority; `Test-Bloco60JarOffline` | Quatro comandos do JAR: diagnóstico=0, recusa sem autoridade=20; [bundle-v2](../../../target/bloco60-local/bundle-v2/variants.json) | RUN/BACKFILL, REPLAY, FORCE_RUN, STATUS e cancelamento Windows/SQL; consumos e negativas |
| D — perda de processo/concorrência/current-history | Probe separado usa composição/JDBC reais; HALT/ACK; barreira e readback SQL independentes | `RuntimeUsersPhysicalProbeTest`; testes de controlador/oráculos | RED de consume preservado e corrigido: descarte após conclusão JDBC; 11 controles de processo/ledger e 14 de oráculos | 74 casos congelados; seis HALTs, retomadas, lease, concorrência com dois PIDs bloqueados e um efeito |
| E — tentativas GraphQL e seis verticais | Observer imediatamente antes de sendAsync; contador compartilhado, sem payload/cursor/ID | `GraphQlHttpExecutorTest`, gateway HTTP real loopback, integração runtime; `Test-Bloco60Controllers` | 22 HTTP sintéticos nas seis verticais no teste do servidor; retry/IO/refusa pré-envio; suíte global abaixo | Reconciliar servidor × diagnóstico × ledger × SQL; sem alegar desempenho produtivo |
| F — evidência e sucessão | Inventário inicial, snapshots exatos, manifests, guards, diff próprio e recibo | Validadores B60/B59 e cadeia privada, scanner/autotestes, UTF-8/AST, forward/reverse check | [gates](../../../target/bloco60-local/final/verification.json), [recibo](../../../target/bloco60-local/final/receipt.json) após conferência final | Novo fechamento físico somente após execução aprovada |

Estas frentes não são novos checkboxes. A existência desta tabela não substitui
o resultado dos comandos. `verification.json` registra exits da revisão final;
o recibo só é emitido quando esses checks passam e é verificado separadamente.

Maven `verify-03`: **1.390 testes, zero falhas, zero erros, cinco skips**, 198
suites, Java 17 offline, heap máximo 512 MiB, build próprio, sem clean compartilhado.
Formatter, Checkstyle, arquitetura e cobertura passaram no mesmo verify.
Os cinco skips preexistentes são três provas de symlink indisponível neste
filesystem, `CotacoesSourceCommandTest` sem opt-in `bloco57.cotacoes.command` e
`MeasurementFoundationReceiptTest` sem opt-in explícito V2-050. Nenhum novo skip.

Evidências falhas preservadas: RED da telemetria, erro de import Checkstyle,
verify-01 com allowlist de schema sem V024 e RED do ponto de consumo. As correções
não alteraram os algoritmos de domínio nem afrouxaram os critérios; verify-02
passou com 1.389 testes e verify-03 acrescentou a contraprova do consumo.
Contagens de testes dirigidos 50/55/51 têm overlap e não são somadas à suíte.

Inventário inicial: 1.572 arquivos, 386 artefatos B59, 808 sourceBindings e 211
artefatos Java B59 íntegros; recibo histórico
`36a8b88b7efbcfd52859214cb91c647cea759335be46e684bcad7df5f210ecba`.
Snapshots preservam bytes anteriores apenas dos caminhos enumerados alterados.
Validador B59 lê essas revisões históricas, mantendo seus hashes originais.

Orçamento físico: zero OPEN, zero reservas, zero SQL, zero JVMs Windows da
campanha, zero fonte real; não há resultado físico desconhecido. As chamadas
HTTP dos testes offline são sintéticas e não consomem o orçamento físico ainda
não aberto. O pacote possui limites e vigência próprios, sem refund/renovação.

Roadmap preservado em 67/115, 48 pendentes, 191 rotas e zero AGORA. Não fechar
V2-022 pai, Q-USR-01, V2-012a/b/c, V2-047, V2-013, V2-050, V2-038 ou release/cutover;
não reabrir V2-033. GraphQL segue transitório e SHADOW_UPSERT_ONLY. Não há prova
da fonte real, completude, snapshot, ausência, incremental ou desativação.

Revisão operacional e limitações:

- A prova SQL preparada é prefixo V023 mais duas rotas revertidas do sufixo;
  não é uma instalação física nova desde banco vazio.
- Flags replay/force são do principal; seu alcance temporário nas permissões
  existentes está explicitado no pacote. Não se assume granularidade por workload.
- Probes de ACK descartam confirmação no cliente; HALTs encerram a JVM própria.
  Nenhum desses cenários físicos ocorreu ainda.
- Bytes de resposta são oferecidos; commits internos são reserva conservadora.
  Ausência de estatística após HALT não vira zero; readback e reserva permanecem.
- Compensação preserva V024, dados e evidências; não apaga o ensaio. Incerteza,
  estado misto ou drift impede repetição e qualificação.

Pendências locais independentes: nenhuma após recibo offline válido. Etapa
dependente: aprovação única do pacote e posterior prova física completa. O
aceite `QUALIFICACAO_FISICA_LOCAL_USUARIOS` continua condicionado a essa prova.
