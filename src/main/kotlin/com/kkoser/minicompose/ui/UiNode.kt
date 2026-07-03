package com.kkoser.minicompose.ui

sealed interface UiNode

data class UiText(
    val text: String
) : UiNode

data class UiButton(
    val text: String,
    val onClick: () -> Unit
) : UiNode

data class UiColumn(
    val children: List<UiNode>,
    val spacing: Int = 0
) : UiNode {
    init {
        require(spacing >= 0) { "spacing must be non-negative" }
    }
}

data class UiRow(
    val children: List<UiNode>,
    val spacing: Int = 0
) : UiNode {
    init {
        require(spacing >= 0) { "spacing must be non-negative" }
    }
}

fun UiNode.dumpTree(indent: String = ""): String {
    return when (this) {
        is UiText -> "${indent}Text(text=${text.quote()})"
        is UiButton -> "${indent}Button(text=${text.quote()})"
        is UiColumn -> buildContainerDump("Column", spacing, children, indent)
        is UiRow -> buildContainerDump("Row", spacing, children, indent)
    }
}

private fun buildContainerDump(
    type: String,
    spacing: Int,
    children: List<UiNode>,
    indent: String
): String {
    val childIndent = "$indent  "
    return buildString {
        append("${indent}$type(spacing=$spacing)")
        children.forEach { child ->
            appendLine()
            append(child.dumpTree(childIndent))
        }
    }
}

private fun String.quote(): String = "\"${replace("\"", "\\\"")}\""
