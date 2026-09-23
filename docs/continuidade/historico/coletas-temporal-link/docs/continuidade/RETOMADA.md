# Retomada — Coletas: fonte real e casos SQL comprovados

COLETAS_TEMPORAL_SOURCE_OBSERVED_SQL_CASES_VERIFIED. Cinco chamadas autorizadas,
dois replays correntes aceitos, timestamp de status ausente6908 confirmado.
Sete pares temporais no SQL shadow passaram com rollback; sem DDL/UAC/produção.
Direção local: complemento temporal GraphQL com identidade e proveniência.
Não confundir prova dos casos com representatividade, rotação ou aceite nominal.
Checkpoint atual: [0063-coletas-prova-temporal.md](checkpoints/0063-coletas-prova-temporal.md),
SHA-256 8076baaf0f0e07b11d0483685a1ade0741e8fa3776b223a265a7f55cd6e17d0e.
Relatório: ../catalogos/coletas-temporal-proof/RELATORIO.md. Evidência privada:
target/coletas-temporal-proof-20260910/. Rodada encerrada5/5,nenhum efeito desconhecido.
O helper iniciou a captura autorizada pelo modo SelfTest incorreto; versão preservada,
modo corrigido e sete verificações offline próprias passaram, sem repetir chamadas.
Próxima ação: desenvolver a ligação temporal local antes de sua ativação operacional.
67/115,191 rotas,zero AGORA; instrução permanente do STATES e evidências anteriores válidas.

# Retomada — diretriz de decisão documental registrada

DECISOES_DOCUMENTAIS_REGISTRADAS. Pedido do usuário atendido no início do STATES:
decisões técnicas fundamentadas nas fontes vigentes, sem perguntas por escolhas
já resolvidas ou informações descobríveis na investigação autorizada.
Preservar limites, evidências e continuidade das frentes independentes.
Checkpoint atual: [0062-decisao-documental.md](checkpoints/0062-decisao-documental.md),
SHA-256 503ec299db67b7274782705e2bfa9ddd3991e55fe2399e88cf556786a6ac699d.
Sucessão documental: manifesto-decisao-documental.json; resultados e diff em
target/decisao-documental-20260910/. Nenhum novo bloco ou aceite. O fechamento
B63 abaixo permanece histórico e válido; Java não foi reexecutado nesta manutenção.
Próxima ação: aplicar a diretriz ao próximo pedido, consultando o estado vigente.
Nenhuma pergunta pendente de aprovação.

# Retomada — B63 temporal local concluído; prova externa pendente

B63_TEMPORAL_LOCAL_COMPLETE_EXTERNAL_GAPS_OPEN. A–D integralmente adotadas pelo
usuário e concluídas no escopo local. Checkpoint0061:
[0061-bloco63-temporal-local-concluido.md](checkpoints/0061-bloco63-temporal-local-concluido.md),
SHA-256 0e562229b2f7a7397f01944995aeb9930d21e4ac18fe2ff8b2e8af725e95d725. Cadeia0060→0059→0058→0057 preservada.

Mapa/ADR0044; correção de gap/overlap sem inventar instante; regressões com
ROOT_ARRAY corrente; pacote representativo C com campos/seleções/limites conhecidos.
Verify Java17 offline isolado1492/0/0/4,44 testes novos,91,65%linhas/76,57%branches.
REDs preservados; JDBC é mock,sem SQL físico. V010 e contratos históricos intactos.

[Relatório](../catalogos/bloco63-temporal-local/RELATORIO.md),
[mapa](../catalogos/bloco63-temporal-local/MAPA-TEMPORAL.md),
[pacote C](../catalogos/bloco63-temporal-local/PROVA-REPRESENTATIVA.md).
Evidência própria: target/b63-temporal-local-20260910/,inventory/before,logs,
java-verification,final-checks,diff,receipt e delivery-checks. Conferir os resultados
reais dos validadores; manifesto e recibos históricos não foram reescritos.

COL-TIME-01 permanece aberto:7 diferenças reais B62; sem equivalência demonstrada.
Q-COL-01,V2-012a/b/c,V2-041 e holds continuam abertos.67/115,48 pendentes,191 rotas,
zero AGORA. Nenhum novo aceite. Orçamento B62 encerrado; nenhum saldo reutilizável.
Sem API,.env,credencial,SQL,UAC,B60/B62 novo,JAR operacional,produção,commit ou push.
Nenhuma pergunta pendente ou efeito desconhecido. Não reexecutar probes B62.

Próximas ações:
1. Conferir Test-Bloco63TemporalLocal.ps1 -IncludePrivateEvidence -SelfTest e recibos.
2. Ratificar inputs reais do pacote C antes de qualquer nova prova de fonte.
3. Qualificar precisão/conflito SQL somente sob autorização futura específica.
