# Checkpoint0435 — capacidade SAMPLE compartilhada qualificada offline

## Objetivo, autoridade e estado

Hermes_e7af6e3279b7: “Prepare o v2 para que tenha capacidade de extrair e nesse intervalo rodar um teste”. Implementação/qualificação de coexistência e uma prova ETL real condicional no shadow existente/sa, preservando V1. Estado: QUALIFICADO_OFFLINE; execução física BLOQUEADO_POR_INPUT concreto. STATES conserva autoridade. Anterior0434: docs/continuidade/checkpoints/0434-v1-v2-gates-de-campanha-sem-falha-jdbc.md SHA58E543EDA2403D54FF4001E7990811FE9EE9D07CE57698BAF6B9895773236627 preservado. Fechamento04/10/2026 UTC; contexto global ausente, não criado.

Alvo apenas localhost/ETL_SISTEMA_V2_SHADOW/sa/segredo externo existente. Não alterar versão16.0.1000.6, contas/senhas/grants/permissões SQL/Windows, V1/jobs/clientes, serviço/listeners/cert/trust/schema/Flyway/produção. Banco único SQL; Runtime bootstrap/qualificação; Fontes evidência; Codex documentos/CI/grafo. Regras não acionado. Nenhuma sessão nova ou callback Hermes.

## Alterações e provas

COL-SAMPLE-SHARED-001/ADR0058: receipt versionado separado/shared opt-in, próprio target/peers/TLS e proteção transacional dos onze objetos ctl/stg com X locks/LOCK_TIMEOUT=0 antes source. Staging/readback parcial não requer core/promotedExecutionId; não gera permit/promoção/completude. Deadline/cancelamento conferidos após próprio close, retorno Banco e antes sucesso CLI. Janela, footprint físico, V1 fora dele e POST independente permanecem obrigatórios. Main/POM/contratos anteriores intactos; oito fontes alteradas/nove congeladas. Três hashes CI atualizados, catálogo130 com demais linhas/ordem/limiares preservados. Before/worktree preexistentes mantidos.

| Critério | Camada | Resultado executado | Evidência |
| --- | --- | --- | --- |
| Banco | Offline | RED6falhas; GREEN106/0/0/0 | banco/handoff.json/freeze.json |
| Runtime | Offline | RED3falhas reais durante close/retornos; GREEN78/0/0/0 | runtime/handoff.json/owned-freeze.json |
| Agregado | Offline snapshot4231 | Uma clean verify exit0; SF2538/0/0/5 e FS7/0/0/0; qualidade/cobertura originais PASS; drift0 | runtime/aggregate01/handoff-final-agregado01.json |
| Root review | Artefatos |327 membros/nove fontes sem divergência | aggregate-root-acceptance.json |
| AST | Código local, sem API | exit0;39263nodes/93507edges/2266communities | graphify-receipt.json |
| Diagnóstico03 | Preflight local | Coerção JSON/cultura causou expiração falsa;0reserva/segredo/driver | banco/handoff03.json; dispatch-date-causal-review.json |
| Diagnóstico04 | Launcher local sob reserva | DPAPI existente disponível/read1; URL sem integratedSecurity=false recusada antes connector;0Driver/SQL/HTTP | banco/handoff-final-diagnostic04.json; ledger-final-diagnostic04.json |
| Correções privadas | Offline | Parser44/38negativos; helper34/AST9+11negativos; URL real7/6negativos e parent6/5negativos; Werror; sem suites idênticas | banco/handoff02.json/handoff04.json/handoff05.json |
| Fonte | Operacional sanitizado | NOT_PROVED, sucesso V1 sem binding de escopo/quota6908 | fontes/handoff.json |
| Janela final | Leitura local única após freeze05 |02:57:32UTC: RUNNING23:53:44UTC−3; next vazio | v1-window-20261004T025732815Z.json |

Referências relativas à unidade target/shared-sample-20261003-e7af6e3279b7/. Root aceita somente offline. Agregado manifest SHAE20849F47A2A57C40D1A437B4024ADCA88FDB0CF5038AEC51F83566E12DC0983; quality SHA220C72918657C544865C97F3B58F546872637E6B71C9187CB7E1B7D50C0A8D78. Correção posterior é privada, não altera fonte/POM do agregado. Docs finais posteriores distinguem fotografia do gate; FAILs/exportador/correções locais/manifests/ledgers anteriores preservados.

Nenhuma conexão Driver/SELECT/HTTP da origem/entidade/linha staged nem rollback/readback físicos. Não classificar recusa de argumento como TLS/autenticação/senha. Reserva04 fechada, JVM próprio saiu, arrays temporários limpos; nenhum recurso próprio pendente/incerto.05 está preparado/congelado, não despachado porque janela deixou de existir; não houve renovação de campanhas históricas. V1 último ciclo21:42:17→22:53:43 SUCCESS/58284 reportados, sem prova de unicidade ou escrita SQL. Nova tentativa RUNNING, nenhum próximo horário demonstrado.

## Até três próximos passos, somente com evento novo do usuário

1. Selecionar/fornecer SANITIZED_RUNTIME_SOURCE_BINDING_FOR_POST_STOP_SUCCESS: sucesso datado depois do429 e igualdade operacional de endpoint/template6908 ou quota aplicável, tenant/principal/quota V2, vinculados à execução efetiva. Não requer nova chamada ou atestado humano por hábito; logs atuais examinados não expõem o binding necessário. Responsável: operador/owner V1/origem. STOP429 vigente.
2. Em nova janela comprovada do V1 com próximo ciclo e margem, Root pode revisar bytes05 e evento próprio; Banco reserva/preflight novos antes alvo puro/DPAPI/única conexão ainda nunca tentada. Não reutilizar03/04 nem pedir novamente permissão da correção já autorizada. Sem TLS prePASS circular ou mudança de serviço/conta/trust; primeira falha encerra sem retry.
3. Somente com fonte e JDBC/contrato físico/V1 fora footprint comprovados, reservar menor trial Java→mapper→staging→mesmo-trial readback→ROLLBACK/close→POST independente. Source cap2/página1/per1/1000linhas, serial/no retry; não declarar janela completa. Nenhuma dessas provas concedida por hash/teste falso.

waiting_user/wakes0; builders ociosos. Não aguardar em loop nem iniciar próximo macrobloco/routine/callback automaticamente.