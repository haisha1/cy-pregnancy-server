param([string]$Username = 'root', [string]$Password = 'root')
$ErrorActionPreference = 'Stop'
# 只创建当前项目库，不删除、不清空、不修改其他库。建表由Flyway在启动时执行。
$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $Password
    & mysql.exe -h localhost -P 3306 -u $Username -e 'CREATE DATABASE IF NOT EXISTS cy_pregnancy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
    if ($LASTEXITCODE -ne 0) { throw 'Database initialization failed.' }
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $previousPassword }
}
