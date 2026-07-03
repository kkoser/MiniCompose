package com.kkoser.minicompose

import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import com.kkoser.minicompose.ui.SwingUiRenderer
import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText

object MiniComposeApp {
    @JvmStatic
    fun main(args: Array<String>) {
        SwingUtilities.invokeLater {
            createWindow().isVisible = true
        }
    }

    fun createWindow(): JFrame {
        return JFrame("MiniCompose").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            contentPane.add(createContentPanel(), BorderLayout.CENTER)
            pack()
            setLocationRelativeTo(null)
        }
    }

    fun createContentPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(24, 24, 24, 24)
            add(SwingUiRenderer.render(buildDemoTree()), BorderLayout.CENTER)
        }
    }

    fun buildDemoTree() = UiColumn(
        listOf(
            UiText("MiniCompose"),
            UiText("Step 1: manual node tree"),
            UiRow(
                listOf(
                    UiButton("Run sample") { },
                    UiText("Rendered directly from UiNode")
                )
            )
        )
    )
}
