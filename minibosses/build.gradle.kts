plugins {
    id("mc.plugin-conventions")
}

repositories {
    // Classic VaultAPI is only published on jitpack; scoped to this module so it doesn't leak into
    // every other plugin's dependency resolution (same split dragonballs uses).
    maven("https://jitpack.io")
}

dependencies {
    // compileOnly isn't inherited by the test source set (same fix as cartel/tweaks/market/carry's build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")

    // minibosses softdepends on Guild (plugin.yml) to fire GuardianDefeatedEvent on a Guardian's
    // death; same compileOnly + testImplementation split tag uses for its own softdepend on names.
    compileOnly(project(":guild"))
    testImplementation(project(":guild"))

    // VaultUnlocked (installed on the server) still serves the classic Vault Economy API; same
    // pinned coordinates dragonballs already verified against OSV (no advisories) on 2026-10-06.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
}
