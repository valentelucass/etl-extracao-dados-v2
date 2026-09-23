# Paginação 6906 — expansão física limitada e envelope inválido — 22/09/2026

## Ordem

Caracterização read-only autorizada para Cotações: metadado e páginas 2–7 da
janela fechada já observada, com `per=100`, teto de sete chamadas, intervalo de
três segundos, timeout de 30 segundos, resposta máxima de 10 MiB e trava
interprocessos V2. A expansão física só seria aceita quando todas as linhas
tivessem `sequence_code` inteiro, escalar e não nulo, com no máximo 100 valores
distintos. Parar no primeiro HTTP não-2xx, 429, JSON/envelope inválido, limite
de entidade não verificável, resposta acima do limite ou página não terminal no
teto. Sem retry, fallback, GraphQL, banco, escrita, DDL/DML, agenda, deploy ou
corte.

## Resultado sanitizado

O metadado e as páginas 2–4 responderam HTTP 200. A página 2 teve 112 linhas
físicas e 100 valores distintos da candidata; todas as ocorrências estavam
presentes, não nulas, escalares e inteiras, portanto a expansão foi aceita
somente como limite operacional da página. A página 3 teve 49 linhas físicas e
48 valores distintos para a mesma candidata.

A página 4 retornou HTTP 200, mas o envelope de dados não era lista válida. A
sonda parou corretamente com `DATA_ENVELOPE_INVALID` após quatro chamadas; as
páginas 5–7 não foram consultadas. Não foram retidos payload, ID, URL,
cabeçalho ou dado de negócio.

## Limite da evidência

O resultado prova apenas que a página 2 tem expansão física dentro do teto
operacional observado e que a sequência falhou por envelope inválido na página
4. Não prova identidade canônica, grão oficial, página terminal, completude,
estabilidade, contrato/release, tarifa, oráculo, paridade ou aceite.
