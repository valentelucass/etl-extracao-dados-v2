# B62 — fonte localizada e investigação real limitada

**SOURCE_INVESTIGATION_COMPLETE_CHARACTERIZATION_PENDING**, 10/09/2026.
O usuário adotou o B62 e depois instruiu explicitamente: “nao faço ideia, vc
deve procurar ou usar .env para procurar nas apis”. Essa instrução posterior
autoriza a investigação read-only desta rodada, conforme a distinção já
registrada na continuação remota B56. Não comprova rotação nem aceite V2-041.

## Busca e execução

O `.env` foi localizado no projeto legado. Endpoint/base HTTPS validados como
mesma origem. Tokens GraphQL e Data Export têm duas definições; somente a última
não vazia foi usada. Nenhum valor ou hash de segredo foi emitido ou alterado.
A busca de nomes em target identificou 82 fixtures e 21 referências de oráculo
de laboratório, sem export real identificado. O atestado canônico V2-041 segue
ausente. A investigação não dependeu de pedir ao usuário onde ficam as APIs.

Quatro chamadas seriais, HTTP200/curl0, 38.809 bytes de corpos no total:

| Operação | Evidência observada | Limite da conclusão |
| --- | --- | --- |
| Schema GraphQL | Query individual, params IndividualInput obrigatório, after String, first Int; input enabled Boolean e updatedAt String | A presença de updatedAt no input não prova campo temporal no node, formato, timezone, inclusividade ou garantia incremental |
| individual(enabled=true), first5 | Cinco edges/nodes, cinco IDs STRING distintos, cinco names STRING, sem ausência/nulo nos dois campos, hasNextPage=true, cursor STRING | Só a primeira página; não prova completude, nullability global, ausência, identidade global ou snapshot |
| Data Export6908 /info | 31 campos e seis filtros; tipos dos campos não declarados/reconhecidos pelo profiler; filtros temporais declarados date/datetime | Metadados não são schema tipado completo nem garantia de temporalidade |
| Data Export6908 /data, per2, página1 | ROOT_ARRAY, cinco linhas e dois IDs INTEGER distintos; três repetições físicas; status_updated_at ausente nas cinco linhas; candidato Manifesto com quatro INTEGER e um NULL | Expansão física respeita per nesta amostra; não prova relação, frescor universal, janela representativa ou integralidade |

Coletas usou 09/09/2026 como dia fechado exploratório de picks.request_date,
com timezone de planejamento America/Sao_Paulo. Não foi ratificada uma janela
representativa de aceite. Tetos: quatro chamadas, 180 segundos, timeout30s,
metadados1MiB/resposta, dados64KiB/resposta, Users first5, Coletas per2 e até
1.000 linhas físicas. Sem retry, redirect, segunda página ou fallback.
O ledger tem quatro reservas anteriores ao efeito e quatro resultados conhecidos.
O orçamento se encerrou em 4/4, sem transferência ou reutilização do B60.

Corpos, identidades, nomes e cursores ficaram apenas em memória e não foram
gravados. [source-summary.json](source-summary.json) contém somente perfis
sanitizados. As respostas não foram convertidas em fixtures reais ou export
durável. A origem funcional está localizada; ainda falta o oráculo independente
e representativo exigido para fechar a caracterização.

## Divergências e regressão local

1. **COL-SHAPE-01 — DIVERGED_BEFORE_STAGING.** A resposta real é ROOT_ARRAY.
   O normalizer produtivo suporta esse formato, mas DataExportTemplate e a
   composição operacional fixam ENVELOPE_DATA_ARRAY; o consumidor B58 também
   exige esse envelope. A regressão sintética de B62 comprova que normalizar
   cinco linhas preserva a expansão e que o caminho B58 atual recusa a forma
   antes de mapper/staging. A capacidade do normalizer isolado não autoriza
   promoção nem mudança silenciosa do release. O próximo passo técnico é
   revisar o binding/release da origem com schema/paths completos e preparar
   sua ligação test-only; não apenas embrulhar a resposta em data.
2. **USR-INPUT-01 — OBSERVADO_SEM_SEMANTICA_TEMPORAL.** IndividualInput.updatedAt
   existe como String no schema consultado. A operação V2 corrente seleciona
   id/name e enabled=true; observed_at continua técnico. Não foram consultados
   valores updatedAt, alterado documento GraphQL, habilitado watermark ou
   inferida equivalência com o caminho incremental do V1.
3. **COL-TIME-01 — PRESENCA_PARCIAL_OBSERVADA.** status_updated_at não veio na
   amostra; demais datas selecionadas no profiler vieram STRING. Isso é
   presença/tipo observados, sem validar formato, fronteiras ou frescor.

Nenhuma correção produtiva por hipótese: código de src/main, contratos históricos,
schema e migrations ficam intactos. Dois testes novos usam somente números
sintéticos e a forma estrutural observada; não contêm registro da fonte.
Reutilizam CharacterizationParserAccess, normalizer e ColetasCharacterization.

## Validação, entrega e recuperação

Sonda de uso único: scripts/probes/Invoke-Bloco62SourceInvestigation.ps1.
Autoteste offline passou dois casos positivos e oito recusas, incluindo
sanitização, expansão, limite de IDs, nulos, tipo de chave, JSON duplicado,
erro GraphQL, cursor incompleto e duplicidade. A primeira tentativa do autoteste
revelou enumeração de array unitário no retorno PowerShell; corrigida antes da
primeira chamada. Logs de ambas preservados. A tentativa de repetir a rodada
foi recusada com B62_SINGLE_USE_NO_REPEAT, antes de ler credenciais ou chamar rede.

Build Java isolado offline Java17/heap512MiB: **1405 testes, zero falhas, zero
erros, quatro skips**, incluindo os dois testes novos verdes. Enforcer, Spotless,
Checkstyle, arquitetura e JaCoCo passaram: 91,53% linhas, 76,43% branches.
Skips: três symlinks condicionais no Windows e comando Cotações opt-in não
habilitado. Prova: java-verification.json na pasta da rodada. A primeira invocação
Maven teve argumento -D interpretado pelo PowerShell; foi corrigida com aspas,
sem alterar teste ou requisito. Logs verify-01/02 preservados. Checks finais,
scanner, diff e integridade ficam em final-checks.json e delivery-checks.json;
nenhum PASS deve ser inferido se esses registros estiverem ausentes ou falhos.

Evidência privada: target/b62-source-20260910-161039/, incluindo inventário,
before/, request, ledger, resumos, logs, build isolado, diffs e receipt final.
O intake anterior target/b62-intake-20260910-155925/ permanece intacto: sua recusa
antecede a instrução posterior e esta investigação, sem ser reescrita como PASS.

Sucessão exata em manifesto.json e Test-Bloco62Investigation.ps1: quatro deltas
documentais/de validação com snapshots, preservando B61 e toda sua história.
Rollback local: revisar o diff e restaurar somente esses quatro deltas pelos
snapshots; manter evidências desta rodada. Não há mutação remota a desfazer.
Não houve SQL, UAC, runtime operacional, escrita produtiva, bootstrap ou cutover.

V2-012a/b/c e V2-041 continuam abertos; nenhum checkbox/rota AGORA novo.
Roadmap 67/115, 48 pendentes, 191 rotas abertas. Users SHADOW_UPSERT_ONLY não
prova snapshot. Q-MAN-01 permanece em EXTERNAL_HOLD. Até três próximas ações:
revisar o contrato de forma/path Coletas; definir comparação independente e
representatividade por rota; tratar evidência real de V2-041 com seus responsáveis.
