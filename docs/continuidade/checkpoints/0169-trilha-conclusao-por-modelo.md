# 0169 — Trilha de conclusão por modelo, manutenção documental

## Identificação e objetivo

- Data: 2026-09-19; consolidação documental após 15:51 UTC.
- Estado da frente: `ACEITO_NO_ESCOPO` documental. Campanha técnica A–N continua `EM_EXECUCAO`.
- Anterior: [0168](0168-recomposicao-e-falhas-provadas-campanha-em-correcao.md), SHA-256 `7b5614655bdd48cc6127d63b103050687ab0a47210caf8198297cbb43cdde596` conferido nesta rodada.
- Objetivo do usuário: criar na raiz uma trilha para concluir o projeto, distribuída entre GPTs e níveis para economizar ao máximo, usando STATES.
- Entrega: [TRILHA_CONCLUSAO_POR_MODELO.md](../../../TRILHA_CONCLUSAO_POR_MODELO.md), ECO-00–26, com modelos, níveis, pré-condições, evidências de saída, cobertura dos itens abertos e prompts.

## Autorização e limites

- Pedido efetivo de 19/09/2026: “colocar na raiz uma trilha para conclusao do projeto separado em tipo de GPT diferente para economia”. Cobre criação e sincronização documental; não executa a trilha.
- Alvos desta manutenção: arquivo novo na raiz, notas aditivas em STATES, trilha anterior e RETOMADA, mais este checkpoint.
- Limite: documentação local, leitura de recibos sanitizados e documentação oficial OpenAI; sem subagentes, fonte de negócio, credencial, Java, banco, migration, CI, deploy, commit, push ou cutover.
- Orçamento/ledger físico: não aplicável. Nenhuma reserva física, vigência ou autorização da campanha anterior foi renovada. Nenhuma aprovação nova está pendente para a entrega documental solicitada.

## Alterações e decisões

- Inventário anterior: `target/trilha-economia-20260919-01/before.json`, `git-status-before.txt` e snapshots `before/`. O worktree já continha alterações extensas e oito exclusões de arquivos Java/testes; foram preservadas.
- Acrescentado o roteiro na raiz; STATES registra a manutenção e as lacunas de validação; trilha anterior aponta ao novo roteiro; RETOMADA distingue manutenção documental de retomada técnica. O conteúdo anterior dos três documentos existentes foi preservado byte a byte como sufixo.
- Nenhum fonte, configuração, schema, manifest histórico, ledger, checkbox ou critério de aceite funcional foi alterado. 39/45 e 67/115 conservados.
- Terra Medium/High executa a maior parte do roteiro; Sol High trata diagnóstico/integração; Luna Low/Medium consolida; Astra High fica com decisões críticas delimitadas e xhigh é escalada condicional. Ultra não integra o fluxo econômico sem subagentes.
- Modelos, esforço e taxas de créditos foram consultados na documentação oficial OpenAI, com links e data no roteiro. A distribuição por bloco é recomendação de engenharia; não promete custo nem percentual de economia.
- Abordagem recusada: tratar os 48 checkboxes abertos como 48 implementações novas ou usar Astra em todo o trabalho. A construção local posterior existe; os gates nominais/materiais continuam separados.

## Execução e evidência

| Passo | Camada e limite | Resultado observado | Evidência |
| --- | --- | --- | --- |
| Ler tentativa 05 e relatórios | Leitura local de recibos, sem repetir teste | OBSERVED, exit1, rollbackConfirmed=true; quatro IT, duas falhas e dois erros; revisão testada anterior às sete etapas atuais | `target/macrobloco-campanhas-integrais-20260915-01/sequence-campaign-sql-05/result.json`, XMLs e WORKLOG da campanha |
| Conferir roteiro | Documental, sem executar os blocos | PASS: 27 blocos únicos, cobertura exata de 48 itens abertos, dez links locais, UTF-8 válido e 115 checkboxes/67 concluídos preservados | `target/trilha-economia-20260919-01/document-validation.json` |
| Conferir diff da rodada | Comparação aos snapshots e `git diff --check` dos documentos existentes | Apenas notas aditivas e novo roteiro; bytes anteriores preservados; zero erro de whitespace. Aviso Git de CRLF/LF não foi usado para normalizar o histórico | Snapshots `before/` e inventário da rodada |
| `pwsh -NoProfile -File scripts/validation/Test-Gpt56ChatTrail.ps1` | Validador documental existente, antes e depois | FAIL/exit1 em ambas: `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md` | `trail-before.log` e `trail-after.log` na rodada |
| `pwsh -NoProfile -File scripts/security/Invoke-OfflineSecretScan.ps1` | Scanner integral offline | FAIL/exit1: 3.542 candidatos, 3.533 textos, um binário verificado, oito MISSING_CANDIDATE; todos já estavam deletados no status inicial. Zero oversized/unexpected_non_text; nenhum outro tipo de finding | `secret-scan.log` e `git-status-before.txt` na rodada |

- Lacunas: a sucessão documental preexistente precisa ser reconciliada na campanha competente; o scanner integral precisa da decisão sobre as exclusões preexistentes, sem restaurá-las ou modificar o índice por inferência. Não alterar hashes históricos para declarar PASS. As adições documentais desta rodada também devem entrar na próxima sucessão legítima.
- Java/build/formatter/análise estática/schema não foram repetidos: não houve mudança de runtime, teste, configuração ou SQL. Não há nova prova funcional.
- Processo próprio de validação desta manutenção: scanner integral terminou com resultado conhecido; nenhuma campanha física foi iniciada. O estado atual dos processos da campanha anterior deve ser conferido em ECO-00.
- Efeitos possíveis sem confirmação desta rodada: nenhum. Nenhum aceite funcional fechado; apenas a entrega documental pedida.

## Retomada imediata — até três ações

| Ordem | Ação | Pré-condição | Prova esperada / alternativa |
| --- | --- | --- | --- |
| 1 | ECO-00, Terra Medium: reconciliar tentativa05, autoria atual, processos próprios e sucessão | Pedido de executar a trilha e leitura dos critérios originais; nenhuma renovação implícita de limites | Mapa de revisões/resultados e próximo teste elegível; permanecer em leitura se existir resultado desconhecido |
| 2 | ECO-01, Sol High, somente se a causa ainda exigir diagnóstico; depois ECO-02, Terra High | Resultado conhecido e autorização aplicável a cada efeito | Correção delimitada e provas da revisão atual; sem reexecutar testes históricos sem impacto |
| 3 | Prosseguir ECO-03–07; parcelas ECO-08–26 conforme inputs próprios | Gates A–N e G01–G08 pertinentes | Entrega local selada e, em etapas futuras, aceites reais; input externo ausente bloqueia só sua parcela |

Condição de conclusão desta manutenção: roteiro entregue, cobertura conferida e limitações registradas. Não confundir com término da campanha integral, prontidão produtiva ou conclusão do projeto.
