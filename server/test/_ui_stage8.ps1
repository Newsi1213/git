﻿function Post($u, $b, $s) {
    $a = @{ UseBasicParsing = $true; Uri = $u; Method = 'POST'; ContentType = 'application/json;charset=UTF-8'; Body = [Text.Encoding]::UTF8.GetBytes($b); TimeoutSec = 10 }
    if ($s) { $a.WebSession = $s }
    return (Invoke-WebRequest @a).Content
}
function Sql($sql) {
    $env:MYSQL_PWD = 'root'
    return (mysql -u root -N -B --default-character-set=utf8mb4 -e "USE campus_quest; $sql" 2>$null)
}
$b = 'http://localhost:8080/campusquest/api'
$s1 = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$s2 = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Post "$b/auth/login" '{"phone":"13700000001","password":"123456"}' $s1
$null = Post "$b/auth/login" '{"phone":"13700000002","password":"123456"}' $s2
$t = (Post "$b/tasks" '{"category":"ERRAND","title":"界面验收：代取一份打印材料","description":"图书馆一楼打印店取材料送到 2 号楼","points":10,"locationDesc":"图书馆 → 2 号楼","timeDesc":"今天 20:00 前"}' $s1) | ConvertFrom-Json
$taskId = $t.data.taskId
$null = Post "$b/tasks/$taskId/apply" '{}' $s2
$appId = Sql "SELECT id FROM applications WHERE task_id=$taskId AND status='PENDING' LIMIT 1;"
$null = Post "$b/tasks/$taskId/confirm" ("{`"applicationId`":$appId}") $s1
$null = Sql "UPDATE tasks SET accepted_at = DATE_SUB(NOW(), INTERVAL 45 MINUTE) WHERE id=$taskId;"
$null = Post "$b/tasks/$taskId/abandon" '{}' $s2
"taskId=$taskId appId=$appId credit=" + (Sql "SELECT credit FROM users WHERE phone='13700000002';")
