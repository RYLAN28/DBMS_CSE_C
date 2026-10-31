$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Prefer a real JDK over Windows' Java launcher aliases.
$jdk = @(
    $env:JAVA_HOME
    'D:\JAVA_INTELLIJ'
    'C:\Program Files\Java\latest\jdk-26'
) | Where-Object { $_ -and (Test-Path $_) -and (Test-Path (Join-Path $_ 'bin\javac.exe')) } | Select-Object -First 1
if ($jdk) {
    $java = Join-Path $jdk 'bin\java.exe'
    $javac = Join-Path $jdk 'bin\javac.exe'
} else {
    $java = (Get-Command java -ErrorAction Stop).Source
    $javac = (Get-Command javac -ErrorAction Stop).Source
}

if (-not $env:ADWISE_DB_PASSWORD) {
    $securePassword = Read-Host 'Enter the PostgreSQL password for the configured user' -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:ADWISE_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}
$sources = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName
& $javac -encoding UTF-8 @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
& $java -cp '.;postgresql-42.7.14.jar' AdServer
