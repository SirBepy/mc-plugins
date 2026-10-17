// The Classes talent-tree screen for Claude SMP modpack players (client only). Not a Paper plugin,
// so it skips mc.plugin-conventions. 26.x ships unobfuscated: the non-remapping Loom id, plain
// `implementation`, no mappings.
plugins {
    id("net.fabricmc.fabric-loom") version "1.18.4"
}

version = "1.0.2"

base {
    archivesName = "classes-tree"
}

// Pinned to what the modpack ships (client/README.md), so the jar runs in it unchanged.
val fabricApiVersion = "0.161.0+26.3"

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation(fabricApi.module("fabric-networking-api-v1", fabricApiVersion))
    implementation(fabricApi.module("fabric-key-mapping-api-v1", fabricApiVersion))
    implementation(fabricApi.module("fabric-lifecycle-events-v1", fabricApiVersion))

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}
