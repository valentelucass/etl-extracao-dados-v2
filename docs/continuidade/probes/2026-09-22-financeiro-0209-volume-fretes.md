# Sonda financeira — volume de Fretes em 02/09

Data: 22/09/2026.

## Resultado sanitizado

Após a quarta página ter encerrado corretamente Coletas, a sonda ampliada
percorreu seis páginas válidas de Fretes. A sexta página ainda não era terminal:
houve 634 linhas físicas, 600 entidades distintas, 34 linhas físicas repetidas,
identidade verificável e nenhuma entidade inconsistente.

Coletas terminou com 376 linhas físicas, 239 entidades distintas, 137 linhas
físicas repetidas, identidade verificável e nenhuma entidade inconsistente.
Faturas 4924 e as auditorias GraphQL não foram chamadas, pois a parada ocorreu
na fonte de Fretes. A rodada não prova ausência de registros nem vínculo
Frete–Coleta, receita, CT-e, Fatura ou equivalência financeira.

## Decisão

O aumento temporário para seis páginas e 25 chamadas foi removido depois de
demonstrar que não existe ainda uma página terminal para Fretes nessa partição.
A sonda voltou ao limite de quatro páginas e dez chamadas.

Para uma próxima prova financeira, é necessário uma partição menor
formalmente suportada e com cobertura/deduplicação definidas pela fonte, ou uma
data fechada de negócio já conhecida por conter Fretes e caber no envelope. O
filtro complementar de atualização não pode ser usado como partição enquanto
sua semântica não estiver comprovada.

Não houve retry, fallback, escrita, banco, DDL/DML, commit, agendamento, deploy
ou corte. Nenhum segredo, URL, payload, cursor, identificador, hash de
identificador, cabeçalho sensível ou dado de negócio foi registrado.
