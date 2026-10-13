plugins {
    id("mc.plugin-conventions")
}

dependencies {
    // compileOnly isn't inherited by the test source set (same fix as cartel/tweaks/market/carry's build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}
