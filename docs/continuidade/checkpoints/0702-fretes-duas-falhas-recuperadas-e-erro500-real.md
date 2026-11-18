# 0702 — Falhas repetidas recuperadas; HTTP500 real ainda exige cobertura

A Source `fretes-day19-real0702-01`, execução `a381fa30-fa2b-47fe-97c8-7c9860a3a787`, consultou somente 19/10/2026 com `per=60`, produto DF1F, perfil de repetição V2 e limites preservados. O teste real demonstrou duas recuperações que antes não estavam comprovadas na ESL:

| Página | Respostas em ordem | Resultado |
| --- | --- | --- |
| 2 | 422 timeout; 422 timeout; 200 | Recuperada após as esperas de 10/20 s. |
| 4 | 422 timeout; 422 timeout; 200 | Recuperada após as esperas de 10/20 s. |
| 5 | 422 timeout; 500 | Encerrou parcial; V2 autoriza repetir somente o timeout422 exato. |

Foram 11 requisições físicas completas: cinco respostas200 (INFO e quatro páginas DATA), cinco respostas422 exatas e uma resposta500. Houve cinco repetições confirmadas, zero desconhecidas e 260 ocorrências projetadas. Não houve terminal vazio, aprovação de completude, novo Excel ou comparação100%. Não afirmar 240 IDs antes de medição independente.

## Evidência da nova falha

O Original 50940 encerrou1 em149.132 ms, pico395.517.952 bytes. Java64408/1 fechou com stdin/stdout/stderr e EOF conhecidos. Controlador registrou11reservas/11conclusões e auditoria completa; o coletor registrou apenas10recebimentos qualificados, pois a última resposta falhou na conclusão semântica. O pai restaurou exclusivamente o seu estado e fechou o slot.

Uma inspeção independente sob Original63156/0,2.377 ms,pico59.625.472 bytes, reabriu e manteve todos os arquivos declarados, conferiu os hashes e publicou `work/fretes-partial-inspection0702-run01/inspection.json`,31.688 bytes/SHA-256 `7E113B75B1B742C9DC67136AB53A9F37993A24C23EF97FA22B0C12E8467C5D05`.

A tentativa11 da página5 recebeu HTTP500, corpo integral46bytes/SHA-256 `D7E6FDA7F55A85A52F7291073B2424FA859FAF95C5B69F88F924D020B2817880`,subscriberEOF verdadeiro,cabeçalhos em2.404 ms e corpo completo em2.405 ms. O corpo coincide com o JSON de erro `{"status":500,"error":"Internal Server Error"}`; o hash do literal foi comparado com o pin medido dentro do Original. O recibo registra READ_BODY,RAWpreservado,closeSucceeded=false e gateAfterSucceeded=false. Os fatos físicos registram resposta completa,status conhecido500,11ªinvocação confirmada e46bytes observados/retidos. Isso não é um request local que expirou aos30s. A causa interna do servidor não é exposta pelo corpo.

A inspeção é diagnóstica: não verifica assinatura, cada valor RAW→JSONL ou toda a identidade PID/birth. Uma auditoria corporal e de assinatura está em revisão antes da reconciliação; o primeiro candidato foi retido por falta de vínculo explícito entre a admissão assinada e a captura. Nenhuma nova Source antes de auditar e reconciliar os11efeitos.

## Estado e trabalho autorizado

Antes dessa Source, a reconciliação0701 concluiu e o Recover idempotente não alterou os hashes nem duplicou26efeitos. Base canônica atual restaurada `A987A224E8003FFB963736A1C7F270CE1983666F137A73B8A54BEF0B65645235`,720.581 bytes/ACLB4/Root0658,18pares completos e11fatos parciais,slot vazio. Onze HTTP da Source0702 estão conhecidos e pendentes de reconciliação. Controle86929A18/30.240B,typed94795F85/1.880B,cadeia1386E315/19.813B(37reservas adicionais). Não restaurarABC8 ou usar grants consumidos.

Censo atual Original64916/0 e seleção Root explícita de sete tuples5B7A297B; Native0702 qualificou10sequências e duas barreiras,qualificação0B5AB373/4.439B,baselineE95DD88E/8.275B,reciboOriginal95A1456D. Nenhum processo externo alterado. O preparo paramétrico foi conferido pelo Root contra os quatro pins exatos no manifesto gerado, review0E7674AE/2.322B; o primeiro precheck usou o nome de campo inexistente latestMainTyped e foi corrigido para latestMainBasis antes de qualquer Source.

Dispatch7BBAE880/release2D92C36B/manifest5511CF37/dynamic59568CD1/once823B3E3A. Callback Root assinou somente após READY; processo Node fechou0. Source04:03:00.055Z–04:06:30.055Z,Original máximo04:07:48.834Z; execução terminou antes desses limites devido ao500.

Writer prepara perfil V3 opt-in para500 exato e422 existente, preservando V1/V2. Não adotar componente incompleto. Deve integrar transporte/observação de Retry-After/coletor/gate/assinador/validadores e testar422→500→200,erro persistente e429 sem repetição antes de compilação,adoção e nova tentativa. Recusar Retry-After presente é seguro dentro do escopo proposto; não ignorar espera do servidor. Outros5xx e transporte desconhecido não ganham autorização por analogia.

Limites mantidos:47HTTPincluindoINFO+repetições,intervalo≥3s após resposta,request≤30s,Source210s,Original300s/1GiB,duas repetições por página/seis por unidade e esperas10/20s. Não ampliar para forçar sucesso.

Fretes0/3semanas,último par completo112/114colunas e48/50totais; duas colunas de custos ainda divergem em11IDs. Coletas3/3. V1pausado. Sessão do navegador interno está no login; pergunta de autenticação pendente paraExcelrecente,não para o trabalho local. Campos adicionais da API não contam como erro. Não declarar solução universal nem aprovação100%.

## Auditoria corporal e reconciliação concluídas0703

O candidato V2 de auditoria corrigiu o vínculo assinatura/captura/admissão/controlador e foi revisado estaticamente sem novo bloqueador. Root congelou os arquivos no índice660D5FC0 e executou módulo/config02(42BEAD8E) sobOriginal23604/0:5.855ms,pico96.026.624B;Python38568/0/3.033ms/32.628.736B eNode78100/0/210ms/17.944.576B,todosSTDIOfechados. Relatório113B57EBECC8C07FEF93236433C7AAC4417D6849F4B1B87517F885D3439A0B31/31.060B confirmou260ocorrências/240IDs,conservação de cada RAW→JSONL→locator,114títulos/aliases INFO,cinco422exatos e500terminal46B/EOF/closefalse/gateAfterfalse preservados. A assinatura Ed25519 existente foi validada e vinculada. Nenhuma equivalência Excel inferida.

Generator8567/preparador95CF passaram com validador realDE97/FctxVariablePartialV3: status500terminal aceito para fechar efeitos conhecidos, sem habilitar repetição500. Preparado7294BEFB/2.481B,efeito8A00045E/2.595B. Reconciliador6FFCF004,índiceRoot06AAFC3C,revisão separada sem novo bloqueador:CAS eRecoveridempotente ambos0,nenhum dos11efeitos duplicado e todos os valores históricos lexicalmente preservados.

NOVO CANÔNICO8614C6F9FD1EB8BE2DC0C4C949EB86B345B155B63C50AB0BCB58F4DFA6D16884/721.387B/ACLB4/Root0658,18complete12partial,slot0 e nenhumHTTPconhecidopendente. Controlework/fretes-partial0703-transaction-v1/current-control.json6775CF0C7ABFFFD5DBB9FA8D0D53206AB54521A62E317C02399951FF58278ECB/30.254B;typed81A3755E/1.894B;cadeiaF9B90050/24.650B,5passos e48reservas adicionais. Aceite11AD08732C04757F2F795288A7350159E665A04477A5727BAC45D473C77685C8/2.050B;intenção0F118089,committed54E27872/971B. Backups preservados. Não restaurarA987/ABC8.

Writer integra V3 nas16famílias Java existentes; componente isolado77374C1E não é produto. ContratoV3FRETES6389_EXACT_422_AND_500_0703_V3 mantémcamposV2 e acrescenta additionalStatus500/additionalBodyBytes46/additionalBodySha256D7E6/retryAfterPolicySTOP_IF_PRESENT. NenhumaSourceV3 autorizada antes de integração completa de transporte/coletor/gate/assinador/controlador/validadores,qualificação Original,review,fingerprint eNative atual. Não afirmar solução completa antes da nova execução real e comparação de conteúdo.