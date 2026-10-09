plugins {
    id("mc.plugin-conventions")
}

repositories {
    maven("https://repo.glaremasters.me/repository/towny/")
}

dependencies {
    // Same Towny coordinates carry/build.gradle.kts already uses for its claim guard.
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
    // compileOnly isn't inherited by the test source set (same fix as tweaks/build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}

// Verifying pass/skip counts via console since build/ reports aren't readable in this session.
tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
