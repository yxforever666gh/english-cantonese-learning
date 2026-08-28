[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
$fastVerification = Join-Path $PSScriptRoot "verify-fast.ps1"

function Find-AdbExecutable {
    $adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -ne $adbCommand) {
        return $adbCommand.Source
    }

    foreach ($sdkVariable in @("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
        $sdkRoot = [Environment]::GetEnvironmentVariable($sdkVariable)
        if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
            $candidate = Join-Path $sdkRoot "platform-tools\adb.exe"
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return $candidate
            }
        }
    }

    $localProperties = Join-Path $projectRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=(.+)$' } |
            Select-Object -First 1
        if ($null -ne $sdkLine -and $sdkLine -match '^sdk\.dir=(.+)$') {
            $sdkRoot = $Matches[1].Trim() -replace '\\:', ':' -replace '\\\\', '\'
            $candidate = Join-Path $sdkRoot "platform-tools\adb.exe"
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return $candidate
            }
        }
    }

    return $null
}

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper not found at $gradleWrapper"
}
if (-not (Test-Path -LiteralPath $fastVerification -PathType Leaf)) {
    throw "Fast verification script not found at $fastVerification"
}

& $fastVerification

Write-Host "Full verification: lintDebug and assembleDebug"
Push-Location -LiteralPath $projectRoot
try {
    & $gradleWrapper --console=plain lintDebug assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Lint or Debug assembly failed with exit code $LASTEXITCODE."
    }

    $adbExecutable = Find-AdbExecutable
    if ($null -eq $adbExecutable) {
        Write-Host "ADB not found; skipping connectedUiTestAndroidTest."
    }
    else {
        $deviceOutput = & $adbExecutable devices 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "ADB device discovery failed; skipping connectedUiTestAndroidTest."
        }
        else {
            $authorizedDevices = @($deviceOutput | Where-Object { $_ -match '^\S+\s+device$' })
            if ($authorizedDevices.Count -eq 0) {
                Write-Host "No authorized ADB device found; skipping connectedUiTestAndroidTest."
            }
            else {
                Write-Host "Authorized ADB device detected; running isolated connected tests."
                & $gradleWrapper --console=plain connectedUiTestAndroidTest
                if ($LASTEXITCODE -ne 0) {
                    throw "Connected device tests failed with exit code $LASTEXITCODE."
                }
            }
        }
    }
}
finally {
    Pop-Location
}

Write-Host "Full verification passed."
