# ADR0060 — carga retida de testes com pin local isolado

- Data:05/10/2026.
- Estado: decisão do usuário aprovada; implementação e qualificação em andamento, execução física ainda não realizada.
- Responsável técnico: Codex, com Banco para TLS/JDBC, Runtime para entrada/qualificação e Fontes para transporte/contratos. Não constitui aceite humano de implementação.

## Contexto e autoridade

A primeira prova Java real extraiu uma Coleta para memória. O usuário esclareceu que deseja preparar o banco de testes e extrair todas as entidades para testar, mantendo dados. A folha SQL mudou de BF723 para E3A5; o perfil SAMPLE de ADR0059 recusou corretamente a mudança antes de autenticar.

Foi apresentada decisão concreta para confiar manualmente em E3A5 exclusivamente em um novo perfil de testes persistidos em `localhost/ETL_SISTEMA_V2_SHADOW`, com o `sa` existente, informando certificado autoassinado, identidade DNS não validada e procedência independente incompleta. O usuário respondeu “pode, so n pode mudar versao sql server”. A referência dessa aprovação é `USER_20261005_E3_TEST_RETAINED`. Não repetir essa pergunta como se ainda estivesse pendente.

Inventários revisáveis em `target/test-database-all-entities-20261005/{banco,runtime,fontes}/`. O catálogo contém11entradas; sua implementação local não equivale a11fontes reais prontas. A unidade inicial é retenção parcial de Coletas; o objetivo de todas as entidades permanece aberto até prova própria de cada rota.

## Decisão

Criar perfil separado `TEST_RETAINED_LOCAL_SQL_PIN_V1`, com opt-ins `test.retained.local.enabled` e `test.retained.sql-pin.enabled`. Não ampliar o perfil SAMPLE, cuja regra continua rollback-only, nem os defaults Windows, fullpilot e Main. Reutilizar os adaptadores tipados de staging e controle, sem modificar DDL por suposição.

### TEST-RETAINED-TLS-001 — identidade exata e restrita

- Alvo literal `localhost:1433/ETL_SISTEMA_V2_SHADOW`, com peers efetivamente loopback verificados antes de autenticar.
- DER SHA256 `E3A5A7061FB56C225F93CC330CD733F5911F7D3F420D608EBE9AFA7371ACE276`, CN=issuer=SSL_Self_Signed_Fallback, RSA3072/SHA256withRSA, validade2026-10-05T12:36:39Z..2056-10-05T12:36:39Z, sem SAN/extensões.
- Verificar pin, validade, tamanho/algoritmos, vínculo ao socket e prova criptográfica de posse no handshake TLS completo antes de LOGIN. A captura pública anterior não comprovou posse nem procedência independente.
- A exceção de nome cobre somente localhost nesse perfil. Manter TLS criptografado e `trustServerCertificate=false`; sem accept-all, stores globais, aprendizado de pin, `encrypt=false` ou fallback.
- Usar `sa` existente via provedor externo; nenhum segredo em URL, argumento, log ou artefato. Nenhuma alteração de conta, credencial, grant, identidade, permissão ou certificado do servidor.
- Testes: perfil/opt-in/alvo incorretos, folha alterada/expirada/futura, peer/socket incompatíveis e handshake incompleto recusam antes da autenticação; os perfis antigos não aceitam E3 por herança.

### TEST-RETAINED-CAPTURE-001 — persistência parcial honesta

Uma nova sessão é dona da transação e do único commit efetivo; chamadas de commit dos adaptadores ficam suprimidas até a decisão desse owner. O perfil antigo que sempre reverte permanece intacto.

Antes de efetivar: verificar identidade/contrato físico, janela/proteção contra escritores conflitantes, limites de batch/SELECT, vínculo da ocorrência e readback de contagens tipadas/auditoria. A amostra inicial limita Coletas a página1/per1/até1000linhas e2HTTP,30s/10MiB/deadline60s, serial sem retry/redirect/fallback. Limites SQL de todas as operações, inclusive readback e conexão pós-commit, serão fixados na reserva concreta antes do efeito.

Um corte por limite de amostra não é travessia completa. Pode ser retido em `ctl`/`stg` com estado CANCELLED e razão de limite de teste, recebendo `CAPTURE_RETAINED_PARTIAL`. Não inventar terminal vazio, DQ, publicação, sucesso integral ou promoção para `core`/`pub`. A implementação versionada já possui estados e tabelas para representar esse caso; isso ainda exige comprovação no catálogo instalado.

Após o commit, confirmar a ocorrência por nova leitura reservada. Falha anterior ao commit provoca rollback da unidade própria. Resposta de commit/fechamento incerta exige reconciliação antes de repetir; não limpar, apagar ou compensar automaticamente dados retidos. Testes devem cobrir commit prematuro, readback ausente, conflito de writer, limites, erro/timeout/cancelamento e resultado incerto.

### TEST-RETAINED-SCOPE-001 — namespace exclusivo da ocorrência

Na primeira ocorrência, usar `TEST_RETAINED_SOURCE_20261005` e `TEST_RETAINED_SCOPE_20261005`, vinculados a uma seleção única de endpoint/token provisionados em memória durante a execução. São nomes técnicos de teste, não identidades de empresa ratificadas pelo fornecedor. Não misturar com SAMPLE antigo, credenciais diferentes ou ocorrências independentes. Se já estiverem ocupados inesperadamente, recusar e reconciliar; nunca apagar para fazer o preflight passar.

Um futuro run conjunto pode declarar namespace próprio para sua campanha inteira, mantendo o mesmo vínculo explícito à seleção da origem. Nenhum ID/suplemento de fixture pode preencher dado real ausente. Testes cobrem namespace ocupado, divergência config/plano/partição e tentativa de reuso indevido.

## Preservação e implantação

A versão SQL Server16.0.1000.6 deve ser verificada por leitura de arquivo/produto e hash antes/depois das operações, sem upgrade, patch, reinstalação ou troca de engine. Também não alterar usuários, permissões, serviços, listeners, certificados do servidor, trust global, schema ou produção por conveniência desta carga. Sem migrations/Flyway até existir causa física elegível; migrations que criem usuários/grants continuam proibidas.

Banco é o único executor SQL; Runtime qualifica o agregado; Fontes controla as chamadas de origem; Codex integra documentos/CI. A primeira unidade SQL será diagnóstico reservado1conexão/até12SELECT/10s/deadline60s/sem retry, após qualificação dos bytes finais. A carga é outra reserva, com budget completo de SQL/HTTP e readback. A aprovação do perfil não converte inventário, hashes, fixtures ou serviço ativo em prontidão física.

Recuperação cliente: desabilitar os novos opt-ins e encerrar somente recursos próprios. Preservar dados de testes já confirmados e seus recibos. Nenhum comando de remoção ou alteração de servidor faz parte da recuperação.

## Critério de conclusão

Primeiro marco: uma amostra real retida em `ctl`/`stg`, commit conhecido, leitura pós-commit e recursos fechados, versão/configuração do servidor preservadas. Objetivo completo: cada uma das11entradas com caminho real qualificado, limites/exigências próprios, registros observados e resultado de teste rastreável; dependências e quarentenas não podem ser escondidas como sucesso. Não equivale a cutover ou aceite produtivo.
