# B60 — campanha física interrompida e compensada

Estado de 10/09/2026 02:34 UTC: `PHYSICAL_CAMPAIGN_STOPPED_COMPENSATED`.
O usuário aprovou explicitamente o pacote
`a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e`,
incluindo preflight, V024, concessões, alcance de force/replay, 74 casos e
compensação, sem renovação de validade, orçamento ou escopo.

O controlador original foi executado sem alteração, em localhost/
ETL_SISTEMA_V2_SHADOW, sob RTR-SVW-002\suporte com elevação Windows normal.
Ledger: target/bloco60-local/physical/a3d28adeb17775bc/ledger.jsonl.
Consolidação: target/execucao-b60-aprovada-20260909-2334/.

| Critério/camada | Resultado observado | Limite do aceite |
| --- | --- | --- |
| Preflight SQL | Alvo, V023, 9.137 linhas, perfil, scopes e colisões passaram. | Expectativas anteriores agora reconfirmadas nessa campanha. |
| Upgrade/sufixo | Duas transações revertidas, mesmo catálogo cfd68974c4fc7667cde1bf9376250d41d58fccf026f9aae5a03268062ecd8e34; preservação idêntica após rollback. | Equivalência composicional sobre V023; não criação física desde banco vazio. |
| Instalação | V024 instalada; preservação de dados anteriores passou. | Migration aplicada é imutável; nenhuma reversão destrutiva permitida. |
| Ativação | Perfil temporário aprovado conferido; quatro variantes protegidas instaladas. | Não comprova sucesso operacional dos JARs. |
| Primeiro caso | USUARIOS_RUN retornou 20/UNCONFIGURED; esperados exit 0 e dois HTTP. | Falha real, não negativo esperado. Zero casos aprovados. |
| Readback do caso | Zero tentativas, decisões, consumos, publicações, selos, páginas, staging, aplicações e histórico tipado. Fonte: zero HTTP/nodes. | Os 73 casos seguintes não foram executados. |
| Compensação | Estado ACTIVE lido antes de RESTORE_B60; PROFILE_RESTORED retornou B60_PROFILE_AFTER_PASS. | Mapping SERVICE v19/replay=force=0, 32 grants totais, quatro scopes v2 revogados, duas policies revogadas; V024 e bindings preservados. |
| Encerramento | Um OPEN, um CLOSE/NOT_QUALIFIED, 19 reservas, 17 sqlcmd, uma JVM, três commits administrativos confirmados. | Zero UNKNOWN pendente, zero escrow usado; campanha fechada, sem reaproveitar saldo ordinário. |

O controlador e a JVM própria terminaram; não ficou listener na porta 62160.
Artefatos protegidos permanecem para revisão conforme o pacote. A comparação
global de multiconjuntos HISTORICAL_ROWS_AFTER da matriz completa não foi
alcançada; há prova de preservação até a instalação e perfil exato após compensação.
Não apresentar essa cobertura parcial como conclusão de todas as verificações.

## Diagnóstico offline do bundle

O manifesto administrado contém
`probe/br/com/esl/etl/v2/bootstrap/RuntimeUsersPhysicalProbe$Fault.class`.
AdministeredArtifactVerifier aceita somente `[A-Za-z0-9_./-]+` nesse campo;
logo essa entrada é incompatível com o verificador empacotado. O gerador inclui
inner classes com `$`, enquanto seus testes de integridade conferem os hashes.
Isso é um impedimento concreto encontrado por inspeção. O log físico externo
mostra apenas UNCONFIGURED; não expõe a causa interna exata nem exclui outros
impedimentos anteriores. Nenhum JAR/código/manifesto aprovado foi alterado para
mascarar ou tentar novamente a falha.

Próxima preparação independente: reproduzir essa incompatibilidade num teste
offline do bundle com o verificador real e corrigir uma revisão futura, mantendo
o pacote aprovado imutável. Uma nova campanha precisará considerar V024 instalada,
SERVICE v19, scopes revogados e o OPEN já encerrado; o mesmo pacote não é
reexecutável. Não há aprovação automática para novo hash, OPEN, prazo ou orçamento.

## Sucessão e progresso

QUALIFICACAO_FISICA_LOCAL_USUARIOS não foi atribuído. V2-022 pai, Q-USR-01,
V2-012a/b/c, bootstrap, sweep, medição por entidade, E2E, release e cutover seguem
abertos. Nenhum checkbox mudou: 67/115 = 58,26%; 48 pendentes, 191 rotas, zero AGORA.

O manifesto desta fase prende exatamente quatro alterações: STATES, trilha,
RETOMADA e o validator da manutenção anterior. Snapshots em
docs/continuidade/historico/bloco60-fisico/ preservam os bytes anteriores.
Test-Bloco60PhysicalClosure valida essa sucessão e, no modo privado, os 2.020
artefatos do recibo anterior na revisão correta, o ledger e os resultados físicos.
Test-GlobalGateMaintenance passa a usar esse mapa fechado para suas leituras
históricas; o pacote aprovado e Test-Bloco60Local permanecem byte a byte intactos.
Nenhum manifesto/recibo/ledger anterior é regravado. A aprovação consta da mensagem
do usuário e do registro privado; campos approval=false dos pacotes históricos
permanecem fotografias da preparação, não o estado atual da autorização.

O encerramento documental depende do recibo final da consolidação. Seu PASS
significa integridade/consolidação da campanha falha, nunca PASS da matriz física.
