# Sonda financeira — limite seguro de três páginas

Data: 22/09/2026.

## Correção aplicada e validação local

O controlador financeiro recebeu a única ampliação aprovada nesta frente:
`MaximumPagesPerSource`, limitado a duas ou três páginas e com padrão de duas.
Endpoints, métodos, autenticação, campos de auditoria, resposta máxima e teto de
dez chamadas não mudaram. O parser PowerShell e os autotestes padrão e de três
páginas passaram sem chamadas de rede.

## Resultado sanitizado

A nova ordem para 01/09/2026 usou três das dez chamadas, todas Data Export de
Coletas (6908). As páginas foram válidas e a identidade pôde ser verificada. O
agregado foi de 361 linhas físicas, 227 entidades distintas, 134 linhas físicas
repetidas e zero entidades inconsistentes. A terceira página não era terminal e
a sonda parou com `DATA_EXPORT_PAGE_LIMIT_REACHED`.

Fretes, Faturas e GraphQL não foram chamados. Logo, vínculo Frete–Coleta, CT-e,
receita, comparação de conjuntos e campos financeiros continuam não comprovados.
O resultado mostra que esta data excede o envelope seguro de três páginas com
teto conjunto de dez chamadas; não é erro da ESL nem ausência de dados.

Não houve retry, fallback, escrita, banco, DDL/DML, commit, agendamento, deploy
ou corte. Nenhum segredo, URL, payload, cursor, identificador, hash de
identificador, cabeçalho sensível ou dado de negócio foi preservado.
