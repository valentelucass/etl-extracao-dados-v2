# 0685 — Fretes: recuperação local testada; revisão impede adoção prematura

O diagnóstico0684 demonstrou que a mesma consulta pode retornar o timeout da ESL e depois200, sem mudança de parâmetros. A falha chegou em2,726s, antes do prazo cliente30s. Isso demonstra intermitência observável; a causa interna do servidor continua desconhecida. Nenhuma troca de ordenação/protocolo foi promovida como solução.

Foi implementado um candidato opt-in: repetir somente DATA422 com JSON técnico exato de timeout, corpo completo e retido; esperar10s após conclusão, no máximo uma repetição por página e duas por unidade. Mesma requisição, sem avanço prematuro de página;47HTTP totais incluindo INFO/repetições, Source210s/Original300s/1GiB preservados. Adicionado per60/47 explícito para reduzir o número de chamadas; per60/17 legado preservado. O candidato ainda não foi admitido para Source real.

## Verificações realizadas

- Snapshot Root de16fontes e3arquivos de testes: `outputs/fretes-retry-root-snapshot0685-01`, índice SHA0E1069D2.
- Primeira compilação parou somente por variável `JsonNode java` sombreando `java.nio.charset.StandardCharsets` em PartialBudget. Correção isolada usa import explícito, sem mudar a validação. Original3720/1/10.888ms/346.562.560B, filhos encerrados, Source/HTTP0 eB369restaurado.
- Segunda unidade compilou produto e testes e passou as15suítes legadas:66casos nomeados mais11suítes completas. O teste novo parou antes do primeiro cenário porque usava offset fixo como timezone da fonte. Fixture corrigida para IANA America/Sao_Paulo; relógio fictício permanece UTC. Original41384/1/70.077ms/372.166.656B, filhos encerrados, Source/HTTP0 eB369restaurado.
- Terceira unidade recompilou as mesmas16fontes e os testes, executou apenas a nova suíte integrada corrigida e compôs o JAR. Não repetiu as15suítes já aprovadas. Integração usa port/executor/governador reais e cliente HTTP falso, sem rede. Original18920/0/22.914ms/374.394.880B;2javac e1suíte, todos os processos/EOF encerrados.
- Produto de diagnóstico:3.510.098B/SHA `C9365365D17E19E35FC9813C59294890115276447ECAECAA0E3358D2884761A2`,37classes/16famílias. Preservadas1.483entradas anteriores,30classes de Coletas e manifesto; testes excluídos.
- O pai genérico da terceira unidade falhou **após restaurar o estado**, por esperar o campo `auditComplete` ausente no worker de build. O resultado real0 e a integridade foram reconciliados por metadados, sem repetir os testes ou fabricar sucesso do pai.

Progresso verificado em `outputs/root-fretes-retry-offline-progress0685-01.json` no workspace Root,4.388B/SHA `1AED1DFE570B0CD74DCE3D7F51873AD5A014E19716582AE0B6959B7A13C1E592`. Esse registro declara explicitamente `acceptedForSource=false` e `acceptedForFreshSourcePreparation=false`.

## Revisão independente solicitada pelo usuário

Criado e colocado na seção ETL V2 o chat de revisão01a1a869-7326-7750-b9d0-4bcf7d897578, usando6.1Terra. A revisão estática encontrou quatro lacunas que os testes existentes não cobriam:

1. O contador do parcial novo não corresponde ao campo exigido pela leitura de SOURCE_STOP, perdendo fatos já conhecidos.
2. Uma repetição despachada que falha sem EOF/RAW completo não é representada de modo reconciliável pelo ledger de respostas.
3. Falhas terminais posteriores ao HTTP não colocam o próprio port em STOP irreversível.
4. Recibos do host fixam `automaticRetry=false`, inclusive quando houve repetição.

O escritor corrige esses pontos em nova pasta `fretes-timeout422-retry0686-draft`, preservando0684congelado. Exigir fixtures de contagem lógica aceita, tentativa semRAW/prefixo/EOF, parada irreversível, duas respostas422 com bytes distintos preservados e flags de repetição derivadas de despacho comprovado. Depois revisar os novos pins e qualificar antes de Source real.

O chat escritor sofreu falha do serviço de IA em dois turnos; isso não é timeout da ESL nem efeito HTTP. Foi retomado com6.1Terra, preservando arquivos. Root permanece único executor pesado/API/navegador; demais chats somente estáticos.

## Estado preservado

Canônico **B369E2D13A3E971B67D1852537F0896DAD47465432D2209816B5033C02E298DB**,713.677B, ACLB4, Root0658, vaga vazia;17pares completos+5fatos parciais Main. As cinco consultas diagnósticas0684 são separadas; as qualificações0685 fizeram zero Source/HTTP/SQL. Nenhuma captura Main com recuperação foi executada. **Fretes0/3semanas; Coletas3/3; V1pausado.**
