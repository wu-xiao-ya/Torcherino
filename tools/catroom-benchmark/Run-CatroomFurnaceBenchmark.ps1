[CmdletBinding()]
param(
    [Parameter()]
    [string[]]$RunCommands = @('torcherino-bench run furnace-suite'),

    [Parameter()]
    [string[]]$ExportCommands = @('torcherino-bench export'),

    [Parameter()]
    [string]$CompletionPattern = 'Torcherino furnace suite completed',

    [Parameter()]
    [string]$CleanroomCoreSha256 = 'DDDF6FE1F26FF9CA6EE70483C5B2B72C653E6FCF1573CEF0E91E3D7AADDF8962',

    [Parameter()]
    [string]$CleanroomInstallerJar = '',

    [Parameter()]
    [string]$BenchmarkServerRoot = '',

    [Parameter()]
    [string]$OutputRoot = '',

    [Parameter()]
    [string]$Torcherino76Jar = 'D:\我的世界\星光科技-Starlight Tech\versions\1.12.2-Cleanroom_0.5.14-alpha\mods\[加速火把] torcherino-7.6.jar',

    [Parameter()]
    [string]$CurrentJar = '',

    [Parameter()]
    [string]$WorktreeCommit = 'c8bcaae',

    [Parameter()]
    [string]$WorktreeBuildClosureCommit = '93a16c9',

    [Parameter()]
    [string]$BuildInfrastructureCommit = 'e66d257',

    [Parameter()]
    [string]$WorktreeRoot = '',

    [Parameter()]
    [string[]]$IncludePaths = @(),

    [Parameter()]
    [string[]]$AdditionalModJars = @(),

    [Parameter()]
    [string[]]$EnderIoProbeModJars = @(),

    [Parameter()]
    [string[]]$ThermalProbeModJars = @(),

    [Parameter()]
    [switch]$SkipEnderIoEnvironmentProbe,

    [Parameter()]
    [switch]$SkipThermalEnvironmentProbe,

    [Parameter()]
    [string]$TargetModsRoot = 'D:\我的世界\星光科技-Starlight Tech\versions\1.12.2-Cleanroom_0.5.14-alpha\mods',

    [Parameter()]
    [string]$HarnessJar = '',

    [Parameter()]
    [string]$JavaHome = '',

    [Parameter()]
    [string]$RconHost = '127.0.0.1',

    [Parameter()]
    [int]$ServerPort = 25565,

    [Parameter()]
    [int]$RconPort = 25575,

    [Parameter()]
    [string]$RconPassword = '',

    [Parameter()]
    [int]$StartupTimeoutSeconds = 600,

    [Parameter()]
    [int]$BenchmarkTimeoutSeconds = 3600,

    [Parameter()]
    [int]$RconTimeoutSeconds = 15,

    [Parameter()]
    [Alias('Candidate')]
    [ValidateSet('all', '7.6', 'scheduler', 'current')]
    [string]$CandidateSelection = 'all',

    [Parameter()]
    [ValidateSet('vanilla', 'thermal1', 'thermal', 'thermal80', 'enderio')]
    [string[]]$BenchmarkLayers = @('vanilla', 'thermal', 'enderio'),

    [Parameter()]
    [ValidateSet('diagnostic', 'timing')]
    [string]$BenchmarkMode = 'diagnostic',

    [switch]$DryRun,

    [switch]$Preflight,

    [switch]$KeepWorktree
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Status {
    param([string]$Message)
    Write-Host "[catroom-benchmark] $Message"
}

function Get-RepoRoot {
    $candidate = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')
    return $candidate.Path
}

function Get-AbsolutePath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Base
    )

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $Base $Path))
}

function Test-PathUnderRoot {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'
    $pathFull = [System.IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
    return $pathFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)
}

function Assert-PathUnderRoot {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Label
    )

    if (-not (Test-PathUnderRoot -Root $Root -Path $Path)) {
        throw "$Label path '$Path' is outside the allowed root '$Root'"
    }
}

function Ensure-Directory {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Remove-DirectorySafe {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Label
    )

    Assert-PathUnderRoot -Root $Root -Path $Path -Label $Label
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$Quiet
    )

    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& git @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if (-not $Quiet) {
        $output | ForEach-Object { Write-Host $_ }
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Remove-GitWorktreeSafe {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRootValue,
        [Parameter(Mandatory = $true)][string]$WorktreeRootValue,
        [Parameter(Mandatory = $true)][string]$WorktreePath
    )

    Assert-PathUnderRoot -Root $WorktreeRootValue -Path $WorktreePath -Label 'worktree'
    if (-not (Test-Path -LiteralPath $WorktreePath)) {
        return
    }
    $removeResult = Invoke-Git -Arguments @(
        '-C', $RepoRootValue, 'worktree', 'remove', '--force', $WorktreePath
    )
    if ($removeResult.ExitCode -ne 0 -and (Test-Path -LiteralPath $WorktreePath)) {
        Remove-DirectorySafe -Root $WorktreeRootValue -Path $WorktreePath -Label 'worktree'
    }
    [void](Invoke-Git -Arguments @('-C', $RepoRootValue, 'worktree', 'prune'))
}

function Get-BenchmarkJavaHome {
    param([string]$PreferredHome)

    if ($PreferredHome) {
        $candidate = Get-AbsolutePath -Path $PreferredHome -Base (Get-Location).Path
        $javaExe = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $javaExe)) {
            throw "JAVA_HOME '$PreferredHome' does not contain bin\\java.exe"
        }
        return $candidate
    }

    $envHome = $env:JAVA_HOME
    if ($envHome) {
        $envJava = Join-Path $envHome 'bin\java.exe'
        if ((Test-Path -LiteralPath $envJava) -and
            (Get-JavaVersionText -JavaExe $envJava) -match 'version "21\.') {
            return [System.IO.Path]::GetFullPath($envHome)
        }
    }

    $knownHomes = Get-ChildItem -LiteralPath 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'jdk-21*' } |
        Sort-Object Name -Descending
    foreach ($candidateHome in $knownHomes) {
        if (Test-Path -LiteralPath (Join-Path $candidateHome.FullName 'bin\java.exe')) {
            return $candidateHome.FullName
        }
    }

    $whereJava = & where.exe java 2>$null
    foreach ($entry in $whereJava) {
        if ((Get-JavaVersionText -JavaExe $entry) -match 'version "21\.') {
            return Split-Path -Parent (Split-Path -Parent $entry)
        }
    }

    throw 'Could not locate a JDK 21 installation; pass -JavaHome explicitly'
}

function Assert-BenchmarkJava {
    param([Parameter(Mandatory = $true)][string]$JavaExe)

    $versionText = Get-JavaVersionText -JavaExe $JavaExe
    if ($versionText -notmatch 'version "21\.') {
        throw "Expected Java 21, but '$JavaExe' reported:`n$versionText"
    }
}

function Get-JavaVersionText {
    param([Parameter(Mandatory = $true)][string]$JavaExe)

    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $versionLines = & $JavaExe -version 2>&1
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    $cleanLines = foreach ($line in $versionLines) {
        if ($line -is [System.Management.Automation.ErrorRecord]) {
            $line.Exception.Message
        } else {
            [string]$line
        }
    }
    return (($cleanLines -join [Environment]::NewLine).Trim())
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToUpperInvariant()
}

function Get-ArtifactManifest {
    param([Parameter(Mandatory = $true)][string[]]$Paths)

    $rows = @()
    foreach ($path in $Paths) {
        $resolved = Get-AbsolutePath -Path $path -Base (Get-Location).Path
        if (-not (Test-Path -LiteralPath $resolved)) {
            continue
        }
        $item = Get-Item -LiteralPath $resolved
        $rows += [pscustomobject][ordered]@{
            name = $item.Name
            path = $item.FullName
            length = $item.Length
            sha256 = Get-FileSha256 -Path $item.FullName
        }
    }
    return $rows
}

function ConvertTo-BoolString {
    param([Parameter(Mandatory = $true)][bool]$Value)
    if ($Value) { return 'true' }
    return 'false'
}

function Write-ServerProperties {
    param(
        [Parameter(Mandatory = $true)][string]$ServerRoot,
        [Parameter(Mandatory = $true)][int]$ServerPortValue,
        [Parameter(Mandatory = $true)][int]$RconPortValue,
        [Parameter(Mandatory = $true)][string]$RconPasswordValue
    )

    $lines = @(
        '#Minecraft server properties'
        '#Generated by tools/catroom-benchmark/Run-CatroomFurnaceBenchmark.ps1'
        'allow-flight=false'
        'allow-nether=false'
        'difficulty=0'
        'enable-command-block=false'
        'enable-query=false'
        'enable-rcon=true'
        'force-gamemode=false'
        'gamemode=0'
        'generate-structures=false'
        'generator-settings=3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;'
        'hardcore=false'
        'level-name=world'
        'level-seed=742190821'
        'level-type=FLAT'
        'max-build-height=256'
        'max-players=1'
        'max-tick-time=0'
        'max-world-size=29999984'
        'motd=CatRoom Furnace Benchmark'
        'network-compression-threshold=256'
        'online-mode=false'
        'op-permission-level=4'
        'player-idle-timeout=0'
        'prevent-proxy-connections=false'
        'pvp=false'
        ('rcon.password=' + $RconPasswordValue)
        ('rcon.port=' + $RconPortValue)
        'resource-pack='
        'resource-pack-sha1='
        'server-ip='
        ('server-port=' + $ServerPortValue)
        'snooper-enabled=false'
        'spawn-animals=false'
        'spawn-protection=0'
        'spawn-monsters=false'
        'spawn-npcs=false'
        'view-distance=4'
        'white-list=true'
    )

    Set-Content -LiteralPath (Join-Path $ServerRoot 'server.properties') -Value $lines -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $ServerRoot 'eula.txt') -Value @(
        '#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://account.mojang.com/documents/minecraft_eula).'
        '#Generated by tools/catroom-benchmark/Run-CatroomFurnaceBenchmark.ps1'
        'eula=true'
    ) -Encoding ASCII
}

function Copy-TreeContents {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    Ensure-Directory -Path $Destination
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        $target = Join-Path $Destination $_.Name
        if ($_.PSIsContainer) {
            Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force
        } else {
            Copy-Item -LiteralPath $_.FullName -Destination $target -Force
        }
    }
}

function Copy-ModArtifacts {
    param(
        [Parameter(Mandatory = $true)][string]$ServerRoot,
        [Parameter(Mandatory = $true)][string[]]$JarPaths,
        [Parameter()][AllowEmptyCollection()][string[]]$SupportPaths = @()
    )

    $modsDir = Join-Path $ServerRoot 'mods'
    Ensure-Directory -Path $modsDir
    Get-ChildItem -LiteralPath $modsDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force

    foreach ($jarPath in $JarPaths) {
        $resolved = Get-AbsolutePath -Path $jarPath -Base (Get-Location).Path
        if (-not (Test-Path -LiteralPath $resolved)) {
            throw "Missing mod jar: $resolved"
        }
        Copy-Item -LiteralPath $resolved -Destination (Join-Path $modsDir (Split-Path -Leaf $resolved)) -Force
    }

    foreach ($supportPath in $SupportPaths) {
        if (-not $supportPath) {
            continue
        }
        $resolved = Get-AbsolutePath -Path $supportPath -Base (Get-Location).Path
        if (-not (Test-Path -LiteralPath $resolved)) {
            throw "Missing support path: $resolved"
        }
        if ((Get-Item -LiteralPath $resolved).PSIsContainer) {
            Copy-TreeContents -Source $resolved -Destination $ServerRoot
        } else {
            Copy-Item -LiteralPath $resolved -Destination (Join-Path $ServerRoot (Split-Path -Leaf $resolved)) -Force
        }
    }
}

function Invoke-GradleBuild {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRootValue,
        [Parameter(Mandatory = $true)][string]$JavaHomeValue,
        [switch]$BuildHarness
    )

    $wrapperJar = Join-Path $RepoRootValue 'gradle\wrapper\gradle-wrapper.jar'
    if (-not (Test-Path -LiteralPath $wrapperJar)) {
        throw "Gradle wrapper jar not found at $wrapperJar"
    }

    $env:JAVA_HOME = $JavaHomeValue
    $env:Path = (Join-Path $JavaHomeValue 'bin') + ';' + $env:Path
    $javaExeValue = Join-Path $JavaHomeValue 'bin\java.exe'
    Push-Location -LiteralPath $RepoRootValue
    try {
        $tasks = @('remapJar', 'verifyPublishedJarManifest')
        if ($BuildHarness) {
            $tasks += @('remapBenchmarkHarnessJar', 'verifyBenchmarkHarnessJar')
        }
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $javaExeValue '-Dorg.gradle.appname=gradlew' -classpath $wrapperJar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain @tasks 2>&1 |
                ForEach-Object { Write-Host $_ }
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed in $RepoRootValue with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }

    $jars = Get-ChildItem -LiteralPath (Join-Path $RepoRootValue 'build\libs') -Filter 'torcherino-*.jar' -File |
        Where-Object {
            $_.Name -notlike '*-sources.jar' -and
            $_.Name -notlike '*-dev.jar' -and
            $_.Name -notlike 'torcherino-benchmark-harness-*'
        } |
        Sort-Object LastWriteTimeUtc -Descending

    if (-not $jars) {
        throw "Could not find a remapped jar under $(Join-Path $RepoRootValue 'build\libs')"
    }

    return $jars[0].FullName
}

function Ensure-WorktreeBuild {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRootValue,
        [Parameter(Mandatory = $true)][string]$Commit,
        [Parameter(Mandatory = $true)][string]$BuildClosureCommit,
        [Parameter(Mandatory = $true)][string]$InfrastructureCommit,
        [Parameter(Mandatory = $true)][string]$WorktreeRootValue,
        [Parameter(Mandatory = $true)][string]$JavaHomeValue
    )

    Ensure-Directory -Path $WorktreeRootValue
    $worktreePath = Join-Path $WorktreeRootValue ($Commit + '-scheduler-baseline')
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) {
        throw 'git.exe was not found on PATH'
    }

    $needsAdd = $true
    if (Test-Path -LiteralPath $worktreePath) {
        try {
            $headResult = Invoke-Git -Arguments @(
                '-C', $worktreePath, 'rev-parse', 'HEAD'
            ) -Quiet
            $head = ($headResult.Output | Select-Object -First 1)
            if ($headResult.ExitCode -eq 0 -and $head.Trim() -like "$BuildClosureCommit*") {
                $needsAdd = $false
            }
        } catch {
            $needsAdd = $true
        }
    }

    if ($needsAdd) {
        if (Test-Path -LiteralPath $worktreePath) {
            Remove-GitWorktreeSafe -RepoRootValue $RepoRootValue -WorktreeRootValue $WorktreeRootValue -WorktreePath $worktreePath
        }
        Write-Status "Creating scheduler baseline worktree $worktreePath at build closure $BuildClosureCommit"
        $addResult = Invoke-Git -Arguments @(
            '-C', $RepoRootValue, 'worktree', 'add', '--detach',
            $worktreePath, $BuildClosureCommit
        )
        if ($addResult.ExitCode -ne 0) {
            throw "git worktree add failed for commit $BuildClosureCommit"
        }
    } else {
        Write-Status "Reusing git worktree $worktreePath"
    }

    $restoreResult = Invoke-Git -Arguments @(
        '-C', $worktreePath, 'restore', "--source=$InfrastructureCommit", '--',
        'build.gradle', 'settings.gradle', 'gradle.properties',
        'gradle/wrapper/gradle-wrapper.properties',
        'gradle/wrapper/gradle-wrapper.jar'
    )
    if ($restoreResult.ExitCode -ne 0) {
        throw "Could not apply build infrastructure from $InfrastructureCommit"
    }

    $plannerRelativePath = 'src\main\java\com\sci\torcherino\acceleration\CoveragePlanner.java'
    $plannerSourcePath = Join-Path $RepoRootValue $plannerRelativePath
    $plannerTargetPath = Join-Path $worktreePath $plannerRelativePath
    Assert-InputPath -Path $plannerSourcePath -Label 'current coordinate-layout fix'
    Copy-Item -LiteralPath $plannerSourcePath -Destination $plannerTargetPath -Force

    $catalogPath = Join-Path $worktreePath 'src\main\java\com\sci\torcherino\compat\CompatibilityCatalog.java'
    Ensure-Directory -Path (Split-Path -Parent $catalogPath)
    @(
        'package com.sci.torcherino.compat;'
        ''
        'import com.sci.torcherino.acceleration.AdapterRegistry;'
        ''
        'public final class CompatibilityCatalog {'
        '    private CompatibilityCatalog() {'
        '    }'
        ''
        '    public static void register(AdapterRegistry registry) {'
        '        // Synthetic scheduler-only benchmark baseline: no machine adapters.'
        '    }'
        '}'
    ) | Set-Content -LiteralPath $catalogPath -Encoding ASCII

    $jar = Invoke-GradleBuild -RepoRootValue $worktreePath -JavaHomeValue $JavaHomeValue
    return [pscustomobject]@{
        WorktreePath = $worktreePath
        JarPath      = $jar
        SourceCommit = $Commit
        BuildClosureCommit = $BuildClosureCommit
        InfrastructureCommit = $InfrastructureCommit
    }
}

function Ensure-CleanroomServer {
    param(
        [Parameter(Mandatory = $true)][string]$ServerRootValue,
        [Parameter(Mandatory = $true)][string]$InstallerJarValue,
        [Parameter(Mandatory = $true)][string]$JavaExeValue,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )

    $serverJar = Join-Path $ServerRootValue 'cleanroom-0.1.0.jar'
    $requiredFiles = @(
        $serverJar,
        (Join-Path $ServerRootValue 'minecraft_server.1.12.2.jar'),
        (Join-Path $ServerRootValue 'libraries\it\unimi\dsi\fastutil\8.5.12\fastutil-8.5.12.jar'),
        (Join-Path $ServerRootValue 'libraries\org\apache\logging\log4j\log4j-core\2.22.1\log4j-core-2.22.1.jar'),
        (Join-Path $ServerRootValue 'libraries\com\cleanroommc\sponge-mixin\0.16.0+mixin.0.8.5\sponge-mixin-0.16.0+mixin.0.8.5.jar'),
        (Join-Path $ServerRootValue 'libraries\org\lwjgl3\lwjgl3\3.3.4-27-CLEANROOM\lwjgl3-3.3.4-27-CLEANROOM-natives-windows.jar')
    )
    $installComplete = $true
    foreach ($requiredFile in $requiredFiles) {
        if (-not (Test-Path -LiteralPath $requiredFile)) {
            $installComplete = $false
            break
        }
    }

    if ($installComplete) {
        $actual = Get-FileSha256 -Path $serverJar
        if ($actual -ne $ExpectedSha256.ToUpperInvariant()) {
            throw "Cleanroom core SHA mismatch for $serverJar. Expected $ExpectedSha256, got $actual"
        }
        Write-Status "Reusing Cleanroom server root $ServerRootValue"
        return
    }

    Ensure-Directory -Path $ServerRootValue
    Write-Status "Installing Cleanroom server into $ServerRootValue"
    & $JavaExeValue -jar $InstallerJarValue --installServer $ServerRootValue
    if ($LASTEXITCODE -ne 0) {
        throw "Cleanroom installer failed with exit code $LASTEXITCODE"
    }

    if (-not (Test-Path -LiteralPath $serverJar)) {
        throw "Cleanroom installer did not create $serverJar"
    }

    $actualAfter = Get-FileSha256 -Path $serverJar
    if ($actualAfter -ne $ExpectedSha256.ToUpperInvariant()) {
        throw "Cleanroom core SHA mismatch for $serverJar. Expected $ExpectedSha256, got $actualAfter"
    }
    foreach ($requiredFile in $requiredFiles) {
        if (-not (Test-Path -LiteralPath $requiredFile)) {
            throw "Cleanroom installer completed without required runtime file: $requiredFile"
        }
    }
}

function New-RconClient {
    param(
        [Parameter(Mandatory = $true)][string]$RconHostValue,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Password,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $client = New-Object System.Net.Sockets.TcpClient
    $client.ReceiveTimeout = $TimeoutSeconds * 1000
    $client.SendTimeout = $TimeoutSeconds * 1000
    $client.Connect($RconHostValue, $Port)

    $stream = $client.GetStream()
    $stream.ReadTimeout = $TimeoutSeconds * 1000
    $stream.WriteTimeout = $TimeoutSeconds * 1000
    $writer = New-Object System.IO.BinaryWriter($stream, [System.Text.Encoding]::UTF8, $true)
    $reader = New-Object System.IO.BinaryReader($stream, [System.Text.Encoding]::UTF8, $true)

    $state = [pscustomobject]@{
        Client    = $client
        Stream    = $stream
        Writer    = $writer
        Reader    = $reader
        NextId    = 1
        Password  = $Password
        Host      = $RconHostValue
        Port      = $Port
    }

    $authId = Send-RconPacket -State $state -Type 3 -Payload $Password
    $response = Read-RconPacket -State $state
    if ($response.RequestId -ne $authId) {
        throw "RCON authentication failed for $($RconHostValue):$Port"
    }

    return $state
}

function Send-RconPacket {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][int]$Type,
        [Parameter(Mandatory = $true)][string]$Payload
    )

    $requestId = $State.NextId
    $State.NextId++

    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($Payload)
    $packetLength = 4 + 4 + $payloadBytes.Length + 2
    $packet = New-Object byte[] (4 + $packetLength)
    $lengthBytes = [System.BitConverter]::GetBytes([int]$packetLength)
    $requestBytes = [System.BitConverter]::GetBytes([int]$requestId)
    $typeBytes = [System.BitConverter]::GetBytes([int]$Type)
    if (-not [System.BitConverter]::IsLittleEndian) {
        [Array]::Reverse($lengthBytes)
        [Array]::Reverse($requestBytes)
        [Array]::Reverse($typeBytes)
    }
    [Array]::Copy($lengthBytes, 0, $packet, 0, 4)
    [Array]::Copy($requestBytes, 0, $packet, 4, 4)
    [Array]::Copy($typeBytes, 0, $packet, 8, 4)
    [Array]::Copy($payloadBytes, 0, $packet, 12, $payloadBytes.Length)
    $State.Stream.Write($packet, 0, $packet.Length)
    $State.Stream.Flush()

    return $requestId
}

function Read-RconPacket {
    param([Parameter(Mandatory = $true)]$State)

    $length = $State.Reader.ReadInt32()
    $requestId = $State.Reader.ReadInt32()
    $type = $State.Reader.ReadInt32()
    $bodyLength = $length - 8
    $bodyBytes = if ($bodyLength -gt 0) { $State.Reader.ReadBytes($bodyLength) } else { [byte[]]@() }
    $body = [System.Text.Encoding]::UTF8.GetString($bodyBytes).TrimEnd([char]0)

    return [pscustomobject]@{
        RequestId = $requestId
        Type      = $type
        Body      = $body
    }
}

function Invoke-RconCommand {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $requestId = Send-RconPacket -State $State -Type 2 -Payload $Command
    $responses = New-Object System.Collections.Generic.List[string]
    do {
        try {
            $packet = Read-RconPacket -State $State
            if ($packet.RequestId -ne $requestId) {
                continue
            }
            if ($packet.Body) {
                [void]$responses.Add($packet.Body)
            }
        } catch [System.IO.IOException] {
            break
        } catch [System.Management.Automation.MethodInvocationException] {
            break
        }
        Start-Sleep -Milliseconds 25
    } while ($State.Client.Connected -and $State.Stream.DataAvailable)

    return ($responses -join [Environment]::NewLine)
}

function Wait-ForLogPattern {
    param(
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $seenLength = 0L
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $LogPath) {
            $info = Get-Item -LiteralPath $LogPath
            if ($info.Length -ne $seenLength) {
                $seenLength = $info.Length
                $content = Get-Content -LiteralPath $LogPath -Raw
                if ($content -match $Pattern) {
                    return $true
                }
            }
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-ForDirectoryFiles {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $Path) {
            $files = Get-ChildItem -LiteralPath $Path -Recurse -File -ErrorAction SilentlyContinue
            if ($files.Count -gt 0) {
                return $true
            }
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Start-CleanroomServer {
    param(
        [Parameter(Mandatory = $true)][string]$ServerRootValue,
        [Parameter(Mandatory = $true)][string]$JavaExeValue
    )

    $stdoutPath = Join-Path $ServerRootValue 'process-stdout.log'
    $stderrPath = Join-Path $ServerRootValue 'process-stderr.log'
    Remove-Item -LiteralPath $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue

    $process = Start-Process -FilePath $JavaExeValue -ArgumentList @('-jar', 'cleanroom-0.1.0.jar', 'nogui') -WorkingDirectory $ServerRootValue -PassThru -WindowStyle Hidden -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    return [pscustomobject]@{
        Process    = $process
        StdoutPath = $stdoutPath
        StderrPath = $stderrPath
    }
}

function Wait-ServerReady {
    param(
        [Parameter(Mandatory = $true)]$ServerProcessInfo,
        [Parameter(Mandatory = $true)][string]$ServerRootValue,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $logPath = Join-Path $ServerRootValue 'logs\latest.log'
    $donePattern = 'Done \(.+\)! For help, type "help" or "\?"'
    $rconPattern = 'RCON running on '
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        $ServerProcessInfo.Process.Refresh()
        if ($ServerProcessInfo.Process.HasExited) {
            throw "Server process exited early with code $($ServerProcessInfo.Process.ExitCode)"
        }

        if (Test-Path -LiteralPath $logPath) {
            $log = Get-Content -LiteralPath $logPath -Raw
            if ($log -match $donePattern -and $log -match $rconPattern) {
                return $true
            }
        }
        Start-Sleep -Seconds 2
    }

    throw "Server did not become ready within $TimeoutSeconds seconds"
}

function Copy-RunArtifacts {
    param(
        [Parameter(Mandatory = $true)][string]$ServerRootValue,
        [Parameter(Mandatory = $true)][string]$DestinationRoot,
        [Parameter(Mandatory = $true)][string]$RunName
    )

    $runRoot = Join-Path $DestinationRoot $RunName
    Ensure-Directory -Path $runRoot

    $items = @(
        'server.properties',
        'eula.txt',
        'logs\latest.log',
        'logs\debug.log',
        'process-stdout.log',
        'process-stderr.log'
    )

    foreach ($item in $items) {
        $source = Join-Path $ServerRootValue $item
        if (Test-Path -LiteralPath $source) {
            $target = Join-Path $runRoot $item
            Ensure-Directory -Path (Split-Path -Parent $target)
            Copy-Item -LiteralPath $source -Destination $target -Force
        }
    }

    $exportRoot = Join-Path $ServerRootValue 'export'
    if (Test-Path -LiteralPath $exportRoot) {
        $exportTarget = Join-Path $runRoot 'export'
        Copy-TreeContents -Source $exportRoot -Destination $exportTarget
    }

    $crashRoot = Join-Path $ServerRootValue 'crash-reports'
    if (Test-Path -LiteralPath $crashRoot) {
        $crashTarget = Join-Path $runRoot 'crash-reports'
        Copy-TreeContents -Source $crashRoot -Destination $crashTarget
    }

    return $runRoot
}

function Write-RunManifest {
    param(
        [Parameter(Mandatory = $true)][string]$RunRoot,
        [Parameter(Mandatory = $true)][hashtable]$Data
    )

    $jsonPath = Join-Path $RunRoot 'run.json'
    ($Data | ConvertTo-Json -Depth 8) | Set-Content -LiteralPath $jsonPath -Encoding UTF8
}

function Format-SummaryRow {
    param([Parameter(Mandatory = $true)][hashtable]$Row)

    $layer = if ($Row.ContainsKey('Layer')) { $Row.Layer } else { 'environment' }
    return '| ' + $Row.Label + ' | ' + $layer + ' | ' + $Row.Source + ' | ' + $Row.JarSha256 + ' | ' + $Row.Status + ' | ' + $Row.DurationSeconds + ' | ' + $Row.RunRoot + ' |'
}

function Assert-InputPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Label
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing $($Label): $Path"
    }
}

$repoRoot = Get-RepoRoot
$javaHomeValue = Get-BenchmarkJavaHome -PreferredHome $JavaHome
$javaExe = Join-Path $javaHomeValue 'bin\java.exe'
Assert-BenchmarkJava -JavaExe $javaExe

if (-not $CleanroomInstallerJar) {
    $CleanroomInstallerJar = Join-Path $repoRoot 'cleanroom\cleanroom-0.1.0-installer.jar'
}
if (-not $BenchmarkServerRoot) {
    $BenchmarkServerRoot = Join-Path $repoRoot 'cleanroom\benchmark-server'
}
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot 'build\reports\torcherino\catroom-furnace-benchmark'
}
if (-not $WorktreeRoot) {
    $WorktreeRoot = Join-Path $repoRoot 'cleanroom\worktrees'
}
if (-not $CurrentJar) {
    $CurrentJar = Join-Path $repoRoot 'build\libs\torcherino-8.0.0-alpha.6.jar'
}
if (-not $HarnessJar) {
    $HarnessJar = Join-Path $repoRoot 'build\libs\torcherino-benchmark-harness-8.0.0-alpha.6.jar'
}

$CleanroomInstallerJar = Get-AbsolutePath -Path $CleanroomInstallerJar -Base $repoRoot
$BenchmarkServerRoot = Get-AbsolutePath -Path $BenchmarkServerRoot -Base $repoRoot
$OutputRoot = Get-AbsolutePath -Path $OutputRoot -Base $repoRoot
$WorktreeRoot = Get-AbsolutePath -Path $WorktreeRoot -Base $repoRoot
$CurrentJar = Get-AbsolutePath -Path $CurrentJar -Base $repoRoot
$HarnessJar = Get-AbsolutePath -Path $HarnessJar -Base $repoRoot
$Torcherino76Jar = Get-AbsolutePath -Path $Torcherino76Jar -Base $repoRoot
$TargetModsRoot = Get-AbsolutePath -Path $TargetModsRoot -Base $repoRoot
$IncludePaths = @($IncludePaths | Where-Object { $_ })
$AdditionalModJars = @($AdditionalModJars | Where-Object { $_ })
if ($AdditionalModJars.Count -eq 0) {
    $AdditionalModJars = @(
        $HarnessJar
    )
}
if ($ThermalProbeModJars.Count -eq 0) {
    $ThermalProbeModJars = @(
        (Join-Path $TargetModsRoot 'CodeChickenLib-1.12.2-3.2.4.1-universal.jar'),
        (Join-Path $TargetModsRoot 'CoFHCore-1.12.2-4.6.6.1-universal.jar'),
        (Join-Path $TargetModsRoot 'CoFHWorld-1.12.2-1.4.0.1-universal.jar'),
        (Join-Path $TargetModsRoot 'ThermalFoundation-1.12.2-2.6.7.1-universal.jar'),
        (Join-Path $TargetModsRoot 'ThermalExpansion-1.12.2-5.5.7.1-universal.jar'),
        (Join-Path $TargetModsRoot 'RedstoneFlux-1.12-2.1.1.1-universal.jar')
    )
}
if ($EnderIoProbeModJars.Count -eq 0) {
    $EnderIoProbeModJars = @(
        (Join-Path $TargetModsRoot 'CodeChickenLib-1.12.2-3.2.4.1-universal.jar'),
        (Join-Path $TargetModsRoot 'endercore-1.12.2-0.5.81.jar'),
        (Join-Path $TargetModsRoot 'EnderIO-CEu-5.4.2.jar')
    )
}
$AdditionalModJars = @($AdditionalModJars | ForEach-Object {
    Get-AbsolutePath -Path $_ -Base $repoRoot
})
$EnderIoProbeModJars = @($EnderIoProbeModJars | ForEach-Object {
    Get-AbsolutePath -Path $_ -Base $repoRoot
})
$ThermalProbeModJars = @($ThermalProbeModJars | ForEach-Object {
    Get-AbsolutePath -Path $_ -Base $repoRoot
})
$allSupportPaths = @($IncludePaths)

Assert-InputPath -Path $CleanroomInstallerJar -Label 'Cleanroom installer jar'
Assert-InputPath -Path $Torcherino76Jar -Label 'Torcherino 7.6 jar'

if (-not $RconPassword) {
    $RconPassword = [Guid]::NewGuid().ToString('N')
}

Ensure-Directory -Path $OutputRoot
Ensure-Directory -Path $WorktreeRoot

$planLines = New-Object System.Collections.Generic.List[string]
$planLines.Add('# CatRoom Furnace Benchmark Plan')
$planLines.Add('')
$planLines.Add('| Field | Value |')
$planLines.Add('|---|---|')
$planLines.Add('| Repo root | ' + $repoRoot + ' |')
$planLines.Add('| Benchmark server root | ' + $BenchmarkServerRoot + ' |')
$planLines.Add('| Output root | ' + $OutputRoot + ' |')
$planLines.Add('| Worktree commit | ' + $WorktreeCommit + ' |')
$planLines.Add('| Worktree build closure | ' + $WorktreeBuildClosureCommit + ' |')
$planLines.Add('| Build infrastructure | ' + $BuildInfrastructureCommit + ' |')
$planLines.Add('| Java home | ' + $javaHomeValue + ' |')
$planLines.Add('| Cleanroom installer | ' + $CleanroomInstallerJar + ' |')
$planLines.Add('| Cleanroom core SHA | ' + $CleanroomCoreSha256.ToUpperInvariant() + ' |')
$planLines.Add('| Torcherino 7.6 jar | ' + $Torcherino76Jar + ' |')
$planLines.Add('| Current alpha.6 jar | ' + $CurrentJar + ' |')
$planLines.Add('| Harness jar | ' + $HarnessJar + ' |')
$planLines.Add('| Target mods root | ' + $TargetModsRoot + ' |')
$planLines.Add('| Benchmark layers | ' + ($BenchmarkLayers -join ', ') + ' |')
$planLines.Add('| Benchmark mode | ' + $BenchmarkMode + ' |')
$planLines.Add('| Ender IO environment probe | ' + ($(if ($SkipEnderIoEnvironmentProbe) { 'skipped' } else { $EnderIoProbeModJars -join ', ' })) + ' |')
$planLines.Add('| Thermal environment probe | ' + ($(if ($SkipThermalEnvironmentProbe) { 'skipped' } else { $ThermalProbeModJars -join ', ' })) + ' |')
$includePathsText = if ($allSupportPaths.Count -gt 0) { $allSupportPaths -join ', ' } else { '(none)' }
$planLines.Add('| Include paths | ' + $includePathsText + ' |')
$planLines.Add('| Run commands | ' + ($RunCommands -join '; ') + ' |')
$exportCommandsText = if ($ExportCommands.Count -gt 0) { $ExportCommands -join '; ' } else { '(none)' }
$planLines.Add('| Export commands | ' + $exportCommandsText + ' |')
$planLines.Add('| Completion pattern | ' + ($(if ($CompletionPattern) { $CompletionPattern } else { '(none)' })) + ' |')

if ($DryRun -or $Preflight) {
    $planPath = Join-Path $OutputRoot 'plan.md'
    $planLines | Set-Content -LiteralPath $planPath -Encoding UTF8
    Write-Status "Plan written to $planPath"
}

if ($Preflight) {
    Assert-InputPath -Path $CurrentJar -Label 'Current alpha.6 jar'
    Assert-InputPath -Path $HarnessJar -Label 'benchmark harness jar'
    foreach ($modJar in $AdditionalModJars) {
        Assert-InputPath -Path $modJar -Label 'benchmark dependency jar'
    }
    foreach ($modJar in $EnderIoProbeModJars) {
        Assert-InputPath -Path $modJar -Label 'Ender IO probe jar'
    }
    foreach ($modJar in $ThermalProbeModJars) {
        Assert-InputPath -Path $modJar -Label 'Thermal probe jar'
    }
    foreach ($commit in @($WorktreeCommit, $WorktreeBuildClosureCommit, $BuildInfrastructureCommit)) {
        $verifyResult = Invoke-Git -Arguments @(
            '-C', $repoRoot, 'rev-parse', '--verify', "$commit^{commit}"
        ) -Quiet
        if ($verifyResult.ExitCode -ne 0) {
            throw "Required commit $commit is not available in the repository"
        }
    }
    Write-Status "Preflight checks passed"
    if ($DryRun) {
        Write-Status 'Dry run requested; no build, install, or server launch performed'
    }
    return
}

if ($DryRun) {
    Write-Status 'Dry run requested; no build, install, or server launch performed'
    return
}

$currentJarPath = $CurrentJar
if (-not (Test-Path -LiteralPath $currentJarPath) -or -not (Test-Path -LiteralPath $HarnessJar)) {
    Write-Status 'Building current alpha.6 and benchmark harness artifacts'
    $currentJarPath = Invoke-GradleBuild -RepoRootValue $repoRoot -JavaHomeValue $javaHomeValue -BuildHarness
}

$worktreeInfo = $null
if ($CandidateSelection -eq 'all' -or $CandidateSelection -eq 'scheduler') {
    $worktreeInfo = Ensure-WorktreeBuild -RepoRootValue $repoRoot -Commit $WorktreeCommit -BuildClosureCommit $WorktreeBuildClosureCommit -InfrastructureCommit $BuildInfrastructureCommit -WorktreeRootValue $WorktreeRoot -JavaHomeValue $javaHomeValue
}

Ensure-CleanroomServer -ServerRootValue $BenchmarkServerRoot -InstallerJarValue $CleanroomInstallerJar -JavaExeValue $javaExe -ExpectedSha256 $CleanroomCoreSha256
Write-ServerProperties -ServerRoot $BenchmarkServerRoot -ServerPortValue $ServerPort -RconPortValue $RconPort -RconPasswordValue $RconPassword

$candidateRows = @(
    [pscustomobject]@{
        Label  = 'torcherino-7.6'
        Source = 'existing jar'
        Jar    = $Torcherino76Jar
    },
    [pscustomobject]@{
        Label  = 'alpha.6-current'
        Source = 'current branch'
        Jar    = $currentJarPath
    }
)
if ($worktreeInfo) {
    $candidateRows += [pscustomobject]@{
        Label  = 'c8bcaae-scheduler-baseline'
        Source = "$WorktreeCommit logic + coordinate-layout fix + $WorktreeBuildClosureCommit build closure + empty compatibility catalog"
        Jar    = $worktreeInfo.JarPath
    }
}
$candidates = @($candidateRows)
if ($CandidateSelection -ne 'all') {
    $selectedLabel = switch ($CandidateSelection) {
        '7.6' { 'torcherino-7.6' }
        'scheduler' { 'c8bcaae-scheduler-baseline' }
        'current' { 'alpha.6-current' }
    }
    $candidates = @($candidates | Where-Object { $_.Label -eq $selectedLabel })
}

$summaryLines = New-Object System.Collections.Generic.List[string]
$summaryLines.Add('# CatRoom Furnace Benchmark Summary')
$summaryLines.Add('')
$summaryLines.Add('| Candidate | Layer | Source | Jar SHA256 | Status | Duration s | Run root |')
$summaryLines.Add('|---|---|---|---:|---|---:|---|')
$summaryRecords = @()

$thermalStatus = if ($SkipThermalEnvironmentProbe) { 'available' } else { 'pending' }
$thermalDetail = if ($SkipThermalEnvironmentProbe) {
    'probe skipped by request; availability assumed'
} else { '' }
$probeStatus = if ($SkipEnderIoEnvironmentProbe) { 'available' } else { 'pending' }
$probeDetail = if ($SkipEnderIoEnvironmentProbe) {
    'probe skipped by request; availability assumed'
} else { '' }

if (-not $SkipThermalEnvironmentProbe) {
    Write-Status 'Running isolated Thermal Expansion/CodeChickenLib CatRoom startup probe'
    Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'world') -Label 'world'
    Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'logs') -Label 'logs'
    Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'crash-reports') -Label 'crash reports'
    Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'export') -Label 'export'
    Copy-ModArtifacts -ServerRoot $BenchmarkServerRoot -JarPaths (@($currentJarPath) + $AdditionalModJars + $ThermalProbeModJars) -SupportPaths $allSupportPaths

    $thermalStartedAt = Get-Date
    $thermalServer = Start-CleanroomServer -ServerRootValue $BenchmarkServerRoot -JavaExeValue $javaExe
    $thermalRcon = $null
    $thermalStatus = 'available'
    $thermalDetail = 'server reached ready state'
    try {
        Wait-ServerReady -ServerProcessInfo $thermalServer -ServerRootValue $BenchmarkServerRoot -TimeoutSeconds ([Math]::Min($StartupTimeoutSeconds, 240))
        $thermalRcon = New-RconClient -RconHostValue $RconHost -Port $RconPort -Password $RconPassword -TimeoutSeconds $RconTimeoutSeconds
        [void](Invoke-RconCommand -State $thermalRcon -Command 'stop' -TimeoutSeconds $RconTimeoutSeconds)
        [void]$thermalServer.Process.WaitForExit($StartupTimeoutSeconds * 1000)
    } catch {
        $thermalStatus = 'environment-blocked'
        $thermalDetail = $_.Exception.Message
        $thermalLog = Join-Path $BenchmarkServerRoot 'logs\latest.log'
        if (Test-Path -LiteralPath $thermalLog) {
            $thermalLogText = Get-Content -LiteralPath $thermalLog -Raw
            if ($thermalLogText -match "NoSuchMethodError: 'java.lang.Object jdk.internal.misc.Unsafe.getObject") {
                $thermalDetail = 'CodeChickenLib 3.2.4.1 is incompatible with CatRoom on the selected Java runtime: jdk.internal.misc.Unsafe.getObject is unavailable'
            }
        }
        if (-not $thermalServer.Process.HasExited) {
            $thermalServer.Process.Kill()
            [void]$thermalServer.Process.WaitForExit(30000)
        }
    } finally {
        if ($thermalRcon) {
            $thermalRcon.Reader.Dispose()
            $thermalRcon.Writer.Dispose()
            $thermalRcon.Stream.Dispose()
            $thermalRcon.Client.Close()
        }
    }

    $thermalDuration = [Math]::Round(((Get-Date) - $thermalStartedAt).TotalSeconds, 2)
    $thermalRunName = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-thermal-environment-probe'
    $thermalRunRoot = Copy-RunArtifacts -ServerRootValue $BenchmarkServerRoot -DestinationRoot $OutputRoot -RunName $thermalRunName
    Write-RunManifest -RunRoot $thermalRunRoot -Data ([ordered]@{
        label = 'thermal-environment-probe'
        source = 'CatRoom + Thermal Expansion 5.5.7.1 dependency set'
        status = $thermalStatus
        detail = $thermalDetail
        durationSeconds = $thermalDuration
        artifacts = Get-ArtifactManifest -Paths (@($currentJarPath) + $AdditionalModJars + $ThermalProbeModJars + @(
            $CleanroomInstallerJar,
            (Join-Path $BenchmarkServerRoot 'cleanroom-0.1.0.jar')
        ))
    })
    [void]$summaryLines.Add((Format-SummaryRow -Row @{
        Label = 'thermal-environment-probe'
        Layer = 'thermal'
        Source = 'Thermal Expansion 5.5.7.1'
        JarSha256 = Get-FileSha256 -Path ($ThermalProbeModJars | Where-Object { $_ -like '*ThermalExpansion*' } | Select-Object -First 1)
        Status = $thermalStatus + ': ' + $thermalDetail
        DurationSeconds = $thermalDuration
        RunRoot = $thermalRunRoot
    }))
    $summaryRecords += [pscustomobject]@{
        label = 'thermal-environment-probe'
        layer = 'thermal'
        source = 'Thermal Expansion 5.5.7.1'
        status = $thermalStatus
        detail = $thermalDetail
        durationSeconds = $thermalDuration
        runJson = Join-Path $thermalRunRoot 'run.json'
    }
}

if (-not $SkipEnderIoEnvironmentProbe) {
    Write-Status 'Running isolated Ender IO/EnderCore CatRoom startup probe'
    Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'world') -Label 'world'
    Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'logs') -Label 'logs'
    Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'crash-reports') -Label 'crash reports'
    Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'export') -Label 'export'
    Copy-ModArtifacts -ServerRoot $BenchmarkServerRoot -JarPaths (@($currentJarPath) + $AdditionalModJars + $EnderIoProbeModJars) -SupportPaths $allSupportPaths

    $probeStartedAt = Get-Date
    $probeServer = Start-CleanroomServer -ServerRootValue $BenchmarkServerRoot -JavaExeValue $javaExe
    $probeRcon = $null
    $probeStatus = 'available'
    $probeDetail = 'server reached ready state'
    try {
        Wait-ServerReady -ServerProcessInfo $probeServer -ServerRootValue $BenchmarkServerRoot -TimeoutSeconds ([Math]::Min($StartupTimeoutSeconds, 240))
        $probeRcon = New-RconClient -RconHostValue $RconHost -Port $RconPort -Password $RconPassword -TimeoutSeconds $RconTimeoutSeconds
        [void](Invoke-RconCommand -State $probeRcon -Command 'stop' -TimeoutSeconds $RconTimeoutSeconds)
        [void]$probeServer.Process.WaitForExit($StartupTimeoutSeconds * 1000)
    } catch {
        $probeStatus = 'environment-blocked'
        $probeDetail = $_.Exception.Message
        $probeLog = Join-Path $BenchmarkServerRoot 'logs\latest.log'
        if (Test-Path -LiteralPath $probeLog) {
            $probeLogText = Get-Content -LiteralPath $probeLog -Raw
            if ($probeLogText -match 'Class versions V1_5 or less must use F_NEW frames') {
                $probeDetail = 'EnderCore 0.5.81 transformer is incompatible with CatRoom ASM 9.6: V1_5 or less must use F_NEW frames'
            }
        }
        if (-not $probeServer.Process.HasExited) {
            $probeServer.Process.Kill()
            [void]$probeServer.Process.WaitForExit(30000)
        }
    } finally {
        if ($probeRcon) {
            $probeRcon.Reader.Dispose()
            $probeRcon.Writer.Dispose()
            $probeRcon.Stream.Dispose()
            $probeRcon.Client.Close()
        }
    }

    $probeDuration = [Math]::Round(((Get-Date) - $probeStartedAt).TotalSeconds, 2)
    $probeRunName = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-enderio-environment-probe'
    $probeRunRoot = Copy-RunArtifacts -ServerRootValue $BenchmarkServerRoot -DestinationRoot $OutputRoot -RunName $probeRunName
    Write-RunManifest -RunRoot $probeRunRoot -Data ([ordered]@{
        label = 'enderio-environment-probe'
        source = 'CatRoom + EnderCore 0.5.81 + Ender IO CEu 5.4.2'
        status = $probeStatus
        detail = $probeDetail
        durationSeconds = $probeDuration
        artifacts = Get-ArtifactManifest -Paths (@($currentJarPath) + $AdditionalModJars + $EnderIoProbeModJars + @(
            $CleanroomInstallerJar,
            (Join-Path $BenchmarkServerRoot 'cleanroom-0.1.0.jar')
        ))
    })
    [void]$summaryLines.Add((Format-SummaryRow -Row @{
        Label = 'enderio-environment-probe'
        Layer = 'enderio'
        Source = 'EnderCore 0.5.81 + Ender IO CEu 5.4.2'
        JarSha256 = Get-FileSha256 -Path $EnderIoProbeModJars[-1]
        Status = $probeStatus + ': ' + $probeDetail
        DurationSeconds = $probeDuration
        RunRoot = $probeRunRoot
    }))
    $summaryRecords += [pscustomobject]@{
        label = 'enderio-environment-probe'
        layer = 'enderio'
        source = 'EnderCore 0.5.81 + Ender IO CEu 5.4.2'
        status = $probeStatus
        detail = $probeDetail
        durationSeconds = $probeDuration
        runJson = Join-Path $probeRunRoot 'run.json'
    }
}

$exportCommandList = @($ExportCommands | Where-Object { $_ })
$configuredRunCommands = @($RunCommands | Where-Object { $_ })
$fatalEnvironmentReason = $null

$layerRows = @()
foreach ($layerName in $BenchmarkLayers) {
    if ($layerName -eq 'vanilla') {
        $layerRows += [pscustomobject]@{
            Label = 'vanilla'
            Status = 'available'
            Detail = ''
            ModJars = @()
        }
    } elseif ($layerName -eq 'thermal1' -or
        $layerName -eq 'thermal' -or
        $layerName -eq 'thermal80') {
        $layerRows += [pscustomobject]@{
            Label = $layerName
            Status = $thermalStatus
            Detail = $thermalDetail
            ModJars = @($ThermalProbeModJars)
        }
    } elseif ($layerName -eq 'enderio') {
        $layerRows += [pscustomobject]@{
            Label = 'enderio'
            Status = $probeStatus
            Detail = $probeDetail
            ModJars = @($EnderIoProbeModJars)
        }
    }
}

foreach ($candidate in $candidates) {
    $jarPath = Get-AbsolutePath -Path $candidate.Jar -Base $repoRoot
    Assert-InputPath -Path $jarPath -Label "$($candidate.Label) jar"

    $candidateJarSha = Get-FileSha256 -Path $jarPath
    foreach ($layer in $layerRows) {
        if ($layer.Status -ne 'available') {
            $blockedStatus = 'environment-blocked: ' + $layer.Detail
            [void]$summaryLines.Add((Format-SummaryRow -Row @{
                Label = $candidate.Label
                Layer = $layer.Label
                Source = $candidate.Source
                JarSha256 = $candidateJarSha
                Status = $blockedStatus
                DurationSeconds = 0
                RunRoot = ''
            }))
            $summaryRecords += [pscustomobject]@{
                label = $candidate.Label
                layer = $layer.Label
                source = $candidate.Source
                status = 'environment-blocked'
                detail = $layer.Detail
                durationSeconds = 0
                runJson = ''
            }
            continue
        }

        $runCommandList = if ($configuredRunCommands.Count -eq 1 -and
            $configuredRunCommands[0] -eq 'torcherino-bench run furnace-suite') {
            @("torcherino-bench run furnace-suite $($layer.Label) $BenchmarkMode")
        } else {
            @($configuredRunCommands)
        }
        $runStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        $runName = "$runStamp-$($candidate.Label)-$($layer.Label)"
        $layerModJars = @($AdditionalModJars + $layer.ModJars | Select-Object -Unique)

        Write-Status "Starting $($candidate.Label) layer $($layer.Label) from $jarPath"
        Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'world') -Label 'world'
        Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'logs') -Label 'logs'
        Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'crash-reports') -Label 'crash reports'
        Remove-DirectorySafe -Root $BenchmarkServerRoot -Path (Join-Path $BenchmarkServerRoot 'export') -Label 'export'
        Copy-ModArtifacts -ServerRoot $BenchmarkServerRoot -JarPaths (@($jarPath) + $layerModJars) -SupportPaths $allSupportPaths

        $server = Start-CleanroomServer -ServerRootValue $BenchmarkServerRoot -JavaExeValue $javaExe
        $startedAt = Get-Date
        $rcon = $null
        try {
            Wait-ServerReady -ServerProcessInfo $server -ServerRootValue $BenchmarkServerRoot -TimeoutSeconds $StartupTimeoutSeconds
            $rcon = New-RconClient -RconHostValue $RconHost -Port $RconPort -Password $RconPassword -TimeoutSeconds $RconTimeoutSeconds

            Write-Status "RCON confirmed for $($candidate.Label) layer $($layer.Label)"
            $fmlResponse = Invoke-RconCommand -State $rcon -Command '/fml confirm' -TimeoutSeconds $RconTimeoutSeconds
            if ($fmlResponse) {
                Write-Status "FML response: $fmlResponse"
            }

            foreach ($command in $runCommandList) {
                Write-Status "RCON run: $command"
                $response = Invoke-RconCommand -State $rcon -Command $command -TimeoutSeconds $RconTimeoutSeconds
                if ($response) {
                    Write-Status $response
                }
            }

            if ($CompletionPattern) {
                Write-Status "Waiting for completion pattern '$CompletionPattern'"
                $completed = Wait-ForLogPattern -LogPath (Join-Path $BenchmarkServerRoot 'logs\latest.log') -Pattern $CompletionPattern -TimeoutSeconds $BenchmarkTimeoutSeconds
                if (-not $completed) {
                    throw "Timed out waiting for completion pattern '$CompletionPattern'"
                }
            }

            foreach ($command in $exportCommandList) {
                Write-Status "RCON export: $command"
                $response = Invoke-RconCommand -State $rcon -Command $command -TimeoutSeconds $RconTimeoutSeconds
                if ($response) {
                    Write-Status $response
                }
            }

            $exportRoot = Join-Path $BenchmarkServerRoot 'export'
            if (-not (Wait-ForDirectoryFiles -Path $exportRoot -TimeoutSeconds $BenchmarkTimeoutSeconds)) {
                throw "No export files appeared under $exportRoot"
            }

            $stopResponse = Invoke-RconCommand -State $rcon -Command 'stop' -TimeoutSeconds $RconTimeoutSeconds
            if ($stopResponse) {
                Write-Status $stopResponse
            }

            if (-not $server.Process.WaitForExit($StartupTimeoutSeconds * 1000)) {
                throw "Server did not stop within $StartupTimeoutSeconds seconds"
            }

            $duration = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
            $runRoot = Copy-RunArtifacts -ServerRootValue $BenchmarkServerRoot -DestinationRoot $OutputRoot -RunName $runName

            $manifest = [ordered]@{
                label                  = $candidate.Label
                layer                  = $layer.Label
                source                 = $candidate.Source
                jarPath                = $jarPath
                jarSha256              = $candidateJarSha
                cleanroomCoreSha256    = $CleanroomCoreSha256.ToUpperInvariant()
                javaHome               = $javaHomeValue
                javaVersion            = Get-JavaVersionText -JavaExe $javaExe
                artifacts              = Get-ArtifactManifest -Paths (@($jarPath) + $layerModJars + @(
                    $CleanroomInstallerJar,
                    (Join-Path $BenchmarkServerRoot 'cleanroom-0.1.0.jar'),
                    (Join-Path $BenchmarkServerRoot 'server.properties')
                ))
                serverRoot             = $BenchmarkServerRoot
                outputRoot             = $OutputRoot
                runRoot                = $runRoot
                runCommands            = $runCommandList
                benchmarkMode          = $BenchmarkMode
                exportCommands         = $exportCommandList
                completionPattern      = $CompletionPattern
                startedAt              = $startedAt.ToString('o')
                finishedAt             = (Get-Date).ToString('o')
                durationSeconds        = $duration
            }
            Write-RunManifest -RunRoot $runRoot -Data $manifest
            [void]$summaryLines.Add((Format-SummaryRow -Row @{
                Label = $candidate.Label
                Layer = $layer.Label
                Source = $candidate.Source
                JarSha256 = $candidateJarSha
                Status = 'passed'
                DurationSeconds = $duration
                RunRoot = $runRoot
            }))
            $summaryRecords += [pscustomobject]@{
                label = $candidate.Label
                layer = $layer.Label
                source = $candidate.Source
                status = 'passed'
                detail = ''
                durationSeconds = $duration
                runJson = Join-Path $runRoot 'run.json'
            }
            Write-Status "Finished $($candidate.Label) layer $($layer.Label) in $duration s"
        } catch {
            $duration = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
            $candidateError = $_.Exception.Message
            $latestLogPath = Join-Path $BenchmarkServerRoot 'logs\latest.log'
            if (Test-Path -LiteralPath $latestLogPath) {
                $latestLogText = Get-Content -LiteralPath $latestLogPath -Raw
                if ($latestLogText -match "NoSuchMethodError: 'void jdk.internal.misc.Unsafe.putObject") {
                    $fatalEnvironmentReason = 'CatRoom core Forge capability initialization is incompatible with the selected Java runtime: jdk.internal.misc.Unsafe.putObject is unavailable'
                    $candidateError = 'environment-blocked: ' + $fatalEnvironmentReason
                }
            }
            try {
                if ($rcon -and $rcon.Client.Connected) {
                    [void](Invoke-RconCommand -State $rcon -Command 'stop' -TimeoutSeconds $RconTimeoutSeconds)
                }
            } catch {
            }
            try {
                if (-not $server.Process.HasExited) {
                    $server.Process.Kill()
                }
            } catch {
            }

            $runRoot = Copy-RunArtifacts -ServerRootValue $BenchmarkServerRoot -DestinationRoot $OutputRoot -RunName $runName
            $manifest = [ordered]@{
                label = $candidate.Label
                layer = $layer.Label
                source = $candidate.Source
                jarPath = $jarPath
                jarSha256 = $candidateJarSha
                cleanroomCoreSha256 = $CleanroomCoreSha256.ToUpperInvariant()
                javaHome = $javaHomeValue
                artifacts = Get-ArtifactManifest -Paths (@($jarPath) + $layerModJars + @(
                    $CleanroomInstallerJar,
                    (Join-Path $BenchmarkServerRoot 'cleanroom-0.1.0.jar'),
                    (Join-Path $BenchmarkServerRoot 'server.properties')
                ))
                serverRoot = $BenchmarkServerRoot
                outputRoot = $OutputRoot
                runRoot = $runRoot
                benchmarkMode = $BenchmarkMode
                startedAt = $startedAt.ToString('o')
                finishedAt = (Get-Date).ToString('o')
                durationSeconds = $duration
                error = $candidateError
            }
            Write-RunManifest -RunRoot $runRoot -Data $manifest
            [void]$summaryLines.Add((Format-SummaryRow -Row @{
                Label = $candidate.Label
                Layer = $layer.Label
                Source = $candidate.Source
                JarSha256 = $candidateJarSha
                Status = $candidateError
                DurationSeconds = $duration
                RunRoot = $runRoot
            }))
            $summaryRecords += [pscustomobject]@{
                label = $candidate.Label
                layer = $layer.Label
                source = $candidate.Source
                status = $candidateError
                detail = $candidateError
                durationSeconds = $duration
                runJson = Join-Path $runRoot 'run.json'
            }
        } finally {
            if ($rcon) {
                $rcon.Reader.Dispose()
                $rcon.Writer.Dispose()
                $rcon.Stream.Dispose()
                $rcon.Client.Close()
            }
        }
        if ($fatalEnvironmentReason) {
            break
        }
    }
    if ($fatalEnvironmentReason) {
        break
    }
}

$summaryPath = Join-Path $OutputRoot 'summary.md'
$summaryLines | Set-Content -LiteralPath $summaryPath -Encoding UTF8
Write-Status "Summary written to $summaryPath"

$summaryRecords | Export-Csv -LiteralPath (Join-Path $OutputRoot 'summary.csv') -NoTypeInformation -Encoding UTF8
ConvertTo-Json -InputObject @($summaryRecords) -Depth 6 |
    Set-Content -LiteralPath (Join-Path $OutputRoot 'summary.json') -Encoding UTF8

if (-not $KeepWorktree -and $worktreeInfo) {
    $worktreePath = $worktreeInfo.WorktreePath
    if (Test-Path -LiteralPath $worktreePath) {
        Assert-PathUnderRoot -Root $WorktreeRoot -Path $worktreePath -Label 'worktree'
        Write-Status "Removing temporary worktree $worktreePath"
        Remove-GitWorktreeSafe -RepoRootValue $repoRoot -WorktreeRootValue $WorktreeRoot -WorktreePath $worktreePath
    }
}
