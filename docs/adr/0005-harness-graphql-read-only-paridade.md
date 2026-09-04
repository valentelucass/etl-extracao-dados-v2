# ADR 0005 — Harness GraphQL transitório e somente leitura para paridade

- Status: Aceito como mecanismo transitório de teste; execução externa suspensa por V2-041
- Data: 2026-08-25
- Ratificação: 2026-08-30

## Contexto

Data Export precisa ser comparado ao contrato GraphQL vigente sem reutilizar comandos operacionais do V1. A prova deve ser estritamente read-only, test-only e sanitizada. Usuários ainda depende de GraphQL, e Coletas/Fretes possuem campos ou relações que exigem sidecar até equivalência comprovada.

## Decisão

O harness GraphQL permanece somente em escopo de testes e usa queries estáticas/minimizadas.

- Não chama CLI, banco, persistência, watermark, expurgo, mutation ou endpoint de escrita do V1.
- O harness Java permanece bloqueado pela allowlist atual: liberar V2-041 não basta. Execução externa exige autorização própria ou alteração formal de `AGENTS.md`, além do perfil `contract-tests`, `-Dcontract.tests.enabled=true`, configuração `CONTRACT_*` completa, alvo nominalmente autorizado e janela/teto aprovados. Configuração parcial falha antes da primeira requisição. Enquanto isso, somente os scripts `curl` nominalmente allowlisted podem ser considerados para uma futura rodada autorizada.
- Não há fallback ou reutilização implícita de `API_*`. URL/token não são impressos, persistidos ou colocados em argumento de usuário.
- `ContractRemoteExecution` compartilha stop-token e orçamento entre Data Export e GraphQL por entidade. A primeira resposta `429` ou teto atingido encerra a rodada.
- O único artefato permitido é `target/contract-evidence/<run-id>/summary.json`, contendo totais, tipos, presença, nulos, formatos e classificações sanitizadas; nenhum payload, ID de negócio, cursor, URL ou hash de identificador.
- O template 4924 pode continuar no harness como amostra auxiliar mínima. Isso não substitui a vertical Faturas por Cliente de V2-030 nem autoriza seu runtime; apenas caracteriza vínculos financeiros sob gate próprio.
- Usuários pede somente `id/name` no selection set vigente. `updatedAt` não é solicitado/provado e não pode preencher `origem_atualizado_em` por inferência.
- O harness é removido quando V2-040 confirmar que nenhuma vertical ou reconciliação depende dele; dependências por campo são fechadas em V2-024/V2-025d e nas tarefas das verticais.

## Consequências

- A ferramenta oferece paridade reproduzível sem poder operacional do legado.
- Resultado remoto nunca autoriza escrita, storage, watermark, sweep, relação ou cutover.
- A ausência de fuso declarado, ordem/cursor, contagem oficial ou autorização mantém o gate aberto mesmo com testes sintéticos verdes.
- V2-017 apenas inventaria seus selection sets offline; nenhuma chamada externa ocorre neste bloco.
