package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.annotations.MiniComposable
import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText

fun currentComposer(): Composer =
    CompositionRuntime.currentComposer() ?: error("MiniCompose composable code must run inside a composition")

fun emitText(composer: Composer, text: String): UiText = composer.text(text)

fun emitButton(composer: Composer, text: String, onClick: () -> Unit): UiButton =
    composer.button(text, onClick)

fun emitColumn(
    composer: Composer,
    spacing: Int = 0,
    content: @MiniComposable () -> Unit
): UiColumn = composer.column(spacing) { content() }

fun emitRow(
    composer: Composer,
    spacing: Int = 0,
    content: @MiniComposable () -> Unit
): UiRow = composer.row(spacing) { content() }

fun <T> emitRemember(composer: Composer, factory: () -> T): T = composer.remember(factory)

fun emitKey(
    composer: Composer,
    vararg keys: Any?,
    content: @MiniComposable () -> Unit
) {
    composer.key(*keys) { content() }
}

fun text(text: String): UiText = emitText(currentComposer(), text)

fun button(text: String, onClick: () -> Unit): UiButton =
    emitButton(currentComposer(), text, onClick)

fun column(
    spacing: Int = 0,
    content: @MiniComposable () -> Unit
): UiColumn = emitColumn(currentComposer(), spacing, content)

fun row(
    spacing: Int = 0,
    content: @MiniComposable () -> Unit
): UiRow = emitRow(currentComposer(), spacing, content)

fun <T> remember(factory: () -> T): T = emitRemember(currentComposer(), factory)

fun key(vararg keys: Any?, content: @MiniComposable () -> Unit) {
    emitKey(currentComposer(), *keys, content = content)
}

fun composableRoot(content: @MiniComposable () -> Unit): RootComposition {
    return RootComposition {
        content()
        requireSingleRootNode()
    }
}
