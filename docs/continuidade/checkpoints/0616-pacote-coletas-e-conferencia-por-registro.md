# 0616 — Pacote aprovado nos 200 casos; conferência externa por registro

Continuação de0615 em26/10/2026. O objetivo continua sendo extrair as entidades,
comprovar a gravação e conferir os valores com a ESL ou os Excel no mesmo recorte.
O usuário reforçou que a comparação precisa ocorrer registro por registro;
contagens iguais não comprovam fidelidade. A regra foi incorporada ao runbook
`docs/runbooks/conferencia-esl-somente-consulta.md`.

## Resultados executados

| Unidade | Resultado real | Limites e alcance |
| --- | --- | --- |
| R54 | 42 modelos, quatro casos Windows e Capture6 aprovados; Original0 | Perfil literal de seis processos então presentes; nenhum processo encerrado pelo ETL |
| fr55/package-g1 | **200 casos aprovados**:150 regressões,12 Manifestos,14 perfis,17 Cotações e sete de integridade; Original0/404.402ms/pico850.997.248bytes | Original1GiB/8min;778 fontes principais e704 de teste compiladas; nenhuma Source ou conexão SQL |
| Revisão das oito ocorrências reais | Três compiladores e cinco runners fecharam0; guardas e produtores fechados, barreiras atuais e mutexes balanceados | Revisão dos recibos dessa execução; não é reconstrução de imagem nativa por hash |
| Coletas Final8 | **Oito casos atuais aprovados no JAR final**, Original0/10.555ms/pico520.015.872bytes | Os quatro casos históricos não foram contados como novos; sem overlay de produção ou SQL |
| Raster V5b | **50 casos distintos aprovados**:48 anteriores reexecutados e dois causais; Original0/14.904ms/pico551.415.808bytes | Registry permanece vazio; nenhuma SQL, DDL, captura, ENGINE ou AFTER qualificada |
| Full624 original | 624 encontrados,623 aprovados e um falhou; Original1/114.138ms/pico657.162.240bytes | Nenhum timeout ou falha de memória; Clock9 ainda não executado |
| Diagnóstico ar55b | Falha original retida como AssertionFailedError em ArchitectureRulesTest.java:688; Original0/110.136ms/pico449.556.480bytes | Um teste encontrado/iniciado/falho, zero aprovados; exit0 significa retenção do diagnóstico, não sucesso do teste |

O pacote contém1.395 classes de produção,55 recursos e oito bibliotecas.
Seu JAR é `153BA720FB224D59B6EDB196AF5D0D0DC17F139B5EDED32FA0DDDC38E91663A4`.
A composição mantém os quatro inputs de produção e as duas fixtures de0615;
as falhas e execuções parciais anteriores permanecem preservadas. O produto ainda
não foi adotado no código canônico, implantado ou admitido para retenção.

## Causa da falha de arquitetura e correção delimitada

O helper `productionClasses()` percorre o CodeSource de Main como diretório.
Quando esse caminho é o próprio JAR, não encontra as classes internas. A falha
real isolada ocorre na asserção que recusa entradas obsoletas da allowlist.
A proposta mínima abre o mesmo JAR como ZIP somente para leitura e fecha o
stream e o arquivo. Preserva o caminho por diretório, o classloader, os filtros
de fonte e nomes internos e todos os26 corpos de teste, asserções e allowlist.

Revisão independente confirmou patch e inverso exatos. O Root aprovou somente
esse helper de teste em um diretório novo de classes de teste, mantendo o JAR
e todo o SourceStage de g1 byte-exatos. ScopeR56 ainda precisa executar todos
os624 casos de novo e depois Clock9; não converter623 anteriores em PASS624.
Os200 e Final8 reais permanecem provas do mesmo JAR, sem reclassificá-los como
novas execuções. Bindings e integração finais continuam pendentes.

O diagnóstico anterior ar55 foi recusado antes de Java porque um processo
externo selecionado terminou naturalmente. Não houve replay dessa unidade.
O Root reconciliou pessoalmente as cinco tuplas originais e selecionou o perfil
R37 literal5 já qualificado, com todas obrigatórias e guardas atuais. ar55b usa
fr53 somente como preparação nativa e fr55/g1 como sujeito de teste; esses papéis
estão ligados separadamente. Não há perfil genérico, processo opcional, parada
de PM2, relaxamento de limites ou fallback automático.

## Critério de fidelidade dos dados

Comparar cada chave e ocorrência, preservando repetições legítimas, e os campos
comuns entre resposta extraída, leitura real de volta do banco e referência
independente. Igualar entidade/template, namespace da chave, grão, período,
campo de data, fuso e demais filtros. Registrar ausentes, excedentes e diferenças
de campos; detalhes de negócio ficam em artefatos privados. Colunas sem
referência demonstrada ficam explicitamente não conferidas.

Os dois Excel são a verdade fornecida para seus intervalos de viagens:290 e346
linhas,636 códigos distintos. A origem exata, chave de correspondência e filtros
ainda precisam ser demonstrados. Raster é hipótese de correspondência;
Localizador8656, Fretes Data Export6389 e Fretes GraphQL não são intercambiáveis.
Nenhum confronto externo de valores foi concluído.

Manifestos315 e Cotações287 têm provas históricas de Source, staging, leitura
de volta e rollback, sem dados de negócio retidos. Isso não representa aceite
externo de fidelidade. Coletas ainda não executou seu fluxo atual completo no
banco. Usuários continua sem EOF. Etapas3 e4 e extração completa permanecem abertas.

## Evidência privada e próxima execução

Na campanha externa `quatro-etapas-20261024-01`:

- `RootFinalR55Own200Qualification0615.json` (`A0E0B216...`),
  `RootR55NativeEightActualReview0615.json` (`A45A417F...`) e
  `RootColetasFinal8Qualification0615.json` (`5D264FEF...`).
- `RootRasterV5bOwn50Qualification0615.json` (`0ACE277D...`).
- `RootFinalR55Full624OriginalClosure0615.json`,
  `RootArchitectureFailureTraceQualification0616b.json` (`E06170FF...`),
  `architecture-helper-review-files-only-0616/review.json` (`B5AA3BE3...`) e
  `RootScopeR56TestHelperDecision0616.json` (`4FAF860B...`).
- `RootCurrentFiveAssessment0616.json` (`FEC023FE...`) e
  `RootRecordFieldFidelityUserDecision0615.json` (`57D54239...`).

Próximas ações:1) concluir624/Clock9 e ligações7/111/4/Main7 do produto final;
2) ligar Coletas ao banco e consultar os contratos atuais das demais entidades;
3) completar as travessias e confrontar registros e campos com ESL/Excel.
Falta de referência externa deve identificar o campo/filtro/chave concreto,
sem substituir a prova por contagem ou inventar equivalência.

Nenhuma Source ou SQL nova nesta unidade. Última Source continua INFOColetas0607,
delta1; contadores globais não são finais. Root é o único executor; ESL somente
consulta e download. Preservar versão SQL, contas, credenciais, permissões,
schema e processos PM2. Nenhum deploy, cutover ou DDL autorizado por testes.
