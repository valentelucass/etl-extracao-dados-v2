# ADR 0042 — Manifestos: texto convertido na borda e coorte local limitada

Decisão local adotada no B61, 10/09/2026. Manutenção de V2-015/V2-026/V2-050;
MAN-01–MAN-07 e migrations V001–V024 permanecem vigentes.

`ManifestoRootReducer` interpretava e serializava status com Jackson e consumia
todo o `Iterable` antes de verificar a coorte. A busca de consumidores encontrou
somente testes: nenhum chamador do redutor em `src/main/java`.

O mapper de entrada agora fornece `ManifestoFieldValue.text(texto, representação)`.
O texto já interpretado serve à precedência; a representação canônica permanece
opaca para o domínio e vem do canonicalizador existente. O redutor devolve o valor
vencedor, preservando escaping e status desconhecido. Ausência e nulo continuam
separados; o domínio não importa biblioteca JSON nem interpreta a representação.
Os campos opacos de staging e seus contratos JDBC não foram substituídos.

O redutor admite de 1 a 100 observações físicas de uma única raiz. A fonte é o
contrato local já expresso por `ManifestoStageRecord.MAXIMUM_PAGE_SIZE`, pela
barreira de `ManifestoStageBatch` e pelas justificativas arquiteturais da coorte
de `ManifestoReductionResult`. O teste de página continua fixando 100. Esse teto
limita o utilitário local; não redefine `per`, a expansão física do fornecedor
ou a quantidade histórica de observações de uma raiz. Ao encontrar entrada além
do teto, a redução recusa antes do 101º `next()` e antes da acumulação. Não trunca.

MAN-01/02 preservam identidade e ownership de Pick/MDF-e; MAN-03/05/06 continuam
no mapper e nos contratos de persistência, sem nova relação; MAN-04 mantém frescor,
presença, conflito e precedência; MAN-07 mantém complemento nulo/valor, zero real
e recusa de valores distintos, sem somar métricas. Testes exercitam ordem/replay,
escaping, raiz divergente, quarentena, nulos, limite exato e sequência preguiçosa.

A regra geral de arquitetura analisa a árvore sintática Java nos pacotes
`domain`/`dominio`, cobrindo todos os módulos, imports e referências qualificadas
em corpos de métodos. Contraexemplos de JSON/JDBC/HTTP/aplicação precisam provocar
`AssertionError`; comentários e literais não contam como dependências. A regra
executada contra os fontes anteriores detectou Jackson no redutor.

Alternativas rejeitadas: apenas remover imports e manter parsing artesanal;
adicionar callback JSON ao domínio; truncar silenciosamente; ligar o redutor ao
runtime. A agregação de massa continua set-based no SQL. O utilitário continua
sem consumidor operacional. Rollback local: restaurar juntos mapper, value object,
redutor e testes a partir do inventário B61, sem tocar schema ou dados.
