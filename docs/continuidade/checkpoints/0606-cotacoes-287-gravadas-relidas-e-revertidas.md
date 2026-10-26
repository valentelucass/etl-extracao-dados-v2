# 0606 — Cotações: 287 registros gravados, relidos e revertidos

O ensaio real de Cotações Data Export6906 concluiu a extração de22/10/2026, a gravação e a leitura de volta de287 registros. A comparação tipada foi igual para todos. O controlador, o processo Java e o executor original terminaram0. Os dois rollbacks, a ausência nas oito tabelas e o fechamento foram comprovados. O banco voltou ao estado anterior ao ensaio; não há gravação permanente, promoção ou implantação por esse resultado.

## Resultado real de Cotações

- Unidade `root-cotacoes-bank-0606-01`, produto P2 F2A78CBD884C828ECE78F66B2064DCEB155634B74F8DF599E2D343699EEE2D35, BoundV15, wrapperV6. Recorte22/10/2026, páginas de100, sem ordenação.
- INFO1+DATA4: cinco reservas e cinco conclusões. Páginas100/100/87/0;287 linhas físicas,285 chaves distintas, três lotes. A página vazia foi observada. O estado permanece LOCAL_TERMINAL_UNVERIFIED e sourceCoverageProven=false: não é prova de cobertura histórica universal nem de paridade com a plataforma.
- Banco:287 gravadas=287 relidas com igualdade tipada;11 locks próprios; duas passagens STRICT,70 SELECT,13 tabelas da união de catálogo e11 protegidas no ensaio. Foram432 operações e três commits do adaptador suprimidos.
- Fechamento: um ACK de rollback original e um ACK de rollback das leituras; dez SELECT pós-rollback, ausência8, transação própria das leituras provada e original ausente. Conexão/processo/stdin/socket fechados; peer antes do login e SQL Server16.0.1000.6 preservados. Identidades numéricas transacionais não persistidas.
- Original0/56.473ms/810.844.160B sob1GiB/6min, sem timeout. Java0, produtor físico qualificado e controlador qualifiedPhysical=true. Não foi necessário repetir extração nem renovar a unidade.
- É staging diagnóstico: sem Main, tarifa inventada, DQ, promoção, commit de negócio retido ou DDL. Não transferir o resultado para o fluxo completo de Cotações ou outro produto.

| Evidência externa preservada | SHA256 |
|---|---|
|RootCotacoesSourceBankQualification0606.json|D16DAC131632617CCD8A54CD1CCC6195211C60F99C5C55B1B9C62DCEFF0791DF|
|0606/controller-receipt.json|59CED9082293789651E99F88ADC9DC501C6A181D8F25831708F346658EE0514D|
|0606/original-memory.json|D34FF4F34FBEFAEB726E3C94D65A24BA7D80A221BB71002F28345753FBEE02C9|
|0606/child/physical-result.json|96E2EDFB72BBE8D75B33DDFC16D5073AAD5F034671DC2ECD201ACD870A9C99C5|
|0606/root-unit-closure.json|EA53E35EED9AC2655C1EE9D1065B2910329EDA67EA3328FEE8A7C44644B24FF7|

O último par Source agora é COT0606, com delta local5. MFT0604 permanece histórico, inclusive seu original geral1 e a prova física315; não foi reescrito. Os contadores globais continuam não finais. Qualquer novo reconhecimento Source precisa considerar0606; V6 e leitores que reconhecem apenas0604/0601 não são admissão para outra execução. Unidades anteriores permanecem aposentadas sem replay ou renovação automática.

## Preparação que precedeu o ensaio

O wrapper dedicadoV6, manifesto BB88E8B7A6F1C1A8F116A74D465CC26F8E09A12E47D7F81D93DC17AC79022494, passou127 modelos próprios,20 Java+107 PowerShell, com original0/95.610ms/503.402.496B. Exige o BoundV15/278 real, Cotações6906/per100, contexto diagnóstico explícito, último Source0604 literal e a correção exata do consumidor catalogLockedTrialTables=11. Revisão dos42 arquivos/64 pins sem bloqueador funcional; uma documentação comparativa herdada ficou registrada como P3, sem uso operacional como autoridade.

O preflight CURRENT passou com127/278,129 arquivos próprios/1123 externos e24 descritores de classes. Original0/30.370ms/115.335.168B, sem provider/SQL/HTTP/CODE. O controlador externo clonado ainda esperava16 descritores da V14 e fechou1 após o sucesso original. A diferença foi comprovada: os16 caminhos anteriores continuam, mais oito classes dos novos modelos COT e bridge;24/24 caminhos, hashes e grupo fechado conferem. Reconciliação Root e adendo independente preservaram outer1/original0, sem reexecutar o preflight. A emissão pessoal finita ocorreu somente depois dessa revisão.

A correção de um campo do consumidor, comprovada pelos cinco testes de0604, foi exercitada no controlador real V6 e este terminou0. O recibo histórico de Manifestos continua1; não foi convertido em sucesso geral retroativo.

## Catálogo atual: Coletas instalada; wire de Usuários ausente

UsersCatalogV3 passou45 próprios,43 preservados mais dois, sem herdar os40/43 anteriores: original0/16.158ms/501.788.672B. Revisão conferiu27 arquivos e543 pins. O roster tem378 nomes,242 tabelas e136 módulos;16 SELECT, reader e limites preservados.

A unidade `live-root-users-meta-0606-01` concluiu16/16 SELECT em uma conexão C78, sem Source/DML/DDL. Original0/31.134ms/561.041.408B; processo0, conexão/peer/native/socket conhecidos e fotografia dos378 objetos antes/depois igual. O arquivo privado tem6.597.157bytes, abaixo do teto8MiB. Isso coleta metadados, mantendo CLOSED_UNKNOWN_DIAGNOSTIC_ONLY; não admite staging ou schema.

Foram observados:

- stg.usp_stage_coleta_record instalada, corpo legível,23 parâmetros; assinatura e chamada ao genérico12 conferem com o gateway atual. O footprint candidato11 existe e os módulos comuns conferem. Ainda faltam o catálogo fechado da nova ligação e verificações atuais de superfícies fora desse plano; nenhum DDL necessário para Coletas foi demonstrado.
- stg.usuario_observed_field_capture, stg.usuario_observed_field_record e stg.usp_stage_usuario_observed_fields ausentes, por linhas wanted com identidade NULL. Não foram criados.
- A rotina genérica atribui staged_at com horário do banco; a leitura de volta futura não pode exigir igualdade desse campo com observed_at enviado no bind23 de Coletas. Os campos de negócio continuam exigindo comparação própria correta.

Prova RootUsersCatalog16Qualification0606.json SHA A0A12EED897553924EB89EF66EF2BC6D5AA541B2CC18CE532FCB90A04C3DE32F; metadados privados80A56F80134DC6A075250E389137932091E62EBA6D64D1CD82E309D1A5B51D56. Revisão ColetasA8A19CDA3BDC743DE4261E2CD0F5CED860F61E3AFDED8BD663CB944CE20FC33D, sealEF7B80FE9E52D1007703677492C3D7BE827D7A95EF4576AF3495953087BCA4FE.

Users15340/10250003 continua interrompido, sem recibo original, fechamento Source, EOF ou replay. A condição textual pede revisão Root atual de closure/receipt/release. O Root revisou a interrupção e a ausência atual do processo/mutexes e emitiu somente uma liberação pessoal finita para esses16 SELECT de metadados; não declarou fechamento limpo ou autoridade Source, não usou fechamentoV2 histórico e não alterou gates. A prova de interrupção permanece intacta.

## Próximos trabalhos e limites

Preparação em andamento: ligação JDBC fechada23 de Coletas, INFO6908 atual reutilizando transporte existente, sincronização operacional de publicação/leitura CIM e composição do ajuste Raster com sua antesimagem real. Raster ainda requer resolver owner/matcher/proteção/recuperação antes de pedir uma decisão estrutural concreta. Nenhum novo DDL foi aprovado ou aplicado.

Capture/Product7/Bind111/Clock4/Main7 permanecem pendentes. O alvo de integração15AB e o produto Source F2A são diferentes; nenhum PASS é transferido entre eles. A composição final do runtime ainda exige sua própria revisão/qualificação. O Chrome continua aguardando reconexão de controle; nenhum Excel ou paridade ESL concluído.

Usuários continua sem EOF; demais entidades, adoção, implantação e etapas3/4 permanecem abertas. Root é o único executor pesado/API/SQL/browser/Git, outros chats somente arquivos. ESL exclusivamente consultas/filtros/downloads; Fretes6389 distinto de GraphQL e Localizador8656 distinto de Raster. Sem alteração de versão SQL/serviços/contas/ACL/V1/PM2, retenção de negócio, deploy ou cutover. Commit e push obrigatórios para alterações concluídas.
