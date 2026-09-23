# Recuperação da extensão B55

Alvo único: localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado.

Antes do commit, a sessão de qualificação aplica somente V022 em transação e faz
ROLLBACK. Uma conexão nova deve reproduzir o catálogo e a contagem anteriores.
Erro de compilação encerra a conexão: sqlcmd -b e :On Error exit não continuam.

Depois do commit, nunca reaplicar V022 ou reinstalar baseline. Consultar catálogo,
recibos, grants e scopes em conexão independente. Interromper somente a campanha
própria. Preservar staging, core, auditoria, referências e todas as revisões B53/B54.
Uma correção de schema exige a próxima migration livre e nova qualificação.

Se a ativação nova precisar ser retirada, executar recovery-grants.sql revisado,
que revoga exclusivamente os sete EXECUTE novos e marca somente os seis scopes
B55 revogados incrementando suas versões. Não voltar versões, estender validade
nem retirar direitos anteriores. Não apagar a release tarifária ou publicações.

Parar em hash divergente, alvo diferente, validade vencida, saldo/prazo esgotado,
erro SQL, catálogo inesperado, grant adicional ou resíduo de transação. Nenhuma
repetição automática. Cada nova tentativa exige outra reserva no ledger B55.
