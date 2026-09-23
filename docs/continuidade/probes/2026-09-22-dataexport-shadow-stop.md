# Rodada Data Export em sombra — interrupção segura

Data: 22/09/2026. Autorização efetiva do usuário: “pode ja testar tudo sim”.

## Escopo e limites

- Somente leitura pelas três sondas autorizadas para 6908, 6389 e 4924, com
  consultas GraphQL estáticas quando a etapa correspondente fosse alcançada.
- Janela fechada: 2026-01-02. Teto agregado previsto: 31 chamadas; timeout de
  até 30 segundos, sem redirect e resposta limitada a 10 MiB.
- Sem escrita, banco, DDL/DML, agenda, deploy, corte, alteração de credencial
  ou persistência de payload/identificador de negócio.

## Resultado observado

- A primeira invocação foi recusada localmente pelo formato da lista de
  templates, antes de `curl.exe`; não produziu chamada externa.
- A invocação corrigida da sonda de contrato consumiu 7 das 7 chamadas do seu
  teto e interrompeu em `HTTP_NON_2XX`.
- A leitura de metadados de 6908 recebeu HTTP 200. O primeiro pedido de dados
  recusado recebeu HTTP 422; como a resposta não era uma página de registros
  válida, a contagem de entidades e a paginação não puderam ser verificadas.
- Em conformidade com a regra de parada, GraphQL para 6908/6389 e a prova
  financeira de 4924 não foram executados. Não houve retry, fallback nem nova
  chamada após a recusa.

## Conclusão

O contrato de consulta ainda não está qualificado para a janela usada. A causa
concreta a investigar é a recusa HTTP 422 do pedido de dados, usando somente
evidência sanitizada; uma nova rodada exige uma ordem própria e não pode ser
tratada como continuação desta execução.
