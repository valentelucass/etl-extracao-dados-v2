# 0615 — Fixture de Coletas validada; Excel como referência

Em 26/10/2026, o usuário confirmou que os dois relatórios Excel fornecidos
representam a verdade no intervalo apresentado. São relatórios de viagens:
290 e 346 linhas, com 636 códigos distintos na união. Servem de referência para
conferir os registros e campos do mesmo recorte. Essa decisão não confirma,
por si, a origem do relatório, o namespace do código, o campo usado no filtro,
os estados selecionados ou o fuso. Raster continua sendo uma correspondência
a investigar; não confundir viagens com linhas físicas de Fretes Data Export.

Os originais e as análises V1/V2 permanecem preservados. A análise dos arquivos
históricos de Raster0581 encontrou somente agregados: o fechamento declara
`businessDataRetained=false`. O contador de 108 viagens não contém códigos,
placas ou horários reutilizáveis para comparar com os Excel. Não há paridade
concluída entre esses arquivos e a extração.

## Correção restrita ao teste

A fixture compartilhada usava INFO antigo de Coletas com seis nomes de filtro
simples. O catálogo P2 usa os seis caminhos qualificados observados em0607.
O Root aprovou uma seleção explícita por versão do catálogo: o release antigo
continua usando seu recurso byte-exato; o P2 usa um recurso de teste independente
com os31campos e seis pares table.field/tipo conferidos contra a observação real.
Versão desconhecida é recusada. Nenhuma asserção ou corpo de teste foi alterado.

O candidato privado fr53 contém quatro inputs de produção já aprovados e dois
inputs de fixture. A contagem emitida correta é1.778: base1.776 mais dois
recursos adicionados. Permanecem778fontes principais,704fontes de teste,
1.395classes de produto,55recursos e oito bibliotecas. A revisão independente
conferiu33arquivos próprios,93externos, fontes, inventários e inversos.
O código canônico ainda não recebeu essa composição.

## Resultados reais e limites

- f1: Original encerrado1,142.154ms,624.939.008bytes de pico. Um Java externo
  surgiu durante o primeiro compilador; o guard reteve
  `NEW_ROOT_MAVEN_OR_UNSELECTED_JVM`. Nenhuma qualificação do produto.
- Esse processo terminou naturalmente. O Root reconciliou pessoalmente as cinco
  tuplas originais e autorizou uma unidade nova f2, preservando f1 e os limites.
- f2: compilação778/704 e JAR153BA720... concluídos; **150/150regressões,
  12Manifestos,14perfis e17Cotações passaram:193casos funcionais reais**.
  O método anteriormente ausente do sucesso passou exatamente uma vez.
  O JAR de produção é byte-idêntico ao da rodada anterior149/150;
  a diferença aprovada ficou somente na fixture de teste.
- Outro processo externo surgiu no último runner. Original1,285.664ms,
  pico771.059.712bytes; filho fechado, sem timeout de lote ou falta de memória.
  Sete relatórios de guard anteriores fecharam0; o oitavo reteve diagnóstico
  tipado da mudança de processos. O relatório completo dos sete testes de
  integridade não existe; não afirmar quantos casos individuais chegaram a rodar.

A exceção exata da antiga rodada149/150 continua desconhecida. O antes/depois
acima comprova o resultado dos testes com a fixture corrigida; não reconstrói
uma exceção que não foi retida. Não há Own200 aprovado, oito guards aprovados
na mesma ocorrência, Final8,624,Clock9 ou bindings finais desse produto.

## Próximas unidades e restrições

O Root selecionou pessoalmente seis tuplas atuais em0614c. R54 é uma preparação
de dados do protocolo STRICT6 já existente, exigindo novos42modelos, quatro
casos Windows e Capture6 antes de qualquer candidato R55. Todos os processos
selecionados continuam obrigatórios; não existe perfil genérico, processo
opcional, fallback automático ou encerramento de PM2. Se a seleção mudar,
parar e reconciliar o estado real.

O matcher privado Raster teve revisão independente favorável de duas linhas
de correção e dois casos causais, preservando os48corpos anteriores. A cópia
V5/50 está somente em preparo, a partir do registry vazio; nenhum Entry,
janela, consentimento ou DDL foi emitido. Catálogo real22 anterior não autoriza
alteração de estrutura.

Continuam pendentes: pacote completo e integração, Coletas Source→staging→
readback integral→rollback, demais adaptações de banco, Usuários até EOF e
comparação dos valores com os Excel/plataforma. Os testes desta retomada fizeram
zeroHTTP, zeroSQL e nenhuma implantação. A última Source continua INFOColetas0607,
delta1; contadores globais não são finais. Root é o único executor; ESL somente
consulta; sem DDL, dados retidos de negócio, deploy, cutover ou ação PM2.

## Evidência privada sanitizada

Na pasta externa da campanha `quatro-etapas-20261024-01`:

- `RootExcelAuthoritativeReferenceUserDecision0614.json`: instrução humana.
- `RootFinalRuntimeFixtureAmendmentDecision0614.json` e
  `RootFinalRuntimeFixtureCountClarification0614.json`: composição e contagem.
- `r53-fixture-runtime-review-files-only-0614/review.json`: revisão estática.
- `RootFinalR53Own200OriginalClosure0614.json` e
  `RootFinalR53Own200OriginalClosure0614b.json`: fechamentos reais1.
- `RootR53Partial193AndNativeInterruption0615.json` (`DF98F7A5...`): quatro
  relatórios funcionais reais, mesmo produto e interrupção tipada; não é PASS200.
- `RootCurrentExternalJvmCimSelection0614c.json`: seis tuplas selecionadas.
- `raster-null0612-review-files-only-0614/review.json`: revisão do patch mínimo.

Não versionar Excel, valores de negócio, payload, segredo ou credencial.
