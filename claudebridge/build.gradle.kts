plugins {
    id("mc.plugin-conventions")
}

dependencies {
    // compileOnly isn't inherited by the test source set; MockBukkit itself only declares
    // paper-api as compileOnly too, so tests that build real Configuration/UUID-bearing objects
    // need it added back explicitly (same fix as names/tag/build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}
