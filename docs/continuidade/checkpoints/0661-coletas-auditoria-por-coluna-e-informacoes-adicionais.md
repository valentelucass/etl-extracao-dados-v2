# Coletas: auditoria por coluna e informações adicionais da API

Em03/11/2026 o usuário esclareceu que a API pode retornar mais informações que o Excel. Essas informações são válidas e devem ser preservadas. O objetivo é conferir cada coluna existente no Excel dentro do mesmo filtro semanal, incluindo seus valores por registro, multiplicidades e totais. Contagem ou estrutura bruta distinta não prova erro de extração sozinha.

Cada ocorrência do Excel deve encontrar uma contraparte válida na API. Registros adicionais exigem classificar escopo e vínculos; não autoriza descartá-los, deduplicar ou alterar os dados para forçar igualdade. Totais iguais continuam insuficientes quando os valores individuais diferem. Diferenças de representação sem regra qualificada ficam pendentes, sem afirmar erro de conteúdo ou igualdade.

O relatório privado0661 reclassifica o resultado integral0660, preservando suas métricas originais e as capturas fechadas. Não é nova extração, nova execução do comparador ou alteração do extrator canônico. Distribuição das31colunas:

| Resultado neste par | Colunas |
| --- | ---: |
| Conteúdo já igual segundo regras aceitas | 8 |
| Apresentação ainda em validação | 6 |
| Valores distintos observados, causa não atribuída | 4 |
| Totais distintos junto de presenças a investigar | 2 |
| Diferenças ainda a classificar | 11 |

As oito colunas iguais são ID, Filial, N°coleta, Cliente, Cidade, UF, CEP e Bairro. A conclusão é específica ao par Source12/MAPPED e Excel29, de19a25/10/2026. As seis pendentes são cinco colunas de datas e Ação da Ocorrência. Colunas numéricas de tentativas e manifesto combinam diferenças de total e estadosNULL/ausência; o contador por chave não equivale ao número de valores numéricos efetivamente alterados. Somatórios de identificadores permanecem apenas como diagnóstico histórico, sem significado de total de negócio.

Provas anteriores continuam válidas:48.391células preservadas RAW→mapped, comparação integral0660 e mudança entre os Excel28/29 demonstrada no0659. Capturas em instantes distintos ainda impedem atribuir automaticamente cada valor divergente ao extrator. Nenhum período completo100% foi declarado:0/3. A próxima etapa local é qualificar apresentação/presenças, isolar diferenças de valores e investigar suas causas. Campos adicionais da API não bloqueiam a aprovação das colunas comparáveis.

Arquivos privados desta classificação no supervisor: `outputs/coletas-auditoria-por-coluna0661.json` e `.md`. Origem preservada: `outputs/coletas-1561-31-colunas-mapped-excel29out-corrigido0660.csv`, SHA256985B0A0C33DBF096C6AF4F7FD789673F306CFD43AAA19CC08CEF2B577017DF3A. Aceite da execução anterior: `root-acceptance-coletas31-mapped-decimal0660-03.json`, SHA2568A8A5148DE8E295EEC4D763460B4BE30D981937942370676BBA810ECF5CD065A.

Root permanece único executor pesado. DRAFT05 do chat de correções recebido, ainda não executado/admitido; revisãoRoot pendente. Não houve Source/HTTP/SQL nesta atualização;STOP429 permanece para novas chamadas, sem impedir análise/correção local. V1 informado pausado permanece preservado. Dados de negócio, credenciais e produção não foram alterados.
