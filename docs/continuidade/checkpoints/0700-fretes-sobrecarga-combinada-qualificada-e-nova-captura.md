# 0700 — Falhas combinadas qualificadas; nova captura real iniciada

## Objetivo, autorização e limites

Continua o pedido do usuário de corrigir e testar sobrecarga, obter prova real e comparar os conteúdos de Fretes com o Excel. Autorizados correções, revisões e testes locais e extração controlada. Não há novo aceite da entidade: Fretes 0/3 semanas; Coletas 3/3. V1 pausado. Campos extras da API continuam permitidos.

Limites preservados: 47 operações HTTP incluindo INFO e repetições; intervalo de 3 s após resposta; request 30 s; Source 210 s; Original inalterado de 300 s/1 GiB. V2 opt-in com duas repetições por página, seis por unidade, esperas de 10/20 s, parada em 429/UNKNOWN/margem insuficiente. Sem mudar processos externos, serviços ou SQL de produção.

## Resultados reais da qualificação

| Camada | Execução observada | Resultado e limite da prova |
| --- | --- | --- |
| Quatro validadores PS V3, com PIDs vinculados | Original 64556/0; 23.707 ms; 173.846.528 bytes | 17 invocações sintéticas e quatro verificações reais da cadeia. Inclui falsos EOF/corpo/assinatura/contadores/PIDs e preserva UNKNOWN/null. Snapshot E39E2731. |
| Produto Java V3 | Original 68700/0; 187.960 ms; 371.920.896 bytes | Três compilações e 29 suítes, todos os filhos/STDIO fechados. Produto 3.529.205 bytes/DF1F1F6E; 16 famílias/38 classes. Mantém demais entradas/30 classes Coletas/manifesto. Inclui as 27 regressões anteriores, o ramo misto V3 e o coletor real com gate/port sintéticos. |
| Controlador com graça | Original 46576/0; 23.453 ms; 298.369.024 bytes | Dez cenários com PS real e filho Java Main de teste: recusa antes de INFO, depois de INFO, depois de 422, travamento, STOP/falha/EOF ausentes, quadro extra, nonce inválido e sinal bloqueado. Todos os filhos fechados. Snapshot 25B6A602. |
| Integração gate/coletor/controlador reais | Original 27320/0; 7.958 ms; 305.717.248 bytes | Duas leases sintéticas validadas; recusa antes da terceira; parser real rejeita; coletor real grava duas respostas/três tentativas e JSONL vazio, sem terceira abertura. Main, port, contexto e Native são de teste; zero HTTP. Snapshot 83C5CEFF. |
| Gravação bloqueada / watchdog externo | Original 42504/124/TimedOut; 60.148 ms; 206.974.976 bytes | Mesmos Original/JobRunner, parâmetro de teste 1 minuto; hook bloqueia por 90 s. Java 27660 já tinha exit1 e EOF antes do bloqueio. O parent manteve falha, UNKNOWN e slot retido. Root verificou depois ausência dos próprios PID/birth e restaurou somente seu slot por CAS. Prova C767B01F/2.968 bytes. Nenhum processo externo encerrado. |
| Fingerprint do produto efetivo | Original 51952/0; 10.096 ms; 65.024.000 bytes | 38 classes/16 famílias e 14 classes compartilhadas comparadas com JAR e integridade atuais. Join A1AAE04A; host 63B859B0. Não confundir fingerprint com prova da causa do timeout ESL. |

A janela curta de graça é de melhor esforço. Não existe promessa de que toda IO síncrona termine em cinco segundos; a contenção física de produção continua pelo Original de 300 s. O teste de watchdog mostrou o mecanismo com prazo menor e preservou a falha. Main de teste nunca foi incluído no produto.

A configuração executada do build conservou um ponteiro de catálogo anterior E22D, não usado pelo javac; a lista efetiva de 16 fontes é exatamente C070. Root preservou o arquivo original e vinculou ambos em `outputs/root-fretes-build-source-selection-correction0700-01.json`. O fingerprint exigiu lista efetiva, classes compiladas e JAR atuais. A revisão separada confirmou o mecanismo e não exigiu repetir um build intacto apenas pelo ponteiro antigo.

## Reconciliação e estado

Os dois efeitos INFO200/DATA422 da Source0698 foram reconciliados com o ramo V3 estrito e prova independente, sem inventar parcial/JSONL nem substituir worker UNKNOWN por zero. Todos os valores históricos foram preservados lexicalmente. Canônico base **ABC8C199696C363928334831F7612306B9A77EADFDF300D141DC6F630AF29FBD**, 719.775 bytes, ACL B4, Root0658; 18 pares completos e dez fatos parciais. Cadeia B81AB727: três passos e 11 reservas depois da última captura completa. Controle 1B72E723; typed CF6446D9; aceite D6D2991C. Não restaurar 6607 ou baselines anteriores.

Censo novo Original18452/0: 348 processos, query347ms, seis JVMs literais anteriores. A sétima JVM transitória já não estava presente; Root não a encerrou. Seleção explícita 2E029047; Native novo de dez sequências/duas barreiras qualificado, prova 290EA234 e Original A7B0B3DE. Nenhuma atualização automática de seleção. O conjunto inteiro continua conferido antes de cada chamada.

## Nova Source em andamento

Unidade `fretes-day19-real0700-01`, execução `6d161524-4467-467d-a8f5-523aae389bf0`, dia 19/10/2026, per30, produto DF1F, controlador com graça 414F91BD. Aceite de produto 19E7AC1B e adoção 243F986D. Dispatch C6473009; release FB170FB9; manifesto B7C5970D; dynamic 5459C44E; once A765452E. Root assinou a admissão somente depois do READY, mantendo signer3D64/policy5B9 e NodeE9BD fixados. Callback 12938EA9; Source03:33:59.9566145Z–03:37:29.9566145Z, Original até03:38:43.0573890Z.

Ao salvar este checkpoint, a Source está ativa e INFO200 já foi aceita. Ainda não existe resultado final nem nova comparação. O slot está ocupado exclusivamente por essa Source. Não iniciar outro Original/Java/Python/CIM enquanto ativa, nem reaproveitar os grants. Efeitos desta execução devem ser fechados e reconciliados antes da próxima.

## Até três próximas ações

1. Aguardar o fechamento conhecido da Source0700, conferir recibos físicos e auditar os efeitos reais; reconciliar sucesso ou parcial preservando incertezas.
2. Com captura completa, obter Excel recente do mesmo dia e comparar os 114 conteúdos, multiplicidades e totais; investigar diferenças sem tolerância/imputação. O último par anterior segue 112/114, duas colunas de custos com 11 IDs divergentes.
3. Depois de 100% diário comprovado, completar e validar as três semanas distintas. A integração sintética e a contenção de falhas não aprovam paridade real ESL por si sós.

Anterior: `0699-fretes-falhas-combinadas-auditadas.md`. Evidência completa em `C:/Users/suporte/Documents/Codex/2026-10-27/etl-v2-supervisor-finalizacao/work/` nos diretórios mencionados. Dificuldade técnica local não é bloqueio externo nem motivo para pedir novamente a autorização já existente.
