# Checkpoint0654 — Coletas: captura local e leitor de conteúdo validados

## Objetivo e resultado

Data: 28/10/2026. Continuação do checkpoint0653. O usuário exige a semana completa de Coletas/Data Export6908, 19–25/10/2026, por Data da solicitação, comparando cada linha, coluna, valor e total, inclusive registros, repetições e campos a mais ou a menos. Nove entidades usam Data Export; somente Usuários usa GraphQL.

A semana ESL congelada continua com1526linhas/31colunas/1155representações de ID/371ocorrências adicionais. Existem46ocorrências adicionais de linhas inteiras idênticas; não deduplicar. XLSX SHA256 `31BC21FE4A7131063243375CBA142AE45BD8F1032A3730E607AFF179319AF77A`. Esse inventário é externo, sem confronto semanal executado. Source semanal0, SQL semanal0, nenhuma entidade integralmente aprovada.

## Resultados efetivamente observados

| Unidade | Resultado | Alcance |
| --- | --- | --- |
| Combined07-v2 |1Original/1javac/1JVM;4casos passaram,0falhas,0skipped;157943ms,pico340340736bytes | Duas fontes de qualificação recompiladas,2famílias/8classes, produto e issuer preservados.108inputs e novoJAR protegidos e conferidos; fechamento/história/ACL/slot restaurados. Sem Source/SQL/NativeCIM. |
| Synthetic19 |1Original/1Python isolado;19casos e451checks passaram;1972ms,pico76439552bytes | Reader/bridge em memória: repetição, extras, lexemas, tipos, ausência/null/vazio, UTF8, byte offsets, ambiguidade, publicação/EOF e limites.35leases conferidas, fechamento/história/ACL/slot restaurados. Sem Source/SQL/XLSX. |
| Preclaim04 |13artefatos e5parses estáticos conferidos | Somente revisão de código e contrato;20casos puros autorados,0executados. Nenhuma posse Native, role ou admissão real comprovada. |

Combined07 corrigiu somente fixtures: o acumulado de10000ocorrências agora percorre duas páginas válidas e uma página vazia real; a página única com70001nós continua recusada pelo limite50000. O timeout usa o governor real e confirmou cancelamento do subscriber e do future, retenção do prefixo e impossibilidade de callbacks tardios alterarem os arquivos fechados. A regressão de expiração/interrupção do helper compartilhado também passou. Não houve mudança no Executor do produto: a revisão independente demonstrou que o Main real já possui deadline monotônico. Os44casos aprovados anteriormente não foram repetidos; não declarar uma suite completa de47no novoJAR.

Produto continua `548102BBEAB5FF6CD088A97D93C59CF21FE692B609744E4DC322780BB32F0A52`; issuer `D3F93D70BA3C4F9E161EB114C8B520A752E3C0603272FEE96565172ED303ABDA`. Qualificação focada07 `F38FCE90DA0B1E7E89FA34D150B5FD6CF3CEFA50CAEC073F9774F6A18F402F0A`; seal `A57FBA36E312FF447CD38DBEEDBD05B94C71F989C27F212C89D3ED5B1B800B6D`; relatório4 `17B1A81CE47C1A969D189DB6D22731D874F7825C60230C97A98CC408267C908A`.

Synthetic19 preservou o readerF8BE e bridgev3 `6B38F75A72F63E1B18802EFA4E00BE699CAD24C8F0B7865E8EAA7B716F5847EC`. Relatório451 `2C4CB15D6D6168BE8B7EBE0C568A37CDEE1E8A558FFC2B6923680F4228DF9560`; Original `4EB0F9BB27E58F203CC0025CD7967130E68090CBC78768A66FA354831D5C70EE`. Os19casos são sintéticos/em memória; o carregador físico de corpus real, as31associações externas e o confronto ESL/SQL permanecem sem qualificação. O ramo de retenção de primeiro diagnóstico não foi exercitado, pois não ocorreu falha.

## Ligação com a consulta real

Arquitetura01 escolhida para integração privada: um Native owner na mesma thread adquire e mantém o SourceMutex continuamente durante o Original assíncrono, o encerramento físico e a reconciliação. A adoção transforma uma única preclaim exata em claim observado, sem segundo slot ou reacquisição. READY significa o CLAIM_READY_REQUEST real antes do ACK/RESERVED_READY; a ordem não depende de entrada humana.

Runtime deve entregar o wrapper/relay, a observação Native atual e qualificada, joins de processos/nascimento, papéis/producer/provider, limite fim a fim3s, fechamento físico e writer durável. Hashes ou flags em modelos não substituem essas provas. Campos fornece helpers de adoção e modelos, ainda sem execução real.

Há uma lacuna concreta no orçamento G2: os máximos normais existentes5914624bytes mais doisCAS de1MiB resultariam8011776bytes,147456acima do teto normal7864320. A reserva cleanup512KiB não pode ser emprestada. A soma total8273920abaixo de8MiB, sozinha, não qualifica isso.56arquivoscontroller+73capture já esgotam129; não foram aprovados dois novos nomes, uma tabela58/73 ou exceção por colocar arquivos em Control externo. A proposta concreta deve reservar nomes/bytes/contagem entre partições dentro dos mesmos8MiB+56MiB/64MiB e129arquivos.

As31associações externas continuam pendentes. Na inspeção posterior, Chrome estava acessível, mas nova aba ESL redirecionou para login; a original foi preservada e somente a nova foi fechada, sem consulta/download/corpus. A pergunta de autenticação já pendente não foi repetida. INFO completo fresco poderá fornecer evidência independente; não inferir vínculos apenas porque valores coincidem. Status done=Finalizada/finished=Coletada no filtro não comprova a renderização da coluna; ação finish e conversões temporais continuam sem regra aprovada.

## Limites e continuidade

Execução é o único executor pesado delegado. Root supervisiona/revisa/documenta/Git; ESL controla navegador. Nenhuma qualificação local ou este checkpoint emite CODE, janela, claim real, Source ou SQL. Release0 preservada. Não repetir unidades para recuperar contexto. Nenhum retry ou próximo lote automático autorizado.

Futuros limites semanais continuam condicionais: Original300s/1GiB, Source210s, request30s, INFO1+DATA<=16/HTTP<=17/per100,<=10000ocorrências, resposta2MiB,64MiB privados; cada envio exige pending=buffered=inFlight=0 e raw=mapped. Necessários EOF real, todos os extras e duplicatas, conteúdo completo de31colunas e totais. Sem pareamento por ordem/semelhança, tolerância numérica, deduplicação ou cap ampliado.

Última Source real permanece MainManifestos0645. O estado compartilhado voltou ao SHA `A39F653656817F16B23116E5C04965B7D0746398F0A7DFBC5E6240C84A2216FF`, antes de eventual reconciliação documental deste checkpoint. História, posse delegada e ACL preservadas. SQL16.0.1000.6/contas/certificados/TLS global/PM2/V1/WMS/produção intactos. DDL18SQL+Raster7, dois objetos físicos Coletas, carga permanente, UsersEOF e demais pendências anteriores continuam sem aprovação/fechamento.

Referências privadas, fora do Git, na base supervisor-team0649-01:

- `root-approval-weekly07-combined-v2-0653-01.json`: `E2C1359835C3D67C2BFC3869CB33B3CFB1099502B45673BF78E02BE625F24F9B`.
- `response-coletas-synthetic19-run01.json`: `06D7D15B22AC670E866BBA577A76FD948CA49980A3F954918E272EA81EF05944`.
- `root-acceptance-synthetic19-0653-01.json`: `97CEC94326CCB2FD3CA56A9B1159D24E8521E240B6C0AF6253A544F491810528`.
- `root-review-preclaim04-static-0653-01.json`: `01C1867A6F25F61286085C53813D9BA856DCDEEECB427C1268B88015F04AAE2B`.

## Próximas três ações

1. Runtime concluir e congelar código/wrapper/contabilidade S2; Root/Campos revisar a implementação concreta, reutilizando provas existentes e qualificando apenas os novos caminhos necessários.
2. Qualificar observação atual/roles/birth/provider/relay/closure e obter definições independentes das31colunas; congelar uma execução semanal real finita, sem Source antecipada ou CODE/janela herdados.
3. Capturar a semana até EOF e comparar todas as ocorrências e células entre ESL, Source, extraído e readback SQL; reportar diferenças, faltas, extras e ambiguidades. A entidade só poderá ser aprovada após esse confronto integral.
