package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.SwingUiRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.util.Locale
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
        debugSummary.text = buildDebugSummary(snapshot)
        debugDump.text = buildString {
            appendLine("Tree")
            appendLine(snapshot.lastTreeDump)
            appendLine()
            appendLine("Groups")
            appendLine(if (snapshot.groupDump.isEmpty()) "<none>" else snapshot.groupDump)
            appendLine()
            appendLine("Dependencies")
            appendLine(if (snapshot.dependencyDump.isEmpty()) "<none>" else snapshot.dependencyDump)
            appendLine()
            appendLine("Dirty groups")
            appendLine(if (snapshot.dirtyGroupDump.isEmpty()) "<none>" else snapshot.dirtyGroupDump)
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

    private fun buildDebugSummary(snapshot: CompositionDebugSnapshot): String {
        val rebuiltPercent = if (snapshot.totalNodeCount == 0) {
            0.0
        } else {
            snapshot.rebuiltNodeCount * 100.0 / snapshot.totalNodeCount
        }
        val baselineRatio = if (snapshot.baselineCompositionDurationNanos == 0L) {
            0.0
        } else {
            snapshot.lastCompositionDurationNanos.toDouble() / snapshot.baselineCompositionDurationNanos
        }
        return "recompositions=${snapshot.recompositionCount}, invalidations=${snapshot.invalidationCount} | " +
            "cost: ${formatMillis(snapshot.lastCompositionDurationNanos)} ms (${formatRatio(baselineRatio)} baseline), " +
            "new UI nodes=${snapshot.rebuiltNodeCount}/${snapshot.totalNodeCount} (${String.format(Locale.ROOT, "%.0f", rebuiltPercent)}%)"
    }

    private fun formatMillis(nanos: Long): String = String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0)

    private fun formatRatio(ratio: Double): String = String.format(Locale.ROOT, "%.2fx", ratio)
}
