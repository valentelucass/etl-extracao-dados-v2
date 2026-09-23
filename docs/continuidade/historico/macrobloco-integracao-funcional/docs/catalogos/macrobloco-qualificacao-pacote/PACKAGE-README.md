# ETL V2 — laboratório local de qualificação

Plataforma de destino: Windows/x64, Java17, PowerShell7.5. Pacote local sintético;
gates operacionais, assinatura, CI, fonte real e feed de vulnerabilidades pendentes.

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
RECOMPOSE, ABSENCE, VARIANTS, TEMPORAL, DEGRADATION e CONCURRENCY. Para editar
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
mssql-jdbc_auth-12.8.1.x64 tem licença Microsoft Proprietary, distinta da MIT do
JAR JDBC. Uso aqui é desenvolvimento/teste local, sem publicação ou instalação
global. sbom.cdx.json usa CycloneDX1.6 e provenance.json declara os limites.
