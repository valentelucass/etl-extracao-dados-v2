# 0425 — Hermes wake01; read06 integrado sem contrato físico comprovado

Data local02/10/2026. Anterior: [0424](0424-sample-qualificado-offline-prova-real-pendente.md), SHA `8CB82966495655F977360DDB5830EE1E2A941544D1B121885902247F659629D7`.

## Objetivo, autoridade e estado

Objetivo original: tentar extração real completa de poucos registros com agentes Maestri/curl diagnóstico. Pedido atual: verificação periódica de continuidade autorizada, ler nota Hermes; contexto explícito mantém STOP429/gates SQL e **não autoriza acesso novo a fontes/SQL**. Esta unidade realizou apenas leitura/reconciliação de evidências existentes e integração documental.

Nota conectada `Hermes - Continuidade`: supervisor Codex, agentes existentes, busy-skip, máximo três acionamentos por checkpoint real; não resetar checkpoint sem progresso real nem ampliar autoridade por rotina. Nenhuma rotina nova, pausa/disable, serviço ou agente criado. Estado após integração: **WAITING_USER** para falta de unidade física nova e resolução dos holds; qualificação offline0424 permanece concluída, prova real não executada.

## Handoff existente e verificação executada

Prefixo: `target/pilot-etl-20261002/banco/read-06-sample-contract/`.

- `handoff.json`/`result.json`: READONLY_DIAGNOSTIC_COMPLETE_CONTRACT_NOT_PROVED, physicalReady=false. Resultado SHA `AAF698DB8FBD7E46F9EA9BAD29E4071F36E71E5D7F152985C1E40B36A9224071`.
- Codex conferiu o hash do resultado, quatro referências de evidência e **16 arquivos** do manifest. Nenhum helper, teste/comparador, API ou SQL executado nesta verificação. Manifest/recibos físicos históricos não recalculados nem alterados.
- Read06 Banco foi executado antes do monitor, sob unidade própria: duas conexões seriais/11 comandos, timeout10s/deadline60s, 5.964s, fechado antes do recibo, transação final0, zero escrita/procedure/API/retry. Alvos exatos/sa protegido/sessões loopback confirmados; não é sessão JDBC Windows nem TLS/readiness do trial.

| Faceta principal | Resultado observado | Limite |
| --- | --- | --- |
| Objetos | 13 módulos,33 tabelas presentes | Presença não é equivalência. |
| Texto estrito | 3 iguais,10 diferentes | Causas sem classificação semântica. |
| ANSI/contexto | 13/13 | Não substitui texto/dependências. |
| Parâmetros/tipos | 109/109 | Somente comparações declaradas. |
| Trigger aplicável | 1/1 | Não inferir outros ramos condicionais. |
| Colunas | 297/297 e um modelo desconhecido | Desconhecido permanece não comprovado. |
| Dependências | 11 resolvidas,2 sem resolução | Não promover a PASS. |

Alertas e release laboratorial foram tratados como caminhos condicionais separados. Auditoria efetiva usa ControlPlaneDataExportExtractionAudit, sem assumir procedures legadas, fechamento completo/recordCounts/core no SAMPLE parcial.

## Correções, evidências e interpretação

`offline-comparator-receipt.json`: original50 checks PASS, 105 migrations/zero erro de parser. `offline-successor-receipt.json`: 22 checks causais PASS, zero SQL/HTTP, guard de texto estrito preservado. Sucessor corrige default FK `NotSpecified`→`NO_ACTION` e comparação AST de constraints/índices que preserva literais/operadores/ordem/collation, ignorando somente parênteses redundantes e representação de identificador.

O comparador físico anterior tinha esse defeito. **As 38 diferenças FK brutas não demonstram drift físico**, nem o total antigo de facetas não comprovadas é total autoritativo de defeitos do schema. Evidências brutas/FAILs preservados. Definições físicas foram descartadas conforme os limites da unidade; sucessor não foi reaplicado, e não há metadados físicos preservados para refazer as comparações offline. Dez diferenças textuais/duas dependências permanecem abertas. Histórico Flyway ausente não prova sozinho schema errado e não autoriza baseline/repair/DDL.

Qualificação0424 permanece: Surefire2456/0/0/5, Failsafe7/0/0/0 e18 gates PASS, sem alteração Java/CI/SQL/Fonte. Não repetir Maven/AST sem mudança que o justifique. STATES/trilha/RETOMADA recebem somente evidência comprovada, sem caixa/P08/G01/cutover. CONTEXTO_GLOBAL ausente e HANDOFF_PATH histórico preservados.

## Controle Hermes e retomada — até três ações

Depois de salvar/conferir este checkpoint, registrar `waiting_user` no controlador local identificado pela nota. O checkpoint muda porque houve integração real de handoff concluído; não reinicia orçamento por esforço/tempo. Esse estado impede novos acionamentos de IA enquanto falta input; não pausa a rotina nem altera seu agendamento. A leitura do controlador confirmou comando `state` como atualização local de continuity.json, sem modelos/SQL/API.

1. Obter unidade metadata-only nova, explicitamente coberta/reservada, para Banco aplicar o sucessor e classificar dez textos/duas dependências em memória. Monitor atual não cria essa autoridade; nenhum segredo novo é necessário.
2. Owner da origem comprova mudança concreta do STOP429; não sondar para descobrir liberação nem renovar teto10/saldo7 por rotina.
3. Banco resolve/qualifica Windows/listeners/TLS/contrato físico sob autoridade/gates próprios antes de avaliar trial real pequeno com reserva/PRE/POST/readback/rollback. Não alterar login/serviço/schema ou implementar migração SQL auth por inferência.

Nenhuma frente offline independente elegível permanece nesta fotografia. Condição de conclusão do objetivo: página real na composição ETL com staging/comparação/rollback provados fisicamente. O monitor não promove conclusão por checks offline ou por leitura da nota.
