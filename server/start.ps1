$ErrorActionPreference = 'Stop'

# 配置优先取环境变量（由 server\cq-env.bat 统一注入），单独使用时用默认值
$tomcatHome = if ($env:CQ_TOMCAT_HOME) { $env:CQ_TOMCAT_HOME } else { 'E:\MyApp\tools\tomcat9' }
$javaHome   = if ($env:CQ_JAVA_HOME)   { $env:CQ_JAVA_HOME }   else { 'E:\0_workspace\java11' }
$appUrl     = if ($env:CQ_APP_URL)     { $env:CQ_APP_URL }     else { 'http://localhost:8080/campusquest/login.html' }

$env:CATALINA_HOME = $tomcatHome
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = $javaHome }
$env:CATALINA_OPTS = '-Dfile.encoding=UTF-8'

function Test-AppUp {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $appUrl -TimeoutSec 5
        return ([int]$r.StatusCode -eq 200)
    } catch {
        return $false
    }
}

# 先看是不是已经在跑
if (Test-AppUp) {
    Write-Output "already_running: $appUrl"
    exit 0
}

# 用 catalina.bat run 起一个独立窗口，比 startup.bat 更可靠
$p = Start-Process -FilePath "$tomcatHome\bin\catalina.bat" -ArgumentList @('run') `
    -WorkingDirectory "$tomcatHome\bin" -PassThru -WindowStyle Minimized

# 最多等 60 秒直到页面可访问
$up = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    if (Test-AppUp) { $up = $true; break }
    if ($p.HasExited) { Write-Error "Tomcat exited prematurely (code=$($p.ExitCode))"; exit 1 }
}

if ($up) {
    Write-Output "up_after=$($i*2+2)s pid=$($p.Id) url=$appUrl"
} else {
    Write-Error "Tomcat did not become ready within 60 seconds"
    exit 1
}
