# SilkVis media helper: Windows Global System Media Transport Controls over WinRT.
#
# Speaks the SilkVis NDJSON bridge protocol on stdin/stdout (one JSON object per line,
# schemaVersion 1). stdout is protocol only; anything human-readable goes to stderr.
#
# This file is intentionally ASCII-only: Windows PowerShell 5.1 decodes .ps1 as ANSI when
# there is no BOM, so non-ASCII text would arrive as mojibake. Track metadata is passed
# through ConvertTo-Json, which escapes non-ASCII as \uXXXX, so Cyrillic titles survive.
#
# Usage:
#   powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File <this> -Probe
#       one-shot snapshot, for diagnosing a machine by hand
#   powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File <this>
#       bridge mode: reads requests, answers on stdout, exits on SHUTDOWN/EOF
#
# Protocol requests: HELLO, SNAPSHOT_REQUEST, SELECT_SESSION, COMMAND, ARTWORK_REQUEST,
# SHUTDOWN. Responses: READY, SESSIONS, SNAPSHOT, COMMAND_RESULT, ARTWORK, ERROR.

param(
    [switch]$Probe,
    [switch]$ProbeArtwork,
    [string]$PinnedSession = ''
)

$ErrorActionPreference = 'Stop'
$script:SchemaVersion = 1
$script:Pinned = $PinnedSession
$script:Generation = @{}
$script:Revision = 0

try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

function Write-Protocol([string]$Type, $Payload, $RequestId) {
    $message = [ordered]@{
        schemaVersion = $script:SchemaVersion
        type          = $Type
        payload       = $Payload
    }
    if ($null -ne $RequestId -and "$RequestId" -ne '') {
        $message['requestId'] = [long]$RequestId
    }
    $json = ConvertTo-Json -InputObject $message -Compress -Depth 8
    [Console]::Out.WriteLine($json)
    [Console]::Out.Flush()
}

function Write-Failure([string]$Code, [string]$Detail) {
    $payload = [ordered]@{ code = $Code }
    if ($Detail) { $payload['detail'] = $Detail }
    Write-Protocol 'ERROR' $payload $null
}

function Write-Diag([string]$Text) {
    [Console]::Error.WriteLine("[silkvis-helper] $Text")
}

# ---------------------------------------------------------------- WinRT plumbing

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null
} catch {
    Write-Failure 'winrt_unavailable' $_.Exception.GetType().Name
    exit 2
}

$script:AsTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

if ($null -eq $script:AsTaskGeneric) {
    Write-Failure 'winrt_astask_missing' $null
    exit 2
}

function Await-Operation($Operation, [Type]$ResultType) {
    if ($null -eq $Operation) { return $null }
    $method = $script:AsTaskGeneric.MakeGenericMethod($ResultType)
    $task = $method.Invoke($null, @($Operation))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

$ManagerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$MediaPropsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]

try {
    $script:Manager = Await-Operation ($ManagerType::RequestAsync()) $ManagerType
} catch {
    Write-Failure 'gsmtc_unavailable' $_.Exception.GetType().Name
    exit 3
}

if ($null -eq $script:Manager) {
    Write-Failure 'gsmtc_unavailable' 'no_manager'
    exit 3
}

# ---------------------------------------------------------------- metadata helpers

# Friendly names for the sources people actually run; unknown ids are shown as-is
# (lowercased, without the .exe) rather than being guessed from a brand name.
function Get-FriendlyName([string]$AppId) {
    if (-not $AppId) { return '' }
    $map = @{
        'spotify.exe'                          = 'Spotify'
        'spotify'                              = 'Spotify'
        'chrome.exe'                           = 'Chrome'
        'msedge.exe'                           = 'Edge'
        'firefox.exe'                          = 'Firefox'
        'yandexmusic.exe'                       = 'Yandex Music'
        'yandexmusic'                          = 'Yandex Music'
        'yandexdesktop.exe'                    = 'Yandex Music'
        'vlc.exe'                              = 'VLC'
        'aimp.exe'                             = 'AIMP'
        'foobar2000.exe'                       = 'foobar2000'
        'music.ui.exe'                         = 'Media Player'
        'soundcloud.exe'                       = 'SoundCloud'
        'spotifyab.spotifymusic_zpdnekdrzrea0' = 'Spotify'
    }
    $key = $AppId.ToLowerInvariant()
    if ($map.ContainsKey($key)) { return $map[$key] }
    $short = $key
    if ($short.EndsWith('.exe')) { $short = $short.Substring(0, $short.Length - 4) }
    return $short
}

function Get-SessionId($Session) {
    if ($null -eq $Session) { return '' }
    return [string]$Session.SourceAppUserModelId
}

# "Current" is whatever Windows itself reports as the active session; if that is empty we
# fall back to the pinned id, then to any session that is actually playing.
function Get-TargetSession {
    $sessions = @($script:Manager.GetSessions())
    if ($script:Pinned) {
        foreach ($s in $sessions) {
            if ((Get-SessionId $s) -eq $script:Pinned) { return $s }
        }
    }
    $current = $null
    try { $current = $script:Manager.GetCurrentSession() } catch { $current = $null }
    if ($null -ne $current) { return $current }
    foreach ($s in $sessions) {
        try {
            if ($s.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') { return $s }
        } catch { }
    }
    if ($sessions.Count -gt 0) { return $sessions[0] }
    return $null
}

function Get-SessionList($Current) {
    $currentId = Get-SessionId $Current
    $items = New-Object System.Collections.ArrayList
    foreach ($s in @($script:Manager.GetSessions())) {
        $id = Get-SessionId $s
        if (-not $id) { continue }
        $entry = [ordered]@{
            sessionId   = $id
            appId       = $id
            displayName = (Get-FriendlyName $id)
            current     = ($id -eq $currentId)
        }
        [void]$items.Add($entry)
    }
    return , @($items.ToArray())
}

function Convert-PlaybackStatus($Status) {
    switch ("$Status") {
        'Playing' { return 'PLAYING' }
        'Paused' { return 'PAUSED' }
        'Stopped' { return 'STOPPED' }
        default { return 'UNKNOWN' }
    }
}

function Get-Capabilities($Session) {
    $caps = New-Object System.Collections.ArrayList
    try {
        $controls = $Session.GetPlaybackInfo().Controls
        if ($controls.IsPlayEnabled) { [void]$caps.Add('PLAY') }
        if ($controls.IsPauseEnabled) { [void]$caps.Add('PAUSE') }
        if ($controls.IsNextEnabled) { [void]$caps.Add('NEXT') }
        if ($controls.IsPreviousEnabled) { [void]$caps.Add('PREVIOUS') }
    } catch { }
    try {
        $timeline = $Session.GetTimelineProperties()
        if ($timeline.MaxSeekTime -gt $timeline.MinSeekTime) { [void]$caps.Add('SEEK') }
    } catch { }
    return , @($caps.ToArray())
}

function Get-Ms($TimeSpan) {
    if ($null -eq $TimeSpan) { return $null }
    return [long][Math]::Round($TimeSpan.TotalMilliseconds)
}

function Get-SnapshotPayload($Session) {
    $id = Get-SessionId $Session
    if (-not $id) { return $null }

    if (-not $script:Generation.ContainsKey($id)) {
        $script:Generation[$id] = [long]($script:Generation.Count + 1)
    }
    $script:Revision = $script:Revision + 1

    $title = $null; $artist = $null; $album = $null; $artworkRef = $null
    try {
        $props = Await-Operation ($Session.TryGetMediaPropertiesAsync()) $MediaPropsType
        if ($null -ne $props) {
            if ($props.Title) { $title = [string]$props.Title }
            if ($props.Artist) { $artist = [string]$props.Artist }
            if ($props.AlbumTitle) { $album = [string]$props.AlbumTitle }
            if ($null -ne $props.Thumbnail) { $artworkRef = "thumb:$id" }
        }
    } catch {
        Write-Diag "media properties unavailable: $($_.Exception.GetType().Name)"
    }

    $state = 'UNKNOWN'
    try { $state = Convert-PlaybackStatus $Session.GetPlaybackInfo().PlaybackStatus } catch { }

    $positionMs = $null; $startMs = $null; $endMs = $null
    $minSeekMs = $null; $maxSeekMs = $null; $rate = $null
    try {
        $timeline = $Session.GetTimelineProperties()
        $positionMs = Get-Ms $timeline.Position
        $startMs = Get-Ms $timeline.StartTime
        $endMs = Get-Ms $timeline.EndTime
        $minSeekMs = Get-Ms $timeline.MinSeekTime
        $maxSeekMs = Get-Ms $timeline.MaxSeekTime
    } catch { }
    try { $rate = [double]$Session.GetPlaybackInfo().PlaybackRate } catch { $rate = $null }
    if ($endMs -ne $null -and $endMs -le 0) { $endMs = $null }

    $payload = [ordered]@{
        sessionId         = $id
        sessionGeneration = [long]$script:Generation[$id]
        revision          = [long]$script:Revision
        sourceAppId       = $id
        sourceDisplayName = (Get-FriendlyName $id)
        title             = $title
        artist            = $artist
        album             = $album
        artworkRef        = $artworkRef
        playbackState     = $state
        positionMs        = $positionMs
        startMs           = $startMs
        endMs             = $endMs
        minSeekMs         = $minSeekMs
        maxSeekMs         = $maxSeekMs
        playbackRate      = $rate
        capabilities      = (Get-Capabilities $Session)
    }

    # Blank strings are reported as null: an empty title is "unknown", not a title.
    foreach ($key in @('title', 'artist', 'album')) {
        if ($payload[$key] -eq '') { $payload[$key] = $null }
    }
    return $payload
}

function Send-Snapshot {
    $target = Get-TargetSession
    Write-Protocol 'SESSIONS' ([ordered]@{ sessions = (Get-SessionList $target) }) $null
    if ($null -eq $target) { return }
    $snapshot = Get-SnapshotPayload $target
    if ($null -ne $snapshot) {
        Write-Protocol 'SNAPSHOT' $snapshot $null
    }
}

# ---------------------------------------------------------------- commands

function Invoke-Command($Payload) {
    $requestId = if ($Payload.PSObject.Properties['requestId']) { $Payload.requestId } else { $null }
    $action = ''
    if ($Payload.PSObject.Properties['action']) { $action = [string]$Payload.action }
    $sessionId = if ($Payload.PSObject.Properties['sessionId']) { [string]$Payload.sessionId } else { '' }

    $target = $null
    foreach ($s in @($script:Manager.GetSessions())) {
        if ((Get-SessionId $s) -eq $sessionId) { $target = $s; break }
    }
    if ($null -eq $target) {
        # Command addressed at a session that is gone: reject, never fall back to a media
        # key, otherwise the action could hit a different player.
        Write-Protocol 'COMMAND_RESULT' ([ordered]@{
                requestId = [long]$requestId
                status    = 'REJECTED'
                errorCode = 'session_gone'
            }) $requestId
        return
    }

    $ok = $false
    try {
        switch ($action) {
            'PLAY' { $ok = Await-Operation ($target.TryPlayAsync()) ([bool]) }
            'PAUSE' { $ok = Await-Operation ($target.TryPauseAsync()) ([bool]) }
            'TOGGLE' {
                $state = Convert-PlaybackStatus $target.GetPlaybackInfo().PlaybackStatus
                if ($state -eq 'PLAYING') { $ok = Await-Operation ($target.TryPauseAsync()) ([bool]) }
                else { $ok = Await-Operation ($target.TryPlayAsync()) ([bool]) }
            }
            'NEXT' { $ok = Await-Operation ($target.TrySkipNextAsync()) ([bool]) }
            'PREVIOUS' { $ok = Await-Operation ($target.TrySkipPreviousAsync()) ([bool]) }
            'SEEK' {
                $position = $null
                if ($Payload.PSObject.Properties['seekPositionMs']) { $position = [long]$Payload.seekPositionMs }
                if ($null -eq $position) {
                    Write-Protocol 'COMMAND_RESULT' ([ordered]@{
                            requestId = [long]$requestId
                            status    = 'REJECTED'
                            errorCode = 'seek_without_position'
                        }) $requestId
                    return
                }
                $ticks = [long]($position * 10000)
                $ok = Await-Operation ($target.TryChangePlaybackPositionAsync($ticks)) ([bool])
            }
            default {
                Write-Protocol 'COMMAND_RESULT' ([ordered]@{
                        requestId = [long]$requestId
                        status    = 'REJECTED'
                        errorCode = 'unsupported_action'
                    }) $requestId
                return
            }
        }
    } catch {
        Write-Diag "command $action failed: $($_.Exception.GetType().Name)"
        $ok = $false
    }

    Write-Protocol 'COMMAND_RESULT' ([ordered]@{
            requestId         = [long]$requestId
            status            = $(if ($ok) { 'APPLIED' } else { 'REJECTED' })
            errorCode         = $(if ($ok) { $null } else { 'not_supported_by_source' })
            resultingRevision = [long]$script:Revision
        }) $requestId

    # Report the new state immediately instead of waiting for the next poll.
    Send-Snapshot
}

function Send-Artwork($Payload, [switch]$Summary) {
    $sessionId = if ($Payload.PSObject.Properties['sessionId']) { [string]$Payload.sessionId } else { '' }
    $target = $null
    foreach ($s in @($script:Manager.GetSessions())) {
        if ((Get-SessionId $s) -eq $sessionId) { $target = $s; break }
    }
    if ($null -eq $target) {
        Write-Failure 'artwork_session_gone' $null
        return
    }
    $ref = if ($Payload.PSObject.Properties['artworkRef']) { [string]$Payload.artworkRef } else { '' }
    $base64 = ''
    $mime = ''
    $byteCount = 0
    $reason = ''
    $stream = $null
    $reader = $null
    try {
        $props = Await-Operation ($target.TryGetMediaPropertiesAsync()) $MediaPropsType
        if ($null -eq $props -or $null -eq $props.Thumbnail) {
            $reason = 'no_thumbnail'
        } else {
            $stream = Await-Operation ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
            if ($null -eq $stream) {
                $reason = 'open_failed'
            } else {
                $size = [int]$stream.Size
                if ($size -le 8) {
                    $reason = 'empty_stream'
                } elseif ($size -gt 524288) {
                    $reason = 'too_large'
                } else {
                    $reader = New-Object -TypeName Windows.Storage.Streams.DataReader -ArgumentList $stream
                    # One LoadAsync may deliver fewer bytes than asked for; a truncated JPEG
                    # decodes to garbage, which is why the cover never appeared. Read until
                    # the whole thumbnail is buffered (bounded by the size check above).
                    $spins = 0
                    while ($reader.UnconsumedBufferLength -lt $size -and $spins -lt 32) {
                        $spins++
                        $want = [Math]::Min(65536, $size - $reader.UnconsumedBufferLength)
                        $got = Await-Operation ($reader.LoadAsync([uint32]$want)) ([uint32])
                        if ($null -eq $got -or $got -le 0) { break }
                    }
                    # Use what actually arrived: a short read is reported, never passed off
                    # as a complete picture (a truncated JPEG decodes to noise or throws).
                    $available = [int]$reader.UnconsumedBufferLength
                    if ($available -le 8) {
                        $reason = 'empty_stream'
                    } else {
                        $buffer = [byte[]]::new($available)
                        $reader.ReadBytes($buffer)
                        $byteCount = $available
                        if ($available -lt $size) { Write-Diag "artwork truncated: $available of $size" }
                        # Spotify and Chrome publish JPEG thumbnails. Until 0.1.18 this branch
                        # only forwarded PNG, so the picture was dropped every single time.
                        $isPng = $buffer[0] -eq 0x89 -and $buffer[1] -eq 0x50 -and $buffer[2] -eq 0x4E -and $buffer[3] -eq 0x47
                        $isJpeg = $buffer[0] -eq 0xFF -and $buffer[1] -eq 0xD8 -and $buffer[2] -eq 0xFF
                        $isGif = $buffer[0] -eq 0x47 -and $buffer[1] -eq 0x49 -and $buffer[2] -eq 0x46
                        $isBmp = $buffer[0] -eq 0x42 -and $buffer[1] -eq 0x4D
                        if ($isPng) { $mime = 'image/png' }
                        elseif ($isJpeg) { $mime = 'image/jpeg' }
                        elseif ($isGif) { $mime = 'image/gif' }
                        elseif ($isBmp) { $mime = 'image/bmp' }
                        if ($mime -ne '') {
                            $base64 = [Convert]::ToBase64String($buffer)
                        } else {
                            $reason = 'not_an_image'
                        }
                    }
                }
            }
        }
    } catch {
        $reason = 'exception'
        Write-Diag "artwork failed: $($_.Exception.GetType().Name)"
    } finally {
        if ($null -ne $reader) { try { $reader.Dispose() } catch { } }
        if ($null -ne $stream) { try { $stream.Dispose() } catch { } }
    }
    if ($reason -ne '' -and $reason -ne 'no_thumbnail') {
        Write-Diag "artwork: $reason ($byteCount bytes)"
    }
    if ($Summary) {
        # Human-readable, for -ProbeArtwork: no base64 in the terminal.
        [Console]::Error.WriteLine("[silkvis-helper] artwork mime=$mime bytes=$byteCount reason=$reason ref=$ref")
        return
    }
    Write-Protocol 'ARTWORK' ([ordered]@{
            artworkRef = $ref
            mime       = $mime
            bytes      = [int]$byteCount
            reason     = $reason
            pngBase64  = $base64
        }) $null
}

# ---------------------------------------------------------------- entry points

if ($Probe) {
    Send-Snapshot
    exit 0
}

# Cover probe: one command that says whether the thumbnail can be read at all, and in
# which format. Prints a summary to stderr instead of dumping base64 into the terminal.
if ($ProbeArtwork) {
    $target = Get-TargetSession
    if ($null -eq $target) {
        Write-Failure 'no_session' $null
        exit 0
    }
    Send-Artwork ([pscustomobject]@{ sessionId = (Get-SessionId $target); artworkRef = 'probe' }) -Summary
    exit 0
}

Write-Protocol 'READY' ([ordered]@{
        provider        = 'windows-powershell'
        protocolVersion = $script:SchemaVersion
    }) $null

while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0) { continue }

    $message = $null
    try { $message = $trimmed | ConvertFrom-Json } catch {
        Write-Failure 'bad_json' $null
        continue
    }
    if ($null -eq $message -or -not $message.PSObject.Properties['type']) {
        Write-Failure 'missing_type' $null
        continue
    }

    $payload = $null
    if ($message.PSObject.Properties['payload']) { $payload = $message.payload }

    switch ([string]$message.type) {
        'HELLO' { Write-Protocol 'READY' ([ordered]@{
                    provider = 'windows-powershell'; protocolVersion = $script:SchemaVersion }) $null }
        'SNAPSHOT_REQUEST' { Send-Snapshot }
        'SELECT_SESSION' {
            if ($null -ne $payload -and $payload.PSObject.Properties['sessionId']) {
                $script:Pinned = [string]$payload.sessionId
            }
            Send-Snapshot
        }
        'COMMAND' { if ($null -ne $payload) { Invoke-Command $payload } }
        'ARTWORK_REQUEST' { if ($null -ne $payload) { Send-Artwork $payload } }
        'SHUTDOWN' { break }
        default { }
    }
    if ([string]$message.type -eq 'SHUTDOWN') { break }
}

exit 0
