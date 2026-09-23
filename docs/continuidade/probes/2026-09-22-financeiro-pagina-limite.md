# Sonda financeira — parada por limite de páginas

Data: 22/09/2026.

## Escopo e autorização

O usuário confirmou Data Export como fonte do V2 e GraphQL apenas como auditoria
transitória. A sonda financeira autorizada foi executada para a data fechada
01/09/2026, com Data Export 6908, 6389 e 4924 e queries GraphQL estáticas
somente se as etapas anteriores permitissem. O teto era de dez chamadas seriais,
`per=100`, três segundos entre chamadas, timeout de até 30 segundos e resposta
máxima de 10 MiB.

## Resultado sanitizado

Foram usadas duas das dez chamadas, ambas para Coletas (6908). As duas páginas
foram válidas e a identidade pôde ser verificada. O agregado observado foi de
325 linhas físicas, 200 entidades distintas, 125 linhas físicas repetidas e
zero entidades inconsistentes.

A segunda página não era terminal. A sonda limita cada fonte a duas páginas e
encerrou corretamente com `DATA_EXPORT_PAGE_LIMIT_REACHED`; portanto, não
consultou Fretes, Faturas ou GraphQL. Nenhuma relação Frete–Coleta, CT-e,
receita, conjunto de IDs ou campo financeiro foi confirmada. A parada demonstra
apenas que esta data excede a capacidade segura da sonda financeira, não erro da
ESL nem ausência de dados.

Não houve retry, fallback, escrita, banco, DDL/DML, commit, agendamento, deploy
ou corte. Nenhum segredo, URL, payload, cursor, identificador, hash de
identificador, cabeçalho sensível ou dado de negócio foi preservado.
