# Comparação de Consolidação e Ocorrências com Excel — 0767

## Escopo e resultado

O usuário pediu encerrar a comparação das duas novas entidades antes de conectar o dashboard e adiar a carga de90dias para a madrugada. Foram extraídas novamente somente Consolidação11067 e Ocorrências12072, no período02–09/11/2026, com os mesmos filtros dos Excel oficiais e sem filtros adicionais. Não houve importação de90dias, ativação de tarefa permanente, publicação ou corte do dashboard.

| Entidade | Campos | Linhas recebidas e conferidas no SQL | Excel usado na comparação |
| --- | ---: | ---: | --- |
| Consolidação Detalhada | 39 | 221 | 1780174:221linhas; confirmação1780206:221 |
| Ocorrências | 20 | 36.467 | 1780186:36.404linhas; posterior1780257:36.472 |
| Total recebido | 59 | 36.688 | Arquivos completos preservados |

As16partições e737.959valores foram reabertos por verificadores independentes. HTTP→envelopes→SQL são integralmente iguais, com paginação terminal, tipos Java, contagens, ordinais, hashes e multiplicidades conferidos. A visão de dados atuais aponta para os16runs novos, com36.688linhas; capturas anteriores, dados e acessos protegidos permanecem preservados. Não há execução RUNNING. SQL16.0.1000.6 e o catálogo11/508 permanecem iguais.

## Consolidação Detalhada

O confronto com o arquivo1780174 terminou com221ocorrências completas,39campos e8.619valores iguais. As duas diferenças iniciais de vazio/Não foram qualificadas como apresentação do Excel, usando uma exportação independente. O dado original continua nulo no modelo e no banco. A regra e seus controles estão em [apresentação0767](apresentacao-excel-0767.md).

O arquivo1780206, gerado depois, mantém221linhas, mas15ocorrências mudaram somente em Última Ocorrência. As outras38colunas permanecem iguais. Essa mudança foi demonstrada comparando os dois Excel diretamente. Ela não é rejeição de tipo, perda de campo ou alteração feita pelo ETL.

## Ocorrências e evolução durante a captura

A captura atual tem36.467linhas e729.340valores, todos iguais ao SQL. Depois de qualificar a apresentação de Agendado, cada uma dessas36.467ocorrências completas, considerando os20campos e sua multiplicidade, aparece integralmente em pelo menos um dos dois Excel oficiais. Não há ocorrência de origem sem confirmação nesses arquivos. O cálculo usa o máximo das multiplicidades de cada ocorrência nos dois arquivos, não a soma de duas versões históricas.

Finalização performance (`ice_fit_dpn_performance_finished_at`) e Localização Atual (`ice_fit_fln_cln_nickname`) mudaram durante o intervalo entre exportações e capturas. Essas mudanças são mantidas visíveis. Não se aceita uma comparação feita apenas com18campos como se fosse igualdade dos20: a projeção dos18campos estáveis serve somente para investigar as diferenças, enquanto a confirmação de conteúdo usa as ocorrências completas dos20campos.

No último Excel existem cinco ocorrências adicionais à captura completa. As18colunas estáveis não têm nenhuma ocorrência somente na origem, e as cinco ocorrências somente no Excel foram todas localizadas numa consulta posterior pelo filtro `search[invoice_occurrences][updated_at]`, com paginação terminal e mapper real. Essa consulta começou dois minutos antes do início da captura completa e terminou no horário do download do segundo Excel, reproduzindo a sobreposição prevista para recuperação de alterações durante a carga. Foram69linhas aceitas pelo filtro, sem rejeições.

No confronto estrito com esse último Excel,36.431ocorrências completas são iguais. Há36pares alterados:15em Finalização performance e21em Localização Atual. Restam apenas as cinco ocorrências adicionais do arquivo posterior. Nenhuma ocorrência da captura fica sem explicação; igualdade literal simultânea continua falsa e não foi substituída por um aceite artificial.

A consulta de diagnóstico é somente de leitura da fonte: não inseriu69linhas parciais no banco. As cinco ocorrências estão identificadas para recuperação pelo mecanismo de atualização; a visão SQL desta prova continua representando a captura completa de36.467linhas, e não o arquivo posterior de36.472. A rotina produtiva recarrega integralmente as datas afetadas antes de substituir a versão atual.

## O que o aceite comprova

- Todos os59atributos estão modelados e o conteúdo recebido chegou ao banco sem diferenças.
- Consolidação tem equivalência integral com o primeiro arquivo oficial identificado, após apresentação qualificada independentemente.
- Em Ocorrências, todas as36.467ocorrências recebidas têm confirmação integral em pelo menos um dos dois arquivos oficiais. As mudanças de conteúdo e as cinco linhas adicionais foram reconciliadas separadamente.
- Valores nulos continuam distintos de falso no armazenamento. A representação Não está restrita às três colunas booleanas observadas na exportação.
- Não foram inferidas chaves de negócio, descartadas duplicatas físicas, removidas linhas para obter igualdade ou aprendidos rótulos novos do próprio arquivo submetido ao aceite.

Isso não comprova igualdade literal dos dois relatórios numa única revisão transacional comum. Em particular,36.467não foi declarado igual a36.472. Não há snapshot comum exposto pelo fornecedor, e os períodos de emissão/ocorrência encerrados continuam sujeitos a atualizações em campos relacionados. O dashboard deve informar o horário da última captura confirmada e consultar a visão atual da origem/período pretendidos, sem somar versões do histórico.

## Limitação confirmada para atualização dos campos ligados ao frete

As36ocorrências cujos campos ligados ao frete mudaram entre captura e Excel não apareceram na consulta por atualização da Ocorrência, embora a janela cobrisse desde dois minutos antes do início da captura até o download do último arquivo. A correspondência foi conferida nas18colunas estáveis, sem deduplicação ou inferência de chave de negócio. Essa mesma consulta encontrou as cinco ocorrências adicionais.

Portanto, o filtro de atualização da Ocorrência sozinho não garante capturar alterações em Finalização performance e Localização Atual. O código0766 já reconfere uma data histórica completa por execução nas nove entidades com filtro, o que protege a recuperação eventual, mas não comprova atualização desses campos em cada ciclo. A carga inicial de90dias não elimina essa característica do filtro.

Antes de tratar o dashboard como atualizado integralmente a cada ciclo, é necessário qualificar a propagação das mudanças de Fretes para as datas de Ocorrências afetadas, ou consumir esses atributos por um relacionamento qualificado com Fretes atuais. Não se deve criar uma chave ou associação apenas pelo número do documento. Esse ajuste não foi implementado nesta comparação e o dashboard não foi conectado. A comparação de conteúdo está concluída; a garantia de atualização horária dos campos relacionados permanece pendente.

## Evidências e preservação

O runtime0766-v1 já qualificado foi usado sem alteração de JAR, mappers, contratos de dados ou scripts produtivos. O acionador finito de verificação selecionou somente as duas entidades, em oito datas cada. A captura terminou em10/11/2026 às16:22:05Z, com395requisições e zero rejeições. Não houve DDL, grants, alteração de conta, serviço ou versão SQL.

Provas do chat: `work/source0767-a1.json`, `independent-source0767.json`, `independent-sql0767.json`, `preservation0767.json`, `comparison0767-con-independent-rule-a2.json`, `native-evolution-con0767.json`, `presentation-null-booleans0767-v2.json`, `presentation-oco-null0767-v2.json`, `comparison0767-final.json`, `reconciliation-threeway0767-a3.json` e `update-proof0767-a2.json`, com os recibos de memória associados. Dados, respostas e os quatro Excel oficiais ficam em `Armazenamento-Projetos/etl-extracao-dados-v2/comparacao0767`, fora do Git e dos entregáveis públicos.

Tentativas preparatórias e comparações estritas divergentes foram preservadas. A auditoria independente já concluída não foi repetida após o erro local de soma de dicionários no PowerShell5: somente a soma do verificador foi corrigida e a auditoria de preservação foi executada novamente. A primeira consulta curta de atualização foi preservada; a consulta sucessora usou a janela correta desde o início da captura, necessária para reencontrar alterações ocorridas enquanto as páginas eram recebidas.

Os recibos0762 continuam históricos e não foram reescritos para parecerem aprovados. A carga mínima de90dias continua implementada e adiada por pedido do usuário. Esta comparação não registra nem inicia automaticamente uma execução de madrugada.
