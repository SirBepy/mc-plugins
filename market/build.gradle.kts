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

    // dragonballs' DebtService only (the Homes/Cosmetics shops' debt gate); same
    // compileOnly-not-shaded pattern guild already uses for names' NamesApi - dragonballs is a
    // softdepend, not a hard dependency, so this must never get bundled into market's shaded jar.
    compileOnly(project(":dragonballs"))
    testImplementation(project(":dragonballs"))
}
