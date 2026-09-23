# ADR0051 — sequências integrais declaradas no pacote

15/09/2026. Decisão local adotada pelo pedido de campanhas integrais A–N.
Implementação e qualificação em andamento; nenhum aceite nominal implícito.

## Decisão

Expor o encadeamento já comprovado em IntegralArtifactReplayIT por manifesto
fechado, preservando os consumidores integrais anteriores. O novo executor
coordena descritores, oráculos e recibos limitados; reutiliza os onze pipelines,
cinco materializações e comparadores existentes. Não retém um universo de
domínio na JVM. A única sessão SQL é rollback-only no alvo local autorizado.

Rejeitar ramificação ambígua sobre estado compartilhado. A cadeia inicial tem
um predecessor imediato por etapa; campanhas independentes continuam no DAG
do supervisor. Cada etapa consome explicitamente entradas e revisões, e seu
resultado é comparado antes da próxima. As três revisões são conceitos distintos:
tentativa/execução, observação da fonte e release de referências.

## Alternativas e consequências

Vários BOOTSTRAP com rollback entre eles não comprovam persistência de estado
entre etapas e foram rejeitados. Um novo motor genérico repetiria regras dos
kernels e também foi rejeitado. A ação SEQUENCE é aditiva ao supervisor; seus
1800s não ampliam ações antigas. Etapas continuam limitadas a240s.

Perda de JVM perde a transação: o journal permite ler recibos e reconciliar o
resultado, sem prometer dados duráveis ou retomada intermediária. Recuperação
material COMMIT/crash/restore permanece externa. ADR0047 mantém sua fronteira
temporal própria; o novo coordenador não promove essa biblioteca ao core.

Schema102 é mantido enquanto não existir necessidade demonstrada de migration
aditiva. Qualificação, compatibilidade, rollback e limitações devem constar dos
recibos e do relatório da execução; este ADR não declara PASS.

## Recomposição e compatibilidade

Recomposição de suplementos recebe uma operação final explícita, sobre as fontes
capturadas, com recibos próprios das cinco materializações. Não representar essa
operação como outra captura mantém a auditoria fiel ao efeito executado.
O contrato atual de tarifa COT é imutável por snapshot; uma troca de release
sem captura não pode ser alegada como recomposição suportada. A recusa é local
à operação específica, sem impedir suplementos ou outras campanhas.

A nova dependência COL→FRE vale no caminho da sequência. Cenários anteriores
preservam sua ordem de captura e a identidade das provas de resiliência. A
compatibilidade será verificada no gate completo, sem alterar os quatro skips
históricos ou promover LocalColetasTemporalRuntime além do ADR0047.
