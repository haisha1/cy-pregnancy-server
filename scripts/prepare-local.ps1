param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$localDirectory = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Path $localDirectory -Force | Out-Null
$secretFile = Join-Path $localDirectory 'jwt-secret.txt'
if (!(Test-Path -LiteralPath $secretFile)) {
    $randomBytes = New-Object byte[] 48
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
    [IO.File]::WriteAllText($secretFile, [Convert]::ToBase64String($randomBytes))
}
function Read-LocalSecret([string]$Path) {
    $value = [IO.File]::ReadAllText($Path).Trim()
    $decoded = $null
    try { $decoded = [Convert]::FromBase64String($value) } catch { throw 'Invalid local secret encoding.' }
    if ($decoded.Length -lt 48 -or $value -match '[\r\n]') { throw 'Invalid local secret length.' }
    return $value
}
# 用户端签名密钥独立保存，重复执行不轮换，不使用管理端JWT密钥。
$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add('JWT_SECRET=' + (Read-LocalSecret $secretFile))
# 只读互通凭据以管理端文件为准，两端IDEA启动与脚本启动保持一致。
$adminRoot = Join-Path $workspaceRoot 'cy-pregnancy-admin/server'
$adminPrepare = Join-Path $adminRoot 'scripts/prepare-local.ps1'
if (Test-Path -LiteralPath $adminPrepare) {
    & $adminPrepare
    $lines.Add('ADMIN_READ_KEY=' + (Read-LocalSecret (Join-Path $adminRoot '.local/user-read-key.txt')))
    $lines.Add('CONTENT_READ_KEY=' + (Read-LocalSecret (Join-Path $adminRoot '.local/content-read-key.txt')))
}
$configFile = Join-Path $localDirectory 'application-secrets.properties'
$temporaryFile = Join-Path $localDirectory ('secrets-' + [Guid]::NewGuid().ToString('N') + '.tmp')
try {
    [IO.File]::WriteAllLines($temporaryFile, $lines, [Text.Encoding]::ASCII)
    if (Test-Path -LiteralPath $configFile) { [IO.File]::Replace($temporaryFile, $configFile, [NullString]::Value) }
    else { [IO.File]::Move($temporaryFile, $configFile) }
} finally {
    if (Test-Path -LiteralPath $temporaryFile) { Remove-Item -LiteralPath $temporaryFile }
}
Write-Output 'Local user-server configuration prepared. Secret values were not printed.'
