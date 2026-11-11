# Publicações de calendário, filtros e Sinistros para o dashboard

As seis views instaladas em 11/11/2026 leem fontes dentro de `ETL_SISTEMA_V2`. Cotações já estava publicada. Os 26 nomes usados pelo dashboard de produção continuam apontando para o V1.

| Publicação | Colunas | Linhas lidas com a conta do dashboard | Origem e regra |
| --- | ---: | ---: | --- |
| `pub.dim_calendario` | 19 | 4.780 | Função de calendário existente da V2, de 01/12/2019 a 31/12/2032. Preserva os dias úteis, feriados, pontos facultativos e a data de referência de faturamento do V1. |
| `pub.vw_dim_filiais` | 2 | 15 | Filiais observadas nos lotes vigentes de Fretes, Manifestos, Contas a Pagar e Faturas por Cliente. |
| `pub.vw_dim_motoristas` | 2 | 232 | Par nome normalizado e filial em Manifestos. A regra do V1 exclui o nome genérico que contém “MOTORISTA”. |
| `pub.vw_dim_veiculos` | 4 | 228 | Par placa e filial em Manifestos. Tipo e proprietário seguem a agregação do V1. |
| `pub.vw_dim_planocontas` | 3 | 59 | Descrições contábeis e classificação derivadas das alocações no último lote de Contas a Pagar. |
| `pub.vw_sinistros_powerbi` | 34 | 9 | Template 6392. Preserva os campos originais, a chave de sinistro/nota/minuta e os valores com as escalas usadas no V1. |

O calendário passou na comparação integral das 18 colunas de negócio, incluindo os nomes dos feriados e o primeiro dia da série. `atualizado_em` registra a instalação da regra no V2. As quatro listas passaram na comparação de conteúdo com uma consulta independente sobre 27.700 registros dos lotes vigentes. São listas de filtros derivadas do histórico já recebido. O cadastro operacional de filiais e as referências de vigência têm contratos próprios.

Em Sinistros, os 9 registros têm 9 chaves distintas. O valor a pagar ao cliente somou R$ 2.459,82 e o resultado final somou R$ -2.459,82. A comparação confirmou as multiplicidades e os campos originais, além de 100 chaves históricas calculadas pela mesma regra no V1. As nove chaves do recorte atual só apareceram na V2. Os valores atuais foram conferidos com o relatório do ESL já armazenado na V2.

## Ensaio e aplicação

Em Windows PowerShell 5.1, a partir da raiz do ETL, rode cada grupo com um recibo novo.

```powershell
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group References -ReceiptPath C:\caminho\referencias-ensaio.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Sinistros -ReceiptPath C:\caminho\sinistros-ensaio.json
```

O ensaio cria as views em uma transação, compara as colunas com o V1, executa a validação com a conta do dashboard e reverte a transação. A instalação usa o mesmo comando com `-Apply` e outro recibo. Uma reexecução confere o hash, a definição instalada e as leituras. O instalador usa o V1 como referência de teste durante a migração. As views publicadas consultam somente objetos da V2.

O ensaio JDBC independente iniciou a sessão em `DASHBOARDS`, com a conta do portal. Conferiu a forma das seis consultas pelo caminho atual do V1, leu os destinos publicados na V2 e verificou a preservação dos 26 synonyms. Os testes completos das telas dependem dos 19 destinos analíticos restantes. O corte e a carga de 90 dias são operações separadas.
