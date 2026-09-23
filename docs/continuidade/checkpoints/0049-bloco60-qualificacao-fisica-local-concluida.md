# Checkpoint0049 — qualificação física local B60 concluída

Data UTC:2026-09-10T16:20:32.7512046+00:00. Anterior:0048,
SHA-256 894b8fd66f8c7c9cee7562cefc19b85a701cce0aca6eb0254a289e5d79ae8d90.
Objetivo e critérios:target/bloco60-local/PROMPT-ADOTADO.txt,frentes A–F.
Estado:ACEITO_NO_ESCOPO / QUALIFICACAO_FISICA_LOCAL_USUARIOS.

Autorizações efetivas:“pode renovar automaticamente para concluir isso logo”
e “Autorizo as três JVMs adicionais e reenviar o UAC”. Registros privados:
USER-AUTHORIZATION.txt e ADDITIONAL-AUTHORIZATION.json. Alvo exclusivo
localhost/ETL_SISTEMA_V2_SHADOW,contas existentes,fonte sintética,UAC normal.
Teto corretivo83 JVMs,240 SQL,400 HTTP;nenhuma restituição ou expansão de alvo.
Inventário inicial2264 arquivos e cópias before/ em target/b60-conclusao-20260910/;recibo anterior
ad82571a…/3538 artefatos preservado via sucessão exata de quatro arquivos.

## Provas e recuperação

| Critério | Camada/prova | Resultado |
| --- | --- | --- |
| A:coexistência/identidade | SQL/JAR das seis verticais,72 casos anteriores rechecados | Mesma origem lógica,protocolos GraphQL/Data Export,sem remap |
| B:migração/auditoria/DQ | V024 histórica,readbacks e SQL060 físico | Sete checks adversariais;preservação antes/depois |
| C:JAR/Windows | Provas anteriores e par b776e40f… | JAR oficial,contas restritas,exits0/0,consumos distintos |
| D:durabilidade/concorrência | 16 amostras SQL simultâneas,requests/recibos físicos;revisão com8 negativas |74/74 casos,uma publicação,dois históricos;sem republicar efeito |
| E:limites/agregado |16 ledgers,HTTP observado,52 ocorrências reconciliadas |204 SQL/83 JVM/75 HTTP;36 tentativas/18 publicações |
| F:recuperação/integridade |Perfil SQL,multiconjuntos,processos,validadores,diff/recibo |SERVICE35/scopes18,V024/históricos preservados |

Provas:target/b60-conclusao-20260910/qualification-verification.json,concurrency-review.json,
verified-sql/result.json e final/. Agregado:65 páginas/60 linhas/21 selos/
21 históricos Users,zero sessões restritas,dois protocolos. Zero grants
temporários,efeitos desconhecidos,processos próprios e listener62160.
16 ledgers fechados,295 reservas,2/8 escrow;nenhum reembolso.

## Falhas preservadas e correções

A campanha26ba79e… encerrou cedo as duas JVMs;recuperação33/16 confirmada.
O UAC complementar foi cancelado sem SQL. Depois da aprovação adicional,
b776e40f… observou ambas as JVMs esperando e terminando com exit0,porém
recusou o teste de igualdade do lock,cuja resource_lock_partition é NULL.
O resultado desse controlador continua NOT_QUALIFIED. Não fabricar PASS.

A revisão independente aplica o critério original de tentativas concorrentes
na mesma ocorrência/partição às amostras físicas,PIDs próprios,requests
congelados,exits e recibos iguais. São16 amostras com cadeia de bloqueio entre
os dois participantes;8 contraprovas recusadas. Isso comprova o critério D
sem depender do comparador defeituoso nem de simulador JDBC. O SQL real
reproduziu oldMatches0/correctedMatches1 e recusou três recursos diferentes.
SQL059 corrige a igualdade preservando todas as colunas;seu laço completo
não foi repetido com duas JVMs,nem é declarado aprovado por essa inferência.

O primeiro SQL adversarial falhou3930:erro esperado deixava transação inválida
para o teste seguinte. SQL058 isolou as fases,mas exigiu transação aberta após
a procedure já ter feito rollback. SQL060 exige o estado correto(0/0) e passou
os sete casos. 057/058 e seus pacotes/logs falhos são históricos imutáveis;
060 é a validação adotada. Todas as rodadas SQL provaram preservação global.

Testes offline e físicos pertinentes concluídos;Java/migrations inalterados.
1397/0/0/4 é suíte histórica,com três skips de symlink e um opt-in de cotações.
Não há novo checkbox:67/115,48 pendentes,191 rotas,zero AGORA. Não fechar pais,
Q-USR-01,release ou cutover. Users transitório SHADOW_UPSERT_ONLY não comprova
snapshot completo,ausência na origem ou prontidão produtiva.

## Continuidade

1. Conferir o recibo e a verificação independente em target/b60-conclusao-20260910/final/.
2. Usar STATES como autoridade;preservar todos os pacotes,ledgers e checkpoints.
3. Qualquer próxima fatia depende de adoção própria;não executar fonte real,
   novo bloco ou JVM além do teto83 por causa deste aceite local.

Nenhum efeito pendente de recuperação ou input externo para o aceite local.
O fechamento revisável desta entrega inclui o diff sem aplicação e os gates
de continuidade/integridade/segredos documentados no recibo final.