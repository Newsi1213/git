﻿# 阶段 8 自测：积分充值、积分流水、排行榜、信用记录与申诉
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/campusquest'
$failed = 0
$tmp = Join-Path $env:TEMP ('cq_stage8_' + (Get-Random))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null

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
function SqlInt($sql) { $v = Sql $sql; if ($null -eq $v -or $v -eq '') { return -1 }; return [int]$v }
function NewStudent($nickname) {
    $phone = '137' + (Get-Random -Minimum 10000000 -Maximum 99999999)
    $none = $null
    $r = TryCall { PostJson '/api/auth/register' ("{`"phone`":`"$phone`",`"nickname`":`"$nickname`",`"password`":`"123456`"}") $none }
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $null = PostJson '/api/auth/login' ("{`"phone`":`"$phone`",`"password`":`"123456`"}") $s
    return @{ Phone = $phone; Session = $s; UserId = ($r.Body | ConvertFrom-Json).data.userId }
}
function PublishTask($session, $title, $points, $category) {
    $json = "{`"category`":`"$category`",`"title`":`"$title`",`"description`":`"阶段八测试任务描述`",`"points`":$points,`"locationDesc`":`"宿舍区 → 教学区`",`"timeDesc`":`"今天 18:00 前`"}"
    $j = (TryCall { PostJson '/api/tasks' $json $session }).Body | ConvertFrom-Json
    return $j.data.taskId
}
function CurlAppeal($formArgs, $cookieFile) {
    $all = @() + $formArgs + @("$base/api/appeals")
    $out = & curl.exe -s -b $cookieFile -c $cookieFile @all
    return ($out -join '')
}
function LoginCookie($phone) {
    $file = Join-Path $tmp ("cookie_" + $phone + ".txt")
    $body = "{`"phone`":`"$phone`",`"password`":`"123456`"}"
    $null = & curl.exe -s -c $file -H "Content-Type: application/json" -d $body "$base/api/auth/login"
    return $file
}

# 测试素材
$pngPath = Join-Path $tmp 'shot.png'
$pngBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
[IO.File]::WriteAllBytes($pngPath, $pngBytes)
$txtPath = Join-Path $tmp 'note.txt'
[IO.File]::WriteAllText($txtPath, 'not an image')
$bigPath = Join-Path $tmp 'big.png'
$big = New-Object byte[] (6 * 1024 * 1024)
[IO.File]::WriteAllBytes($bigPath, $big)

$publisher = NewStudent '阶段八发布者'
$acceptor = NewStudent '阶段八接单人'
$cookieB = LoginCookie $acceptor.Phone
$mark = '阶段八' + (Get-Random -Minimum 1000 -Maximum 9999)

# ---------- 充值 ----------
$before = SqlInt "SELECT points FROM users WHERE id=$($acceptor.UserId);"
$recharge = TryCall { PostJson '/api/points/recharge' '{"amount":50}' $acceptor.Session }
$rechargeJson = $recharge.Body | ConvertFrom-Json
$after = SqlInt "SELECT points FROM users WHERE id=$($acceptor.UserId);"
$rechargeLog = SqlInt "SELECT COUNT(*) FROM point_logs WHERE user_id=$($acceptor.UserId) AND type='RECHARGE' AND change_amount=500 AND balance_after=$($before + 500);"
Test-Case '模拟充值 50 元到账 500 积分并写流水' ($rechargeJson.code -eq 0 -and $rechargeJson.data.added -eq 500 -and ($after - $before) -eq 500 -and $rechargeLog -eq 1) $recharge.Body

$badRecharge = TryCall { PostJson '/api/points/recharge' '{"amount":0}' $acceptor.Session }
Test-Case '充值金额 0 返回 1001' (($badRecharge.Body | ConvertFrom-Json).code -eq 1001) $badRecharge.Body
$badRecharge2 = TryCall { PostJson '/api/points/recharge' '{"amount":10001}' $acceptor.Session }
Test-Case '充值金额超过 10000 返回 1001' (($badRecharge2.Body | ConvertFrom-Json).code -eq 1001) $badRecharge2.Body

$me = (TryCall { GetJson '/api/auth/me' $acceptor.Session }).Body | ConvertFrom-Json
Test-Case '个人中心积分与数据库一致' ($me.code -eq 0 -and $me.data.points -eq (SqlInt "SELECT points FROM users WHERE id=$($acceptor.UserId);") -and $me.data.credit -eq (SqlInt "SELECT credit FROM users WHERE id=$($acceptor.UserId);")) $me.data.points

$logs = (TryCall { GetJson '/api/points/logs?page=1&size=2' $acceptor.Session }).Body | ConvertFrom-Json
Test-Case '积分流水分页返回正确结构' ($logs.code -eq 0 -and $logs.data.list.Count -le 2 -and $logs.data.size -eq 2 -and $logs.data.total -ge 2) ("total=" + $logs.data.total)

$rechargeLogs = (TryCall { GetJson '/api/points/logs?type=RECHARGE&size=50' $acceptor.Session }).Body | ConvertFrom-Json
$allRecharge = @($rechargeLogs.data.list | Where-Object { $_.type -ne 'RECHARGE' })
Test-Case '积分流水按类型过滤生效' ($rechargeLogs.code -eq 0 -and $rechargeLogs.data.total -ge 1 -and $allRecharge.Count -eq 0) ("total=" + $rechargeLogs.data.total)

# ---------- 构造放弃扣分 ----------
$task = PublishTask $publisher.Session "$mark 申诉用任务" 10 'ERRAND'
$null = PostJson "/api/tasks/$task/apply" '{}' $acceptor.Session
$appId = SqlInt "SELECT id FROM applications WHERE task_id=$task AND applicant_id=$($acceptor.UserId);"
$null = PostJson "/api/tasks/$task/confirm" ("{`"applicationId`":$appId}") $publisher.Session
$null = Sql "UPDATE tasks SET accepted_at = DATE_SUB(NOW(), INTERVAL 35 MINUTE) WHERE id=$task;"
$null = PostJson "/api/tasks/$task/abandon" '{}' $acceptor.Session
$abandonLog = SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($acceptor.UserId) AND related_task_id=$task AND reason='ABANDON' AND change_amount=-2;"
Test-Case '超时放弃产生扣分记录' ($abandonLog -eq 1) "log=$abandonLog"

$creditLogs = (TryCall { GetJson '/api/credit/logs?size=50' $acceptor.Session }).Body | ConvertFrom-Json
$row = @($creditLogs.data.list | Where-Object { $_.taskId -eq $task })
Test-Case '信用记录含任务标题且初始无申诉状态' ($creditLogs.code -eq 0 -and $row.Count -eq 1 -and $row[0].changeAmount -eq -2 -and $row[0].taskTitle -ne $null -and $row[0].appealId -eq $null) ($row | ConvertTo-Json -Compress)

# ---------- 申诉校验 ----------
$noShot = CurlAppeal @('-F', "taskId=$task", '-F', 'content=客观原因无法完成') $cookieB
$noShotJson = $noShot | ConvertFrom-Json
Test-Case '不上传截图提交申诉被拒 4003' ($noShotJson.code -eq 4003) $noShot

$badType = CurlAppeal @('-F', "taskId=$task", '-F', 'content=客观原因无法完成', '-F', "screenshot=@$txtPath;type=text/plain") $cookieB
Test-Case '非图片类型被拒 4001' (($badType | ConvertFrom-Json).code -eq 4001) $badType

$tooBig = CurlAppeal @('-F', "taskId=$task", '-F', 'content=客观原因无法完成', '-F', "screenshot=@$bigPath;type=image/png") $cookieB
Test-Case '超过 5MB 图片被拒 4002' (($tooBig | ConvertFrom-Json).code -eq 4002) ($tooBig.Substring(0, [Math]::Min(80, $tooBig.Length)))

$blank = CurlAppeal @('-F', "taskId=$task", '-F', 'content=', '-F', "screenshot=@$pngPath;type=image/png") $cookieB
Test-Case '申诉理由为空被拒 1001' (($blank | ConvertFrom-Json).code -eq 1001) $blank

$noPunishTask = PublishTask $publisher.Session "$mark 无扣分任务" 5 'OTHER'
$noPunish = CurlAppeal @('-F', "taskId=$noPunishTask", '-F', 'content=这个任务没有扣分', '-F', "screenshot=@$pngPath;type=image/png") $cookieB
Test-Case '无扣分记录的任务不能申诉 1001' (($noPunish | ConvertFrom-Json).code -eq 1001) $noPunish

$ok = CurlAppeal @('-F', "taskId=$task", '-F', 'content=当天突发高烧，已提前联系发布者说明', '-F', "screenshot=@$pngPath;type=image/png") $cookieB
$okJson = $ok | ConvertFrom-Json
Test-Case '上传截图提交申诉成功' ($okJson.code -eq 0 -and $okJson.data.status -eq 'PENDING' -and $okJson.data.screenshotPath -like 'uploads/*') $ok

$shotPath = $okJson.data.screenshotPath
$diskPath = 'E:\MyApp\' + ($shotPath -replace '/', '\')
Test-Case '申诉截图落盘到 uploads 目录' (Test-Path $diskPath) $diskPath

$img = TryCall { Invoke-WebRequest -UseBasicParsing -Uri "$base/$shotPath" -TimeoutSec 10 }
Test-Case '上传的截图可通过 /uploads 访问' ($img.Status -eq 200 -and $img.Body.Length -gt 0) ($img.Status)

$dup = CurlAppeal @('-F', "taskId=$task", '-F', 'content=重复提交', '-F', "screenshot=@$pngPath;type=image/png") $cookieB
Test-Case '同一任务重复申诉返回 3011' (($dup | ConvertFrom-Json).code -eq 3011) $dup

$mineAppeals = (TryCall { GetJson '/api/appeals/mine' $acceptor.Session }).Body | ConvertFrom-Json
$appealRow = @($mineAppeals.data | Where-Object { $_.taskId -eq $task })
Test-Case '我的申诉列表返回待处理记录' ($mineAppeals.code -eq 0 -and $appealRow.Count -eq 1 -and $appealRow[0].status -eq 'PENDING' -and $appealRow[0].taskTitle -ne $null) ($mineAppeals.data | ConvertTo-Json -Compress)

$creditLogs2 = (TryCall { GetJson '/api/credit/logs?size=50' $acceptor.Session }).Body | ConvertFrom-Json
$row2 = @($creditLogs2.data.list | Where-Object { $_.taskId -eq $task })
Test-Case '信用记录显示申诉待处理状态' ($row2.Count -eq 1 -and $row2[0].appealStatus -eq 'PENDING' -and $row2[0].appealId -ne $null) ($row2 | ConvertTo-Json -Compress)

# ---------- 排行榜 ----------
$origPoints = SqlInt "SELECT points FROM users WHERE id=$($acceptor.UserId);"
$null = Sql "UPDATE users SET points=77777 WHERE id=$($acceptor.UserId);"
$board = (TryCall { GetJson '/api/leaderboard?limit=20' $acceptor.Session }).Body | ConvertFrom-Json
Test-Case '排行榜榜首为积分最高用户' ($board.code -eq 0 -and $board.data.list.Count -gt 0 -and $board.data.list[0].userId -eq $acceptor.UserId -and $board.data.list[0].rank -eq 1) ($board.data.list[0] | ConvertTo-Json -Compress)
Test-Case '排行榜返回我的排名信息' ($board.data.me.rank -eq 1 -and $board.data.me.points -eq 77777) ($board.data.me | ConvertTo-Json -Compress)

$prev = 2147483647
$sorted = $true
foreach ($item in $board.data.list) {
    if ($item.points -gt $prev) { $sorted = $false }
    $prev = $item.points
}
Test-Case '排行榜按积分降序排列' $sorted ($board.data.list | ForEach-Object { $_.points } | Out-String)
$null = Sql "UPDATE users SET points=$origPoints WHERE id=$($acceptor.UserId);"

$limitBoard = (TryCall { GetJson '/api/leaderboard?limit=3' $acceptor.Session }).Body | ConvertFrom-Json
Test-Case '排行榜 limit 参数生效' ($limitBoard.code -eq 0 -and $limitBoard.data.list.Count -le 3) ("count=" + $limitBoard.data.list.Count)

$profilePage = TryCall { Invoke-WebRequest -UseBasicParsing -Uri "$base/profile.html" -TimeoutSec 10 }
$leaderPage = TryCall { Invoke-WebRequest -UseBasicParsing -Uri "$base/leaderboard.html" -TimeoutSec 10 }
Test-Case '个人中心与排行榜页面可访问且为中文' ($profilePage.Status -eq 200 -and $profilePage.Body -match '信用分规则' -and $leaderPage.Status -eq 200 -and $leaderPage.Body -match '积分排行榜') ($leaderPage.Status)

Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue

if ($failed -gt 0) { Write-Output "阶段8测试失败：$failed 项"; exit 1 }
Write-Output '阶段8测试全部通过'
