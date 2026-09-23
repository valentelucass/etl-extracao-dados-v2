# Checkpoint 0044 — B60, consolidação cumulativa

Anterior: 0043-bloco60-retomada-e-correcao-sql.md; SHA-256
38ba964026b3fae275de19224483dcc9ddd3cf37575d0598d9baf900387af8a4.

B60 físico incompleto: prazo cumulativo encerrado enquanto a última revisão aguardava UAC.
32/74 casos originais comprovados; 42 restantes e uma repetição do cancelamento preparados, sem execução física da revisão final.

A instrução efetiva do usuário posterior à proposta foi adotada como autorização
para solucionar a pendência concreta. Não se exigiram palavras formais adicionais.
O consentimento UAC normal permanece controle do Windows, sem desativação ou bypass.

Correções comprovadas offline: lote SQL com GO elimina cinco declarações duplicadas;
fixture HTTP captura por requisição o cancelamento esperado (RED/ GREEN e negativo
inesperado); duas janelas REPLAY coincidem com a origem SQL; chave temporária usa
Latin1_General_100_BIN2 para comparar source_key. Java não mudou nesta continuação.
Falhas SQL134, desconexão tardia e SQL468 continuam registradas, sem reescrever ledgers.

Cumulativo: 54/240 sqlcmd, 33/80 JVMs, 34/400 HTTP; zero reembolso ou renovação. Deadline: 2026-09-10T04:40:10.0501172Z.
Recuperação SQL comprovada: SERVICE21, replay/force desligados, quatro Users scopes v4 revogados, políticas temporárias revogadas e grants temporários retirados. V024 e multiconjunto histórico preservados; zero efeitos desconhecidos.

Pacote final: database/proposals/bloco60-restante-r2/package.json; 476 arquivos.
SHA-256: 6172093677610a4f9415468f7c18f9531c6826a702c7e2a9a9c1cbf61021dca7.
Alvo exclusivo localhost/ETL_SISTEMA_V2_SHADOW, suporte administrador,
etl_v2_exec e etl_v2_view; fonte apenas sintética em loopback.

Provas autoritativas: target/execucao-b60-corretiva-20260910-0040/physical-verification.json e os ledgers
physical-corrective/cc4f84cb37f69724, physical-continuation/0560a96925160b1e,
physical-remaining/60e96d5d9b27ecf6 e, se executado, physical-remaining-r2/6172093677610a4f
sob target/bloco60-local/. Logs de erros e compensações preservados.
Revisão, gates, diff e recibo independente: target/execucao-b60-corretiva-20260910-0040/final/.
Java17 verify anterior: 1394 testes, zero falhas/erros, quatro skips; evidência
target/b60-correcao-20260910/verify-01/, vinculada pelo recibo anterior.

Nenhum novo checkbox. 67/115 = 58,26%, 48 pendentes, 191 rotas, zero AGORA.
O recorte local não fecha V2-022 pai, Q-USR01, V2-012a/b/c, V2-047, V2-013,
V2-050 por entidade, V2-038, release ou cutover. Não reabre V2-033 nem atribui
novo aceite às cinco verticais. Users mantém SHADOW_UPSERT_ONLY transitório;
hasNextPage=false não prova snapshot, completude ou exclusão. Legado segue escritor.

Próximas ações: consultar relatório/recibo final; preservar evidências; iniciar
outra frente ou nova janela física somente dentro de autorização efetiva própria.