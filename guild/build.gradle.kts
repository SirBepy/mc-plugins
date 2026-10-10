plugins {
    id("mc.plugin-conventions")
}

repositories {
    // Classic VaultAPI is only published on jitpack (same scoping as dragonballs/build.gradle.kts).
    maven("https://jitpack.io")
}

dependencies {
    // compileOnly isn't inherited by the test source set (same fix as tweaks/market/carry's build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")

    // Title tags go through Names' prefix API (NamesTitleSink); Names is a softdepend, so not shaded.
    compileOnly(project(":names"))

    // Same coordinates dragonballs already uses for the radar shop: VaultUnlocked on the server serves
    // the classic Vault Economy API (checked against OSV, no advisories, 2026-10-06).
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
}

// Verifying pass/skip counts via console since build/ reports aren't readable in this session.
tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
