# 0688 — Fretes: parcial conferido e reconciliado sem nova consulta

## Entrega verificada

Root encerrou a conferência independente da tentativa descrita no
[checkpoint 0687](0687-fretes-teste-real-sem-timeout-e-parada-native.md).
O Original 68980 saiu 0 em 4.437 ms, pico 83.660.800 bytes: manteve e verificou
os oito pares RAW/recibo, vinculou a medição anterior de 456 ocorrências/420 IDs
e o journal às respostas completas e conferiu os códigos admitidos. O gate
bloqueante precede a abertura/reserva da próxima consulta; a falha Native ocorreu
antes da resposta de lease nove. O fechamento físico e essa ordem comprovam que
o adaptador não iniciou uma nona invocação. Isso não mede transmissões internas
do cliente HTTP: wire continua UNKNOWN. Nenhum valor de negócio foi publicado.

A assinatura Ed25519 existente foi verificada de fato com a chave pública já
admitida, sem ler chave privada ou emitir assinatura. Node próprio 50560 saiu 0,
com nascimento, stdin e EOF conhecidos. Worker e pai fecharam com sucesso,
pós-verificação dos pins e restauração do estado.

O pacote host RawOnly FROZEN02/98A17350 passou os 13 casos sintéticos em cada
uma das quatro implementações reais extraídas por AST. Original 55744/0,
8.064 ms, pico 141.471.744 bytes, sem filhos/RAW/CIM/SQL/HTTP. Esses testes
verificam o contrato e rejeições; não aprovam cobertura dos dados. O teste de
assinatura simulada da fixture permanece distinto da verificação real acima.

## Reconciliação atual

Prova independente Root B24F0540 (12.367 bytes), efeito E7A7B124 (1.375 bytes)
e aceite host 9E7B7107 estão nos `outputs/` do workspace Supervisor. O Root
validou o ramo RawOnly contra as provas reais e acrescentou somente dois fatos
ao canônico, preservando lexicalmente todos os valores históricos e a ACL.
A primeira invocação do script de reconciliação falhou no parser antes de
qualquer execução; o espaçamento de catch foi corrigido antes da única mutação.

**Canônico atual:**
`035F280C28E48448D0AD23E3EA13C07E4C7ABA3810DFA9C75432F49871426904`,
714.414 bytes, ACL B4E214EB, Root 0658, vaga vazia. São 17 capturas completas e
seis parciais reconciliadas. Não restaurar B369 ou repetir os grants consumidos.

A cadeia nova `root-fretes-closed-partial-chain0687-01.json` tem quatro passos,
15 reservas adicionais (2+3+2+8), esquema reservado V2, pin 2D469D54/11.207 bytes.
Controle atual B3CB047D/27.869 bytes e typed 56507785/1.967 bytes passaram
FctxControl/FctxFrontier com o último par completo histórico preservado. Aceite
de reconciliação E0934463/2.015 bytes. Não houve nova Source, HTTP ou SQL.

O controller real continua com audit falso; retry UNKNOWN e os cinco contadores
null foram preservados. Não foi criado capture parcial Java, JSONL ou manifest
fictício. Os oito efeitos agora estão contabilizados, sem aprovar completude.

## Limite e próximas ações

A recuperação de timeout passou nos testes locais, mas a tentativa real não
teve 422, portanto não exercitou a repetição na ESL. A causa interna da
intermitência permanece desconhecida. A correção de encerramento gracioso e o
suporte Java explícito à cadeia RawOnly estão sendo preparados pelo escritor;
o Java atual rejeita o novo esquema antes de uma consulta. **Nenhuma nova
Source foi admitida.** Fretes segue 0/3 semanas; Coletas mantém 3/3 com conteúdo
integral das 31 colunas. V1, SQL e produção permanecem sem alterações.

1. Revisar e qualificar o suporte Java RawOnly e o encerramento gracioso.
2. Com os componentes atuais qualificados, preparar nova unidade finita com
   controle 035F/chain 2D469D54 e seleção Native então atual.
3. Concluir a captura e comparar o conteúdo das 114 colunas com Excel próximo,
   até três semanas integrais aprovadas.
