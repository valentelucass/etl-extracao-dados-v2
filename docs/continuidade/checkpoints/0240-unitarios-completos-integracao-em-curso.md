# Checkpoint 0240 — unitários completos, integração em curso

UTC: 2026-09-22T05:20:29.291Z. Anterior: docs/continuidade/checkpoints/0239-p07-fisico-em-execucao.md, SHA-256 d5143376035b3d58612390e7fbe6c60efaa52a6d31bd96b62f35f1ce82b187a0. Estado TESTADO_NA_CAMADA unitária/estática e EM_EXECUCAO na integração. O objetivo original P09–P33 e P07/P08 permanece, conforme pedido efetivo; não há nova pergunta ao usuário.

Autoridade e limites: physical/authority.json e ledger.json, válidos até 2026-09-23T04:40:21.019Z; 15480/43200 segundos reservados. Wrapper p07-verify-02 ativo sobre fonte congelada pos0236-source-01; build pos0236-p07-verify-01. Somente localhost/ETL_SISTEMA_V2_SHADOW, integrado, sintéticos e rollback. Sem DDL, commit de domínio, fontes externas, produção, publicação, deploy ou corte. Segunda e última reserva FULL; não duplicar a execução.

Evidência: checkpoint-0240-evidence.json preserva a amostra de progresso e pins. Unitários: 2263/251 classes, zero falhas/erros, quatro skips. Gate PMD full: 251 XML/2263 casos, 653 fontes, 36 alertas brutos e 36 disposições; sem supressão ou aceite nominal. Integração na amostra 2026-09-22T05:19:48.754Z: 86 casos/23 classes, sem falhas/erros; número parcial não é aprovação.

Delta adicional de tooling: Test-OfflineSecretScan.ps1 passou de 17 para 18 na comparação do recibo agregado. RED demonstrou exit 0 e 18 casos corretos com passed falso; GREEN executou os mesmos 18 e confirmou passed verdadeiro. Antes/depois e relatórios preservados. O script não integra runtimePattern nem sourceInventory do pacote; não requer nova suíte física. Nenhuma alteração de Java após freeze.

Fechamento privado preparado em duas fases: validação documental inicial e readback final, 1800+900 segundos dentro da ordem existente, ainda sem reserva/execução. Manifesto não publicado, P08 não iniciado. Históricos, checkpoints, ledgers anteriores e falhas preservados. Contadores 39/45 e 67/115 intactos; nenhum aceite humano novo.

Próximas ações: (1) observar a integração ativa e conferir resultado integral, cobertura, identidades e agregados; (2) após aprovação, executar P08 serial pelos requests preparados; (3) consolidar matriz e estado, selar sucessão, validar e conferir os bytes finais. Fontes externas dependem de evidência concreta ausente; não substituí-la por fixtures.
