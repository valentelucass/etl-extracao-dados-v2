# Checkpoint0437 — cadeia TLS identificada; extração pendente

## Identificação e objetivo

Hermes15d6b2180cbc: avançar para extrair, primeiro obter causa TLS concreta e corrigir defeitos elegíveis no cliente. Anterior: docs/continuidade/checkpoints/0436-jdbc-real-tls-refused-sem-extracao.md, SHA AA780590FB2E04BFC9A2C68083A5E463A5F775CA98D522CF228FEFE3368A1F61. Estado BLOQUEADO_POR_INPUT para extração; unidade de diagnóstico concluída, objetivo não alcançado. STATES é canônico; CONTEXTO_GLOBAL continua ausente, não recriado.

## Autoridade, limites e decisões

Usuário autorizou correção sanitizada e UMA nova tentativa JDBC read-only Banco em localhost/ETL_SISTEMA_V2_SHADOW/sa existente:1con/até12SELECT/10scomando/60sabsolutos/no retry. Correção de confiança somente no cliente V2 com material público/identidade/procedência comprovados; nenhuma aceitação automática de folha desconhecida. Versão16.0.1000.6, contas/identidades/permissões/senhas, stores globais, SQLcert/config/serviço/listeners/schema/Flyway/V1/produção imutáveis. STOP429 exige evidência do mesmo escopo, não sonda. PM2 somente mesma app atribuída, se necessário e seguro; não executado.

Banco único SQL/reserva e helper; Runtime cliente/PM2; Fontes binding; Codex único editor compartilhado. Novos helpers privados corrigem perda da causa, inputs JVM e aridade IPC; produção/Main/POM/CI/9fontes iguais. Sem Maven integral repetido nem grafo reconstruído: código novo privado em target, fora do grafo produtivo. Planos de trust não são implementação/aceite. Contraprovas/compileFAIL/recibos antigos preservados.

## Execução e evidência

| Critério | Camada | Observado | Referência privada |
| --- | --- | --- | --- |
| Causa perdida | Offline | RED antigo;36casos novos PASS;7bindings; Werror/formatter/lint PASS; queries/caps/cleanup iguais | banco/{causal-receipt,final-bytes-receipt,child-input-receipt}.json |
| Despacho/preflight | Operacional | Root17:32:09UTC,600s setup; Banco17:33:22UTC V1 WAITING/gap966s/margem300;39+45pins sem drift | jdbc-tls-causal-dispatch.json; banco/execution01/preflight.json |
| Única tentativa nova | JDBC real | CERT_PATH_BUILD_FAILURE/08S01,4tipos completos;1attempt/0sessão/SELECT/HTTP | banco/execution01/receipt.json |
| Cleanup/ledger | JVM/OS | CLOSED/saldo0/sem incerteza/JVM saiu/buffers limpos/binário igual | banco/execution01/{reservation,ledger,close}.json |
| Material TLS | OS/JDK offline | PKIX118âncoras; binding ausente/My vazio/arquivos ausentes; identidade do certificado apresentado NÃO observada | runtime/process-trust-review.json; banco/local-public-tls.json; client-trust-decision-input.json |
| Fonte | Local/offline | Origens provisionadas iguais; associação V1 recuperada; sucesso37848 sem6908/principal/quota;STOP429 | fontes/handoff-controller-update.json; supervisor-source-review.json |
| PM2 | OS/arquivo | App exata identificada; cacheID7/paradaPID0 vs Java37192 vivo; RECOVERY_PENDING/DUMP_CHANGED;0stop/start | runtime/handoff.json; supervisor-runtime-review.json |
| Integração | Readback |13pins físicos+39/45Banco,40Runtime,7Fonte/11deps; três finais OCIOSOS | supervisor-physical-final-review.json |

Handoff Banco SHA3213C238A318D4105A81944042C232040C33F937976ADA19F1F969DEA79D78E4; manifest físico SHA C32250CBD41980E642349E0261A2261754DC488BD5115A060405205156A3033B. Freeze Banco SHA1A1CB346746C105009803C22902C57F3BB995604B6547E116DF92B31FCD372FB. Não reclassificar retrospectivamente72699, nem equiparar construção de cadeia a um motivo NO_TRUST_ANCHOR não recebido. Sem autenticação SQL ou prova de senha/hostname/schema.0entidades/linhas/staging/rollback/readback físicos. Nenhum processo próprio ativo/efeito incerto/restauração V1 pendente.

## Retomada — até três ações, sem execução automática

1. Operador SQL/provisionamento fornece cadeia pública .cer/.pem e prova independente dos bytes/identidade localhost do endpoint existente. Conferir validade/uso servidor/algoritmos/cadeia/SAN antes de proposta pinada de truststore exclusivo do V2. Sem material, não há correção de confiança elegível; cadeia não cobre localhost implica que truststore sozinho não resolve. Sem chave privada, accept-all ou mudança de certificado SQL/store global.
2. Administrador da integração ESL fornece registro JÁ EXISTENTE de sucesso6908 após429de02/10 ou liberação da quota da mesma integração/empresa/principal, somente classificações/igualdades/timestamp. Pergunta prática única preservada no handoff Fontes; não expor token nem consultar HTTP para descobrir liberação.
3. Somente após decisão/inputs, delimitar nova unidade física: orçamento deste diagnóstico consumido. Se pausa PM2 necessária, comando read-only exato Runtime no contexto gestor já permitido deve reconciliar cache parado vs Java vivo antes de qualquer stop/start/finally. Nenhum controlador/guard acionado por esta unidade.

Supervisor waiting_user/wakes0. Banco, Runtime e Fontes OCIOSOS; Regras não acionado. Backlog/checkboxes/aceites produtivos inalterados. Não iniciar outro macrobloco, rotina, callback Hermes ou loop de espera.