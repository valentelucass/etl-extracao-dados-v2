# Critério de comparação integral com Excel — 0762

O aceite de cada relatório exige o mesmo template, período civil inclusivo, filtros complementares vazios, cabeçalhos exatos e todas as linhas e colunas. Não é uma amostra nem uma comparação somente de contagens. As repetições são comparadas como multiconjunto de tuplas completas; a ordenação de apresentação pode variar. Chave de negócio e deduplicação não foram inferidas.

Os Excel oficiais são gerados no painel ESL e conservados no armazenamento privado, com ID do arquivo, período, SHA256, data de criação exibida, data do download e quantidade processada no painel. A criação exibida é a criação do trabalho do arquivo, que pode permanecer Agendado antes de Gerado; não representa um instante de snapshot do banco da origem. O XLSX precisa passar CRC do ZIP, uma planilha, cabeçalhos e número de colunas exatos, nenhuma célula de fórmula e número de linhas idêntico ao processado. Nenhum valor de negócio é publicado no relatório técnico.

Antes de comparar o Excel, outro processo confere HTTP literal → JSONL → banco, com conteúdo integral, ordinal, SHA, escopo, data, quantidade e multiplicidades. Essa prova é separada da equivalência ao Excel. Um resultado SQL exato não transforma um Excel divergente em aprovado.

## Apresentações qualificadas

- Datas civis do JSON são comparadas à data à meia-noite gravada no Excel. Timestamps exibidos pelo Excel sem fuso são comparados à apresentação local observada da origem. O Java conserva o Instant e o valor original, com offset e fração; o comparador não altera a persistência.
- Somente os campos já qualificados usam apresentação em segundos do Excel. Na amostra integral do primeiro ciclo, os três atributos opcionais de Ocorrências tiveram376/448/163 pares não nulos não ambíguos: todos iguais em relógio local e em segundos, sem fração no Excel. Frações existentes no JSON continuam preservadas no recebimento.
- Valores monetários e números são comparados por valor decimal exato, sem tolerância arbitrária. A representação lexical original permanece no HTTP privado. Não se remove diferença numérica por arredondamento de conveniência.
- `charged_value_details` é objeto JSON na API e texto JSON na coluna Composição XML do Excel. A comparação interpreta o JSON desse campo e compara todas as chaves e valores decimais, preservando estrutura. Ordem de chaves e representações numericamente equivalentes como100 e100.0 não mudam o valor. Chaves duplicadas e constantes não finitas no JSON do Excel são recusadas; textos comuns não recebem essa conversão.
- Booleanos têm a apresentação Sim/Não provada. A lista de notas usa o separador e a ordem observados, sem ordenar ou eliminar notas repetidas. Não há trim ou normalização de texto generalizada.
- Códigos com rótulos no Excel usam apenas mapas observados em pares não ambíguos dos demais atributos. `ice_ttt_solution_type` teve163 pares, cinco códigos e zero conflitos na primeira janela integral. O mapa com rótulos fica privado. Isso não fecha um enum no modelo Java; código desconhecido não é convertido por palpite.

## Diagnóstico sem falso aceite

A primeira carga de02–09/11/2026 armazenou191 linhas de Consolidação e35.129 de Ocorrências. Os Excel solicitados depois tiveram194 e35.314. Depois de aplicar somente apresentações qualificadas,178/191 e34.516/35.129 tuplas completas coincidiram. Foram encontrados13 pares não ambíguos alterados em Consolidação, com três linhas adicionais no Excel; em Ocorrências611 pares alterados e dois registros de origem/187 do Excel ainda sem pareamento não ambíguo. Os campos alterados e as contagens estão nos recibos do chat. O diagnóstico de pares não constitui prova de chave primária nem permite apagar divergências.

Os dois relatórios incluem atributos derivados do estado atual de entrega e tratativa. Consultas paginadas e exportações assíncronas em momentos diferentes não comprovam um snapshot transacional comum. A segunda campanha conserva o Excel solicitado após concluir a extração de cada entidade nova e compara tudo depois de terminar o ciclo completo. O resultado final deve registrar as diferenças remanescentes, sem descartar o dia atual, remover repetições, trocar a janela ou declarar100% por inferência.

Critérios aprovados e pendentes estão em STATES e no checkpoint0762. As contagens acima são evidência histórica do ciclo1, não o resultado final do ciclo2.


## Resultado final e revisão das traduções

divergente; equivalência de 100% pendente.

- Template 11067: 39 cabeçalhos exatos; origem 209 / Excel 212; ocorrências completas iguais 208; somente origem 1 / somente Excel 4; equivalência integral=False; arquivo ESL 1778129.
- Template 12072: 20 cabeçalhos exatos; origem 36.074 / Excel 36.166; ocorrências completas iguais 35.953; somente origem 121 / somente Excel 213; equivalência integral=False; arquivo ESL 1778123.

As 448 diferenças preliminares de delivery_status eram tradução não qualificada, não prova de alteração da origem. Mapa comprovado por 448 pares com outras 19 colunas iguais, 34.516 controles e zero conflitos; congelado antes dos confrontos finais. O campo ctd_ant_document teve dois textos, ambos iguais no Excel, sem conversão numérica. As diferenças de quantidade e tuplas completas permanecem explícitas. Ver checkpoint final e recibos.
