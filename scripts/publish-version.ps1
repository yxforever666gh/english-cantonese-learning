[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+(?:\.\d+)?$')]
    [string]$Version,

    [switch]$Push
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$buildFile = Join-Path $projectRoot "app\build.gradle.kts"
$changeLog = Join-Path $projectRoot "CHANGELOG.md"
$fullVerification = Join-Path $PSScriptRoot "verify-full.ps1"
$tagName = "v$Version"
$expectedRemotePattern = 'github\.com[/:]yxforever666gh/english-cantonese-learning(?:\.git)?/?$'

function Invoke-GitChecked {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [switch]$Capture
    )

    $output = & git.exe -C $projectRoot @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $exitCode.`n$($output -join [Environment]::NewLine)"
    }
    if ($Capture) {
        return @($output)
    }
    $output | ForEach-Object { Write-Host $_ }
}

if ($null -eq (Get-Command git.exe -ErrorAction SilentlyContinue)) {
    throw "git.exe was not found on PATH."
}
foreach ($requiredFile in @($buildFile, $changeLog, $fullVerification)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required file not found: $requiredFile"
    }
}

$repositoryRoot = (Invoke-GitChecked -Arguments @("rev-parse", "--show-toplevel") -Capture | Select-Object -First 1).Trim()
if ((Resolve-Path -LiteralPath $repositoryRoot).Path -ne $projectRoot) {
    throw "Run this script from its own repository; detected Git root: $repositoryRoot"
}

$branch = (Invoke-GitChecked -Arguments @("branch", "--show-current") -Capture | Select-Object -First 1).Trim()
if ($branch -ne "main") {
    throw "Publishing is only allowed from main; current branch is '$branch'."
}

$status = @(Invoke-GitChecked -Arguments @("status", "--porcelain=v1", "--untracked-files=all") -Capture)
if ($status.Count -ne 0) {
    throw "The worktree must be clean before publishing.`n$($status -join [Environment]::NewLine)"
}

$buildContent = Get-Content -LiteralPath $buildFile -Raw
$versionNameMatch = [regex]::Match($buildContent, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($buildContent, 'versionCode\s*=\s*(\d+)')
if (-not $versionNameMatch.Success -or $versionNameMatch.Groups[1].Value -ne $Version) {
    throw "app/build.gradle.kts versionName must equal $Version."
}
if (-not $versionCodeMatch.Success -or [int]$versionCodeMatch.Groups[1].Value -lt 1) {
    throw "app/build.gradle.kts must contain a positive integer versionCode."
}
$changeLogContent = Get-Content -LiteralPath $changeLog -Raw
if ($changeLogContent -notmatch "(?m)^## \[$([regex]::Escape($Version))\](?:\s|$)") {
    throw "CHANGELOG.md does not contain a [$Version] release heading."
}

$localTag = @(Invoke-GitChecked -Arguments @("tag", "--list", $tagName) -Capture)
if ($localTag.Count -ne 0) {
    throw "Local tag $tagName already exists."
}

$originUrl = (Invoke-GitChecked -Arguments @("remote", "get-url", "origin") -Capture | Select-Object -First 1).Trim()
if ($originUrl -notmatch $expectedRemotePattern) {
    throw "origin must target yxforever666gh/english-cantonese-learning; found '$originUrl'."
}

$remoteTag = & git.exe -C $projectRoot ls-remote --tags origin "refs/tags/$tagName" 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect tags on origin.`n$($remoteTag -join [Environment]::NewLine)"
}
if (@($remoteTag).Count -ne 0) {
    throw "Remote tag $tagName already exists."
}

& $fullVerification

$postVerificationStatus = @(Invoke-GitChecked -Arguments @("status", "--porcelain=v1", "--untracked-files=all") -Capture)
if ($postVerificationStatus.Count -ne 0) {
    throw "Verification changed the worktree; review it before tagging.`n$($postVerificationStatus -join [Environment]::NewLine)"
}

Invoke-GitChecked -Arguments @("tag", "-a", $tagName, "-m", "English and Cantonese Learning $Version")
Write-Host "Created annotated tag $tagName."

if ($Push) {
    Invoke-GitChecked -Arguments @("push", "-u", "origin", "main")
    Invoke-GitChecked -Arguments @("push", "origin", "refs/tags/$tagName")
    Write-Host "Pushed main and $tagName to origin."
}
else {
    Write-Host "Push was not requested; main and $tagName remain local."
}
