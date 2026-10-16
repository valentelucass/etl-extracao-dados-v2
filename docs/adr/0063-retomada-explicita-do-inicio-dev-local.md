# ADR0063 — Retomada explícita do início no ledger dev-local

Status: implementado, qualificado e aceito pelos critérios START01–07 no modelo local delimitado do HERMES_ce7fca106978/paridade01 (Regras04 e Supervisor ETL). Responsáveis técnicos: Banco e Persistência (ledger), Runtime e Qualificação (CLI), Supervisor ETL (integração). Não representa aceite humano, paridade operacional completa ou autorização de infraestrutura.

## Contexto

A orientação adicional de Lucas autorizou um bloco interno coeso de paridade funcional com o V1, mantendo bloqueada a operação dependente de quota6908. A comparação dos componentes reais encontrou uma lacuna reproduzível: DevLocalLedger criava o diretório da execução antes de registrar START. A morte de sua JVM nesse intervalo deixava material que uma nova JVM recusava com ORPHAN_RUN_CONFLICT, sem registro recuperável (RUN_NOT_FOUND).

O V1 SqlServerRecoveryReplayGate registra o início sob transação/lock e RecoveryUseCase distingue execução de término. A decisão conserva essa intenção de rastreabilidade no componente local do V2; não transforma filesystem em transação SQL. A contraprova dos bytes iniciais, com Runtime.halt após o mkdir real e reabertura em outra JVM, está preservada em paridade01/banco02/baseline-red01.json.

## Decisão e regra DEV-FS-002

Antes dos diretórios de uma execução, persistir uma intenção de início imutável, limitada e vinculada à identidade do workspace, à execução, ao hash técnico do input e ao próximo elo do journal. Manter o lock exclusivo real. O leitor atual conserva o formato do journal e lê workspaces históricos sem intenção.

Uma intenção própria íntegra permite somente reconciliação explícita por reconcileStart e pelo comando reconcile-start, com execução e hash esperado. Verificar todo material existente antes de preencher diretórios ou registrar START. Orphan sem intenção, material inesperado, links, alteração de input/workspace/sequence/hash ou escrita parcial/corrupta são recusados, sem adotar, sobrescrever, truncar ou apagar evidências. Uma intenção pendente segura o próximo slot do journal e bloqueia novas mutações até reconciliação.

Exemplo: uma JVM morre depois de criar runs/exec-local, com intenção própria durável e sem START. Uma JVM nova observa a pendência sem escrevê-la; o operador invoca reconcile-start. O readback confirma um único STARTED, sem outputs ou trabalho de negócio. O operador pode então executar stop explícito. Reconciliar novamente retorna o estado realmente persistido e não duplica START nem reexecuta negócio.

Open, runs, readback e status permanecem observações sem reconciliação automática. A CLI informa pendingStartHold/pendingStarts; a execução pendente tem START_PENDING, sem fabricar um Run ou COMPLETED. A configuração lida nesse comando é controle local de deadline, não prova do input de uma execução de negócio.

## Contratos afetados e limites

DevLocalLedger.start, reconcileStart e pendingStartIds; DevLocalMain/DevLocalMainArguments e seus recibos locais. O opt-in --synthetic e identityScope=SYNTHETIC_LOCAL_ONLY/physicalEvidence=false permanecem explícitos. O fluxo qualificado é um workspace já inicializado e uma execução não reservada; workspace-init não é reconciliável pela CLI nesta unidade.

O modelo é morte controlada de processo em fronteiras determinadas de gravações completas, usando filesystem/JVM/CLI/locks reais. Não há garantia de sobrevivência a queda de energia, flush de diretórios, writers que desrespeitem o lock, nem reparo automático de escrita interrompida. Mocks e resultados de negócio sintéticos não comprovam fonte, SQL, DQ, promoção, completude, scheduler produtivo ou cutover.

## Verificação e recuperação

Os testes de contrato estão em DevLocalLedgerStartRecoveryTest e DevLocalMainStartRecoveryProcessTest. Cobrem morte de JVM e reabertura, leitura sem mutação, input/conflitos/tamper/links/material estrangeiro, lock interprocesso, idempotência e CLI explícita. Banco18 novos/35 regressões PASS; Runtime10 casos CLI/12 controles anteriores qualificados com reuso explícito e3 instrumentados finais PASS; Root build720Java17 e arquitetura22/22PASS. Resultados executados, reuso, falhas preservadas e coorte de bytes ficam no relatório de paridade e checkpoint de encerramento. A prova inclui filesystem/JVM/CLI reais e não comprova fonte ou SQL.

Instrumentação e baseline anteriores são fixtures externas pinadas, com opt-in explícito de teste. Ausência do opt-in aborta somente os casos dependentes e não comprova seus critérios; configuração presente/incompleta ou divergente falha. Casos independentes continuam executáveis no fluxo padrão. O runbook registra propriedades, pins e limite de reprodução a partir de um clone sem essas fixtures. Nenhuma alteração de POM ou dependência produtiva foi necessária.

A prova focal adicional interrompeu reconcileStart em DIRECTORY_1, BEFORE_START e AFTER_START e retomou em JVM nova, com consulta de bytes antes de repetir, um START/um terminal STOPPED e segunda retomada sem alteração. Três cenários/nove JVMs históricas: um caso reclassificado somente por readback após erro do controller e dois novos/seis JVMs. Root verificou81 pins; helper, agente, código e grafo permaneceram iguais. Essa prova cobre reconciliação nas fronteiras declaradas e não afirma atomicidade de stop/recover interrompido em suas gravações, perda elétrica ou escrita arbitrária incompleta.

Recuperação operacional: preservar arquivos e hashes, observar status e seguir o runbook dev-local-start-recovery. Não remover intenção/journal ou adotar orphan manualmente. Reverter somente o código não torna seguro operar um workspace com pendência: o leitor antigo desconhece intenções. Congelar o workspace e conservar o leitor atual até a reconciliação ou decisão explícita sobre suas evidências; nenhum purge ou retry automático.

STOP429 permanece na operação dependente. Nenhum efeito em V1, SQL Server16.0.1000.6/instalação, contas, permissões, serviço, listeners, certificado/trust, schema/Flyway, PM2 ou produção. C78/BF/E3/Main/defaults não herdam esta decisão.
