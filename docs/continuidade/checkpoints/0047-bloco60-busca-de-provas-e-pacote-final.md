# Checkpoint 0047 — busca de provas e pacote final B60

Data: 2026-09-10T15:06:53.1161740+00:00. Anterior: 0046-bloco60-recuperacao-e-concorrencia-pendente.md,
SHA-256 149fc96d26a46194e0302b8c1a62b9e34d7d6001bd3fa5b81172f109ee1177e3.
Objetivo: concluir a qualificação física local de Usuários e as frentes A–F
do prompt target/bloco60-local/PROMPT-ADOTADO.txt. Estado: TESTADO_NA_CAMADA;
efeitos físicos finais BLOQUEADO_POR_INPUT de janela específica.

Instrução efetiva: “procure as provas entao e conclua”. Busca, testes offline,
preparação, diff e continuidade cobertos. A aprovação original veda renovação
automática; a última janela terminou às 13:15:21.8816492 UTC. Esta revisão
não iniciou SQL, JVM física, conta, serviço, produção ou fonte externa.

Inventário inicial: target/b60-provas-finais-20260910/initial-inventory.json, 2.256 arquivos,
SHA-256 13fd1defecb916a66a994626bdcaeb6bdfafd3a4ba912f7e62ff742620f1bd2d. Cópias integrais em before/.
O recibo anterior 3105901c37258ef71eee28d48085888aafe129ef1f255af663c6e8d6add1e8b8
teve os 3.388 artefatos conferidos antes das alterações documentais.

## Resultado da busca

Rechecados 73 conjuntos de assertivas (72 originais e um cancelamento extra),
onze cadeias completas e débitos 160 SQL/77 JVM/71 HTTP. O par SQL está
PUBLISHED, com dois consumos, uma publicação e um selo. Os logs/exits das
duas JVMs e o PASS do observador simultâneo não existem nos diretórios da
campanha; a busca global de logs também não encontrou o marcador de sucesso.
As duas validações SQL finais não foram reservadas nem executadas.
Não inferir simultaneidade ou exit a partir da contagem de consumos.

Evidências novas: target/b60-provas-finais-20260910/prior-proof-integrity.json, evidence-audit.json,
package-offline-tests.json e logs package-tests-01/02. O primeiro teste
offline falhou por desserialização automática de datas no próprio teste;
corrigido com DateKind String, preservando o teste falho. Resultado final:
17 verificações de pacote/orçamento, quatro cenários de coordenação,
C# compilado e análise de três scripts PowerShell/18 SQL, zero SQL/JVM físico.
Java e migrations não mudaram; suíte histórica 1397/0/0/4 permanece histórica.

## Pacote revisável

target/b60-provas-finais-20260910/package/package.json, SHA-256 26ba79e319a5c652b968ed617fd890b760c3649f42cfcdf268edfef4bb0cba57,
83 arquivos vinculados. README explica alvo, identidades, grants, versões,
limites, provas, parada e compensação. Controlador Invoke-FinalPhysical.ps1.
Proposta: nova janela única de 60 minutos, validade absoluta até 16/09/2026;
três JVMs remanescentes, 20 SQL ordinários e até seis readbacks/compensação
de escrow, dois HTTP. Os 72 casos não serão repetidos. Pré-condição SERVICE31,
scopes14 e V024; ativação SERVICE32/scopes15/DQv8; compensação SERVICE33/scopes16.
A partição sintética nova usa início 2033-07-17T03:00:01Z, dentro da janela
anterior, preservando todas as partições antigas. IDs e chaves novos congelados.
Prontidão da barreira observada no SQL; resultados de ambas as JVMs retidos
mesmo após falha; validação adversarial envolvida em ROLLBACK explícito.

Nenhum novo aceite ou checkbox. Recuperação anterior SERVICE31/scopes14,
sem grants temporários, V024/históricos preservados; nenhum efeito novo incerto.
Os snapshots exatos dos quatro documentos alterados e o manifesto sucessor
mantêm os recibos anteriores verificáveis. Revisão final e recibo em target/b60-provas-finais-20260910/final/.

## Retomada — até três ações

1. Obter do usuário a aprovação do pacote acima e de sua única janela nova;
   requisito da seção 4 do prompt e da proibição expressa de renovar vigência.
2. Conferir hashes e executar pelo UAC normal no alvo exato, com três JVMs
   no máximo; readbacks e compensação pertencem ao mesmo pacote/ledger.
3. Conferir concorrência, adversarial, agregado e recuperação; atualizar
   STATES e a cadeia somente com provas e emitir recibo final independente.

Condição de conclusão: todos os critérios locais comprovados; o aceite máximo
é QUALIFICACAO_FISICA_LOCAL_USUARIOS. Nunca fonte real, completude, release,
cutover ou encerramento dos critérios maiores por esta preparação.