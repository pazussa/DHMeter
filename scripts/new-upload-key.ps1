param(
    [string]$KeystorePath = ".secrets/dhmeter-upload.jks",
    [string]$Alias = "upload",
    [Parameter(Mandatory = $true)]
    [string]$StorePassword,
    [string]$KeyPassword,
    [string]$DName = "CN=DHMeter Upload, OU=Mobile, O=DHMeter, L=Unknown, ST=Unknown, C=US",
    [int]$ValidityDays = 9125
)

$ErrorActionPreference = "Stop"

if (-not $KeyPassword) {
    $KeyPassword = $StorePassword
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))

if ([System.IO.Path]::IsPathRooted($KeystorePath)) {
    $fullKeystorePath = $KeystorePath
} else {
    $fullKeystorePath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $KeystorePath))
}

$keystoreDir = Split-Path -Parent $fullKeystorePath
if (-not (Test-Path $keystoreDir)) {
    New-Item -ItemType Directory -Path $keystoreDir -Force | Out-Null
}

if (Test-Path $fullKeystorePath) {
    throw "Keystore already exists: $fullKeystorePath"
}

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool was not found in PATH. Install a JDK and try again."
}

& keytool -genkeypair `
    -v `
    -keystore $fullKeystorePath `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity $ValidityDays `
    -storepass $StorePassword `
    -keypass $KeyPassword `
    -dname $DName

Write-Host ""
Write-Host "Upload keystore created:"
Write-Host $fullKeystorePath
Write-Host ""
Write-Host "Use these environment variables to build a signed AAB:"
Write-Host "`$env:RELEASE_STORE_FILE='$fullKeystorePath'"
Write-Host "`$env:RELEASE_STORE_PASSWORD='<store password>'"
Write-Host "`$env:RELEASE_KEY_ALIAS='$Alias'"
Write-Host "`$env:RELEASE_KEY_PASSWORD='<key password>'"
