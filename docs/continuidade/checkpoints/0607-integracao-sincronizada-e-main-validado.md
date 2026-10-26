# 0607 — Integração sincronizada e inicializador validados

A nova sincronização foi exercitada com processos reais e no pacote de integração. Os sete testes originais de integridade, os111 casos do agregado, os quatro de Clock e os sete de ligação ao Main passaram, cada qual com seu próprio executor original fechado0. A integração continua sendo uma proposta qualificada em testes; não houve adoção do runtime, implantação, nova extração de dados ou gravação por esses resultados.

## Sincronização real R37

Manifesto BBB1546C1B615BA5C76212B7F10085C25B5080CBB67B809FC4A200C26F122499. Os42 modelos anteriores foram executados novamente, sem alteração de corpos. Quatro casos Windows reais comprovaram timeout100ms sem acesso ao arquivo, abandono com liberação correta, erro de leitura com liberação e fechamento do criador. Todos fecharam0.

A captura real concluiu11 amostras, duas barreiras novas, produtor dentro do Job original e fechado, cinco identidades externas exatas e três ausências preservadas. Foram27 aquisições e liberações, sem timeout, abandono ou erro; produtor, monitor e mutex fechados. Original0/2.103ms/96.944.128B. Não houve parada de processos externos ou de PM2; não se afirma identidade ou hash de imagem nativa por essa prova CIM.

Prova RootR37CaptureQualification0607.json SHA13BB2366A3FDAEC6FAD0C17023F917BC97E7AC12374B21E01D0FF352BA065111. Política3488B4D7D8ECBC9B30D0E1D7C97C67CB1F352D020A0F12C3E25CE53FB01EACF6 e seleção exataF7EF02C1753413BF1A832A2F631068A9BAD7CBE3A3BAEC0F4710F90A9F702A1E.

## Pacote R38 e Main R42

O alvo é15AB5FF74440772205EE3BF60F55049DAF6E30740CB97B0915351CC4C4EDFF28. Ele é diferente do produtoF2A usado no ensaio real de Cotações0606. Nenhum sucesso é transferido entre os dois.

| Operação própria | Resultado | Tempo original | Pico do Job |
|---|---|---:|---:|
|R38 Prepare|0; entradas novas vinculadas à capturaR37|10.290ms|36.024.320B|
|R38 integridade|7/7, zero falhas; sete corpos originais preservados|109.703ms|725.807.104B|
|R38 Bind|0; recibo próprio de integridade e entradas dos agregados vinculados|6.047ms|37.031.936B|
|R38 agregado|111/111;32 métodos|22.087ms|459.399.168B|
|R38 Clock|4/4; um método|16.785ms|452.526.080B|
|R42 Prepare|0;49 classes API preservadas, zero novas linhas CODE|8.884ms|36.515.840B|
|R42 Main|7/7; três métodos,53 origens conferidas|7.926ms|353.947.648B|

Todos usaram originais separados1GiB/6min, sem timeout, Source, SQL, leitura de provider ou admissão física. Heaps e deadlines não foram ampliados. A integridade observou790 amostras e808 aquisições/liberações sem erro. O Main observou58 amostras e73 aquisições/liberações sem erro; Main real de produção não foi acionado.

A revisão encontrou no R39 uma variável indefinida em `return proccode,out,err`, que impediria o primeiro retorno normal do compilador. R39 não foi executado nem alterado. O sucessorR42 muda somente a expressão para `proc.returncode`; a inversão restaura exatamente o SHA anterior72897EE3F71C23454FCDAC43C60F29D93265F6703815D207EF1F34F23A3A3C19. Os sete testes reais do Main foram a validação; não foram criados testes espelho.

Provas externas: RootR38Product7Qualification0607.json SHA749065EFBA619BF5EE0F9AA9F38773E73A9EDA378696D3585DC87E0258AD36E1; RootR38jar111Qualification0607.json8DEC3691F0E2C4ED03E43B377CC3F065E65A6EE3A0E8714AE6429269E4C03F68; RootR38jarclock4Qualification0607.json745E0CC7B3F7AC501C0E0DD2AAAB07F5C10F1543AC1B6001486CC5E6835DDE56; RootR42Main7Qualification0607.jsonBDBFAB4EAE028623EE27BB28192667A8E3572DD6BD217FED2FAEAC05E7B4515E.

## Coletas e próximas etapas

Coletas INFO6908 é uma das cinco entidades já cobertas pelo coletor diagnósticoV5. A preparaçãoV7 reutiliza os60 arquivos de runtime e o produtoE05B sem recompilar ou alterar transporte/parser/Gate. Seis modelos novos passaram com original0/11.930ms/106.319.872B. Reconhecem a Source realCOT0606 e seu delta local5, preservando os contadores globais não finais; os300+10 anteriores e os30 do consumidorV6 não são contados como novos sucessos.

A consulta INFO atual, a ligação JDBC23 própria e o fluxo completo Coletas ainda estão pendentes. Uma futura consulta exige emissão literal pessoal Root, unidade nova, umaINFO/zeroDATA/zeroSQL,3s mínimos,30s/2MiB e original1GiB/4min; nenhuma renovação ou repetição automática. A preparação não concede crédito, CODE, janela ou admissão física. A rotina Coletas23 foi observada instalada no catálogo0606; sua existência ainda não qualifica gravação e leitura completas.

Cotações287 e Manifestos315 permanecem os ensaios reais de gravação/leitura/rollback já documentados. Usuários continua interrompido sem EOF ou replay. Raster precisa de programa estrutural, proteção e recuperação concretos revisados; FretesLocalV3 está preparado, sem nova autoridade ou execuçãoDDL. O Chrome aguarda reconexão do controle; nenhum Excel ou paridade ESL foi concluído.

Etapas3/4, composição final, adoção e implantação continuam abertas. Root é o único executor; os demais chats trabalham somente em arquivos. ESL apenas consultas/filtros/downloads, FretesDataExport6389 distinto de GraphQL e Localizador8656 distinto de Raster. Não alterar versãoSQL, serviços, contas, ACL, V1 ou PM2. Sem commit de negócio retido, DDL, deploy ou cutover. Commit e push mantidos para alterações concluídas.
