# 0667 — Primeiro período de Coletas aprovado na comparação completa

Em 03/11/2026, a comparação da extração de Coletas com o Excel da ESL fechou **100% para o intervalo de 19 a 25/10/2026**. Este é o primeiro de três períodos exigidos. A publicação no dashboard continua pendente.

## Resultado conferido

| Verificação | Resultado |
|---|---:|
| Linhas da extração | 1.561 |
| Linhas do Excel | 1.561 |
| Coletas distintas | 1.155 |
| Colunas comparadas juntas em cada linha | 31 |
| Decisões de célula, somados os dois lados | 96.782 |
| Coletas com todas as linhas completas iguais | 1.155 |
| Coletas faltantes ou extras | 0 |
| Diferenças de quantidade de ocorrências por coleta | 0 |
| Linhas ou células pendentes | 0 |
| Diferenças na associação dos 31 valores | 0 |
| Verificações de funcionamento executadas | 131 |

Foram comparados os conjuntos completos de linhas por ID da coleta, incluindo quantas vezes cada linha aparece. Os dois lados continham 1.499 combinações distintas dos 31 valores, totalizando 1.561 ocorrências. Todas foram preservadas; não houve descarte de duplicatas, escolha arbitrária de filho nem pareamento pela posição da linha.

A conferência usa as decisões já qualificadas: decimais no valor numérico representado pelo Excel, datas civis, rótulos declarados pelo fornecedor, nulos/omissões com evidência e datas/horas na precisão efetivamente exibida no workbook. Os valores brutos, frações temporais, offsets, precisão adicional e campos extras da API permanecem preservados. Diferença entre representações originais não foi apagada nem tratada com tolerância arbitrária.

As duas verificações adicionais demonstraram que o comparador detecta (a) valores trocados entre campos apesar de os totais de cada coluna serem iguais e (b) a perda de uma ocorrência repetida. A igualdade foi decidida pelas chaves completas das 31 colunas, sem usar somente hashes.

## Escopo exato da aprovação

- Entidade: Coletas; fonte Data Export; template 6908; filtro `picks.request_date`.
- Source03: `033b024b-d604-4210-9930-67106a1aba2c`, captura concluída às 16:04:37 BRT.
- Produto da captura: SHA-256 `548102BBEAB5FF6CD088A97D93C59CF21FE692B609744E4DC322780BB32F0A52`.
- Projeção Mapped: SHA-256 `3198801F863E21697A828A3F27508ECA7E1A607E55AA8DC5AD825C0BF58E2FFE`.
- Excel ESL1760193: gerado às 16:05, sem cache, SHA-256 `EDD65AF4F47B87EF0BEBAE14A10E32B4E89DC90E70DB0FE9651DE2076EA36512`.
- Resumo: `C:/Users/suporte/Documents/Codex/2026-10-27/etl-v2-supervisor-finalizacao/outputs/coletas-fresh03-joint31-mapped-0665-actual01/pair-summary.json`, campo `joint31Association`.
- Aceite Root: `C:/Users/suporte/Documents/Codex/2026-10-27/etl-v2-supervisor-finalizacao/outputs/root-acceptance-coletas-week-complete0667-01.json`, 4.279 bytes, SHA-256 `B323905B9D0257AF402890B532979041C49BEE85A2EA6E06BB213AF8A6E42D96`.

A prova conjunta executou sobre o Mapped dessa captura, conforme o escopo exato da política independente de timestamps. O RAW já possui comparação129 separada aprovada e seus artefatos não foram reclassificados como se tivessem executado a prova conjunta131. A aprovação não se estende automaticamente a outras datas, workbooks, versões do produto, catálogo SQL ou consumo do dashboard.

## Execução e preservação

Original terminou em 123.944 ms, retorno0, pico346.873.856 bytes, dentro do limite de1GiB/300s. Todas as etapas e processos próprios foram encerrados. Zero requisições HTTP/Source e zero SQL nesta comparação offline. Estado canônico856FD820/703000B, ACLB4E214EB e slot0 restaurados. Nenhuma alteração em V1,SQL2022,PM2 ou serviços de produção.

## Andamento e próximos passos

| Intervalo de solicitação | Situação |
|---|---|
| 19–25/10/2026 | Aprovado: comparação completa das 31 colunas por linha e coleta |
| 12–18/10/2026 | Preparação da nova extração e do Excel; ainda não executado |
| 26/10–01/11/2026 | Preparação da nova extração e do Excel; ainda não executado |
| Consumo pelo dashboard | Pendente de publicação, contexto, semântica e conferência real |

O usuário determinou continuar até concluir Coletas e somente então avançar à próxima entidade, documentando cada resultado e correção. A instrução atual cobre a continuidade das próximas semanas; a autorização antiga de uma tentativa foi consumida e não será reutilizada. Cada próxima execução terá escopo e recibos novos, respeitando os limites de requisições, intervalo após resposta, ausência de retry e parada no primeiro429/não2xx.

O Chrome está listado pela ferramenta, mas a leitura e a abertura de abas falharam com erro do controle (`timeoutMs`). Foi solicitada reconexão enquanto o preparo local continua. Isso não comprova que a sessão da ESL expirou. Não foram contornadas proteções do navegador nem solicitado novo login sem evidência.

Para o dashboard, a auditoria anterior dos18campos diretos passou tipos/limites, com nulos preservados. Os três campos de contexto, regras de publicação e catálogo canônico continuam abertos no mapa de aceites do consumidor. Nenhum sucesso desses testes de estrutura foi apresentado como aprovação do consumo real.
