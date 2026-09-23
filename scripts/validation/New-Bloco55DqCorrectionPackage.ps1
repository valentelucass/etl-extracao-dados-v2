$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$dir=Join-Path $root 'database/proposals/bloco55-dq-correction'
if(Test-Path $dir){throw 'CORRECTION_PACKAGE_EXISTS_PRESERVE'}
[void][IO.Directory]::CreateDirectory($dir)
$enc=[Text.UTF8Encoding]::new($false)
function Write-DqFile([string]$Name,[string]$Text){[IO.File]::WriteAllText((Join-Path $dir $Name),$Text,$enc)}
function Digest([string]$Value){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Unicode.GetBytes($Value))).ToLowerInvariant()}
function Field([string]$Value){([Text.Encoding]::Unicode.GetByteCount($Value)).ToString()+':'+$Value}
$old=Get-Content (Join-Path $root 'database/proposals/bloco55-runtime-extension/activation/quality-references.json') -Raw|ConvertFrom-Json
$refs=[Collections.Generic.List[object]]::new();$apply='';$verify='';$recovery='';$checks=@('COUNT_EQUATION','PAGE_TERMINALITY','PROMOTION_RECONCILIATION','QUARANTINE_SLA')
$owner='laboratory-owner';$retention='bloco55-preserve-v1';$effective='2026-09-08T00:00:00.124'
foreach($prior in $old){
 $version=$prior.version.Replace('-v1','-v2');$scope=$prior.scope
 $parts=@('dq-policy-v1',(Field $version),$scope,'4','3600',(Field $owner),(Field $owner),(Field $retention),(Field $owner),$effective)
 for($i=0;$i -lt 4;$i++){$parts+=($i+1).ToString()+':'+$checks[$i]+':0:0:'+(Field $owner)}
 $fingerprint=Digest ($parts -join '|')
 $refs.Add([ordered]@{template=$prior.template;mode='BACKFILL';version=$version;fingerprint=$fingerprint;scope=$scope;effectiveFrom=$effective;priorVersion=$prior.version;priorFingerprint=$prior.fingerprint})
 $apply+="IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=N'$($prior.version)' AND policy_fingerprint='$($prior.fingerprint)' AND policy_state=N'RATIFIED') THROW 52854,N'EXACT_INVALID_PRIOR_POLICY_REQUIRED',1;`n"
 $apply+="UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED' WHERE policy_version=N'$($prior.version)' AND policy_fingerprint='$($prior.fingerprint)';`n"
 $apply+="INSERT ctl.data_quality_policy VALUES(N'$version','$fingerprint','$scope',4,3600,N'$owner',N'$owner',N'$retention',N'$owner',N'RATIFIED','$effective',SYSUTCDATETIME());`n"
 $apply+="INSERT ctl.data_quality_check_policy SELECT N'$version','$fingerprint',n,c,0,0,N'$owner' FROM (VALUES(1,N'COUNT_EQUATION'),(2,N'PAGE_TERMINALITY'),(3,N'PROMOTION_RECONCILIATION'),(4,N'QUARANTINE_SLA'))p(n,c);`n"
 $verify+="IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=N'$version' AND policy_fingerprint='$fingerprint' AND scope_fingerprint='$scope' AND policy_state=N'RATIFIED') THROW 52854,N'EXACT_CORRECTED_POLICY_REQUIRED',1;`n"
 $verify+="IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_check_policy WHERE policy_version=N'$version' AND policy_fingerprint='$fingerprint' AND maximum_failed_rows=0 AND maximum_failure_basis_points=0 AND threshold_owner_role=N'$owner')<>4 THROW 52854,N'EXACT_FOUR_STRICT_CHECKS_REQUIRED',1;`n"
 $recovery+="UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED' WHERE policy_version=N'$version' AND policy_fingerprint='$fingerprint' AND policy_state=N'RATIFIED';`n"
}
$prefix=":On Error exit`nSET NOCOUNT ON; SET XACT_ABORT ON;`nIF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT<>0 THROW 52854,N'EXACT_TARGET_REQUIRED',1;`nBEGIN TRANSACTION;`n"
Write-DqFile 'apply.sql' ($prefix+$apply+"GO`n:r `"verify.sql`"`nGO`nIF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52854,N'EXACT_TRANSACTION_REQUIRED',1;`nCOMMIT TRANSACTION;`nPRINT N'B55_THREE_DQ_REVISIONS_COMMITTED';`n")
Write-DqFile 'verify.sql' ("SET NOCOUNT ON;`n"+$verify+"PRINT N'B55_THREE_CORRECTED_DQ_REVISIONS_EXACT';`n")
Write-DqFile 'recovery.sql' ($prefix+$recovery+"COMMIT TRANSACTION;`nPRINT N'CORRECTED_POLICIES_REVOKED_OLD_INVALID_POLICIES_REMAIN_REVOKED_DATA_PRESERVED';`n")
Write-DqFile 'quality-references.json' ($refs|ConvertTo-Json -Depth 6)
Write-DqFile 'README.md' @'
# Correção dos fingerprints DQ do Bloco 55

PREPARED_REQUIRES_ADDITIONAL_SCOPE_DECISION. Não executado.

O gerador original contou 30 bytes para laboratory-owner (correto: 32) e 36 para
bloco55-preserve-v1 (correto: 38). O consumidor SQL bloqueou as três publicações.
As políticas originais são imutáveis; não é permitido corrigir sua identidade,
apagar suas linhas ou enfraquecer a verificação do consumidor.

Este pacote preserva e revoga as três definições inválidas e acrescenta três
revisões v2, com os mesmos quatro checks estritos por workload. São 15 linhas
adicionais: três políticas e doze checks. Permanecem três políticas ativas; passam
a existir seis definições B55 no histórico, três revogadas. Não há grant, scope,
tarifa, conta, validade, fonte, schema ou autorização produtiva adicional.

A seção 3 limita a três políticas sintéticas. A interpretação conservadora trata
as três definições extras como delta fora desse teto, ainda que substituam as
anteriores. O pacote está separado para decisão, sem reduzir o restante A–J.

Após adoção: reservar no ledger; qualificar apply.sql em rollback; verificar
hashes canônicos contra o consumidor SQL e preservação em nova conexão; aplicar
atomicamente e verificar de novo. Depois do commit, recuperação revoga somente
as revisões v2, sem reativar as inválidas nem remover dados. Requests da rodada
anterior permanecem vinculados ao material original; criar ocorrências próprias
com as referências novas. Cada tentativa adicional conserva os caps B55.

Os nomes de papéis DQ são rótulos sintéticos do laboratório, sem atestado de
governança produtiva. Nenhum aceite foi marcado por preparar este pacote.
'@
$files=@(Get-ChildItem $dir -File|Sort-Object Name|ForEach-Object{[ordered]@{path=$_.Name;sha256=(Get-FileHash $_.FullName).Hash.ToLowerInvariant()}})
Write-DqFile 'manifest.json' ([ordered]@{state='PREPARED_NOT_APPLIED';database='localhost/ETL_SISTEMA_V2_SHADOW';additionalPolicyDefinitions=3;additionalCheckRows=12;historicalPolicyDefinitionsAfter=6;activePolicyDefinitionsAfter=3;grantDelta=0;scopeDelta=0;validityExtended=$false;files=$files}|ConvertTo-Json -Depth 6)
(Get-FileHash (Join-Path $dir 'manifest.json')).Hash.ToLowerInvariant()
