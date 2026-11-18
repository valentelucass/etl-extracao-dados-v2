# Deterministic selector only. No process, Maven, Java, class/JAR, network or business reads.
$script:ExactOwnedSpotlessImplementationPath=$PSCommandPath
function Need-ExactSpotless([bool]$ok,[string]$code){if(-not$ok){throw $code}}
function ExactSpotlessNoReparse([string]$path){
 for($at=[IO.Path]::GetFullPath($path);$at;$at=[IO.Path]::GetDirectoryName($at)){
  Need-ExactSpotless (([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-eq0) 'EXACT_SPOTLESS_REPARSE_REFUSED'
 }
}
function Test-ExactOwnedSpotlessArgumentSyntax([string]$argument){
 $prefix='-DspotlessFiles=.*[\\/]('; $suffix=')\.java'
 if(-not$argument.StartsWith($prefix,[StringComparison]::Ordinal)-or-not$argument.EndsWith($suffix,[StringComparison]::Ordinal)){return $false}
 $middle=$argument.Substring($prefix.Length,$argument.Length-$prefix.Length-$suffix.Length)
 $names=[string[]]@($middle.Split('|'));if($names.Count-lt1-or$names.Count-gt512){return $false}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($name in $names){
  # Qualified alternatives use only the literal cross-platform separator atom, never arbitrary regex.
  $relative=$name.Replace('[\\/]','/')
  $basename=$relative-cmatch'^[A-Za-z_][A-Za-z0-9_]*$'
  $qualified=$relative-cmatch'^src/(main|test)/java/br/com/esl/etl/v2/(?:[A-Za-z_][A-Za-z0-9_]*/)*[A-Za-z_][A-Za-z0-9_]*$'
  if((-not$basename-and-not$qualified)-or$relative.Replace('/','[\\/]')-cne$name-or-not$seen.Add($name)){return $false}
 }
 $sorted=[string[]]@($names);[Array]::Sort($sorted,[StringComparer]::Ordinal)
 return (($names-join'|')-ceq($sorted-join'|'))
}
function Get-ExactOwnedSpotlessSelection([string]$repository,[string[]]$ownedJava){
 $repository=[IO.Path]::GetFullPath($repository);ExactSpotlessNoReparse $repository
 Need-ExactSpotless ($ownedJava.Count-ge1-and$ownedJava.Count-le512) 'EXACT_SPOTLESS_OWNED_COUNT'
 $owned=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 $leaves=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($relative in $ownedJava){
  Need-ExactSpotless ($relative-cmatch'^src/(main|test)/java/br/com/esl/etl/v2/(?:[A-Za-z_][A-Za-z0-9_]*/)*[A-Za-z_][A-Za-z0-9_]*\.java$'-and$owned.Add($relative)) 'EXACT_SPOTLESS_ORDERED_OWNED_PATH'
  [void]$leaves.Add([IO.Path]::GetFileNameWithoutExtension($relative))
 }
 $names=[string[]]@($leaves);[Array]::Sort($names,[StringComparer]::Ordinal)
 $inventory=[Collections.Generic.List[string]]::new();$matched=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($root in @('src/main/java','src/test/java')){
  $stack=[Collections.Generic.Stack[string]]::new();$stack.Push((Join-Path $repository $root))
  while($stack.Count){
   $directory=$stack.Pop();ExactSpotlessNoReparse $directory
   foreach($entry in [IO.Directory]::EnumerateFileSystemEntries($directory)){
    $attributes=[IO.File]::GetAttributes($entry)
    Need-ExactSpotless (($attributes-band[IO.FileAttributes]::ReparsePoint)-eq0) 'EXACT_SPOTLESS_INVENTORY_REPARSE'
    if(($attributes-band[IO.FileAttributes]::Directory)-ne0){$stack.Push($entry);continue}
    if([IO.Path]::GetExtension($entry)-cne'.java'){continue}
    $relative=[IO.Path]::GetRelativePath($repository,$entry).Replace('\','/')
    $inventory.Add($relative);Need-ExactSpotless ($inventory.Count-le10000) 'EXACT_SPOTLESS_INVENTORY_BOUND'
   }
  }
 }
 $alternatives=[Collections.Generic.List[string]]::new();$qualifiedOwned=[Collections.Generic.List[string]]::new();$externalHomonyms=0
 foreach($name in $names){
  $siblings=@($inventory|Where-Object{[IO.Path]::GetFileNameWithoutExtension($_)-ceq$name})
  $outside=@($siblings|Where-Object{-not$owned.Contains($_)})
  if($outside.Count-eq0){$alternatives.Add($name)}else{
   $externalHomonyms+=$outside.Count
   foreach($relative in $ownedJava|Where-Object{[IO.Path]::GetFileNameWithoutExtension($_)-ceq$name}){
    $qualifiedOwned.Add($relative);$withoutExtension=$relative.Substring(0,$relative.Length-5)
    $alternatives.Add($withoutExtension.Replace('/','[\\/]'))
   }
  }
 }
 $selectorAlternatives=[string[]]$alternatives.ToArray();[Array]::Sort($selectorAlternatives,[StringComparer]::Ordinal)
 $pattern='.*[\\/]('+($selectorAlternatives-join'|')+')\.java';$argument='-DspotlessFiles='+$pattern
 Need-ExactSpotless (Test-ExactOwnedSpotlessArgumentSyntax $argument) 'EXACT_SPOTLESS_CANONICAL_SYNTAX'
 # Restricted literals, separator atoms and alternation have identical Java/.NET matching semantics.
 $regex=[regex]::new('\A'+$pattern+'\z',[Text.RegularExpressions.RegexOptions]::CultureInvariant,[TimeSpan]::FromSeconds(1))
 foreach($relative in $inventory){
  if($regex.IsMatch((Join-Path $repository $relative))){
   Need-ExactSpotless ($owned.Contains($relative)-and$matched.Add($relative)) 'EXACT_SPOTLESS_UNOWNED_HOMONYM_OR_DUPLICATE'
  }
 }
 Need-ExactSpotless ($matched.SetEquals($owned)) 'EXACT_SPOTLESS_ALL_OWNED_AND_ONLY_OWNED'
 $inventoryPaths=[string[]]@($inventory);[Array]::Sort($inventoryPaths,[StringComparer]::Ordinal)
 $selected=[string[]]@($matched);[Array]::Sort($selected,[StringComparer]::Ordinal)
 $hash={param([string[]]$paths)[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes(($paths-join"`n")+"`n")))}
 $qualifiedPaths=[string[]]$qualifiedOwned.ToArray();[Array]::Sort($qualifiedPaths,[StringComparer]::Ordinal)
 return [ordered]@{schema='ROOT_EXACT_OWNED_JAVA_SPOTLESS_SELECTOR_V1';algorithm='ORDINAL_BASENAME_WITH_EXACT_PATH_HOMONYM_DISAMBIGUATION_V2';repository=$repository;argument=$argument;pattern=$pattern;sourceRoots=@('src/main/java','src/test/java');ownedJavaCount=$owned.Count;distinctBasenames=$names.Count;repositoryJavaCount=$inventoryPaths.Count;matchedJavaCount=$selected.Count;inventoryJavaPaths=$inventoryPaths;inventoryPathSha256=(&$hash $inventoryPaths);matchedJavaPaths=$selected;matchedPathSha256=(&$hash $selected);qualifiedOwnedPaths=$qualifiedPaths;unownedHomonymsExcluded=$externalHomonyms;allOwnedAndOnlyOwned=$true;unownedHomonymsMatched=0;argumentUtf16=$argument.Length;commandLimitRaised=$false;Source=0;HTTP=0;SQL=0}
}
function Assert-ExactOwnedSpotlessUnitSelection($unit,[string[]]$owned,[string]$repository){
 $current=Get-ExactOwnedSpotlessSelection $repository $owned
 Need-ExactSpotless ($unit.Contains('compactSpotlessSelection')-and(CanonicalJson $unit.compactSpotlessSelection)-ceq(CanonicalJson $current)) 'EXACT_SPOTLESS_UNIT_PROOF_CHANGED'
 $control=@($unit.inputPins|Where-Object file -ceq 'scripts/validation/Exact-OwnedSpotlessSelection.ps1')
 Need-ExactSpotless ($control.Count-eq1) 'EXACT_SPOTLESS_HELPER_OWNED_BY_CURRENT_UNIT'
 $bytes=[IO.File]::ReadAllBytes($script:ExactOwnedSpotlessImplementationPath)
 Need-ExactSpotless ($control[0].bytes-eq$bytes.Length-and$control[0].sha256.ToUpperInvariant()-ceq[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))) 'EXACT_SPOTLESS_HELPER_SAME_PINNED_IMPLEMENTATION'
 return $current.argument
}
