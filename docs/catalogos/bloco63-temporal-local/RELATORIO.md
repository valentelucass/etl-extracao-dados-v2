# B63 — manutenção temporal local de Coletas

10/09/2026. **B63_TEMPORAL_LOCAL_COMPLETE_EXTERNAL_GAPS_OPEN.**
Adoção integral pelo usuário de `prompt-bloco-63-semantica-temporal-coletas.md`.
Entrega A–D exclusivamente local; nenhuma promoção de checkbox do roadmap.

O mapper deixa de inventar um instante para horários locais no gap/overlap de
America/Sao_Paulo. Preserva bruto e presença e percorre o fallback aprovado.
O harness existente de Coletas agora seleciona a forma pelo release: os novos
testes passam ROOT_ARRAY pelo parser/gate/streamer/mapper/staging, conservando
o envelope B58 e o replay B62 como evidência histórica.

## Entregas e evidência

| Frente | Resultado | Evidência e limite |
| --- | --- | --- |
| A | Mapa V1→V2, decisão ADR0044, distinção entre evento, atualização da entidade, data civil e captura | [MAPA-TEMPORAL.md](MAPA-TEMPORAL.md); leitura estática de SQL, sem execução física |
| B | Correção mínima COL-TIME-02; ligação test-only por release; 44 novos testes Java | RED gap/overlap27/6/0/0; RED ligação corrente19/10/0/0; GREEN dirigido259/0/0/1; verify1492/0/0/4 |
| C | Dez casos, seleções/campos/bindings conhecidos, limites e propostas SQL/GraphQL revisáveis | [PROVA-REPRESENTATIVA.md](PROVA-REPRESENTATIVA.md), [inputs-prova.json](inputs-prova.json); orçamento/autorização/oráculo/aceites externos não preenchidos por inferência |
| D | Verify isolado, inventário/diff, sucessão documental, validador próprio e continuidade | `manifesto.json`, checkpoint0061, evidências privadas abaixo; resultados de integridade na própria rodada |

## Regressões executadas

`ColetaDataExportRecordMapperTest`: 23 casos adicionais de formatos locais,
Z/offset, virada de dia, gap/overlap, ABSENT/NULL/vazio/inválido/valor, ausência
de fallback por updated_at, precedência não baseada no máximo, início válido
de dia civil, fuso independente do host e nanos preservados.

`ColetasCurrentTemporalTest`: 19 casos adicionais no release corrente, incluindo
done/finished/cancelamento/desconhecido, expansão/per/repetição e ordem inversa,
campo de status temporal recusado mesmo NULL, tipos inválidos antes do mapper,
envelope antigo recusado, cap/sem terminal e expectativa falsa de paridade.
O staging conserva as duas observações com status divergentes; não simula dedupe.

`JdbcSqlServerColetaGatewaysTest`: dois casos adicionais partem do mapper real,
verificam nanos e fallback com calendário UTC e captura em parâmetro independente.
JDBC é proxy, não conexão física. A conversão DATETIME2(3) não foi executada.
As suítes existentes B58/Q-FND, contrato6908 e replay B62 sintético também passaram.
Nenhuma probe B62 foi executada e nenhum corpo real foi reconstruído.

Os dois REDs estão preservados; não foram convertidos em sucesso. O primeiro
mostrou seis resultados STATUS_UPDATED_AT onde o esperado era FINISH_DATE;
o segundo mostrou dez falhas com a ligação fixa ao envelope histórico.
O GREEN dirigido passou após corrigir esses dois pontos. Os dois testes JDBC
foram acrescentados depois do GREEN e passaram no verify completo.

Verify: Java17.0.20.1, Maven3.9.14, heap512MiB, offline, cópia privada completa,
sem clean no target canônico nem perfil físico. **1.492 testes, zero falhas,
zero erros, quatro skips.** Enforcer, Spotless, Checkstyle, arquitetura e JaCoCo
atendidos: 91,65% linhas, 76,57% branches. Os skips são os três casos de symlink
no Windows e Cotações opt-in preexistentes. O JAR foi empacotado e não executado.
Os cinco arquivos Java alterados/adicionados têm hashes da cópia efetivamente
testada registrados em `java-verification.json` e conferidos contra o worktree.

## Limitações e decisões preservadas

COL-TIME-01 continua aberto: release6908 sem status_updated_at e sete diferenças
B62 não resolvidas na fonte. Nem frescor preenchido, nem HTTP200, nem ausência
de diferenças dos campos comuns comprova paridade temporal/representatividade.
Não copiar updated_at/finish_date/observedAt para timestamp de status.

Manter a projeção de data civil V2 em São Paulo; V1 usa +00:00 no fallback SQL.
Gap/overlap em horário explícito exige offset, enquanto o início válido do dia
civil permanece intencional. SQL tem precisão diferente do Instant Java e gates
conservadores que podem bloquear mudança de status na mesma data. O mapa registra
a ordem real V004→V010 e a proposta C delimita a prova física futura. Não há
alegação de dedupe, convergência, transação ou terminalidade física integral.

Preservados releases, migrations, baseline, decisões antigas, fixtures B58,
perfis Q-FND, probes/replay/ledgers/requests/recibos B62 e checkpoints anteriores.
Sem API, .env, credencial, SQL operacional, UAC, nova campanha B60/B62,
runtime operacional, fornecedor, produção, agendamento, deploy, commit ou push.

## Diff, sucessão e recuperação

Inventário inicial: 2.365 arquivos; oito deltas preexistentes com snapshots
exatos e dezoito adições mais manifesto. `Test-Bloco63Preparation` resolve a
fotografia anterior e propaga somente deltas verificados. Os manifests históricos
permanecem com os hashes originais. Validação corrente:
`scripts/validation/Test-Bloco63TemporalLocal.ps1 -IncludePrivateEvidence -SelfTest`.

Evidência privada: `target/b63-temporal-local-20260910/`. Inclui inventory/before,
passo-inicial, logs e XML de cada fase, java-verification, local-checks,
final-checks, diff-completo.patch, diff-resumo.json, receipt e delivery-checks.
`final-checks.json` registra os exits realmente observados de cada validador;
`receipt.json` vincula esses resultados, o manifesto e o diff. A evidência
histórica B62 não foi usada como nova execução Java.

Rollback revisável: restaurar exclusivamente os oito deltas a partir de before
e retirar as adições funcionais da revisão se necessário, preservando todas as
evidências desta rodada e das anteriores. Não há ação operacional de recuperação.
Revisão técnica local realizada pelo diff; revisão humana não alegada.

STATES/trilha mantêm67/115,48 pendentes,191 rotas,zero AGORA. Nenhum novo aceite:
Q-COL-01, V2-012a/b/c e V2-041 abertos; holds externos e demais blocos intactos.

Próximas ações, somente após seus inputs próprios:
1. Ratificar janela/coorte, expectativas independentes, scope e Segurança do pacote C.
2. Obter evidência temporal de fonte com autorização própria e classificar COL-TIME-01.
3. Qualificar precisão/conflito/convergência em SQL autorizado, antes de aceite agregado.
