# Cadeia integral por contratos

Estado: **CADEIA_INTEGRAL_LOCAL_CONCLUIDA**. Implementação e provas locais sobre o checkpoint0154.
A medida continua **39/45** e **67/115**, com as mesmas unidades e critérios. V099 e as
decisões explícitas anteriores são preservadas. [Relatório](RELATORIO.md), [provas](MATRIZ-DE-PROVAS.md)
e [execução local](EXECUCAO-LOCAL.md) descrevem o pacote; selo/readback externos vinculam a entrega final.

## Entrada completa

`java -jar etl-dataexport-v2.jar local-scenario run --input input.json --oracle oracle.json` seleciona
`local-artifact-scenario-v2`. O manifesto declara `LOCAL_ARTIFACT_ROLLBACK`, o alvo exato
`localhost/ETL_SISTEMA_V2_SHADOW`, `EXPLICIT_INTEGRAL_INPUTS_V1`, origem e tenant sintéticos,
janela civil de até 31 dias, `America/Sao_Paulo`, relógio lógico, revisão, de 2 a 32 raízes,
tamanho de página de 1 a 16 e política fiscal explícita. O teto de raízes acompanha os
oráculos e a qualificação local; não é uma afirmação sobre capacidade produtiva.

As seis entradas `COL`, `FRE`, `MAN`, `COT`, `LOC`, `USER` são obrigatórias, além das quatro
expansões, Raster, relações, referências, suplementos e sweep. Cada arquivo é referido por caminho
relativo confinado e SHA-256; os bytes são conferidos novamente ao consumir. O modo completo
recusa entradas ausentes. As fábricas de laboratório continuam disponíveis para os modos
históricos explicitamente identificados.

`local-capture-pages-v1` declara família, contexto, revisão, fingerprint do contrato, limites,
contagem física esperada, completude e páginas. Data Export termina com página vazia; o limite
de Coletas/Fretes se refere às entidades distintas da página, preservando as linhas expandidas.
Usuários conserva `USERS_SNAPSHOT`, `enabled=true`, páginas de 20, modos BACKFILL/REPLAY e
cursor retornado pela página anterior. Nenhum filtro incremental por `updatedAt` foi criado.

Fretes fornece os mesmos arquivos e a mesma release `integral-freight-pages-v1` aos caminhos
relacional e de dependência das expansões. O contrato reúne os campos já consumidos por esses
caminhos; a V101 exige fingerprint e associação ao mesmo cenário antes da captura relacional.

## Apoios e consumidores

| Entrada | Consumidor existente |
|---|---|
| Páginas COL/FRE/MAN | Extratores Data Export, auditoria, staging e `LocalRelationalRuntime`/runtimes analíticos |
| Páginas COT/USER | `LocalAnalyticQuotesRuntime`/`LocalAnalyticUsersRuntime`, gates de contrato e control plane |
| CAP/FAT/INV/SIN | `ExpansionArtifact`, `LocalExpansionRuntime`, aplicação e reconciliação SQL |
| FRE/LOC de dependência | `LocalExpansionDependencyRuntime` e relações declaradas |
| Raster | `RasterArtifact`, parser existente e `LocalRasterRuntime` |
| Referências | Importadores JDBC de expansão, dimensões, frota, regiões e tarifas |
| Suplementos | Repositórios tipados de termos, vínculos, atributos, composição e documentos fiscais |
| Esperados | Comparador tipado das 19 saídas e oráculo SQL independente dos cinco fatos |
| Sweep explícito | Quatro capturas reais, conferência SQL V102 e preview das 33 responsabilidades |

O manifesto de referências exige revisão, vigência cobrindo a janela e políticas explícitas.
O manifesto de suplementos exige nove grupos: termos financeiros, dimensões, vínculos
relacionais, atributos de Fretes, atributos de Coletas, estados de Manifestos, relações de
Fretes, composição de Manifestos e vínculos fiscais. Chaves fornecidas são resolvidas contra
as capturas do próprio cenário. Série fiscal ausente permanece ausente; não é preenchida por
uma constante de laboratório.

A V100 admite namespaces sintéticos limitados e amarra origem/tenant à execução analítica e
às execuções relacionadas. O modo integral registra explicitamente Data Export e GraphQL
para essa origem dentro da transação local. As decisões de negócio das migrations anteriores
não são redefinidas por essa associação.

`local-collection-sweep-v2` declara origem/tenant, revisão, data, release, universo e quatro
listas de páginas. Cada chave tem sua quantidade física esperada; perder uma página ou
redistribuir linhas entre raízes, mesmo conservando o total, impede o recibo. A V102 e o
gateway Java recusam apply de ausência para esse universo. O preview conserva os estados
nominais do catálogo existente, inclusive as responsabilidades não aplicáveis ou bloqueadas.

## Oráculos e provas

Os conjuntos A/B são elaborados antes do SQL, com períodos, IDs, relações, ordem e valores
distintos. Os esperados vêm de regras separadas e tabelas declaradas; nenhuma consulta de
produção alimenta o esperado. O contrato `local-sql-oracles-v1` compara tuplas tipadas, presença,
precisão e cardinalidade. Exceções técnicas são restritas a UUID da execução, instante observado
limitado ao intervalo da execução e proveniência conferida contra wire independente.

A cadeia completa passou nos conjuntos A2/B24 e no JAR extraído. Contraprovas cobrem
entradas faltantes, relações inválidas, oráculo errado, replay, isolamento, resiliência e
sweep. A regressão final preserva todos os IDs de teste da base e os quatro skips
históricos. Os recibos de tentativas falhas, correções e resultados finais são preservados
na rodada e vinculados em [provas.json](provas.json).

## Limites externos

V2-041 permanece vigente: sem segredos, chamada autenticada, execução da V1, produção, deploy
ou cutover. SQL usa somente autenticação Windows no shadow local. Dados de prova são
transacionais, com commit bloqueado e rollback. As migrations V2 têm aplicação local
explicitamente autorizada. G01–G08 permanecem no catálogo nominal de pendências externas;
provas sintéticas não ratificam contratos reais ou referências de negócio.
