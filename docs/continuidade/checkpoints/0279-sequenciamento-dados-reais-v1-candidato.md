# Checkpoint 0279 — sequenciamento dos dados reais — 22/09/2026 22:05 UTC

## Identificação e objetivo

- Anterior: `0278-handoff-extracao-esl-v2.md`, SHA-256 `e7c0f450be41d3c8f7e23665d405eb9833272dc05bbb2241d0f69a99e62aa626`.
- Objetivo do usuário: cobrar as provas que dependem de dados reais quando o V2 efetivamente fizer a primeira extração real em sombra; considerar `ETL_SISTEMA` da V1 como possível referência.
- Estado: decisão de sequenciamento registrada; B17–B29 e V2-041/G01 seguem abertos.
- Autoridade dos critérios: `STATES.md`; roteiro em `BLOCOS_ETAPA_2.md` e `TRILHA_CONCLUSAO_POR_MODELO.md`.

## Autorização e limites

- Instrução efetiva: mensagem do usuário em 22/09/2026 dizendo que não ligará o V2 agora e que as pendências com dados reais devem ser cobradas quando ele for ligado e puxar dados reais; citou o banco V1 como alternativa possível.
- Esta unidade cobre alinhamento documental. Não autoriza conexão ao V1, execução ESL/Raster, operação Java, DDL/DML, escrita produtiva, agenda, deploy ou cutover.
- A primeira leitura real do V2 exige V2-041/G01 e autorização específica de canal/runtime, entidade, janela, limites e alvo sombra. `curl` V2-025d e JAR têm autorizações diferentes.
- Banco V1: somente candidato. Antes de qualquer consulta, obter escopo read-only de host/banco/objetos/colunas/janela, identidade de acesso, limites, responsável, finalidade e validação de grão/tempo/divergências.
- Orçamento/campanha: nenhuma chamada ou consulta executada nesta unidade; condição externa nova para 4924 ausente.

## Alterações e decisões

- Inventário anterior: working tree preexistente preservado; nenhum código, schema, segredo, ledger histórico ou checkbox alterado.
- Arquivos desta unidade: `STATES.md` (decisão autoritativa), `BLOCOS_ETAPA_2.md` (momento de cobrança), `TRILHA_CONCLUSAO_POR_MODELO.md` (rastreio), este checkpoint e `docs/continuidade/RETOMADA.md` (índice, atualizado por último).
- Decisão: exigir as provas reais na campanha da entidade em sombra e concluir seus gates antes de publicação autoritativa, sweep/apply de ausência ou cutover. Diferença real requer correção e revalidação.
- Hipótese não comprovada: `ETL_SISTEMA` representar fielmente o mesmo grão, janela e estado da ESL; não foi aceito como oráculo.
- Abordagem rejeitada: marcar P17–P29 apenas pela decisão de adiar a cobrança, pois não há evidência de paridade real.

## Execução e evidência

| Passo/critério | Camada | Comando sanitizado e limites | Esperado | Observado | Evidência |
| --- | --- | --- | --- | --- | --- |
| Sequenciamento | documentação | edição local, sem rede/SQL | distinguir momento da prova e aceite | registrado, sem caixa marcada | cabeçalhos de `STATES.md`, trilha e blocos |
| Preservação | arquivos | `git diff --check -- STATES.md` | sem erro de whitespace | passou | verificação local desta unidade |

- Efeitos possíveis sem confirmação: nenhum externo.
- Processo/campanha próprios ativos: nenhum.
- Testes: não executados, pois não houve alteração de código, contrato executável ou schema.
- Aceites fechados: nenhum nesta unidade.

## Retomada imediata — até três ações

1. Obter de Segurança/Operações o atestado V2-041/G01 de rotação/invalidação e continuidade do writer legado.
2. Preparar a primeira leitura real controlada em sombra somente com autorização vigente do canal/runtime, entidade, janela, limites e alvo; parar se contrato/limite/terminal não verificável.
3. Na campanha correspondente, qualificar P17/P20 e gates dependentes; se usar o banco V1, obter antes autorização read-only delimitada e provar comparabilidade.

Bloqueio externo: V2-041/G01 depende de Segurança/Operações; contrato/identidade/paginação e referência tarifária dependem do fornecedor e owner de tarifas; oráculo/janela dependem do responsável de negócio/dados. Condição de parada: falha de contrato, limite, terminalidade ou falta de autorização. Conclusão: gates reais aprovados por entidade com evidência, antes do uso autoritativo.
