﻿# 阶段 11 自测：管理端申诉处理（列表 / 计数 / 详情 / 通过 / 驳回）与任务管理（搜索 / 下架 / 恢复）
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/campusquest'
$failed = 0
$tmp = Join-Path $env:TEMP ('cq_stage11_' + (Get-Random))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null

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
function PublishTask($session, $title, $points, $category) {
    $json = "{`"category`":`"$category`",`"title`":`"$title`",`"description`":`"阶段十一测试任务描述`",`"points`":$points,`"locationDesc`":`"宿舍区 → 教学区`",`"timeDesc`":`"今天 20:00 前`"}"
    $j = (PostJson '/api/tasks' $json $session).Content | ConvertFrom-Json
    return $j.data.taskId
}
function LoginCookie($phone) {
    $file = Join-Path $tmp ("cookie_" + $phone + ".txt")
    $body = "{`"phone`":`"$phone`",`"password`":`"123456`"}"
    $null = & curl.exe -s -c $file -H "Content-Type: application/json" -d $body "$base/api/auth/login"
    return $file
}
function SubmitAppeal($cookieFile, $taskId, $content, $pngPath) {
    $resp = & curl.exe -s -b $cookieFile -c $cookieFile -F "taskId=$taskId" -F "content=$content" -F "screenshot=@$($pngPath.Replace('\','/'));type=image/png" "$base/api/appeals"
    return (($resp -join '') | ConvertFrom-Json)
}

$pngPath = Join-Path $tmp 'appeal.png'
$pngBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
[IO.File]::WriteAllBytes($pngPath, $pngBytes)

$mark = '阶段十一' + (Get-Random -Minimum 1000 -Maximum 9999)
$pub = NewStudent '阶段十一发布者'
$acc = NewStudent '阶段十一接单人'
$accCookie = LoginCookie $acc.Phone
$none = $null

# ---------- 权限 ----------
$guest = TryCall { GetJson '/api/admin/appeals' $none }
Test-Case '未登录访问管理端申诉接口返回 401' ($guest.Status -eq 401) "status=$($guest.Status)"
$guestBody = $guest.Body | ConvertFrom-Json
Test-Case '未登录错误码为 1002' ($guestBody.code -eq 1002) "code=$($guestBody.code)"
$stu = TryCall { GetJson '/api/admin/appeals' $acc.Session }
Test-Case '学生访问管理端申诉接口返回 403' ($stu.Status -eq 403) "status=$($stu.Status)"
$stuBody = $stu.Body | ConvertFrom-Json
Test-Case '学生访问错误码为 1003' ($stuBody.code -eq 1003) "code=$($stuBody.code)"
$stuTask = TryCall { PostJson '/api/admin/tasks/1/offline' '{"reason":"x"}' $acc.Session }
Test-Case '学生调用管理端任务接口被拒绝' ($stuTask.Status -eq 403) "status=$($stuTask.Status)"

# ---------- 管理员登录 ----------
$adminS = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$adminLogin = (PostJson '/api/auth/admin/login' '{"username":"admin","password":"admin123"}' $adminS).Content | ConvertFrom-Json
Test-Case '管理员登录成功' ($adminLogin.code -eq 0) "code=$($adminLogin.code)"
$me = (GetJson '/api/admin/me' $adminS).Content | ConvertFrom-Json
Test-Case '管理端 me 返回 admin' ($me.data.username -eq 'admin') "username=$($me.data.username)"
Test-Case '管理端 me 角色为 ADMIN' ($me.data.role -eq 'ADMIN') "role=$($me.data.role)"

# ---------- 造数据：任务一超时放弃 + 提交申诉 ----------
$t1 = PublishTask $pub.Session "$mark 任务一" 10 'EXPRESS'
$apply1 = (PostJson "/api/tasks/$t1/apply" '{}' $acc.Session).Content | ConvertFrom-Json
$null = PostJson "/api/tasks/$t1/confirm" ("{`"applicationId`":$($apply1.data.applicationId)}") $pub.Session
$null = Sql "UPDATE tasks SET accepted_at = DATE_SUB(NOW(), INTERVAL 40 MINUTE) WHERE id=$t1;"
$abandon1 = (PostJson "/api/tasks/$t1/abandon" '{}' $acc.Session).Content | ConvertFrom-Json
Test-Case '超时放弃扣 2 分信用' ($abandon1.data.credit -eq 98) "credit=$($abandon1.data.credit)"
$appealContent1 = "$mark 当天临时封路，已提前在聊天里和发布者说明并得到同意，请核实截图"
$ap1 = SubmitAppeal $accCookie $t1 $appealContent1 $pngPath
Test-Case '学生提交申诉成功' ($ap1.code -eq 0) "code=$($ap1.code) msg=$($ap1.message)"
Test-Case '申诉截图落盘为相对路径' ($ap1.data.screenshotPath -match '^uploads/\d{4}/\d{2}/\d{2}/[0-9a-f]{32}\.png$') "path=$($ap1.data.screenshotPath)"
$ap1Id = $ap1.data.appealId

# ---------- 列表与计数 ----------
$pendingList = (GetJson '/api/admin/appeals?status=PENDING&page=1&size=50' $adminS).Content | ConvertFrom-Json
$pendingDb = SqlInt "SELECT COUNT(*) FROM appeals WHERE status='PENDING';"
Test-Case '待处理列表总数与数据库一致' ([int]$pendingList.data.total -eq $pendingDb) "api=$($pendingList.data.total) db=$pendingDb"
$idList = @($pendingList.data.list | ForEach-Object { $_.id })
Test-Case '待处理列表包含刚提交的申诉' ($idList -contains $ap1Id) "ids=$($idList -join ',') id=$ap1Id"
$counts = (GetJson '/api/admin/appeals/counts' $adminS).Content | ConvertFrom-Json
Test-Case '待处理计数与数据库一致' ([int]$counts.data.pending -eq (SqlInt "SELECT COUNT(*) FROM appeals WHERE status='PENDING';")) "api=$($counts.data.pending)"
Test-Case '已通过计数与数据库一致' ([int]$counts.data.approved -eq (SqlInt "SELECT COUNT(*) FROM appeals WHERE status='APPROVED';")) "api=$($counts.data.approved)"
Test-Case '已驳回计数与数据库一致' ([int]$counts.data.rejected -eq (SqlInt "SELECT COUNT(*) FROM appeals WHERE status='REJECTED';")) "api=$($counts.data.rejected)"

# ---------- 详情 ----------
$detail = (GetJson "/api/admin/appeals/$ap1Id" $adminS).Content | ConvertFrom-Json
Test-Case '详情申诉人昵称正确' ($detail.data.userNickname -eq $acc.Nickname) "v=$($detail.data.userNickname)"
Test-Case '详情显示当前信用分 98' ([int]$detail.data.userCredit -eq 98) "v=$($detail.data.userCredit)"
Test-Case '详情包含任务标题' ($detail.data.taskTitle -like "*$mark 任务一*") "v=$($detail.data.taskTitle)"
Test-Case '详情包含任务编号' ($detail.data.taskNo -match '^CQ-\d{8}-\d{3}$') "v=$($detail.data.taskNo)"
Test-Case '详情申诉说明无乱码' ($detail.data.content -eq $appealContent1) "v=$($detail.data.content)"
Test-Case '详情包含截图路径' ($detail.data.screenshotPath -eq $ap1.data.screenshotPath) "v=$($detail.data.screenshotPath)"
$abandonLog = @($detail.data.creditLogs | Where-Object { $_.reason -eq 'ABANDON' })
Test-Case '详情包含放弃扣分流水' ($abandonLog.Count -ge 1 -and [int]$abandonLog[0].changeAmount -eq -2) "count=$($abandonLog.Count)"
$missing = TryCall { GetJson '/api/admin/appeals/99999999' $adminS }
Test-Case '不存在的申诉返回 404' ($missing.Status -eq 404) "status=$($missing.Status)"

# ---------- 通过申诉 ----------
$beforeCredit = SqlInt "SELECT credit FROM users WHERE id=$($acc.UserId);"
$approve = (PostJson "/api/admin/appeals/$ap1Id/approve" '{}' $adminS).Content | ConvertFrom-Json
Test-Case '通过申诉返回成功' ($approve.code -eq 0) "code=$($approve.code) msg=$($approve.message)"
Test-Case '通过后状态为 APPROVED' ($approve.data.status -eq 'APPROVED') "v=$($approve.data.status)"
Test-Case '通过后信用分 +2' ([int]$approve.data.credit -eq ($beforeCredit + 2)) "before=$beforeCredit after=$($approve.data.credit)"
Test-Case '数据库信用分为 100' ((SqlInt "SELECT credit FROM users WHERE id=$($acc.UserId);") -eq 100) 'db credit'
Test-Case '新增 APPEAL_REFUND +2 流水' ((SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($acc.UserId) AND related_task_id=$t1 AND reason='APPEAL_REFUND' AND change_amount=2;") -eq 1) 'refund log'
Test-Case '学生收到申诉通过通知' ((SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($acc.UserId) AND type='APPEAL_RESULT' AND content LIKE '%申诉已通过%';") -ge 1) 'notify'
$again = (PostJson "/api/admin/appeals/$ap1Id/approve" '{}' $adminS).Content | ConvertFrom-Json
Test-Case '已处理申诉再次通过返回 3012' ($again.code -eq 3012) "code=$($again.code) msg=$($again.message)"
Test-Case '重复通过不会重复加信用' ((SqlInt "SELECT COUNT(*) FROM credit_logs WHERE user_id=$($acc.UserId) AND related_task_id=$t1 AND reason='APPEAL_REFUND';") -eq 1) 'dup refund'
Test-Case '重复通过后信用仍为 100' ((SqlInt "SELECT credit FROM users WHERE id=$($acc.UserId);") -eq 100) 'credit'

# ---------- 驳回申诉 ----------
$t2 = PublishTask $pub.Session "$mark 任务二" 10 'ERRAND'
$apply2 = (PostJson "/api/tasks/$t2/apply" '{}' $acc.Session).Content | ConvertFrom-Json
$null = PostJson "/api/tasks/$t2/confirm" ("{`"applicationId`":$($apply2.data.applicationId)}") $pub.Session
$null = Sql "UPDATE tasks SET accepted_at = DATE_SUB(NOW(), INTERVAL 40 MINUTE) WHERE id=$t2;"
$null = PostJson "/api/tasks/$t2/abandon" '{}' $acc.Session
$ap2 = SubmitAppeal $accCookie $t2 "$mark 第二次申诉说明" $pngPath
Test-Case '第二条申诉提交成功' ($ap2.code -eq 0) "code=$($ap2.code) msg=$($ap2.message)"
$ap2Id = $ap2.data.appealId
$emptyReject = (PostJson "/api/admin/appeals/$ap2Id/reject" '{"reason":"   "}' $adminS).Content | ConvertFrom-Json
Test-Case '驳回理由为空返回 1001' ($emptyReject.code -eq 1001) "code=$($emptyReject.code) msg=$($emptyReject.message)"
Test-Case '驳回失败后申诉仍为待处理' ((Sql "SELECT status FROM appeals WHERE id=$ap2Id;") -eq 'PENDING') 'status'
$rejectReason = '截图无法证明发布者同意延期，按规则维持扣分'
$rejectJson = "{`"reason`":`"$rejectReason`"}"
$reject = (PostJson "/api/admin/appeals/$ap2Id/reject" $rejectJson $adminS).Content | ConvertFrom-Json
Test-Case '驳回申诉返回成功' ($reject.code -eq 0) "code=$($reject.code) msg=$($reject.message)"
Test-Case '驳回后状态为 REJECTED' ($reject.data.status -eq 'REJECTED') "v=$($reject.data.status)"
$dbReject = Sql "SELECT reject_reason FROM appeals WHERE id=$ap2Id;"
Test-Case '驳回理由已保存且无乱码' ($dbReject -eq $rejectReason) "db=$dbReject"
Test-Case '学生收到含驳回理由的通知' ((SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($acc.UserId) AND type='APPEAL_RESULT' AND content LIKE '%截图无法证明%';") -ge 1) 'notify'
Test-Case '驳回后信用分不变（仍为 98）' ((SqlInt "SELECT credit FROM users WHERE id=$($acc.UserId);") -eq 98) 'credit'
$approveRejected = (PostJson "/api/admin/appeals/$ap2Id/approve" '{}' $adminS).Content | ConvertFrom-Json
Test-Case '已驳回申诉不能再通过（3012）' ($approveRejected.code -eq 3012) "code=$($approveRejected.code)"
$rejectedList = (GetJson '/api/admin/appeals?status=REJECTED&page=1&size=50' $adminS).Content | ConvertFrom-Json
$rejectedIds = @($rejectedList.data.list | ForEach-Object { $_.id })
Test-Case '已驳回列表包含第二条申诉' ($rejectedIds -contains $ap2Id) "ids=$($rejectedIds -join ',')"
$counts2 = (GetJson '/api/admin/appeals/counts' $adminS).Content | ConvertFrom-Json
Test-Case '处理后计数与数据库一致' ([int]$counts2.data.approved -eq (SqlInt "SELECT COUNT(*) FROM appeals WHERE status='APPROVED';") -and [int]$counts2.data.rejected -eq (SqlInt "SELECT COUNT(*) FROM appeals WHERE status='REJECTED';")) "approved=$($counts2.data.approved) rejected=$($counts2.data.rejected)"

# ---------- 任务管理：搜索 ----------
$t3 = PublishTask $pub.Session "$mark 任务三" 12 'EXPRESS'
$apply3 = (PostJson "/api/tasks/$t3/apply" '{}' $acc.Session).Content | ConvertFrom-Json
$null = PostJson "/api/tasks/$t3/confirm" ("{`"applicationId`":$($apply3.data.applicationId)}") $pub.Session
$t3No = Sql "SELECT task_no FROM tasks WHERE id=$t3;"
$encMark = [uri]::EscapeDataString($mark)
$encNick = [uri]::EscapeDataString($pub.Nickname)
$searchMark = (GetJson "/api/admin/tasks?keyword=$encMark&page=1&size=50" $adminS).Content | ConvertFrom-Json
$searchIds = @($searchMark.data.list | ForEach-Object { $_.id })
Test-Case '按标题关键词搜到本阶段任务' ($searchIds -contains $t3) "ids=$($searchIds -join ',')"
Test-Case '关键词搜索总数与数据库一致' ([int]$searchMark.data.total -eq (SqlInt "SELECT COUNT(*) FROM tasks t JOIN users u ON t.publisher_id=u.id WHERE t.title LIKE '%$mark%' OR t.task_no LIKE '%$mark%' OR u.nickname LIKE '%$mark%';")) "api=$($searchMark.data.total)"
$searchNo = (GetJson "/api/admin/tasks?keyword=$t3No&page=1&size=50" $adminS).Content | ConvertFrom-Json
Test-Case '按任务编号精确搜到一条' ([int]$searchNo.data.total -eq 1 -and $searchNo.data.list[0].id -eq $t3) "total=$($searchNo.data.total)"
$searchNick = (GetJson "/api/admin/tasks?keyword=$encNick&page=1&size=50" $adminS).Content | ConvertFrom-Json
$nickIds = @($searchNick.data.list | ForEach-Object { $_.id })
Test-Case '按发布者昵称搜到任务' ($nickIds -contains $t1 -and $nickIds -contains $t2) "ids=$($nickIds -join ',')"

# ---------- 下架 ----------
$emptyOffline = (PostJson "/api/admin/tasks/$t3/offline" '{"reason":""}' $adminS).Content | ConvertFrom-Json
Test-Case '下架理由为空返回 1001' ($emptyOffline.code -eq 1001) "code=$($emptyOffline.code) msg=$($emptyOffline.message)"
Test-Case '下架失败时任务仍为展示中' ((SqlInt "SELECT offline FROM tasks WHERE id=$t3;") -eq 0) 'offline'
$offlineReason = '任务描述含违规内容，请整改后联系管理员'
$offlineJson = "{`"reason`":`"$offlineReason`"}"
$offline = (PostJson "/api/admin/tasks/$t3/offline" $offlineJson $adminS).Content | ConvertFrom-Json
Test-Case '下架任务成功' ($offline.code -eq 0) "code=$($offline.code) msg=$($offline.message)"
Test-Case '数据库 offline 为 1' ((SqlInt "SELECT offline FROM tasks WHERE id=$t3;") -eq 1) 'offline'
Test-Case '数据库记录下架理由' ((Sql "SELECT offline_reason FROM tasks WHERE id=$t3;") -eq $offlineReason) 'reason'
Test-Case '数据库记录下架时间' ((SqlInt "SELECT offline_at IS NOT NULL FROM tasks WHERE id=$t3;") -eq 1) 'offline_at'
Test-Case '下架不改变任务状态' ((Sql "SELECT status FROM tasks WHERE id=$t3;") -eq 'IN_PROGRESS') 'status'
$offlineAgain = (PostJson "/api/admin/tasks/$t3/offline" $offlineJson $adminS).Content | ConvertFrom-Json
Test-Case '重复下架返回 3004' ($offlineAgain.code -eq 3004) "code=$($offlineAgain.code) msg=$($offlineAgain.message)"
$lobbyOffline = (GetJson "/api/tasks?keyword=$encMark&onlyWaiting=0&page=1&size=50" $acc.Session).Content | ConvertFrom-Json
$lobbyIds = @($lobbyOffline.data.list | ForEach-Object { $_.id })
Test-Case '下架后大厅看不到该任务' (-not ($lobbyIds -contains $t3)) "ids=$($lobbyIds -join ',')"
Test-Case '大厅总数不含已下架任务' ([int]$lobbyOffline.data.total -eq (SqlInt "SELECT COUNT(*) FROM tasks WHERE offline=0 AND status<>'CANCELLED' AND title LIKE '%$mark%';")) "api=$($lobbyOffline.data.total)"
$detailOffline = TryCall { GetJson "/api/tasks/$t3" $acc.Session }
Test-Case '下架后详情返回 404' ($detailOffline.Status -eq 404) "status=$($detailOffline.Status)"
$detailOfflineBody = $detailOffline.Body | ConvertFrom-Json
Test-Case '下架后详情提示任务不存在或已下架' ($detailOfflineBody.code -eq 1004 -and $detailOfflineBody.message -like '*已下架*') "code=$($detailOfflineBody.code) msg=$($detailOfflineBody.message)"
$ov = (GetJson '/api/stats/overview' $acc.Session).Content | ConvertFrom-Json
$topIds = @($ov.data.top | ForEach-Object { $_.taskId })
Test-Case 'Top5 不含已下架任务' (-not ($topIds -contains $t3)) "ids=$($topIds -join ',')"
Test-Case '任务总数不含已下架任务' ([int]$ov.data.totalTasks -eq (SqlInt "SELECT COUNT(*) FROM tasks WHERE offline=0;")) "api=$($ov.data.totalTasks)"
$expressCount = @($ov.data.categories | Where-Object { $_.category -eq 'EXPRESS' })[0].count
Test-Case '分类占比不含已下架任务' ([int]$expressCount -eq (SqlInt "SELECT COUNT(*) FROM tasks WHERE offline=0 AND category='EXPRESS';")) "api=$expressCount"
$offlineFilter = (GetJson "/api/admin/tasks?keyword=$encMark&offline=1&page=1&size=50" $adminS).Content | ConvertFrom-Json
$allOffline = $true
foreach ($row in @($offlineFilter.data.list)) { if ([int]$row.offline -ne 1) { $allOffline = $false } }
Test-Case '已下架筛选只返回下架任务' ($allOffline -and [int]$offlineFilter.data.total -ge 1) "total=$($offlineFilter.data.total)"
Test-Case '已下架筛选结果包含任务三' (@($offlineFilter.data.list | ForEach-Object { $_.id }) -contains $t3) 'missing'

# ---------- 恢复 ----------
$online = (PostJson "/api/admin/tasks/$t3/online" '{}' $adminS).Content | ConvertFrom-Json
Test-Case '恢复任务成功' ($online.code -eq 0) "code=$($online.code) msg=$($online.message)"
Test-Case '恢复后 offline 为 0' ((SqlInt "SELECT offline FROM tasks WHERE id=$t3;") -eq 0) 'offline'
Test-Case '恢复后清空下架理由' ((SqlInt "SELECT offline_reason IS NULL FROM tasks WHERE id=$t3;") -eq 1) 'reason'
Test-Case '恢复后原状态仍为进行中' ((Sql "SELECT status FROM tasks WHERE id=$t3;") -eq 'IN_PROGRESS') 'status'
$onlineAgain = (PostJson "/api/admin/tasks/$t3/online" '{}' $adminS).Content | ConvertFrom-Json
Test-Case '重复恢复返回 3004' ($onlineAgain.code -eq 3004) "code=$($onlineAgain.code)"
$lobbyOnline = (GetJson "/api/tasks?keyword=$encMark&onlyWaiting=0&page=1&size=50" $acc.Session).Content | ConvertFrom-Json
$lobbyIds2 = @($lobbyOnline.data.list | ForEach-Object { $_.id })
Test-Case '恢复后任务回到大厅' ($lobbyIds2 -contains $t3) "ids=$($lobbyIds2 -join ',')"
$detailOnline = (GetJson "/api/tasks/$t3" $acc.Session).Content | ConvertFrom-Json
Test-Case '恢复后详情可访问且状态未变' ($detailOnline.code -eq 0 -and $detailOnline.data.status -eq 'IN_PROGRESS') "status=$($detailOnline.data.status)"

# ---------- 管理端侧栏 ----------
$appealsHtml = Get-Content -Raw -Encoding UTF8 'E:\MyApp\server\src\main\webapp\admin\appeals.html'
$tasksHtml = Get-Content -Raw -Encoding UTF8 'E:\MyApp\server\src\main\webapp\admin\tasks.html'
$navA = [regex]::Match($appealsHtml, '<nav class="nav">([\s\S]*?)</nav>')
$navT = [regex]::Match($tasksHtml, '<nav class="nav">([\s\S]*?)</nav>')
$linksA = [regex]::Matches($navA.Groups[1].Value, '<a ')
$linksT = [regex]::Matches($navT.Groups[1].Value, '<a ')
Test-Case '申诉处理页侧栏为三项' ($linksA.Count -eq 3) "count=$($linksA.Count)"
Test-Case '任务管理页侧栏为三项' ($linksT.Count -eq 3) "count=$($linksT.Count)"
Test-Case '侧栏包含申诉处理与任务管理' ($navA.Groups[1].Value -like '*申诉处理*' -and $navA.Groups[1].Value -like '*任务管理*' -and $navA.Groups[1].Value -like '*退出*') 'labels'
Test-Case '管理端页面不含学生端导航项' ($appealsHtml -notlike '*接单大厅*' -and $tasksHtml -notlike '*个人中心*') 'student nav leaked'

Write-Output ''
if ($failed -eq 0) { Write-Output '阶段 11 全部测试通过' } else { Write-Output "阶段 11 测试失败 $failed 项"; exit 1 }
