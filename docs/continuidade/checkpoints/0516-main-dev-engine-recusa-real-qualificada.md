# Checkpoint0516 — Main DEV engine: recusa real qualificada; extração pendente

Pedido HERMES_863b2573942e, fechamento em 2026-10-13T18:49:14.740070+00:00. Anterior: [0515](0515-adaptador-c78-offline-qualificado-main-real-pendente.md), SHA256 `1c7b99a1ed012e49b290006526e6723a53d0890afc2e2c5055ffc26d0f9ebb19`. Objetivo de Lucas: Main V2 integrada, extração real no shadow e paridade funcional V1. Este conjunto entregou código e qualificação dirigida com dois processos reais da Main pública; o objetivo integral permanece não verificado.

Estado aceito: `ACEITO_NO_ESCOPO` somente para `REAL_MAIN_DEV_ENGINE_GATE_REFUSAL`. Código/sessão/limites estão `TESTADO_NA_CAMADA` offline; Main positiva e captura continuam abertas. A frente física está `BLOQUEADO_POR_INPUT`, sem converter fixtures em entrega integrada ou ausência de produtor em bloqueio externo por si só.

## Autorização e preservação

Lucas autorizou avançar as etapas internas do mesmo objetivo, coordenando os quatro builders existentes. Nenhuma autoridade/reserva SQL/TDS/HTTP/provider foi emitida nesta unidade. O novo caminho é opt-in, `runtime.dev.engine.enabled=true` com default false, somente `C78_LOCAL_SHADOW_SQL_PIN_V1` em `localhost:1433/ETL_SISTEMA_V2_SHADOW`. BF/E3/Windows/defaults e autoridades catalogais33/39 conservados; claims consumidas não foram repetidas. Banco permanece único executor SQL/TDS. Não houve pausa/restauração PM2 ou escrita V1/produção.

Root releu o arquivo SQL instalado após a qualificação: produto `16.0.1000.6`, arquivo `2022.0160.1000.06 ((SQL22_RTM).221008-0913)`, SHA256 `73dcd4a89ff178cf6b2978ee29dccd8898fe5c1977abac8208aa4ecb5ec43e32`, idênticos ao início. Sem atualização/reinstalação/troca, DDL/Flyway, contas/credenciais/permissões/identidades/ACL, elevação, serviços/listeners/certificados ou trust global. `../CONTEXTO_GLOBAL.md` continua ausente; não recriado.

## Alterações e correções causais

Runtime implementou a entrada pública `run --dev-bundle <manifest> --profile C78_LOCAL_SHADOW_SQL_PIN_V1 --dev-engine`, também aceitando despacho explícito com `--dispatch-sha256`. Main/composição chamam o adaptador concreto Banco e o executor operacional existente; sem seleção de implementação por SPI ou mock. Banco implementou admissão/despacho/sessão próprios, reutilizando o conector C78 real e segredo externo existente de forma lazy. Verify precede open, checkpoint precede fonte e cada tentativa HTTP. Após tentativa de open, outcome sem readback não vira zero efeitos/sucesso. Diagnóstico limitado por tipo/frame conserva causas e cleanup sem ler mensagem sensível.

Banco impôs tetos reais: seis inputs/leases, 64 ancestors, caminho512caracteres/32componentes, metadataTABLES29 e 64 statements antes de prepareCall. Slot só volta após close confirmado; close incerto conserva ocupação. São recursos/metadados limitados, não universo de chaves de negócio. Root manteve a regra arquitetural e acrescentou dois testes: exceção exata por classe/campo/tipo/modificador e pin dos bytes revisados; outro repository com os mesmos nomes é recusado.

Inventário final versus baseline01: oito fontes de produção e quatro testes alterados/adicionados, nenhum Java removido; UTF-8 conferido em root/final-source-diff36.json. Mudanças preexistentes preservadas. Root é editor docs/CI. CI21/28/33 corrigiu três hashes, duas classes novas e cinco classes catalogais preexistentes sem classificação, além de dois pins antigos de DevLocalMain/Arguments. As duas fontes e cinco classes eram idênticas ao baseline atual e suas qualificações anteriores; nenhum revert de código ou relaxamento da política. Revisões/pins antigos e falhas foram preservados.

## Execução e evidência

| Critério | Camada e limite | Observado | Artefato privado |
| --- | --- | --- | --- |
| Coorte final | JDK17, release17, UTF-8, Xlint:all/Werror | 735fontes/1286classes, compile0, sem drift; sucessor recompila3/copias1280 | runtime01/compile02 e cohort-manifest02.json |
| Banco | Testes dirigidos no agregado final | 16PASS: adaptador10 + limites6 | runtime01/final-checks02/bank-cohort.stdout |
| Arquitetura | Sete métodos dirigidos, incluindo dois novos | 7PASS; batch com CI teve uma falha de classificação preservada | runtime01/final-checks02/architecture-and-ci.stdout |
| CI | Apenas gate afetado após revisão causal33 | 3PASS, zero classes sem classificação/drift | runtime01/ci-only03; scope SHA256 d996634fa56c5260302225727dec7fa78b82f18d4cc065ef857ae123a895cd7d |
| Runtime | Doze testes dirigidos iniciais; dois consumidores afetados no sucessor | PASS; os dois são requalificação, não novos testes somados | runtime01/tests01 e final-checks02/runtime-consumers.stdout |
| ENGAUTH01–06 | Main pública final, deadline60s, retry0, logs64KiB | run03 PID71672/1063ms e run04 PID47780/1094ms, ambos exit20/DEV_ENGINE_GATE_ADMISSION, sem timeout | runtime01/run03 e run04 |
| Ordem/bytes/fechamento | javap, classload do JAR final e leitura exclusiva pós-exit | Adm.verify antes de open; construtores concretos; processos/streams encerrados e leases fechados | runtime01/byte-evidence03, classload-readback02.json, lease-close03.json |
| Integridade independente Root | Readback de 62 referências do handoff final | Todos os hashes/bytes coincidem | root/runtime-readback34.json e final-review37.json |
| AST restrito | Banco3 + BoundsTest1 + ArchitectureTest1, sem LLM/HTTP/clustering | 42.541nós, exit0, processos próprios0/handles fechados | runtime01/graph-own03/result.json |

JAR final real: `runtime01/deployment02/etl-dataexport-v2.jar`, 2755180bytes, SHA256 `24fae470f2b4480531640f7b122d66ec50f669972eefbc59a2fb9486ba8784a9`. Handoff final Runtime SHA256 `fad478f9cd3136db241579019499ba002c6d2cc38f0479c97e7029957a1805d2`. Formatter/Checkstyle dirigidos PASS. Maven/coverage integrais e integração SQL não foram repetidos nem alegados. AST global timeout, falhas anteriores de lint/arquitetura/CI/comparador e pacote01 permanecem preservados; sucessores corrigem causas verificadas.

A conclusão zero efeitos usa ordem dos bytes e recusa tipada nos processos reais, junto com readback de fechamento; flags impressas sozinhas não seriam prova. Efeitos efetivos deste pedido: SQL/TDS/HTTP/provider/PM2/DDL/commit/reservas físicas/replay33/39 zero. Engine, fonte e captura reais NÃO executados, nenhum dado novo retido, DQ/promoção/paridade/cutover ou checkbox produtivo promovido. Sem efeito físico incerto a reconciliar nesta unidade.

## Bloqueios concretos e próxima fronteira

Fontes não recebeu evidência6908 vigente do MESMO caller por canal de auditoria EXISTENTE legitimamente autorizado. Faltam observação original/procedência/acesso e vigência cobrindo deadline, os seis vínculos privados (origem/empresa/integração/principal/egressIP/quotaBucket), saldo e conflito de consumidores. O alvo audit existente identificado é produção/nonshadow com trust-all não admitido; autorização/pin C78 do shadow não se transfere para ele. Necessita evidência original do custodian ou decisão específica de acesso/alçada/TLS para o canal existente. Não criar recibo, chave, verifier, identidade/grant/store nem sonda para descobrir quota. STOP429 permanece somente no dependente.

A Main pública ainda NÃO possui produtor legítimo de admissão independente implementado e vinculado. Construtores sem-argumentos e Path/String recebem admissão independente ausente; o terceiro construtor aceita port tipado, exercitado com controles sintéticos. Implementação e vínculo reais dependem do canal/procedência acima comprovados; um contrato JSON/hash/PID/fixture não resolve autenticidade. Prova negativa executada não é Main positiva nem backend executado.

Comparação Banco do relatório real39 já existente, sem nova consulta: 276colunas/273 conformes; três collations de ctl.execution_audit divergem de V105 (status/traversal_verification/failure_category). Cinco procedures não foram observadas, sem afirmar inexistência: temporal_plan, temporal_gaps, record_execution_metric, raise_observability_alert, observe_platform_health. Assinaturas23, digests4, chaves36/FKs26 conferidos. Falso negativo de aridade recovery-stale corrigido no comparador (zero parâmetros real), não no SQL. Não baixar contrato, mudar baseline ou aplicar DDL para declarar pronto. Proteção efetiva contra writers e contratos físicos completos ainda precisam de prova própria.

Próximas ações, condicionadas aos inputs e à nova decisão aplicável: (1) custodian fornece prova original de quota/caller no canal existente autorizado ou usuário decide o acesso/TLS específico; (2) com procedência admitida, implementar/vincular produtor independente real e resolver divergências sem DDL/alteração de segurança ou versão; (3) somente após gates/coorte/limites/reserva novos, Banco executa unidade física delimitada da Main e captura com readback/proteção. Não renovar campanhas consumidas. Ao concluir este pedido, aguardar decisão do usuário antes de outro processo.

Evidências privadas sob `hermes863b2573942e/{runtime01,banco01,fontes01,regras01,root}`; contratos Regras ENGAUTH01–06, Fontes source-admission-contract01, Banco publication02/resource-bounds. Quatro builders devolveram PONG e conclusões observadas; Codex `PONG_863b2573942e_CODEX`. Sem callback Hermes. Recibo único local `863b2573942e.response.json`: status completed encerra a comunicação, não o objetivo ETL.
