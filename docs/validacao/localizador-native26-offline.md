## Resultado confirmado em 08/11/2026 — 3/3 períodos completos

O Localizador de Cargas, relatório ESL 8656, foi comparado integralmente com o ETL V2 nos três períodos abaixo. As 26 colunas, os conjuntos de tuplas completas com todas as repetições e os quatro totais coincidiram nas implementações Java e Python independentes. São ocorrências físicas de linhas, sem deduplicação nem uso da minuta como identidade global.

| Período de 2026 | Linhas V2 | Linhas ESL | Resultado | Arquivo oficial usado |
|---|---:|---:|---|---|
| 12–18/10 | 3.476 | 3.476 | Confere | localizador-de-cargas_2026_11_07_21_43.xlsx —1773250 |
| 19–25/10 | 4.189 | 4.189 | Confere | localizador-de-cargas_2026_11_07_21_46.xlsx —1773252 |
| 26/10–01/11 | 4.598 | 4.598 | Confere | localizador-de-cargas_2026_11_08_06_29.xlsx —1773635 |
| Total | 12.263 | 12.263 | 3/3 | 21 dias civis completos |

O filtro é somente a data do frete (`service_at`); os sete filtros auxiliares, inclusive atualização, ficaram explicitamente vazios. Volumes foi comparado como inteiro exato. Peso taxado, Valor NF e Total a receber foram comparados com codificação binária direta e arredondamento nearest-even, sem tolerância numérica nem arredondamento financeiro para produzir igualdade.

As notas fiscais mantêm a ordem e as repetições do array original, com separador observado `, `. Os dicionários de Tipo, Status e Tipo de serviço foram provados por agrupamentos das outras 23 colunas completas e suas multiplicidades, sem associação por frequência. O código `manifested` corresponde à célula fisicamente ausente no Excel, comprovado em 8 registros da segunda semana e 15 da terceira. Os casos Source NULL foram avaliados separadamente. As políticas de nulos, carriers XLSX e precisão temporal também ficaram explícitas no catálogo observado; os bytes RAW permanecem intactos.

O Excel anterior da terceira semana conservava uma diferença em data de entrega e status. Ele foi preservado como evidência histórica. O novo arquivo foi gerado pela interface ESL, sem mudar filtros ou conteúdo da origem, e conferiu integralmente com os mesmos RAWs do V2. Os outros dois Excel e as 21 extrações diárias completas foram reutilizados. Tentativas incompletas continuaram excluídas, com seus resultados originais preservados.

### Código e testes executados

A interpretação estrita dos oito filtros INFO qualificados, a nulabilidade dos oito campos realmente observados e o suporte explícito a código de enum exibido como célula ausente foram incorporados ao código V2. As correções de desempenho preservam validação, persistência, cadeia de evidências e limites. O runtime final Full150 passou 1044 métodos em 106 classes, após formatação e compilação próprias; todos os 1036 métodos anteriores permaneceram. A unidade contém 361 arquivos Java, 43 controles e 404 inputs.

Também passaram as verificações Python de apresentação 79, observação de enums 54, perfil Native 40, gate final 75 e amostras 70. O mapa do código foi atualizado por AST local, com 404 inputs/402 arquivos extraíveis, preservando evidência de outras frentes e sem chamada LLM. Esses testes complementam as três comparações reais, sem substituí-las.

### Cadeia de prova

Workspace da tarefa: `C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade`.

- Fonte: `work/lc-capture-set132/result.json`, SHA256 84FCA964CE17DF845680C5C4BCD3C71ADE080F0B88CEC51646FB39E168901A42,21dias completos.
- Metadados atuais: `work/lc165refresh-native-refresh/postcapture-result.json`, SHA256 E36CD3A68FDFFAD57A09920F4524D26620BD3F4D8DAE7058DF52F770F44B6B4B.
- Catálogo observado: `work/localizador0752/policy173/localizador-native26.OBSERVED.json`, SHA256 544892A2F19365FD7023C8067CFA63A070AC9C49BEF806CE75A479152B0C77E5; revisão179 e vínculo explícito dos seis recibos às semanas, Source e Native atuais.
- Comparadores: `work/lc178compare-comparisons/driver-result.json`, SHA256 5C67B89DE8011FAB0097B56627621FB9F3CD9321C6792CB2F447A0429B0DFE98,12 operações fechadas/3 períodos iguais.
- Gate final: `work/lc180final-gate/result.json`, SHA256 52198253E47CE2A11ACECD715F1DFEC51CCA02F2F0C1FFCEE48BFA1FA3B1E3C0; fechamento SHA256 B9DCFF60010784801BCF537FA0B8626D391E0A14C424A126D16ED5F9022AB9DB.
- Full TEST150: `work/independent-export-localizador0752-build150-test-union/root-executed-binding.json`, SHA256 A36DEB1E2E3B4AD4D94A93711AE86624C0D11FA04D12E8BA57B862EC7BF804B7; unidade5E760E9265AD1DD424B01904585FE74F6C455C0954E95A2C8A7F29BC376A7ACB.

### Excel para conferência do gestor

O arquivo `outputs/localizador-3periodos0752/Provas_Localizador_ETL_V2.xlsx` contém três resumos de quantidade e nove pares reais, três por período. As abas Quantidades e Amostras foram recalculadas, exportadas, reabertas e renderizadas; ambas receberam inspeção visual do Root. As 54 verificações visíveis mostram Filial, Minuta, NF, Data do frete, Volumes e Total a receber, com arquivo e linha ESL. Os pares foram associados pelas 26 colunas completas. O valor monetário de prova mantém todos os dígitos para evitar perda de precisão do Excel.

Recibo real: `work/lc194workbook/result.json`, SHA256 22B4EAA1F65653D68F99991DCEEE025B91CD8D8AA95CD7B69CDF5001CB81C3BF; fechamento DA57DE874A6DB182592FEC9CBD1172FD2B236AC932F4BC58CE6177466D4DB8F8. XLSX 10860 bytes, SHA256 6880be5dbf8d054c1cb70d48653f9a171ae82c574a13097134640151fa388e3c. O resumo público de validação é `outputs/localizador-3periodos0752/Validacao_Localizador_3periodos.json`.

### Limite do aceite

O aceite é de comparação histórica nesses três períodos e da correção de código testada. Não demonstra simultaneidade entre snapshots nem garante ausência de erros futuros. SQL, adoção Main, deploy, alterações V1 e ACL não foram executados. O pipeline deve continuar recusando contratos, tipos, códigos ou períodos que não consiga verificar, sem marcar extração parcial como sucesso.

### Registro anterior preservado

O texto abaixo é a fotografia histórica anterior ao encerramento 3/3. Pendências e contagens0/3 nele não representam o resultado atual acima.


# Localizador de Cargas — comparação independente com a ESL

Estado desta fotografia: **0/3 períodos comparados**. As contagens abaixo pertencem aos Excel oficiais efetivamente lidos; a fonte API ainda precisa completar os mesmos períodos. O estado corrente e os próximos responsáveis ficam em `STATES.md`.

## Escopo observado

Relatório ESL **8656**, perfil `LOCALIZADOR_8656_SERVICE_AT_NATIVE26_V1`, 26 colunas. O único filtro preenchido é `freights[service_at]`; os sete demais controles ficam explicitamente vazios, incluindo `scopes[by_updated_at]`. O dia do frete usa o relógio civil do valor original com offset, sem conversão para uma data UTC.

| Período de 2026 | Arquivo oficial | ID ESL | Ocorrências no Excel |
|---|---|---:|---:|
| 12–18/10 | localizador-de-cargas_2026_11_07_21_43.xlsx | 1773250 | 3476 |
| 19–25/10 | localizador-de-cargas_2026_11_07_21_46.xlsx | 1773252 | 4189 |
| 26/10–01/11 | localizador-de-cargas_2026_11_07_21_47.xlsx | 1773254 | 4598 |

As três transferências Native28 fecharam com um GET por arquivo, sem autenticação Bearer, redirecionamento, repetição ou consulta adicional de metadados. NativeProfile32 abriu os três ZIP/XLSX até EOF, verificou CRC de todas as entradas e conferiu os 26 cabeçalhos: **12263 ocorrências físicas**, 637676 visitas. Os links assinados e arquivos integrais permanecem privados.

## Retenção e critérios de comparação

A captura atual divide cada semana em sete dias civis disjuntos. Cada dia exige INFO válido, todas as páginas DATA contíguas e uma página final realmente igual a `[]`. Um prazo excedido, uma recusa ou zero linhas antes do terminal não demonstram um dia vazio. O payload retém os registros originais, inclusive números e escapes; o resultado do mapper nunca substitui os bytes de origem.

A união semanal Source0 deve demonstrar os sete dias, inclusive eventual payload vazio de zero bytes. Ela concatena literalmente os RAWs e preserva os fatos dos Producers. A qualificação do Consumer atual reabre todos os RAWs diários não vazios, seus corpos originais e fechamentos. Um Consumer posterior não reescreve os flags anteriores nem exige baixar novamente o Excel.

A igualdade final exige 26 bags completos de colunas, bags de tuplas completas, todas as multiplicidades, quatro totais e concordância dos quatro eixos originais/apresentados em Java e Python independente. Minuta é um valor comum; nenhuma chave global, deduplicação ou contagem de cargas distintas foi demonstrada. NF mantém ordem e repetições de seus itens. Os quatro totais são Volumes, Peso taxado, Valor NF e Total a receber; a política candidata é inteiro exato para Volumes e unidades binárias exatas para os três decimais, sem tolerância.

Carriers XLSX, ausência/NULL/empty, separador NF, apresentação temporal e dicionários de enums exigem observação e catálogo explícitos. A correlação de códigos e rótulos usa as outras 23 colunas completas como âncoras, com multiplicidades, e recusa ambiguidades. Frequências globais não demonstram uma ligação.

## Descrição INFO realmente observada

O corpo INFO preserva os 26 campos históricos, mas qualifica os oito filtros. A política `STRICT_HISTORICAL_OR_OBSERVED_QUALIFIED_BIJECTION_V1` aceita somente o conjunto histórico completo ou o conjunto atual completo abaixo; mistura, duplicação e outro qualificador são recusados.

| Nome atual | Nome histórico |
|---|---|
| freights.service_at | service_at |
| fit_freight_location.current_location_id | current_location_id |
| freights.payer_id | payer_id |
| freights.type | type |
| freights.status | status |
| fit_fis_invoice.number | number |
| fit_fis_invoice.key | key |
| scopes.by_updated_at | by_updated_at |

JavaInfo32 foi aplicado em cinco arquivos. O INFO real retido foi validado novamente em Source0 sem nova HTTP; os seis pacotes Python Info32 passaram 282 fixtures. O full TEST33 passou **980 métodos/98 classes**, após FORMAT e compilação próprios, e fechou/restaurou o Original. Binding: `work/independent-export-localizador0752-build33-test-union/root-executed-binding.json`, SHA256 `57A9386A161FAEF85C6B0663B1DA7350170EE41B2943A3EB3C2D40D00A8E1E1B` no workspace da tarefa. Gate33/Manager33 passaram outras 60/55 fixtures Source0. Esses testes não representam comparações de períodos reais.

## Evidência causal ainda em tratamento

Source38 de 13/10 atingiu o prazo após 800 linhas/8 páginas DATA, sem terminal. Os oito campos extras realmente observados como NULL são `fit_dyn_drt_nickname`, `fit_dyn_name`, `fit_fln_cln_nickname`, `fit_fln_status`, `fit_fte_lce_occurrence_at`, `fit_fte_lce_ore_description`, `fit_o_n_drt_nickname` e `fit_o_n_name`. O mapper histórico processou todas as ocorrências sem recusas. O contrato P2 nullable8 foi aplicado na integração44, apenas com essas oito mudanças de nulabilidade. P1b e os perfis históricos permanecem imutáveis.

Wire39 reabriu os corpos e registros físicos: nove HTTP200 fechadas, INFO e DATA1–8; CURL levou aproximadamente 1–2 segundos por resposta, enquanto o intervalo entre respostas cresceu de 15 para 30 segundos. Wire40 confirmou que os nove diretórios de operação correspondem exatamente às nove receipts fechadas e que DATA9 não iniciou, sem latch de efeito desconhecido ou 429. O flag anterior `wireOutcomeUnknown=true` permanece no Producer; a reconciliação é uma evidência separada.

A otimização candidata de `QualificationJson` reutiliza a configuração estrita do leitor JSON. O probe42 Source0 fechou com o runtime antigo33 selado e o candidato em ClassLoaders isolados: 2000 interpretações levaram 77,97/41,36ms; 2000 leituras físicas, 2564,59/2073,33ms. Os mesmos limites de strings foram confirmados. Essa medição sintética não demonstra que a coleta real já cabe no prazo: o parsing representa uma pequena parcela da leitura física.

Uma segunda correção reúne os três eventos de um fato (RESERVED/STARTED/EVIDENCE) sob o mesmo lock. Cada transição e cada novo arquivo mantêm validação, persistência e hash; a cadeia e os fatos completos continuam conferidos antes e depois do fato lógico. Somente as releituras intermediárias redundantes foram fundidas, sem cache de arquivos ou aumento de limites. O probe45 Source0 passou e fechou: oito fatos completos,3,683s→1,990s; doze fatos,5,935s→3,358s. Todos os arquivos sintéticos das versões antiga/nova ficaram literalmente idênticos e foram reabertos. A tentativa43 maior atingiu o limite do Java antigo e foi preservada como falha, sem efeitos de fonte.

Root integrou os 11 alvos e 24 regressões na integração44 (recibo `root-combined44-applied.json`, SHA256 `38EC1651FDE3FEC8F8B51D37320EBB707BC430CAE81198525E92BAEDFE2EBC68`). A suíte44 completa passou **1004 métodos/101 classes**, com todos os980nomes anteriores:341+401+262, sem falhas, erros ou testes ignorados, com auditoria exata dos101XML e fechamento/restauração de todos os Originals. Binding SHA256 `95B1ABC31E79871A54246A354ACE39391F3568B6B06D7A8F04AC5AA925CA822D`; unidade356Java/43controles/399inputs, SHA256 `8CBEA299FE05F1A49AA0EB642AC08410BBAF4B955EF81CEC476A0C9BDBBB6CC8`. A medição sintética e os testes não demonstram igualdade de dados nem que uma coleta completa já cabe no prazo.

## Limites e encerramento

Um único executor pesado por vez, Original de 1 GiB/300s, prazo global270s, Java512m; captura Source210s, INFO1+DATA16 HTTP no máximo, `per=100`, retry0/redirect0, RAW8MiB/12000linhas. Inspeções e comparações offline são Source0/HTTP0/SQL0. Não houve SQL, migração, adoção Main, deploy, alteração de V1 ou ACL.

O encerramento exige três comparações reais completas, código final qualificado, catálogo/revisão e fechamentos verificáveis, documentação atualizada e Excel com três resumos de quantidades e nove pares de amostras reais para conferência do gestor. Não há garantia de erro zero em extrações futuras ou de simultaneidade entre os snapshots.
