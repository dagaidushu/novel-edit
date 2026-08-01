$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $env:JAVA_HOME) {
    $bundledJdk = Join-Path $root '..\.jdk\jdk-17.0.20+8'
    if (Test-Path (Join-Path $bundledJdk 'bin\java.exe')) {
        $env:JAVA_HOME = $bundledJdk
    } else {
        $java = Get-Command java -ErrorAction SilentlyContinue
        if (-not $java) { throw 'JDK 17 is required. Install it and set JAVA_HOME.' }
        $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java.Source)
    }
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Push-Location $root
try {
    # WiX's Gradle download task is incompatible with Gradle 8.2 configuration caching.
    & .\gradlew.bat --no-daemon --offline --no-configuration-cache :shared-core:test :desktop-app:test :desktop-app:packageMsi :desktop-app:createDistributable
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }

    $release = Join-Path $root '..\release'
    New-Item -ItemType Directory -Force -Path $release | Out-Null
    Get-ChildItem -Path (Join-Path $release '*') -File -Include '*.msi','*.zip' | Remove-Item -Force
    Copy-Item desktop-app\build\compose\binaries\main\msi\*.msi $release -Force
    Copy-Item README.md (Join-Path $release 'README.md') -Force
    Copy-Item LICENSE (Join-Path $release 'LICENSE') -Force
    Copy-Item THIRD_PARTY_NOTICES.md (Join-Path $release 'THIRD_PARTY_NOTICES.md') -Force
    Copy-Item packaging\update.json.example (Join-Path $release 'update.json.example') -Force
    $portable = Join-Path $root 'build\portable'
    Remove-Item $portable -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $portable | Out-Null
    Copy-Item desktop-app\build\compose\binaries\main\app\NovelEdit $portable -Recurse -Force
    Copy-Item packaging\NovelEdit-Portable.cmd $portable -Force
    Copy-Item README.md $portable -Force
    Copy-Item LICENSE $portable -Force
    Copy-Item THIRD_PARTY_NOTICES.md $portable -Force
    Copy-Item packaging\update.json.example $portable -Force
    Compress-Archive -Path "$portable\*" -DestinationPath (Join-Path $release 'NovelEdit-Windows-x64-portable.zip') -Force
    Get-ChildItem $release -File | Where-Object Extension -in '.msi','.zip' | Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash)  $([IO.Path]::GetFileName($_.Path))" } | Set-Content (Join-Path $release 'SHA256SUMS.txt') -Encoding ascii
} finally {
    Pop-Location
}
