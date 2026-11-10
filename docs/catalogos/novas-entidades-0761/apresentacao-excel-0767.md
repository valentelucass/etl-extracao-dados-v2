# Apresentação de colunas booleanas no Excel do ESL

Em 10/11/2026, a comparação do template11067 no período02–09/11 encontrou221linhas na API e221no Excel oficial1780174. Inicialmente220ocorrências completas eram iguais. Na ocorrência restante, a API devolveu `null` em duas colunas e o Excel exibiu `Não`:

| Campo da API | Coluna do Excel | Apresentação observada |
| --- | --- | --- |
| `ctd_fgs_fte_has_delivery_receipt` | Comprovante Anexado | `true` → Sim; `false` → Não; `null` → Não |
| `ctd_fgs_dpn_finished_within_deadline` | Entregue no Prazo | `true` → Sim; `false` → Não; `null` → Não |

Essa é uma regra específica de apresentação da exportação do ESL. O dado extraído, o modelo Java e o envelope SQL conservam o valor nulo, com `presence=NULL`, `wire=NULL`, valor e representação bruta nulos. Não se converte desconhecido em falso no banco. A regra não se estende às outras colunas nem aceita números, objetos ou textos como booleanos.

## Observação independente

Uma segunda exportação oficial,1780206, foi gerada em10/11/2026 às13:01:54BRT, com as mesmas221linhas e39cabeçalhos. A qualificação da apresentação usou206pares em que todas as outras37colunas coincidiam integralmente e sem ambiguidade. Os15pares cujo conteúdo já havia mudado foram excluídos somente dessa qualificação, sem remover nenhuma linha das comparações completas.

| Campo | Controles true/Sim | Controles false/Não | Controles null/Não |
| --- | ---: | ---: | ---: |
| Comprovante Anexado | 115 | 90 | 1 |
| Entregue no Prazo | 148 | 57 | 1 |

A regra foi congelada com o hash do arquivo1780206 e os hashes das oito capturas de origem. Aplicada à comparação independente com o primeiro arquivo1780174, produziu igualdade integral de221ocorrências,39colunas e8.619valores, incluindo multiplicidades. Nenhum rótulo ou valor novo foi aprendido do arquivo submetido a esse aceite.

## Mudança observada entre as duas exportações

Os dois Excel oficiais possuem221linhas. Entre eles,206ocorrências completas são iguais e15mudaram somente em `ctd_fgs_fte_lce_ore_description`, a Última Ocorrência. Todas as outras38colunas e suas multiplicidades são iguais. Essa evolução foi comparada diretamente entre os dois arquivos, sem depender da API, e permanece registrada separadamente da apresentação dos booleanos.

O aceite se refere aos arquivos e capturas identificados. A fonte pode alterar campos relacionados de registros com emissão antiga; um período encerrado não equivale a um snapshot imutável. Não se deve alterar valores do ETL, deduplicar linhas ou acrescentar tolerâncias para ocultar essa evolução.

Provas técnicas do chat: `work/presentation-null-booleans0767-v2.json`, `comparison0767-con-independent-rule-a2.json` e `native-evolution-con0767.json`. Respostas, envelopes e Excel oficiais ficam somente em `Armazenamento-Projetos/etl-extracao-dados-v2/comparacao0767`. Esta observação não altera as regras de negócio nem publica o dashboard.

## Ocorrências: Agendado

No template12072, `ice_fit_dpn_manual_schedule`, apresentado como Agendado, possui a mesma representação de exportação: `true` corresponde a Sim, `false` e `null` são apresentados como Não. A qualificação usou o arquivo oficial1780186 e confrontou todas as outras19colunas, incluindo a multiplicidade das ocorrências. Houve4.672controles true/Sim,31.670false/Não e6null/Não, sem conflitos. Grupos cujas outras colunas haviam mudado não foram usados para aprender essa apresentação.

O segundo arquivo1780257 foi usado para a comparação posterior, com a regra já congelada. Os seis nulos continuam nulos na resposta literal, na observação Java e no SQL. A equivalência visual não altera o dado nem se aplica às demais colunas de Ocorrências. Tipos diferentes de booleano ou nulo continuam recusados.

Essa observação elimina a diferença de representação de Agendado. As mudanças efetivas em Finalização performance e Localização Atual são tratadas separadamente, na [comparação0767](comparacao-excel-0767.md), sem transformar diferenças reais em igualdade de conteúdo.
