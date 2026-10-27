# 0639 — Main formatada integrada; publicação de Usuários em correção

Continuação de27/10/2026. Root permanece o único executor de processos,
consultas externas, SQL, navegador e Git. Os três colaboradores trabalham
somente com arquivos. Nenhuma chamada Source ou conexão SQL nova ocorreu nas
execuções descritas abaixo.

## Entrada principal: código final e integração

O estilo das duas fontes Main passou em cópia isolada: apply, check e Checkstyle,
Original0,15.425ms e742.502.400bytes de pico. Prova03F23FC63DEBA20E97B9C3FD398E8370B275E3F29EF31083FCC7ADC1C8B49F83.
Depois da formatação, os mesmos dez testes foram executados novamente contra um
novo JAR:10passados/0falhas, cinco filhos fechados0, Original0,16.620ms,
352.776.192bytes. Não houve herança da qualificação do JAR anterior.

Qualificação real A934C221873BAA5E0DA203A35C751AB7319CDB69856733D0FBDB3549E4C3DF15;
JAR A98F4B980751E5AE174677A7FFAF70E1308503EE368C23D48F2B0A233E1F61E6;
Original3CFC745D96A80E795EC099A37E368DADBB9FB465EFBD468F27D3C43AE892FB34;
JUnit3E51E0FB8C31D0A6843398CA7B7FC6FBEA3D9AB95476586E97471DD9788D652A.
Proposta mínima2C4809ADE18089E462A71FC0BB7657A1EE3EF6662235400B39724701B89898DE,
revisão AB27CB1CAC8749DBB66355ABE2F14A1B7BEA264EFA28BE8A7A3CEDE0FEB88A06.

Foram integradas somente Main.java modificada e MainEntityRollbackCommand.java
nova, com os bytes formatados05FAC452… eAFDA1DB1…. O comando entity-rollback
mantém três perfis fixos:COLETAS6908,MFT6399,COT6906. O caminho observed-staging
das quatro P3 foi preservado. A nova porta exige API externa V6 e seus controles;
não representa implantação ou autorização física por si.

O primeiro aplicador recusou antes de alterar arquivos porque conferia slot
vazio após o caller ter reservado o próprio slot. A correção conferiu o slot
próprio, aplicou as duas imagens e então recusou uma contagem incorreta do Root:
1778 era o índice misto de arquivos, não uma contagem de arquivos Java. Ambos os
Original1 históricos foram preservados, assim como o backup da Main anterior.
Não houve reaplicação nem desfazimento para fabricar sucesso.

A reconciliação independente somente de leitura fechou Original0 em61.741ms,
pico61.153.280bytes:1777 arquivos indexados anteriores iguais, Main substituída e
Command acrescentado;1779 arquivos mistos, dos quais1483 Java. A mutação passada
continua registrada como Original1. Prova4477EE8EE199458A55D9EC3D9CBBB832DCA32A033D061BEBF494609A68656C2E;
coorteD67C9426C5B7D4248FDBC39150AD589D130B19E9AE57479C144E52644254A6E9;
Original888526A2B2DFE120F2D4D23F72086544525A9EF4A17FBADB2092DC0B504B6675.

O sucessor físico do Core e da entrada V6 foi preparado com pin literal no novo
JAR e verificação da Main.run genuína, identidade de classes e origem do driver.
Manifesto8845744756AE191668E6E96D167F8B0F605A63B7013A15B48A3C86FD5E60287A;
CoreSeal44AE1A2E058A2C93D8729FFD7A9C094A8D46E5401B659CB41DD46CA27B7EBA87.
A primeira execução dos oito testes novos compilou os dois grupos sem erro e
fechou com7PASS/1FAIL. Os três casos Main→perfil→recusa por CODE passaram. Falhou
uma asserção na linha82 do teste de contexto: confundia o domínio Main com a
versão compartilhada do encoder de fingerprints. O produtor mantém a versão
source-contract-semantics-v1 e inclui o domínio Main no material do SHA. O autor
prepara correção somente do teste, com contraprova do domínio, sem alterar as
cinco fontes de runtime ou o CoreSeal. Original1 conhecido,21.127ms,
341.831.680bytes, sem timeout. As regressões legadas81/4/4 não chegaram a ser
executadas nessa ocorrência; não declarar89 ou herdar322/3 anteriores. Não
reaproveitar CODE, janela, captura ou admissão históricos.

O mapa AST foi atualizado e adotado com as duas fontes finais:46.158 nós e
120.665 relações, Original0,273.731ms e1.320.407.040bytes sob teto2GiB, sem LLM
ou clustering. ProvaA200548A53E4B72306ADF9717E5CB7CC00CD3A666A8833FD60A3663BAB88725D;
Original1BCC2A13EF7900FD9579C1639DFB418F5A719EDC4E9F72A1CAE56172B5E3B1E3.
A primeira ocorrência AST recusou antes da extração porque o caller forneceu
SHA maiúsculo e o helper comparava hexdigest minúsculo literalmente. O sucessor
normalizou somente a representação dos dois SHA e usou nova saída; falha e
candidato anteriores ficaram preservados. Isso não era alteração dos bytes.
HTML/relatório semântico históricos ficaram intactos; o mapa não qualifica
execução física. Revisão CoreMain5A8BDED9635A57B335235F8E30F6508901DBBF895A3A99C21788CF37BFD162B1
permite somente os testes fechados preparados.

## Usuários: falha reproduzida antes de nova consulta

O publisher privado grava e confere cada página antes da próxima consulta,
com criação nova, force/readback e renomeação atômica. Limites propostos:
128MiB de arquivos incluindo cauda parcial e fechamento; retenção global64MiB
mantida. Cem mil linhas são um teto, não capacidade garantida. Prefixo salvo,
fim observado da Source e fechamento Original são evidências distintas.

A revisão da primeira proposta encontrou finish aceitando outra Capture com
as mesmas contagens. A proposta6160B8656884EC15986A76C33C7A392DF9F14F01B69EFCD20DE7F2E07BBAA34A
vinculou o mesmo Capture e a mesma publicação e conferiu a última página. A
contraprova estrangeira foi incluída sem alterar os nove testes anteriores.

A execução real da nova suíte encontrou12/iniciou12:os nove testes anteriores
passaram, os três grupos novos de publicação falharam. Compilação0, Original1
conhecido,13.048ms e508.407.808bytes; sem timeout, HTTP ou SQL. Relatório
F0DBE618F3DA91B5292DB206E235C101AED234C22B2DEB5EC08338482E3632A1.
Essa proposta não está qualificada e não pode liberar uma leitura real.

O diagnóstico seguinte reutilizou as mesmas39 classes compiladas e selecionou
somente os três métodos falhos. Os três chegaram ao fechamento do publisher e
falharam em finish. O diagnóstico
reteve somente classes de exceção e arquivos/linhas, sem mensagens ou valores;
Original1 é o resultado real, não aprovação do código. Manifesto diagnóstico
DD97362C329D6FE5CA52EA2820AFCDA100308EE9A14182C48FC31A369808ED0C.
Fechamento diagnósticoDEBFEC57CEF7878C174A538727FF9E80B410E368AC6D22D4C2C38A21C1828517,
8.658ms/514.338.816bytes; relatório18A6AB4283979DCFADDA75EEA945A63B56110B84FB4A0D27FD06990D5C319B81.
O caso técnico mínimo de serialização numérica foi executado, Original0,
5.526ms/326.549.504bytes:dois LongNode escritos voltam como IntNode com o mesmo
valor e os mesmos bytes; a igualdade direta das árvores antigas resulta false.
Normalizar a árvore esperada pelo mesmo parser estrito preserva a igualdade do
documento. Quatro contraprovas recusaram inteiro alterado, decimal alterado,
campo ausente e booleano alterado. Nenhum publisher/kernel foi recompilado ou
qualificado pelo caso mínimo. Prova7ECE650657A1BEE88577CAC527FA1F2270552B06821AC312BD6D088170F7FC26;
relatório0787133640933FDDFEA6A20C350C8B088E8AD7D8E9C629BFD305314CB945DA54;
OriginalC81BF73886B340FFA11FED7079E9BA8BE336AA3418FAF613D08D00800362F857.
A correção mínima do fechamento será uma proposta separada e exigirá nova
qualificação12 real; a proposta6160 continua com o resultado9PASS/3FAIL.

A entrada Java e o controlador para uma campanha nova estão concretos em
proposta6A9EF938EC9158F69814E60EB701FDAC7D099BE0A431F6508AF6017532DC5421,
ainda sem admissão ou execução. Exigem publisher final com12 testes reais
passados/Original0 e compilação própria da entrada. Não retomar o V3 perdido,
usar6160 falho ou afirmar EOF por prefixo salvo. O histórico54540 observado no
V3 permanece sem fim ou cursor recuperável comprovado.

## Pendências externas preservadas

Os18 SQL/quatro grupos e o grupo Raster separado de7 DDL/5 alterações continuam
sem execução. As duas perguntas de exceção específica aguardam resposta:
AGENTS mantém a proibição permanente de DDL/grants/Flyway. A sessão ESL também
aguarda autenticação para confronto exclusivamente de consulta. Não houve
logout, alteração de negócio ou fechamento da aba original.

Permanecem válidas as provas privadas de Coletas1, Manifestos315 e Cotações287,
fonte→banco→readback sem diferenças nos campos efetivamente comparados, com
rollback. Raster108 viagens/355 paradas tem códigos/placas e previsões de paradas
conferidos contra Excel até o minuto; não é paridade integral ou prova de mesmo
recorte para as636 linhas dos dois Excel. Fretes Data Export6389 permanece
distinto de Fretes GraphQL; Localizador8656 permanece distinto de Raster.

Etapas3/4, gravação permanente e comparação externa integral continuam abertas.
SQL16.0.1000.6, contas, permissões, V1, PM2 e produção foram preservados.

## Orientação atual: V1 ativado pelo usuário

O usuário informou que ativou o V1 para a atualização diária. Preservar o V1 e
continuar código, revisão, testes locais e conferências de capturas existentes.
Novas consultas Source do V2 ficam aguardando a conclusão do V1 para evitar
concorrência no mesmo limite da API. Isso não pausa o objetivo inteiro nem
autoriza parar processos. A leitura atual de memória tinha6963MiB livres/70%
de comprometimento; oito JVMs foram observadas, sem encerramento ou inferência
de dono pelo nome. A seleção histórica de cinco JVMs não é a coorte atual.
Nova campanha Source exigirá memória/coorte/admissão então atuais e sua reserva
própria; não inferir conclusão do V1 somente pela passagem do tempo.
