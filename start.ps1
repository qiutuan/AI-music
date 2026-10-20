$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $projectRoot 'backend\target\ai-music-studio-1.0.0.jar'
$fallbackJdk = 'C:\Users\Administrator\Documents\ChatGPT\My\.tooling\jdk\jdk-17.0.20.1+1\bin\java.exe'
if (-not (Test-Path $jar)) { throw '未找到可运行文件。请先执行 .\build.ps1。' }
$java = if (Get-Command java -ErrorAction SilentlyContinue) { 'java' } elseif (Test-Path $fallbackJdk) { $fallbackJdk } else { throw '未找到 Java 17+。' }
$env:STUDIO_DATA_DIR = Join-Path $projectRoot 'data'
Write-Host '谱间已启动：http://127.0.0.1:8080'
& $java -jar $jar

