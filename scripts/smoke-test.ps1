# =============================================================================
# Smoke test for the three-end platform (user / merchant / admin)
# Verifies: role routing, end-point authorization, merchant data isolation,
#           onboarding audit flow, order fulfilment, dashboards, account governance.
#
# Usage (backend must be running on http://127.0.0.1:8080/api):
#   powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
# =============================================================================
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080/api'
$script:pass = 0
$script:fail = 0

function Write-Result($name, $ok, $detail) {
    if ($ok) {
        $script:pass++
        Write-Host ("  [PASS] " + $name) -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host ("  [FAIL] " + $name + " -> " + $detail) -ForegroundColor Red
    }
}

function Invoke-Api {
    param($Method, $Path, $Token, $Body)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{
        Method      = $Method
        Uri         = ($base + $Path)
        Headers     = $headers
        ContentType = 'application/json; charset=utf-8'
    }
    if ($Body) { $params['Body'] = ($Body | ConvertTo-Json -Depth 6 -Compress) }
    try {
        $resp = Invoke-WebRequest @params -UseBasicParsing
        return ($resp.Content | ConvertFrom-Json)
    } catch {
        $raw = $null
        if ($_.Exception.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $raw = $reader.ReadToEnd()
            } catch { $raw = $null }
        }
        if ($raw) {
            try { return ($raw | ConvertFrom-Json) } catch { return @{ code = -1; msg = $raw } }
        }
        return @{ code = -1; msg = $_.Exception.Message }
    }
}

function Login($phone, $pwd) {
    $r = Invoke-Api 'POST' '/auth/login' $null @{ phone = $phone; password = $pwd }
    if ($r.code -eq 1) { return $r.data }
    return $null
}

function Get-Records($resp) {
    if ($resp -and $resp.data -and $resp.data.records) { return @($resp.data.records) }
    return @()
}

Write-Host ""
Write-Host "=== 1. Login on all three ends (role + landing path) ===" -ForegroundColor Cyan
$admin = Login '13800000000' 'admin123'
$merchant = Login '13700000001' 'merchant123'
$merchant2 = Login '13700000002' 'merchant123'
$user = Login '13900000001' '123456'

Write-Result 'admin login role=3 homePath=/admin/dashboard' ($admin -and $admin.role -eq 3 -and $admin.homePath -eq '/admin/dashboard') ($admin | ConvertTo-Json -Compress)
Write-Result 'approved merchant login role=2 homePath=/merchant/dashboard' ($merchant -and $merchant.role -eq 2 -and $merchant.homePath -eq '/merchant/dashboard') ($merchant | ConvertTo-Json -Compress)
Write-Result 'merchant login carries own shopId=1' ($merchant -and $merchant.shopId -eq 1) ($merchant | ConvertTo-Json -Compress)
Write-Result 'pending merchant has no shop yet' ($merchant2 -and $null -eq $merchant2.shopId) ($merchant2 | ConvertTo-Json -Compress)
Write-Result 'user login role=1 homePath=/user/home' ($user -and $user.role -eq 1 -and $user.homePath -eq '/user/home') ($user | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "=== 2. End-point authorization (interceptor by path prefix) ===" -ForegroundColor Cyan
$r = Invoke-Api 'GET' '/admin/stats/overview' $user.token
Write-Result 'user blocked from /admin/** (403)' ($r.code -eq 403) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/admin/stats/overview' $merchant.token
Write-Result 'merchant blocked from /admin/** (403)' ($r.code -eq 403) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/merchant/dashboard/overview' $user.token
Write-Result 'user blocked from /merchant/** (403)' ($r.code -eq 403) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/admin/stats/overview' $null
Write-Result 'anonymous blocked from protected api (401)' ($r.code -eq 401) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/merchant/dashboard/overview' $merchant.token
Write-Result 'merchant can call own /merchant/**' ($r.code -eq 1) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/cart/count' $admin.token
Write-Result 'admin blocked from user-only /cart (403)' ($r.code -eq 403) ($r | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "=== 3. Merchant data isolation ===" -ForegroundColor Cyan
$r = Invoke-Api 'GET' '/merchant/product/list?page=1&size=100' $merchant.token
$foreign = @(Get-Records $r | Where-Object { $_.shopId -ne 1 })
Write-Result 'merchant product list only contains own shop (shopId=1)' ($r.code -eq 1 -and $foreign.Count -eq 0) ("foreign=" + $foreign.Count)
$r = Invoke-Api 'POST' '/merchant/product/save' $merchant.token @{ name = 'isolation-probe'; price = 100; stock = 1; status = 1; shopId = 2 }
$after = Invoke-Api 'GET' '/merchant/product/list?page=1&size=200' $merchant.token
$leaked = @(Get-Records $after | Where-Object { $_.name -eq 'isolation-probe' -and $_.shopId -eq 2 })
$mineProbe = @(Get-Records $after | Where-Object { $_.name -eq 'isolation-probe' -and $_.shopId -eq 1 })
Write-Result 'forged shopId=2 in save is forced back to own shop' ($leaked.Count -eq 0 -and $mineProbe.Count -eq 1) ("leaked=" + $leaked.Count + " mine=" + $mineProbe.Count)
$r = Invoke-Api 'GET' '/merchant/product/list?page=1&size=5&shopId=2' $merchant.token
Write-Result 'merchant cannot peek shop 2 products via shopId param' ($r.code -ne 1) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/merchant/order/page?page=1&size=5&shopId=2' $merchant.token
Write-Result 'merchant cannot peek shop 2 orders via shopId param' ($r.code -ne 1) ($r | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "=== 4. Admin acting on behalf of a shop (explicit shopId required) ===" -ForegroundColor Cyan
$r = Invoke-Api 'GET' '/merchant/dashboard/trend' $admin.token
Write-Result 'admin without shopId is asked to pick a shop' ($r.code -eq 0) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/merchant/dashboard/overview?shopId=2' $admin.token
Write-Result 'admin with shopId=2 gets that shop dashboard' ($r.code -eq 1 -and $r.data.shopName) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/merchant/shop/mine' $admin.token
Write-Result 'admin /merchant/shop/mine returns shop list for delegation' ($r.code -eq 1 -and $r.data.admin -eq $true) (($r.data.shops | Measure-Object).Count)

Write-Host ""
Write-Host "=== 5. Onboarding audit loop (apply -> approve -> shop created) ===" -ForegroundColor Cyan
$r = Invoke-Api 'GET' '/merchant/shop/mine' $merchant2.token
Write-Result 'pending merchant has no shop yet (business view)' ($r.code -eq 1 -and $null -eq $r.data.shop) ($r.data | ConvertTo-Json -Compress)
$r = Invoke-Api 'POST' '/merchant/product/save' $merchant2.token @{ name = 'not-approved-item'; price = 100; stock = 1; status = 1 }
Write-Result 'merchant without approved shop cannot add product' ($r.code -eq 0) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/admin/apply/page?status=0&page=1&size=10' $admin.token
$applyId = @(Get-Records $r)[0].id
Write-Result 'admin sees pending onboarding apply' ($r.code -eq 1 -and $applyId) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'POST' ("/admin/apply/" + $applyId + "/audit") $admin.token @{ approved = $true; remark = 'smoke test approved' }
Write-Result 'admin approves onboarding apply' ($r.code -eq 1) ($r | ConvertTo-Json -Compress)
$r2 = Login '13700000002' 'merchant123'
Write-Result 'shop auto-created and merchant becomes active' ($r2.shopId -and $r2.shopAuditStatus -eq 1) ($r2 | ConvertTo-Json -Compress)
$r = Invoke-Api 'GET' '/admin/audit/page?page=1&size=10' $admin.token
$hasLog = @(Get-Records $r | Where-Object { $_.action -eq 'APPLY_APPROVE' }).Count -gt 0
Write-Result 'approval written to audit log (APPLY_APPROVE)' ($r.code -eq 1 -and $hasLog) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'POST' ("/admin/apply/" + $applyId + "/audit") $admin.token @{ approved = $true; remark = 'duplicate' }
Write-Result 'duplicate approval rejected (idempotent guard)' ($r.code -eq 0) ($r | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "=== 6. User order -> pay -> merchant fulfilment -> platform view ===" -ForegroundColor Cyan
$shopList = Invoke-Api 'GET' '/shop/list?page=1&size=10' $user.token
Write-Result 'user shop list returns only open shops' ($shopList.code -eq 1 -and @($shopList.data).Count -gt 0) (@($shopList.data).Count)
$products = Invoke-Api 'GET' '/shop/1/products' $user.token
$productId = @($products.data)[0].id
Write-Result 'shop products available for ordering' ($productId) ("productId=" + $productId)
$order = Invoke-Api 'POST' '/order/create' $user.token @{ shopId = 1; address = 'Beijing Wangjing SOHO T1'; remark = 'smoke test order'; items = @(@{ productId = $productId; count = 1 }) }
Write-Result 'user creates order (conditional stock deduct)' ($order.code -eq 1) ($order | ConvertTo-Json -Compress -Depth 4)
$oid = $order.data.id
$pay = Invoke-Api 'POST' ("/order/" + $oid + "/pay") $user.token
Write-Result 'user pays order -> status 2 (waiting accept)' ($pay.code -eq 1 -and $pay.data.status -eq 2) ($pay | ConvertTo-Json -Compress)
$mOrders = Invoke-Api 'GET' '/merchant/order/page?status=2&page=1&size=20' $merchant.token
$mine = @(Get-Records $mOrders | Where-Object { $_.id -eq $oid }).Count
Write-Result 'owning merchant sees the new order' ($mine -eq 1) ($mOrders | ConvertTo-Json -Compress -Depth 3)
$other = Invoke-Api 'POST' ("/merchant/order/" + $oid + "/accept") $merchant2.token
Write-Result 'other merchant cannot accept my order' ($other.code -eq 0) ($other | ConvertTo-Json -Compress)
$acc = Invoke-Api 'POST' ("/merchant/order/" + $oid + "/accept") $merchant.token
Write-Result 'owning merchant accepts -> status 3' ($acc.code -eq 1) ($acc | ConvertTo-Json -Compress)
$del = Invoke-Api 'POST' ("/merchant/order/" + $oid + "/deliver") $merchant.token
Write-Result 'merchant delivers -> status 4' ($del.code -eq 1) ($del | ConvertTo-Json -Compress)
$fin = Invoke-Api 'POST' ("/merchant/order/" + $oid + "/finish") $merchant.token
Write-Result 'merchant finishes -> status 5' ($fin.code -eq 1) ($fin | ConvertTo-Json -Compress)
$aOrders = Invoke-Api 'GET' '/admin/order/page?page=1&size=5' $admin.token
Write-Result 'platform sees all orders' ($aOrders.code -eq 1 -and $aOrders.data.total -ge 1) ("total=" + $aOrders.data.total)

Write-Host ""
Write-Host "=== 7. Dashboards and stats (platform vs single shop) ===" -ForegroundColor Cyan
$ov = Invoke-Api 'GET' '/admin/stats/overview' $admin.token
Write-Result 'platform overview returns users/merchants/shops/pending' ($ov.code -eq 1 -and $null -ne $ov.data.totalUsers -and $null -ne $ov.data.totalMerchants) ($ov.data | ConvertTo-Json -Compress)
$m1 = Invoke-Api 'GET' '/merchant/dashboard/overview' $merchant.token
$m2 = Invoke-Api 'GET' '/merchant/dashboard/overview' $merchant2.token
Write-Result 'shop dashboards differ (data scope isolation)' ($m1.code -eq 1 -and $m1.data.totalGmv -ne $m2.data.totalGmv) ("shop1=" + $m1.data.totalGmv + " shop2=" + $m2.data.totalGmv)
$trend = Invoke-Api 'GET' '/merchant/dashboard/trend?days=7' $merchant.token
Write-Result 'merchant trend returns 7 continuous days' ($trend.code -eq 1 -and @($trend.data).Count -eq 7) (@($trend.data).Count)
$rank = Invoke-Api 'GET' '/admin/stats/shopRank' $admin.token
Write-Result 'platform shop GMV rank available' ($rank.code -eq 1) ($rank.data | ConvertTo-Json -Compress)
$dist = Invoke-Api 'GET' '/admin/stats/shopAudit' $admin.token
Write-Result 'shop audit distribution available (4 buckets)' ($dist.code -eq 1 -and @($dist.data).Count -eq 4) ($dist.data | ConvertTo-Json -Compress)
$roleDist = Invoke-Api 'GET' '/admin/stats/roleDistribution' $admin.token
Write-Result 'role distribution available (3 roles)' ($roleDist.code -eq 1 -and @($roleDist.data).Count -eq 3) ($roleDist.data | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "=== 8. Account governance (ban / unban, admin protected) ===" -ForegroundColor Cyan
$r = Invoke-Api 'POST' '/admin/user/9/status?status=0' $admin.token
Write-Result 'platform bans a normal user' ($r.code -eq 1) ($r | ConvertTo-Json -Compress)
$banned = Login '13900000008' '123456'
Write-Result 'banned account cannot login' ($null -eq $banned) 'login should fail'
$r = Invoke-Api 'POST' '/admin/user/9/status?status=1' $admin.token
Write-Result 'platform unbans the user' ($r.code -eq 1) ($r | ConvertTo-Json -Compress)
$r = Invoke-Api 'POST' '/admin/user/1/status?status=0' $admin.token
Write-Result 'admin account is protected from ban' ($r.code -eq 0) ($r | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "============================ SUMMARY ============================" -ForegroundColor Yellow
if ($script:fail -eq 0) {
    Write-Host ("  PASS: " + $script:pass + "    FAIL: " + $script:fail) -ForegroundColor Green
} else {
    Write-Host ("  PASS: " + $script:pass + "    FAIL: " + $script:fail) -ForegroundColor Red
    exit 1
}
