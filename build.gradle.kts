import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

gradle.projectsEvaluated {
    project(":usage-wrapped-service").extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }
}
