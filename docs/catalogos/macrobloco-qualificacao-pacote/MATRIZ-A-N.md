# Matriz A–N — qualificação e pacote local

CONSTRUÇÃO_LOCAL_CONCLUÍDA no escopo sintético, condicionada ao selo final e seus
validadores na revisão entregue. Cada linha liga mecanismo, consumidor, oráculo,
prova e limite. V2-038/039 são as duas capacidades novas;012/015/022/023/045/050
são aprofundamentos de unidades já contadas.

| Frente | Componente/consumidor | Oráculo/teste | Evidência | Limite |
| --- | --- | --- | --- | --- |
| A | Inventory-before2988, snapshots, predecessor e sucessor exato | Pins/deltas/inventário; validadores analítico/expansão/relacional/temporal/continuidade | inventory-before.json; predecessor-result.json; sucessão final e diffs | V2-038/039; sem usar HEAD sujo como baseline |
| B | QualificationCampaign/Json/Gate/Topology, Worker/Supervisor | JSON fechado, enum/tipo/DAG/limites; QualificationContractTest e parser mutado | Verify03;17smokes;21recusas extraídas | 35nós não equivalem a35unidades de construção |
| C | QualificationOracles/Comparator/WireOracle/Lineage/Location/Monitoring; harness V2-012/JDBC | 673colunas explícitas;19saídas;971metadados; valores exatos, nulo/zero/Unicode/UTC/linhagem | ValueOracle/Lineage/Metadata/Output/Monitoring/CharacterizationIT; ABSENCE/VARIANTS | SQL04 positiva em confirmação/reaparecimento; fixture não fecha V2-012b/c |
| D | QualificationPlanner/WindowExecutor/TemporalMatrix e RuntimeTemporalPlanner/Coordinator/Dispatcher | Cinco políticas efetivas, quatro modos, lookback/late/correção/frontier/blackout/deadline/catch-up/DST23h | WindowIT/TemporalMatrixIT/TemporalPlansIT; TEMPORAL/ADMISSION extraídos | Captura DEGRADED permanece; fonte não avança por fato/replay/gap |
| E | QualificationCaseExecutor e cenário por onze adapters/6dimensões/5fatos | Equações SQL,19oráculos,35gates e DAG com ramo independente | SCENARIO;4degradações;WAVES com2filhos/3casos | CAP passa com Raster bloqueado; dependente sem filho; cutover database-wide |
| F | CampaignJournal, Supervisor/Worker, ProcessEvidence e ControlFiles | Reserva/nonce/PID/criação/JAR/exit/recibo/logs selados; estado desconhecido | 4barreiras;8adulterações;CompositionIT; status/resume/compare | Journal recupera controle; SQL revertido é reconstruído, sem COMMIT/crash durável |
| G | ColetaTemporalLaboratorySession/LaboratoryJdbcBudget, QualificationConcurrency/Metrics | SPIDs físicos,claim53401,timeout/cancelamento e consumidor após rollback;64handles | Concurrency/Cancellation/Control/MetricsIT; CONCURRENCY extraído; escalas | Caso300s, heap512MiB,100.000JDBC e limites11entradas; nenhum SLO nominal |
| H | New-QualificationPackage, QualifiedPackage, QualificationPackage.psm1 | Manifesto/papéis/hash/tamanho,lock/SBOM/proveniência/recursos do JAR consumidos | 172membros/9deps;package-final/repro;Artifact PASS | Sem src/testes/logs/credenciais; suporte Windows/x64/Java17 |
| I | outputTimestamp, dois builds offline e CycloneDX1.6 | ZIP/JAR byte-idênticos,1.996inputs e1.015classes/recursos, mutante de fonte pós-build | artifact-final-01; SBOM schema oficial offline; lock9 | Hash não é assinatura; feed NOT_EXECUTED; snapshot local não é CI publicado |
| J | Expand-QualificationPackage e QualificationConfiguration | Traversal/UNC/ADS/link/colisão/bomba; campos/travas/alvo/versão antes de JDBC | 25envelopeguards+21extraídos;0JDBC/filhos | DLL local até240caracteres; extração sem migrations |
| K | Invoke-Qualification e QualificationLaboratoryMain | inspect/plan/run/status/resume/compare; exemplo empacotado e cwd/classpath exclusivos | 17smokes+4escalas,126comandos em21diretórios novos com espaço/Unicode | 29comandos negativos adicionais; nenhum smoke de outroSO alegado |
| L | Testes tipados/JDBC, guardas de pacote/controle, scanner e revisão | Mutantes de valor/chave/metadata/versão/journal/recibo/processo/fonte; UTF8 | Verify03;25/21/8guardas;16scanner-guards;checks finais | Tentativas falhas preservadas, sem exclusão genérica ou redução de cobertura |
| M | Invoke-QualificationBuild, Artifact e pacote medido | 378IT anteriores exatas+39novas;1.976unitários;4skips históricos;0ITskip | Verify03;4/16/32/16;3planos novos e regressão medida | Heap/lag/duração diagnósticos, sem platô/SLO/aceite real |
| N | STATES funcional, quadro45, relatório/comandos/matrizes, checkpoint e sucessão | Antes/depois exatos, inventários/diffs,validadores/contraprovas e selo externo | succession-final-01;succession-final-checks-01;final-seal.json | 37→39/45 somenteV2-038/039;67/115 preservados;sem aprovação humana |

Evidências relativas a `target/macrobloco-qualificacao-pacote-20260913-01/`. [Resumo](verification-summary.json),
[responsabilidades](matriz-responsabilidades.json), [colunas](matriz-colunas.json),
[relatório](RELATORIO.md) e [quadro45](quadro-construcao.json) vinculam os bytes.
