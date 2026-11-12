$ErrorActionPreference = 'Stop'

# 配置优先取环境变量（由 server\cq-env.bat 统一注入），单独使用时用默认值
$tomcatHome = if ($env:CQ_TOMCAT_HOME) { $env:CQ_TOMCAT_HOME } else { 'E:\MyApp\tools\tomcat9' }
$javaHome   = if ($env:CQ_JAVA_HOME)   { $env:CQ_JAVA_HOME }   else { 'E:\0_workspace\java11' }
$appUrl     = if ($env:CQ_APP_URL)     { $env:CQ_APP_URL }     else { 'http://localhost:8080/campusquest/login.html' }

$env:CATALINA_HOME = $tomcatHome
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = $javaHome }

function Test-AppUp {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $appUrl -TimeoutSec 5
        return ([int]$r.StatusCode -eq 200)
    } catch {
        return $false
    }
}

# 先看是不是已经停了
if (-not (Test-AppUp)) {
    Write-Output 'already_stopped'
    exit 0
}

& "$tomcatHome\bin\shutdown.bat" | Out-Null

# 最多等 20 秒直到页面不可访问
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 2
    if (-not (Test-AppUp)) { break }
}

# shutdown.bat 偶发停不干净，兜底结束残留的 Tomcat java 进程
if (Test-AppUp) {
    $javaProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*catalina*' }
    if ($javaProcs) {
        foreach ($jp in $javaProcs) { Stop-Process -Id $jp.ProcessId -Force -ErrorAction SilentlyContinue }
        Write-Output "force_killed_java_pids=$(@($javaProcs.ProcessId) -join ',')"
    }
    Start-Sleep -Seconds 2
}

if (-not (Test-AppUp)) {
    Write-Output "down_after=$($i*2+2)s"
} else {
    Write-Error "Tomcat still reachable at $appUrl after stop attempt"
    exit 1
}
