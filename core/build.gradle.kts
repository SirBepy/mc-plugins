plugins {
    id("mc.java-conventions")
}

repositories {
    // Serves both releases and snapshots; Floodgate ships 2.2.5 only as a snapshot build.
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    // BedrockForms (dev.sirbepy.mc.core.bedrock) only; its public API is Floodgate-free, so this
    // stays compileOnly and names/safechests need no Floodgate dependency of their own.
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")

    // compileOnly isn't inherited by the test source set, and MockBukkit itself only declares
    // paper-api as compileOnly too, so BedrockFormsTest needs it added back explicitly (same fix
    // as names/build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}
