plugins {
    id("mc.plugin-conventions")
}

dependencies {
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.4.0")
    // compileOnly isn't inherited by the test source set (same fix as names/tag/build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("dev.aurelium:auraskills-api-bukkit:2.4.0")
}
