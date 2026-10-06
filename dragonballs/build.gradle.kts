plugins {
    id("mc.plugin-conventions")
}

repositories {
    // Classic VaultAPI is only published on jitpack; scoped to this module so it doesn't leak into
    // every other plugin's dependency resolution.
    maven("https://jitpack.io")
}

dependencies {
    // compileOnly isn't inherited by the test source set (same fix as tweaks/market/carry's build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")

    // VaultUnlocked (installed on the server) still serves the classic Vault Economy API; verified
    // 2026-10-06 against OSV (no advisories) and the real MilkBowl/VaultAPI GitHub org/tags.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
}

// Verifying pass/skip counts via console since build/ reports aren't readable in this session.
tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
