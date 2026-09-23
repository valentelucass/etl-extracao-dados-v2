# Correção dos fingerprints DQ do Bloco 55

PREPARED_REQUIRES_ADDITIONAL_SCOPE_DECISION. Não executado.

O gerador original contou 30 bytes para laboratory-owner (correto: 32) e 36 para
bloco55-preserve-v1 (correto: 38). O consumidor SQL bloqueou as três publicações.
As políticas originais são imutáveis; não é permitido corrigir sua identidade,
apagar suas linhas ou enfraquecer a verificação do consumidor.

Este pacote preserva e revoga as três definições inválidas e acrescenta três
revisões v2, com os mesmos quatro checks estritos por workload. São 15 linhas
adicionais: três políticas e doze checks. Permanecem três políticas ativas; passam
a existir seis definições B55 no histórico, três revogadas. Não há grant, scope,
tarifa, conta, validade, fonte, schema ou autorização produtiva adicional.

A seção 3 limita a três políticas sintéticas. A interpretação conservadora trata
as três definições extras como delta fora desse teto, ainda que substituam as
anteriores. O pacote está separado para decisão, sem reduzir o restante A–J.

Após adoção: reservar no ledger; qualificar apply.sql em rollback; verificar
hashes canônicos contra o consumidor SQL e preservação em nova conexão; aplicar
atomicamente e verificar de novo. Depois do commit, recuperação revoga somente
as revisões v2, sem reativar as inválidas nem remover dados. Requests da rodada
anterior permanecem vinculados ao material original; criar ocorrências próprias
com as referências novas. Cada tentativa adicional conserva os caps B55.

Os nomes de papéis DQ são rótulos sintéticos do laboratório, sem atestado de
governança produtiva. Nenhum aceite foi marcado por preparar este pacote.