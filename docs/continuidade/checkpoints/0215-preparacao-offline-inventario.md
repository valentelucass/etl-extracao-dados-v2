# 0215 — preparação offline: inventário e primeira validação

Data: 2026-09-21. Estado: EM_EXECUCAO, sem aceite funcional.
Anterior: `0214-p09-v2-041-offline.md`, SHA-256
`fcb7811b6c296657fd6ae6e851c18a32772d2fe30e1e0a36f262aa177a7a6336`.

Pedido efetivo: preparar integralmente P10/P11/P12/P14/P15/P21 no mesmo chat,
sem subagentes, rede, segredos, banco, publicação ou efeitos operacionais.
Critérios: STATES e plano de preparação. P09 permanece fora da reavaliação.
Alvo: workspace e testes sintéticos. Sem reserva ou saldo físico utilizado.
Recuperação: conservar snapshots e recibos; não restaurar o worktree inteiro.

Inventário inicial, autorização e recibos:
`target/preparacao-offline-p10-p21-20260921-01/`.
3.674 arquivos não sensíveis inventariados; quatro documentos a editar copiados
em `before/`. HEAD local observado; zero remote configurado, sem ler URLs.

Passaram: política/implementação de vulnerabilidades (33 casos, 30 recusas),
lifecycle/schema/contrato progressivo, pacote Windows estático, referências,
dimensão Usuários, identidades 10633/8636/4924/6392, Raster e 12 contraprovas,
preparação da trilha. Nenhum destes resultados é CI, prova física ou aceite nominal.
O catálogo histórico de frota falhou no pin do mapper de Manifestos. A causa é
drift de revisão; manifesto/decisão antigos não foram regravados.
Erros auxiliares de sintaxe PowerShell e glob Windows ocorreram antes de efeitos;
a enumeração AST foi corrigida. Ausência de `.mvn/maven.config` não foi suprida.

Java dirigido em cópia isolada está em execução, JDK17, Maven offline e settings
vazios próprios; consultar `java.result.json` antes de repetir. Sem integração SQL.
Não há efeito externo desconhecido introduzido nesta rodada.

Próximas ações:
1. Reconciliar Java e comprovar a revisão corrente sem alterar pins históricos.
2. Consolidar matriz por gate, owner-papel, dependência e requisito sanitizado.
3. Validar a entrega e sincronizar STATES, trilha/matriz, checkpoint e RETOMADA.
