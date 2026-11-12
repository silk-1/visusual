#requires -Version 5.1
<#
Read-only checks. No modules, Gradle, JDK or network required.
Usage: powershell -NoProfile -File .\scripts\verify.ps1
Exit codes: 0 = passed, 1 = violations, 2 = invalid/missing inputs or I/O error.
Java is checked as bytes so a BOM or malformed UTF-8 cannot hide non-ASCII data.
#>
[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$violations = $false
$inputError = $false

try {
    $root = (Resolve-Path -LiteralPath $RepositoryRoot).ProviderPath
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        throw 'RepositoryRoot must be a directory.'
    }
} catch {
    Write-Output ('ERROR root: ' + $_.Exception.Message)
    exit 2
}

function Read-LanguageKeys([string]$Path) {
    $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
    $raw = [System.IO.File]::ReadAllText($Path, $utf8)
    $document = ConvertFrom-Json -InputObject $raw -ErrorAction Stop
    if ($null -eq $document -or $document -isnot [pscustomobject]) {
        throw ('Expected a JSON object: ' + $Path)
    }
    $keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($property in $document.PSObject.Properties) {
        [void]$keys.Add($property.Name)
    }
    # Prevent PowerShell pipeline enumeration of the set, including an empty set.
    return ,$keys
}

try {
    $lang = Join-Path $root 'src/main/resources/assets/silkvis/lang'
    $en = Read-LanguageKeys (Join-Path $lang 'en_us.json')
    $ru = Read-LanguageKeys (Join-Path $lang 'ru_ru.json')
    $missingRu = @($en | Where-Object { -not $ru.Contains($_) } | Sort-Object)
    $missingEn = @($ru | Where-Object { -not $en.Contains($_) } | Sort-Object)
    $label = 'PASS'
    if ($missingRu.Count -gt 0 -or $missingEn.Count -gt 0) {
        $label = 'FAIL'
        $violations = $true
    }
    Write-Output ('{0} lang: en_us={1} ru_ru={2} missing_in_ru={3} missing_in_en={4}' -f
        $label, $en.Count, $ru.Count, $missingRu.Count, $missingEn.Count)
    foreach ($key in $missingRu) { Write-Output ('  missing_in_ru: ' + $key) }
    foreach ($key in $missingEn) { Write-Output ('  missing_in_en: ' + $key) }
} catch {
    Write-Output ('ERROR lang: ' + $_.Exception.Message)
    $inputError = $true
}

try {
    $src = Join-Path $root 'src'
    if (-not (Test-Path -LiteralPath $src -PathType Container)) {
        throw 'Missing src directory.'
    }
    $javaFiles = @(Get-ChildItem -LiteralPath $src -Recurse -Force -File -Filter '*.java' |
        Sort-Object FullName)
    if ($javaFiles.Count -eq 0) { throw 'No Java sources found under src.' }
    $badFiles = 0
    foreach ($file in $javaFiles) {
        $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
        $relative = $file.FullName.Substring($root.TrimEnd([char[]]'\/').Length + 1).Replace('\', '/')
        $line = 1
        $column = 1
        $reportedLine = 0
        $badFile = $false
        foreach ($value in $bytes) {
            if ($value -gt 127 -and $reportedLine -ne $line) {
                Write-Output ('  non_ascii: {0}:{1}:{2} byte=0x{3:X2}' -f $relative, $line, $column, $value)
                $reportedLine = $line
                $badFile = $true
            }
            if ($value -eq 10) { $line++; $column = 1 } else { $column++ }
        }
        if ($badFile) { $badFiles++ }
    }
    $label = 'PASS'
    if ($badFiles -gt 0) { $label = 'FAIL'; $violations = $true }
    Write-Output ('{0} ascii: java_files={1} non_ascii_files={2}' -f $label, $javaFiles.Count, $badFiles)
} catch {
    Write-Output ('ERROR ascii: ' + $_.Exception.Message)
    $inputError = $true
}

if ($inputError) { Write-Output 'RESULT ERROR'; exit 2 }
if ($violations) { Write-Output 'RESULT FAIL'; exit 1 }
Write-Output 'RESULT PASS'
exit 0
