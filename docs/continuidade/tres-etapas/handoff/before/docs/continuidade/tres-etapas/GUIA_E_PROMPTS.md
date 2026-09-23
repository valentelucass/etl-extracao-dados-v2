# Finalização em três etapas — guia dos próximos chats

Diretriz adotada pelo usuário em 22/09/2026. Este guia organiza o trabalho restante;
STATES.md conserva os critérios e aceites. Não cria autorização de operação.
Estado inicial: etapa 1 é a próxima; etapas 2 e 3 estão preparadas para geração de
prompt quando solicitadas, sem execução ou aceite presumido.

## Etapas fixas

| Etapa | Intervalo | Entrega integrada |
| --- | --- | --- |
| 1 — Condições para a campanha | P09–P15 | Segurança/acesso, governança e baseline, ambiente, contratos das fontes, identidade e referências efetivamente usadas. Consolidar requisitos operacionais de uma vez. |
| 2 — Qualificação integrada | P16–P29 | Adequar e provar verticais, histórico, relações, dimensões, ausência aplicável, fatos e saídas; medir escala, executar E2E/recuperação e qualificar o RC. |
| 3 — Entrada em operação | P30–P33 | Preparar a unidade de corte, executar ensaio e recuperação, realizar o corte autorizado e cumprir observação/retirada do legado. |

São três etapas de trabalho, não três novos checkboxes nem promessa de duração.
Observação, janelas operacionais e aprovações mantêm os requisitos originais.
Um bloqueio de uma fonte não paralisa as frentes independentes autorizadas.

## Como responder quando o usuário pedir um prompt

1. Ler AGENTS.md, STATES.md, ../CONTEXTO_GLOBAL.md, RETOMADA.md, o protocolo de
   continuidade e este guia. Usar a matriz já existente, sem refazer a auditoria
   inteira: [matriz P09–P33](../qualificacao-p07-p33/matriz.json).
2. Identificar a etapa solicitada. “Etapa 2” é sempre P16–P29; “etapa 3” é sempre
   P30–P33, não P02/P03 da trilha. Se o pedido for apenas “próximo prompt”, usar a
   primeira etapa ainda não concluída no estado vigente, incluindo sua retomada.
3. Conferir o último checkpoint e os recibos referenciados apenas quanto ao delta
   relevante. Na ausência de alteração causal, reutilizar P07/P08 e os testes
   compatíveis. Ledgers CLOSED nunca são reutilizados ou renovados pelo prompt.
4. Entregar na conversa um único bloco `text`, completo e pronto para copiar,
   reunindo o contrato comum abaixo e a etapa solicitada. Preencher os caminhos,
   IDs, estado, evidências e limites disponíveis; nenhum placeholder genérico.
5. Informar o GPT/esforço em uma linha, usando a preferência explícita do usuário
   ou a recomendação da trilha aplicável ao conjunto. Não criar vários chats ou
   agentes por tarefa; não prometer consumo ou duração com base no saldo exibido.
6. Preparar somente o prompt, salvo pedido explícito de execução. Não gerar
   automaticamente o prompt da etapa seguinte no fim de uma execução.

Se um requisito conhecido estiver ausente, incorporá-lo uma única vez no prompt
com P/critério, artefato, responsável e efeito bloqueado. Não pedir novamente
“novas evidências”, repetir a mesma busca ou entregar um prompt que só refaça a
recusa conhecida. Incluir todo trabalho independente executável da mesma etapa.
Se não houver nenhum, apresentar o impedimento consolidado sem simular execução.

## Contrato comum para o prompt completo

```text
Projeto: ETL Data Export V2.
Workspace: C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2.
Execute a etapa solicitada como um macrobloco integrado. Uma entrada minha,
atualizações breves sem resposta obrigatória e uma entrega final consolidada.
Não encerre por tarefa pequena, não peça “continue” e não devolva somente plano
enquanto houver implementação/correção/validação elegível no escopo.

Leia os documentos obrigatórios e o guia de três etapas. Use os critérios
canônicos de STATES e os componentes/evidências já registrados na matriz.
O estado é o vigente no início da execução, não a memória do chat anterior.
Confronte apenas o delta: preserve trabalho preexistente, relatórios falhos,
checkpoints e manifests históricos. Corrija defeitos demonstrados e avance.

Respeite dependências por fonte/entidade/saída. Falha ou input ausente de uma
fatia não paralisa as demais independentes. Não invente contrato, identidade,
regra, aprovação, oráculo, paridade real ou recuperação a partir de fixtures.
Autorização já vigente e exata não exige nova pergunta; autorização ausente
não nasce do objetivo de finalizar. G01 continua bloqueando uso externo de
credenciais até sua evidência suficiente. Não testar tokens para substituí-la.

Antes de cada efeito físico, conferir alvo, alcance e autorização documentados;
registrar ordem finita própria e reserva quando exigida. Não reutilizar ledger
fechado nem aumentar limites só para obter PASS. SQL local segue o alvo exato
autorizado e rollback. Produção, credenciais, infraestrutura, agendamento,
publicação, COMMIT material, deploy, corte e retirada exigem seu alcance próprio.

Comece por testes dirigidos às correções e consumidores afetados. Agrupe as
mudanças antes da regressão aplicável. Preserve assertions, cobertura, massas,
skips históricos e limites. Requalifique P07/P08 apenas no impacto demonstrado
sobre código, entradas, ambiente ou contrato; Markdown não exige Maven/SQL/JAR.
Não crie novos validadores/relatórios repetitivos sem necessidade demonstrada.

Consolide requisitos externos conhecidos de uma vez. Não repita investigação
de bloqueio inalterado. Falha técnica local exige diagnóstico e correção no
mesmo macrobloco, não passagem para outro chat. Ao esgotar o trabalho elegível,
registre exatamente o critério externo pendente, seu responsável e desbloqueio.

Ao terminar, atualizar STATES primeiro, depois trilha/validações; salvar e
conferir checkpoint e atualizar RETOMADA. Registrar o estado da etapa inteira,
seus critérios aprovados, pendências e provas por camada. Não alterar os
manifestos históricos para ocultar drift. Entregar código/diff, testes, pacote
quando aplicável e recibo final; não chamar preparação de implementação.
Não declarar etapa ou projeto concluído sem todos os critérios correspondentes.
```

O gerador deve inserir no bloco, antes da execução, o estado real e as referências
atuais, a etapa abaixo e os limites numéricos já sustentados por seus executores.
Se ainda não existe ordem física adequada, o executor a prepara antes dos efeitos
dentro da autorização existente; não copiar o saldo de uma ordem encerrada.

## Conteúdo obrigatório da etapa 1 — P09–P15

Reunir condições por classe de credencial, ambiente, fonte e família de referência.
P09: rotação/invalidação e continuidade reais. P10: governança remota autorizada.
P11: baseline e aceite de segurança. P12: ambiente e fundação operacional com
provas materiais próprias. P13: contratos válidos por fonte. P14: identidade e
semântica ainda abertas. P15: referências oficiais, vigência e ratificação.
Usar os intakes/procedimentos existentes; não refazer o preparo já comprovado.
Entregar um conjunto consolidado que indique quais fatias liberam a etapa 2 e
quais permanecem impedidas. Nenhuma autorização externa é inferida deste guia.

## Conteúdo obrigatório da etapa 2 — P16–P29

O objetivo é qualificar a implementação existente numa campanha integrada,
corrigindo as divergências reais até os critérios aplicáveis serem atendidos.

- P16–P20: onze verticais, caracterização/oráculo, bootstrap e histórico,
  relações MC/CF e paridade core, seguindo contratos e referências de cada fatia.
- P21–P23: seis dimensões e as responsabilidades de ausência/sweep. Habilitação
  exige aplicabilidade e completude comprovadas; não ativar todos por conveniência.
- P24–P27: cinco fatos, 19 contratos SQL, paridade analítica e escala medida.
  SQL-10 mantém aceite interno próprio; as demais saídas usam seus consumidores.
- P28–P29: E2E, recuperação/durabilidade e desempenho exigidos, seguidos de RC,
  proveniência/SBOM/smoke e segurança/SAST/licenças/SOs no mesmo artefato.

Conferir as saídas da etapa 1 somente nas dependências usadas. Não exigir que toda
a etapa 1 esteja fechada para executar uma frente independente já habilitada.
Cotações não espera MC/CF; Contas a Pagar não espera Faturas/Fretes; fonte não
espera a dimensão que será derivada dela. Fatos dependem das entradas que usam.
Provas sintéticas não substituem fornecedor, oráculo, volume representativo,
COMMIT/crash/restore ou aceite nominal. Preservar todas as lacunas técnicas reais,
inclusive SAST integral: dez regras PMD não bastam para fechá-lo.

Saída: matriz da etapa 2 atualizada por critério, correções e provas executadas,
RC qualificado no alcance efetivo, pendências consolidadas e condições de entrada
da etapa 3. Atualizar a mesma etapa em retomadas; não criar um chat por entidade.

## Conteúdo obrigatório da etapa 3 — P30–P33

Usar o RC e os aceites reais da etapa 2, reconferindo somente o delta relevante.
P30: unidade DATABASE_WIDE/CUTOVER-DB-01, Tcut, writer único, fences, consumidores,
PNR e recuperação. P31: G06, ensaio isolado material e duas recuperações com
RTO/RPO medidos. P32: G07, DoD e ensaio aceitos, corte efetivamente autorizado.
P33: G08, período de observação, consumidores, retenção/destinos e retirada
autorizada do legado. Simulação de PNR não é PNR produtivo nem aprovação de corte.

O prompt deve identificar autorização, alvo, janela, responsável e recuperação
por efeito antes de sua execução. A adoção das três etapas não concede nenhum
desses efeitos. Preparar a execução verificável e avançar somente no alcance
existente, sem modificar produção ou desligar o legado por inferência.
Observação obrigatória pode atravessar sessões; manter a etapa 3 e seu checkpoint,
sem simular passagem do tempo ou prometer finalização em um único chat.

Saída: evidência real de ensaio, corte, observação e retirada quando executados;
ou condição operacional exata ainda faltante. Projeto completo somente com os
critérios originais de P30–P33 e dos predecessores integralmente comprovados.

## Pedidos curtos que funcionam em outros chats

```text
Gere o prompt completo da etapa 2 de finalização, conforme STATES.md,
TRILHA_CONCLUSAO_POR_MODELO.md e docs/continuidade/tres-etapas/GUIA_E_PROMPTS.md.
Quero P16–P29 como macrobloco integrado. Apenas gere o prompt; não execute.
```

```text
Gere o prompt completo da etapa 3 de finalização, conforme STATES.md,
TRILHA_CONCLUSAO_POR_MODELO.md e docs/continuidade/tres-etapas/GUIA_E_PROMPTS.md.
Quero P30–P33 como macrobloco integrado. Apenas gere o prompt; não execute.
```
