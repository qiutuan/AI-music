$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$fallbackNpm = 'E:\create\Nodejs\npm.cmd'
$fallbackJdk = 'C:\Users\Administrator\Documents\ChatGPT\My\.tooling\jdk\jdk-17.0.20.1+1'
$fallbackMaven = 'C:\Users\Administrator\Documents\ChatGPT\My\.tooling\maven\apache-maven-3.9.16\bin\mvn.cmd'

$npm = if (Get-Command npm.cmd -ErrorAction SilentlyContinue) { 'npm.cmd' } elseif (Test-Path $fallbackNpm) { $fallbackNpm } else { throw '未找到 npm。请先安装 Node.js 20+。' }
$maven = if (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { 'mvn.cmd' } elseif (Test-Path $fallbackMaven) { $fallbackMaven } else { throw '未找到 Maven。请先安装 Maven 3.9+。' }
if (-not (Get-Command java -ErrorAction SilentlyContinue) -and (Test-Path $fallbackJdk)) {
    $env:JAVA_HOME = $fallbackJdk
    $env:PATH = "$fallbackJdk\bin;$env:PATH"
}

Push-Location (Join-Path $projectRoot 'frontend')
try {
    & $npm install --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
    & $npm run build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
} finally { Pop-Location }

$staticDir = Join-Path $projectRoot 'backend\src\main\resources\static'
New-Item -ItemType Directory -Path $staticDir -Force | Out-Null
Copy-Item -Path (Join-Path $projectRoot 'frontend\dist\*') -Destination $staticDir -Recurse -Force

Push-Location (Join-Path $projectRoot 'backend')
try {
    & $maven clean package
    if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }
} finally { Pop-Location }
Write-Host "构建完成：$projectRoot\backend\target\ai-music-studio-1.0.0.jar"

