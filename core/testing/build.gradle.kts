plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(project(":core:content")); testImplementation(project(":core:simulator")); testImplementation(project(":core:command")); testImplementation("junit:junit:4.13.2"); testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") }
tasks.test { systemProperty("contentRoot", rootProject.file("content").absolutePath); testLogging { events("passed", "failed", "skipped") } }
