# Checkpoint 0568 — P1B no produto,pacote219D e oráculo exatoV6

24/10/2026,23:26BRT. Sucessor0567. Objetivo das quatro etapas ativo. Etapas3/4
não aceitas;pergunta humana sobre exceçãoDDL dos2objetos Fretes sem resposta.
Nada sobre chats/delegação foi interpretado como aprovação de schema.

## Catálogo P1B adicionado e qualificado

Root revisou/adicionou5arquivos novos de candidatos-p1b:2classesmain e3recursos,
maisDataExportObservedEntityContractCatalogTest(3métodosJUnit). Sem mudança de
selector,DTO,ordem,transporte,template,SQL ou ativação operacional. ADR0075.
469verificações externas anteriores permanecem com seus recibos,sem reexecução
ou soma comoJUnit. P1original/98checks e P1B permanecem congelados.

P1B caracteriza95/37/27paths de resposta para91/37/26campos de raiz. Preserva
história numérica/nullable,INFOUNDECLARED,2NULL-only sem tipo de valor,e inclui
as listas efetivamente observadas. Versões/fingerprints:

| Template | Versão | Release SHA256 |
|---|---|---|
|6399|2026-10-24.p1b-manifestos-observed.v1|6fbc71f29c4062324ec5ccd248b7adf9638cc2c60c81d69379b429fbeba4f77f|
|6906|2026-10-24.p1b-cotacoes-observed.v1|6dd5197a7fa7ef035c4d0c5420b2403da7260c9ae38e0c3d5e8da72d6f5523f2|
|8656|2026-10-24.p1b-localizacao-cargas-observed.v1|24373b7c25cb4c7df64e260fbd3c0624c05255aeb2dc28be3b774a1216c8482f|

Root p1b-product:strictcompile e58testes funcionais PASS;Checkstyle recusou um
ifsembraces no novo teste. Snapshot/logs/58reports preservados;nenhum PASS global
foi alegado dessa rodada. Recuperação nova p1b-product-recovery copiou o snapshot,
corrigiu só braces,recompilou testes e reexecutou somente os3métodos alterados.
Não contar61distintos. Style/formatter/package e7IT PASS;zipClasses/recursos
iguais ao target. Receipt0/sem timeout/357858ms/pico880775168bytes,teto2GiB.

**JAR atual qualificado** em p1b-product-recovery/snapshot/target:
`219D408FE35E4A95A36F6FA7B740000A8EDE640676AA47C3111EC34FC86E4AAB`.
8libs inalteradas. Não foi implantado. A49 é baseline histórico,sem apagar prova.
Root publicou no repo os3Java formatados;3recursos mantiveram bytes/pins exatos.

Comparação independente leve p1b-baseline-comparison.json:1367entradas anteriores
foraMETA-INF idênticas,nenhuma faltante/alterada;apenas classes/recursos novos.
CasesJUnit porclassname/name:262Local+27Manifestos+45Fretes=334históricos;
união com58atuais=**378distintos por composição**. Não somar os3rechecks nem469
checks externos;7IT pertencem ao pacote atual. Semfullverify/cobertura nova.
AST44514/116337 é anterior às adições e **precisa atualizar após integração**;
não apresentá-lo como mapa atual dos novos fontes. Nenhum index gerado agora.

## Oráculo de banco textual exato e atual219D

Entrega estática Banco em diretório irmão da campanha:
delegacao-revisao-banco-20261024-01/trial-text-readback-candidate. V3 copiado do
V2 com nomes novos e apenas troca da igualdade de9JSON por texto completo.
V2/falhas/propostas anteriores imutáveis. SELECT lêasstrings diretamente nas
tabelas,semFORJSON/JSON_QUERY;inserts conservam parâmetros.Nenhuma perda real
noSQL foi alegada;caso decimal artificial demonstra fraqueza do oráculo antigo.

Root qualificou Stage3FretesPipelineV6 +FretesBankTrialV3 e2helpers contraJAR219D:
strict/Werror,pedido principalC78,19casos temporais do oráculo real preservado e
teste de igualdade textual vsDoubleNode PASS. stage3-v6-offline/result.json,
receipt0/7132ms/sem timeout/pico327745536bytes,teto1GiB.0HTTP/SQL.
Stage3Fretes-v6.ps1 está somente preparado;exige catálogo pós-adoção real que
continua inexistente. Não confundir preparação/offlinecom admissão física.
NenhumALTER/proc/readback37foi executado. DDL ainda pendente;C78rollback-only.

## Próxima unidade delegada: ligação real dos contratos e rawLocal

ETL V2 — Fluxo e usuários concluiu revisão estática em
delegacao-usuarios-20261024-01/revisao-fluxo-observado/REPORT.md. Perfil fechado
como7ºcomponentePageRequest,construtorlegado6mantido,verificação central nos3
gates e descriptor genérico do release já vinculado ao guard. Sem flags livres
noJSON,sem fallback sintético,GETBODY/retry1/preferred-only/per1..100/semordem
explicitamente ligados ao releaseP1B. FRETES continuaid asc.Semântica legada
não deve mudar bytes de fingerprint;novos campos derivados sóparaos3seletores.

P2Local:StrictParserjáconservaBigDecimal/escala,masnão léxico original completo.
Proposta transporta rawStringSlices limitados desdeRootARRAY atémapper26,sem
JsonNode.toString;legado17preservado eSQL17continua impedindo staging26.

Root autorizou implementação **externa candidata** em
revisao-fluxo-observado/candidato-fluxo-p1b-p2,sem editar repo. Testes adversos
e regressões,perfillazy que não force recursos observados nolegado,semrefatoração
ampla. Vaga liberada ao chat após fechamento dos jobsRoot;Maven/JDKoffline no
executororiginal≤2GiB/finitos,piso4096/85. **Root agora somente leitura/docs**:
não iniciar index/SQL/build simultâneo nem adotar fonte candidato sem revisar.
Nenhum resultado dessa nova implementação foi recebido neste checkpoint.

ChatContratos recebeu P3estático em p3-captura-observada:avaliar menor captura
físicaUNBOUND das4expansões,sem fonte/tenantfalsamenteSYNTHETIC,occurrence interno
monotônico por execução/multiplicidade/raw+issues. Primeiro verificar armazenamento
genérico existente e gates;propor objetos/DDL necessários em escopo próprio,
sem executar. Não escrever produto/canônicos nem fazerAPI/SQL/build. Falta de
identidade não deve virar impedimento inventado para toda observação física,
mas continua impedindo promoções/fatos que aexijam. PropostaFretes2objetos não
autoriza outros. Root mantém docs/CI/SQL e decisão de adoção.

## Estado e próximos passos

1185HTTPfonte/1186reservas(88DataExport,1096GraphQL,1Raster),zero429observado;
8conexõesSQLencerradas/0DDL/0commit retido. Nada novo àESL/SQL desde0567;builds,
modelos e programa offline não são captura.SnapshotUsuários18.980/18.978/2nomes
semEOF;cursorperdido;sucessorobservador27cenários+Windows513snapshotsqualificado,
sem launcher/live/capturaaceita. Não renovar automaticamente a travessia.

1. Receber/revisar candidatoFluxoP1B/P2;integrar bytes qualificados por unidade.
2. Receber P3estático e preparar escopo exato de captura observada das4expansões.
3. Com resposta humanaDDL real,qualificar/aplicar somente escopo concedido,
   observar catálogo novo eprovar37fonte→staging→readback→rollback→ausência.
4. Atualizar AST após alterações finais;preservar aceites de entidade/identidade/
   snapshot/retentiva/paridade/produção que ainda faltam. Não fechar etapa4porbase.

SQL16.0.1000.6/instalação,contas/credenciais/permissões/identidades/ACL/elevação,
serviços/listeners/TLSglobal/V1/WMS/produção preservados. C78nãoherdaE3retentivo.
Gates43RAW/53exclusões,MainHTTPUNKNOWN/MISSING_NOT_RECONSTRUCTED mantidos.
