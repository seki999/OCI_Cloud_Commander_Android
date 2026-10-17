pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "OCICloudCommander"
include(":app", ":core:model", ":core:simulator", ":core:command", ":core:content", ":core:testing", ":core:persistence")
