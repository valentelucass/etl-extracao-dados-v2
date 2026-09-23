# ADR 0043 — Coletas: contrato de captura e forma definida pelo release

Decisão local de B62 em 10/09/2026, após o pedido do usuário de implementar a
solução. Regra técnica **COL-SHAPE-01**, responsabilidade de engenharia. Nenhuma
equivalência de negócio nova; responsável nominal pelos aceites de paridade
continua pendente. Mantêm-se COL/ADR0023, identidade INTEGER, status e frescor.

O problema tinha três partes: o release V2-025a descrevia `/data` e quatro campos
de resposta; a composição HTTP fixava a mesma forma; os metadados esperavam
tipos declarados e `scopes.by_updated_at`. A fonte observada em B62 retorna
array na raiz, anuncia 31 campos sem tipos declarados e seis filtros, incluindo
`by_updated_at`. O prefixo `scopes` pertence ao parâmetro da requisição.

`DataExportColetasContractCatalog` fornece a revisão
`2026-09-10.b62-coletas-scalar-capture.1`. A resolução corrente de Coletas usa
essa revisão quando não existe recurso administrado de laboratório.
`DataExportContractObservationConfiguration.forRelease` deriva forma e chave
do release e rejeita outro template, fonte, chave ou raiz incompatível. O gate
existente confere também o fingerprint da configuração e a fronteira de paths.
O binding e o plano contêm o fingerprint do novo release: uma ocorrência com
contrato anterior não se transforma silenciosamente na nova revisão.

Os 31 nomes e os seis filtros são exatos, apoiados pelo `/info`. `select` é
preservado como tipo declarado externo com fingerprint próprio, sem convertê-lo
em string. Os nomes adicionais continuam proibidos. A resposta exige `id`
INTEGER presente e não nulo. Os demais campos admitem ausência/nulo, mantendo
a distinção já prevista pelo mapper. `sequence_code` e o candidato de Manifesto
admitem somente INTEGER quando possuem valor; status, motivo e cinco campos
temporais admitem STRING. A revisão não adiciona `status_updated_at`, ausente
na amostra anterior, nem inventa uma relação ou watermark a partir de updated_at.

Os 21 campos restantes são preservados como JSON escalar: STRING, INTEGER,
NUMBER ou BOOLEAN. **Essa é uma política local de captura sem coerção, não uma
medição dos tipos desses campos na fonte.** O mapper já conserva o payload
canônico sem consumir tais colunas nas regras de negócio. Objetos e arrays
continuam recusados, inclusive vazios; nenhum wildcard ou container opaco foi
aberto. Uma coluna só poderá adquirir interpretação de negócio após decisão e
teste próprios. A política evita inventar tipos do fornecedor a partir de nomes.

Exemplo sintético: cinco linhas para dois IDs com `per=2` são preservadas como
cinco observações físicas. `invoices_weight="0001.2300"`, booleanos, decimal,
texto vazio e nulo permanecem com seus tipos e valores. `id="1"`, terceiro ID,
campo desconhecido, container ou drift dos campos consumidos recusam a página
antes do staging. Ausência de página terminal não produz sucesso, mesmo se uma
página anterior já foi entregue ao staging. Fim local não prova snapshot.

O catálogo histórico, fixtures B58 e contratos administrados B54/B55/B60 ficam
intactos. `DataExportTemplate.promotableResponseForm()` integra a fotografia
histórica de semântica do template; não seleciona o release corrente. A seleção
efetiva ocorre na composição e é selada pelo binding contratual.

A alternativa de envolver o array real artificialmente em `data` foi rejeitada:
ocultaria a forma original da observação. Também foram rejeitados remover o
gate, aprender automaticamente o contrato em cada chamada ou inferir tipos
de negócio dos campos não consumidos.

Prova: `DataExportColetasContractTest` exercita HTTP loopback → parser estrito →
metadata/gate → paginação → mapper → staging em memória; testa preservação e
recusas. `RuntimeOperationalExecutionTest` verifica seleção e execução pelos
handlers oficiais com JDBC sintético. Não é prova SQL física ou replay real.
O diagnóstico adicional confirmou `/info`, mas `/data` respondeu429. A rodada
parou e não foi repetida; falta validar esta revisão sobre dados reais.

Rollback: restaurar os deltas desta manutenção pelo `before/` e usar o release
histórico em nova ocorrência apropriada. Preservar artefatos e fingerprints
antigos; não reclassificar ocorrência, alterar schema, dados ou ledger histórico.
