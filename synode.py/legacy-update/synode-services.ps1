<#
.SYNOPSIS
  Show, stop, start a Windows synode's 2 services, or upgrade a synode installed before the upgrade tool.

.DESCRIPTION
  For synodes installed by packages without winsrv/stop-winsrv.bat, e.g. the 0.8.0 of 2026-08, whose
  jserv-album jar doesn't stop as a service (jvmStop() calls System.exit(), the service stays STOP_PENDING).
  The services are the 2 saved in WEB-INF/settings.json envars (winsrv.synode, winsrv.web), as upgrade.exe
  reads them.

    status   the services' states and processes (default)
    stop     sc stop; a service still not stopped after -Wait seconds has its process killed
    start    sc start, waits for RUNNING
    backup   stop, back up the local data and settings (below), start again
    upgrade  stop, back up, ask to go on (unless -Yes), then the new package's upgrade.exe (its upgrade.pkg,
             unless -KeepTool) with the package: it backs up again, unpacks, copies back and restarts the
             services (synodepy3 commands.update_srv())

  The backup, made while the services are stopped, goes to a new folder <root>/pre-upgrade-<yyyyMMdd-HHmmss>,
  never written again: the volume's dictionary.json, *.db, syntity.json; WEB-INF/; web-dist/private/;
  bin/resource.json. Not the documents in the volume, which neither this
  nor the upgrade changes.
  It is made because upgrade.exe's own backup, backup-<yyyyMMdd>/, is overwritten by a second run on the
  same day: after an aborted or failed upgrade, a retry backs up the files as the failed run left them.

  Windows PowerShell 5.1 or newer, nothing else. Copy the folder legacy-update\ into the installation folder
  (where WEB-INF\ is), then run, in the installation folder:

    legacy-update\update.bat <new package zip>

  or legacy-update\stop-services.bat, start-services.bat, backup-services.bat; or:

    powershell -ExecutionPolicy Bypass -File synode-services.ps1 -Action upgrade -Package <zip> [-Root <folder>]

  It asks the administrator permission once (UAC) if needed and continues in a new window, which keeps
  a transcript, <root>/synode-services.log.

.PARAMETER Root
  The installation folder (where WEB-INF/ is), default: the current folder. The .bat files give the
  folder above legacy-update\ if it has WEB-INF\, else the current folder.
#>
param(
    [ValidateSet('status', 'stop', 'start', 'backup', 'upgrade')]
    [string]$Action = 'status',
    [string]$Package,
    [string]$Root,
    [int]$Wait = 40,
    [switch]$KeepTool,
    [switch]$Yes,
    [switch]$Pause
)

$ErrorActionPreference = 'Stop'

function Test-Admin {
    $p = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-ServiceNames {
    $settings = Join-Path $Root 'WEB-INF\settings.json'
    if (-not (Test-Path $settings)) {
        throw "$settings not found. Run this in the installation folder, or give -Root."
    }
    $envars = (Get-Content -Raw -Encoding UTF8 $settings | ConvertFrom-Json).envars
    $names = @($envars.'winsrv.synode', $envars.'winsrv.web') | Where-Object { $_ }
    if ($names.Count -ne 2) {
        throw "No service names in $settings envars (winsrv.synode, winsrv.web), which the upgrade also needs. " +
              "Find them with: sc.exe query state= all | findstr Synode"
    }
    return $names
}

function Get-Srv([string]$name) {
    return Get-CimInstance Win32_Service -Filter "Name='$name'"
}

function Show-Srv([string]$name) {
    $s = Get-Srv $name
    if ($s) { Write-Host ("{0}: {1} (pid {2})" -f $name, $s.State, $s.ProcessId) }
    else { Write-Host "${name}: not installed" }
}

function Wait-State([string]$name, [string[]]$done, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ($true) {
        $s = Get-Srv $name
        $state = if ($s) { $s.State } else { $null }
        if (($done -contains $state) -or ((Get-Date) -ge $deadline)) { return $state }
        Start-Sleep -Seconds 1
    }
}

function Stop-Srv([string]$name) {
    $s = Get-Srv $name
    if (-not $s -or $s.State -eq 'Stopped') { Show-Srv $name; return }
    if ($s.State -ne 'Stop Pending') { sc.exe stop $name | Out-Null }
    $state = Wait-State $name @('Stopped', $null) $Wait
    if ($state -and $state -ne 'Stopped') {
        $id = (Get-Srv $name).ProcessId
        Write-Host "$name is still $state, killing its process $id ..."
        if ($id) { Stop-Process -Id $id -Force -ErrorAction Continue }
        $state = Wait-State $name @('Stopped', $null) 30
    }
    if ($state -and $state -ne 'Stopped') { throw "$name can't be stopped, still $state." }
    Show-Srv $name
}

function Start-Srv([string]$name) {
    if (-not (Get-Srv $name)) { throw "$name is not installed." }
    sc.exe start $name | Out-Null
    $state = Wait-State $name @('Running', 'Stopped') $Wait
    Show-Srv $name
    if ($state -ne 'Running') { throw "$name is not running. See logs\commons-daemon.*.log and the service's logs." }
}

function Backup-Local {
    <# The local data and settings, to a new pre-upgrade-<time> folder; the services must be stopped. #>
    $settings = Join-Path $Root 'WEB-INF\settings.json'
    $vol = (Get-Content -Raw -Encoding UTF8 $settings | ConvertFrom-Json).volume
    if (-not $vol -or -not [IO.Path]::IsPathRooted($vol)) {
        throw "settings.json volume is not an absolute path: '$vol'. The upgrade requires one; fix it first."
    }
    if (-not (Test-Path $vol)) { throw "The volume $vol doesn't exist." }
    $dir = Join-Path $Root ('pre-upgrade-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    if (Test-Path $dir) { throw "$dir exists." }

    # [source, destination under $dir, required]
    $items = @(
        @((Join-Path $vol 'dictionary.json'), 'vol\dictionary.json', $true),
        @((Join-Path $vol 'jserv-main.db'), 'vol\jserv-main.db', $true),
        @((Join-Path $vol 'doc-jserv.db'), 'vol\doc-jserv.db', $true),
        @((Join-Path $vol 'syntity.json'), 'vol\syntity.json', $false),
        @((Join-Path $Root 'bin\resource.json'), 'bin\resource.json', $false))
    foreach ($d in 'WEB-INF', 'web-dist\private') {
        $src = (Join-Path $Root $d)
        if (Test-Path $src) {
            $src = (Resolve-Path $src).Path
            Get-ChildItem -Recurse -File $src | ForEach-Object {
                $items += ,@($_.FullName, (Join-Path $d $_.FullName.Substring($src.Length).TrimStart('\', '/')), $true)
            }
        }
    }

    New-Item -ItemType Directory -Path $dir | Out-Null
    foreach ($i in $items) {
        $src, $rel, $required = $i
        if (-not (Test-Path $src)) {
            if ($required) { throw "Missing: $src. Nothing is upgraded; the partial backup is in $dir." }
            Write-Host "Skipped (not found): $src"
            continue
        }
        $dst = Join-Path $dir $rel
        New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
        Copy-Item -LiteralPath $src -Destination $dst
        Write-Host "Backed up: $src"
    }

    Write-Host ''
    Write-Host '================================================================' -ForegroundColor Yellow
    Write-Host " Backed up to: $dir" -ForegroundColor Yellow
    Write-Host ' Keep this folder until the synode works after the upgrade.' -ForegroundColor Yellow
    Write-Host ' upgrade.exe makes its own backup-<date>\, but overwrites it if run again on the' -ForegroundColor Yellow
    Write-Host ' same day (e.g. retrying after an abort): this one is the safe copy.' -ForegroundColor Yellow
    Write-Host ' To restore: legacy-update\stop-services.bat, then copy back' -ForegroundColor Yellow
    Write-Host "   $dir\vol\*  ->  $vol" -ForegroundColor Yellow
    Write-Host "   $dir\WEB-INF, web-dist, bin  ->  $Root" -ForegroundColor Yellow
    Write-Host '================================================================' -ForegroundColor Yellow
    return $dir
}

function Expand-Tool {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Package)
    try {
        $e = $zip.Entries | Where-Object { ($_.FullName -replace '^\./', '') -eq 'upgrade.pkg' } | Select-Object -First 1
        if (-not $e) { throw "$Package has no upgrade.pkg. Copy the newest upgrade.exe here and use -KeepTool." }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $Root 'upgrade.exe'), $true)
    } finally { $zip.Dispose() }
    Write-Host "${Package}:upgrade.pkg -> $(Join-Path $Root 'upgrade.exe')"
}

function Invoke-Upgrade {
    if (-not $Package -or -not (Test-Path $Package)) { throw "Give the new package: -Package <zip>" }
    while (Get-Process album_gui -ErrorAction SilentlyContinue) {
        if ((Read-Host 'The desktop app (album_gui) is running. Close it, then press Enter (q to quit)') -eq 'q') {
            throw 'Aborted, nothing is changed.'
        }
    }
    $exe = Join-Path $Root 'upgrade.exe'
    if ($KeepTool) { if (-not (Test-Path $exe)) { throw "No $exe." } } else { Expand-Tool }

    foreach ($n in Get-ServiceNames) { Stop-Srv $n }
    try { $backup = Backup-Local }
    catch { Write-Host 'Backup failed, starting the services again ...'; foreach ($n in Get-ServiceNames) { Start-Srv $n }; throw }
    if (-not $Yes -and (Read-Host "Upgrade now with $Package ? [y/N]") -ne 'y') {
        foreach ($n in Get-ServiceNames) { Start-Srv $n }
        throw "Not upgraded, the services are started again. The backup is kept: $backup"
    }

    # upgrade_cli.py ends with input('Press Enter to quit ...'): answered by the piped empty line.
    # A native command's stderr, redirected, is an error record: don't stop on it.
    Write-Host "upgrade.exe $Package"
    $complete = $false
    $ErrorActionPreference = 'Continue'
    '' | & $exe $Package 2>&1 | ForEach-Object {
        $line = "$_"
        Write-Host $line
        if ($line -match 'Update complete') { $complete = $true }
    }
    $ErrorActionPreference = 'Stop'
    if (-not $complete) {
        throw "The upgrade did not complete, see its output above. Before retrying, keep the backup $backup " +
              "(upgrade.exe's backup-<date>\ will be overwritten by the retry)."
    }
    foreach ($n in Get-ServiceNames) { Show-Srv $n }   # the names can change with the versions
    Write-Host "Upgraded. The backup before the upgrade is kept: $backup"
}

# ---- main ----
if (-not $Root) { $Root = (Get-Location).Path }
$Root = (Resolve-Path $Root).Path
if ($Package) { $Package = (Resolve-Path $Package).Path }

if ($Action -ne 'status' -and -not (Test-Admin)) {
    $a = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"", '-Action', $Action,
           '-Root', "`"$Root`"", '-Wait', $Wait, '-Pause')
    if ($Package) { $a += @('-Package', "`"$Package`"") }
    if ($KeepTool) { $a += '-KeepTool' }
    if ($Yes) { $a += '-Yes' }
    try { Start-Process powershell -Verb RunAs -ArgumentList $a }
    catch { Write-Host 'Administrator permission is required.' -ForegroundColor Red; exit 1 }
    Write-Host 'Continued in the Administrator window.'
    exit 0
}

Set-Location $Root
$code = 0
if ($Action -ne 'status') { Start-Transcript -Append -Path (Join-Path $Root 'synode-services.log') | Out-Null }
try {
    switch ($Action) {
        'status'  { foreach ($n in Get-ServiceNames) { Show-Srv $n } }
        'stop'    { foreach ($n in Get-ServiceNames) { Stop-Srv $n } }
        'start'   { foreach ($n in Get-ServiceNames) { Start-Srv $n } }
        'backup'  {
            $names = Get-ServiceNames
            foreach ($n in $names) { Stop-Srv $n }
            try { Backup-Local | Out-Null } finally { foreach ($n in $names) { Start-Srv $n } }
        }
        'upgrade' { Invoke-Upgrade }
    }
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    $code = 1
} finally {
    if ($Action -ne 'status') { Stop-Transcript | Out-Null }
    if ($Pause) { Read-Host 'Press Enter to quit' | Out-Null }
}
exit $code
