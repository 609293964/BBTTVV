package com.android.purebilibili.feature.home.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarMiuixPolicyTest {

    @Test
    fun `floating android native bottom bar adopts miuix chrome defaults`() {
        val spec = resolveMd3BottomBarFloatingChromeSpec(isFloating = true)

        assertEquals(50f, spec.cornerRadiusDp)
        assertEquals(36f, spec.horizontalOutsidePaddingDp)
        assertEquals(12f, spec.innerHorizontalPaddingDp)
        assertEquals(12f, spec.itemSpacingDp)
        assertEquals(1f, spec.shadowElevationDp)
        assertFalse(spec.showDivider)
    }

    @Test
    fun `material label mode maps to matching miuix display mode`() {
        assertEquals(
            Md3BottomBarDisplayMode.IconAndText,
            resolveMd3BottomBarDisplayMode(labelMode = 0)
        )
        assertEquals(
            Md3BottomBarDisplayMode.IconOnly,
            resolveMd3BottomBarDisplayMode(labelMode = 1)
        )
        assertEquals(
            Md3BottomBarDisplayMode.TextOnly,
            resolveMd3BottomBarDisplayMode(labelMode = 2)
        )
        assertEquals(
            Md3BottomBarDisplayMode.IconAndText,
            resolveMd3BottomBarDisplayMode(labelMode = 99)
        )
    }

    @Test
    fun `android native floating branch declares its own tuning entrypoint`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt")

        assertTrue(source.contains("resolveAndroidNativeBottomBarTuning("))
        assertTrue(source.contains("resolveAndroidNativeBottomBarContainerColor("))
        assertTrue(source.contains("KernelSuAlignedBottomBar("))
        assertTrue(source.contains("SharedFloatingBottomBarIconStyle.CUPERTINO"))
    }

    @Test
    fun `android native floating branch reuses shared refraction motion policy`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt")

        assertTrue(source.contains("val refractionMotionProfile = resolveBottomBarRefractionMotionProfile("))
        assertTrue(source.contains("val refractionLayerPolicy = resolveBottomBarRefractionLayerPolicy("))
        assertTrue(source.contains("rememberCombinedBackdrop(backdrop, tintedContentBackdrop)"))
    }

    @Test
    fun `android native indicator backdrop keeps blur before lens distortion`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt")

        assertTrue(
            Regex(
                """\.background\(indicatorColor, shellShape\)[\s\S]*?drawBackdrop\([\s\S]*?effects = \{[\s\S]*?blur\([\s\S]*?lens\(""",
                RegexOption.MULTILINE
            ).containsMatchIn(source)
        )
    }

    @Test
    fun `android native ordinary blur does not redraw raw backdrop over haze`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt")

        assertTrue(source.contains("if (backdrop != null && !useHazeBlur)"))
        assertTrue(source.contains("Modifier.unifiedBlur("))
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(
            File(path),
            File(normalizedPath)
        ).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
