# Checkpoint 0244 — finalização em três etapas

UTC: 2026-09-22T14:01:12.801Z. Anterior: docs/continuidade/checkpoints/0243-qualificacao-local-e-sucessao-conferidas.md, SHA-256 917e206cca19ec738b24225fec2fa5b921a22086d9ffb5fe251bfff83603a5ae.

Pedido do usuário: ajustar documentação e geração dos prompts futuros das etapas 2 e 3, adotando três etapas para finalizar. Estado: diretriz ADOTADA; execução das etapas NÃO INICIADA por esta manutenção. Etapa1=P09–P15, etapa2=P16–P29, etapa3=P30–P33. Guia: docs/continuidade/tres-etapas/GUIA_E_PROMPTS.md.

STATES atualizado antes da trilha; guia contém contrato comum, escopo de cada etapa e pedidos curtos para os chats futuros. O gerador entrega um prompt completo da etapa solicitada, sem executar por inferência, fragmentar por tarefa ou exigir a memória do chat anterior. Bloqueios por fatia, limites, critérios e autorizações continuam preservados. P07/P08 são reutilizáveis sem delta causal; nenhum percentual/aceite novo.

Alvo: quatro arquivos existentes (STATES, trilha, RETOMADA e leitor de sucessão), guia novo e este checkpoint. Runtime/Java/SQL/pacote/credenciais/produção intocados. O leitor recebe somente uma sucessão documental exata com snapshots, conjunto público fechado e predecessor imutável. Before privado: target/tres-etapas-20260922-01/before.json; snapshots públicos no catálogo desta manutenção. Rollback documental exige restaurar o conjunto coerente a partir desses arquivos, preservando versões, sem edição dos manifests históricos.

Provas físicas anteriores: recibo target/qualificacao-p07-p33-20260922-01/physical/closed-receipt.json, SHA-256 876b6977fb37a9dad10544311a0362f3a84790b011f3a2d5bbbb1d6822f9e863; ledger CLOSED, sem saldo reutilizável. Validação desta manutenção: UTF-8/links/diff/contadores, contraprovas de sucessão e trilha; resultados somente quando executados em target/tres-etapas-20260922-01/closed-receipt.json. Não alegar nova suíte Java/SQL ou operação. Falha local de validação deve ser corrigida nesta manutenção com evidência preservada.

Próximas ações: (1) conferir o recibo documental e, se ausente, concluir as verificações previstas; (2) quando solicitado, gerar o prompt completo da etapa1 ou da etapa nominalmente pedida usando o estado vigente; (3) após sua execução e atualização de STATES, gerar o prompt da etapa2/3 somente quando o usuário pedir. A próxima etapa de execução é1; etapas2/3 não foram declaradas liberadas integralmente.
