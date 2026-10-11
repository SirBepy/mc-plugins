plugins {
    id("mc.plugin-conventions")
}

repositories {
    // Classic VaultAPI is only published on jitpack (same scoping as dragonballs/build.gradle.kts).
    maven("https://jitpack.io")
}

dependencies {
    // compileOnly isn't inherited by the test source set (same fix as tweaks/build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")

    // Same coordinates dragonballs and guild already use: VaultUnlocked on the server serves the
    // classic Vault Economy API the Heads stall and Wanted board pay through.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
}
