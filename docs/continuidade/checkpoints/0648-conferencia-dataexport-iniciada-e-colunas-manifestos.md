# 0648 — Conferência Data Export iniciada; inventário de Manifestos

27/10/2026. O usuário autenticou a aba original da ESL e reiterou a origem:
as nove entidades usam Data Export; somente Usuários usa GraphQL. Pediu
conferir entidade por entidade, o intervalo extraído, os registros e os valores
de cada coluna. A comparação externa permanece em andamento, sem aceite.

## Navegação e limite observado

A sessão autenticada chegou ao módulo Data Export, painel de relatórios,
pasta Relatórios, opção Relação de Manifestos Detalhada. Uma consulta anterior
na Relação operacional do TMS não pertence a essa evidência e não será usada
como prova de paridade Data Export.

O painel Data Export avisou: prévia dos 100 primeiros registros; informação
completa somente no arquivo de exportação. O ID6399 foi depois confirmado na
lista de templates e no endereço da definição. Não inferir equivalência de
templates apenas pelo título.

O calendário confirmou Data=17/10/2026–17/10/2026, demais filtros visíveis vazios.
Após acionar a consulta, o controle da aba deixou de responder. Leitura de DOM,
acessibilidade e captura de tela excederam seus prazos. A aba segue listada e
nenhum diálogo JavaScript foi encontrado. O resultado da consulta é UNKNOWN:
não afirmar erro interno da ESL, contagem remota ou conclusão do pedido.
Não houve repetição da consulta, exportação, logout ou fechamento da aba.
O usuário confirmou que a aba travou. Na retomada, a aba antiga já não estava
disponível; havia outra aba ESL autenticada. O reload anunciado não ocorreu:
o controle recusou por vínculo antigo antes da ação. A aba disponível foi
retomada, sem logout ou fechamento da aba do usuário.

## Identidades confirmadas no cadastro Data Export

Na lista de templates,26 itens visíveis, os nomes, raízes e links de definição
confirmaram os nove códigos. Nenhum cadastro foi salvo ou alterado:

| Código | Nome na ESL | Raiz na ESL | Grupo |
| --- | --- | --- | --- |
| 6908 | Relação de Coletas Detalhada | Coleta | Relatórios |
| 6389 | Relação de Fretes Detalhada | Frete | Relatórios |
| 6399 | Relação de Manifestos Detalhada | Manifesto | Relatórios |
| 6906 | Relação de Cotações Detalhada | Cotação | Relatórios |
| 8656 | Localizador de Cargas | Frete | Relatórios |
| 8636 | Contas a Pagar | Fatura a Pagar | Controladoria |
| 4924 | Fatura por Cliente | Frete | Cobrança |
| 10633 | Inventário | Ordem de Conferência | Relatórios |
| 6392 | Sinistros | Sinistro | Qualidade |

Isso esclarece o nome upstream10633, antes ambíguo: Inventário/Ordem de
Conferência. Não o equiparar a Fatura por Cliente, cuja raiz é Frete.

A leitura da definição6399 ocorreu em aba auxiliar aberta pelo link da lista.
Foram observadas91 colunas e18 filtros. A posição60 aparece em duas colunas
(Retiradas e Despachos); títulos Coletas e Entregas também se repetem. Portanto,
não montar correspondência somente por título ou número exibido. Um painel
auxiliar Operação foi aberto e fechado sem salvar; não houve alteração.
Ao reduzir a paginação da prévia para10, o controle voltou a exceder prazos.
A tentativa de fechar somente a aba auxiliar também não teve confirmação;
não afirmar fechamento. A aba do usuário não foi fechada por esta unidade.
Exportação completa e comparação externa dos valores continuam pendentes.

## Inventário local efetivamente executado

Leitura nova e limitada dos arquivos existentes, sem API ou SQL:

- Prova Main0645/36DF e source.jsonl/B5F2 conferidos por SHA256 antes/depois.
- Pedido original A548 confirma MANIFESTOS e dia comercial17/10; início
  2026-10-17T03:00:00Z e fim exclusivo2026-10-18T03:00:00Z.
- Filtro de negócio é manifests.service_date. Não usar created_at como
  substituto da data de serviço nem afirmar semântica temporal do fornecedor.
- Captura315 linhas físicas,17 raízes,91 campos do metadata local.
- Todos os91 campos estão presentes em todas as315 linhas: zero células
  ausentes,2.563 valores null e253 strings vazias. Preservar essas diferenças.
- Resultado contém por coluna contagem de presença, null, string vazia e tipos
  observados; zero valores de negócio publicados e zero células ESL comparadas.
- Nenhum campo inesperado. Isso é inventário da captura, não comparação de
  valores com o site, nem nova qualificação da tipagem dos91 campos.

B = C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01.

Preparo final B/mft-column-inventory0648-v2/manifest.json, SHA256
4DC4F19CDD30775983D63C1049A68DB649C76B6FCD62441CDE28B17AC9F3F874.
Revisão Root somente de arquivos, explicitamente não independente.

Prova B/mft-column-inventory0648-02-root-control/qualification.json, SHA256
AC0B4F0560C4DB3B02D54C85C3F0FB6F2F6A10F7897A4D5F301991992E6E6BBC.
Resultado no mesmo diretório, result.json, SHA256
60C5B55660C0D7170B6D4E102728108A77E7B50BEF3599D957635C4FBDDB99D3.
Original0/1.578ms/59.527.168bytes, limite1GiB, slot adquirido e fechado,
bytes históricos do ledger restaurados exatamente. SourceHTTP0/SQL0/DDL0.

Predecessor v1 e controle01 preservados: falhou antes da leitura dos dados
porque o nome auxiliar H colidiu com o alias PowerShell Get-History. O sucessor
usa nomes explícitos. A falha não recebeu PASS; fechamento e restauração do
ledger foram verificados antes da nova unidade offline.

## Sequência da comparação externa

Recuperar a leitura estável da aba sem repetir uma operação de resultado desconhecido.
Confirmar ID do template e filtros, obter exportação completa no mesmo recorte
e vincular colunas da ESL aos nomes da origem por definição, nunca por posição
ou coincidência de contagens. Comparar multiconjuntos de linhas preservando
duplicatas, valores null/vazios/zero, identificadores, precisão e datas. Registrar
diferenças por campo e separar cobertura do recorte de histórico integral.

Seguem os recortes documentados em0646: Coletas6908/17out; Fretes6389/17out;
Manifestos6399/17out; Cotações6906/22out; Localização8656/17out; CAP8636/17out;
Faturas4924/17out; contrato local INVENTARIO10633/17out; Sinistros6392/01–23out.
O nome upstream10633 foi confirmado nesta unidade. Usuários GraphQL tem apenas
prefixo antigo sem EOF e cursor retomável; Raster é comparação separada.

Última Source real continua Main0645. Comparação interna0645 de6.300 células
sem diferença não é paridade externa nem cobertura integral de campos SQL.
Coletas/Manifestos/Cotações continuam rollback-only, sem carga permanente.
UsersEOF, bancos restantes,18SQL/quatrogrupos e Raster7DDL continuam abertos.
Não aplicar DDL ou ampliar exceções por este pedido de conferência.

SQL16, contas, identidades, permissões, serviços, TLS, PM2/V1 e produção
preservados. Código canônico inalterado desde540d869; esta unidade altera
somente documentação e exige scan dos textos, commit e push.
