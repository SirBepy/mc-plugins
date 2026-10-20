import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("mc.server-deploy-bundle")
    id("mc.server-seed-players")
}

// Assembles the real friends-server folder: our plugins + the pinned third-party list +
// worldgen/committed datapacks + owner config, ready to copy to the Hetzner VPS later.
// Mirrors testserver's imperative-task style (see testserver/build.gradle.kts) rather than
// Gradle's Copy/Sync tasks, because a Sync would delete files it doesn't know about
// (world saves, plugin data) and that is explicitly forbidden here. deployBundle (packaging the
// VPS upload) and seedPlayers (Mojang UUID resolution) are separate convention plugins, applied
// above, so this file stays to the assemble-mode task factory.

val ourPluginProjects = ServerArtifacts.ourPluginProjects

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

// Minimal hand-rolled JSON: these filenames never contain a quote or backslash (Modrinth slugs and
// the committed datapacks/ entries), so a full JSON writer dependency isn't worth adding for this.
fun jsonStringArray(values: List<String>) = values.joinToString(",\n    ") { "\"$it\"" }

fun jsonManifest(stardust: List<String>, bundled: List<String>) =
    "{\n" +
        "  \"stardust\": [\n    ${jsonStringArray(stardust)}\n  ],\n" +
        "  \"bundled\": [\n    ${jsonStringArray(bundled)}\n  ]\n" +
        "}\n"

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
                "# Aikar's flags, sized for the CX43 VPS (16 GB RAM, 8 shared vCPU). Override MC_HEAP for\n" +
                "# constrained environments (e.g. the Docker smoke test); defaults to the real sizing.\n" +
                "cd \"\$(dirname \"\$0\")\"\n" +
                "MC_HEAP=\"\${MC_HEAP:-10G}\"\n" +
                "exec java -Xms\$MC_HEAP -Xmx\$MC_HEAP \\\n  " +
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

// Writes secrets.properties values into the ASSEMBLED copy only (distDir), never into
// server/config/ (the committed, owner-editable source), so a real bot token never lands in git.
fun injectSecrets(distDir: File) {
    val secretsFile = layout.projectDirectory.file("secrets.properties").asFile
    if (!secretsFile.exists()) return
    val props = java.util.Properties().apply { secretsFile.inputStream().use { load(it) } }
    // Minibosses decrypts the Guardian Chronicles at startup with this key; without it they stay locked.
    props.getProperty("minibosses.chronicles-key")?.let { key ->
        distDir.resolve("plugins/Minibosses").apply { mkdirs() }.resolve("chronicles.key").writeText(key.trim())
    }
    val discordToken = props.getProperty("discordbot.token") ?: return
    val configFile = distDir.resolve("plugins/DiscordBot/config.yml")
    // A brand-new plugin's data folder doesn't exist yet on a fresh dist (only created by the
    // plugin's own saveDefaultConfig() at runtime); extract its bundled default here instead of
    // skipping the injection, so the uploaded copy already has the real token on first boot.
    if (!configFile.exists()) {
        val jar = distDir.resolve("plugins").listFiles { f -> f.name.startsWith("discordbot-") }?.firstOrNull()
        if (jar == null) return
        java.util.zip.ZipFile(jar).use { zip ->
            val entry = zip.getEntry("config.yml") ?: return
            configFile.parentFile.mkdirs()
            zip.getInputStream(entry).use { input -> configFile.outputStream().use { input.copyTo(it) } }
        }
    }
    val lines = configFile.readLines().toMutableList()
    // Overwrite unconditionally regardless of the line's current value (committed source ships an
    // empty token so the secret scanner never flags a placeholder that looks real).
    val idx = lines.indexOfFirst { it.startsWith("bot-token:") }
    if (idx >= 0) {
        lines[idx] = "bot-token: \"$discordToken\""
        configFile.writeText(lines.joinToString("\n") + "\n")
    }
}

// Shared by every local-only override function below: patches/appends properties into an existing
// (or absent, on a very first assemble) server.properties without touching unrelated keys.
fun applyPropertyOverrides(propsFile: File, overrides: Map<String, String>) {
    // On the very first assemble of a fresh dist dir, server.properties doesn't exist yet (it's
    // generated from this first boot, per docs/server.md), so there may be nothing to read here yet.
    val lines = if (propsFile.exists()) propsFile.readLines().toMutableList() else mutableListOf()
    overrides.forEach { (key, value) ->
        val line = "$key=$value"
        val idx = lines.indexOfFirst { it.startsWith("$key=") }
        if (idx >= 0) lines[idx] = line else lines.add(line)
    }
    propsFile.writeText(lines.joinToString("\n") + "\n")
}

// A fresh random password only on the very first assemble of a dist dir; re-assembling keeps
// whatever RCON already authenticates with so a booted server's console doesn't get locked out.
fun rconPasswordOrGenerate(previous: String?) = previous?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()

// Shared by applyLocalOverrides/applyPregenOverrides below: both are disposable, local-only boots
// needing the same 8 keys, differing only in which port pair they bind.
fun disposableBootOverrides(port: String, rconPort: String, previousRconPassword: String?) = linkedMapOf(
    "online-mode" to "false",
    "white-list" to "false",
    "enforce-whitelist" to "false",
    "server-ip" to "127.0.0.1",
    "server-port" to port,
    "enable-rcon" to "true",
    "rcon.port" to rconPort,
    "rcon.password" to rconPasswordOrGenerate(previousRconPassword),
)

// Local-smoke-test-only overrides, applied after the real server.properties is copied in. Never
// called by assembleServer, only assembleLocalServer, so these never reach the real server.
fun applyLocalOverrides(propsFile: File, previousRconPassword: String?) {
    // 25566/25576 (not the real server's 25565/25575): so this local smoke server can run
    // alongside testserver's bot e2e server, which owns 25565/25575.
    applyPropertyOverrides(propsFile, disposableBootOverrides("25566", "25576", previousRconPassword))
}

// Terrain-pregeneration-only overrides: a disposable, Chunky-driven boot used once to generate the
// world before it's uploaded to the host, never touched by assembleServer or assembleLocalServer.
fun applyPregenOverrides(propsFile: File, previousRconPassword: String?) {
    // 25567/25577: distinct from the real server (25565/25575), server/local (25566/25576)
    // and testserver's e2e server (25565/25575), so all four can run at once if needed.
    applyPropertyOverrides(propsFile, disposableBootOverrides("25567", "25577", previousRconPassword))
}

// Local-smoke-test-only: Geyser's own Bedrock login validation checks a real Xbox Live JWT chain,
// which the e2e bedrock-protocol test client (an unauthenticated/offline Bedrock client, see
// e2e/bedrock.mjs) can never produce. Disabling it is a real security hole for the real server
// (anyone could spoof a Bedrock username), so this is appended only to server/local's copy of the
// config, never to server/config/plugins/Geyser-Spigot/config.yml. The committed config has no
// "advanced:" block yet (see its own header), so this appends one rather than patching a line.
fun applyLocalGeyserOverrides(distDir: File) {
    val configFile = distDir.resolve("plugins/Geyser-Spigot/config.yml")
    if (!configFile.exists()) return
    val text = configFile.readText()
    if (Regex("(?m)^advanced:").containsMatchIn(text)) return
    configFile.writeText(
        text.trimEnd('\n') + "\n" +
            "advanced:\n  bedrock:\n    validate-bedrock-login: false\n"
    )
}

// REAL ships to the host as-is; LOCAL and PREGEN are both disposable, local-only boots that get
// server.properties overrides (different ports/passwords each, see applyLocalOverrides/applyPregenOverrides).
enum class AssembleMode { REAL, LOCAL, PREGEN }

fun registerAssemble(name: String, distDirPath: String, mode: AssembleMode) = tasks.register(name) {
    group = "server"
    description = when (mode) {
        AssembleMode.REAL -> "Assembles the real friends-server folder into server/dist."
        AssembleMode.LOCAL -> "Assembles the real server folder into server/local with local-only overrides, for smoke testing."
        AssembleMode.PREGEN -> "Assembles the real server folder into server/pregen with local-only overrides, for one-time Chunky terrain pre-generation."
    }
    outputs.upToDateWhen { false }
    dependsOn(ourPluginProjects.map { project(it).tasks.named("shadowJar") })
    doLast {
        // Lives under the project dir, not buildDirectory: `gradlew clean` wipes build/, which
        // would otherwise delete a locally booted world and Floodgate's key.pem.
        val distDir = layout.projectDirectory.dir(distDirPath).asFile
        distDir.mkdirs()

        // Captured before the config copy below overwrites server.properties, so a local rcon
        // password survives repeated assembleLocalServer/assemblePregenServer runs ("generated once").
        val propsFile = distDir.resolve("server.properties")
        val previousRconPassword = if (mode != AssembleMode.REAL && propsFile.exists()) {
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
        val stardustFilenames = ServerArtifacts.datapacks.map { url ->
            val filename = java.net.URLDecoder.decode(url.substringAfterLast('/'), Charsets.UTF_8)
            downloadIfMissing(url, datapackDir.resolve(filename))
            filename
        }
        val committedDatapacksDir = layout.projectDirectory.dir("datapacks").asFile
        copyTree(committedDatapacksDir, datapackDir, exclude = isReadmeFile)
        // Top-level entries only (matches what lands directly under world/datapacks: a committed
        // pack is either one zip file or one folder, never copied file-by-file into the manifest).
        val bundledFilenames = (committedDatapacksDir.listFiles() ?: emptyArray())
            .filter { !isReadmeFile(it) }
            .map { it.name }
            .sorted()

        // e2e/server-check.mjs reads this instead of hardcoding the Stardust filenames itself, so
        // bumping a pinned URL above changes what the check expects without touching the e2e script.
        distDir.resolve("datapacks.manifest.json").writeText(jsonManifest(stardustFilenames, bundledFilenames))

        copyTree(layout.projectDirectory.dir("config").asFile, distDir)
        copyTreeIfAbsent(layout.projectDirectory.dir("seed").asFile, distDir, exclude = isReadmeFile)

        distDir.resolve("eula.txt").writeText(
            "# The owner has accepted the Minecraft EULA (https://aka.ms/MinecraftEULA) by running a server.\n" +
                "eula=true\n"
        )

        writeStartScripts(distDir)
        injectSecrets(distDir)

        when (mode) {
            AssembleMode.LOCAL -> {
                applyLocalOverrides(propsFile, previousRconPassword)
                applyLocalGeyserOverrides(distDir)
            }
            AssembleMode.PREGEN -> applyPregenOverrides(propsFile, previousRconPassword)
            AssembleMode.REAL -> {}
        }
    }
}

registerAssemble("assembleServer", "dist", AssembleMode.REAL)
registerAssemble("assembleLocalServer", "local", AssembleMode.LOCAL)
registerAssemble("assemblePregenServer", "pregen", AssembleMode.PREGEN)

// No build/compile plugin is applied to this module (it only assembles files), so there's no
// `clean` task by default; added for the one-time migration off the old server/build/{dist,local}
// layout, and as a safety valve if Gradle itself ever writes stray cache files under build/.
tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
