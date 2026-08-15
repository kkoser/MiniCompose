package com.kkoser.minicompose.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import javax.inject.Inject

class MiniComposeCompilerGradlePlugin @Inject constructor() : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.dependencies.add(
            "implementation",
            "${target.group}:plugin-annotations:${target.version}"
        )
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "com.kkoser.minicompose.compiler"

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(
            groupId = "com.kkoser.minicompose",
            artifactId = "compiler-plugin",
            version = "1.0-SNAPSHOT"
        )
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider { emptyList() }
    }
}
