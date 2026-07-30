param(
    [string]$OutputPath = "upstream-port-manifest.json"
)

$ErrorActionPreference = "Stop"

$utf8 = New-Object System.Text.UTF8Encoding($false)
chcp.com 65001 | Out-Null
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$windowStart = "2026-07-19T00:00:00Z"
$windowEnd = "2026-07-27T00:00:00Z"

$decisions = @{
    "c785edc1d2edd97edabda0dfbe579ddab7466e3c" = @("PORT", "P0", "LOW", "VideoDecodeFormat; Dash.getBestVideo", "VideoDecodeFormatTest; VideoPlaybackUseCaseTest", "P0-01: implemented normalized hev/hvc codec-family classification and shared DASH scoring.")
    "2594f813a428193b7a7b2a11fc3e9a68f3195f5c" = @("ALREADY_PRESENT", "P0", "MEDIUM", "VideoLoadPolicy.buildDashAttemptQualities", "VideoLoadPolicyTest", "P0-02: target already leads with 127/126/125 and uses a bounded deduplicated fallback chain.")
    "ca6bad51fe12a57c1cf0301c1600f62b2e68938c" = @("ALREADY_PRESENT", "P0", "MEDIUM", "Dash.getBestVideo; VideoPlaybackUseCase.selectDashTracks", "VideoPlaybackUseCaseTest", "P0-03: target selects a quality group before applying codec/device support.")
    "1991eda318fe407bc06b183a3f418a0d8a05eaea" = @("ALREADY_PRESENT", "P0", "LOW", "PlayerSettingsCache.audioPassthrough", "Settings policy tests", "P0-04: local MPD is gated by audio passthrough, whose default and uninitialized cache value are false.")
    "edf23e150817b131010ba510fd8ff500871a95e1" = @("ALREADY_PRESENT", "P0", "HIGH", "PlayerSurface; Media3 PlayerView", "Manual SDR/HDR/Dolby Vision matrix", "P0-05: programmatic Media3 PlayerView keeps its default SurfaceView; no TextureView override exists.")
    "13e4726318205c1691c43a9e119e8f4066c2b5af" = @("ALREADY_PRESENT", "P0", "MEDIUM", "DanmakuOverlaySyncState", "DanmakuOverlaySyncStateTest", "P0-06: payload, attach token, viewport, position and speed already drive idempotent hard/soft synchronization.")
    "cf2145514117c67f2facf3a88f9640fe585f92c2" = @("NOT_APPLICABLE", "P0", "LOW", "PlayerOverlayStateMachine", "Player input regression", "P0-10: target has persistent speed selection but no temporary long-press speed gesture.")
    "85fad95ff9850981ea8371a17620de3dcc75d416" = @("PORT", "P0", "MEDIUM", "PlayerOverlayStateMachine; PlayerOverlayHost; PlayerFocusCoordinator; PlayerExclusiveOverlayPolicy", "PlayerOverlayActionsTest; PlayerFocusCoordinatorTest", "P0-07: existing comment isolation retained; added a single owner with interactive-video priority for conflicting modal prompts.")
    "905fdd114bbfbc129fb5958a30d810029eeaa4dc" = @("ALREADY_PRESENT", "P0", "LOW", "PlayerOverlayUiState.scrubPreviewPositionMs", "PlayerOverlayStateMachineTest", "P0-09: actual playback position and nullable scrub preview are separate state sources.")
    "f912c1941f11df369d943f6ec5b153f1f0174057" = @("PORT", "P0", "MEDIUM", "PlayerMediaSourceCoordinator; PlayerMediaStartPolicy", "PlayerMediaStartPolicyTest", "P0-08: implemented Media3 initial-position overloads for DASH, merged, segmented and URL playback without a post-prepare seek.")

    "20978deca4e5959cae4e048115a7bcc75fc3cacd" = @("PORT", "P1", "MEDIUM", "SettingsManager strictCustomCdn; VideoPlaybackUseCase.applyPlaybackCdnPlugins; CdnRegionPolicy", "CdnRegionPolicyTest; SettingsManagerTest; playback tests", "P1-02: added an opt-in strict custom bilivideo host mode; only rewritten candidates survive and raw fallbacks are removed.")
    "8bd4db7cb73d386de380f9a746762f11b9bfeca2" = @("PORT", "P1", "MEDIUM", "PlayerSettingsStore.pgcPreferredQuality; PlaybackLoadCoordinator", "PlaybackContentQualityPolicyTest; SettingsCatalogTest", "P1-01: PGC quality inherits the normal preference until explicitly configured and retains the existing account downgrade policy.")
    "5e6fb56125b13fa491549cf2f173d3c8c4c04b82" = @("PORT", "P1", "LOW", "PLAYER_PLAYBACK_SPEED_PRESETS", "PlaybackSpeedSupportTest; TV menu focus/scroll test", "P1-08: expanded the bounded TV speed menu through 2.75x.")
    "426ee11aa2fe9eaa8e099ad5285f3c6803d9f0ff" = @("NOT_APPLICABLE", "P1", "LOW", "No target subtitle shortcut", "Subtitle architecture audit", "P1-08: target has subtitle parsing policy scaffolding but no player subtitle action/rendering chain or shortcut to lazy-load.")
    "ae029f2f2e68db6297cf1d2e341ff502eb59d324" = @("NOT_APPLICABLE", "P1", "MEDIUM", "No active target subtitle discovery consumer", "Subtitle architecture audit", "P1-03: target does not currently consume PlayerInfo subtitles in the player, so a gRPC-only discovery fallback would be unreachable dead code.")
    "506d3227f9809021f2eb840884feef2d6983e716" = @("PORT", "P1", "HIGH", "LiveDanmakuMessageParser; LiveSuperChatQueue; LivePlayerViewModel; LivePlayerScreen", "LiveSuperChatTest; live player compile/UI tests", "P1-06: parsed SUPER_CHAT_MESSAGE/JPN and DELETE into a bounded separate non-focusable overlay with privacy-mode username masking.")
    "236a674c782e032848835bd1ffedb32281848671" = @("NOT_APPLICABLE", "P1", "MEDIUM", "No dynamic detail comments feature", "Dynamic feature architecture audit", "P1-05: target dynamic UI is a feed and has no forwarded-dynamic comment panel to reopen.")
    "9edf3c5e6655bc8819f5e98935459ff6b9e12141" = @("NOT_APPLICABLE", "P1", "LOW", "No dynamic comment paginator", "Dynamic feature architecture audit", "P1-05: target has no dynamic comment pagination state machine.")
    "e46799041a14b046e121cb48cd26ea4db1f06cb2" = @("NOT_APPLICABLE", "P1", "MEDIUM", "No favorite/watch-later playback queue", "Profile navigation and player architecture audit", "P1-04: target opens a selected item directly and has no collection-backed playback queue to hydrate.")
    "43a4ac84b67d823c5df68aa8d4eddb5748521e07" = @("ALREADY_PRESENT", "P1", "MEDIUM", "Settings focus return targets; SettingsCatalog", "SettingsCatalogTest; tvUiRegression", "P1-07: settings choice/dialog overlays already return to their stable source row.")
    "56edf19bc94433349219e70771f29ca6fd1e3bde" = @("PORT", "P1", "MEDIUM", "SearchScreen submitted-result focus intent", "Home focus tests; tvUiRegression", "P1-07: a completed submitted search now routes focus to the first result or the category row when empty.")
    "9e8f657d6854b5a5d438e4d75769517b017771ca" = @("PORT", "P1", "MEDIUM", "SearchScreen empty-result focus fallback", "Home focus tests; tvUiRegression", "P1-07: empty search results retain a category-to-input D-pad path.")
    "b7f0fda612a7eddabaa7862012db5f6e810d6fab" = @("ALREADY_PRESENT", "P1", "LOW", "DetailCommentCard", "DetailFocusCoordinatorTest", "P1-07: the comment Surface is the sole focus target; text and metadata controls do not steal focus.")
    "37d12742a17ae8c56c581bc42f15f002a2adae35" = @("ALREADY_PRESENT", "P1", "LOW", "AppTopBarAdapter stable focus state", "tvUiRegression", "P1-07: the mixed RecyclerView top bar owns stable selected/focused visual state.")
    "10f2f714fb6dc3a0f832c69650ac24d5262d72c8" = @("NOT_APPLICABLE", "P1", "LOW", "No target single-history delete action", "Search focus architecture audit", "P1-07: target search history entries currently have no single-item delete control; no new focusable touch-oriented delete affordance was introduced.")

    "8bf8054bdaa16a69b413bf064f9a7485647f384d" = @("PORT", "P2", "MEDIUM", "DynamicRepository.getDynamicFeed; shouldContinueDynamicIncrementalFetch; resolveDynamicFeedUpdateBaseline", "DynamicFeedFetchPolicyTest; DynamicPaginationRegistryTest", "P2-01: incremental refresh now counts raw response items toward the first-page update_num target, keeps the ten-page cap, rejects stalled cursors, and preserves the first-page baseline.")
    "e5d49d57dd6a500df67086127294c7663a38eacc" = @("PORT", "P2", "MEDIUM", "HomeFeedRequestOwner; HomeViewModel.loadMore; HomeViewModel.refresh", "HomeFeedRequestOwnerTest; HomeFeedControllerRepositoryTest", "P2-02: refresh now cancels and joins stale recommendation work and only the latest request id may publish UI or Today Watch state. Cover sizing was already fixed at 480x270, so only stale-request cancellation semantics were ported.")
    "feec79935d8e55db48b6082c7b5f8e2c2dd9bc21" = @("PORT", "P2", "MEDIUM", "ReplyPicture.toCommentPictureUiModels; CommentPictureThumbnailRow; CommentPictureCompactPreview", "CommentPicturePolicyTest; comment UI tests; tvUiRegression", "P2-03: comment image placeholders and requests use the source aspect ratio, with a 16:9 fallback and a bounded 480px thumbnail request.")
    "10f49c4d22af2e586080f3ff2e135f8a0143773a" = @("PORT", "P2", "HIGH", "CommentImageViewer; commentPictureConfirmModifier; PlayerExclusiveOverlayPolicy", "CommentPicturePolicyTest; PlayerOverlayActionsTest; tvUiRegression", "P2-03: added a TV-bounded comment image viewer with non-wrapping left/right navigation, confirm/Back close, stable source-focus restore, and highest-priority player modal ownership.")

    "6d25d212c55ed8a49ef95c9c498c261d3432898a" = @("REJECT", "P2", "HIGH", "Anime4K/OpenGL pipeline", "Separate RFC and device matrix", "P2: new GPU rendering stack is outside this port.")
    "8c393cf39ced77a4d108b04f33797ee14fa59d71" = @("REJECT", "P2", "HIGH", "Ijkplayer kernel", "Not applicable", "P2: target uses Media3 and must not add a second player kernel.")
    "60a5a82c3ce7e06c62606542e49981b7ac5a7a55" = @("REJECT", "P2", "HIGH", "Ijkplayer CI", "Not applicable", "P2: Ijkplayer build workflow is outside the Media3 target.")
    "5a3054f02bf25ec3c9e64103ce8943bd7c271fbc" = @("REJECT", "P2", "HIGH", "Ijkplayer CI", "Not applicable", "P2: Ijkplayer build workflow is outside the Media3 target.")
}

$anime4kPattern = "Anime4K|anime4k"
$mobileUiPattern = "miuix|cupertino|haze|portrait|predictive|transition|morph|blur|icon|splash"
$releasePattern = "release|version|changelog"

function Get-PatchScopes {
    param([string]$Sha)

    $patch = git show --format= --unified=0 --find-renames --find-copies $Sha --
    $scopes = foreach ($line in $patch) {
        if ($line -match "^@@.*@@\s*(.*)$") {
            $scope = $Matches[1].Trim()
            if ($scope) { $scope }
        }
    }
    return @($scopes | Select-Object -Unique | Select-Object -First 8)
}

function New-ManifestEntry {
    param(
        [string]$Source,
        [string]$Sha
    )

    $title = (git show -s --format="%s" $Sha).Trim()
    $changedFiles = @(
        git diff-tree --no-commit-id --name-only -r $Sha |
            Where-Object { $_ -and $_.Trim() } |
            ForEach-Object { $_.Trim() }
    )
    $scopes = @(Get-PatchScopes -Sha $Sha)
    $actualBehaviors = if ($scopes.Count -gt 0) {
        @("Patch changes these concrete hunk scopes: " + ($scopes -join "; "))
    } else {
        @("Patch changes repository content in: " + (($changedFiles | Select-Object -First 8) -join "; "))
    }

    $decision = $decisions[$Sha]
    if ($null -eq $decision) {
        $classification = "DEFER"
        $priority = "P2"
        $risk = "LOW"
        $targetSymbols = @()
        $testsRequired = @()
        $notes = "Audited from patch; outside the selected P0 semantic-port scope."

        if ($title -match $releasePattern) {
            $classification = "REJECT"
            $notes = "Release/version/changelog work is intentionally not ported."
        } elseif ($title -match $anime4kPattern) {
            $classification = "REJECT"
            $risk = "HIGH"
            $notes = "Anime4K/OpenGL work requires a separate RFC and device matrix."
        } elseif ($title -match $mobileUiPattern) {
            $classification = "REJECT"
            $risk = "MEDIUM"
            $notes = "Phone-oriented visual/navigation behavior is not copied into the TV focus architecture."
        }
    } else {
        $classification = $decision[0]
        $priority = $decision[1]
        $risk = $decision[2]
        $targetSymbols = @($decision[3] -split "; ")
        $testsRequired = @($decision[4] -split "; ")
        $notes = $decision[5]
    }

    [ordered]@{
        source = $Source
        sha = $Sha
        title = $title
        changedFiles = $changedFiles
        actualBehaviors = $actualBehaviors
        targetSymbols = $targetSymbols
        classification = $classification
        priority = $priority
        risk = $risk
        testsRequired = $testsRequired
        notes = $notes
    }
}

$entries = [System.Collections.Generic.List[object]]::new()
$sources = @(
    @("BiliPai", "upstream-bilipai/main"),
    @("blbl", "upstream-blbl/main")
)

foreach ($source in $sources) {
    $name = $source[0]
    $ref = $source[1]
    $shas = git log $ref --since=$windowStart --until=$windowEnd --format="%H"
    foreach ($sha in $shas) {
        $entries.Add((New-ManifestEntry -Source $name -Sha $sha))
    }
}

$json = $entries | ConvertTo-Json -Depth 8
$resolvedOutputPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
[System.IO.File]::WriteAllText(
    $resolvedOutputPath,
    $json + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Output "Generated $($entries.Count) entries at $resolvedOutputPath"
