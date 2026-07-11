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
    private var leftScopeToken = 0
    private var rightScopeToken = 0
    private var transientBranchToken = 0
    private var compactLayoutToken = 0
    private var expandedLeftToken = 0
    private var expandedRightToken = 0
    private var rootComposition = createRootComposition()

    @JvmStatic
    fun main(args: Array<String>) {
        SwingUtilities.invokeLater {
            createWindow(showDebugInfo = args.none { it == "--no-debug" }).isVisible = true
        }
    }

    fun createWindow(showDebugInfo: Boolean = true): JFrame {
        return JFrame("MiniCompose").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            contentPane.add(createContentPanel(showDebugInfo), BorderLayout.CENTER)
            pack()
            setLocationRelativeTo(null)
        }
    }

    fun createContentPanel(showDebugInfo: Boolean = true): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(24, 24, 24, 24)
            add(CompositionHostPanel(rootComposition, showDebugInfo), BorderLayout.CENTER)
        }
    }

    fun createDebugContentPanel(): JPanel = createContentPanel(showDebugInfo = true)

    fun buildDemoTree(): UiNode = rootComposition.recompose()

    internal fun resetDemoStateForTests() {
        rememberedBuildToken = 0
        leftScopeToken = 0
        rightScopeToken = 0
        transientBranchToken = 0
        compactLayoutToken = 0
        expandedLeftToken = 0
        expandedRightToken = 0
        rootComposition = createRootComposition()
    }

    private fun createRootComposition(): RootComposition {
        return RootComposition {
            buildDiagnosticsShowcase()
        }
    }

    private fun Composer.buildDiagnosticsShowcase() = column(spacing = 18) {
        val buildToken = remember { ++rememberedBuildToken }
        val headlineState = remember { mutableStateOf(0) }
        val siblingLeftState = remember { mutableStateOf(0) }
        val siblingRightState = remember { mutableStateOf(0) }
        val showTransientBranch = remember { mutableStateOf(true) }
        val transientCounter = remember { mutableStateOf(0) }
        val showAlternateLayout = remember { mutableStateOf(false) }
        val layoutFlipCounter = remember { mutableStateOf(0) }

        text("MiniCompose diagnostics")
        text("Remembered build token: $buildToken")
        text("Use the controls below to watch group reuse in the debug panel.")

        column(spacing = 12) {
            text("1. Shared parent state with stable remembered siblings")
            text("Headline ticks: ${headlineState.value}")
            row(spacing = 12) {
                column(spacing = 6) {
                    val leftRememberedToken = remember { ++leftScopeToken }
                    text("Left scope token: $leftRememberedToken")
                    text("Left count: ${siblingLeftState.value}")
                    button("Left +1") {
                        siblingLeftState.value += 1
                    }
                }
                column(spacing = 6) {
                    val rightRememberedToken = remember { ++rightScopeToken }
                    text("Right scope token: $rightRememberedToken")
                    text("Right count: ${siblingRightState.value}")
                    button("Right +1") {
                        siblingRightState.value += 1
                    }
                }
            }
            row(spacing = 12) {
                button("Retitle parent") {
                    headlineState.value += 1
                }
                text("This invalidates the parent while keeping sibling remember slots stable.")
            }
        }

        column(spacing = 12) {
            text("2. Hidden branch with remembered state")
            text("Branch visible: ${showTransientBranch.value}")
            if (showTransientBranch.value) {
                column(spacing = 6) {
                    val branchToken = remember { ++transientBranchToken }
                    val transientCounter = remember { mutableStateOf(0) }
                    text("Transient branch token: $branchToken")
                    text("Transient count: ${transientCounter.value}")
                    row(spacing = 12) {
                        button("Transient +1") {
                            transientCounter.value += 1
                        }
                        button("Hide branch") {
                            showTransientBranch.value = false
                        }
                    }
                }
            } else {
                text("Branch is hidden, so its remembered token should be dropped.")
                button("Show branch") {
                    showTransientBranch.value = true
                }
            }
        }

        column(spacing = 12) {
            text("3. Shape swap with nested remembered content")
            text("Alternate layout: ${showAlternateLayout.value}")
            if (showAlternateLayout.value) {
                row(spacing = 10) {
                    val swapToken = remember { ++expandedLeftToken }
                    column(spacing = 6) {
                        text("Expanded left lane")
                        text("Swap token: $swapToken")
                    }
                    column(spacing = 6) {
                        val extraToken = remember { ++expandedRightToken }
                        text("Expanded right lane")
                        text("Extra token: $extraToken")
                    }
                }
            } else {
                column(spacing = 6) {
                    val compactToken = remember { ++compactLayoutToken }
                    text("Compact layout")
                    text("Compact token: $compactToken")
                }
            }
            row(spacing = 12) {
                button("Swap layout") {
                    showAlternateLayout.value = !showAlternateLayout.value
                    layoutFlipCounter.value += 1
                }
                text("Layout swaps: ${layoutFlipCounter.value}")
            }
        }
    }
}
