# Builds hfmwic.dll (HEIC photos on Windows through Windows Imaging Component, ADR 0004). Needs
# Visual Studio with C++ (its CMake is used when none is on the PATH) and JAVA_HOME pointing to a JDK.
#
#   powershell -File tools/natives/build-wic.ps1 [-Out <folder>]
param(
    [string]$Out = "$PSScriptRoot\..\..\core\ocr-desktop\src\main\resources\hfm\heif\native\Windows\x86_64",
    # Short and outside Temp: MSBuild's tracking logs fail on long paths.
    [string]$Work = "$env:USERPROFILE\.hfm-wic-build"
)
$ErrorActionPreference = 'Stop'

$cmake = (Get-Command cmake -ErrorAction SilentlyContinue).Source
if ($null -eq $cmake) {
    $vs = & "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    $cmake = "$vs\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
}
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to a JDK' }

& $cmake -S "$PSScriptRoot\wic" -B $Work -A x64 "-DJDK=$env:JAVA_HOME"
if ($LASTEXITCODE -ne 0) { throw 'cmake configure failed' }
& $cmake --build $Work --config Release
if ($LASTEXITCODE -ne 0) { throw 'cmake build failed' }

New-Item -ItemType Directory -Force $Out | Out-Null
Copy-Item "$Work\Release\hfmwic.dll" $Out -Force
Write-Output "hfmwic.dll ($((Get-Item "$Out\hfmwic.dll").Length) bytes) -> $((Resolve-Path $Out).Path)"
