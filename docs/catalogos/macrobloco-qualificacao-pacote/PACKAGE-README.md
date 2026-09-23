# ETL V2 — laboratório local de qualificação

Plataforma de destino: Windows/x64, Java17, PowerShell7.5. Pacote local sintético;
gates operacionais, assinatura, CI e fonte real pendentes. O scan técnico das
dependências atuais foi executado na rodada P11; o aceite nominal da baseline
por Segurança permanece pendente. A montagem offline não executa outro feed:
`vulnerabilityFeed=NOT_EXECUTED` na proveniência descreve esta etapa do pacote.

O manifesto inclui cada arquivo por tamanho, tipo, papel e SHA256. Forneça o hash
do manifesto obtido com o pacote por um canal confiável; hash não é assinatura.
O verificador recusa extras, ausências, alterações e revisões incompatíveis antes
de JDBC. Extração não instala schema, DLL, serviço ou agenda.

O caminho absoluto do membro native/*.dll deve ter no máximo240caracteres.
Esse limite conservador local é conferido antes de JDBC; use um diretório de
extração curto dentro da rodada. Um caminho de268caracteres foi recusado pelo
carregador nativo nesta máquina, com o mesmo arquivo que carregou em173.

A entrada fixa é Invoke-Qualification.ps1, com Command inspect/plan/run/status/
resume/compare e ManifestSha256 explícito. Campaign informa um contrato JSON
fechado; Control é um diretório próprio novo para run e existente para status/
resume. Configuration opcional informa configuração externa sintética fechada.
config/campaign.synthetic.json contém um cenário de duas raízes e as dezenove
saídas, com os pins desta revisão já preenchidos. config/config.synthetic.json
declara alvo, duas travas, rollback e limites numéricos.

Após a extração validada pelo SHA256 externo indicado no relatório da entrega,
abra PowerShell no diretório extraído e execute:

```powershell
$pin = (Get-Content .\package.sha256 -Raw).Trim()
$campaign = Join-Path $PWD.Path 'config/campaign.synthetic.json'
$control = Join-Path (Split-Path $PWD.Path -Parent) 'controle-exemplo-01'
& .\Invoke-Qualification.ps1 -Command inspect -ManifestSha256 $pin
& .\Invoke-Qualification.ps1 -Command plan -ManifestSha256 $pin -Campaign $campaign
& .\Invoke-Qualification.ps1 -Command run -ManifestSha256 $pin -Campaign $campaign -Control $control
& .\Invoke-Qualification.ps1 -Command status -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command resume -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command compare -ManifestSha256 $pin -Control $control
```

run exige controle novo; status/resume/compare usam o mesmo controle. Uma nova
campanha exige outro nome. O JSON permite ações fechadas SCENARIO, QUERY, REPLAY,
RECOMPOSE, ABSENCE, VARIANTS, TEMPORAL, DEGRADATION, CONCURRENCY, ARTIFACT e SEQUENCE. Para editar
casos/ondas, copie o exemplo para fora do payload e informe esse caminho explícito.
O contrato recusa campos extras, modos incompatíveis, pins alterados e limites
inválidos antes de JDBC. TEMPORAL executa provas locais das cinco políticas e
fronteiras; DEGRADATION exige um dos quatro faults tipados. O plano mostra o
adiamento por blackout, deadline ou dependência antes de criar um filho.

JAR, libs, native, contratos, fixtures, oráculos e schema vêm exclusivamente do
payload. O diretório de controle e os inputs declarados ficam fora do payload
imutável. Não acrescente logs ou arquivos a ele: a verificação detecta extras.

SQL autorizado neste laboratório: localhost/ETL_SISTEMA_V2_SHADOW, autenticação
Windows existente, duas travas do perfil e rollback de todos os dados sintéticos.
O Main padrão permanece sem I/O. Não executar migrations implicitamente.

Os estados de dado são PASS_LOCAL, FAILED, BLOCKED_DEPENDENCY, CANCELLED e
OUTCOME_UNKNOWN. Uma recusa esperada pode passar como teste e manter o dado
bloqueado. Sem recibo terminal não se presume sucesso ou autoriza repetição.
Journal recupera o controle; outro processo reconstrói a fixture revertida.

O diretório licenses registra POMs, licença nativa e schemas oficiais. A DLL
mssql-jdbc_auth-12.8.2.x64 tem licença Microsoft Proprietary, distinta da MIT do
JAR JDBC. Uso aqui é desenvolvimento/teste local, sem publicação ou instalação
global. sbom.cdx.json usa CycloneDX1.6 e provenance.json declara os limites.

## Entradas locais por arquivos

Quando montado com ArtifactInputs, o pacote contém artifact-cases/index.json,
dois conjuntos independentes e config/campaign.artifact-small.json /
campaign.artifact-large.json. Use um desses caminhos em Campaign nos
seis comandos acima e um Control novo. ARTIFACT atravessa o mesmo supervisor,
worker, journal, cancelamento e comparação; valida os pins de runtime, schema,
janela, entrada e oráculo antes de reservar o caso. Os exemplos usam três dias
sintéticos de 2036, com catch-up/deadline declarados no JSON. O relógio lógico
do exemplo não altera os limites físicos de execução.

Main também consome arquivos externos ao payload. No diretório extraído, os
comandos abaixo ilustram as opções; cada caminho deve apontar a um manifesto
local existente. Os perfis e exemplos de sweep da entrega ficam no diretório
de entradas separado, junto de seus pins e evidências.

```powershell
$main = 'br.com.esl.etl.v2.bootstrap.Main'
java -Xmx512m -cp .\etl-dataexport-v2.jar $main local-profile characterize --artifact C:\entradas\perfil\profile.json
java -Xmx512m -cp .\etl-dataexport-v2.jar $main local-data characterize --artifact .\artifact-cases\artifact-small\cap\capture.json
java -Xmx512m -cp .\etl-dataexport-v2.jar $main local-raster characterize --artifact .\artifact-cases\artifact-small\raster\raster.json
```

As caracterizações não abrem SQL. Perfis disponíveis: COL, MAN, COT, USER, FRE e
LOC; expansões: CAP/8636, FAT/4924, INV/10633 e SIN/6392. Tipo, presença, frescor,
completude e identidade são avaliados pelos contratos locais versionados. O
resultado é sanitizado; provider continua UNVERIFIED e paridade nominal BLOCKED.

Para os comandos físicos, prepare explicitamente o ambiente local já instalado:

```powershell
$env:V2_SHADOW_JDBC_URL = 'jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=45000'
$localJvm = @('-Xmx512m', '-Dshadow.local.integration.profile.active=true', '-Dshadow.local.integration.enabled=true', ('-Djava.library.path=' + (Join-Path $PWD.Path 'native')), '-cp', '.\etl-dataexport-v2.jar', $main)
java @localJvm local-data capture --artifact .\artifact-cases\artifact-small\cap\capture.json
java @localJvm local-raster capture --artifact .\artifact-cases\artifact-small\raster\raster.json
java @localJvm local-scenario run --input .\artifact-cases\artifact-small\input.json --oracle .\artifact-cases\artifact-small\oracle.json
java @localJvm local-sweep observe --input C:\entradas\sweep\program.json
```

Main impõe 240 segundos e rollback; a sessão confirma o alvo no master e bloqueia
COMMIT de domínio. O sweep exige quatro snapshots explícitos, executa somente a
prova sintética de Coletas e consome o preview das 33 responsabilidades, preservando
BLOCKED/DISABLED/NOT_APPLICABLE. Ausência de pai não é regra de exclusão de filhos.

Os oráculos são separados da entrada e vinculados ao JAR/schema. No formato de
compatibilidade `local-artifact-scenario-v1`, quatro expansões, Raster e relações
vêm de arquivos; seis fontes de apoio e referências usam
`PACKAGED_ANALYTIC_SUPPORT_V1`. Os cenários integrais v2/v3 e as sequências atuais
declaram as onze famílias de entrada, referências e suplementos por arquivo.
Essa composição local não comprova contratos reais do fornecedor. O envelope
capture_occurrence/data/binding é uma convenção sintética. Nomes, aliases e
ordinais não substituem chaves tipadas. Alterar um arquivo exige atualizar seus
pins explícitos; alterar o payload exige remontar o pacote e conferir seu novo
manifesto. Um hash correto não concede autoridade de negócio.

Main retorna 0 no sucesso local, 20 em recusa de configuração/entrada e 40 na
divergência conhecida de comparação; o supervisor usa seu contrato próprio de
exits/recibos. Confira o exit e o recibo, incluindo confirmação do rollback. Nenhum
comando executa migration, fonte remota, instalação ou publicação produtiva.

## Sequências integrais declaradas

Pacotes com índice `qualification-artifact-cases-v2` podem conter casos SEQUENCE.
Neles, cada etapa consome exclusivamente suas onze famílias de arquivos,
referências, suplementos e oráculos declarados. Os autores de exemplos não
participam da execução do JAR. Consulte o índice para os casos realmente presentes.

Para as alternativas `sequence-a` e `sequence-b`, use respectivamente
`config/campaign.sequence-a.json` e `config/campaign.sequence-b.json` nos comandos
inspect/plan/run/status/resume/compare acima. Cada campanha usa um Control novo.
As etapas de uma sequência compartilham a mesma transação e são comparadas antes
de avançar. Os arquivos de cada etapa podem compartilhar membros imutáveis com
o mesmo conteúdo; os pins identificam os bytes consumidos.

O comando direto equivalente, com o ambiente local explícito já preparado, é:

```powershell
java @localJvm local-sequence run --sequence .\artifact-cases\sequence-a\sequence.json
```

A sequência declara até1800s e cada etapa até240s. Os limites das ações anteriores
permanecem. A campanha tem teto3600s, heap512MiB e query60s. O recibo distingue
revisões de execução, fonte, referência e suplemento. RECOMPOSE reaproveita as
fontes capturadas e registra cinco materializações; alteração de tarifa que exige
captura não é aceita nessa operação. Os33previews não habilitam apply integral.

Um resultado selado pode ser lido sem repetir o worker. Perda da JVM perde também
a transação SQL: após reconciliar a tentativa anterior, uma nova execução deve
começar a sequência desde o início, com outro controle. Não há retomada durável
no meio da transação nem prova de COMMIT/crash/restore.
