# =============================================================================
# 智联生活 · 本地打包脚本（Windows PowerShell）
#
# 用法（在项目根目录执行）：
#   pwsh -File scripts\pack-package.ps1              # 生成 tar.gz（保留 Linux 可执行位）
#   pwsh -File scripts\pack-package.ps1 -Format zip  # 生成 zip（体积更小，但会丢可执行位）
#
# 产物：..\smartlife-deploy-<时间戳>.tar.gz（放在项目上一级目录）
#
# 设计要点：
#   1) 用 **白名单** 明确列出要打包的路径，而不是"排除法"——避免误带 node_modules/target/日志
#   2) tar.gz 会保留文件权限与 LF 换行；zip 在 Windows 上会丢可执行位（服务器需 chmod）
#   3) 打包后立即自检：列出清单 + 校验关键文件是否齐全
# =============================================================================
param(
    [ValidateSet('tar', 'zip')]
    [string]$Format = 'tar'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Split-Path -Parent $repoRoot

# ---------------------------------------------------------------- 白名单
$includeFiles = @(
    '.gitignore',
    '.gitattributes',
    '.env.prod.example',
    'docker-compose.yml',
    'docker-compose.full.yml',
    'docker-compose.prod.yml',
    'README.md'
)
$includeDirs = @(
    'backend/src',
    'backend/pom.xml',
    'backend/Dockerfile',
    'backend/settings.xml',
    'backend/.dockerignore',
    'frontend/src',
    'frontend/index.html',
    'frontend/package.json',
    'frontend/package-lock.json',
    'frontend/vite.config.js',
    'frontend/Dockerfile',
    'frontend/nginx.conf',
    'frontend/.dockerignore',
    'sql',
    'scripts',
    'docs'
)

Write-Host ''
Write-Host '=== 1. 收集要打包的文件 ===' -ForegroundColor Cyan
$staging = Join-Path $env:TEMP "smartlife-pkg-$stamp"
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging -Force | Out-Null

$count = 0
$bytes = 0

# 排除规则（白名单内部仍需剔除的产物）
$excludePattern = '\\(node_modules|target|dist|\.git|\.idea|backup)(\\|$)|\.log$|\.rdb$'

function Copy-ItemSafe {
    param([string]$source, [string]$destRoot)
    $rel = $source.Substring($repoRoot.Length).TrimStart('\')
    if ($rel -match $excludePattern) { return }
    $dest = Join-Path $destRoot $rel
    $destDir = Split-Path -Parent $dest
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }
    Copy-Item -Path $source -Destination $dest -Force
    $script:count++
    $script:bytes += (Get-Item $source).Length
}

foreach ($f in $includeFiles) {
    $p = Join-Path $repoRoot $f
    if (Test-Path $p) { Copy-ItemSafe -source $p -destRoot $staging }
    else { Write-Host "  [跳过] 不存在: $f" -ForegroundColor DarkYellow }
}
foreach ($d in $includeDirs) {
    $p = Join-Path $repoRoot $d
    if (-not (Test-Path $p)) { Write-Host "  [跳过] 不存在: $d" -ForegroundColor DarkYellow; continue }
    if ((Get-Item $p).PSIsContainer) {
        Get-ChildItem -Path $p -Recurse -File -Force | ForEach-Object { Copy-ItemSafe -source $_.FullName -destRoot $staging }
    } else {
        Copy-ItemSafe -source $p -destRoot $staging
    }
}
Write-Host ("  已收集 {0} 个文件，未压缩 {1:N1} MB" -f $count, ($bytes / 1MB)) -ForegroundColor Green

Write-Host ''
Write-Host '=== 2. 打包 ===' -ForegroundColor Cyan
if ($Format -eq 'tar') {
    $archive = Join-Path $outDir "smartlife-deploy-$stamp.tar.gz"
    # Windows 10+ 自带 bsdtar，支持 -czf 且保留权限位
    tar -czf $archive -C $staging .
    if ($LASTEXITCODE -ne 0) { throw "tar 打包失败" }
} else {
    $archive = Join-Path $outDir "smartlife-deploy-$stamp.zip"
    Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $archive -Force
}
Write-Host ("  产物: {0}" -f $archive) -ForegroundColor Green
Write-Host ("  压缩后大小: {0:N2} MB" -f ((Get-Item $archive).Length / 1MB))

Write-Host ''
Write-Host '=== 3. 打包自检（关键文件是否都进包了）===' -ForegroundColor Cyan
$mustHave = @(
    'docker-compose.prod.yml',
    '.env.prod.example',
    '.gitattributes',
    'backend/Dockerfile',
    'backend/settings.xml',
    'backend/pom.xml',
    'frontend/Dockerfile',
    'frontend/nginx.conf',
    'sql/init.sql',
    'sql/manual/upgrade_v2_three_end.sql',
    'scripts/deploy.sh',
    'scripts/verify-package.sh',
    'docs/重新部署验证清单.md',
    'backend/src/main/java/com/smartlife/controller/MerchantController.java',
    'frontend/src/views/admin/Applies.vue'
)
$missing = 0
foreach ($m in $mustHave) {
    $p = Join-Path $staging ($m -replace '/', '\')
    if (Test-Path $p) { Write-Host "  [OK]   $m" -ForegroundColor Green }
    else { Write-Host "  [缺失] $m" -ForegroundColor Red; $missing++ }
}
if ($missing -gt 0) { throw "打包自检失败：缺失 $missing 个关键文件" }

# 顺手检查有没有把大文件/产物带进去
Write-Host ''
Write-Host '=== 4. 检查是否误带产物 ===' -ForegroundColor Cyan
$bad = Get-ChildItem -Path $staging -Recurse -Force -Directory |
       Where-Object { $_.Name -in @('node_modules', 'target', 'dist', '.git', '.idea') }
if ($bad) { $bad | ForEach-Object { Write-Host "  [误带] $($_.FullName)" -ForegroundColor Red } }
else { Write-Host '  [OK] 无 node_modules / target / dist / .git / .idea' -ForegroundColor Green }

Remove-Item $staging -Recurse -Force

Write-Host ''
Write-Host '===================================================================' -ForegroundColor Yellow
Write-Host ' 打包完成，下一步上传到服务器：' -ForegroundColor Yellow
Write-Host ''
Write-Host ("   scp `"{0}`" root@<公网IP>:/root/" -f $archive)
Write-Host ''
Write-Host ' 服务器上解压（tar.gz 保留权限位，无需 chmod）：' -ForegroundColor Yellow
Write-Host '   mkdir -p /opt/smartlife && tar -xzf /root/smartlife-deploy-*.tar.gz -C /opt/smartlife'
Write-Host '   cd /opt/smartlife && bash scripts/verify-package.sh'
Write-Host '===================================================================' -ForegroundColor Yellow
