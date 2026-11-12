$ErrorActionPreference = 'Stop'
$root = 'E:\MyApp'
$src = Join-Path $root 'server\src\main\java'
$webapp = Join-Path $root 'server\src\main\webapp'
$classes = Join-Path $webapp 'WEB-INF\classes'
$servletApi = Join-Path $root 'tools\tomcat9\lib\servlet-api.jar'
$libJars = Join-Path $webapp 'WEB-INF\lib\*'

if (-not (Test-Path $servletApi)) { Write-Error "servlet-api.jar not found: $servletApi" }
if (-not (Test-Path $classes)) { New-Item -ItemType Directory -Force -Path $classes | Out-Null }

$files = @(Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($files.Count -eq 0) { Write-Output 'No java sources, skip.'; exit 0 }

$argFile = Join-Path $env:TEMP 'cq_javac_sources.txt'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($argFile, $files, $utf8NoBom)

& javac -encoding UTF-8 -source 11 -target 11 -cp "$servletApi;$libJars" -d $classes "@$argFile"
if ($LASTEXITCODE -ne 0) { Write-Error "Compile failed, exit code $LASTEXITCODE" }
Write-Output "Build ok: $($files.Count) java files -> $classes"
