# Checkpoint 0535 — Auditoria complementar das dependências empacotadas

Data UTC: 2026-10-17T00:25:52.8109729Z. Pedido USER_20261016_CONTINUAR_V2; instrução atual: “conclua entao, um a um até encerrar”. Escopo: encerrar sequencialmente as frentes locais elegíveis enquanto a ESL aguarda o gestor. Anterior: docs/continuidade/checkpoints/0534-qualificacao-local-e-integridade-de-estado.md, SHA256 4fcbb38ef32233e83a86b42d85c7d309467fa20c88c81db5af73799d44e98421.

Zero autoridade física nova. ESL em standby: nenhuma INFO/probe/captura/reserva/quota renovada pelo tempo. C78 operacional rollback-only; E3 não é herdado. MainHTTP anterior UNKNOWN, MISSING_NOT_RECONSTRUCTED, V1 parado pelo usuário, SQL Server 16.0.1000.6 e proibições permanentes preservados. Root único docs/CI/autoridade; Banco único SQL/TDS. Jobs pesados somente no executor vigente, serial, 2 GiB por processo/árvore, piso4096MiB/comprometimento85%, prazos finitos. Documentação, desenho, modelos e testes locais não aprovam efeitos SQL/Source, retenção real, paridade integral ou cutover.
## Segunda frente: auditoria complementar efetivamente executada

Escopo de efeitos:1 HTTPS público OSV, SourceESL0, SQL/TDS0, provider0, credenciais0. A unidade pública separada foi delimitada pelo Root no pedido atual do usuário, após0534. Não remove o EXTERNAL_HOLD, não usa caller/egress alternativo, não renova C54 e não admite release. O cliente ficou fora do código/runtime do repositório e está arquivado com seus bytes executados.

Primeiro foram executados `PolicyOnly` e `VerifyImplementation`: ambos PASS, fingerprint `83c8698663f33b0e79ac987d72c7853fb59b12af42f31b07d1809377a454e056`,33 casos sintéticos únicos (3 de passagem/30 de bloqueio), zero exceções efetivas. São duas verificações do mesmo conjunto, não66 casos e não uma auditoria real do scanner. O cliente complementar passou24 testes offline com transporte sintético e guardas contra rede real.

Root verificou o [protocolo oficial querybatch](https://google.github.io/osv.dev/post-v1-querybatch/) e o [detalhe por ID](https://google.github.io/osv.dev/get-v1-vulns/). A ordem das respostas corresponde às queries; paginação indicaria parcialidade e o detalhe preservaria o ID exato. O plano novo registra essa revisão como proveniência, sem fabricar autoridade operacional.

## Resultado observado

Consulta concluída em2026-10-17T00:23:53.699Z (16/10/2026,21:23 em São Paulo). Um POST, HTTP200, sem retry/redirect/fallback, body completo e fechamento de resposta/conexão confirmados pelo cliente.518bytes no corpo enviado,473bytes HTTP recebidos,28bytes de body gravado. Resposta literal: `{"results":[{},{},{},{},{}]}`; SHA256 `75f196f26b823bd31225287828f28e3ce63dda93cce61d54965a97dd460b3faf`. Não houve necessidade de GET de detalhes nem token de paginação.

| Biblioteca Maven empacotada | Avisos correspondentes retornados nesta consulta |
|---|---:|
| com.fasterxml.jackson.core:jackson-annotations:2.18.11 |0|
| com.fasterxml.jackson.core:jackson-core:2.18.11 |0|
| com.fasterxml.jackson.core:jackson-databind:2.18.11 |0|
| com.microsoft.sqlserver:mssql-jdbc:12.8.2.jre11 |0|
| org.slf4j:slf4j-api:2.0.16 |0|

Conclusão restrita: `NO_MATCHES_RETURNED_BY_OSV_IN_THIS_QUERY`, `COMPLEMENTARY_OBSERVATION_COMPLETE`. Não se conclui risco zero ou segurança de uma biblioteca. Não há severidade/score a inventar, idade global da base OSV conhecida ou equivalência com NVD. Os outros componentes build/test/native e o projeto inteiro não estão cobertos. Apenas GAVs públicos foram enviados; nenhum path/hash/JAR/configuração/caller/dado de negócio foi transmitido.

Os cinco arquivos físicos foram conferidos contra manifest/lock e rehashados antes/depois. JAR preparado05 permaneceu `f0b6d689811f150dae8c7166a05b606b2e2d97126e6c575af93d3352515a01f7`; manifest e lock também ficaram iguais. Nenhum POM, versão ou pin foi alterado. Plano, request exato, body/status/UTC/hashes, cliente, testes e receipts foram preservados no índice da etapa.

Limites mantidos: uma batch e até32IDs únicos,10s/request,120s globais originais,8MiB HTTP; watchdog3min/2GiB serial. A execução pública usou21.663.744B de pico por árvore,1.576ms de receipt, sem timeout; programa registrou1,093s. O executor local da política/24tests também terminou0. Nenhuma chave de API, proxy autenticado, cookie ou trust customizado foi usado.

## G05 continua pendente, sem relatório substituto

Dependency-Check12.2.0 e sua cadeia transitiva não estão no repositório Maven local; DB/feed também estão ausentes. Isso é falta material comprovada, não demonstração de impossibilidade em2GiB. Não foi executado Dependency-Check, não existe relatório real desse scanner nesta etapa e nenhum JSON OSV foi convertido artificialmente para schema1.1. `G05Accepted=false`, `fullProjectDependencyAudit=false`; política/threshold0.0/failOnError/exceções e incidente continuam intactos.

Para fechar a auditoria completa, permanecem necessários distribuição/cadeia12.2.0 com procedência, descriptor/canais efetivos verificados, DB/feed compatíveis com origem/data verificáveis e uma execução real limitada seguida da classificação de achados no contrato existente. Não inventar flags offline nem aceitar mtime como freshness. O complemento público está encerrado; o gate completo não está fechado.

Cobertura bootstrap continua8023/10071=79,6644%, déficit34 linhas; a consulta não altera essa pendência, os oráculos de fonte ou V2-012a. Próxima frente elegível: adoção somente da direção de desenho e qualificação do recorte puro de retenção, mantendo o perfil C78 operacional não adotado e rollback-only.

