import java.net.URI
import java.security.MessageDigest

plugins {
    id("mc.plugin-conventions")
}

repositories {
    // Classic VaultAPI is only published on jitpack; scoped to this module so it doesn't leak into
    // every other plugin's dependency resolution.
    maven("https://jitpack.io")
    maven("https://repo.glaremasters.me/repository/towny/")
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

    // Same coordinates/version/repository carry already pulls Towny's API from; soft-depend, only
    // touched when Towny is actually enabled (see ScatterTownyGuard).
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
}

// The server runs GeyserMC's 26.3 preview build, whose API isn't published to Maven, so the API
// is compiled against that same pinned, sha256-checked jar. Only the API packages are kept: the
// full jar shades libraries that would clash with MockBukkit's on the test classpath.
val geyserPin = ServerArtifacts.realServerExtraPlugins.single { it.filename == "Geyser-Spigot.jar" }
val downloadGeyser = tasks.register("downloadGeyser") {
    val target = layout.buildDirectory.file("geyser/${geyserPin.filename}")
    inputs.property("sha256", geyserPin.sha256)
    outputs.file(target)
    doLast {
        val file = target.get().asFile
        fun sha256() = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (!file.exists() || sha256() != geyserPin.sha256) {
            file.parentFile.mkdirs()
            URI(geyserPin.url).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            check(sha256() == geyserPin.sha256) { "sha256 mismatch for ${geyserPin.url}" }
        }
    }
}
val geyserApiJar = tasks.register<Jar>("geyserApiJar") {
    destinationDirectory = layout.buildDirectory.dir("geyser")
    archiveFileName = "geyser-api-${geyserPin.sha256.take(12)}.jar"
    from(downloadGeyser.map { zipTree(it.outputs.files.singleFile) }) {
        include("org/geysermc/geyser/api/**", "org/geysermc/api/**", "org/geysermc/event/**",
            "org/geysermc/cumulus/**", "org/cloudburstmc/math/**")
    }
}

dependencies {
    // Geyser's API for the Bedrock summon dragons (bedrock/GeyserDragons); Geyser itself provides
    // it at runtime.
    compileOnly(files(geyserApiJar))
    testImplementation(files(geyserApiJar))
}
