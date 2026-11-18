# 0699 — Falhas combinadas de Fretes auditadas, sem novo aceite

## Identificação e objetivo

05/11/2026, continuação do checkpoint 0698. O usuário pediu testes de sobrecarga e prova definitiva, mantendo a comparação real de valores em três períodos. Fretes continua em 0/3 semanas; Coletas em 3/3. V1 pausado. Campos adicionais da API são permitidos. Não confundir comparação de conteúdo com contagem de registros ou colunas.

## Autorização e limites

Continua a autorização para corrigir, revisar, testar e executar extração controlada. Limites preservados: 47 HTTP incluindo INFO e repetições; intervalo mínimo de 3 s após resposta; request 30 s; Source 210 s; Original inalterado de 300 s/1 GiB. Perfil opt-in V2 permite duas repetições por página e seis por unidade, com esperas de 10/20 s. Parada em 429/UNKNOWN, falta de orçamento ou recusa Native. Sem alterar processos externos, serviços ou SQL de produção. Nenhuma Source nova até reconciliar os dois efeitos abaixo.

## Execução e evidência

- Produto V2 `989AAEC5` passou em três compilações e 27 suítes no mesmo Original 39160/0, 193.400 ms, pico 363.470.848 bytes. Todos os filhos e STDIO fecharam. Run: `work/fretes-retry-v2-java0697-run03`. Conjunto de 16 fontes E22DC6ED; snapshot 9FEFC5F0. Mantidos os demais arquivos, 30 classes Coletas e manifesto. A prova A/B anterior de repetição oculta permanece válida: duas chamadas do cliente/três no servidor antes, duas/duas mudando apenas `jdk.httpclient.redirects.retrylimit=1`. Isso não explica internamente o 422 da ESL.
- Node/política e sete cenários Native sintéticos passaram sob Original 14816/0, 10.388 ms, 78.589.952 bytes. Fixture de assinatura usou chaves sintéticas; não confundir com assinatura Root real. Fingerprint real sob Original 72316/0: 38 classes, 16 famílias e 14 vínculos compartilhados. Aceite de produto 9B2AC791, adoção de host 8B5D7035.
- Censo real anterior tinha seis JVMs; Native real de dez sequências/duas barreiras passou. A Source `fretes-day19-real0698-01`, execução `029c8b01-6799-4c43-aa62-b42be61293b7`, recebeu somente INFO 200 e DATA página 1 com timeout 422 exato. Uma repetição foi agendada, mas a proteção `FRETES_CURRENT_EXACT_SELECTED_COUNT` recusou o próximo lease antes da reserva/invocação. Não houve terceira chamada observada nem lease3.
- Source Original 34448/1 fechou em 48.315 ms, pico 469.401.600 bytes; Java 17220/-1 e STDIO fecharam. O controlador encerrou o próprio Java antes da escrita do parcial gerenciado. Foram preservados exatamente quatro arquivos: dois corpos e dois recibos em `outputs/fretes-day19-real0698-01-actual01/capture.pending`. Não existe parcial Java, JSONL ou captura completa. Não fabricar esses artefatos. Controller audit=false e worker retry=UNKNOWN/null devem permanecer assim.
- Auditoria independente real `work/fretes-raw-timeout-audit0699-run01`: Original 19100/0, 4.112 ms, 85.422.080 bytes. Reabriu e manteve os quatro arquivos, confirmou EOF e hashes, INFO200/erro422 exato de 67 bytes, journal com duas invocações completas, somente INFO aceita, agendamento sem reserva de repetição e ausência de lease3. Vinculou código admitido Native-antes-lease e Java serial-antes-open. Node 16940/0 verificou a assinatura Ed25519 existente em 253 ms, com STDIO fechado; nenhuma chave privada lida nem nova assinatura emitida. Relatório 7.608 bytes/DCEFF224. Original e slot fechados/restaurados.
- O censo posterior dessa auditoria encontrou sete JVMs. A adicional tem criação de kernel em 02:48:45.5618080Z, durante a Source. É evidência compatível com a mudança de contagem; não é snapshot exato do instante da recusa e não prova finalidade do processo. Nenhum processo externo foi encerrado.
- Os quatro helpers V3 de reconciliação INFO200/DATA422 passaram em 17 invocações sintéticas e quatro verificações reais do estado anterior: Original 21712/0, 26.258 ms, 211.812.352 bytes. Run `work/fretes-raw-timeout-ps-qa0699-run01`. A revisão posterior encontrou falta de vínculo entre os PIDs do recibo e as provas de fechamento; candidato corrigido isolado 9DE39ECD/snapshot E39E2731 está em nova qualificação. O primeiro PASS não autoriza adoção desse defeito.

## Estado e decisões

Canônico permanece 6607DCC9D46B57ECDE9A522A87F9631EEBD6FFF85B761E6A05F30F7BE371A803, 719.024 bytes, ACL B4, Root0658, 18 pares completos e nove fatos parciais canônicos. Há **dois efeitos HTTP adicionais conhecidos ainda pendentes**. Apenas o teste Root atual pode ocupar temporariamente o slot. Provas independentes CD70FECC e efeito F80DE767 foram preparados, mas não reconciliados. Não restaurar baselines anteriores nem reutilizar grants consumidos.

O candidato de encerramento com graça foi ampliado para falhas combinadas, STOP/frame/EOF ausentes, quadro extra, sinal e journal bloqueados. Ainda não está adotado. A janela de espera curta não garante duração de IO síncrona; o teto físico depende do watchdog externo do Original. Revisão e testes devem comprovar o fechamento ou conservar UNKNOWN e o slot, sem inventar sucesso. A fixture com coletor real e port/gate sintéticos não substitui uma prova integrada com gate e controlador reais.

O último par completo continua com 112/114 colunas iguais e diferenças nos custos de 11 IDs. A captura parcial posterior coincidiu nesses dois custos nos 180 IDs disponíveis; isso não aprova o dia nem a semana por transitividade.

## Retomada imediata

1. Fechar revisão e qualificação dos validadores V3 com os dois vínculos de PID; reconciliar exatamente os dois efeitos conhecidos, preservando valores históricos e UNKNOWN.
2. Qualificar produto Java V3 e encerramento combinado, incluindo contenção de travamento pelo watchdog. Nenhuma Source enquanto esses critérios aplicáveis estiverem pendentes.
3. Com censo/seleção/Native atuais e novo envelope limitado, obter captura completa, baixar Excel próximo no mesmo período e comparar os 114 conteúdos, multiplicidades e totais. Só então prosseguir às três semanas.

Não falta autorização do usuário para o trabalho local. Faltam provas executadas das correções pendentes; dificuldade local não é bloqueio externo.
