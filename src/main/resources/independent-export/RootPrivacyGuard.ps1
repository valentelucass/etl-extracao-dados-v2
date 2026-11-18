# Read-only privacy checks. No ACL, identity or permission mutation.
function PrivacyNeed0712([bool]$Value,[string]$Code){if(-not$Value){throw $Code}}
function Test-PrivacyDecision0712($Observation,$Policy,[bool]$RequireParentHash){
 if($null-eq$Observation-or$null-eq$Policy-or$Observation.ownerSid-cne$Policy.ownerSid-or$Observation.nullDacl-ne$false-or$Observation.reparsePoint-ne$false){return $false}
 if($RequireParentHash-and$Observation.sddlSha256-cne$Policy.parentSddlSha256){return $false}
 if($Observation.allowRules-isnot[array]-or$Observation.allowRules.Count-ne4){return $false}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($ace in $Observation.allowRules){
  if($ace.sid-isnot[string]-or-not$seen.Add($ace.sid)-or-not$Policy.allowedRights.Contains($ace.sid)){return $false}
  if($ace.mask-isnot[long]-and$ace.mask-isnot[int]){return $false};$rights=[long]$ace.mask;$max=[long]$Policy.allowedRights[$ace.sid]
  if($rights-le0-or($rights-band(-bnot$max))-ne0){return $false}
 }
 return $seen.SetEquals([string[]]@($Policy.allowedRights.Keys))
}
function Read-PrivacyPolicy0712($Pin){
 $p=Read $Pin.path
 $owner='S-1-5-21-263687237-119212591-4036808788-1000';$sandbox='S-1-5-21-263687237-119212591-4036808788-1002'
 PrivacyNeed0712 ($p.schema-ceq'ROOT_FRETES_EXISTING_PRIVATE_ACL_OBSERVATION0712_V1'-and[IO.Path]::IsPathFullyQualified($p.parentPath)-and$p.ownerSid-ceq$owner-and$p.sandboxSid-ceq$sandbox-and$p.sandboxAccount-ceq'ROD-SRVW-001\CodexSandboxUsers'-and$p.parentSddlSha256-cmatch'^[A-F0-9]{64}$'-and$p.trustedExistingCodexSandboxReadGroup-is[bool]-and$p.trustedExistingCodexSandboxReadGroup-and$p.allowedRights-is[Collections.IDictionary]-and$p.allowedRights.Count-eq4) 'PRIVACY_POLICY_SCHEMA'
 $full=[long][Security.AccessControl.FileSystemRights]::FullControl;$read=[long]([Security.AccessControl.FileSystemRights]::ReadAndExecute -bor [Security.AccessControl.FileSystemRights]::Synchronize)
 PrivacyNeed0712 ($p.allowedRights.Contains($owner)-and[long]$p.allowedRights[$owner]-eq$full-and$p.allowedRights.Contains('S-1-5-18')-and[long]$p.allowedRights['S-1-5-18']-eq$full-and$p.allowedRights.Contains('S-1-5-32-544')-and[long]$p.allowedRights['S-1-5-32-544']-eq$full-and$p.allowedRights.Contains($sandbox)-and[long]$p.allowedRights[$sandbox]-eq$read) 'PRIVACY_EXACT_ALLOWED_SIDS_AND_RIGHTS'
 return $p
}
function Get-PrivacyObservation0712([string]$Path,$Policy){
 PrivacyNeed0712 ([IO.Path]::IsPathFullyQualified($Path)-and[IO.Path]::GetFullPath($Path)-ceq$Path) 'PRIVACY_ABSOLUTE_NORMALIZED_PATH'
 $cursor=$Path;while($cursor){$item=Get-Item -LiteralPath $cursor -Force;PrivacyNeed0712 (($item.Attributes-band[IO.FileAttributes]::ReparsePoint)-eq0) 'PRIVACY_REPARSE_POINT';$next=[IO.Path]::GetDirectoryName($cursor);if([string]::IsNullOrEmpty($next)-or$next-ceq$cursor){break};$cursor=$next}
 $acl=Get-Acl -LiteralPath $Path;$binary=$acl.GetSecurityDescriptorBinaryForm();$raw=[Security.AccessControl.RawSecurityDescriptor]::new($binary,0)
 $owner=$acl.GetOwner([Security.Principal.SecurityIdentifier]).Value;$sddl=$acl.GetSecurityDescriptorSddlForm([Security.AccessControl.AccessControlSections]::All)
 $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($sddl)))
 $rules=@();foreach($rule in $acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])){
  PrivacyNeed0712 ($rule.AccessControlType-eq[Security.AccessControl.AccessControlType]::Allow) 'PRIVACY_UNSUPPORTED_DENY_ACE'
  $rules+=@{sid=$rule.IdentityReference.Value;mask=[long]$rule.FileSystemRights}
 }
 $sandbox=[Security.Principal.SecurityIdentifier]::new($Policy.sandboxSid);$account=$sandbox.Translate([Security.Principal.NTAccount]).Value
 PrivacyNeed0712 ($account-ieq$Policy.sandboxAccount-and[Security.Principal.WindowsIdentity]::GetCurrent().User.Value-ceq$Policy.ownerSid) 'PRIVACY_CURRENT_ACCOUNT_AND_TRUSTED_EXISTING_GROUP'
 return @{ownerSid=$owner;nullDacl=($null-eq$raw.DiscretionaryAcl);reparsePoint=$false;sddlSha256=$hash;allowRules=$rules}
}
function Assert-PrivatePath0712([string]$Path,$Policy,[bool]$Parent){
 $v=Get-PrivacyObservation0712 $Path $Policy;PrivacyNeed0712 (Test-PrivacyDecision0712 $v $Policy $Parent) 'PRIVACY_ACL_NOT_ADMITTED';return $v
}
function Test-PrivacyFixtures0712{
 $p=@{ownerSid='OWNER';parentSddlSha256='A';allowedRights=@{OWNER=[long]7;SYSTEM=[long]7;ADMIN=[long]7;SANDBOX=[long]3}}
 $o=@{ownerSid='OWNER';nullDacl=$false;reparsePoint=$false;sddlSha256='A';allowRules=@(@{sid='OWNER';mask=[long]7},@{sid='SYSTEM';mask=[long]7},@{sid='ADMIN';mask=[long]7},@{sid='SANDBOX';mask=[long]3})}
 PrivacyNeed0712 (Test-PrivacyDecision0712 $o $p $true) 'FIXTURE_VALID'
 $bad=$o.Clone();$bad.ownerSid='OTHER';PrivacyNeed0712 (-not(Test-PrivacyDecision0712 $bad $p $true)) 'FIXTURE_OWNER'
 $bad=$o.Clone();$bad.nullDacl=$true;PrivacyNeed0712 (-not(Test-PrivacyDecision0712 $bad $p $true)) 'FIXTURE_NULL_DACL'
 $bad=$o.Clone();$bad.allowRules=@($o.allowRules)+@(@{sid='EVERYONE';mask=[long]3});PrivacyNeed0712 (-not(Test-PrivacyDecision0712 $bad $p $true)) 'FIXTURE_UNTRUSTED'
 $bad=$o.Clone();$bad.sddlSha256='B';PrivacyNeed0712 (-not(Test-PrivacyDecision0712 $bad $p $true)) 'FIXTURE_PARENT_DRIFT'
 return @{passed=5;Source=0;HTTP=0;CIM=0;ACLChanges=0}
}