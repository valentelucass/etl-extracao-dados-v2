# Checkpoint 0559 — Seleção do contrato observado implementada, em qualificação

24/10/2026. Anterior: [0558](0558-get-body-qualificado-e-regressao-per50-concluida.md), SHA256
`2665B31D1BAC19D355B94606CA3E650422D1EC1D883E8DD078718129512A0894`.
O usuário determinou: “pois continue”. Objetivo permanece extração completa; unidade atual integra
o release real6389 à seleção do pedido principal, sem transformar prova diagnóstica em persistência.

## Alteração material e limites

RT-FRETE-OBS-001/ADR0070: propriedade opcional `sourceContractVersion` seleciona uma versão
observada conhecida somente para FRETES/BODY/uma tentativa e fora do perfil laboratorial.
Sem ela, a resolução antiga é preservada. O campo entra no hash do pedido e o release real é
vinculado à ocorrência/configuração pelo ContractExecutionBinding existente.

Novo DataExportObservedFretesContractCatalog carrega recurso próprio empacotado, limitado64KiB,
com SHA256 fixado e os três fingerprints recomputados. Recurso técnico49988B copiado byte a byte
do release observado; SHA256 `19B4D5D9E093A1BA1C88978EDD46EF5755F9D6F35772B3EFA8213D3F89AD6ADB`.
Preservados114 campos de metadata UNDECLARED, tipos reais dos16 filtros e15 paths UNOBSERVED.
Não generaliza C78/6908 nem substitui o catálogo sintético por default.

Plano e evidência externos: `integracao-contrato-fretes-20261024-01` na base externa do projeto.
Antes do efeito, Root registrou escopo HTTP0/SQL0, baseline JAR0558, unidade nova e batches finitos
de2GiB/12min pelo executor original. Alterações preexistentes preservadas; sem commit/deploy.

RED executado no snapshot com a implementação antiga: um teste/um erro esperado, causa
OPERATIONAL_REQUEST_SCHEMA; compilação de testes aprovada, Job exit0/88200ms/pico677027840B,
sem timeout. A seleção real positiva é o teste causal, não uma aprovação por contagem de arquivos.
GREEN, compilação estrita, estilo e pacote estão em execução; não afirmar aprovação antecipada.

O teste do catálogo foi ajustado antes do GREEN para distinguir os114 campos sem declaração dos
16 filtros com tipos reais. Nenhuma declaração do fornecedor foi apagada para fazer o teste passar.

## Próxima fronteira identificada no código

RuntimeDevSourceEvidenceInspection.verify ainda aceita somente COLETAS, com recusa explícita para
Fretes antes de fonte. O produtor geral C78DevEngineAdmissionProducer é apenas inspeção e termina
com CURRENT_SELECTED_SQL_SURFACE_NOT_QUALIFIED; não emite admissão física. O perfil SAMPLE12 é
Coletas e não pode ser aplicado por herança a Fretes. Portanto, selecionar o release real resolve
uma fronteira concreta, mas não autoriza chamar a Main esperando que grave Fretes automaticamente.

Esta unidade não executou API/SQL/TDS nem leu token/credencial. As20 chamadas reais da retomada
anterior permanecem consumidas e encerradas. SQL16.0.1000.6, contas/permissões/identidades/ACL,
serviços/listeners/trust/DDL/Flyway/V1/WMS/produção não foram alterados. C78 rollback-only,
MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, demais gates e falhas preservados.

1. Observar o batch GREEN atual e corrigir somente causas demonstradas, preservando recibos.
2. Concluir pacote/AST/integridade e publicar o resultado observado em novo checkpoint.
3. Preparar uma rota física própria de Fretes com Fonte/Banco e reserva delimitada, sem reutilizar
   a admissão Coletas nem converter JSON diagnóstico em autorização SQL.
