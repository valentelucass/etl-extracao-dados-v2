# 0170 — Roteamento por custo total; Sol opcional

## Identificação e objetivo

- 2026-09-19; `ACEITO_NO_ESCOPO` documental. Anterior: [0169](0169-trilha-conclusao-por-modelo.md), preservado.
- Usuário questionou se Sol seria econômico frente a Astra, no contexto do pedido de máxima economia na trilha.
- Entrega revisada: [trilha na raiz](../../../TRILHA_CONCLUSAO_POR_MODELO.md). A recomendação anterior de Sol obrigatório foi substituída; não foi demonstrada vantagem de custo total de nenhum dos dois neste repositório.

## Autorização e limites

- Ajuste documental da distribuição por modelo e nível; nenhuma execução dos blocos.
- Alvos: roteiro, notas em STATES/trilha antiga/RETOMADA e este checkpoint. Sem subagentes, configuração de modelo, runtime, SQL, fonte, credenciais, deploy, commit, push ou autorização física nova.
- Orçamento/ledger físico: não aplicável. Snapshots prévios e ação registrada antes das edições em `target/trilha-economia-20260919-02/WORKLOG.md` e `before.json`.
- Recuperação: diff contra `before/`, preservando alterações concorrentes e históricos; não regravar manifests para esconder drift.

## Alterações e decisões

- Mantidos os 27 blocos e todos os aceites/dependências. Distribuição: 18 Terra, sete Astra, dois Luna; zero Sol obrigatório.
- Diagnóstico/revisão ECO-01/06/11/15: Astra Medium. Política crítica de ausência ECO-17: Astra High. ECO-12/23 continuam Astra High nos casos delimitados.
- Procedimentos prescritos ECO-10/20/24/25: Terra High; decisão inédita de segurança/recuperação exige escalada direta a Astra antes da ação dependente.
- Sol permanece opção quando consumo observado sustentar vantagem para a mesma qualidade; não é etapa obrigatória entre Terra e Astra.
- Incluída comparação pelo custo total até o aceite, considerando tentativas, contexto relido e retrabalho. Taxas oficiais consultadas em `https://learn.chatgpt.com/docs/pricing`; níveis em `https://learn.chatgpt.com/docs/models`.
- O limiar de 40% no roteiro é cálculo condicionado às taxas Standard e mesma proporção de categorias de tokens; não é benchmark nem previsão da conta do usuário. Esforço menor ou duração menor isoladamente não prova economia.

## Execução e evidência

| Verificação | Camada | Resultado | Evidência |
| --- | --- | --- | --- |
| Roteiro, cobertura e checkboxes | Documental | PASS: 27 blocos, 48 itens abertos cobertos, 115 checkboxes/67 concluídos preservados, zero bloco Sol obrigatório; links locais e UTF-8 conferidos | `target/trilha-economia-20260919-02/document-validation.json` |
| Validador histórico da trilha | Documental | FAIL/exit1: mesmo `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md` já documentado em0169 | `target/trilha-economia-20260919-02/trail-after.log` |

- Scanner integral da rodada0169 tinha oito MISSING_CANDIDATE em exclusões preexistentes; não foi repetido nesta revisão de recomendação. Não há alegação de gate integral verde.
- Nenhum build, teste Java ou banco foi executado. Nenhum aceite funcional mudou; 39/45 e67/115 permanecem. Nenhum efeito desta rodada tem resultado desconhecido.
- Checkpoint0169 e manifests/ledgers históricos preservados. Novas notas substituem apenas recomendações de modelo; estados técnicos continuam subordinados ao STATES e aos recibos da campanha.

## Retomada imediata — até três ações

1. ECO-00/Terra Medium: reconciliar a campanha quando solicitada execução; conferir revisão/recibos e autorizações originais.
2. Se faltar diagnóstico, ECO-01/Astra Medium diretamente; depois ECO-02/Terra High. Não consumir uma tentativa Sol por obrigação da trilha.
3. Registrar consumo disponível e resultado de cada bloco efetivamente executado para ajustar recomendações; não repetir trabalho concluído só para comparar modelos.

Conclusão desta unidade: recomendação revisada e conferida. Não conclui campanha, gate externo ou projeto produtivo.
