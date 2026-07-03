package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.SwingUiRenderer
import java.awt.BorderLayout
import javax.swing.JPanel

class CompositionHostPanel(
    private val composition: RootComposition
) : JPanel(BorderLayout()) {
    init {
        composition.addInvalidationListener { refresh() }
        refresh()
    }

    fun refresh() {
        val tree = if (composition.isDirty() || composition.latestTree == null) {
            composition.recompose()
        } else {
            composition.latestTree!!
        }

        val rendered = SwingUiRenderer.render(tree)
        removeAll()
        add(rendered, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
}
