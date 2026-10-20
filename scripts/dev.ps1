param(
    [ValidateSet('run', 'test', 'package')][string]$Task = 'run',
    [string]$JdkPath = 'C:/Program Files/Java/jdk-1.8',
    [string]$MavenPath = 'D:/code/apache-maven-3.9.16'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaExecutable = Join-Path $JdkPath 'bin/java.exe'
$mavenExecutable = Join-Path $MavenPath 'bin/mvn.cmd'
if (!(Test-Path -LiteralPath $javaExecutable) -or !(Test-Path -LiteralPath $mavenExecutable)) {
    throw 'JDK or Maven not found. Set -JdkPath / -MavenPath.'
}
# 不修改操作系统全局环境。只为本次进程及它的子进程指定JDK。
$env:JAVA_HOME = $JdkPath
$env:PATH = (Join-Path $JdkPath 'bin') + ';' + $env:PATH
$env:SPRING_PROFILES_ACTIVE = 'local'
# 与IDEA直接启动共用local配置；外部环境变量仍优先，不修改系统环境。
& (Join-Path $PSScriptRoot 'prepare-local.ps1')
Push-Location $projectRoot
try {
    switch ($Task) {
        'run' { & $mavenExecutable '-B' 'spring-boot:run' }
        'test' { & $mavenExecutable '-B' 'test' }
        'package' { & $mavenExecutable '-B' 'package' }
    }
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
