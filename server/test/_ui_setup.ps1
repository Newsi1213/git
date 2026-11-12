﻿function Post($u, $b, $s) {
    $a = @{ UseBasicParsing = $true; Uri = $u; Method = 'POST'; ContentType = 'application/json;charset=UTF-8'; Body = [Text.Encoding]::UTF8.GetBytes($b); TimeoutSec = 10 }
    if ($s) { $a.WebSession = $s }
    return Invoke-WebRequest @a
}
$b = 'http://localhost:8080/campusquest/api'
$s1 = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$s2 = New-Object Microsoft.PowerShell.Commands.WebRequestSession
try { $null = Post "$b/auth/register" '{"phone":"13700000001","nickname":"界面验收员","password":"123456"}' $null } catch {}
try { $null = Post "$b/auth/register" '{"phone":"13700000002","nickname":"界面申请人","password":"123456"}' $null } catch {}
$null = Post "$b/auth/login" '{"phone":"13700000001","password":"123456"}' $s1
$null = Post "$b/auth/login" '{"phone":"13700000002","password":"123456"}' $s2
$t = (Post "$b/tasks" '{"category":"EXPRESS","title":"界面验收：取快递到 5 号楼","description":"东门驿站取件送到 5 号楼 301","points":20,"locationDesc":"东门菜鸟驿站 → 5 号楼 301","timeDesc":"今天 18:00 前"}' $s1).Content | ConvertFrom-Json
$r = (Post "$b/tasks/$($t.data.taskId)/apply" '{}' $s2).Content | ConvertFrom-Json
"taskId=$($t.data.taskId) application=$($r.data.applicationId) code=$($r.code)"
