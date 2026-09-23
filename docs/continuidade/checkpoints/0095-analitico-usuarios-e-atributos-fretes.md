# 0095 — Usuários integrado; atributos tipados de Fretes em prova

EM_EXECUCAO A–N, sem encerramento intermediário. Predecessor0094. V001–V057
instaladas e imutáveis; baseline acompanha. Inventário2704/snapshots/request e
logs em target/macrobloco-analitico-20260912-01/. Construção permanece32/45.

Physical-users-05 passou2IT, zero falhas/erros/skips, antes/depois iguais:
21usuários atravessam parser/gate Relay, LocalUsuariosRuntime, dispatcher,
staging, DQ de fingerprint real, promotion/current/history e SQL19; replay21noops,
update1/noop20 com NULL explícito; erroEnvelope exige GraphQlResponseException.
As outras cinco dimensões haviam passado5IT em physical-dimensions-02.

Descobertas corrigidas: ColetaTemporalLaboratorySession reportava loginTimeout0;
agora normaliza a URL local para5s e reporta5, mantendo sessão rollback-only.
Teste não pode usar relógio técnico2036 no runtime operacional: o skew foi
recusado pelo SQL. Usa Clock.systemUTC, conservando janela de negócio2036.
Tentativas physical-users01..04 preservadas; negativo reforçado para causa real.

Implementados: AnalyticUsersSyntheticSource (parser/gate existentes, semHTTP),
LocalAnalyticUsersRuntime, JdbcAnalyticQuality e resource quality-policy.sql;
policy estrita de laboratório não fabrica permits nem altera gates.

Fretes: frete-atributos.json inventaria108atributos da tabela legada,18base/termos
existentes e90 de suplemento sintético explícito (ADR ANA09). Criados modelo
FreightAnalyticAttributes, mapper estrito, AnalyticScalarParser,
FreightSupplementObservation, JDBC batch16, fixture de atributos brutos e V057.
V057 qualificada/instalada (schema-freight-attributes-install-01); contém450colunas
tipadas/presença/wire/raw/erro +binding/versão, FKbase/observação e conflito.
Aviso SQL de largura máxima teórica exige teste dos maiores valores permitidos.
Não repetir os geradores que reescrevem V057 instalada.

PROCESSO EM ANDAMENTO: physical-freight-attributes-01, exec session84312.
Reconciliar exit/log/failsafe antes de repetir. Três IT escritos:90atributos com
metadata/resultados, conflito/invalidade/revisão e binding/imutabilidade.
Ainda não declarados passados. MAT01 e SQL02 ainda NÃO implementadas.

Manutenção Raster: JdbcRasterBatch fecha todos os statements se o construtor
falhar; comparação por campos virou AnalyticFieldComparison, reutilizada pelo
suplemento. Reexecutar testes afetados e HTTP BodyHandler novo. Arquitetura
completa ainda não rodada; novas APIs List limitadas precisam auditoria exata.

Próximas ações:
1. Reconciliar prova dos90atributos e corrigir; testar limites reais de largura,
   parser e HTTP/arquitetura; terminar variantes dimensionais ainda pendentes.
2. MAT01 PE/CB e SQL02 sobre capturas+suplemento+LOC+bindings/ref; depois MAT02/05
   e restantes SQL01..12 (exceto13), preservando as cinco materializações.
3. J–N: composição11entradas, 19consultas, SweepK, modos/recomposição/hidratação,
   testes completos/escala/concorrência/JAR, diff e sucessão exata CheckGuidance.

Não encerrar pelo tamanho da tarefa; continuar após compactações. As pendências
locais necessárias permanecem parte do pedido vigente. Não há gate externo que
impeça implementar/testar o mecanismo sintético positivo.
