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
    upgrade  check the package accepts the installed version, ask to disconnect the network, stop, back up,
             write bin/resource.json of the installed version if there is none, ask to go on (unless -Yes),
             then the new package's upgrade.exe (its upgrade.pkg, unless -KeepTool) with the package: it backs
             up again, unpacks, copies back, restarts the services, reinstalled first if the versions changed
             (synodepy3 commands.update_srv())

  The installed version: an installation without bin/resource.json (packages before 2026-10) gets one written
  from its services (the jars of their Procrun Classpath, or else their names Synode-<jar_ver>-<id>,
  Synode.web-<web_ver>-<id>). Without it upgrade.exe would take its own version for the installed one, and an
  older installation would keep running its old jar.
  See README.md.

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

function Compare-Ver([string]$a, [string]$b) {
    <# < 0, 0, > 0 as a is older than, the same as, newer than b ("0.8" = "0.8.0"), as semanticshare ver_cmp() #>
    $x = @([regex]::Matches($a, '\d+') | ForEach-Object { [int]$_.Value })
    $y = @([regex]::Matches($b, '\d+') | ForEach-Object { [int]$_.Value })
    for ($i = 0; $i -lt [Math]::Max($x.Count, $y.Count); $i++) {
        $u = if ($i -lt $x.Count) { $x[$i] } else { 0 }
        $v = if ($i -lt $y.Count) { $y[$i] } else { 0 }
        if ($u -ne $v) { return $u - $v }
    }
    return 0
}

function Read-ZipJson([string]$zip, [string]$name) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $z = [IO.Compression.ZipFile]::OpenRead($zip)
    try {
        $e = $z.Entries | Where-Object { ($_.FullName -replace '^\./', '') -eq $name } | Select-Object -First 1
        if (-not $e) { return $null }
        $r = New-Object IO.StreamReader($e.Open())
        try { return $r.ReadToEnd() | ConvertFrom-Json } finally { $r.Dispose() }
    } finally { $z.Dispose() }
}

function Get-ProcrunJar([string]$name) {
    <# The jar of a Procrun service (its Java Classpath), or $null #>
    foreach ($k in "HKLM:\SOFTWARE\WOW6432Node\Apache Software Foundation\Procrun 2.0\$name\Parameters\Java",
                   "HKLM:\SOFTWARE\Apache Software Foundation\Procrun 2.0\$name\Parameters\Java") {
        try {
            $cp = (Get-ItemProperty -Path $k -ErrorAction Stop).Classpath
            if ($cp) { return ($cp -split ';')[0] }
        } catch { }
    }
    return $null
}

function Get-Installed {
    <# The installed versions and jars: bin/resource.json, or else from the services. #>
    $res = Join-Path $Root 'bin\resource.json'
    if (Test-Path $res) {
        $r = Get-Content -Raw -Encoding UTF8 $res | ConvertFrom-Json
        return @{ jar_ver = $r.versions.jar_ver; web_ver = $r.versions.web_ver; html_srver = $r.versions.html_srver
                  synode = $r.files.synode; htmlweb = $r.files.'html-web'; manifest = $true }
    }
    $srv, $web = Get-ServiceNames
    if ($srv -notmatch '^Synode-([\d.]+)-' ) { throw "Can't read the version from the service name $srv." }
    $jver = $Matches[1]
    if ($web -notmatch '^Synode\.web-([\d.]+)-') { throw "Can't read the version from the service name $web." }
    $wver = $Matches[1]

    $jar = Get-ProcrunJar $srv
    if (-not $jar) { $jar = "bin\jserv-album-$jver.jar" }
    $hjar = Get-ProcrunJar $web
    if (-not $hjar) {
        $h = @(Get-ChildItem -Path (Join-Path $Root 'bin') -Filter 'html-web-*.jar' | Sort-Object Name)
        if ($h.Count -ne 1) { throw "Can't tell the web service's jar: bin\html-web-*.jar are $($h.Name -join ', ')." }
        $hjar = "bin\$($h[0].Name)"
    }
    $jar = $jar -replace '^[.\\/]+', '' -replace '\\', '/'
    $hjar = $hjar -replace '^[.\\/]+', '' -replace '\\', '/'
    if ($hjar -notmatch 'html-web-([\d.]+)\.jar$') { throw "Can't read the version of the web service's jar $hjar." }
    return @{ jar_ver = $jver; web_ver = $wver; html_srver = $Matches[1]; synode = $jar; htmlweb = $hjar; manifest = $false }
}

function Write-InstalledResources($inst) {
    <# bin/resource.json of the installed version, for upgrade.exe (semanticshare PkgResources) #>
    $json = [ordered]@{
        type = 'io.oz.syn.pkgresource.PkgResources'
        files = [ordered]@{ synode = $inst.synode; 'html-web' = $inst.htmlweb }
        versions = [ordered]@{ jar_ver = $inst.jar_ver; web_ver = $inst.web_ver; html_srver = $inst.html_srver }
        upgrade_from = @{}
    } | ConvertTo-Json -Depth 4
    $p = Join-Path $Root 'bin\resource.json'
    [IO.File]::WriteAllText($p, $json, (New-Object Text.UTF8Encoding $false))   # no BOM
    Write-Host "Written $p, the installed version $($inst.jar_ver)"
    return $p
}

function Test-Accepts($inst) {
    <# Does the package accept the installed version? Its bin/resource.json upgrade_from {min, max}, no downgrade. #>
    $pkg = Read-ZipJson $Package 'bin/resource.json'
    if (-not $pkg) { throw "$Package has no bin/resource.json, not a package this upgrade is for." }
    $mine, $lo, $hi = $pkg.versions.jar_ver, $pkg.upgrade_from.min, $pkg.upgrade_from.max
    $v = $inst.jar_ver
    if ($lo -and (Compare-Ver $v $lo) -lt 0) { throw "The installed version $v is older than $lo, the oldest the package $mine upgrades. Nothing is changed." }
    if ($hi -and (Compare-Ver $v $hi) -gt 0) { throw "The installed version $v is newer than $hi, the newest the package $mine upgrades. Nothing is changed." }
    if ($mine -and (Compare-Ver $v $mine) -gt 0) { throw "The installed version $v is newer than the package $mine (a downgrade). Nothing is changed." }
    Write-Host "Upgrading $v -> $mine"
}

function Confirm-Offline {
    <# Ask to disconnect the network: the synode must not exchange with its peers or the central while upgrading. #>
    Write-Host ''
    Write-Host '================================================================' -ForegroundColor Yellow
    Write-Host ' Please disconnect this computer from the network now' -ForegroundColor Yellow
    Write-Host ' (unplug the cable, turn off Wi-Fi), until the upgrade is done.' -ForegroundColor Yellow
    Write-Host ' A synode stopped in the middle of a synchronization or a registry' -ForegroundColor Yellow
    Write-Host ' update can leave doc-refs unresolved. The upgrade needs no network.' -ForegroundColor Yellow
    Write-Host '================================================================' -ForegroundColor Yellow
    while ($true) {
        $nets = @()
        try { $nets = @(Get-NetConnectionProfile -ErrorAction Stop | ForEach-Object { "$($_.InterfaceAlias) ($($_.Name))" }) } catch { }
        if ($nets.Count -eq 0) {
            if ($Yes) { return }
            if ((Read-Host 'Disconnected? Press Enter to go on (q to quit)') -eq 'q') { throw 'Aborted, nothing is changed.' }
            return
        }
        Write-Host "Still connected: $($nets -join ', ')" -ForegroundColor Yellow
        if ($Yes) { return }
        $a = Read-Host 'Disconnect, then press Enter to check again (c to continue anyway, q to quit)'
        if ($a -eq 'q') { throw 'Aborted, nothing is changed.' }
        if ($a -eq 'c') { return }
    }
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
    $inst = Get-Installed
    Test-Accepts $inst
    Confirm-Offline

    $exe = Join-Path $Root 'upgrade.exe'
    if ($KeepTool) { if (-not (Test-Path $exe)) { throw "No $exe." } } else { Expand-Tool }

    $names = Get-ServiceNames
    foreach ($n in $names) { Stop-Srv $n }
    try { $backup = Backup-Local }
    catch { Write-Host 'Backup failed, starting the services again ...'; foreach ($n in $names) { Start-Srv $n }; throw }
    $written = if ($inst.manifest) { $null } else { Write-InstalledResources $inst }
    if (-not $Yes -and (Read-Host "Upgrade now with $Package ? [y/N]") -ne 'y') {
        if ($written) { Remove-Item $written }
        foreach ($n in $names) { Start-Srv $n }
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
              "(upgrade.exe's backup-<date>\ will be overwritten by the retry). The services may be stopped: " +
              "legacy-update\start-services.bat starts them."
    }
    foreach ($n in Get-ServiceNames) { Show-Srv $n }   # the names can change with the versions
    Write-Host "Upgraded. The backup before the upgrade is kept: $backup"
    Write-Host 'You can connect this computer to the network again.' -ForegroundColor Green
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
