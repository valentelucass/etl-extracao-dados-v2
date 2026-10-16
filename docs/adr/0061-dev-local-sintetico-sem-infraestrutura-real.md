# ADR0061 — DEV LOCAL sintético com composição e persistência próprias

Estado: adotado e qualificado no DEV LOCAL para HERMES_b77c5029cfbd. Provas e matriz final no checkpoint0475; aceites físicos/produtivos permanecem separados.

Lucas pediu concluir todo o modo DEV LOCAL e excluir funcionalidades opcionais realmente inviáveis neste ambiente. O escopo proíbe novas conexões SQL, chamadas externas, credenciais reais, downloads, mudanças V1 e produção. Os comandos `local-sequence run` e `local-data capture` existentes abrem uma sessão JDBC; somente passar Maven ou caracterizar arquivos não entrega esse objetivo.

Disponibilizar um entrypoint separado e opt-in `DevLocalMain`, com dataset claramente sintético, mappers/regras existentes, engine local e persistência em diretório novo verificado. A composição não registra cliente de fornecedor, DataSource, segredo/provider ou execução produtiva. O JAR mantém `Main` e defaults strict existentes. Uso de arquivo sintético não recebe identidade nominal, completude real, publicação SQL ou aceite produtivo por inferência.

Arquivos locais têm journal e readback próprios, lock, limites finitos, estado terminal e recusas para replay conflitante/inputs inválidos. Reexecução deve consultar a ocorrência; não limpar evidência nem sobrescrever outputs para esconder erro. Esse contrato não declara COMMIT/rollback SQL, durabilidade contra perda elétrica, restore de banco ou fences do writer produtivo.

O plano P01–P33 original continua canônico para aceites integrais. Uma matriz DEV separada deduplica as 48 unidades abertas e registra critérios sintéticos comprovados e parcelas excluídas, causa, impacto e trilha futura. P09/P10/P11/P12/P30/P31/P32/P33 dependem de operações externas nesse recorte. P24 e P26 dependem de materializers e paridade SQL: aproximações de fatos com regras inventadas foram recusadas e removidas somente da composição nova, com o histórico preservado. A unidade025d de cURL externo fica excluída dentro de P13, que mantém outros critérios locais. Componentes locais independentes e proteções continuam disponíveis; cada exclusão registra impacto e trilha futura. Nenhum checkbox físico/produtivo é promovido por testes sintéticos.

Reversão: deixar de invocar o entrypoint/launcher DEV. Artefatos novos são isolados; não remover dados locais/evidências automaticamente. Caminhos JDBC, pin TLS, STOP429, orçamentos encerrados, histórico, migrations, serviços, contas, versão SQL16.0.1000.6 e V1 permanecem sob seus contratos anteriores.

Aceite desta decisão exige CLI empacotada realmente executada (startup, fluxos, readback/replay, erros/cancelamento e shutdown), testes causais e agregado dos bytes finais, matriz sem lacuna essencial oculta e scanner aplicável. Registro de implementação não equivale a esse aceite; provas seguem nos checkpoints e catálogo DEV.
