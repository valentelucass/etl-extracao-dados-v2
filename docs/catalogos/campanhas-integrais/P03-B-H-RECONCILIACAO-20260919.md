# P03/B–H — reconciliação para admissão local de P04

Estado: **ACEITO_NO_ESCOPO como predecessor técnico de P04 local**. Esta
reconciliação não fecha P03 agregado, os fronts A/N, os itens V2 pais, P04/I–J,
paridade real, pacote ou produção.

## Base e integridade

As provas físicas são `p03-campaign-sql-07` (30 unitários e 6 IT, todos PASS) e
`p03-relational-counterproof-02` (41 IT PASS); a tentativa
`p03-regression-sql-01` permanece FAIL histórico por um erro de preparação já
isolado, e seus 25 IT sem a classe afetada só são reutilizados pelos mesmos
bytes. `code-readback.json` registra os pins das 11 classes; a leitura atual
confirmou 11/11 SHA-256 idênticos. Os recibos, processos e rollback/agregados
estão em `target/macrobloco-campanhas-integrais-20260915-01/p03-corrections-20260919/`.

| Critério | Componente e revisão/pin atual | Teste e recibo | Resultado | Limitação |
| --- | --- | --- | --- | --- |
| B — contrato executável | `QualificationCampaign`; `LocalArtifactSequenceTest` SHA-256 `d3b87ec71ff7b56a9557e8934654c2aa1ac616f340b2d8d3b6faf0c247b91520` | `p03-campaign-sql-07`, 25 unitários; contrato SEQ-01/SEQ-04 | Envelope/DAG, limites e agenda declarada exercitados; zero falhas/erros/skips | Não é fonte real nem prova de worker P04. |
| C — executor integrado | `IntegralCampaignIT` SHA-256 `9995a6a37bac76c8fdaf10a36ec1b90da14d33fcc199aee512df5abbd76d4bea` | `p03-campaign-sql-07`, 2 IT `sevenStagesKeepTheirStateAndCompareIndependentExpectedTuples` | Sete etapas A/B em sessão SQL rollback-only | Não executa o supervisor em subprocesso. |
| D — modos, revisão e idempotência | `IntegralArtifactReplayIT` SHA-256 `7901d69779f64e1233e5489217a789e9d4143cbb10b7a6053412d4e101c1b52b`; `SequenceReferenceIT` SHA-256 `79bf01261d642154aeac2c1a5df50428248617997431074849bfa8187e08e524` | `p03-regression-sql-01` (reutilização por byte) e `p03-campaign-sql-07`, 2 IT de referência | Replay/no-op, fonte preservada e revisão tarifária distinta comprovados | A prova não concede durabilidade pós-perda de JVM. |
| E — agenda e dependências | `QualificationPlanner`; caminho de sete etapas de `IntegralCampaignIT` no pin acima | `p03-campaign-sql-07`; contrato SEQ-04 | Políticas consumidas no caminho A/B, com fronteira civil e ordem COL→FRE | A correção de prazo da fixture é validada offline nesta revisão; requer nova execução P04 para a camada supervisor. |
| F — referências e recomposição | `SequenceReferenceIT` pin acima; `SequenceRecompositionIT` SHA-256 `af25e4beac01abdf1fb38886e8c77b1ec8c9926e33059bba84546cf4a9358c98`; ADR0052 | `p03-campaign-sql-07`, 2 IT de referência e 2 IT de recomposição | Referência independente, fonte preservada e recomposição sem recaptura PASS | Limitado ao schema104/local sintético; não altera V001–V102. |
| G — oráculos por etapa | `IntegralCampaignIT` pin acima; `RelationalLaboratoryMatrixIT` SHA-256 `750b7868c97fa5d5088477e6212c2de035552b65d03e8af6313becafa4e0ffd2` | `p03-campaign-sql-07` e `p03-relational-counterproof-02`, 41 IT | Cinco fatos, 19 saídas e contraprovas MC; rollback/agregados confirmados | Não é gate P07 nem revisão integral. |
| H — falhas e isolamento | `SequenceFailureIT` SHA-256 `d48fc849b03c423fdc3b56d371dc5e70522703345dca5acc64068f49be61273a`; `IntegralContextIsolationIT` SHA-256 `7b59e8f2a804d477acf13bc0314427a405672f3b9cd7bd7a2689a0b0a58307b4` | `p03-regression-sql-01`, 4 IT de falha e 2 IT de isolamento, reutilizados por byte | Falha de Coletas bloqueia Fretes/etapas posteriores; escopos não se misturam | Não substitui cancelamento, journal, recibo parcial ou retomada P04. |

## Decisão de admissão

O contrato vigente exige B, C e H para P04. Os pins, resultados e limites acima
atendem essa dependência técnica local, sem inferir aceite de nenhum pai. P04
permanece bloqueada separadamente: `p04-supervisor-sql-01..06` têm resultado
observado e rollback agregado, mas cada uma consumiu uma reserva de 3600 s e o
ledger atual não informa saldo cumulativo nem vigência para nova reserva. Não há
autorização implícita para repetir a campanha.
