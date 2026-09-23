# Pesquisa documental dirigida — 12/09/2026

A pesquisa distingue documentação, observação legada e contrato sintético. Não
foram usados tokens, endpoints de dados ou consultas a pessoas reais.

| Pergunta | Fonte e revisão | Resultado e uso | Limite |
| --- | --- | --- | --- |
| Quais envelopes, aliases, tipos e filtros Raster existem no corpus? | Catálogo raster-contrato-local de 08/09/2026, 51 declarações; Java/SQL V1 vinculados por hash | Modelos Trip/Stop/Route separados, presença/wire/raw, parser estrito, janela civil explícita e binding lateral | DTO não prova wire type nem identidade; revisão sintética define frescor |
| Há schema público Raster versionado e vinculado ao fornecedor? | Busca no domínio rastergr.com.br e página oficial em 12/09/2026 | Site institucional e produtos localizados; nenhum schema oficial do método localizado nesta rodada. Mirror encontrado pela busca não foi adotado como contrato | Sem endpoint documental conhecido, não se inventou /info nem se enviou credencial ao mirror |
| A documentação ESL online está renderizável nesta ferramenta? | [Postman oficial](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5) e [GraphQL oficial](https://demonstracao.eslcloud.com.br/graphql_docs), consulta em 12/09/2026 | Postman retornou HTML sem linhas renderizadas; GraphQL retornou erro da ferramenta. Usado corpus arquivado de 09/09/2026 | Ausência de renderização não foi interpretada como contrato vazio |
| Quais documentos orientam Filiais, Clientes, Veículos, Motoristas e Plano de Contas? | Índices ESL endpoints.csv, graphql-operacoes.csv e cinco anexos arquivados; fonte.json registra hashes | Operações de pessoas físicas/jurídicas, tipos de veículos e campos financeiros orientam atributos. Mutações documentadas não foram executadas. Bindings/registro sintético separam cadastro do contexto do Manifesto | Nome de operação/campo não prova crosswalk, lifecycle, capacidade por papel ou ID imutável |
| Pode-se ratificar Frota por placa/nome do Manifesto? | ADR0025 e catálogo frota-manifestos-v2-035c, V02 | Não. Registro e binding sintéticos por papel/vigência são contrato exclusivo do laboratório | Gates históricos da identidade real permanecem |
| De onde vêm as colunas de consumo e regras de fatos? | 19 views e três procedures do V1 indicadas pelo inventário de portabilidade | 673 expressões de saída congeladas por ordem e hash em contratos-colunas-inicial.json; MAT/RAS/PUB do STATES dirigem implementação | Nenhum acesso ao projeto de dashboards, wrapper cross-database ou aceite consumidor |

O host de integração Raster foi localizado no ConfigRaster.java do V1. Sua
existência não autorizou getEventoFimViagem remoto, nem revelou endpoint de
schema. O transporte exercitado é exclusivamente HTTP loopback com credenciais
fictícias e corpo limitado; nenhum default aponta para esse host legado.

Lacunas externas que permanecem específicas: estabilidade/escopo de CodSolicitacao;
identidade da parada versus Ordem; semântica temporal e completude reais;
IDs/vigência/rekey de Frota; atribuição nominal de filial; referência financeira,
política fiscal e manifesto consumidor aprovados. Cada mecanismo local deve
ter caso sintético positivo e contraprova da dependência ausente.

Pesquisa dirigida em12/09/2026 para o relógio de Manifestos: a documentação de
[datetimeoffset](https://learn.microsoft.com/en-us/sql/t-sql/data-types/datetimeoffset-transact-sql?view=sql-server-ver17)
descreve precisão máxima de100ns; ela não conserva sozinha os9 dígitos aceitos
pelo parser Java. A página de
[DATEDIFF_BIG](https://learn.microsoft.com/en-us/sql/t-sql/functions/datediff-big-transact-sql?view=sql-server-ver17),
revisada em18/11/2025 e consultada nesta rodada, descreve contagem de fronteiras
e consideração do offset. Aplicação local: segundos UTC em BIGINT e fração de
9 dígitos em INT, comparados como par, com texto/offset originais preservados.
V068 e a contraprova de1ns exercitam captura, preparação e stale; isso não
altera garantias de relógio/completude do fornecedor.
