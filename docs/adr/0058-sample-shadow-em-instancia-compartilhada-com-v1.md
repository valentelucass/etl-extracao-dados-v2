# ADR0058 — SAMPLE shadow em instância compartilhada com V1

Data: 2026-10-03. Decisão de escopo adotada pelo usuário em Hermes_e7af6e3279b7; implementação congelada e qualificação offline agregada aprovada em04/10 UTC. Nenhuma prontidão física presumida.

## Problema e decisão

O preflight SAMPLE anterior herdou isolamento global de laboratório/manutenção: nenhum consumidor e listeners exclusivamente loopback. Isso impediu admitir a prova antes de conectar, sem demonstrar falha JDBC. Exigir instância vazia é incompatível com preservar o V1 ativo. A sessão SA já verifica seus próprios peers/alvo/identidade; isso não equivale a validar isolamento global.

Adotar contrato versionado separado, exclusivo SAMPLE SA local em shadow existente, que permita consumidores fora do escopo e listeners preexistentes. Manter os contratos anteriores Windows/piloto completo. Não preencher falsamente os booleanos antigos nem converter este escopo em produção.

Regra **COL-SAMPLE-SHARED-001**, origem pedido Hermes_e7af6e3279b7; responsável técnico Supervisor ETL, Banco/Persistência e Runtime. Exemplo positivo: V1 ativo fora do shadow, própria sessão SA/TCP/peers loopback/TLS estrito e footprint shadow protegido. Exemplos negativos: outro banco/remoto, TLS permissivo, escritor conflitante no shadow, fuga da sessão, commit ou promoção. Devem ser recusados causalmente em testes, sem desligar clientes.

## Critérios obrigatórios

- Receipt novo com alcance explícito, binding/hash dos três inputs/fingerprints, identidade e TLS, TTL, referência da reserva; não reutilizar receita de isolamento global.
- Confirmar ausência de escritores conflitantes no shadow e proteger a ocorrência contra corrida. Um booleano ou lease cooperativo que não impeça escritor comum não basta. Recusar conflito sem aguardar indefinidamente ou encerrar processos alheios.
- Banco propõe footprint transacional explícito com locks exclusivos até rollback e timeout de lock zero. A semântica precisa ser conferida em código e testes: NOWAIT combinado com TABLOCK não substitui a configuração de timeout zero. Referência técnica: [Microsoft, table hints](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table?view=sql-server-ver16) e [locking guide](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-locking-and-row-versioning-guide?view=sql-server-ver16). Não é prova de locks físicos já obtidos.
- Alvo literal shadow, identidade sa, TLS verificado, TCP com peers loopback, sessão/transação protegidas; bloquear commit/autoCommit/unwrap/catalog/schema e promoção.
- Uma página/per1/até1000linhas, HTTP2 máximo, serial, sem retry/redirect. Limite não é terminalidade completa da origem; página inicial vazia não prova o fluxo.
- Readback staging por binding/conjuntos/multiplicidade na mesma prova; rollback explícito, close sem erro e readback independente atribuído à ocorrência. Agregados shadow devem ser interpretáveis sob isolamento; atividade legítima V1 e avanço de IDENTITY não são erro automático nem autorizam reparo.
- Antes da prova física, consultar estado atual V1 e próximo ciclo quando comprovado, reservar deadline conservador com margem. Expiração fecha somente recursos próprios e nega recibo de sucesso incerto. Não esperar em loop por janela.
- STOP429 só pode mudar com evidência operacional datada do mesmo endpoint/template/tenant/identidade operacional efetivamente consumidos, conforme critério original; nenhuma sonda ou inferência por aplicativos distintos.

## Limites, validação e recuperação

Preservar versão, contas/senhas/grants/permissões SQL e Windows/identidades, V1/jobs/consumidores, serviço/listeners/certificados/trust, schema/Flyway, perfil Windows e Main desabilitado por padrão. Não exigir fechar48 itens históricos antes da amostra.

Banco implementa a proteção e é único executor SQL; Runtime implementa receipt/CLI e qualifica freeze agregado; Fontes compara evidência de liberação; Codex edita documentos/CI. Causais red/green e qualificação dos bytes alterados ficam no handoff da unidade `target/shared-sample-20261003-e7af6e3279b7/` e STATES, sem repetir suítes idênticas.

Implementação: receipt `coletas-6908-sample-shared-preflight-sa-v1`, opt-in adicional `coletas6908.sample.shared.enabled=true` e API `executeSharedSampleSa`. Banco protege onze objetos ctl/stg com locks X da transação e `LOCK_TIMEOUT=0`, conferidos nos checkpoints da mesma sessão. O footprint físico atual e as dependências dos procedimentos ainda precisam de prova antes do uso real; os testes JDBC simulados não a substituem. Runtime verifica deadline/cancelamento também depois de fechar o trial, depois do retorno do Banco e antes de imprimir sucesso. Deadline absoluto da ocorrência no receipt é de no máximo60s e cobre o POST independente obrigatório; o orçamento externo não renova esse prazo.

Evidência dirigida: Banco106 e Runtime78 testes sem falhas/erros/skips, bytes congelados e formatter/compiler/checkstyle aplicáveis aprovados. Três testes de expiração reproduziram aceitação indevida antes da correção. FAILs de implementação e launcher permanecem preservados. Main desabilitado, POM, perfis anteriores e limiares não foram alterados nesta unidade. A suíte integral `mvn --offline clean verify` passou uma vez em snapshot completo: Surefire2538/0falhas/0erros/5skips e Failsafe7/0/0/0, formatter/checkstyle/Enforcer/cobertura originais PASS; drift de fonte/snapshot/inventário zero. Root conferiu327 artefatos e nove arquivos congelados, sem divergência. `aggregate-root-acceptance.json` aceita somente a camada offline; fechamento posterior destes documentos não reescreve manifests nem exige repetir a suíte.

Falha local de implementação exige menor correção e nova evidência. Falta de TLS válido existente, prova de escopo da origem ou janela não autoriza bypass; concluir capacidade offline e declarar input exato. Prova com efeito incerto exige reconciliação read-only da ocorrência antes de repetir; sem reserva/retry/reparo automáticos. Esta ADR não é recibo físico.

Fechamento0435: tentativas privadas de diagnóstico foram recusadas localmente antes do Driver por coerção de data JSON e URL sem `integratedSecurity=false`; não demonstram falha TLS/senha. Launcher05 qualificado usa ISO UTC literal/invariant e alvo SA puro real antes do provedor protegido. Regra `JDBC-DIAG-SA-ADMISSION-05` (origem Hermes e recusa04; Banco responsável; exemplos/contratos/testes em `banco/diagnostic05/rule-contract.json` na unidade) altera apenas declaração por conexão já exigida. Sete casos de admissão real com connector falso e seis de binding passaram; negativos recusam antes segredo/conector. Código/helper anteriores intactos, nenhum SQL/HTTP real. Janela V1 final RUNNING; novo launcher não executado. Fonte exige correlação operacional do mesmo escopo/quota; TLS/auth/contrato físico continuam não observados, conforme STATES.
