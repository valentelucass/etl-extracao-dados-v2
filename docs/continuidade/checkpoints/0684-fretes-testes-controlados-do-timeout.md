# 0684 — Fretes: testes controlados do timeout

Em 04/11/2026, o usuário pediu investigar por testes, até separar o que provoca a falha da solução que permite concluir a extração. Root executou quatro diagnósticos novos e isolados. Todos usaram Data Export 6389, GET com JSON no corpo, 19/10/2026, página 1, per=30, o mesmo cliente cURL, a mesma conta/tenant e os mesmos limites. A única variação entre A e B foi a presença de `order_by: id asc`. Nenhum diagnóstico chamou a Main nem virou captura completa.

| Teste, horário de Brasília | Ordenação adicional | Resposta | Tempo HTTP | Resultado |
| --- | --- | --- | --- | --- |
| A1, 17:30 | ausente | 200 | 3,947 s | 30 linhas / 30 IDs |
| B1, 17:35 | id asc | 422 | 2,726 s | corpo completo de 67 bytes, timeout da ESL |
| A2, 17:37 | ausente | 200 | 2,437 s | 30 linhas / 30 IDs; mesmo corpo de A1 |
| B2, 17:38 | id asc | 200 | 2,429 s | 30 linhas / 30 IDs |

Todas as quatro respostas usaram HTTP/1.1 e todos os processos/fluxos encerraram com confirmação. A falha B1 tem exatamente o mesmo SHA do corpo de timeout das falhas anteriores: `6EB41D27C98AAA98F57BDF5F9B3968B91CA51F9B776F59305546E3B5D0D6336A`. O erro chegou antes de três segundos, muito antes do prazo de 30 segundos do cliente.

## O que foi demonstrado

- O erro pode ocorrer já na primeira página. O V2 processa uma resposta por vez; esse caso não foi causado por esperar muitos arquivos locais ou terminar toda a paginação.
- A consulta com `id asc` pode falhar e funcionar com os mesmos parâmetros. Remover a ordenação ainda não é uma correção causal comprovada.
- A amostra sem ordenação funcionou duas vezes, mas não demonstrou uma extração completa estável. O INFO retido declara `default_order=service_at`; isso não demonstra ordem total nem estabilidade entre páginas.
- cURL também recebeu o erro. Portanto, uma falha exclusiva do cliente Java não está demonstrada. A diferença de protocolo/transmissão precisa de medição própria.
- A origem interna do erro — consulta SQL, cache, fila ou outro limite do servidor — não é exposta por essa resposta. Não inventar uma causa interna nem prometer imunidade permanente.

## Evidência física e contabilidade

Aceite agregado: `outputs/root-fretes-timeout-controlled-evidence0684-01.json` no workspace Root, 9.775 bytes, SHA `C3E6604C656F403D0E357CA8CFBBA4B7CB6FFA1E18E562EF91A02B6CA6A7004C`. Liga os recibos, reservas e respostas de `work/fretes-causal-order0684-{live01,idcontrol01,noorder02,idcontrol02}`. Os RAW completos foram retidos no armazenamento privado; nenhum valor de negócio ou segredo foi publicado neste documento.

São quatro novos HTTP reservados/concluídos, quatro Source diagnósticas e quatro Original, SQL=0. Ledger explicitamente separado dos contadores da Main e dos pares completos. Cada unidade passou pela vaga exclusiva Root0658, pelo Original inalterado de 1 GiB/300 s, pelo censo atual das seis identidades JVM e pelo portão de memória; cURL próprio limitado a 35 s/128 MiB, resposta a 8 MiB, Source a 60 s, uma requisição por unidade, sem retry/fallback. Intervalo entre unidades superior a três segundos após conclusão. Nenhum 429 nestes quatro diagnósticos; isso não demonstra saldo de franquia.

Antes da rede, 11 fixtures puros passaram e o mesmo caminho real de processo/stdio foi qualificado com cURL `--version`, sem HTTP/token/CIM nessa fixture. Corrigida emissão acidental de `VoidTaskResult` pelo script de diagnóstico usando `[void]`; isso é um defeito do novo diagnóstico, não a causa do timeout remoto. O primeiro fechamento offline tinha apenas divergência de capitalização no nome do PID após restauração; foi reconciliado pelos recibos sem repetir efeitos.

Canônico permanece **B369E2D13A3E971B67D1852537F0896DAD47465432D2209816B5033C02E298DB**, 713.677 bytes, ACL B4, Root0658, vaga vazia. Mantidos 17 pares completos + 5 fatos parciais da Main. Os quatro diagnósticos não acrescentam pares nem alteram os cinco fatos. V1 permanece pausado.

## Continuação

Às 17:49, uma quinta consulta real usou Java 17 com o mesmo builder, headers, GET com corpo e `sendAsync` da rota Fretes, preservando a preferência de protocolo padrão. Recebeu **HTTP200, HTTP/1.1 efetivo, 30 linhas/30 IDs em 2.374 ms**; x-runtime=1.183969. O corpo completo de 114.672 bytes tem SHA `A8969368407DF65CB3810553CAE02DE9E609E256C7147C0A5638DFAE704227F8`, idêntico ao cURL B2. Não houve INFO prévio nesse novo cliente, portanto não se afirma reproduzir a reutilização da conexão da Main.

O Java próprio6988 encerrou0, 4.385 ms/136.933.376 bytes, stdin/stdout/stderr fechados. Original40376 encerrou0, 8.151 ms/207.814.656 bytes. Uma tentativa lógica foi reservada/concluída; o número de transmissões internas do JDK não foi observado e permanece `UNKNOWN`. Sem retry de aplicação. Os quatro cURL e esse Java são cinco operações Source diagnósticas, nunca cinco capturas Main.

Antes dessa consulta, um javac e uma suíte Java de 12 casos sintéticos passaram, sob Original58776/0/6.247 ms/300.441.600 bytes. Qualificaram corpo limitado, JSON/duplicatas, stdin, headers, retenção parcial e preferência dos dois protocolos; zero HTTP/token nos testes. Sete classes resultantes e três dependências Jackson foram pinadas no diagnóstico. Aceite da quinta consulta: `outputs/root-fretes-java-timeout-evidence0684-01.json`,3.604 bytes/SHA `CDA0F958DA57F0BFBF52C659E9191D80D0155B8972D87DE9A9B839EFBBB9B8BC`.

O resultado não justifica mudar ordenação, forçar protocolo ou aumentar o timeout. O próximo candidato é uma recuperação opt-in, explicitamente vinculada à admissão: somente 422 completo com o JSON técnico específico de timeout, RAW retido, mesma página/parâmetros, espera de dez segundos, no máximo uma repetição por página e duas por unidade, dentro dos mesmos 47 HTTP totais/Source210s/Original300s. Não repetir429, outro422, erro de portão ou transporte incerto; sem renovação de grant e sem ocultar falha final. **O candidato ainda não foi qualificado/adotado nem comprovado na extração completa.**

A solução de extração precisa conservar os dados/ocorrências, demonstrar terminal real, impedir lacunas/duplicação entre páginas e ser confrontada com o Excel. **Fretes permanece 0/3 semanas aprovadas; Coletas permanece 3/3.**
