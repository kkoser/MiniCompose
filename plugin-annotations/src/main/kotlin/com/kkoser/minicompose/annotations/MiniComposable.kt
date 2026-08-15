package com.kkoser.minicompose.annotations

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class MiniComposable
