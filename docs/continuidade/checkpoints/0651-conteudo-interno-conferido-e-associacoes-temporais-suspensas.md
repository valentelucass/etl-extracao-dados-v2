# 0651 — Conteúdo interno conferido; duas associações temporais suspensas

27/10/2026. O usuário reforçou que a conferência deve confrontar os valores
internos de cada registro/campo, e não apenas quantidades ou nomes de colunas.
A equipe0649 permanece: supervisor coordena/revisa/documenta/Git, Execução é
o único executor pesado, Campos revisa arquivos e ESL controla o navegador.
Nenhuma nova chamada Source, SQL ou DDL foi realizada nesta etapa.

B = C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01.
T = B/supervisor-team0649-01. Valores de negócio e comparadores privados ficam
fora do Git. A publicação anterior0650 foi5c2be366a62691e116a57c75611cd49ea49f7761,
confirmada em origin/main; recibo B/RootGitCommitPush0650.json,
SHA2562140E57FA6E91081D3E017C9E688B49C9E5EE7516DBAA45C8F740BF039D8AAFF.

## Cotações: confronto real por registro e campo

O sucessorv4 compara fonte, extração retida, readback SQL real e Excel ESL do
Data Export6906/22out. Corrigiu o eixo numérico do comparador: representação
textual de decimal versus célula numérica não significa valor matemático
diferente. Vinte casos causais passaram, preservando precisão exata,
identificadores com zeros à esquerda e distinção null/vazio/zero; sem tolerância
arbitrária. O diagnóstico antigo por multiconjuntos não era pareamento de células:
seus3.565 itens sem correspondente não são3.565 defeitos de conteúdo.

Foram pareados284 registros sem ambiguidade e confrontadas10.224 células em36
campos. Há6.697 iguais em tipo/valor,2.559 de valor numérico exatamente igual com
representação diferente,352 de mesmo horário civil sem fuso comprovado,317 de
texto vazio versus branco e284 de código versus rótulo sem tradução aprovada.

Permanecem15 diferenças de conteúdo em8 registros:12 numéricas e3 entre
null/ausência e valor preenchido. Os campos incluem volumes, pesos, valor da NF,
valor do frete, emissão do CT-e e descontos/subtotal. A revisão individual
classificou5 casos como compatíveis com diferença de precisão,7 como diferenças
numéricas observáveis e3 como null/preenchido. Nos5 primeiros, a distância é
exatamente uma ULP de binary64; isso não comprova a causa nem transforma os
casos em igualdade. Dos3 casos null,1 tem fonte/SQL preenchidos e Excel branco;
2 têm fonte/SQL null e Excel preenchido. Nenhuma diferença foi apagada.

Fonte e payload original do readback coincidiram em10.619 verificações. Oito
projeções tipadas selecionadas coincidiram em2.296 verificações. Os15 casos
mantêm fonte/SQL scalar exatos;14 mantêm também scalar exato na extração retida.
A evidência de SQL é histórica, real e obtida com rollback; não comprova carga
permanente, estado atual de todas as tabelas ou parâmetros JDBC enviados.
Momentos distintos das observações podem afetar a comparação; causa de erro
no ETL, atualização ou precisão não foi concluída.

Continuam395 células sem comparação segura:287 de Observações, cuja associação
técnica ainda não está comprovada, e108 de3 ocorrências repetidas sem identidade
inequívoca. Não declarar paridade integral.

Provas e revisões:

- B/cot-content0651-02-root-control/qualification.json,
  SHA2560E7423E1AE72451C0F2DD3983ADCCA4F1F112E83746FE9F7AF9B311C6256D424;
  result84042DB290306AB51E2DCDB84CE5D44D49AC4FB6F346C2FC7C062BB3EB88D46D;
  summaryAFCB8691A6E8A45A20DCD43A2903453B63B2E22946E6376BED4B188C1FBB48A8.
  Original0/4.133ms/624.619.520bytes, fechamento e história preservados.
- T/response-cot-content0651-v4.json,
  SHA256650ECBAFEC78E82C8533C30CE1395570FE4D0C4FA6AF66D5F510B0C184463F9A.
- T/campos-regras/review-cotacoes-15-casos0651-02/review.private.json,
  SHA256543EE532906C7D8FADA6AED78D8CB90274B20003AAA0EF06C4EB57827B944705.
  A revisão v2 corrige apenas a descrição das duas direções null/preenchido;
  o original v1 foi preservado.
- T/review-supervisor-cot-content0651-v2.json registra entrega dos valores
  exatos em JSONL e HTML local. O renderer usa DateKind String para preservar
  literalmente precisão/fuso das datas. Os arquivos anteriores ficam preservados.

## Manifestos: conteúdo preservado; mapa externo sob revisão

O confronto do Data Export6399/17out verificou28.665 campos da fonte contra
extração retida e payload SQL, sem diferenças, e6.300 verificações de20 projeções
selecionadas. Não generalizar para todas as projeções, binds ou carga permanente.

A execuçãov4 usou76 associações então aceitas:456 células de6 registros de
ocorrência única e23.100 campos constantes entre candidatos. Os309 registros
com repetições não foram arbitrariamente pareados. Ficaram384 células sem
comparação por candidatos com valores distintos, mais4.725 de15 associações
não aprovadas. Os totais históricos10.402 iguais estritos/7.283 iguais numéricos
permanecem no resultado imutável; não são paridade externa integral.

Nos315 registros, closed_at vinculado a Fechamento e finished_at a Chegada
divergiam dos valores civis das colunas vinculadas e coincidiam com as colunas
opostas,630 casos. A revisão independente suspendeu exatamente esses dois
vínculos anteriores. Os outros74 não foram reabertos por essa decisão; não houve
nova execução do comparador nem nova associação aprovada.

A interface ESL comprova o filtro Manifesto/Fechamento ligado a
manifests[finished_at], com label/input correspondentes. É evidência de FILTRO;
não comprova o alias técnico das colunas exportadas item_113641/item_113640 e
não autoriza Chegada→closed_at por exclusão ou coincidência dos valores.
Parte da prova é transcrição de leitura DOM bem-sucedida após leituras
subsequentes travarem; esse limite está explícito no pacote. Não aplicar swap,
alterar payload, política de freshness ou atribuir corrupção ao ETL.

Provas e revisões:

- B/mft-content0651-02-root-control/qualification.json,
  SHA2560B11622807B36557121F9764A157DE890D70454CA2AEBA75FC55B0C12686ED90;
  result96638ADF1D2FB36E762B0E12A1A8AD83FA782E986EF9DFAA85A7AD768CFA3D9A;
  summary1BB47B3624BCCD6178F04F4327DC3ABC62AB2DD6B3CAC55847B752BA4EBAA433.
  Original0/10.293ms/638.529.536bytes,35 casos causais, fechamento/história preservados.
- B/mft-diagnostic0651-01-root-control/presentation-diagnostic.json,
  SHA2564C8D204282AFAC6E85275B815C5BAC4E4612C857E4C1589105A92447D5CA7E47.
- T/review-mft-temporal-bindings0651.json,
  SHA256A673446753A8DF152D76D52A293F9A9BC110896ECD92B3DDBFDBD7A512F20931.
- T/conferencia-esl/mft-temporal-ui-binding-recheck0651-v1.json,
  SHA256C381EFB53704FD832696EF9F77E40B4D5B9FB24016D46F85B60A8D554EADE4D2.
- T/review-supervisor-mft-content0651.json registra entrega local do conteúdo
  completo e12 casos de datas com fonte/extração/SQL/Excel lado a lado. O relatório
  distingue explicitamente o resultado histórico e os dois vínculos suspensos.

## Seis entidades precisam de valores completos; Fretes primeiro

A revisão das linhagens selecionadas não recuperou payloads completos retidos
de Fretes6389, Localizador8656, Contas a Pagar8636, Faturamento4924,
Inventário10633 e Sinistros6392. Isso não é afirmação de ausência global.
Contagens, tipos e diagnósticos históricos não substituem os valores.

T/request-retained-values-six-entities0651-v2.json,
SHA256B097939C9F84F6B1365213EABE31EC6B21E1A5F7D0B60DC0EC56440FCA34ED66,
preserva a v1 e prepara envelopes distintos,23HTTP condicionais no agregado,
sem autorização de lote. Fretes é a única próxima entidade. Manter filtros
exatos de cada pacote, inclusive issue_date E created_at em Contas a Pagar e
opening_at_date01–23out em Sinistros. As diferenças históricas de quantidades
de CAP/Sinistros não têm causa comprovada.

Fretes passou30 casos locais pela entrada Main candidata e captura completa
de INFO/DATA, bytes/raw records, decimal lexical, Unicode, presença, arrays e
ocorrências. Per50 não foi tratado como limite de linhas físicas: fixture61
linhas passou, limites explícitos1000/page,2000total,8MiB/body,64MiBretidos;
2DATA/1INFO, sem extensão se não houver término. JSON/UTF8 inválidos, excesso,
INFO incompatível,429/non2xx e ausência de terminal recusam/fecham parcial.

A baseline Main atual não possui a rota Fretes. A candidata privada não foi
integrada ao produto canônico e os30 casos não qualificam transporte real,
RootEntry real, paginação do fornecedor ou admissão. Fixture excluída do JAR.

- T/response-fretes-local-value-main0651-v3.json,
  SHA256723835C0828BDFEBD33FD6BF54316706F617019D2522E7BAD485D422DFA6C3D6.
- B/fretes-value-main0651-03-root-control/qualification.json,
  SHA256ED117C901F44B7921BF30CC24E82CA1C4B6E9B262EDC27207DAE71B4C48A28B8;
  JAR privado5FF215DF338F8B902A90DDBC01A561ADC192896F88D2AA5EBDB55252D5DAF673.
  Original0/8.224ms/331.137.024bytes,30casos,3compilações cumulativas incluindo
  a falha de preparo preservada; sem Maven/formatter completo.
- T/request-review-fretes-main-value-sink0651.json,
  SHA2563C1F46CB1ED1F98E05D38DC02D17B2885DA54A4FA4357FE1CC15B797A78BBEC0.
  Revisão independente por Campos em andamento.
- T/prepared-fretes-protected-source-request0651.json,
  SHA2568AD4275CA6784FD6BFD039DE9A09B1AC6DA17864563FA9C7BA10E45A67757035,
  explicitamente não emitível: entry/rawport/LatestMainTyped/CODE/unidade/janela/
  nativeTLS ainda não vinculados. Não usar MFT0645 como qualificação de Fretes.

Execução prepara a ligação concreta ao transporte DataExport existente e a
RootEntry source-only, sem efeitos na fonte. Uma qualificação local nova foi
delimitada a1 compilação/suite focada sob os controles existentes; se falhar,
preservar e relatar, sem retry automático. O pacote completo atual deve ser
revisado antes de qualquer chamada Source. Nenhuma unidade/CODE/janela emitida.

## Pendências e limites mantidos

Coletas: resultado e candidata de status de0650 continuam válidos no escopo
offline, sem ativação. A proposta SQL mínima é texto, sem DDL autorizado.
Users/P3 está pausado por prioridade de conteúdo: revisão encontrou dois P2,
surrogate UTF16 dividido entre chunks e erro de cleanup mascarando erro primário;
nenhuma correção foi promovida ou nova consulta GraphQL executada.

Continuam nove entidades DataExport; somente Users usa GraphQL. Login Chrome
foi restabelecido. Se uma aba travar, abrir outra e fechar a aba com erro, conforme
o usuário; não salvar templates nem encerrar sessão. A inspeção focada de ativos
estáticos já observados terminou sem prova nova: pageAssets lista scripts, mas
não documenta leitura/exportação de seu conteúdo. Sem estado oculto, fetch ou
rotas inventadas; não repetir os31 painéis já lidos. Aguardar INFO completo.

Última Source qualificada permanece MainManifestos0645. Futuros efeitos exigem
LatestMainTyped e produto/perfil/caller/sink/nativeTLS atuais, nova unidade/CODE/
janela; serialização de2s após resposta completa, STOP global no primeiro
non2xx/429/incerteza, sem retry/redirect/fallback ou avanço automático de entidade.
Original1GiB/4096MiB livres/commit85%, único slot CAS e fechamento/história ficam
preservados. SQL16.0.1000.6, permissões, serviços, TLS global, PM2, V1 e produção
não foram alterados. DDL18SQL e Raster7 separados, carga permanente, UsersEOF,
retenção e paridade integral das nove continuam abertos. Este checkpoint registra
evidência e pendências; não amplia autorização operacional.
