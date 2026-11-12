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
$appId = Sql "SELECT id FROM applications WHERE task_id=6 AND status='PENDING' LIMIT 1;"
Post "$b/tasks/6/confirm" ("{`"applicationId`":$appId}") $s1
Post "$b/tasks/6/deliver" '{}' $s2
Post "$b/tasks/6/complete" '{}' $s1
Sql "SELECT CONCAT(status, ':', acceptor_id) FROM tasks WHERE id=6;"
