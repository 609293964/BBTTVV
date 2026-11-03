# Preview with -WhatIf. Only ignored, untracked build output and logs are removed.
[CmdletBinding(SupportsShouldProcess = $true)]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$gitRoot = & git -C $repoRoot rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0 -or
    (Resolve-Path -LiteralPath $gitRoot).Path -ne $repoRoot) {
    throw 'Run this script from a BBTTVV checkout.'
}

$relativePaths = @('.gradle', '.kotlin', 'build', 'tmp')
foreach ($module in @('app', 'baselineprofile', 'buildSrc', 'network-core', 'settings-core')) {
    $relativePaths += "$module/build"
    $relativePaths += "$module/.gradle"
    $relativePaths += "$module/.kotlin"
    $relativePaths += "$module/.cxx"
    $relativePaths += "$module/.externalNativeBuild"
}
$relativePaths += @(Get-ChildItem -LiteralPath $repoRoot -Force -File -Filter '*.log' |
    ForEach-Object { $_.Name })

# Validate every target before deleting any files. Never follow directory links.
$targets = @()
$fileCount = 0
$totalBytes = 0L
foreach ($relative in $relativePaths) {
    $candidate = Join-Path $repoRoot $relative
    if (-not (Test-Path -LiteralPath $candidate)) { continue }
    $item = Get-Item -LiteralPath $candidate -Force
    $absolute = (Resolve-Path -LiteralPath $candidate).Path
    if (-not $absolute.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Target outside checkout: $relative"
    }
    $pending = [Collections.Generic.Queue[IO.FileSystemInfo]]::new()
    $pending.Enqueue($item)
    while ($pending.Count -gt 0) {
        $entry = $pending.Dequeue()
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Refusing to follow a linked path in: $relative"
        }
        if ($entry.PSIsContainer) {
            foreach ($child in Get-ChildItem -LiteralPath $entry.FullName -Force) {
                $pending.Enqueue($child)
            }
        } else {
            $fileCount += 1
            $totalBytes += $entry.Length
        }
    }
    $tracked = @(& git -C $repoRoot ls-files -- $relative)
    if ($LASTEXITCODE -ne 0 -or $tracked.Count -gt 0) {
        throw "Refusing to remove tracked files in: $relative"
    }
    & git -C $repoRoot check-ignore -q -- $relative
    if ($LASTEXITCODE -ne 0) {
        throw "Refusing to remove a path that is not ignored: $relative"
    }
    $targets += $absolute
}

Write-Output ('Cleanup candidates: {0} files, {1:N2} MiB.' -f $fileCount, ($totalBytes / 1MB))
foreach ($absolute in $targets) {
    if ($PSCmdlet.ShouldProcess($absolute, 'Remove local build output or diagnostic log')) {
        Remove-Item -LiteralPath $absolute -Recurse -Force
    }
}
