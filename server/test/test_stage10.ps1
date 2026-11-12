# 阶段 10 自测：工作台摘要、个人统计、近 7 天发布量、分类占比、Top 5
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/campusquest'
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
function PublishTask($session, $title, $points, $category) {
    $json = "{`"category`":`"$category`",`"title`":`"$title`",`"description`":`"阶段十测试任务描述`",`"points`":$points,`"locationDesc`":`"宿舍区 → 教学区`",`"timeDesc`":`"今天 20:00 前`"}"
    $j = (PostJson '/api/tasks' $json $session).Content | ConvertFrom-Json
    return $j.data.taskId
}

$mark = '阶段十' + (Get-Random -Minimum 1000 -Maximum 9999)
$a = NewStudent '阶段十发布者'
$b = NewStudent '阶段十接单人'
$c = NewStudent '阶段十申请人'
$none = $null

# 未登录访问看板接口
$guestSum = TryCall { GetJson '/api/stats/summary' $none }
Test-Case '未登录访问统计接口返回 401' ($guestSum.Status -eq 401) "status=$($guestSum.Status)"

# 造数据：A 发布 2 个任务，B 申请两个，C 申请任务一
$t1 = PublishTask $a.Session "$mark 任务一" 12 'EXPRESS'
$t2 = PublishTask $a.Session "$mark 任务二" 8 'CLEANING'
$applyB1 = (PostJson "/api/tasks/$t1/apply" '{}' $b.Session).Content | ConvertFrom-Json
$null = PostJson "/api/tasks/$t1/apply" '{}' $c.Session
$null = PostJson "/api/tasks/$t2/apply" '{}' $b.Session

# ---------- 摘要：待接单阶段 ----------
$sumA = (GetJson '/api/stats/summary' $a.Session).Content | ConvertFrom-Json
Test-Case 'A 我发布中为 2' ($sumA.data.publishedActive -eq 2) "v=$($sumA.data.publishedActive)"
Test-Case 'A 待接单为 2' ($sumA.data.publishedWaiting -eq 2) "v=$($sumA.data.publishedWaiting)"
Test-Case 'A 待我确认为 0' ($sumA.data.toConfirm -eq 0) "v=$($sumA.data.toConfirm)"
Test-Case 'A 进行中为 0' ($sumA.data.inProgress -eq 0) "v=$($sumA.data.inProgress)"
Test-Case 'A 未读数与数据库一致' ($sumA.data.unreadCount -eq (SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($a.UserId) AND is_read=0;")) "v=$($sumA.data.unreadCount)"
$sumB = (GetJson '/api/stats/summary' $b.Session).Content | ConvertFrom-Json
Test-Case 'B 进行中为 0（尚未确认）' ($sumB.data.inProgress -eq 0) "v=$($sumB.data.inProgress)"

# ---------- 摘要：确认后 ----------
$null = PostJson "/api/tasks/$t1/confirm" ("{`"applicationId`":$($applyB1.data.applicationId)}") $a.Session
$sumA2 = (GetJson '/api/stats/summary' $a.Session).Content | ConvertFrom-Json
Test-Case '确认后 A 进行中为 1' ($sumA2.data.inProgress -eq 1) "v=$($sumA2.data.inProgress)"
Test-Case '确认后 A 发布中仍为 2' ($sumA2.data.publishedActive -eq 2) "v=$($sumA2.data.publishedActive)"
Test-Case '确认后 A 待接单为 1' ($sumA2.data.publishedWaiting -eq 1) "v=$($sumA2.data.publishedWaiting)"
$sumB2 = (GetJson '/api/stats/summary' $b.Session).Content | ConvertFrom-Json
Test-Case '确认后 B 进行中为 1' ($sumB2.data.inProgress -eq 1) "v=$($sumB2.data.inProgress)"

# ---------- 摘要：送达后 ----------
$null = PostJson "/api/tasks/$t1/deliver" '{}' $b.Session
$sumA3 = (GetJson '/api/stats/summary' $a.Session).Content | ConvertFrom-Json
Test-Case '送达后 A 待我确认为 1' ($sumA3.data.toConfirm -eq 1) "v=$($sumA3.data.toConfirm)"
Test-Case '送达后 A 进行中为 0' ($sumA3.data.inProgress -eq 0) "v=$($sumA3.data.inProgress)"
$sumB3 = (GetJson '/api/stats/summary' $b.Session).Content | ConvertFrom-Json
Test-Case '送达后 B 进行中为 0' ($sumB3.data.inProgress -eq 0) "v=$($sumB3.data.inProgress)"

# ---------- 完成后个人统计 ----------
$null = PostJson "/api/tasks/$t1/complete" '{}' $a.Session
$perA = (GetJson '/api/stats/personal' $a.Session).Content | ConvertFrom-Json
Test-Case 'A 个人发布数为 2' ($perA.data.published -eq 2) "v=$($perA.data.published)"
Test-Case 'A 个人完成数为 1' ($perA.data.completed -eq 1) "v=$($perA.data.completed)"
Test-Case 'A 个人接单数为 0' ($perA.data.accepted -eq 0) "v=$($perA.data.accepted)"
Test-Case 'A 积分收入与流水一致' ($perA.data.income -eq (SqlInt "SELECT IFNULL(SUM(change_amount),0) FROM point_logs WHERE user_id=$($a.UserId) AND change_amount>0;")) "v=$($perA.data.income)"
Test-Case 'A 积分支出与流水一致' ($perA.data.expense -eq (SqlInt "SELECT IFNULL(-SUM(change_amount),0) FROM point_logs WHERE user_id=$($a.UserId) AND change_amount<0;")) "v=$($perA.data.expense)"
$perB = (GetJson '/api/stats/personal' $b.Session).Content | ConvertFrom-Json
Test-Case 'B 个人接单数为 1' ($perB.data.accepted -eq 1) "v=$($perB.data.accepted)"
Test-Case 'B 个人完成数为 0（B 未发布任务）' ($perB.data.completed -eq 0) "v=$($perB.data.completed)"
Test-Case 'B 积分收入包含任务收入' ($perB.data.income -eq (SqlInt "SELECT IFNULL(SUM(change_amount),0) FROM point_logs WHERE user_id=$($b.UserId) AND change_amount>0;")) "v=$($perB.data.income)"

# ---------- 全站看板 ----------
$ov = (GetJson '/api/stats/overview' $a.Session).Content | ConvertFrom-Json
$days = @($ov.data.days)
Test-Case '折线图返回 7 天' ($days.Count -eq 7) "count=$($days.Count)"
$today = (Sql "SELECT CURDATE();")
Test-Case '折线图最后一天是今天' ($days[6].date -eq $today) "last=$($days[6].date) today=$today"
$asc = $true
for ($i = 1; $i -lt 7; $i++) { if ([string]::Compare($days[$i].date, $days[$i - 1].date, [StringComparison]::Ordinal) -le 0) { $asc = $false } }
Test-Case '折线图日期升序且连续' ($asc) ($days.date -join ',')
$daySum = 0
foreach ($d in $days) { $daySum += [int]$d.count }
$dbWindow = SqlInt "SELECT COUNT(*) FROM tasks WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY);"
Test-Case '折线图合计与数据库一致' ($daySum -eq $dbWindow) "sum=$daySum db=$dbWindow"
$todayDb = SqlInt "SELECT COUNT(*) FROM tasks WHERE DATE(created_at)=CURDATE();"
Test-Case '今天的发布量与数据库一致' ([int]$days[6].count -eq $todayDb) "v=$($days[6].count) db=$todayDb"

$cats = @($ov.data.categories)
Test-Case '分类占比返回 6 类' ($cats.Count -eq 6) "count=$($cats.Count)"
Test-Case '分类顺序固定为六类' ((($cats | ForEach-Object { $_.category }) -join ',') -eq 'EXPRESS,TAKEOUT,ERRAND,MOVING,CLEANING,OTHER') (($cats | ForEach-Object { $_.category }) -join ',')
$pctSum = 0
foreach ($cat in $cats) { $pctSum += [int]$cat.percent }
Test-Case '分类百分比合计为 100' ($pctSum -eq 100) "sum=$pctSum"
$totalDb = SqlInt "SELECT COUNT(*) FROM tasks WHERE offline=0;"
Test-Case '任务总数与数据库一致' ([int]$ov.data.totalTasks -eq $totalDb) "v=$($ov.data.totalTasks) db=$totalDb"
Test-Case '取快递数量与数据库一致' ((@($cats | Where-Object { $_.category -eq 'EXPRESS' })[0].count) -eq (SqlInt "SELECT COUNT(*) FROM tasks WHERE offline=0 AND category='EXPRESS';")) 'EXPRESS mismatch'

$top = @($ov.data.top)
Test-Case 'Top 5 最多返回 5 条' ($top.Count -le 5 -and $top.Count -gt 0) "count=$($top.Count)"
$dbTop = @(Sql "SELECT t.id, COUNT(a.id) c FROM tasks t LEFT JOIN applications a ON a.task_id=t.id WHERE t.offline=0 GROUP BY t.id ORDER BY c DESC, t.id DESC LIMIT 5;")
$expect = @()
foreach ($line in $dbTop) { $p = "$line".Split("`t"); $expect += ($p[0] + ':' + $p[1]) }
$actual = @()
foreach ($row in $top) { $actual += ($row.taskId.ToString() + ':' + $row.applyCount.ToString()) }
Test-Case 'Top 5 与数据库排序一致' (($actual -join ',') -eq ($expect -join ',')) "actual=$($actual -join ',') expect=$($expect -join ',')"
$uniq = @($top | ForEach-Object { $_.taskId } | Sort-Object -Unique)
Test-Case 'Top 5 任务不重复' ($uniq.Count -eq $top.Count) "uniq=$($uniq.Count)"
$sorted = $true
for ($i = 1; $i -lt $top.Count; $i++) { if ([int]$top[$i].applyCount -gt [int]$top[$i - 1].applyCount) { $sorted = $false } }
Test-Case 'Top 5 申请人数降序' ($sorted) (($top | ForEach-Object { $_.applyCount }) -join ',')
$t1Row = @($top | Where-Object { $_.taskId -eq $t1 })
if ($t1Row.Count -eq 1) {
    Test-Case '本阶段任务一被正确统计申请人数' ($t1Row[0].applyCount -eq 2) "v=$($t1Row[0].applyCount)"
}

Write-Output ''
if ($failed -eq 0) { Write-Output '阶段 10 全部测试通过' } else { Write-Output "阶段 10 测试失败 $failed 项"; exit 1 }
