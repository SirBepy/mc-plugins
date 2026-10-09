plugins {
    id("mc.plugin-conventions")
}

dependencies {
    // ArenaLayout/PillarScanner are tested as pure geometry (no server needed); ArenaProtection
    // and the rebuild-scheduling tests use MockBukkit. Only paper-api is needed here (compileOnly
    // isn't inherited by the test source set, same fix as carry/tweaks's build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}
