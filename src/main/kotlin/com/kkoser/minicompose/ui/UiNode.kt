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
    val children: List<UiNode>
) : UiNode

data class UiRow(
    val children: List<UiNode>
) : UiNode
