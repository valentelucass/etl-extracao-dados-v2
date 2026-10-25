# Checkpoint 0576 — Contexto do banco isolado; Usuários V3 em qualificação

## Identificação e objetivo

25/10/2026, 2026-10-25T06:37:31.528872+00:00. Anterior: [0575-pacote-p3-qualificado-e-causa-do-contexto-em-investigacao.md](0575-pacote-p3-qualificado-e-causa-do-contexto-em-investigacao.md),
SHA256 0D364D86CDB25F6EDEF221B5462F27624AF0D167FACE49F3A3A0073DA4AE79AC. Objetivo: concluir as quatro etapas da extração V2;
etapas 3/4 continuam abertas. Preparação e diagnóstico não encerram a entrega.

## Autorização e limites

O usuário pediu continuar os testes, investigar com os recursos disponíveis e
concluir as quatro etapas, autorizando a divisão em outros chats. Root supervisiona
os três chats ETL V2 e integra os resultados; os demais trabalham estaticamente.
Consultas de fonte respeitam o intervalo de 3 segundos e tentativa única.
Nenhuma nova consulta de fonte foi executada neste checkpoint.

Preservar V1/WMS/produção, SQL 16.0.1000.6, instalação, contas, autenticação,
permissões, serviços, listeners, certificado e trust. C78 segue rollback-only;
nenhum DDL/Flyway ou commit de negócio retido foi aplicado. A pergunta humana
específica sobre os dois objetos de Fretes continua pendente, sem ampliação para
Localização ou P3 e sem repetir a pergunta. Não inventar binding ou autoridade.

## Execução e evidência

- Catálogo V4: 59 grupos offline efetivamente aprovados, strict JDK17, recibo
  original de 1 GiB, 9.939 ms, pico 368.095.232 B, exit0/sem timeout.
  RootCatalogV4OfflineReview.json fixa qualificação e recibo.
- Nova unidade física V4/live-10250003: primeira SELECT recusada por
  CONTEXT_MISMATCH; conexão fechada conhecida, peer no mesmo socket verificado
  antes de LOGIN7, processo SQL original inalterado. Recibo original 1 GiB,
  14.425 ms, pico 286.441.472 B, exit1/sem timeout. Uma SELECT tentada, nenhuma
  concluída pelo verificador, nenhum DDL/DML/HTTP/commit explícito.
- Os diagnósticos da unidade real mostram getAutoCommit TRUE antes e depois
  do escopo do ResultSet, profundidade de transação zero e todas as demais
  guardas esperadas satisfeitas. Somente transactionStateZero é falso. O valor
  exato não foi retido: não afirmar que foi 1 nem reconstruir a linha descartada.
  RootCatalogV4ClosureReview.json (SHA256
  89544892EECE1578873119B1DFC2CE3D854C91F7BBD6D946BEB6357816859E8F)
  confere os três artefatos e o fechamento.
- Total atual: 10 conexões SQL fechadas, 0 DDL e 0 commit de negócio retido.
  Fonte permanece em 3.193 HTTP confirmados/3.194 reservas locais:
  DataExport95, GraphQL3096, Raster2. Nenhuma falha ou reserva vira HTTP confirmado.

## Alterações e decisões

A exigência de XACT_STATE=0 dentro de uma SELECT não representa corretamente
a confirmação automática de cada comando. O sucessor diagnóstico V5 manterá
o mesmo SQL e as demais guardas, exigirá getAutoCommit TRUE e profundidade0,
aceitará durante a consulta somente estados textuais exatos 0/1 e recusará
-1, NULL, formatos inesperados, transação ambiente e falha dos getters. Publicará
categorias técnicas, sem valores inesperados. Não usará SET, setAutoCommit,
nova SELECT ou alteração da sessão. Isso ainda precisa de testes e de nova
unidade física finita; não é catálogo positivo ou admissão de escrita.

Referências primárias: documentação Microsoft sobre
[autocommit JDBC](https://learn.microsoft.com/en-us/sql/connect/jdbc/reference/setautocommit-method-sqlserverconnection?view=sql-server-ver15)
e [XACT_STATE](https://learn.microsoft.com/en-us/sql/t-sql/functions/xact-state-transact-sql?view=sql-server-ver17).

A composição física da grade readonly terá sucessor separado do core, com os
100 SQLs preservados, 18 regressões e novos casos causais. A revisão Root dos
textos fixos está em curso; a inspeção de hashes/estrutura não é parser geral,
prova de predicados no engine ou teste completo de procedimentos.

P3q04 mantém 419JUnit+7IT aprovados, sem adoção ou implantação. Produto corrente
continua E05/150JUnit+7IT. Na composição posterior, o rascunho r04 corrige o
limite insuficiente do leitor do resultado real de 355.245 B, isola Python antes
dos imports e separa a parada da factory da parada do grupo. A revisão estática
não achou outro bloqueio nesses três ajustes. Coorte preparada passa a 56
destinos (44v5+11Java+recurso original), 22 invocações novas preparadas e zero
executadas. Prova positiva Windows/Unit será fase própria após qualificação,
sem preencher evidência artificial ou conceder fonte.

Raster: composição externa V2 congelada, manifesto
400FCA42DA50CD88DCBE61848C251A8935452C02CEBBF8E5EF6F62D5F8B0261B,
45 casos preparados/0 executados e revisão independente em curso. Distinguirá
causas de rejeição decimal por histogramas técnicos; não altera parser,
coordenadas, identidade ou multiplicidade. Os 49 campos/230 paradas com issues
da observação real anterior permanecem fatos separados.

## Processo próprio ainda ativo

Único executor: Usuários V3 offline37, sessão Root46905, pasta
users-successor-live-candidate-v3/q-20261025, manifesto
7A460C056B2C0094147089261332F08F37DC8384A2B487FDFA89B592E11AC65E.
Executor original 1 GiB/45 min; transporte fake, zero fonte/SQL. Confere prefixo
sintético real de 5.000 páginas/100.000 nós/20.001 metadados selados, memória
compartilhada de IDs/cursors e fechamento/leitura integral. Os 37 casos ainda
não possuem resultado: aguardar processo, relatório efetivo e recibo antes de
liberar a próxima unidade. Não iniciar outro Java/Maven/SQL/fonte/índice.

A ocorrência real anterior terminou em 40.000 observados/39.997 válidos/
3 quarentenas, ainda com hasNext=true; o cursor não foi retido. A preparação
V3 e este teste sintético não completam essa leitura nem autorizam replay.

## Retomada imediata — até três ações

1. Fechar Usuários offline37 com relatório/recibo efetivos e revisão; só então
   liberar a próxima unidade sequencial. Sem repetir ou renovar automaticamente.
2. Qualificar os sucessores V5 do catálogo e da grade readonly, conferir os textos
   e executar unidades finitas reais separadas para obter evidência atual do banco.
3. Qualificar a composição P3 r04 e sua fase positiva Windows, o Raster45 e as
   composições físicas de Manifestos/Cotações/P3; integrar somente bytes aprovados.

Bloqueio externo parcial: autorização DDL específica de Fretes ainda sem resposta
humana. Demais escopos SQL não foram aprovados por essa pergunta. Continuar o
trabalho independente elegível. Conclusão exige prova real de fonte, mapeamento,
staging tipado, readback independente e fechamento de todas as entidades.
