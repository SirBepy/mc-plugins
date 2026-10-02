plugins {
    id("mc.plugin-conventions")
}

repositories {
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    // compileOnly isn't inherited by the test source set; tests need paper-api and PacketEvents
    // visible too (MockBukkit pulls paper-api transitively at testImplementation scope, but the
    // pure-logic tests here construct YamlConfiguration/Component/NamedTextColor directly).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
}
