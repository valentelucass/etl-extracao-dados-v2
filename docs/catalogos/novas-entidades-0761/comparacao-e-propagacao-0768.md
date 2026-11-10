# Comparação e propagação de campos relacionados — 0768

## Escopo e resultado

O ajuste autorizado fecha a recuperação de mudanças ligadas ao frete em Ocorrências e a reconferência de Consolidação antes da integração com o dashboard. A prova real extraiu somente as duas entidades no período **02 a 09/11/2026**, usando os mesmos filtros dos arquivos oficiais. Não houve carga inicial de 90 dias, agendamento, execução contínua ou conexão do dashboard.

| Entidade | Campos | Linhas recebidas e conferidas no SQL | Excel posterior |
| --- | ---: | ---: | ---: |
| Consolidação Detalhada | 39 | 221 | 221 |
| Ocorrências | 20 | 36.509 | 36.509 |

Foram conferidos independentemente **36.730 registros, 738.799 valores e 16 partições**, da resposta HTTP literal ao modelo Java e à releitura SQL. Contagens, campos, valores, ordinais, hashes e multiplicidades coincidem integralmente. A visão atual aponta para as 16 fotografias confirmadas; não soma cópias históricas. As fotografias anteriores de 36.688 registros/737.959 valores foram relidas integralmente e permanecem iguais. Dados protegidos, usuários, permissões, papéis, 13 etapas de instalação, catálogo 11/508 e SQL 16.0.1000.6 foram preservados, sem execuções RUNNING.

## Comparação com o Excel

Consolidação foi comparada nos 39 campos e tem igualdade integral com o arquivo posterior, incluindo multiplicidades. Ocorrências foi comparada nos 20 campos. Os arquivos oficiais são identificados pelos IDs e SHA256 nos recibos privados; os filtros foram observados na interface, sem filtros adicionais além da emissão/ocorrência do período.

Igualdade integral com o arquivo posterior: **falsa**. O ESL não expõe uma revisão transacional comum entre exportação e API. Todas as 36.509 linhas completas de Ocorrências, incluindo os 20 campos e suas multiplicidades, têm confirmação em pelo menos um dos dois novos Excel. A união usa o máximo das multiplicidades, nunca a soma de versões. A comparação temporal encontrou 0 linhas novas ou versões de campos próprios presentes no arquivo posterior e ausentes ou diferentes da captura; todas foram reencontradas pelo filtro de atualização, sem gravar deltas parciais no SQL. A projeção auxiliar dos campos sem as mudanças relacionadas em análise serve somente a essa investigação; o aceite de conteúdo usa as linhas completas dos 20 campos.
No confronto estrito com o arquivo posterior há 35 linhas com diferenças: 32 em Finalização performance e três em Previsão de entrega. No confronto por coluna, as outras 18 colunas, inclusive Localização Atual, não apresentam diferenças de valores ou multiplicidades. Todas as versões recebidas dessas linhas constam integralmente no Excel anterior ou posterior. Isso documenta alterações dos campos relacionados durante o intervalo, sem declarar que fotografias obtidas em horários distintos representam um mesmo instante. Os 36 casos da regressão anterior coincidem integralmente com o arquivo posterior.

O painel do ESL informou 36.000 processados para o arquivo posterior de Ocorrências, mas a leitura independente do XLSX íntegro confirmou 36.509 linhas e os 20 cabeçalhos. As quantidades por data coincidem com a extração. O contador visível permanece registrado como 36.000; a contagem real do arquivo tem recibo próprio, SHA256 e validação do ZIP. Nenhuma linha foi cortada para coincidir com o contador.

As políticas de apresentação são as já qualificadas em [0767](apresentacao-excel-0767.md) e [0762](comparacao-excel-0762.md). Nenhum rótulo ou regra foi aprendido a partir dos novos arquivos submetidos ao aceite. Valores nulos continuam nulos no modelo e no SQL; as três regras vazio/Não se aplicam somente à comparação com o Excel.

## Regressão corrigida

As **36 ocorrências** da limitação anterior foram novamente localizadas com os atributos observados e suas multiplicidades. Todas pertencem a datas efetivamente recapturadas pela fase produtiva `RELATED_UPDATED`, e todas têm confirmação integral dos 20 campos em pelo menos um dos novos Excel. São as 15 mudanças de finalização da performance e 21 de localização identificadas em [0767](comparacao-excel-0767.md). As referências auxiliares não se tornaram chaves primárias, não combinaram linhas e não eliminaram duplicatas físicas.

Consolidação percorre diretamente todo o histórico carregado em cada rodada, com cursor durável. Ocorrências usa alterações de Fretes e a leitura de localização para selecionar datas e só substitui cada data após captura completa, mapper válido e releitura SQL exata. A leitura auxiliar pode ser dispensada quando todas as datas já estão selecionadas; isso não dispensa nenhuma captura ou confirmação SQL. Detalhes no [runbook](../../runbooks/atualizacao-campos-relacionados-0768.md).

## Falhas preservadas e respectivas correções

| Evidência observada | Tratamento implementado e verificado |
| --- | --- |
| Fretes recusou período de negócio extenso com `required_filter_blank` | Divisão das buscas, conservando a janela de atualização e a data mínima fixa do histórico. |
| Intervalo de oito dias de Fretes teve timeout em página posterior | Consulta por um dia de serviço, com cursor que conserva intervalos confirmados e fila de datas. Controles CURL diários passaram antes da nova captura. |
| Localização histórica retornou uma linha sem número de frete | Projeção auxiliar validada e conservadora, com 26 campos e quarentena legada preservados. Reprodução sobre os mesmos 883 registros passou; a linha sem identidade seleciona todos os dias relacionados ao intervalo, sem inventar frete ou publicar a entidade legada. |
| Mudança relacionada descoberta após fotografia anterior do mesmo ciclo | A fase `RELATED_UPDATED` exige nova captura; não reutiliza a fotografia anterior para encerrar esse trabalho. |

As tentativas anteriores continuam marcadas como falhas ou interrompidas nos recibos originais. O aceite usa a nova execução concluída com saída zero, não a alteração de um indicador de tentativa falha.

## Qualificação e operação

**161 testes específicos** passaram, com compilação, estilo, Checkstyle e pacote aprovados no índice Git isolado das alterações locais não relacionadas. A árvore Java qualificada é `8ad7f2a2a846bcb0c420a2ffb14db6296fa522e4`. O runtime privado v6 contém o mesmo JAR exercitado pela prova real; bibliotecas, JDBC e scripts operacionais mantêm os hashes anteriores. O grafo foi atualizado apenas para os cinco arquivos Java deste ajuste.

A prova finita retomou o cursor da consulta interrompida, preservando sua janela original e os intervalos já concluídos. Não afirma que o ciclo das onze entidades ou a carga de 90 dias foi executado. O checkpoint global dessa carga permanece com o SHA256 anterior. O operador poderá acionar o runtime qualificado no terminal; mínimo inicial de 90 dias e espera de 60 minutos após terminar cada execução continuam conforme 0766. Um lote confirmado não significa que todo o histórico já foi carregado.
