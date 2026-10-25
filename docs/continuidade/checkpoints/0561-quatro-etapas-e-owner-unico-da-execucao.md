# Checkpoint 0561 — Quatro etapas adotadas; execução sequencial com owner único

24/10/2026. Sucessor de0560; fatos e falhas anteriores preservados. O usuário determinou separar
as quatro etapas do último balanço, executá-las uma por uma e retornar o balanço ao final da quarta.
Plano externo: `quatro-etapas-20261024-01/plan.json` na base externa do projeto.

1. Nove Data Export: consultas atuais INFO/DATA pelo GET com corpo documentado;18 chamadas
   máximas em nove unidades próprias de até2, per1/página1/dia17/10, intervalo3s, uma tentativa,
   timeout30s/resposta10MiB/deadline720s. Erro fecha a unidade;429 fecha a campanha inteira.
   Não é prova de travessia completa, identidade ou persistência.
2. Fretes: vincular as provas reais terminais e o release observado à execução atual; preservar
   os testes e os pacotes qualificados, sem repetir PASS sem causa ou promover o catálogo sintético.
3. Banco: catálogo atual e prova real fonte→staging→readback→rollback→ausência, em unidades
   próprias reservadas antes do efeito. C78 continua rollback-only; nenhum commit retido implícito.
4. Demais entidades: campos/IDs/paginação/mapeamento/gravação reais, incluindo Usuários/Raster,
   sem fabricar semântica do fornecedor ou aprovar acesso ausente por fixture.

A opção de usar auxiliar não é uma precondição técnica intrínseca. Para cumprir o pedido atual
sem nova rodada de perguntas, Root assume sequencialmente o papel Banco nas unidades físicas,
como único owner/controller real e executor SQL desta ocorrência. Não existe delegação ou novo
agente. A pergunta anterior sobre auxiliar não será tratada como aprovação recebida, e nenhum
Builder concorrente recebe SQL. Toda prova física mantém admissão, orçamento, observação de
socket/identidade/TLS/versão/alvo/schema/writers, leitura independente e encerramento reais.

Nenhuma chamada nova ou SQL ocorreu ao escrever este checkpoint. Campanhas anteriores continuam
encerradas e consumidas. A janela17/10 é um recorte concreto inicial, não todo o histórico.
Não marcar a quarta etapa concluída por mero diagnóstico se faltarem critérios físicos ou
semântica externa; registrar o impedimento concreto depois de esgotar os passos elegíveis.

SQL16.0.1000.6/instalação, contas/credenciais/permissões/identidades/ACL/elevação, serviços/listeners/
trust global/schema/DDL/Flyway/V1/WMS/produção preservados. Sem mensagens externas, chats ou agentes.

1. Qualificar e executar as nove unidades de fonte já predeclaradas.
2. Qualificar o catálogo atual do banco antes de admitir captura; corrigir somente causas locais.
3. Executar as provas restantes e consolidar aceites somente na camada realmente observada.
