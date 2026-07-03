package com.kkoser.minicompose

import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import com.kkoser.minicompose.ui.SwingUiRenderer
import com.kkoser.minicompose.runtime.button
import com.kkoser.minicompose.runtime.column
import com.kkoser.minicompose.runtime.compose
import com.kkoser.minicompose.runtime.row
import com.kkoser.minicompose.runtime.text
import com.kkoser.minicompose.ui.UiNode

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

    fun buildDemoTree(): UiNode = compose {
        column {
            text("MiniCompose")
            text("Step 2: composer-built node tree")
            row {
                button("Run sample") { }
                text("Rendered through Composer")
            }
        }
    }
}
