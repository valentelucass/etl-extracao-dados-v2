# Checkpoint 0035 — B60 adoção, integridade e telemetria

Data: 2026-09-09. Anterior: 0034-bloco59-fechamento-local.md,
SHA-256 e941b7bd7d64a0dfd5ddeb77f656df96ac115d0172ba09aa5e7f777766873655.
Objetivo: implementar A–F do pedido adotado, preparar qualificação física local
de Usuários e coexistência das seis verticais, concluir testes e fechamento
revisável. Pedido preservado em target/bloco60-local/PROMPT-ADOTADO.txt.

Autorização: implementação/SQL versionado/testes offline/preparação/evidência.
A seção 4 proíbe inclusive consultas SQL de preflight antes da aprovação única
do pacote físico concreto. Nenhuma aprovação solicitada ainda; nenhum efeito
SQL ou fonte real, credencial de fornecedor, orçamento herdado ou commit/push.
Número60 e V024 conferidos livres. Nenhum aceite/checkbox novo.

Base: target/bloco60-local/initial-inventory.json e initial/,1572 cópias exatas.
initial-verification.json confirma recibo B59
36a8b88b7efbcfd52859214cb91c647cea759335be46e684bcad7df5f210ecba,
386 artefatos,808 bindings,211 artefatos Java,196 suites/1378 testes/0/0/5;
zero drift, reverse --check0 sem reversão. Test-Bloco59Local com evidência
privada passou exit0 em initial-b59-validator.log. Java B59 não reexecutado.

Frente E TESTADO_NA_CAMADA: GraphQlHttpAttemptObserver observa cada submissão
ao transporte após o checkpoint do governor, inclusive falha/retry; fábrica,
gateway/executor e composição operacional transportam o observador. Contador
O(1) de RuntimeHttpAttempts mantém teto compartilhado e diagnóstico numérico
GraphQL, sem payload/cursor/segredo. RED red-graphql-count-01:28 testes,1 falha
esperada por UNOBSERVED. graphql-directed-01:50 testes/0 falhas/0 erros/0 skips,
Maven offline Java17/heap512MiB, formatter/lint verdes. Loopback real confirmou
duas tentativas (503 e resposta GraphQL inválida) mesmo sem página concluída;
I/O/retry e recusa pré-envio também testados. Suíte global ainda pendente.

Revisão SQL em andamento, sem migration nova ainda:
- catálogo original não representa dois protocolos; preservar source_kind
  original e vínculos/fingerprints históricos, acrescentando vínculos explícitos;
- CK do selo V015 exige duas páginas; Users precisa uma preenchida e Data Export
  deve manter duas; a preparação B59 sozinha não altera essa constraint;
- a preparação compara applied_at<=published_at, mas V007 amostra applied_at
  depois do retorno da publicação genérica. Conferir por contraprova e corrigir
  a leitura futura sem reescrever preparação ou recibos históricos;
- revisar fences de staging/apply Users e ordem dos locks antes da incorporação.

Não há processo Maven ativo nem efeito de resultado desconhecido. B59 intacto
na fotografia inicial; sucessão exata dos caminhos alterados será emitida ao
fechamento B60. Contagem canônica preservada:67/115,48 pendentes,191rotas,0AGORA.

Próximas ações:
1. Concluir revisão SQL e contraprovas; criar V024/baseline/manifest/validações.
2. Preparar campanha física completa, artefato Windows e matriz A–F limitada.
3. Executar regressão/gates e fechar pacote revisável; só então solicitar a
   aprovação exigida pela seção4, sem alegar qualificação física executada.
