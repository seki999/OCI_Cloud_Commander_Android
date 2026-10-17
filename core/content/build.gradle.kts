plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(17) }
dependencies { api(project(":core:model")); implementation(project(":core:simulator")); implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") }
