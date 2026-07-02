package com.kkoser.minicompose

import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

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
            add(JLabel("MiniCompose is bootstrapped."), BorderLayout.CENTER)
        }
    }
}
