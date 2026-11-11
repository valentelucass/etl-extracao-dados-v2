# Cotações para o dashboard: trechos e recaptura — 0771

**Regra confirmada pelo gestor:** cada item calculado do ESL entra separadamente nos indicadores. A mesma regra vale quando a origem, o destino, os CEPs e o número da cotação coincidem, mas os itens correspondem a cargas diferentes. O número continua sendo o informado pela origem e a projeção não inventa uma chave permanente para o item.

`database/production/publication/002_dashboard_cotacoes.sql` publica o template 6906 em `ETL_SISTEMA_V2.pub.vw_cotacoes_powerbi`. Ela oferece os 54 nomes e tipos que o dashboard usa. As 37 informações originais são preservadas em `Metadata`. Tarifas, valores nas escalas do painel, status baseado em CT-e/NFS-e e motivo de reprovação seguem a definição instalada do V1. A data civil de NFS-e é interpretada no horário de São Paulo, como no mapeador V1.

O relatório entra no painel a partir da cobertura do último lote confirmado de cada dia. Recapturar um dia substitui suas linhas: não é necessário usar número, endereço, preço ou ordem de página para identificar o mesmo trecho entre execuções. O histórico continua disponível no recebimento e não entra no cálculo. Se houver mais de uma origem para Cotações, a consulta não mistura os escopos. Antes de instalar ou redirecionar, a validação exige uma origem e cobertura não vazia.

## Validação antes da instalação

Em Windows PowerShell 5.1, rodar `scripts/validation/Test-DashboardCotacoesProjection.ps1 -ReceiptPath <recibo-novo.json>`. O teste lê a definição V1 e executa o corpo da view candidata como consulta. Verifica ordem, tipos, larguras e escalas das 54 colunas, ausência de truncamento de texto, totais e multiplicidade dos registros originais. Reproduz a definição real da visão atual sobre tabelas locais do teste: duas cargas na mesma rota e dois trechos em rotas distintas continuam dois após a substituição do lote com mudança de valor. Um lote vazio não volta ao anterior e duas origens não são somadas. Nenhuma view é criada, nenhum registro produtivo é escrito e não há requisição ESL.

No recorte de 02 a 10/11, havia 1.523 linhas para 1.500 números. Dez grupos repetiam número, rota e CEPs. Em sete grupos, as cargas diferem em volume, valor de nota ou peso. Nos três demais, a lista de trechos calculados no TMS do ESL confirmou os itens separados.

| Cotação | Itens no ESL e na view | Evidência |
| --- | ---: | --- |
| 147372 | 2 | Os dois itens Bariri → Jacareí exibem 2 volumes, 54,2 kg e NF de R$ 1.312,50. |
| 147758 | 2 | Os dois itens Jaú → Rio de Janeiro exibem 50 volumes, 490 kg e NF de R$ 21.500,00. |
| 148085 | 3 | Dois itens Castro → Sumaré exibem 930 volumes, 1.542 kg e NF de R$ 73.786,42. O terceiro exibe 1.020 volumes, 1.816,05 kg e NF de R$ 87.808,94. |

O template 6906 fornece o valor da NF. O número da nota e o identificador do item calculado ficam fora do relatório. A quantidade de itens acima foi conferida nas fichas de origem.

## Instalação e leitura

Execute `scripts/operations/Install-DashboardCotacoes.ps1` no Windows PowerShell 5.1. Sem argumentos, o instalador ensaia em uma transação revertida. Com `-Apply`, instala a view e registra o hash no diário da V2, após conferir o template, o alvo, a leitura pelo usuário do dashboard e a igualdade da contagem e do frete com a origem vigente. A reexecução verifica o hash e a definição existente.

Em 11/11/2026, o ensaio foi revertido e a instalação posterior passou. O dashboard foi testado em processo com JDBC, a conta usada no portal e a view publicada na V2. O exemplo 148597 retornou dois itens, R$ 1.892,57, dois registros no CSV e um item por página. A rota de produção para Cotações segue no V1 até o ensaio de troca e retorno com os demais contratos.
