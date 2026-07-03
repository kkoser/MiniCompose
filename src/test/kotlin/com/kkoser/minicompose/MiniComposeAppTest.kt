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
        assertEquals(UiText("Remembered build token: 1"), column.children[1])
        assertTrue(column.children[2] is UiRow)

        val row = column.children[2] as UiRow
        assertEquals(12, row.spacing)
        assertEquals(2, row.children.size)

        val leftColumn = row.children[0] as UiColumn
        assertEquals(6, leftColumn.spacing)
        assertEquals(UiText("Count: 0"), leftColumn.children[0])
        assertEquals(UiText("Rendered through Composer"), leftColumn.children[1])

        val rightColumn = row.children[1] as UiColumn
        assertEquals(8, rightColumn.spacing)
        assertTrue(rightColumn.children[0] is UiButton)
        assertEquals(UiText("Nested layouts"), rightColumn.children[1])
    }

    @Test
    fun `createContentPanel updates the rendered count after button click`() {
        val panel = MiniComposeApp.createContentPanel()

        assertEquals(1, panel.componentCount)

        val host = panel.getComponent(0) as JPanel
        val content = host.getComponent(0) as JPanel
        val tree = content.getComponent(0) as JPanel
        val rememberedLabel = tree.getComponent(1) as JLabel
        val row = tree.getComponent(2) as JPanel
        val leftColumn = row.getComponent(0) as JPanel
        val countLabel = leftColumn.getComponent(0) as JLabel
        val rightColumn = row.getComponent(2) as JPanel
        val incrementButton = rightColumn.getComponent(0) as JButton

        assertEquals("Remembered build token: 1", rememberedLabel.text)
        assertEquals("Count: 0", countLabel.text)

        incrementButton.doClick()

        val updatedContent = host.getComponent(0) as JPanel
        val updatedTree = updatedContent.getComponent(0) as JPanel
        val updatedRememberedLabel = updatedTree.getComponent(1) as JLabel
        val updatedRow = updatedTree.getComponent(2) as JPanel
        val updatedLeftColumn = updatedRow.getComponent(0) as JPanel
        val updatedCountLabel = updatedLeftColumn.getComponent(0) as JLabel

        assertEquals("Remembered build token: 1", updatedRememberedLabel.text)
        assertEquals("Count: 1", updatedCountLabel.text)
        assertTrue(panel.components.first().isVisible)
    }
}
