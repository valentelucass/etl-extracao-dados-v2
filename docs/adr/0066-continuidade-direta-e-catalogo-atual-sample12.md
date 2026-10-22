# ADR0066 — continuidade direta e catálogo atual SAMPLE12

- Data: 16/10/2026.
- Estado: decisão adotada; catálogo atual e reconciliação somente leitura executados, qualificação Java206/206 aprovada. A amostra ficou não comprovada e seu MainHTTP permanece desconhecido. Sucessão dos resultados em STATES/checkpoints0529/0530. Este documento não é despacho ou reserva física.
- Responsáveis: Root mantém decisão, documentação e CI; Banco implementa contratos/controles e é o único executor SQL/TDS; Runtime compõe e qualifica o pacote; Fontes mantém e revisa o vínculo atual da origem. Responsável de negócio conhecido: Lucas.
- Relacionadas: ADR0062, ADR0064 e ADR0065.

## Problema e escopo

O usuário pediu diretamente continuar a extração completa em16/10 e informou ter encerrado processos, inclusive V1, para liberar memória. O código existente vinculava o caminho físico a identificadores exatos de pedidos antigos. Reutilizar esses identificadores faria uma rodada nova parecer sucessora física autorizada de unidades consumidas. Ampliar genericamente os identificadores eliminaria uma fronteira de escopo útil.

Os contratos SQL históricos também precisam de observação atual antes da captura. Um relatório antigo, um hash preservado ou uma flag positiva não demonstram que tabelas, definições e restrições continuam instaladas. O snapshot de writers não impede uma corrida posterior. A nova unidade continua sendo a prova pequena C78 com readback e rollback, sem herdar retenção E3 ou promover dados.

## CONTINUITY-DIRECT-001 — identidade finita e autoridade separada

Adotar somente o par exato `user20261016-continuar-v2` / `USER_20261016_CONTINUAR_V2`, além dos pares históricos que cada consumidor já aceitava. Fonte, Runtime e Banco verificam o par realmente selecionado; aliases, variações de caixa, prefixos, request cruzado e dispatch de outra família recusam. A nova família SAMPLE é `U20261016SAMPLE12-` com32 dígitos hexadecimais; o catálogo antecedente tem família distinta `U20261016CAT12-`.

Origem: instruções diretas do usuário neste chat, registradas em AGENTS/checkpoint0529. Nenhum prompt Hermes é fabricado. Unidades C5439/43 permanecem consumidas, seus holders fechados e seus recibos com o significado original. Manifesto do pacote, integridade dos arquivos, DTO de INFO e testes sintéticos não concedem efeito.

Root adota os bytes revisados dos emissores e publica decisão/reserva próprias antes de cada efeito. O controller Banco real mantém sua identidade de processo/início e é o pai da Main pública; não simular PID, papel ou horário. O emissor preenche apenas dados vivos de processo/tempo, consome claim exclusivo e não cria orçamento por repetição. Falha ou emissão incerta conserva a ocorrência; qualquer sucessor exige causa corrigida e nova decisão explícita dentro do objetivo autorizado.

Testes: `CurrentSourceInfoEvidenceTest`, `RuntimeDevCurrentSourceNamespaceTest`, `RuntimeDevCurrentCatalogDispatchTest`, `C78Sample12NamespaceTest` e recusas de request em `C78CurrentSourcePrivateChannelTest`. O pacote final deve preservar Java17, `-parameters`, dependências pinadas, coorte de classes/recursos atual e o vínculo `config.parent.parent.parent` com a unidade. A pasta operacional fica diretamente em `runtime01/deploymentNN`.

## CATALOG-CURRENT-001 — antecedente atual completo e limitado

Criar o modo separado `SAMPLE12_CURRENT12`, conservando os modos anteriores. A unidade admite uma conexão, doze SELECTs estáticos, timeout10s por query, deadline60s, sem retry, HTTP, DDL ou DML. A ordem é contexto PRE, tabelas, colunas, procedimentos, parâmetros, índices atuais, FKs, checks, triggers, módulos atuais, writers e contexto POST. A seleção segue o alvo C78 literal, pin imutável e vínculo nativo do mesmo socket antes de LOGIN, conforme ADR0062/0064.

Conferir29 tabelas/312 colunas/29 procedimentos e suas assinaturas, restrições e definições completas, incluindo cinco dependências finitas V020/V015/V004 usadas nessa rota:34 módulos. A definição retorna em chunks UTF16 codificados em hex, com comprimento e SHA256 totais. Recusar módulo ausente/encriptado, chunk ausente/duplicado/fora de ordem, definição alterada ou limite excedido; nunca truncar para aceitar. O conjunto é fechado e não constitui descoberta automática de dependências de rotas de promoção.

O modo atual usa até2048 linhas para módulos, chunks de1024 bytes e limite agregado de1.900.000 caracteres; o arquivo físico UTF8 completo, com estrutura JSON, fica limitado a2MiB. Os limites antigos não são ampliados. Índices filtrados incluem comprimento e definição UTF16 completa, até256 bytes, comparados aos predicados canônicos V003/V010. Nome, chaves e `has_filter` sozinhos não comprovam o filtro. Os outros contratos físicos continuam exatos.

Root lê o relatório atual e seu encerramento causal, confronta os bytes com a referência histórica revisada e publica revisão específica vinculada a dispatch, invocation, alvo, imagem/PID e arquivos efetivos. A referência histórica fornece critérios; não prova a instalação presente. O emissor SAMPLE dereferencia e verifica essa revisão, o relatório completo e o fechamento antes de claim, INFO ou Main. `writer0` é apenas observação; a captura continua exigindo a proteção all29 e mesma transação do owner após cada CALL.

Testes: `C78DevCatalogCurrentSampleTest`, regressões de Inspector/ReportStore, política de arquitetura com pins dos dois protocolos revisados e testes privados do leitor atual, incluindo caminho completo e mutações de fechamento, horário, invocation, request, referências e filtros. Relatório/closure de teste não podem ser utilizados como evidência física.

## SOURCE-COMPOUND-001 — prazo único e fechamento honesto

A proposta para a nova captura é BACKFILL15/10/2026 em America/Sao_Paulo, Coletas6908, per1/página1/até12 linhas/uma raiz. O holder novo fixa o deadline original antes das leases/INFO: uma INFO antecedente e até duas chamadas da Main, cap composto3 e60s, cada chamada até30s/10MiB. O bind da Main pode encurtar o prazo; não o renova. Serial, sem retry/redirect/fallback; primeiro não2xx/429 ou limite inválido encerra a operação dependente.

Abertura tentada sem holder retornado não comprova fechamento. O recibo deve marcar fechamento incerto, conservar a causa primária e impedir sucesso. A limpeza do próprio Job, processos, streams, readers, leases e sessão SQL precisa ser conhecida; ausência de objeto retornado ou saída de um processo isolada não basta. Segredos permanecem exclusivamente nos canais privados existentes e em memória.

## CATALOG-OWNED-RECOVERY-001 — observar a ocorrência consumida sem repetir a captura

A amostra de16/10 foi recusada e o controller descartou o candidato da Main antes de persistir o diagnóstico. Os recursos locais encerrados não demonstram captura, readback, rollback histórico ou número/status das chamadas HTTP anteriores. A INFO antecedente200 e os61 frames de verificação pura também não fecham esse histórico. A ausência de auditoria local preservada mantém MainHTTP=UNKNOWN; não usar outra INFO como sonda de quota.

Adotar o modo explícito `OWNED_SAMPLE12_RECOVERY12`, operação `CATALOG12_OWNED_RECOVERY_SELECT_ONLY`, prefixo `U20261016RECOVERY12-`. Conservar os três modos antigos. A factory recebe UUID de execução, PID e porta tipados; o Runtime aceita somente a ocorrência consumida concreta e quatro campos fechados: executionId, mainPID, clientPort e consumedSampleDispatch{path,sha256}. O sidecar Root também pina o pedido e a prova de peer originais. Esses arquivos são evidência; a lease não revalida autoridade, claim ou prazo passados.

Reservar outra unidade exclusivamente read-only:1 conexão/12SELECTs/10s por query/60s/providerDPAPI1/HTTP0/DDL0/DML0, com fechamento conhecido. Conservar onze consultas do catálogo atual e substituir CURRENT_MODULES por OWNED_RECOVERY_SUMMARY. As27 métricas são contagens por executionId, relações parent/child e sessão própria pelo PID/porta/tupla loopback/banco, incluindo transações/requests/locks. Não retornar payload ou IDs de negócio. Não encerrar sessão anterior, reverter transação externa nem excluir registros.

Usar schema `C78_DEV_OWNED_RECOVERY_REPORT_V1`, layer `owned_occurrence_recovery_counts_only` e status `RECOVERY_READONLY_OBSERVED`. Validar27 nomes exatos, únicos, ordenados e contagens int64 não negativas; preservar valores não zero e limite físico2MiB. Esse relatório não passa como catálogo de módulos atual ou recibo de captura. A revisão Root independente lê os arquivos reais, confirma12queries/contexto/contratos e encerramento. Contagens zero demonstram ausência atual apenas nos escopos consultados; historicalRollbackProved permanece false e não há nova autoridade Source.

O catálogo real também revelou que `stg.coleta_exact_time` se relaciona por stage_record_id e não tem execution_id. Baseline e SETS do owner passam a usar JOIN explícito a stg.coleta_record e filtro r.execution_id. Não alterar schema. No baseline anterior, a coluna sem alias podia ser resolvida no escopo externo e contar toda a tabela; esse fato impede inferir que a falha sempre precedeu o HTTP. Testes: C78DevOwnedRecoveryPlanTest, RuntimeDevOwnedRecoveryCatalogDispatchTest e C78Sample12OwnerExactTimeSchemaTest, mais regressões de Inspector/Store/Owner/arquitetura/CI. Proxy JDBC é prova de componente; a unidade real de reconciliação qualifica a execução das novas consultas.

O controller sucessor persiste o candidato sanitizado antes da validação, com vocabulário finito de códigos e sem mensagens arbitrárias. Isso não transforma candidato recusado em owner válido. A linha estática RUNTIME_HTTP_ATTEMPTS pode conservar contagens de tentativas, nunca status HTTP ou sucesso. Snapshots consumidos e qualificações anteriores permanecem intactos; novos pins exigem qualificação própria antes de outra Main.

## Consequências e recuperação

Esta escolha permite avançar com uma identidade nova e um catálogo atual sem mudar padrões históricos ou confiar em flags. O custo é qualificar novamente os bytes alterados e reservar catálogo e captura separadamente. Sucesso da amostra demonstra somente a camada realmente observada: captura, correspondência em staging, readback e reversão. Não demonstra retenção, travessia completa, paridade, sweep, promoção ou cutover.

Recuperação: desabilitar os novos opt-ins, fechar exclusivamente recursos próprios e preservar ocorrências e falhas. Não reiniciar V1 parado pelo usuário por suposição. Preservar SQL Server16.0.1000.6/instalação, contas, credenciais, permissões, identidades, ACL, elevação, serviços, listeners, certificados/trust global, schema/DDL/Flyway, V1/PM2 e produção. Nenhuma alteração de risco ou perfil de retenção é autorizada por esta ADR.
