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

// run/ is the real-feel server (Paper 26.3 + worldgen datapacks). run-e2e/ is for Mineflayer bots:
// as of 2026-10-02 ViaBackwards mangles older clients' movement packets on a 26.3 server
// ("Invalid move player packet"), and Mineflayer only speaks up to 26.1, so bots get a 26.2 server.
fun registerPrepare(name: String, dir: java.io.File, withDatapacks: Boolean, motd: String) =
    tasks.register(name) {
        description = "Writes first-run server settings" + if (withDatapacks) " and downloads worldgen datapacks." else "."
        outputs.upToDateWhen { false }
        doLast {
            if (withDatapacks) {
                val packDir = dir.resolve("world/datapacks").apply { mkdirs() }
                datapacks.forEach { url ->
                    val file = java.net.URLDecoder.decode(url.substringAfterLast('/'), Charsets.UTF_8)
                    val target = packDir.resolve(file)
                    if (!target.exists()) {
                        logger.lifecycle("Downloading datapack $file")
                        URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                    }
                }
            }
            // Offline mode lets the e2e bots join and RCON lets them run admin commands; this is a
            // local-only test server, so the RCON password is random per checkout and never committed.
            val props = dir.resolve("server.properties")
            if (!props.exists()) {
                dir.mkdirs()
                val rconPassword = java.util.UUID.randomUUID().toString()
                props.writeText(
                    """
                    online-mode=false
                    white-list=false
                    motd=$motd
                    spawn-protection=0
                    view-distance=8
                    simulation-distance=6
                    enable-command-block=true
                    enable-rcon=true
                    rcon.port=25575
                    rcon.password=$rconPassword
                    server-ip=127.0.0.1
                    """.trimIndent() + "\n"
                )
            }
        }
    }

fun RunServer.configureCommon() {
    // -PthirdPartyOnly boots without our plugins; -Ponly=names,tag boots with only those,
    // e.g. while another of ours doesn't compile yet.
    val selected = (findProperty("only") as String?)?.split(',')?.map { ":${it.trim()}" }
    if (!project.hasProperty("thirdPartyOnly")) {
        (selected ?: ourPlugins).forEach { pluginJars(project(it).tasks.named("shadowJar")) }
    }
    downloadPlugins {
        thirdPartyPlugins.forEach { url(it) }
    }
    jvmArgs("-Xms2G", "-Xmx4G", "-Dcom.mojang.eula.agree=true")
}

val runDir = layout.projectDirectory.dir("run").asFile
val e2eDir = layout.projectDirectory.dir("run-e2e").asFile
val prepareServer = registerPrepare("prepareServer", runDir, withDatapacks = true, motd = "mc-plugins test server")
val prepareE2eServer = registerPrepare("prepareE2eServer", e2eDir, withDatapacks = false, motd = "mc-plugins e2e server")

tasks.named<RunServer>("runServer") {
    minecraftVersion("26.3")
    runDirectory(runDir)
    configureCommon()
    dependsOn(prepareServer)
}

tasks.register<RunServer>("runE2eServer") {
    minecraftVersion("26.2")
    runDirectory(e2eDir)
    configureCommon()
    dependsOn(prepareE2eServer)
}
