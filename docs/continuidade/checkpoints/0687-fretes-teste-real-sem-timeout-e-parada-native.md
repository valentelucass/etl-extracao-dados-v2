# 0687 — Fretes: teste real sem timeout; interrupção local e captura parcial

## Resultado comprovado

A recuperação do erro exato `422 timeout` espera 10 segundos e repete a mesma
página, no máximo uma vez por página e duas por unidade, dentro das 47 reservas.
O teste controlado anterior demonstrou intermitência da ESL para a mesma consulta;
a causa interna do servidor permanece não exposta. A correção é recuperação
limitada, não prova de eliminação do erro na origem.

O produto 66CA26BA (3.517.706 bytes) passou 16 suítes Java. O host passou
49 regressões, 16 casos de fatos desconhecidos, oito casos de repetição observada
e 14 casos da cadeia. A rodada inicial da cadeia falhou por campos ausentes na
fixture legada; a falha foi preservada e corrigida em fixture isolada. O vínculo
de classes/fingerprint passou em sua fase, embora o Original 43044 tenha saído 1
na fixture posterior; o aceite Root registra essa distinção. Aceites privados:
`outputs/root-acceptance-fretes-retry-product0686-01.json` (A035FAC4) e
`outputs/root-generic-gate-host-adoption-fretes0686-01.json` (65CE92B9), no
workspace Supervisor.

## Tentativa real e limites do resultado

A unidade `fretes-day19-retry-real0686-01`, execução
`38017373-cc29-4b93-bf66-0a3cfbb3d24b`, realizou INFO e sete páginas DATA:
oito reservas e oito invocações confirmadas do cliente, todas com resposta 200
completa. Não houve 422 e a recuperação de timeout não foi exercitada na ESL.
O Original 82824 saiu 1, em 78.810 ms, pico 396.357.632 bytes. O controlador
encerrou seu Java 16468 após `FRETES_CURRENT_EXACT_SELECTED_COUNT` antes de uma
nova lease. O Original, o Java e seus fluxos tiveram fechamento conhecido.
O estado foi restaurado; nenhum processo externo foi encerrado pelo Root.

Restaram oito RAW e oito recibos, sem `capture-partial.json`, `source.jsonl`
ou manifest final. O diagnóstico independente protegido, Original 62940/0,
mediu 456 ocorrências e 420 IDs, sem imprimir valores. São dados parciais:
não houve página terminal nem aprovação de conteúdo/completude. Preservar
`controller.auditComplete=false`, retry do worker `UNKNOWN` e seus cinco
contadores desconhecidos como null. Oito invocações do adaptador não provam
quantidade de transmissões internas do cliente HTTP; wire permanece UNKNOWN.

O censo posterior encontrou seis processos externos onde a seleção anterior
continha sete. Esse censo não reconstrói o instante exato da falha. A seleção
explícita Root 31A1DA95 e a qualificação Native 0687/0 foram produzidas para os
seis processos reobservados, sem ampliar limites ou conceder nova Source.

## Estado e trabalho pendente

Canônico permanece B369E2D13A3E971B67D1852537F0896DAD47465432D2209816B5033C02E298DB,
713.677 bytes, ACL B4E214EB, Root 0658, vaga vazia: 17 capturas completas e cinco
parciais já reconciliadas. **As oito reservas reais desta unidade estão fechadas,
mas ainda precisam de reconciliação RawOnly independente antes de nova Source.**
Não ignorá-las, reexecutar grants consumidos, fabricar um parcial Java ou promover
os flags desconhecidos para zero/sucesso. Nova prova Root offline e suporte
explícito de cadeia RawOnly em PowerShell/Java estão em preparação. O escritor
também prepara encerramento gracioso e limitado após falha pré-dispatch do gate.

Fretes continua **0/3 semanas integralmente aprovadas**. Coletas conserva
**3/3 semanas, conteúdo integral das 31 colunas**. V1 permanece pausado pelo
usuário; nenhuma SQL, publicação ou adoção produtiva ocorreu nesta unidade.

## Até três próximas ações

1. Auditar sob Original os oito pares RAW/recibo, journal, semântica anterior à
   invocação e assinatura existente; emitir prova Root somente após fechamento.
2. Qualificar a reconciliação RawOnly e o encerramento gracioso, preservando
   histórico, limites, desconhecidos e falhas anteriores.
3. Após reconciliação e admissão nova, concluir a extração, baixar Excel próximo
   e comparar conteúdo das 114 colunas; repetir para as três semanas.
