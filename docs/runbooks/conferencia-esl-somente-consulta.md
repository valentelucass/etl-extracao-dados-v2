# Conferência na ESL: acesso exclusivamente de consulta

Instrução direta do usuário em 25/10/2026. O site aberto é
`https://rodogarcia.eslcloud.com.br/tms`; a área Data Export foi localizada pelo
seletor de módulos em `/analytics/report_panel`.

Permissões: navegar, ler, consultar, filtrar e baixar Excel. Nunca criar, editar,
excluir, aplicar, salvar configuração, importar, carregar arquivos ou enviar dados
ou mensagens à ESL. A permissão de consulta não concede qualquer dessas ações,
mesmo que a conta apresente os respectivos controles. Downloads ficam fora do Git.
Somente o supervisor opera esta sessão; os demais chats trabalham nos arquivos.

A prioridade atual é comprovar a gravação do V2. Depois de a extração e o banco
passarem pelos seus critérios reais, usar a plataforma como conferência
independente. A abertura do site, uma prévia ou um Excel não comprovam gravação.

## Encontrar o relatório correto

- Fretes do V2 usa Data Export6389. Foi localizado **Relação de Fretes Detalhada**
  dentro do módulo Data Export. Confirmar também o template, campos e filtros
  contra INFO/contrato antes de aceitar equivalência. Não substituir por Fretes
  GraphQL, pela pesquisa global do TMS ou por outro relatório chamado Fretes.
- Foram vistos, no mesmo grupo, Inventário, Localizador de Cargas, Relação de
  Coletas Detalhada, Relação de Cotações Detalhada e Relação de Manifestos Detalhada.
  Os arquivos recentes também exibiam Fatura por Cliente e Contas a Pagar.
- Sinistros ainda não teve relatório equivalente localizado nesta navegação.
- Localizador de Cargas Data Export8656 não equivale automaticamente ao Raster. O template4924 é Faturas por Cliente; não usar como identificação de Localizador.
  Para Raster, ainda falta localizar e demonstrar fonte/relatório equivalente;
  não inferir correspondência entre localização, viagem, parada e ocorrência.
- Para Usuários, conferir a semântica da consulta GraphQL IndividualConnection,
  enabled=true e sem filtro temporal. Não presumir que uma tela de usuários de
  acesso representa esse conjunto. A travessia do V2 continua incompleta.

## Igualar o recorte antes de comparar

Reforço direto do usuário em26/10/2026: a conferência deve ser **registro por
registro e campo por campo**, comparando o conteúdo efetivamente gravado no
banco com a ESL ou com o Excel no mesmo recorte. Quantidades iguais ou a
igualdade entre a resposta da API e o banco não substituem essa conferência
independente. Os dois relatórios de exceções fornecidos são a referência
autoritativa das viagens nos intervalos apresentados.

1. Registrar template/entidade e os campos de data e status efetivamente usados,
   período, fuso America/Sao_Paulo, filial, filtros e abrangência de acesso.
2. Comparar os mesmos filtros do pedido do ETL. Na tela de Fretes observada,
   **Data do frete** tinha o controle `search_freights_service_at`; o padrão era
   24/09/2026–25/10/2026, diferente da prova ETL de17/10. Não comparar esses totais.
3. Confirmar paginação/exportação completa. Separar linhas físicas, identificadores
   distintos e repetições legítimas do relatório; nunca deduplicar para ajustar soma.
4. Conferir campos, valores, nulos, datas, casas decimais e totais aplicáveis, além
   de quantidades. A apresentação do Excel pode formatar valores; conservar o
   arquivo original privado e distinguir formatação de divergência real.
5. Registrar diferenças e limites honestamente. Não corrigir a origem pela tela.
   Nenhum aceite global, identidade, exclusão, histórico completo ou paridade é
   inferido de uma amostra, total igual ou lista de arquivos.

## Confrontar os registros e seus campos

1. Demonstrar a chave que relaciona o registro da referência com o registro do
   ETL, incluindo o grão do relatório. Um código de viagem não é automaticamente
   um identificador de frete, manifesto ou solicitação Raster. Preservar as
   repetições legítimas e comparar suas ocorrências; não deduplicar para fechar
   totais nem usar posição da linha como identidade.
2. Para cada chave e ocorrência, confrontar os campos comuns entre o dado
   extraído, o conteúdo lido de volta do banco e a referência independente.
   Conferir identificadores, placa, status, nomes, datas, horários e valores
   disponíveis, respeitando tipos, nulos e precisão. Aplicar somente as
   transformações documentadas pelo contrato, distinguindo apresentação de
   mudança de valor.
3. Emitir diferenças por registro: ausente no ETL/banco, excedente no ETL/banco
   ou campo divergente, com coluna, valor de referência e valor encontrado.
   Esses detalhes de negócio ficam em artefato privado, fora do Git; a
   documentação do projeto recebe somente evidência sanitizada e conclusão.
4. Campo sem correspondência demonstrada ou ausente no Excel/tela fica
   explicitamente **não conferido**. Não interpretar ausência de referência
   como valor correto e não declarar todos os campos validados pelos dez campos
   dos relatórios de viagens.
5. O resultado por entidade deve indicar o recorte, as chaves e colunas
   efetivamente conferidas, registros correspondentes, faltantes, excedentes,
   divergentes e limitações. A entidade só recebe aceite de fidelidade para a
   abrangência realmente verificada. Usuários sem EOF e recortes diferentes
   permanecem pendências próprias.

## Observações reais da primeira navegação

A tela Data Export de Fretes advertiu **54 ligações**, **máximo recomendado30** e
que a exportação pode ser prejudicada. É evidência do aviso, não prova causal dos
timeouts nem autorização para dividir/editar o template. Os últimos arquivos
listados eram de2025 e não foram usados como oráculo dos testes de2026. Nenhum
download ou confronto com banco foi concluído nesta navegação. O acesso foi
somente consulta; não houve alteração de negócio na ESL.

## Identificação dos relatórios do V2

IDs conferidos em DataExportTemplate e nas evidências Stage1; confirmar o ID do
relatório da plataforma antes de comparar. Nome parecido não estabelece identidade.

| Entidade Data Export | Template |
| --- | ---: |
| Coletas | 6908 |
| Fretes | 6389 |
| Manifestos | 6399 |
| Cotações | 6906 |
| Localização de Cargas / Localizador | 8656 |
| Contas a Pagar | 8636 |
| Faturas por Cliente | 4924 |
| Inventário | 10633 |
| Sinistros | 6392 |

Correção de25/10/2026: a referência anterior a Localizador4924 estava errada.
4924 é Faturas por Cliente; Localizador usa8656. Essa correção é documental;
não houve consulta à entidade errada. Raster requer correspondência própria,
e Fretes GraphQL não substitui Fretes Data Export6389.
