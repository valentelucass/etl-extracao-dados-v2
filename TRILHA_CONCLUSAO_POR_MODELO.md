# Matriz ESL — segunda rodada interrompida por HTTP 429 — 22/09/2026

Sob nova ordem read-only do usuário, a sonda ampliada preservou nomes técnicos
sanitizados e fingerprints de `/info` para 6908 (31/6) e 6389 (110/16).
Coletas teve uma página válida de três linhas/três entidades sob `per=3`.
Fretes respondeu HTTP 429 na quarta chamada; os sete templates seguintes
não foram consultados. O recibo classificou o corpo como JSON inválido, mas o
status 429 está registrado; a sonda foi corrigida offline para classificar
HTTP antes do corpo. O erro de desembrulho de array de um item também foi
corrigido e coberto por autoteste. Nenhum checkbox mudou: 67/115. Estado no
cabeçalho de `STATES.md`, checkpoint 0287 e recibo privado em `target/`.

# Matriz mínima dos nove contratos ESL — tentativa e correção — 22/09/2026

Uma rodada read-only pontual, sob ordem explícita do usuário, tentou o perfil
da matriz V2-025d com 18 chamadas máximas. Parou após quatro HTTP 200:
6908 apresentou metadata 31/6 e quatro linhas físicas com limite por `id`
verificável; 6389 apresentou metadata 110/16, mas a página foi recusada pelo
parser novo com `DATA_ENVELOPE_INVALID`. Sem payload salvo, o shape exato não
foi provado. O parser passou a aceitar objeto único/vazio, como o cliente
existente, e a validar `id` escalar/`per` de 6908/6389; autoteste offline
passou, sem replay da rede. Nenhum checkbox mudou: 67/115. Estado no cabeçalho
de `STATES.md` e checkpoint 0286; recibo privado sanitizado em `target/`.

# Paridade de identidade Coletas 6908 — 22/09/2026

A sonda read-only autorizada comparou duas travessias Data Export completas
(`per=50/100`) com GraphQL terminal numa janela fechada independente. Foram
cinco chamadas de oito permitidas, sem erro; quatro linhas físicas de uma
entidade em cada travessia, seguida de página vazia, e uma entidade GraphQL.
Chave natural e ID canônico coincidiram nos três conjuntos. Esta unidade de
sonda está concluída, mas nenhuma caixa V2-025d/V2-012/P17/P20/V2-041 foi
fechada; seus critérios excedem esta janela. Estado e limites no cabeçalho de
`STATES.md` e checkpoint 0285, recibo sanitizado privado em `target/`.

# Primeira observação real Coletas/Fretes — 22/09/2026

Sob decisão explícita do usuário de prosseguir read-only com a credencial
existente, três sondas `curl` serializadas concluíram: Coletas perfil inicial
5/5 HTTP 200, travessia 4/4 HTTP 200 com 326 linhas/252 entidades; Fretes
5/5 HTTP 200 com 410 linhas/400 entidades. Ambas as travessias pararam no
teto sem terminal, sem retry, sobreposição de `id`, SQL ou escrita. `/info`
6389 agora declara 110 campos e `id`, mas não `finished_at`; a baseline local
é sintética e exige release real antes de runtime. Nenhum P17/P20, V2-041,
paridade ou cutover foi fechado. Cabeçalho de `STATES.md` e checkpoints
0283/0284 são a referência; recibos sanitizados privados em `target/`.

# Próximo passo: primeira amostra antes de P17–P29 — 22/09/2026

A revisão da ordem de gates confirmou que P17–P29 dependentes de dados reais
são cobrados na campanha, não antes de sua primeira amostra. P07/P08 e B16
interno já têm prova local; não há trabalho local pendente elegível. A próxima
ação material é G01 de Segurança/Operações e, após comprovação e janela/teto
reconfirmados, a sonda `curl` allowlisted de Coletas 6908. Cotações 6906
continua rota posterior com autoridade própria; Java ESL também exige escopo
próprio. Nenhuma chamada, teste, checkbox ou contador novo. Estado
autoritativo no cabeçalho de `STATES.md`; checkpoint 0282.

# Smoke offline do JAR V2 — 22/09/2026

Após o pedido de iniciar testes, `config validate` e `dry-run` do JAR existente
passaram com JDK 17 e configuração exemplo: `LOCAL_SHADOW`, fontes e auditoria
desligadas, autorização operacional `deny-all`. Atestado V2-041/G01 ausente no
intake; nenhuma leitura ESL/SQL, aceite P17–P29 ou mudança de contador. Os itens
de qualificação alegadamente resolvidos não vieram com evidência. A triagem
posterior confirmou zero trabalho local elegível na matriz P07–P33; o próximo
input é dos owners externos. Resultado autoritativo no cabeçalho de `STATES.md`; checkpoint
`0281-smoke-offline-jar-v2.md`.

# ETL_SISTEMA V1 — teste de acesso delimitado em 22/09/2026

A pedido do usuário, foi testada somente a conexão local read-only a
metadados do `master`: alvo V1 existente, acesso disponível, sessão Windows
atual com papel `sysadmin` (1/1/1, `sqlcmd` exit 0). A verificação parou
antes de consultar qualquer objeto do V1 porque falta identidade read-only
aprovada; `etl_v2_view` tem negativa de acesso ao banco V1 registrada na
trilha anterior. Camada provada: conectividade e metadados, sem dado de
negócio, paridade, oráculo ou aceite B17–B29. A retomada exige owner SQL/V1
definir acesso de leitura delimitado e comparabilidade de grão/janela.
Resultado autoritativo no cabeçalho de `STATES.md`.

# Sequenciamento dos gates de dados reais — decisão do usuário em 22/09/2026

Os insumos de paridade que dependem de dados reais serão cobrados na primeira
campanha da entidade em que o V2 efetivamente ler dados reais em sombra.
Isso não dispensa P17–P29: os gates aplicáveis devem passar antes de publicação
autoritativa, sweep/apply de ausência ou cutover. A primeira leitura ainda
depende de V2-041/G01 e de autorização específica de canal/runtime, entidade,
janela, limites e alvo sombra. `ETL_SISTEMA` da V1 é candidato a referência,
mas seu uso exige autorização de consulta read-only com escopo e validação de
grão, tempo e divergências; esta decisão não autorizou conexão ao V1.
Nenhum aceite ou checkbox foi alterado. Estado autoritativo em `STATES.md`.

# Próxima conversa — habilitação futura de leitura ESL pelo V2

O handoff no cabeçalho de `STATES.md` fixa a próxima rota: V2-041/G01
permanece `EXTERNAL_HOLD` até atestado autenticado de rotação/invalidação e
continuidade do writer legado por Segurança/Operações. V2-025d, quando
desbloqueado e com janela/teto reconfirmados, cobre somente `curl` controlado.
Extração ESL real pelo JAR requer autorização própria de canal, entidade,
limites e alvo de sombra. B16 interno e o código local estão qualificados no
alcance registrado; B17–B29 e a sonda 4924 não receberam novo input/aceite.
Esta nota não concede autorização nem registra nova execução.

# Código de sombra — equivalência integral de bytes e gate fresco inconclusivo

Em 22/09/2026, a nova suíte física foi interrompida no teto registrado de
45 minutos após 91 classes de IT sem erro; as 246 tabelas preservaram as
contagens agregadas. A comparação SHA-256 de 1.281 arquivos `src/main` e
`src/test` e do `pom.xml` com a cópia histórica `pos0236-p07-verify-01/build`
foi idêntica. Essa cópia teve `verify` PASS, JaCoCo PASS, 2.263 unitários e
492 ITs, com rollback. Nos 172 arquivos SQL de banco comparados, somente o
validador read-only de Manifestos difere; ele passou contra V022 instalada.
Assim, o código de runtime desta revisão conserva prova integral por bytes
iguais, embora o `verify` fresco não tenha terminado. P17–P29 e 4924 não
receberam novo aceite. Evidência e limites no cabeçalho de `STATES.md`.

# B17 Cotações — continuação cURL sem terminal

Em 22/09/2026, a ordem read-only limitada para 6906 na janela 03/09
consumiu quatro chamadas de seis possíveis. `/info` e páginas 2–3 retornaram
HTTP 200; a página 2 teve 100 linhas/100 candidatos distintos, e a página 3,
62 linhas/60 distintos. A página 4 voltou HTTP 200 com envelope inválido para
o contrato local e encerrou a rodada sem retry. A mesma posição já falhara
em outra janela após página curta. Sem contrato versionado de terminal, tarifa
e oráculo independente, P17–P20 continuam abertos. O recibo sanitizado está
em `docs/continuidade/probes/2026-09-22-6906-continuacao-0309.md`.

# `.env` V2 na raiz por determinação do usuário

Em 22/09/2026, o arquivo privado único, com quatro chaves API e oito Raster,
foi movido de `.codex-local/.env` para `.env` na raiz; as quatro sondas e o
exemplo foram alinhados. O arquivo é ignorado e não rastreado pelo Git. O
scanner passou a aceitar somente esse caso exato, mantendo findings para
`.env` rastreado e `.envrc` ignorado; 20 autotestes e a varredura integral
passaram com zero findings. O finding inicial para `.env` na raiz está
preservado em `STATES.md`. Nenhuma chamada externa ou aceite B17–B20 decorre
da configuração.

# Configuração privada de API e Raster no V2

Em 22/09/2026, sob ordem do usuário, `.codex-local/.env` recebeu somente as
quatro chaves de API necessárias às sondas e as oito chaves Raster solicitadas,
tomadas da última definição não vazia do legado, sem exposição de valores.
`.env.example` contém apenas placeholders; as quatro sondas preferem o arquivo
privado e mantêm fallback legado. O scanner integral passou com zero findings;
o finding anterior para `.env` na raiz foi corrigido sem enfraquecer o scanner.
Configuração não é execução Raster, autorização produtiva ou aceite P17–P20.

# Sonda cURL 6906 — nova janela, paginação ainda não terminal

Em 22/09/2026, sob ordem read-only delimitada no cabeçalho de `STATES.md`,
o autoteste da sonda passou e duas chamadas cURL de 6906 retornaram HTTP 200.
O `/info` indicou 37 campos/seis filtros; a página inicial teve 101 linhas
físicas para 100 candidatos `sequence_code` distintos válidos no limite da
sonda. A ordem parou por teto não terminal, sem retry ou página seguinte.
Nenhum P17–P20 foi aceito; a fonte não forneceu oráculo independente, tarifa
aprovada, completude ou contrato/release oficial.

# B16 — cobertura interna de sombra das onze entidades

Em 22/09/2026, a política explícita do owner para B16 foi aplicada às oito
entidades ainda sem subaceite: Manifestos, Coletas, Fretes, Localização,
Faturas, Inventário, Sinistros e Raster condicional. A prova offline dirigida
passou 94/94 testes em JDK 17; a tentativa física anterior parou com 72 erros
na trava de opt-in, antes de conexão/validação. `STATES.md` registra o alcance
por entidade. Com Cotações, CAP e Usuários já aceitos internamente, a linha
agregadora B16 do checklist foi fechada somente para sombra. P16 canônico,
P17–P29 e os aceites reais permanecem abertos.

# B16 Usuários — aceite interno de sombra

Em 22/09/2026, a política explícita do owner para B16 foi aplicada a Usuários.
V2-033 e a ponte GraphQL current/history permanecem apenas em sombra; os testes
dirigidos `ExtrairUsuariosGraphQlTest` e `RuntimeUsersIntegrationTest` passaram
45/45 offline em JDK 17. A sublinha interna de Usuários foi fechada, sem alterar
P16/P17, oráculo nominal, completude do snapshot, paridade ou autorização externa.
Evidência e limites estão no cabeçalho vigente de `STATES.md`.

# Amostra Data Export — Cotações e Contas a Pagar sem aceite externo

A ordem read-only explicitamente autorizada de 22/09/2026 consumiu quatro
chamadas HTTP 200: `/info` e a primeira página de 6906 e 8636. A amostra 6906
tem 100 linhas físicas e `sequence_code` numérico/distinto nessa página; a de
8636 tem 86 linhas, sem `accounting_debit_id`, e 85 valores distintos para 86
ocorrências de `ant_ils_sequence_code`. Ela confirma a ausência de chave de
raiz de CAP e não prova contrato/release, tarifa, oráculo, paridade ou qualquer
aceite P16–P29. Recibo sanitizado: `docs/continuidade/probes/2026-09-22-6906-8636-amostra-readonly.md`.
Não repetir nem ampliar essa ordem sem condição e autorização próprias.
O teste dirigido `Test-CotacoesV2027ShadowVertical.ps1` passou depois da amostra,
validando a vertical V2-027 somente na camada local/rollback-only. Assim, a linha
local correspondente de B16 foi marcada em `BLOCOS_ETAPA_2.md`; contrato oficial,
tarifa e oráculo ainda impedem qualquer aceite da entidade.
Na sequência, uma observação sanitizada dos nove campos usados pelo mapper 6906
não encontrou incompatibilidade de presença/tipo, e
`CotacaoDataExportRecordMapperTest` passou 10/10 sob JDK 17. A segunda linha de
B16 foi marcada para o alinhamento técnico observado; não promove contrato,
completude, tarifa, oráculo ou paridade.
A ordem paginada seguinte recebeu HTTP 200 na página 2, mas a resposta excedeu
100 linhas físicas com `per=100`; ela parou com
`PHYSICAL_ROW_BOUND_EXCEEDED`. A expansão não tem grão/identidade verificável
sob essa ordem, portanto não abriu novo aceite nem deve ser repetida sem novo
contrato e ordem.
Na ordem corretiva posterior, o classificador local pré-testado confirmou que a
página 2 continha 112 linhas físicas para 100 `sequence_code` inteiros,
escalares e não nulos distintos; a página 3 tinha 49 linhas para 48 distintos.
Isso permite caracterizar expansão física apenas dentro do teto operacional,
sem promover a candidata a chave canônica. A página 4 respondeu HTTP 200 com
envelope de dados inválido, e a sonda parou com `DATA_ENVELOPE_INVALID` após
quatro das sete chamadas; páginas 5–7 não foram consultadas. Não há terminal,
completude, contrato, tarifa, oráculo, paridade ou aceite adicional. Recibo:
`docs/continuidade/probes/2026-09-22-6906-paginacao-expansao-envelope.md`.
Por decisão explícita do usuário, B16 de Cotações foi então concluída por aceite
interno de sombra, não por contrato do fornecedor. Ele consolida somente as
provas locais e observadas acima; mantém P17, tarifa, oráculo, completude,
paridade, publicação e produção bloqueados. Decisão:
`docs/continuidade/decisoes/2026-09-22-b16-cotacoes-aceite-interno-sombra.md`.
O validador de identidade 6906 passou e preserva os limites de tenant,
estabilidade, expansão, sweep e cutover.
O usuário também dispensou contrato/release oficial como pré-requisito genérico
de B16 para as demais entidades. Isso fecha a política, mas não transforma
nenhuma vertical em aceita: cada uma continua a exigir evidência técnica própria
para seu aceite interno de sombra. P17, paridade e produção não foram alterados.
Contas a Pagar é a primeira unidade individual concluída sob essa política: os
28 campos, raiz/parcela/rateio explícitos, filtro `issue_date + created_at` e
rollback-only já registrados para V2-029 foram requalificados pelo
`ExpansionLaboratoryContractTest` offline (15 testes, zero falhas/erros).
O aceite é somente interno de sombra; `accounting_debit_id` continua ausente na
amostra 8636, e `ant_ils_sequence_code` não é inferido como chave de raiz/linha.
P17, referência financeira, oráculo, paridade, publicação e produção continuam
bloqueados. Checkpoint 0270.

# Sonda financeira — caminho de Coletas corrigido

A sonda financeira concluiu 07/09 em seis chamadas, sem parada: Data Export e
GraphQL tiveram conjuntos de identidade iguais para 19 Coletas. A falha local
era uma chave nula em lista de atributos vazia e foi corrigida/testada sem rede.
Fretes e 4924 estavam vazios, portanto vínculo/receita/CT-e/Fatura e equivalência
financeira continuam sem prova de negócio. Checkpoint 0255 e recibo sanitizado:
`docs/continuidade/checkpoints/0255-sonda-financeira-caminho-corrigido.md` e
`docs/continuidade/probes/2026-09-22-financeiro-0709-sucesso-tecnico.md`.

# Sonda financeira — parada segura por volume de Coletas

Na data 01/09, a sonda financeira parou corretamente após duas páginas válidas
de Coletas porque a segunda não era terminal. Ela não chamou Fretes, 4924 ou
GraphQL. É limitação de volume da sonda, não erro da ESL. Checkpoint 0253 e
recibo sanitizado: `docs/continuidade/probes/2026-09-22-financeiro-pagina-limite.md`.

# Correção de escopo — GraphQL somente como auditoria transitória

O usuário confirmou que o V2 mantém Data Export como fonte. GraphQL é permitido
apenas como comparação de auditoria e não integra o runtime. Esta correção
substitui a restrição prospectiva sobre a etapa financeira; a sonda 6389 anterior
continua sendo, corretamente, evidência de Data Export sem GraphQL.

# Sonda Data Export — Coletas e Fretes aceitos sem GraphQL

Fretes (6389) também passou na janela 01–07/09 apenas com Data Export: cinco
chamadas, HTTP 200 e JSON válido, sem parada. Coletas e Fretes têm agora
evidência de contrato/paginação limitada à janela, não vínculo ou financeiro.
O 4924 fica fora desta trilha porque sua sonda permitida exige GraphQL para
comparar conjuntos. Checkpoint 0252 e recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-6389-7d-sucesso.md`.

# Sonda Data Export — Coletas aceita na janela de sete dias

Após validar localmente a sonda e os contratos, Coletas (6908) foi consultada
de 01–07/09 em uma ordem menor: cinco chamadas, três segundos de intervalo,
todos os status HTTP 200 e JSON válido, sem parada. A fonte voltou a aceitar a
consulta; isso não comprova paridade GraphQL nem cobertura global. Checkpoint
0251 e recibo sanitizado: `docs/continuidade/probes/2026-09-22-dataexport-6908-7d-sucesso.md`.

# Sonda Data Export — resultado desconhecido na segunda partição

A ordem independente de sete dias (08–14/09) não devolveu stdout, exit code nem
sessão ao controlador. O processo não ficou ativo e a sonda não persiste recibo,
portanto não se conhece o resultado remoto. A janela não será repetida nem terá
status inferido. Checkpoint 0250 e recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-7d-resultado-desconhecido.md`.

# Sonda Data Export — limite da fonte na janela de 30 dias

O pedido de amostra nos 30 dias fechados foi particionado porque a sonda limita
cada janela a sete dias. A primeira partição foi interrompida pela ESL com HTTP
429 na primeira leitura de dados, após metadado 6908 HTTP 200 e duas chamadas
totais. A parada foi imediata: não houve retry, Fretes, GraphQL, 4924, escrita,
banco, deploy ou corte. Checkpoint 0249 e recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-30d-rate-limit.md`.

# Sonda Data Export — consulta recente aceita, sem amostra de Fretes

Após revisar a documentação ESL, a sonda limitada do template 6389 para uma
data recente recebeu HTTP 200 e JSON válido. Não havia registros no dia, logo
o acesso e o formato foram confirmados, mas identidade e paridade continuam
pendentes. A recusa HTTP 422 anterior, em janela histórica, segue sem causa
estruturada; a regra histórica do fornecedor é apenas hipótese. Checkpoint 0248
e recibo sanitizado em `docs/continuidade/probes/2026-09-22-dataexport-6389-recente-vazio.md`.
Próximo insumo: data recente, fechada e sabidamente com Fretes. Sem escrita,
banco, GraphQL, 4924, deploy ou corte.

# Sonda Data Export — sucessão documental pendente

O check de whitespace não encontrou erro; o validador de continuidade recusou o
novo delta com `HANDOFF_PIN`. Os manifests e ledgers históricos ficaram
imutáveis, logo a falha foi preservada e exige sucessão documental própria. O
resultado da fonte continua: parada segura no HTTP 422, sem GraphQL, 4924,
escrita, deploy ou corte. Checkpoint 0247, SHA-256
`0ba9d7610490291592eda15b006716384d1201e806d34cf1ee22aa1faf24337e`.

# Sonda Data Export em sombra — parada segura em 22/09/2026

Sob autorização explícita do usuário, a sonda read-only de contrato usou 7/7
chamadas do teto próprio. Metadados de 6908 receberam HTTP 200, mas o primeiro
pedido de dados recusado recebeu HTTP 422; entidades e paginação não puderam ser
verificadas. A regra de parada interrompeu a rodada: GraphQL e 4924 não foram
chamados, sem retry, escrita, banco, deploy ou corte. Não há aceite novo nem
alteração de 39/45 e 67/115. Evidência sanitizada e checkpoint:
`docs/continuidade/probes/2026-09-22-dataexport-shadow-stop.md` e
`docs/continuidade/checkpoints/0246-sonda-dataexport-sombra-interrompida.md`
(SHA-256 `3883661bb6249a49cd024ddfdec67f612ecd7c1a5fa7bd54695a5c0419f32c28`).
Nova consulta depende de diagnosticar a recusa e registrar ordem própria.

# Encaminhamento vigente após a etapa 1 local

P09–P15: trabalho local elegível identificado esgotado, aceites externos pendentes. Próximo prompt é etapa 2 (P16–P29), nunca retorno automático à etapa 1 por checkbox aberto. Etapa 3 continua P30–P33. Ler [encaminhamento](docs/continuidade/tres-etapas/handoff/ENCAMINHAMENTO.md), [prompt 2](docs/continuidade/tres-etapas/handoff/ETAPA_2.txt) e [prompt 3](docs/continuidade/tres-etapas/handoff/ETAPA_3.txt). Selecionar fatias pelo delta e pelas dependências usadas, sem inventar trabalho ou liberar gates. Nenhum P recebeu aceite nesta manutenção; 39/45 e 67/115 preservados. STATE e checkpoint 0245 registram o alcance; as diretrizes abaixo permanecem históricas quando conflitarem com este encaminhamento.

# Diretriz vigente: finalizar em três etapas

O usuário adotou três etapas amplas: **1 = P09–P15; 2 = P16–P29; 3 = P30–P33**. Seguir [GUIA_E_PROMPTS](docs/continuidade/tres-etapas/GUIA_E_PROMPTS.md) ao preparar os próximos chats. Esta diretriz sucede a escolha de pequenos agrupamentos das seções 11 e 13; os detalhes por P continuam como dependências e critérios internos da etapa.

Pedidos como “dê o prompt da etapa 2” ou “da etapa 3” geram um único bloco pronto para copiar, preenchido com o STATES e os recibos atuais. Preparar o prompt não executa a etapa. Não exigir que o usuário escolha cada P, repita contexto ou peça continuação por tarefa. Preservar critérios, autorizações, limites e evidências; não prometer que toda operação termina em três chats ou que o tempo obrigatório de observação desaparece.

Conferência desta manutenção: tentativa documental anterior interrompida no teto e preservada; leitor de arquivos otimizado mantendo os checks. Resultado autoritativo em target/tres-etapas-20260922-01/closed-receipt.json, após sua execução. P07/P08 físicos não foram repetidos.

# Qualificação local P07/P08 e sucessão PASS

Provas físicas, pacote e validações documentais atuais conferidas. Relatório/matriz: docs/continuidade/qualificacao-p07-p33/. P09–P33 preservam critérios externos; 39/45 e 67/115 inalterados. Scanner/readback final e encerramento são comprovados pelos recibos privados da rodada, não pela fotografia desta trilha. Ver STATES e checkpoint0243.

# P07/P08 locais aprovados; concluir sucessão documental

Build, testes, cobertura, integridade, reprodução do pacote, JAR extraído, guardas, A/B, variantes e readback PASS nos bytes atuais. P09–P33 preservam critérios e inputs externos; 39/45 e 67/115 intactos. Ver STATES e checkpoint 0242. O fechamento documental exige seus próprios recibos.

Conferência posterior a 0242: correções documentais do leitor XML e do cálculo de revisão estão em execução; provas físicas permanecem intactas. Ver STATES e recibos privados da recuperação do compositor. Não há novo aceite enquanto a validação documental estiver pendente.

Leitor XML/revisão: 15 contraprovas PASS. Replay integral preservou nova recusa por Unicode no pipe; correção UTF-8 privada em execução, conforme STATES. Nenhum resultado físico foi alterado.

Recuperação do compositor concluída: 15 contraprovas estruturais, três de UTF-8 e replay integral03 PASS. Resultado físico derivado e matriz materializados; validar agora a sucessão/cadeia/trilha e o fechamento final, sem repetir provas físicas.

# P07 local aprovado nos bytes atuais

2263 unitários/4 skips históricos e 492 integrações/105 classes; build, cobertura, identidades e rollback PASS. P08 pronto, ainda pendente de execução. Ver STATES e checkpoint 0241. Nenhum aceite nominal novo.

# Unitários completos e gate técnico reconferido

2.263 casos unitários, zero falha/erro e quatro skips históricos. PMD composto PASS sobre 251 XML do build atual; 36 alertas brutos preservados. Recibo do scanner corrigido, mesmos 18 testes PASS. Integração física ainda ativa, sem PASS P07/P08 presumido. Ver STATES e checkpoint 0240.

# P07 integral em execução

Controlador privado corrigido após falha pré-Maven, preservada. Segunda reserva FULL ativa; nenhum PASS integral presumido. Ver STATES e checkpoint 0239. P08 depende dos gates completos de P07; P09–P33 conservam seus critérios e dependências.

# P07–P33 — segurança técnica local validada

285testes/77novos PASS;1172Java conferidos. PMD36alertas brutos com36disposições técnicas verificadas,sem supressão;40contraprovas PASS. Pré-flight SQL sombra PASS e ordem nova300/43200s reservados. P07físico/P08 ainda pendentes;G01 e demais critérios externos não promovidos. Busca explícita de documentação preservou provas reais existentes e confirmou falta do atestado. Ver STATES/checkpoint0238.

# Execução P07–P33 — contraprovas em qualificação

Reconciliação0236 PASS3841arquivos;88casos dirigidos RED com49falhas reproduzidas,sem erro/skip. Correções JDBC aplicadas;GREEN em execução. Nova ordem física própria preparada,nenhumSQL nesta fotografia. Disposição PMD específica/ADR0054 e matriz74linhas em preparação;P07/P08 ainda pendentes.39/45 e67/115 preservados. O objetivo permanece todos os critérios atéP33,sem converter inputs externos em PASS. Estado autoritativo:STATES.md;checkpoint0237.

Sucessão (10+15+18 contraprovas) e trilha PASS nesta rodada; scanner/readback finais registrados no recibo de fechamento após execução. Nenhuma aprovação física/nominal nova.

# Avanço técnico de segurança e resiliência — 22/09/2026

Oito defeitos concretos corrigidos após análise PMD10regras e revisão dos caminhos sinalizados: três perdas de causa em finally, dois statements não fechados em falha, permit perdido em recusa de conexão, fechamento Raster com causa substituída e Error HTTP tratado como retry.24regressões novas; Maven test offline final: 2186 casos,zero falhas/erros,4skips históricos. Enforcer,Spotless,Checkstyle e compilação com warnings fatais PASS. Nenhum SQL real ou fonte remota.

Gate local reutilizável em scripts/security/Invoke-LocalStaticAnalysis.ps1,com12contraprovas aprovadas. PMD analisou653Java pelas10regras;40alertas iniciais→37remanescentes,sem supressão. Triagem técnica explícita em docs/continuidade/avanco-seguranca/triagem.json;alerta não equivale automaticamente a vulnerabilidade e o gate permanece FINDINGS_OPEN. Não fecha SAST integral/V2-015c.

Delta Java exige nova qualificação física P07/P08 dos bytes alterados; resultados0233/0235 permanecem históricos. A suíte local desta rodada não reivindica JDBC,coverage físico,release ou corte.16inputs externos anteriores,39/45 e67/115 preservados. Nenhum aceite nominal novo. Unit01 falhou antes dos testes por seleção relativa do formatter; causa Windows/regex absoluto corrigida sem mudar POM,limites ou assertions. Unit02 preservada com2175casos/5erros por heap da JVM filha fora do teto512MiB (13regressões novas PASS),corrigido no ambiente privado;unit03 parou em EmptyBlock antes dos testes,corpo TWR corrigido;unit04 dos fontes finais.

Relatório: docs/continuidade/avanco-seguranca/RELATORIO.md. Fechamento documental consultável em validacoes.json e target/avanco-seguranca-20260922-01/closed-receipt.json quando existir;ledger físico anterior intacto.

# P09–P33 — conclusão das parcelas locais executáveis (22/09/2026)

Revisão por25etapas,11entidades,6dimensões,5fatos e19contratos concluída. Não foi demonstrado outro delta funcional local elegível; os requisitos reais/operacionais continuam delimitados por fatia e papel em docs/continuidade/entrega-p09-p33/matriz.json.16inputs externos anteriores preservados,mais os critérios já canônicos de G01,oráculos/histórico/ausência/consumidores/escala/SAST e G06–G08. Nenhum aceite nominal novo;39/45 e67/115.

Correção concreta P29: README atualizado para DLL12.8.2,alcance do feed e compatibilidade v1. Sucessor documental offline:2ZIPs idênticos7941140bytes/730membros,725membros sem mudança;SBOM/extração/inspect+3plans PASS. Não é novo P08 físico nem RC produtivo. P07/P08 de0233 reutilizados somente no alcance dos bytes preservados:2162unitários/4skips históricos,492IT/105classes. Verificação dirigida conferiu60XMLs únicos/363casos aprovados.

Executados agora:intakeG01 com19contraprovas,scanner self-test18,sucessão10+15,trilha e PMD offline2regras em652fontesJava,zero violações/erros. PMD não é aceite SAST integral. Duas falhas documentais por IBM850 foram preservadas;correção somente UTF8 no processo filho passou,sem ambiente global ou ajuste de limites. Falha inicial da reconciliação por escopo excessivo do snapshot também preservada/corrigida.

Nenhum Java,SQL,POM,migration,schema,dependência,assertion,timeout ou massa alterado. Maven foi usado apenas para PMD privado offline;suíteJava/JDBC não repetida. Sem rede,segredos,banco,COMMIT de domínio,produção,publicação,deploy ou corte. Ledger0233 CLOSED e intacto;nenhuma campanha física aberta. Relatório,matriz e resultado em docs/continuidade/entrega-p09-p33/;fechamento autoritativo e processos em target/conclusao-p09-p33-20260922-01/closed-receipt.json após readback.

# P09–P33 — execução local e revisão por fatia (22/09/2026)

Em execução: 3798 arquivos reconciliados com o fechamento0233,176 referências íntegras; nenhum delta posterior de runtime/schema. P07/P08 permanecem provas locais reutilizáveis, ledger anterior CLOSED e intacto. A primeira comparação chamou de runtime o snapshot completo; os quatro deltas documentais já selados foram identificados, sem mudança posterior. Recibos preservados em target/conclusao-p09-p33-20260922-01/.

Três frentes independentes conferiram11 entidades,seis dimensões,cinco fatos,19 contratos (18 externos+SQL-10 interno),ausência e operação. Até aqui não há defeito funcional adicional demonstrado. Correção concreta P29 em curso: README do pacote distingue DLL12.8.2,scan técnico versus aceite nominal e compatibilidade v1 versus onze entradas atuais. Sucessor documental do envelope será validado offline; runtime/JAR,schemas,fixtures e oráculos conservarão seus bytes/provas. Nenhum novo aceite físico ou nominal presumido.

G01 ausente no caminho de intake; Gitleaks não disponível no PATH. Os16 requisitos externos anteriores continuam; o escopo P09–P33 inclui também oráculos/janelas/histórico,ausência,manifestos de consumidores,escala e G06–G08. Não repetir a busca sem nova evidência.39/45 e67/115 preservados. Usuário pediu rapidez e testes locais; nenhuma fonte,segredo,banco,infraestrutura,publicação ou produção autorizada por inferência.

Fechamento documental pós0227 PASS:sucessão atual/cadeia histórica/trilha,autoteste do scanner,scans delimitados e UTF-8 conferidos. P07/P08 qualificados nos novos bytes;16inputs externos permanecem. Evidência:docs/catalogos/requalificacao-pos0227/validacoes.json.39/45 e67/115 inalterados.

LOCAL_REQUALIFICATION_P07_P08_PASS. P07 integral e P08 dos novos bytes concluídos no escopo local:2162unitários/4skips históricos,492ITs/105classes,build/cobertura PASS;pacotes byte a byte iguais,smoke,A/B,8variantes,8+21guardas PASS e agregados preservados.16inputs externos permanecem;39/45 e67/115. Relatório:docs/catalogos/requalificacao-pos0227/RELATORIO.md. Validação documental final ainda pendente.

Smoke01 interrompido antes do JAR/JDBC por pwsh duplicado no PATH privado; causa reproduzida e correcao offline RED/GREEN comprovada. Readback/processos PASS. P07 integral e ZIPs730membros identicos preservados;P08 ainda pendente. Checkpoint0231. Nova tentativa smoke02 dentro da mesma ordem,sem ampliar limites.16inputs externos e contadores intactos.

P07 integral pós0227 PASS:2162unitários/4skips históricos;492ITs/105classes,sem falhas/erros;build/cobertura,identidades exatas e readback PASS. Runtime sem alteração. P08 ainda condicionado à própria execução completa. Checkpoint0230;16inputs externos,39/45 e67/115 preservados.

Macroblocos3/4 reconferidos:23 artefatos íntegros,16 requisitos externos sem novo input suficiente; evidência e owners em docs/catalogos/requalificacao-pos0227/investigacao-inputs.json. Checkpoint0229. Nenhum aceite novo;P07 ainda em execução,P08 condicionado.

# Requalificação pós-0227 — pré-flight conferido

P07/P08 em execução autorizada local; nova ordem finita própria em target/requalificacao-pos0227-20260922-01/. Pré-flight PASS, agregados iguais, sem processos próprios ou pressão SQL sinalizada na amostra. Snapshot2079 arquivos de runtime sem drift. Falhas anteriores e16 inputs externos preservados;39/45 e67/115. Checkpoint0228; P07/P08 ainda sem novo aceite.

Validacao documental da rodada fisica PASS: falhas P07 preservadas, sucessao/cadeia/trilha e scans delimitados conferidos. A qualificada/C corrigido; B nao qualificado e P08 nao iniciado.16 requisitos externos permanecem;39/45 e67/115 intactos. Recibos:docs/catalogos/p11-fisico/validacoes.json.

Falha documental preservada: partial-closure-01 parou na composicao por typo Import-sourceText gerado nesta rodada. Corrigido para Import-Module; nao houve SQL nem promocao de aceite. Nova validacao documental02 usara recibos proprios.

# P11 — A qualificada, C corrigido; B executado com falhas preservadas

P11_NATIVE_PASS_FULL_VERIFY_FAILED. JDBC/nativo12.8.2:1 IT PASS e agregados iguais.
As duas rodadas passaram2162 unitarios,com4skips historicos. Full01:
492 ITs/105classes,1erro de lock apos rollback;JaCoCo PASS,Maven falhou.
Full02 foi interrompido apos79 ITs/22classes,2timeouts SQL em Manifestos;
nao completou VerifyPhysical. Escala e ManifestGates originais passaram
isoladamente (4+4casos),sem mudar codigo,asserts ou timeouts. Esses
diagnosticos nao substituem suite integral. P08 nao iniciou.
Readback final igual:0/0/453 auditorias,246tabelas/1816objetos;sem DDL/commit.
SQL sinalizou pressao fisica de memoria apos full02 (flag1),e flag0 apos
o diagnostico. Causalidade dos erros nao comprovada. Nao alterar terceiros.
Duas tentativas corretivas da rodada consumidas (native02 e full02);
sem renovacao automatica do teto/vigencia/limite. B permanece NAO_QUALIFICADO,
nao BLOQUEADO_POR_INPUT por ser uma falha tecnica de execucao.
Busca23 documentos/recibos:16 inputs externos nao comprovados nos artefatos
consultados,com origem e responsavel por papel. Nenhum nome/aceite inferido.
Relatorio:docs/catalogos/p11-fisico/RELATORIO.md.39/45 e67/115 preservados.
P09 nao reavaliado;sem fonte de negocio,producao,publicacao,deploy ou cutover.
Fotografias anteriores preservadas;validacao documental final em andamento.

# P11 — segunda falha SQL preservada; pressao de memoria observada

P11_SECOND_FULL_FAILED. Full02:2162 unitarios/4skips historicos;79 ITs
registrados em22classes,2 timeouts SQL em AnalyticLaboratoryManifestGatesIT.
Execucao interrompida apos falhas persistidas,apenas arvore Maven propria
conferida por PID/inicio/parent/attempt. Wrapper exit1,rollback confirmado;
readback dedicado igual ao inicial0/0/453,246tabelas/1816objetos.
Apos a falha,SQL declarou process_physical_memory_low=1;zero requisicoes
ativas/bloqueadas e transacoes read/write no alvo. Isso demonstra pressao
de recursos naquele instante,nao causalidade retroativa dos timeouts.
Diagnostico atual da classe original de quatro gates,sem mudar codigo/asserts/timeouts.
P07 nao qualificado;P08 nao iniciou. A local qualificada,C corrigido;16
requisitos externos sem novo aceite. Teto36000s/vigencia originais mantidos.
Evidencias:target/p11-fisico-20260921-01/;fotografias anteriores preservadas.

# P11 — diagnostico de escala PASS; nova suite integral em execucao

P11_P07_FULL_RECHECK_RUNNING. A classe original de quatro escalas passou
sem mudar codigo,asserts ou timeouts;rollback/agregados iguais. O erro da
primeira suite continua preservado e sua origem de bloqueio nao foi identificada
retroativamente. VerifyPhysical02 iniciado em p11-p07-verify-02,mesmo snapshot.
Plano sucessor pipeline-plan-02.json conserva anterior STOPPED e reduz
somente tetos futuros ainda nao reservados conforme0/1/6/7 etapas reais.
Teto36000s e vigencia originais mantidos;nenhum efeito ampliado. P08 depende
do novo PASS integral. Recibos:target/p11-fisico-20260921-01/diagnostic-summary.json.
39/45 e67/115 intactos;16 requisitos externos conservados.

# P11 — P07 interrompido por lock no readback; diagnostico local

P11_P07_LOCK_FAILURE_PRESERVED. VerifyPhysical01 executou2162 unitarios
(4skips historicos) e492 ITs/105classes:491 ITs passaram,1 erro SQL de lock
no quarto caso de RelationalLaboratoryScaleIT (repeticao256),linha142,
conferencia de contagens apos rollback. Escala1024 passou. JaCoCo PASS.
Nao qualificar P07 nem P08 com esta falha. Pipeline interrompido antes do pacote.
Wrapper confirmou rollback; readback dedicado preservou agregados0/0/453,
246tabelas/1816objetos. Diagnostico posterior:zero requisicoes ativas/bloqueadas,
transacoes e sessoes JDBC no alvo. Isso nao identifica retroativamente o bloqueador.
Classe original de quatro escalas em diagnostico reservado,sem mudar codigo,
asserts ou timeouts. Alvo exato,sinteticos/rollback,sem DDL/commit/producao.
Ledger:target/p11-fisico-20260921-01/;teto36000s e vigencia originais mantidos.
16 requisitos externos e39/45,67/115 conservados.

# P11 — JDBC/nativo qualificados; regressao integral em execucao

P11_NATIVE_QUALIFIED_P07_RUNNING. Pedido efetivo "conclua esses" adotado para A/B locais,
com localhost/ETL_SISTEMA_V2_SHADOW, sinteticos, rollback, sem DDL ou commit
de dominio. Ordem/limites/ledger proprios: target/p11-fisico-20260921-01/.
Nativo12.8.2 e JDBC12.8.2 passaram 1 IT, zero falha/erro/skip; DLL conferida
contra lock/Central, agregados antes/depois iguais (auditorias0/0/453).
Lock atualizou seis componentes e preservou POMs/licencas historicos.
P07 VerifyPhysical iniciado em copia isolada; ainda nao aprovado. P08 aguarda.
Busca solicitada:23 documentos/recibos relacionados aos16 requisitos externos;
provas reais existentes reaproveitadas, nenhum aceite nominal localizado.
Falha do executor native01 antes de SQL, diagnosticos offline01/02 e correcao03
preservados. Sem novo aceite:39/45 e67/115. P09 nao reavaliado.
Historico abaixo e a fotografia anterior; sucessao sera selada ao fechar a rodada.

Validação pós-P11: regressão unitária inteira, construção local e sucessão/trilha PASS. Matriz de rede corrigida por rodada;16 inputs externos e A/B físicos pendentes. Evidência: docs/catalogos/p11-regressao-local/validacoes.json.

# P11 pós-0220 — delta P07/P08 delimitado e matriz de rede corrigida

P11_LOCAL_REGRESSION_RECORDED. 2162 casos/247 classes, zero falhas/erros,
4 skips históricos; JAR/oito libs construídos localmente. Sem SQL/nativo,
VerifyPhysical/cobertura ou pacote qualificado executado; aceites P07/P08 são
históricos. A/B físicos dependem de ordem própria; C corrigido por sucessão.
16 inputs externos permanecem; FEED-ACHADOS já atendido no scan0220.
Relatório e matriz atuais: docs/catalogos/p11-regressao-local/.
39/45 e67/115, zero novo aceite. P09 sem novo input ou reavaliação.

Validações finais P11/trilha PASS; baseline técnica sem vulnerabilidade. Aceite nominal de Segurança não inferido. Evidência: docs/catalogos/p11-publico-corrigido/validacoes.json.

# P11 — dependências corrigidas e auditoria pública validada

P11_PUBLIC_FEED_PATCHED. Jackson 2.18.11/JDBC 12.8.2: 205 testes PASS,
NVD atualizado,13 dependências/zero achado,parser corrigido/seis regressões.
Sem novo aceite humano ou V2;39/45 e67/115. P10/P12/P14/P15/P21 mantêm
preparação. Relatório/matriz: docs/catalogos/p11-publico-corrigido/.
Próxima ação: aceite nominal da baseline por Segurança; SQL/nativo não executados.

Validação atual PASS na camada local; P11 mantém quatro achados reais abertos. Recibos: docs/catalogos/p11-cache-offline/validacoes.json. Matriz vigente: docs/catalogos/p11-cache-offline/matriz-atual.json.

# P11 — scan real local concluído, correção de achados pendente

P11_CACHED_SCAN_COMPLETED_FINDINGS_OPEN.13 dependências,4 achados,JSON/HTML e exit1 esperado
da política0.0; cache antigo sem aceite de frescor. Candidato JDBC13.4.0:
122 testes/16 classes PASS,sem alerta JDBC no cache;3 Jackson permanecem.
POM público e provas anteriores preservados. P10/P12/P14/P15/P21 continuam
preparados offline. P09 não reavaliado. Sem novos aceites39/45/67/115.
Ver docs/catalogos/p11-cache-offline/RELATORIO.md; próximo passo é obter
dependências corrigidas/feed e qualificar o candidato, sem repetir B56.

# Correção local pós0216 concluída

CORRECAO_LOCAL_POS0216. Trilha ampla e catálogo de frota PASS após corrigir a
validação entre revisões.14 contraprovas da sucessão +11 P06 PASS;13 da matriz
e24 da preparação PASS. P10/P11/P12/P14/P15/P21 preparados integralmente na
camada offline;17 requisitos externos com origem/owner-papel preservados.
Relatório: docs/catalogos/continuidade-pos0216/RELATORIO.md. Falhas antigas
continuam históricas; novo PASS não as reescreve.39/45 e67/115 sem mudança.
Próximo macrobloco: intake offline de input sanitizado novo; nenhum efeito
externo ou aceite é liberado. P09 não foi reavaliado.

# P10/P11/P12/P14/P15/P21 — preparação offline concluída

STATES registra seis frentes locais validadas e parcelas externas BLOQUEADO_POR_INPUT.
48 testes Java/10 classes PASS, gates estáticos e contratos/contraprovas PASS.
17 requisitos externos, 36 pins e dependências por dimensão estão em
[relatório da preparação](docs/catalogos/preparacao-offline-p10-p21/RELATORIO.md).
P10→G02; P11→FEED; P12→G05; P14→G03/G04; P15/P21→G04 e suas fontes aplicáveis.
P09 não foi reavaliado. Divergências históricas P06 e pin de frota preservadas,
sem regravar selo/manifesto. 39/45 e 67/115 inalterados; nenhum aceite V2.
Próximo macrobloco: intake offline de input sanitizado novo; nenhum efeito externo
elegível sem autoridade própria. A numeração histórica P08–P11 dos catálogos de
identidade não é a sequência P01–P33 desta trilha; o mapeamento vigente é P14.

Verificação final do recorte: 13 contraprovas, 36 pins, dez XMLs/48 testes
conferidos; scanners delimitados 3+292 arquivos sem achado. Preparação geral
PASS. O gate histórico da trilha continua vermelho em P06, preservado.

# P09/V2-041 — alcance offline concluído; G01 pendente (21/09/2026)

P09 concluiu a validação local de V2-041 e está
`BLOQUEADO_POR_INPUT`: o atestado sanitizado G01 não está presente e não foi
aberto qualquer `.env` ou segredo. O intake contratual passou; a integridade
P08 M/N foi reconferida (dez recibos, 724 membros e zero fonte). O scanner
auxiliar foi corrigido para decodificar caminhos Git UTF-8, recebeu contraprova
Unicode e passou 18 autotestes; a varredura final passou com 3.680 candidatos,
zero achado e zero arquivo não inspecionado/excessivo.

`gitleaks` não existe no ambiente, portanto não houve scan canônico de
worktree/histórico, instalação ou equivalente manual. Segurança e Operações
devem fornecer G01: atestado sanitizado com referência restrita autenticada,
as seis classes/consumidores, continuidade do writer, invalidação, rollback e
três scans, além do Gitleaks aprovado para as duas varreduras locais. Nenhum
resultado local desbloqueia V2-041, V2-025d, rede, release, deploy ou cutover.
39/45 e 67/115 permanecem inalterados. Ver
`docs/catalogos/p09-v2-041/RELATORIO.md` e checkpoint0214.

Os validadores de intake, scanner e preparação passaram. A trilha ampla mantém
a falha histórica `P06_SUCCESSION_HASH_docs/runbooks/continuidade-agentes.md`;
P09 não alterou o runbook nem reescreveu o manifesto/selo P06 para ocultá-la.

# P08 M/N — fechamento local selado (21/09/2026)

M/N concluídos no escopo local: pacote ampliado de 724 membros, A/B e oito
recusas via JAR extraído, regressão, scanner, autotestes, selo e readback
passaram. Evidência em `p08-mn-*-23` e relatório
`docs/catalogos/campanhas-integrais/P08-MN-FECHAMENTO-20260921.md`. Permanecem
vedadas inferências de produção, paridade real, cutover, revisão humana ou
aceite dos pais V2; 39/45 e 67/115 não mudam.

# P08 — divergência do guard resolvida no runtime; M/N continuam abertos (21/09/2026)

O pacote com724 membros era válido, mas o runtime ainda limitava o manifesto a
512 membros e o inventário a514 arquivos. `QualifiedPackage` passou a expor o
contrato único de1.024 membros e1.026 arquivos. A falha histórica
`QUAL_JSON_ARRAY` permanece preservada como diagnóstico; o candidato corrigido
retorna `QUAL_JSON_MEMBERS` para `missing-member`.

Os testes focados passaram26 casos sem falha/erro. O build
`p08-runtime-member-limit-build-17` produziu candidato de724 membros; seus21
guardas extraídos passaram com `childrenCreated=0` e `jdbc=NOT_STARTED`.
Não houve SQL, leitura de `ctl`, fonte ou produção. M/N não foram aceitos:
contraprovas, smoke, guard de controle, scanner, selo/readback e sucessão seguem
pendentes. A/L e os contadores39/45 e67/115 não mudaram. Ver POS0216, ledger
`p08-runtime-member-limit-offline-ledger-17` e checkpoint0210. P07 e V049
seguem imutáveis e fora do macrobloco; as fotografias abaixo são históricas.

# P08 — pré-flight bloqueado antes do pacote (21/09/2026)

P08 recebeu a ordem física independente `p08-pacote-supervisor-selagem-20260921-01`.
O alvo autorizado foi confirmado ONLINE no `master`; os agregados de schema foram
246 tabelas e 1.816 objetos. A linha de base agregada de auditoria não pôde ser
completada, porque uma relação de auditoria esperada não estava disponível no
alvo. A regra da ordem encerrou a única reserva de pré-flight (1.200 s) e fechou
o ledger sem pacote, extração, Maven, JAR, supervisor, A/B, smoke, guards ou selo.

P08 está **BLOCKED_PREFLIGHT_AUDIT_AGGREGATE_UNAVAILABLE**; A/L/M/N continuam
abertos, C–K e os contadores39/45 e67/115 permanecem históricos. Ver relatório
`docs/catalogos/campanhas-integrais/P08-PACOTE-SUPERVISOR-POS0208.md`, ledger da
rodada e checkpoint0208. Uma execução futura requer nova ordem e contrato de
agregados de auditoria verificável; não repetir esta tentativa.

# P07 — Replay corrigido e VerifyPhysical PASS; P08 requer ordem própria

P07 `p07-replay-pos0207-verify-01` passou uma única vez após corrigir a linhagem
BOOTSTRAP de REPLAY, sem alterar V049:2.162 unidades (4 skips históricos),492 ITs
sem falha/erro/skip, rollback e agregados preservados,zero processo próprio. P08
não iniciou e só é elegível sob ordem futura própria. A/L/M/N abertos;39/45,67/115
inalterados. Ver relatório, ledger e checkpoint0207.

# P07 — VerifyPhysical falhou; P08 bloqueado

A ordem P07/P08 POS0205 consumiu sua única VerifyPhysical P07:
`p07-pos0205-verify-01` encerrou exit1, rollback confirmado e zero PID próprio,
mas Failsafe encontrou dois erros `EXP_PLAN_REPLAY_ORIGINAL_REQUIRED` em
AnalyticScenarioRuntimeIT e QualificationReplayIT. P08 não iniciou. O contrato
P07 exige verify verde, logo nenhuma requalificação C–K, aceite L ou pacote M/N
foi inferido. Contadores seguem39/45 e67/115. Ver
docs/catalogos/campanhas-integrais/P07-P08-POS0205.md e checkpoint0206; nova
Physical requer ordem finita depois da correção offline de linhagem REPLAY→BOOTSTRAP.

# P06 — fechamento offline; pacote P07–P08 preparado

Revisão P06 concluída:3achados médios corrigidos e1baixo de compatibilidade preparado.
68testes dirigidos+18preflight canônico PASS; helpers10 checks offline, scanner17,
remoções5 e sucessor11 contraprovas. As duas pendências históricas de integridade
foram tratadas por evidência exata; gates dos bytes finais em final-validation/*complete*,
closure.json e review-manifest.json da rodada P06-REVISAO-POS0202-20260920T225525774Z.

P07 tecnicamente elegível após readback; efeitos físicos P07/P08 exigem nova ordem
com alvo/vigência/orçamento. Nenhum uso do saldo POS0198. Consumo físico zero.
Novo JAR requer requalificação; aceites anteriores preservados como históricos.
A/L/M/N continuam EM_EXECUCAO,39/45 e67/115 inalterados, sem seloN ou produção.

Checkpoint0205 SHA-256 000b1a0dd6246d9184647666e7cdebd4b6283a723e7daad20ba7ef0927ff939b; relatório docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.
P06_REVIEW_OFFLINE_COMPLETE; sucessão conserva0203 c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Prefácios seguintes são fotografias históricas.

# P06 — revisão técnica concluída; requalificação física pendente (20/09/2026)

P06_REVIEW_OFFLINE_COMPLETE. Três achados médios corrigidos (causa de rollback,
remoções históricas no scanner e sucessão) e compatibilidade dos helpers preparada.
68 testes dirigidos e18 preflight canônico PASS, sem perfil/ambiente JDBC;
scanner17contraprovas, remoções5, sucessão11 e trilha completa preliminar PASS.
Readback final: target/P06-REVISAO-POS0202-20260920T225525774Z/closure.json e final-validation.

P07 tecnicamente elegível após gates finais; efeitos físicos exigem ordem própria
com alvo, vigência e orçamento. Nenhuma reserva física, SQL/JDBC/fonte ou DDL nesta
rodada. Novo JAR requer requalificação C–K; aceites antigos e recibos preservados.
L inclui regressão P07 e não recebe aceite integral; A/M/N seguem abertos.
39/45 construção e67/115 aceites inalterados. Nenhum selo A–N/revisão humana/produção.

Relatório: docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.
Checkpoint0204 SHA-256 a2e33d720076e67baf1d05da854c1968db427ae3d6ad0e8f30f7d0bcdbfce1a3. Sucessão vincula0203:
c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Fotografias abaixo preservam resultados e falhas anteriores.

# P06 — correções offline verificadas; integridade final em validação

P06_REVIEW_OFFLINE_COMPLETE refere-se à revisão técnica; gates finais de integridade em validação.
68 testes dirigidos e18 preflight canônico PASS, scanner17contraprovas e remoções5PASS.
Nova sessão/JAR exige P07 físico sob ordem própria; L/A/M/N continuam abertos.
39/45 e67/115 preservados. Consumo físico zero. Checkpoint0203 SHA-256 c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Relatório: docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.

Prefácios seguintes preservam fotografias históricas.

# Trilha — P04/I–J e P05/K aceitos no escopo local (20/09/2026)

O macrobloco POS0198 terminou: preflight canônico 18/18 no teto original
de 240 s; P04 20 ITs/20 unidades; P05 quatro escalas 2/4/8/16 e quatro
unidades, todos PASS. I, J e K ACEITO_NO_ESCOPO na matriz A–N, com recibos
completos, rollback e agregados preservados, bytes conferidos e zero processos.
Consumo 2/3 campanhas, 7.200/10.800 s; P04 #2 não usada. Estados autoritativos
e limites no prefácio vigente do STATES; evidência no catálogo POS0198.

39/45 e 67/115 inalterados. P05 local não fecha V2-050/P27 representativo.
P06–P08 permanecem sem execução nesta ordem. As lacunas históricas de
sucessão da trilha e oito ausências do scanner foram preservadas e registradas.
Fotografias anteriores abaixo não descrevem o aceite vigente.

# Trilha — POS0198: P04/I/J aceitos; P05 em execução

P05 reservada22:19:54Z–23:19:54Z após revalidar alvo/aceite/ledger/processos.
Preparação test-only8/8 PASS: parada serial após falha e agregados por escala.
Escalas2/4/8/16 ainda em execução, sem aceite K por inferência. Consumo2/3
campanhas,7200/10800s; P04#2 não utilizada. Limites,39/45 e67/115 preservados.

# Trilha — POS0198: P04/I/J aceitos no escopo shadow; P05 elegível

P04#1 PASS:20IT/20unidades,cinco XMLs íntegros,exit0/sem timeout,rollback e
246contagens preservados,zero processo próprio. Sucesso/cancelamento7etapas;
falha tardia5etapas com33/33/33/33/0. Máxima etapa31.072s; sequências abaixo
1800s. Fonte/banco2076arquivos conferidos. I/J ACEITO_NO_ESCOPO local,
conforme p04-acceptance.json e STATES; nenhum paiV2 ou contador histórico fecha.

P05 agora elegível após preparação da parada serial e dos agregados por escala,
prova offline e nova confirmação de alvo/ledger. Ainda sem reserva P05.
Consumo1/3campanhas; máximo original2P04+1P05,sem reutilizar reservas.

# Trilha — P04/P05 POS0198: preflight PASS; qualificação P04 em curso

STATES mantém a autoridade:72testes offline e preflight canônico18/18 PASS,
exit0/sem timeout no teto240s; controlador histórico inalterado. Duas causas
separadas corrigidas: contraprova explodida implícita sob Failsafe/JAR e
resolução repetida de ancestrais Windows. Binding/guards preservados e testados.

Ordem POS0198 adotada21:34:04Z, expira2026-09-22T21:34:04Z. Master local
confirmado; P04#1 reservada21:52:44Z por3600s, cinco ITs/duas travas/sintéticos
rollback-only. Consumo1/3; I/J IMPLEMENTADO_NAO_QUALIFICADO até prova integral.
P05 NOT_RESERVED_NOT_EXECUTED, condicionado ao aceite I/J nesta ordem.
39/45 e67/115 e fotografias históricas preservados. Relatório:
[POS0198](docs/catalogos/campanhas-integrais/P04-P05-POS0198-20260920.md).

# Trilha — P04/P05 pós-0196: guard corrigido; preflight bloqueia Physical

Ordem `P04-P05-POS0196-20260920-01` registrada com vigência absoluta48h e
saldo físico intacto. O guard do controlador agora lê exclusivamente os XMLs do
build isolado; sua matriz efetiva10/10 passou, preservando a recusa127 para os
cinco XMLs históricos com supervisor5/1/0/0. A asserção real do supervisor foi
executada2/2 offline/JDK17, incluindo33/33/33/33/0 e quatro mutações recusadas.

O preflight canônico da revisão corrigida (`ArtifactDirected`, JDK17,
heap512MiB,240s) fechou `exit124/timedOut=true` durante
`PackagedFixtureBindingIT`. Diagnóstico adicional: seleção Failsafe direta usa
o JAR como classe explodida e invalida a contraprova por fingerprint igual; a
seleção Surefire correspondente excedeu240s e foi contida como árvore própria.
Não houve SQL, JDBC, Physical nem reserva. I/J continuam
IMPLEMENTADO_NAO_QUALIFICADO; P05/K permanece inelegível e não executado.
39/45 e67/115, oito ausências e históricos preservados. Consultar
`P04-P05-POS0196-20260920.md`, ledger e checkpoint0197 antes de novo efeito.

# P02 — diagnóstico offline pós-0194 concluído, sem aceite I/J

STATES é a autoridade. Checkpoint0196 e
`docs/catalogos/campanhas-integrais/P02-DIAGNOSTICO-POS0194.md` retificam a
leitura de2405.848s: total de cinco testes, máximo individual758.817s;
recibos de sequência abaixo1800s e etapas abaixo240s. Não houve aumento de
limite, nova reserva ou Physical; o ledger histórico permanece intacto.

Asserção histórica33 no terminal bloqueado é defeito test-only; recibo confirma
33/33/33/33/0. Guard127 é defeito de caminho sourceRoot/build e não foi alterado:
a raiz correta encontra cinco XMLs e ainda recusa a falha real do supervisor.
Contraprovas documentais, javac17 e Spotless offline/JDK17 passaram; não se
executou o helper Java nem a integração. Sucessão histórica continua falha.

I/J IMPLEMENTADO_NAO_QUALIFICADO; P05/K sem reserva/execução e bloqueado por
precedência.39/45 e67/115 preservados; oito MISSING_CANDIDATE permanecem.
Correção mínima e prova offline estão delimitadas no relatório; nova Physical
depende de autoridade finita nova e preflight da revisão corrigida. P03–P08
não foram executados. Prefácios abaixo conservam a fotografia anterior, cuja
interpretação temporal fica explicitamente retificada por P02.

# Regra de execução — uma entrada e uma saída (reforço20/09/2026)

Uma entrada do usuário → ler a documentação e evidências → implementar,
integrar, testar, diagnosticar e corrigir → uma entrega final consolidada.
Não pedir “continue” ou autorização repetida entre etapas já cobertas. Não
encerrar com uma correção local apenas proposta quando ela pode ser executada.
Teste falho exige investigação e correção técnica, não uma nova passagem ao
usuário. Checkpoints/atualizações são internos ao trabalho e não exigem resposta.
Esta orientação permanente do STATES rege também retomadas e futuros macroblocos.
Limites explícitos e barreiras de segurança permanecem; dependência externa real
precisa de evidência, e progresso parcial nunca recebe aceite fictício.

# Trilha — P04/I-J pós-0194 não qualificado; P05 não executado

`P04-P05-POS0194-01` consumiu sua única reserva P04 local depois de preflight
ArtifactDirected18/18 PASS e confirmação do alvo sombra. A campanha terminou
sem timeout, com rollback agregado e sem processo próprio, mas não cumpriu o
critério integral: o supervisor levou2405.848s, acima de1800s, e os XMLs
isolados registram uma falha em cinco testes (expectativa33, observado0 no
cenário de oráculo tardio). O guard
também retornou127 ao procurar relatórios no workspace em vez do build isolado.
Esses fatos impedem aceite mesmo com quatro ITs sem falhas.

A expectativa test-only do terminal bloqueado foi ajustada após o fechamento
(preview vazio,33 nos anteriores), sem reexecutar a prova. A tentativa estática
parou antes da fonte pela incompatibilidade do Maven/JVM25 com o formatter fixado;
a reserva acabou e o excesso de sequência/guard de caminho continuam impeditivos.

I/J seguem IMPLEMENTADO_NAO_QUALIFICADO; P05/K não foi reservado/executado e
P06+ permanece fora do escopo. Não repetir a tentativa nem transferir sua
reserva.39/45 e67/115 não mudam. Evidência: `P04-P05-POS0194.md`, ledger
`target/P04-P05-POS0194-01/` e checkpoint0195; prefácios abaixo são históricos.

# Trilha — macrobloco P04→P05 pós-0193: P04 sem aceite, P05 não executado

A ordem finita `P04-P05-POS0193-01` consumiu as duas tentativas P04 no alvo
sombra autorizado. Preflight JAR/fixture/supervisor18/18 passou e quatro ITs
parciais mais16 unitários passaram nas campanhas; contudo o XML obrigatório de
`QualificationSequenceSupervisorIT` não foi produzido em nenhuma tentativa
contida. O controlador local foi corrigido e testado offline para transformar
essa ausência em falha127, sem aceitar o exit do wrapper. Rollback/agregados e
processos próprios foram reconciliados após cada contenção. I/J seguem
IMPLEMENTADO_NAO_QUALIFICADO; K/P05 não iniciou e P06 não é elegível. Não há
terceira P04 ou conversão de saldo.39/45 e67/115 permanecem. Consultar
`STATES.md`, checkpoint0194 e ledger privado antes de nova autoridade.

P04 em continuidade: corrigir fixture/JAR e provar offline; prosseguir com a
campanha local finita proposta na resposta anterior, sem nova reconfirmação,
registrando reserva própria e preservando ledgers fechados. Sem P05/produção.

# P04 — correção local testada18/18; qualificação física não fechada

Regra permanente de uma entrada/uma saída reforçada; defeito local foi corrigido
e testado sem nova pergunta. Autoria e supervisor do IT usam o mesmo runtime
JAR; driver de teste compartilhado, asserções propagadas, guard produtivo intacto.
p04-0190-bridge-final-offline PASS18/18, gates verdes,14 oráculos/duas sequências
e contraprovas. A única campanha física desta continuação foi consumida antes
da ponte final, com16 unidades/15 ITs PASS, sequência não qualificada e rollback
confirmado. Não repetir ledgers nem promover prova offline a aceite físico.
I/J IMPLEMENTADO_NAO_QUALIFICADO; P05/K não elegível,39/45 e67/115 inalterados.
Referência: P04-CONTINUIDADE-0190.md e checkpoint final desta rodada.

# P04 — prova física contida; correção do supervisor segue offline

BindingProbe demonstrou que o supervisor do IT ainda executava classes soltas
enquanto a fixture corrigida estava ligada ao JAR. Campanha própria fechada:
16 unidades/15 ITs PASS; sequência sem aceite, rollback confirmado, sem timeout.
A correção test-only prossegue sem pedir confirmação: mesmo JAR no supervisor
e na autoria, contraprova de propagação de asserção e verifyFiles completo.
Preflight p04-0190-bridge-offline em execução; I/J não aceitos, P05 não iniciado.

# P04 em continuidade — preflight17/17 e prova física em execução

SEQ-FIX-01 implementada somente na autoria de testes;14 oráculos ligados ao
JAR,3 adulterações recusadas. Preflight17/17/gates PASS. Campanha própria
`target/p04-continuidade-0190/ledger.json`: uma tentativa3600 s, mesmos limites
e alvo local, sem reutilização de reservas históricas. Physical em execução;
I/J não aceitos antecipadamente, P05 não iniciado,39/45 e67/115 inalterados.

# Trilha — P04: classpath provado, sequência bloqueada por runtime (20/09/2026)

Segunda tentativa da ordem P04-REQUALIFICACAO-20260919-01 consumida:16 unidades
e15 ITs PASS, incluindo retomada6/6 e quatro workers PASS_LOCAL com rollback.
Worker de sequência FAILED/LOCAL_SCENARIO_ORACLE_BINDING: oráculo vinculado a
classes descompactadas, worker vinculado ao JAR. Input/schema conferem.
Árvore própria encerrada; controlador observou rollback e agregados iguais,
logs íntegros, sem timeout ou processo remanescente. Duas tentativas totais,
nenhuma terceira; nenhuma correção adicional sem a respectiva qualificação.
I/J não aceitos integralmente; P05/K permanece não elegível.39/45 e67/115.
Próximo trabalho: corrigir a geração/vinculação da fixture em revisão nova,
validar offline e obter nova autoridade finita antes de qualquer prova física.
Consulte STATES e P04-I-J-RECONCILIACAO-20260920.md; históricos abaixo preservados.

# Trilha — P04 com causa corrigida offline; prova física pendente (20/09/2026)

O diagnóstico de 0187 foi retificado no STATES: leitura rasa de cinco stderr
comprovou QUAL_PACKAGE_RUNTIME_CLASSPATH, não timeout de etapa. Correção
proporcional e 16 testes offline PASS tornam elegível a segunda tentativa
condicional da mesma ordem P04-REQUALIFICACAO-20260919-01. Não há novo orçamento
nem autorização P05. I/J continuam não qualificados; 39/45 e 67/115 inalterados.
Históricos e ledger fechado são preservados; a continuação será registrada
em adendo, sem reaproveitar reserva consumida.

# Trilha — requalificação P04 bloqueada; P05/K não elegível (20/09/2026)

A ordem nova `P04-REQUALIFICACAO-20260919-01` não reutilizou o ledger fechado
anterior e consumiu uma tentativa P04. O preflight JDK17 foi 12/12 com os gates
de build verdes; o alvo local foi confirmado no `master`. A fase Physical criou
o JAR e as bibliotecas runtime antes do Failsafe. Unidades, sweep 5/5,
cancelamento 3/3 e concorrência 1/1 passaram, mas retomada ficou em 2/6, com
quatro exits 2, e a sequência excedeu seu teto de etapa sem recibo. A árvore
exata da tentativa foi interrompida, com rollback agregado igual antes/depois.

Não foi delimitada uma correção local proporcional e validável sem diagnóstico
recursivo de fixture proibido. Logo, a segunda tentativa condicional não foi
reservada, I/J não são aceitos e P05/K não iniciou. P03/B–H continua apenas
predecessor técnico local; P06–P08 continuam fora do escopo. Evidência:
`target/macrobloco-p04-requalificacao-20260919-01/ledger.json` e checkpoint0187.
Contadores permanecem 39/45 e 67/115.

# Trilha — P04 executado sem aceite; P05/K não elegível (20/09/2026)

O macrobloco adotado `P04-P05-APOS-0185-01` consumiu suas duas tentativas P04
locais. Ambas têm recibo conhecido e readback de rollback igual. A primeira
demonstrou que `Physical` não criava o JAR da fixture; a correção direcionada
foi validada offline. A segunda alcançou a criação do JAR, mas revelou que o
supervisor constrói o wildcard de classpath com `Path.resolve("*")`, inválido
no Windows; 4/6 retomadas falharam e o passo de recusa excedeu 240 s na coleta
recursiva da fixture. A correção e o contraprova unitária estão presentes, mas
não têm reexecução física coberta.

I/J permanecem **não aceitos localmente**; o sweep/preview parcial não substitui
worker, receipt selado, retomada e completude integral. P05/K não foi reservado
nem executado porque a precedência I/J não fechou. O saldo numérico do ledger
não transfere a autorização P04 falha a P05. P03/B–H conserva apenas o recorte
técnico aceito; 39/45 e 67/115 permanecem inalterados. P06–P08 seguem fora de
escopo. Consulte checkpoint0186 e ledger antes de qualquer nova autorização.

# Trilha — preparação integral P01–P33 disponível (19/09/2026)

Fotografia vigente: preparação local solicitada pelo usuário, **sem campanha física
nova e sem aceite P04/P05**. O [guia de preparação](docs/runbooks/preparacao-integral-trilha.md)
e o [mapa verificável](docs/catalogos/preparacao-trilha/plano.json) cobrem33 passos,
48 IDs abertos e entradas G01–G08/FEED. Consulte a seção14 antes de selecionar
outro macrobloco. O verificador offline passou com um positivo e24 negativos;
ele valida o mapa, não autoriza efeito nem substitui os gates históricos.

Retificação do bloqueio de saldo/vigência: a ordem original define limites por
sequência/etapa/campanha, mas não data final ou quantidade global numérica.
O prompt P04 manda conferir saldo/vigência e não renova a campanha. Ausência
de campo não prova expiração/esgotamento, nem autoriza tentativas ilimitadas.
Conferir a autoridade aplicável uma vez; se ainda faltar decisão, usar o pacote
finito já preparado no guia, sem pedir ao usuário que invente limites técnicos.
A proposta não está adotada. Preparação offline não fica bloqueada por saldo SQL.

Prioridade local restante: suficiência do predecessor P03 → P04/I–J → P05/K;
depois P06 e P07→P08, cada qual com suas condições. Reutilizar a causa temporal
comprovada. Conferir consumidores/revisão, não apenas hashes de testes; prova
in-process não substitui execução do JAR de P08. Fontes, governança e ambiente
têm filas de inputs preparadas, sem novas dependências artificiais entre elas.
39/45 e67/115 preservados; preparação não acrescenta percentual de construção.
Os prefácios abaixo são históricos, não ordens para repetir P01 ou outro hold.

# Trilha — P04: correção temporal offline; bloqueio de saldo/vigência (19/09/2026)

A fixture de sequência agora usa prazo lógico259200s, estritamente para cobrir a
janela civil sintética de11–14/08/2037 no tick de14/08 às12:00Z. A regressão
offline em `QualificationContractTest` mantém a recusa da configuração86400 e de
um tick posterior à nova fronteira, e admite as configurações de sucesso, falha
tardia e cancelamento. Build offline com JDK17, Enforcer, Spotless e Checkstyle
passou. A mudança não altera timeout, heap, SQL nem o contrato que recusa
deadline vencido.

P03/B–H foi vinculado aos recibos/pins vigentes e está ACEITO_NO_ESCOPO somente
como predecessor técnico da P04 local; ver
`docs/catalogos/campanhas-integrais/P03-B-H-RECONCILIACAO-20260919.md` e a
matriz A–N atualizada. P03 agregado, os demais fronts A–N e qualquer aceite real
permanecem abertos. P04/I–J não pode iniciar prova física: seis reservas P04 de
3600s são históricas observadas, mas não há saldo cumulativo nem vigência
auditáveis para uma sétima. O próximo trabalho elegível é somente receber e
conferir esse ledger/autorização, então reservar e executar P04 serialmente; P05
continua fora do escopo.39/45 e67/115 permanecem inalterados.

# Trilha — P04: bloqueio temporal anterior ao worker (19/09/2026)

Conforme a retificação no STATES e checkpoint0182, os três casos falhos de sql-06
foram DEFERRED/BLOCKED_DEPENDENCY antes de STARTED. Reprodução offline comprovou
LOGICAL_DEADLINE_EXCEEDED: prazo de um dia incompatível com o tick da fixture de
três dias. Cópia em memória com259200s permitiu os três planos (PASS_LOCAL/DUE),
sem executar worker/SQL. Correção de fixture e regressão ainda pendentes; não
concluir I/J nem atribuir a falha a classpath. Nenhum limite físico foi ampliado.

P03 conserva apenas o recorte aceito em0181; vincular critérios/pins B–H antes
de nova prova física P04. A matriz A–N não fornece ainda essa comprovação agregada.
39/45 construção (86,7%) e67/115 aceites (58,3%) preservados; nenhum percentual
representa produção. P05–P08 permanecem fora do escopo. Prefácios abaixo históricos.

# Trilha — P04 bloqueado por correção local (19/09/2026; diagnóstico histórico retificado acima)

P04/I–J não aceito. Após corrigir o classpath do worker para incluir as dependências seladas,
`p04-directed-10` compilou, mas `p04-supervisor-sql-06` terminou `exit=1`: 5 IT, 3 falhas,
0 erros/0 skips e rollback agregado confirmado. Sucesso, falha tardia e cancelamento ainda
retornam `exit=2`; recusa pré-SQL e owner vivo passaram. Preservar recibos e diagnosticar a
causa adicional antes de qualquer retry. P05–P08 continuam fora de escopo.

# Trilha — revisão 3.6: recorte P03 comprovado (19/09/2026)

Conforme STATES: sucessão MC e revisão tarifária corrigidas por V103/V104 qualificadas e instaladas somente em localhost/ETL_SISTEMA_V2_SHADOW. A/B sete etapas, referência três etapas e recomposição PASS; qualificação composta30unit/72IT distintos, rollback e bytes confirmados. A falha intermediária da contraprova(evidenceId) permanece registrada e foi resolvida por nova reserva da classe afetada, sem alterar relações/expectativas. [Relatório e recibos](docs/catalogos/campanhas-integrais/P03-CORRECOES-20260919.md).

P03 inteiro e paisV2 continuam abertos;39/45 e67/115 preservados. P04–P08 não iniciados. Próximo macrobloco: conferir explicitamente o saldo dos critérios P03 e então admitir P04(supervisor/preview), GPT-5.6 Terra/High; nova decisão semântica exige Astra/High. Não presumir o aceite do predecessor nem repetir a campanha aprovada sem mudança causal. Demais ordem/dependências/modelos P01–P33 preservados. Scanner/trilha históricos continuam falhos pelos motivos registrados, sem selagemP08. Novo prompt apenas quando solicitado.

As revisões/prefácios abaixo são históricos; os critérios e a tabela de dependências continuam vigentes.

<!-- Revisão3.5/checkpoint0180: regression01 reconciliada65PASS/1erro de evidenceId no teste novo; MatrixIT corrigida em prova02. Campanha07 PASS preservada. -->
<!-- Revisão3.5: campanha07 PASS30unit/6IT, A/B7etapas e referência3; rollback confirmado. Regressão dirigida em curso, checkpoint0179. P03 inteiro aberto. -->
<!-- Revisão3.5: V103/V104 instaladas localmente e qualificadas; checkpoint0178; campanha07 em curso, P03 não concluído. -->
<!-- Revisão 3.5 em execução: autorização P03 atual cobre V103/V104 locais; checkpoint0177. Instalação e provas funcionais pendentes. Fotografia3.4 abaixo preservada. -->
# Trilha de conclusão — prioridade, dependências e modelo GPT

**Revisão 3.4 — 19/09/2026. P01 reconciliado; P02 diagnosticado; recorteP03 bloqueado para evolução SQL.**


Resultado da estabilização solicitada: [relatório técnico](docs/catalogos/campanhas-integrais/ESTABILIZACAO-20260919.md), tentativa `sequence-campaign-sql-06`.27unit e2ITrecomposição PASS;2erros A/B por sucessão MC e2falhas de referência por SQL-05 vazio;rollback/agregados confirmados. Diagnóstico/asserções corrigidos, sem correção funcional SQL. P03 inteiro e paisV2 não concluídos;39/45 e67/115 preservados. A tabela abaixo conserva a ordem e os critérios; a fotografia05 é histórica.

Próximo macrobloco proposto: terminar este recorteP03 com sucessão relacional e revisão de tarifa, GPT-6 Astra/High. **Dependência concreta:** autorização explícita para preparar, qualificar e aplicar migrations aditivas somente no SQL local, com baseline e contraprovas; o pedido19/09 proíbe DDL/migrations. Sem essa mudança de escopo, P03 físico permanece bloqueado e P04 não é liberado. Sucessão finalP08/scanner integral continuam pendentes; não modificar manifests históricos nem restaurar as oito exclusões para contornar os gates. Não gerar prompt sem solicitação.

Checkpoint final0176: [diagnóstico e bloqueio SQL](docs/continuidade/checkpoints/0176-estabilizacao-diagnosticada-bloqueio-sql.md). Auditoria final preservou115checkboxes/67marcados, índice/schema/manifests; diff/UTF-8 PASS. Scanner integral FAIL apenas8exclusões preexistentes; validator histórico FAIL de sucessão no worktree. Provas detalhadas e snapshots em stabilization-20260919, sem selo finalP08.

**Quando você pedir o próximo prompt:** ler esta trilha junto com o `STATES.md`, selecionar um macrobloco coeso para o mesmo chat e indicar seu GPT/nível. O pedido copiável está na seção11; as regras de agrupamento estão na seção13. Não existe obrigação de trocar de chat ou gerar prompt a cada tarefa.

**Uma entrada e uma entrega final por prompt:** o usuário inicia o macrobloco uma vez; o executor trabalha até o resultado ou bloqueio comprovado, sem pedir continuação por etapa. A entrega final consolida todo o escopo executado.

Este roteiro foi reorganizado a partir dos critérios de `Tarefas pendentes` e da ordem obrigatória do [STATES.md](STATES.md), confrontados com a campanha técnica em andamento. **P01–P33 substituem os IDs ECO das versões anteriores**, que eram planejamento e não foram executados por esta trilha. Não são novos blocos funcionais nem novos aceites do projeto.

O objetivo é terminar o trabalho que falta, reaproveitando a construção já comprovada. A ordem das tarefas vem antes da escolha de modelo. O roteiro não concede autorização de fonte, banco, infraestrutura, release ou produção.

## 1. Ponto de partida histórico da revisão — atualizar pelo prefácio vigente

| Situação | O que fazer com ela |
| --- | --- |
| Campanha A–N `EM_EXECUCAO`, [checkpoint técnico 0168](docs/continuidade/checkpoints/0168-recomposicao-e-falhas-provadas-campanha-em-correcao.md) | Fechar essa campanha antes de abrir outra frente de construção local abrangente. Os checkpoints 0169–0171 são documentais. |
| Base0165/schema102 registrados; 39/45 unidades de construção e 67/115 checkboxes | Preservar. Os números medem coisas diferentes; não são percentual de prontidão produtiva. Os 48 checkboxes abertos incluem pais e subitens. |
| `sequence-campaign-sql-05/result.json`: OBSERVED, exit1, rollback confirmado | A tentativa tem resultado conhecido. Os XMLs registram quatro IT, duas falhas e dois erros. Ainda é preciso reconciliar a revisão testada com a autoria atual; não repetir automaticamente a tentativa. |
| WORKLOG registra mudança posterior para sete etapas | O resultado da tentativa05 pertence ao snapshot anterior. Faltam provas da revisão atual, quatro escalas, pacote e fechamento. |
| Validador histórico falha em `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`; scanner integral anterior apontou oito `MISSING_CANDIDATE` | Problemas já existentes e registrados. P01 classifica a sucessão e as exclusões; P08 só fecha com evidência válida da revisão entregue. Preservar alterações do usuário e manifests históricos. |
| Verticais básicas, expansões, seis dimensões, cinco fatos e 19 saídas têm construção local registrada | Checkbox agregado aberto não significa “implementar tudo de novo”. Confrontar com o contrato real e alterar somente o delta necessário. |
| G01–G08 continuam com parcelas externas pendentes | Receber os inputs indicados na seção 4. Nenhum modelo substitui fornecedor, owner, ambiente ou autorização. |

Fontes da retomada: [AGENTS.md](AGENTS.md), [contexto global](../CONTEXTO_GLOBAL.md), [RETOMADA](docs/continuidade/RETOMADA.md), [protocolo](docs/runbooks/continuidade-agentes.md), [contrato da campanha](docs/catalogos/campanhas-integrais/CONTRATO.md), [matriz A–N](docs/catalogos/campanhas-integrais/matriz-a-n.json) e [gates externos](docs/catalogos/campanhas-integrais/ENTRADAS-E-EFEITOS-EXTERNOS.md). Recibos completos ficam em `target/macrobloco-campanhas-integrais-20260915-01/`.

## 2. Como seguir a ordem

1. **Precedência local: P01 → P02 → P03 → P04 → P05 → P06 → P07 → P08.** Começar no primeiro resultado ainda pendente da fotografia vigente, não reiniciar P01 a cada chat. P02 pode ser dispensado se a causa mecânica já estiver demonstrada; registrar a justificativa. Não repetir entrega comprovada na revisão correta.
2. **Depois: escolher a menor prioridade P09–P33 que esteja elegível para o escopo.** A coluna “Depende de” determina a precedência. Um bloqueio não permite pular sua dependência, mas permite executar outra linha independente.
3. **Instanciar por entidade, fato ou contrato.** Exemplo: `P17/Coletas`, `P20/Cotações`, `P24/MAT-04`. Um resultado de Cotações não libera Fretes; uma pendência de Inventário não bloqueia o que não o utiliza.
4. **Solicitar os inputs externos desde P01**, pelo quadro da seção 4, sem gastar chats repetidos para reinventariar o mesmo bloqueio. Esta orientação prepara a lista para o usuário; não autoriza enviar mensagens a terceiros.
5. **Concluir cada etapa pelo resultado de saída.** Registrar revisão, camada, evidência, limitações e próximo P elegível. Só marcar o ID canônico no STATES quando seu critério original inteiro estiver atendido.

Nos passos repetíveis, dependências significam **a fatia usada**, não toda a tarefa agregada. Uma dependência não aplicável precisa de motivo aceito; não se apaga a linha. Preparação documental pode anteceder o efeito, mas não conta como execução ou aceite físico.

## 3. Prioridade imediata — fechar a campanha local

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P01 — Reconciliar estado, revisão e bloqueios locais** | **Terra / Medium** | Nenhuma etapa nova; leitura dos documentos obrigatórios | Comparar checkpoint0168, WORKLOG, inputs, resultados04/05, relatórios e autoria de sete etapas; conferir processos próprios antes de repetir efeito. Inventariar a sucessão documental e as oito exclusões preexistentes; separar divergência legítima de arquivo perdido. Listar apenas inputs externos ainda faltantes. | Checkpoint técnico atualizado com revisão atual, provas reaproveitáveis, falhas conhecidas, lacunas de integridade e próximo teste permitido. Não restaura arquivo, altera índice ou reescreve hash histórico por inferência. Inicialmente read-only/documental; qualquer efeito posterior conserva seus gates. |
| **P02 — Diagnosticar a falha restante** | **Astra / Medium** | P01 | Investigar a causa atual em `IntegralCampaignIT`/`SequenceReferenceIT`, capturas, revisões de suplemento, releases, relógio e oráculos. Separar defeito do harness de defeito do produto. Não presumir que a correção da04 resolveu a05. | Causa demonstrada, correção delimitada e contraprova que preserva o contrato. Se houver decisão crítica não resolvida, subir a High com questão específica. Se a causa já está provada em P01, registrar P02 dispensado e seguir. |
| **P03 — Corrigir e qualificar a sequência atual** | **Terra / High** | P01; P02 quando necessário | Completar as lacunas B–H: entrada, executor, modos, agenda, referências, recomposição, oráculos e isolamento. Provar campanhas A/B na autoria atual de sete etapas, mantendo tetos e ordem Coletas→Fretes. | Provas dirigidas e IT aprovadas da mesma revisão; cinco fatos/19 saídas por etapa, replay/referências/recomposição e falhas observadas. Não mudar o esperado apenas para acompanhar a implementação. |
| **P04 — Fechar supervisor e preview** | **Terra / High** | P03 | Frentes I/J: journal, cancelamento, recibos parciais, admissão de retomada, isolamento e 33 previews. Reaproveitar mecanismos existentes. | Sucesso e recusas demonstrados, rollback confirmado, evidência parcial preservada. Preview não concede apply; transação perdida não vira recovery durável de domínio. |
| **P05 — Medir as quatro escalas locais** | **Terra / Medium** | P04 | Frente K: executar as quatro escalas admissíveis e medir heap, lote, linhas em voo, SQL e duração no caminho novo. Corrigir somente gargalo comprovado. | Recibos por escala e limites cumpridos. Amostra sintética limitada não prova SLO nem platô produtivo. Defeito de desenho vai a Astra Medium; depois retorna para implementação delimitada. |
| **P06 — Revisar o delta técnico antes do pacote** | **Astra / Medium** | P05 | Frente L, parte de revisão: conferir invariantes de transação, identidade, oráculos, compatibilidade e suficiência das provas A–K. Revisar somente o delta e as dependências atingidas. | Achados tratados; correções voltam à prova afetada. Não transferir falha local para G01–G08 nem alegar revisão humana. |
| **P07 — Executar o gate final da revisão** | **Terra / Medium** | P06 | Frente L, regressão: build, testes, formatter/lint/análise estática e verificações existentes exigidas para a revisão final. Separar skips previstos de prova ausente. | Gates aplicáveis aprovados e vinculados ao código que será empacotado. Nova alteração relevante invalida a prova afetada e exige sua revalidação. |
| **P08 — Executar pacote, selar e fechar A–N** | **Terra / High** | P07 | Primeiro M: executar JAR extraído/supervisor, A/B e recusas. Depois N: diff/overlay, matrizes, scanner, sucessão legítima, selo/readback e continuidade. Usar Luna apenas para resumo de evidência já decidida. | Artefato executado e entrega local íntegra, com A–N rastreáveis. Resolver os bloqueios locais de integridade na revisão atual, preservando históricos/exclusões legítimas. Não declarar entrega selada se o gate pertinente continua vermelho. Atualizar STATES → trilhas → validadores → checkpoint. |

**Resultado de P08: entrega local da campanha.** Não fecha automaticamente os pais V2-012/013/035/036/037/038/039/050 nem qualquer aceite real.

Correspondência da campanha: **A→P01; B–H→P02/P03; I/J→P04; K→P05; L→P06/P07; M/N→P08.** G acompanha os consumidores C–F. A evidência anterior válida pode satisfazer uma frente; a matriz `EM_EXECUCAO` não deve ser promovida só por existir código.

## 4. Inputs que precisam ser preparados desde o início

Este quadro deriva dos gates já existentes. Reutilizar seus catálogos e pendências; não criar nova coleta burocrática. Responsáveis são papéis, até haver nome/time confirmado.

| Prioridade do input | Quem fornece | Artefato que falta | Libera |
| --- | --- | --- | --- |
| **Primeira: G01 / V2-041** | Segurança e Operações | Atestados de rotação/invalidação, consumidores e saúde do writer legado; autorizações próprias por fonte | P09 e, depois, chamadas autenticadas/operacionais cobertas. Não bloqueia P01–P08 sintéticos. |
| **Primeira: G03, por fonte** | Fornecedor e owner de dados | Identidade/grão/tipos/cardinalidade, filtros, snapshot/completude, oráculo, janela e limites | P13–P20 da entidade. Antecipar pedidos difíceis de CAP/FAT/INV/SIN/Raster sem sondar fonte não autorizada. |
| **Primeira: G04, por consumidor** | Negócio e donos de referências/saídas | Baseline de referências, regras fiscais, série NFS-e, frota, ausência; manifesto nominal das 18 saídas externas e escopo da 19ª interna | P14/P15/P21–P26. Não consultar o projeto de dashboards para inventar o contrato. |
| **Antes de CI: G02** | Owner do repositório | Remote/provedor, aprovadores, proteções, autorização de publicação | P10. Não bloqueia correção offline ou sonda que tenha autorização própria. |
| **Antes do efeito material: G05 / V2-045b** | DBA, Operações, Segurança e Compliance | Ambiente V2 dedicado, principals, TLS, storage, retenção ratificada, backup/restore, RTO/RPO, quota/janela e autorização por efeito | P12 e execuções reais P15–P29 pertinentes. Não converter autorização rollback-only em permissão de COMMIT/crash/restore. |
| **Antes do feed: V2-015d** | Responsável de segurança | Feed/NVD autorizado, aceite de baseline e tratamento nominal de achados/exceções | P11; obrigatório antes do release/corte. Não bloqueia sonda ESL independente só por ser “rede”. |
| **Na convergência: G06/G07/G08** | Donos da operação, consumidores e corte | Pacote, rota, Tcut, executores, janela, aceites de ensaio/corte, observação e retirada | P31/P32/P33, respectivamente. Autorizações são distintas. |

## 5. Preparar as condições reais, sem criar dependência falsa

P09–P13 podem ser preparados enquanto a campanha local termina. **Efeitos externos continuam condicionados ao input e à autorização exatos.** A prioridade numérica desempata tarefas elegíveis; P10 não precisa esperar P09, nem P13 precisa esperar a construção inteira do ambiente se sua sonda for somente em memória.

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P09 — Fechar rotação e continuidade** | **Terra / High** | P01; G01 | V2-041: conferir o procedimento coordenado e os atestados por classe/consumidor; rotação efetiva pelos responsáveis conforme autorização. | Invalidação anterior e continuidade comprovadas, evidência sanitizada e limites válidos para a próxima fonte. Remover texto de segredo do código não substitui rotação. |
| **P10 — Ativar governança remota** | **Terra / Medium** | P01; V2-016a já entregue; G02 | V2-016b: publicar somente o conjunto aprovado; remote, protections, CODEOWNERS e CI/Gitleaks no SHA remoto autorizado. | Histórico escaneado, checks obrigatórios executados e aprovadores reais. Teste local não recebe o nome CI. |
| **P11 — Aceitar baseline de vulnerabilidades** | **Terra / Medium** | P01; política/implementação locais já prontas; autorização do feed | V2-015d: executar gate real, classificar achados e tratar exceções pelos responsáveis. Reusar a política fail-closed. | Baseline aceita e evidência de correção/exceção válida. Decisão de segurança inédita vai a Astra High e ao aceitante competente. Não exigir conclusão desta etapa para toda consulta independente. |
| **P12 — Qualificar ambiente e fundação operacional** | **Terra / High** | P09 e P10 para fechamento operacional; G05; subgates locais V2-042/022b já comprovados | V2-045b/V2-039a: conferir banco V2 dedicado, schema fresh/upgrade quando autorizado, identidade/grants, TLS, retenção, storage, backup/restore, RTO/RPO, config, scheduler e alertas. Qualificar os critérios próprios do runtime V2-022 necessários às execuções seguintes. | Ambiente e capacidade operacional no escopo autorizado; provas materiais separadas das provas rollback-only. Não fechar V2-022 agregado só porque V2-022b está marcado. Se sua agenda/recuperação ainda falha, corrigir antes do consumidor dependente. Não é V2-038 final nem startup produtivo automático. |
| **P13 — Reconfirmar contratos de fonte** | **Terra / High** | P09; G03 e autorização da fonte/rodada | V2-025d: rodada serial conforme allowlist e limites para as nove requisições Data Export; atualizar fingerprints e matriz de campos. Aceitar contrato válido já fornecido na fatia apropriada, sem repetir sonda desnecessária. | Contrato por entidade com tipos, filtro, janela, paginação, identidade candidata e lacunas explícitas. A agregada025d só fecha ao cumprir toda a rodada requerida. Usuários continua GraphQL ratificado; Raster tem contrato/autorização próprios; não inferir template 9901. |

P12 precisa estar apto **antes de receber dados reais ou executar prova material no alvo**. Isso é diferente de exigir infraestrutura produtiva para testes sintéticos locais já autorizados. Se P12 estiver bloqueado, P13/P14 e a preparação documental de outras frentes podem avançar dentro do seu alcance.

## 6. Da fonte à paridade core — repetir por entidade

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P14 — Fechar identidade e decisões semânticas pendentes** | **Astra / High** | P13 na entidade, ou contrato versionado equivalente válido; prova G03/G04 | V2-009b/8636/4924/10633/6392 e V2-009c. Decidir raiz/filho, título/documento, crosswalk, tenant, rekey, grão e precedência fiscal quando aplicável. Não reabrir identidade já aceita sem drift. | Decisão sustentada, casos de colisão/cardinalidade e ADR/contrato quando necessário. Se o problema é falta de evidência, registrar exatamente qual; não inventar chave. Uma identidade inequívoca já aceita dispensa novo diagnóstico Astra. |
| **P15 — Ratificar referências usadas** | **Terra / High** | P12 para importação material; G04 | V2-035a: qualificar baseline autorizado, releases, vigência, proveniência, calendário, filial, frota e tarifas das famílias realmente usadas. | Referências ratificadas e verificadas no escopo, sem seed/default produtivo inventado. **Não inclui o fechamento das dimensões derivadas**, que ocorre em P21. |
| **P16 — Adequar e provar a vertical em sombra** | **Terra / High** | P12 para efeito material; P13; P14 quando pendente; P15 somente para referência usada | V2-009d, V2-029/030/031/032/034b e deltas das verticais básicas se houver drift. Comparar contrato real com código existente; ajustar somente o necessário. | Implementação apta em sombra com enforcement, mapper/presença/frescor, staging/promoção, DQ, replay e provas pertinentes. Não fechar pai com binding sintético. Fonte pronta não precisa esperar dimensão que será derivada dela. |
| **P17 — Aceitar a caracterização inicial** | **Terra / High** | P16, contrato/identidade aplicáveis e fonte-oráculo autorizada | V2-012a: comparar janela representativa e limitada antes do bootstrap: campos, tipos, presença, chaves, grão, status, tempo, expansão e relações disponíveis. | Relatório de divergências resolvidas/classificadas e aceite da fatia012a. Inspeção `/info` de P13 sozinha não satisfaz esta etapa. |
| **P18 — Planejar e executar bootstrap histórico** | **Terra / High** | P17 e ambiente P12; autorização da origem/destino | V2-047: reusar planejador; escolher reextração/export/ponte autorizada, T0, horizonte, partições e delta até Tcut. Executar somente quando a vertical estiver IMPLEMENTADA_EM_SHADOW. | Histórico reconciliado por entidade; namespace bootstrap separado, sem watermark incremental elevado pelo maior período. Linha sem histórico necessário recebe NOT_APPLICABLE aceito. Migração read-only do legado requer autorização própria. |
| **P19 — Fechar as relações MC e CF** | **Terra / High** | P18 de Manifestos para MC; P18 de Coletas/Fretes quando aplicável para CF; runtime V2-022 apto em P12 | Primeiro V2-046a: Manifesto→Coleta/backlog/hidratação, com bases010/026. Depois V2-046b: Coleta→Frete, com046a e011. Reexecutar crosswalk após cada bootstrap pertinente. | Cardinalidade, órfãos, conflito, replay e SLA provados em SQL; sem heurística silenciosa nem multiplicação de raízes. A base Fretes não espera046a para existir; **a paridade relacional espera a relação**. |
| **P20 — Aceitar paridade core** | **Terra / High** | P17; P18 se houver histórico; P19 somente nas relações usadas | V2-012b: comparar conjuntos, chaves, nulos, status, datas, relações, somas e histórico em janelas fechadas/repetidas, set-based. | Divergências corrigidas ou aceitas nominalmente, evidência por entidade. Não depende de fato/view downstream. Libera os consumidores core, a política de ausência e os fatos que usam essa entrada. |
| **P21 — Fechar dimensões derivadas** | **Terra / High** | P16/P18/P20 das fontes usadas e P15 aplicável | V2-035b: finalizar conteúdo, papéis, vigência/rekey e paridade das seis dimensões a partir das entradas qualificadas. | Dimensão no grão aprovado e prova própria. Fonte→dimensão, nunca dimensão→própria fonte. Contrato de publicação da dimensão continua em P25. |

### Ordem das entidades dentro de P13–P20

Esta é a prioridade operacional sugerida quando as respectivas entradas estiverem disponíveis. Não cria dependência entre entidades independentes. Pular uma entidade bloqueada libera somente a próxima que **não dependa dela**.

| Prioridade | Entidade | Precedência material e principal pendência |
| --- | --- | --- |
| 1 | Usuários | Base033 já existe; snapshot GraphQL real e completude/paridade. Apoia Coletas e dimensão Usuários; não inventar incremental temporal. |
| 2 | Manifestos | Base026 existe e não precisa aguardar relação MC para ingerir. Bootstrap de Manifestos precede046a. Provar pick/MDF-e/crosswalk reais. |
| 3 | Coletas | Base010 existe; Usuários/referências pertinentes aptos. Bootstrap e046a alimentam o fechamento relacional. |
| 4 | Fretes | Base011 existe; depende da base de Coletas. Bootstrap/046b precedem paridade do escopo Coleta→Frete. |
| 5 | Cotações | Independente das relações MC/CF; pode avançar antes quando pronta. Exige referência tarifária ratificada para seu consumidor. |
| 6 | Localização | Base028 depende de Fretes; falta vínculo/tempo nominal e paridade. Não esperar CAP/FAT/INV/SIN se não os utiliza. |
| 7 | Contas a Pagar | Raiz/parcela/rateio8636 ainda sem prova nominal; não bloquear as seis anteriores. Alimenta Plano de Contas e Filiais. |
| 8 | Faturas por Cliente | Depende de Fretes e identidade/título/crosswalk4924; série NFS-e precisa de fonte própria. Alimenta MAT-03/MAT-04. |
| 9 | Inventário | Depende de Fretes e identidade de raiz/componentes10633; alimenta MAT-02. |
| 10 | Sinistros | Depende de Fretes e identidade/arrays/semântica temporal6392; qualificação própria. |
| 11 | Raster condicional | Decisão MANTER já existe, mas identidade009c/completude/frescor e autorização próprios ainda são necessários. Fora da primeira onda; ausência não autoriza omitir sua responsabilidade mantida no encerramento. |

Dependências exatas de P21, pelo STATES: **Filiais ← Fretes/Manifestos/CAP/FAT; Clientes ← Coletas/Fretes/FAT; Veículos/Motoristas ← Manifestos + decisão035c/provas de frota; Plano de Contas ← CAP; Usuários ←033.** A existência local das seis dimensões não substitui os cadastros/bindings nominais.

## 7. Ausência, fatos e saídas — ramos independentes após o core

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P22 — Aceitar a política de ausência** | **Astra / High** | P20 por entidade; contrato de snapshot e G04 | V2-013: classificar as 33 responsabilidades, presença raiz/filho, confirmações, propagação, guardrails e reativação com os responsáveis. | Aplicabilidade nominal ENABLED/DISABLED/BLOCKED/NOT_APPLICABLE fundamentada. Ausência de completude não vira ENABLED. A linha aceita é necessária ao corte, mesmo sem prune. |
| **P23 — Implementar/provar apply quando permitido** | **Terra / High** | P22; autorização da entidade; snapshot completo e ambiente P12 | V2-013: completar somente os consumidores de ausência habilitados; provar preview/apply separado de upsert e as recusas de parcialidade, cap, vazio anômalo e reuso de evidência. | Soft delete/reativação e salvaguardas comprovados onde aplicáveis; para linha sem apply, registrar motivo aceito e não executar mutação. Não transforma033/013 locais em sweep real universal. |
| **P24 — Qualificar os cinco fatos** | **Terra / High** | P20 das entradas; P15/P19/P21 apenas nos subconjuntos usados | V2-036: adequar cargas existentes ao grão/regra nominal e provar consumo de entradas PUBLISHED no ambiente sombra, cardinalidade, no-op, partição e recomposição. | Fato em paridade com regras MAT-01–05 e decisões fiscais/filial resolvidas. Não depende de012c, que vem depois. Não espera P23 quando o fato não exige apply de ausência. |
| **P25 — Fechar contratos SQL de consumo** | **Terra / High** | P16/P20 para core; P21 para dimensão; P24 quando houver fato; manifesto consumidor G04 | V2-037: conferir os 19 contratos, nomes/ordem/tipos/nullability/grão/filtros e linhagem completa. SQL10 interno tem escopo próprio; 18 saídas são externas. | Contrato aprovado, teste/fingerprint e compatibilidade. Nenhum wrapper cross-database; não ler dashboards como oráculo. Contrato core independente não espera todos os fatos. |
| **P26 — Aceitar paridade analítica** | **Terra / High** | P25 e P20 de todas as entradas; P24 quando aplicável | V2-012c: comparar fato/saída com oráculo nominal, set-based, incluindo fiscal, somas, relações, labels e filtros. | Divergências corrigidas ou aceitas por saída; liberar qualificação dessa saída, sem retroalimentar dependência do fato. |
| **P27 — Provar memória e SQL por escopo** | **Terra / Medium** | P20 para core; P26 para fato/view, com036/037 aplicáveis | V2-050: medir heap, lotes, linhas/bytes em voo, planos/IO e duração em volume representativo autorizado. Reusar harness local. | Gate por vertical/fato com platô/limites e push-down demonstrados. Medição local P05 não fecha automaticamente este aceite. Investigar só gargalo observado. |

**Precedência, em termos simples:** core → política/ausência e core → fatos/contratos são ramos separados. Um bloqueio de sweep não impede construir ou comparar uma saída independente; continua bloqueando o aceite que realmente exija aquela capacidade.

### Dependências dos fatos: não esperar uma entidade sem necessidade

Todos exigem paridade core aceita e entradas PUBLISHED em sombra. A tabela reproduz o DAG de V2-036; relações/dimensões adicionais só entram quando efetivamente usadas.

| Fato | Entradas e referências exigidas |
| --- | --- |
| MAT-01 — Fretes operacional | Fretes + Localização + pagadores excluídos/documentos de filiais |
| MAT-02 — Coletores | Fretes + Manifestos + Inventário + aliases/filiais |
| MAT-03 — Faturamento | Fretes + Localização + Faturas por Cliente + calendário/atribuição de filial |
| MAT-04 — Faturas | Faturas por Cliente; grão de título e precedência fiscal nominal |
| MAT-05 — Manifestos | Manifestos + Coletas + Fretes + frota própria; vínculos MC/CF usados |

Assim, MAT-01 pode avançar antes de Inventário; MAT-02 aguarda Inventário; MAT-04 não precisa esperar MAT-03. Escolher o primeiro fato com todos os inputs prontos, sem impor uma cadeia artificial MAT-01→MAT-02→MAT-03→MAT-04→MAT-05.

## 8. Convergência para operação e conclusão do projeto

| Etapa | Modelo / esforço | Depende de | Trabalho concreto | Saída exigida |
| --- | --- | --- | --- | --- |
| **P28 — Qualificar E2E/recovery/desempenho** | **Terra / High** | P12, P27 e P20 ou P26 do escopo; P23 só para funcionalidade de ausência habilitada | V2-038 e parcelas restantes de022: agenda, blackout, catch-up, replay, cancelamento, concorrência, durabilidade e recovery sob autorização específica. | Qualificação reproduzível por SHA, budgets e checkpoint/efeitos corretos. Prova com COMMIT/crash/restore não é substituída por rollback local. Gate por entidade/saída, sem antecipar corte. |
| **P29 — Congelar RC e gates de release** | **Terra / Medium** | P10, P11, P12 e P28 da onda/unidade; G01 vigente | Primeiro V2-039b: pacote/config/checksum/proveniência/SBOM e smoke. Depois V2-015c: SAST/licenças/segurança e SOs suportados no mesmo RC. | Artefato e relatórios coerentes, pronto para revisão/ensaio. Alteração de código/config relevante exige requalificar o impacto e congelar outra revisão. |
| **P30 — Revisar a unidade real de corte** | **Astra / High** | P29; P18/P20/P25/P26/P28 de todas as responsabilidades usadas e P22 aceito | V2-048b/V2-014: confrontar topologia048a, cobertura, Tcut, writer único, fences, ponto de não retorno, rollback pré-escrita e recuperação V2 pós-escrita. | Pacote de ensaio concreto sem lacunas da unidade **CUTOVER-DB-01 / DATABASE_WIDE**. Revisão técnica não substitui aceite humano nem autoriza provisionar ou cortar. |
| **P31 — Ensaiar troca e recuperação** | **Terra / High** | P30; G06 | V2-048b: ensaio material isolado, carga histórica+delta, freeze, fences negativos, troca e duas recuperações, pelos executores autorizados. | Ensaio aceito, RTO/RPO medidos e somente SIMULATED_PNR. Falha retorna à causa; não vira aprovação de corte. |
| **P32 — Executar o corte autorizado** | **Terra / High** | P31, DoD/gates completos da unidade e G07 | V2-014: conferir autorização nominal/datada, backup/rota/abort, revogação do writer antigo antes do novo e smoke pelos responsáveis. | Primeira publicação produtiva aceita, writer único e PNR registrado. Depois do PNR, recuperação aprovada permanece no V2; legado não volta a escrever. |
| **P33 — Encerrar responsabilidades e retirar o legado** | **Terra / Medium** | P32 para todas as responsabilidades mantidas; observação/retenção e G08 | V2-040: reconciliar comandos, dados, campos, views, jobs, credenciais, consumidores e ownership; executar retirada/arquivamento explicitamente autorizados. | Nenhuma responsabilidade mantida sem destino; evidência e documentação final. GraphQL/adapters só saem quando não houver consumidor e existir substituição válida. |

**Qualificar por entidade não significa cortar por entidade.** O STATES mantém DATABASE_WIDE até prova e ratificação de outra topologia. Raster condicional ou qualquer responsabilidade não aplicável exige tratamento nominal na unidade; não pode sumir da matriz para facilitar o corte.

## 9. Modelos escolhidos depois das dependências

| Modelo | Uso nesta trilha | Regra de economia |
| --- | --- | --- |
| Terra Medium | Reconciliação, execução de gates, medição, CI, pacote/RC prescritos e documentação final | Padrão quando procedimento e critério já são claros. |
| Terra High | Java/SQL, transações, integração, paridade, provas físicas e selagem técnica | Manter escopo delimitado; diagnóstico profundo não deve virar várias tentativas cegas. |
| Astra Medium | Diagnóstico P02 e revisão P06 | Receber reprodução, invariantes, diff e evidência; devolver decisão executável. |
| Astra High | Identidade P14, ausência P22 e revisão do corte P30 | Reservar para ambiguidade semântica e consequências difíceis de recuperar. Se decisão já estiver comprovada, reaproveitar. |
| Luna Low/Medium | Resumo, links e transcrição de resultados explícitos dentro de uma etapa | Auxílio opcional. Não criar um chat só para copiar logs; não atribuir selo, decisão de aceite ou revisão de segurança a Luna. |
| Sol | Alternativa opcional | Nenhuma etapa exige passagem por Sol. Usar somente se evidência de consumo/qualidade da sua tarefa justificar. |

A escolha é recomendação de engenharia, não benchmark deste repositório. Medir **custo total até cumprir o mesmo aceite**, incluindo contexto, raciocínio, correções e revalidação. Nas taxas Standard publicadas, Astra custa 2,5× Sol por categoria de token; isso não prova que a entrega custará mais ou menos. Com proporções iguais de entrada/cache/saída, gastar menos de 40% dos tokens totais torna Astra mais barato; não foi medido aqui. [OpenAI Docs — preços](https://learn.chatgpt.com/docs/pricing).

Não usar Max/Ultra/Fast por padrão. Ultra envolve delegação e não integra este fluxo sem subagentes. xhigh só após High deixar uma questão concreta sem solução. Nomes/disponibilidade variam pelo cliente; Low pode aparecer como Light. [OpenAI Docs — modelos e esforço](https://learn.chatgpt.com/docs/models).

Uma etapa não exige um chat novo: executar passos contíguos no mesmo modelo enquanto o contexto for útil, salvando checkpoints. Ao trocar de modelo, levar um resumo curto com paths/recibos e o problema exato. Não repetir suíte, scanner ou investigação de hold sem mudança que justifique, preservando todos os gates obrigatórios.

## 10. Cobertura dos itens abertos do STATES

Todos os 48 IDs abertos da fotografia lida estão mapeados abaixo. Pais agregados e suas fatias não contam como implementações independentes. Nenhuma linha desta trilha marca aceite funcional.

| IDs abertos | Etapas responsáveis |
| --- | --- |
| V2-041 | P09 |
| V2-016, V2-016b | P10 |
| V2-015, V2-015c, V2-015d | P07, P11, P29 |
| V2-045, V2-045b | P12 |
| V2-022 | P01–P08, P12, P28 |
| V2-025, V2-025d | P13 |
| V2-009, V2-009b, V2-009b/10633, V2-009b/8636, V2-009b/4924, V2-009b/6392, V2-009c, V2-009d | P14, P16 |
| V2-035, V2-035a, V2-035b | P15, P21 |
| V2-047 | P18 |
| V2-046, V2-046a, V2-046b | P19 |
| V2-012, V2-012a, V2-012b, V2-012c | P17, P20, P26 |
| V2-013 | P04 local; P22/P23 reais |
| V2-029, V2-030, V2-031, V2-032, V2-034, V2-034b | P14/P16 e gates por entidade posteriores |
| V2-036 | P24 |
| V2-037 | P25 |
| V2-050 | P05 local; P27 representativo |
| V2-038 | P28, reutilizando somente provas aplicáveis P03–P08 |
| V2-039, V2-039a, V2-039b | P08 local; P12/P29 operacionais |
| V2-048, V2-048b | P30/P31 |
| V2-014 | P32 |
| V2-040 | P33 |

## 11. Pedido para gerar o próximo prompt

Use o mesmo pedido neste chat ou em qualquer chat posterior, depois que o resultado do trabalho estiver registrado. Você não precisa escolher os passos nem o modelo antecipadamente:

```text
Leia STATES.md e TRILHA_CONCLUSAO_POR_MODELO.md atualizados.
Com base nos dois, monte o próximo macrobloco coeso que possa ser executado
inteiro no mesmo chat, respeitando prioridades, dependências e autorizações.
Agrupe as tarefas compatíveis para economizar contexto e trocas de chat.
Escolha o GPT e nível adequados ao conjunto e justifique brevemente.
Entregue aqui o prompt completo, preenchido e pronto para copiar no novo chat,
com objetivo, etapas em ordem, contexto/evidências, limites, validações,
critérios de conclusão e ponto de parada. Apenas prepare o prompt; não execute.
Inclua a regra de uma entrada minha e uma entrega final consolidada,
sem pedidos de continuação ou encerramentos intermediários por tarefa.
```

**Na fotografia atual:** usar o prefácio vigente do STATES e a preparação da seção14. A indicação antiga de reiniciar P01 a partir da0168 ficou superada pelas reconciliações até0184. Conferir somente o delta e os critérios ainda não demonstrados. Antes de entregar prompt P04→P05, comprovar o alcance da ordem aplicável e a suficiência técnica; se faltar decisão, usar o pacote já delimitado, sem entregar mais um prompt de execução que termine no mesmo gate conhecido.

## 12. Critério de conclusão e limites da revisão

**Concluir a campanha local:** P01–P08 com provas da revisão entregue e integridade preservada. **Concluir o projeto:** critérios originais dos itens abertos efetivamente atendidos, qualificação e operação reais aceitas, corte da unidade autorizado/executado e V2-040 encerrado com evidência.

Esta revisão conferiu a precedência documental e as fontes de estado; não executou os 33 passos nem demonstrou prontidão produtiva. Se um contrato/autoridade/input mudar, ajustar a dependência atingida com evidência, mantendo o restante do roteiro estável. Versões anteriores e motivos desta revisão estão preservados na rodada `target/trilha-prioridades-20260919-01/` e no checkpoint0171.

## 13. Selecionar um macrobloco por chat a partir dos dois arquivos

**Regra de entrega direta:** um macrobloco só deve ser aberto quando contiver ao
menos uma unidade com resultado verificável possível no escopo autorizado. O
executor deve concluí-la integralmente, incluindo correção e teste causal, em
vez de produzir manutenção documental ou reexecutar provas sem delta. Registros
de continuidade e caixas da etapa são atualizados como consequência do resultado.
Sem unidade elegível, responder com o bloqueio e input exatos, sem fabricar um
novo prompt intermediário.

**Fluxo adotado pelo usuário:** ele pede um prompt; o chat lê `STATES.md` e esta trilha, escolhe o próximo macrobloco e seu GPT/nível, e entrega o prompt aqui na conversa. O usuário o leva ao próximo chat. Esse executor realiza o macrobloco e atualiza os registros; quando o usuário pedir outro prompt, a seleção se repete sobre o estado atualizado. Gerar uma passagem automaticamente ao fim de cada tarefa foi uma interpretação rejeitada.

| Arquivo | Papel na escolha |
| --- | --- |
| `STATES.md` | O que foi realmente concluído, o que falta, critérios canônicos, evidências, bloqueios e autorizações registradas |
| `TRILHA_CONCLUSAO_POR_MODELO.md` | Prioridade, precedência, modelos por tipo de trabalho e regras para agrupar o próximo chat |

Os dois são a base da seleção; consultar os critérios e referências da frente para comprovar elegibilidade. AGENTS, contexto global, continuidade e evidências citadas continuam obrigatórios para a execução. Não exigir a memória do chat anterior nem reler indiscriminadamente todo o histórico.

### Contrato de uma entrada e uma saída

Reforço explícito de20/09/2026: se ainda não encontrou a solução local, ler a
documentação, testar, diagnosticar e corrigir dentro do escopo autorizado.
Não confundir falha técnica com falta de autorização nem solicitar novamente
o que já foi concedido. Executar todas as correções/verificações independentes
elegíveis antes de consolidar eventual dependência externa comprovada.

Cada prompt adotado deve permitir **uma entrada do usuário → execução autônoma do macrobloco → uma entrega final consolidada**. Não pedir “continue”, “posso seguir?” ou confirmação de rotina entre tarefas já cobertas. Não encerrar apenas com planejamento, resumo intermediário ou oferta de continuar enquanto houver trabalho elegível dentro do macrobloco. Checkpoints ficam nos arquivos, sem exigir nova interação por checkpoint.

Atualizações breves de andamento, quando necessárias, não exigem resposta do usuário e não são entregas finais. Antes de compor o macrobloco, conferir os inputs conhecidos para reduzir interrupções. Se surgir bloqueio real de informação, autorização, integridade ou limite, não ultrapassá-lo: concluir o que for independente e autorizado e consolidar na entrega final o resultado parcial, o impedimento e o input exato necessário. Uma saída única não garante conclusão diante de dependência externa ausente e não autoriza inventar dados ou permissões.

### Como dimensionar o macrobloco

1. **Começar pelo estado real:** verificar o resultado da frente atual e o primeiro P elegível. Se há entrega parcial, avaliar sua correção antes de avançar. Resultado desconhecido exige reconciliação; bloqueio precisa de input identificado, não de nova tentativa sem mudança.
2. **Agrupar por entrega coesa:** reunir etapas/fatias que compartilhem contexto, arquivos, ambiente e um resultado verificável. Dependências externas ao macrobloco devem estar satisfeitas; as internas entram em ordem e só liberam a etapa seguinte quando sua prova/aceite estiver atendido. Não juntar tarefas desconectadas apenas por usarem o mesmo GPT.
3. **Escolher um GPT/nível para o conjunto:** partir das recomendações individuais da seção9 e selecionar o menor nível suficiente para o trabalho mais exigente incluído. Uma medição Medium pode permanecer junto da correção Terra High quando isso reutiliza contexto. Separar diagnóstico/revisão Astra quando incluí-lo encareceria todo o restante; Sol continua opcional. Justificar a escolha pelo trabalho, sem prometer economia medida.
4. **Definir um tamanho executável:** incluir o máximo de trabalho coeso com entradas, implementação, validações e ponto de parada delimitados. Não há número fixo de P por chat nem garantia de duração. Dividir quando surgir mudança substancial de contexto/modelo, decisão sem evidência, aceite externo, risco/volume ou limite de campanha incompatível. Um macrobloco pode conter somente uma etapa se houver motivo concreto.
5. **Continuar dentro do escopo:** o executor avança pelas etapas internas elegíveis sem pedir “continue” entre elas, registra checkpoints após unidades coerentes e para no limite combinado. Preserva os tetos e autorizações de cada efeito; agrupar tarefas não cria campanha física nova nem renova saldo.
6. **Deixar o estado pronto para a próxima escolha:** ao encerrar, sincronizar STATES → trilha → verificações, salvar/conferir checkpoint e atualizar RETOMADA. Registrar resultado, provas e pendências; gerar outro prompt somente quando solicitado. Se tudo foi concluído com evidência, não inventar outro macrobloco.

### Agrupamentos candidatos, sujeitos ao estado de cada pedido

São exemplos de empacotamento de trabalho para um chat, não novos IDs funcionais nem seleção automática. Recalcular o alcance pelos inputs, evidências e autorizações vigentes.

| Entrega do chat | Etapas que podem caber juntas | GPT / nível | Fronteira do macrobloco |
| --- | --- | --- | --- |
| Reconciliação da campanha | P01 e suas verificações documentais | Terra / Medium | Parar com a revisão/falha delimitada; decidir depois se P02 é necessário |
| Diagnóstico delimitado | P02, somente se houver lacuna causal | Astra / Medium | Causa, correção proposta e contraprova suficientes para execução |
| Sequência, supervisão e escalas locais | P03 → P04 → P05 | Terra / High | Provas da revisão atual, dentro das reservas/limites existentes; não incluir revisão P06 |
| Revisão técnica | P06 | Astra / Medium | Achados e correções verificáveis; não declarar gate aprovado sem execução |
| Gate e pacote da campanha | P07 → P08 | Terra / High | Regressão antes do JAR; fechar M/N somente com integridade/provas válidas |
| Uma vertical até o histórico aceito | Fatias P16 → P17 → P18 de uma entidade | Terra / High | Contrato/identidade/ambiente prontos; parar em aceite/input externo ainda faltante |
| Relações e paridade core | Fatias P19 → P20 que usam as mesmas bases | Terra / High | Históricos/bases prontos e relações necessárias provadas antes da paridade |
| Uma saída analítica qualificada | Fatias P24 → P25 → P26 → P27 de um fato/saída | Terra / High | Entradas core/referências/manifesto/oráculo prontos; sem incluir outros fatos por conveniência |

Para etapas não exemplificadas, aplicar o mesmo critério. Um intervalo não autoriza cumprir etapas com dependências pendentes. Não combinar toda a campanha A–N em Astra só para evitar a troca, nem fragmentar uma implementação coesa em chats de tarefas mecânicas.

### O que entregar quando o usuário pedir o prompt

Informar brevemente **“Macrobloco: objetivo — etapas/fatias; GPT / nível; motivo do agrupamento; ponto de parada”**. Depois entregar um único bloco `text` completo, já preenchido com os dados reais. Não entregar apenas um link ou campos para o usuário completar.

O prompt deve identificar o resultado esperado, trabalho incluído/excluído, ordem interna, critérios de saída por etapa, arquivos/evidências aproveitáveis, pendências, limites e responsabilidade de atualizar os dois documentos ao final. Autorizações são referenciadas com origem, alvo e alcance, nunca inventadas. Preparar o prompt é planejamento; não executa o macrobloco.

Estrutura para o chat preencher ao gerar o prompt solicitado:

```text
Projeto/workspace: {projeto e caminho real}.
GPT/nível indicado: {modelo e esforço para o macrobloco}.
Macrobloco deste chat: {objetivo e lista ordenada de P/fatias/IDs V2}.
Resultado final: {entrega verificável e ponto de parada}.
Fora deste macrobloco: {etapas/efeitos excluídos e motivo da fronteira}.

Regra de interação: uma entrada minha e uma entrega final consolidada.
Execute autonomamente o macrobloco, sem pedir continue ou confirmação de
rotina e sem encerrar em plano/resultado intermediário se ainda houver
trabalho elegível no escopo. Atualizações breves não exigem minha resposta.
Se surgir bloqueio real, preserve os limites, conclua o trabalho independente
autorizado e informe na entrega final a causa e o input necessário.

Leia STATES.md e TRILHA_CONCLUSAO_POR_MODELO.md atualizados.
Cumpra AGENTS.md, ../CONTEXTO_GLOBAL.md, docs/continuidade/RETOMADA.md
e docs/runbooks/continuidade-agentes.md; confira as referências da frente.
Estado de partida: {o que está comprovado/parcial/bloqueado, revisão do
worktree, checkpoint e evidências locais; HEAD sozinho pode não conter tudo}.
Dependências externas ao macrobloco: {provas de atendimento e inputs pendentes}.
Arquivos/runbooks para atuar: {paths e finalidade}.
Evidência reaproveitável: {testes/recibos, revisão/camada e limitações}.
Falhas ou efeitos desconhecidos: {o que conferir antes de repetir, ou ausência
comprovada; processos/ledger pertinentes sem segredo nem dado de negócio}.
Autorização e limites: {origem/data, alvo, operações, vigência, tetos e saldo
quando aplicáveis; explicitar alcance ausente sem autorizar por inferência}.

Execute o macrobloco nesta ordem: {etapas incluídas e critério que libera
cada etapa interna, comandos/validações pertinentes e evidência de saída}.
Não peça continuação entre etapas internas já cobertas; pare se uma
pré-condição falhar, um limite for atingido ou o escopo exigir decisão externa.
Não salte uma dependência, não amplie o macrobloco nem use subagentes.
Preserve alterações preexistentes e provas históricas; não repita trabalho
comprovado sem mudança que justifique revalidar. Ausência de artefato deve
ser registrada, nunca compensada com prova inventada.

Ao encerrar, registre o resultado real, testes e pendências no STATES,
sincronize a trilha/verificações, salve e confira checkpoint e atualize RETOMADA.
Apresente o que foi concluído e o que restou, sem fechar aceite por planejamento.
Deixe os dois arquivos prontos para selecionar o próximo macrobloco.
Gere o próximo prompt somente quando eu solicitar.
```

Os campos entre chaves são preenchidos pelo gerador com fatos ou lacunas explícitas. O novo chat precisa ter acesso ao workspace e aos artefatos citados; o prompt não contém segredos/payloads nem substitui evidência ausente.

Esta revisão corrige o fluxo de uso, preservando prioridades, dependências, modelos individuais e aceites de P01–P33. Registro: checkpoint0173 e `target/trilha-macroblocos-chat-20260919-01/`. A regra automática da revisão3.1/checkpoint0172 fica histórica.

A revisão3.3 explicita uma entrada e uma entrega final por macrobloco, sem mudar seu escopo ou os aceites. Registro: checkpoint0174 e `target/trilha-entrada-saida-20260919-01/`.

## 14. Terreno preparado para os próximos macroblocos

O [guia integral](docs/runbooks/preparacao-integral-trilha.md) é o ponto único de
preparação: origem dos limites, conjunto P04→P05 delimitado, proposta finita
somente se necessária, critérios/testes reutilizáveis, coleta G01–G08/FEED e
agrupamentos posteriores. O [mapa](docs/catalogos/preparacao-trilha/plano.json)
mantém33 etapas com dependências condicionais por fatia e todos os48 IDs abertos.
Não é autorização nem substitui as tabelas/aceites desta trilha e do STATES.

Antes de gerar o próximo prompt, executar a verificação local:

```powershell
pwsh -NoProfile -File scripts/validation/Test-TrilhaPreparation.ps1
```

Um PASS comprova coerência/cobertura documental, **não prontidão física**.
Conferir evidência e autoridade da fatia antes de propor sua execução. Não inventar
expiração ou saldo global; também não interpretar ausência como autorização
ilimitada. Não pedir novamente o que já estiver comprovado e vigente. Bloqueio
conhecido sem mudança deve levar ao pacote de entrada preciso ou a trabalho
independente autorizado, não a outro preflight dispendioso e idêntico.

Preparação não recebe checkbox funcional. A construção segue39/45 (86,7%) e os
aceites67/115 (58,3%); o delta deste trabalho é0 ponto percentual nesses contadores.
Cada percentual futuro deve identificar quais critérios serão realmente fechados,
sem dupla contagem de pais/fatias nem reclassificação silenciosa de V2-017.
# Qualificação técnica de sombra — correção de validador, gate JaCoCo aberto

Em 22/09/2026, por ordem do usuário, a revisão corrente foi verificada sem
fonte externa nem banco produtivo. A regra `PREPARE` do validador read-only de
Manifestos foi corrigida para reconhecer a procedure V022 instalada; o
validador passou após a correção. Cinco provas estáticas de verticais, nove
validadores SQL read-only, gates de schema/dados, scanner e a IT JDBC sintética
rollback-only passaram. A suíte unitária teve 2.263 testes sem falha/erro e
cinco ignorados; `mvn verify` terminou **vermelho no JaCoCo por cobertura por
pacote**, sem redução de limiar. Contagens de auditoria local ficaram 0/0.
O resultado e os limites estão no cabeçalho de `STATES.md`; logs locais em
`target/shadow-technical-delivery-20260922-01/`. B17–B29, fonte, negócio e
produção seguem sem aceite; não houve nova sonda 4924.
