# 0679 — Fretes: três registros atualizados e timeout na nova captura

Em 04/11/2026, **Fretes continua 0/3 semanas integralmente aprovadas; Coletas mantém 3/3**. A meta do usuário continua sendo comparar o conteúdo de todas as colunas e ocorrências em três períodos, corrigindo causas reais e preservando informações adicionais da API. Anterior: [0678](0678-fretes-conteudo-numerico-do-primeiro-dia-conferido.md).

## Comparação efetivamente executada

No par Source completo de 19/10 (`a8190d89-1369-44a6-8e93-8640a84e9747`) e Excel oficial 1763163, as 946 ocorrências e os 850 IDs coincidem. Todos os valores numéricos preenchidos das 41 colunas com filtro numérico coincidem na codificação do Excel; representações originais permanecem retidas. Os textos e inteiros preenchidos, as datas civis, os dois campos booleanos e a apresentação ordenada das notas fiscais também foram confrontados. Vazios e regras por campo ainda passam pela integração do comparador completo.

O diagnóstico independente de datas passou sete testes causais e 16 vetores do codec. Todos os valores datetime preenchidos do Excel decodificaram com microssegundos zero; os componentes de dois decodificadores independentes coincidiram. O formato nativo observado é `DD/MM/YYYY HH:MM:SS`. A API conserva frações adicionais, que não foram apagadas nem tratadas como erro. Esse diagnóstico não declarou renderização pelo motor do Microsoft Excel.

O confronto causal passou oito testes e comparou, por ID e multiplicidade, Status, Previsão de entrega, Finalização de performance, Comprovante de entrega e Data de atualização. **As diferenças desses cinco campos estão concentradas em três IDs; a Data de atualização do Excel é estritamente posterior à da Source nos três.** Padrões: um grupo difere em Status/Previsão/Atualização; um em Status/Performance/Comprovante/Atualização; um só em Atualização. Nos outros 847 grupos, as cinco tuplas coincidiram sem pendências. Não foi atribuída autoria nem emitido aceite das 114 tuplas com base apenas nesse recorte.

Evidências privadas em `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\work`:

| Unidade | Resultado | Evidência |
| --- | --- | --- |
| Textos, presença e apresentação | Original 0; 7.333 ms; 147.574.784 bytes; sete testes | `fretes-nonnumeric-xml0677-run01\diagnosis.json`, SHA `5C2E6D709711300A7AE8E569128A030BF8A874E87BF1368105B28E53330BA410` |
| Datas: dois decodificadores | Original 0; 3.515 ms; 134.533.120 bytes; sete testes | `fretes-timestamp-xml0677-run01\diagnosis.json`, SHA `564B7FED6A1F2092CDB2E46B67064F225265C44B86642B51FF02EAE90BCB7085` |
| Interseção das diferenças | Original 0; 3.909 ms; 136.724.480 bytes; oito testes | `fretes-causal-overlap0677-run01\diagnosis.json` |

## Nova captura causal: encerrada sem dados

Para reduzir o intervalo entre API e Excel, o Root emitiu uma nova unidade delimitada `fretes-day19-real0678-01`, execução `b3916481-c675-415f-b075-ae2fd4b376ab`, mesmo dia 19/10, `per=60`, INFO1/DATA até16/HTTP até17, intervalo mínimo de três segundos após cada resposta, request30s/Source210s/Original300s/1GiB. A unidade anterior não foi reaproveitada. V1 permaneceu pausado e SQL/produção intactos.

A unidade realizou **INFO200 e primeira DATA422**, total de duas requisições e zero registros projetados, com parada imediata e sem retry. O diagnóstico protegido do corpo retido de 67 bytes confirmou `timeout` / `Tempo limite excedido`. Não foi erro429, login ou diferença de conteúdo. Original encerrou 1 em 40.225 ms, pico371.245.056 bytes; Java73524 e fluxos encerraram com fechamento conhecido. O diagnóstico local posterior encerrou0, sem nova Source.

O efeito parcial foi reconciliado sem sobrescrever qualquer valor histórico:

- Canônico **atual**: `849BA769268BDCD0CDC9F5306280CC67604146652B58F9A3975F3B7B9D9CB09D`, 712.108 bytes, ACL B4, Root0658, vaga vazia.
- Permanecem os **17 pares completos**, acrescidos agora de **três fatos parciais**. O último par completo continua sendo a Source anterior de 19/10.
- Efeito `root-fretes-partial422-effect0678-01.json`, SHA `BA6AE483DF6CA599B64CE13B7497005E2E2D8B2B246EFDA735C741EF196AD7C9`.
- Reconciliação `root-reconciliation-fretes-partial4220678-01.json`, SHA `AA7594DD183F9181FFC1200D4523AD4B997856C1A494BE7E3F87027355056113`.
- Não restaurar98CA ou anteriores. Não reutilizar grants/janelas/unidade0678-01. Nenhuma captura completa foi adicionada por esse timeout.

## Trabalho em andamento e retomada

1. Correções prepara o sucessor do comparador de114 colunas: inteiros exatos, vazio textual limitado aos sete campos observados, totais numéricos com não valores conservados à parte, e provas derivadas dos XML/Source reais. Root já redigiu uma seleção candidata de114 campos, ainda não aprovada.
2. Terra prepara apenas um candidato estático para completar o dia com páginas menores. `per20` já recebeu DATA200 anteriormente, mas16 páginas não cobrem850 IDs. A proposta deve preservar defaults, exigir limites novos finitos e assinados e manter velocidade/deadlines/STOP. **Nenhum aumento de limite nem nova Source foi admitido ou executado por esta preparação.**
3. Depois de qualificar e admitir os bytes concretos, executar uma nova captura elegível e gerar o Excel imediatamente; conferir114 colunas/tuplas/totais e só depois avançar nos demais dias e semanas.

Root permanece único executor pesado, API e navegador. Os dois chats de apoio ficam na seção ETL V2, restritos a trabalho estático. Não há bloqueio externo declarado; timeout do fornecedor e dificuldades de implementação não constituem autorização para repetir a tentativa ou promover sucesso. Dashboard: consumo/publicação real continuam pendentes.
