# Checkpoint0082 — MAT04 e inputs sintéticos de MAT03

EM_EXECUCAO no pedido único A–N. Predecessor0081 SHA256 c4ff81f87b357d8dde778db9f9171876b7e0aa768dac5085910c9c9afa60846d.
Inventário2549/snapshots/universo45:target/macrobloco-expansao-20260912-01/; limites e autorização inalterados.

MAT04 implementada:mart.usp_materialize_expansion_invoices consome SQL FAT,
vínculos explícitos e labels. Uma linha por root_id sintético,emissão como
atributo;sem soma da expansão;null/full e bloqueios têm disposições. História,
recibo e linhagem SQL;JdbcExpansionMaterializations e invoiceFacts tipada.
physical-invoices-01:10/0/0/0,Java/JDBC/SQL,rollback e agregados conferidos.
Oráculos manuais cobrem2 títulos/200 apesar2 docs por título,correção isolada
sem duplicar,aging5 dias,zeros/nulos/DECIMAL máximo,FISCAL_UNRESOLVED/NULL_DATE/
FIELD_CONFLICT/REFERENCE_MISSING. Ainda ampliar estados e contraprovas M.

MAT03 SEM carga. V046/modelo/source getter/fixture/parser/JDBC capturam termos
laterais sintéticos por source key do Frete:revisão/data/classificação/cortesia/
elegibilidade/volumes/pagador/moeda/unidade. Receita não está na fixture;valor
será lido do Frete capturado. Termos são separados do /data,órfãos/empates
não escolhem alias. Hidratação começa a fornecer termos empacotados.
Próxima prova physical-freight-terms-01:estado autoritativo {"arguments":["--offline","--batch-mode","--no-transfer-progress","spotless:apply","process-test-classes","failsafe:integration-test","failsafe:verify","-Pshadow-local-integration","-Dshadow.local.integration.enabled=true","-Dit.test=ExpansionLaboratoryFreightTermsIT"],"exit":0,"attempt":"physical-freight-terms-01","state":"OBSERVED","phase":"Physical"}.
Consultar exit/log/relatório/processo próprio antes de repetir. Limite900s/heap512.

Migrations novas instaladas após qualificação,imutáveis;próxima V047:
database/migrations/V045__materialize_expansion_invoices.sql SHA256 97b69464e51e00b2890b6d9b46a637375f394e27a40e73528c106936133ca09b
database/migrations/V046__capture_expansion_freight_terms.sql SHA256 e138f13b07d47972f5c96a34f0c04a7e4aee9dcabb5b0a59c42060c07d038e54.
Regressão anterior33 físicas+16 guards passou no checkpoint0081;não substituir
essa evidência pela prova de inputs financeiros ainda em andamento.

Próximas ações:
1. Conferir physical-freight-terms-01 e corrigir falhas;implementar MAT03 com
calendário/cancelamento/bloqueio/cortesia/filial/volumes sobre capturas reais.
2. Sexta consulta,recomposição BOOTSTRAP/INCREMENTAL/BACKFILL/REPLAY e runner
one-shot integrado,recuperando plano/recibos por SQL na mesma transação.
3. Completar M/N:adversariais,concorrência efetiva,escala/planos,JAR/verify,
scanners/validadores/contratos/diff/manifestos/quadro45 antes-depois/entrega.

Não declarar A–N completas. Sem aceite real/B64. Somente localhost/shadow exato/
Windows;DDL fora das IT,DML rollback-only. Sem API/.env/segredos/grants/reset/
V1/dashboard/produção/serviço/commit/push. Prosseguir após compactação.
