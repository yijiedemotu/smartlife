# =============================================================================
# 智联生活 · 运行版部署包（本地打包，含预编译产物）
#
# 适用场景：服务器无法拉取 maven / node 等"构建镜像"，只能用本地已编译好的
#          产物 + 服务器已有的运行镜像（openjdk/nginx/mysql/redis/rabbitmq）。
#
# 用法（在项目根目录执行）：
#   powershell -ExecutionPolicy Bypass -File scripts\pack-runtime.ps1
#
# 产物：..\smartlife-runtime-<时间戳>.tar.gz（约 58MB，主要是后端的 jar）
#
# 前置：先构建好产物
#   cd backend  && mvn clean package -DskipTests
#   cd frontend && npm install && npm run build
# =============================================================================

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Split-Path -Parent $repoRoot

Write-Host ''
Write-Host '=== 1. 检查预编译产物 ===' -ForegroundColor Cyan
$jar = Join-Path $repoRoot 'backend\target\smartlife-backend-1.0.0.jar'
$dist = Join-Path $repoRoot 'frontend\dist'

if (-not (Test-Path $jar)) {
    throw "缺少后端 jar：$jar`n请先执行：cd backend && mvn clean package -DskipTests"
}
if (-not (Test-Path (Join-Path $dist 'index.html'))) {
    throw "缺少前端 dist：$dist`n请先执行：cd frontend && npm install && npm run build"
}
$jarMb = [math]::Round((Get-Item $jar).Length / 1MB, 2)
$distCount = (Get-ChildItem $dist -Recurse -File | Measure-Object).Count
Write-Host ("  [OK] backend/target/smartlife-backend-1.0.0.jar  {0} MB" -f $jarMb) -ForegroundColor Green
Write-Host ("  [OK] frontend/dist/                              {0} 个文件" -f $distCount) -ForegroundColor Green

Write-Host ''
Write-Host '=== 2. 收集文件（白名单）===' -ForegroundColor Cyan
$staging = Join-Path $env:TEMP "smartlife-runtime-$stamp"
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging -Force | Out-Null

# 配置与脚本（覆盖到服务器既有目录上）
$configFiles = @(
    'docker-compose.runtime.yml',
    'docker-compose.prod.yml',
    '.env.prod.example',
    '.gitattributes',
    'README.md',
    'backend/Dockerfile.runtime',
    'backend/.dockerignore',
    'frontend/Dockerfile.runtime',
    'frontend/nginx.conf',
    'frontend/.dockerignore',
    'sql/init.sql',
    'sql/README.md',
    'sql/manual/upgrade_v2_three_end.sql',
    'scripts/deploy.sh',
    'scripts/backup-db.sh',
    'scripts/restore-db.sh',
    'scripts/verify-package.sh',
    'scripts/smoke-test.ps1'
)
foreach ($f in $configFiles) {
    $src = Join-Path $repoRoot ($f -replace '/', '\')
    if (Test-Path $src) {
        $dst = Join-Path $staging ($f -replace '/', '\')
        New-Item -ItemType Directory -Path (Split-Path $dst -Parent) -Force | Out-Null
        Copy-Item $src $dst -Force
    } else {
        Write-Host "  [跳过] 不存在: $f" -ForegroundColor DarkYellow
    }
}

# 预编译产物（运行版镜像的关键输入）
$targetDir = Join-Path $staging 'backend\target'
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
Copy-Item $jar (Join-Path $targetDir 'smartlife-backend-1.0.0.jar') -Force
Write-Host "  [OK] backend/target/smartlife-backend-1.0.0.jar" -ForegroundColor Green

$distDest = Join-Path $staging 'frontend\dist'
Copy-Item $dist $distDest -Recurse -Force
Write-Host ("  [OK] frontend/dist/ ({0} 个文件)" -f $distCount) -ForegroundColor Green

$allFiles = Get-ChildItem $staging -Recurse -File
$totalMb = [math]::Round(($allFiles | Measure-Object Length -Sum).Sum / 1MB, 2)
Write-Host ("  合计 {0} 个文件，未压缩 {1} MB" -f $allFiles.Count, $totalMb) -ForegroundColor Green

Write-Host ''
Write-Host '=== 3. 打包 ===' -ForegroundColor Cyan
$archive = Join-Path $outDir "smartlife-runtime-$stamp.tar.gz"
tar -czf $archive -C $staging .
if ($LASTEXITCODE -ne 0) { throw 'tar 打包失败' }
$sizeMb = [math]::Round((Get-Item $archive).Length / 1MB, 2)
Write-Host ("  产物: {0}" -f $archive) -ForegroundColor Green
Write-Host ("  压缩后: {0} MB" -f $sizeMb)

Write-Host ''
Write-Host '=== 4. 打包自检 ===' -ForegroundColor Cyan
$mustHave = @(
    'docker-compose.runtime.yml',
    'backend/Dockerfile.runtime',
    'backend/target/smartlife-backend-1.0.0.jar',
    'frontend/Dockerfile.runtime',
    'frontend/dist/index.html',
    'frontend/nginx.conf',
    '.env.prod.example',
    'scripts/verify-package.sh'
)
$missing = 0
foreach ($m in $mustHave) {
    $p = Join-Path $staging ($m -replace '/', '\')
    if (Test-Path $p) { Write-Host "  [OK]   $m" -ForegroundColor Green }
    else { Write-Host "  [缺失] $m" -ForegroundColor Red; $missing++ }
}
if ($missing -gt 0) { throw "打包自检失败：缺失 $missing 个关键文件" }

Remove-Item $staging -Recurse -Force

Write-Host ''
Write-Host '===================================================================' -ForegroundColor Yellow
Write-Host ' 运行版包打包完成。服务器上执行：' -ForegroundColor Yellow
Write-Host ''
Write-Host '   # 1) 解压覆盖到项目目录（保留已有 .env.prod）'
Write-Host '   tar -xzf /root/smartlife-runtime-*.tar.gz -C /opt/smartlife'
Write-Host '   cd /opt/smartlife'
Write-Host ''
Write-Host '   # 2) 校验包完整性（会检查运行版前提条件）'
Write-Host '   bash scripts/verify-package.sh --fix'
Write-Host ''
Write-Host '   # 3) 构建并启动（全部使用本地已有镜像，不联网）'
Write-Host '   docker compose --env-file .env.prod -f docker-compose.runtime.yml up -d --build'
Write-Host '===================================================================' -ForegroundColor Yellow
