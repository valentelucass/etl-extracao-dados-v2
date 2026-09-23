# 0039 — correção dos gates encontrados na auditoria global

Manutenção local adotada pelo usuário: “corrija esses erros para depois avançarmos”.
Estado: EM_EXECUCAO. Antecessor: 0038-bloco60-fechamento-offline-aprovacao-pendente.md,
SHA-256 d884c068a95eb82bec8bd4850a8de40d2eb0662370c8d12fa7c285637f7a3ae2.

Escopo A01/A02/A03: allowlist estática V001–V024, bindings de schema/bootstrap e
catálogo de cutover. O schema atual já declara versão 5 e 41 triplets; esta
manutenção não altera migrations, grants, runtime ou permissões reais.
Testes de regressão serão acrescentados para descobrir drift na suíte Java.

Inventário e cópias de 1.725 arquivos em target/correcao-gates-pos-b60/initial-inventory.json
e initial/. Auditoria anterior preservada em target/auditoria-global-concluidos-20260910.
Snapshots canônicos das revisões anteriores em docs/continuidade/historico/gates-pos-b60/.
Recibos B55–B60, pacotes históricos e checkpoints anteriores não serão regravados.

O validator B60 receberá uma sucessão exata que verifica snapshots e novos hashes,
sem excluir diretórios. Isso exige atualizar somente seu hash no pacote preparado;
o pacote anterior será preservado e o novo continuará sem aprovação física.
Não há SQL, fonte externa, segredo, instalação, agendamento, deploy, commit/push,
novo bloco funcional, mudança de checkbox ou consumo/renovação de orçamento.

Próximas ações:
1. Executar os testes de regressão e preservar o RED dos três achados.
2. Corrigir os vínculos, conferir os gates e a sucessão de evidências.
3. Executar verify global, scanner e guards; fechar com diff e recibo próprio.

O término exige os três gates verdes, regressão e preservação verificadas.
A autorização atual é somente para esta correção; o aceite físico B60 permanece pendente.
