# Checkpoint0653 — Coletas: testes locais e correção de limites

## Identificação e objetivo

- Data: 28/10/2026. Anterior: docs/continuidade/checkpoints/0652-semana-completa-de-coletas-e-criterio-de-conteudo-integral.md, SHA256 `389899B202A8621AE4D457A373C1A86C8B065725FDE9B7AE0BF6CF292F5D7C93`.
- O usuário exige comparar uma semana inteira, cada linha, valor, coluna e total, incluindo o que faltar ou sobrar. Contagens isoladas e amostras não encerram esse critério.
- Prioridade: Coletas/Data Export6908, 19–25/10/2026, por Data da solicitação. Estado: EM_CORRECAO_LOCAL; captura Source da semana e comparação integral ainda não executadas. Nenhuma entidade integralmente validada.
- Critério vigente: `request-supervisor-weekly-coletas0652-02.json`, SHA256 `711E0F05E59BEEB2130AC6DD4E2C76B6E63D371573074C1CDBB875BC1216F0CA`.

## Autorização e limites

O pedido humano autoriza a extração e conferência, com outros chats trabalhando e este supervisor coordenando. Execução e qualificação é o único executor pesado delegado; Campos e regras e Conferência ESL preparam arquivos e revisam. Root faz coordenação, revisão, documentação e Git. Nove entidades usam Data Export; GraphQL somente Usuários.

Cada unidade executada abaixo teve aprovação Root concreta e foi encerrada. Nenhuma admite uma repetição automática, Source, novo CODE/janela, Native real, SQL ou DDL. A candidata segue release0, sem autoridade real. Preservados SQL16.0.1000.6, contas, permissões, certificados, TLS global, serviços, listeners, PM2, V1 parado pelo usuário, WMS e produção. DDL18SQL/Raster7 e proposta física Coletas continuam sem aprovação. Última Source real permanece MainManifestos0645.

Os tetos propostos da futura semana permanecem condicionais: Original300s/1GiB; Source210s; request30s; INFO1+DATA<=16, HTTP<=17, per100 raízes distintas, até10000 linhas físicas, resposta2MiB, captura56MiB+controller8MiB. Todo novo envio requer pending=buffered=inFlight=0 e raw=mapped. Chegar ao teto sem página vazia real é incompleto. Sem retry/redirect/fallback, extensão de cap ou entidade seguinte.

## Alterações e decisões

- ESL congelada: XLSX `31BC21FE4A7131063243375CBA142AE45BD8F1032A3730E607AFF179319AF77A`, 1526 linhas, 31 colunas, 1155 representações de ID e 371 ocorrências adicionais. Preservar também 46 ocorrências extras de linhas inteiras idênticas. Não deduplicar; isso ainda é inventário externo, sem paridade semanal.
- Compilação06 passou; inspeção/seal separado passou. Três JARs, 32 famílias/75 classes, classpath explícito de19 arquivos e três classes do pipeline original conferidos. Os JARs permanecem candidatos privados. Product `548102BBEAB5FF6CD088A97D93C59CF21FE692B609744E4DC322780BB32F0A52`; seal `AE83A5E7A2D688C37ABD31264B2577749A6DBD370586466A70823A29D5725360`. Falhas anteriores de compilação/manifesto foram preservadas.
- TEST46 executou a candidata selada: 44 passaram e2 falharam. Os dois positivos pelo Main genuíno com HTTP simulado passaram, incluindo1526 ocorrências/1155 raízes e página terminal vazia. Isso testa a conservação interna do cenário sintético, não os dados reais nem a paridade com ESL/SQL.
- Falha T1: `exact10000OccurrencesStillRequireTheRealEmptyTerminal`. A fixture concentra10000 objetos com seis escalares em uma página, 70001 nós, acima do runtimeDefaults50000. A recusa reteve raw e deixou a captura incompleta. Correção07 em preparo: distribuir a borda acumulada em páginas válidas e manter a contraprova de nodecap. Não elevar o cap nem remover o parser estrito.
- Falha T2: `timeoutDrainsPendingSubscriberAndLateTerminalsCannotWriteAfterClose`. Worker sintético permaneceu vivo após o join. O permit ungoverned da fixture retorna sempre500ms; o executor confia no permit. A revisão independente localizou prazo monotônico por tentativa/corpo no governor real escolhido pelo Main. Portanto a falha não demonstra um defeito de timeout do Main real. Correção mínima07 em avaliação: usar a implementação real do governor na fixture com política de teste delimitada, mantendo a prova de drenagem/cancelamento/prefixo/latecallbacks. A candidata de deadline adicional no executor, se autorada, permanece não adotada. Não aumentar o join nem substituir timeout por interrupção artificial; conferir Source210/Original300.
- PSmodels03 concluiu20 e falhou na leitura do próprio diário ativo: reader FileShare.Read conflita com o writer aberto. PSmodels04 introduziu exclusivamente um snapshot reader do diário próprio, com owner-thread/handle/path/flush/allocation/length/byte bound; writer e leitor genérico imutável permaneceram protegidos. Os mesmos24 modelos passaram. Scripts03 recebe a correção em sucessor separado, ainda sem qualificação real integrada.
- Leitor/bridge02 da Conferência ESL foi autorado: raw UTF8 e lexemas, publicação/EOF, tuplas técnicas, campos extras, ausência/null/vazio/tipos e joins ambíguos. Root leu o código e conferiu35 pins. Antes de executar: limitar as duas enumerações de diretório durante a leitura e propagar o esgotamento global de budget imediatamente. Sucessor03 em preparo; nenhum corpus real lido por esse leitor.
- As31 associações externas continuam PENDENTE. O catálogo do filtro distingue done=Finalizada e finished=Coletada; isso não comprova a associação/renderização da coluna. Ação finish e regras temporais continuam sem conversão aprovada. Sessão principal ESL expirou; pergunta de autenticação pendente, sem repetir. O trabalho local continua.
- S2: mapas de reuso direto do Main0645 e alternativa de produtor/relay no worker Original foram preparados, sem implementação ou transferência de qualificação. Posse efetiva do SourceMutex, serialização entre fases, coleta limitada, deadline3s e reconciliação após fechamento físico continuam abertos. Não promover modelos locais a evidência Native ou reutilizar claims/janelas0645. O resolver antigo fixo em Raster0637 não serve como LatestMainTyped atual.

## Execução e evidência

| Unidade | Resultado observado | Limites e fechamento |
| --- | --- | --- |
| Inspeção/seal06 | Original0,9288ms, pico105684992bytes,151 leases | Sem javac/JUnit/Source/SQL; história/ACL/slot restaurados. Prova `743006F7F5B84E1AF66FAE1706D73B85B3C15C65644A0BA8A36E54246022503A`. |
| TEST46 | Original1/JVM1;44PASS/2FAIL/0skipped,170633ms,pico266285056bytes | Sem timeout, javac, Source, SQL ou NativeCIM; PID/nascimento kernel do worker unidos ao recibo Original,65 leases e história/ACL/slot conferidos. Relatório `CD63B1DE5F5AB8C43961F68ED3DDA48A9E99583CDEA11DF05938CDB98E26292B`. |
| PSmodels04 | Original0/24PASS,9806ms,pico176721920bytes | Mesmos24 casos, sem Java/Source/SQL/NativeCIM ou retry;29 leases, fechamento e história/ACL/slot conferidos. |

Base privada das referências: `C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01\supervisor-team0649-01`. Provas e testemunhos completos ficam fora do Git; não publicar valores de negócio ou corpos de resposta.

- `response-coletas-weekly06-test46-run01.json`: `6C62F8C101708BC6CE2B11F2979585B769130246F81DB21758B422E7D115FC8D`.
- `response-coletas-ps-models04-run01.json`: `17CC29D16571D40770E9646188C8D78C15969F76AC5F130B9C0E7CF2D8933CE8`.
- `root-approval-coletas-weekly06-test46-0652-01.json`: `3F53911C28C43A47CAB587FDC6D964A132664E90B0131F25B34AB330D3683FAB`.
- `root-approval-coletas-psmodels04-0652-03.json`: `EED808BCF53EDC04389E9F8B1FF1CBB82D8960C9AA9DBE12DE1DDCB97C3F396C`.
- `review-supervisor-coletas-psmodels04-0652.json`: `BCF984BB2EE3278F148D1E5753688FEBF921B30D32528F6CAEF1821035BA07C6`.
- `review-supervisor-coletas-capture-adapter02-0652.json`: `EA4CB38E280B0D716A29D2240B53482349CBF5F93E8FA78D4E347924BDC25542`.

Sem efeito Source/SQL/DDL incerto desta unidade. As autorizações TEST46 e PS04 foram consumidas; não são saldo para repetir. Execução apenas prepara07. Campos faz revisão causal; ESL prepara leitor03. Não há aceites de entidade, carga permanente, UsersEOF, retenção completa ou paridade externa encerrados.

## Retomada imediata — até três ações

1. Revisar correção07 e revisão independente, congelar build/inspeção/testes focados com novos pins e decidir nova unidade protegida. Não repetir a candidata06 sem mudança causal.
2. Fechar a integração Source-only real (S2, Native atual, roles/birth/provider/closure/resolver) e leitor03 com qualificação local própria; obter evidência independente das31 associações. Nenhum CODE/janela/claim real emitido por este checkpoint.
3. Só então capturar a semana até EOF real e comparar Source→extraído→readback SQL e ESL: linhas completas por raiz qualificada, todas as31 colunas, campos extras, duplicatas e totais. Registrar diferenças e ambiguidades; sem pareamento por ordem ou semelhança. Só concluir quando o conteúdo inteiro estiver demonstrado.

Parar em deriva de pins/escopo, limite, não2xx/429, erro de conservação ou fechamento incerto. O checkpoint registra progresso verificável e falhas abertas; não substitui a extração e a comparação pedidas.
