# 0678 — Fretes: conteúdo numérico conferido no Excel atualizado

Em 04/11/2026, a auditoria passou a comparar o conteúdo efetivo do dia 19/10/2026. **Fretes continua com 0/3 semanas integralmente aprovadas. Coletas conserva 3/3.** O usuário exige todas as colunas, valores, associações, multiplicidades e totais nos três períodos; informações adicionais da API são válidas e devem permanecer preservadas.

O checkpoint anterior é [0677](0677-fretes-primeiro-dia-extraido-completo.md). O Root permanece como único executor de trabalhos pesados, navegador e API. Os chats de Correções e Fretes/Terra, na seção ETL V2, preparam e revisam código estático. V1 permanece pausado pelo usuário; nenhuma ação SQL, de produção, contas, permissões ou versão do SQL foi realizada.

## Resultado observado

- A captura completa de 19/10 contém 946 ocorrências, 850 IDs e 114 membros por registro. A auditoria de proveniência confirmou RAW, JSONL e localizadores, sem deduplicação nem alteração dos dados.
- O confronto com o Excel semanal antigo reteve todas as 4.693 linhas, inclusive 3.747 fora dos IDs do dia. Vinte colunas coincidiram literalmente em todos os grupos. As demais diferenças foram preservadas como diferenças ou pendências; esse confronto não aprovou a semana.
- O novo Excel oficial **ESL 1763163**, gerado em 04/11 às 13:24, filtro 19–19/10, sem cache e demais filtros vazios, contém 946 linhas e 114 colunas. Arquivo privado de 535.291 bytes, SHA-256 `FF9E7FC77819FA1425914007CF60626DC4166BA1051E5F6E94D511E4BBA832C0`.
- O diagnóstico direto do XML desse arquivo confirmou os mesmos 850 IDs e a mesma multiplicidade de ocorrências. Todos os 946 `service_at` estão no dia 19/10, com offset `-03:00`, sem conversão UTC.
- Nas **41 colunas com filtro numérico**, todos os grupos com valores preenchidos coincidiram pela codificação discreta binary64 do Excel. **27 colunas** estavam integralmente preenchidas em todos os 850 grupos; **14** têm nulos/omissões ainda a qualificar. Os decimais originais continuam preservados em eixo separado: igualdade da codificação não declara igualdade das representações decimais originais.
- As diferenças de custos observadas no arquivo mais antigo não apareceram no Excel atualizado. Os arquivos possuem gerações distintas; não foi demonstrado defeito de extração nesses custos, nem autor/instante da alteração na origem.

## Provas executadas e limites

As evidências privadas ficam em `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\work`:

| Unidade | Resultado observado | Referência |
| --- | --- | --- |
| Auditoria RAW/JSONL/Excel semanal antigo | Original 0; 133.121 ms; 176.844.800 bytes; 946 ocorrências comparadas; todas as 114 colunas reportadas | `fretes-day19-audit0677-run01\audit\final-result.json`, SHA `D9BBFE433CD5C9C638A60108A60BA0A2AB99F1EB09ED8C82364DE191E7103261` |
| XML direto do Excel atualizado | Seis testes sintéticos e 16 vetores do codec passaram; Original 0, 5.136 ms, 133.128.192 bytes; leitura/comparação local em 2.967 ms | `fretes-direct-xml0677-run01\diagnosis.json`, SHA `81578EB537A6DA3BB16A0B70B2308EA0F894C9DBA82D4971493AE4D901ECA82D` |
| Novo adaptador de apresentação | 25/25 testes causais passaram; Original 0, 1.872 ms, 72.990.720 bytes; nenhum dado de negócio lido | `fretes-semantic-synthetic0677-run01\diagnosis.json` |

O diagnóstico numérico não aprovou automaticamente regras de apresentação, datas, textos, notas fiscais, nulos ou a associação conjunta das 114 colunas. Os testes cobrem, entre outros pontos, trocas de valores que preservam totais, fanout e repetições, diferença entre decimal original e codificação, soma de células, nulos/ausências/texto vazio e preservação de arrays. Não substituem o confronto dos dados reais.

Todos os processos próprios dessas unidades encerraram com fechamento conhecido e EOF dos fluxos. Sem Source, HTTP ou SQL nas comparações locais. O canônico foi restaurado exatamente para **98CAEA3A9132278151D3A4DCAC1F5392433F94585DE67EAE3138F1068E96746B**, 711.338 bytes, ACL B4, Root0658, vaga vazia, 17 pares completos e dois fatos parciais anteriores. Não restaurar baseline antigo nem reutilizar grants.

## Próximas ações

1. Concluir as regras comprovadas de datas, traduções, booleanos, NF e vazios no XML atualizado, preservando toda representação original.
2. Comparar cada uma das 114 colunas, a associação completa por ocorrência e os totais; investigar qualquer divergência restante antes da próxima extração.
3. Completar os demais dias e as três semanas com unidades novas e delimitadas, respeitando os limites de requisição e parada no primeiro erro. Apenas depois de Fretes 3/3 escolher outra entidade.

Não há bloqueio externo declarado nesta unidade. Fretes só recebe sucesso integral quando os três períodos satisfizerem o critério completo; consumir/publicar no dashboard continua como etapa separada, ainda pendente.
