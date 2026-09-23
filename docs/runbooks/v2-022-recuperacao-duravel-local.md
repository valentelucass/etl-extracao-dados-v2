# Bloco 52/P02R — recuperação durável local

> Atualização Bloco 53: qualificação física concluída, V001–V017 instaladas e frentes Windows/SQL implementadas. Consulte o [registro integrado](v2-022-bloco53-integrado.md); as provas e limitações abaixo são históricas do bloco original.


## Registro anterior à implementação

Escopo: pacote A+B+C+D de recuperação de Coletas/Fretes no motor local. Dependências
do Bloco 51: dispatcher serial, casos de uso reais, guard com auditoria terminal,
candidate set, DQ fail-closed e publicação idempotente. Nenhuma operação externa,
SQL físico, grant, credencial, CLI operacional, cutover ou nova vertical é autorizada.

Leitura integral: AGENTS, STATES, CONTEXTO_GLOBAL, trilha de chats, prompt do Bloco 52,
ADRs 0009/0010/0011/0012/0014/0022/0032/0033 e runbook do motor local. SQL e
consumidores são consultados nos trechos envolvidos. As contagens 974/949 são
fotografias históricas; a entrada do bloco registra 1001 testes e quatro skips.

Invariantes: identidade exata inclui ocorrência, ciclo/plano, namespace, modo,
janela, replay e fingerprints; erro de leitura nunca é ausência. Não há reextração
de staging parcial, ressurreição terminal, roubo de lease ou sweep stale na entrada.
Relógio e exclusão são SQL. Um recibo confirmado prevalece sobre cancelamento.

Estados: PUBLISHED íntegro devolve recibo; EXTRACTING/EXTRACTED/STAGED/PROMOTED
somente continuam com evidência durável completa e lease vigente; ausência de
evidência, DQ inválida, terminalidade e contradições recusam com reason tipado.
Uma nova tentativa exige nova ocorrência/chave; replay exige origem explícita.

Aceites a comprovar: ambas as verticais reais; perda de ack de start/transição/
prepare/apply; nova instância e processos Java independentes; zero reextração e
efeitos duplicados; adulteração de vínculos e mudança entre leitura/comando;
cancelamento, prazos JDBC e fechamento; resultados independentes/dependências;
verify Java 17 e validadores estáticos. Prova sintética não comprova atomicidade,
locks, relógio ou restart de SQL Server físico. Nenhum aceite está marcado aqui.

## Implementação e uso interno

A composição local fornece RuntimeRecoveryPort ao construtor adicional de
RuntimeDispatcher. O caminho normal grava selo após guard.complete() e
verifyCompletion(), antes de EXTRACTED/STAGED. Para retomar, reconstruir plano,
ContractExecutionBinding e DataQualityPolicyReference da mesma ocorrência e chamar
recover(plan, cancellation, expectations). Não há necessidade de reconstruir um
guard aprovado, conexão antiga, permit ou sessão. A composição oficial não chama
essa API e permanece deny-all. O construtor antigo conserva a API legada sem selagem.

| Evidência/estado persistido | Resultado/ação local |
| --- | --- |
| Ausente | NOT_FOUND; nenhum start automático |
| EXTRACTING sem conclusão selada | IN_PROGRESS/PARTIAL_EXTRACTION; outra ocorrência se houver nova tentativa |
| EXTRACTED/STAGED sem selo | EVIDENCE_MISSING; nenhum permit fabricado |
| Selado EXTRACTING/EXTRACTED/STAGED/PROMOTED, lease e policy válidas | ELIGIBLE; comando revalida e segue o protocolo existente |
| DQ ausente em ocorrência elegível | Avaliação real pelo entrypoint existente, antes do apply |
| DQ reprovada/obsoleta | DQ_FAILED/DQ_OBSOLETE; sem promoção |
| Lease perdida | LEASE_LOST; não renova nem rouba lease |
| FAILED/CANCELLED/BLOCKED | TERMINAL; preserva o terminal |
| Recibo íntegro PUBLISHED | PUBLISHED; nenhum fetch/apply/frontier adicional |
| Coletas publicada sem recibo tipado V015 | EVIDENCE_MISSING; não usa contagens genéricas como se fossem tipadas |
| Contradição/digest/identidade inválida | INCONSISTENT |
| Falha de leitura | UNAVAILABLE, causa retida; não significa ausência de commit |
| Cancelado antes de continuar | Recusa local; não grava terminal nem desfaz publicação |
| Ack incerto de comando | READ limitado posterior; nunca repete RESUME automaticamente |

READ e RESUME compartilham exclusão V2_APPLY e row fences. A revisão é revalidada,
assim como identidade, selo, auditoria, candidate set, policy/DQ e lease. Os instantes
do recibo JDBC usam Calendar UTC explícito, como os gateways existentes; janelas
submilissegundo são recusadas em vez de truncadas. SQL continua autoridade de relógio.
Login timeout deve estar configurado no DataSource; query timeout não o substitui.
Network timeout e cancelamento de statement usam executor exclusivo de cada chamada,
encerrado em finally. Não há conexão remota nem datasource operacional montado aqui.

## Regras e evidência local

P02R-01: selo somente após contrato/travessia/auditoria reais. P02R-02: identidade e
resumos íntegros/exatos, sem interpretar indisponibilidade como ausência. P02R-03:
continuação revalidada, sem checkpoint inventado ou ressurreição terminal. P02R-04:
recibo tipado de Coletas persistido na transação dos efeitos, antes do ack; retry não
recalcula COL-03 a partir de current. Origem: prompt do Bloco 52 e ADR 0034;
responsável técnico: manutenção do V2; nenhuma regra de negócio nova foi ratificada.

Os 22 testes novos e 27 anteriores do motor passaram juntos (49 testes focados).
RuntimeRecoverySyntheticState persiste scalars/resumos limitados em arquivo; não
serializa objetos Java ou chaves de negócio. Novos objetos e oito processos Java
filhos próprios (quatro pares escritor/leitor para Coletas/Fretes × apply/prepare)
comprovam recuperação sem memória anterior. O teste de recibo tipado conserva o
resultado original quando suas contagens diferem das genéricas e rejeita adulteração
que ainda fecha a equação numérica. Outro teste compara o corpo SQL de COL-03 após
retirar somente as três adições P02R com o corpo original de V010.

A suíte completa clean verify offline em Java 17 passou 1023 testes, zero falhas/erros
e quatro skips esperados: três capacidades de symlink do filesystem e um receipt
V2-050 opt-in. Enforcer, Spotless, Checkstyle, arquitetura e JaCoCo passaram sem alterar
thresholds. A primeira rodada completa identificou duas expectativas estáticas ainda
limitadas a V014; ambas agora exigem V015. Logs/relatórios em target/bloco52-final.log,
target/bloco52-typed-receipt.log e target/bloco52-build. POM isolado equivalente ao
original, alterando somente build.directory; nenhum processo do usuário foi parado.

Reprodução: configurar JAVA_HOME do processo para JDK 17, executar Maven offline
com saída isolada equivalente, rodar os testes LocalRuntimeIntegrationTest e
RuntimeDurableRecoveryIntegrationTest e então clean verify. Executar os checkers
Test-RuntimeDurableRecovery.ps1, Test-ProgressiveDataGate.ps1 e Test-Gpt56ChatTrail.ps1,
mais os validadores de Coletas/Fretes e scanner offline. Os exercícios SQL 048/049
estão somente preparados; NÃO fazem parte dessa reprodução autorizada sem SQL físico.

O validator da trilha rejeitou cinco contraprovas em cópia sob target: retirada do
aceite local, conclusão operacional indevida, rota AGORA indevida, retirada da
proibição de rede e número de bloco incorreto. O snapshot real foi preservado.

## Handoff e rollback

Somente V2-022/RECUPERACAO_DURAVEL_LOCAL foi marcado concluído. Pai V2-022,
V2-022b, V2-041 e V2-042b/c permanecem abertos. Painel: 113 checkboxes, 59 concluídos,
54 pendentes, 196 fatias abertas. Bloco 53 não atribuído; próximo pacote grande
proposto: qualificação física G08, condicionada à autorização específica.

Não há prova física de compilação/execução T-SQL, atomicidade, locks, relógio, driver,
attention, socket ou restart de SQL Server. Não houve API/rede, .env, credenciais,
SQLCMD, perfil JDBC físico, deploy, commit ou push. O checksum não é assinatura contra
um administrador que possa reescrever toda a cadeia. Não são suportados checkpoint
parcial, takeover de lease nem reconstrução de contagens tipadas históricas ausentes.

Para desfazer trabalho local, revisar o diff deste bloco e retirar somente a composição
nova e seus artefatos, preservando mudanças anteriores. V001–V014 não foram editadas.
V015 é aditiva e não foi aplicada; após eventual aplicação futura, rollback exige
migration compensatória autorizada que preserve evidências, não edição de histórico.
Nenhum dado operacional precisa ser revertido neste bloco.

Gates finais de segurança: scanner self-test 9/9; varredura de 1082 candidatos, 1081 textos e um binário conhecido, zero finding/oversized/não inspecionado. UTF-8 estrito sem BOM em 451 textos alterados/novos (inclui trabalho preexistente) e git diff --check passaram. POM temporário removido após equivalência; a limpeza recursiva combinada foi bloqueada pela revisão automática e o snapshot de contraprovas foi mantido sob target, junto dos logs e relatórios ignorados.
