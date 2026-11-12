package com.example.Vlocker

import com.example.Vlocker.ui.UiScaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiScaleHelperTest {

    @Test
    fun `pleos wide display dialog width respects automotive max cap`() {
        val width = UiScaleHelper.computeDialogWidthPx(
            screenWidthPx = 2560,
            maxDialogWidthPx = 960,
            isLandscape = true
        )
        assertEquals(960, width)
    }

    @Test
    fun `phone portrait dialog width uses comfortable screen ratio`() {
        val width = UiScaleHelper.computeDialogWidthPx(
            screenWidthPx = 1080,
            maxDialogWidthPx = 1260,
            isLandscape = false
        )
        assertTrue(width in 980..1020)
    }

    @Test
    fun `never exceeds available screen width minus margin`() {
        val width = UiScaleHelper.computeDialogWidthPx(
            screenWidthPx = 800,
            maxDialogWidthPx = 1200,
            isLandscape = true
        )
        assertTrue(width <= 752)
        assertTrue(width > 400)
    }
}

