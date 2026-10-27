# 0650 — Correção de Coletas testada; catálogo Users/P3 local

27/10/2026. Continuação das três frentes organizadas em0649. O supervisor
permanece responsável por coordenação, revisão, documentação e Git. O chat
Execução e qualificação continua como único executor pesado; ESL controla o
navegador e Campos prepara/revisa candidatos. Não houve nova autorização de
Source, SQL ou DDL. A publicação0649 foi539efa997f2e1b9f3ca35ca774119a1e7d73a445,
confirmada em origin/main; recibo B/RootGitCommitPush0649.json, SHA256
BE2C844914DA2A2E90F06EF9835D1E593DD72D5B973F802DDDA44B9D8FABAA3F.

B = C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01.
T = B/supervisor-team0649-01. Os arquivos de negócio permanecem privados.

## Comparação externa de Coletas e revisão independente

O sucessor v3 corrigiu exclusivamente três vínculos do comparador: Cliente
usa pck_cor_nickname; Região da Coleta usa pck_prn_name; Filial Responsável
usa pck_prn_drt_nickname. O confronto de um registro do dia17/10/2026 passou
a27 células iguais e4 diferenças, de31 colunas. Isso não altera o v2 histórico.

Prova B/esl-coletas-comparison0649-03-root-control/qualification.json,
SHA256 C773B93FECA0A3DDAA40586CED2DEEB8CAB4AD4D7914EF6F42A3EAFC37ED5374.
Result.json SHA256720B68418F4206C6B39AD1FC22ED5C9690C05493EAAC605DA352B3F3F2353C8F.
Campos revisou os arquivos/pins sem executar de novo e não encontrou bloqueio
nesse escopo. A revisão é T/campos-regras/review-coletas-v3-campos.json,
SHA256 DBBC3FC626B2E4011ED4F49203C6E2BBDB93C69C02849A0750101A9FB59B255F.

Continuam diferentes created_at/updated_at na precisão de subsegundos e no
marcador de fuso, o rótulo finished=Finalizada versus Coletada e o código bruto
da ação versus seu rótulo no Excel. A ação bruta pck_pus_ore_trigger não é a
mesma função de occurrence_action derivada do status. Null/vazio no Excel é
uma convenção do comparador, não prova de fidelidade externa das presenças.

## Candidata de status: prova causal concluída, sem ativação

Campos preparou versão privada coletas-status-v2, alterando apenas o rótulo
de finished para Coletada, a versão e seu comentário. Raw/code, terminalidade,
tentativas e ação derivada permanecem iguais. O supervisor leu o patch e os
testes e verificou52 arestas/pins do pacote sucessor02. Nenhum arquivo Java,
fixture histórica ou migration canônica foi substituído.

Uma leitura durante atualizações do primeiro preparo encontrou referência
inconsistente. A observação e a tentativa foram preservadas; a cápsula02 foi
congelada com nomes próprios. Depois, a primeira compilação do executor falhou
porque um JSON foi selecionado por índice como baseline Java. Original1,
fechamento conhecido, slot liberado e bytes do ledger restaurados; zero JUnit.
Isso é falha de preparo, não teste vermelho do defeito.

O supervisor emitiu sucessor local explícito060908, com seleção por caminho
único/pin/package antes de javac, permitindo3 compilações adicionais. O total
ficou4 compilações, contando a falha preservada, e3 invocações JUnit; limites
Original1GiB/4096MiB/85%, serialização e slot CAS não foram relaxados.

O executor vinculou68 fontes reais, JDK17 e dependências locais, sem stubs.
As três unidades concluíram:

- Baseline:3 falhas de asserção esperadas, no rótulo das formas de finished.
- Candidata:31 testes novos e31 regressões passaram, sem falhas.
- Mutante que restaura apenas Finalizada, mantendo v2:3 falhas esperadas.

Original0,25.076ms,413.618.176bytes, sem timeout e com fechamento/slot/história
preservados. Javac usou -Xlint:all -Werror. Formatter/Maven completo não foram
executados. Logs, XMLs e códigos reais foram preservados.

Prova B/coletas-status-focused0649-03-root-control/qualification.json,
SHA256 A272437C6A231A95D2C790B14DDCEAE1FB60FD391DDE7FF42EC40AA9E2B40083;
result.json SHA2569CB69A879F843DE924CE390D5E2441435973FC09FAF2F0EC5D1DDB5BE7663689.
Root conferiu16 referências diretas da resposta/compilação/JUnit/XML e o
Original/fechamento sem reexecutar. Recibo T/review-supervisor-campos-causal0649.json,
SHA25665FA110FBB9D4EB84FD94B461DA3EEA2AE64FA6654C20E2C2C715CC20D753D79.

A prova é offline. O contrato V010/procedure aceita apenas v1 e o par
finished/Finalizada: a candidata não foi ativada nem está qualificada para SQL.
Campos prepara proposta física mínima de sucessor, mantendo V010 e registros
históricos. DDL não está aprovado. Um writer/relatório novo apenas para consumir
um rótulo de apresentação foi excluído do escopo pela supervisão; a proposta
aditiva de apresentação permanece candidata não ativada, sem testes executados.

## Users/P3: catálogo e adaptador privados

Execução preparou um escopo próprio para8 objetos,17 constraints,57 colunas e
33 parâmetros, com33 consultas fixas de metadados. Não fez alias ao owner antigo
Fretes/Localizador. Passaram20 casos com JDBC simulado, cobrindo limites,
visibilidade e fechamento de recursos; isso não é leitura de catálogo real.

Prova B/users-p3-catalog0649-01-root-control/qualification.json,
SHA256 A58C76CF5A2FF1F3EE05C4B07EEBCC7578B97A1CE6655BC9B9CB2C709AB76EA3;
result.json SHA2560E09587B05429D3CCB08213951773949B07DA57787B619D9EB4B1FD02BEF0BA8.
Original0/6.427ms/334.295.040bytes, sem SQL real. Revisão independente de arquivos
foi solicitada a Campos. Nada foi integrado ao código canônico; registro positivo
continua vazio, guard_pins_ready=0, proteção BEFORE/AFTER, engine e admissão física
continuam pendentes. Não confundir teste do adaptador com qualificação operacional.

## Frentes em andamento e limites

ESL segue nas nove entidades Data Export; GraphQL somente para Usuários.
Há novos arquivos baixados e comparações em preparo pelo executor. A frente ESL
relatou que CAP22 usou somente Emissão, enquanto o pedido histórico combinava
Emissão e Data criação; está preparando o recorte equivalente. Sinistros61vs60
segue como diferença a investigar por registro, sem causa ou lacuna atribuída.
Manifestos17/10 foi solicitado após confirmação dos filtros; a solicitação não
é prova de download ou confronto concluído. O usuário autoriza abrir outra aba
e fechar a que falhar, preservando sessão e sem salvar cadastros/templates.

Última Source real permanece MainManifestos0645. Retenção permanente, demais
bancos, UsersEOF/cursor, comparação integral,18SQL/quatrogrupos e Raster7DDL
continuam abertos. SQL16.0.1000.6, instalação, contas, permissões, serviços,
listeners, certificados/TLS, PM2/V1/WMS e produção não foram alterados.
O usuário informou V1 parado; nenhum reinício foi presumido.

Próximo trabalho: concluir/revisar propostas, comparar arquivos por chave/coluna
e recorte real, integrar somente unidades qualificadas e manter commit e push.
