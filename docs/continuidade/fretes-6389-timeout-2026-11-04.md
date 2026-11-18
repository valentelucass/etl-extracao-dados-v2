# Registro para investigação na ESL — Fretes Data Export6389

Atualização 17:38: quatro consultas isoladas de controle, mesmo dia/página/per e cliente HTTP/1.1, resultaram em 200 sem ordem, 422 com `id asc`, 200 sem ordem e 200 com `id asc`. A falha de 67 bytes chegou em 2,726 segundos. Portanto, a falha é intermitente nessa consulta; ordenação e cliente Java não estão demonstrados como causas exclusivas. Ver [testes controlados0684](checkpoints/0684-fretes-testes-controlados-do-timeout.md). A extração completa e as três semanas de Fretes continuam pendentes.

Em04/11/2026, entre16:38:18 e16:38:38, horário de Brasília, uma tentativa controlada de consulta do relatório **Relação de Fretes Detalhada**, template6389, recebeu a descrição dos campos normalmente e falhou na primeira página de dados.

| Item | Observado |
| --- | --- |
| Site | rodogarcia.eslcloud.com.br |
| Relatório na interface | https://rodogarcia.eslcloud.com.br/analytics/reports/6389/edit |
| Origem utilizada | API Data Export |
| Método de consulta | GET com corpo, contrato já usado em captura anterior concluída |
| Filtro | freights[service_at] =2026-10-19 -2026-10-19 |
| Ordenação | id asc |
| Página |1 |
| Tamanho solicitado |30fretes distintos |
| Consulta dos campos | HTTP200 |
| Consulta dos dados | HTTP422 |
| Resposta da ESL | errors:[timeout]; formatted_errors:[Tempo limite excedido] |
| Requisições efetuadas |2, sem repetição automática |
| Intervalo | mínimo3segundos após cada resposta; uma requisição por vez |
| Prazo por requisição |30segundos |
| Dados recebidos nesta tentativa | nenhum registro |
| Identificador da execução local |15ee6752-8937-431e-a2f8-36d728bb7c28 |

O processo local encerrou em39,6segundos. Uma captura anterior desse mesmo dia concluiu946ocorrências, correspondentes a850fretes distintos. Em outra tentativa, no dia12/10, a primeira página trouxe uma ocorrência e a página seguinte retornou a mesma mensagem de timeout. Esses resultados não determinam a causa interna da falha.

Para investigação do fornecedor, falta esclarecer o motivo do timeout na consulta de dados e as condições necessárias para concluir a paginação de forma estável. O arquivo não contém token, senha, IDs de negócio ou valores dos fretes. As respostas e recibos originais permanecem retidos em armazenamento privado do projeto.
