# ADR 0074 — Relógio codificado pela origem nos horários de Sinistros

24/10/2026. RG-SIN-019,owner técnico Root;owner de negócio não declarado.
Leitura técnica de60 linhas6392/janela01–23/10 mostrou occurrence_at_time como
ISO offset date-time em60/60 valores e finished_at_time nesse formato em35,
com25 nulos. O parser LocalTime anterior recusava todos esses valores.

Nos dois paths nomeados,aceitar LocalTime existente ou ISO OffsetDateTime estrito,
projetando somente a hora escrita na origem,sem converter paraUTC/São Paulo.
A data e o offset originais continuam integralmente em ExpansionValue.raw.
Não inferir que a data embutida equivale a occurrence_at_date/finished_at_date,
nem usar essa projeção como instante ou para cálculo de duração. O parser time
genérico anterior permanece igual para outros consumidores.

Exemplo sintético:2036-03-17T07:08:09.123-03:00 produz relógio07:08:09.123 e
bruto intacto. O teste também usa outro offset para impedir conversão implícita.
Formato inválido ou date-time sem offset permanece issue com bruto preservado.

Sete comentários reais têm1025–2241 caracteres,o banco tipado existente limita1024
e o bruto permite4000. Não truncar,não ampliar limiteSQL nem fingir campo válido:
esses campos conservam o bruto e a issue de qualidade. Essa diferença é separada
da correção temporal. Sem DDL/sem publicação/sem aceitação global de identidade.
