package com.kkoser.minicompose.ui

import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object SwingUiRenderer {
    fun render(node: UiNode, onEvent: ((String) -> Unit)? = null): JComponent {
        return when (node) {
            is UiText -> renderText(node)
            is UiButton -> renderButton(node, onEvent)
            is UiColumn -> renderStack(node.children, BoxLayout.Y_AXIS, node.spacing, onEvent)
            is UiRow -> renderStack(node.children, BoxLayout.X_AXIS, node.spacing, onEvent)
        }
    }

    private fun renderText(node: UiText): JLabel {
        return JLabel(node.text).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }
    }

    private fun renderButton(node: UiButton, onEvent: ((String) -> Unit)?): JButton {
        return JButton(node.text).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener {
                onEvent?.invoke("Button click: ${node.text}")
                node.onClick.invoke()
            }
        }
    }

    private fun renderStack(
        children: List<UiNode>,
        axis: Int,
        spacing: Int,
        onEvent: ((String) -> Unit)?
    ): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, axis)
            alignmentX = Component.LEFT_ALIGNMENT
            children.forEachIndexed { index, child ->
                add(render(child, onEvent))
                if (index < children.lastIndex && spacing > 0) {
                    add(
                        Box.createRigidArea(
                            if (axis == BoxLayout.Y_AXIS) {
                                Dimension(0, spacing)
                            } else {
                                Dimension(spacing, 0)
                            }
                        )
                    )
                }
            }
        }
    }
}
