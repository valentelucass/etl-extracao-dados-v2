# 0129 — SQL04/SQL10, harness e instantes UTC

EM_EXECUCAO A–N,13/09/2026. Predecessor0128:
ec6c1e3f969b2c8e6d3b1abe142828cb3420c64cb6f19f400ca00f48f78c84f9.
O objetivo continua sendo o pedido integral de qualificação/pacote adotado em
target/preparacao-macrobloco-qualificacao-pacote-20260913-01/
PROMPT-MACROBLOCO-QUALIFICACAO-PACOTE-LOCAL.md. Sem perguntas/subagentes,
sem encerramento parcial; continuar após compactações.

Alvo/limites preservados: somente V2 e localhost/ETL_SISTEMA_V2_SHADOW, Windows
integrado, duas travas, DML sintético revertido. Não houve DDL/migration,
COMMIT de domínio, uso de fonte real, V1/dashboard ou publicação. Construção
37/45, aceites67/115. Nenhum pai operacional fechado.

Evidências sob target/macrobloco-qualificacao-pacote-20260913-01:

| Prova | Resultado observado |
| --- | --- |
| oracle-output-physical-01 | Falhou: tempos/hash esperados e defeito de serialização UTC descoberto; rollback confirmado |
| oracle-output-physical-02 | Falhou somente na expectativa do relógio lógico de Manifestos; rollback confirmado |
| oracle-output-monitor-physical-01 | QualificationOutputIT passou:18saídas completas, SQL04 candidata/confirmada/reaparecida; QualificationMonitoringIT falhou em estados/contagem esperados; exit agregado1 e rollback confirmado |
| oracle-monitor-harness-physical-01 | QualificationMonitoringIT e QualificationCharacterizationIT passaram:2IT,0falhas/erros/skips; rollback confirmado |
| utc-lease-physical-01 | QualificationUtcIT passou:1IT,0falhas/erros/skips; lease UTC e quatro capturas com relógio exato; rollback confirmado |

Os19contratos/673colunas têm oráculo explícito e provas dirigidas positivas:
18saídas no comparador tipado completo e SQL10 com21eventos de todas as famílias.
SQL04 mostrou uma linha após duas confirmações independentes, quatro capturas
por confirmação; reaparecimento voltou a zero. Comparação inclui tipos,
precision/scale/nullability da metadata de negócio, valores, multiplicidade,
ordem, Metadata estruturado, hash LOC e relógios declarados. Isto não completa
as variantes representativas, quatro modos, pacote nem todos971campos físicos.

QualificationMonitoring usa intenções/capturas declaradas e IDs/tempos dos
recibos de controle; contagens/estados são independentes da view. O controle
capture-only de expansão/relacional termina DEGRADED e Raster APPLIED, conforme
políticas existentes. A prova do monitor conserva esses estados. O harness
MapperCharacterization V2-012 passou a consumir Metadata lido de SQL02/05/07,
reconstruindo apenas o lado observado; esperados vêm dos inputs independentes.
Mutante de expectativa foi recusado e providerEvidence permaneceu NOT_EXECUTED.

Defeito corrigido:20parâmetros Instant em11adapters analítico/expansão usavam
setTimestamp sem Calendar UTC; o fuso JVM deslocava DATETIME2. Todos passaram a
informar UTC, incluindo a leitura do lease da fila. Inventário exato privado:
utc-parameter-delta.json. QualificationUtcIT confere contra Instant declarado,
sem obter o esperado da saída. Datas civis/migrations/fontes foram preservadas.
A regressão integral dos adapters corrigidos continua obrigatória.

Oito saídas usam o relógio lógico2036-04-15T12Z. O hash LOC preserva a escala9
do envelope, distinta da escala8 de consumo. QualificationLocationOracle carrega
32esperados fixos e exemplo independente UTF-16LE; raízes1/2 foram comprovadas.
O comparador agora exige metadata exatamente uma vez; a nova unidade de recusa
por falta de metadata ainda precisa execução dirigida/verify final.

Não há processo próprio ativo ao fechar este checkpoint. Nenhuma tentativa tem
resultado desconhecido. WORKLOG privado guarda detalhes e caminhos de execução.

Próximas ações (todas permanecem neste pedido):

1. Integrar comparação ao executor real de campanha/pacote, DAG35 e planner
   afetando comandos/janelas SQL. Usar o dispatcher já consumido por USUARIO/COT,
   sem chamar captura analítica de publicação operacional. Completar variantes,
   quatro modos, correção/late data, SQL03 pós-ausência e gates degradados.
2. Construir entrypoint/supervisor com journal, reserva/nonce/PID/recibo atômico,
   quatro barreiras, reconciliação e concorrência física em claims consumidos.
3. Montar/verificar pacote completo/SBOM exato; provar dois builds/diretórios,
   escalas/limites e verify integral378IT anteriores+novas; revisar diffs e
   finalizar sucessor/selos/STATES/trilha/RETOMADA sem alterar manifests antigos.

A–N ainda não está concluído; entrypoint/campanha/pacote permanecem lacunas
locais a implementar. Os contadores só poderão evoluir após qualificação integral.
