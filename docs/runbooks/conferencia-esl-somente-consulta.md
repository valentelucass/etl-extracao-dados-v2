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
- Localizador de Cargas Data Export4924 não equivale automaticamente ao Raster.
  Para Raster, ainda falta localizar e demonstrar fonte/relatório equivalente;
  não inferir correspondência entre localização, viagem, parada e ocorrência.
- Para Usuários, conferir a semântica da consulta GraphQL IndividualConnection,
  enabled=true e sem filtro temporal. Não presumir que uma tela de usuários de
  acesso representa esse conjunto. A travessia do V2 continua incompleta.

## Igualar o recorte antes de comparar

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

## Observações reais da primeira navegação

A tela Data Export de Fretes advertiu **54 ligações**, **máximo recomendado30** e
que a exportação pode ser prejudicada. É evidência do aviso, não prova causal dos
timeouts nem autorização para dividir/editar o template. Os últimos arquivos
listados eram de2025 e não foram usados como oráculo dos testes de2026. Nenhum
download ou confronto com banco foi concluído nesta navegação. O acesso foi
somente consulta; não houve alteração de negócio na ESL.
