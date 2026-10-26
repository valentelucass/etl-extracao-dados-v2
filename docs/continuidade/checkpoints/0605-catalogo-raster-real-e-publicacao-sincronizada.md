# 0605 — Catálogo Raster real e controle de publicação sincronizada

A leitura do catálogo instalado do Raster concluiu as 15 consultas previstas. O banco tem as cinco tabelas examinadas, mas as duas restrições de escopo ainda exigem dados simulados. Latitude e longitude estão em DECIMAL(28,8), enquanto a fonte observada contém valores que exigem maior precisão. Isso identifica incompatibilidades reais; não admite a gravação Raster nem autoriza alteração de schema.

O controle isolado de publicação sincronizada R36 também terminou com sucesso. A sincronização eliminou as falhas de abertura observadas na janela do controle anterior, mantendo os limites e sem repetir leituras. Ainda falta provar a integração operacional com esse mecanismo. Nenhum desses dois resultados conclui as etapas3/4.

## Raster: fatos do catálogo instalado

- CandidatoV5, manifesto `8A186E74A3B77EF39CA98C01443528071FF39E4F2182C621D858E74DF6E3E927`; os 25 modelos próprios anteriores permanecem como prova da mesma versão, sem nova execução ou transferência para outra.
- Unidade `live-root-raster-meta-0605-01`: original0, processo Java0, 15 SELECT tentados e15 concluídos; uma conexão C78, um provider, sem Source, DML ou DDL. Fechamento de conexão/processo/stdin/socket e identidade nativa SQL Server16.0.1000.6 preservados. 15.605ms, pico559.280.128B sob1GiB/4min.
- Tabelas: ctl.analytic_lab_run, ctl.analytic_raster_capture, stg.analytic_raster_trip, stg.analytic_raster_stop e stg.analytic_raster_structure. As duas coordenadas de stop são DECIMAL(28,8), nullable.
- CK_analytic_run e CK_analytic_raster_capture estão habilitadas e confiáveis, mas exigem fn_local_synthetic_scope e synthetic-analytic-v1. O trigger proposto ctl.trg_raster_observed_capture_scope_v1 não está instalado.
- Cinco módulos,40 linhas de chaves estrangeiras e187 dependências foram coletados para revisão. A igualdade da fotografia dos objetos antes/depois passou; coleta de metadados não significa fechamento semântico, permissão de migração ou admissão de dados reais.
- Prova Root: `RootRasterCatalog15Qualification0605.json`, SHA256 `C9FAF39A7C8F5FF34A5247F966686013F062D6C8468E37DFDC05EF0D8491D019`. Metadados privados SHA256 `8AF7CDF7948780B429AE710A92C14FDA278B9432F6D8A31398E4FD370BFFC438`; controlador `7C705D4EE08E022F705ED133D0ED594D0256AFA438BB84819D3B14F90DB7EA4A`; original `448BD0185724EF88EEE0824903AD0C9A9EF5F9987B74B2D0CD291E323D8403A2`.

Uma comparação de hashes no controlador externo inicialmente tratou diferenças de maiúsculas como divergência. A parada aconteceu antes da janela, executor original, provider ou SQL. O Root confirmou os mesmos bytes e corrigiu apenas a comparação hexadecimal; a reconciliação foi preservada. Não houve repetição de consulta ao banco.

## Integração: controle R36 positivo, produto ainda pendente

R34 havia reproduzido erros de ausência/compartilhamento durante File.Replace; R35 com MoveFileExW falhou com erro nativo5 e permaneceu inconclusivo. R36 preservou o File.Replace com argumento null tipado e colocou publicação e leitura completas sob um mutex Local novo por unidade. Uma aquisição limitada a100ms por operação; timeout, abandono ou erro recusam a operação. Não há retry, fallback, alteração de ACL ou mutex Global.

O experimento R36 concluiu4.635 leituras: uma do controle de ausência conhecida e4.634 leituras válidas. Foram445 publicações, sendo uma inicial,14 na cadência250ms e430 na fase acelerada. As fases válidas tiveram zero falha de abertura/leitura/fechamento, documento inválido ou regressão de sequência. Os contadores de aquisição/liberação coincidiram em4.635 para o leitor e445 para o publicador, com zero timeout, abandono ou erro; os dois handles fecharam, publicador0 e sem parada forçada.

Original0,21.509ms, pico133.574.656B sob1GiB/2min. A janela, formato801B, caminho164 caracteres, controles e tetos foram preservados; o menor número de iterações sob sincronização é observado, sem alegar mesma taxa de execução. Os casos de falha do mutex foram revistos estaticamente, não executados nesse experimento. Não houve CIM, JVM, JUnit, HTTP ou SQL. O resultado não prova retrospectivamente a causa exata deR33 e mantém operationPass=false e qualifiedProduct=false.

Prova `RootR36MutexControlQualification0605.json`, SHA256 `92D2C2928587E55EF02C3ABDD7866F4F8E02524EE1F40E65B154D79B1F60E571`; manifesto `E5E3FAF38D913D38CCA976610971111C4805AA2563B363D55DED8EB9B5517058`. Sucessores operacionais mínimos em preparação, com os42 modelos anteriores preservados e poucos casos causais de segurança. Depois ainda são necessários Capture, Product7, Bind111, Clock4 e Main7, sem relaxar seleção, limites ou guardas.

## Cotações, Usuários e Coletas

BoundV15 passou278 testes próprios e dois preflights de arquivos, após revisão independente dos129 arquivos e1123 pins externos. Todos274 anteriores foram reexecutados, mais quatro casos causais para Cotações P2/per100; Manifestos permanece em50. Os modelos usam o produto P2 já qualificado por200 testes e verificam serialização, withPage, bindings distintos, Gate real com INFO37/6 filtros em transporte RAM e preservação do instante original. Original0,128.248ms, pico715.091.968B sob1GiB/4min, sem Source/SQL. Não houve adoção do runtime, emissão de CODE ou janela.

Prova `RootBankV15Own278Qualification0605.json`, SHA256 `8AFDA0823FD085CA3C53465D03DF80573D7A6285B3EF52A1A1C7726AF99A0890`; manifesto `D92209A41FC78FD1283105CC80C37294DEC341D5DAD744957CDDE5F3A64C2E7D`. Qualification `CD3478FFC9C03B95CDC0D298A4BEFF2A36C4222080ED006C48428EFCD8819503`, original `9F559999185BA4E484F58FAC4D2C8BE7238FDC831DB1893ADAFB43CF93687B43`, quatro casos COT `B432C31A2CFD08D5E419E024EDAA56328836141AA4906929215386A2BAA95C95`. O wrapper dedicado de Cotações ainda precisa reconhecer o último Source0604, incorporar a correção de um campo do consumidor comprovada pelos cinco testes anteriores e passar sua própria qualificação antes do ensaio real.

Os limites temporais são distintos: entrada do wrapper antes de READY exige menos de30s; a validade do instante original para start_execution permanece300s, igual ao procedimento SQL. O instante original atravessa a Source sem ser recriado. Páginas100 podem caber melhor no teto Source210s; a estimativa de cinco chamadas/162s não é uma observação real nem garantia de sucesso. Permanecem oito chamadas máximas, uma tentativa por chamada e intervalo3s.

O estudo de Coletas identificou a ligação existente ao gateway de23 parâmetros e diferenças concretas para o caminho Manifestos/Cotações: perfil LEGACY GET_WITH_QUERY, setBoolean e timeout próprio, além do footprint a comprovar. O catálogo de Usuários está recebendo os três objetos wire e o procedimento específico stg.usp_stage_coleta_record para uma consulta atual dos objetos; preparação de roster não comprova instalação. Nenhum coletor novo, alteração genérica de reflection ou DDL foi aplicado.

## Estado e continuidade

O último Source continua Manifestos0604: três reservas/conclusões,315 linhas físicas,315 gravadas=315 relidas e rollback/ausência/fechamento provados. Java0 e produtor físico completo; original geral1 e controlador não qualificado por campo obsoleto no consumidor. Os recibos continuam intactos. O delta local é3; contadores globais não finais, sem replay/renovação de unidades anteriores.

O Chrome continuava listado, mas perdeu a conexão de controle durante a preparação dos filtros, antes de consulta ou exportação. Reconexão solicitada, sem novo retorno até este registro; nenhum Excel ou paridade foi concluído. A plataforma permanece exclusivamente para consultas, filtros e downloads. Fretes Data Export6389 é distinto de GraphQL; Localizador8656 não é Raster.

Usuários continua sem EOF; a prova completa de todas as entidades, adoção do runtime e etapas3/4 permanecem abertas. Root é o único executor pesado/API/SQL/browser/Git; outros chats trabalham somente em arquivos. Sem DDL, retenção de commit de negócio, alteração de versão SQL/serviços/contas/ACL/V1/PM2, deploy ou cutover. Mudanças concluídas seguem com commit e push.
