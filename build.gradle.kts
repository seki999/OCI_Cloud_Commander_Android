plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    kotlin("android") version "2.0.20" apply false
    kotlin("jvm") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
    kotlin("plugin.compose") version "2.0.20" apply false
    kotlin("kapt") version "2.0.20" apply false
}
tasks.register("validateContent") { dependsOn(":core:testing:test") }
tasks.register("solveLevels") { dependsOn(":core:testing:test") }

tasks.register<Exec>("validateJsonSchema") {
    commandLine(providers.gradleProperty("pythonExecutable").getOrElse("python"), "scripts/validate_content.py")
}
tasks.named("validateContent") { dependsOn("validateJsonSchema") }
tasks.register<Exec>("verifyFullGame") {
    dependsOn("solveLevels")
    commandLine(providers.gradleProperty("pythonExecutable").getOrElse("python"), "scripts/validate_content.py", "--require-60")
}
