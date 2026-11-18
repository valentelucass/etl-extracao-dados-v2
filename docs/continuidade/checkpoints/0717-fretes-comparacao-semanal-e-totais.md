# Checkpoint 0717 — comparação semanal e totais de Fretes

Registro de metadados informado pelo Root em 05/11/2026. O estado abaixo é um snapshot anterior a eventual nova execução LIVE, não uma leitura atual do canônico nem autorização para alterá-lo.

## Comparação independente

Os arquivos API1766200 e Excel UI1766446, para 19–25/10/2026, têm 4.694 linhas e 4.188 IDs em cada lado. A comparação semanal verificou 114 colunas e multiplicidades: 98 bags de coluna iguais, 16 divergentes, 49 tuplas diferentes por lado e 45 IDs com conteúdo alterado. Não foram identificados IDs exclusivos nem diferenças de multiplicidade por ID. O diagnóstico por chave é agregado; nenhum ID ou valor privado é reproduzido aqui.

Original 78512 encerrou com código 0, em 58.324 ms, pico 205.676.544 B. Diagnóstico no workspace supervisor `work/two-xlsx-week-totals-actual0716-run03/diagnosis.json`, SHA-256 prefixo `27515DCC`, 51.308 B. A igualdade de 98 colunas não é igualdade de linhas, aprovação da semana ou prova de erro de uma origem. Capturas às 10:42 e 11:55, intervalo de 73 minutos e influência temporal não demonstrada.

## Cinquenta totais

Dos 50 campos, 49 ficaram prontos: 44 totais iguais e 5 diferentes, colunas 104, 106, 107, 110 e 114. A coluna 100 permanece pendente: API teve 4.011 ocorrências numéricas e 683 ausentes; UI teve 4.014 numéricas e 680 ausentes. Ausência não foi convertida em zero. A comparação usa o modelo numérico qualificado e não constitui aprovação semântica ou funcional da semana.

Qualificação semanal de 14 casos: Original 40280/0. Cinco casos de totais do módulo V2: Original 56924/0. Perfis físicos semanais: Original 19060/0; admissão Root com prefixo `1C80`. Esses resultados qualificam os controles descritos, não todas as condições de produção.

## Estado e continuação

Fretes permanece 0/3 semanas completas; Coletas 3/3; V1 pausado; Main e consumo do dashboard pendentes. Snapshot informado: estado `494B92E7` (728.504 B), slot vazio e Aux control `1F6E8925` com três fatos. A nova POST 0717 estava em preparação e ainda não havia sido executada no momento deste registro; o Root atualizará o estado após qualquer execução LIVE.

Controles preparatórios Source0: quatro fixtures passaram sob Original 34640/0. OFFLINE passou 16 casos de parser e cinco de privacidade sob Original 35112/0; curl 47240/0 e EOF, com restauração do estado `494B92E7`. Esses testes não são prova do resultado de uma chamada LIVE.

Não registrar aqui IDs individuais, valores, corpos privados ou endereços assinados. Preservar as provas e o histórico anterior; não alterar canônico, ACL ou fatos existentes por este checkpoint.
## Atualização posterior — nova exportação aceita e reconciliada

O fechamento da segunda tentativa sem despacho passou Apply e Recover; a observação do próprio Original 77840 registrou sete selecionados contra seis presentes, com uma identidade apenas na seleção e nenhuma apenas no censo. Isso explica a barreira daquela tentativa, não a causa de tentativas anteriores. O Root selecionou explicitamente seis identidades usando a evidência própria, pin `A646AB17`; nenhuma seleção é automática. OFFLINE05 passou com parent `F1A720`.

Novo POST experimental do relatório 6389 para 19–25/10 aceito às 13:50: arquivo API 1766708, Original 83756/0, auditoria Original 34360/0. Reconciliação de quatro fatos passou Apply e Recover; comprovante `60F63` e controle AUX `E47B4D40`, 5.695 B, em `work/fretes-aux-repeat-export-reconcile0717-root03/aux-current-control.json` no workspace supervisor. A UI solicitou exportação independente às 13:50 e ainda constava gerando quando informado. Um POST aceito não prova geração, download ou igualdade dos arquivos. Fretes continua 0/3; Coletas 3/3, V1 pausado, Main/dashboard pendentes. Estado e slot devem ser lidos das provas atuais antes de uma nova operação.
## Atualização posterior — par próximo disponível, conteúdo pendente

As exportações API 1766708 e UI 1766710 foram geradas às 13:50. O stage Source0 sob Original 48320/0 verificou ambos os ZIPs, cada um com nove entradas e leitura até EOF. O Root aprovou a independência das aquisições em `outputs/root-two-xlsx-independence0717-01.APPROVED.json`, SHA-256 `C129723AFA2EF14DE2EC501A6DBAE655B3A9F606CA7E1C994375067EC0715D42`, 3.696 B. Esse aceite de independência não é comparação de conteúdo.

Controle AUX de seis fatos: `work/fretes-aux-download-reconcile-next-root0717-01/aux-current-control.json`, SHA-256 `795BC6EC9BBC305AA5B41BD4BEA7998B56CCB09BE17BF1B8D3DA7DAAB0D275A7`, 8.176 B. No momento da informação, o slot próprio de PROFILES estava ativo e a extração de perfis semanais estava em execução; consultar os recibos e estado atuais antes de continuar. A comparação real de 114 colunas, tuplas, IDs e 50 totais ainda não havia terminado, e nenhuma igualdade desse novo par é alegada neste registro. Fretes 0/3, Coletas 3/3, V1 pausado; Main e dashboard pendentes.
## Resultado posterior — primeira semana aceita para a rota independente

A comparação fechada do par próximo API 1766708/UI 1766710, capturado às 13:50, passou em Original 8128/0. Cada lado teve 4.694 linhas e 4.188 IDs. Os 114 bags de coluna e o multiconjunto completo de tuplas foram iguais; todos os IDs tiveram conteúdo igual, sem IDs exclusivos nem diferenças de multiplicidade. Os 50 totais ficaram prontos e iguais, com zero pendências.

O Root verificou individualmente as 114 colunas e os 50 totais em `work/Accept-RepeatWeekRoot0717-01.ps1` e aceitou o conteúdo da semana 19–25/10 para a rota independente API/Excel em `outputs/root-fretes-first-week-content-acceptance0717-01.json`, 3.576 B, SHA-256 `EDF6A20B4CD2B2F9A37F597505D3DD41BFC56D92C29A8F05FEB2E1C7BA18F37F`. Diagnóstico `work/two-xlsx-week-totals-actual0717-run01/diagnosis.json` no workspace supervisor. Esta aceitação não aprova Main, consumo do dashboard ou a entidade completa.

O par anterior, preservado, teve 98/114 bags iguais e 44 totais iguais, cinco diferentes e um pendente. O novo par passou 114/114 e 50/50; o intervalo menor de captura não prova a causa de cada diferença anterior nem uma correção de mapeamento. Fretes passa a 1/3 semanas aceitas por essa rota; Coletas 3/3, V1 pausado, Main/dashboard/entidade pendentes.