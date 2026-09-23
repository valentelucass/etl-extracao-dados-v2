# Sonda financeira — caminho técnico corrigido

Data: 22/09/2026.

## Correção validada localmente

O controlador recusava uma lista de atributos vazia de Coletas como se ela
contivesse uma chave nula. A causa foi isolada por marcadores sanitizados na
inserção da identidade e corrigida em `New-EntityIndex`: nomes nulos ou vazios
agora não entram na lista nem nos mapas de atributos.

O parse PowerShell e os autotestes padrão e com três páginas passaram sem rede.
Eles cobrem identidade nula, lista de atributos vazia, formato de `edges`, nó
GraphQL, item ausente, grupo de receita ausente, página terminal e query estática.

## Resultado sanitizado

A ordem independente para 07/09/2026 consumiu seis das dez chamadas permitidas,
terminou com exit code 0 e sem motivo de parada. Coletas Data Export terminou em
duas páginas com 19 entidades e a auditoria GraphQL de Coletas terminou em uma
página com a mesma contagem; conjuntos de chave natural e identificador canônico
foram iguais. Não houve borda inválida nem falha de processamento.

Fretes Data Export, Faturas 4924 e a auditoria GraphQL de Fretes terminaram
vazios. Por isso vínculo Frete–Coleta, receita, CT-e, Fatura 4924 e equivalência
de campos financeiros continuam sem prova de negócio. A igualdade de Fretes
vazios não é promovida a paridade de negócio; a equivalência financeira continua
falsa.

Não houve retry, fallback, escrita, banco, DDL/DML, commit, agendamento, deploy
ou corte. Nenhum segredo, URL, payload, cursor, identificador, hash de
identificador, cabeçalho sensível ou dado de negócio foi preservado.
