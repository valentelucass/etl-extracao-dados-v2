# ADR 0051 — Campanha e pacote local verificáveis

Status: decisão implementada e qualificada localmente em13/09/2026; resultados e limites em docs/catalogos/macrobloco-qualificacao-pacote/RELATORIO.md.

O cenário analítico anterior integra onze entradas e dezenove saídas, mas seus
comandos são coordenados a partir do workspace. V2-038/V2-039 exigem um contrato
consumido de campanha e um conteúdo distribuível cuja integridade preceda JDBC.

Adotamos JSON fechado, versionado e limitado para configuração, campanha,
oráculos e inventário. Enumerações selecionam comportamentos compilados; arquivos
não fornecem SQL, comandos, classes ou destinos arbitrários. O planner civil
existente determina janelas e adiamentos. Resultado de contraprova e gate do dado
são distintos; dependentes exigem PASS_LOCAL de todas as dependências.

O pacote Windows/x64/Java17 contém o JAR, oito bibliotecas runtime, DLL integrada
12.8.1.x64, configuração sintética, contratos, schema/baseline, fixtures/oráculos,
comandos fixos, licenças, SBOM e proveniência. Hash fornecido pelo operador ancora
o manifesto. Hash não equivale a assinatura. Nenhuma dependência será atualizada.
O timestamp do build é fixado em 2026-09-13T00:00:00Z; evidência com relógio real
fica fora do payload determinístico. Reprodutibilidade só será declarada após
comparar os bytes de duas construções independentes da mesma fotografia.

A SBOM usa o subconjunto necessário de CycloneDX 1.6, com correspondência exata
dos nove artefatos de terceiros. O consumidor verificará versão, inventário,
hashes, coordenadas, licenças e origem; feed permanece NOT_EXECUTED. O builder e
as provas finais deverão validar o documento contra o schema versionado.

Journal de arquivos preserva o controle local entre processos. Dados sintéticos
revertidos precisam ser reconstruídos. Ausência de recibo terminal é resultado
desconhecido e requer reconciliação; PID, exit isolado e log parcial não provam
sucesso. Nenhuma migration é necessária nesta decisão; eventual lacuna SQL exige
prova e reserva separadas antes de nova versão.

Alternativas descartadas: ZIP contendo somente o JAR; classpath obtido do Maven
durante smoke; golden copiado da view; status agregado que aprova dependentes
falhos; journal de arquivos apresentado como recuperação de domínio durável.

Fontes públicas consultadas em 13/09/2026:

- [Maven — reproducible builds](https://maven.apache.org/guides/mini/guide-reproducible-builds.html), propriedade outputTimestamp; plugin JAR local 3.4.2.
- [CycloneDX — schema 1.6](https://github.com/CycloneDX/specification/blob/master/schema/bom-1.6.schema.json), formato fechado a congelar para validação offline.
- [Microsoft — propriedades JDBC](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties?view=sql-server-ver17), autenticação nativa integrada; driver local 12.8.1.jre11 preservado.

Owner nominal, paridade real, feed, assinatura, CI, release operacional, serviço,
produção, COMMIT/crash de domínio e RTO/RPO continuam fora da prova sintética.

Correção descoberta pelo oráculo temporal em oracle-output-physical-01:
vinte parâmetros de Instant em onze adapters do laboratório usavam setTimestamp
sem Calendar UTC. No fuso padrão America/Sao_Paulo, a representação DATETIME2
deslocava o relógio em três horas. Passam a informar UTC explicitamente, como
os gateways de staging já faziam; a leitura do lease da fila usa o mesmo fuso.
Não se alteram datas civis, instantes das fontes ou migrations. O delta exato
está em utc-parameter-delta.json privado. A prova conjunta de dezoito saídas
passou em oracle-output-monitor-physical-01; a regressão integral permanece
obrigatória após essa correção nos adapters existentes.

O oráculo distingue oito saídas com observação no relógio lógico declarado
(2036-04-15T12:00Z) dos campos produzidos no intervalo técnico do caso. Em SQL10,
IDs/tempos vêm exclusivamente dos recibos de controle das capturas declaradas;
contagens/estados esperados são regras independentes dos inputs e das políticas.
EXP_LAB_CAPTURE_ONLY e a política relacional terminam em DEGRADED, Raster em
APPLIED. A comparação positiva do monitor conserva esses estados; ela não os
substitui por publicação operacional. Não se usa a view como fonte de esperados.

O hash LOC é esperado fixo por raiz, calculado offline a partir dos literais de
entrada e de um exemplo independente do envelope UTF-16LE v2. O enquadramento
preserva DECIMAL(38,9), distinto das colunas de consumo DECIMAL(28,8), e os
delimitadores das strings JSON. Hash não qualifica valores que não tenham sido
comparados separadamente.

## Recortes de fato consumidos pelo planner

QualificationWindowExecutor converte as fronteiras explícitas do RuntimeTemporalPlanner
em datas civis no fuso declarado e envia modo/start/end às cinco procedures existentes.
O lookback é limitado ao começo da fixture. Um recorte não sela o cenário completo nem
avança a fronteira da fonte; os recibos SQL e a igualdade antes/depois são conferidos.
Datas fora da fixture são recusadas. window-executor-physical-02 passou2IT nos quatro
modos, janela curta versus lookback, correção para outro dia e blackout, com rollback.
Não há nova migration ou regra de publicação paralela.

## Janelas de captura e matriz temporal

A ação fechada TEMPORAL usa cópia declarada das cinco políticas atuais, ligada aos
bytes de configuração por teste, e reutiliza RuntimeTemporalPlanner/Coordinator.
LaboratoryCaptureWindow mantém instantes da partição e datas inclusivas da fonte
separados: lookback não muda o início publicado. As sobrecargas preservam os
padrões anteriores dos três caminhos de captura. Cotações passa pelo dispatcher
e publicação/DQ existentes; os quatro workloads de captura conservam DEGRADED.
Cada prova usa savepoint e rollback antes da próxima, na sessão limitada do caso;
o cenário completo é reconstruído depois. O journal não recupera linhas revertidas.

A fonte sintética temporal filtra três registros declarados pela janela recebida:
antigo, corrente e chegada tardia com data antiga. Os esperados de quantidade/data
são literais da fixture, conferidos no staging e nos recibos. A meia-noite inexistente
de2018 em São Paulo deve ser recusada; a variante explícita03:00 atravessa23horas
e precisa publicar um intervalo físico igual ao plano. Isso não muda a política
nominal da matriz. temporal-matrix-physical-07 passou com rollback e o candidato07
executou TEMPORAL e VARIANTS pelos seis comandos do pacote. O verify final segue
pendente; as tentativas anteriores falhas ficam preservadas.

## Coleções técnicas e handles JDBC

O verify integral01 identificou contratos de coleção ainda não cadastrados na
regra de arquitetura. As28 APIs novas têm entradas exatas com cardinalidade e
consumidor; construtores também recusam excesso antes de copiar. Path é Iterable
de segmentos e representa um único caminho, não uma coleção de linhas.

O registro de cancelamento precisa conservar a identidade dos statements reais
do driver, pois os adapters TVP usam o tipo concreto Microsoft. A exceção de campo
na regra de persistência é restrita à classe, nome e tipo Set de Statement exatos;
nenhum conjunto de chaves ou resultados é autorizado. O registro limita64 handles
abertos, remove os fechados e fecha o handle recusado. A prova física abre64,
recusa o65º e executa após liberar capacidade; permanece obrigatória no verify.
Metadados SQL usam página explicitamente limitada a1..123 colunas de um contrato.


## Scanner e evidências preservadas

POMs Maven e licenças TXT entram na varredura como texto UTF-8; não são excluídos.
Quatro descrições públicas do schema CycloneDX1.6 têm exceções exatas por caminho
e valor. Alterar o valor ou mover a mesma frase para outro caminho continua
recusado;16contraprovas passaram, incluindo os novos formatos e essas mutações.
O self-test admite EvidenceAttempt limitado à rodada para preservar fixtures e
logs, mantendo os11casos antigos. Logs auxiliares são capturados em bytes e o
processo PowerShell filho fixa UTF-8 explicitamente; exit0 com encoding inválido
não qualifica a evidência. As tentativas anteriores falhas permanecem privadas.


Limite de caminho nativo descoberto na composição: o mesmo DLL empacotado carregou via System.load em173caracteres e foi recusado em268 pelo carregador local, sem JDBC (native-path-diagnostic-01, UTF8 íntegro). O contrato local agora limita o caminho absoluto do membro NATIVE_AUTH a240caracteres, conservador, e recusa QUAL_PACKAGE_NATIVE_PATH_LIMIT antes de JDBC. Fixture de teste usa target/qf e identificação curta; mutante Java e21ª contraprova extraída cobrem caminho longo. Não se afirma limite universal do SO nem se muda registro/PATH/configuração global.


## Fechamento da decisão

Verify03 passou1.976unitários/4skips históricos e417IT/0skip, incluindo todas
as378IT anteriores. Dois builds independentes produziram o mesmo ZIP/JAR;
1.996inputs e1.015classes/recursos conferiram. As17campanhas e escalas4/16/32/16
passaram no pacote final, com25/21/8contraprovas. As menções a provas pendentes
acima preservam a evolução da decisão; o relatório e selo finais delimitam
a revisão concluída. Nenhum gate operacional/real foi ratificado.
