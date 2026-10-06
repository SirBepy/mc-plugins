plugins {
    id("mc.plugin-conventions")
}

dependencies {
    // compileOnly isn't inherited by the test source set (same fix as tweaks/market/carry's build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}

// Verifying pass/skip counts via console since build/ reports aren't readable in this session.
tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
