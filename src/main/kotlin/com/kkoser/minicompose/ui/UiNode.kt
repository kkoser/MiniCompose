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
