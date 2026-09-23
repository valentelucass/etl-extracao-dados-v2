# 0040 — correção dos gates globais testada localmente

Objetivo mantido: corrigir A01/A02/A03 antes de avançar. Estado: TESTADO_NA_CAMADA.
Antecessor: [0039](0039-correcao-gates-globais-adocao.md), preservado integralmente.
Sem novo bloco funcional, checkbox ou aceite físico.

O gate progressivo agora aceita exatamente as migrations V001–V024 existentes.
O catálogo de cutover deriva a fundação versão 5/41 triplets e mantém
responsabilidades, DAG e fences intactos. O bootstrap foi ligado aos hashes
atuais de schema e cutover, preservando os outros 14 bindings e os bloqueios.

Provas executadas em target/correcao-gates-pos-b60/:

| Camada | Prova | Resultado observado |
|---|---|---|
| Regressão RED | red-02, dois testes de contrato/classe | 9 testes, 3 falhas correspondentes a A01/A02/A03, zero erros/skips |
| Java offline | verify-01, JDK 17 e heap 512 MiB | 1393 testes, zero falhas/erros, quatro skips condicionais; build/estilo/arquitetura/cobertura PASS |
| Gates | gates-01/results.json | 48 validators globais + 4 de manutenção/B60: 52 PASS |
| Medição sintética | verify-01/target/v2-050-measurement | Seis cenários PROVEN_SYNTHETICALLY; sem benchmark SQL/fornecedor |
| Integridade histórica | Test-GlobalGateMaintenance, modo privado | 1725 arquivos iniciais e 2094 hashes do recibo B60 conferidos por suas revisões |
| Contraprovas | manutenção, guards B60 e pacote | Oito guards de sucessão, 13 guards B60 e guards do pacote recusados corretamente |

Red-01 ficou interrompido no formatter e permanece separado do RED funcional.
O fechamento acrescenta este checkpoint, atualiza STATES/trilha/RETOMADA e o
manifesto da sucessão. Os gates que dependem desses documentos serão repetidos
em gates-02; diff e recibo ficam em review/ e final/. Só final/receipt.json
aprovado e íntegro encerra a revisão final, sem presumir seu resultado aqui.

Quatorze deltas explícitos; 1711 arquivos anteriores preservados. Os snapshots
mantêm as versões originais, sem reescrever receipts/manifests B55–B60.
O pacote B60 foi alterado exclusivamente no hash do validator local, passando a
a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e.
Seu antecessor permanece preservado; validade, budget, SQL, requests e ausência
de aprovação não mudaram. Não houve operação física, fonte, segredo, instalação,
agendamento, deploy, commit/push ou alteração de código runtime/migrations/grants.

Próximas ações:
1. Conferir gates-02, diff/checks e final/receipt.json desta manutenção.
2. Preservar auditoria anterior e snapshots, inclusive os testes que falharam.
3. Retomar a próxima frente autorizada após o fechamento; não presumir aprovação física B60.

Progresso: 67/115 (58,26%), 48 pendentes, 191 rotas abertas, zero AGORA.
