package com.kkoser.minicompose.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwingUiRendererTest {
    @Test
    fun `rendering text produces a JLabel with matching content`() {
        val component = SwingUiRenderer.render(UiText("Hello"))

        assertEquals("Hello", (component as javax.swing.JLabel).text)
    }

    @Test
    fun `rendering a button wires the label and click handler`() {
        var clicked = false

        val component = SwingUiRenderer.render(
            UiButton("Press me") {
                clicked = true
            }
        )

        val button = component as javax.swing.JButton
        assertEquals("Press me", button.text)

        button.doClick()

        assertTrue(clicked)
    }

    @Test
    fun `rendering a column nests children in order`() {
        val component = SwingUiRenderer.render(
            UiColumn(
                listOf(
                    UiText("One"),
                    UiText("Two")
                )
            )
        )

        val panel = component as javax.swing.JPanel
        assertEquals(2, panel.componentCount)
        assertEquals("One", (panel.getComponent(0) as javax.swing.JLabel).text)
        assertEquals("Two", (panel.getComponent(1) as javax.swing.JLabel).text)
    }

    @Test
    fun `rendering a row nests children in order`() {
        val component = SwingUiRenderer.render(
            UiRow(
                listOf(
                    UiText("Left"),
                    UiText("Right")
                )
            )
        )

        val panel = component as javax.swing.JPanel
        assertEquals(2, panel.componentCount)
        assertEquals("Left", (panel.getComponent(0) as javax.swing.JLabel).text)
        assertEquals("Right", (panel.getComponent(1) as javax.swing.JLabel).text)
    }
}
