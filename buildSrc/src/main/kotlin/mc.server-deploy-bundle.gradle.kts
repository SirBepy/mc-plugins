// Packages server/dist into server/bundle/mc-server-bundle.tar.gz for upload to the VPS. Applied
// only to server/ (see its plugins block), separate from the assemble-mode task factory there.

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
