# Checkpoint 0538 — diagnóstico HTTP atual dos nove Data Export

21/10/2026. Pedido direto do usuário neste chat: “preciso que teste” e “precisamos ver se TODaS as de dataexport tambem”. Essa instrução autorizou um NOVO diagnóstico HTTP limitado para responder à ESL, substituindo o standby somente para este teste. Não autoriza captura Main, SQL, retenção, release, retomada V1 ou repetição de unidades antigas.

## Execução observada

- Origem HTTPS existente rodogarcia.eslcloud.com.br, token API_DATAEXPORT_TOKEN selecionado do .env V2 pela última declaração não vazia. Comparação em memória com a configuração V1 confirmou mesma base e mesmo token; nenhum valor/digest de token foi emitido.
- Gate local Global\\ETL_V2_DATAEXPORT_READONLY_PROBE; chamadas próprias seriais; intervalo mínimo3s; uma página/per1 na janela15/10/2026; retry0/redirect0; conexão10s/request30s; teto10MiB por resposta e1000linhas físicas no resumo. Não existe prova de ausência global de consumidores; V1 observada WAITING_NEXT_CYCLE, não pausada.
- Reserva inicial max18. Coletas INFO200/DATA200; Fretes INFO200/DATA422 interrompeu essa unidade após4 chamadas. Sete unidades independentes, sem repetir as anteriores, consumiram mais14 chamadas; todas INFO200/DATA200. Continuação explicitamente reservada para os relatórios ainda não testados, com STOP compartilhado em429/auth/5xx/transporte/limite.
- Diferença de formato em Fretes identificada: o helper histórico usa horários, enquanto BusinessDateRange/contrato V2 usam somente datas. Unidade causal separada de UMA chamada DATA, sem INFO e sem repetir query idêntica, mudou somente esse filtro. Retorno422 novamente. Total consumido19 HTTP; nenhum429 observado. Não atribuir o422 a quota, aos horários ou a defeito do fornecedor sem a causa.
- Instantes reais: Coletas DATA21/10/2026 16:09:31 BRT; Fretes DATA16:09:41 e checagem somente datas16:13:18 BRT; demais sete concluídos até16:12:22 BRT. Timestamps UTC íntegros nos recibos.

| Relatório | ID | INFO | DATA | Linhas físicas recebidas |
|---|---:|---:|---:|---:|
| Coletas |6908|200|200|1|
| Fretes |6389|200|422|não disponíveis|
| Manifestos |6399|200|200|7|
| Cotações |6906|200|200|1|
| Localização |8656|200|200|1|
| Contas a Pagar |8636|200|200|1|
| Faturas por Cliente |4924|200|200|1|
| Inventário |10633|200|200|10|
| Sinistros |6392|200|200|1|

As múltiplas linhas físicas em per1 não são por si falha: podem existir expansões. O diagnóstico não validou identidade por entidade nem completude. Nenhum payload de negócio foi retido; apenas status/horários/contagens/request técnico.

## Evidência e limites

Pasta privada: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/diagnostico-esl-20261021T190624Z`. `reservation.json`, `http-ledger.jsonl`, `results.json`, `continuation-reservation.json`, `continuation-results.json`, unidades `template-*`, `fretes-date-only`, `consolidated-results.json` e `resposta-para-esl.txt`. Cada efeito foi precedido de reserva e seguido de recibo; originais consumidos preservados.

O primeiro SelfCheck local detectou enumeração PowerShell de array vazio; correção por atribuição direta do nó e ToArray no agregado. Os SelfChecks finais passaram com network_calls0 antes dos efeitos. Scripts de diagnóstico externos ao código de produção reutilizam somente as funções AST do probe e seu gate. Recibos contam respostas de rede reais, não SelfCheck nem fixture.

O helper sanitizado não reteve corpo do422. Endpoint/query/horário/status foram preservados para correlação pela ESL. Não criar mensagem textual do erro ou categoria interna retrospectiva.

V1 foi lida em logs reais de21/10: Coletas GraphQL1318/71páginas/COMPLETO, Data Export sete entidades/161páginas/COMPLETO e ciclo15:16:41–15:50:24 SUCCESS. Não houve start/stop/restart; a observação atual supera a fotografia antiga de V1 parada sem reescrever o passado. Volumes de persistência são os reportados nos logs, não SQL independente.

Nenhum SQL/TDS/JVM Main/provider de banco/commit/DDL/DML/alteração de credenciais/serviços/PM2/produção foi executado neste diagnóstico. Fonte HTTP isolada não qualifica composição Main. MainHTTP de16/10 continua UNKNOWN/MISSING_NOT_RECONSTRUCTED; todas as unidades anteriores continuam consumidas; C78 rollback-only e versão SQL16.0.1000.6 preservados. Cobertura/G05/paridade/retenção/extração completa mantêm seus gates. Nenhuma reserva futura emitida.

## Próximas ações

1. Encaminhar o resultado atual e o request Fretes6389 à ESL para explicar422; não alegar falha geral nem atual falha de Coletas.
2. Avaliar auditoria/resposta concreta do fornecedor, preservando requests consumidos e a distinção entre diagnóstico atual e HTTP histórico desconhecido.
3. Preparar eventual unidade futura conforme sua autoridade/gates próprios, sem tomar este teste como liberação automática de Main/captura ou quota ilimitada.

