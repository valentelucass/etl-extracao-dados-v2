# Checkpoint 0565 — Usuários fechado localmente; predicados SQL e delegação

24/10/2026,22:02BRT. Sucessor0564; o pedido de concluir as quatro etapas continua
ativo. Não declarar etapas3/4 concluídas nem transformar diagnóstico em captura.

## Encerramento verdadeiro de Usuários

`users-full-streaming-live-memory-job-receipt.json` registra exit1,3.313.719ms,
sem timeout,teto1GiB,pico235.839.488bytes. O job próprio encerrou; não está ativo.
949 páginas bem-sucedidas,18.980IDs distintos,18.978 mapeados sem recusa e2
INVALID_NAME_VALUE; última resposta ainda hasNextPage=true. Sem terminalidade
ou aceite de snapshot. Nenhum ID/nome/cursor/payload de negócio foi persistido.

`results.json` é o último sucesso estável,949páginas,com stateRESERVED obsoleto.
`results.tmp` preserva stateSTOPPED/failureCodeSOURCE_RESERVATION_FAILED na
reserva950; stderr contém RESULT_SAVE_FAILED. A falha aconteceu em beforeAttempt,
antes de httpClient.sendAsync no executor real. Portanto a reserva950 não virou
chamadaHTTP. `users-full-streaming/closure.json` reconcilia sem editar originais,
incluindo hashes,ledger949 e posições do código. A IOException subjacente foi
perdida pelo catch original: não afirmar sua classe ou culpar a API.

Reprodução local separada manteve `legacy.json` aberto por leitor Windows:
substituição atômica antiga falhou com AccessDeniedException. Isso demonstra o
mecanismo possível,sem provar a classe perdida do incidente. O novo auxiliar
TechnicalMetadataJournal grava snapshots únicos e publica selo após fechamento,
sem renomear/substituir arquivo que leitores possam manter aberto.128roundtrips,
recusa de incompleto/teto/tamanho/reabertura PASS;compile17 strict/Werror PASS;
batch0/sem timeout. Prova em metadata-journal-qualification/result.json,zero
HTTP/SQL e sem alteração do JAR. Integração no sucessor foi delegada abaixo;
não alegar que a travessia antiga foi recuperada nem renovar sua unidade.

## Proposta SQL testada por consultas,sem DDL

`sql-predicate-catalog` foi qualificado offline e executado em uma conexão C78
com mesma identidade nativa/socket antesLOGIN7,TLS,alvoSQL16.0.1000.6 e encerramento
confirmados.13SELECT,zeroDML/DDL/HTTP.47módulos continuam iguais ao código atual.
Os12casos executaram os predicados exatos extraídos da proposta v3 em SELECT:
real/legado válidos aceitos;scopeunknown/ausente/NULL,pathausente/NULL,parseinválido,
presença legada forjada,datatipadaausente e fingerprint/versão incorretos recusados
no nível devido. Nos2últimos oCHECKrepresentacional passa e oproc barra; isso é
intencional porque oCHECKnão consulta ctl.execution_attempt.

Não é teste de ALTER,procedimento completo,gravação ou rollback de37fretes.
`sql-proposal-v3/forward-proposal.sql` continua proposta não executada de apenas
stg.usp_stage_frete_record e stg.frete_record/CK_stg_frete_freshness. Readback real
continua aberto após52123. A pergunta assíncrona sobre exceçãoDDL continua sem
resposta humana. Sem Flywaymigrate/baseline/repair/clean ou histórico inferido.

Stage3FretesPipeline-v5 e controller foram preparados,sem compilar/rodar: pedido
principal deve vincular destino real C78; catálogo pós-adoção tem de vir do banco
real,se/quando houver adoção autorizada. Não alterar baseline antigo para fazê-lo
coincidir. C78 segue rollback-only para dados,não há autorização de retenção.

## Delegação autorizada pelo usuário

O usuário autorizou expressamente criar chats separados e nomeados para acelerar,
deixando este supervisor; depois pediu seção própria e renomeação. A exceção é
somente para esses chats delegados,sem remover outras proibições ou autorizarDDL.
Seção ETLV2 criada,IDe655219b-797f-4cbd-86c1-4e03d9246a31,acima dos fixados;
os quatro chats foram movidos para ela. Não foram alterados WMS/outros chats.

- ETL V2 — Supervisor:01a1455c-57df-7983-b86f-3b4e9dc3e19c. Root único editor
  canônico docs/CI,integrador e owner físicoBanco. Enquanto o delegadoUsuários
  qualifica,Root fica em leitura/preparo leve,sem segundo batch/SQL concorrente.
- ETL V2 — Usuários e retomada da coleta:01a17091-5e7a-7932-80b4-7b769589846c.
  Integrar e qualificar observador offline em delegacao-usuarios-20261024-01;
  transporte falso/semrede,progressoselado,contagens/falhas sanitizadas. Pode
  compilar/testar só pelo executor original,uma vaga≤1GiB. ZeroHTTP/SQL.
- ETL V2 — Contratos e demais entidades:01a17092-71ca-7d91-a166-7f1410137efe.
  Leitura estática dos consumidores/regrasV1/identidades/gates;relatório externo
  em delegacao-contratos-20261024-01,sem alteração de produto/batch/HTTP/SQL.
- ETL V2 — Revisão do banco:01a17093-42c9-7641-aa5c-519bcffe3b5e.
  Revisão estática independente dos2objetos/proteções/rollback em
  delegacao-revisao-banco-20261024-01;zeroSQL/HTTP/batch/DDL/credenciais.

Cada delegado finaliza seu relatório; supervisor observa com wait_threads e
revisa antes de integrar. Nenhuma mensagem automática de retorno entre chats,
automação,novo agente ou adoção implícita. Não editar docs canônicas em paralelo.

## Estado consolidado e próximos passos

1181chamadas à fonte nesta campanha:84DataExport,1096GraphQL,1Raster;1182reservas
locais porque a950deUsuários não foi enviada. Zero429 observado. Seis conexões
SQL encerradas:3catálogos,3tentativasstaging;zeroDDL e zero dado de negócio retido.
O último catálogo só avaliou predicados. JARA49EA79E...A98999/334testes por
composição/7IT eAST44514/116337 permanecem,não houve nova qualificação do produto.
Matriz externa current-matrix.json passa a representar o stream como fechado,
não como processo ainda ativo. Catálogo de entidades conserva ressalvas físicas.

1. Revisar as três entregas delegadas e integrar somente correções qualificadas.
2. Aguardar resposta à exceçãoDDL específica; não executar efeito dependente sem ela.
3. Se autorizada,qualificar alteração2objetos,reverter testes,adotar só dentro do
   escopo concedido,observar catálogo novo e provar37fonte→staging→readback→rollback
   →ausência. Não promover/implantar/retê-los por inferência.
4. Usuários ainda não tem terminal; identidades/grão/consumidores/captura dos
   demais continuam a exigir seus próprios aceites. Não fechar etapa4 por amostras.

SQL16.0.1000.6/instalação,contas/credenciais/permissões/identidades/ACL/elevação,
serviços/listeners/certificados/trustglobal,V1/WMS/produção preservados. Gates43RAW/
53exclusões eMainHTTPUNKNOWN/MISSING_NOT_RECONSTRUCTED não foram apagados.
