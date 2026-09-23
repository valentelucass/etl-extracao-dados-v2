# Coletas — prova temporal com fonte real e SQL local

10/09/2026. **COLETAS_TEMPORAL_SOURCE_OBSERVED_SQL_CASES_VERIFIED**.
Pedido efetivo: testar com dados reais e chaves já provisionadas para resolver
os impedimentos técnicos e continuar a construção. Esta autorização de teste
substitui o limite offline anterior somente para a investigação delimitada
abaixo. B63 A–D continua concluído; esta é evidência posterior, sem novo bloco
do roadmap ou aceite de qualificação representativa.

## Resultado e decisão técnica

O caminho corrente aceitou os corpos reais e o SQL passou nos sete cenários
temporais exercitados. A ausência do instante de status no Data Export continua
demonstrada. Mais leituras desse mesmo campo ausente não estabelecem equivalência.
Para preservar esse instante, a direção técnica é complementar Coletas com
`statusUpdatedAt` do GraphQL, mantendo identidade, presença e proveniência
separadas, conforme ADR0023/0044 e a direção 7 do STATES. Não trocar o campo
por `updated_at`, data civil ou horário de captura.

Essa direção permite continuar a implementação local da ligação temporal e suas
regressões. Sua ativação operacional ainda precisa de binding real, correlação
de versões/status entre leituras e critérios próprios; nenhum sidecar foi ligado
nesta rodada. A diferença de fonte fica classificada e com tratamento técnico
definido, sem declarar uma equivalência que o contrato 6908 não oferece.

## Fonte real — rodada encerrada em cinco chamadas

O plano próprio escolheu a coorte histórica de 09/09/2026 para comparar os mesmos
registros em duas leituras. Não a declarou representativa. Teto de cinco chamadas,
240 segundos, timeout de 30 segundos, intervalo mínimo de 10 segundos, 64 KiB por
resposta, `per=2` IDs distintos e `first=20`. Sem retry, redirect ou renovação.
Foram executados metadata 6908, Data Export, GraphQL e uma repetição de cada canal:
cinco HTTP 200/curl 0, aproximadamente 49 segundos. Orçamento encerrado 5/5.

| Observação | Resultado por leitura |
| --- | --- |
| Data Export | 5 linhas físicas, 2 raízes; página não terminal |
| GraphQL | 20 nós; `hasNextPage=true` |
| Pareamento por alias e conferência do ID de origem | 4 linhas pareadas, sem diferença de ID; 1 linha fora da página GraphQL capturada |
| Campos comuns nas linhas pareadas | 0 diferenças de status, request/service/finish date e cancellation reason |
| `status_updated_at` no 6908 | Ausente nas 5 linhas |
| `statusUpdatedAt` nas linhas pareadas | Presente e parseável com offset nas 4 linhas; sem fração observada |
| `updated_at` versus `statusUpdatedAt` | 4 diferenças de texto bruto; isso não mede diferença entre instantes normalizados |
| Caminho Java corrente | 5 linhas stageadas, 0 quarentenas, 0 diferenças estruturais de preservação |

As duas leituras GraphQL tiveram 20 raízes em comum, sem mudança observada de
status ou timestamp de status. Repetição sem mudança não comprova evolução,
snapshot ou ausência de corrida entre canais. Os 10 registros lidos são duas
observações da mesma página, não 10 registros distintos. Uma linha sem par na
página limitada não é prova de ausência na origem.

Reutilizado somente o consumidor test-only `ColetasSourceReplay` e funções
revisadas de transporte, com o build B63 Java17 já verificado e os hashes das
fontes conferidos. O replay histórico B62 e seu ledger não foram executados ou
alterados. O rótulo `canonicalIdDifferences` do consumidor é comparação de IDs
de fonte; não é prova do surrogate SQL nem do binding operacional.
Corpos, tokens, URLs de ambiente, cursores e IDs permaneceram em memória/stdin;
nenhum corpo real foi retido ou inserido no banco.

### Falha do modo de autoteste e correção

A importação do helper histórico sobrescreveu a variável `SelfTest` do novo
script privado. A invocação inicialmente destinada ao autoteste iniciou a
rodada real já autorizada. Seu consumo foi reservado e observado no ledger:
cinco chamadas, nenhum erro HTTP, nenhuma repetição posterior ou efeito desconhecido.
A versão efetivamente executada foi preservada em `Source-Proof.executed.ps1`
com o hash do ledger. Ela não é apresentada como autoteste offline aprovado.

O script corrigido preserva o modo antes da importação e exige exatamente uma
opção `SelfTest`/`Run`; a rodada consumida impede nova execução. Passaram quatro
casos sintéticos de comparação e três contraprovas: modo ausente, modos simultâneos
e rodada encerrada. O hash do ledger permaneceu inalterado nessas contraprovas.

## SQL físico — dados sintéticos e rollback

Conexão Windows integrada com alvo explícito `localhost/ETL_SISTEMA_V2_SHADOW`;
nome do banco, objetos e permissões conferidos. Foram usados os procedimentos
instalados de control plane, staging, preparação, DQ e promoção de Coletas,
sem executar o reset/baseline da validação histórica 039. Nenhum DDL, grant,
UAC, alteração de ambiente, campanha B60 ou comando produtivo foi executado.

| Cenário | Resultado físico comprovado |
| --- | --- |
| Aberta → done mais recente | Estado done; aplicação genérica UPDATED; frescor novo |
| Aberta → done retroativo | Estado done e frescor terminal; aplicação genérica STALE_NO_OP |
| Terminal → aberta mais recente | Estado terminal e frescor anterior preservados; aplicação genérica UPDATED |
| Aberta retroativa | Estado/frescor anterior preservados; STALE_NO_OP |
| Repetição idêntica | NO_OP, estado e frescor preservados |
| Status diferente no mesmo instante | Erro 51428; transação revertida |
| Instantes .1231000 e .1234000 com status diferente | Ambos viram .123 em DATETIME2(3); erro 51428 |

A conversão física também confirmou arredondamento de .1236000 para .124.
A primeira execução passou sete casos; uma segunda execução acrescentou
assertivas independentes de frescor e disposição aos casos sem erro. Ambas estão
preservadas. Cada caso terminou com zero transações abertas e zero linhas de
Coletas no escopo sintético próprio. Rollback não promete reutilizar valores de
IDENTITY consumidos; nenhuma linha de domínio da prova permaneceu.

Isso qualifica os casos listados na revisão instalada, não todas as transações,
concorrência ou divergências dentro de um mesmo lote. V004/V010 e a política de
empates não foram alterados. O erro de empate é proteção comprovada; removê-lo
para promover registros sem instante discriminante inventaria uma ordem.

## Critérios e continuidade

| Critério | Estado permitido |
| --- | --- |
| Parsing/mapping do corpo real corrente | Comprovado na página observada e sua repetição |
| Precisão SQL e sete pares temporais | Comprovados em SQL físico, com dados sintéticos |
| T01 evolução real | Não observada; status permaneceu igual entre leituras |
| T02–T05 / T08 representatividade dos casos na fonte | Não comprovada por esta seleção limitada |
| T06/T07 no banco | Somente os pares acima; conflito de mesmo lote/concorrência permanece fora da prova |
| T09 expansão / T10 limite de captura | Expansão observada; nenhuma travessia terminal ou snapshot |
| COL-TIME-01 | Ausência de fonte confirmada; complemento GraphQL definido como direção técnica, ainda sem integração operacional |
| Q-COL-01 / V2-012a/b/c | Sem aceite representativo ou nominal |
| V2-041 | Acesso funcional não comprova rotação/invalidação; permanece aberto |

Evidência sanitizada: [source-summary.json](source-summary.json),
[sql-summary.json](sql-summary.json) e manifesto próprio. Evidência privada da
rodada em `target/coletas-temporal-proof-20260910/`: inventário, autorização,
scripts/versões executadas, plano, ledger, resultados, checks, diff e recibo.
Java produtivo não mudou; 1492/0/0/4 permanece evidência histórica B63, sem nova
execução da suíte completa. Roadmap preservado em 67/115, 191 rotas e zero AGORA.

Próximas ações: implementar a ligação temporal em escopo local delimitado;
qualificar transições reais e os bindings antes de ativá-la; tratar o aceite
representativo e a evidência V2-041 em seus critérios próprios. Não repetir esta
rodada nem exigir outra consulta para redescobrir a ausência já confirmada.
