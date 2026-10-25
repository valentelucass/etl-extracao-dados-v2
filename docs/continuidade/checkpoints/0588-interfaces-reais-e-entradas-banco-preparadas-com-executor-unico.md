# Checkpoint 0588 — Interfaces reais e entradas de banco preparadas

2026-10-25T12:59:19.575030+00:00. Anterior [0587-perda-de-campos-usuarios-identificada-e-correcoes-em-preparo.md](0587-perda-de-campos-usuarios-identificada-e-correcoes-em-preparo.md), SHA F26E84B546A9C0D7B06DE3B08D641A5A382798464D628E26482D3050C3A8F9EB. Etapas3/4 continuam abertas. Nenhum Java, Maven, SQL, fonte adicional ou índice foi executado nesta unidade.

UsuáriosV3 continua único executor: sessão15340/run10250003/original1GiB320min,5.000calls/100milnós/18.000s/per20/3s/uma tentativa. Snapshot selado: 2339páginas/46780observados/46776semrecusa/4quarentenas, hasNext=True, semEOF. Counters são parciais no UTC do selo; não medem wire histórico. Não iniciar outro Java/Maven/SQL/fonte/índice antes do close conhecido, recibo original e liberação Root; não alterar job vivo, renovar orçamento/cursor ou seguir após429.

## Usuários: correções revisadas e conservação ainda aberta

r04 manifesto93E77407DBE71378CC68A03AFDB196B947CAF356F83FF3FF8CC3AE0A1771AC42 recebeu revisão independente A1048DD4CB73423A038512BB4C860135ADA47394A5BB73F5CDA00E7F24175F09,20pins internos/23externos sem drift. As quatro correções r02 estão ligadas à rotina real e contraprovas: bootstrap implicit com FROM antes depth1/state1, latch de aceite após checkpoint, timeout atualizado depois dos binds e close/isClosed com prazo original.33métodos preparados, nenhum executado; não são prova do engine.

Limitação adicional medida por código: UsersTypedReader consulta prazo antes de executeQuery, mas não durante next/getters/Reader.read. A recusa posterior impede falsoPASS, porém não garante a margem cleanup durante streaming. Native está preparando r05 com checkpoints antes/depois das leituras e closes, fragmentos wire id/name reais, writer/reader independentes e proposta SQL mínima separada. O defeito de conservação de campos recusados do0587 permanece aberto; não reconstruir valores do journal agregado ou relaxar255 para ocultá-lo. Catálogo/admissão/registroRoot continuam fechados e nenhuma nova DDL foi aprovada.

## Entradas reais de catálogo Bank e próximas unidades

Root materializou8arquivos de entradas por entidade a partir do catálogo STRICT real fechado35/35, sem fonte/SQL:11tabelas/9módulos/4triggers Manifestos e11tabelas/12módulos/6triggers Cotações. Corpos normalizados foram comparados aos hashes instalados observados, referências/relatórios/recibos pinados e SQL de catálogo copiado bytea-byte do produtor STRICT. Review A4A8B3BB16D5352C2271BF58986D21D7D509B2FA4990BD126455BFE7F9234362. Isso prepara insumos técnicos; não emite release, reservaSource, grant ou admissão. A mesma conexão física futura ainda terá dois cortes novos35+35 sob proteção, sem herdar frescor.

Fila Root somente preparada: BANK81,RASTER25,USERSCAT40 em unidades separadas1GiB6/4/4min, cada uma por seu controlador original e com revisão própria. Plan A41BAB8D9AFE36F353AF52427BA77C01262FFA8870B5E278404F08FFCDD495C7, wrapper66BE16A28660336BE455C39FF3D29B87E38F2967D44A5EA4A0959616391C6630. Pins dos três candidatos e externos conferidos, parser estáticoPS passou; zerocompilação/modelos/execuções. Falha inicial do preparador foi caminho longo Python antes de qualquer saída, corrigido no próprio preparador com prefixo estendido; não é falha de candidato ou qualificação. Wrapper recusa executor ativo/counters sem fechamento, não roda fila automática e não libera estado em finally sem reconciliação.

Banco entregou coletorUsers diagnóstico próprio16SELECT/40grupos preparados, manifesto168994DC675E90F604672742F4C505FEDACE848E7B743E8D6E93CF8272EE3713;374nomes lexicais não são fechamento semântico. Saída continua CLOSED_UNKNOWN_DIAGNOSTIC_ONLY, sem captura. Também identificou cinco lacunas no ownerRasterV2: revogação de portas já entregues, tipos/NULL de contexto, prazo incluindo opener/provider, fechamento observado e catálogoV5cinco tabelas. Contracts prepara sucessor concreto; nenhum teste novo executado.

## Interface P3 e preparação DDL

Fluxo congelou workflow manifesto43A6116FC36503B8C6E96510DAE812322BFE7FEAA39FA78E7F4A363626FFE869 com43invocações preparadas/zeroexecutadas. API runRootUnit(UUID, ObservedExpansionCapturePlan, String) compara plano integral e fingerprint antes de efeito; UUID sozinho recusa. Mesmo handoff RAM privado/singleuse preservado até writer/reader151, sem JSONauthority. Native liga a rota Main a essa assinatura fixa, mantendo RootCODE/controller fora do produto. Fluxo prepara controlador Source+SQL próprio; controllerSource-onlyq01 e receipt não concedem isso. P3sinistrosq01 expirado continua0calls/não reemitido.

Sucessor SQL de contexto P3 usa CURRENT_TRANSACTION_ID, forward9B8B1AF092BD6CCB4E54AB55B7959B094266C3F96606EE0405434277780B05EF, só arquivos/zeroengine; preservaCC08anterior. Pré-DDLArchitecture prepara coletor próprio Fretes23/Local19/P3novo14. Banco prepara programas concretos separados de aplicação/recuperação Fretes2objetos+Local1; não usar ownerC78typedrollback-only como ownerDDL. EscoposP3/Raster/Users são separados. Nenhuma autorização decorre de hash/fixture/proposta; a perguntaFretes anterior continua sem resposta. Não pedir aplicação antes de fechar ação e recuperação revisáveis.

## Três próximas ações

1. Concluir/encerrar a travessia finitaUsersV3, conferir journal integral/selos/recibo/cleanup e reconciliar counters antes de liberar executor.
2. Qualificar unidades preparadas próprias e seus sucessores, medir catálogos/contexto reais e provar fonte→staging→readback→rollback→ausência por entidade, incluindo campos recusados.
3. Fechar programas/controleDDL e decisão humana específica quando concretos, integrar Main/P3/Raster/Users elegíveis e validar coorte/CI/AST final. Não encerrar por preparo ou testes em memória.

ProdutoE05/150+7 semdeploy; q06/447+7 externo. Nove Data Export terminais só nos recortes, principalFretes37/114,97predicados parciais Fretes/Local e RasterRAM108/355 semissues permanecem provas distintas.20JDBCconfirmadosfechados+1tentativa nativa fechada semretornoJDBCobservado;0DDL/commitnegócio retido. Preservar SQL16.0.1000.6/instalação, contas/credenciais/permissões/identidades/ACL/elevação, serviços/listeners/certificados/trustglobal, V1/WMS/produção,43RAW/53exclusões/MainHTTPUNKNOWN/MISSING_NOT_RECONSTRUCTED.
