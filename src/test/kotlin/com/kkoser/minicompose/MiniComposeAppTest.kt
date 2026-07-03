package com.kkoser.minicompose

import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class MiniComposeAppTest {
    @BeforeEach
    fun resetDemoState() {
        MiniComposeApp.resetDemoStateForTests()
    }

    @Test
    fun `buildDemoTree returns the expected counter structure`() {
        val tree = MiniComposeApp.buildDemoTree()

        val column = assertInstanceOf(UiColumn::class.java, tree)
        assertEquals(3, column.children.size)
        assertEquals(UiText("MiniCompose"), column.children[0])
        assertEquals(UiText("Count: 0"), column.children[1])

        assertTrue(column.children[2] is UiRow)
        val row = column.children[2] as UiRow
        assertEquals(2, row.children.size)
        assertTrue(row.children[0] is UiButton)
        assertEquals(UiText("Rendered through Composer"), row.children[1])
    }

    @Test
    fun `createContentPanel updates the rendered count after button click`() {
        val panel = MiniComposeApp.createContentPanel()

        assertEquals(1, panel.componentCount)

        val host = panel.getComponent(0) as JPanel
        val tree = host.getComponent(0) as JPanel
        val countLabel = tree.getComponent(1) as JLabel
        val row = tree.getComponent(2) as JPanel
        val incrementButton = row.getComponent(0) as JButton

        assertEquals("Count: 0", countLabel.text)

        incrementButton.doClick()

        val updatedTree = host.getComponent(0) as JPanel
        val updatedCountLabel = updatedTree.getComponent(1) as JLabel

        assertEquals("Count: 1", updatedCountLabel.text)
        assertTrue(panel.components.first().isVisible)
    }
}
