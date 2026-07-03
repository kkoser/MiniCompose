package com.kkoser.minicompose.ui

import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object SwingUiRenderer {
    fun render(node: UiNode): JComponent {
        return when (node) {
            is UiText -> renderText(node)
            is UiButton -> renderButton(node)
            is UiColumn -> renderStack(node.children, BoxLayout.Y_AXIS)
            is UiRow -> renderStack(node.children, BoxLayout.X_AXIS)
        }
    }

    private fun renderText(node: UiText): JLabel {
        return JLabel(node.text).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }
    }

    private fun renderButton(node: UiButton): JButton {
        return JButton(node.text).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener { node.onClick.invoke() }
        }
    }

    private fun renderStack(children: List<UiNode>, axis: Int): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, axis)
            alignmentX = Component.LEFT_ALIGNMENT
            children.forEach { child ->
                add(render(child))
            }
        }
    }
}
