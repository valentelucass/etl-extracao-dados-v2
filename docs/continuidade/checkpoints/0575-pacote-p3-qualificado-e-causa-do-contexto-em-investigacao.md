# Checkpoint 0575 — Pacote P3 qualificado; contexto do banco em investigação

25/10/2026. Sucessor de0574. Etapas3/4 abertas. Todos os jobs atuais fecharam;
nenhum executor ativo ao registrar este checkpoint. Root libera cada nova unidade
sequencial separadamente. Não há nova admissão de fonte/SQL por este documento.

## Resultados efetivamente executados

- P3q04:419JUnit distintos/7IT, zero falhas/erros/skips. Compilação completa774main/
  698test, estilo, formatação, pacote, todos os bytes de classe/recurso,8libs e
  fontes Root/frozen sem divergência. Job original2GiB/378.184ms/pico1.072.603.136B,
  exit0/sem timeout. JAR25A095A5789589CA28E29CE96C385B1CB9357A55319A47587FCE5E6066C13309
  em Codex/2026-10-25/etl-v2-p3/q04/target. Coorte44arquivos v5. Qualificado no
  snapshot, ainda NÃO adotado no produto, sem implantação/fullverify. Produto
  corrente continua E05/150JUnit+7IT. RootP3Q04QualificationReview.json fixa provas.
- Banco E05Bv4:85 cenários reais offline aprovados, strict17, selos antes/depois,
  recibo original1GiB/10.299ms/pico632.860.672B/exit0/sem timeout. Nenhum SQL/HTTP.
  RootBankV4QualificationReview.json; não herda85da rodada v3 recusada por log.
- JDBC P3:40 invocações reais JUnit (33preservadas+7novas),23 métodos, zero
  falhas/erros/skips/aborts. Strict17 e recibo1GiB/12.131ms/pico499.412.992B/exit0.
  Usa JARq04 qualificado. RootP3Jdbc40QualificationReview.json. Dummy JDBC;
  nenhuma conexão/DDL/fonte/admissão física. PhysicalTrial original seguePENDING.
- Grade readonly:18JUnit reais aprovados, compilação estrita/selos/classes,
  recibo1GiB/16.718ms/pico360.108.032B/exit0.100SQLs fixos/97casos elegíveis/3contextos;
  Fretes37 e Local60, casos61/62 excluídos. RootReadonlyGrid18QualificationReview.json.
  Isso não prova verdade dos predicados no engine nem revisão Root do texto fixo.
- Catálogo V3:44 grupos offline passaram; primeira unidade física abaixo falhou.
  As unidades anteriores e falhas q01/q02/q03 permanecem preservadas, sem herança.

## Catálogo físico: primeira guarda recusou

entity-trial-fresh-catalog-v3-candidate/runs/live-10250002: conectou ao SQL16.0.1000.6,
  alvo shadow/C78, peer nativo no mesmo socket confirmado antesLOGIN7, certificado
  e nativePID48480/start09/10 preservados. Uma SELECT foi tentada; zero concluídas
  pelo verificador. CONTEXT_MISMATCH antes de publicar os valores do contexto.
  Conexão fechada conhecida, controller encerrado, native inalterado; recibo1GiB/
  15.740ms/exit1/sem timeout. Nona conexãoSQL fechada; zeroDDL/DML nesta unidade.
  Não há35SELECT/13tabelas/13módulos completos nem catálogo físico positivo.

Não reconstruir valores descartados. Cinco linhas CONTEXT dos três catálogos
históricos fechados têm depth0/state1; os verificadores antigos só exigiam depth0.
A nova guarda exige state0 dentro de SELECT com DMV. Isso sustenta uma hipótese,
não comprova o valor da unidade nova. O conector não chama setAutoCommit nem SQL.
Sucessor diagnóstico V4 preservará a guarda e a query, publicando antes dela
somente flags por predicado, shape/type/null e getAutoCommit como getter. Nenhum
login inesperado/valor cru será gravado; não alterar estado/configuração da sessão.

Fontes primárias consultadas para a investigação: Microsoft Learn
https://learn.microsoft.com/en-us/sql/connect/jdbc/reference/setautocommit-method-sqlserverconnection?view=sql-server-ver15
e https://learn.microsoft.com/en-us/sql/t-sql/functions/xact-state-transact-sql?view=sql-server-ver17 .
Elas documentam transação por instrução no autocommit e significado dos estados;
não provam a ocorrência10250002. A política futura depende da nova prova causal.

## Demais frentes preparadas

Composição fonte P3r03:55arquivos (44base+11novos) ainda não compilados. Revisão
independente encontrou RootP3Qualification lendo o result real por teto262.144B;
q04/result.json já tem355.245B. Sucessor r04 solicitado com teto específico finito
e contraprova real; não enxugar/adaptarJSON nem qualificar55 por PASS44. Tabela
de código continua zero unidades. Fonte nova depende dessa composição qualificada.

UsersV3 está congelada7A460C056B2C0094147089261332F08F37DC8384A2B487FDFA89B592E11AC65E:
37casos preparados, zeroexecutados. Teto futuro5.000calls/100.000nodes/5h, retenção
RAM64MiB/256+2chars por entrada, leitor integral em uma passagem, qualificador
original1GiB/45min e futuroJob1GiB/320min. Nenhuma nova reserva/release de fonte.
Valores extremos podem causarSTOPLIMIT antes de100mil, jamaisEOF inventado.
Usuários atual permanece40mil/hasNexttrue/39997válidos/3quarentenas/cursor perdido.

Raster49 mantém a observação108viagens/355paradas/46atributos com valores/230paradas
com issues de latitude/longitude,16repetições deOrdem. Análise congeladaA419DF...,
20testes apenas preparados. Novo diagnóstico externo será qualificado antes de
outra consulta. Sem arredondar/truncar/deduplicar ou aceitar identidade por suposição.

Native auxiliar prepara composição física P3 com capability genuína e orçamento
monotônico anterior ao open. Banco prepara Manifestos/Cotações e composição física
da grade, ambos bloqueados por fatos atuais/qualificação/revisão Root próprios.
P3SQLcincoobjetos/Fretes2/Local1 seguem propostas separadas, sem aprovaçãoDDL.

Totais:3.193HTTP=95DataExport+3.096GraphQL+2Raster,3.194intenções locais,
9conexõesSQL fechadas,0DDL/0commit de negócio retido. C78rollback-only, SQL16.0.1000.6/
instalação/contas/identidades/ACL/permissões/serviços/listeners/TLSglobal/V1/WMS/
produção preservados.43RAW/53exclusões e MainHTTPUNKNOWN/MISSING_NOT_RECONSTRUCTED
intactos. AST44514/116337 até integração final. Não encerrar as quatro etapas por
diagnósticos, Fake, hashes ou delegação; seguir as unidades elegíveis.
