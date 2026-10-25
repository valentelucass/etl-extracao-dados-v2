# Checkpoint 0539 — retorno ESL sobre Coletas e limites de chamadas

23/10/2026. O usuário encaminhou a resposta da ESL e uma imagem Postman neste chat. Unidade de leitura/avaliação/documentação; nenhuma chamada à API, SQL ou provider, alteração de configuração, transporte, credenciais, V1 ou PM2.

## Evidência recebida

- A ESL declara que consultou três páginas normalmente. Isso é relato do fornecedor; não recebemos os três recibos individuais.
- A imagem mostra GET `/api/analytics/reports/6908/data`, correspondente a Coletas, com resposta HTTP200; campos técnicos visíveis: `picks.request_date = 2026-10-15 - 2026-10-15`, `page = "3"` e `per = "100"`. A aba Body está selecionada como raw/JSON. O início do corpo não aparece integralmente; não reconstruir silenciosamente sua estrutura completa ou o request efetivo.
- A data da chamada Postman, o caller/tenant/token usados, os headers completos e a terminalidade da paginação não são demonstrados pela imagem. 23/10 é a data de recebimento neste chat.
- Nenhum ID ou valor de negócio da resposta foi transcrito/copiado para o repositório. Imagem original fornecida pelo usuário permanece no anexo/arquivo temporário, sem cópia adicional: `C:/Users/suporte/AppData/Local/Temp/codex-clipboard-999d6cba-379b-448c-b290-7d89e8dc975e.png`.
- Sobre429, a ESL informa duas causas possíveis: intervalo menor que2segundos entre requisições; ou chamadas superiores ao pacote de APIs contratado. Não informa neste retorno saldo, bucket, janela de apuração, escopo do limite ou a causa do evento histórico específico.

## Confronto com o diagnóstico executado de21/10

Checkpoint0538 e evidência privada `diagnostico-esl-20261021T190624Z/consolidated-results.json`:19HTTP, nove INFO200, oito DATA200 e dois DATA422 para Fretes6389. Coletas6908 DATA200 com uma linha; nenhum429 observado; intervalo próprio mínimo3s. Esse estado é uma observação de21/10, não um teste atual de23/10.

Portanto:
1. O retorno ESL é compatível com o sucesso de Coletas observado por nós; não comprova nem explica Fretes6389.
2. O erro422 permanece sem corpo de erro retido nem categoria/causa comprovada. Não o atribuir a429, quota, data, fornecedor ou processamento SQL por inferência.
3. Postman exibe GET com rawJSON no corpo e página3/per100; o diagnóstico próprio transmitiu GET_WITH_QUERY sem body, página1/per1. `HttpDataExportGateway.java:284–305` trata esses transportes separadamente. A diferença não é, sozinha, causa do422 e não autoriza mudar o transporte global.
4. A orientação de2s deve ser considerada no planejamento efetivo das chamadas; nosso espaçamento próprio de3s não prova exclusividade global nem saldo vigente da franquia. V1/V2 usavam a mesma base/token no diagnóstico21/10; não presumir franquias isoladas ou ausência de concorrentes.
5. Três páginas relatadas/página3 visível não provam captura integral, terminalidade, snapshot, paridade, processamento ou gravação na V2.

## Estado e próxima ação concreta

Solicitar à ESL a reprodução de **Fretes6389**, com o endpoint, os filtros e os horários já preservados em `resposta-para-esl.txt` e nos ledgers de0538. Diferenciar os422 de21/10 às16:09:41 e16:13:18BRT do429 histórico de outro evento.

Podem prosseguir preparos/qualificações locais elegíveis, conforme autorização anterior. Operação Main/captura/SQL depende de sua admissão própria: este retorno não emite reserva, não renova unidades consumidas, não esclarece MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED nem concede retenção C78. A validação de processamento e gravação V2 de ponta a ponta continua distinta da consulta HTTP.

Preservar versão SQL16.0.1000.6, credenciais/identidades/contas/ACL, serviços/listeners/trust, V1/PM2/produção, históricos e evidências consumidas. Sem aceite novo de extração completa, paridade, retenção ou cutover.
