# 0621 — Consultas de Fretes: testes aprovados, execução real ainda aberta

Data: 2026-10-26. Coletas308+três preflights do0620 permanece qualificado
offline. Etapas3 e4 continuam abertas.

## Trabalho concluído

Root integrou os sete overlays revisados da adaptação CATALOG_READONLY no
mecanismo Fretes/Local existente, em cópias privadas. Mantidos os43 corpos
anteriores,22 arquivos SQL, limites, alvo, provider, peer e fechamento.
Dez causais novos verificam os dois escopos fixos e recusas relevantes.
Registro DDL permanece vazio; o controller recusa operações DDL.

Own53 passou em uma primeira ocorrência. Antes de qualquer consulta, Root
identificou um erro próprio na integração: os45 hashes candidateFiles foram
escritos em maiúsculas, mas os gates Java comparam hexadecimal minúsculo.
Criou sucessor novo com os45 arquivos idênticos e98 pins externos preservados;
normalizou somente os valores dos hashes e adicionou a proveniência.
Não tentou o gate físico com o manifesto inadequado.

A revisão incremental aprovou o envelope corrigido. Root reexecutou53 casos
distintos e a compilação estrita sem diagnósticos, com22 classes. Original0,
10.146ms, pico503.623.680bytes, sem SQL/provider/Source. O PASS anterior
não foi transferido ao manifesto novo.

## Consulta real de Fretes: falha preservada

Uma unidade nova foi despachada com operação explícita CATALOG_READONLY,
escopo Fretes, código e perfil fixados, revisão Root atual e janela vigente.
Original fechou1 em18.112ms, pico532.021.248bytes; não houve timeout.

O controller observou peer antes do login, um acesso ao provider, fechamento
do Java/conexão, identidade SQL inalterada e ausência do socket próprio.
O resultado registrou24 chamadas SQL iniciadas e323.333bytes de metadados
recebidos. Não publicou `catalog-image-1.json`. A classe da falha foi
`IllegalStateException`; código literal e stack da falha original não foram
retidos. Não se afirma que todas as23 consultas de catálogo terminaram.

Isso **não qualifica Fretes25, Local21 ou Catalog57**. Local21 não foi
executado. Zero HTTP, DDL, DML ou leitura de tabelas de negócio. A investigação
está limitada às fontes/recibos atuais e a um eventual diagnóstico fechado,
sem relaxar consultas, shapes, guardas ou limites para buscar PASS.

## Demais condições atuais

- Root observou seis Java externos: as cinco identidades antigas e PID18712,
  correlacionado anteriormente ao PM2 ETL-EXTRACAO-DADOS-LOOP. Nenhum perfil
  novo foi admitido; nenhum processo ou PM2 foi alterado. Solicitada ao usuário
  pausa temporária dessa tarefa, respeitando sua orientação de preservá-la.
  Até confirmação e reconciliação real, a sequência bd60 fica pendente.
- Direct7/Bind/111/4/Main7 e nove causais técnicos ainda não executados.
  Adoção68 continua condicionada aos bindings reais; migrations6 vêm depois,
  com qualificação própria, sem DDL autorizado por estes resultados.
- Retenção privada de Coletas já participa do pipeline Java que passou308.
  O dispatcher Root físico V6 ainda está preso a Cotações/V15, perfil observado
  e oito argumentos; Coletas exige LEGACY e dez argumentos. Wiring mínimo em
  preparo no mecanismo existente, sem novo owner/Entry/capability ou execução.
- JAR153/g1, Own200, Final8, Full624 e Clock9 preservados. Última Source ainda
  INFOColetas0607/delta1. Não houve nova extração DATA nesta unidade.
- Users sem EOF e comparação externa por registro/campo continuam abertos.
  Contagens históricas ou concordância Source/SQL não provam fidelidade à
  ESL/Excel. Os Excel continuam verdade nos recortes informados, sem associação
  automática a uma entidade ou a um campo de data.

## Evidências privadas

Base B: `C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01`.

| Artefato em B | SHA256 |
| --- | --- |
| fretes-local-readonly46-review-files-only-0620/review.json | A558C1C8A7453F4C55AC7511DF1FA645917F47114F4A9A8ECE0CE7517E5DD7D4 |
| RootFretesLocalReadonly46Integration0620b.json | 36CC3BF9E24C1DCE059C3334B722D39AE2144657AA91728DA447085A5A72B4CF |
| fretes-local-readonly46-envelope-review-files-only-0620b/review.json | B29B68C69D668E8CF2DCA5DBBE14E7E4A8BAB0D578B208EE4F94DD062783B279 |
| RootFretesLocalOwn53Qualification0620b.json | 9BBD1E71391AE69559274D742042D732C7FDE8D2B63E4A951360663D88EB1105 |
| RootFretesMetadataFailure0620.json | E222E29CF593C55B0593CB85390CA012D42A3C88DAC042E60149FB55DDB52F4D |
| RootNativeEligibilityObservation0620.json | 074D6948CD3291C894FE8A3DF08E9C59FD79D0652D6BB326AA9F02B41232BDA2 |

Manifesto Fretes/Local corrigido:
`C9A2E6253C92FEAF0DA285A456EE797A28E8CF51BAEC1D063674236525B4511E`.
Sem carga completa, commit de dados retido, confronto externo concluído ou
encerramento das quatro etapas. Valores brutos e segredos fora do Git;
ESL somente consulta; Root único executor pesado/API/SQL/browser/Git.
