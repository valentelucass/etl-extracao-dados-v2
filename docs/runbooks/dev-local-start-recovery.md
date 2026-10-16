# Retomar um início pendente no componente dev-local

Este procedimento usa a CLI DevLocalMain com filesystem real e dados locais sintéticos. Requer workspace já inicializado, execução não reservada e os bytes qualificados pela ADR0063. Não executa ETL com fonte ou SQL. Mantenha STOP429 e o V1 preservados.

1. Pare novas mutações do mesmo workspace. Preserve o journal, start-intents e diretórios da ocorrência. Obtenha o hash técnico esperado do input original, fora de logs de negócio; não substitua esse valor por um hash aprendido de uma intenção incerta.
2. Execute `status --workspace <diretorio-local> --execution <execucao> --synthetic`. START_PENDING/pendingStartHold informa necessidade de reconciliação. Status não registra START nem altera arquivos. Se a intenção estiver corrompida, o comando pode recusar; conserve a recusa e os arquivos.
3. Somente com a ocorrência e input esperados, execute `reconcile-start --workspace <diretorio-local> --execution <execucao> --input-sha256 <sha256-minusculo-64> --synthetic` pelo mesmo lançador qualificado de DevLocalMain. Não passe config, mode, previous ou case. Paths remotos, ADS, links, execução workspace-init e hash incompatível são recusados.
4. Confira o recibo e execute status novamente. Readback STARTED comprova inicialização do registro, sem comprovar extração ou conclusão. Uma reconciliação de registro já terminal mostra o término realmente gravado. Confira shutdownComplete/exitCode; resultado perdido é desconhecido até nova observação, não autorização para retry de negócio.
5. Para fechar uma execução STARTED sem trabalho de negócio, execute `stop --workspace <diretorio-local> --execution <execucao> --synthetic` e observe status em processo novo. STOPPED é término local honesto. Reconciliar a mesma intenção depois disso não deve adicionar outro START.

Não opere este runbook com workspace não inicializado ou com recuperação de workspace-init. Não adote diretório estrangeiro, altere hash/intenção, apague arquivos ou substitua journal. Escrita parcial/corrupta, material inesperado, conflito de identidade/input/case ou lock ocupado exigem conservar a recusa e investigar a ocorrência. Não repetir para contornar uma recusa.

O lock cobre escritores cooperativos deste componente. As provas são de morte de processo e reabertura em JVM nova, não queda de energia. Não voltar ao leitor antigo num workspace com intenção pendente: ele desconhece o contrato novo. Relatório e evidências: [paridade V1](../catalogos/paridade-v1-ce7f/RELATORIO.md), [ADR0063](../adr/0063-retomada-explicita-do-inicio-dev-local.md).

## Executar as provas de interrupção

Os casos que interrompem a JVM usam instrumentação de teste externa. DevLocalLedgerStartRecoveryTest exige `-Dbank.start.external-fixtures.enabled=true`, os paths `bank.start.crash.agent`, `bank.start.crash.asm` e `bank.start.legacy.classes`, com os arquivos hash-pinados no handoff final de Banco. Legacy.classes é o JAR da baseline compilada antes do reparo, não as classes atuais. Sem esse opt-in (ou com false), somente os13 casos dependentes são abortados por pressuposto JUnit explícito; os5 independentes executam.

DevLocalMainStartRecoveryProcessTest usa a mesma flag `bank.start.external-fixtures.enabled` e exige também os valores esperados `bank.start.crash.agent.sha256`/`bank.start.crash.asm.sha256`, além dos paths agent/asm. Ausência ou false desabilita somente seus3 casos instrumentados; os7 independentes continuam disponíveis. True exige fixture completa e verificada; valor inválido ou pin divergente falha. Runtime não usa a baseline legacy; a flag compartilhada não dispensa os pré-requisitos específicos de cada classe.

Abort/skip por ausência de fixture não é PASS das fronteiras de interrupção. Opt-in inválido, incompleto ou fixture divergente deve falhar antes de lançar o processo instrumentado.

O pacote de evidência de paridade01/banco02 mantém o agente de teste, seu código, a baseline compilada e a proveniência dos bytes. O agente pinado desta unidade tem SHA256 `93bf2d8f253e1ecd83c577ae33f41a7d50510e95eb869e4a02067bf4c0be97a3`; ASM9.7.1 tem `8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281`; o JAR baseline tem `f94457342a8f64166968fe48422ae244769ac019c2a47821bf96b9def567a1a8`. Não é download automático ou dependência produtiva. O clone sozinho não contém essas fixtures externas; consultar o manifesto qualificado antes de reproduzir as fronteiras.

Selecione DevLocalLedgerStartRecoveryTest e DevLocalMainStartRecoveryProcessTest no executor JUnit/Maven existente, com JDK17 e os três paths verificados. A execução padrão dos casos independentes, a execução opt-in completa e os testes de admissão do gate têm resultados separados. Conservar falhas/aborts e limites de processo; não reunir tentativas diferentes em um total de sucesso sem indicar quais casos e bytes foram executados.
