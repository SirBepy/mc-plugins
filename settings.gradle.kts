plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mc-plugins"

include("core", "names", "safechests", "tag", "claudebridge", "testserver", "server")
