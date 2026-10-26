# Continuidade 0614 — Caminho Windows diagnosticado; regressão isolada

Data: 26/10/2026. Root único executor; delegações somente em arquivos. Nenhum processo PM2 foi encerrado, nenhuma estrutura SQL foi alterada e nenhuma consulta real nova à ESL foi feita nesta rodada. Commit/push continuam obrigatórios.

## Causa local reproduzida com a função real

R52 foi revisado, mas parou antes de Original/claim/Java porque o sexto processo selecionado terminou naturalmente. Root reconfirmou pessoalmente as cinco tuplas originais e escolheu uma ocorrência nova do R50 já revisado e imutável, sem fallback automático nem reaproveitar uma ocorrência encerrada.

R50/0613c fechou Original1 em107.904ms, pico121.212.928bytes, sem timeout e antes de iniciar Java. O diagnóstico retido aponta GUARD_START/PRODUCER_START; a classe exata permaneceu OTHER_EXCEPTION_TYPE. Não reescrever essa observação como uma exceção histórica mais específica.

Uma sonda finita chamou o mesmo CampaignJvmGuard, sem alterar guard/policy/produtor/limites. No canal com282caracteres, PRODUCER_START reproduziu NotADirectoryError/errno20/winerror267; no canal com147, start/sample/finish e assurance passaram com11amostras, produtor fechado. Original0,2.198ms,95.928.320bytes; zeroJava/API/SQL. Prova `RootProducerPathCausalQualification0613d.json`, SHA256 `514D9F9D914FFC523F8DCDFAC9AC62EE5D87E5193A13D2FF9C47CAA6034C40D1`.

Root materializou os mesmos31arquivos próprios e o mesmo selo R50 em uma pasta física curta `fr50`, sem junction/reparse, sem copiar runs e sem mudar código/manifesto. A reconciliação posterior conferiu32pins, inventário e contenção de caminhos; os erros não terminantes da expressão Split usada na cópia ficaram registrados, e a reconciliação usou erros terminantes e separação correta dos segmentos. Revisão independente conferiu também87pins externos, os ancestrais e o inverso exato do driver. O novo canal tem179caracteres; o arquivo temporário do produtor,194.

Selo preservado: `0905365FED4B67723B7650ADD0C8C4DBB8D1ED1B6670D1D4ADA3BE17D479A937`. Revisão `r50-short-physical-review-files-only-0613d/review.json`, SHA256 `BFDDBC2283133C87CDEE241F84C6ED1EE71F304660F21CA4F43AA9D8294198B4`. Sem aumento de heap, prazo, retry ou relaxamento de identidades.

## Resultado real da execução curta d1

- Compilação completa de778arquivos Java principais e704arquivos de teste, sem alterar os corpos dos casos.
- JAR novo `153BA720FB224D59B6EDB196AF5D0D0DC17F139B5EDED32FA0DDDC38E91663A4`,1395classes/55resources/oito bibliotecas. Três entradas de classe alteradas, um recurso Cotações adicionado, nenhuma remoção e demais recursos preservados.
- Primeiro bloco:150testes encontrados,149aprovados, um falhou; zero ignorados/abortados. Os outros50casos planejados ainda NÃO executaram.
- Método sem sucesso: `RuntimeOperationalExecutionTest#bothOfficialHandlersUseRealPipelineAndRecoverWithoutSourceComposition`.
- Quatro reports guarded reais: três compilações exit0 e o runner exit1; filhos conhecidos fechados, barreiras frescas e produtor fechado. Não houve repetição da falha de caminho ou recusa CIM nesses quatro reports.
- Original1,220.094ms, pico663.883.776bytes, sem timeout. Nenhuma qualificação positiva de200, Final8, pacote final, banco, adoção ou implantação.

Prova `RootR50ShortBuildRegressionFailure0613d.json`, SHA256 `766E1EB8EB5B07B27EA04D95E5BE7EAD6FA4831CB8C1C845B012E15350ECB327`. Original `ED9E4D074009FC8682DDE5A25BE89F9E5D58C6FF912012F5F1DB529EE1724A3F`; relatório de regressão `FCF5AD6E977C400FEC4DC72DA9A1F19DAC49E7C32CBAF25AFB5E0E52C4F3BC34`.

## Próxima correção delimitada, ainda não qualificada

A leitura dos arquivos demonstrou uma incompatibilidade: RuntimeBootstrapTestFixture usa o INFO histórico de Coletas com seis filtros sem prefixo; o catálogo atual P2 exige cinco picks.* e um scopes.by_updated_at. Isso demonstra uma recusa possível do Gate, mas a exceção/linha da falha d1 ainda não foi retida. Root prepara diagnóstico do método isolado; Contratos prepara a análise e uma correção mínima revisável, preservando o recurso histórico, DATA31 e a exigência de SUCCESS. Não remover o teste, reduzir a contagem nem alterar produção apenas para aceitar o mock antigo.

Receitas full624/Clock9 permanecem inertes; Own200/Final8/produto qualificado/bindings continuam pendentes. Banco prepara o patch mínimo do matcher Raster e dois causais em arquivos, sem executar DDL ou emitir autorização.

A análise dos dois Excel continua636códigos de viagem distintos, sem comparação real: origem/filtros/fuso/namespace aguardam resposta. Fretes/Local/P3/Users possuem plano de catálogo somente leitura preparado, ainda sem execução atual. ÚltimaSource permanece INFOColetas0607/delta1; contadores globais não finais; UserssemEOF. Etapas3/4 e extração completa continuam abertas.
