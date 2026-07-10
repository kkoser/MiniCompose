package com.kkoser.minicompose

import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class MiniComposeAppTest {
    @BeforeEach
    fun resetDemoState() {
        MiniComposeApp.resetDemoStateForTests()
    }

    @Test
    fun `buildDemoTree returns the expanded diagnostics layout`() {
        val tree = MiniComposeApp.buildDemoTree()

        val column = assertInstanceOf(UiColumn::class.java, tree)
        assertEquals(6, column.children.size)
        assertEquals(18, column.spacing)
        assertEquals(UiText("MiniCompose diagnostics"), column.children[0])
        assertEquals(UiText("Remembered build token: 1"), column.children[1])
        assertEquals(UiText("Use the controls below to watch scope reuse in the debug panel."), column.children[2])

        val sharedStateSection = assertInstanceOf(UiColumn::class.java, column.children[3])
        assertEquals(4, sharedStateSection.children.size)
        assertEquals(UiText("1. Shared parent state with stable remembered siblings"), sharedStateSection.children[0])
        assertEquals(UiText("Headline ticks: 0"), sharedStateSection.children[1])

        val sharedRow = assertInstanceOf(UiRow::class.java, sharedStateSection.children[2])
        assertEquals(12, sharedRow.spacing)
        assertEquals(2, sharedRow.children.size)

        val leftColumn = assertInstanceOf(UiColumn::class.java, sharedRow.children[0])
        assertEquals(UiText("Left scope token: 1"), leftColumn.children[0])
        assertEquals(UiText("Left count: 0"), leftColumn.children[1])
        assertInstanceOf(UiButton::class.java, leftColumn.children[2])

        val rightColumn = assertInstanceOf(UiColumn::class.java, sharedRow.children[1])
        assertEquals(UiText("Right scope token: 1"), rightColumn.children[0])
        assertEquals(UiText("Right count: 0"), rightColumn.children[1])
        assertInstanceOf(UiButton::class.java, rightColumn.children[2])

        val hiddenBranchSection = assertInstanceOf(UiColumn::class.java, column.children[4])
        assertEquals(3, hiddenBranchSection.children.size)
        assertEquals(UiText("2. Hidden branch with remembered state"), hiddenBranchSection.children[0])
        assertEquals(UiText("Branch visible: true"), hiddenBranchSection.children[1])
        val transientBranch = assertInstanceOf(UiColumn::class.java, hiddenBranchSection.children[2])
        assertEquals(UiText("Transient branch token: 1"), transientBranch.children[0])
        assertEquals(UiText("Transient count: 0"), transientBranch.children[1])
        assertInstanceOf(UiRow::class.java, transientBranch.children[2])

        val swapSection = assertInstanceOf(UiColumn::class.java, column.children[5])
        assertEquals(UiText("3. Shape swap with nested remembered content"), swapSection.children[0])
        assertEquals(UiText("Alternate layout: false"), swapSection.children[1])
        assertInstanceOf(UiColumn::class.java, swapSection.children[2])
        assertInstanceOf(UiRow::class.java, swapSection.children[3])
    }

    @Test
    fun `shared sibling scopes keep remembered values stable when parent state changes`() {
        val panel = MiniComposeApp.createContentPanel()

        val initialTree = renderedTree(panel)
        val sharedSection = initialTree.logicalPanel(3)
        val sharedRow = sharedSection.logicalPanel(2)
        val leftColumn = sharedRow.logicalPanel(0)
        val rightColumn = sharedRow.logicalPanel(1)
        val retitleRow = sharedSection.logicalPanel(3)
        val retitleButton = retitleRow.logicalButton(0)

        assertEquals("Left scope token: 1", leftColumn.logicalLabel(0).text)
        assertEquals("Right scope token: 1", rightColumn.logicalLabel(0).text)

        retitleButton.doClick()

        val updatedTree = renderedTree(panel)
        val updatedSharedSection = updatedTree.logicalPanel(3)
        val updatedSharedRow = updatedSharedSection.logicalPanel(2)
        val updatedLeftColumn = updatedSharedRow.logicalPanel(0)
        val updatedRightColumn = updatedSharedRow.logicalPanel(1)

        assertEquals("Headline ticks: 1", updatedSharedSection.logicalLabel(1).text)
        assertEquals("Left scope token: 1", updatedLeftColumn.logicalLabel(0).text)
        assertEquals("Right scope token: 1", updatedRightColumn.logicalLabel(0).text)
    }

    @Test
    fun `hidden remembered branch is recreated after it is shown again`() {
        val panel = MiniComposeApp.createContentPanel()

        val initialTree = renderedTree(panel)
        val hiddenSection = initialTree.logicalPanel(4)
        val visibleBranch = hiddenSection.logicalPanel(2)
        val transientRow = visibleBranch.logicalPanel(2)
        val transientButton = transientRow.logicalButton(0)
        val hideButton = transientRow.logicalButton(1)

        assertEquals("Transient branch token: 1", visibleBranch.logicalLabel(0).text)
        assertEquals("Transient count: 0", visibleBranch.logicalLabel(1).text)

        transientButton.doClick()
        assertEquals("Transient count: 1", renderedTree(panel).logicalPanel(4).logicalPanel(2).logicalLabel(1).text)

        hideButton.doClick()
        val hiddenTree = renderedTree(panel)
        val hiddenBranchSection = hiddenTree.logicalPanel(4)
        assertEquals("Branch visible: false", hiddenBranchSection.logicalLabel(1).text)
        assertEquals("Branch is hidden, so its remembered token should be dropped.", hiddenBranchSection.logicalLabel(2).text)
        assertEquals("Show branch", hiddenBranchSection.logicalButton(3).text)

        hiddenBranchSection.logicalButton(3).doClick()

        val reshownTree = renderedTree(panel)
        val reshownSection = reshownTree.logicalPanel(4)
        assertEquals("Branch visible: true", reshownSection.logicalLabel(1).text)
        val reshownBranch = reshownSection.logicalPanel(2)
        assertEquals("Transient branch token: 2", reshownBranch.logicalLabel(0).text)
        assertEquals("Transient count: 0", reshownBranch.logicalLabel(1).text)
    }

    @Test
    fun `shape swaps replace compact layout with expanded remembered lanes`() {
        val panel = MiniComposeApp.createContentPanel()

        val initialTree = renderedTree(panel)
        val swapSection = initialTree.logicalPanel(5)
        val compactLayout = swapSection.logicalPanel(2)
        val swapButton = swapSection.logicalPanel(3).logicalButton(0)

        assertEquals("Compact layout", compactLayout.logicalLabel(0).text)
        assertEquals("Compact token: 1", compactLayout.logicalLabel(1).text)

        swapButton.doClick()

        val expandedTree = renderedTree(panel)
        val expandedSection = expandedTree.logicalPanel(5)
        val expandedRow = expandedSection.logicalPanel(2)
        val expandedLeft = expandedRow.logicalPanel(0)
        val expandedRight = expandedRow.logicalPanel(1)

        assertEquals("Alternate layout: true", expandedSection.logicalLabel(1).text)
        assertEquals("Expanded left lane", expandedLeft.logicalLabel(0).text)
        assertEquals("Swap token: 1", expandedLeft.logicalLabel(1).text)
        assertEquals("Expanded right lane", expandedRight.logicalLabel(0).text)
        assertEquals("Extra token: 1", expandedRight.logicalLabel(1).text)
    }

    @Test
    fun `debug panel is enabled by default and reports recomposition activity`() {
        val panel = MiniComposeApp.createContentPanel()

        val host = panel.getComponent(0) as JPanel
        val debugPanel = host.getComponent(1) as JPanel
        val summary = debugPanel.getComponent(0) as JLabel
        val dumpScrollPane = debugPanel.getComponent(1) as JScrollPane
        val dump = dumpScrollPane.viewport.view as JTextArea

        assertTrue(summary.text.contains("recompositions=1"))
        assertTrue(dump.text.contains("Tree"))
        assertTrue(dump.text.contains("Dependencies"))
    }

    private fun renderedTree(panel: JPanel): JPanel {
        val host = panel.getComponent(0) as JPanel
        val content = host.getComponent(0) as JPanel
        return content.getComponent(0) as JPanel
    }

    private fun JPanel.logicalChildren(): List<Component> {
        return components.filterNot { it is Box.Filler }
    }

    private fun JPanel.logicalChild(index: Int): Component {
        return logicalChildren()[index]
    }

    private fun JPanel.logicalLabel(index: Int): JLabel {
        return logicalChild(index) as JLabel
    }

    private fun JPanel.logicalButton(index: Int): JButton {
        return logicalChild(index) as JButton
    }

    private fun JPanel.logicalPanel(index: Int): JPanel {
        return logicalChild(index) as JPanel
    }
}
