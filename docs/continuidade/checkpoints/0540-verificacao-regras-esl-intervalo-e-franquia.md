# Checkpoint 0540 — verificação das regras ESL de intervalo e franquia

23/10/2026. Pedido do usuário: “tem regras, verifique as regras a ser seguida”, referindo-se ao intervalo mínimo2segundos e ao pacote de chamadas informados pela ESL. Unidade de auditoria local e correção dos exemplos documentais; nenhuma requisição HTTP, SQL, leitura/materialização de token ou execução Main.

## Regras e conferência

| Regra a seguir | Evidência local conferida | Situação |
|---|---|---|
| Respeitar pelo menos2segundos entre chamadas, também entre páginas, INFO/DATA e novas tentativas. | Governor aplica minimumRequestInterval em cada acquire; perfil preparado deployment02/03/04/05 usa2000ms e max-in-flight1. | Conforme no perfil preparado, não prova de execução atual/integrada. |
| Evitar sobreposição de consumidores do mesmo limite. | Um governor mantém semáforo/budgets no próprio processo/objeto; o mutex das sondas protege somente sondasV2 nesta máquina. | Não há nesta evidência coordenação global entreV1/V2/outros consumidores. Escopo contratual precisa ser confirmado. |
| Não ultrapassar a franquia contratada. | Contadores por ciclo/workload existem; a amostra preparada tem limite2chamadas. | Esses2 são orçamento da amostra, NÃO franquia do fornecedor. Limite contratado, período e saldo vigente não foram informados. |
| Contabilizar tentativas e recursos auxiliares no orçamento local. | EslRequestGovernor.reserve incrementa origem/workload por permit; DataExportHttpExecutor.acquire ocorre em cada tentativa. | Controle local não equivale a contador contratado/durável entre ciclos/processos. |
| Em diagnóstico429, parar novas chamadas dependentes e esclarecer limite/consumo antes de sucessor. | Probes/diagnóstico0538 param em429; perfil preparado tem max-attempts1. | Não confundir com runtime genérico: este suporta retry limitado em429 quando configurado com mais tentativas. Não é seguro presumir que429é sempre transitório. |
| Não atribuir saldo novo ao tempo decorrido ou a um200isolado. | RetornoESL não informa saldo; testes21/10 tiveram19HTTP sem429. | Mantém-se pendência de franquia/escopo; não renovar autoridade/reserva consumida. |

O valor2s é requisito informado pela ESL, registrado em0539 e já documentado na leitura local de22/09. Nosso diagnóstico executado21/10 utilizou intervalo próprio3s. Essas provas são de escopos diferentes: fonte, perfil preparado e diagnóstico não constituem uma garantia de operação global.

## Inconsistências demonstradas

1. `config/application.example.properties` recomendava250ms em Data Export e GraphQL. Corrigidos SOMENTE esses dois exemplos comentados para2000ms, com explicação do mínimo e do alcance local.
2. `EslResiliencePolicy.java:27–35` exige intervalo não negativo e teto60s, sem piso2s. `RuntimeConfigurationFactory.java:229–235,317–323,601–608` lê o intervalo como inteiro não negativo; portanto configurações genéricas abaixo2000ms continuam possíveis. `RuntimeConfigurationFactoryTest.java:720` usa250ms em fixture válida. A correção do exemplo NÃO implementa esse piso.
3. `DataExportHttpExecutor.java:94–158` considera429retryable e pode repetir com maxAttempts>1, impondo embargo conforme policy/Retry-After. O perfil de amostra conferido usa UMA tentativa; não generalizar sua parada ao runtime inteiro.
4. `RuntimeOperationalExecution.java:571,605` cria governors por composição/invocação. Não há aqui prova de semáforo persistente, coordenação entre processos ou compartilhamento comV1. `LocalAnalyticUsersRuntime` usa scope sintético e intervalo0; não confundir esse caso offline com perfil remoto.

## Alteração e validação executadas

- Exemplo não secreto:250→2000ms em duas linhas comentadas; fontesJava, .env, perfis privados preparados, JARs e históricos não modificados.
- Conferência local dos quatro perfis preparados:minimum-request-interval-ms2000, max-in-flight1, retry.max-attempts1, max-requests-per-cycle2. Somente esses números foram lidos; nenhum segredo emitido.
- Leitura focada de policy, governor, configuração, executor, ADR0011 e protocolo de sondas. Graphify e ai-memory usados como índice/histórico, sem promover memória a prova.
- Verificados os dois valores do exemplo, links atuais, UTF-8 e hashes estáveis dos quatro fontesJava auditados. Não houve build, suíteJava ou testeHTTP nesta unidade; não declarar enforcement novo por inspeção/edição documental.
- Recibo local sanitizado, com os quatro hashes e as verificações: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/verificacao-regras-esl-20261023/verification.json`.

## Pendências para assegurar cumprimento em operação

1. Validar o piso2s na entrada da configuração remota, preservando a distinção de scopes sintéticos/testes, e qualificar a mudança antes de gerar nova coorte operacional. Até lá, tratar intervalo remoto<2000ms como configuração não admitida na revisão de preparo.
2. Definir a coordenação efetiva das chamadas que compartilham o limite, considerandoV1/V2 e o escopo informado pelo fornecedor. Não pausar/alterarV1 ou serviços por suposição.
3. Obter limite, período de apuração, saldo/consumo e abrangência da franquia (conta/token/IP/outro) da ESL/gestor; casar orçamento local com esse limite em cada operação autorizada. Não criar remainingRequests/quotaBucket ou considerar max-requests-per-cycle como saldo comercial.

Resultado: regras verificadas; exemplo corrigido; conformidade do perfil preparado é parcial e delimitada. Nenhuma liberação automática de Main/SQL/captura/retenção. Fretes6389/422, MainHTTP históricoUNKNOWN, C78rollback-only, cobertura/G05/paridade e extração completa conservam suas pendências.
