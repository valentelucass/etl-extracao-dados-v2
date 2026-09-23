Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Get-Bloco54SqlModuleHashes([string]$Path){
    $bytes=[IO.File]::ReadAllBytes([IO.Path]::GetFullPath($Path))
    if($bytes.Length -gt 2097152){throw 'SQL_MODULE_FILE_LIMIT'}
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($bytes)
    foreach($batch in [regex]::Split($text,'(?m)^[ \t]*GO[ \t]*\r?\n?')){
        $header=[regex]::Match($batch,'\A\s*CREATE(?: OR ALTER)? (?:FUNCTION|PROCEDURE) (?<name>[A-Za-z_.]+)')
        if(-not $header.Success){continue}
        # The local SQL Server retains leading batch whitespace and blanks OR/ALTER words.
        # These are predicted hashes; installation must still compare actual sys.sql_modules.
        $normalized=[regex]::Replace($batch,'\A(\s*)CREATE OR ALTER','$1CREATE  ')
        [pscustomobject]@{name=$header.Groups['name'].Value;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Unicode.GetBytes($normalized))).ToLowerInvariant()}
    }
}
Export-ModuleMember -Function Get-Bloco54SqlModuleHashes
