# Revisão das correções básicas e contratos de entidades

Resultado: documentação das 11 entradas e índice dos 2.437 IDs conferidos com a construção;
correção FRE-02/FRE-03 implementada e exercitada; nove classes antigas retiradas do código de
produção. O estado de Usuários foi esclarecido como snapshot deliberado. Nenhum aceite externo
ou incremento dos números 39/45 e 67/115.

## Código e motivo

- V099 altera as duas procedures de promoção e compartilha a identificação da transição terminal
  com CT-e omitido. O core e o recibo preservam frescor efetivo, o staging retém observação bruta,
  e o core registra política, execução, origem e instante observados. O nulo explícito continua distinto.
- A limpeza retira os nove tipos de página substituídos por extração completa. Retirados quatro
  arquivos de testes exclusivos (cinco testes); as verificações úteis de capability e contrato
  permanecem. A referência constante de Localização foi ligada ao catálogo comum. Um grafo de
  bytecode não revela todas as dependências de constantes incorporadas pelo compilador; a
  compilação identificou essa referência e ela foi corrigida.
- Allowlists e baseline agora incluem V099; migrations V001–V098 e manifests anteriores estão
  preservados. Os validadores de sucessão usam os bytes históricos para comparar o pacote anterior,
  enquanto o novo manifesto verifica arquivos atuais e remoções declaradas com snapshot.
- O produtor e os consumidores do pacote exigem 99 migrations. A integração reproduziu a
  rejeição indevida da V099 pelo limite 98; o leitor atual aceita o pacote completo e recusa
  índice incompleto mesmo com hashes recalculados. O verificador PowerShell de envelopes
  mantém o conjunto fechado 98/99 para arquivos históricos; o JAR atual exige 99.
- Duas contraprovas adicionais detectaram perda de CT-e por finalização parcial e substituição
  de performance oficial por fallback na primeira candidata. A exceção foi restringida para
  preservar o grupo conhecido e manter a proteção original da performance.

## Provas executadas

| Camada | Resultado |
|---|---|
| Build e retomada delimitada, Java 17, heap 512 MiB | 2037 testes unitários finais; 4 skips históricos; zero falha/erro; cobertura verificada |
| SQL/JDBC opt-in localhost/ETL_SISTEMA_V2_SHADOW | 458 testes em 93 classes; zero falha/erro/skip; rollback confirmado |
| Regressão SQL 063 nas procedures instaladas | 16 casos e repetição da promoção; pré-correção reproduziu STALE_NO_OP; contraprovas adicionais reproduziram perda de informação; pós-correção passou |
| Definições instaladas | 3 corpos correspondem à V099; normalização apenas de newline e cabeçalho CREATE do SQL Server |
| JAR com entradas sintéticas | 9 templates conferidos, consumidores das 11 entradas carregados; snapshot Usuários e 5 casos de Fretes com nulo/omissão distintos |
| Catálogo | 11 entradas e 2.437 IDs; nenhuma reclassificação; 6 contraprovas |

Tentativas falhas ficam na rodada privada: fixture temporal incompleta corrigida; opção
QUOTED_IDENTIFIER da primeira candidata V099 corrigida; referência à constante removida
corrigida; duas allowlists de migrations atualizadas. Verify-03 atingiu o teto de 900 s com
53 classes de integração concluídas; rollback confirmado. Verify-04 executou as 40 restantes,
com as mesmas fontes/migrations, preservando relatórios concluídos. Duas classes de pacote
falharam por schema 98/99. Após corrigir os consumidores, Verify-05 recompilou/empacotou,
repetiu toda a unidade e todas as classes Qualification IT e aprovou a cobertura. O agregado
unitário usa o complemento receipt-final-02 para executar o teste de medição cujo opt-in não
estava habilitado em Verify-05; as classes compiladas são as mesmas, sem JDBC nesse complemento.
O agregado de integração
seleciona o relatório mais recente por classe; as integrações não afetadas de 03/04 são
preservadas, não apresentadas como nova execução única. Fontes/testes alterados entre as
rodadas e a seleção exata de XMLs constam de verification.json. A versão SQL final tem prova
própria de 16 casos, anterior a Verify-05; agregados físicos continuam iguais aos iniciais.
O scanner no workspace encontrou apenas candidatos removidos ainda no índice Git; a verificação
da entrega usa snapshot integral e verificado dos arquivos existentes, sem alterar a regra do scanner.
O gate SQL 005 foi consultado, mas seu
escopo V001–V017 recusa também os módulos V025–V098 anteriores; é uma validação histórica
inaplicável ao schema ampliado. Não foi relaxado nem contado como PASS. A validação atual usa
os metadados físicos da suíte vigente e o readback exato das definições alteradas.

## Revisão e limites

Revisão técnica local pelo agente que implementou; não é revisão humana nem revisão por outro
agente. Conferidos escopo de tenant/origem/entidade, tri-state, isolamento do ramo terminal,
preservação do staging, consistência core/recibo, replay, conflito, controles não terminais e
ausência de mudança em grants, identidades, produção e contratos nominais.

A V1 não foi executada e não é oráculo absoluto. Datas de amostras, metadados históricos e fixtures
não comprovam contrato atual da API. V2-041 mantém chamadas autenticadas bloqueadas. As outras
candidatas da investigação, a integração remota, os vínculos nominais e a compatibilidade das
saídas continuam com os limites documentados; não foram apagados ou aceitos por esta limpeza.

## Recuperação e entrega

O pacote é uma revisão local sobre a fotografia de 3.308 arquivos do predecessor. Contém JAR/lib,
overlay dos arquivos alterados/novos, lista explícita das 13 remoções e diff validado contra a
fotografia inicial. Os snapshots no histórico permitem revisar os bytes anteriores. Não aplicar
remoções a outra revisão sem conferir os hashes.

Rollback de código: usar o diff inverso/snapshots após conferir o estado local. No SQL, V099 não
altera tabelas nem migra dados; as duas definições anteriores estão em V004 e V022. Recuperação
de um schema instalado deve ser uma nova migration versionada, com nova validação, preservando
V099 como história. Nenhum rollback, deploy ou consulta remota é disparado pelo pacote.

Evidências: [verificacao-local.json](verificacao-local.json), [contratos e roteiro API](README.md).
Diff, logs completos, ZIP e selo/readback ficam em `target/correcao-basica-e-contratos-entidades-20260914-01/`.
O selo final registra os checks de cadeia e segurança executados após este relatório.
