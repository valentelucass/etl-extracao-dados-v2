# Checkpoint0086 — campos completos e contratos limitados

EM_EXECUCAO A–N. Predecessor0085 SHA256 c4536f73e653b9c48d9ba38eacfb8e386e4435f5e6876d48ebf58f8c42e5797c.
Pedido e inventário2549: target/macrobloco-expansao-20260912-01/; unidades45 congeladas.
Mesmo escopo adotado: localhost/ETL_SISTEMA_V2_SHADOW, Windows, DDL versionado
fora de IT e DML sintético rollback-only. Sem dados reais, API, grants ou aceite.

verify-01 terminou com 1616 testes,2 falhas de arquitetura,0 erros,4 skips
históricos. Coleções limitadas receberam APIs Page/Batch/Limited e sete
entradas explícitas no registro de arquitetura, com limites e consumidores;
nenhum predicado foi relaxado. directed-bounded-01:54/0/0/0.
Matriz151 campos e contrato das seis consultas (51/59/42/52/24/31 colunas)
construídos a partir dos catálogos e metadata SQL; fontes observadas separadas
de bindings sintéticos e formatos ainda não ratificados.

physical-fields-01:3 passam,Sinistros falha CK_expansion_array_bound. V038
omitira rcfdc/rctac/rctrc da lista; V051 corrige lista, conserva posições0–31.
V051 qualificada por rollback e instalada em schema-sinistro-arrays-install-01;
V038–V051 imutáveis,próximaV052. physical-fields-02:4/0/0/0, todos151 campos
com readback tipado independente; agregados antes/depois iguais.
Falhas e logs preservados. ADR0049 EXP24 e baseline/validadores atéV051.

Nenhum processo ativo ao gravar. Próxima campanha verify-02 será reservada
pelo runner com900s/heap512/Java17offline. Consultar exit/log/process antes de
repetir; nenhum DDL/DML paralelo. JAR em processo, SQL062, scanners e entregaN
ainda pendentes. Nenhum encerramento integral ou aceite real declarado.

Próximas ações: (1) verify completo e correções necessárias; (2) JAR/SQL062/
scanners/validadores e fonte-versus-build; (3) quadro45, relatório/diff/manifesto
e sucessão exata com snapshots. Prossiga após compactações sem pedir continue.
