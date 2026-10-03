# 用纯 JVM 跑时间线单元测试（不需要 Android 设备）
$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$repo = (Resolve-Path "$PSScriptRoot\..").Path
$jdk = "C:\Program Files\Java\jdk-21"
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk; $env:PATH = "$jdk\bin;$env:PATH" }

$out = Join-Path $repo "build\test"
New-Item -ItemType Directory -Force -Path $out | Out-Null
Remove-Item -Recurse -Force (Join-Path $out "*") -ErrorAction SilentlyContinue

& javac -encoding UTF-8 -d $out `
    (Join-Path $repo "app\java\com\zenxi\yogatimer\TimerConfig.java") `
    (Join-Path $repo "app\java\com\zenxi\yogatimer\Timeline.java") `
    (Join-Path $repo "tools\TimelineTest.java")
if ($LASTEXITCODE -ne 0) { throw "测试编译失败" }

& java "-Dfile.encoding=UTF-8" -cp $out com.zenxi.yogatimer.TimelineTest
exit $LASTEXITCODE
