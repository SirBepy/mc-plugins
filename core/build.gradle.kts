plugins {
    id("mc.java-conventions")
}

repositories {
    // Serves both releases and snapshots; Floodgate ships 2.2.5 only as a snapshot build.
    maven("https://repo.opencollab.dev/main/")
    // Classic VaultAPI is only published on jitpack (same scoping as dragonballs/build.gradle.kts).
    maven("https://jitpack.io")
}

dependencies {
    // BedrockForms (dev.sirbepy.mc.core.bedrock) only; its public API is Floodgate-free, so this
    // stays compileOnly and names/safechests need no Floodgate dependency of their own.
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")

    // VaultEconomy (dev.sirbepy.mc.core.econ) only; same coordinates guild/market/dragonballs
    // already use for the classic Vault Economy API. compileOnly here means a plugin with no
    // Vault on its classpath still loads core fine, as long as it never calls VaultEconomy.find.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }

    // compileOnly isn't inherited by the test source set, and MockBukkit itself only declares
    // paper-api as compileOnly too, so BedrockFormsTest needs it added back explicitly (same fix
    // as names/build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
}
