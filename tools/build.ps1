# 禅息 (ZenXi) 构建脚本：aapt2 -> javac -> d8 -> 打包 -> zipalign -> apksigner
#
# 用法:  powershell -ExecutionPolicy Bypass -File tools\build.ps1
#
param(
    [string]$SdkRoot = "$PSScriptRoot\..\.toolchain\android-sdk",
    [string]$JdkHome = "C:\Program Files\Java\jdk-21"
)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$repo = (Resolve-Path "$PSScriptRoot\..").Path
$app = Join-Path $repo "app"
$build = Join-Path $repo "build"
$dist = Join-Path $repo "dist"
$bt = Join-Path $SdkRoot "build-tools\35.0.0"
$androidJar = Join-Path $SdkRoot "platforms\android-35\android.jar"
$versionName = "1.0.1"
$versionCode = 2

foreach ($p in @($bt, $androidJar, (Join-Path $app "AndroidManifest.xml"))) {
    if (-not (Test-Path $p)) { throw "缺少构建依赖: $p" }
}

if (Test-Path $JdkHome) { $env:JAVA_HOME = $JdkHome }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

New-Item -ItemType Directory -Force -Path $build, $dist | Out-Null
Remove-Item -Recurse -Force (Join-Path $build "*") -ErrorAction SilentlyContinue
# 清掉 dist 里的旧包，避免混淆（dist 里只放产物）
Remove-Item -Recurse -Force (Join-Path $dist "*") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $build "gen"), (Join-Path $build "classes"), (Join-Path $build "dex") | Out-Null

function Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }

Step "aapt2 compile (res -> res.zip)"
& "$bt\aapt2.exe" compile --dir (Join-Path $app "res") -o (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile 失败" }

Step "aapt2 link (base.apk + R.java)"
& "$bt\aapt2.exe" link `
    -o (Join-Path $build "base.apk") `
    -I $androidJar `
    --manifest (Join-Path $app "AndroidManifest.xml") `
    --java (Join-Path $build "gen") `
    --min-sdk-version 24 `
    --target-sdk-version 35 `
    --version-code $versionCode `
    --version-name $versionName `
    --auto-add-overlay `
    (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败" }

Step "javac"
$sources = @()
$sources += Get-ChildItem -Recurse -Path (Join-Path $app "java") -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem -Recurse -Path (Join-Path $build "gen") -Filter *.java | ForEach-Object { $_.FullName }
$listFile = Join-Path $build "sources.txt"
($sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }) | Set-Content -Encoding ASCII $listFile
& javac "@$listFile" -encoding UTF-8 --release 11 -classpath $androidJar `
    -Xlint:none -nowarn -d (Join-Path $build "classes")
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

Step "d8 (-> classes.dex)"
$classes = Get-ChildItem -Recurse -Path (Join-Path $build "classes") -Filter *.class | ForEach-Object { $_.FullName }
if ($classes.Count -eq 0) { throw "没有编译出 class" }
& "$bt\d8.bat" --release --min-api 24 --lib $androidJar --output (Join-Path $build "dex") @classes
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }

Step "打包 APK"
python (Join-Path $PSScriptRoot "package_apk.py") (Join-Path $build "base.apk") (Join-Path $build "dex") (Join-Path $build "unsigned.apk")
if ($LASTEXITCODE -ne 0) { throw "打包失败" }

Step "zipalign"
& "$bt\zipalign.exe" -f -p 4 (Join-Path $build "unsigned.apk") (Join-Path $build "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

Step "签名"
$ksDir = Join-Path $repo "keystore"
$ks = Join-Path $ksDir "zenxi.jks"
New-Item -ItemType Directory -Force -Path $ksDir | Out-Null
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -v -keystore $ks -alias zenxi -keyalg RSA -keysize 2048 `
        -validity 10950 -storepass zenxi2026 -keypass zenxi2026 `
        -dname "CN=ZenXi Yoga Timer, OU=ZenXi, O=ZenXi, L=Shanghai, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "生成签名密钥失败" }
}
$outApk = Join-Path $dist ("ZenXi-v{0}.apk" -f $versionName)
if (Test-Path $outApk) { Remove-Item $outApk -Force }
& "$bt\apksigner.bat" sign --ks $ks --ks-key-alias zenxi `
    --ks-pass pass:zenxi2026 --key-pass pass:zenxi2026 `
    --min-sdk-version 24 --max-sdk-version 35 `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out $outApk (Join-Path $build "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "签名失败" }

Step "校验"
& "$bt\apksigner.bat" verify --verbose $outApk
if ($LASTEXITCODE -ne 0) { throw "签名校验失败" }
& "$bt\aapt2.exe" dump badging $outApk | Select-String -Pattern "^package|^launchable-activity|^application-label|sdkVersion|targetSdkVersion"

$size = (Get-Item $outApk).Length / 1MB
Write-Host ("`n完成: {0}  ({1:N2} MB)" -f $outApk, $size) -ForegroundColor Green
