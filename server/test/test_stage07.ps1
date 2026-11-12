﻿# 阶段 7 自测：完成确认、积分结算、24 小时自动确认、双方互评与信用变化
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
    $json = "{`"category`":`"$category`",`"title`":`"$title`",`"description`":`"阶段七测试任务描述`",`"points`":$points,`"locationDesc`":`"宿舍区 → 教学区`",`"timeDesc`":`"今天 18:00 前`"}"
    $r = TryCall { PostJson '/api/tasks' $json $session }
    $j = $r.Body | ConvertFrom-Json
    return @{ Id = $j.data.taskId; Code = $j.code; Body = $r.Body }
}
function StartTask($publisher, $acceptor, $taskId) {
    $null = PostJson "/api/tasks/$taskId/apply" '{}' $acceptor.Session
    $appId = SqlInt "SELECT id FROM applications WHERE task_id=$taskId AND applicant_id=$($acceptor.UserId);"
    $null = PostJson "/api/tasks/$taskId/confirm" ("{`"applicationId`":$appId}") $publisher.Session
    return $appId
}
function Deliver($session, $taskId) {
    return TryCall { PostJson "/api/tasks/$taskId/deliver" '{}' $session }
}

$publisher = NewStudent '阶段七发布者'
$acceptor = NewStudent '阶段七接单人'
$stranger = NewStudent '阶段七路人'
$null = Sql "UPDATE users SET credit=95 WHERE id=$($publisher.UserId); UPDATE users SET credit=90 WHERE id=$($acceptor.UserId);"

$mark = '阶段七' + (Get-Random -Minimum 1000 -Maximum 9999)

# ---------- T1：手动确认完成 + 好评 ----------
$t1 = PublishTask $publisher.Session "$mark 帮忙取快递" 20 'EXPRESS'
$app1 = StartTask $publisher $acceptor $t1.Id
$null = Deliver $acceptor.Session $t1.Id
$pointsBefore = SqlInt "SELECT points FROM users WHERE id=$($acceptor.UserId);"

$detailBefore = (TryCall { GetJson "/api/tasks/$($t1.Id)" $publisher.Session }).Body | ConvertFrom-Json
Test-Case '待确认时发布者可确认完成、接单者不可' ($detailBefore.data.canComplete -eq $true -and $detailBefore.data.canReview -eq $false) $detailBefore.data.canComplete

$complete = TryCall { PostJson "/api/tasks/$($t1.Id)/complete" '{}' $publisher.Session }
Test-Case '发布者确认完成成功' (($complete.Body | ConvertFrom-Json).code -eq 0 -and ($complete.Body | ConvertFrom-Json).data.status -eq 'COMPLETED') $complete.Body

$status1 = Sql "SELECT CONCAT(status, ':', IFNULL(finished_at, 'null')) FROM tasks WHERE id=$($t1.Id);"
Test-Case '任务状态为已完成且记录完成时间' ($status1 -match '^COMPLETED:\d{4}') $status1

$pointsAfter = SqlInt "SELECT points FROM users WHERE id=$($acceptor.UserId);"
$incomeLog = SqlInt "SELECT COUNT(*) FROM point_logs WHERE user_id=$($acceptor.UserId) AND type='INCOME' AND related_task_id=$($t1.Id) AND change_amount=20;"
Test-Case '完成后积分结算给接单者并写收入流水' (($pointsAfter - $pointsBefore) -eq 20 -and $incomeLog -eq 1) "before=$pointsBefore after=$pointsAfter log=$incomeLog"

$settleA = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($publisher.UserId) AND type='SETTLEMENT' AND related_task_id=$($t1.Id);"
$settleB = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($acceptor.UserId) AND type='SETTLEMENT' AND related_task_id=$($t1.Id);"
Test-Case '完成时给双方写结算通知' ($settleA -eq 1 -and $settleB -eq 1) "A=$settleA B=$settleB"

$completeAgain = TryCall { PostJson "/api/tasks/$($t1.Id)/complete" '{}' $publisher.Session }
Test-Case '重复确认完成返回 3004' (($completeAgain.Body | ConvertFrom-Json).code -eq 3004) $completeAgain.Body

$reviewA = TryCall { PostJson "/api/tasks/$($t1.Id)/review" '{"rating":"GOOD","comment":"很准时，谢谢"}' $publisher.Session }
$reviewAJson = $reviewA.Body | ConvertFrom-Json
$creditAfterGood = SqlInt "SELECT credit FROM users WHERE id=$($acceptor.UserId);"
$goodLog = SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($acceptor.UserId) AND reason='GOOD_REVIEW' AND related_task_id=$($t1.Id) AND change_amount=3;"
Test-Case '好评使对方信用 +3 并写流水' ($reviewAJson.code -eq 0 -and $reviewAJson.data.change -eq 3 -and $creditAfterGood -eq 93 -and $goodLog -eq 1) "credit=$creditAfterGood log=$goodLog"

$reviewNotify = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($acceptor.UserId) AND type='REVIEW_RECEIVED' AND related_task_id=$($t1.Id);"
Test-Case '收到评价通知已写入' ($reviewNotify -eq 1) "notify=$reviewNotify"

$reviewDup = TryCall { PostJson "/api/tasks/$($t1.Id)/review" '{"rating":"BAD"}' $publisher.Session }
Test-Case '重复评价返回 3009' (($reviewDup.Body | ConvertFrom-Json).code -eq 3009) $reviewDup.Body

$reviewB = TryCall { PostJson "/api/tasks/$($t1.Id)/review" '{"rating":"GOOD","comment":"合作愉快"}' $acceptor.Session }
$creditPublisher = SqlInt "SELECT credit FROM users WHERE id=$($publisher.UserId);"
Test-Case '接单者也可评价发布者（信用 +3）' (($reviewB.Body | ConvertFrom-Json).code -eq 0 -and $creditPublisher -eq 98) "publisherCredit=$creditPublisher"

$detailAfter = (TryCall { GetJson "/api/tasks/$($t1.Id)" $acceptor.Session }).Body | ConvertFrom-Json
Test-Case '评价后详情展示我的评价且不可再评' ($detailAfter.data.myReviewRating -eq 'GOOD' -and $detailAfter.data.canReview -eq $false) $detailAfter.data.myReviewRating

$reviewStranger = TryCall { PostJson "/api/tasks/$($t1.Id)/review" '{"rating":"GOOD"}' $stranger.Session }
Test-Case '非任务双方评价返回 1003' (($reviewStranger.Body | ConvertFrom-Json).code -eq 1003) $reviewStranger.Body

$reviewInvalid = TryCall { PostJson "/api/tasks/$($t1.Id)/review" '{"rating":"XX"}' $stranger.Session }
Test-Case '非法评价取值返回 1001' (($reviewInvalid.Body | ConvertFrom-Json).code -eq 1001) $reviewInvalid.Body

$longComment = '很' * 101
$reviewLong = TryCall { PostJson "/api/tasks/$($t1.Id)/review" ("{`"rating`":`"GOOD`",`"comment`":`"$longComment`"}") $stranger.Session }
Test-Case '评价内容超过 100 字返回 1001' (($reviewLong.Body | ConvertFrom-Json).code -eq 1001) ($reviewLong.Body.Substring(0, [Math]::Min(80, $reviewLong.Body.Length)))

# ---------- T5：未完成任务不能评价 ----------
$t5 = PublishTask $publisher.Session "$mark 未完成任务" 5 'OTHER'
$reviewEarly = TryCall { PostJson "/api/tasks/$($t5.Id)/review" '{"rating":"GOOD"}' $publisher.Session }
Test-Case '未完成任务评价返回 3010' (($reviewEarly.Body | ConvertFrom-Json).code -eq 3010) $reviewEarly.Body
$null = PostJson "/api/tasks/$($t5.Id)/cancel" '{}' $publisher.Session

# ---------- T2：24 小时超时自动确认 ----------
$t2 = PublishTask $publisher.Session "$mark 自动确认任务" 30 'TAKEOUT'
$null = StartTask $publisher $acceptor $t2.Id
$null = Deliver $acceptor.Session $t2.Id
$pointsBefore2 = SqlInt "SELECT points FROM users WHERE id=$($acceptor.UserId);"
$null = Sql "UPDATE tasks SET confirm_deadline = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=$($t2.Id);"

Start-Sleep -Milliseconds 3300
$null = TryCall { GetJson '/api/ping' $null }
Start-Sleep -Milliseconds 500

$status2 = Sql "SELECT status FROM tasks WHERE id=$($t2.Id);"
$pointsAfter2 = SqlInt "SELECT points FROM users WHERE id=$($acceptor.UserId);"
$autoLog = SqlInt "SELECT COUNT(*) FROM point_logs WHERE user_id=$($acceptor.UserId) AND type='INCOME' AND related_task_id=$($t2.Id) AND change_amount=30;"
Test-Case '超过确认期限后请求触发自动完成并结算' ($status2 -eq 'COMPLETED' -and ($pointsAfter2 - $pointsBefore2) -eq 30 -and $autoLog -eq 1) "status=$status2 before=$pointsBefore2 after=$pointsAfter2 log=$autoLog"

$autoNotify = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($acceptor.UserId) AND type='SETTLEMENT' AND related_task_id=$($t2.Id) AND content LIKE '%自动确认%';"
Test-Case '自动完成通知标注系统自动确认' ($autoNotify -eq 1) "notify=$autoNotify"

$detail2 = (TryCall { GetJson "/api/tasks/$($t2.Id)" $acceptor.Session }).Body | ConvertFrom-Json
Test-Case '自动完成后详情剩余秒数为空且可评价' ($detail2.data.remainingSeconds -eq $null -and $detail2.data.canReview -eq $true) ("remain=" + $detail2.data.remainingSeconds)

$reviewBad = TryCall { PostJson "/api/tasks/$($t2.Id)/review" '{"rating":"BAD","comment":"迟到了很久"}' $acceptor.Session }
$creditBad = SqlInt "SELECT credit FROM users WHERE id=$($publisher.UserId);"
$badLog = SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($publisher.UserId) AND reason='BAD_REVIEW' AND related_task_id=$($t2.Id) AND change_amount=-3;"
Test-Case '差评使对方信用 -3 并写流水' (($reviewBad.Body | ConvertFrom-Json).code -eq 0 -and $creditBad -eq 95 -and $badLog -eq 1) "credit=$creditBad log=$badLog"

# ---------- T3：信用分封顶 ----------
$t3 = PublishTask $publisher.Session "$mark 封顶测试" 10 'CLEANING'
$null = StartTask $publisher $acceptor $t3.Id
$null = Deliver $acceptor.Session $t3.Id
$null = PostJson "/api/tasks/$($t3.Id)/complete" '{}' $publisher.Session
$null = Sql "UPDATE users SET credit=100 WHERE id=$($acceptor.UserId);"
$reviewCap = TryCall { PostJson "/api/tasks/$($t3.Id)/review" '{"rating":"GOOD"}' $publisher.Session }
$capLog = SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($acceptor.UserId) AND reason='GOOD_REVIEW' AND related_task_id=$($t3.Id) AND change_amount=0 AND remark LIKE '%已封顶%';"
$creditCap = SqlInt "SELECT credit FROM users WHERE id=$($acceptor.UserId);"
Test-Case '信用满分时好评写 0 分封顶流水' (($reviewCap.Body | ConvertFrom-Json).code -eq 0 -and $creditCap -eq 100 -and $capLog -eq 1) "credit=$creditCap log=$capLog"

# ---------- T4：非发布者不能确认完成 ----------
$t4 = PublishTask $publisher.Session "$mark 权限测试" 5 'MOVING'
$null = StartTask $publisher $acceptor $t4.Id
$null = Deliver $acceptor.Session $t4.Id
$detail4 = (TryCall { GetJson "/api/tasks/$($t4.Id)" $acceptor.Session }).Body | ConvertFrom-Json
Test-Case '接单者视角不可确认完成' ($detail4.data.canComplete -eq $false) $detail4.data.canComplete
$completeByAcceptor = TryCall { PostJson "/api/tasks/$($t4.Id)/complete" '{}' $acceptor.Session }
Test-Case '非发布者确认完成返回 1003' (($completeByAcceptor.Body | ConvertFrom-Json).code -eq 1003) $completeByAcceptor.Body
$null = PostJson "/api/tasks/$($t4.Id)/complete" '{}' $publisher.Session

# ---------- 我的任务评价状态 ----------
$mineDone = (TryCall { GetJson '/api/tasks/mine?type=accepted&status=COMPLETED&size=50' $acceptor.Session }).Body | ConvertFrom-Json
$row1 = @($mineDone.data.list | Where-Object { $_.id -eq $t1.Id })
$row2 = @($mineDone.data.list | Where-Object { $_.id -eq $t2.Id })
Test-Case '我的任务返回双方评价状态' ($row1.Count -eq 1 -and $row1[0].myReviewRating -eq 'GOOD' -and $row2[0].myReviewRating -eq 'BAD') ("t1=" + $row1[0].myReviewRating + " t2=" + $row2[0].myReviewRating)

if ($failed -gt 0) { Write-Output "阶段7测试失败：$failed 项"; exit 1 }
Write-Output '阶段7测试全部通过'
