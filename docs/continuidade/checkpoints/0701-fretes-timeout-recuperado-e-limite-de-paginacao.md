# 0701 — Timeout recuperado; nova captura exige menos páginas

A Source real `fretes-day19-real0700-01`, execução `6d161524-4467-467d-a8f5-523aae389bf0`, encerrou parcial. A ESL devolveu um timeout 422 na página 20; o extrator esperou e recuperou a mesma página. Houve 26 requisições completas, 793 ocorrências e 720 IDs distintos. A próxima tentativa, número 27/página 25, foi interrompida antes da invocação HTTP porque a margem de 33 segundos já não cabia no orçamento restante. Nenhuma página terminal foi recebida. Não aprovar completude por contagem ou pelos 793 registros disponíveis.

O Original 22940 encerrou com código 1 em 200.093 ms, pico de 428.331.008 bytes; Java 20080/1 e STDIO fecharam. O controlador finalizou auditoria conhecida: 26 reservas e conclusões, uma repetição confirmada, zero desconhecidas. A tentativa 27 consta como `NOT_ATTEMPTED`, sem reserva, despacho ou status HTTP. O pai restaurou seu próprio estado e fechou; os 26 efeitos precisam de reconciliação antes de outra Source.

## Auditoria independente e revisão

Original 45192/0, 5.499 ms e pico de 98.639.872 bytes reabriu e manteve os 55 arquivos declarados sob controle: 26 corpos, 27 recibos e dois JSONL. Confirmou os 793 registros/720 IDs, erro de 67 bytes, repetição idêntica e ausência da chamada 27. Node 67804/0 verificou a assinatura Ed25519 já existente; todos os próprios processos e STDIO fecharam. A contagem de linhas JSONL não é prova de igualdade de todos os valores RAW→JSONL.

Prova: `work/fretes-partial-retry-audit0701-run01/actual-audit.json`, SHA-256 `FF20FAA2C047CD2178DC2D765E9D989368D19FDC9F807526EAE87C7CBE9A6739`.

Gerador e preparador de provas metadata passaram. Prepared `CE97C2CDA3ADE9B976C4FCC44AEE54153558F4B475D7A2CD502E2F7EB09E2873`, efeito `346FB8D4039D4F6993F59D00B66A98A5AA5530D5521EBC73F98432F36B7457FD`. A revisão independente reteve o reconciliador antes da execução: poderia substituir o canônico e falhar antes de publicar a cadeia e o controle. Correção em candidato novo: preparação antecipada, intenção recuperável, publicação verificável e retomada idempotente, sem repetir os 26 efeitos. Canônico ainda ABC8/B4/Root0658, 18 complete+10 partial, slot vazio; 26 HTTP conhecidos pendentes. Nenhuma nova Source até conclusão da reconciliação.

## Cobertura de falhas efetivamente exercitada

| Situação | Evidência e limite da conclusão |
| --- | --- |
| Reenvio oculto no Java após conexão interrompida | A/B real em loopback: duas chamadas contadas/três recebidas antes; duas/duas depois de `jdk.httpclient.redirects.retrylimit=1`. Original 77040/0; seis cenários reais de transporte passaram. |
| Timeout 422 repetido | Loopback com duas falhas, esperas reais de 10/20 s e sucesso; Source0700 recuperou um timeout real da ESL. Isso não elimina a causa interna remota. |
| Lentidão, corpo incompleto, corte antes dos cabeçalhos e 429 | Exercitados com cliente e executor reais em loopback. 429 encerra sem repetição. Não são provas de sobrecarga interna da ESL. |
| Falta de orçamento para mais uma página | Source0700 parou antes da chamada 27 e preservou parcial conhecida. Páginas de 30 itens exigiram operações demais para Source210 s. |
| Alteração da contagem Native durante extração | Dez casos do controlador passaram. Integração do gate, coletor e controlador reais confirmou recusa antes da terceira abertura e geração de parcial; Main, port, contexto e Native eram sintéticos. |
| Saída inválida/excessiva, EOF ausente, stderr excessivo, travamento e limites de repetição | Regressão do controlador atual: Original 49268/0, 25.156 ms, pico do job 289.693.696 bytes; 14 casos e todos os filhos/STDIO fechados. Main e quadros sintéticos; não prova HTTP real. |
| Travamento ao gravar por 90 s | Mesmo Original/JobRunner, limite de teste de um minuto: interrupção em 60.148 ms/código124. Pai conservou UNKNOWN e slot; Root comprovou ausência dos próprios PIDs/births antes da restauração. Graça de 5 s é melhor esforço; limite externo de produção continua 300 s. |
| Regras Java e PowerShell | Produto DF1F: três compilações/29 suítes executáveis passaram. Quatro validadores PowerShell V3: 21 invocações passaram, incluindo vínculos de PIDs. Fingerprint atual conferiu 16 famílias/38 classes/14 compartilhadas. |

Não há promessa de conclusão sob qualquer falha ou duração ilimitada. A prova exigida é recuperar falhas transitórias quando houver orçamento e preservar uma falha explícita e auditável quando não houver; jamais promover parcial a completa.

## Próximas ações

1. Revisar e concluir a reconciliação recuperável dos 26 efeitos conhecidos.
2. Observar os processos atuais e qualificar Native sob o estado novo; executar o mesmo dia com `per=60`, já suportado e testado, mantendo 47 HTTP totais, intervalo mínimo de 3 s após resposta, request30 s, Source210 s e Original300 s/1 GiB. Reutilizar produto/host/fingerprint qualificados sem recompilar.
3. Auditar a captura completa e comparar cada valor das 114 colunas, multiplicidades e totais com Excel recente do mesmo filtro. A sessão do navegador interno está no login e a pergunta de autenticação continua pendente; a API e o trabalho local não dependem dessa resposta.

Fretes continua 0/3 semanas aprovadas. Último par completo: 112/114 colunas iguais, 11 IDs diferentes apenas nos dois campos de custos, 48/50 totais iguais; os custos das 198 ocorrências parciais posteriores coincidiram com o Excel, sem aprovação por transitividade. Coletas 3/3. V1 pausado. Campos adicionais da API são permitidos e não contam como divergência por si só. Nenhum serviço, processo externo ou SQL de produção alterado.

## Reconciliação concluída e retomada idempotente

O congelado Root E2A5E939, índice23126A65, publicou a transação após revisão estática. FctxControl/FctxFrontier e preservação lexical passaram antes do CAS e na recuperação. O comando Recover com intenção ED7466E5 encerrou0 e preservou exatamente os hashes de canônico, controle, cadeia, typed e committed; os26efeitos não foram duplicados. Não se trata de teste com interrupção física injetada.

Estado atual A987A224E8003FFB963736A1C7F270CE1983666F137A73B8A54BEF0B65645235/720581B/ACLB4,18complete11partial,slot0 e nenhumHTTPconhecidopendente. Aceite908522E8707109990AA74880FE6FC5546514F8A800B023E4A5084FEF8F78B9D8/2050B. Controle work/fretes-partial0701-transaction-v2/current-control.json 86929A18/30240B; typed94795F85/1880B; cadeia1386E315/19813B (4passos/37reservasadicionais). IntentED7466E5 ecommittedE2253698. Backup físico e snapshot anterior preservados; não restaurarABC8.

Censo novo Original64916/0/2321ms53559296B: enumeração completa agora7JVMs, seis inalteradas e36820/7856/java.exe/birth2026-11-05T03:48:15.3982690Z. Root selecionou explicitamente os sete tuples atuais, seleção5B7A297B/4364B, sem inferir aplicação/propósito e sem encerrar processo externo. NovoNative7 em andamento, manifest6E5ED97E/12760B/once9C140FA8/742B. Preparador de seis0702 nunca executado; o candidato de sete0702 não faz atualização automática.