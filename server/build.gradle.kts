import java.io.File
import java.net.URI
import java.security.MessageDigest

// Assembles the real friends-server folder: our plugins + the pinned third-party list +
// worldgen/committed datapacks + owner config, ready to copy to the Hetzner VPS later.
// Mirrors testserver's imperative-task style (see testserver/build.gradle.kts) rather than
// Gradle's Copy/Sync tasks, because a Sync would delete files it doesn't know about
// (world saves, plugin data) and that is explicitly forbidden here.

val ourPluginProjects = listOf(":names", ":safechests", ":tag", ":claudebridge", ":tweaks", ":carry", ":market", ":dragonballs")

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
    val discordToken = props.getProperty("discordsrv.token") ?: return
    val configFile = distDir.resolve("plugins/DiscordSRV/config.yml")
    if (!configFile.exists()) return
    val lines = configFile.readLines().toMutableList()
    // Overwrite unconditionally regardless of the line's current value (committed source ships an
    // empty token so the secret scanner never flags a placeholder that looks real, e.g. "BOTTOKEN").
    val idx = lines.indexOfFirst { it.startsWith("BotToken:") }
    if (idx >= 0) {
        lines[idx] = "BotToken: \"$discordToken\""
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

// Local-smoke-test-only overrides, applied after the real server.properties is copied in. Never
// called by assembleServer, only assembleLocalServer, so these never reach the real server.
fun applyLocalOverrides(propsFile: File, previousRconPassword: String?) {
    applyPropertyOverrides(
        propsFile,
        linkedMapOf(
            "online-mode" to "false",
            "white-list" to "false",
            "enforce-whitelist" to "false",
            "server-ip" to "127.0.0.1",
            // 25566/25576 (not the real server's 25565/25575): so this local smoke server can run
            // alongside testserver's bot e2e server, which owns 25565/25575.
            "server-port" to "25566",
            "enable-rcon" to "true",
            "rcon.port" to "25576",
            "rcon.password" to rconPasswordOrGenerate(previousRconPassword),
        ),
    )
}

// Terrain-pregeneration-only overrides: a disposable, Chunky-driven boot used once to generate the
// world before it's uploaded to the host, never touched by assembleServer or assembleLocalServer.
fun applyPregenOverrides(propsFile: File, previousRconPassword: String?) {
    applyPropertyOverrides(
        propsFile,
        linkedMapOf(
            "online-mode" to "false",
            "white-list" to "false",
            "enforce-whitelist" to "false",
            "server-ip" to "127.0.0.1",
            // 25567/25577: distinct from the real server (25565/25575), server/local (25566/25576)
            // and testserver's e2e server (25565/25575), so all four can run at once if needed.
            "server-port" to "25567",
            "enable-rcon" to "true",
            "rcon.port" to "25577",
            "rcon.password" to rconPasswordOrGenerate(previousRconPassword),
        ),
    )
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

fun gitShortSha(): String {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    return output.ifBlank { "unknown" }
}

// server/dist/ should never contain these (nothing in assembleServer writes them), but this is a
// defensive check so a future change to this file can't accidentally tar up a booted server's
// world saves, logs, or Floodgate's key.pem into something that gets uploaded to the VPS.
fun isForbiddenInBundle(relPath: String): Boolean {
    val normalized = relPath.replace('\\', '/')
    return (normalized.startsWith("world/") && !normalized.startsWith("world/datapacks/")) ||
        normalized == "logs" || normalized.startsWith("logs/") || normalized.contains("/logs/") ||
        normalized.endsWith("key.pem")
}

tasks.register("deployBundle") {
    group = "server"
    description = "Packages server/dist into server/bundle/mc-server-bundle.tar.gz for upload to the VPS."
    outputs.upToDateWhen { false }
    dependsOn("assembleServer")
    doLast {
        val distDir = layout.projectDirectory.dir("dist").asFile
        val seedDir = layout.projectDirectory.dir("seed").asFile
        // Lives under build/ (unlike dist/local): bundle staging is fully regenerated every run,
        // is never booted, and holds no world/plugin data worth protecting from `gradlew clean`.
        val stagingDir = layout.buildDirectory.dir("bundle-staging").get().asFile
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        // Seed-originated files (e.g. the committed LuckPerms starting groups) land in dist at the
        // same relative path assembleServer wrote them to (copyTreeIfAbsent), since nothing else in
        // a fresh assemble creates those paths. Excluding them from the bulk copy below and copying
        // server/seed/ separately into a top-level seed/ dir is what lets apply-bundle.sh apply the
        // two trees with different overwrite rules on the remote end.
        val seedRelativePaths = seedDir.walkTopDown()
            .filter { it.isFile && it.name != "README.md" }
            .map { it.relativeTo(seedDir).path }
            .toSet()

        distDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(distDir).path
            if (rel in seedRelativePaths) return@forEach
            if (isForbiddenInBundle(rel)) {
                logger.warn("deployBundle: skipping unexpected file in dist (should never be there): $rel")
                return@forEach
            }
            val target = stagingDir.resolve(rel)
            target.parentFile.mkdirs()
            file.copyTo(target, overwrite = true)
        }
        copyTree(seedDir, stagingDir.resolve("seed"), exclude = isReadmeFile)

        // Rename the version-pinned jar to a stable name so the deploy scripts and apply-bundle.sh
        // never need to know the exact Paper build; bump ServerArtifacts.kt's pin, nothing else.
        val pinnedJarName = ServerArtifacts.paperDownload.filename
        val pinnedJar = stagingDir.resolve(pinnedJarName)
        check(pinnedJar.exists()) { "expected $pinnedJarName in server/dist; run :server:assembleServer first" }
        pinnedJar.copyTo(stagingDir.resolve("paper.jar"), overwrite = true)
        pinnedJar.delete()
        // start.sh/start.bat already reference the pinned jar name; rewrite just that one line
        // rather than regenerating the file, so Aikar's flags stay defined in exactly one place.
        val startSh = stagingDir.resolve("start.sh")
        startSh.writeText(startSh.readText().replace(pinnedJarName, "paper.jar"))
        val startBat = stagingDir.resolve("start.bat")
        if (startBat.exists()) startBat.writeText(startBat.readText().replace(pinnedJarName, "paper.jar"))

        stagingDir.resolve("BUNDLE_VERSION").writeText("${gitShortSha()} ${java.time.LocalDate.now()}\n")

        val bundleDir = layout.projectDirectory.dir("bundle").asFile.apply { mkdirs() }
        val tarFile = bundleDir.resolve("mc-server-bundle.tar.gz")
        tarFile.delete()
        val topLevelEntries = stagingDir.listFiles()!!.map { it.name }.sorted()
        val tarCommand = listOf("tar", "czf", tarFile.absolutePath, "-C", stagingDir.absolutePath) + topLevelEntries
        val tarProcess = ProcessBuilder(tarCommand).redirectErrorStream(true).start()
        val tarOutput = tarProcess.inputStream.bufferedReader().readText()
        check(tarProcess.waitFor() == 0) { "tar failed: $tarOutput" }

        logger.lifecycle("Wrote $tarFile")
    }
}

// Generates the first-boot op/whitelist/LuckPerms-admin seed data from server/players.properties,
// since RCON is off on the real server and there's no console to run `/op`/`/whitelist add` by hand
// after the very first boot. Writes into server/seed/, so a value already set by the live server
// (e.g. the owner editing ops.json directly) is never overwritten on a later re-seed.
data class SeedPlayer(val name: String, val isAdmin: Boolean)
data class ResolvedSeedPlayer(val name: String, val uuid: String, val isAdmin: Boolean)

fun readSeedPlayers(): List<SeedPlayer> {
    val file = layout.projectDirectory.file("players.properties").asFile
    if (!file.exists()) return emptyList()
    val props = java.util.Properties().apply { file.inputStream().use { load(it) } }
    fun names(key: String) = props.getProperty(key, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return names("admins").map { SeedPlayer(it, isAdmin = true) } + names("players").map { SeedPlayer(it, isAdmin = false) }
}

fun dashedUuid(hex32: String): String {
    check(hex32.length == 32) { "expected a 32-char hex UUID, got '$hex32'" }
    return "${hex32.substring(0, 8)}-${hex32.substring(8, 12)}-${hex32.substring(12, 16)}-" +
        "${hex32.substring(16, 20)}-${hex32.substring(20)}"
}

// Mojang's public read-only profile lookup; no auth needed. Returns null if the name doesn't
// resolve to a Java account (typo, Bedrock-only via Floodgate, etc).
fun resolveMojangUuid(name: String): String? {
    val connection = URI("https://api.mojang.com/users/profiles/minecraft/$name").toURL()
        .openConnection() as java.net.HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    return try {
        if (connection.responseCode != 200) return null
        val body = connection.inputStream.bufferedReader().readText()
        Regex(""""id"\s*:\s*"([0-9a-fA-F]{32})"""").find(body)?.groupValues?.get(1)?.let { dashedUuid(it) }
    } finally {
        connection.disconnect()
    }
}

tasks.register("seedPlayers") {
    group = "server"
    description = "Reads server/players.properties and writes server/seed/{ops.json,whitelist.json,LuckPerms admin users}."
    outputs.upToDateWhen { false }
    doLast {
        val wanted = readSeedPlayers()
        if (wanted.isEmpty()) {
            logger.lifecycle("seedPlayers: server/players.properties has no admins/players configured yet, nothing to do.")
            return@doLast
        }
        val resolved = wanted.mapNotNull { p ->
            val uuid = resolveMojangUuid(p.name)
            if (uuid == null) {
                logger.warn("seedPlayers: could not resolve a Mojang UUID for '${p.name}' (typo, or not a Java account?), skipping")
                null
            } else {
                ResolvedSeedPlayer(p.name, uuid, p.isAdmin)
            }
        }
        if (resolved.isEmpty()) return@doLast

        val seedDir = layout.projectDirectory.dir("seed").asFile

        fun jsonArray(entries: List<ResolvedSeedPlayer>, fields: (ResolvedSeedPlayer) -> String) =
            if (entries.isEmpty()) "[]\n" else "[\n" + entries.joinToString(",\n") { "  {\n${fields(it)}\n  }" } + "\n]\n"

        seedDir.resolve("ops.json").apply {
            parentFile.mkdirs()
            writeText(
                jsonArray(resolved.filter { it.isAdmin }) { r ->
                    "    \"uuid\": \"${r.uuid}\",\n    \"name\": \"${r.name}\",\n    \"level\": 4,\n    \"bypassesPlayerLimit\": false"
                }
            )
        }
        seedDir.resolve("whitelist.json").apply {
            parentFile.mkdirs()
            writeText(jsonArray(resolved) { r -> "    \"uuid\": \"${r.uuid}\",\n    \"name\": \"${r.name}\"" })
        }

        // Format matches LuckPerms' own YamlUser output: a bare group name under "parents:" (the
        // per-user file's own inheritance key), unlike a group file's "permissions:" list, which
        // expresses inheriting ANOTHER group as a "group.<name>" permission node instead.
        val usersDir = seedDir.resolve("plugins/LuckPerms/yaml-storage/users").apply { mkdirs() }
        resolved.filter { it.isAdmin }.forEach { r ->
            usersDir.resolve("${r.uuid}.yml").writeText(
                "uuid: ${r.uuid}\nname: ${r.name}\nprimary-group: admin\nparents:\n- admin\n"
            )
        }

        logger.lifecycle("seedPlayers: wrote seed files for ${resolved.size} player(s), ${resolved.count { it.isAdmin }} admin.")
    }
}

// No build/compile plugin is applied to this module (it only assembles files), so there's no
// `clean` task by default; added for the one-time migration off the old server/build/{dist,local}
// layout, and as a safety valve if Gradle itself ever writes stray cache files under build/.
tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
