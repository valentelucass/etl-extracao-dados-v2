# Sonda financeira — limite seguro em 02/09

Data: 22/09/2026.

## Ordem e limites

A solicitação atual do usuário acionou a sonda financeira para a data fechada
02/09/2026. Foram usados apenas Data Export 6908, 6389 e 4924 por consulta de
leitura, e GraphQL somente como auditoria transitória que não chegou a ser
chamada. O teto foi de dez chamadas seriais, três páginas por fonte, três
segundos entre chamadas, até 30 segundos por chamada e 10 MiB por resposta.

## Resultado sanitizado

A sonda consumiu três das dez chamadas e parou corretamente no limite de
páginas de Coletas. As três páginas eram válidas, continham 376 linhas físicas,
239 entidades distintas, 137 linhas físicas repetidas, nenhuma entidade
inconsistente e identidade verificável; a terceira página não era terminal.

Fretes, Faturas 4924 e as auditorias GraphQL não foram chamados. Portanto a
rodada não informa ausência de Fretes nem confirma vínculo Frete–Coleta, receita,
CT-e, Fatura ou equivalência financeira. A data apenas excede o envelope
financeiro aprovado.

Não houve retry, fallback, escrita, banco, DDL/DML, commit, agenda, deploy ou
corte. Nenhum segredo, URL, payload, cursor, identificador, hash de
identificador, cabeçalho sensível ou dado de negócio foi registrado.
