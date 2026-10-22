# ADR 0067 — Design de retenção C78 com modelo puro, sem perfil operacional

Status: DESENHO_ADOTADO_SEM_PERFIL_OPERACIONAL. Decisão D-C78-RETENTION-DESIGN-01, escopo reduzido pelo Root a especificação de design e consumidor puro de revisão. A qualificação do recorte depende do recibo real descrito no checkpoint da fase3.

## Contexto e decisão

O owner C78/SAMPLE atual finaliza com rollback. Sua evidência não pode ser reinterpretada como retenção, nem a aprovação E3 da ADR0060 pode ser transferida para C78. A proposta0533 permanece íntegra e histórica. O usuário determinou concluir as frentes locais elegíveis enquanto a resposta ESL aguarda o gestor.

Adota-se a especificação em `../catalogos/c78-retention-design/DESIGN.md` como direção técnica para eventual implementação futura, e um consumidor Python autônomo com vetores literais para revisão de três subcontratos puros. Não se adota perfil operacional, novo owner físico, API Java, flag, integração ao build/runtime, admissão, reserva ou commit.

O recorte executável contém somente: geometria e três símbolos de namespace sintético; ledger de quatro buckets de créditos abstratos; classificação de conhecimento de commit/readback/rollback/close. Os expected são literais independentes da saída do modelo. Todos os resultados, inclusive positivos, declaram `MODEL_ONLY_NO_EFFECT_AUTHORITY` e `authorizesPhysicalEffects=false`.

O custo explícito e positivo de uma tentativa é consumido antes do registro da tentativa, independentemente da finalização; custo, prova e reserva de sufixo devem caber com aritmética int64 exata. Falha não recredita, a unidade não reabre e uma tentativa incerta não admite repetição. Números de teste não representam orçamento SQL/HTTP. Conexão, unidade e verifier de confirmação são símbolos distintos dos da captura; isso não autentica recursos físicos.

## Alcance do aceite e lacunas

O máximo que a execução pode provar é `RECORTE_DE_MODELO_PURO_QUALIFICADO`. Não prova intenção operacional completa: perfil/operação/ciclo/janela/fuso/fingerprints/release/caller permanecem fora do modelo. Não compõe os subcontratos em elegibilidade de owner. Não inventaria comandos, deadlines, custos de driver, prova por CALL, proteção contra writers, provider/TLS, catálogo ou orçamento físico completo. As entregas mais amplas de DESIGN.md permanecem requisitos de trabalho futuro.

Commit conhecido, tentativa incerta, ausência atual, leitura indisponível e close incerto são dimensões separadas. Ausência não reconstrói rollback histórico, e correspondência atual não reconstrói ack histórico. Nenhum teste puro prova SQL, retenção, completude ou paridade reais.

## Continuidade

C78 permanece rollback-only. ESL permanece em standby. O futuro perfil retido exigirá implementação própria, revisão, qualificação, catálogo atual e reservas independentes de antecedente/captura/confirmação/reconciliação antes de efeitos. Retenção produtiva, TTL/legal hold, backup/restore, release e cutover permanecem aceites separados. Não se altera ADR0060/0062/0064/0066, SQL Server16.0.1000.6, instalação, contas, credenciais, permissões, ACL, elevação, serviços, listeners, trust global, DDL/Flyway, V1/PM2 ou produção.

## Evidência local da parcela qualificada

Execução real em cópia privada sob o executor vigente:44 expected literais correspondentes/zero divergências,10INTENT/19BUDGET/15OBSERVATION. Fonte Python SHA dec2eeed63ba6f8f487c7b0237c75f0dc7a56de9fb702434a6995dab446e3a97; vetores SHA cf7c093fd1eb82ee9940edb4cfb1260e66d9979f2889c21bbb2fad34afce7fca; resultado SHA69a7efcb27c87edc85095d0434592f34ac2c8ee94e68dd537bba013c751e1b8f. Os bytes executados são os publicados no catálogo. Ver checkpoint0536 e seu índice para receipt e revisão. Os44 resultados, inclusive positivos, conservam authorizesPhysicalEffects=false. A qualificação não fecha as três entregas operacionais amplas.