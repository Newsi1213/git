﻿# 阶段 12 自测：页面完整性、断网静态资源、信用拦截、账实一致、自动确认幂等、组件页
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/campusquest'
$root = 'E:\MyApp\server\src\main\webapp'
$failed = 0

function Test-Case($name, $condition, $detail) {
    if ($condition) { Write-Output "PASS  $name" }
    else { Write-Output "FAIL  $name  => $detail"; $script:failed++ }
}
function PostJson($path, $json, $session) {
    $a = @{ UseBasicParsing = $true; Uri = "$base$path"; Method = 'POST'; ContentType = 'application/json;charset=UTF-8'; Body = [Text.Encoding]::UTF8.GetBytes($json); TimeoutSec = 20 }
    if ($session) { $a.WebSession = $session }
    return Invoke-WebRequest @a
}
function GetJson($path, $session) {
    $a = @{ UseBasicParsing = $true; Uri = "$base$path"; Method = 'GET'; TimeoutSec = 20 }
    if ($session) { $a.WebSession = $session }
    return Invoke-WebRequest @a
}
function TryCall($scriptBlock) {
    try { $r = & $scriptBlock; return @{ Status = [int]$r.StatusCode; Body = $r.Content } }
    catch {
        $rp = $_.Exception.Response
        if ($rp) { $sr = New-Object IO.StreamReader($rp.GetResponseStream()); return @{ Status = [int]$rp.StatusCode; Body = $sr.ReadToEnd() } }
        throw
    }
}
function Sql($sql) {
    $env:MYSQL_PWD = 'root'
    return (mysql -u root -N -B --default-character-set=utf8mb4 -e "USE campus_quest; $sql" 2>$null)
}
function SqlInt($sql) { $v = Sql $sql; if ($null -eq $v -or $v -eq '') { return -1 }; return [int]$v }
function NewStudent($nickname) {
    $phone = '137' + (Get-Random -Minimum 10000000 -Maximum 99999999)
    $none = $null
    $r = TryCall { PostJson '/api/auth/register' ("{`"phone`":`"$phone`",`"nickname`":`"$nickname`",`"password`":`"123456`"}") $none }
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $null = PostJson '/api/auth/login' ("{`"phone`":`"$phone`",`"password`":`"123456`"}") $s
    return @{ Phone = $phone; Session = $s; UserId = ($r.Body | ConvertFrom-Json).data.userId; Nickname = $nickname }
}

# ---------- 11 个页面可访问 ----------
$pages = @('login.html', 'dashboard.html', 'lobby.html', 'publish.html', 'my-tasks.html',
    'task-detail.html', 'messages.html', 'profile.html', 'leaderboard.html',
    'admin/appeals.html', 'admin/tasks.html')
foreach ($p in $pages) {
    $r = Invoke-WebRequest -UseBasicParsing -Uri "$base/$p" -TimeoutSec 20
    Test-Case "页面可访问：$p" ([int]$r.StatusCode -eq 200 -and $r.Content.Length -gt 500) "status=$($r.StatusCode) len=$($r.Content.Length)"
}
$r = Invoke-WebRequest -UseBasicParsing -Uri "$base/components.html" -TimeoutSec 20
Test-Case '组件自查页可访问' ([int]$r.StatusCode -eq 200) "status=$($r.StatusCode)"

# ---------- 断网：本地资源齐全，页面不外链 ----------
$libs = @('assets\lib\vue.min.js', 'assets\lib\element-ui\index.js',
    'assets\lib\element-ui\theme-chalk\index.css', 'assets\lib\axios.min.js')
foreach ($lib in $libs) {
    Test-Case "本地依赖存在：$lib" (Test-Path (Join-Path $root $lib)) 'missing'
}
$scanFiles = @()
$scanFiles += Get-ChildItem $root -Filter *.html -File
$scanFiles += Get-ChildItem (Join-Path $root 'assets\js') -Filter *.js -File -Recurse | Where-Object { $_.FullName -notlike '*\lib\*' }
$scanFiles += Get-ChildItem (Join-Path $root 'assets\css') -Filter *.css -File
$external = @()
foreach ($f in $scanFiles) {
    $text = Get-Content -Raw -Encoding UTF8 $f.FullName
    if ($text -match 'https?://') { $external += $f.Name }
}
Test-Case '页面与脚本无外网地址引用' ($external.Count -eq 0) ("files=" + ($external -join ','))

# ---------- 组件页要素 ----------
$comp = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'components.html')
Test-Case '组件页含任务卡片三态' ($comp -match 'task-card' -and $comp -match "status: 'WAITING'" -and $comp -match "status: 'IN_PROGRESS'" -and $comp -match "status: 'COMPLETED'") 'task-card'
Test-Case '组件页含五种状态标签' (([regex]::Matches($comp, '<status-tag ')).Count -ge 6) 'status-tag'
Test-Case '组件页含评价弹窗' ($comp -like '*review-dialog*') 'review'
Test-Case '组件页含分页、空状态、错误条、按钮层级' ($comp -like '*el-pagination*' -and $comp -like '*cq-empty*' -and $comp -like '*error-bar*' -and $comp -like '*el-button*') 'missing element'

# ---------- 信用门槛（B 89 / C 84） ----------
$none = $null
$sB = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = PostJson '/api/auth/login' '{"phone":"13800000002","password":"123456"}' $sB
$sC = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = PostJson '/api/auth/login' '{"phone":"13800000003","password":"123456"}' $sC
$pubTry = (PostJson '/api/tasks' '{"category":"EXPRESS","title":"演示信用拦截","description":"测试","points":10,"locationDesc":"A","timeDesc":"B"}' $sB).Content | ConvertFrom-Json
Test-Case '信用 89 发布被拦截并提示低于 90' ($pubTry.code -ne 0 -and $pubTry.message -like '*低于 90*') "code=$($pubTry.code) msg=$($pubTry.message)"
Test-Case '信用 89 发布失败不产生任务' ((SqlInt "SELECT COUNT(*) FROM tasks WHERE title='演示信用拦截';") -eq 0) 'task created'
$pubDemo = NewStudent '阶段十二信用发布者'
$demoTask = ((PostJson '/api/tasks' ("{`"category`":`"ERRAND`",`"title`":`"阶段十二信用门槛`",`"description`":`"信用门槛测试`",`"points`":10,`"locationDesc`":`"A`",`"timeDesc`":`"B`"}") $pubDemo.Session).Content | ConvertFrom-Json).data.taskId
$applyC = (PostJson "/api/tasks/$demoTask/apply" '{}' $sC).Content | ConvertFrom-Json
Test-Case '信用 84 申请被拦截并提示低于 85' ($applyC.code -ne 0 -and $applyC.message -like '*低于 85*') "code=$($applyC.code) msg=$($applyC.message)"
$lobbyB = (GetJson '/api/tasks?onlyWaiting=1&page=1&size=50' $sB).Content | ConvertFrom-Json
Test-Case '信用 89 账号仍可浏览大厅' ($lobbyB.code -eq 0 -and [int]$lobbyB.data.total -gt 0) "total=$($lobbyB.data.total)"
$applyB = TryCall { PostJson "/api/tasks/$demoTask/apply" '{}' $sB }
$applyBBody = $applyB.Body | ConvertFrom-Json
Test-Case '信用 89 账号可以申请接单' ($applyB.Status -eq 200 -and $applyBBody.code -eq 0) "code=$($applyBBody.code) msg=$($applyBBody.message)"

# ---------- 分页与空搜索 ----------
$p1 = GetJson '/api/tasks?page=1&size=1&onlyWaiting=0' $sB
$p2 = GetJson '/api/tasks?page=2&size=1&onlyWaiting=0' $sB
$p1j = $p1.Content | ConvertFrom-Json
$p2j = $p2.Content | ConvertFrom-Json
Test-Case '大厅分页第 1、2 页内容不重复' ([int]$p1j.data.list[0].id -ne [int]$p2j.data.list[0].id -and [int]$p1j.data.total -ge 2) "p1=$($p1j.data.list[0].id) p2=$($p2j.data.list[0].id)"
$empty = (GetJson '/api/tasks?keyword=zzzz_no_such_task_zzzz&page=1&size=10' $sB).Content | ConvertFrom-Json
Test-Case '搜索无结果返回空列表' ($empty.code -eq 0 -and [int]$empty.data.total -eq 0 -and $empty.data.list.Count -eq 0) "total=$($empty.data.total)"

# ---------- 账实一致与信用边界 ----------
Test-Case '积分余额等于流水累加' ((SqlInt "SELECT COUNT(*) FROM users u WHERE u.points <> COALESCE((SELECT SUM(change_amount) FROM point_logs p WHERE p.user_id=u.id),0);") -eq 0) 'mismatch'
Test-Case '积分余额等于最后一条流水余额' ((SqlInt "SELECT COUNT(*) FROM users u WHERE u.points <> COALESCE((SELECT p.balance_after FROM point_logs p WHERE p.user_id=u.id ORDER BY p.id DESC LIMIT 1),0);") -eq 0) 'mismatch'
Test-Case '不存在负积分' ((SqlInt "SELECT COUNT(*) FROM users WHERE points<0;") -eq 0) 'negative'
Test-Case '信用分全部在 0–100' ((SqlInt "SELECT COUNT(*) FROM users WHERE credit<0 OR credit>100;") -eq 0) 'out of range'
Test-Case '积分流水不可为空的类型齐全' ((SqlInt "SELECT COUNT(DISTINCT type) FROM point_logs;") -ge 5) 'types'

# ---------- 自动确认幂等 ----------
$pub = NewStudent '阶段十二发布者'
$acc = NewStudent '阶段十二接单人'
$t = ((PostJson '/api/tasks' ("{`"category`":`"EXPRESS`",`"title`":`"阶段十二自动确认`",`"description`":`"自动确认幂等测试`",`"points`":8,`"locationDesc`":`"A`",`"timeDesc`":`"B`"}") $pub.Session).Content | ConvertFrom-Json).data.taskId
$ap = ((PostJson "/api/tasks/$t/apply" '{}' $acc.Session).Content | ConvertFrom-Json).data.applicationId
$null = PostJson "/api/tasks/$t/confirm" ("{`"applicationId`":$ap}") $pub.Session
$null = PostJson "/api/tasks/$t/deliver" '{}' $acc.Session
Test-Case '送达后进入待确认完成' ((Sql "SELECT status FROM tasks WHERE id=$t;") -eq 'PENDING_CONFIRM') 'status'
$null = Sql "UPDATE tasks SET confirm_deadline = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=$t;"
Start-Sleep -Seconds 4
$null = GetJson '/api/tasks?onlyWaiting=1&page=1&size=5' $acc.Session
Test-Case '到期访问后自动确认完成' ((Sql "SELECT status FROM tasks WHERE id=$t;") -eq 'COMPLETED') 'status'
$incomeBefore = SqlInt "SELECT COUNT(*) FROM point_logs WHERE type='INCOME' AND related_task_id=$t;"
$pointsAfter = SqlInt "SELECT points FROM users WHERE id=$($acc.UserId);"
Start-Sleep -Seconds 4
$null = GetJson '/api/tasks?onlyWaiting=1&page=1&size=5' $acc.Session
$null = GetJson "/api/tasks/$t" $pub.Session
Test-Case '自动确认只结算一次' ((SqlInt "SELECT COUNT(*) FROM point_logs WHERE type='INCOME' AND related_task_id=$t;") -eq $incomeBefore -and $incomeBefore -eq 1) "count=$incomeBefore"
Test-Case '自动确认后接单者积分只加一次' ((SqlInt "SELECT points FROM users WHERE id=$($acc.UserId);") -eq ($pointsAfter) -and $pointsAfter -eq 108) "points=$pointsAfter"
$notifyAuto = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($acc.UserId) AND type='SETTLEMENT' AND content LIKE '%自动确认%';"
Test-Case '自动确认通知文案标明系统自动确认' ($notifyAuto -ge 1) "count=$notifyAuto"

Write-Output ''
if ($failed -eq 0) { Write-Output '阶段 12 联调测试全部通过' } else { Write-Output "阶段 12 联调测试失败 $failed 项"; exit 1 }
