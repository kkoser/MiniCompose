package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.SwingUiRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JPanel
import javax.swing.JTextArea

class CompositionHostPanel(
    private val composition: RootComposition,
    private val showDebugInfo: Boolean = false
) : JPanel(BorderLayout()) {
    private val contentPanel = JPanel(BorderLayout())
    private val debugSummary = JLabel()
    private val debugDump = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = false
        wrapStyleWord = false
    }

    init {
        add(contentPanel, BorderLayout.CENTER)
        if (showDebugInfo) {
            add(createDebugPanel(), BorderLayout.SOUTH)
        }
        composition.addInvalidationListener { refresh() }
        refresh()
    }

    fun refresh() {
        val tree = if (composition.isDirty() || composition.latestTree == null) {
            composition.recompose()
        } else {
            composition.latestTree!!
        }

        val rendered = SwingUiRenderer.render(tree, composition::recordUiEvent)
        contentPanel.removeAll()
        contentPanel.add(rendered, BorderLayout.CENTER)
        if (showDebugInfo) {
            updateDebugPanel()
        }
        revalidate()
        repaint()
    }

    private fun createDebugPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 0, 0, 0)
            debugSummary.border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
            debugDump.columns = 28
            debugDump.rows = 10
            add(debugSummary, BorderLayout.NORTH)
            add(JScrollPane(debugDump).apply {
                preferredSize = Dimension(320, 180)
            }, BorderLayout.CENTER)
        }
    }

    private fun updateDebugPanel() {
        val snapshot = composition.debugSnapshot()
        debugSummary.text = "recompositions=${snapshot.recompositionCount}, invalidations=${snapshot.invalidationCount}"
        debugDump.text = buildString {
            appendLine("Tree")
            appendLine(snapshot.lastTreeDump)
            appendLine()
            appendLine("Scopes")
            appendLine(if (snapshot.scopeDump.isEmpty()) "<none>" else snapshot.scopeDump)
            appendLine()
            appendLine("Dependencies")
            appendLine(if (snapshot.dependencyDump.isEmpty()) "<none>" else snapshot.dependencyDump)
            appendLine()
            appendLine("Dirty scopes")
            appendLine(if (snapshot.dirtyScopeDump.isEmpty()) "<none>" else snapshot.dirtyScopeDump)
            if (snapshot.events.isNotEmpty()) {
                appendLine()
                appendLine("Events")
                snapshot.events.forEach { event ->
                    appendLine(event)
                }
            }
        }
        debugDump.caretPosition = 0
    }
}
