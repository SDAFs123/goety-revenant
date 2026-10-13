# ============================================================================
#  构建 + 部署 + 清 Lightspeed 缓存
# ----------------------------------------------------------------------------
#  这个构建环境有三分之一的概率"卡死"（日志停在 fork 那句，几个小时不动）。
#  真因是**输出句柄打架**：同步执行 cmd /c "gradlew ... > log 2>&1" 时，
#  父进程若也被捕获着 stdout，子进程的重定向会和它抢同一个句柄。
#  解法两条，都在下面：
#     ① 用 Start-Process -NoNewWindow **异步启动**，自己轮询日志（17 秒 vs 15 分钟）
#     ② 它仍然是概率性的 —— 所以**自动重试三次**，别让人工去点
#
#  另一个坑：换了同名 jar 但游戏里没变化 —— 整合包里的 lightspeed 把每个 jar 的
#  文件清单缓存了起来。每次部署后必须删掉那个缓存目录。
# ============================================================================

$ErrorActionPreference = "Continue"
$root  = "C:\Users\Administrator\Desktop\新建文件夹 (2)\GoetyRevenant"
$mods  = "D:\PCL 正式版 2.12.6\.minecraft\versions\goety\mods"
$cache = "D:\PCL 正式版 2.12.6\.minecraft\versions\goety\lightspeed-cache"
$javaHome = "C:\Users\Administrator\.gradle\jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.19+10"

Set-Location $root
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;" + $env:Path
Remove-Item Env:HTTP_PROXY,Env:HTTPS_PROXY,Env:http_proxy,Env:https_proxy,Env:GRADLE_OPTS -ErrorAction SilentlyContinue

function Invoke-Attempt {
    # 每次开工前都清一遍：卡死的构建会留下挂住的 java 和 daemon 注册记录
    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 4
    Remove-Item "$env:USERPROFILE\.gradle\daemon" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item build.log -ErrorAction SilentlyContinue

    # ① 异步启动，不接管它的输出
    Start-Process -FilePath "cmd.exe" -NoNewWindow -WorkingDirectory $root `
        -ArgumentList "/c gradlew.bat build --no-daemon --console=plain --offline > build.log 2>&1"

    $deadline = (Get-Date).AddMinutes(6)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if (-not (Test-Path build.log)) { continue }
        $t = Get-Content build.log -Raw -ErrorAction SilentlyContinue
        if ($t -match "BUILD SUCCESSFUL") { return "ok" }
        if ($t -match "BUILD FAILED") { return "failed" }
    }
    return "timeout"
}

$result = "timeout"
for ($attempt = 1; $attempt -le 3; $attempt++) {
    Write-Output "[第 $attempt 次构建]"
    $result = Invoke-Attempt
    Write-Output ("  -> " + $result)
    if ($result -eq "ok" -or $result -eq "failed") { break }
    Write-Output "  （卡住了，重来）"
}

if ($result -ne "ok") {
    Select-String -Path build.log -Pattern "error:|错误" -Encoding UTF8 |
        Select-Object -First 15 | ForEach-Object { $_.Line }
    Write-Output "构建失败，没有部署。"
    exit 1
}

Copy-Item "build\libs\goety_revenant-0.1.0.jar" "$mods\goety_revenant-0.1.0.jar" -Force
Get-Item "$mods\goety_revenant-0.1.0.jar" | ForEach-Object { "已部署: " + $_.Length + " 字节  " + $_.LastWriteTime }

if (Test-Path $cache) {
    Remove-Item $cache -Recurse -Force -ErrorAction SilentlyContinue
    if (Test-Path $cache) { Write-Output "[警告] Lightspeed 缓存没删掉 —— 游戏可能正在运行" }
    else { Write-Output "已清 Lightspeed 资源索引缓存" }
}
