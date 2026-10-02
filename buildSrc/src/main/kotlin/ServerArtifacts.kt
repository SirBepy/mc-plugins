/**
 * Pinned download URLs shared between the test server (testserver/build.gradle.kts, two local
 * Paper servers for dev + e2e bots) and the real friends-server assembly
 * (server/build.gradle.kts). One place to bump a version so both stay in sync.
 */
object ServerArtifacts {
    // Modrinth CDN links, pinned. Bump a version by replacing its URL. Same list for both
    // testserver's run/ and run-e2e/, so they behave identically to each other.
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

    // A pinned download with an explicit target filename and expected hash: some hosts (GeyserMC's
    // API) serve a URL that ends in a generic path segment like `/spigot`, not a real filename, and
    // the real-server assembly needs to verify what it downloaded before trusting it on the VPS.
    data class PinnedJar(val url: String, val filename: String, val sha256: String)

    // Plugins only the real friends server runs, never testserver: Geyser/Floodgate need a real
    // Xbox/Bedrock auth round-trip testserver has no use for, and Chunky pre-generates terrain
    // nobody needs on a throwaway local world.
    val realServerExtraPlugins = listOf(
        PinnedJar(
            url = "https://cdn.modrinth.com/data/9eGKb6K1/versions/EJth3OAr/voicechat-bukkit-2.6.24.jar",
            filename = "voicechat-bukkit-2.6.24.jar",
            sha256 = "af3d4e2ad318d3065959248c1370ae32a619752cb4d2a4fec2f7a79b10067769",
        ),
        // Pinned to the concrete version/build resolved from "latest" on 2026-10-02 (2.11.3 / 1247),
        // not tracked as "latest" going forward.
        PinnedJar(
            url = "https://download.geysermc.org/v2/projects/geyser/versions/2.11.3/builds/1247/downloads/spigot",
            filename = "Geyser-Spigot.jar",
            sha256 = "6fed2d9711e2db3c365508ec10db36d06a0199abd226159d23d57f82662fd7a6",
        ),
        // Pinned to 2.2.5 / build 141, resolved the same way and on the same date as Geyser above.
        PinnedJar(
            url = "https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/141/downloads/spigot",
            filename = "floodgate-spigot.jar",
            sha256 = "21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e",
        ),
        PinnedJar(
            url = "https://cdn.modrinth.com/data/fALzjamp/versions/MdY6JATr/Chunky-Bukkit-1.5.3.jar",
            filename = "Chunky-Bukkit-1.5.3.jar",
            sha256 = "530d2c7430a96a39957391b7088be144daa3108f7665896d1c23aa8dd4af32f3",
        ),
    )

    // Paper 26.3 is still beta; pin build 143 explicitly rather than tracking "latest" (resolved
    // 2026-10-02 from https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/143).
    const val paperVersion = "26.3"
    const val paperBuild = 143
    val paperDownload = PinnedJar(
        url = "https://fill-data.papermc.io/v1/objects/32cf4a93545e218525bc4536b017c6b5d5085d27d449d64266a6b23ba4d0cbb9/paper-26.3-143.jar",
        filename = "paper-26.3-143.jar",
        sha256 = "32cf4a93545e218525bc4536b017c6b5d5085d27d449d64266a6b23ba4d0cbb9",
    )
}
