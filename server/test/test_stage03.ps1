﻿# 阶段 3 自测：基础设施（响应格式、鉴权、CORS、文件服务）
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/campusquest'
$failed = 0

function Test-Case($name, $condition, $detail) {
    if ($condition) { Write-Output "PASS  $name" }
    else { Write-Output "FAIL  $name  => $detail"; $script:failed++ }
}

function Call-Api($path, $method = 'GET', $headers = @{}) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri "$base$path" -Method $method -Headers $headers -TimeoutSec 15
        return @{ Status = [int]$r.StatusCode; Body = $r.Content; Headers = $r.Headers }
    } catch {
        $rp = $_.Exception.Response
        if ($rp) {
            $sr = New-Object IO.StreamReader($rp.GetResponseStream())
            return @{ Status = [int]$rp.StatusCode; Body = $sr.ReadToEnd(); Headers = $rp.Headers }
        }
        throw
    }
}

$ping = Call-Api '/api/ping'
Test-Case 'GET /api/ping 返回 code 0' ($ping.Status -eq 200 -and ($ping.Body | ConvertFrom-Json).code -eq 0) $ping.Body

$tasks = Call-Api '/api/tasks'
$tasksJson = $tasks.Body | ConvertFrom-Json
Test-Case '未登录访问 /api/tasks 返回 401' ($tasks.Status -eq 401) $tasks.Status
Test-Case '未登录错误码 1002 且中文提示' ($tasksJson.code -eq 1002 -and $tasksJson.message -eq '登录已失效，请重新登录') $tasks.Body

$opt = Call-Api '/api/tasks' 'OPTIONS' @{ Origin = 'http://localhost:8080' }
Test-Case 'OPTIONS 预检返回 200 且带 CORS 头' ($opt.Status -eq 200 -and $opt.Headers['Access-Control-Allow-Credentials'] -eq 'true') "$($opt.Status)"

$bad = Call-Api '/uploads/not-exist.jpg'
Test-Case '不存在的图片返回 404' ($bad.Status -eq 404) $bad.Status

$traversal = Call-Api '/uploads/../../etc/passwd'
Test-Case '路径穿越返回 404' ($traversal.Status -eq 404) $traversal.Status

if ($failed -gt 0) { Write-Output "阶段3测试失败：$failed 项"; exit 1 }
Write-Output '阶段3测试全部通过'
