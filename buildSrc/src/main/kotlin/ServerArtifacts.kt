/**
 * Pinned download URLs shared between the test server (testserver/build.gradle.kts, two local
 * Paper servers for dev + e2e bots) and the real friends-server assembly
 * (server/build.gradle.kts). One place to bump a version so both stay in sync.
 */
object ServerArtifacts {
    // Our own plugin modules, shared so a new one only needs adding here, not in both build files.
    val ourPluginProjects = listOf(":names", ":safechests", ":tag", ":claudebridge", ":tweaks", ":carry", ":market", ":dragonballs", ":haru", ":cartel", ":guild", ":endfight", ":merfolk", ":minibosses", ":classes")

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
        // Land claims. Modrinth lists Towny up to 26.2 only; enabled cleanly on Paper 26.3 build 143
        // (checked 2026-10-03).
        "https://cdn.modrinth.com/data/Vs77PB2W/versions/pra46LOM/Towny-0.103.2.0.jar",
        // Skills/XP in place of mcMMO, whose current builds are paid-only (no official free jar on
        // GitHub, Hangar or Modrinth, checked 2026-10-03). Modrinth lists 26.3 for this version.
        "https://cdn.modrinth.com/data/uDdZAVls/versions/9rSJ3THD/AuraSkills-2.4.0.jar",
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
        // Superseded by the Eclipse "Backpacks!" port in server/datapacks/eclipse-backpacks, but kept so
        // backpacks crafted from it still open and can be emptied. CC-BY-ND: shipped unmodified. Its own
        // textures are no longer offered (server.properties pushes Eclipse's pack), so Java clients see
        // these as a carrot on a stick.
        "https://cdn.modrinth.com/data/mbRlC0kb/versions/gPNeY4V0/Backpacks26.3-DP.zip",
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
        // Release build 1255 (2026-10-14, "Version bumps for Java 26.3") shipped GeyserMC/Geyser#6712
        // as a real release, replacing the earlier "geyserpreview" pr.6712 pin; build 1262
        // (2026-10-16, resolved from https://download.geysermc.org/v2/projects/geyser/versions/latest/builds)
        // also carries GeyserMC/Geyser#6786, which fixes the registry-freeze bug filed upstream as
        // GeyserMC/Geyser#6764 (confirmed via `gh api repos/GeyserMC/Geyser/issues/6764`: closed,
        // completed, by merged PR #6786) - see docs/server.md "Geyser is now on a release build".
        // That bug's now-deleted workaround was server/datapacks/dragonkind-geyser-fix.
        PinnedJar(
            url = "https://download.geysermc.org/v2/projects/geyser/versions/2.12.0/builds/1262/downloads/spigot",
            filename = "Geyser-Spigot.jar",
            sha256 = "9f0efc83d1ece6f77552c573930431f08a760fa5c3eb02528c584fc291d44f92",
        ),
        // Pinned to 2.2.5 / build 141 ("Update to 26.3 (#686)"); re-checked 2026-10-17 against
        // https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds, still the
        // newest build, so no bump needed alongside the Geyser release pin above.
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

    // Paper 26.3 is still beta; pin build 156 explicitly rather than tracking "latest" (resolved
    // 2026-10-05 from https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/156).
    const val paperVersion = "26.3"
    const val paperBuild = 156
    val paperDownload = PinnedJar(
        url = "https://fill-data.papermc.io/v1/objects/f34cf5ddc90fabda14a944446808a29eb24d39b187a1f40df9051df41f8eebad/paper-26.3-156.jar",
        filename = "paper-26.3-156.jar",
        sha256 = "f34cf5ddc90fabda14a944446808a29eb24d39b187a1f40df9051df41f8eebad",
    )
}
