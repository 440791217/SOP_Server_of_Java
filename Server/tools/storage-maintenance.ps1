param([ValidateSet('verify','apply-schema','apply-frame-rates','apply-frame-statistics','apply-device-settings','apply-execution-clock','migrate-files')][string]$Mode = 'verify')
$ErrorActionPreference = 'Stop'
$serverDirectory = Split-Path -Parent $PSScriptRoot
$workspaceDirectory = Split-Path -Parent (Split-Path -Parent $serverDirectory)
$cacheDirectory = Join-Path $workspaceDirectory '.maven-cache'
$logDirectory = Join-Path $workspaceDirectory 'logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$dependencies = @('com/mysql/mysql-connector-j','org/yaml/snakeyaml','org/springframework/spring-web','org/springframework/spring-core','org/springframework/spring-beans','org/springframework/spring-context','org/springframework/spring-jcl')
$classpath = @((Join-Path $serverDirectory 'target/classes'))
foreach ($dependency in $dependencies) {
  $folder = Join-Path $cacheDirectory $dependency
  if (Test-Path -LiteralPath $folder) {
    $jar = Get-ChildItem -LiteralPath $folder -Recurse -Filter '*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($jar) { $classpath += $jar.FullName }
  }
}
$log = Join-Path $logDirectory ('sop-storage-' + $Mode + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
Push-Location $serverDirectory
try { & $javaExecutable --class-path ($classpath -join ';') (Join-Path $PSScriptRoot 'StorageMaintenance.java') ('--' + $Mode) *> $log; $result = $LASTEXITCODE; Get-Content -LiteralPath $log; if ($result -ne 0) { throw "Storage maintenance failed; see $log" } }
finally { Pop-Location }
