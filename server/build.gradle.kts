import java.io.File
import java.net.URI
import java.security.MessageDigest

// Assembles the real friends-server folder: our plugins + the pinned third-party list +
// worldgen/committed datapacks + owner config, ready to copy to the Hetzner VPS later.
// Mirrors testserver's imperative-task style (see testserver/build.gradle.kts) rather than
// Gradle's Copy/Sync tasks, because a Sync would delete files it doesn't know about
// (world saves, plugin data) and that is explicitly forbidden here.

val ourPluginProjects = listOf(":names", ":safechests", ":tag")

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

// Third-party plugins and worldgen datapacks are not version-pinned by hash (matches testserver's
// existing behavior); only the real-server-only downloads (Paper itself, Geyser, Floodgate,
// Chunky, Simple Voice Chat) are, since those are what actually gets deployed to the VPS.
fun downloadIfMissing(url: String, target: File) {
    if (target.exists()) return
    target.parentFile.mkdirs()
    logger.lifecycle("Downloading ${target.name}")
    URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
}

fun downloadPinned(pinned: ServerArtifacts.PinnedJar, target: File) {
    if (target.exists()) {
        val actual = sha256(target)
        check(actual == pinned.sha256) {
            "${target.name} on disk does not match its pinned sha256 (expected ${pinned.sha256}, got $actual); " +
                "delete it and re-run to re-download."
        }
        return
    }
    target.parentFile.mkdirs()
    logger.lifecycle("Downloading ${pinned.filename}")
    URI(pinned.url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
    val actual = sha256(target)
    check(actual == pinned.sha256) { "sha256 mismatch for ${pinned.filename}: expected ${pinned.sha256}, got $actual" }
}

fun copyTree(src: File, destRoot: File, exclude: (File) -> Boolean = { false }) {
    if (!src.exists()) return
    src.walkTopDown().filter { it.isFile && !exclude(it) }.forEach { file ->
        val target = destRoot.resolve(file.relativeTo(src).path)
        target.parentFile.mkdirs()
        file.copyTo(target, overwrite = true)
    }
}

// Seed data becomes the live server's own after first boot (e.g. LuckPerms data), so a target
// file that already exists in the dist dir (ours or the running server's) is left alone.
fun copyTreeIfAbsent(src: File, destRoot: File, exclude: (File) -> Boolean = { false }) {
    if (!src.exists()) return
    src.walkTopDown().filter { it.isFile && !exclude(it) }.forEach { file ->
        val target = destRoot.resolve(file.relativeTo(src).path)
        if (!target.exists()) {
            target.parentFile.mkdirs()
            file.copyTo(target)
        }
    }
}

val isReadmeFile: (File) -> Boolean = { it.name == "README.md" }

fun writeStartScripts(distDir: File) {
    val jarName = ServerArtifacts.paperDownload.filename
    // Aikar's flags (https://docs.papermc.io/paper/aikars-flags), sized for the CX43 VPS (16 GB RAM).
    val aikarFlags = listOf(
        "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch",
        "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32", "-XX:+PerfDisableSharedMem",
        "-XX:MaxTenuringThreshold=1", "-Dusing.aikars.flags=https://mcflags.emc.gs",
        "-Dio.netty.allocator.maxOrder=9",
    )
    distDir.resolve("start.sh").apply {
        writeText(
            "#!/usr/bin/env bash\n" +
                "# Aikar's flags, sized for the CX43 VPS (16 GB RAM, 8 shared vCPU).\n" +
                "cd \"\$(dirname \"\$0\")\"\n" +
                "exec java -Xms10G -Xmx10G \\\n  " +
                aikarFlags.joinToString(" \\\n  ") +
                " \\\n  -jar $jarName --nogui\n"
        )
    }
    distDir.resolve("start.bat").apply {
        writeText(
            "@echo off\r\n" +
                "rem Local Windows equivalent of start.sh, same flags.\r\n" +
                "cd /d %~dp0\r\n" +
                "java -Xms10G -Xmx10G ^\r\n  " +
                aikarFlags.joinToString(" ^\r\n  ") +
                " ^\r\n  -jar $jarName --nogui\r\n"
        )
    }
}

// No-op for now: DiscordSRV's config.yml isn't committed yet (docs/plan.md "Later" list), so there
// is nowhere to put the token. This hook exists so wiring it up later touches one function, not the
// whole assemble task.
fun injectSecrets(distDir: File) {
    val secretsFile = layout.projectDirectory.file("secrets.properties").asFile
    if (!secretsFile.exists()) return
    val props = java.util.Properties().apply { secretsFile.inputStream().use { load(it) } }
    @Suppress("UNUSED_VARIABLE")
    val discordToken = props.getProperty("discordsrv.token") ?: return
    // TODO(part 2): write discordToken into plugins/DiscordSRV/config.yml once that file is committed.
}

// Local-smoke-test-only overrides, applied after the real server.properties is copied in. Never
// called by assembleServer, only assembleLocalServer, so these never reach the real server.
fun applyLocalOverrides(propsFile: File, previousRconPassword: String?) {
    val rconPassword = previousRconPassword?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
    val overrides = linkedMapOf(
        "online-mode" to "false",
        "white-list" to "false",
        "enforce-whitelist" to "false",
        "server-ip" to "127.0.0.1",
        "enable-rcon" to "true",
        "rcon.port" to "25575",
        "rcon.password" to rconPassword,
    )
    // On the very first local assemble, config/server.properties doesn't exist yet (it's generated
    // from this first boot, per docs/server.md), so there may be nothing to read here yet.
    val lines = if (propsFile.exists()) propsFile.readLines().toMutableList() else mutableListOf()
    overrides.forEach { (key, value) ->
        val line = "$key=$value"
        val idx = lines.indexOfFirst { it.startsWith("$key=") }
        if (idx >= 0) lines[idx] = line else lines.add(line)
    }
    propsFile.writeText(lines.joinToString("\n") + "\n")
}

fun registerAssemble(name: String, distDirPath: String, local: Boolean) = tasks.register(name) {
    group = "server"
    description = if (local) {
        "Assembles the real server folder into server/build/local with local-only overrides, for smoke testing."
    } else {
        "Assembles the real friends-server folder into server/build/dist."
    }
    outputs.upToDateWhen { false }
    dependsOn(ourPluginProjects.map { project(it).tasks.named("shadowJar") })
    doLast {
        val distDir = layout.buildDirectory.dir(distDirPath).get().asFile
        distDir.mkdirs()

        // Captured before the config copy below overwrites server.properties, so a local rcon
        // password survives repeated assembleLocalServer runs ("generated once").
        val propsFile = distDir.resolve("server.properties")
        val previousRconPassword = if (local && propsFile.exists()) {
            propsFile.readLines().firstOrNull { it.startsWith("rcon.password=") }?.substringAfter('=')
        } else {
            null
        }

        downloadPinned(ServerArtifacts.paperDownload, distDir.resolve(ServerArtifacts.paperDownload.filename))

        val pluginsDir = distDir.resolve("plugins").apply { mkdirs() }
        ourPluginProjects.forEach { path ->
            val jar = project(path).tasks.named("shadowJar").get().outputs.files.singleFile
            jar.copyTo(pluginsDir.resolve(jar.name), overwrite = true)
        }
        ServerArtifacts.thirdPartyPlugins.forEach { url ->
            val filename = java.net.URLDecoder.decode(url.substringAfterLast('/'), Charsets.UTF_8)
            downloadIfMissing(url, pluginsDir.resolve(filename))
        }
        ServerArtifacts.realServerExtraPlugins.forEach { pinned ->
            downloadPinned(pinned, pluginsDir.resolve(pinned.filename))
        }

        val datapackDir = distDir.resolve("world/datapacks").apply { mkdirs() }
        ServerArtifacts.datapacks.forEach { url ->
            val filename = java.net.URLDecoder.decode(url.substringAfterLast('/'), Charsets.UTF_8)
            downloadIfMissing(url, datapackDir.resolve(filename))
        }
        copyTree(layout.projectDirectory.dir("datapacks").asFile, datapackDir, exclude = isReadmeFile)

        copyTree(layout.projectDirectory.dir("config").asFile, distDir)
        copyTreeIfAbsent(layout.projectDirectory.dir("seed").asFile, distDir, exclude = isReadmeFile)

        distDir.resolve("eula.txt").writeText(
            "# The owner has accepted the Minecraft EULA (https://aka.ms/MinecraftEULA) by running a server.\n" +
                "eula=true\n"
        )

        writeStartScripts(distDir)
        injectSecrets(distDir)

        if (local) {
            applyLocalOverrides(propsFile, previousRconPassword)
        }
    }
}

registerAssemble("assembleServer", "dist", local = false)
registerAssemble("assembleLocalServer", "local", local = true)
