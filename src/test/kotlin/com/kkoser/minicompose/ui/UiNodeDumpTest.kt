package com.kkoser.minicompose.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UiNodeDumpTest {
    @Test
    fun `tree dump includes layout spacing and nested children`() {
        val tree = UiColumn(
            children = listOf(
                UiText("Title"),
                UiRow(
                    children = listOf(
                        UiButton("Press me") {},
                        UiText("Body")
                    ),
                    spacing = 8
                )
            ),
            spacing = 12
        )

        assertEquals(
            """
            Column(spacing=12)
              Text(text="Title")
              Row(spacing=8)
                Button(text="Press me")
                Text(text="Body")
            """.trimIndent(),
            tree.dumpTree()
        )
    }
}
