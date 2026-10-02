import xyz.jpenilla.runpaper.task.RunServer
import java.net.URI

plugins {
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

val ourPlugins = listOf(":names", ":safechests", ":tag")

// Modrinth CDN links, pinned. Bump a version by replacing its URL.
val thirdPartyPlugins = listOf(
    "https://cdn.modrinth.com/data/Vebnzrzj/versions/b0mk8uS6/LuckPerms-Bukkit-5.5.71.jar",
    "https://cdn.modrinth.com/data/J6U9o3JG/versions/eXHdkjXf/HuskHomes-Paper-4.11-9ed80cc.jar",
    "https://cdn.modrinth.com/data/1Ffl2uIq/versions/rE8pwOLY/PvPManager.jar",
    "https://cdn.modrinth.com/data/YqbUofZE/versions/j3QcPcdy/Bolt-1.2.22.jar",
    "https://cdn.modrinth.com/data/HYKaKraK/versions/m78nFxYg/packetevents-spigot-2.14.0.jar",
    "https://cdn.modrinth.com/data/UmLGoGij/versions/ATlquwiT/DiscordSRV-Build-1.30.5.jar",
    "https://cdn.modrinth.com/data/ijC5dDkD/versions/OxlW1jL5/QuickShop-Hikari-6.3.0.3.jar",
    "https://cdn.modrinth.com/data/ayRaM8J7/versions/qZgRzoYs/VaultUnlocked-2.20.3.jar",
    "https://cdn.modrinth.com/data/hXiIvTyT/versions/nY6VN1XH/EssentialsX-2.22.0.jar",
    "https://cdn.modrinth.com/data/P1OZGk5p/versions/FaishMnD/ViaVersion-5.12.0.jar",
    "https://cdn.modrinth.com/data/NpvuJQoq/versions/SxGhdsPK/ViaBackwards-5.12.0.jar",
)

// Worldgen datapacks must exist before the world is first generated.
val datapacks = listOf(
    "https://cdn.modrinth.com/data/8oi3bsk5/versions/fqnsIp6d/Terralith_v2.6.5%2B26.3.zip",
    "https://cdn.modrinth.com/data/ZVzW5oNS/versions/vV8n8tu0/Incendium_Legacy_v5.5.3%2B26.3.zip",
    "https://cdn.modrinth.com/data/LPjGiSO4/versions/BJjbILcf/Nullscape_v2.0.1%2B26.3.zip",
    "https://cdn.modrinth.com/data/aKCwCJlY/versions/EV74q3tR/Structory_v1.3.18%2B26.3.zip",
    "https://cdn.modrinth.com/data/j3FONRYr/versions/4ahPGAsT/Structory_Towers_v1.0.19%2B26.3.zip",
    "https://cdn.modrinth.com/data/wXiGiyGX/versions/sKKMLxcI/Amplified_Nether_v1.3.0%2B26.3.zip",
)

val runDir = layout.projectDirectory.dir("run")

val prepareServer = tasks.register("prepareServer") {
    description = "Downloads datapacks into the test world and writes first-run server settings."
    val dir = runDir.asFile
    outputs.upToDateWhen { false }
    doLast {
        val packDir = dir.resolve("world/datapacks").apply { mkdirs() }
        datapacks.forEach { url ->
            val name = java.net.URLDecoder.decode(url.substringAfterLast('/'), Charsets.UTF_8)
            val target = packDir.resolve(name)
            if (!target.exists()) {
                logger.lifecycle("Downloading datapack $name")
                URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        // Offline mode lets the e2e bots join; this is a local test server only.
        val props = dir.resolve("server.properties")
        if (!props.exists()) {
            props.writeText(
                """
                online-mode=false
                motd=mc-plugins test server
                spawn-protection=0
                view-distance=8
                simulation-distance=6
                enable-command-block=true
                """.trimIndent() + "\n"
            )
        }
    }
}

tasks.named<RunServer>("runServer") {
    minecraftVersion("26.3")
    runDirectory(runDir.asFile)
    ourPlugins.forEach { pluginJars(project(it).tasks.named("shadowJar")) }
    downloadPlugins {
        thirdPartyPlugins.forEach { url(it) }
    }
    jvmArgs("-Xms2G", "-Xmx4G", "-Dcom.mojang.eula.agree=true")
    dependsOn(prepareServer)
}
