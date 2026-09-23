# Checkpoint0063 — Coletas: fonte real e pares temporais no SQL

10/09/2026. Predecessor0062-decisao-documental.md, SHA-256
503ec299db67b7274782705e2bfa9ddd3991e55fe2399e88cf556786a6ac699d.
COLETAS_TEMPORAL_SOURCE_OBSERVED_SQL_CASES_VERIFIED.

Pedido efetivo: testar com dados reais/chaves provisionadas para resolver os
impedimentos temporais e continuar a construção. Autorização de teste atual
substitui o limite offline B63 nesta investigação; não concede cutover, aceite
de negócio, rotação, DDL, UAC, grants ou runtime operacional.

Fonte: rodada nova própria, 5/5 chamadas read-only, HTTP200/curl0, sem retry;
metadata6908 e duas leituras da mesma página Data Export/GraphQL de 09/09/2026.
Por leitura: 5 linhas/2 raízes e20 nós;4 linhas pareadas/1fora da página limitada;
campos comuns iguais, timestamp de status ausente6908. Dois replays correntes
aceitos, sem quarentena ou perda estrutural. Nenhuma mudança de status observada
em20 raízes comuns entre leituras; sem snapshot ou representatividade.

Falha no helper: importação sobrescreveu SelfTest e iniciou a rodada real já
autorizada antes das contraprovas. Cinco efeitos reservados/observados; versão
executada preservada, nenhum efeito desconhecido. Modo corrigido: quatro casos
sintéticos e três guardas passaram sem alterar ledger. Não chamar novamente.

SQL: localhost/ETL_SISTEMA_V2_SHADOW confirmado, autenticação integrada existente.
Sete casos físicos passaram; rollback e ausência de linhas no escopo próprio
conferidos. Segunda execução reforçou frescor/disposição. DATETIME2(3) arredonda;
instantes distintos podem colidir e resultar em51428. Promoção terminal retroativa,
antirregressão, stale e NO_OP comprovados nos pares; sem qualificação de lote ou
concorrência. Nenhuma migration/policy/DDL foi alterada.

Decisão sustentada por ADR0023/0044: complementar o instante de status via
GraphQL com proveniência; não substituir por updated_at/data civil/captura.
Direção de implementação local definida, sem sidecar operacional ativado.
COL-TIME-01/Q-COL-01/V2-012a/b/c/V2-041 continuam abertos onde falta seu critério.

Inventário2390; snapshots próprios dos quatro deltas em
docs/continuidade/historico/coletas-temporal-proof/. Catálogo/relatório:
docs/catalogos/coletas-temporal-proof/. Scripts, logs, ledger, resultados,
checks/diff/recibo em target/coletas-temporal-proof-20260910/; conferir retornos
antes de presumir PASS. Evidências anteriores preservadas. Java não mudou;
1492/0/0/4 é evidência anterior.67/115,191 rotas,zero AGORA; nenhum checkbox novo.
Recuperação documental pelos snapshots, sem apagar provas; rodada encerrada.

Próximas ações:
1. Implementar a ligação temporal local delimitada pela decisão/relatório.
2. Qualificar transições reais e bindings antes de ativação operacional.
3. Receber os aceites representativo e V2-041 próprios, sem os presumir do acesso.
