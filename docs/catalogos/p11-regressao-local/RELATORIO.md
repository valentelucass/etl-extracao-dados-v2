# Pendências P10/P11/P12/P14/P15/P21 após0220

P11_LOCAL_REGRESSION_RECORDED. C corrigido; B comprovado na parcela local;
A e parcelas físicas B aguardam autorização específica. As seis P não recebem
conclusão integral:16 requisitos externos continuam abertos e nenhum responsável
nominal/aceite novo foi fornecido.

## Provas novas e provas reutilizadas

Suíte unitária inteira: **2162 casos, 247 classes, zero falhas/erros e 4
skips históricos**. Comparação exata de identidade/multiplicidade e skips com
a prova P07 anterior. Compilação de652 fontes/515 fontes de teste, Enforcer,
Spotless e Checkstyle passaram. JAR e oito libs construídos; não executados como
aplicação. Sem perfil IT, SQL, DLL nativa ou gate de cobertura. ForkCount=0 para
manter a JVM sob guard; recursos copiados com igualdade por hash na cópia local.
POM público inalterado. Limites900s por execução local e512MiB.

Reutilizados scan0220 de13 dependências/zero achados/erros,205 testes direcionados,
parser/política, B56 com tipos observados e provas locais SQL/principals. Nada disso
foi repetido como pendência. Evidências/recibos fixados em resultado.json.

## Correção de rede

A matriz histórica permanece intacta e é predecessor da matriz-atual.json nova.
O campo agregado networkCalls foi removido da nova revisão por misturar rodadas.
Preparação offline:zero chamadas externas. Rodada pública0220:rede usada, **sete
GETs controlados com recibo**, mas o total de requisições internas Maven/NVD e
retries não foi instrumentado, portanto **null/TOTAL_NAO_INSTRUMENTADO**. Não
contar41.788 registros NVD, reservas, tentativas ou checks de socket como HTTP.
Rodada atual:zero rede externa/SQL, com fixtures HTTP loopback efêmeras.

## Saldo técnico e externo

[Delta P07/P08](DELTA-P07-P08.md) identifica seis pins históricos do pacote,
DLL nova ausente no cache e cada prova que precisa de ordem própria. A fonte
do requisito é o pedido anexado A/B e os critérios P07/P08 da trilha. Usuário
autorizante fornece a ordem; DBA/Operações sustentam o alvo local aplicável.

| P | Requisitos externos abertos | Responsável por papel |
| --- | --- | --- |
| P10 | G02-PUBLICACAO, G02-CI | Owner do repositorio |
| P11 | FEED-BASELINE | Responsavel de seguranca |
| P12 | G05-AMBIENTE, G05-RETENCAO, G05-RECUPERACAO | DBA, Operacoes, Seguranca e Compliance |
| P14 | G03-10633, G03-8636, G03-4924, G03-6392, G03-RASTER, G04-SEMANTICA | Fornecedor e owner de dados por fonte; Negocio e owners de referencias e consumidores |
| P15 | G04-REFERENCIAS, G04-RATIFICACAO | Negocio e owners de referencias e consumidores |
| P21 | G04-DIMENSOES, G04-FROTA | Negocio e owners de referencias e consumidores |

São17 requisitos rastreados, **16 abertos**: FEED-ACHADOS continua atendido no scan
registrado. A matriz conserva requisito exato, origem, evidência anterior, saldo
e nominalOwner=null para cada um. Os inputs exigem os artefatos/decisões dos
respectivos owners; preparação ou autorização de download não são aceites.
G01/P09 é dependência condicional, sem reavaliação ou solicitação de segredos.

## Falhas, preservação e recuperação

unit01 recusou subprocesso do plugin de recursos. Cópia manual byte a byte
corrigiu o harness isolado. unit02:2162 casos, zero falhas e um erro na limpeza
da fixture sintética .env.local; o guard impedia o JUnit de enumerá-la. unit03
foi interrompida pelo executor após conferir que a primeira correção usava o
sufixo errado. unit04 repetiu toda a suíte e passou. Logs, XMLs vermelhos e
recibo de parada preservados. Primeiro javac de GuardProbe omitiu classpath;
corrigido localmente. Guard final:dez recusas e duas permissões sintéticas.

Não houve alteração do runtime, POM público, schema, migrations, produção,
fontes de negócio, credenciais, publicação, deploy, paridade real ou cutover.
Contadores39/45 e67/115 intactos. Inventário inicial3739 arquivos; snapshots
exatos dos quatro deltas em docs/continuidade/historico/p11-regressao-local/.
Recuperação: revisar somente os quatro deltas contra esses snapshots e preservar
qualquer edição posterior; não restaurar o worktree inteiro.

Fechamento: STATES → trilha/matriz → validadores → checkpoint0222 com hash
conferido → RETOMADA. A cadeia histórica é validada pelos seus bytes anteriores;
manifestos/ledgers antigos não foram regenerados para ocultar drift.

Validações finais: matriz10 e sucessão10 contraprovas PASS; cadeia anterior14+11
e trilha ampla PASS. Scans delimitados:6 textos do catálogo,299 validadores
e222 checkpoints, zero achado. Recibos em validacoes.json. O scan do espelho
em target retornou zero candidatos e foi descartado como prova. O primeiro
check de diff confundiu exit1 normal do no-index com whitespace; corrigido
e diff/UTF-8/sufixos históricos conferidos. Nenhuma falha foi promovida a PASS.
