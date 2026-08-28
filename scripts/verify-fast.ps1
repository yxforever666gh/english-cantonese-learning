[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"

function Find-JdkHome {
    $javaCandidates = [System.Collections.Generic.List[string]]::new()

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaCandidates.Add((Join-Path $env:JAVA_HOME "bin\java.exe"))
    }

    Get-Command java.exe -All -ErrorAction SilentlyContinue | ForEach-Object {
        $javaCandidates.Add($_.Source)
    }

    $knownJdkParents = @(
        (Join-Path $env:USERPROFILE ".jdks"),
        (Join-Path $env:USERPROFILE ".gradle\jdks"),
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:ProgramFiles "Microsoft"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "JetBrains")
    )
    foreach ($parent in $knownJdkParents) {
        if (Test-Path -LiteralPath $parent -PathType Container) {
            Get-ChildItem -LiteralPath $parent -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $javaCandidates.Add((Join-Path $_.FullName "bin\java.exe"))
            }
        }
    }
    $javaCandidates.Add((Join-Path $env:ProgramFiles "Android\Android Studio\jbr\bin\java.exe"))

    foreach ($javaCandidate in @($javaCandidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $javaCandidate -PathType Leaf)) {
            continue
        }

        $versionOutput = & $javaCandidate -version 2>&1
        if ($LASTEXITCODE -ne 0) {
            continue
        }
        $versionText = $versionOutput -join " "
        $versionMatch = [regex]::Match($versionText, '(?:java|openjdk) version "(\d+)(?:\.(\d+))?')
        if (-not $versionMatch.Success) {
            continue
        }

        $majorVersion = [int]$versionMatch.Groups[1].Value
        if ($majorVersion -eq 1 -and $versionMatch.Groups[2].Success) {
            $majorVersion = [int]$versionMatch.Groups[2].Value
        }
        if ($majorVersion -ge 17) {
            return Split-Path -Parent (Split-Path -Parent $javaCandidate)
        }
    }

    return $null
}

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper not found at $gradleWrapper"
}

$detectedJavaHome = Find-JdkHome
if ($null -eq $detectedJavaHome) {
    throw "A working JDK 17 or newer was not found. Set JAVA_HOME to a compatible JDK installation."
}
$env:JAVA_HOME = $detectedJavaHome

$javaExecutable = Join-Path $env:JAVA_HOME "bin\java.exe"
Write-Host "Fast verification: testUiTestUnitTest"
Write-Verbose "Using Java: $javaExecutable"

Push-Location -LiteralPath $projectRoot
try {
    & $gradleWrapper --console=plain testUiTestUnitTest
    if ($LASTEXITCODE -ne 0) {
        throw "Fast verification failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host "Fast verification passed."
