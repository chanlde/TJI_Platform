// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

val testDocs by tasks.registering(Exec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Run unit tests for documentation validation tooling."
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine("python3", "-m", "unittest", "discover", "-s", rootProject.file("tools").absolutePath, "-p", "test_*.py")
}

val checkDocs by tasks.registering(Exec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Validate documentation layout, links, product-line templates, and legacy Doc paths."
    dependsOn(testDocs)
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine("python3", rootProject.file("tools/check_docs.py").absolutePath)
}

tasks.named("check") {
    dependsOn(checkDocs)
}
