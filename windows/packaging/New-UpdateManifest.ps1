[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version,

    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string]$Repository,

    [string]$ReleaseTag = "v$Version",
    [string]$Notes = "",
    [string]$ReleaseDirectory = '',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ReleaseDirectory)) { $ReleaseDirectory = Join-Path $PSScriptRoot '..\..\release' }
if ([string]::IsNullOrWhiteSpace($OutputPath)) { $OutputPath = Join-Path $PSScriptRoot '..\..\release\update.json' }
$release = [IO.Path]::GetFullPath($ReleaseDirectory)
$output = [IO.Path]::GetFullPath($OutputPath)
$msi = Join-Path $release "NovelEdit-$Version.msi"
$portable = Join-Path $release 'NovelEdit-Windows-x64-portable.zip'

foreach ($file in @($msi, $portable)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "发布文件不存在：$file"
    }
}

$base = "https://github.com/$Repository/releases/download/$ReleaseTag"
$manifest = [ordered]@{
    version = $Version
    msiUrl = "$base/$([Uri]::EscapeDataString([IO.Path]::GetFileName($msi)))"
    portableUrl = "$base/$([Uri]::EscapeDataString([IO.Path]::GetFileName($portable)))"
    msiSha256 = (Get-FileHash -LiteralPath $msi -Algorithm SHA256).Hash.ToLowerInvariant()
    portableSha256 = (Get-FileHash -LiteralPath $portable -Algorithm SHA256).Hash.ToLowerInvariant()
    notes = $Notes
}

$parent = Split-Path -Parent $output
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$temporary = Join-Path $parent ".update-$([Guid]::NewGuid().ToString('N')).tmp"
try {
    [IO.File]::WriteAllText($temporary, ($manifest | ConvertTo-Json -Depth 3), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary -Destination $output -Force
} finally {
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
}

Write-Host "已生成：$output"
