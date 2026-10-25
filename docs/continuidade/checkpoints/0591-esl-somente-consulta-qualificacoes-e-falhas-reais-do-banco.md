# Checkpoint0591 — ESL somente consulta; qualificações e causas locais do banco

25/10/2026. Continuidade0590. Usuário abriu a ESL e autorizou exclusivamente
consultas, filtros, leitura e download de Excel; proibiu aplicar ou enviar dados.
A prioridade é a gravação correta no V2, seguida de conferência com os relatórios
correspondentes. Não confundir Fretes Data Export com GraphQL nem presumir Raster
na ESL. Regra operacional: [conferência somente consulta](../../runbooks/conferencia-esl-somente-consulta.md).

## Entregas e verificações executadas

O commit anterior **e6a189515d6307ad9cb13c19db98df271b239d64** foi enviado para
origin/main e conferido no remoto;131arquivos, árvore limpa,70casos do scanner,
varredura4656candidatos/4652textos/4binários aprovados/0achados e catálogo
3contratos/42aspectos/14fixtures PASS. Prova privada RootGitCommitPush0590.json.
Não é implantação. O índice AST posterior atingiu1GiB e encerrou1, sem substituir
o índice anterior; graph-0590-01/memory-receipt.json preservado. Não aumentar o
limite nem apagar o grafo por esse erro. Atualização do índice continua pendente.

Após auditoria da interrupção Users e nova observação de recursos, Root executou
sequencialmente, pelo executor original com1GiB e prazo finito:

| Componente externo | Resultado próprio | Prova Root privada |
| --- | --- | --- |
| Manifestos/Cotações BankV7 |81modelos PASS|RootOffline0591-BANK81.json|
| Catálogo RasterV5 |25modelos PASS|RootOffline0591-RASTER25.json|
| Catálogo UsuáriosV1 |40modelos PASS|RootOffline0591-USERSCAT40.json|
| Owner correção/recuperação Fretes2+Local1V2 |38modelos PASS|RootOffline0591-DDL38.json|

São184cenários locais desses quatro componentes, sem Source, JDBC, provider ou
DDL. Conferidos os resultados, nomes distintos, classes, arquivos/pins e recibos
originais. A primeira leitura Root do resultado Raster usava `case` quando o
schema real usa `name`; corrigida a leitura, sem repetir os25testes. O revisor
original foi preservado. Nenhum modelo admite captura ou qualifica SQL engine.

## Manifestos: duas tentativas encerradas antes dos efeitos

Root preparou uma unidade real delimitada: Manifestos6399/dia17/per50, GET com
corpo, até8chamadas/uma tentativa/3s,1000linhas/7páginas,1C78/90s/10s por query,
catálogo35+35 na própria conexão, readback tipado, rollback/ausência e zeroDDL ou
commit retido. Isso não é uma renovação Users nem ampliação do perfil C78.

1. V7/sessão57406 encerrou1 antes de Java/Source/SQL: Physical.Live.ps1 tinha `-or`
   no início de uma continuação inválida, linha62;15erros de parser em cascata.
   Recibo original1GiB,2processos,10.829ms. RootManifestos0591PreSourceFailure.json.
2. V8 corrigiu somente essa continuação, preservouV7, verificou os3scripts sem
   erro e executou próprios81modelos PASS. Seal
   F814AE91604FA3A0428E8CFC21DD1473DC6A40DF068AAC227B6882D5B4903C1B;
   RootOffline0591-BANKV8.json. Essa revalidação não soma81cenários distintos aos184.
   A tentativa47500 encerrou1 no preflight, antes de provider/HTTP/JDBC:
   REAL_CATALOG_CONTROLLER_CLOSURE_REQUIRED. O consumidor exigia
   CLOSED_OBSERVED; o comprovante STRICT real imutável informa
   JDBC_RETURNED_CLOSED e contém os fatos tipados de fechamento. Não adulterar o
   comprovante nem aceitar UNKNOWN como fechado. Recibo1GiB/21.980ms/4processos;
   RootManifestosV8PreflightFailure0591.json. Contratos preparaV9 com teste positivo
   sobre os artefatos reais antes de nova tentativa física.

As duas tentativas fizeram0chamadas pelos adaptadores ETL e0conexões SQL; o filho
live da segunda não chegou a iniciar. O recibo de controller mantém seu próprio
childClosedKnown=false; Root confirmou o fechamento da árvore do Job original,
sem inventar fechamento JDBC ou sucesso físico. Nenhum dado foi gravado.

## P3: compilou; qualificação ainda falha

Controllerq06/r02, manifesto579630E6B921CA5AF9B3C2FB436D48547CF8B88C1BFFE3C5B36E6C534EDED8E3,
compilou estritamente. JUnit descobriu32métodos/111invocações:99PASS e12FALHAS,
em três métodos parametrizados de captura100ns/non100ns e recusa antes do
provider SQL. Não chamar111PASS. Sessão51722/workerExit2, recibo original fechado
em14.430ms/pico378363904B, Source0/SQL0/providers reais0. O runner não reteve causa
Throwable; Fluxo prepara diagnóstico técnico mínimo e correção causal, preservando
r02. Prova RootP3Controller111Failure0591.json. Sem adoção ou liberação física.

## Consulta ESL e estado restante

Localizado módulo Data Export e Relação de Fretes Detalhada. Filtro padrão web
24/09–25/10 não equivale ao dia17 do ETL. Aviso real54ligações/recomendado30 é
indício de peso, não causa definitiva dos timeouts. Arquivos recentes exibidos
eram de2025; nenhum Excel foi usado para comparação. Navegação web consultiva
ocorreu;0Source acima refere-se aos adaptadores ETL, não a todo HTTP do navegador.

As nove leituras Data Export terminais nos recortes e Raster108/355 permanecem.
Users54540/54534semrecusa/6quar,hasNext=true e ausência de closure/recibo original
continuam inalterados; counters históricos parciais5931/5932 não viraram finais.
Nenhum cursor/replay Users foi criado. ProdutoE05/150+7 e q06/447+7 externo mantêm
seus escopos; etapas3/4 não estão encerradas. DDL ainda sem decisão específica;
RasterR5 sem proteção contínua de metadata demonstrada, não admitido por hash.

Os três chats existentes continuam em unidades somente arquivos: Fluxo
Userswire/P3diagnóstico; ContratosV9 do consumidor de catálogo; Banco sucessor do
probe de contexto JDBC. Root é único executor e editor canônico. Todos os jobs
deste checkpoint têm recibo e encerraram; nenhuma concessão física decorre disso.

Próximas ações:1) corrigir e qualificar consumidorV9 e P3 com causas reais;
2) executar prova elegível fonte→banco→readback→rollback e completar propostasDDL
antes da decisão necessária;3) integrar entidades elegíveis e, com banco correto,
conferir ESL no mesmo recorte. SQL16.0.1000.6/instalação/contas/identidades/
permissões/ACL/serviços/listeners/certificados/trustglobal/V1/WMS/produção intactos.
