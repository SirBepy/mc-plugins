plugins {
    id("mc.plugin-conventions")
}

repositories {
    // Classic VaultAPI is only published on jitpack; scoped to this module so it doesn't leak into
    // every other plugin's dependency resolution (same split dragonballs uses).
    maven("https://jitpack.io")
}

dependencies {
    // compileOnly isn't inherited by the test source set (same fix as cartel/tweaks/market/carry's build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")

    // minibosses softdepends on Guild (plugin.yml) to fire GuardianDefeatedEvent on a Guardian's
    // death; same compileOnly + testImplementation split tag uses for its own softdepend on names.
    compileOnly(project(":guild"))
    testImplementation(project(":guild"))

    // VaultUnlocked (installed on the server) still serves the classic Vault Economy API; same
    // pinned coordinates dragonballs already verified against OSV (no advisories) on 2026-10-06.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
}

// The Guardian Chronicles' story must not be readable in this public repo, so only its AES-GCM
// ciphertext is committed (src/main/resources/chronicles.enc). The plaintext lives in the
// gitignored .for_bepy/chronicles/chronicles.yml and the key in server/secrets.properties
// (minibosses.chronicles-key). Both tasks run ChronicleCipher itself, so the build and the plugin
// can never disagree on the format; decryptChronicles rebuilds the plaintext on a machine with the key.
listOf("encryptChronicles" to "encrypt", "decryptChronicles" to "decrypt").forEach { (taskName, mode) ->
    tasks.register<JavaExec>(taskName) {
        classpath = sourceSets["main"].runtimeClasspath
        mainClass = "dev.sirbepy.mc.minibosses.chronicle.ChronicleCipher"
        args(mode, rootProject.file(".for_bepy/chronicles/chronicles.yml"),
            file("src/main/resources/chronicles.enc"), rootProject.file("server/secrets.properties"))
    }
}
