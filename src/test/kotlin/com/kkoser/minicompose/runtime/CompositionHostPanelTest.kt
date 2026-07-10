package com.kkoser.minicompose.runtime

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompositionHostPanelTest {
    @Test
    fun `debug panel shows tree dump and event log when enabled`() {
        val count = mutableStateOf(0)
        val composition = RootComposition {
            column {
                button("Increment") {
                    count.value += 1
                }
                text("Count: ${count.value}")
            }
        }

        val panel = CompositionHostPanel(composition, showDebugInfo = true)

        val debugPanel = panel.getComponent(1) as javax.swing.JPanel
        val debugSummary = debugPanel.getComponent(0) as javax.swing.JLabel
        val debugScroll = debugPanel.getComponent(1) as javax.swing.JScrollPane
        val debugText = debugScroll.viewport.view as javax.swing.JTextArea

        assertTrue(debugSummary.text.contains("recompositions=1"))
        assertTrue(debugText.text.contains("Column(spacing=0)"))
        assertTrue(debugText.text.contains("Tree"))
        assertTrue(debugText.text.contains("Scopes"))
        assertTrue(debugText.text.contains("Dependencies"))
        assertTrue(debugText.text.contains("Dirty scopes"))

        val renderedTree = (panel.getComponent(0) as javax.swing.JPanel).getComponent(0) as javax.swing.JPanel
        val button = renderedTree.getComponent(0) as javax.swing.JButton
        button.doClick()

        assertTrue(debugSummary.text.contains("recompositions=2"))
        assertTrue(debugSummary.text.contains("invalidations=1"))
        assertTrue(debugText.text.contains("Button click: Increment"))
        assertTrue(debugText.text.contains("Count: 1"))
        assertTrue(debugText.text.contains("Dirty scopes"))
    }
}
