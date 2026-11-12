﻿# 阶段 6 自测：申请接单、确认、送达、取消、放弃、我的任务
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
function SqlInt($sql) {
    $v = Sql $sql
    if ($null -eq $v -or $v -eq '') { return -1 }
    return [int]$v
}
function NewStudent($nickname) {
    $phone = '137' + (Get-Random -Minimum 10000000 -Maximum 99999999)
    $none = $null
    $r = TryCall { PostJson '/api/auth/register' ("{`"phone`":`"$phone`",`"nickname`":`"$nickname`",`"password`":`"123456`"}") $none }
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $null = PostJson '/api/auth/login' ("{`"phone`":`"$phone`",`"password`":`"123456`"}") $s
    return @{ Phone = $phone; Session = $s; UserId = ($r.Body | ConvertFrom-Json).data.userId }
}
function PublishTask($session, $title, $points, $category) {
    $json = "{`"category`":`"$category`",`"title`":`"$title`",`"description`":`"阶段六测试任务描述`",`"points`":$points,`"locationDesc`":`"宿舍区 → 教学区`",`"timeDesc`":`"今天 18:00 前`"}"
    $r = TryCall { PostJson '/api/tasks' $json $session }
    $j = $r.Body | ConvertFrom-Json
    return @{ Id = $j.data.taskId; Code = $j.code; Body = $r.Body }
}

$publisher = NewStudent '阶段六发布者'
$acceptor = NewStudent '阶段六接单人'
$third = NewStudent '阶段六第三人'
$low = NewStudent '阶段六低信用'
$null = Sql "UPDATE users SET credit=84 WHERE id=$($low.UserId);"

$mark = '阶段六' + (Get-Random -Minimum 1000 -Maximum 9999)
$t1 = PublishTask $publisher.Session "$mark 取快递到 3 号楼" 20 'EXPRESS'
Test-Case '发布者发布任务成功' ($t1.Code -eq 0 -and $t1.Id -gt 0) $t1.Body

$applyB = TryCall { PostJson "/api/tasks/$($t1.Id)/apply" '{}' $acceptor.Session }
$applyBJson = $applyB.Body | ConvertFrom-Json
$appBId = $applyBJson.data.applicationId
Test-Case '接单人申请成功并返回 applicationId' ($applyBJson.code -eq 0 -and $appBId -gt 0 -and $applyBJson.data.applicationCount -eq 1) $applyB.Body

$selfApply = TryCall { PostJson "/api/tasks/$($t1.Id)/apply" '{}' $publisher.Session }
Test-Case '不能申请自己发布的任务返回 3005' (($selfApply.Body | ConvertFrom-Json).code -eq 3005) $selfApply.Body

$dupApply = TryCall { PostJson "/api/tasks/$($t1.Id)/apply" '{}' $acceptor.Session }
Test-Case '重复申请返回 3006' (($dupApply.Body | ConvertFrom-Json).code -eq 3006) $dupApply.Body

$lowApply = TryCall { PostJson "/api/tasks/$($t1.Id)/apply" '{}' $low.Session }
$lowJson = $lowApply.Body | ConvertFrom-Json
Test-Case '信用 84 分不能接单返回 3003' ($lowJson.code -eq 3003 -and $lowJson.message -match '85') $lowApply.Body

$applyC = TryCall { PostJson "/api/tasks/$($t1.Id)/apply" '{}' $third.Session }
$appCId = ($applyC.Body | ConvertFrom-Json).data.applicationId
Test-Case '第三人申请成功' (($applyC.Body | ConvertFrom-Json).code -eq 0 -and $appCId -gt 0) $applyC.Body

$listApps = TryCall { GetJson "/api/tasks/$($t1.Id)/applications" $publisher.Session }
$listAppsJson = $listApps.Body | ConvertFrom-Json
Test-Case '发布者可查看申请列表且两人待确认' ($listAppsJson.code -eq 0 -and $listAppsJson.data.Count -eq 2 -and @($listAppsJson.data | Where-Object { $_.status -eq 'PENDING' }).Count -eq 2) $listApps.Body
Test-Case '申请列表含申请人昵称与信用分' ($listAppsJson.data[0].applicantNickname -ne '' -and $listAppsJson.data[0].applicantCredit -ge 0) $listApps.Body

$forbiddenApps = TryCall { GetJson "/api/tasks/$($t1.Id)/applications" $acceptor.Session }
Test-Case '非发布者查看申请列表返回 1003' (($forbiddenApps.Body | ConvertFrom-Json).code -eq 1003) $forbiddenApps.Body

$confirm = TryCall { PostJson "/api/tasks/$($t1.Id)/confirm" ("{`"applicationId`":$appBId}") $publisher.Session }
$confirmJson = $confirm.Body | ConvertFrom-Json
Test-Case '发布者确认接单成功任务进入进行中' ($confirmJson.code -eq 0 -and $confirmJson.data.status -eq 'IN_PROGRESS' -and $confirmJson.data.acceptorId -eq $acceptor.UserId) $confirm.Body

$statusB = Sql "SELECT status FROM applications WHERE id=$appBId;"
$statusC = Sql "SELECT status FROM applications WHERE id=$appCId;"
Test-Case '被选中申请变 SELECTED、其他变 REJECTED' ($statusB -eq 'SELECTED' -and $statusC -eq 'REJECTED') "B=$statusB C=$statusC"

$conv = SqlInt "SELECT COUNT(*) FROM conversations WHERE task_id=$($t1.Id) AND publisher_id=$($publisher.UserId) AND acceptor_id=$($acceptor.UserId);"
Test-Case '确认后自动创建会话记录' ($conv -eq 1) "conv=$conv"

$notifyA = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($publisher.UserId) AND type='APPLICATION_RECEIVED' AND related_task_id=$($t1.Id);"
$notifyB = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($acceptor.UserId) AND type='APPLICATION_CONFIRMED' AND related_task_id=$($t1.Id);"
Test-Case '收到申请与被确认通知已写入' ($notifyA -eq 2 -and $notifyB -eq 1) "A=$notifyA B=$notifyB"

$applyC2 = TryCall { PostJson "/api/tasks/$($t1.Id)/apply" '{}' $third.Session }
Test-Case '已确认后申请返回 3007' (($applyC2.Body | ConvertFrom-Json).code -eq 3007) $applyC2.Body

$minePub = TryCall { GetJson '/api/tasks/mine?type=published&size=50' $publisher.Session }
$minePubJson = $minePub.Body | ConvertFrom-Json
$pubRow = @($minePubJson.data.list | Where-Object { $_.id -eq $t1.Id })
Test-Case '我发布的接口包含该任务且状态正确' ($minePubJson.code -eq 0 -and $pubRow.Count -eq 1 -and $pubRow[0].status -eq 'IN_PROGRESS' -and $pubRow[0].applicationCount -eq 2 -and $pubRow[0].counterpartNickname -eq '阶段六接单人') $minePub.Body

$mineAcc = TryCall { GetJson '/api/tasks/mine/accepted?size=50' $acceptor.Session }
$mineAccJson = $mineAcc.Body | ConvertFrom-Json
$accRow = @($mineAccJson.data.list | Where-Object { $_.id -eq $t1.Id })
Test-Case '我接的接口包含该任务' ($mineAccJson.code -eq 0 -and $accRow.Count -eq 1) $mineAcc.Body

$detail = TryCall { GetJson "/api/tasks/$($t1.Id)" $acceptor.Session }
$detailJson = $detail.Body | ConvertFrom-Json
Test-Case '详情页人数为真实申请数且接单者可送达' ($detailJson.data.applicationCount -eq 2 -and $detailJson.data.canDeliver -eq $true -and $detailJson.data.canApply -eq $false) $detail.Body

$deliver = TryCall { PostJson "/api/tasks/$($t1.Id)/deliver" '{}' $acceptor.Session }
$deliverJson = $deliver.Body | ConvertFrom-Json
Test-Case '接单者标记已送达进入待确认' ($deliverJson.code -eq 0 -and $deliverJson.data.status -eq 'PENDING_CONFIRM' -and $deliverJson.data.confirmDeadline -ne $null) $deliver.Body

$deliverAgain = TryCall { PostJson "/api/tasks/$($t1.Id)/deliver" '{}' $acceptor.Session }
Test-Case '重复标记送达返回 3004' (($deliverAgain.Body | ConvertFrom-Json).code -eq 3004) $deliverAgain.Body

$detail2 = (TryCall { GetJson "/api/tasks/$($t1.Id)" $publisher.Session }).Body | ConvertFrom-Json
Test-Case '待确认详情含剩余秒数（约 24 小时）' ($detail2.data.remainingSeconds -gt 86000 -and $detail2.data.remainingSeconds -le 86400) ("remaining=" + $detail2.data.remainingSeconds)

# ---- 放弃流程：30 分钟内不扣分 ----
$t2 = PublishTask $publisher.Session "$mark 代取外卖" 10 'TAKEOUT'
$null = PostJson "/api/tasks/$($t2.Id)/apply" '{}' $acceptor.Session
$app2 = SqlInt "SELECT id FROM applications WHERE task_id=$($t2.Id) AND applicant_id=$($acceptor.UserId);"
$null = PostJson "/api/tasks/$($t2.Id)/confirm" ("{`"applicationId`":$app2}") $publisher.Session

$abandon1 = TryCall { PostJson "/api/tasks/$($t2.Id)/abandon" '{}' $acceptor.Session }
$abandon1Json = $abandon1.Body | ConvertFrom-Json
Test-Case '30 分钟内放弃回到待接单且不扣分' ($abandon1Json.code -eq 0 -and $abandon1Json.data.status -eq 'WAITING' -and $abandon1Json.data.within30 -eq $true -and $abandon1Json.data.penalty -eq 0 -and $abandon1Json.data.credit -eq 100) $abandon1.Body

$penaltyLogs1 = SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($acceptor.UserId) AND reason='ABANDON' AND related_task_id=$($t2.Id);"
$accRow2 = Sql "SELECT CONCAT(status, ':', penalty_applied) FROM applications WHERE id=$app2;"
Test-Case '放弃后申请记为已放弃且无扣分流水' ($penaltyLogs1 -eq 0 -and $accRow2 -eq 'ABANDONED:0') "logs=$penaltyLogs1 app=$accRow2"

$lobby = (TryCall { GetJson '/api/tasks?size=100' $third.Session }).Body | ConvertFrom-Json
$lobbyIds = @($lobby.data.list | ForEach-Object { $_.id })
Test-Case '放弃后任务重新出现在大厅' ($lobbyIds -contains $t2.Id) ($lobbyIds -join ',')

# ---- 超过 30 分钟放弃：扣 2 分 ----
$reapply = TryCall { PostJson "/api/tasks/$($t2.Id)/apply" '{}' $acceptor.Session }
Test-Case '放弃后可重新申请（复用申请记录）' (($reapply.Body | ConvertFrom-Json).code -eq 0 -and ($reapply.Body | ConvertFrom-Json).data.applicationId -eq $app2) $reapply.Body
$null = PostJson "/api/tasks/$($t2.Id)/confirm" ("{`"applicationId`":$app2}") $publisher.Session
$null = Sql "UPDATE tasks SET accepted_at = DATE_SUB(NOW(), INTERVAL 31 MINUTE) WHERE id=$($t2.Id);"

$abandon2 = TryCall { PostJson "/api/tasks/$($t2.Id)/abandon" '{}' $acceptor.Session }
$abandon2Json = $abandon2.Body | ConvertFrom-Json
Test-Case '超 30 分钟放弃扣 2 分信用' ($abandon2Json.code -eq 0 -and $abandon2Json.data.penalty -eq 2 -and $abandon2Json.data.credit -eq 98 -and $abandon2Json.data.within30 -eq $false) $abandon2.Body

$penaltyLogs2 = SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($acceptor.UserId) AND reason='ABANDON' AND related_task_id=$($t2.Id) AND change_amount=-2;"
Test-Case '超时放弃写入一条扣分流水' ($penaltyLogs2 -eq 1) "logs=$penaltyLogs2"

# ---- 同一任务再次超时放弃不重复扣分 ----
$null = PostJson "/api/tasks/$($t2.Id)/apply" '{}' $acceptor.Session
$null = PostJson "/api/tasks/$($t2.Id)/confirm" ("{`"applicationId`":$app2}") $publisher.Session
$null = Sql "UPDATE tasks SET accepted_at = DATE_SUB(NOW(), INTERVAL 40 MINUTE) WHERE id=$($t2.Id);"
$abandon3 = TryCall { PostJson "/api/tasks/$($t2.Id)/abandon" '{}' $acceptor.Session }
$abandon3Json = $abandon3.Body | ConvertFrom-Json
$penaltyLogs3 = SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($acceptor.UserId) AND reason='ABANDON' AND related_task_id=$($t2.Id);"
Test-Case '同一任务反复放弃不会重复扣分' ($abandon3Json.code -eq 0 -and $abandon3Json.data.penalty -eq 0 -and $abandon3Json.data.credit -eq 98 -and $penaltyLogs3 -eq 1) ("penalty=" + $abandon3Json.data.penalty + " logs=$penaltyLogs3")

# ---- 取消任务退款 ----
$t3 = PublishTask $publisher.Session "$mark 宿舍打扫" 15 'CLEANING'
$balBefore = SqlInt "SELECT points FROM users WHERE id=$($publisher.UserId);"
$cancel = TryCall { PostJson "/api/tasks/$($t3.Id)/cancel" '{}' $publisher.Session }
$cancelJson = $cancel.Body | ConvertFrom-Json
Test-Case '取消任务全额退回积分' ($cancelJson.code -eq 0 -and $cancelJson.data.status -eq 'CANCELLED' -and $cancelJson.data.refund -eq 15 -and $cancelJson.data.balance -eq ($balBefore + 15)) $cancel.Body

$refundLog = SqlInt "SELECT COUNT(*) FROM point_logs WHERE user_id=$($publisher.UserId) AND type='REFUND' AND related_task_id=$($t3.Id) AND change_amount=15;"
Test-Case '取消任务写入退款流水' ($refundLog -eq 1) "log=$refundLog"

$lobby2 = (TryCall { GetJson '/api/tasks?size=100' $third.Session }).Body | ConvertFrom-Json
$lobbyIds2 = @($lobby2.data.list | ForEach-Object { $_.id })
Test-Case '取消任务后从大厅消失' ($lobbyIds2 -notcontains $t3.Id) ($lobbyIds2 -join ',')

$cancelTwice = TryCall { PostJson "/api/tasks/$($t3.Id)/cancel" '{}' $publisher.Session }
Test-Case '重复取消返回 3004' (($cancelTwice.Body | ConvertFrom-Json).code -eq 3004) $cancelTwice.Body

# ---- 我的任务条数与数据库一致 ----
$dbPub = SqlInt "SELECT COUNT(*) FROM tasks WHERE publisher_id=$($publisher.UserId) AND offline=0;"
$apiPub = (TryCall { GetJson '/api/tasks/mine?type=published&size=50' $publisher.Session }).Body | ConvertFrom-Json
$dbAcc = SqlInt "SELECT COUNT(*) FROM tasks WHERE acceptor_id=$($acceptor.UserId) AND offline=0;"
$apiAcc = (TryCall { GetJson '/api/tasks/mine?type=accepted&size=50' $acceptor.Session }).Body | ConvertFrom-Json
Test-Case '我的任务两个 tab 条数与数据库一致' ($apiPub.data.total -eq $dbPub -and $apiAcc.data.total -eq $dbAcc) "pub=$($apiPub.data.total)/$dbPub acc=$($apiAcc.data.total)/$dbAcc"

$statusFilter = (TryCall { GetJson '/api/tasks/mine?type=published&status=CANCELLED&size=50' $publisher.Session }).Body | ConvertFrom-Json
Test-Case '我的任务状态筛选生效' ($statusFilter.data.total -eq 1 -and $statusFilter.data.list[0].id -eq $t3.Id) $statusFilter.data.total

$html = TryCall { Invoke-WebRequest -UseBasicParsing -Uri "$base/my-tasks.html" -TimeoutSec 10 }
Test-Case '我的任务页面可访问且为中文' ($html.Status -eq 200 -and $html.Body -match '我的任务') $html.Status

if ($failed -gt 0) { Write-Output "阶段6测试失败：$failed 项"; exit 1 }
Write-Output '阶段6测试全部通过'
