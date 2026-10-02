plugins {
    id("mc.plugin-conventions")
}

repositories {
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
}
