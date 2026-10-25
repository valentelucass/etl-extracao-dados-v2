# Checkpoint 0602 — Falha LOCK_TIMEOUT medida no banco; correção de integração validada

25/10/2026. Base publicada: b1439313b7c22eb61edb2c5871a5e436b4df66e6.
Etapas 3 e 4 continuam abertas. Houve um diagnóstico C78 novo, sem API,
DML, DDL ou commit de negócio. Nenhuma implantação ou alteração na ESL.

## Banco: condição de parada reproduzida sem repetir a extração

O candidato externo bank-precheck-sql-only-diagnostic-v1, manifesto
6CF2596A30AD150D80B1C7101F0972C42CD77C2D4AA6BCADC2A2AC54AD60DAD3,
reutiliza as 88 classes reais own271, JAR654B e as oito bibliotecas.
O prefixo não chama Source, registro de execução, staging ou begin() do ensaio.
Os 11 modelos próprios passaram no original 1GiB/4min: exit0,11.613ms,
pico496.345.088B. Prova RootSqlPrecheckOwn11Qualification0602.json.

O controller Root separado usa uma conexão C78, uma leitura do provedor,
credencial somente em stdin, prova nativa dos dois lados do mesmo socket e
SQL16.0.1000.6. Antes do despacho foram corrigidos dois pontos locais:
stdin com LF explícito e pin do claim pelo leitor local com lease.
Foram mantidos sob leitura os arquivos do diagnóstico, classes, ferramentas,
catálogo STRICT transitivo e as evidências atuais do fechamento0601.

A revisão funcional independente não encontrou outro bloqueante do controller.
A revisão do diagnóstico apontou limites P2: uma exceção de getter/metadata
pode tornar a atribuição do predicado ambígua; e a margem de query era verificada
depois de prepareStatement, diferindo da fronteira do prefixo original.
Esses limites não são aprovação do banco; serão corrigidos no próximo diagnóstico.

A unidade sql-precheck-native-root0602-01 executou de fato e terminou:
- Contexto inicial passou; INIT retornou; os 11 comandos de lock retornaram.
- STATE_BIND falhou em LOCK_TIMEOUT_ZERO: a espera por bloqueio não era zero.
- Os cinco valores e tipos JDBC foram lidos e aceitos: INTEGER,SHORT,SHORT,LONG,
  INTEGER; INTEGER,SMALLINT,SMALLINT,BIGINT,INTEGER. Profundidade, estado
  transacional, sessão e transação positiva passaram. Unicidade da linha ficou
  UNOBSERVED porque o helper interrompeu antes dessa leitura.
- Failure.type=ILLEGAL_STATE; STRICT não começou, zero SELECTs STRICT.
- Rollback original tentou1 e confirmou1; conexão fechada, sem incerteza,
  sem transação de leitura de cleanup. Não houve leitura de ausência8.
- Child terminou1, que representa recusa diagnóstica. O controller e seu
  original terminaram0 porque o resultado e o fechamento foram observados.
  Original1GiB/4min,15.692ms,pico291.819.520B,sem timeout.
- Child e stdin fechados normalmente, socket exato ausente, SQL nativo inalterado.
  Sem Source, DML, DDL, commit, IDs transacionais ou mensagens brutas persistidos.

RootSqlPrecheckActualCause0602.json conserva os fatos e os limites.
Diagnóstico: C244DC342918B4625DCB0F4E58B45BDC99D386C68F0F5D8F5E3A9E67053C172C.
Recibo original: E4A130979349767A29C8EC456EA7B44C8C7796306BCA43612376DBDD72B76393.
A unidade foi aposentada sem replay. bankQualified=false e retainedChangesOutcome
continua NOT_ASSESSED. Este diagnóstico não demonstra gravação ou ausência integral.

O erro de0601 não conservou seu predicado: não reescrever o recibo antigo como se
ele tivesse observado LOCK_TIMEOUT_ZERO. A nova execução reproduz uma condição
concreta do mesmo prefixo e orienta a correção.

A hipótese atual é o escopo de SET no PreparedStatement/RPC. A
[documentação Microsoft](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-statements-transact-sql?view=sql-server-ver16)
informa que opções SET executadas em SQL dinâmico podem ser restauradas ao
retorno. O [driver12.8.2](https://github.com/microsoft/mssql-jdbc/blob/v12.8.2/src/main/java/com/microsoft/sqlserver/jdbc/SQLServerPreparedStatement.java)
usa RPC para consultas preparadas. Isso ainda exige controle causal real.
O sucessor em preparo troca somente a execução do INIT literal por Statement,
mantém consultas preparadas, os mesmos locks/STATE/STRICT e fechamento, e corrige
os dois limites do diagnóstico. Sem mudança global, servidor, driver ou runtime.

## Integração: causa PS5.1 comprovada e modelos novos aprovados

R24 passou42 próprios:30 corpos anteriores mais12 causais V2/count5,
original0/582ms/pico22.880.256B. A Capture R24 falhou com original2,
1.794ms/pico100.573.184B. O produtor terminou1 após publicar sample sequência1;
pending sequência2 existia. Não atribuir esse encerramento a um kill externo.

O diagnóstico com quatro casos de arquivos próprios no PowerShell5.1 mediu:
File.Replace com terceiro argumento literal null lança ArgumentException;
o null tipado NullString funciona. O comportamento foi igual com e sem um
leitor próprio aberto com ShareDelete. Original0/884ms/pico74.690.560B.
RootProducerAtomicReplaceCause0602.json. Não é problema demonstrado de caminho
longo: o produtor tinha220 caracteres. Nenhuma API, SQL ou CIM nesse diagnóstico.

R29 corrige exatamente esse argumento do produtor; os workers, guard, harness,
42 corpos e limites continuam iguais. Manifesto
84C4E72117619EC29FE4BF5C03631829CE543C097FF8D60B34D3CAE1590031E2.
Passaram42 próprios novos, sem falhas/erros/skips ou função nativa:
original0/460ms/pico23.044.096B, worker62720 igual ao PID do original.
RootR29Own42Qualification0602.json. Não herdar a qualificação de R24.

A Capture R29 NÃO começou: a observação anterior ao despacho encontrou duas
JVMs novas do PM2,44484 e67396, além das cinco da seleção F7EF.
Não existe recibo2 de Capture R29 nem output de uma campanha iniciada.
Root não encerrou processos nem adicionou identidade por pai/nome.
Reobservar o ciclo antes de decidir a seleção; sem ampliar prazos, memória,
privilégios, serviços ou fazer fallback. R30/R31 estão preparados e selados,
sem qualificação de produto7/Root111/Clock4/Main7 por esses arquivos.

## Continuidade e limites

Última Source continua Manifestos0601:315linhas/17manifestos/página final vazia,
três consultas confirmadas, banco não qualificado. Delta3 preservado separadamente;
Cotações0595 não volta a ser o predecessor mais recente. Sem Source nesta rodada.
Contadores globais permanecem parciais.

Usuários continua54.540/seis quarentenas/hasNext,sem EOF ou replay.
INFO300+10, Usuários142 e pacote200 preservados sem adoção.
Chrome ESL permanece somente consulta; sem novo Excel ou paridade aprovada.
Fretes DataExport6389 é distinto de GraphQL; Localizador8656 não é Raster.

Root único executor pesado/API/SQL/browser/Git e editor canônico; demais chats
somente arquivos. Mantidas as restrições SQL16.0.1000.6, instalação, contas,
credenciais, permissões, serviços, certificados, V1/WMS/PM2 e produção.
Sem novo DDL, commit retido ou cutover. Evidências privadas fora do Git.
