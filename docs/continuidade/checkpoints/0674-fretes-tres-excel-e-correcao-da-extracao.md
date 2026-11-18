# Fretes 6389: três Excel atuais e correções anteriores à captura

Atualizado em 04/11/2026. Coletas permanece com os três períodos integralmente aprovados no checkpoint0673. A entidade em trabalho agora é **Fretes, Data Export6389**. Fretes ainda tem **0/3 períodos comparados e aprovados**; as evidências abaixo não representam paridade com a API.

## Recortes e Excel oficiais

Os três arquivos foram gerados pela ESL autenticada no navegador interno, relatório “Relação de Fretes Detalhada”, filtro `freights[service_at]` (“Data do frete”). Os outros15 filtros foram observados vazios; cache “Não”. Nenhum template foi salvo ou alterado.

| Período | Arquivo ESL | Gerado em04/11 | Linhas informadas pela ESL | Colunas |
|---|---:|---|---:|---:|
|19–25/10/2026|1762485|09:51|4693|114|
|12–18/10/2026|1762509|09:57|3836|114|
|26/10–01/11/2026|1762535|10:04|5217|114|

Os downloads originais foram preservados e as cópias foram conferidas por bytes, SHA e identidade de arquivo antes/depois **dentro do Original**. Diretório: `C:/Users/suporte/Documents/Armazenamento-Projetos/dashboards-etl/fretes-three-weeks-fresh-excel0674-01/excel/`.

| Excel | Bytes | SHA256 |
|---|---:|---|
|fretes-2026-10-19-a-2026-10-25.xlsx|2630827|3BCC04D534771E8EC6D919C8253D052BAC42B3805AD8982D0FA6A886C07FC112|
|fretes-2026-10-12-a-2026-10-18.xlsx|2131242|502F8495BCCE8C49102EECC033F3737D00529476BA9D326AC71AECAE0B326AF5|
|fretes-2026-10-26-a-2026-11-01.xlsx|2855281|34E82AEE46B3175C1829C1D533265A27220EE6CCED3E56CD65AD3E2370CC6068|

Recibo fechado: `etl-v2-supervisor-finalizacao/work/fretes-three-excel-stage0674-run01/verification/final-result.json`. Original0/1794ms/pico59609088B, Source/HTTP/SQL0, arquivos de origem inalterados.

## Leitura integral dos Excel

A leitura do Excel antigo (4684×114) concluiu e foi aceita somente como inventário Native: aceite Root `0188D89E7BE9B0729591B27ABE85FD442572387B32E62BFA9D5531D3560F949F`. Preservou534090 posições,446877 células explícitas e87213 posições ausentes, todos os XML, strings, estilos, fórmulas, caches e lexemas. Original0/146158ms/pico76296192B.

Os leitores dos três **novos** arquivos têm unidades e admissões independentes. Os três fecharam com sucesso e foram aceitos pelo Root em outputs/root-acceptance-fretes-three-fresh-native0674-01.json (8006B/SHA2EFAB2D6D0474EC58EF56FB12379735F6CE3C2CA1F732DB2D5BC6465F7D27B45). Não filtrar novamente os Excel, não descartar ou deduplicar linhas. Os limites foram dimensionados pelas formas reais e a execução permanece serial, Original1GiB/300s, Python280s/leitor260s, saída512MiB/110arquivos.

Primeiro inventário novo: `outputs/native6389-19-25-fresh0674-actual01/inventory.json`,36471B/SHA7FA0168BE1F6522730AAF6AC891545AAD6FFA8E6145B7EEDF1FB9C5E68B80D55; COMPLETE13114E929D8B08CFC02F547BAA86690075DE797B03CB5A62DBCE062AD6599279. Segundo Original0/121513ms/pico75071488B. Terceiro5217linhas×114colunas,497174células explícitas/97678ausentes/594852posições, inventário36893B/SHA69A00892D291FE76F0D339F7369BD12EC7FEAE4A3A0585BC6131046A0ECFA519; Original0/166939ms/pico76894208B. Nenhum valor de Source foi comparado por esses leitores.

## Problemas encontrados e corrigidos antes da API

1. O Fretes anterior fixava19–25/10 e um executor antigo. O sucessor vincula o filtro à revisão imutável assinada: permite as três semanas pedidas ou um único dia contido nelas. Mantém INFO1+DATAaté16/17HTTP, per100, parada no primeiro erro e espera mínima3s **depois da resposta**.
2. A Main antiga de Fretes substituiria a entrada de Coletas. A Main mesclada preserva `coletas-weekly-source`, suas dependências e as demais rotas. A fonte anterior recompilada reproduziu exatamente os bytes das classes Main atuais antes do merge.
3. **Bug reproduzido:** `DataExportHttpBodyHandler` descartava corpos não200 na via observedRaw. O teste sintético429 falhou por corpo vazio. A correção retém o corpo limitado somente para a captura Fretes, mantendo a parada sem retry. As outras vias do handler foram preservadas. Não ocorreu429 real nestas unidades.

O primeiro teste falho está preservado em `work/fretes-build0674-run01/`. O sucessor02 passou **20 testes de escopo e11 de transporte/RAW**, incluindo429/503/302, espera após conclusão, ausência de EOF, UTF-8 inválido, duplicatas, extras e requisição incorreta. São testes locais sem sockets.

Aceite offline Root: `outputs/root-acceptance-fretes-offline-product0674-01.json`,4161B/SHA7CF097A8F43623859F3D7B8E9DD0250266E3F8E8B54B6539132B0C612C73A422.

Produto privado para preparo da próxima captura: `work/fretes-build0674-run02/fretes-and-coletas-product0674-unqualified.jar`,3469899B/SHA17294DEC0D9ADD51EBBFE3B60DEB2F96157F3EF28A06ED9F26A6EAC989216E07. Preservou1483 entradas da base,30 classes Coletas e o manifesto; classes de teste não entram no produto. Original0/18094ms/pico333545472B, cinco filhos próprios e stdio fechados. **Esse aceite não emite autoridade Source ou aceite dos dados.**

## Próxima captura e pendências reais

Ainda não houve nova consulta API de Fretes nesta etapa. O plano é começar por19/10, extrair partições contíguas sem elevar os limites e conservar todas as ocorrências da semana. As comparações devem verificar cada valor/coluna, associação, ausências, multiplicidade e totais. Campos adicionais da API são válidos e devem ser preservados.

Há outro defeito concreto a resolver no gate anterior: a procura de “último Source” só reconhece chaves numéricas e ignora a chave UUID do último Source Coletas. O sucessor estático03 ainda fixa16pares e Coletas; foi devolvido para correção, pois o segundo dia Fretes terá17pares e o último Source será Fretes. Não compilar um produto novo por dia nem inventar campos antigos para adaptar recibos.

O censo atual foi observado pelo Root sob Original, sem Source:324 processos,444ms, sete JVMs. Seis identidades antigas permanecem;60480/7856/java.exe/birth2026-11-04T13:02:55.4692250Z foi selecionada explicitamente,75284 saiu do conjunto JVM. Seleção Root: `outputs/root-exact-background-jvm-selection-fretes0674-01.json`,4904B/SHA35B6E8DBADAF3D67FC504F499DBD492E5AA2BF10DF58BC024AB1495B828829FF. Não declarar contrato “seis exatas” para esse conjunto de sete. São necessários caller, qualificação Native atual, contexto da última Source e assinatura/unidade/janela novos antes de qualquer HTTP. Nenhum processo ou serviço foi parado.

Estado canônico após as unidades fechadas:86A86EEA9CC6D35DDA082791C1743132CE795DDFD91EA5AC1CD0D50E2DC81DB8/707552B, ACLB4E214EB…, Root0658, vaga0 e16pares Source preservados. Root é o único executor pesado. V1 permanece pausado. SQL/produção e consumo real do dashboard não foram executados ou qualificados nesta etapa.

## Avanço posterior: Generic06 e Native atual fechados

Produto03 substitui02 apenas para o próximo preparo:3472004B/SHA6D8046B1AF20D3619F3926722BFB357CD2F14E8395B6C073B0702DB516AEA516. Passou20escopo+11RAW+18metadados genéricos=49casos, Original0/20138ms/pico376442880B e seis filhos conhecidos fechados. Aceite Root outputs/root-acceptance-fretes-offline-product0674-02.json,4654B/SHA71C925F6F1EC73F506B7076AA2B031846A73DF8A5FC76BFC074CDE5338EF7607. Generic06 resolve UUID/perfil explícito, preserva16..64pares sem ordenação numérica, e aceita somente o conjunto JVM explicitamente escolhido pelo Root, sem fingir seis.

Native01 parou antes de Source:60480 deixou o conjunto externo; o primeiro snapshot divergiu da seleção. Original1/4051ms/pico148586496B, produtor clean0/stdio encerrado e canônico86A86/B4/vaga0 restaurado. Não aprovar a primeira tentativa nem reutilizá-la. Censo02 sob Original observou322processos/458ms; Root selecionou pessoalmente as seis tuplas estáveis mais42568/7856/java.exe/2026-11-04T14:02:51.0261360Z. Nova seleção outputs/root-exact-background-jvm-selection-fretes0674-02.json,4955B/SHA5A4D3E256DF8C445DEE5120592D4DFA40C6124C12E15E80D727382DB2A43341B. Não houve parada de processo/serviço nem atualização automática da coorte.

Native02 passou10sequências distintas, duas barreiras com consulta nova, sete tuplas exatas, limites de Job reais e encerramento do produtor. Original0/10667ms/pico147525632B. Proof work/fretes-native-seven0674-run02/output/native-qualification.actual.json,4367B/SHA13BC855D90090D477CCCE70C044136C1094FD08CAB3B5159F60CDD2378AB7CA9; baseline8275B/SHA3520D61340DD588DAEA2B5B61674998DF4529A0E3D4A13076591809F2E4971AB; Original455B/SHA594D7511B7DCC5A7FEBA2816A7DB45E21312F018A9D6FC0407DA4ECB7315CBE3. Recibos históricos4/42 vinculados com revisão Root explícita; regras31..42 de seis não transferidas. Não é prova de imagem/hash de imagem nem de Source.

Revisão Root encontrou campo flags ausente em worker-birth.json, exigido pelo finalizador. Host07 acrescenta somente flags=$job.Flags, obtido do OwnJob real; Java/produto não recompilados por essa correção. Qualificação offline do host e vínculo das14classes do fingerprint ao produto atual em preparo. Fretes permanece0/3 comparações e0Source novo; próxima ação concreta é a unidade19/10 após fechamento destes pré-requisitos, depois confronto integral por semana. Estado86A86/707552B/B4/Root0658/vaga0/16pares mantido.