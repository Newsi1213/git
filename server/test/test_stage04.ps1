﻿# 阶段 4 自测：账号模块（注册、登录、会话、角色、退出）
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/campusquest'
$failed = 0

function Test-Case($name, $condition, $detail) {
    if ($condition) { Write-Output "PASS  $name" }
    else { Write-Output "FAIL  $name  => $detail"; $script:failed++ }
}
function PostJson($path, $json, $session) {
    $args = @{ UseBasicParsing = $true; Uri = "$base$path"; Method = 'POST'; ContentType = 'application/json;charset=UTF-8'; Body = [Text.Encoding]::UTF8.GetBytes($json); TimeoutSec = 15 }
    if ($session) { $args.WebSession = $session }
    return Invoke-WebRequest @args
}
function GetJson($path, $session) {
    $args = @{ UseBasicParsing = $true; Uri = "$base$path"; Method = 'GET'; TimeoutSec = 15 }
    if ($session) { $args.WebSession = $session }
    return Invoke-WebRequest @args
}
function TryCall($scriptBlock) {
    try { $r = & $scriptBlock; return @{ Status = [int]$r.StatusCode; Body = $r.Content } }
    catch {
        $rp = $_.Exception.Response
        if ($rp) {
            $sr = New-Object IO.StreamReader($rp.GetResponseStream())
            return @{ Status = [int]$rp.StatusCode; Body = $sr.ReadToEnd() }
        }
        throw
    }
}
function Sql($sql) {
    $env:MYSQL_PWD = 'root'
    return (mysql -u root -N -B --default-character-set=utf8mb4 -e "USE campus_quest; $sql" 2>$null)
}

$phone = '139' + (Get-Random -Minimum 10000000 -Maximum 99999999)
$reg = TryCall { PostJson '/api/auth/register' ("{`"phone`":`"$phone`",`"nickname`":`"阶段四测试`",`"password`":`"123456`"}") $null }
$regJson = $reg.Body | ConvertFrom-Json
Test-Case '注册成功返回 code 0' ($reg.Status -eq 200 -and $regJson.code -eq 0 -and $regJson.data.userId -gt 0) $reg.Body

$row = Sql "SELECT points, credit FROM users WHERE phone='$phone';"
Test-Case '注册即送 100 积分、信用 100' ($row -match '^100\t100$') $row
$plog = Sql "SELECT COUNT(*) FROM point_logs p JOIN users u ON p.user_id=u.id WHERE u.phone='$phone' AND p.type='REGISTER' AND p.change_amount=100 AND p.balance_after=100;"
Test-Case '写入注册赠送积分流水' ($plog -eq '1') $plog
$clog = Sql "SELECT COUNT(*) FROM credit_logs c JOIN users u ON c.user_id=u.id WHERE u.phone='$phone' AND c.reason='INIT' AND c.change_amount=100;"
Test-Case '写入初始信用流水' ($clog -eq '1') $clog

$dup = TryCall { PostJson '/api/auth/register' ("{`"phone`":`"$phone`",`"nickname`":`"重复`",`"password`":`"123456`"}") $null }
Test-Case '重复注册返回 2001' (($dup.Body | ConvertFrom-Json).code -eq 2001) $dup.Body

$bad = TryCall { PostJson '/api/auth/register' '{"phone":"12345","nickname":"短","password":"123456"}' $null }
Test-Case '手机号格式错误返回 2002' (($bad.Body | ConvertFrom-Json).code -eq 2002) $bad.Body

$wrong = TryCall { PostJson '/api/auth/login' ("{`"phone`":`"$phone`",`"password`":`"bad`"}") $null }
Test-Case '密码错误返回 2003 且不泄露账号是否存在' (($wrong.Body | ConvertFrom-Json).code -eq 2003) $wrong.Body

$student = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = TryCall { PostJson '/api/auth/login' ("{`"phone`":`"$phone`",`"password`":`"123456`"}") $student }
$loginJson = $login.Body | ConvertFrom-Json
Test-Case '登录成功返回学生身份与昵称' ($login.Status -eq 200 -and $loginJson.code -eq 0 -and $loginJson.data.role -eq 'STUDENT' -and $loginJson.data.nickname -eq '阶段四测试') $login.Body

$me = TryCall { GetJson '/api/auth/me' $student }
$meJson = $me.Body | ConvertFrom-Json
Test-Case '登录后可读取当前用户（手机号脱敏）' ($meJson.code -eq 0 -and $meJson.data.phoneMasked -match '^\d{3}\*{4}\d{4}$' -and $meJson.data.points -eq 100) $me.Body

$anon = TryCall { GetJson '/api/auth/me' $null }
Test-Case '未登录读取用户返回 401/1002' ($anon.Status -eq 401 -and ($anon.Body | ConvertFrom-Json).code -eq 1002) $anon.Body

$forbidden = TryCall { GetJson '/api/admin/appeals' $student }
Test-Case '学生访问管理端接口返回 403/1003' ($forbidden.Status -eq 403 -and ($forbidden.Body | ConvertFrom-Json).code -eq 1003) $forbidden.Body

$admin = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$adminLogin = TryCall { PostJson '/api/auth/admin/login' '{"username":"admin","password":"admin123"}' $admin }
Test-Case '管理员登录成功' (($adminLogin.Body | ConvertFrom-Json).data.role -eq 'ADMIN') $adminLogin.Body
$adminBad = TryCall { PostJson '/api/auth/admin/login' '{"username":"admin","password":"x"}' $null }
Test-Case '管理员密码错误返回 2004' (($adminBad.Body | ConvertFrom-Json).code -eq 2004) $adminBad.Body

$out = TryCall { PostJson '/api/auth/logout' '{}' $student }
Test-Case '学生退出成功' (($out.Body | ConvertFrom-Json).code -eq 0) $out.Body
$afterOut = TryCall { GetJson '/api/auth/me' $student }
Test-Case '退出后会话失效返回 401' ($afterOut.Status -eq 401) $afterOut.Body

$adminOut = TryCall { PostJson '/api/auth/logout' '{}' $admin }
Test-Case '管理员退出成功' (($adminOut.Body | ConvertFrom-Json).code -eq 0) $adminOut.Body

$loginHtml = [IO.File]::ReadAllText('E:\MyApp\server\src\main\webapp\login.html', [Text.Encoding]::UTF8)
Test-Case '登录页无外部 http 资源引用' (-not ($loginHtml -match 'https?://')) '发现外链'
Test-Case '本地依赖文件齐全' ((Test-Path 'E:\MyApp\server\src\main\webapp\assets\lib\vue.min.js') -and (Test-Path 'E:\MyApp\server\src\main\webapp\assets\lib\element-ui\index.js') -and (Test-Path 'E:\MyApp\server\src\main\webapp\assets\lib\axios.min.js')) '缺文件'

if ($failed -gt 0) { Write-Output "阶段4测试失败：$failed 项"; exit 1 }
Write-Output '阶段4测试全部通过'
