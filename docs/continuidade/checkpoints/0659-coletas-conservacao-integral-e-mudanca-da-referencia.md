# 0659 — Coletas: conservação dos valores, referência alterada e comparador corrigido

Em03/11/2026, o usuário definiu uma entidade por vez: Coletas6908 até100% em três períodos distintos, com a mesma versão final das correções. Conferir todos os registros, colunas, valores, totais, multiplicidades e itens faltantes/adicionais; preservar campos extras. V1 foi informado pausado e deve permanecer assim. Outras entidades suspensas. Data Export para entidades; GraphQL somente Usuários.

## Resultado real dos arquivos fechados

O diagnóstico02 releu integralmente o confronto aceito de Source12 contra Excel29:40.199registros lógicos/92.854.448bytes e31colunas. Comparou, pela proveniência da mesma ocorrência capturada,48.391células em1561ocorrências/1155raízes: zero mudanças de valor, tipo ou presença entre RAW e mapped. O relatório nativo completo possui1561joins/4683arestas/3122diferenças; todas essas diferenças são assinaturas de objetos na raiz, sem folhas ou caminhos de ausência alterados. A serialização ordenou os membros JSON, sem mudar os valores. Isto comprova conservação interna das31projeções, não paridade externa nem nova qualificação de todos os extras.

A comparação completa anterior contra Excel28 tinha uma raiz com diferença exata em Volumes; a referência Excel29 tem12. Ambas usam a mesma RAW imutável. A diferença agregada dos dois Excel é333volumes: corresponde a333dos335 da diferença posterior, enquanto os2iniciais continuam sem causa comprovada. A causa de negócio da mudança entre os Excel ainda não foi demonstrada. O timestamp da própria coleta não pode ser usado sozinho para explicar mudanças de agregados de documentos relacionados. As12raízes e suas multiplicidades estão preservadas em CSV privado, sem pareamento arbitrário de duplicatas.

Diagnóstico02: Original0/27.212ms/pico204.824.576B; Python24.639ms. Source/HTTP/SQL/Java0. Fechamentos, EOF, inputs e restauração exata1D1B/701862B/ACL B4/slot0 aceitos. Falha01 de codecASCII foi preservada;02 corrigiu apenas a representação do autocheck, sem alterar valores capturados.

## Correção mínima do comparador

O DRAFT privado03 acrescenta um eixo de igualdade dos valores que ignora somente a ordem dos membros únicos de objetos JSON; o eixo estrito permanece. Arrays, multiplicidades, léxicos numéricos, Unicode, folhas alteradas, ausência e membros duplicados não são escondidos. Acrescenta uma regra de decimal exato restrita aos três campos declarados pelo INFO e vinculados independentemente, permitindo strings numéricas somente no ladoSource. Não arredonda nem usa tolerância. As aprovações reais dos seis subjects das duas comparações ainda precisam ser emitidas; testes sintéticos não as substituem.

Root revisou o delta e executou32casos sintéticos, todosPASS: Original0/2754ms/pico77.762.560B, Python217ms. Inclui igualdade de objetos reordenados, diferenças reais, arrays, duplicatas, NULL/ABSENT, decimais com resíduos mínimos, valores cruzados com totais iguais e integração dos corpos reais de totalização. Cinco módulos compilados sintaticamente. ZeroCorpus/Source/HTTP/SQL/Java. Não é adoção canônica nem nova comparação integral real. A primeira unidade01 terminou fechada com erro ao publicar o resultado porque a pasta de saída ainda não existia; sucessora02 criou apenas essa pasta exata dentro do worker protegido, mantendo comparador e32testes intactos. Ambas encerradas sem replay ou renovação automática.

## Limite externo e próximo trabalho

STOP429 de30/10 continua vigente. A consulta sanitizada dos últimos64KiB do log existente V1 encontrou18respostas200DataExport entre11:32:49 e11:33:26 de03/11, mas os eventos não vinculam host/template/credencial efetiva para aceitar a liberação no callerV2. Não converter sucesso genérico, mtime ou pausaV1 em saldo de quota. Nenhuma chamada de teste, retry, fallback ou troca de identidade realizada. Preservar piso3s após completion, serialização, timeout30s e parada no primeiro erro. Chrome voltou a responder e a sessãoDataExport autenticada foi confirmada; a configuração6908 foi consultada sem salvar ou exportar. OriginalTMS preservada.

Coletas permanece0/3. Próximo trabalho interno: qualificar as regras específicas de representação, incluindo estilo/época por célula das datas civis e evidência de ação/nulos; adotar os subjects decimais apenas após revisão e executar um confronto integral dos arquivos fechados com os bytes corrigidos. Para as três rodadas externas, obter extração e Excel do mesmo período em instantes próximos, depois que existir evidência aplicável de liberação da origem. Não avançar a outra entidade antes dos três100%.

## Provas privadas

Raizprivada: `C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01\supervisor-team0649-01`.

- `root-acceptance-coletas-causal02-observations0658-01.json`,4622B,SHA25679601C26737B5613EB08E703FEAEBC7C857011A69EE17A5D7656B6BEADC6D815.
- `root-coletas-reference-change0659-01.json`,8756B,SHA25637E78B163B8E3900A062AF69D4045711E87C1A256142A5819A36D31CF12E6263.
- `root-acceptance-coletas-comparator03-synthetic32-0659-01.json`,2421B,SHA2568EF3262C354ADF6FC0D3CDDA13FA195B8CA9361171F5460AABBF17271146F7D8.
- DRAFT03 em `etl-v2-correcoes-extracao/outputs/coletas-comparator-semantic03/index.json`,SHA2568F47814C040109184DC9AD098A36C74A6470D413F6174E2534E54D80D88CA452.

Root01a17ee4-5f83-7051-9cec-ffc8e16642fe continua único executor pesado. O chat de correções01a18d09-ebf2-7ba0-b1ab-a056b1823888 prepara somente código/metadados Coletas, sem execução pesada. Não houve alteração SQL16, PM2/V1, contas, credenciais, produção ou datasets de negócio.
