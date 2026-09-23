# Contrato da cadeia integral local

O modo `local-artifact-scenario-v2` seleciona entradas explícitas. A implementação está em
`DeclaredIntegralInputs`, `DeclaredCapturePages`, `DeclaredAnalyticReferences`,
`DeclaredAnalyticSupport` e nos leitores existentes de expansão, Raster, relações e sweep.
O alcance é sintético, transacional e restrito a `localhost/ETL_SISTEMA_V2_SHADOW`.

## Envelope e arquivos

O manifesto exige exatamente: `version`, `mode`, `target`, `support`, `source`, `tenant`,
`windowStart`, `windowEndExclusive`, `zone`, `logicalClock`, `revision`, `roots`, `pageSize`,
`fiscalPolicy`, `sources`, `expansions`, `raster`, `relations`, `references`, `supplements`
e `sweep`. `mode=LOCAL_ARTIFACT_ROLLBACK` e `support=EXPLICIT_INTEGRAL_INPUTS_V1` são literais
do contrato. Tipos, membros excedentes e membros obrigatórios ausentes são recusados.

Cada referência de arquivo declara caminho relativo e SHA-256. `PinnedLocalJson` conserva
as restrições de caminho, links e UTF-8 dos contratos de qualificação. O preflight percorre
todos os arquivos transitivos; cada consumo confere novamente os bytes. Uma alteração após
o preflight não pode ser aceita pelo hash anterior.

| Parte | Limite / vínculo |
|---|---|
| Manifesto integral | 16 KiB; 2–32 raízes; revisão 1–1.000 |
| Origem e tenant | `SYNTHETIC_` seguido de ASCII maiúsculo, dígito ou `_`; 11–40 caracteres cada |
| Janela | `[start,endExclusive)`, 1–31 dias civis; `America/Sao_Paulo` |
| Relógio | `Instant` explícito, convertido em `Clock.fixed` para a semântica local; tempos de observação/lease continuam técnicos |
| Seis capturas | COL/FRE/MAN/COT/LOC/USER obrigatórias; data inicial, origem, tenant e revisão iguais ao cenário |
| Página Data Export | 1–16 entidades por página; até 1.000 linhas físicas expandidas, 64 KiB por arquivo |
| Página Usuários | Exatamente 20 como tamanho configurado, até 20 registros retornados; cursor encadeado |
| Travessia | 1–256 arquivos por captura; teto declarado de 1–10.000 linhas e quantidade esperada exata |
| Expansões | Exatamente CAP/FAT/INV/SIN; identidade de raiz/parcela/componente fornecida pelo contrato local existente |
| Raster | Janela inteira, fuso, origem, tenant e revisão iguais; filhos mantêm identidade explícita |
| Referências | Cinco arquivos: expansão, dimensões, frota, regiões e tarifas; revisão e vigência explícitas |
| Suplementos | Nove grupos obrigatórios; 1–128 arquivos por grupo; até 64 linhas/128 KiB por arquivo |
| Execução de suplementos | Lotes de até 16; resolução conjunta de chaves por `OPENJSON`, sem lookup SQL por registro |
| Oráculo de saídas | Exatamente SQL-01–19; até 4.096 linhas por saída; arquivos de até 64 linhas/512 KiB |
| Comparação | Cinco fatos por tuplas e multiplicidade; 19 saídas tipadas; 35 escopos e 33 responsabilidades de preview |

Os limites qualificam esta ferramenta local. Não constituem limite ou garantia do fornecedor,
SLO produtivo, autorização de ampliação da janela externa ou autorização de outro alvo SQL.

## Presença, grão e precisão

As páginas passam pelos extratores, parsers, mapeadores e gateways existentes. Os contratos
por entidade preservam `ABSENT/NULL/VALUE`, léxico e proveniência onde previstos. Campo
opcional ausente não recebe valor de uma fixture. Vazio é avaliado pelo contrato do campo:
não há conversão global de `""` para NULL, zero ou identidade.

COL/FRE distinguem raiz de linha expandida. MAN conserva a raiz, pick e MDF-e sob seus
contratos próprios. USER mantém `USERS_SNAPSHOT`, `enabled=true` e a tradução admitida para
BACKFILL/REPLAY; `updatedAt` não se torna cursor incremental. CAP/FAT/INV/SIN recebem
identidades sintéticas declaradas e tipadas; o ordinal local do oráculo apenas identifica
sua linha esperada, sem criar vínculo de domínio. Raster não cria `Ordem` por posição.

Valores financeiros usam os tipos decimais exatos dos mapeadores/JDBC/SQL. O oráculo declara
tipo, escala, chave, cardinalidade e valor; não usa tolerância numérica geral. Instantes
preservam a precisão contratual de cada camada; SQL `datetime2(7)` trunca a oitava/nona casa
no caminho de projeção já existente. A regressão distingue criação do Frete, criação/emissão
de CT-e, frescor efetivo e finalização. A V099 permanece a regra de omissão terminal.

## Relações e apoios

As relações fornecem chaves, tipo, papel e revisão. O caminho de Fretes relacional e o caminho
de dependências das expansões compartilham `integral-freight-pages-v1`, os mesmos arquivos e
a mesma revisão. O SQL amarra os grupos ao cenário, à origem/tenant e à janela.

Os nove grupos são `financial`, `dimensions`, `relational`, `freight`,
`collections`, `manifestStates`, `freightRelations`, `compositions` e
`fiscal`. A resolução procura as chaves nas capturas utilizáveis do próprio cenário. Chave
desconhecida ou ambígua impede composição; nomes, sequência de chegada e hashes legados não
resolvem o vínculo. As estruturas de referência são fechadas e os importadores tipados
existentes verificam revisão, cardinalidade e vigência. Não se seleciona uma release corrente
implicitamente. Série fiscal só entra pela referência explicitamente fornecida.

## Sweep e recuperação

`local-collection-sweep-v2` exige universo declarado, quantidade física por chave e quatro
observações. A prova SQL considera páginas, auditoria, raízes e dono da responsabilidade;
mesmo total global com distribuição incorreta por chave é recusado. O resultado é preview.
A V102 rejeita apply desse universo com `ANA_COLLECTION_DECLARED_PREVIEW_ONLY`.

Falhas do modo integral são provocadas nos arquivos declarados ou nos pontos reais de
cancelamento/deadline/lease. Flags históricas que geravam fixtures auxiliares são recusadas
antes de SQL. Replay conserva os vínculos imutáveis; revisão posterior exige novos dados e
evidências coerentes. Uma falha não promove uma etapa incompleta. A sessão física bloqueia
commit de domínio e o encerramento confirma rollback.

## Oráculos externos à implementação

Os exemplos A/B são escritos antes de consultar SQL. As tabelas esperadas e as regras do
autor de testes são separadas dos mapeadores e das consultas de produção. A variante maior
altera IDs não sequenciais, ordem, relações, período civil, páginas, valores e referências.
O JAR compara os arquivos esperados recebidos; não os recalcula a partir do observado.

Somente coordenadas técnicas declaradas recebem tratamento específico: UUID da execução,
tempo técnico limitado ao intervalo observado e proveniência conferida pelo wire independente.
Isso não ignora a linha de monitoramento. SQL-04 fica vazia nos exemplos integrais porque o
preview não aplica confirmação de ausência; a regressão histórica de Coletas fornece a
contraprova positiva de confirmação e reaparecimento sob seu contrato próprio.

As contraprovas de valor, chave, cardinalidade, precisão e fato alteram apenas o esperado e
exigem divergência. Replay e permutações admitidas conferem invariância. Nenhuma comparação
sintética ratifica identidade, regra fiscal ou completude de fonte real.
