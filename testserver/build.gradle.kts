import xyz.jpenilla.runpaper.task.RunServer
import java.net.URI

plugins {
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

val ourPlugins = listOf(":names", ":safechests", ":tag", ":claudebridge")

// Pinned artifact URLs live in buildSrc/src/main/kotlin/ServerArtifacts.kt, shared with server/.
val thirdPartyPlugins = ServerArtifacts.thirdPartyPlugins
val datapacks = ServerArtifacts.datapacks

// run/ is the real-feel server (Paper 26.3 + worldgen datapacks). run-e2e/ is for Mineflayer bots:
// as of 2026-10-02 ViaBackwards mangles older clients' movement packets on a 26.3 server
// ("Invalid move player packet"), and Mineflayer only speaks up to 26.1, so bots get a 26.2 server.
fun registerPrepare(name: String, dir: java.io.File, withDatapacks: Boolean, motd: String, staticDatapacksDir: java.io.File? = null) =
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
            // Non-worldgen datapacks (e.g. the graves pack) load fine when dropped in after the world
            // already exists, unlike worldgen packs above; always overwrite so a committed version bump
            // reaches a world directory left over from a previous run.
            if (staticDatapacksDir != null && staticDatapacksDir.exists()) {
                val packDir = dir.resolve("world/datapacks").apply { mkdirs() }
                staticDatapacksDir.listFiles { f -> f.isFile }?.forEach { source ->
                    source.copyTo(packDir.resolve(source.name), overwrite = true)
                }
            }
            // Towny's rules (free towns, open wilderness) must match the real server for a bot run to
            // mean anything. Towny copies them into each world's own file only when it first sees the
            // world, so an older run dir needs its plugins/Towny/data/worlds/ deleted once.
            serverTownyConfig.copyTo(dir.resolve("plugins/Towny/settings/config.yml"), overwrite = true)
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

val serverTownyConfig = rootProject.layout.projectDirectory.file("server/config/plugins/Towny/settings/config.yml").asFile
val runDir = layout.projectDirectory.dir("run").asFile
val e2eDir = layout.projectDirectory.dir("run-e2e").asFile
val e2eDatapacksDir = layout.projectDirectory.dir("e2e-datapacks").asFile
val prepareServer = registerPrepare("prepareServer", runDir, withDatapacks = true, motd = "mc-plugins test server")
// e2e-datapacks/ holds committed, non-worldgen packs (the graves pack, for Tag's head-vs-grave e2e
// scenario) built for the e2e server's pinned 26.2, separate from the real server's 26.3 build.
val prepareE2eServer = registerPrepare(
    "prepareE2eServer", e2eDir, withDatapacks = false, motd = "mc-plugins e2e server",
    staticDatapacksDir = e2eDatapacksDir,
)

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
