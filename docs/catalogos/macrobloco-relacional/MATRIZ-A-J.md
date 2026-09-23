# Matriz A–J — construção local

RELATIONAL_LOCAL_COMPLETE. Todas as frentes A–J foram entregues e verificadas
localmente, incluindo diff, documentação e continuidade.
Evidências privadas referidas abaixo pertencem a
`target/macrobloco-relacional-20260911-01/`.

| Frente | Entrega | Teste/camada e evidência | Limite específico |
| --- | --- | --- | --- |
| A | Contrato executável de raiz/componente, tipo/presença, escopo/run, versão/fingerprint, data, binding e cardinalidade; V029–V037 e baseline | ContractTest; MatrixIT; seis instalações com hashes; schema-validation-final.json | Bindings e extensões de item são sintéticos; não há ratificação real de identidade |
| B | Três fontes passam pelos parsers/guards/casos de uso/auditoria/gateways reais; Coleta exata; picks/MDF-e independentes | LocalIntegrationIT, MatrixIT: três vazias completas, três travessias parciais recusadas, coortes, offsets/1ns/terminal/51428; ScaleIT | Campo real ausente não vira path de fornecedor; mapper continua rejeitando contratos inválidos |
| C | SQL resolve MC por evidência, mantém raiz única, recibos, candidatos sem binding e órfãos | Órfão→claim→captura complementar→vínculo; equivalência/revisão/cardinalidade; ausência de lookup por alias conferida em SQL061 | Alias ambíguo nunca autoriza identidade; nenhuma consulta ao legado durante ingestão |
| D | Crosswalk CF no grão do item e dependência de MC; histórico de revisão e links ativos | 1:1,1:N explícita eN:N; cadeia e replay. verify-04 inclui40 testes na matriz física, com CF1:N e MDF-e amplo | Não reduz item a raiz nem usa hash/tempo/coincidência numérica como identidade |
| E | Backlog persistido, claims limitados, exclusão, lease, expiração, adiamento, abandono, teto e quarentena; hidratação mínima | Clock/ticker injetados; MatrixIT: lease/teto, vazio, contrato, temporária, cancelamento depois de claim e timeout; concorrência real | Regime rollback-only serializa transações do mesmo run; sem retry infinito ou fonte real |
| F | Planner existente ligado a capturas/recibos SQL; BOOTSTRAP/INCREMENTAL/BACKFILL/REPLAY, gaps e reabertura | LocalIntegrationIT: partições3→1→2, seis fronteiras de falha, leitura por novo adapter, repetição sem novas capturas e watermark preservado | Reidratação na mesma transação; não prova COMMIT/crash nem promove watermark por maior data |
| G | Runner one-shot opt-in com scenario/hydrate/replay/status e exits existentes | jar-01: dez processos/casos, comandos0, status10, negativas20, sem timeout; classes do JAR conferidas | Main padrão dormente; cada invocação cria/reverte a fixture; não é serviço ou operação real |
| H | Status/partição/recibos tipados com contagens fixas, conservação, proveniência e distinção entre vazio e incompleto | CaptureReceipt/ResolutionReceipt; três capturas vazias, hidratação vazia, desativação por revisão/no-op, zero multiplicação | Projeção de laboratório; não é fato V2-036 nem view publicada V2-037 |
| I | Java/JDBC/SQL, duas sessões reais, três escalas e repetição, instrumentação e planos SQL reais | verify-04:95 IT,0 falhas/erros/skips. scale-verification.json;12 planos | 4096 excedeu240s e permanece falha exploratória; escalas aprovadas16/256/1024. Heap não prova platô/SLO |
| J | Verify, formato, estilo, arquitetura, cobertura, regressão39 IT anteriores, JAR e preservação de fontes/schema/dados; diff e continuidade | verify-04 1582/0/0/4;797 fontes; validações e scanner registrados no relatório | Continuidade/runtime/contraprovas aprovados; gitleaks indisponível e feed externo não executado |

Não há novo B64, checkbox de implementação por classe/documento, cutover,
publicação ou aceite agregado de R01/R02/V2-046/V2-047/V2-012/V2-050/V2-038.
Oráculo independente, correspondências reais, janela representativa, aceite
nominal e Segurança/V2-041 permanecem nos subgates que os exigem.
