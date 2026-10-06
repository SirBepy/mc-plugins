import java.net.URI

// Generates the first-boot op/whitelist/LuckPerms-admin seed data from server/players.properties,
// since RCON is off on the real server and there's no console to run `/op`/`/whitelist add` by hand
// after the very first boot. Writes into server/seed/, so a value already set by the live server
// (e.g. the owner editing ops.json directly) is never overwritten on a later re-seed. Applied only
// to server/ (see its plugins block), separate from the assemble-mode task factory there.
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
