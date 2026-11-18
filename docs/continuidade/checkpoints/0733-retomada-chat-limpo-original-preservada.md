# Retomada em chat limpo — Coletas e Fretes

Atualizado em 05/11/2026, aproximadamente 21:43 BRT. O usuário encerrou o trabalho deste chat para economizar contexto e pediu apenas um chat novo na seção ETL V2. Primeiro verificar o estado; depois concluir somente estas duas entidades. Não reabrir todas as conversas antigas nem começar outra entidade.

## Estado real ao parar

- **Dados: Coletas 3/3 e Fretes 3/3 aprovados por conteúdo.** Foram comparados os valores de cada coluna, tuplas completas, associação por ID, repetições e totais. Não foi só contar registros ou colunas. Extras da API são permitidos.
- Sem execução pesada ou HTTP conhecida em andamento. Canônico `FB923CC52CBB6740386AB1D62D9ADD4663C04675E6FCE645EEB08B36EC6DA7E6`, 781028 bytes, slot vazio; AUX28 preservado.
- **Código final ainda NÃO aplicado nem qualificado.** A candidata V6 ficou fora do projeto. Nenhum dos seus 39 arquivos foi aplicado: 15 destinos existentes continuam iguais e 24 novos continuam ausentes. Unit07 não foi preparada. Nenhum FORMAT/TEST220, classpath final, replay Java real ou diferencial129+2 foi executado.
- O release que citava SHA `14375CA2…F009F5D` foi explicitamente **revogado**. O índice V6 local terminou com outro SHA, abaixo. Não reutilizar o release antigo nem presumir que a diferença foi reconciliada.
- V1 pausado. Main não adotado, qualificação permanente/entidade e consumo real do dashboard não emitidos. SQL de produção, deploy, serviços e ACL não foram alterados.

## Diretórios

P = `C:\Users\suporte\Documents\projetos\etl-extracao-dados-v2` — projeto de código.

R = `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao` — provas, preparadores e recibos do supervisor antigo.

O = `C:\Users\suporte\Documents\Codex\2026-11-05\etl-v2-implementar-extracao-validacao-permanentes` — candidata final e backup de código.

D = `C:\Users\suporte\Documents\projetos\dashboards-etl` — consumidor existente. V1 é apenas fonte de leitura em `C:\Users\suporte\Documents\projetos\script-etl-automacao`.

## Primeiro passo concreto

1. Ler este arquivo e os AGENTS aplicáveis. Verificar o recibo de parada e o estado do projeto, sem executar captura nova.
2. Verificar o índice final V6 e seus 39 arquivos contra V5. Root havia conferido que só `HistoricalContentRouteIntegrationTest.java` mudava no código, com duas asserções restauradas no recibo persistido. A divergência posterior do índice precisa ser explicada com bytes reais antes de novo aceite.
3. Depois da revisão concreta, aplicar a candidata e gerar uma unidade final nova com o preparador de metadados. Não fabricar recibo de aplicação ou de teste. O pacote espera **72 Java, 12 controles, 22 classes e 220 casos**; os 143 nomes anteriores foram preservados.
4. Executar FORMAT, repinar uma unidade nova após FORMAT e executar TEST completo com V5d. Só um TEST positivo e fisicamente fechado permite classpath e provas seguintes. O Root preparou `R\work\Prepare-FinalExportRoot0732-01.ps1`, AST0, **não executado**; ele retorna review não aprovado, sem disparar Original.
5. Após build fechado: classpath/inventário reais dentro Original; seis preparações de controles e seis replays históricos pelo código final; diferencial original129+2. V7 tem sequência executável no README, um par por Original.
6. Conferir integração Main/ponte local e documentar exatamente o que passou. Ativação e publicação/consumo SQL de produção não foram feitos e não podem ser alegados.

O usuário quer economizar tokens: utilizar os arquivos atuais e investigar uma pendência concreta por vez. Não repetir buscas amplas em milhares de arquivos, recriar chats por entidade ou importar toda a história do supervisor.

## Código final e parada verificável

- Índice V6: `O\outputs\independent-export-consolidated-candidate-v6\candidate.index.json`, **19763 B**, SHA **E55B3B48FC818A66A792A881367F2413FFE1BE13A0F74C4350C02E915ED61F21**.
- Recibo de parada: `O\outputs\independent-export-v6-apply-unit07\STOPPED-RELEASE-REVOKED.FINAL.json`, SHA **DE5BD2C359B331F965090121E1935C50429CDC00F39FFAA553FF9EFC5EF5E48F**.
- Backup dos 15 existentes: `O\outputs\independent-export-v6-apply-unit07\backup-code-manifest.json`.
- No diretório V6: `source-and-test-ledger.PREPARED.json`, `OWNER-CONSOLIDATED.diff`, `FINAL-REVIEW-AND-SCOPE.json` e matriz de campos/dependências. São candidatos, não prova de execução.

O projeto ainda contém a unit06 anterior. Seus **143 JUnit passaram duas vezes**, com Maven0; porém nenhum desses TEST gerou qualificação completa: um lote falhou no leitor XML do harness após os testes; outro atingiu300s na conferência posterior ao Maven. Ambos continuam históricos e não qualificam o build final220.

A candidata integra Main por perfil, um único porto de entrega, produtor desde a captura, stage tipado e releitura. Corrige a minuta de Fretes para `corporation_sequence_number`, coluna27, e não `sequence_code`, coluna2. A revisão também corrigiu cancelamento fiscal: fallback de status do frete usa quatro valores exatos; busca por trecho permanece apenas nos campos fiscais. Dados/referências ausentes deixam a entrega pendente, sem valores inventados.

## Provas de conteúdo já obtidas

Períodos: 12–18/10, 19–25/10 e 26/10–01/11/2026.

| Período | Coletas: linhas / IDs | Fretes: linhas / IDs |
|---|---:|---:|
| 12–18/10 | 1277 / 937 | 3836 / 3475 |
| 19–25/10 | 1561 / 1155 | 4694 / 4188 |
| 26/10–01/11 | 1439 / 1144 | 5224 / 4592 |

Coletas: **31 colunas comuns e 8 totais**, com 131 mecanismos qualificados historicamente. Fretes: **114 colunas e 50 totais**. Todos os valores/tuplas/IDs/multiplicidades dos pares aprovados bateram. Divergências anteriores ficaram preservadas. As recapturas próximas demonstraram convergência dos arquivos ESL; não alegar que valores foram alterados no ETL para forçar igualdade.

Índice resumido das provas: `R\outputs\coletas-fretes-fechamento0732-DRAFT.md`. Essa página tem links absolutos para os seis aceites; os campos finais de execução estão honestamente PENDENTES.

Fretes aceites em R/outputs: `root-fretes-first-week-content-acceptance0717-01.json`, `root-fretes-week-content-acceptance0720-week12.json`, `root-fretes-week-content-acceptance0725-week26.json`. Último par: API1767811/UI1767813, ambos05/11às19:24. O catálogo final tem sete pares: seis positivos e um negativo preservado/bloqueado.

As seis referências Python completas também foram executadas com sucesso e reproduziram os aceites. Recibos: `R\work\python-realpair-reference0726-<tag>-01\root-executed-binding.json`, tags `coletas-week19`, `coletas-week12`, `coletas-week26`, `fretes-week19`, `fretes-week12`, `fretes-week26-recapture05`. Projeções privadas não devem ser abertas fora Original.

## Pacotes finais preparados, revisados estaticamente, não executados

Todos os caminhos abaixo são relativos a R. Verificar pins antes de adotar.

| Pacote / índice | SHA256 / bytes |
|---|---|
| `outputs\independent-export-offline-root0730-v5d-DRAFT\index-DRAFT.json` | `49A29946D9619244097E083FFFE5967BA8737EDE10F5019E5C43DF697CA8448B` /11358 |
| `outputs\java-runtime-classpath0730-v4-DRAFT\index-DRAFT.json` | `CD5F5816575A6FD0EC99FE4E471C6FF3F750894D77BBE3150AC4BB9E78B9F2CF` /8130 |
| `outputs\coletas-java-differential0730-v3-DRAFT\index-DRAFT.json` | `D15A0365E3BBA4B6E3648320EB8E592D53CE12DC6115F9C03A5F021F7E38CE08` /9459 |
| `outputs\java-realpair-replay0728-v7-DRAFT\index-DRAFT.json` | `9802E9C13D27EE8BBACFC913BE57FD6039C09FC8E65D5D8E57DB681189FE1F90` /12142 |
| Núcleo histórico `outputs\java-historical-content-core0730-v1-DRAFT\index-DRAFT.json` | `BFEFD52E4B957A0B9C8082707DF8EEAD9171205DEA27BC2BBF5279070E771024` /5793 |
| Catálogo `outputs\java-realpair-replay0725-v3-final-DRAFT\pair-catalog.DRAFT.json` | `B1225E93749D25EAE502313BCFF174E1CB4577542B01725D0205FCC6FA688FCF` /217873 |

V5d mantém todos os arquivos/casos e usa Java ArgumentList para iniciar Maven, evitando o limite8191do cmd. Bootstrap/JAR só dentro Original, selecionado único e pinado. Heap512m, fork0, MavenTEST240s, Original1GiB/300s e três pós-checks preservados. O guard de ancestrais foi otimizado sem cache/atalho: benchmark real Original38044/0 fechou em17s, ganho6,05x nessa parte. **O lote final inteiro ainda não foi comprovado sem timeout.**

V7 usa Main `independent-export compare-content`, mesmo núcleo de comparação, resultado **HISTORICAL_CONTENT_EQUAL_FOR_THIS_PAIR**, sempre sem autenticação/aceite operacional/Main/dashboard. Capturas antigas não têm todos os eventos/horários exigidos pelo protocolo live: não inventar `requestedAt`, cliques ou ciclos. A comparação live continua exigindo seus eventos e proximidade<=300s. Coletas usa source mapeado pinado com linhagem, sem alegar parserRAW.

Revisão estática69 aceitou ancoragemB122/API/UI individual e conversão Upper/Lower apenas de descritores. Observou uma pendência de evidência: replay Coletas verifica relatório131/aceite timestamp ao ler, mas o digest pré/pós do worker não cobre esses arquivos adicionais. Antes de alegar imutabilidade durante toda a execução, ampliar a retenção/conferência admitida ou limitar explicitamente a alegação. Não emitir sucesso de execução por revisão estática.

## Limites, autoridade e dashboard

- Entidades por Data Export; GraphQL somente usuários. Extras da API não reprovam a comparação.
- Respeitar regras vigentes: mínimo3s após resposta antes de nova requisição, retry0, parada imediata429; retomar operação/fileId persistido sem repetir POST desconhecido. Não fazer nova aquisição na verificação inicial.
- Um executor pesado/Source por vez. Original permanece em `C:\Users\suporte\Documents\projetos\.runtime\memory-control\Executar-Batch.ps1`,1GiB/300s; RAM>=4GiB/commit<=85%, filhos finitos/EOF/fechamento. Não aumentar limites, pular testes ou aprovar artefatos de lote falho.
- Leitura/hash/stat de arquivos de negócio e JAR/class somente dentro Original. Preparadores/chats auxiliares só código e controles. Não imprimir tokens, URLs assinadas ou dados privados.
- Canônico em `C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01\supervision-current.json`. AUX28: `R\work\fretes-aux-download-reconcile-next-root0719-week26-08\aux-current-control.json`,57388B/SHA49A25567E3A2636E4D81E1DFCBC32B2BE5B9D97F34BC803DF0C0B60B003FA25D. Preservar28fatos e históricos Main19complete14partial; não restaurar AUX antigo.
- Os contratos antigos fixam ownerRootUUID `01a17ee4-5f83-7051-9cec-ffc8e16642fe`. O chat novo precisa conferir a continuidade/autoridade aplicável antes de executar; não falsificar identidade nem alterar ACL para contornar uma guarda.
- Dashboard atual: Coletas tem21campos em `dbo.vw_coletas_powerbi`; Fretes consome `dbo.fato_fretes_faturamento`, elegibilidade/exclusão, referência de faturamento e `responsavel_regiao_destino_key`. Referências governadas de calendário, fiscal, localização, região, manifestos e exclusão precisam de origem real. Stage local e fixtures não comprovam publicação/consumo SQL real. Não iniciar novas entidades para mascarar uma dependência.

Próxima resposta ao usuário deve ser simples e curta. Declarar o que está aprovado e a próxima verificação concreta. Nenhum dos dois está encerrado como100% do código final neste momento.
