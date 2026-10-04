# Builds hfmheif.dll (HEIC photos on the desktop, ADR 0004) on Windows x64 from the pinned libheif
# and libde265 sources in heif.env. Needs Visual Studio with C++ (its CMake is used when none is on
# the PATH) and JAVA_HOME pointing to a JDK.
#
#   powershell -File tools/natives/build-heif.ps1 [-Out <folder>]
param(
    [string]$Out = "$PSScriptRoot\..\..\core\ocr-desktop\src\main\resources\hfm\heif\native\Windows\x86_64",
    # Short and outside Temp: MSBuild's tracking logs fail on long paths.
    [string]$Work = "$env:USERPROFILE\.hfm-heif-build"
)
$ErrorActionPreference = 'Stop'

$versions = @{}
Get-Content "$PSScriptRoot\heif.env" | Where-Object { $_ -match '^(\w+)=(.+)$' } | ForEach-Object { $versions[$Matches[1]] = $Matches[2].Trim() }

$cmake = (Get-Command cmake -ErrorAction SilentlyContinue).Source
if ($null -eq $cmake) {
    $vs = & "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    $cmake = "$vs\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
}
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to a JDK' }

function Get-Source([string]$name, [string]$version, [string]$sha) {
    $file = "$Work\$name-$version.tar.gz"
    if (-not (Test-Path $file)) {
        Invoke-WebRequest -UseBasicParsing "https://github.com/strukturag/$name/releases/download/v$version/$name-$version.tar.gz" -OutFile $file
    }
    $actual = (Get-FileHash -Algorithm SHA256 $file).Hash.ToLower()
    if ($actual -ne $sha) { throw "$name-$version.tar.gz has SHA-256 $actual, expected $sha" }
    tar -xzf $file -C $Work
    if ($LASTEXITCODE -ne 0) { throw "could not unpack $file" }
    return "$Work\$name-$version"
}

function Invoke-CMake([string[]]$arguments) {
    & $cmake @arguments
    if ($LASTEXITCODE -ne 0) { throw "cmake $($arguments -join ' ') failed" }
}

New-Item -ItemType Directory -Force $Work | Out-Null
$prefix = "$Work\prefix"
# The static C runtime, so the DLL needs nothing that Windows or the bundled Java runtime lacks.
$common = @('-A', 'x64', '-DBUILD_SHARED_LIBS=OFF', '-DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded', "-DCMAKE_INSTALL_PREFIX=$prefix")

$de265 = Get-Source 'libde265' $versions.LIBDE265_VERSION $versions.LIBDE265_SHA256
Invoke-CMake (@('-S', $de265, '-B', "$Work\build-de265") + $common + @('-DENABLE_SDL=OFF', '-DENABLE_DECODER=OFF', '-DENABLE_ENCODER=OFF'))
Invoke-CMake @('--build', "$Work\build-de265", '--config', 'Release', '--target', 'install')

# Only the HEVC decoder (libde265, built in): no encoders, other codecs, plugins or tools.
$heif = Get-Source 'libheif' $versions.LIBHEIF_VERSION $versions.LIBHEIF_SHA256
$off = 'X265', 'KVAZAAR', 'UVG266', 'VVDEC', 'VVENC', 'X264', 'OpenH264_DECODER', 'DAV1D', 'AOM_DECODER', 'AOM_ENCODER', 'SvtEnc', 'RAV1E',
    'JPEG_DECODER', 'JPEG_ENCODER', 'OpenJPEG_DECODER', 'OpenJPEG_ENCODER', 'FFMPEG_DECODER', 'OPENJPH_ENCODER', 'UNCOMPRESSED_CODEC',
    'LIBSHARPYUV', 'EXAMPLES', 'GDK_PIXBUF' | ForEach-Object { "-DWITH_$_=OFF" }
Invoke-CMake (@('-S', $heif, '-B', "$Work\build-heif") + $common + $off + @(
    '-DWITH_LIBDE265=ON', '-DWITH_LIBDE265_PLUGIN=OFF', '-DENABLE_PLUGIN_LOADING=OFF', '-DBUILD_TESTING=OFF', '-DBUILD_DOCUMENTATION=OFF',
    '-DCMAKE_DISABLE_FIND_PACKAGE_ZLIB=ON', '-DCMAKE_DISABLE_FIND_PACKAGE_Brotli=ON',
    "-DLIBDE265_INCLUDE_DIR=$prefix\include", "-DLIBDE265_LIBRARY=$prefix\lib\libde265.lib",
    '-DCMAKE_C_FLAGS=/DLIBDE265_STATIC_BUILD', '-DCMAKE_CXX_FLAGS=/DLIBDE265_STATIC_BUILD /EHsc'))
Invoke-CMake @('--build', "$Work\build-heif", '--config', 'Release', '--target', 'install')

Invoke-CMake (@('-S', "$PSScriptRoot\heif", '-B', "$Work\build-hfmheif", '-A', 'x64', '-DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded', "-DDEPS=$prefix", "-DJDK=$env:JAVA_HOME"))
Invoke-CMake @('--build', "$Work\build-hfmheif", '--config', 'Release')

New-Item -ItemType Directory -Force $Out | Out-Null
Copy-Item "$Work\build-hfmheif\Release\hfmheif.dll" $Out -Force
Write-Output "hfmheif.dll ($((Get-Item "$Out\hfmheif.dll").Length) bytes) -> $((Resolve-Path $Out).Path)"
