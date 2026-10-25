# Checkpoint 0587 — Perda de campos Usuários identificada; correções em preparo

2026-10-25T12:34:14.929771+00:00. Anterior [0586-integracao-revisada-e-ligacao-p3-em-preparo.md](0586-integracao-revisada-e-ligacao-p3-em-preparo.md), SHA256 6625C4B5553CD5097AC0BDC492CEF4DB40156411BE76EF9616586CEAE240D5A4. Etapas 3 e 4 abertas; nenhum novo Java, Maven, SQL ou fonte nesta unidade.

Único executor: Usuários V3, sessão15340/run10250003, original1GiB/320min; mesmos limites5.000calls/100milnós/5h/per20/3s/uma tentativa. Snapshot selado desta escrita: 1835 páginas/36700 registros/36697 válidos/3 recusados, hasNext=True. Ainda sem encerramento. Counters são parciais no UTC do selo; não representam fechamento ou wire físico. Nenhum outro Java/Maven/SQL/fonte/índice até close conhecido, recibo original e liberação Root. Não alterar V3 vivo, ampliar orçamento, reaproveitar cursor ou renovar após429.

## Defeito de conservação confirmado por código

O mapper legado Users descarta name recusado e, quando o ID é recusado, pode também perder o valor do ID. UsuarioStageRecord mantém apenas sourceKey permitido e reason; o gateway binda NULL para name/presença e V024 retorna em QUARANTINE antes do sidecar. stg.execution_record não tem payload/raw. Isso demonstra classificação/rejeição, não conservação sem perda. A inspeção não leu payload atual nem revelou valores. Review RootUsersRejectedFieldLossReview.json SHAEE3723C356A1D45CCE05EDCCC563778360CB73B0505B054738D7AA40650F2663 fixa os cinco arquivos canônicos/linhas. Não reconstruir valores descartados do journal agregado; EOF futuro não corrige essa perda histórica.

Conservação separada preparada em work/users-field-conservation-candidate-20261025-r03, manifesto51BB36C84E27849E67CC94AED5D62BE580532A2950804FB1083A0DAFF4357E54: uma classe+um teste, oito métodos preparados/zero executados. Recorta SOMENTE os fragmentos JSON wire id/name do UTF8 original, com presença distinta, antes de cópia, até256KiB/campo e4MiB/página20, RAM privada/zeroização. Preserva números/escapes/containers sem tentar reconstruí-los de JsonNode, cujo parser GraphQL legado usa Double por padrão. Hook do corpo genuíno e writer ainda não estão ligados. Proposta SQL de evidência de campos/linhagem é separada e não aplicada; não há autorização nova, versão Flyway ou claim de retenção. Não relaxar255 nem promover dado inválido a VALID para ocultar perda.

## Caminho Users real e quatro correções causais

Kernel mecânico r01 C95DE07270A81AA84F3068033C10A0F64AC7B6BB1F629B0A1D877AF0D0879D61 continua somente preparado. r02 manifesto8C3B92ED4B95EBEDA135744BB601C7AC68002362E88EC95108C5BA0F4EAEF71C liga factory/parser/mapper reais, handoff privado/singleuse, source fechada antes C78, controlplane3RPCs reais e overlay borrowedTransaction sem commit local;24métodos preparados/zero executados. Registro Root vazio e catálogo/admissão Users continuam fechados.

Revisão independente r02 SHA CC42E65663CC5F4431F1CDB2D15795B42D19134F529FD83850AC8F7E86A7990C identificou: setAutoCommit(false)+SELECTsemFROM não estabelece a transação exigida; falha tardia do checkpoint podia ser sobrescrita por positivo após cleanup; timeout não era recalculado depois dos binds; close após90s podia gerar sucesso. Nenhuma falha foi testada no engine. r04 separado está corrigindo os quatro caminhos e preparando contraprovas no owner compartilhado, sem fabricar grant nem chamar o dummy de prova SQL. O bootstrap correto tem registerSource/startCycle/startExecution; o último já inicia EXTRACTING, sem transição PLANNED inventada.

## Demais entregas estáticas

BankV7: seloA47B82C96004591A47131AA275F45FAEAD1DDDC0291A5420C9AE76AAD5E05D0B,56arquivos/715referências/81modelos preparados. Raster catálogoV5: manifesto8A186E74A3B77EF39CA98C01443528071FF39E4F2182C621D858E74DF6E3E927,31arquivos/275referências/15SELECT/5tabelas/25modelos preparados. Raster owner transaction-idV2: manifesto536033A306FF0C7A93F49FBD4F6834E1B8C7E87173C0D3FE9F659141A2D25221,19ensaios preparados. Zero execução nova; modelos históricos não qualificam esses bytes. Banco revisa composição e prepara coletor Users próprio; expansão lexical374objetos não é fechamento semântico/admissão.

Pré-DDL próprio: manifestoD0BAF6DA5043EEEB5A36CD6B4BFFACBB97A852889F52FFD672B6EFA21E241879,56inputs/23referências,31SQL/10móduloscanônicos/30vetores preparados. Planos Fretes23/Local19/P3novo14SELECT são diagnósticos; coletor C78 próprio está em implementação externa, sem herdar catálogo/grant35. Ausência ou UNKNOWN não aprova DDL. A revisão anterior dos quatro escopos permanece desenho sem efeitos; a nova conservação Users tampouco está aprovada.

Rota P3→Main sucessoraV2: manifestoCC272AE7C47E5D13BA112EF6F37FA28081A73618BAF611578BB97B20993E8899, Main overlay com case+ajuda e15métodos/27invocações preparados/zero executados. Plan concreto; rollback-trial ainda fechado até API Flow comparar pedido/plano/fingerprint antes de provider e devolver handoff/resultado genuínos. Não ignorar o pedido, copiar CODE/launcher Root ao produto ou ligar P3 ao Promotion normal.

## Três próximas ações

1. Encerrar/reconciliar Usuários V3 finito com journal integral/selos/recibo/cleanup e somente então liberar a próxima árvore.
2. Qualificar os candidatos próprios e coletores, medir catálogos/contexto reais e completar ligação fonte→staging tipado→readback→rollback→ausência, incluindo conservação dos campos recusados.
3. Fechar programas/controle DDL concretos e decisão humana específica, concluir integrações elegíveis P3/Raster/Users e validar coorte final/CI/AST. Não encerrar por preparo ou resultado offline.

ProdutoE05/150+7 adotado semdeploy; q06/447+7 externo. Permanecem nove Data Export terminais apenas nos recortes, principalFretes37/114,97predicados parciais Fretes/Local e RasterRAM108/355/zeroissues, sem nova captura SQL.20JDBCconfirmadosfechados+1tentativanativafechadasemretornoJDBCobservado,0DDL/commit de negócio retido. PerguntaFretes2objetos ainda sem resposta; não cobre outros escopos. Preservar SQL16.0.1000.6/instalação, contas/credenciais/permissões/identidades/ACL/elevação, serviços/listeners/certificados/trustglobal, V1/WMS/produção,43RAW/53exclusões/MainHTTPUNKNOWN/MISSING_NOT_RECONSTRUCTED.
