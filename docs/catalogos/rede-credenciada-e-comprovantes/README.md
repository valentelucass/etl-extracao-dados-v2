# Consolidação Detalhada (11067) e Comprovantes de Entrega (11361)

## Uso no ETL V2

- **11067** alimentará uma tela de gestão da rede credenciada. A extração existente segue em `modulos/consolidacoes`. Filial expedidora, transportadora, CNPJ, rota, valores, peso, volumes e prazos já fazem parte dos 39 campos. O [catálogo detalhado do 11067](../novas-entidades-0760/CONSOLIDACAO-11067.md) contém a semântica dos campos. A consulta corrente expõe também `consolidations.updated_at` como décimo filtro. Em 17/11, a cobertura atual no SQL tinha 80 datas entre 26/08 e 13/11/2026, com 5.777 linhas.
- **11361** fornece o número de arquivamento associado ao frete. O código fica em `modulos/comprovantesentrega`, com mapeamento em `aplicacao` e observação em `domain`. O [contrato de origem](../../../src/main/resources/contracts/dataexport/comprovantes-11361-v1.json) registra os 23 nomes e títulos do ESL. A extração salva o registro original e seus valores mapeados, com nulos e listas de NF preservados. O 11361 é a 13ª fonte da carga histórica e da rotina de atualização do ETL V2.

## Retomada da carga

O arquivo de progresso mantém o início e o fim originais da carga de 90 dias. As fontes que já têm dias confirmados continuam do ponto salvo. O 11361 começa no último dia da mesma janela, 13/11/2026, e retrocede até 16/08/2026. A carga histórica passa de 1.080 para 1.170 combinações de fonte e data.

O cadastro do 11361 no SQL é feito por [012_delivery_archive_receiving.sql](../../../database/production/receiving/012_delivery_archive_receiving.sql). A data de referência é a do filtro usado na requisição. Uma nova leitura completa da mesma data passa a ser a versão corrente daquele dia. A rotina de 60 minutos busca os novos dias e revisita os dias anteriores em lotes para capturar mudanças como a finalização de um arquivamento já recebido.

Para iniciar pelo PowerShell do servidor, execute `& .\CARGA_90_DIAS.bat` na pasta do ETL V2. Em outra janela, `& .\CARGA_90_DIAS.bat --acompanhar` mostra o progresso das 13 fontes.

Na validação de 17/11/2026, o SQL devolveu integralmente os 822 registros do recorte de 06/11 e manteve 822 registros correntes após duas gravações. O arquivo de progresso preservou os 575 dias por fonte já confirmados, de um total de 1.170.

## Consultas verificadas

| Relatório | Filtro obrigatório do recorte | Filtro de atualização |
| --- | --- | --- |
| 11067 | `search[consolidations][issue_date]` | `search[consolidations][updated_at]` por data |
| 11361 | `search[freight_archivings][created_at]` | Ausente nos metadados |

O 11361 é ativo no grupo **Relatórios**, com raiz **Arquivamento de Frete**. Seus cinco filtros são Data criação, Filial, Usuário, Status e Nº Arquivamento. A resposta de dados não projeta a data de criação do arquivamento. A Data de Anexo do Comprovante e a Data da Última Atualização da localização pertencem ao frete e não substituem esse filtro. O período de um dia fica registrado no recibo da captura.

Em ambos, o cURL usou `GET /api/analytics/reports/{id}/info` e `GET /api/analytics/reports/{id}/data`, autenticação existente enviada por stdin e data inclusiva. Com `per=100`, a segunda página literal `[]` encerrou cada recorte. Para o 11361 em 06/11, uma repetição com `per=5` devolveu os mesmos 822 registros físicos, com multiplicidade. As três páginas úteis trouxeram 339, 225 e 258 linhas, cinco arquivamentos em cada. A quarta página veio vazia. O limite é aplicado ao número de arquivamentos, e cada arquivamento expande seus fretes.

| Relatório | Data | Campos | Linhas cURL | Arquivo oficial ESL | Linhas equivalentes |
| --- | --- | ---: | ---: | --- | ---: |
| 11067 | 06/11/2026 | 39 | 50 | 1800720 | 50 |
| 11067 | 06/11/2026, segunda leitura | 39 | 53 | 1800795 | 53 |
| 11361 | 06/11/2026 | 23 | 822 | 1800738 | 822 |
| 11361 | 12/11/2026 | 23 | 623 | — | — |
| 11361 | 13/11/2026 | 23 | 1.332 | — | — |

Os arquivos de 06/11 foram gerados no Painel do ESL com o mesmo filtro aplicado à prévia. Na segunda leitura do 11067 a origem já incluía três relações a mais. Java, cURL e o novo Excel concordaram nas 53 linhas. O Java extraiu 822 linhas do 11361 com os mesmos valores do cURL e do Excel. As duas execuções Java chegaram à página vazia, com zero linhas rejeitadas. Os cabeçalhos coincidiram em ordem. A comparação usou todas as colunas e a multiplicidade das tuplas. As equivalências de apresentação incluem rótulos de status e booleanos, números decimais, listas de NF e datas de origem até o segundo, pois o Excel do ESL omite frações de segundo. O JSON retém as frações recebidas. A evidência cobre os recortes acima.

O Java também confirmou as 623 linhas de 12/11 e 1.332 de 13/11, sem rejeições. A captura de 13/11 manteve todos os vínculos vistos no cURL inicial, mas 53 linhas passaram de `pending` para `finished` entre as leituras. As 1.332 linhas gravadas pelo Java corresponderam ao conteúdo HTTP da própria execução. Essa mudança de status em uma data antiga confirma a necessidade de revisitar datas por criação se a fonte entrar na rotina.

## Campos do 11361

| Campo da API | Coluna do ESL | Tipo no cURL |
| --- | --- | --- |
| `fag_crn_psn_nickname` | Responsável pelo Arquivamento | texto |
| `sequence_code` | Nº Arquivamento | inteiro |
| `name` | Nome do arquivo | texto |
| `status` | Status | texto |
| `fag_fhs_fit_crn_psn_nickname` | Filial Emissora | texto |
| `fag_fhs_fit_pyr_nickname` | Cliente | texto |
| `fag_fhs_fit_corporation_sequence_number` | Nº Minuta | inteiro |
| `fag_fhs_fit_fhe_cte_number` | Nº CT-e | inteiro ou nulo |
| `fag_fhs_fit_invoices_mapping` | NF | lista de textos |
| `fag_fhs_fte_has_delivery_receipt` | Freight cache/Comprovante de entrega | booleano |
| `fag_fhs_fit_d_t_created_at` | Data de Anexo do Comprovante | data e hora ou nulo |
| `fag_fhs_fit_dyn_drt_nickname` | Responsável pela Região de Destino | texto ou nulo |
| `fag_fhs_fit_fln_cln_nickname` | Localização Atual | texto ou nulo |
| `fag_fhs_fit_fln_updated_at` | Data da Última Atualização | data e hora ou nulo |
| `fag_fhs_fit_status` | Frete/Status | texto |
| `fag_fhs_fit_emission_type` | Frete/Tipo de emissão | texto |
| `fag_fhs_fit_service_type` | Frete/Tipo do serviço | texto |
| `fag_fhs_fit_operation_type` | Frete/Tipo Op. Frete | inteiro |
| `fag_fhs_fit_type` | Frete/Tipo | texto |
| `fag_fhs_fit_fte_flt_ron_sequence_code` | Consolidação Inicial/Nº Consolidação | inteiro ou nulo |
| `fag_fhs_fit_fte_flt_ron_document_number` | Consolidação Inicial/Nº CTe 3º | texto ou nulo |
| `fag_fhs_fit_fte_llt_ron_sequence_code` | Consolidação Final/Nº Consolidação | inteiro ou nulo |
| `fag_fhs_fit_fte_llt_ron_document_number` | Consolidação Final/Nº CTe 3º | texto ou nulo |

## Associação com Fretes

O número de arquivamento vem de `sequence_code`. As três capturas do 11361 somaram 2.777 linhas em 49 arquivamentos. A maior lista de NF tinha 86 elementos. Havia 80 fretes sem CT-e. A chave candidata foi cruzada com a cobertura atual de Fretes no SQL usando Filial Emissora e Nº Minuta (`fit_crn_psn_nickname` + `corporation_sequence_number` no 6389). Houve 2.753 correspondências com um único ID interno de frete. As 24 referências restantes não tinham a minuta no conjunto de Fretes consultado. Nos 2.674 vínculos em que ambos os lados tinham CT-e, todos os números coincidiram.

Só se deve atribuir o arquivamento ao ID do frete quando o cruzamento devolver uma correspondência única. Ausência ou ambiguidade fica pendente. Se futuras capturas mostrarem vários arquivamentos para o mesmo frete, todos devem ser preservados até existir uma regra de seleção confirmada. A periodicidade do 11361 também precisa contemplar alterações feitas em arquivamentos de datas antigas, já que o relatório só oferece filtro por criação.

## Comando de extração de uma fonte

Após empacotar o projeto, execute a partir da raiz do repositório no servidor. O intervalo inclui as duas datas, com até oito dias civis. A pasta da captura e o recibo devem ser novos.

```powershell
$relatorio = '11361'
$base = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos/etl-extracao-dados-v2'
$saida = Join-Path $base ('captura-'+$relatorio+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
. ./scripts/probes/DataExportProbeGate.ps1
$trava = Enter-DataExportProbeGate
if (-not $trava) { throw 'Outra extração do Data Export está em andamento.' }
try {
    $argumentos = @('.env', $saida, ($saida+'.json'), '2026-11-06', '2026-11-06', $relatorio)
    & java -cp 'target/etl-dataexport-v2.jar;target/lib/*' br.com.esl.etl.v2.bootstrap.DataExportSnapshotMain @argumentos
    if ($LASTEXITCODE -ne 0) { throw 'Extração incompleta. Consulte o recibo.' }
} finally {
    Exit-DataExportProbeGate $trava
}
```

Use `11067` para selecionar Consolidação. O modo de cinco argumentos continua disponível para o conjunto anterior.
