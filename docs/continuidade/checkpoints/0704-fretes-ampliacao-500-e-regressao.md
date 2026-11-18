# 0704 — Fretes: ampliação para 500 e testes de regressão

## Estado e limite da conclusão

Trabalho em andamento em 05/11/2026. Fretes permanece com **0/3 semanas integralmente validadas**. A recuperação dos timeouts 422 foi observada em extração real, inclusive duas falhas consecutivas nas páginas 2 e 4. A extração 0702 parou depois em um erro 500 da ESL na página 5; os dados parciais foram auditados e o efeito HTTP foi reconciliado. Isso não representa captura completa nem aprovação das 114 colunas.

O corpo 500 real tinha 46 bytes e SHA-256 `D7E6FDA7F55A85A52F7291073B2424FA859FAF95C5B69F88F924D020B2817880`. A nova política só admite esse caso exato, com corpo completo, fechamento comprovado e ausência observada de Retry-After. O corpo genérico não demonstra a causa interna do erro na ESL.

## Política candidata V3

Perfil `FRETES6389_EXACT_422_AND_500_0703_V3`: até duas repetições por página e seis por execução, esperas de 10 e 20 segundos, intervalo mínimo de três segundos depois da resposta concluída. Mantidos 47 HTTP, 30 segundos por requisição, 210 segundos de Source, Original de 300 segundos/1 GiB e páginas com per=60. V1 continua pausado. Entidades continuam em Data Export; GraphQL somente usuários.

Qualquer 429, Retry-After presente, corpo diferente ou incompleto, ausência de headers conhecidos, falha de fechamento ou limite esgotado interrompe o fluxo. Nenhuma falha dá permissão para publicar captura incompleta como sucesso.

## Matriz em execução

As 29 suítes Java anteriores foram mantidas, incluindo conexão lenta antes de headers, corpo que não termina, desconexão antes dos headers, interrupção no corpo, 429, recuperação 422, limites de páginas/tentativas e refusas do gate. Duas suítes foram adicionadas para V3: cenários sintéticos e conexões locais com 422 seguido de 500, 500 persistente, corpo 500 diferente, Retry-After e comprovação das esperas reais. Não se trata de teste contra TLS ou infraestrutura interna da ESL.

A matriz PowerShell acrescenta validação de políticas, provas corporais, parciais e capturas completas V3 às regressões anteriores e quatro verificações do estado atual: 30 invocações sintéticas e quatro contra metadados atuais. O runtime ainda precisa repetir as 14 situações anteriores do controlador, dez recusas Native, a integração sintética gate/collector/controlador V3 e a política Node.

## Resultados preservados

1. PS QA0704 run01 **falhou**, Original PID 29300, saída 1, 11.017 ms, pico do job 94.871.552 bytes, sem timeout. A política inicial passou, mas o parcial legado sem retryPolicy encontrou acesso incondicional à propriedade em FctxVariablePartial. Estado e slot foram restaurados; Source/HTTP/SQL=0. O snapshot e os recibos da falha não foram sobrescritos.
2. Corrigida a guarda de histórico de retry nos quatro helpers. O perfil V3 ainda exige esquema UNKNOWN e prova corporal. Índice host candidato `55CCCABAA8CAEC1D17C9A02CCBB17F54A16811DCE90748FB5BACCAC6E8D60187`; snapshot Root PS run02 `A41F861E5B2962F52D5E340CD9D85FE1D00EFB423DA58B09AD3B4A1C8A133F99`. **As 34 invocações passaram**: Original PID 14972, saída 0, 41.216 ms, pico de 203.735.040 bytes. Worker `work/fretes-retry500-ps-qa0704-run02/worker-result.json`, 12.972 bytes, SHA-256 `456F3ACD5A6388E936AD5F61F9F9CDD616B780FC0B0063141487CD37183A88D9`; fechamento e restauração do estado confirmados.
3. Java candidato final do Writer `index-proof0705-final.json`, SHA-256 `AD436649528E5C43DC28947BC6FF08559D0064ADCC9B5D7A4EFC1A9A093EADD6`, congelado em snapshot Root `BFCEFEF44B54D462087752F374DFC9C5045D361EEDFE1AEBE6A549D8F38485A6`. Corrige contadores 422/500 individuais e 422 ausente como zero apenas em V3; malformados são rejeitados. **As 31 suítes passaram**, com três javac e 16 famílias: Original PID 50516, saída 0, 281.432 ms, pico de 418.742.272 bytes. Todos os filhos/STDIO fecharam; estado restaurado. Worker de 23.745 bytes, SHA-256 `D27E1A7263B4396612CED14AC3E9BB16C4433C6ED38A15C6920EBBB1459908D5`. Produto gerado, ainda não adotado: 3.533.574 bytes, SHA-256 `08831F694A0CA09B309D00FA0CFA88A172F312C4B86BA5BC73368D872DBBAB5B`. O loopback V3 levou 80.705 ms, PID 45396/0, STDIO fechado; não é chamada à ESL.

## Estado canônico

Referência limpa antes/depois de cada job fechado: 721.387 bytes, SHA-256 `8614C6F9FD1EB8BE2DC0C4C949EB86B345B155B63C50AB0BCB58F4DFA6D16884`, ACL `B4E214EB2D92C1A0FBFF90AB2BDF72A3CCCF367AC492C02D32DB5B44603008A0`, 18 completos + 12 parciais. Durante execução autorizada somente o slot próprio muda. Zero HTTP conhecido pendente após reconciliação 0703. O produto V3 ainda não foi adotado.

Para conclusão faltam resultados efetivos das qualificações, auditor corporal V3 funcional, adoção/fingerprint/Native atuais, nova captura completa e comparação de conteúdo com Excel próximo no tempo. Colunas extras da API são permitidas; comparação exige os valores de todas as colunas do Excel, incluindo multiplicidade, linha e totais. Não declarar 100% a partir de quantidade de linhas ou colunas.

## Qualificações adicionais concluídas

Runtime run04 passou: Original 51064/0, 50.610 ms, pico 364.859.392 bytes; 14 casos legados, dez recusas Native sintéticas, integração mista 422→500→200, três javac e política Node. Worker 22.020 bytes/SHA-256 `D506531F2C2B6C00EF6588958156FA84AF0A6669F1F8F8FFCE43DABE9458CE19`; configuração `22E0BD7B6889AE6CDADF04F4596B1A735BBFB6F3AB3E7BCA40D790ED58AB5C3C`. Três verificações adicionais de vínculo de candidato/papel também passaram. Run02 falhou por helper de teste errado; run03 verificou os 24 primeiros casos e o misto, mas falhou ao contar o diretório auxiliar de captura como segundo caso. Falhas foram preservadas. O helper correto do controlador é **RootFretes.CurrentLease.ps1**, separado de Common e CurrentContext.

Adoção offline Root: host `outputs/root-fretes-exact500-host-index0704-01.json`, 4.536 bytes/`5AD3FB3646C3F82D6FAC216AEBE36B96D0997741FCA4E360A66DF169C4BD757E`; produto `root-acceptance-fretes-exact500-product0704-01.json`, 2.386 bytes/`E789F05247A0D319AC05FD118207119C22AE3994805F34AE5F3711FB9AFB4328`; adoção host `root-generic-gate-exact500-host-adoption0704-01.json`, 4.015 bytes/`5AA1FE3D8328ECE0C692FFDB31C473788FE7C3A8956325C148A86096F5F1C1A0`. Nenhum concede autoridade Source sozinho.

Fingerprint real atual passou: Original 54492/0, 9.470 ms, 62.730.240 bytes, 38 classes/16 famílias/14 compartilhadas. Configuração `work/fretes-v3-fingerprint0704-prep01/configuration.json`, `2AC371E0929DB19BAB2A1CFE57B4C97A5B99A27A289DE1D306E469D3AE55DF55`; join atual `7434FB066A84C76F1769EA2AAC0D61CCE0CE41C92C58EDDFF9AF80FD87FCC647`; runtime47 `63D77D5CE476D83CD606D26A6C2E9B63DA7137BCC5AD83F81D3190E34C25BAB2`. Seleção de fontes é diretamente E90AF6; removida exceção histórica E22.

Auditor corporal V3: revisão corrigiu vínculo da assinatura/callback com caller/produto/host/class join/FP/qualificações, formato parcial de cinco campos e enumeração Hashtable.Values nos wrappers. Índice `outputs/fretes-v3-body-audit0704-DRAFT/index-DRAFT.json`, `D7D80FE9C39FF0214A1C87A2BFD45D40C02723FA38A639CD2383543F786C38EE`. QA sintético `5C70B7B24EB3F313F9E36DDB5F4E36ECC7A8853040D23C099806FE5BD14B3119` passou sob Original 37504/0, 4.401 ms, 78.991.360 bytes: 21 componentes Python, formato PowerShell real e quatro contratos Node. Worker `50C4CF8C3D73B5715A3A999B2729E6DAFC3123EF08FEB1076D38E5D0306036F8`. Esses testes não são execução integral dos workers de auditoria nem Ed25519 real; F825 público permanece verificador já qualificado e será usado na captura real. Não há timestamp absoluto de cada chamada no produto: medições live de gap/backoff ficam UNKNOWN, separadas da conservação corporal. As esperas foram medidas nos testes de conexão local, sem afirmar medição de cada Source.

## Nova tentativa real e mudança no ambiente

Source `fretes-day19-real0704-01` assinada parou no primeiro lease Native antes de INFO: `FRETES_CURRENT_EXACT_SELECTED_COUNT`, zero HTTP/reservas/despachos/registros. Original 53072/1 e Java 71360/1 encerraram com STDIO; estado restaurado. Fechamento Root sem efeito HTTP: `outputs/root-fretes-zero-http-closure0705-01.json`, 1.930 bytes/`5509161C60677F35C8444C5F47988E6C68F0D70555CF18CAAC18D2F8F3035B9C`. Nenhum parcial com efeito foi adicionado ao canônico.

O censo anterior tinha sete JVMs; a JVM 77176 encerrou antes da tentativa, deixando as seis identidades antigas intactas. Root leu censo completo novo `work/fretes-current-jvm0704-run02/output/current-jvms.json`, 2.026 bytes/`619D363A967873221B19F0842694FBC8D68013A208BEF2CDCAEEFF270763D1A4`, e selecionou pessoalmente os seis tuples. Seleção nova `outputs/root-exact-background-jvm-selection-fretes0705-01.json`, `FCC6DAD293A86562720F7E41910A7F0DF9200A01FEFE312953214422EA7433A4`; Native atual `work/fretes-native-six0705-run01/output/native-qualification.actual.json`, 4.433 bytes/`64357273E2A85BF0FD6898BC73FF9B30DD931A23D1D579A8C346D8D7CB51AFEA`. Nenhum processo externo foi alterado e a proteção não foi afrouxada.

Source **fretes-day19-real0704-02 está em andamento** neste registro. Caller READY `F5EB29731C803DBB2E153E70AA902302039431E11A4744D52D0819F26DF08771`; despacho `24BF320045C3D712FCFC39EF4C2871BAFF19A97B4AA9206E915401699DA70D70`. Root assinou somente após READY: janela Source **05:24:15.9226497Z–05:27:45.9226497Z**; Original até **05:29:04.4498918Z**. Parent tool sessão **58572**; watcher **96300 já encerrou 0**. Original PID 34752, Java 17536. Oito páginas de dados aceitas, nona despachada na última observação; resultado final pendente. Não executar outro Original/pesado até fechamento, depois auditar e reconciliar todos os efeitos antes de outra Source. Base limpa continua 8614/18 completos+12 parciais, mas o slot próprio está ocupado enquanto essa captura trabalha.
