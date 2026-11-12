﻿# 阶段 5 自测：任务发布、接单大厅、任务详情
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/campusquest'
$failed = 0

function Test-Case($name, $condition, $detail) {
    if ($condition) { Write-Output "PASS  $name" }
    else { Write-Output "FAIL  $name  => $detail"; $script:failed++ }
}
function PostJson($path, $json, $session) {
    $a = @{ UseBasicParsing = $true; Uri = "$base$path"; Method = 'POST'; ContentType = 'application/json;charset=UTF-8'; Body = [Text.Encoding]::UTF8.GetBytes($json); TimeoutSec = 15 }
    if ($session) { $a.WebSession = $session }
    return Invoke-WebRequest @a
}
function GetJson($path, $session) {
    $a = @{ UseBasicParsing = $true; Uri = "$base$path"; Method = 'GET'; TimeoutSec = 15 }
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
function NewStudent($nickname) {
    $phone = '137' + (Get-Random -Minimum 10000000 -Maximum 99999999)
    $none = $null
    $r = TryCall { PostJson '/api/auth/register' ("{`"phone`":`"$phone`",`"nickname`":`"$nickname`",`"password`":`"123456`"}") $none }
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $null = PostJson '/api/auth/login' ("{`"phone`":`"$phone`",`"password`":`"123456`"}") $s
    return @{ Phone = $phone; Session = $s; UserId = ($r.Body | ConvertFrom-Json).data.userId }
}

$publisher = NewStudent '阶段五发布者'
$other = NewStudent '阶段五接单人'

$mark = '阶段五' + (Get-Random -Minimum 1000 -Maximum 9999)
$taskJson = "{`"category`":`"EXPRESS`",`"title`":`"$mark 取快递到 3 号楼`",`"description`":`"东门菜鸟驿站取件，送到 3 号楼 402，取件码 1234`",`"points`":20,`"locationDesc`":`"东门菜鸟驿站 → 3 号楼 402`",`"timeDesc`":`"今天 17:30 前`"}"
$p1 = TryCall { PostJson '/api/tasks' $taskJson $publisher.Session }
$p1Json = $p1.Body | ConvertFrom-Json
$taskId = $p1Json.data.taskId
Test-Case '发布任务成功返回 taskId 与 taskNo' ($p1Json.code -eq 0 -and $taskId -gt 0 -and $p1Json.data.taskNo -match '^CQ-\d{8}-\d{3}$') $p1.Body
Test-Case '发布时扣除预算积分' (($p1Json.data.points -eq 20) -and ($p1Json.data.balance -eq 80)) $p1.Body

$balance = Sql "SELECT points FROM users WHERE id=$($publisher.UserId);"
$logCount = Sql "SELECT COUNT(*) FROM point_logs WHERE user_id=$($publisher.UserId) AND type='PUBLISH' AND related_task_id=$taskId AND change_amount=-20;"
Test-Case '数据库余额与发布流水正确' ($balance -eq '80' -and $logCount -eq '1') "balance=$balance logs=$logCount"

$noPoints = TryCall { PostJson '/api/tasks' ("{`"category`":`"ERRAND`",`"title`":`"$mark 积分不足测试`",`"description`":`"测试`",`"points`":150,`"locationDesc`":`"某地`",`"timeDesc`":`"今天`"}") $publisher.Session }
$npJson = $noPoints.Body | ConvertFrom-Json
Test-Case '积分不足返回 3001 且提示当前余额' ($npJson.code -eq 3001 -and $npJson.message -match '当前余额 80') $noPoints.Body

$emptyTitle = TryCall { PostJson '/api/tasks' ("{`"category`":`"EXPRESS`",`"title`":`"  `",`"description`":`"测试`",`"points`":10,`"locationDesc`":`"某地`",`"timeDesc`":`"今天`"}") $publisher.Session }
Test-Case '标题为空返回 1001' (($emptyTitle.Body | ConvertFrom-Json).code -eq 1001) $emptyTitle.Body
$badPoints = TryCall { PostJson '/api/tasks' ("{`"category`":`"EXPRESS`",`"title`":`"$mark 越界`",`"description`":`"测试`",`"points`":1000,`"locationDesc`":`"某地`",`"timeDesc`":`"今天`"}") $publisher.Session }
Test-Case '积分超过 999 返回 1001' (($badPoints.Body | ConvertFrom-Json).code -eq 1001) $badPoints.Body

$low = NewStudent '阶段五低信用'
$null = Sql "UPDATE users SET credit=89 WHERE id=$($low.UserId);"
$lowPublish = TryCall { PostJson '/api/tasks' ("{`"category`":`"EXPRESS`",`"title`":`"$mark 低信用发布`",`"description`":`"测试`",`"points`":10,`"locationDesc`":`"某地`",`"timeDesc`":`"今天`"}") $low.Session }
$lowJson = $lowPublish.Body | ConvertFrom-Json
Test-Case '信用 89 分不能发布返回 3002' ($lowJson.code -eq 3002 -and $lowJson.message -match '90') $lowPublish.Body

$p2Json = "{`"category`":`"MOVING`",`"title`":`"$mark 搬运床垫`",`"description`":`"从 1 号楼搬到 5 号楼 402`",`"points`":40,`"locationDesc`":`"1 号楼 → 5 号楼 402`",`"timeDesc`":`"周六上午`"}"
$p2 = TryCall { PostJson '/api/tasks' $p2Json $publisher.Session }
$p2TaskId = ($p2.Body | ConvertFrom-Json).data.taskId
Test-Case '发布第二条任务成功' (($p2.Body | ConvertFrom-Json).code -eq 0 -and $p2TaskId -gt 0) $p2.Body

$list = TryCall { GetJson '/api/tasks?size=50' $other.Session }
$listJson = $list.Body | ConvertFrom-Json
$found = @($listJson.data.list | Where-Object { $_.id -eq $taskId })
Test-Case '大厅列表包含新任务且只有待接单' ($listJson.code -eq 0 -and $found.Count -eq 1 -and $found[0].status -eq 'WAITING') $list.Body
Test-Case '大厅卡片含发布者昵称、信用、申请人数' ($found[0].publisherNickname -eq '阶段五发布者' -and $found[0].publisherCredit -eq 100 -and $found[0].applicationCount -eq 0) $list.Body

$cat = TryCall { GetJson '/api/tasks?category=EXPRESS&size=50' $other.Session }
$catJson = $cat.Body | ConvertFrom-Json
$allExpress = @($catJson.data.list | Where-Object { $_.category -ne 'EXPRESS' }).Count -eq 0
$hasTask = @($catJson.data.list | Where-Object { $_.id -eq $taskId }).Count -eq 1
Test-Case '分类筛选只返回对应分类' ($allExpress -and $hasTask) $cat.Body

$kw = TryCall { GetJson ("/api/tasks?keyword=" + [uri]::EscapeDataString($mark) + "&size=50") $other.Session }
$kwJson = $kw.Body | ConvertFrom-Json
Test-Case '关键词搜索命中标题' ($kwJson.data.total -eq 2) $kw.Body

$sorted = TryCall { GetJson '/api/tasks?sort=points_desc&size=50' $other.Session }
$sortedJson = $sorted.Body | ConvertFrom-Json
$ys = @($sortedJson.data.list | ForEach-Object { $_.points })
$isDesc = $true
for ($i = 1; $i -lt $ys.Count; $i++) { if ($ys[$i] -gt $ys[$i - 1]) { $isDesc = $false } }
Test-Case '按积分从高到低排序' ($isDesc -and $ys.Count -ge 2) ($ys -join ',')

$paged = TryCall { GetJson '/api/tasks?page=1&size=1' $other.Session }
$pagedJson = $paged.Body | ConvertFrom-Json
Test-Case '分页返回 page/size/total/pages/list' ($pagedJson.data.page -eq 1 -and $pagedJson.data.size -eq 1 -and $pagedJson.data.total -ge 2 -and $pagedJson.data.pages -ge 2 -and $pagedJson.data.list.Count -eq 1) $paged.Body

$null = Sql "UPDATE tasks SET status='IN_PROGRESS', acceptor_id=$($other.UserId), accepted_at=NOW() WHERE id=$p2TaskId;"
$waitingOnly = TryCall { GetJson '/api/tasks?size=50' $other.Session }
$waitingIds = @(($waitingOnly.Body | ConvertFrom-Json).data.list | ForEach-Object { $_.id })
Test-Case '默认只看待接单，不含进行中任务' ($waitingIds -notcontains $p2TaskId) ($waitingIds -join ',')

$allStatus = TryCall { GetJson '/api/tasks?onlyWaiting=0&size=50' $other.Session }
$allIds = @(($allStatus.Body | ConvertFrom-Json).data.list | ForEach-Object { $_.id })
Test-Case '关闭只看待接单后包含进行中任务' ($allIds -contains $p2TaskId) ($allIds -join ',')

$detail = TryCall { GetJson "/api/tasks/$taskId" $other.Session }
$dJson = $detail.Body | ConvertFrom-Json
Test-Case '任务详情字段完整且中文正常' ($dJson.code -eq 0 -and $dJson.data.title -eq "$mark 取快递到 3 号楼" -and $dJson.data.description -match '取件码 1234' -and $dJson.data.publisherNickname -eq '阶段五发布者' -and $dJson.data.points -eq 20) $detail.Body
Test-Case '非发布者详情显示可申请' ($dJson.data.canApply -eq $true -and $dJson.data.canCancel -eq $false) $detail.Body

$missing = TryCall { GetJson '/api/tasks/99999999' $other.Session }
Test-Case '不存在的任务返回 404/1004' ($missing.Status -eq 404 -and ($missing.Body | ConvertFrom-Json).code -eq 1004) $missing.Body

$html = TryCall { Invoke-WebRequest -UseBasicParsing -Uri "$base/lobby.html" -TimeoutSec 10 }
Test-Case '大厅页面可访问' ($html.Status -eq 200 -and $html.Body -match '接单大厅') $html.Status
$publishHtml = TryCall { Invoke-WebRequest -UseBasicParsing -Uri "$base/publish.html" -TimeoutSec 10 }
Test-Case '发布页可访问' ($publishHtml.Status -eq 200) $publishHtml.Status
$detailHtml = TryCall { Invoke-WebRequest -UseBasicParsing -Uri "$base/task-detail.html" -TimeoutSec 10 }
Test-Case '详情页可访问' ($detailHtml.Status -eq 200) $detailHtml.Status

if ($failed -gt 0) { Write-Output "阶段5测试失败：$failed 项"; exit 1 }
Write-Output '阶段5测试全部通过'
