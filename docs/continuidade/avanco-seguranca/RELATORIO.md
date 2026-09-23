# Correções locais executadas

Foram corrigidos oito defeitos, com 24 novos casos de regressão. A execução final offline executou 2186 casos (4 skips históricos), sem falhas/erros; Enforcer, Spotless, Checkstyle e compilação passaram. Nenhuma fonte externa ou conexão SQL foi usada.

| ID | Local | Problema corrigido | Prova unitária |
| --- | --- | --- | --- |
| SEC-01 | QualificationConcurrency | Encerramento do executor substituía a falha da operação. | QualificationConcurrencyTest |
| SEC-02 | LocalArtifactScenario.previewSequence | Rollback substituía Error do trabalho isolado. | SavepointScopeTest |
| SEC-03 | QualificationTemporalMatrix.isolated | Rollback substituía Error do trabalho isolado. | SavepointScopeTest |
| SEC-04 | ColetaTemporalLaboratorySession.control | Falha ao verificar statement anterior deixava o novo aberto. | ColetaTemporalLaboratorySessionCloseTest |
| SEC-05 | BoundedRuntimeDataSource | Falha ao configurar timeout deixava statement recém-criado aberto. | RuntimeJdbcBoundariesTest |
| SEC-06 | BoundedRuntimeDataSource | Recusa RuntimeException do opener consumia permanentemente permit. | RuntimeJdbcBoundariesTest |
| SEC-07 | JdbcRasterBatch.close | Falha posterior de fechamento/observer substituía a primeira. | JdbcRasterBatchTest |
| SEC-08 | DataExportHttpExecutor | Error assíncrono era convertido em IOException e podia ser repetido. | DataExportHttpExecutorTest |

A checagem [PMD local](../../../scripts/security/README-static-analysis.md) executa dez regras, exige todas as fontes analisadas, recusa relatórios ausentes/incompletos, erros e supressões. Seus 12 casos de controle passaram. Nas 653 fontes finais, restaram 37 alertas, ante 40 iniciais; nenhum foi ocultado. [Triagem](triagem.json) distingue sanitização intencional, transferência de ownership e os defeitos encontrados nos caminhos adjacentes. A triagem registra a leitura anterior aos testes; o resultado central registra sua execução posterior. O gate continua FINDINGS_OPEN; este recorte não é SAST integral nem aceite de Segurança.

A primeira tentativa de Maven parou no formatter: seleção relativa não correspondia ao regex absoluto exigido pelo Spotless no Windows. A causa foi corrigida; logs e falha permanecem. Unit02 preserva2175casos,5erros de pré-condição da JVM filha sem teto512MiB e13novas regressões aprovadas. Unit03 fixou esse teto no ambiente privado,mas parou no Checkstyle EmptyBlock antes dos testes; o fechamento foi ajustado mantendo a ordem e a causa. Unit04 comprova os bytes finais,sem alterar assertions ou POM. As provas físicas e o pacote anteriores não qualificam este novo Java: P07/P08 precisam de requalificação própria antes de qualquer promoção. Permanecem os 16 requisitos externos e os demais critérios P09–P33 da [matriz anterior](../entrega-p09-p33/matriz.json).39/45 e67/115 não foram promovidos.

Não houve alteração de dependências, POM, schema, migrations, dados, credenciais, agendamento, publicação ou produção. Recuperação: snapshots exatos dos arquivos existentes em ../historico/avanco-seguranca/, sem restauração automática; novos arquivos são explicitados pelo manifesto. [Resultado](resultado.json) registra os recibos; [validações](validacoes.json) distingue checks executados e pendentes.

O [recibo final](../../../target/avanco-seguranca-20260922-01/closed-receipt.json) reúne a varredura, a conferência final e o encerramento. O [diff para revisão](../../../target/avanco-seguranca-20260922-01/code-review.patch) contém as alterações de código e ferramentas desta rodada.
