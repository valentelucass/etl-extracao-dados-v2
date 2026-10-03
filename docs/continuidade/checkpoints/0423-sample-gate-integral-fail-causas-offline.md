# 0423 — Gate SAMPLE integral FAIL; causas locais identificadas

Data02/10/2026. Anterior: [0422](0422-sample-testes-focados-ci-integrado-pendente.md), SHA `87CF30BB9E949ADE549FBBF332261D00DCD3D35BA2253D6E64BC7F1DA0C22AA7`.

## Objetivo, autoridade e estado

Pedido efetivo segue tentar a extração completa de poucos dados com agentes Maestri/curl diagnóstico. Esta unidade verifica o gate offline completo dos bytes SAMPLE já congelados; não autoriza nova consulta externa nem SQL. **TESTADO_NA_CAMADA_OFFLINE / FAIL_INTEGRADO**, não físico/pronto. Escopo/owners de `target/pilot-etl-20261002/supervisor-scope.json` preservados. Banco único SQL, Runtime bootstrap/launcher/testes, Codex docs/CI; não produção, DDL, credencial, serviço, limiar ou nova exclusão de cobertura.

Runtime avisou início de um único `mvn --offline clean verify` sobre final-build-01 completo; Codex confirmou freeze de fontes/docs/grafo e não os alterou durante a suíte. Runtime avisou encerramento FAIL, sem processo ativo desse gate. Codex leu recibo/log/relatórios e conferiu hash do manifesto antes de registrar esta fotografia. Alterações atuais de estado ocorreram depois do fechamento; não invalidam o zero drift registrado naquela execução nem reescrevem seu manifesto.

## Execução e evidência

Prefixo privado: `target/pilot-etl-20261002/runtime/`.

| Passo/camada | Observado | Evidência/limite |
| --- | --- | --- |
| Build offline completo cap2 | exit1, FAIL_OFFLINE_INTEGRATED, accepted=false | `final-gate-receipt.json`, `final-clean-verify-01.log`; zero HTTP/SQL/shadow. |
| Inputs congelados | 4209; sourceDrift0/snapshotDrift0 | `final-input-manifest.json`, SHA `60FF7D9E5B78ED9C3AB6F6DF36D22A65EFA68163B057DA20FE610C5F078BB6C4`. |
| Surefire | 2453/0/0/5; SAMPLE12 e Banco trial21 PASS | XMLs `final-build-01/target/surefire-reports/`, hashes no recibo. Não encerra gate integral. |
| Failsafe | 6/0/2/0 | `QualificationPackageIntegrityIT`: dois parse JSON token NOTE; relatórios preservados. |
| JaCoCo | bootstrap linhas0.79<0.80, FAIL | Check autoritativo no log; não mudar limiar/exclusão. |
| Diagnóstico de launcher | Setter-null gera notas JVM; remoção real elimina-as | `launcher-environment-diagnostic-02.log`; demonstra emissão da nota, ainda não comprova correção dos dois testes de pacote. |

Causas/decisões: Runtime investigou contaminação de subprocesso por variáveis JVM vazias criadas no launcher. Codex confirmou diagnóstico comparativo e pediu reexecução causal do pacote sob remoção real, sem relaxar JSON. Cobertura exige testes significativos dos caminhos ainda descobertos; HTML identifica SampleRunner21/64 e BankTrial19/19 linhas perdidas. Estatística total HTML inclui classes excluídas e não é o check de cobertura. Não modificar código de produto sem defeito demonstrado. FAIL01 e snapshots/manifest imutáveis.

Runtime recebeu por Maestri as correções offline, limites e critérios de novo gate: menor correção do launcher e testes de recusa optflags/binding/secret antes de abrir JDBC, eventualmente composição com SQL falso; nenhum HTTP/JDBC real, nenhuma reflexão artificial para perseguir percentual. Banco três bytes permanecem congelados. Novo freeze/candidato CI se Java mudar; Codex integra catálogo/estado. Um gate completo novo é justificado somente depois das causas tratadas; sem duplicação da execução idêntica.

## Retomada — até três ações

1. Runtime conclui diagnóstico/correção do launcher e testes causais de cobertura; entrega resultado e freeze novo, preserva FAILs e encerra por handoff sem polling. Codex confere bytes/CI antes do próximo gate completo offline.
2. Após correções verificadas, executar um gate completo no snapshot novo, comparar drift e XMLs/quality gates; somente PASS fecha qualificação offline. Registrar falhas reais, não substituir histórico.
3. Prova real continua dependente de mudança concreta na quota429 pela origem e gates Banco Windows/listeners/TLS/objetos com reserva/PRE/POST próprios. Saldo teórico7 não renova teto10 nem permite sondar para descobrir liberação.

Nenhuma carga real pelo ETL, nenhum SQLwrite, aceite físico/P08/G01/cutover ou checkbox. Gate não está rodando; Runtime tem unidade nova de correção offline. Codex não mantém processo em espera ou polling. STATES canônico; CONTEXTO_GLOBAL ausente; lacuna histórica HANDOFF_PATH sem alteração.
