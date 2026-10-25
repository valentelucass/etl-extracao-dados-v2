# Checkpoint 0563 — Presença Fretes corrigida; recusa SQL real identificada

24/10/2026. Sucessor0562. A execução das quatro etapas continua;sem retorno final.

Três unidades Fretes próprias consumidas,3 HTTP200 cada,37 linhas válidas/per50/
página2 vazia. Banco sempre C78 openDev/TLS/mesmo socket observado antes LOGIN7,
owner Root sequencial,14 objetos com locks X pertencentes à transação,catálogo47
módulos revalidado,transação aninhada com identidade/depth/SPID conferidos.
Nenhuma promoção,commit retido,DDL ou alteração externa.

1. Stage3-fretes-v2:falha ControlPlanePersistenceException após início;instrumentação
   não reteve código SQL. Leitura do procedimento mostrou que start já cria EXTRACTING;
   a transição PLANNED→EXTRACTING extra do helper era indevida e foi removida.
2. v3:SQL52122 no staging. Mapper enviava19 presenças;procedimento existente exige18.
   Corrigido Java para projetar os18 paths tipados,conservando servico_em ABSENT,
   service_at no payload inteiro e presença/path/raw/parse na evidência temporal.
   ADR0071/RG-FRETE-019. RED causal1falha confirmada,45 testes locais e7IT PASS,
   strictcompile/style/format/package PASS. JAR94E8BE49...9FFF895 em
   `quatro-etapas-20261024-01/mapper-sql-compatibility/snapshot/target`,sem implantação.
3. v4 com esse JAR:avançou da validação de presença para SQL52123. Procedimento e
   constraint ainda aceitam apenas SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE;
   mapper real preserva DATAEXPORT_6389_SERVICE_DATE_MAPPING. Não falsificar marca
   real como sintética para obter PASS. Prova de gravação/readback NÃO concluída.
   Conexões fechadas com rollback;DDL continua proibido no escopo vigente.

Os receipts antigos dos helpers v2/v3 escreveram sqlChanges=0 como metadado fixo;
isso significa nenhum efeito RETIDO, não ausência de DML transitória. Foram tentadas
chamadas reais de control plane/staging e revertidas. Artefatos históricos preservados;
v4 já distingue retainedSqlChanges/transientWritesPossible. Nenhum sucesso de gravação
ou prova de ausência posterior foi inferido do fechamento em falha.

Etapa4 em execução:primeira campanha8 relatórios,29HTTP200/zero429. Coletas1,
Manifestos315,Localização37,Contas13,Faturas37,Inventário207 tiveram duas passagens
terminais per50/per100 com igualdade integral de valores/multiplicidades em RAM.
Coletas/Contas/Faturas/Inventário mapearam sem issues. Manifestos295/315 recusados
INVALID_TEXT_FIELD_TYPE:tipo NUMBER observado em mft_s_n_sequence_code corresponde
às295 ocorrências por passagem;tipo do campo será corrigido somente após teste causal.
Localização37/37 recusados UNAPPROVED_SOURCE_PATH:origem26campos versus17do mapper.
Cotações22/10 atingiu200linhas válidas e teto4páginas,sem terminal;unidade fechada.
Sinistros DATA200,mas analisador local parou com JsonParseException na passagem
de JSON pelo pipe;sem qualificar mapeamento ou completar travessia. Sucessor específico
preparou pipe UTF8/escape não ASCII com teste causal de transporte sintético, e
orçamento próprio Cotações/Sinistros. Sem repetir relatórios já aprovados sem causa.

Total antes desse sucessor:59 HTTP novos nesta campanha,zero429;5 conexões SQL
(2 catálogos+3 provas transacionais). Somente correção Java aplicada ao repositório;
SQL16.0.1000.6/instalação/identidades/contas/permissões/ACL/serviços/listeners/trust/
schema/Flyway/V1/WMS/produção intactos. C78 rollback-only,MainHTTPUNKNOWN e gates
permanentes preservados. Preparar proposta concreta para os2 objetos SQL antes de
qualquer pedido de exceção;continuar demais fontes/Usuários/Raster e registrar limites.
