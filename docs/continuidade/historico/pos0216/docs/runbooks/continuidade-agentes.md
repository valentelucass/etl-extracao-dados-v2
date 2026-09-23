# Continuidade de trabalho dos agentes

Este protocolo reduz a dependência da memória do chat. Não garante ausência de
erro: exige que decisões e resultados possam ser conferidos em arquivos e
evidências independentes. Não concede acesso, orçamento ou aceite.

## Ordem de leitura ao iniciar ou retomar

1. Mensagem efetiva mais recente do usuário e autorizações anteriores aplicáveis.
2. `AGENTS.md`, `STATES.md` e `../CONTEXTO_GLOBAL.md` do projeto.
3. [RETOMADA.md](../continuidade/RETOMADA.md): trabalho atual, último checkpoint,
   impedimentos e até três próximas ações.
4. Prompt/runbook adotado e critérios originais das tarefas que serão tocadas.
5. Evidências apenas da frente escolhida: manifests, contratos, recibos e ledger.

O resumo automático serve como índice. Confrontar suas afirmações com os arquivos;
não copiar para o estado um PASS ou uma autorização que só apareça no resumo.
Havendo conflito, registrar as versões e resolver antes do efeito dependente.
Não reabrir todo o histórico em cada retomada: buscar por ID com `rg` e seguir
as referências da frente. Históricos longos ficam fora do ponto de retomada.

## Papéis dos arquivos

| Registro | Conteúdo que pertence nele |
| --- | --- |
| `STATES.md` | Critérios canônicos, dependências, aceites e progresso comprovado |
| Trilha | Índice das rotas; deriva do STATES, sem contador paralelo |
| Prompt adotado | Objetivo, inclusões, exclusões, limites e condições de parada |
| `RETOMADA.md` | Índice curto do trabalho corrente; recomenda-se até 120 linhas |
| Checkpoint numerado | Fotografia imutável de uma unidade de trabalho, pelo modelo |
| Manifest/receipt | Proveniência, hashes, camada exercitada, limites e resultados |
| Ledger físico | Reserva antes do efeito e campanhas; nunca um diário substituto |
| ADR/contrato | Decisão técnica, alternativas rejeitadas e semântica que não se infere |

Não guardar tokens, credenciais, payloads, IDs de negócio ou dados pessoais nesses
resumos. Referenciar artefatos privados sanitizados. Hash de arquivo de evidência
é integridade do artefato, não hash de identidade de negócio nem prova de autoria.

## Vocabulário que evita um falso concluído

| Estado | Condição para usar |
| --- | --- |
| `PROPOSTO` | Escopo preparado, sem adoção/execução presumida |
| `PRONTO_PARA_EXECUTAR` | Dependências e autorização verificadas; ainda sem efeito |
| `EM_EXECUCAO` | Passo iniciado, referência da campanha/processo próprio registrada |
| `RESULTADO_DESCONHECIDO` | Efeito possível sem confirmação; exige reconciliação |
| `IMPLEMENTADO_NAO_QUALIFICADO` | Código existe, provas necessárias ainda faltam |
| `TESTADO_NA_CAMADA` | Testes executados e camada nomeada; não é aceite agregado |
| `ACEITO_NO_ESCOPO` | Todos os critérios originais da tarefa conferem com evidência |
| `BLOQUEADO_POR_INPUT` | Falta concreta, origem do requisito e desbloqueio identificados |

Uma frente pode ter código implementado e estar bloqueada para qualificação física.
Registrar as duas coisas. Não usar uma única palavra "concluído" para esconder
que falta SQL, JAR, oráculo real ou aprovação exigida pelo critério.

## Ciclo de trabalho e checkpoint

Antes do primeiro efeito, congelar o inventário inicial das alterações existentes
e preparar o diretório privado da rodada. Para cada unidade coerente:

1. Escrever a ação e o resultado esperado; apontar o critério original e a prova
   que distinguirá sucesso, recusa correta e falha do controlador.
2. Conferir autorização, alvo, hashes, limites, saldo e vigência. Separar a
   autorização do usuário de uma hipótese técnica; não repetir a pergunta se
   a ação já estiver exatamente coberta.
3. Se houver efeito físico, reservar antes no ledger do bloco/campanha. Registrar
   caminho do request congelado e meio de observar o resultado por conta própria.
4. Executar. Salvar comando sanitizado, exit esperado/observado, camada e recibo.
   Registrar testes falhos sem convertê-los em sucesso por causa do exit do wrapper.
5. Conferir estado autoritativo e critério. Escrever novo checkpoint numerado,
   conferir sua leitura e somente então atualizar o ponteiro em `RETOMADA.md`.
6. Escolher até três próximas ações. Cada uma informa pré-condição, evidência
   esperada e alternativa independente se estiver bloqueada.

Fazer isso após cada frente/campanha, mudança de decisão ou falha relevante,
antes de operação longa e antes de pausar. Uma sessão de horas deve conter
vários checkpoints; uma campanha conserva seu teto original em minutos.
Pode haver vários checkpoints do mesmo bloco, sem novos aceites artificiais.

## Recuperar quando o contexto ou a confirmação se perdeu

- Não repetir instalador, migration, seed, concessão ou RUN porque o resumo não
  contém o retorno. Estado inicial: `RESULTADO_DESCONHECIDO`.
- Conferir recibo de aplicação, catálogo, consumo, execução e publicação no
  sistema autoritativo, dentro da autorização e orçamento aplicáveis. No runtime
  deste projeto o estado durável é SQL; o manifesto de lote não o substitui.
- Processo pode continuar após a perda de contexto. Conferir apenas processos
  próprios e seu controle anterior; não usar nome `java` para encerrar terceiros.
  Um identificador de processo em evidência não vira daemon/PID file operacional.
- Se commit ocorreu, recuperar pelo caminho versionado previsto. Se não ocorreu,
  repetir apenas conforme o contrato e com reserva nova quando exigida. Saldo não
  é devolvido por tentativa falha, saída perdida ou compressão.
- Retomar a frente autorizada independente enquanto um input externo continua
  faltando. Sem input novo, não repetir sondas, análises ou mutações do mesmo hold.

## Regra para encerramento e percentual

Para cada aceite: ID canônico → requisito original → componente → camada →
evidência → verificação → limitações. Escrever "não comprovado" quando faltar
um elo. Um pai só fecha quando todo seu critério for atendido; várias rotas de
uma tarefa não são vários checkboxes. Não dividir itens para alcançar percentual.

Sincronizar STATES, trilha e validadores nessa ordem. Usar diff da rodada contra
o inventário inicial; preservar o worktree anterior. Rodar verificações adequadas
ao que mudou. Teste Java anterior continua histórico se Java não mudou; não
alegar uma nova suíte executada por uma alteração só de documentação.

## Transição documental após B55

O B55 terminou com 65/115 itens, 259 reservas e 11 campanhas fechadas. Seu
`runtime-bloco55.json`, REVIEW_05, recibo final, logs e ledgers são imutáveis.
O validator privado original também fixou o hash de todos os arquivos existentes.

O [manifesto desta manutenção](../continuidade/manifesto-pos-bloco55.json)
registra a mudança exata de quatro arquivos: AGENTS, STATES, trilha e o próprio
validator integrado. O [validator de continuidade](../../scripts/validation/Test-ContinuidadeAgentes.ps1)
confere os hashes antes/depois e os novos documentos. O B55 continua verificando
todos os outros hashes originais, inclusive Java, migrations e artefatos.
Isso não autoriza uma exceção genérica a docs ou ao próximo bloco.

Um bloco futuro deve registrar seu próprio inventário e sucessão de evidências.
Ao evoluir um documento referenciado por hash, preservar a revisão anterior e
qualificar a nova fase. Não regenerar o manifest B55 nem atualizar hashes antigos
somente para fazer um teste passar. Ausência de evidência privada permite apenas
verificação documental, nunca reconstituição fictícia do teste físico.

## Texto curto para retomar no chat

```text
Continue no mesmo objetivo, a partir de docs/continuidade/RETOMADA.md.
Leia AGENTS.md, STATES.md, ../CONTEXTO_GLOBAL.md e o protocolo de continuidade.
Confira o checkpoint e as evidências antes de agir; resumo não é autorização.
Não repita efeitos de resultado desconhecido: reconcilie o estado autoritativo.
Conclua as frentes independentes já autorizadas e registre novos checkpoints.
Não marque aceite sem atender integralmente seu critério original.
```

Esse texto retoma uma autorização existente; não adota uma proposta pendente.

## Próximo chat por macrobloco, quando solicitado

**Uma entrada e uma entrega final por prompt:** o usuário inicia o macrobloco
uma vez; o executor realiza todas as etapas internas elegíveis, sem pedir
“continue”, confirmação de rotina ou novo prompt por tarefa. Não encerrar apenas
com plano/resultado intermediário enquanto houver trabalho coberto a executar.
Checkpoints e atualizações breves de andamento não exigem resposta do usuário.
Se surgir bloqueio real, preservar limites, concluir o trabalho independente
autorizado e consolidar na entrega final o resultado, impedimento e input exato
necessário. Nunca inventar autorização ou aceite para cumprir a saída única.

Correção explícita do usuário em19/09/2026: ao pedir um novo prompt, o chat cruza
`STATES.md` e [TRILHA_CONCLUSAO_POR_MODELO.md](../../TRILHA_CONCLUSAO_POR_MODELO.md)
para selecionar um macrobloco coeso de tarefas e o GPT/nível para executá-lo no
mesmo chat. Seguir as seções11/13 da trilha. Não gerar passagem automaticamente
por tarefa nem tratar cada P como um chat obrigatório.

O executor conclui as etapas internas elegíveis do macrobloco em ordem, sem pedir
continuação entre itens já cobertos, registrando checkpoints por unidade coerente.
Ao encerrar, sincroniza STATES/trilha/verificações, salva e confere checkpoint e
só então aponta RETOMADA. Registra resultado real, pendências e ponto de parada.

Quando o usuário solicitar o próximo prompt, entregar na resposta um único bloco
`text` completo e preenchido: macrobloco, etapas/fatias e ordem, modelo/nível,
evidências, dependências, autorizações/limites, provas e critérios de saída.
O prompt não exige memória do chat anterior; os documentos operacionais e as
evidências referenciadas devem ser conferidos antes do efeito dependente.

Preparar o prompt não executa o macrobloco, cria autorização ou renova limites.
Uma entrega parcial conserva sua lacuna; um bloqueio não é promovido a aceite.
Os registros0172 e prefácios que exigiam passagem automática ficam históricos,
superados por esta correção do usuário.
