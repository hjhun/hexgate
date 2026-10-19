pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "Hexgate"
include(":app", ":terminal-emulator", ":terminal-view")
project(":terminal-emulator").projectDir = file("vendor/terminal-emulator")
project(":terminal-view").projectDir = file("vendor/terminal-view")
