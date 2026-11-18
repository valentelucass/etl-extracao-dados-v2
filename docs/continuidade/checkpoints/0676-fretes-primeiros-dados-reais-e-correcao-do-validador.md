# Fretes: primeiros dados reais recebidos; validador de amostra em correção

Atualizado em 04/11/2026. Coletas mantém os três períodos com todas as31colunas aprovadas. **Fretes ainda não tem período integral aprovado (0/3).** A nova consulta recebeu20registros reais, com115campos observados na resposta; os três Excel têm114colunas. Campo adicional da API é preservado e não reprova a comparação por si só.

## Evidência executada

O produto privado `6953F53B63F789D54B055BB250E79FC0055F42DD1F466F8F4344C2DDD0F2B034`,3474059B, passou66casos Java nomeados e uma suíte executável completa de per20. As funções PowerShell efetivas passaram30casos. Os1483itens não alterados,30classes Coletas e manifesto foram preservados. Aceite Root `outputs/root-acceptance-fretes-per20-product0675-02.json`,7540B/SHA B3576351605D868B55E73171043388D14CE9855BCFD06123D3F82534ED924973; adoção host `outputs/root-generic-gate-host-adoption-fretes0675-01.json`,3813B/SHA EB6D975325213A0F03C2C0E07C8F566400358EE786DEF4EB3F775BFEA935BEDA. Essas provas locais não são comparação de conteúdo.

O Native atual0675 passou dez sequências e duas barreiras novas com sete identidades Java selecionadas explicitamente pelo Root. O processo11768 substituiu42568 por mudança externa observada; nenhum processo externo foi encerrado. Original0/10675ms/152535040B, todos próprios e pipes fechados. Prova `work/fretes-native-seven0675-run01/output/native-qualification.actual.json`,4367B/SHA5B30A33C30CF993ECBAA4112CFCA8FAC4CDB4302BBBBB27B32BEE344840F8786. Reobservação continua obrigatória em cada envio.

Unidade nova `fretes-day19-real0675-01`, execução85bf1f45-4323-41a1-8100-32eddea0e170: Main genuína, DataExport6389, filtro Data do frete19/10/2026, per20/id asc. Recebeu INFO200/15768B e DATA1 HTTP200/76287B, ambos completos e preservados. O processamento parou em DATA_RULE por `ContractDriftException`; não houve segunda página nem retry. Original1/30208ms/376832000B; Java26676 encerrado com stdin/stdout/stderr/EOF conhecidos. Duas reservas e duas conclusões HTTP, zero registros projetados, vinte registros RAW retidos, nenhuma cobertura terminal. A unidade/grant não pode ser repetida.

Um diagnóstico local novo, sem API, executou os parsers atuais sobre essa resposta preservada: **20linhas/20IDs inteiros/zero descidas na ordenação, zero mudanças incompatíveis e11mudanças compatíveis não aprovadas**. O baseline antigo só havia observado nulos em oito campos que agora contêm valores escalares; outros três campos antes preenchidos também aceitam nulos. Não é diferença de valores contra o Excel nem erro comprovado da origem. Relatório técnico sem valores `work/fretes-response-diagnosis0675-run01/diagnosis.json`; Original0/4990ms/271749120B, javac+Java0 e todos próprios fechados. Correção estática em andamento para aceitar essas variações legítimas com alertas, preservando tipos/raiz/chave/limites e impedindo promoção semântica automática.

## Histórico e orçamento

Os dois efeitos HTTP novos foram reconciliados por um único CAS, preservando lexicalmente todos os valores anteriores. Canônico atual **7C3F3A0CCE264FF30C983B8B27BD3DE2E9DEBA5ED4141378798701036B0E6B98/709134B**, ACL B4E214EB…, Root0658, vaga0. Há16pares históricos preservados e dois parciais Fretes separados:422/timeout e200/parada local por variações compatíveis. Quatro HTTP reais ao todo nesses parciais; a captura completa anterior de Coletas não foi reetiquetada. Não restaurar86A86/C5F nem reutilizar caller/grant.

Novo efeito `outputs/root-fretes-partial200-effect0675-01.json`,2968B/SHA9C05E3FD0CF5168D7A62C747750696E4B8D5ADC3552432A8809890D7D7543330; reconciliação `outputs/root-reconciliation-fretes-partial2000675-01.json`,1284B/SHAAFD2760BD2A7189AEB2DC84D0AAA413C3B85CEF156D919DFEC583313B76A2884. A cadeia de metadados `outputs/root-fretes-closed-partial-chain0675-01.json`,2932B/SHA193E7153868FF3CF44B843ACA1D298F73BA4B45A364ADD34F17F2696137EA811, liga86A→C5F→7C3F sem adicionar aceite completo. O suporte executável dessa cadeia ainda precisa ser qualificado.

Uma leitura local protegida contou todas4693ocorrências do Excel19–25, sem descarte:19/10 tem946linhas/850IDs;20/10 tem994/899;21/10 tem915/819;22/10 tem911/806;23/10 tem905/799;24/10 tem22/14. Relatório agregado `work/fretes-excel-day-budget0675-run01/diagnosis.json`; Original0/4743ms/71540736B, Python fechado. **per20 exigiria44páginas DATA com terminal no dia19 e não cabe no limite16.** Root decidiu preparar per60 firmado adicional, que estima15páginas preenchidas+terminal para850ou899IDs, mantendo16DATA/17HTTP/210sSource/300sOriginal1GiB e3s após cada resposta. O desempenho de per60 ainda não foi medido na ESL; não há ampliação automática de orçamento ou garantia de completude.

## Próximas ações

1. Qualificar causalmente a correção do validador e a cadeia de efeitos fechados, com per20/60/100 vinculado à revisão e ao pedido; preservar falhas, extras e limites.
2. Comparar imediatamente os20IDs já retidos com todas as114colunas correspondentes do Excel, incluindo associações e multiplicidades. Esse relatório deve dizer cobertura parcial e nunca aprovar uma semana.
3. Após os pré-requisitos, executar nova captura delimitada para completar o intervalo e comparar todas as ocorrências/colunas/totais, repetindo nos três períodos solicitados antes de escolher outra entidade.

Os chats **ETL V2 — Correções da extração** e **ETL V2 — Fretes: investigar timeout da API** trabalham apenas em arquivos estáticos na seçãoETL V2. Root continua único executor pesado/API/navegador. V1 humano pausado; SQL, produção e publicação/consumo real do dashboard permanecem intactos e pendentes.
