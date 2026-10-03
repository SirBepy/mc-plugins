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
        // Structure pack #2 alongside Structory (Joe: "I think dungeons-and-taverns is better, but
        // structory might work better with the other datapacks"); no documented conflict found
        // between the two, or with Terralith/Incendium, so both ship (resolved 2026-10-03).
        "https://cdn.modrinth.com/data/tpehi7ww/versions/iqFWpMVO/Dungeons%20and%20Taverns%20v6.0.2.zip",
        // Stonecutter recipe expansion (500+ recipes), unrelated to player heads despite the link
        // Joe sent (see server/datapacks/README.md); pure data, no textures/conflicts.
        "https://cdn.modrinth.com/data/DuUMFIfX/versions/IJwVIaLT/MasterCutter%2Bv1.8.0-mc26.3.zip",
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
        // Build 1247 (the then-latest release) fails to enable on Paper 26.3 beta: its shaded cloud
        // command parser calls the removed CraftItemStack#asBukkitCopy/#asCraftMirror methods (see
        // docs/server.md "Known issue"). Pinned instead to GeyserMC's own official preview build of
        // GeyserMC/Geyser#6712 ("Update to Java 26.3", open/unmerged as of 2026-10-02), published on
        // GeyserMC's own download API under the "geyserpreview" project (pr.6712, build 3057,
        // resolved 2026-10-02 from https://download.geysermc.org/v2/projects/geyserpreview/versions/pr.6712/builds/3057).
        // Swap this back to a normal "geyser" release build once 26.3 support ships there.
        PinnedJar(
            url = "https://download.geysermc.org/v2/projects/geyserpreview/versions/pr.6712/builds/3057/downloads/spigot",
            filename = "Geyser-Spigot.jar",
            sha256 = "d7fcfb9a2ad46980efef2e6a5f6837921b945fe2bbec34efd8c8ca552963c3e1",
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
