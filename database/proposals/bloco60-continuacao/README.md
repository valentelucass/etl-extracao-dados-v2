# B60 — continuação da correção dentro do mesmo orçamento

A instrução efetiva de 10/09/2026 após apresentação do pacote corretivo determinou
continuar e solucionar mesmo a pendência. A primeira tentativa corretiva encontrou
erro de compilação SQL134: os perfis before/active declaravam cinco variáveis no
mesmo lote. O estado autoritativo confirmou UNACTIVATED/SERVICE19 e preservação
integral do multiconjunto histórico. Nenhum grant, política ou escopo foi ativado.

Esta revisão insere GO antes do perfil ativo, separando o escopo das variáveis
sem separar a conexão ou a transação. ScriptDom160 e declaração por lote reproduzem
a falha anterior e aprovam a correção. Ativação continua atômica, com um COMMIT;
qualquer falha encerra sqlcmd e exige a recuperação já prevista.

Predecessor: pacote cc4f84cb…a167 e seu ledger encerrado, preservados byte a byte.
Oito sqlcmd já foram consumidos. Esta continuação debita os mesmos tetos globais:
80 JVMs,240 sqlcmd (232 ordinários/8 escrow),400 HTTP; deadline original imutável
**10/09/2026 04:40:10.0501172 UTC**. Novo ledger de revisão não renova tempo nem saldo.
Bloco60ContinuationBudget rejeita a soma que exceder qualquer teto global aplicável.
Não há fonte real, ampliação de permissões, DDL ou renovação de contas/validade.

JAR/bundles,74 requests,oráculos,perfis e recuperação continuam os bytes revisados
do pacote corretivo. As mesmas requests são seguras porque nenhuma JVM foi iniciada
na tentativa anterior; o preflight de colisões será reexecutado.
O controlador usa database/proposals/bloco60-correcao como working directory SQL,
substituindo exclusivamente o arquivo de ativação por activate.sql desta pasta.

O pacote da continuação prende arquivos, ledger, readback UNACTIVATED e perfil
restaurado anterior. O resultado UNKNOWN conservador do submit foi resolvido pelo
readback autoritativo de ausência de ativação; o ledger histórico não é reescrito.
