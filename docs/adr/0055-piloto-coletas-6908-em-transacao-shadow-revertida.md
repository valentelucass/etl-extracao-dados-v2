# ADR 0055 — Piloto Coletas 6908 em transação shadow revertida

Data: 30/09/2026. Estado: decisão técnica local; execução com fonte real e SQL físico pendente de autorização e preflight novos.

## Decisão

Compor uma rota opt-in separada do `Main` operacional para uma data civil fechada de Coletas 6908. O plano fixa origem e tenant, partição, `per`, no máximo quatro páginas, até 1000 linhas físicas, até 10 MiB por resposta e prazo global de até 60 s. A fonte usa `GET_WITH_QUERY`, uma tentativa e timeout por request de até 30 s. O `Coletas6908PilotMain` valida o plano e recusa `--execute`. A única entrada física candidata é `Coletas6908PilotShadowIT`, selecionada explicitamente pelo perfil Failsafe `shadow-local-integration` e suas duas travas; ela injeta fonte sintética, sem HTTP real. Sua inclusão/compilação offline não constitui execução JDBC nem libera o piloto real.

O `DataExportPageStreamer` percorre serialmente até página vazia, dentro do teto, com `ContractRunGuard` e auditoria de conclusão. A extração captura identidades escopadas, multiplicidade e página de origem antes do mapper. Os batches de staging são de até 100 linhas e têm mapa explícito batch→página; batch não equivale a página. Teto, 429, erro, cancelamento ou prazo sem terminal interrompem a execução. O status de completude permanece `BLOCKED_NO_COMPLETENESS_PROOF`, permitindo apenas `SHADOW_UPSERT`.

O runner injeta `trial.controlPlaneDataSource()`, `trial.stagingGateway()` e `trial.promotionGateway()` na composição existente. O `RuntimeExecutionSession` obtém os permits reais após a auditoria terminal e a qualidade real antes da promoção. O trial confere a transação e a sessão compartilhadas, compara identidades/linhas físicas por raiz contra o staging e o core da mesma execução, e faz rollback explícito ao fechar. Recibo positivo só sai depois do fechamento e se chama `SIMULATED_PROMOTION_ROLLED_BACK`; fechamento incerto interrompe sem recibo positivo.

## Limites e recuperação

A comparação usa o conjunto de páginas capturadas antes do mapper. Ela não prova todas as raízes da janela, snapshot estável, filhos completos, exclusões nem paridade de campos: `fieldPresence` expected está vazio e `presenceComparedCells=0`. Uma página vazia é terminal desta travessia limitada, não um oráculo global. O rollback dos dados não garante que o contador `IDENTITY` do SQL Server permaneça igual; o readback físico futuro deve registrar esse impacto e sua recuperação. Nenhum estado produtivo, cutover, agendamento ou P agregado é autorizado por esta ADR.

Para uma execução real, Operação/Plataforma ESL e o owner do tenant devem fornecer source instance, tenant scope, dia/corte e release 6908 autenticado; o Supervisor deve aprovar alvo, impacto, recuperação e reserva física nova. Banco é o único executor SQL. Se algum guard, comparação, prazo ou fechamento falhar, o trial para e a condição física deve ser reconciliada antes de outra unidade. O piloto não usa `run` produtivo padrão e não promove aceite de P07/P08.
