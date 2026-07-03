package com.kkoser.minicompose

import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniComposeAppTest {
    @Test
    fun `buildDemoTree returns the expected static structure`() {
        val tree = MiniComposeApp.buildDemoTree()

        val column = assertInstanceOf(UiColumn::class.java, tree)
        assertEquals(3, column.children.size)
        assertEquals(UiText("MiniCompose"), column.children[0])
        assertEquals(UiText("Step 2: composer-built node tree"), column.children[1])

        assertTrue(column.children[2] is UiRow)
        val row = column.children[2] as UiRow
        assertEquals(2, row.children.size)
        assertTrue(row.children[0] is UiButton)
        assertEquals(UiText("Rendered through Composer"), row.children[1])
    }

    @Test
    fun `createContentPanel wraps the rendered tree`() {
        val panel = MiniComposeApp.createContentPanel()

        assertEquals(1, panel.componentCount)
        assertTrue(panel.components.first().isVisible)
    }
}
