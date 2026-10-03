# Checkpoint0426 — ajustes ETL qualificados; contrato físico classificado

## Identificação e objetivo

- Registro: 2026-10-03T16:58:18.5671425+00:00; anterior0425: 0425-hermes-read06-integrado-contrato-nao-comprovado.md, SHA759EBF6110ADF1008FFD61ACC5B565275A92021D2ADDE0F2B548616F20E566F4.
- Objetivo do usuário: realizar pequena extração completa pelo ETL. Estado: correções/qualificação offline concluídas, prova real BLOQUEADO_POR_INPUT; não houve amostra Java/JDBC real.
- Critérios: AGENTS/STATES, ADR0056/COL-SAMPLE-01; quatro builders existentes, Supervisor único editor compartilhado, Banco único executor SQL.

## Autorização e limites

- Pedido efetivo Hermes db1853e6aeeb de03/10: retomar correções/qualificação e nova leitura metadata-only delimitada no shadow. Prompt preservado na ponte local, sem callback Hermes/sessões novas.
- Banco localhost/ETL_SISTEMA_V2_SHADOW: duas conexões/12comandos/timeout10s/deadline60s, somente metadados, protegido loopback; gasto2/12 e6,052s, saldo0/0, ledger fechado. Sem DDL/DML/procedure/domínio/Flyway/grants/auth/TLS/listeners/retry.
- Fonte STOP429/teto10/saldo7 congelados; SAMPLE operacional cap2 não cria autorização. Produção/cutover/promoção/watermark/sweep proibidos neste escopo.

## Alterações e decisões

- Inventário anterior target/ajustes-extracao-20261003/initial-hashes.json e initial-git-status.txt; material preexistente preservado.
- Fontes altera DataExportHttpExecutor.java/teste: status terminal preservado na causa da exceção de Retry-After; Runtime altera Coletas6908SamplePreflight.java/SampleRunnerTest.java: reservationReference textual não vazio. ADR0056 e um hash do catálogoCI reconciliados; código Banco/domínio/pom/limiares unchanged.
- TLS já guardado antes JDBC; rejeitado patch redundante. Comparador não relaxa texto estrito; dez diferenças layout/comentários não dão autorização física. FK38 falsas diferenças resolvidas. Duas dependências e facetas restantes não comprovadas; bruto70 não é contagem de schema defeituoso.
- Limitação: logs privados iniciais Runtime parcialmente sobrescritos pelo launcher, XML red e green finais preservados. Falhas preflight01 e casing agregado preservadas. Histórico0424/FAIL01/manifests não reescritos.

## Execução e evidência

| Critério | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Defeito status terminal | Offline | red2fail/green7PASS, semHTTP | fontes/handoff.json e audit-receipt.json |
| Defeito reserva coercível | Offline | red CLI1fail/green27PASS | runtime/handoff.json e qualifier-green-02-receipt.json |
| Qualificação agregada atual | Offline snapshot | cleanverify exit0;2459/0/0/5 +7/0/0/0;19gates/295XMLs | qualificacao/integrated-receipt.json SHA1CD80379E36BC45AD845B9BEC9482F5CD0B78F09A369EAE0801C8B9FFC73B1D5; supervisor-quality-review.json SHADE404301BE09FAF70C48AB341CCFA0918967FC9C569A37D00BB862499E1DE683 |
| Inventário congelado | Offline | 4212inputs/source-snapshot drift0 no fechamento;13código matching | integrated-input-manifest.json SHAEB94B3B262BE59E7116ABAAF70ACE041143C1D805322945CF81704A3BA6386CD; integrated-freeze.json SHAF2286DFFA4E49F7AA21ECEC7F455BB05801130C106833636BD22D11998D13263 |
| Metadados causalmente classificados | SQL somente leitura |2con fechadas/12cmd/6,052s, zero escrita; FK38match/texto10layout/deps2unproved | banco/metadata02/contract-receipt.json SHA4ECE2C2BE3B402AE6410304CC02F3E3C92BF19066B61D7D24B811002B81BA0DF; ledger-close/post-readback/manifest/handoff |
| Revisão Regras | Offline readback |18bytes antigos conferidos, sem patch/novos testes | regras/handoff.json, histórico no instante da revisão |

Todos os paths de evidência relativa acima partem de target/ajustes-extracao-20261003/. Scanner4213candidatos/4212textos/1wrapper/0achados PASS. Aceite fechado somente qualificação offline e classificação metadata autorizada; P08/G01/parentes/checkboxes/cutover não fechados. Nenhum efeito próprio incerto ou conexão deixada aberta; nenhuma definição física persistida. Integração documental/grafo depois do gate é drift explícito, não repin histórico. Graphify/validação final: consultar recibo supervisor-final-integration.json após execução.

## Retomada imediata — até três ações

1. Responsável da origem fornece evidência concreta da liberação STOP429; somente então avaliar autorização/reserva de chamadas, sem sondagem automática.
2. Usuário/responsável define nova unidade metadata-only limitada para as duas dependências e facetas restantes: reserva2/12 atual consumida, nenhum SQL extra automático.
3. Banco qualifica Windows/JDBC, listeners exclusivamente loopback, TLS verificável e contrato físico antes de novo trial SAMPLE com PRE/POST, close/rollback e readback independente. SQL-auth diagnóstico não substitui guards.

Parada: não usar timer/saldo teórico/manifests para renovar autoridade, não mudar infraestrutura/schema. Conclusão final exige invocação Java real, página populada, staging/readback da mesma ocorrência e rollback/readback independente; testes sintéticos não satisfazem. STATES conserva autoridade. Controlador deve ficar waiting_user ao concluir, sem rotina/serviço/agendamento novo.