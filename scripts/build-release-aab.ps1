param(
    [string]$StoreFile = $env:RELEASE_STORE_FILE,
    [string]$StorePassword = $env:RELEASE_STORE_PASSWORD,
    [string]$KeyAlias = $env:RELEASE_KEY_ALIAS,
    [string]$KeyPassword = $env:RELEASE_KEY_PASSWORD
)

$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
Set-Location $repoRoot

$missing = @()
if (-not $StoreFile) { $missing += "RELEASE_STORE_FILE" }
if (-not $StorePassword) { $missing += "RELEASE_STORE_PASSWORD" }
if (-not $KeyAlias) { $missing += "RELEASE_KEY_ALIAS" }
if (-not $KeyPassword) { $missing += "RELEASE_KEY_PASSWORD" }

if ($missing.Count -gt 0) {
    throw "Missing signing values: $($missing -join ', ')"
}

if ([System.IO.Path]::IsPathRooted($StoreFile)) {
    $fullStoreFile = $StoreFile
} else {
    $fullStoreFile = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $StoreFile))
}

if (-not (Test-Path $fullStoreFile)) {
    throw "Keystore not found: $fullStoreFile"
}

$env:RELEASE_STORE_FILE = $fullStoreFile
$env:RELEASE_STORE_PASSWORD = $StorePassword
$env:RELEASE_KEY_ALIAS = $KeyAlias
$env:RELEASE_KEY_PASSWORD = $KeyPassword

& .\gradlew.bat :app:bundleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Gradle bundleRelease failed."
}

$aabPath = Join-Path $repoRoot "app\build\outputs\bundle\release\app-release.aab"
if (-not (Test-Path $aabPath)) {
    throw "AAB not found: $aabPath"
}

if (Get-Command jarsigner -ErrorAction SilentlyContinue) {
    $verifyOutput = & jarsigner -verify -verbose -certs $aabPath 2>&1 | Out-String
    if ($verifyOutput -match "jar is unsigned") {
        throw "AAB is unsigned. Verify RELEASE_* values and retry."
    }
}

Write-Host ""
Write-Host "Signed AAB ready:"
Write-Host $aabPath
