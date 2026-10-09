plugins {
    id("mc.plugin-conventions")
}

repositories {
    maven("https://repo.glaremasters.me/repository/towny/")
}

dependencies {
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.4.0")
    // Same Towny coordinates carry and merfolk use; only touched when Towny is enabled (TownyAdminBypass).
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
    // compileOnly isn't inherited by the test source set (same fix as names/tag/build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("dev.aurelium:auraskills-api-bukkit:2.4.0")
}
