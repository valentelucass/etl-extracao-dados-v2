# 0637 — Previsões das paradas conferidas com o Excel; integridade dos contratos

Em 27/10/2026, uma nova consulta real trouxe 108 viagens e 355 paradas da Raster,
para 22/10/2026. Desta vez foram preservados os 14 campos de cada viagem e cinco
campos de cada parada: Ordem, Tipo, CNPJCliente, DataHoraPrevChegada e
DataHoraPrevSaida. O produto continua sendo o JAR153 já qualificado; não houve
recompilação do produto, alteração de SQL, PM2, V1 ou produção.

## O que o confronto demonstrou

Os dois Excel originais continuam íntegros: 290 + 346 = 636 linhas. A comparação
reconstituiu os mesmos 108 pares por código literal, sem ambiguidade entre viagens,
e confirmou novamente as 108 placas. Os 14 campos das viagens ficaram exatamente
iguais entre as duas consultas: 1.512 comparações de presença, tipo, raw, typed e
issue, sem diferenças. Os parâmetros não secretos das consultas também são iguais.
Isso permite interpretar o resultado junto das 57 diferenças registradas no 0636.

Foram comparadas todas as 355 paradas, sem selecionar a primeira, a última ou uma
parada pelo campo Ordem, Tipo ou CNPJ. São quatro combinações de campos, totalizando
1.420 comparações de componentes civis até o minuto:

| Campo do Excel | Campo da parada | Viagens com uma coincidência | Viagens com mais de uma | Viagens sem coincidência |
|---|---|---:|---:|---:|
| Previsão de saída da origem (I) | DataHoraPrevSaida | 107 | 1 | 0 |
| Previsão de saída da origem (I) | DataHoraPrevChegada | 50 | 1 | 57 |
| Previsão de chegada ao destino (J) | DataHoraPrevChegada | 104 | 4 | 0 |
| Previsão de chegada ao destino (J) | DataHoraPrevSaida | 13 | 0 | 95 |

Portanto, nas 108 viagens existe pelo menos uma parada cuja previsão de saída
coincide com a coluna I do Excel, e pelo menos uma cuja previsão de chegada
coincide com a coluna J. As contagens de ocorrências são 109 e 112, respectivamente;
não representam viagens adicionais. Nenhuma dessas 1.420 comparações ficou sem
uma representação civil suportada.

A evidência favorece a hipótese de que a coluna I corresponde à saída prevista
de uma parada, enquanto a comparação anterior usava DataHoraPrevIni da viagem.
Como todas as 108 viagens possuem a coincidência de saída, as 57 diferenças
anteriores também estão nesse conjunto. Não se observou uma alteração dos dados
das viagens entre as consultas, nem um deslocamento fixo de fuso. Nenhum campo do
ETL foi trocado ou preenchido para forçar concordância com a planilha.

Ainda não se deve declarar qual parada é a origem/destino canônico apenas por uma
coincidência temporal. Uma viagem tem múltiplas saídas coincidentes e quatro têm
múltiplas chegadas coincidentes. Os campos Ordem e Tipo não receberam significado
inventado. Relações literais entre CNPJCliente da parada e CNPJClienteOrig/Dest da
viagem foram contadas separadamente, excluindo valores vazios de MATCH; CNPJ não
foi usado para selecionar ou descartar candidatos.

O Excel não declara fuso ou segundos. As provas são de igualdade até o minuto dos
componentes civis apresentados, sem conversão de fuso ou igualdade de instante.
Nomes de clientes, cidades, proprietário e status continuam sem correspondência
externa demonstrada. O filtro semanal do Excel também não foi demonstrado
equivalente ao filtro da API por evento de fim em 22/10. Não classificar as demais
linhas do Excel como faltantes do ETL por isso. Não há prova de paridade integral
de todas as entidades, de todos os 49 atributos Raster ou de HTTP bruto retido.

## Execuções e rastreabilidade

O componente privado é o manifesto 714B6E94, Entry B9F0 preservado, projeção F13B e
cinco classes auxiliares. Dois grupos causais reais passaram: parser do JAR153 até
retenção física de duplicatas/presenças, e writer/reader recusando contagens,
ordinais ou campos adulterados, mesmo com hash refeito. Original0, 11.165 ms,
370.323.456 bytes. Qualificação F0F374B07268CF3C93E90BB50E226C2F1644D7CEDC4EA28CF0AC3FD35272B734.

O controlador 1CB048B9 reutiliza os gates de caminho byte a byte, sem retag de Own6
ou Path3/Counter1 históricos. Um novo grupo PS passou no leitor real e no vínculo
dos contadores Raster 1/1, recusando globalFinal=true, contagem divergente e
referência ausente. Original0, 5.313 ms, 94.097.408 bytes; qualificação
21988873FD14F8E93670459B226937A8CEF2F1907648CEC9FF417C0A5564E18F.
Join3 real DC4B4BC437B47F96946F30A112B1B6B85A76310803F33C89AF009EC326056E43.
Um WIP anterior de Common gerado com substituição de texto expandiu uma cauda
indevidamente; foi preservado e corrigido por edição literal antes de parser,
Original ou Source. Não foi falha do produto nem consumo de chamada.

Root revisou os vínculos reais, passou pelo preflight somente de leitura e emitiu
uma nova unidade a0371001: uma POST, 45 s de unidade, 30 s por requisição, intervalo
de 3 s, limites de 499 viagens/1.000 paradas/500 por viagem/10 MiB e Original de
1 GiB/1 minuto. A unidade terminou Original0/Java0, 30.090 ms, 347.885.568 bytes,
108 viagens/355 paradas retidas, zero SQL. Prova da captura
C313ECC49020AC56F381D2F555F402E0C053CD475F1E1C45CED326A36F388447;
Original ECE98DD93EEFAEC43F6FF079EF0F7B61A7FDD385837FDF05A38C149BE48C06B8.
Dados selecionados e COMPLETE ficam privados, fora do Git.

Root acrescentou apenas source0637AdditionalConfirmedAdapterReservations=1 e
lastActualSourcePair0637 ao ledger. Before, CAS, backup concreto, readback e
DeepEquals do histórico foram conferidos; contadores globais continuam não finais.
Qualificação AC978BE7F1D29195FB72C200FA71194009CE746CFC903D813752EC9FC67DA704.

O comparador V1 ficou preservado com a lacuna de teste de multiplicidade. V2/78DF
extraiu a mesma decisão para uma função compartilhada pelo fluxo real e pelo único
grupo causal novo. Este cobre zero paradas, representação não suportada, nenhuma,
uma ou duas previsões coincidentes usando o leitor civil real. Não repetiu Own3,
Civil2, Delta2, Java2 ou as suítes anteriores. Comparação real + esse grupo passaram
no mesmo Original0: 949 ms, 28.758.016 bytes. Qualificação
4B969D2EBAEB5F0F8F996FAFBC79494233A8045413B7C92F363CE038D78FB8AF.
Somente contagens e estados técnicos são públicos; detalhes por ocorrência estão
no relatório privado rxe-stop-real0637-v2.

## Correção da preservação dos contratos no Git

A publicação anterior 6912fcb integrou os 68 arquivos e passou a varredura integral
real: 4.755 candidatos, zero apontamentos. Root identificou depois uma diferença
entre os bytes qualificados dos dois contratos MFT/COT e os blobs armazenados:
o padrão eol=lf convertia CRLF, mas os pins do produto verificam SHA dos bytes.

| Contrato | Bytes qualificados, preservados no JAR153 e no trabalho | Blob anterior normalizado |
|---|---|---|
| Manifestos 6399 P2 | FBB1D4B349B02DBF9450CB2EC113EACF55643A219E837BF4CE624D7B7B43CCCC | 59B7FFD01FD250F8BD3777AB6CBE70002986071885F525DA203FCC5F050971CA |
| Cotações 6906 P2 | 8E6FC68811EF08E707DFBEDD21818EFA9BB8C2943D9D253183640CB5AE17799C | 4E51D0449654FCE7E08966EB80880D49DCC974AA30D173F1188961620E427263 |

A menor correção acrescenta apenas dois caminhos exatos com -text -eol em
.gitattributes. Não altera os pins, JSON semântico, JAR, parser ou regra global.
Três causais reais de Git reproduziram os dois hashes incorretos com o padrão
antigo, preservaram os bytes no índice e no checkout com as duas exceções e
confirmaram que outro JSON continua em LF e o recurso P3 permanece íntegro.
Original0, 3.805 ms, 90.341.376 bytes; resultado
C72D64C5BB7EBBC7B93584919ED7AA763E219C91060EA9BEAA86E02E6A7AD2B3.
Adoção atômica de .gitattributes/backup antes exato:
579AE0E66CDF28AE4EAFF4A13BD3F6D2D5D9324377EEE6BC1CCCE8C36C79133A.

Uma checagem adicional no índice da fixture mostrou que preservar CRLF também
exige declarar CR como parte do terminador para o verificador de whitespace:
diff --cached --check retornou 2 nas duas versões raw, embora seus hashes fossem
corretos. Um grupo causal incremental confirmou a solução nos mesmos dois caminhos:
whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol. Os três controles
normais permanecem explícitos. Os contratos válidos passaram no diff-check e
mantiveram os pins no índice/checkout; um espaço indevido antes de CRLF continuou
sendo recusado. Não foi usado -diff ou relaxamento global. Original0; adoção final
C19AF0E44F44FA601701B41585F7B0086BC7391DAB271B9203D852908F67D6A1.

A publicação desta correção deve adicionar os dois recursos com --renormalize
restrito aos seus caminhos e conferir os blobs do índice e do HEAD contra os pins
FBB1/8E6. Antes do stage, o cache de stat pode mostrar somente .gitattributes e os
quatro documentos como alterados; o escopo final esperado é de sete arquivos.
Baseline integral 0636 + scan do delta exato, sem reexecutar suítes do produto
inalterado ou requalificar o AST anterior apenas por metadados de Git.

## O que ainda impede a conclusão global

Manifestos 315, Cotações 287 e Coletas 1 mantêm suas provas reais de gravação,
readback e comparação privada sem diferenças, com rollback. Isso não é carga
permanente. Fretes Data Export 6389 continua separado de Fretes GraphQL;
Localização 8656 continua separada de Raster. Os nove Data Export têm provas
terminais nos recortes documentados; não equivalem a histórico completo.

O fechamento estrutural revisado tem 18 SQL privados íntegros e quatro grupos:
ajustar rotina/CHECK de Fretes; ajustar rotina de Localização; criar três objetos
de campos originais de Usuários; criar cinco objetos compartilhados dos quatro
relatórios P3. V106/V107 não foram instaladas e guard_pins_ready continua zero.
Reconciliação estática: inventário 2D0FA9A19B2E04125F1AFACECE688FCB0AE369131332003248AD8E0EC479DFBB.
A proibição permanente de DDL/grants/Flyway está preservada. Foi solicitada ao
usuário uma exceção explícita somente para esses ajustes no banco de testes.
Ela ainda não é uma aprovação. Mesmo após eventual aprovação, continuam
obrigatórios preflight protegido atual, caller/owners, before/after, transação e
recuperação reais; nenhum JSON, flag ou aprovação substitui essas provas.

Main7 chegou ao workflow P3 genuíno em modo fechado/offline, com zero HTTP/SQL.
Não é uma carga física via Main; essa porta atual não aceita Coletas/MFT/COT.
Não repetir Main7 ou Source dessas entidades para fingir fechamento dessa lacuna.

Usuários permanece sem EOF e sem cursor recuperável do V3 interrompido. first=100
não é uma simples configuração: produto/contratos atuais limitam Usuários a 20,
e esse máximo entra no fingerprint. Um cursor isolado também não recompõe o
estado do leitor ou prova continuidade entre execuções. Análise somente de
arquivos 822963FE7D2C0A2EEDE49A06243C6FC035DBD1BFAFECB2270E56CF160D0930A9.

A sessão de consulta ESL expirou; foi solicitada nova entrada no Chrome. A aba
original foi preservada, sem logout, recarga ou fechamento por Root nesta etapa.
Não houve confronto novo com o site. Autorização estrutural e acesso ao site
continuam pendentes de resposta, enquanto todas as provas acima já foram feitas.

Etapas 3/4, carga permanente e fidelidade externa integral continuam abertas.
SQL16.0.1000.6, contas, credenciais, permissões, PM2, V1 e produção preservados.
