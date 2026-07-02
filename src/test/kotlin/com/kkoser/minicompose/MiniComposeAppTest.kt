package com.kkoser.minicompose

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniComposeAppTest {
    @Test
    fun `createContentPanel builds a simple bootstrap panel`() {
        val panel = MiniComposeApp.createContentPanel()

        assertEquals(1, panel.componentCount)
        assertTrue(panel.components.first().isVisible)
    }
}
