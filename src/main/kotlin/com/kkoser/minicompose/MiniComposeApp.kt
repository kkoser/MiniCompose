package com.kkoser.minicompose

import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import com.kkoser.minicompose.runtime.Composer
import com.kkoser.minicompose.runtime.CompositionHostPanel
import com.kkoser.minicompose.runtime.RootComposition
import com.kkoser.minicompose.runtime.mutableStateOf
import com.kkoser.minicompose.runtime.button
import com.kkoser.minicompose.runtime.column
import com.kkoser.minicompose.runtime.remember
import com.kkoser.minicompose.runtime.row
import com.kkoser.minicompose.runtime.text
import com.kkoser.minicompose.ui.UiNode

object MiniComposeApp {
    private var rememberedBuildToken = 0
    private var rootComposition = createRootComposition()

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
            add(CompositionHostPanel(rootComposition), BorderLayout.CENTER)
        }
    }

    fun buildDemoTree(): UiNode = rootComposition.recompose()

    internal fun resetDemoStateForTests() {
        rememberedBuildToken = 0
        rootComposition = createRootComposition()
    }

    private fun createRootComposition(): RootComposition {
        return RootComposition {
            buildCounterScreen()
        }
    }

    private fun Composer.buildCounterScreen() = column {
        val buildToken = remember { ++rememberedBuildToken }
        val counterState = remember { mutableStateOf(0) }
        text("MiniCompose")
        text("Remembered build token: $buildToken")
        row(spacing = 12) {
            column(spacing = 6) {
                text("Count: ${counterState.value}")
                text("Rendered through Composer")
            }
            column(spacing = 8) {
                button("Increment") {
                    counterState.value += 1
                }
                text("Nested layouts")
            }
        }
    }
}
