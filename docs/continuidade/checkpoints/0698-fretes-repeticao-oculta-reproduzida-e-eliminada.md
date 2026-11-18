# 0698 — Repetição oculta reproduzida e eliminada em teste controlado

Foi encontrado um defeito real de contagem física sob falha de conexão. No teste local, o coletor chamou o cliente duas vezes, incluindo INFO, mas o servidor recebeu três requisições quando a conexão foi fechada antes dos cabeçalhos da segunda resposta. A repetição adicional ocorreu dentro do cliente HTTP do JDK, fora do contador do adaptador. Isso é evidência de um defeito no comportamento sob desconexão; não demonstra que esse defeito causou os 422 recebidos da ESL.

O diagnóstico usou o coletor, porta, executor e leitor de corpo reais, com servidor HTTP em 127.0.0.1. O adaptador de teste encaminhou apenas o host sintético para loopback; o gate de autorização era simulado. Nenhuma chamada à ESL, nenhuma credencial real, nenhum TLS ou Native real foi exercitado.

## Prova antes e depois

O teste A/B conservou o mesmo executável Java 17.0.20.1, as mesmas classes do produto e os mesmos bytes da fixture. A única variável foi a opção de inicialização `-Djdk.httpclient.redirects.retrylimit=1`.

| Cenário | Antes | Depois |
| --- | --- | --- |
| Conexão encerrada antes dos cabeçalhos | Cliente 2 / servidor 3; teste reprovado | Cliente 2 / servidor 2; parada correta |
| Dois 422 exatos seguidos, depois 200 | Cliente 5 / servidor 5 | Cliente 5 / servidor 5; duas repetições após 10/20 s reais |
| Cabeçalhos lentos | Cliente 2 / servidor 2; parada | Cliente 2 / servidor 2; parada |
| Corpo que deixa de responder | Cliente 2 / servidor 2; parada | Cliente 2 / servidor 2; parada |
| Corpo interrompido com tamanho declarado maior | Não alcançado por falha anterior | Cliente 2 / servidor 2; prefixo conservado, sem EOF |
| HTTP 429 com Retry-After | Não alcançado nessa fixture | Cliente 2 / servidor 2; sem repetição |

Antes: Original 17400/1, 86.502 ms, pico de 335.876.096 bytes; Java do coletor local 17212/1, 60.732 ms. Resultado em `work\fretes-transport-diagnosis0697-run01`. Depois: Original 77040/0, 72.393 ms, pico de 231.866.368 bytes; Java 48356/0, 66.974 ms, stdin fechado e EOF das duas saídas. Os seis cenários passaram. Resultado em `work\fretes-hidden-retry-ab0697-run01`. Ambos os caminhos pertencem ao workspace do supervisor, `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao`.

A [documentação oficial do Java 17](https://docs.oracle.com/en/java/javase/17/core/java-networking.html) define a propriedade como limite de tentativas de envio, incluindo redirecionamento e falhas. O [código OpenJDK 17u, MultiExchange](https://github.com/openjdk/jdk17u/blob/master/src/java.net.http/share/classes/jdk/internal/net/http/MultiExchange.java) verifica o máximo antes da próxima tentativa. Desabilitar somente retry de conexão não cobre a exceção de conexão expirada; o limite de uma tentativa é necessário para a contagem observada.

## Integração do candidato

O controlador V3 passa a opção somente à própria JVM filha; não altera variáveis globais, outras JVMs ou processos externos. O candidato RawPort C94D exige a opção antes de construir o cliente HTTP, recusando sua ausência. Não tenta aplicar a opção tardiamente após inicialização do cliente. Os limites continuam em 47 requisições totais, três segundos após cada resposta, request até 30 segundos, Source 210 segundos, Original 300 segundos/1 GiB. V2 permite até duas repetições por página e seis por unidade, após 10/20 segundos; 429 e resultados incertos continuam exigindo parada.

O controlador real passou em 14 casos com filhos Java reais e quadros sintéticos sob Original 38548/0, 25.964 ms, pico de 317.558.784 bytes. Casos: V1 zero/uma repetição; V2 zero/duas/seis; terceira repetição na página e sétima na unidade recusadas; parada sinalizada por 429; UNKNOWN; mensagem inválida; EOF precoce; excesso de stdout/stderr; travamento. Todos os processos e STDIO fecharam. O filho sintético confirmou a opção da JVM. Evidências em `work\fretes-controller-qa0697-run02`. O produtor de lease era stub: isso não qualifica Native ou o Main de produção.

A primeira tentativa desse harness falhou antes de iniciar o filho porque o Root selecionou o arquivo de helpers incompleto. Foi corrigida a seleção para RootFretes.CurrentLease, preservando o controlador e o shim; o resultado falho continua em run01. O shim tem o nome Main apenas para testar o protocolo e foi empacotado em JAR exclusivo de teste. É proibido colocá-lo no produto.

Um teste local anterior consumia o corpo com `ByteArrayInputStream.readAllBytes`, desviando do método que expõe a falha de corpo incompleto. Ele registrou parado, mas não lançou a exceção esperada. A fixture final passa a usar o mesmo laço limitado de leitura do coletor, mantendo todas as assertivas. O coletor real já comprovou a parada no teste integrado de corpo interrompido; não tratar a falha da fixture como prova de aceitação pelo coletor.

## Trabalho ainda em execução

O produto V2 completo, com a correção de igualdade entre política parcial e admissão assinada, está na qualificação final de 27 suítes Java. Índice Root 9FEFC5F0, conjunto de 16 famílias E22DC6ED. Ainda faltam resultado completo, testes Node/Native sintéticos, revisão final, fingerprint/vínculo do produto, adoção dos componentes, Native real atual e nova Source controlada. Nenhum candidato foi adotado por este checkpoint.

Estado canônico permanece 6607DCC9D46B57ECDE9A522A87F9631EEBD6FFF85B761E6A05F30F7BE371A803, com 18 pares completos e nove fatos parciais; slot pode ser ocupado temporariamente apenas pelo teste Root vigente. Fretes 0/3 semanas; Coletas 3/3. Os dois campos de custos da captura parcial nova coincidem nos 180 IDs/198 ocorrências, conforme checkpoint 0697. Aprovação completa exige captura integral e comparação real das 114 colunas e totais com Excel do mesmo intervalo.
