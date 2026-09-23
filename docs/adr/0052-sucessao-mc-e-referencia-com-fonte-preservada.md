# ADR0052 — Conjunto MC explícito e revisão tarifária independente

19/09/2026. Escopo: recorte P03 autorizado pelo usuário, sombra local sintética.
Responsável de negócio não informado. Origem: diagnósticos da tentativa06 e
pedido de conclusão P03. Não define completude do fornecedor.

## SEQ-MC-01

Um suplemento integral de relações declara o conjunto completo de componentes
MC para cada origem nele incluída. A declaração ocorre depois de todos os seus
lotes. A maior revisão declarada aposenta logicamente evidências anteriores
daquela origem, preservando bindings, links e resoluções históricos. Origens
ausentes não são aposentadas. Captura e bind ordinários continuam sendo parciais;
sem declaração, permanece a precedência por componente. CF não muda.

Exemplo: origem M com componente A na revisão1 e conjunto explícito B na revisão2
conserva A como SUPERSEDED e avalia B. Um simples bind de B na revisão2 conserva A.
Dois destinos concorrentes na mesma revisão continuam CONFLICT por ONE_TO_ONE.
Não se escolhe identidade arbitrária nem se relaxa a linhagem.

Consumidores: V103, JdbcRelationalLaboratory e DeclaredAnalyticSupport. O contrato
fechado do suplemento integral, seus hashes e a leitura de todos os lotes são
a origem desta declaração local; páginas incrementais não a concedem.

## SEQ-REF-01

Uma observação com frescor e conteúdo iguais pode aplicar uma release tarifária
diferente já validada pelo kernel. A atualização toca somente atributos de tarifa
e gera recon.cotacao_reference_revision com execução, cotação, releases anterior
e nova e proveniência da fonte. O kernel de publicação conserva NO_OP da fonte.
O snapshot analítico ganha revisão encadeada quando a release muda; igualdade de
fonte e release conserva o snapshot. Fonte obsoleta não revisa tarifa. Empate de
frescor com conteúdo divergente continua recusado.

Exemplo: página S/frescor T/release R1 → S/T/R2 gera uma revisão de referência;
repetir S/T/R2 é no-op. Não se inventa T2. V104 evolui as definições efetivas de
V022 e V100, preservando seus fences de contexto. V011/V085 não são editadas.

## Provas e recuperação

Contraprovas: substituição explícita versus omissão parcial, origem omitida,
conflito contemporâneo, replay, mudança de release com fonte preservada e stale.
Qualificação usa upgrade e sufixo do baseline sobre schema102, em transações
revertidas, com catálogo e contagens antes/depois. Não equivale a recriação física
do banco. Instalação separada confirma apenas DDL versionado; testes Java nunca
aplicam migrations e permanecem rollback-only. Nenhum grant novo é concedido.
Falha antes do COMMIT reverte DDL; após instalação, recuperação exige migration
compensatória revisada, sem editar/reaplicar V103/V104 ou apagar histórico.

Provas executadas: p03-campaign-sql-07, p03-regression-sql-01 e
p03-relational-counterproof-02. A falha de preparação do teste em01 e sua correção
permanecem nos recibos; qualificação composta e limitações registradas em STATES.
