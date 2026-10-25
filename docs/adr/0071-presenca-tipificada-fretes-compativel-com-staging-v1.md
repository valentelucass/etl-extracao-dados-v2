# ADR 0071 — Presença tipificada Fretes compatível com staging v1

24/10/2026. Decisão local de implementação; não ratifica semântica do fornecedor,
não aplica DDL e não autoriza promoção/cutover.

A prova real do dia17/10 completou37 linhas da origem, mas o staging rejeitou o
envelope com SQL52122. A mudança0555 adicionara `service_at` à lista de presença,
e o procedimento existente exige exatamente18 paths. Catálogo atual comprovou
igualdade do procedimento com a migration versionada; o resumo enviado tinha19.

RG-FRETE-019: conservar os18 paths do contrato tipado de staging v1. Quando
`servico_em` está ausente, sua presença continua ABSENT; não a substituir por VALUE.
O campo real `service_at` continua integralmente no payload canônico e como path,
valor bruto, presença e parse state no envelope de evidência temporal. Nenhum dos
114 campos recebidos é descartado do payload. Exemplo sintético: id1/service_at
2036-03-17 sem servico_em produz18 presenças, servico_em ABSENT e evidência
temporal path /service_at com presença VALUE. O SQL não é alterado para aceitar
um envelope incompatível e a evidência ainda conserva seu escopo transitório.

Responsável técnico nesta ocorrência:Root; responsável de negócio não declarado.
Contrato afetado: projeção `field_presence_json` de6389→stg.frete_record. Teste
causal no cenário mapsObservedServiceAtWithTheOriginalSourcePathAndPayload:
RED confirmou19≠18; GREEN/pacote e repetição física são registrados em STATES.
Recuperação:reverter somente este delta Java/teste após conferir modificações
posteriores; nenhuma mudança de schema ou recuperação SQL permanente envolvida.
