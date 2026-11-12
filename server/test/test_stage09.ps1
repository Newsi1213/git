﻿# 阶段 9 自测：系统通知、未读数、聊天会话、文字与图片消息、图片访问
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/campusquest'
$failed = 0
$tmp = Join-Path $env:TEMP ('cq_stage9_' + (Get-Random))
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
    $json = "{`"category`":`"$category`",`"title`":`"$title`",`"description`":`"阶段九测试任务描述`",`"points`":$points,`"locationDesc`":`"宿舍区 → 教学区`",`"timeDesc`":`"今天 20:00 前`"}"
    $j = (PostJson '/api/tasks' $json $session).Content | ConvertFrom-Json
    return $j.data.taskId
}
function LoginCookie($phone) {
    $file = Join-Path $tmp ("cookie_" + $phone + ".txt")
    $body = "{`"phone`":`"$phone`",`"password`":`"123456`"}"
    $null = & curl.exe -s -c $file -H "Content-Type: application/json" -d $body "$base/api/auth/login"
    return $file
}
function CurlChat($convId, $formArgs, $cookieFile) {
    $all = @() + $formArgs + @("$base/api/conversations/$convId/images")
    return ((& curl.exe -s -b $cookieFile -c $cookieFile @all) -join '')
}

# ---------- 测试素材 ----------
$pngPath = Join-Path $tmp 'shot.png'
$pngBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
[IO.File]::WriteAllBytes($pngPath, $pngBytes)
$fakeJpg = Join-Path $tmp 'virus.jpg'
[IO.File]::WriteAllBytes($fakeJpg, [Text.Encoding]::ASCII.GetBytes('MZ this is not an image'))
$bigPath = Join-Path $tmp 'big.png'
[IO.File]::WriteAllBytes($bigPath, (New-Object byte[] (6 * 1024 * 1024)))

$publisher = NewStudent '阶段九发布者'
$acceptor = NewStudent '阶段九接单人'
$outsider = NewStudent '阶段九路人'
$cookieB = LoginCookie $acceptor.Phone
$mark = '阶段九' + (Get-Random -Minimum 1000 -Maximum 9999)
$none = $null

# ---------- 通知：收到申请 ----------
$taskId = PublishTask $publisher.Session "$mark 帮忙取快递" 12 'EXPRESS'
$apply = PostJson "/api/tasks/$taskId/apply" '{}' $acceptor.Session
$appId = ($apply.Content | ConvertFrom-Json).data.applicationId

$countA = (GetJson '/api/notifications/unread-count' $publisher.Session).Content | ConvertFrom-Json
Test-Case 'B 申请后 A 的未读数为 1' ($countA.data.unreadCount -eq 1) "unread=$($countA.data.unreadCount)"
$listA = (GetJson '/api/notifications?onlyUnread=1&page=1&size=10' $publisher.Session).Content | ConvertFrom-Json
$n1 = $listA.data.list[0]
Test-Case '仅未读过滤返回 1 条' ($listA.data.total -eq 1) "total=$($listA.data.total)"
Test-Case '通知类型为 APPLICATION_RECEIVED' ($n1.type -eq 'APPLICATION_RECEIVED') "type=$($n1.type)"
Test-Case '通知关联任务正确' ($n1.taskId -eq $taskId) "taskId=$($n1.taskId)"
Test-Case '通知内容包含任务标题' ($n1.content -like "*$mark*") "content=$($n1.content)"
Test-Case '通知未读状态为 0' ($n1.isRead -eq 0) "isRead=$($n1.isRead)"
$meA = (GetJson '/api/auth/me' $publisher.Session).Content | ConvertFrom-Json
Test-Case '/auth/me 未读数与通知一致' ($meA.data.unreadCount -eq 1) "me=$($meA.data.unreadCount)"

# ---------- 通知：标记已读 ----------
$read = (PostJson "/api/notifications/$($n1.id)/read" '{}' $publisher.Session).Content | ConvertFrom-Json
Test-Case '标记已读返回未读数 0' ($read.data.unreadCount -eq 0) "unread=$($read.data.unreadCount)"
$listUnread = (GetJson '/api/notifications?onlyUnread=1' $publisher.Session).Content | ConvertFrom-Json
Test-Case '仅未读列表变为空' ($listUnread.data.total -eq 0) "total=$($listUnread.data.total)"
$listAll = (GetJson '/api/notifications?page=1&size=10' $publisher.Session).Content | ConvertFrom-Json
Test-Case '全部列表中该通知 isRead=1 且刷新后保持' ($listAll.data.list[0].isRead -eq 1) "isRead=$($listAll.data.list[0].isRead)"
$meA2 = (GetJson '/api/auth/me' $publisher.Session).Content | ConvertFrom-Json
Test-Case '读取后 /auth/me 未读数为 0' ($meA2.data.unreadCount -eq 0) "me=$($meA2.data.unreadCount)"

# ---------- 通知：被确认 ----------
$null = PostJson "/api/tasks/$taskId/confirm" ("{`"applicationId`":$appId}") $publisher.Session
$listB = (GetJson '/api/notifications?page=1&size=10' $acceptor.Session).Content | ConvertFrom-Json
Test-Case 'B 收到 APPLICATION_CONFIRMED 通知' ($listB.data.list[0].type -eq 'APPLICATION_CONFIRMED') "type=$($listB.data.list[0].type)"
Test-Case 'B 通知内容包含任务标题' ($listB.data.list[0].content -like "*$mark*") "content=$($listB.data.list[0].content)"

# ---------- 会话与详情 ----------
$detailA = (GetJson "/api/tasks/$taskId" $publisher.Session).Content | ConvertFrom-Json
$convId = $detailA.data.conversationId
Test-Case '发布者任务详情返回 conversationId' ($convId -gt 0) "convId=$convId"
$detailB = (GetJson "/api/tasks/$taskId" $acceptor.Session).Content | ConvertFrom-Json
Test-Case '接单者看到同一会话' ($detailB.data.conversationId -eq $convId) "convId=$($detailB.data.conversationId)"
$detailC = (GetJson "/api/tasks/$taskId" $outsider.Session).Content | ConvertFrom-Json
Test-Case '非参与方任务详情 conversationId 为 0' ($detailC.data.conversationId -eq 0) "convId=$($detailC.data.conversationId)"

$convsA = (GetJson '/api/conversations' $publisher.Session).Content | ConvertFrom-Json
Test-Case '会话列表包含新会话' (@($convsA.data | Where-Object { $_.id -eq $convId }).Count -eq 1) 'missing'
$convA = @($convsA.data | Where-Object { $_.id -eq $convId })[0]
Test-Case '会话显示对方昵称' ($convA.otherNickname -eq $acceptor.Nickname) "other=$($convA.otherNickname)"
Test-Case '会话显示任务标题' ($convA.taskTitle -like "*$mark*") "title=$($convA.taskTitle)"
Test-Case '无消息时预览为暂无消息' ($convA.lastMessage -eq '暂无消息') "preview=$($convA.lastMessage)"

# ---------- 文字消息 ----------
$textMsg = (PostJson "/api/conversations/$convId/messages" '{"content":"取件码 8-2-1023，中午前送到 3 号楼"}' $publisher.Session).Content | ConvertFrom-Json
Test-Case '发送文字成功且类型 TEXT' ($textMsg.data.msgType -eq 'TEXT') "type=$($textMsg.data.msgType)"
Test-Case '发送者视角 mine=true' ($textMsg.data.mine -eq $true) "mine=$($textMsg.data.mine)"
$msgListB = (GetJson "/api/conversations/$convId/messages?page=1&size=30" $acceptor.Session).Content | ConvertFrom-Json
Test-Case '对方能读到文字消息' (@($msgListB.data.list | Where-Object { $_.content -like '*8-2-1023*' }).Count -eq 1) 'missing'
Test-Case '对方视角 mine=false' ($msgListB.data.list[0].mine -eq $false) "mine=$($msgListB.data.list[0].mine)"
Test-Case '消息带发送者昵称' ($msgListB.data.list[0].senderNickname -eq $publisher.Nickname) "nick=$($msgListB.data.list[0].senderNickname)"

# ---------- 图片消息 ----------
$imgJson = CurlChat $convId @('-F', "file=@$($pngPath.Replace('\','/'));type=image/png") $cookieB | ConvertFrom-Json
Test-Case '发送图片成功' ($imgJson.code -eq 0) "code=$($imgJson.code) msg=$($imgJson.message)"
Test-Case '图片消息类型 IMAGE' ($imgJson.data.msgType -eq 'IMAGE') "type=$($imgJson.data.msgType)"
$imgPath = $imgJson.data.imagePath
Test-Case '图片路径为相对路径且文件名为 UUID' ($imgPath -match '^uploads/\d{4}/\d{2}/\d{2}/[0-9a-f]{32}\.png$') "path=$imgPath"
$imgDisk = Join-Path 'E:\MyApp' ($imgPath.Replace('/','\'))
Test-Case '图片真实落盘到 uploads' (Test-Path $imgDisk) "disk=$imgDisk"
Test-Case '数据库只存相对路径' ((Sql "SELECT image_path FROM messages WHERE id=$($imgJson.data.id);") -eq $imgPath) 'db mismatch'
$imgHttp = GetJson "/$imgPath" $none
Test-Case '图片可通过 /uploads 访问' ($imgHttp.StatusCode -eq 200) "status=$($imgHttp.StatusCode)"
Test-Case '图片响应 Content-Type 为 image/png' ($imgHttp.Headers['Content-Type'] -like 'image/png*') "ct=$($imgHttp.Headers['Content-Type'])"

$pollA = (GetJson "/api/conversations/$convId/messages?afterId=$($textMsg.data.id)&size=50" $publisher.Session).Content | ConvertFrom-Json
Test-Case '增量轮询能拉到新图片消息' (@($pollA.data.list | Where-Object { $_.msgType -eq 'IMAGE' }).Count -eq 1) 'missing'
Test-Case '增量轮询 lastId 前进' ($pollA.data.lastId -eq $imgJson.data.id) "lastId=$($pollA.data.lastId)"

# ---------- 非参与方被拒 ----------
$denyRead = TryCall { GetJson "/api/conversations/$convId/messages" $outsider.Session }
$denyReadJson = $denyRead.Body | ConvertFrom-Json
Test-Case '非参与方读消息返回 1003' ($denyReadJson.code -eq 1003 -and $denyRead.Status -eq 403) "code=$($denyReadJson.code) status=$($denyRead.Status)"
$denySend = TryCall { PostJson "/api/conversations/$convId/messages" '{"content":"我不在该会话"}' $outsider.Session }
$denySendJson = $denySend.Body | ConvertFrom-Json
Test-Case '非参与方发消息返回 1003' ($denySendJson.code -eq 1003 -and $denySend.Status -eq 403) "code=$($denySendJson.code) status=$($denySend.Status)"

# ---------- 上传校验 ----------
$bigJson = CurlChat $convId @('-F', "file=@$($bigPath.Replace('\','/'));type=image/png") $cookieB | ConvertFrom-Json
Test-Case '6MB 图片被拒且提示不超过 5MB' ($bigJson.code -eq 4002 -and $bigJson.message -like '*5MB*') "code=$($bigJson.code) msg=$($bigJson.message)"
$fakeJson = CurlChat $convId @('-F', "file=@$($fakeJpg.Replace('\','/'));type=image/jpeg") $cookieB | ConvertFrom-Json
Test-Case 'exe 改名 .jpg 仍被拦下（4001）' ($fakeJson.code -eq 4001) "code=$($fakeJson.code) msg=$($fakeJson.message)"
$cnJson = CurlChat $convId @('-F', "file=@$($pngPath.Replace('\','/'));type=image/png;filename=中文截图.png") $cookieB | ConvertFrom-Json
Test-Case '中文文件名上传成功' ($cnJson.code -eq 0) "code=$($cnJson.code) msg=$($cnJson.message)"
Test-Case '中文文件名落盘为 UUID 且无乱码' ($cnJson.data.imagePath -match '^uploads/\d{4}/\d{2}/\d{2}/[0-9a-f]{32}\.png$') "path=$($cnJson.data.imagePath)"

# ---------- 消息分页顺序 ----------
$pageMsg = (GetJson "/api/conversations/$convId/messages?page=1&size=2" $acceptor.Session).Content | ConvertFrom-Json
Test-Case '历史消息按 id 升序返回' ($pageMsg.data.list[0].id -lt $pageMsg.data.list[1].id) "ids=$($pageMsg.data.list.id -join ',')"
Test-Case '消息总数与数据库一致' ($pageMsg.data.total -eq (SqlInt "SELECT COUNT(*) FROM messages WHERE conversation_id=$convId;")) "total=$($pageMsg.data.total)"

# ---------- 会话排序 ----------
$taskId2 = PublishTask $publisher.Session "$mark 第二单" 10 'ERRAND'
$apply2 = PostJson "/api/tasks/$taskId2/apply" '{}' $acceptor.Session
$appId2 = ($apply2.Content | ConvertFrom-Json).data.applicationId
$null = PostJson "/api/tasks/$taskId2/confirm" ("{`"applicationId`":$appId2}") $publisher.Session
$detail2 = (GetJson "/api/tasks/$taskId2" $publisher.Session).Content | ConvertFrom-Json
$convId2 = $detail2.data.conversationId
$convsOrder = (GetJson '/api/conversations' $publisher.Session).Content | ConvertFrom-Json
Test-Case '新会话按创建时间排在最前' (@($convsOrder.data)[0].id -eq $convId2) "first=$(@($convsOrder.data)[0].id)"
Start-Sleep -Milliseconds 1200
$null = PostJson "/api/conversations/$convId/messages" '{"content":"第二单聊一句，把老会话顶上来"}' $publisher.Session
$convsOrder2 = (GetJson '/api/conversations' $publisher.Session).Content | ConvertFrom-Json
Test-Case '最新消息时间决定会话排序' (@($convsOrder2.data)[0].id -eq $convId) "first=$(@($convsOrder2.data)[0].id)"

# ---------- 结算与评价通知 ----------
$deliver = PostJson "/api/tasks/$taskId/deliver" '{}' $acceptor.Session
Test-Case '接单者标记已送达成功' ((($deliver.Content | ConvertFrom-Json).code) -eq 0) "body=$($deliver.Content)"
$complete = PostJson "/api/tasks/$taskId/complete" '{}' $publisher.Session
Test-Case '发布者确认完成成功' ((($complete.Content | ConvertFrom-Json).code) -eq 0) "body=$($complete.Content)"
$settleA = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($publisher.UserId) AND type='SETTLEMENT' AND related_task_id=$taskId;"
$settleB = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($acceptor.UserId) AND type='SETTLEMENT' AND related_task_id=$taskId;"
Test-Case '结算后双方各收到 SETTLEMENT 通知' ($settleA -eq 1 -and $settleB -eq 1) "A=$settleA B=$settleB"
$review = PostJson "/api/tasks/$taskId/review" '{"rating":"GOOD","comment":"合作顺利，送得很快"}' $acceptor.Session
Test-Case '接单者提交好评成功' ((($review.Content | ConvertFrom-Json).code) -eq 0) "body=$($review.Content)"
$reviewNotify = SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($publisher.UserId) AND type='REVIEW_RECEIVED' AND related_task_id=$taskId;"
Test-Case '发布者收到 REVIEW_RECEIVED 通知' ($reviewNotify -eq 1) "count=$reviewNotify"

# ---------- 通知分页 ----------
$pageNotify = (GetJson '/api/notifications?page=1&size=2' $publisher.Session).Content | ConvertFrom-Json
Test-Case '通知分页 size 生效' (@($pageNotify.data.list).Count -le 2) "count=$(@($pageNotify.data.list).Count)"
Test-Case '通知总数与数据库一致' ($pageNotify.data.total -eq (SqlInt "SELECT COUNT(*) FROM notifications WHERE user_id=$($publisher.UserId);")) "total=$($pageNotify.data.total)"

Write-Output ''
if ($failed -eq 0) { Write-Output '阶段 9 全部测试通过' } else { Write-Output "阶段 9 测试失败 $failed 项"; exit 1 }
