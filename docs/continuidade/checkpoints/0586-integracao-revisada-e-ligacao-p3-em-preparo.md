# Checkpoint 0586 — Integração revisada e ligação P3 em preparo

25/10/2026, 2026-10-25T11:42:21.201671+00:00. Anterior: [0585-catalogo-strict-real-e-usuarios-v3-em-travessia.md](0585-catalogo-strict-real-e-usuarios-v3-em-travessia.md), SHA256 4BC0D22BFA5B496D464248F368DFB7B7F0A18D9BDE6982A453CAE41A9A7F363A. Etapas 3 e 4 permanecem abertas. Esta unidade fez somente leitura, hashes e preparação de arquivos, sem Java, Maven, SQL ou nova fonte.

## Execução atual e evidência limitada

Usuários V3 é a única árvore protegida: sessão 15340, run 10250003, executor original 1 GiB/320 minutos. Mantém 5.000 chamadas, 100.000 nós, 18.000 segundos, 20 itens por página, intervalo de 3 segundos e uma tentativa. Nenhum outro Java/Maven/SQL/fonte/índice até fechamento conhecido, recibo original e liberação Root.

O snapshot selado desta escrita indica 845 páginas, 16900 registros observados, 16898 mapeados válidos e 2 separados por problemas no conteúdo. Ainda indica páginas seguintes: True. Não é encerramento nem aceite de snapshot. Os contadores correntes são parciais e vinculados ao UTC do selo; o baseline anterior continua 3.204 chamadas/3.205 reservas, Data Export 104/GraphQL 3.096/Raster 4. Não ampliar o orçamento, reaproveitar cursor, renovar após 429 ou repetir unidades expiradas.

## Integração mínima conferida por arquivos

Pacote estático `work/final-integration-static-plan-20261025-01`, manifesto 4D1882A7CBBBC0FDE3DEBBB7DFEF313346ECF2A15F7F81320B2E3F5C87CDC472. Propõe 51 destinos: 26 Java de produção, 21 testes/fixtures e 4 recursos; 9 substituições e 42 novos, sem colisão. Inclui os quatro overlays Raster, preservando a guarda JDBC legada acima de oito casas. O suporte RasterFieldObserver fica somente em teste.

Root conferiu os arquivos próprios e referências dos dois pacotes, origens/destinos, 1.559 pins canônicos de src, POM e oito bibliotecas. Review `RootStaticIntegrationAndDdlReview.json`, SHA256 681F689E38004232295E5BC9051891453CB5516764C1DB46281C9B5E244276EC. A conferência é de integridade e mapping, não compilação ou qualificação da seleção nova. A seleção futura é de 50 classes funcionais, uma classe de política e 7 testes de pacote; não herda os 447 casos da coorte maior. O pin LF-normalizado de JdbcRasterBatch na linha 44 de physical-sources.txt precisará do delta revisado, mantendo DIRECT_SQL_JDBC, limiares e exclusões.

Foi encontrada uma lacuna funcional: P3 ainda não chega ao Main/pedido/caso de uso corrente. Não basta adicionar classes ou casos ao switch. Está em preparação uma rota explícita opt-in de plan e rollback-trial para os quatro templates, com pedido tipado, a mesma referência RAM e capacidade Root genuína. Não usa Promotion/RuntimeWorkload normal nem declara retenção, publicação ou identidade global. Nenhum desses arquivos foi adotado. Produto E05 continua 150 testes e 7 IT; q06/447+7 continua externo e sem implantação.

Os 151 slots tipados P3 são 28 CAP + 53 Faturas + 26 Inventário + 44 Sinistros. Faturas tem 52 nomes wire e o slot adicional third_party_ctes_value, presente no DTO/projector/reader; quando não enviado pela fonte, permanece ABSENT. Não somar campos wire e slots tipados como se fossem a mesma medida. Source q01 preserva CAP/FAT/INV terminais e Sinistros não emitido, com zero chamadas e janela expirada, sem renovação automática.

## Próximas provas próprias de banco

Plano Usuários sucessor 02: manifesto 42807F0BC850C444729E6BC06C7A8AE2E6D0AB94BA52A69745779FD76F3D5D61, cinco arquivos/35 referências. M07 inclui classe/minor nas duas direções; as 15 tabelas e seis módulos são sementes, com fechamento de dependências/FKs/triggers/events ainda a materializar. Borrowers serão revogados antes de rollback, com poison e readback independente VALID/QUARANTINE. A capacidade física Users ainda não existe; o kernel e suas contraprovas ficam externos e fechados antes de qualquer efeito.

Documentação Microsoft confirma CURRENT_TRANSACTION_ID() desde SQL Server 2016, retorno bigint e leitura da própria sessão por qualquer usuário. Isso evita depender de novo grant para a DMV. Não foi executado no alvo. Memo `RootSqlTransactionIdentityPrimaryReview.json`, SHA256 1A016D3D6B0947B3C45E205633E2A0E86C20C8D748E96929D9B8FF12365496E4, contém as fontes primárias e as verificações ainda necessárias. A consulta literal e as contraprovas de ROLLBACK+BEGIN precisam de qualificação própria; nenhum pin congelado é alterado por essa descoberta.

Raster exige cinco tabelas no footprint: ctl.analytic_lab_run, ctl.analytic_raster_capture, stg.analytic_raster_trip, stg.analytic_raster_stop e stg.analytic_raster_structure. O nome CK_analytic_run é CHECK, não tabela ctl.analytic_run. O catálogo Raster12 antigo enumera quatro tabelas e está somente preparado; o Banco prepara sucessor incluindo a estrutura e dependências. Permanecem os 37 testes offline reais e a leitura RAM anterior 108/355/zero issues, sem nova prova física.

## DDL: revisão concreta, sem execução ou aprovação

Pacote `ddl-four-scopes-review-candidate`, manifesto 3A349523FF9CFC4A636C36BADC1BE258AD12442EDE986ADD7D86639A40BFBAC9: nove inputs/39 referências, somente desenho/preflight sem efeitos e 18 grupos causais propostos, zero executados. Fretes altera CHECK e procedure; Local26 somente a procedure; P3 cria cinco raízes e dez filhos inline; Raster altera duas coordenadas STG, dois CHECKs e um trigger. Os forwards Fretes/Local são referências bloqueadas com rollback externo; P3 contém guard_pins_ready=0; Raster forward/undo têm COMMIT estrutural efetivo. Nenhum é tratado como instalador autorizado.

Faltam catálogos próprios atuais, programas sucessores fechados, owner DDL separado do C78 rollback-only, proteção e recuperação demonstradas e decisão humana específica. O consentimento pendente para dois objetos Fretes não recebeu resposta nem cobre Local/P3/Raster. A pergunta ampliada não foi enviada como se o pacote estivesse pronto. O STRICT35 real cobre somente seu escopo Manifestos/Cotações/ctl e não é grant para esses objetos. Os 97 predicados Fretes/Local continuam prova parcial de predicados, não de procedures completas.

## Três próximas ações

1. Encerrar e reconciliar Usuários V3 com recibo original, journal integral, selos e cleanup; somente então liberar a próxima árvore.
2. Qualificar os candidatos e catálogos próprios Bank V7/Users/Raster/Fretes/Local/P3 e executar as provas elegíveis de fonte, staging tipado, readback, rollback e ausência.
3. Fechar a decisão DDL sobre pacote executável revisável, concluir a rota integrada P3 e as provas restantes, depois integração conjunta/CI/AST. Não encerrar por preparação ou resultado offline.

Permanecem 20 sessões JDBC confirmadas fechadas e uma tentativa nativa fechada sem retorno JDBC observado, zero DDL e zero commit de negócio retido. Preservar SQL 16.0.1000.6/instalação, contas, credenciais, permissões, identidades, ACL, elevação, serviços, listeners, certificados/trust global, V1/WMS/produção, 43 RAW, 53 exclusões, MainHTTP UNKNOWN e MISSING_NOT_RECONSTRUCTED. Nenhuma descrição substitui recibo ou autoridade.
