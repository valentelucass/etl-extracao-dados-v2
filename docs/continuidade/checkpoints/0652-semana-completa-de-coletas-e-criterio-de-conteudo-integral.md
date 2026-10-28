# 0652 — Semana completa de Coletas e conferência de todo o conteúdo

27/10/2026. O usuário exige uma semana inteira de uma entidade, cada linha,
valor, coluna e total, incluindo o que estiver faltando ou sobrando.
Nenhuma entidade foi validada integralmente. Os resultados parciais0650/0651
não encerram esse aceite. A prioridade passa a ser Coletas6908,19–25/10/2026;
Fretes fica congelado como trabalho secundário.

Equipe0649: supervisor coordena/revisa/documenta/Git; Execução é o único
executor pesado; Campos revisa arquivos/semântica; ESL controla o navegador.
B = C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01.
T = B/supervisor-team0649-01. Dados de negócio ficam fora do Git.
HEAD/origin0651: ce97985bcf18a7718ee17eeca6c5f34916985cd4.
Recibo B/RootGitCommitPush0651.json:
8CD164F8862385490FBBC1AD3BF5C1B8D5B5AA3E4EE67B334D92D4C1F6697AC0.

## Exportação semanal e inventário completos

Data Export6908 foi exportado uma vez e baixado. Filtro picks[request_date],
Data Solicitação,19/10/2026–25/10/2026; picks[finish_date],
picks[service_date], picks[corporation_id], picks[status] e
scopes[by_updated_at] vazios. Os31 títulos/posições/IDs/caminhos permanecem;
isso não aprova automaticamente aliases. Fuso e inclusividade do provedor
não foram expostos pela UI. Nenhum template/configuração foi alterado.

Pedido T/conferencia-esl/request-weekly-coletas-esl0652.json:
BA58D153551EFD5569B451F6A4D4DEA90C490B4DDC37476CB1E45B470E087895.
Manifesto95070152152FD4B7AA8B9F8F9F9E3F4E344A51FB77ADAC80519593C625B5058A.
XLSX284.911bytes:
31BC21FE4A7131063243375CBA142AE45BD8F1032A3730E607AFF179319AF77A.
Root verificou seis referências e preservou cópia para o usuário; recibo
T/review-supervisor-weekly-coletas-esl0652-01.json:
3BF1ABD09D5F57C3500F5DAE3FC3D2F6E5289019D8936C1F7F97B408A241D4E0.

O inventário físico completo preservou XML, strings, tipos, estilos e
números originais. Original0/6.849ms/661.524.480bytes sob1GiB, slot fechado,
histórico preservado; zero Source/SQL/DDL/compilação. Há1planilha,
1.526linhas de dados e31colunas, sem coluna física adicional, fórmula,
linha vazia ou cabeçalho duplicado. São1.155 representações de ID distintas,
371ocorrências adicionais:1.050IDs×1,97×4,7×9,1×25. A identidade Source
ainda precisa ser qualificada. Há19grupos de linhas completas repetidas,
46repetições além da primeira; nenhuma removida. Totais Decimal mecânicos
ficam privados; ID/serial de data não são medidas de negócio.

Entrega T/response-weekly-coletas-inventory0652-v1.json:
80AC1A55AD7A5620A98ACC346CDD59E5FCFE4109B07131051A4CC6FB9BE6C755.
Prova B/weekly-coletas-xlsx-inventory0652-01-root-control/qualification.json:
FA2862ED92BA3E1003C1711DAC2F03C4CCC9EAE1B12D9D38E4423AC5BCCE27B8.
Inventário095B2D477A559F80267FDCB7F2561685F5E4BA18EFA5F0FD1015929F503F56C5.
Ainda zero células confrontadas com uma nova captura semanal Source.

## Critério e método de conteúdo integral

T/request-supervisor-weekly-coletas0652-02.json:
711E0F05E59BEEB2130AC6DD4E2C76B6E63D371573074C1CDBB875BC1216F0CA.
Dez pontos: escopo idêntico; terminal/retenção completa; identidade/grão;
multiplicidade bidirecional; união de todas as colunas/campos; valores/tipos;
totais por medida; fonte→extração→readback; diferenças explícitas; decisão
integral somente ao fechar tudo. O arquivo01 inválido/null é supersedido.

Matriz Campos31+campos adicionais:
T/campos-regras/coletas-weekly-acceptance0652-02/acceptance-matrix.json,
303F7A9997300F2189669A296AEC1DD7398D5F58446A30F97A1A1821DC617A90.
É preparação, sem novo corpus ou alias aprovado.

Preferir pares por identidade independente. Sem chave externa estável da
ocorrência filha, comparar também o multiconjunto conjunto de linhas
COMPLETAS31colunas dentro do ID raiz qualificado, preservando tipos,
presença, repetições e correlação entre colunas. Igualdade exata de todas
as tuplas demonstra cobertura do conteúdo/multiplicidade sem atribuir
identidade à ocorrência indistinguível. Diferenças geram tuplas completas
somente-Source/somente-ESL e ambiguidades, nunca pares por ordem/semelhança.
Manter eixo estrito e eixo separado apenas com conversões comprovadas.
Totais, quantidades e multiconjuntos isolados por coluna não substituem
linhas completas. Todo campo extra capturado deve ser listado e explicado.

## Extensão necessária, aprovada apenas para implementação local

O caminho0626/atual fixa17/10,1000linhas/7páginas/8HTTP, segue da Source
obrigatoriamente para SQL e não retém raw HTTP Coletas. Nem trocar o DTO
nem sete dias separados torna esse caminho executável sem alteração.

Proposta T/proposal-weekly-coletas-current-successor0652-v1.json:
1AF65AAF2E1411B864561F60910E5E70291346ED560102D259E90167FBEF0606.
Root verificou17pins e aprovou implementação local: entrada Source-only
Coletas pelo Main genuíno, callback fechado no transporte existente antes
do decode e sink de todas as ocorrências com limites únicos. Preservar
caminhos antigos/Fretes; sem framework novo. Cápsula de fontes/diff/
classpath/fixtures/fluxo real e revisão Campos/Root antes de compilar.

Limites propostos, sem admissão: per100IDs, até17HTTP incluindoINFO,
16DATA incluindoEOF vazio,10.000linhas físicas,2MiB/resposta,64MiBretidos,
210sSource/5minOriginal/gap3s após conclusãoHTTP. per conta IDs distintos,
não fanout. Cap/deadline atingido=STOP incompleto, sem ampliar relógios,
retry/redirect/fallback ou unidade seguinte automática. Referência14HTTP
supõe1155raízes iguais ao XLSX, sem provar volume/cobertura Source.

Recibo T/review-supervisor-weekly-coletas-preparation0652-01.json:
DEF8066F34C44642AD6DEF44F0F5116550EB577AF6F3F18484708949AC45FA49.
Nenhuma compilação Coletas, admissão atual, unidade/CODE/janela emitidos.
Source/SQL/DDL novos=0. Readback semanal de entrada congelada exige escopo
separado; rollback não prova carga permanente.

## Pendências preservadas

Quatro casos antigos Coletas: duas datas subsegundo, status finished e
ação pck_pus_ore_trigger=finish. O XML já contém diferenças subsegundo,
além do arredondamento do leitor. A correção privada0650 continua
inativa/incompatível com SQLv1. Novo filtro ESL mostra finished=Coletada
e done=Finalizada, oito pares preservados; uso na coluna sob revisão
Campos. A operação de coluna não expôs regra finish=Baixar Entrega/Coleta.
Pedido T/conferencia-esl/request-coletas-status-action-ui0652.json:
BB3D161830EA59E89B0E48386E52B44CD4AE0455E85080823967BBA504C83F62.
Nenhuma normalização automática ou confusão com ação derivada.

Fretes congelado antes da nova compilação, contador0. R1–R4/revisão0D8A
e R5/PID0 pendentes para sucessor. Root observou PID0 System Idle Process
por CIM singleton; Campos confirmou a falsa recusa no guard pid>0 de
todas as rows do snapshot completo. Não é bypass/prova Source.
Revisão R5:75F47174DBCF8E563AF84DB3940ADBC24F71553FF12C92E5D0C4453B74A1981D.
Os30testes locais anteriores não qualificam esse sucessor.

Última Source real: MainManifestos0645. Cotações15diferenças, dois vínculos
Manifestos suspensos, demais capturas/UsersP3/UsersEOF/retenção/carga
permanente/estruturas seguem abertos conforme0651. Nove DataExport,
somente Usuários GraphQL. SQL16.0.1000.6, contas/TLS/serviços/listeners/
PM2/V1/produção preservados. DDL18SQL/quatro grupos e Raster7 separados
não foram aprovados ou executados.

Próximo: concluir e revisar cápsula Coletas, decidir qualificação local
focada, preparar admissão LatestMainTyped própria com produto/perfil/
caller/sink/Native/TLS e unidade/CODE/janela novos. Depois capturar semana
com EOF, comparar todo conteúdo e fazer readback separado sob escopo
revisado. Provas antigas não transferem autoridade. Nenhuma entidade
validada antes de encerrar todas as diferenças e campos.
