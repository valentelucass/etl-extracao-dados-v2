# 0620 — Coletas: preparação corrigida e 308 testes aprovados

Data: 2026-10-26. Continuação do checkpoint0619. Etapas3 e4 abertas.

## Resultado real

Root executou uma ocorrência nova, `root-coletas-legacy-semantics-0620b-01`,
no sucessor privado `coletas-legacy-request-semantics-offline-successor-0619-v2`.
Os 284 casos existentes, 20 casos de retenção privada e quatro causais de
semântica LEGACY passaram: **308 casos e três preflights completos**.
Os preflights são de Manifestos, Cotações e Coletas. Original fechou0,
sem timeout, em190.123ms; pico712.249.344bytes, dentro de1GiB.

Esta é qualificação offline dos auxiliares atuais. Zero HTTP, conexão SQL,
DDL, implantação ou mudança de PM2. Não houve dataset real novo, gravação
retida, confronto externo nem admissão de captura física.

## Correção e ocorrência anterior preservada

Coletas usa legitimamente o perfil LEGACY. Os dois pontos auxiliares que
chamavam um getter exclusivo de perfil observado passaram a usar um helper
único, derivado do body realmente serializado e de um domínio próprio.
O getter continua recusando LEGACY; produto, wire e semântica observada de
Manifestos/Cotações permanecem preservados. Os quatro novos casos verificam
essa distinção e mutações relevantes do pedido.

A primeira integração dessa correção falhou na compilação real: Original1,
34.928ms, pico436.756.480bytes. A API de configuração não possui
`sourceZone()` diretamente. Root identificou cinco acessos em três fontes
e corrigiu para `settings().sourceZone()`, conforme os tipos reais do produto.
Os bytes e recibos da falha permanecem preservados; não receberam PASS.

O sucessor novo mantém144 arquivos idênticos e altera apenas as três fontes
com esses cinco acessos. Total147 arquivos e1.334 pins externos: os1.329
anteriores preservados mais cinco referências da correção. Runner, limites,
quatro entradas offline, seis corpos Coletas anteriores, 20 corpos de retenção
e JAR153 permanecem idênticos ao predecessor. Revisão independente aprovou
essa ocorrência nova antes da execução. Não houve rebuild nem repetição Own200.

## Demais entregas e pendências

- Binding final: os21 arquivos bd60 já foram integrados/revisados no0619.
  Direct7/Bind/111/4/Main7 e nove causais ainda não executados. Adoção68
  segue condicionada aos bindings reais e à reconciliação nativa vigente.
- Banco Fretes/Local: recebida adaptação privada de sete arquivos para
  os dois escopos CATALOG_READONLY existentes. Preserva43 corpos e prepara
  dez causais novos;53 ainda não executados neste checkpoint. Fretes25 e
  Local21, total46 SELECTs de metadados, continuam sem observação real nova.
  Proposta em revisão, sem Entry ou autoridade DDL. Não ler negócio nem
  transformar essa preparação em prova de Catalog57.
- Migrations V106/V107 e quatro caminhos relacionados permanecem propostas,
  sem adoção/DDL. Adoção68 deve preceder a mudança desses seis caminhos para
  preservar a igualdade declarada com g1 e separar as qualificações.
- Última observação nativa0619: cinco identidades esperadas e duas extras,
  uma correlacionada ao PM2 ETL-EXTRACAO-DADOS-LOOP; versão não identificada.
  Nenhum processo foi parado e nenhum perfil de sete admitido.

JAR153/g1, Own200, Final8, Full624 e Clock9 continuam preservados.
Última Source continua INFOColetas0607/delta1. Os históricos Manifestos315
e Cotações287 foram gravados, relidos e revertidos, mas seus valores não foram
retidos para comparação externa; não reconstruí-los a partir das contagens.

Próximos aceites: conferir46 metadados pelo caminho readonly existente;
fechar os bindings finais; revisar os gates físicos já existentes antes da
captura Source→staging→readback→rollback com valores privados reais; completar
Users até EOF e conferir registros/campos com ESL/Excel no mesmo recorte.
Não há extração completa de todas as entidades nem encerramento das quatro etapas.

## Evidências privadas

Base B: `C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01`.

| Artefato em B | SHA256 |
| --- | --- |
| RootColetasLegacyCompileFailure0620.json | 04640A855DC33F659B61C849AC2C53590D57136A2F411E7369E8D5122829B27A |
| RootColetasSourceZoneFixIntegration0620b.json | F1954973F382932E77EB05B9BCFBDDACD9B1F84C4B807D5B909442042E3E0755 |
| coletas-source-zone-fix-review-files-only-0620b/review.json | 000D08B0DC5C1B02F0941DB331E8992EF229CB164CC862A85DBA66D815E14041 |
| RootColetasLegacySemanticsOriginalClosure0620b.json | 40F974E93B3236EF54F6F8440C719E980DCCC70BB06503B5703C35D7469CEA9B |
| RootColetasLegacySemanticsQualification0620b.json | F38470201EADC298C3A67C2FF4BAE8D438D148300C49DE168B0FE126828D9ED5 |

Selo do sucessor147: `2843BF2D600C3ECE8A68B741FD2F55E4B6DC5C3E3ABFFD1CD78FC47A9CE395DA`.
Inventário da proposta Fretes/Local: `A16502D40133FD752FDA5206AADC615B732B8698825D2369A5C3C529B1A85CCB`.
Dados brutos e segredos permanecem fora do Git. ESL somente consulta;
Root único executor pesado/API/SQL/browser/Git; PM2 preservado.
